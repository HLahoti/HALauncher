package com.example.halauncher.model

import android.content.Intent
import android.graphics.drawable.Drawable

/**
 * Anything the launcher's search can find and open: an app, a contact, a file, and so on.
 *
 * Opening an entry tries [link] first, then each of [fallbackLinks] in order, and uses the
 * first one some installed app can handle. For example, a contact would link to
 * `ACTION_VIEW` on its contact URI (opens in whichever contacts app the user has), with a
 * fallback of `ACTION_DIAL` on `tel:<number>` (opens the phone app with the number dialled).
 * A file would link to `ACTION_VIEW` on its content URI with its MIME type.
 */
interface SearchEntry {
    /** Unique across all entries of all categories; used as a stable list key. */
    val id: String

    /** What the user sees and what search matches against. */
    val name: String

    val category: SearchCategory

    /** What opens when the entry is chosen. */
    val link: Intent

    /** Tried in order when nothing can handle [link]. */
    val fallbackLinks: List<Intent> get() = emptyList()

    /**
     * An image specific to this entry, such as an app icon or contact photo. When null,
     * search shows the category's icon in its place.
     */
    val icon: Drawable? get() = null
}
