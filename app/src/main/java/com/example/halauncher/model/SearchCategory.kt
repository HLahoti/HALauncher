package com.example.halauncher.model

/**
 * The kind of thing a [SearchEntry] points to. Each category has its own icon, shown next
 * to every search result (see `SearchCategory.icon` in the UI layer).
 */
enum class SearchCategory {
    APP,
    CONTACT,
    FILE,

    /** App shortcuts. They're listed with the apps, under the Apps tab, rather than in a tab of their own. */
    SHORTCUT,

    /** Folders on shared storage. They're listed with the files, under the Files tab. */
    FOLDER;

    /** The search tab this category's entries are listed under. */
    val tab: SearchCategory
        get() = when (this) {
            SHORTCUT -> APP
            FOLDER -> FILE
            else -> this
        }

    /** Whether search has a tab (and filter button) just for this category. */
    val hasOwnTab: Boolean get() = tab == this

    companion object {
        /** Every category listed under [tab]: the category itself and any that live with it. */
        fun listedUnder(tab: SearchCategory): List<SearchCategory> = entries.filter { it.tab == tab }
    }
}
