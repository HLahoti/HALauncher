package com.example.halauncher.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.halauncher.R
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.ContactEntry
import com.example.halauncher.model.FileEntry
import com.example.halauncher.model.FolderEntry
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.model.ShortcutEntry
import com.example.halauncher.ui.components.GlassDropdownMenu
import com.example.halauncher.ui.components.GlassMenuItem
import com.example.halauncher.ui.components.GlassMenuTitle
import com.example.halauncher.ui.components.launchSafely
import java.io.File

/**
 * What search's long-press menus need from the rest of the launcher. With [enabled] off (the
 * default, and while picking an app for a button) results have no menu.
 *
 * @property pinKeys what's pinned to the sphere (PinnedAppsStore.keyOf), so an app's menu offers pin
 * or unpin, and a pinned shortcut's offers unpin.
 * @property canPinMore whether there's room on the sphere for another pin.
 * @property locked whether settings are locked: pinning is left out then.
 * @property recentIds the entries that count as recents, which offer "Remove from recents".
 */
data class SearchEntryActions(
    val enabled: Boolean = false,
    val pinKeys: Set<String> = emptySet(),
    val canPinMore: Boolean = false,
    val locked: Boolean = false,
    val recentIds: Set<String> = emptySet(),
    val onTogglePinned: (SearchEntry) -> Unit = {},
    val onForgetRecent: (SearchEntry) -> Unit = {},
)

/** One line of a result's menu. */
private class EntryMenuItem(@StringRes val label: Int, val run: () -> Unit)

/**
 * The long-press menu for [entry], anchored to its row while [expanded]: a greyed first line
 * naming it, then what can be done with it. Choosing a line closes the menu. [onOpened] is called
 * when an action opens the entry itself (calling or messaging a contact, opening a file with a
 * chosen app), so it's recorded and search closes, as a tap would.
 */
@Composable
fun SearchEntryMenu(
    entry: SearchEntry,
    expanded: Boolean,
    actions: SearchEntryActions,
    onDismiss: () -> Unit,
    onOpened: (SearchEntry) -> Unit,
) {
    val context = LocalContext.current
    // Built once per opening.
    val items = remember(expanded, entry, actions) {
        if (expanded) entryMenuItems(context, entry, actions, onOpened) else emptyList()
    }
    GlassDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        GlassMenuTitle(entry.name)
        items.forEach { item ->
            GlassMenuItem(stringResource(item.label)) {
                onDismiss()
                item.run()
            }
        }
    }
}

/**
 * Whether [entry] has anything in its menu, so long-pressing it does something. Asked for every
 * row shown, so it stays cheap: no lookups, unlike building the menu.
 */
fun hasEntryMenu(entry: SearchEntry, actions: SearchEntryActions): Boolean =
    actions.enabled && when (entry) {
        is AppInfo, is FileEntry, is FolderEntry -> true
        is ContactEntry -> entry.phoneNumber != null || entry.id in actions.recentIds
        is ShortcutEntry -> entry.id in actions.pinKeys || entry.id in actions.recentIds
        else -> entry.id in actions.recentIds
    }

/**
 * The menu lines for [entry], most-used first, with "Remove from recents" last. At most three
 * per kind of entry, plus that one, so the menu stays short.
 */
private fun entryMenuItems(
    context: Context,
    entry: SearchEntry,
    actions: SearchEntryActions,
    onOpened: (SearchEntry) -> Unit,
): List<EntryMenuItem> = buildList {
    if (!actions.enabled) return@buildList
    when (entry) {
        is AppInfo -> {
            add(EntryMenuItem(R.string.action_app_info) { context.openAppInfo(entry) })
            if (!actions.locked) {
                val pinned = entry.key in actions.pinKeys
                if (pinned || actions.canPinMore) {
                    add(EntryMenuItem(if (pinned) R.string.action_unpin else R.string.action_pin) { actions.onTogglePinned(entry) })
                }
            }
        }
        is ContactEntry -> entry.phoneNumber?.let { number ->
            add(EntryMenuItem(R.string.action_call) { context.openFor(entry, onOpened, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) })
            add(EntryMenuItem(R.string.action_message) { context.openFor(entry, onOpened, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))) })
            add(EntryMenuItem(R.string.action_copy_number) { context.copy(entry.name, number, R.string.number_copied, number) })
        }
        is FileEntry -> {
            // An APK only installs from a file manager (see FileEntry.isApk), so it isn't offered
            // to other apps to open.
            if (!entry.isApk) {
                add(EntryMenuItem(R.string.action_open_with) { context.openFor(entry, onOpened, openWithIntent(context, entry)) })
            }
            add(EntryMenuItem(R.string.action_share) { context.launchSafely(shareIntent(context, entry), entry.name) })
            entry.folder?.let { folder ->
                val path = storagePath(folder + entry.name)
                add(EntryMenuItem(R.string.action_copy_path) { context.copy(entry.name, path, R.string.path_copied, entry.name) })
            }
        }
        is FolderEntry -> {
            val path = storagePath(entry.path)
            add(EntryMenuItem(R.string.action_copy_path) { context.copy(entry.name, path, R.string.path_copied, entry.name) })
        }
        // A shortcut added through "Add to home screen" can come off the sphere from here too.
        // Others, and Settings screens, have nothing worth a menu of their own.
        is ShortcutEntry -> if (!actions.locked && entry.id in actions.pinKeys) {
            add(EntryMenuItem(R.string.action_unpin) { actions.onTogglePinned(entry) })
        }
        else -> Unit
    }
    if (entry.id in actions.recentIds) {
        add(EntryMenuItem(R.string.action_remove_from_recents) { actions.onForgetRecent(entry) })
    }
}

/** Starts [intent] on [entry]'s behalf; if it opens, it counts as opening [entry]. */
private fun Context.openFor(entry: SearchEntry, onOpened: (SearchEntry) -> Unit, intent: Intent) {
    if (launchSafely(intent, entry.name)) onOpened(entry)
}

/** [app]'s App info page: the work profile's for a work app. */
private fun Context.openAppInfo(app: AppInfo) {
    val profile = app.workProfile
    if (profile == null) {
        launchSafely(appInfoIntent(app.packageName), app.name)
    } else if (!AppInfo.openDetailsInProfile(this, app.packageName, profile)) {
        Toast.makeText(this, getString(R.string.launch_failed, app.name), Toast.LENGTH_SHORT).show()
    }
}

private fun appInfoIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

/** Asks which app to open [file] with, every time (unlike tapping it, which uses the default). */
private fun openWithIntent(context: Context, file: FileEntry): Intent {
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(file.uri, file.mimeType ?: ANY_TYPE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return Intent.createChooser(view, context.getString(R.string.action_open_with))
}

/** The share sheet for [file], with read access to it granted to whichever app is chosen. */
private fun shareIntent(context: Context, file: FileEntry): Intent {
    val send = Intent(Intent.ACTION_SEND)
        .setType(file.mimeType ?: ANY_TYPE)
        .putExtra(Intent.EXTRA_STREAM, file.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The chooser passes the grant on only for URIs in the clip data.
    send.clipData = ClipData.newRawUri(file.name, file.uri)
    return Intent.createChooser(send, context.getString(R.string.action_share))
}

/** The full path of [relative] (a path from the storage root) on internal storage. */
@Suppress("DEPRECATION") // Still the path of internal storage, and what file managers show.
private fun storagePath(relative: String): String =
    File(Environment.getExternalStorageDirectory(), relative.trimEnd('/')).absolutePath

/**
 * Copies [text] to the clipboard under [label]. Android 13+ confirms it on screen itself; before
 * that, a toast reading [confirmation] (formatted with [arg]) does.
 */
private fun Context.copy(label: String, text: String, @StringRes confirmation: Int, arg: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text)) ?: return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, getString(confirmation, arg), Toast.LENGTH_SHORT).show()
    }
}

private const val ANY_TYPE = "*/*"
