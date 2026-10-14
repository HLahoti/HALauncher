package com.example.halauncher.search

import androidx.annotation.WorkerThread
import com.example.halauncher.config.SearchLayersConfig
import com.example.halauncher.data.SearchEntryProvider
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.search.index.IndexChange
import com.example.halauncher.search.index.InteractionLog
import com.example.halauncher.search.index.SearchIndex
import com.example.halauncher.search.ranking.RankingCandidate
import com.example.halauncher.search.ranking.RankingContext
import com.example.halauncher.search.ranking.SearchRanker
import com.example.halauncher.search.ranking.UsageHistory

/**
 * Two-layer search.
 *
 * 1. **Frequent layer**: an in-memory [Trie] of the entries the user opens most (by the
 *    [ranker] with no query, i.e. usage and time of day). Instant, answers every keystroke.
 * 2. **Disk layer**: the [index], holding every entry from every provider. Queried once the
 *    user pauses typing, ranked by the [ranker], minus whatever the frequent layer already
 *    showed.
 *
 * Opening an entry is recorded in the [interactions] log, which the next [buildFrequentLayer]
 * ranks by. An opened entry also goes to the front of the current layer straight away, in
 * place, through [moveToFrontOfFrequentLayer].
 *
 * Nothing here runs by itself: the background IndexUpdater applies source changes through
 * [applyChanges] and callers rebuild the frequent layer after it reports an update.
 */
class SearchEngine(
    private val index: SearchIndex,
    private val interactions: InteractionLog,
    private val ranker: SearchRanker,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Re-reads [provider] and brings its entries in the disk index up to date. */
    @WorkerThread
    fun sync(provider: SearchEntryProvider): SearchIndex.SyncResult = sync(provider.category, provider.loadEntries())

    /** Brings [category]'s entries in the disk index up to date with already-loaded [entries]. */
    @WorkerThread
    fun sync(category: SearchCategory, entries: List<SearchEntry>): SearchIndex.SyncResult =
        index.syncCategory(category, entries)

    /** Applies a batch of index changes as one transaction (see [SearchIndex.applyChanges]). */
    @WorkerThread
    fun applyChanges(changes: List<IndexChange>): Map<SearchCategory, SearchIndex.SyncResult> =
        index.applyChanges(changes)

    /** Folds the write-ahead log into the database file without blocking anyone. */
    @WorkerThread
    fun checkpoint() = index.checkpoint()

    /** How many entries each category has in the disk index; categories with none are left out. */
    @WorkerThread
    fun entryCounts(): Map<SearchCategory, Int> = index.counts()

    /**
     * Builds the frequent layer: every [pinned] entry first, in the order given (things opened
     * this session, most recent first, which must not drop out or sink mid-session), then the
     * best entries by usage, most relevant first, up to [SearchLayersConfig.FREQUENT_LAYER_SIZE]
     * in all. An empty query lists them in that order. While there's too little history to fill
     * it, it's topped up from [seed] (apps, on a fresh install).
     */
    @WorkerThread
    fun buildFrequentLayer(seed: List<SearchEntry>, pinned: List<SearchEntry> = emptyList()): Trie<SearchEntry> {
        val now = clock()
        val histories = interactions.allHistories(now)
        val context = RankingContext(query = "", terms = emptyList(), nowMillis = now)
        val chosenIds = mutableSetOf<String>()
        val pins = pinned.filter { chosenIds.add(it.id) }
        val frequent = index.byIds(histories.keys).values
            .filter { it.id !in chosenIds }
            .map { entry -> entry to ranker.score(context, RankingCandidate(entry, histories.getValue(entry.id))) }
            .sortedByDescending { (_, score) -> score }
            .map { (entry, _) -> entry }
            .take((SearchLayersConfig.FREQUENT_LAYER_SIZE - pins.size).coerceAtLeast(0))
        frequent.mapTo(chosenIds) { it.id }

        val room = SearchLayersConfig.FREQUENT_LAYER_SIZE - pins.size - frequent.size
        val topUp = if (SearchLayersConfig.SEED_FREQUENT_LAYER && room > 0) {
            seed.filter { it.id !in chosenIds }.take(room)
        } else {
            emptyList()
        }
        return trieOf(pins + frequent + topUp)
    }

    /**
     * Puts [entry] first in [layer], in place: something just opened leads the recents, and
     * something opened from the disk results answers instantly from now on. This touches only
     * the nodes along the entry's name, no rebuild. It can take the layer past its size limit
     * until the next [buildFrequentLayer].
     *
     * The trie isn't thread-safe, so call this on the thread that searches [layer] (or before the
     * layer is shared).
     *
     * @return false if the entry was already first.
     */
    fun moveToFrontOfFrequentLayer(layer: Trie<SearchEntry>, entry: SearchEntry): Boolean =
        layer.moveToFront(entry.name, entry) { it.id == entry.id }

    private fun trieOf(entries: List<SearchEntry>) =
        Trie<SearchEntry>().apply { entries.forEach { insert(it.name, it) } }

    /**
     * Frequent-layer matches: entries whose name starts with [query]. Per keystroke, [cursor]
     * walks only the characters that changed since its last query, and with no [category] the
     * result is the trie's own list: nothing is copied or allocated. A [category] is a search tab,
     * so it includes the categories listed under it (shortcuts under apps).
     */
    fun quickSearch(cursor: Trie<SearchEntry>.Cursor, query: String, category: SearchCategory?): List<SearchEntry> {
        val matches = cursor.search(query)
        return if (category == null) matches else matches.filter { it.category.tab == category }
    }

    /**
     * Disk-layer matches for [query], ranked, leaving out [excludeIds] (what the frequent layer
     * already showed). A blank query has none: with nothing typed, only the frequent layer shows.
     * A [category] is a search tab, as in [quickSearch].
     *
     * Candidates are ranked by name and usage first; only the [SearchLayersConfig.DISK_RESULT_LIMIT]
     * that make the cut have their payloads read and decoded.
     */
    @WorkerThread
    fun deepSearch(query: String, category: SearchCategory?, excludeIds: Set<String>): List<SearchEntry> {
        val terms = searchTerms(query)
        val match = FtsQuery.prefixMatch(terms) ?: return emptyList()

        val hits = index.matchHits(match, category?.let(SearchCategory::listedUnder), SearchLayersConfig.DISK_CANDIDATE_LIMIT)
            .filter { it.id !in excludeIds }
        val now = clock()
        val histories = interactions.historiesFor(hits.map { it.id }, now)
        val context = RankingContext(query = query, terms = terms, nowMillis = now)
        val best = hits
            .map { hit ->
                val usage = histories[hit.id] ?: UsageHistory.NEVER_OPENED
                hit to ranker.score(context, RankingCandidate(hit.name, hit.category, usage))
            }
            .sortedWith(compareByDescending<Pair<SearchIndex.Hit, Double>> { it.second }.thenBy { it.first.name })
            .take(SearchLayersConfig.DISK_RESULT_LIMIT)
            .map { it.first }
        return index.decode(best)
    }

    /**
     * Every entry of [category] (all of them when null), A–Z, leaving out [excludeIds] (what the
     * frequent layer already showed). Unlike [deepSearch] it ignores the query and lists the whole
     * set, for showing everything on a blank query — e.g. browsing the Apps category, where the
     * user wants to see all their apps alphabetically after their recent ones.
     */
    @WorkerThread
    fun listAll(category: SearchCategory?, excludeIds: Set<String>): List<SearchEntry> =
        index.all(category, SearchLayersConfig.DISK_CANDIDATE_LIMIT).filter { it.id !in excludeIds }

    /** The indexed entries with these [ids], in the same order; ids not in the index are left out. */
    @WorkerThread
    fun entriesByIds(ids: List<String>): List<SearchEntry> {
        val found = index.byIds(ids)
        return ids.mapNotNull(found::get)
    }

    /**
     * Remembers that the user opened [entry], for the next [buildFrequentLayer] and ranking. Also
     * prunes expired history, when a prune is due.
     */
    @WorkerThread
    fun recordOpen(entry: SearchEntry) = interactions.record(entry, clock())
}
