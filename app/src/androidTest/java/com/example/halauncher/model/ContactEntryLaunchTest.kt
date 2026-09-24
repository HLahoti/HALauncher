package com.example.halauncher.model

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.halauncher.ui.components.firstHandledIntent
import com.example.halauncher.ui.components.launchSafely
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks, against the real apps on the device, how a contact entry opens. Uses a made-up
 * contact and a fictional 555 number rather than anything from the device's contacts.
 */
@RunWith(AndroidJUnit4::class)
class ContactEntryLaunchTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val contact = ContactEntry(
        contactId = 1L,
        lookupKey = "test-lookup-key",
        name = "Test Contact",
        phoneNumber = "5550100",
    )

    @Test
    fun contactLinkOpensInTheContactsApp() {
        assertSameIntent(contact.link, context.firstHandledIntent(listOf(contact.link) + contact.fallbackLinks))
    }

    @Test
    fun fallbackIsTheDialerWithTheNumber() {
        val dial = contact.fallbackLinks.single()
        assertEquals(Intent.ACTION_DIAL, dial.action)
        assertEquals("tel:5550100", dial.data.toString())
        assertNotNull("no dialer can handle the fallback", context.firstHandledIntent(listOf(dial)))
    }

    @Test
    fun whenNoAppCanOpenTheContactTheDialerOpensInstead() {
        // Stands in for a phone without a contacts app: nothing handles this link.
        val unopenable = Intent("com.example.halauncher.test.NO_APP_HANDLES_THIS")
        val intents = listOf(unopenable) + contact.fallbackLinks
        assertSameIntent(contact.fallbackLinks.single(), context.firstHandledIntent(intents))
        assertTrue(context.launchSafely(intents, contact.name))
    }

    @Test
    fun contactWithoutNumberHasNoFallback() {
        assertTrue(contact.copy(phoneNumber = null).fallbackLinks.isEmpty())
    }

    /** Intent has no value equality; compare action, data, type, component and categories. */
    private fun assertSameIntent(expected: Intent, actual: Intent?) {
        assertTrue("expected $expected but was $actual", actual != null && expected.filterEquals(actual))
    }
}
