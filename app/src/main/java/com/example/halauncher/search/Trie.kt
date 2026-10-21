package com.example.halauncher.search

import java.util.IdentityHashMap

/**
 * Case- and accent-insensitive word-prefix index from names to values (search entries, in
 * practice). It matches the way the disk index does: every word of the query must start some
 * word of the name, in any order. "music", "spo mu" and "mu spo" all find "Spotify Music".
 *
 * Design choices:
 * - **A key per word.** A value is stored under each word of its name, split as [searchTerms]
 *   splits it (letters and digits; everything else separates words). A query's first word is
 *   walked like any prefix; its other words, if any, narrow that node's values by checking them
 *   against each value's words.
 * - **Values at every node.** Each node stores all values with a word passing through it (not
 *   only the terminal node), so a one-word search is a walk of the word's length plus one list
 *   read, with no subtree traversal. The cost is memory: a value is referenced once per character
 *   of its name, which is negligible for a few hundred entries.
 * - **Names that start with the query first.** A node keeps the values whose *first* word passes
 *   through it ahead of those reached only by a later word, so "mus" lists "Musixmatch" before
 *   "Spotify Music", as the ranking orders them. A value appears at most once per node, even when
 *   several of its words share the path ("Mega Music" under "m").
 * - **Reads don't copy; inserts do.** A node's value lists are immutable once created. [insert]
 *   replaces them with new lists one element longer, at each node along the name's words, so it
 *   costs the sum of those lists' sizes. In exchange, a one-word [searchPrefix] or [Cursor.search]
 *   returns the node's list itself: no copying and no allocation. Only a query of several words
 *   makes a new (filtered) list. A list handed out earlier never changes, even after later
 *   inserts. Searches happen per keystroke; inserts happen per rebuild or app opened.
 * - **Same folding as the rest of search, without allocating.** Keys and queries are folded a
 *   character at a time with [foldForSearch] (accents stripped, lower-cased: "Café" and "cafe"
 *   are the same key). The ranking uses the same function, and it mirrors the disk index's
 *   tokeniser, so the layers agree on what matches. A character that folds away (a lone combining
 *   accent) is simply not a step, and doesn't split a word.
 *
 * Values come back in insertion order (name-start matches, then the rest), so inserting a sorted
 * list gives sorted results. Not thread-safe: once shared, only use it (and its cursors) on the
 * thread that reads it.
 */
class Trie<T> {
    private val root = TrieNode<T>()

    /** Bumped by every [insert]; a [Cursor] uses it to notice its cached path may be stale. */
    private var version = 0

    /** Each value's words, folded, for checking a query's later words against. */
    private val wordsOf = IdentityHashMap<T, List<String>>()

    /** Adds [value] under each word of [name], at each node along the word. */
    fun insert(name: CharSequence, value: T) {
        root.add(value, firstWord = true)
        forEachWordNode(name) { node, firstWord -> node.add(value, firstWord) }
        wordsOf[value] = searchTerms(name.toString())
        version++
    }

    /**
     * Puts [value] first under each word of [name], at each node along the way, dropping any
     * value there that [isSame] says it replaces. Like [insert], it makes new lists: ones already
     * returned stay as they were.
     *
     * @return false if [value] was already first (nothing changed).
     */
    fun moveToFront(name: CharSequence, value: T, isSame: (T) -> Boolean): Boolean {
        if (root.values.firstOrNull()?.let(isSame) == true) return false
        root.putFirst(value, isSame, firstWord = true)
        forEachWordNode(name) { node, firstWord -> node.putFirst(value, isSame, firstWord) }
        wordsOf[value] = searchTerms(name.toString())
        version++
        return true
    }

    /**
     * Values with a word starting with each word of [query]. Nothing typed (blank) matches
     * everything; something typed with no letters or digits in it ("-") matches nothing, as on
     * disk. A one-word query returns a shared, immutable list, not a copy.
     */
    fun searchPrefix(query: CharSequence): List<T> {
        val start = firstTermStart(query)
        if (start == query.length) return if (isNothingTyped(query)) root.values else emptyList()
        val end = termEnd(query, start)
        var node = root
        for (i in start until end) {
            val char = foldForSearch(query[i])
            if (char == FOLDED_AWAY) continue
            node = node.children[char] ?: return emptyList()
        }
        return narrowByLaterTerms(node.values, query, end)
    }

    /**
     * Whether a value matching [predicate] is found by [query] (see [searchPrefix]). Checking a
     * value's own name tells whether it was inserted.
     */
    fun contains(query: CharSequence, predicate: (T) -> Boolean): Boolean = searchPrefix(query).any(predicate)

    fun isEmpty(): Boolean = root.values.isEmpty()

    /** A [Cursor] for incremental searches of this trie, e.g. one per search box. */
    fun cursor(): Cursor = Cursor()

    /** Calls [visit] with each node along each word of [name], and whether it's the first word. */
    private inline fun forEachWordNode(name: CharSequence, visit: (TrieNode<T>, Boolean) -> Unit) {
        var node: TrieNode<T>? = null // null between words
        var words = 0
        for (i in name.indices) {
            val char = foldForSearch(name[i])
            if (char == FOLDED_AWAY) continue
            if (!isSearchWordChar(char)) {
                node = null
                continue
            }
            if (node == null) words++
            val from = node ?: root
            node = from.children.getOrPut(char) { TrieNode() }
            visit(node, words == 1)
        }
    }

    /** [matches] left with those whose words also start with every word of [query] from [from]. */
    private fun narrowByLaterTerms(matches: List<T>, query: CharSequence, from: Int): List<T> {
        if (matches.isEmpty() || firstTermStart(query, from) == query.length) return matches
        val terms = searchTerms(query.subSequence(from, query.length).toString())
        return matches.filter { value ->
            val words = wordsOf[value].orEmpty()
            terms.all { term -> words.any { it.startsWith(term) } }
        }
    }

    /**
     * Searches that pick up from the previous query instead of walking from the root each time.
     *
     * It remembers the folded characters of the last query's first word and the nodes they
     * reached. A new query keeps the part it shares with the last one and walks only the rest:
     * typing a character is one step, Backspace is one step back, and an edit mid-word re-walks
     * from the change. Typing further words walks nothing; they only narrow the result. Queries
     * are compared after folding, so "cafe" → "café" is still one step. After any [insert] the
     * trie may have new nodes (a query that dead-ended might now match), so the cursor notices
     * the version change and starts from the root.
     *
     * Its buffers grow only when a query is longer than any before, so a one-word search doesn't
     * allocate. Same threading rule as the trie.
     */
    inner class Cursor internal constructor() {
        /** The trie this cursor searches. */
        val trie: Trie<T> get() = this@Trie

        /** Folded characters of the last query's first word, [typedLength] of them. */
        private var typed = CharArray(INITIAL_CAPACITY)
        private var typedLength = 0

        /** The new query's folded first word, before it becomes [typed]. Swapped, not copied. */
        private var incoming = CharArray(INITIAL_CAPACITY)

        /** Where the new query's first word ends (as an index into the query), set by [foldInto]. */
        private var incomingEnd = 0

        /**
         * `path[i]` is the node reached after `i` characters; `path[0]` is the root. It stops
         * short of [typedLength] + 1 where the query ran off the trie (no match).
         */
        private val path = ArrayList<TrieNode<T>>(INITIAL_CAPACITY + 1).apply { add(root) }

        private var seenVersion = version

        /** Same result as [Trie.searchPrefix] for [query], reusing the previous query's walk. */
        fun search(query: CharSequence): List<T> {
            if (seenVersion != version) reset()
            val length = foldInto(query)

            // Keep what the new query shares with the last one.
            var shared = 0
            while (shared < typedLength && shared < length && typed[shared] == incoming[shared]) shared++
            while (path.size > shared + 1) path.removeAt(path.lastIndex)

            // Walk only the new part. Once the walk has run off the trie, the rest can't match.
            for (i in shared until length) {
                if (path.size != i + 1) break
                path.last().children[incoming[i]]?.let(path::add)
            }

            val previous = typed
            typed = incoming
            incoming = previous
            typedLength = length
            if (length == 0) return if (isNothingTyped(query)) root.values else emptyList()
            val matches = if (path.size == length + 1) path.last().values else emptyList()
            return narrowByLaterTerms(matches, query, incomingEnd)
        }

        /** Folds [query]'s first word into [incoming]; returns how many chars. */
        private fun foldInto(query: CharSequence): Int {
            val start = firstTermStart(query)
            val end = termEnd(query, start)
            incomingEnd = end
            // Folding never lengthens text, so the raw length is always enough room.
            if (incoming.size < end - start) incoming = CharArray(maxOf(end - start, incoming.size * 2))
            var length = 0
            for (i in start until end) {
                val char = foldForSearch(query[i])
                if (char != FOLDED_AWAY) incoming[length++] = char
            }
            return length
        }

        private fun reset() {
            typedLength = 0
            path.clear()
            path.add(root)
            seenVersion = version
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 32

        /** The first index at or after [from] whose character folds to a word character, or the length. */
        fun firstTermStart(text: CharSequence, from: Int = 0): Int {
            var i = from
            while (i < text.length && !isSearchWordChar(foldForSearch(text[i]))) i++
            return i
        }

        /** One past the last character of the word starting at [start]: where a separator comes. */
        fun termEnd(text: CharSequence, start: Int): Int {
            var i = start
            while (i < text.length) {
                val char = foldForSearch(text[i])
                if (char != FOLDED_AWAY && !isSearchWordChar(char)) break
                i++
            }
            return i
        }

        /** True if [text] is only whitespace and characters that fold away: a blank search. */
        fun isNothingTyped(text: CharSequence): Boolean =
            text.all { it.isWhitespace() || foldForSearch(it) == FOLDED_AWAY }
    }
}

private class TrieNode<T> {
    val children: MutableMap<Char, TrieNode<T>> = HashMap()

    /** Values whose name's first word passes through here. */
    private var nameStarts: List<T> = emptyList()

    /** Values reached here only by a later word of their name. */
    private var wordStarts: List<T> = emptyList()

    /**
     * [nameStarts] then [wordStarts], made whenever either changes. Never mutated after creation,
     * so safe to hand out as-is.
     */
    var values: List<T> = emptyList()
        private set

    /** Appends [value], unless an earlier word of the same name already put it here. */
    fun add(value: T, firstWord: Boolean) {
        if (nameStarts.lastOrNull() === value || wordStarts.lastOrNull() === value) return
        // New lists, not appends: lists already returned by searches stay as they were.
        if (firstWord) nameStarts = nameStarts + value else wordStarts = wordStarts + value
        updateValues()
    }

    /** Puts [value] first, dropping what [isSame] matches, unless an earlier word just did. */
    fun putFirst(value: T, isSame: (T) -> Boolean, firstWord: Boolean) {
        if (nameStarts.firstOrNull() === value || wordStarts.firstOrNull() === value) return
        val names = nameStarts.filterNot(isSame)
        val words = wordStarts.filterNot(isSame)
        nameStarts = if (firstWord) listOf(value) + names else names
        wordStarts = if (firstWord) words else listOf(value) + words
        updateValues()
    }

    private fun updateValues() {
        values = if (wordStarts.isEmpty()) nameStarts else nameStarts + wordStarts
    }
}
