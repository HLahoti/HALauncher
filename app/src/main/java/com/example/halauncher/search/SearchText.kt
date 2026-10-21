package com.example.halauncher.search

import java.text.Normalizer

private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

/** Returned by [foldForSearch] for a character that disappears when folded (a lone accent). */
const val FOLDED_AWAY = '\uFFFF'

/** Marks a [FOLD_CACHE] slot not computed yet. U+FFFE is a noncharacter, so never a real result. */
private const val NOT_COMPUTED = '\uFFFE'

/**
 * One slot per UTF-16 code unit (128 KB), filled the first time each character is folded. Racing
 * threads can only write the same value to a slot, so no locking is needed.
 */
private val FOLD_CACHE = CharArray(Char.MAX_VALUE.code + 1) { NOT_COMPUTED }

/**
 * How search compares one character: accents stripped and lower-cased, mirroring the disk index's
 * unicode61 tokeniser ("É" → 'e'). A combining accent on its own (as in "e" + U+0301) folds to
 * [FOLDED_AWAY]. A character that doesn't reduce to a single base letter, such as a Hangul
 * syllable, is only lower-cased.
 *
 * This is the one definition shared by the frequent-layer trie, [normalizeForSearch] and so the
 * ranking, so every layer treats "cafe" and "Café" alike. After a character's first use it's a
 * single array read, so per-keystroke search doesn't allocate.
 */
fun foldForSearch(char: Char): Char {
    val cached = FOLD_CACHE[char.code]
    if (cached != NOT_COMPUTED) return cached
    return computeFold(char).also { FOLD_CACHE[char.code] = it }
}

private fun computeFold(char: Char): Char {
    // Plain ASCII needs no decomposition.
    if (char.code < 0x80) return char.lowercaseChar()
    val base = Normalizer.normalize(char.toString(), Normalizer.Form.NFD)
        .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
    return when (base.length) {
        0 -> FOLDED_AWAY
        1 -> base[0].lowercaseChar()
        else -> char.lowercaseChar()
    }
}

/**
 * [text] with every character put through [foldForSearch] ("Café Crème" → "cafe creme"), so
 * ranking compares text the same way the trie and the disk index match it.
 */
fun normalizeForSearch(text: String): String = buildString(text.length) {
    for (char in text) {
        val folded = foldForSearch(char)
        if (folded != FOLDED_AWAY) append(folded)
    }
}

/**
 * Whether a folded character (see [foldForSearch]) is part of a word: a letter or a digit, the
 * same set [searchTerms] keeps. Everything else separates words. One character at a time, so the
 * trie can split names and queries into words without allocating.
 */
fun isSearchWordChar(folded: Char): Boolean = when (Character.getType(folded)) {
    Character.UPPERCASE_LETTER.toInt(),
    Character.LOWERCASE_LETTER.toInt(),
    Character.TITLECASE_LETTER.toInt(),
    Character.MODIFIER_LETTER.toInt(),
    Character.OTHER_LETTER.toInt(),
    Character.DECIMAL_DIGIT_NUMBER.toInt(),
    Character.LETTER_NUMBER.toInt(),
    Character.OTHER_NUMBER.toInt() -> true
    else -> false
}

/** The words of [text] as the index sees them: letters and digits, split on everything else. */
fun searchTerms(text: String): List<String> =
    normalizeForSearch(text).split(NON_WORD).filter { it.isNotEmpty() }

/** Builds FTS4 MATCH expressions from what the user typed. */
object FtsQuery {
    /**
     * Every term as a prefix, all required: "spo mu" → `spo* mu*`, matching "Spotify Music".
     * Terms contain only letters and digits and are lower case, so none can be read as FTS
     * syntax (quotes, `-`, or the upper-case operators AND / OR / NOT / NEAR).
     *
     * @return null when [terms] is empty; an empty search lists entries instead of matching.
     */
    fun prefixMatch(terms: List<String>): String? =
        if (terms.isEmpty()) null else terms.joinToString(" ") { "$it*" }
}
