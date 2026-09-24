package com.example.halauncher.search.ranking

import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry

/**
 * Scores how relevant an entry is to what the user is doing right now. Higher is better; the
 * scale only has to be consistent within one ranker. Used both to order disk search results and,
 * with an empty query, to choose which entries the frequent (trie) layer keeps.
 */
fun interface SearchRanker {
    fun score(context: RankingContext, candidate: RankingCandidate): Double
}

/** What's known about the moment of the search. */
data class RankingContext(
    /** The raw query; blank when choosing frequent entries. */
    val query: String,
    /** The query split into normalised words (see searchTerms). */
    val terms: List<String>,
    val nowMillis: Long,
)

/**
 * What ranking looks at: an entry's name and category (not the whole entry, so disk matches can
 * be ranked before they're decoded) and its open history.
 */
data class RankingCandidate(
    val name: String,
    val category: SearchCategory,
    val usage: UsageHistory,
) {
    constructor(entry: SearchEntry, usage: UsageHistory) : this(entry.name, entry.category, usage)
}

/** When an entry was opened, within the interaction log's retention window. */
data class UsageHistory(val openTimesMillis: List<Long>) {
    val openCount: Int get() = openTimesMillis.size
    val lastOpenedMillis: Long? get() = openTimesMillis.maxOrNull()

    companion object {
        val NEVER_OPENED = UsageHistory(emptyList())
    }
}
