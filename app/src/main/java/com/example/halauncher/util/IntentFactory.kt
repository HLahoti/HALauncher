package com.example.halauncher.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore

/** Builds the intents the home screen launches. All package names live here. */
object IntentFactory {

    private const val SAMSUNG_CLOCK_PACKAGE = "com.sec.android.app.clockpackage"
    private const val SAMSUNG_CLOCK_ACTIVITY = "$SAMSUNG_CLOCK_PACKAGE.ClockPackage"

    // Samsung Clock's own tab selector: 0 = Alarm, 1 = World clock, 2 = Stopwatch, 3 = Timer.
    private const val SAMSUNG_EXTRA_SELECT_TAB = "clockpackage.select.tab"
    private const val SAMSUNG_TAB_ALARM = 0

    /** Standard actions only a clock app handles, used to find it whatever its package name. */
    private val CLOCK_ACTIONS = listOf(
        AlarmClock.ACTION_SHOW_ALARMS,
        AlarmClock.ACTION_SET_ALARM,
        AlarmClock.ACTION_SHOW_TIMERS,
        AlarmClock.ACTION_SET_TIMER,
    )

    /**
     * Intents that open the device's clock app, best first; the launch uses the first that works:
     * 1. The Alarm tab. On Samsung Clock, its main screen with the Alarm tab selected (its
     *    [AlarmClock.ACTION_SHOW_ALARMS] handler only shows a widget alarm picker). Elsewhere,
     *    [AlarmClock.ACTION_SHOW_ALARMS], which opens the Alarm tab of Google Clock, AOSP Clock
     *    and the clocks built on it (OnePlus, Oppo, …). Those only accept it from apps holding the
     *    SET_ALARM permission, which the manifest declares.
     * 2. The main screen of whichever app handles the standard alarm or timer actions, a
     *    preinstalled one first. Opening an app's main screen needs no permission, so this works
     *    with any clock app, whatever it is called.
     */
    fun alarmIntents(context: Context): List<Intent> {
        val pm = context.packageManager

        val samsungClock = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(SAMSUNG_CLOCK_PACKAGE, SAMSUNG_CLOCK_ACTIVITY)
            .putExtra(SAMSUNG_EXTRA_SELECT_TAB, SAMSUNG_TAB_ALARM)
        // Not Intent.resolveActivity: given an explicit component it returns it unchecked, so this
        // would pass on every phone and open nothing where Samsung Clock isn't installed.
        val alarmScreen = if (pm.queryActivities(samsungClock).isNotEmpty()) {
            samsungClock
        } else {
            Intent(AlarmClock.ACTION_SHOW_ALARMS)
        }
        return listOfNotNull(alarmScreen, clockAppLaunchIntent(pm))
    }

    /** The launch intent of the app handling [CLOCK_ACTIONS], preinstalled apps first. */
    private fun clockAppLaunchIntent(pm: PackageManager): Intent? =
        CLOCK_ACTIONS.asSequence()
            .flatMap { pm.queryActivities(Intent(it)) }
            .map { it.activityInfo.applicationInfo }
            .sortedByDescending { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            .map { it.packageName }
            .distinct()
            .firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }

    private fun PackageManager.queryActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            queryIntentActivities(intent, 0)
        }

    /** Opens the phone app's keypad, empty, ready to dial a number (nothing is called). */
    fun dialerIntent(): Intent = Intent(Intent.ACTION_DIAL)

    /** Opens the camera app, ready to take a photo (the app itself saves it, not us). */
    fun cameraIntent(): Intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)

    /** Opens the device's default gallery / photos app. */
    fun galleryIntent(): Intent = appCategoryIntent(Intent.CATEGORY_APP_GALLERY)

    /** Opens the device's default messaging (SMS) app. */
    fun messagesIntent(): Intent = appCategoryIntent(Intent.CATEGORY_APP_MESSAGING)

    /** Opens the device's default web browser. */
    fun internetIntent(): Intent = appCategoryIntent(Intent.CATEGORY_APP_BROWSER)

    /**
     * A MAIN intent for one of Android's app categories (browser, gallery, messaging, …). The
     * system routes it to whichever app the user has set as their default for that category, so the
     * button follows the user's own choice without the launcher having to ask.
     */
    private fun appCategoryIntent(category: String): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(category)

    /** Opens the calendar app at the current moment. */
    fun calendarIntent(nowMillis: Long = System.currentTimeMillis()): Intent {
        val uri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(nowMillis.toString())
            .build()
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /**
     * The app's own launch intent. Falls back to a MAIN/LAUNCHER intent scoped to the
     * package, which simply fails to resolve (and shows a toast) if the app is gone.
     */
    fun launchAppIntent(context: Context, packageName: String): Intent =
        context.packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(packageName)
}
