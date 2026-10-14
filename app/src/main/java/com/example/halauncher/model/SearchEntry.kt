package com.example.halauncher.model

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/**
 * Anything the launcher's search can find and open: an app, a contact, a file, and so on.
 *
 * Opening an entry tries [launchDirectly] first, then [link], then each of [fallbackLinks] in
 * order, and uses the first one that works. For example, a contact would link to
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
     * Opens the entry some way other than an intent, before [link] is tried. For things only the
     * launcher can start, such as an app shortcut (started through LauncherApps). The default
     * does nothing, so the entry opens through its links.
     *
     * @return whether it opened; false goes on to [link].
     */
    fun launchDirectly(context: Context): Boolean = false

    /**
     * An image specific to this entry, such as an app icon or contact photo. When null,
     * search shows the category's icon in its place.
     */
    val icon: Drawable? get() = null

    /**
     * A short secondary line shown under [name] in search results, or null for none. Each
     * category fills it with its own information, already formatted for display, so the UI can
     * render it the same way for every kind of entry: a contact's phone number, a file's folder,
     * and so on.
     */
    val detail: String? get() = null
}
