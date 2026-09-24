package com.example.halauncher.search.index

import android.net.Uri
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.ContactEntry
import com.example.halauncher.model.FileEntry
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
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

    override val category = SearchCategory.CONTACT

    override fun encode(entry: SearchEntry): String {
        val contact = entry as ContactEntry
        return JSONObject()
            .put(CONTACT_ID, contact.contactId)
            .put(LOOKUP_KEY, contact.lookupKey)
            .put(PHONE, contact.phoneNumber ?: JSONObject.NULL)
            .toString()
    }

    override fun decode(name: String, payload: String): SearchEntry {
        val json = JSONObject(payload)
        return ContactEntry(
            contactId = json.getLong(CONTACT_ID),
            lookupKey = json.getString(LOOKUP_KEY),
            name = name,
            phoneNumber = json.optStringOrNull(PHONE),
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

/** optString turns JSON null into the text "null"; this returns a real null instead. */
private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
