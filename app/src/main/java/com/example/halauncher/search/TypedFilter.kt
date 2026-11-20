package com.example.halauncher.search

import com.example.halauncher.config.FilterSuggestionConfig
import com.example.halauncher.model.SearchFilter

/**
 * "!name" typed in search, to call up a filter chip by name: "!pdf" brings up PDFs, "!im" Images, a
 * lone "!" every chip. Like macOS Spotlight's "/PDF", with a prefix that's on the first page of most
 * phone keyboards ([FilterSuggestionConfig.FILTER_PREFIX]).
 *
 * Only a word that starts with the prefix counts (at the start, or after a space), so "Hi!" and
 * "Yahoo!" stay text. The first such word is the one used; it's left out of what's searched.
 */
object TypedFilter {

    /**
     * [query] split into the [typed] filter name (what follows the "!", lower case, maybe empty) and
     * the [rest], which is searched as usual. [typed] is null when there's no "!name".
     */
    data class Parsed(val rest: String, val typed: String?)

    private val FILTER_WORD = Regex("""(^|\s)${Regex.escape(FilterSuggestionConfig.FILTER_PREFIX)}(\S*)""")

    fun parse(query: String): Parsed {
        val match = FILTER_WORD.find(query) ?: return Parsed(query, null)
        val word = match.groups[2]!!
        // From the prefix (after any space the match took) to the end of its word.
        val start = word.range.first - FilterSuggestionConfig.FILTER_PREFIX.length
        val rest = (query.substring(0, start) + query.substring(word.range.last + 1)).trim().replace(SPACES, " ")
        return Parsed(rest, word.value.lowercase())
    }

    /**
     * The filters whose name [typed] starts, best first: those it names in full, then the rest in
     * their usual order. [labels] are the chips' names as shown (in the user's language), matched
     * along with the English names in [FilterSuggestionConfig.FILTER_NAMES]. A blank [typed] matches
     * every filter.
     */
    fun matching(typed: String, labels: Map<SearchFilter, String>): List<SearchFilter> {
        fun names(filter: SearchFilter) =
            FilterSuggestionConfig.FILTER_NAMES[filter].orEmpty() + listOfNotNull(labels[filter]?.lowercase())
        val matches = SearchFilter.entries.filter { filter -> names(filter).any { it.startsWith(typed) } }
        return matches.sortedByDescending { filter -> typed in names(filter) }
    }

    /** [query] with its "!name" word taken out, or as it is if it has none. */
    fun strip(query: String): String = parse(query).rest

    private val SPACES = Regex("""\s+""")
}
