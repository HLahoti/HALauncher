package com.example.halauncher.data

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import com.example.halauncher.R
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.model.SettingEntry
import com.example.halauncher.model.ShortcutEntry
import com.example.halauncher.search.searchTerms

/**
 * Screens of the system Settings app, from a fixed catalog of the standard intent actions every
 * Android phone is meant to handle (plus a few long-standing hidden ones most do).
 *
 * Only screens this device will really open are returned. An action is kept when:
 * - an installed **system** app handles it, so a third-party app claiming the action isn't
 *   mistaken for the setting;
 * - that activity is exported and needs no permission the launcher lacks, so starting it can't
 *   fail with a SecurityException;
 * - the hardware or state it's about is there: no NFC screen on a phone without NFC, no
 *   developer options until they're turned on.
 *
 * A screen the Settings app also offers as a shortcut is listed as that shortcut instead (see
 * [SettingsDuplicates]).
 *
 * Names and keywords are the launcher's own strings, since Settings doesn't share its titles.
 * Needs no permission; the actions are declared under `<queries>` in the manifest so Android 11+
 * lets them be resolved.
 */
class SettingsProvider(private val context: Context) : SearchEntryProvider {

    override val category = SearchCategory.SETTING

    @WorkerThread
    override fun loadEntries(): List<SearchEntry> = loadSettings()

    /** [loadEntries], typed. */
    @WorkerThread
    fun loadSettings(): List<SettingEntry> {
        val detail = context.getString(R.string.category_setting_detail)
        return CATALOG.mapNotNull { screen ->
            if (!screen.isAvailable(context)) return@mapNotNull null
            screen.actions.firstNotNullOfOrNull { action -> systemHandler(action)?.let { action to it } }
                ?.let { (action, handler) ->
                    SettingEntry(
                        key = screen.key,
                        name = context.getString(screen.title),
                        action = action,
                        packageName = handler.packageName,
                        detail = detail,
                        keywords = context.getString(screen.keywords),
                    )
                }
        }
    }

    /** The system activity that would open [action], or null if none can be started from here. */
    private fun systemHandler(action: String): ActivityInfo? {
        val handlers: List<ResolveInfo> = try {
            context.packageManager.queryIntentActivities(Intent(action), PackageManager.MATCH_DEFAULT_ONLY)
        } catch (e: RuntimeException) {
            // The package manager died (the system is restarting).
            return null
        }
        return handlers.asSequence()
            .map { it.activityInfo }
            .firstOrNull { it.isSystem() && it.exported && it.enabled && canStart(it) }
    }

    private fun ActivityInfo.isSystem(): Boolean =
        applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    private fun canStart(activity: ActivityInfo): Boolean {
        val permission = activity.permission ?: return true
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * One Settings screen.
     *
     * @property key stable id, independent of language; changing it loses the screen's history.
     * @property actions the intent actions that open it, best first; the first this device handles
     * is used.
     * @property isAvailable whether the device has what the screen is about.
     */
    private class Screen(
        val key: String,
        @StringRes val title: Int,
        @StringRes val keywords: Int,
        val actions: List<String>,
        val isAvailable: (Context) -> Boolean = { true },
    ) {
        constructor(
            key: String,
            @StringRes title: Int,
            @StringRes keywords: Int,
            action: String,
            isAvailable: (Context) -> Boolean = { true },
        ) : this(key, title, keywords, listOf(action), isAvailable)
    }

    private companion object {
        // Hidden actions: not in the SDK, but handled by AOSP Settings and most phones' own.
        const val ACTION_NOTIFICATION_SETTINGS = "android.settings.NOTIFICATION_SETTINGS"
        const val ACTION_ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS"
        const val ACTION_TETHER_SETTINGS = "android.settings.TETHER_SETTINGS"

        fun hasFeature(vararg features: String): (Context) -> Boolean = { context ->
            features.any(context.packageManager::hasSystemFeature)
        }

        val hasTelephony = hasFeature(PackageManager.FEATURE_TELEPHONY)

        val developerOptionsOn: (Context) -> Boolean = { context ->
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        }

        /**
         * Every screen searched for, roughly in the order Settings lists them. Constants newer
         * than minSdk are only strings here; an older Android simply doesn't resolve them.
         */
        @SuppressLint("InlinedApi")
        val CATALOG = listOf(
            // Network and connections
            Screen("wifi", R.string.setting_wifi, R.string.setting_wifi_keywords, Settings.ACTION_WIFI_SETTINGS, hasFeature(PackageManager.FEATURE_WIFI)),
            Screen("network", R.string.setting_network, R.string.setting_network_keywords, Settings.ACTION_WIRELESS_SETTINGS),
            Screen("airplane_mode", R.string.setting_airplane_mode, R.string.setting_airplane_mode_keywords, Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            Screen("mobile_network", R.string.setting_mobile_network, R.string.setting_mobile_network_keywords, Settings.ACTION_DATA_ROAMING_SETTINGS, hasTelephony),
            Screen("network_operator", R.string.setting_network_operator, R.string.setting_network_operator_keywords, Settings.ACTION_NETWORK_OPERATOR_SETTINGS, hasTelephony),
            Screen("apn", R.string.setting_apn, R.string.setting_apn_keywords, Settings.ACTION_APN_SETTINGS, hasTelephony),
            Screen("data_usage", R.string.setting_data_usage, R.string.setting_data_usage_keywords, Settings.ACTION_DATA_USAGE_SETTINGS),
            Screen("hotspot", R.string.setting_hotspot, R.string.setting_hotspot_keywords, ACTION_TETHER_SETTINGS),
            Screen("vpn", R.string.setting_vpn, R.string.setting_vpn_keywords, Settings.ACTION_VPN_SETTINGS),
            Screen("bluetooth", R.string.setting_bluetooth, R.string.setting_bluetooth_keywords, Settings.ACTION_BLUETOOTH_SETTINGS, hasFeature(PackageManager.FEATURE_BLUETOOTH)),
            Screen("nfc", R.string.setting_nfc, R.string.setting_nfc_keywords, Settings.ACTION_NFC_SETTINGS, hasFeature(PackageManager.FEATURE_NFC)),
            Screen("contactless_payments", R.string.setting_contactless_payments, R.string.setting_contactless_payments_keywords, Settings.ACTION_NFC_PAYMENT_SETTINGS, hasFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)),
            Screen("cast", R.string.setting_cast, R.string.setting_cast_keywords, Settings.ACTION_CAST_SETTINGS),

            // Display, sound and notifications
            Screen("display", R.string.setting_display, R.string.setting_display_keywords, Settings.ACTION_DISPLAY_SETTINGS),
            Screen("night_light", R.string.setting_night_light, R.string.setting_night_light_keywords, Settings.ACTION_NIGHT_DISPLAY_SETTINGS),
            Screen("screen_saver", R.string.setting_screen_saver, R.string.setting_screen_saver_keywords, Settings.ACTION_DREAM_SETTINGS),
            Screen("sound", R.string.setting_sound, R.string.setting_sound_keywords, Settings.ACTION_SOUND_SETTINGS),
            Screen("volume", R.string.setting_volume, R.string.setting_volume_keywords, Settings.Panel.ACTION_VOLUME),
            Screen("notifications", R.string.setting_notifications, R.string.setting_notifications_keywords, ACTION_NOTIFICATION_SETTINGS),
            Screen("app_notifications", R.string.setting_app_notifications, R.string.setting_app_notifications_keywords, Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS),
            Screen("do_not_disturb", R.string.setting_do_not_disturb, R.string.setting_do_not_disturb_keywords, ACTION_ZEN_MODE_SETTINGS),

            // Apps
            Screen(
                "apps", R.string.setting_apps, R.string.setting_apps_keywords,
                listOf(Settings.ACTION_APPLICATION_SETTINGS, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
            ),
            Screen("default_apps", R.string.setting_default_apps, R.string.setting_default_apps_keywords, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Screen("home_app", R.string.setting_home_app, R.string.setting_home_app_keywords, Settings.ACTION_HOME_SETTINGS),
            Screen("usage_access", R.string.setting_usage_access, R.string.setting_usage_access_keywords, Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Screen("notification_access", R.string.setting_notification_access, R.string.setting_notification_access_keywords, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            Screen("dnd_access", R.string.setting_dnd_access, R.string.setting_dnd_access_keywords, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            Screen("overlay", R.string.setting_overlay, R.string.setting_overlay_keywords, Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Screen("write_settings", R.string.setting_write_settings, R.string.setting_write_settings_keywords, Settings.ACTION_MANAGE_WRITE_SETTINGS),
            Screen("battery_optimisation", R.string.setting_battery_optimisation, R.string.setting_battery_optimisation_keywords, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Screen("unknown_apps", R.string.setting_unknown_apps, R.string.setting_unknown_apps_keywords, Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            Screen("all_files_access", R.string.setting_all_files_access, R.string.setting_all_files_access_keywords, Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),

            // Battery and storage
            Screen("battery", R.string.setting_battery, R.string.setting_battery_keywords, Intent.ACTION_POWER_USAGE_SUMMARY),
            Screen("battery_saver", R.string.setting_battery_saver, R.string.setting_battery_saver_keywords, Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Screen("storage", R.string.setting_storage, R.string.setting_storage_keywords, Settings.ACTION_INTERNAL_STORAGE_SETTINGS),

            // Security, privacy and accounts
            Screen("security", R.string.setting_security, R.string.setting_security_keywords, Settings.ACTION_SECURITY_SETTINGS),
            Screen("screen_lock", R.string.setting_screen_lock, R.string.setting_screen_lock_keywords, DevicePolicyManager.ACTION_SET_NEW_PASSWORD),
            Screen(
                "biometrics", R.string.setting_biometrics, R.string.setting_biometrics_keywords,
                Settings.ACTION_BIOMETRIC_ENROLL,
                hasFeature(PackageManager.FEATURE_FINGERPRINT, PackageManager.FEATURE_FACE, PackageManager.FEATURE_IRIS),
            ),
            Screen("privacy", R.string.setting_privacy, R.string.setting_privacy_keywords, Settings.ACTION_PRIVACY_SETTINGS),
            Screen("location", R.string.setting_location, R.string.setting_location_keywords, Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            Screen("accounts", R.string.setting_accounts, R.string.setting_accounts_keywords, Settings.ACTION_SYNC_SETTINGS),
            Screen("add_account", R.string.setting_add_account, R.string.setting_add_account_keywords, Settings.ACTION_ADD_ACCOUNT),

            // Accessibility
            Screen("accessibility", R.string.setting_accessibility, R.string.setting_accessibility_keywords, Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Screen("captions", R.string.setting_captions, R.string.setting_captions_keywords, Settings.ACTION_CAPTIONING_SETTINGS),

            // System
            Screen("languages", R.string.setting_languages, R.string.setting_languages_keywords, Settings.ACTION_LOCALE_SETTINGS),
            Screen("regional_preferences", R.string.setting_regional_preferences, R.string.setting_regional_preferences_keywords, Settings.ACTION_REGIONAL_PREFERENCES_SETTINGS),
            Screen("keyboards", R.string.setting_keyboards, R.string.setting_keyboards_keywords, Settings.ACTION_INPUT_METHOD_SETTINGS),
            Screen("physical_keyboard", R.string.setting_physical_keyboard, R.string.setting_physical_keyboard_keywords, Settings.ACTION_HARD_KEYBOARD_SETTINGS),
            Screen("dictionary", R.string.setting_dictionary, R.string.setting_dictionary_keywords, Settings.ACTION_USER_DICTIONARY_SETTINGS),
            Screen("voice_input", R.string.setting_voice_input, R.string.setting_voice_input_keywords, Settings.ACTION_VOICE_INPUT_SETTINGS),
            Screen("date_time", R.string.setting_date_time, R.string.setting_date_time_keywords, Settings.ACTION_DATE_SETTINGS),
            Screen("printing", R.string.setting_printing, R.string.setting_printing_keywords, Settings.ACTION_PRINT_SETTINGS, hasFeature(PackageManager.FEATURE_PRINTING)),
            Screen("developer_options", R.string.setting_developer_options, R.string.setting_developer_options_keywords, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, developerOptionsOn),
            Screen("about", R.string.setting_about, R.string.setting_about_keywords, Settings.ACTION_DEVICE_INFO_SETTINGS),
            Screen("regulatory_labels", R.string.setting_regulatory_labels, R.string.setting_regulatory_labels_keywords, Settings.ACTION_SHOW_REGULATORY_INFO),
        )
    }
}

/**
 * Settings screens that the Settings app also offers as long-press shortcuts ("Wi-Fi", "Battery"
 * on a Pixel) would show up twice. The shortcut is kept, since it has the Settings app's own icon
 * for it, and the catalog's entry is left out, handing the shortcut its keywords so the same
 * words still find it. They're the same screen when the app that handles the catalog's screen
 * offers a shortcut with the same name (compared word by word, so "Wi‑Fi" and "Wi-Fi" match).
 */
object SettingsDuplicates {

    data class Merged(val settings: List<SettingEntry>, val shortcuts: List<ShortcutEntry>)

    fun merge(settings: List<SettingEntry>, shortcuts: List<ShortcutEntry>): Merged {
        val byKey = settings.associateBy { key(it.packageName, it.name) }
        val covered = HashSet<String>()
        val merged = shortcuts.map { shortcut ->
            val setting = byKey[key(shortcut.packageName, shortcut.name)] ?: return@map shortcut
            covered += setting.id
            shortcut.copy(keywords = setting.keywords)
        }
        return Merged(settings.filterNot { it.id in covered }, merged)
    }

    private fun key(packageName: String, name: String) = packageName + "/" + searchTerms(name).joinToString(" ")
}
