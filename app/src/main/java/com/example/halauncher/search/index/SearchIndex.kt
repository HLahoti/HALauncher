package com.example.halauncher.search.index

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.WorkerThread
import androidx.core.database.sqlite.transaction
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.search.index.SearchDatabase.Companion.COL_CATEGORY
import com.example.halauncher.search.index.SearchDatabase.Companion.COL_ENTRY_ID
import com.example.halauncher.search.index.SearchDatabase.Companion.COL_NAME
import com.example.halauncher.search.index.SearchDatabase.Companion.COL_PAYLOAD
import com.example.halauncher.search.index.SearchDatabase.Companion.ENTRIES

/**
 * The disk search layer: every entry from every provider, in an FTS4 table. Unlike the
 * in-memory trie it holds everything, and it matches any word in a name, not just the start.
 *
 * All methods do disk I/O; call them off the main thread.
 */
class SearchIndex(private val database: SearchDatabase, private val codecs: SearchEntryCodecs) {

    /** What a [syncCategory] changed. */
    /** What a sync or bulk update changed in one category. */
    data class SyncResult(val inserted: Int, val updated: Int, val removed: Int, val unchanged: Int) {
        val changedAnything: Boolean get() = inserted + updated + removed > 0

        operator fun plus(other: SyncResult) = SyncResult(
            inserted + other.inserted,
            updated + other.updated,
            removed + other.removed,
            unchanged + other.unchanged,
        )

        companion object {
            val NONE = SyncResult(0, 0, 0, 0)
        }
    }

    /**
     * Makes the stored entries of [category] match [entries] by changing only what differs:
     * new ids are inserted, entries whose name or payload changed are updated, ids no longer
     * present are removed, and everything else is left as it is. The index built in earlier
     * sessions is kept and only the difference is written. It's one transaction, so searches
     * see either the old set or the new one.
     */
    @WorkerThread
    fun syncCategory(category: SearchCategory, entries: List<SearchEntry>): SyncResult =
        applyChanges(listOf(IndexChange.ReplaceAll(category, entries))).getValue(category)

    /**
     * Applies every change in [changes], across any categories, as **one** transaction: the
     * background updater's bulk write. Unchanged rows aren't rewritten.
     *
     * The transaction is non-exclusive and the database runs in write-ahead-log mode, so searches
     * on other threads keep reading the last committed state the whole time instead of waiting;
     * they see all of this batch or none of it.
     *
     * @return what changed, per category touched.
     */
    @WorkerThread
    fun applyChanges(changes: List<IndexChange>): Map<SearchCategory, SyncResult> {
        val results = mutableMapOf<SearchCategory, SyncResult>()
        if (changes.isEmpty()) return results
        database.writableDatabase.transaction(exclusive = false) {
            for (change in changes) {
                val result = when (change) {
                    is IndexChange.ReplaceAll -> replaceAllIn(change.category, change.entries)
                    is IndexChange.Upsert -> upsertIn(change.category, change.entries)
                    is IndexChange.Remove -> removeIn(change.category, change.ids)
                }
                results[change.category] = (results[change.category] ?: SyncResult.NONE) + result
            }
        }
        return results
    }

    /**
     * Folds the write-ahead log back into the main database file without blocking readers or
     * writers (a PASSIVE checkpoint), so the log doesn't keep growing after bulk updates.
     */
    @WorkerThread
    fun checkpoint() {
        database.writableDatabase.rawQuery("PRAGMA wal_checkpoint(PASSIVE)", null).use { it.moveToFirst() }
    }

    private fun SQLiteDatabase.replaceAllIn(category: SearchCategory, entries: List<SearchEntry>): SyncResult {
        val wanted = encode(category, entries)
        val stored = storedRows(category, ids = null)
        var removed = 0
        compileStatement("DELETE FROM $ENTRIES WHERE docid = ?").use { delete ->
            for ((id, row) in stored) {
                if (id in wanted) continue
                delete.bindLong(1, row.docId)
                delete.executeUpdateDelete()
                removed++
            }
        }
        return writeWanted(category, wanted, stored).copy(removed = removed)
    }

    /** Inserts or updates just [entries]; everything else in [category] is left alone. */
    private fun SQLiteDatabase.upsertIn(category: SearchCategory, entries: List<SearchEntry>): SyncResult {
        val wanted = encode(category, entries)
        return writeWanted(category, wanted, storedRows(category, ids = wanted.keys))
    }

    private fun SQLiteDatabase.removeIn(category: SearchCategory, ids: Collection<String>): SyncResult {
        var removed = 0
        compileStatement("DELETE FROM $ENTRIES WHERE docid = ?").use { delete ->
            for (row in storedRows(category, ids).values) {
                delete.bindLong(1, row.docId)
                delete.executeUpdateDelete()
                removed++
            }
        }
        return SyncResult(inserted = 0, updated = 0, removed = removed, unchanged = 0)
    }

    /** Later duplicates of an id win, as they would have with a plain insert. */
    private fun encode(category: SearchCategory, entries: List<SearchEntry>): Map<String, StoredText> {
        val codec = codecs.forCategory(category)
        val wanted = LinkedHashMap<String, StoredText>()
        for (entry in entries) wanted[entry.id] = StoredText(entry.name, codec.encode(entry))
        return wanted
    }

    /** Inserts [wanted] rows missing from [stored] and updates those whose text changed. */
    private fun SQLiteDatabase.writeWanted(
        category: SearchCategory,
        wanted: Map<String, StoredText>,
        stored: Map<String, StoredRow>,
    ): SyncResult {
        var inserted = 0
        var updated = 0
        compileStatement("UPDATE $ENTRIES SET $COL_NAME = ?, $COL_PAYLOAD = ? WHERE docid = ?").use { update ->
            compileStatement(
                "INSERT INTO $ENTRIES ($COL_NAME, $COL_ENTRY_ID, $COL_CATEGORY, $COL_PAYLOAD) VALUES (?, ?, ?, ?)",
            ).use { insert ->
                for ((id, text) in wanted) {
                    val existing = stored[id]
                    when {
                        existing == null -> {
                            insert.bindString(1, text.name)
                            insert.bindString(2, id)
                            insert.bindString(3, category.name)
                            insert.bindString(4, text.payload)
                            insert.executeInsert()
                            inserted++
                        }
                        existing.text != text -> {
                            update.bindString(1, text.name)
                            update.bindString(2, text.payload)
                            update.bindLong(3, existing.docId)
                            update.executeUpdateDelete()
                            updated++
                        }
                    }
                }
            }
        }
        return SyncResult(inserted, updated, removed = 0, unchanged = wanted.size - inserted - updated)
    }

    private data class StoredText(val name: String, val payload: String)
    private data class StoredRow(val docId: Long, val text: StoredText)

    /** Stored rows of [category] by entry id: all of them, or only those in [ids]. */
    private fun SQLiteDatabase.storedRows(category: SearchCategory, ids: Collection<String>?): Map<String, StoredRow> {
        val rows = HashMap<String, StoredRow>()
        val chunks = ids?.chunked(MAX_SQL_ARGS - 1) ?: listOf(null)
        for (chunk in chunks) {
            if (chunk != null && chunk.isEmpty()) continue
            val sql = buildString {
                append("SELECT docid, $COL_ENTRY_ID, $COL_NAME, $COL_PAYLOAD FROM $ENTRIES WHERE $COL_CATEGORY = ?")
                if (chunk != null) append(" AND $COL_ENTRY_ID IN (").append(chunk.joinToString(",") { "?" }).append(")")
            }
            val args = arrayOf(category.name) + chunk.orEmpty()
            rawQuery(sql, args).use { cursor ->
                while (cursor.moveToNext()) {
                    rows[cursor.getString(1)] = StoredRow(cursor.getLong(0), StoredText(cursor.getString(2), cursor.getString(3)))
                }
            }
        }
        return rows
    }

    /**
     * A match found by name alone: enough to rank it, without reading or decoding its payload.
     * Turn the ones worth showing into entries with [decode].
     */
    data class Hit(val docId: Long, val id: String, val name: String, val category: SearchCategory)

    /**
     * Entries whose name matches the FTS [matchExpression] (see FtsQuery), optionally limited to
     * [category], as undecoded [Hit]s. Returns at most [limit], shortest names first: those are
     * the likelier strong matches, and the caller ranks them properly afterwards.
     */
    @WorkerThread
    fun matchHits(matchExpression: String, category: SearchCategory?, limit: Int): List<Hit> {
        val sql = buildString {
            append("SELECT docid, $COL_ENTRY_ID, $COL_NAME, $COL_CATEGORY FROM $ENTRIES WHERE $ENTRIES MATCH ?")
            if (category != null) append(" AND $COL_CATEGORY = ?")
            append(" ORDER BY length($COL_NAME) LIMIT ").append(limit)
        }
        val args = listOfNotNull(matchExpression, category?.name).toTypedArray()
        return database.readableDatabase.rawQuery(sql, args).use { cursor ->
            val hits = ArrayList<Hit>(cursor.count)
            while (cursor.moveToNext()) {
                hits += Hit(cursor.getLong(0), cursor.getString(1), cursor.getString(2), SearchCategory.valueOf(cursor.getString(3)))
            }
            hits
        }
    }

    /**
     * The entries for [hits], in the same order, reading and decoding only these rows' payloads.
     * A hit that can't be decoded any more (e.g. its app was uninstalled) is left out.
     */
    @WorkerThread
    fun decode(hits: List<Hit>): List<SearchEntry> {
        val decoded = HashMap<Long, SearchEntry>(hits.size)
        for (chunk in hits.chunked(MAX_SQL_ARGS)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val sql = "SELECT docid, $COL_NAME, $COL_CATEGORY, $COL_PAYLOAD FROM $ENTRIES WHERE docid IN ($placeholders)"
            database.readableDatabase.rawQuery(sql, chunk.map { it.docId.toString() }.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val category = SearchCategory.valueOf(cursor.getString(2))
                    codecs.forCategory(category).decode(cursor.getString(1), cursor.getString(3))
                        ?.let { decoded[cursor.getLong(0)] = it }
                }
            }
        }
        return hits.mapNotNull { decoded[it.docId] }
    }

    /** Decoded entries matching [matchExpression]; see [matchHits]. */
    @WorkerThread
    fun match(matchExpression: String, category: SearchCategory?, limit: Int): List<SearchEntry> =
        decode(matchHits(matchExpression, category, limit))

    /** Every entry (optionally of one [category]) A–Z, up to [limit]. */
    @WorkerThread
    fun all(category: SearchCategory?, limit: Int): List<SearchEntry> =
        if (category == null) {
            query(where = null, args = emptyArray(), orderBy = "$COL_NAME COLLATE NOCASE", limit = limit)
        } else {
            query("$COL_CATEGORY = ?", arrayOf(category.name), orderBy = "$COL_NAME COLLATE NOCASE", limit = limit)
        }

    /** The stored entries with these ids; ids not in the index are left out. */
    @WorkerThread
    fun byIds(ids: Collection<String>): Map<String, SearchEntry> =
        ids.chunked(MAX_SQL_ARGS).flatMap { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            query("$COL_ENTRY_ID IN ($placeholders)", chunk.toTypedArray(), orderBy = null, limit = null)
        }.associateBy { it.id }

    /** Categories with at least one stored entry, in declaration order. */
    @WorkerThread
    fun categories(): List<SearchCategory> = counts().keys.toList()

    /** How many entries each category holds, in declaration order; empty categories are left out. */
    @WorkerThread
    fun counts(): Map<SearchCategory, Int> {
        val counts = mutableMapOf<String, Int>()
        val sql = "SELECT $COL_CATEGORY, COUNT(*) FROM $ENTRIES GROUP BY $COL_CATEGORY"
        database.readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) counts[cursor.getString(0)] = cursor.getInt(1)
        }
        return SearchCategory.entries.mapNotNull { category -> counts[category.name]?.let { category to it } }.toMap()
    }

    private fun query(where: String?, args: Array<String>, orderBy: String?, limit: Int?): List<SearchEntry> {
        val sql = buildString {
            append("SELECT $COL_NAME, $COL_CATEGORY, $COL_PAYLOAD FROM $ENTRIES")
            if (where != null) append(" WHERE ").append(where)
            if (orderBy != null) append(" ORDER BY ").append(orderBy)
            if (limit != null) append(" LIMIT ").append(limit)
        }
        return database.readableDatabase.rawQuery(sql, args).use(::decodeAll)
    }

    private fun decodeAll(cursor: Cursor): List<SearchEntry> {
        val entries = ArrayList<SearchEntry>(cursor.count)
        while (cursor.moveToNext()) {
            val category = SearchCategory.valueOf(cursor.getString(1))
            codecs.forCategory(category).decode(cursor.getString(0), cursor.getString(2))?.let(entries::add)
        }
        return entries
    }

    private companion object {
        /** Stays under SQLite's default limit of 999 bound parameters per statement. */
        const val MAX_SQL_ARGS = 500
    }
}

/** One change to the disk index; a batch of them is applied together by [SearchIndex.applyChanges]. */
sealed interface IndexChange {
    val category: SearchCategory

    /** The category should contain exactly [entries] (a full re-sync; only differences are written). */
    data class ReplaceAll(override val category: SearchCategory, val entries: List<SearchEntry>) : IndexChange

    /** Add or refresh just these entries. */
    data class Upsert(override val category: SearchCategory, val entries: List<SearchEntry>) : IndexChange

    /** Drop these entry ids, if present. */
    data class Remove(override val category: SearchCategory, val ids: Collection<String>) : IndexChange
}
