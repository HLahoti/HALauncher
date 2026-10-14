package com.example.halauncher.model

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

/**
 * A contact from the device's contacts database (the data behind the default contacts app).
 *
 * Opens the contact in whichever app handles contacts; if there is none, opens the phone
 * app with [phoneNumber] dialled (not called).
 *
 * @property photoUri the contact's photo, or null if it has none. Search loads it only when the
 * contact is shown (see ContactPhotoCache), so it isn't [icon].
 * @property photoVersion when the contact last changed while it had a photo (0 without one), so a
 * replaced photo isn't mistaken for the cached one.
 */
data class ContactEntry(
    val contactId: Long,
    val lookupKey: String,
    override val name: String,
    val phoneNumber: String?,
    val photoUri: Uri? = null,
    val photoVersion: Long = 0L,
) : SearchEntry {
    // The lookup key survives contact syncs and merges; the numeric id alone may not.
    override val id: String get() = idFor(lookupKey)
    override val category: SearchCategory get() = SearchCategory.CONTACT

    override val detail: String? get() = phoneNumber

    override val link: Intent
        get() = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(contactId, lookupKey))

    override val fallbackLinks: List<Intent>
        get() = listOfNotNull(
            phoneNumber?.let { Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", it, null)) },
        )

    companion object {
        /** The [id] of the contact with this lookup key. */
        fun idFor(lookupKey: String): String = "contact:$lookupKey"
    }
}
