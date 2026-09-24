package com.example.halauncher.search.index

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.WorkerThread
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.search.index.SearchDatabase.Companion.COL_CATEGORY
import com.example.halauncher.search.index.SearchDatabase.Companion.COL_ENTRY_ID
import com.example.halauncher.search.index.SearchDatabase.Companion.COL_KEY
import com.example.halauncher.search.index.SearchDatabase.Companion.COL_OPENED_AT
import com.example.halauncher.search.index.SearchDatabase.Companion.COL_VALUE
import com.example.halauncher.search.index.SearchDatabase.Companion.INTERACTIONS
import com.example.halauncher.search.index.SearchDatabase.Companion.MAINTENANCE
import com.example.halauncher.search.ranking.UsageHistory
import java.util.concurrent.TimeUnit

/**
 * What the user opened and when. It decides which entries the frequent (trie) layer holds,
 * and feeds the usage parts of ranking.
 *
 * History older than [retentionDays] stops counting the moment it expires (reads leave it out),
 * and is deleted from disk at most once per [pruneIntervalMillis]: recording an open checks
 * whether a prune is due, instead of deleting on every open. When the last prune ran is kept in
 * the database, so the interval holds across restarts.
 *
 * All methods do disk I/O; call them off the main thread.
 */
class InteractionLog(
    private val database: SearchDatabase,
    private val retentionDays: Int,
    private val pruneIntervalMillis: Long = TimeUnit.DAYS.toMillis(1),
) {
    /** When history was last pruned; read from disk on first use. */
    private var lastPrunedAt: Long? = null

    @WorkerThread
    fun record(entry: SearchEntry, openedAtMillis: Long) {
        val db = database.writableDatabase
        db.insert(
            INTERACTIONS,
            null,
            ContentValues().apply {
                put(COL_ENTRY_ID, entry.id)
                put(COL_CATEGORY, entry.category.name)
                put(COL_OPENED_AT, openedAtMillis)
            },
        )
        pruneIfDue(db, openedAtMillis)
    }

    /**
     * Deletes expired history if the last prune was at least [pruneIntervalMillis] ago (or the
     * clock has since gone backwards past it). Returns whether it pruned.
     */
    @WorkerThread
    @Synchronized
    fun pruneIfDue(nowMillis: Long): Boolean = pruneIfDue(database.writableDatabase, nowMillis)

    @Synchronized
    private fun pruneIfDue(db: SQLiteDatabase, nowMillis: Long): Boolean {
        val last = lastPrunedAt ?: readLastPruned(db).also { lastPrunedAt = it }
        if (last in (nowMillis - pruneIntervalMillis + 1)..nowMillis) return false
        db.delete(INTERACTIONS, "$COL_OPENED_AT < ?", arrayOf(cutoff(nowMillis).toString()))
        db.insertWithOnConflict(
            MAINTENANCE,
            null,
            ContentValues().apply {
                put(COL_KEY, KEY_LAST_PRUNED)
                put(COL_VALUE, nowMillis)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        lastPrunedAt = nowMillis
        return true
    }

    /** Open history of every entry opened within the retention window before [nowMillis]. */
    @WorkerThread
    fun allHistories(nowMillis: Long): Map<String, UsageHistory> = histories(nowMillis, where = null, args = emptyArray())

    /** Open history for [ids] within the retention window before [nowMillis]; ids never opened are left out. */
    @WorkerThread
    fun historiesFor(ids: Collection<String>, nowMillis: Long): Map<String, UsageHistory> =
        ids.chunked(MAX_SQL_ARGS).fold(mutableMapOf()) { all, chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            all.apply { putAll(histories(nowMillis, "$COL_ENTRY_ID IN ($placeholders)", chunk.toTypedArray())) }
        }

    private fun histories(nowMillis: Long, where: String?, args: Array<String>): Map<String, UsageHistory> {
        // Expired opens may still be on disk until the next prune; they never count.
        val sql = buildString {
            append("SELECT $COL_ENTRY_ID, $COL_OPENED_AT FROM $INTERACTIONS WHERE $COL_OPENED_AT >= ?")
            if (where != null) append(" AND (").append(where).append(")")
        }
        val opens = mutableMapOf<String, MutableList<Long>>()
        database.readableDatabase.rawQuery(sql, arrayOf(cutoff(nowMillis).toString()) + args).use { cursor ->
            while (cursor.moveToNext()) {
                opens.getOrPut(cursor.getString(0)) { mutableListOf() } += cursor.getLong(1)
            }
        }
        return opens.mapValues { (_, times) -> UsageHistory(times) }
    }

    private fun cutoff(nowMillis: Long) = nowMillis - TimeUnit.DAYS.toMillis(retentionDays.toLong())

    private fun readLastPruned(db: SQLiteDatabase): Long =
        db.rawQuery("SELECT $COL_VALUE FROM $MAINTENANCE WHERE $COL_KEY = ?", arrayOf(KEY_LAST_PRUNED)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else NEVER
        }

    private companion object {
        const val MAX_SQL_ARGS = 500
        const val KEY_LAST_PRUNED = "interactions_last_pruned"
        /** Before any prune: the first open always prunes. */
        const val NEVER = Long.MIN_VALUE
    }
}
