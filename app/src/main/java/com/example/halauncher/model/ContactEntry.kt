package com.example.halauncher.model

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

/**
 * A contact from the device's contacts database (the data behind the default contacts app).
 *
 * Opens the contact in whichever app handles contacts; if there is none, opens the phone
 * app with [phoneNumber] dialled (not called).
 */
data class ContactEntry(
    val contactId: Long,
    val lookupKey: String,
    override val name: String,
    val phoneNumber: String?,
) : SearchEntry {
    // The lookup key survives contact syncs and merges; the numeric id alone may not.
    override val id: String get() = "contact:$lookupKey"
    override val category: SearchCategory get() = SearchCategory.CONTACT

    override val link: Intent
        get() = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(contactId, lookupKey))

    override val fallbackLinks: List<Intent>
        get() = listOfNotNull(
            phoneNumber?.let { Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", it, null)) },
        )
}
