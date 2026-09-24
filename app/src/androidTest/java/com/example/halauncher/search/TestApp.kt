package com.example.halauncher.search

import android.content.Intent
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry

/** Minimal entries for device tests that only need an id and a name. */
object TestApp {
    fun entry(name: String): SearchEntry = object : SearchEntry {
        override val id = "test:$name"
        override val name = name
        override val category = SearchCategory.APP
        override val link = Intent()
    }
}
