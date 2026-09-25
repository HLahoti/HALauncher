package com.example.halauncher.data

/**
 * Storage folders whose files are never worth a launcher search result, so they stay out of the
 * disk index entirely: they're skipped by a full sync ([FileSearchProvider.loadEntries]) and a
 * change to one is neither indexed nor removed, it's simply left alone ([FileSearchProvider.loadByIds]).
 *
 * Everything here is app- or system-internal, not user content: encrypted backups only the owning
 * app can open, and the OS trash bin (files the user already deleted). WhatsApp *content* — Images,
 * Video, Documents, Voice Notes, Audio — is deliberately not listed.
 *
 * Matching is on MediaStore's RELATIVE_PATH: the file's folder relative to the storage root, with
 * no leading slash and a trailing one, e.g. "Android/media/com.whatsapp/WhatsApp/Databases/". A
 * [PREFIXES] entry matches that folder and everything beneath it. Because it's by folder, a file
 * moved out of a denylisted folder is indexed again on its next change, and one moved in drops out.
 */
object FilePathDenylist {

    /** Denylisted folder prefixes, in RELATIVE_PATH form (no leading slash, trailing slash). */
    val PREFIXES: List<String> = listOf(
        // Samsung's (and other apps') trash bin: files the user already sent to trash.
        "Android/.Trash/",
        // WhatsApp's encrypted chat-history backups (msgstore*.db.crypt14) — unreadable outside WhatsApp.
        "Android/media/com.whatsapp/WhatsApp/Databases/",
        // WhatsApp's encrypted settings backups (*.crypt14) plus backed-up stickers/wallpapers.
        "Android/media/com.whatsapp/WhatsApp/Backups/",
        // WhatsApp app internals: chat wallpapers, link-preview thumbs, share staging, its own trash, AI media.
        "Android/media/com.whatsapp/WhatsApp/Media/WallPaper/",
        "Android/media/com.whatsapp/WhatsApp/Media/.Links/",
        "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp AI Media/",
        "Android/media/com.whatsapp/WhatsApp/.Shared/",
        "Android/media/com.whatsapp/WhatsApp/.trash/",
    )

    /** Whether [relativePath] (a MediaStore RELATIVE_PATH) sits in a denylisted folder. */
    fun matches(relativePath: String?): Boolean {
        if (relativePath == null) return false
        return PREFIXES.any { relativePath.startsWith(it, ignoreCase = true) }
    }

    /**
     * A selection fragment that excludes denylisted folders from a MediaStore query, as
     * (clause, args). It keeps rows whose [pathColumn] is null (folder unknown, so unclassifiable)
     * or matches none of the prefixes. Callers AND this into their own selection and prepend [args].
     */
    fun sqlExclusion(pathColumn: String): Pair<String, List<String>> {
        if (PREFIXES.isEmpty()) return "1=1" to emptyList()
        val clause = PREFIXES.joinToString(
            prefix = "($pathColumn IS NULL OR (",
            separator = " AND ",
            postfix = "))",
        ) { "$pathColumn NOT LIKE ? ESCAPE '\\'" }
        val args = PREFIXES.map { escapeLike(it) + "%" }
        return clause to args
    }

    /** Escapes LIKE wildcards ('%', '_') and the escape char itself, so a prefix matches literally. */
    private fun escapeLike(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
