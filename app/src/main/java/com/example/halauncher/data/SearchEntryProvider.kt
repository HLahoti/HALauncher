package com.example.halauncher.data

import androidx.annotation.WorkerThread
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry

/**
 * A source of searchable entries, all of one [category]. Its entries are synced into the
 * disk search index (see SearchEngine.sync), so adding a new kind of result means adding
 * a provider and a SearchEntryCodec for its category.
 */
interface SearchEntryProvider {
    val category: SearchCategory

    /** Loads this source's entries. May do I/O, so call it off the main thread. */
    @WorkerThread
    fun loadEntries(): List<SearchEntry>
}
