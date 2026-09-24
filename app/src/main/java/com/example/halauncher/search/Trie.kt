package com.example.halauncher.search

/**
 * Case- and accent-insensitive prefix index from names to values (search entries, in practice).
 *
 * Design choices:
 * - **Values at every node.** Each node stores all values whose key passes through it (not
 *   only the terminal node), so a search is a walk of `prefix.length` steps plus one list read,
 *   with no subtree traversal. The cost is memory: a value is referenced once per character of
 *   its key, which is negligible for a few hundred entries.
 * - **Reads don't copy; inserts do.** A node's value list is immutable once created. [insert]
 *   replaces it with a new list one element longer, at each node along the key, so it costs the
 *   sum of those lists' sizes. In exchange, [searchPrefix] and [Cursor.search] return the node's
 *   list itself: no copying and no allocation. A list handed out earlier never changes, even
 *   after later inserts. Searches happen per keystroke; inserts happen per rebuild or app opened.
 * - **Same folding as the rest of search, without allocating.** Keys and queries are folded a
 *   character at a time with [foldForSearch] (accents stripped, lower-cased: "Café" and "cafe"
 *   are the same key). The ranking uses the same function, and it mirrors the disk index's
 *   tokeniser, so the layers agree on what matches. A character that folds away (a lone combining accent) is simply not a step.
 *   Surrounding whitespace is skipped by index, so no new strings are made.
 *
 * Values come back in insertion order, so inserting a sorted list gives sorted results.
 * Not thread-safe: once shared, only use it (and its cursors) on the thread that reads it.
 */
class Trie<T> {
    private val root = TrieNode<T>()

    /** Bumped by every [insert]; a [Cursor] uses it to notice its cached path may be stale. */
    private var version = 0

    /** Adds [value] under [word], at each node along the word's path. */
    fun insert(word: CharSequence, value: T) {
        var node = root
        node.values += value
        for (i in word.indices) {
            val char = foldForSearch(word[i])
            if (char == FOLDED_AWAY) continue
            node = node.children.getOrPut(char) { TrieNode() }
            // A new list, not an append: lists already returned by searches stay as they were.
            node.values += value
        }
        version++
    }

    /**
     * Values whose key starts with [prefix] (surrounding whitespace ignored). An empty prefix
     * matches everything. The returned list is shared and immutable; it's not a copy.
     */
    fun searchPrefix(prefix: CharSequence): List<T> {
        val start = firstNonSpace(prefix)
        val end = lastNonSpace(prefix, start)
        return nodeAt(prefix, start, end)?.values ?: emptyList()
    }

    /**
     * Whether a value matching [predicate] is stored under [prefix] (its key starts with it).
     * Walks only that path. Checking a value's own key tells whether it was inserted.
     */
    fun contains(prefix: CharSequence, predicate: (T) -> Boolean): Boolean =
        nodeAt(prefix, 0, prefix.length)?.values?.any(predicate) ?: false

    fun isEmpty(): Boolean = root.values.isEmpty()

    /** A [Cursor] for incremental searches of this trie, e.g. one per search box. */
    fun cursor(): Cursor = Cursor()

    private fun nodeAt(text: CharSequence, start: Int, end: Int): TrieNode<T>? {
        var node = root
        for (i in start until end) {
            val char = foldForSearch(text[i])
            if (char == FOLDED_AWAY) continue
            node = node.children[char] ?: return null
        }
        return node
    }

    /**
     * Searches that pick up from the previous query instead of walking from the root each time.
     *
     * It remembers the folded characters of the last query and the nodes they reached. A new
     * query keeps the part it shares with the last one and walks only the rest: typing a
     * character is one step, Backspace is one step back, and an edit mid-word re-walks from the
     * change. Queries are compared after folding, so "cafe" → "café" is still one step. After any
     * [insert] the trie may have new nodes (a query that dead-ended might now match), so the
     * cursor notices the version change and starts from the root.
     *
     * Its buffers grow only when a query is longer than any before, so searching doesn't
     * allocate. Same threading rule as the trie.
     */
    inner class Cursor internal constructor() {
        /** The trie this cursor searches. */
        val trie: Trie<T> get() = this@Trie

        /** Folded characters of the last query, [typedLength] of them. */
        private var typed = CharArray(INITIAL_CAPACITY)
        private var typedLength = 0

        /** The new query's folded characters, before they become [typed]. Swapped, not copied. */
        private var incoming = CharArray(INITIAL_CAPACITY)

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
            return if (path.size == length + 1) path.last().values else emptyList()
        }

        /** Folds [query] (surrounding whitespace ignored) into [incoming]; returns how many chars. */
        private fun foldInto(query: CharSequence): Int {
            val start = firstNonSpace(query)
            val end = lastNonSpace(query, start)
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

        fun firstNonSpace(text: CharSequence): Int {
            var i = 0
            while (i < text.length && text[i].isWhitespace()) i++
            return i
        }

        /** One past the last non-whitespace character at or after [start]. */
        fun lastNonSpace(text: CharSequence, start: Int): Int {
            var end = text.length
            while (end > start && text[end - 1].isWhitespace()) end--
            return end
        }
    }
}

private class TrieNode<T> {
    val children: MutableMap<Char, TrieNode<T>> = HashMap()

    /** Never mutated after creation; [Trie.insert] replaces it. Safe to hand out as-is. */
    var values: List<T> = emptyList()
}
