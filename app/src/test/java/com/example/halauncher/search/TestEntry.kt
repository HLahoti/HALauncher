package com.example.halauncher.search

import android.content.Intent
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry

/** A minimal SearchEntry for pure-JVM tests. */
data class TestEntry(
    override val name: String,
    override val category: SearchCategory = SearchCategory.APP,
) : SearchEntry {
    override val id: String get() = "test:${category.name}:$name"
    override val link: Intent get() = Intent()
}
