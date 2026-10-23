package com.example.halauncher.data

import android.content.Intent
import com.example.halauncher.model.SettingEntry
import com.example.halauncher.model.ShortcutEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsDuplicatesTest {

    private fun setting(name: String, keywords: String? = null, packageName: String = SETTINGS) =
        SettingEntry(key = name, name = name, action = "action", packageName = packageName, detail = null, keywords = keywords)

    private fun shortcut(name: String, packageName: String = SETTINGS) =
        ShortcutEntry(packageName = packageName, shortcutId = name, name = name, appName = "Settings", link = Intent())

    @Test
    fun aScreenTheSettingsAppOffersAsAShortcutIsListedAsTheShortcut() {
        // The catalog's name has a non-breaking hyphen; the shortcut's a plain one.
        val wifi = setting("Wi‑Fi", keywords = "wireless internet")
        val display = setting("Display")
        val merged = SettingsDuplicates.merge(listOf(wifi, display), listOf(shortcut("Wi-Fi")))

        assertEquals(listOf(display), merged.settings)
        val kept = merged.shortcuts.single()
        assertEquals("Wi-Fi", kept.name)
        assertEquals("wireless internet", kept.keywords)
    }

    @Test
    fun anotherAppsShortcutWithTheSameNameChangesNothing() {
        val wifi = setting("Wi‑Fi", keywords = "wireless")
        val other = shortcut("Wi-Fi", packageName = "com.example.wifi")
        val merged = SettingsDuplicates.merge(listOf(wifi), listOf(other))

        assertEquals(listOf(wifi), merged.settings)
        assertNull(merged.shortcuts.single().keywords)
    }

    private companion object {
        const val SETTINGS = "com.android.settings"
    }
}
