package com.example.halauncher.model

/**
 * What a suggested filter chip narrows search to: a whole search tab (Contacts), or one kind of
 * result listed under a tab (Settings, under Apps; PDFs, under Files).
 *
 * A file kind is told by the file name's extension, which every indexed file keeps, so the same test
 * works on results in memory and in the disk index's query (see [nameSuffixes]).
 *
 * @property tab the search tab the filter belongs to.
 * @property category the one category it keeps, for a kind; null for a whole tab.
 * @property extensions the file-name extensions it keeps (lower case), for a file kind; empty otherwise.
 */
enum class SearchFilter(
    val tab: SearchCategory,
    val category: SearchCategory? = null,
    val extensions: Set<String> = emptySet(),
) {
    APPS(SearchCategory.APP),
    CONTACTS(SearchCategory.CONTACT),
    EVENTS(SearchCategory.EVENT),
    FILES(SearchCategory.FILE),
    SETTINGS(SearchCategory.APP, SearchCategory.SETTING),
    SHORTCUTS(SearchCategory.APP, SearchCategory.SHORTCUT),
    FOLDERS(SearchCategory.FILE, SearchCategory.FOLDER),
    IMAGES(SearchCategory.FILE, SearchCategory.FILE, setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "avif")),
    VIDEOS(SearchCategory.FILE, SearchCategory.FILE, setOf("mp4", "mkv", "webm", "mov", "3gp", "avi", "m4v")),
    PDFS(SearchCategory.FILE, SearchCategory.FILE, setOf("pdf")),
    ;

    /** Whether this is a whole tab rather than a kind listed under one. */
    val isTab: Boolean get() = category == null

    /** Whether a result of [category] named [name] stays when this filter is on. */
    fun matches(category: SearchCategory, name: String): Boolean = when {
        this.category == null -> category.tab == tab
        category != this.category -> false
        extensions.isEmpty() -> true
        else -> name.substringAfterLast('.', missingDelimiterValue = "").lowercase() in extensions
    }

    fun matches(entry: SearchEntry): Boolean = matches(entry.category, entry.name)

    /** Whether everything this filter keeps, [other] keeps too. */
    fun isWithin(other: SearchFilter): Boolean = this == other || (other.isTab && tab == other.tab)

    /**
     * The categories the disk index is searched in for this filter. Shortcuts are searched with the
     * apps, as each app found also lists its shortcuts.
     */
    val searchedCategories: List<SearchCategory>
        get() = when (category) {
            null, SearchCategory.SHORTCUT -> SearchCategory.listedUnder(tab)
            else -> listOf(category)
        }

    /** SQL LIKE patterns the name must match one of, for a file kind ("%.pdf"); empty otherwise. */
    val nameSuffixes: List<String> get() = extensions.map { "%.$it" }

    companion object {
        /** The filter for the whole of [tab]. */
        fun forTab(tab: SearchCategory): SearchFilter = entries.first { it.isTab && it.tab == tab.tab }
    }
}
