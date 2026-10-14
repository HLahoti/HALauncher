package com.example.halauncher.model

/**
 * The kind of thing a [SearchEntry] points to. Each category has its own icon, shown next
 * to every search result (see `SearchCategory.icon` in the UI layer).
 */
enum class SearchCategory {
    APP,
    CONTACT,
    FILE,
    SHORTCUT,
}
