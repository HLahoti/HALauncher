package com.example.halauncher.search

import com.example.halauncher.config.FilterSuggestionConfig
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.model.SearchFilter
import kotlin.math.ln

/**
 * Picks the filter chips search suggests for a query: the [SearchFilter]s that would narrow what's
 * listed, best first.
 *
 * It only looks at what search already has: the listed results, in display order, and how many more
 * disk matches each filter would keep than made the list. So it costs no extra queries, and it can
 * run on every keystroke (frequent layer only) and again once the disk answers.
 *
 * On "All" every filter can be suggested; on a tab, only the kinds listed under it.
 */
object FilterSuggester {

    /**
     * The chips to show, best first.
     *
     * @param tab the search tab showing, or null for "All".
     * @param query what's typed. Its words can hint at a filter ("wifi" for Settings).
     * @param listed the listed results, in display order.
     * @param unlisted how many disk matches each filter would keep that didn't make the list.
     * @param previous the chips showing for the last answer on this tab. Which chips show only changes
     *   when a new one scores clearly better ([FilterSuggestionConfig.REPLACE_MARGIN]), so the row
     *   doesn't churn as the user types.
     * @param provisional whether the disk is still to answer. Until it does, the chips showing are kept
     *   in place even where the frequent layer's matches alone don't back them (the disk may well),
     *   so the row doesn't flicker on every keystroke; a much better newcomer can still replace one.
     *   Once the disk has answered, the chips are put in order of score.
     */
    fun suggest(
        tab: SearchCategory?,
        query: String,
        listed: List<SearchEntry>,
        unlisted: Map<SearchFilter, Int> = emptyMap(),
        previous: List<SearchFilter> = emptyList(),
        provisional: Boolean = false,
    ): List<SearchFilter> {
        if (query.isBlank()) return emptyList()
        val grandTotal = listed.size + unlisted.filterKeys { it.isTab }.values.sum()
        if (grandTotal == 0 && !provisional) return emptyList()
        val words = searchTerms(query)

        val totals = candidatesFor(tab).associateWith { filter -> listed.count(filter::matches) + (unlisted[filter] ?: 0) }
        val scores = totals.mapNotNull { (filter, total) ->
            // A chip must narrow the results: leave out one that would show nothing, or everything.
            if (total == 0 || total == grandTotal) return@mapNotNull null
            // Nor show two chips that would show the same results: the narrower one says more
            // ("Settings" rather than "Apps", when every app-side match is a setting).
            if (totals.any { (other, n) -> other != filter && other.isWithin(filter) && n == total }) return@mapNotNull null
            var rank = 0.0
            listed.forEachIndexed { i, entry -> if (filter.matches(entry)) rank += 1.0 / (i + 1) }
            val intent = if (hintsAt(words, filter)) FilterSuggestionConfig.INTENT_BOOST else 0.0
            filter to rank + FilterSuggestionConfig.VOLUME_WEIGHT * ln(1.0 + total) + intent
        }.toMap()
        fun score(filter: SearchFilter) = scores[filter] ?: 0.0

        // The chips already showing that still apply stay; while provisional, all of them do.
        val chips = previous.filter { provisional || it in scores }.take(FilterSuggestionConfig.MAX_CHIPS).toMutableList()
        for (candidate in scores.keys.sortedByDescending(::score)) {
            if (candidate in chips) continue
            if (chips.size < FilterSuggestionConfig.MAX_CHIPS) {
                chips += candidate
                continue
            }
            val weakest = chips.minBy(::score)
            if (score(candidate) > score(weakest) + FilterSuggestionConfig.REPLACE_MARGIN) {
                chips[chips.indexOf(weakest)] = candidate
            }
        }
        // Mid-typing the order holds still; once the disk has answered, the best leads.
        return if (provisional) chips else chips.sortedByDescending(::score)
    }

    /** The filters that can be offered on [tab]: every one on "All", the kinds listed under a tab otherwise. */
    fun candidatesFor(tab: SearchCategory?): List<SearchFilter> =
        if (tab == null) SearchFilter.entries else SearchFilter.entries.filter { !it.isTab && it.tab == tab }

    private fun hintsAt(words: List<String>, filter: SearchFilter): Boolean {
        val hints = FilterSuggestionConfig.INTENT_WORDS[filter] ?: return false
        return words.any { word ->
            word in hints || (word.length >= FilterSuggestionConfig.INTENT_MIN_PREFIX && hints.any { it.startsWith(word) })
        }
    }
}
