package com.example.halauncher.search.index

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.halauncher.config.IndexUpdaterConfig

/**
 * The on-disk search store, shared by [SearchIndex] (every entry, full-text searchable) and
 * [InteractionLog] (what the user opened, and when).
 *
 * @param name the database file; null keeps it in memory (for tests).
 */
class SearchDatabase(context: Context, name: String? = NAME) :
    SQLiteOpenHelper(context.applicationContext, name, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        // Write-ahead logging: a writer appends to a separate log while readers keep reading the
        // last committed state, so searches never wait for a background bulk update (and vice
        // versa). Android also gives readers their own pooled connections in this mode.
        db.enableWriteAheadLogging()
        // In WAL mode NORMAL is still crash-safe (a power cut can lose only the latest commit,
        // never corrupt the file) and avoids an fsync on every commit.
        db.execSQL("PRAGMA synchronous = NORMAL")
        db.rawQuery("PRAGMA wal_autocheckpoint = ${IndexUpdaterConfig.WAL_AUTOCHECKPOINT_PAGES}", null).use { it.moveToFirst() }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_ENTRIES)
        db.execSQL(CREATE_INTERACTIONS)
        db.execSQL(CREATE_INTERACTIONS_INDEX)
        db.execSQL(CREATE_MAINTENANCE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Entries are rebuilt from their providers on every launch, so when their schema changes
        // the index can simply be dropped and recreated. Interactions are the user's history:
        // migrate them in place, never drop.
        if (oldVersion < 2) db.execSQL(CREATE_MAINTENANCE)
        if (oldVersion < 3) {
            // Entries gained a searchable keywords column.
            db.execSQL("DROP TABLE IF EXISTS $ENTRIES")
            db.execSQL(CREATE_ENTRIES)
        }
    }

    companion object {
        const val NAME = "search.db"
        private const val VERSION = 3

        const val ENTRIES = "entries"
        const val COL_NAME = "name"
        const val COL_KEYWORDS = "keywords"
        const val COL_ENTRY_ID = "entry_id"
        const val COL_CATEGORY = "category"
        const val COL_PAYLOAD = "payload"

        const val INTERACTIONS = "interactions"
        const val COL_OPENED_AT = "opened_at"

        /** Housekeeping state as key / integer value pairs (e.g. when history was last pruned). */
        const val MAINTENANCE = "maintenance"
        const val COL_KEY = "key"
        const val COL_VALUE = "value"

        /**
         * FTS4 table holding every search entry. Only the name and the entry's hidden keywords
         * (see SearchEntry.keywords; empty for most) are tokenised and searchable; the rest rides
         * along so a match can be turned back into an entry. The unicode61
         * tokeniser folds case and strips accents ("café" matches "cafe"), and the prefix
         * indexes make short "term*" queries fast.
         */
        private val CREATE_ENTRIES = """
            CREATE VIRTUAL TABLE $ENTRIES USING fts4(
                $COL_NAME, $COL_ENTRY_ID, $COL_CATEGORY, $COL_PAYLOAD, $COL_KEYWORDS,
                notindexed=$COL_ENTRY_ID, notindexed=$COL_CATEGORY, notindexed=$COL_PAYLOAD,
                tokenize=unicode61 "remove_diacritics=1",
                prefix="1,2,3"
            )
        """.trimIndent()

        private val CREATE_INTERACTIONS = """
            CREATE TABLE $INTERACTIONS (
                $COL_ENTRY_ID TEXT NOT NULL,
                $COL_CATEGORY TEXT NOT NULL,
                $COL_OPENED_AT INTEGER NOT NULL
            )
        """.trimIndent()

        private val CREATE_MAINTENANCE =
            "CREATE TABLE IF NOT EXISTS $MAINTENANCE ($COL_KEY TEXT PRIMARY KEY NOT NULL, $COL_VALUE INTEGER NOT NULL)"

        private val CREATE_INTERACTIONS_INDEX =
            "CREATE INDEX interactions_by_entry ON $INTERACTIONS ($COL_ENTRY_ID, $COL_OPENED_AT)"
    }
}
