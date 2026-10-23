package com.example.halauncher.search.index

import android.net.Uri
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.ContactEntry
import com.example.halauncher.model.FileEntry
import com.example.halauncher.model.FolderEntry
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.model.SettingEntry
import com.example.halauncher.model.ShortcutEntry
import org.json.JSONObject

/**
 * Converts one category's entries to and from the payload string stored beside their name
 * in the disk index. Each category needs one (see [SearchEntryCodecs]).
 */
interface SearchEntryCodec {
    val category: SearchCategory

    /** Everything except the name needed to rebuild [entry]. */
    fun encode(entry: SearchEntry): String

    /** Rebuilds the entry, or null if it can't be (e.g. the app has since been uninstalled). */
    fun decode(name: String, payload: String): SearchEntry?
}

/** One codec per category; construction fails if a category has none. */
class SearchEntryCodecs(codecs: List<SearchEntryCodec>) {
    private val byCategory = codecs.associateBy { it.category }

    init {
        val missing = SearchCategory.entries - byCategory.keys
        require(missing.isEmpty()) { "No SearchEntryCodec for $missing" }
    }

    fun forCategory(category: SearchCategory): SearchEntryCodec = byCategory.getValue(category)
}

/**
 * Apps are stored by package name only. Icons and launch intents aren't serialisable, so
 * decoding looks the app up among those loaded this session via [findApp].
 */
class AppEntryCodec(private val findApp: (packageName: String) -> AppInfo?) : SearchEntryCodec {
    override val category = SearchCategory.APP
    override fun encode(entry: SearchEntry): String = (entry as AppInfo).packageName
    override fun decode(name: String, payload: String): SearchEntry? = findApp(payload)
}

object ContactEntryCodec : SearchEntryCodec {
    private const val CONTACT_ID = "id"
    private const val LOOKUP_KEY = "lookup"
    private const val PHONE = "phone"
    private const val PHOTO = "photo"
    private const val PHOTO_VERSION = "photoVersion"

    override val category = SearchCategory.CONTACT

    override fun encode(entry: SearchEntry): String {
        val contact = entry as ContactEntry
        return JSONObject()
            .put(CONTACT_ID, contact.contactId)
            .put(LOOKUP_KEY, contact.lookupKey)
            .put(PHONE, contact.phoneNumber ?: JSONObject.NULL)
            .put(PHOTO, contact.photoUri?.toString() ?: JSONObject.NULL)
            .put(PHOTO_VERSION, contact.photoVersion)
            .toString()
    }

    override fun decode(name: String, payload: String): SearchEntry {
        val json = JSONObject(payload)
        return ContactEntry(
            contactId = json.getLong(CONTACT_ID),
            lookupKey = json.getString(LOOKUP_KEY),
            name = name,
            phoneNumber = json.optStringOrNull(PHONE),
            // Absent in entries indexed before photos were stored; the next sync adds them.
            photoUri = if (json.has(PHOTO)) json.optStringOrNull(PHOTO)?.let(Uri::parse) else null,
            photoVersion = json.optLong(PHOTO_VERSION),
        )
    }
}

object FileEntryCodec : SearchEntryCodec {
    private const val MEDIA_ID = "id"
    private const val URI = "uri"
    private const val MIME = "mime"
    private const val SIZE = "size"
    private const val MODIFIED = "modified"
    private const val FOLDER = "folder"

    override val category = SearchCategory.FILE

    override fun encode(entry: SearchEntry): String {
        val file = entry as FileEntry
        return JSONObject()
            .put(MEDIA_ID, file.mediaId)
            .put(URI, file.uri.toString())
            .put(MIME, file.mimeType ?: JSONObject.NULL)
            .put(SIZE, file.sizeBytes)
            .put(MODIFIED, file.modifiedMillis)
            .put(FOLDER, file.folder ?: JSONObject.NULL)
            .toString()
    }

    override fun decode(name: String, payload: String): SearchEntry {
        val json = JSONObject(payload)
        return FileEntry(
            mediaId = json.getLong(MEDIA_ID),
            name = name,
            uri = Uri.parse(json.getString(URI)),
            mimeType = json.optStringOrNull(MIME),
            sizeBytes = json.getLong(SIZE),
            modifiedMillis = json.getLong(MODIFIED),
            folder = json.optStringOrNull(FOLDER),
        )
    }
}

/** Folders are stored by path; nothing else is needed to rebuild one. */
object FolderEntryCodec : SearchEntryCodec {
    private const val PATH = "path"

    override val category = SearchCategory.FOLDER

    override fun encode(entry: SearchEntry): String =
        JSONObject().put(PATH, (entry as FolderEntry).path).toString()

    override fun decode(name: String, payload: String): SearchEntry =
        FolderEntry(path = JSONObject(payload).getString(PATH), name = name)
}

/**
 * Shortcuts are stored by app and shortcut id. The app's name and launch intent come from the
 * apps loaded this session via [findApp], so a shortcut whose app is gone isn't rebuilt.
 */
class ShortcutEntryCodec(private val findApp: (packageName: String) -> AppInfo?) : SearchEntryCodec {
    override val category = SearchCategory.SHORTCUT

    override fun encode(entry: SearchEntry): String {
        val shortcut = entry as ShortcutEntry
        return JSONObject()
            .put(PACKAGE, shortcut.packageName)
            .put(SHORTCUT_ID, shortcut.shortcutId)
            .put(ICON_VERSION, shortcut.iconVersion)
            .toString()
    }

    override fun decode(name: String, payload: String): SearchEntry? {
        val json = JSONObject(payload)
        val app = findApp(json.getString(PACKAGE)) ?: return null
        return ShortcutEntry(
            packageName = app.packageName,
            shortcutId = json.getString(SHORTCUT_ID),
            name = name,
            appName = app.name,
            link = app.link,
            iconVersion = json.optLong(ICON_VERSION),
        )
    }

    private companion object {
        const val PACKAGE = "package"
        const val SHORTCUT_ID = "id"
        const val ICON_VERSION = "iconVersion"
    }
}

/**
 * Settings screens are stored by catalog key, action and handling app. Their keywords live only
 * in the index's keywords column, so a decoded entry has none.
 */
object SettingEntryCodec : SearchEntryCodec {
    private const val KEY = "key"
    private const val ACTION = "action"
    private const val PACKAGE = "package"
    private const val DETAIL = "detail"

    override val category = SearchCategory.SETTING

    override fun encode(entry: SearchEntry): String {
        val setting = entry as SettingEntry
        return JSONObject()
            .put(KEY, setting.key)
            .put(ACTION, setting.action)
            .put(PACKAGE, setting.packageName)
            .put(DETAIL, setting.detail ?: JSONObject.NULL)
            .toString()
    }

    override fun decode(name: String, payload: String): SearchEntry {
        val json = JSONObject(payload)
        return SettingEntry(
            key = json.getString(KEY),
            name = name,
            action = json.getString(ACTION),
            packageName = json.getString(PACKAGE),
            detail = json.optStringOrNull(DETAIL),
        )
    }
}

/** optString turns JSON null into the text "null"; this returns a real null instead. */
private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
