package com.example.halauncher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import com.example.halauncher.model.ContactEntry
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry

/**
 * Contacts from the device's shared contacts database, which every contacts app (Samsung
 * Contacts, Google Contacts, …) reads and writes. Returns nothing until the user grants
 * [Manifest.permission.READ_CONTACTS].
 */
class ContactProvider(private val context: Context) : SearchEntryProvider {

    override val category = SearchCategory.CONTACT

    /** The contacts' photos, loaded as search shows them. */
    val photos = ContactPhotoCache(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    @WorkerThread
    override fun loadEntries(): List<SearchEntry> {
        if (!hasPermission()) return emptyList()
        return try {
            val numbers = loadBestPhoneNumbers()
            loadContacts()
                .map { contact -> contact.copy(phoneNumber = numbers[contact.contactId]) }
                .also(photos::retainOnly)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the query.
            emptyList()
        }
    }

    private fun loadContacts(): List<ContactEntry> {
        val projection = arrayOf(
            Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY,
            Contacts.PHOTO_URI, Contacts.CONTACT_LAST_UPDATED_TIMESTAMP,
        )
        val contacts = mutableListOf<ContactEntry>()
        context.contentResolver.query(Contacts.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Contacts._ID)
            val keyCol = cursor.getColumnIndexOrThrow(Contacts.LOOKUP_KEY)
            val nameCol = cursor.getColumnIndexOrThrow(Contacts.DISPLAY_NAME_PRIMARY)
            val photoCol = cursor.getColumnIndexOrThrow(Contacts.PHOTO_URI)
            val updatedCol = cursor.getColumnIndexOrThrow(Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol)?.trim()
                val lookupKey = cursor.getString(keyCol)
                if (name.isNullOrEmpty() || lookupKey == null) continue
                // Only the URI is read here; the photo itself loads when search shows the contact.
                val photoUri = cursor.getString(photoCol)?.let(Uri::parse)
                contacts += ContactEntry(
                    contactId = cursor.getLong(idCol),
                    lookupKey = lookupKey,
                    name = name,
                    phoneNumber = null,
                    photoUri = photoUri,
                    // Only matters with a photo; left 0 otherwise so other edits don't change the entry.
                    photoVersion = if (photoUri != null) cursor.getLong(updatedCol) else 0L,
                )
            }
        }
        return contacts
    }

    /** One number per contact: the one marked default, else primary, else the first. */
    private fun loadBestPhoneNumbers(): Map<Long, String> {
        val projection = arrayOf(Phone.CONTACT_ID, Phone.NUMBER)
        val sortOrder = "${Phone.IS_SUPER_PRIMARY} DESC, ${Phone.IS_PRIMARY} DESC"
        val numbers = mutableMapOf<Long, String>()
        context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID)
            val numberCol = cursor.getColumnIndexOrThrow(Phone.NUMBER)
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberCol)?.takeIf { it.isNotBlank() } ?: continue
                // Rows come best-first, so keep the first number seen for each contact.
                val contactId = cursor.getLong(idCol)
                if (contactId !in numbers) numbers[contactId] = number
            }
        }
        return numbers
    }
}
