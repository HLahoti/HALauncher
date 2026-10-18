package com.example.halauncher.data

/**
 * Which storage folders search lists. Paths are in MediaStore's RELATIVE_PATH form: relative to the
 * storage root, with a trailing slash ("DCIM/Camera/"), and "" for the root itself.
 */
object FolderRules {

    /** WhatsApp's weekly subfolders, e.g. "WhatsApp Voice Notes/202336/". */
    private val WEEK_FOLDER = Regex("""\d{6}""")

    /**
     * A folder whose name, or the name of a folder above it, starts with a dot. Hidden folders are
     * never listed.
     */
    fun isHidden(path: String): Boolean = path.split('/').any { it.startsWith('.') }

    /**
     * Folders left out while "Reduce index size" is on: the storage root, the [FilePathDenylist]
     * folders (and everything in them), and WhatsApp's weekly folders. WhatsApp keeps voice and
     * video notes in one folder per week ("202336"), dozens of them with nothing to tell them apart,
     * and their files are hidden from MediaStore, so they'd only ever look empty.
     */
    fun leftOutWhenReduced(path: String): Boolean =
        path.isEmpty() || FilePathDenylist.matches(path) || isWhatsAppWeekFolder(path)

    private fun isWhatsAppWeekFolder(path: String): Boolean {
        val trimmed = path.trimEnd('/')
        val name = trimmed.substringAfterLast('/')
        val parent = trimmed.substringBeforeLast('/', missingDelimiterValue = "")
        // Under WhatsApp/Media/, wherever WhatsApp keeps it: "Android/media/com.whatsapp/WhatsApp/"
        // now, "WhatsApp/" at the top of storage on older versions.
        return WEEK_FOLDER.matches(name) && "/$parent/".contains("/WhatsApp/Media/", ignoreCase = true)
    }

    /**
     * Every folder from the top of [path] down to [path] itself: "DCIM/" and "DCIM/Camera/" for
     * "DCIM/Camera/". Nothing for the root ("") or a malformed path.
     */
    fun withAncestors(path: String): List<String> {
        val parts = path.split('/').filter { it.isNotEmpty() }
        return parts.indices.map { i -> parts.subList(0, i + 1).joinToString("/", postfix = "/") }
    }
}
