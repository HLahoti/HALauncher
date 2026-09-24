package com.example.halauncher.util

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore

/** Builds the intents the home screen launches. All package names live here. */
object IntentFactory {

    private const val GOOGLE_CLOCK_PACKAGE = "com.google.android.deskclock"

    private const val SAMSUNG_CLOCK_PACKAGE = "com.sec.android.app.clockpackage"
    private const val SAMSUNG_CLOCK_ACTIVITY = "$SAMSUNG_CLOCK_PACKAGE.ClockPackage"

    // Samsung Clock's own tab selector: 0 = Alarm, 1 = World clock, 2 = Stopwatch, 3 = Timer.
    private const val SAMSUNG_EXTRA_SELECT_TAB = "clockpackage.select.tab"
    private const val SAMSUNG_TAB_ALARM = 0

    /** Clock apps we can open but have no known way to open at a specific tab. */
    private val OTHER_CLOCK_PACKAGES = listOf(
        "com.android.deskclock",
        "com.oneplus.deskclock",
        "com.coloros.alarmclock",
    )

    /**
     * Opens the device's clock app, on its Alarm tab where we know how:
     * 1. Samsung Clock: its main screen with the Alarm tab selected. Its
     *    [AlarmClock.ACTION_SHOW_ALARMS] handler only shows a widget alarm picker.
     * 2. Google Clock: [AlarmClock.ACTION_SHOW_ALARMS], which opens its Alarm tab.
     * 3. Another known clock app: its normal launch screen.
     * 4. Whatever app handles [AlarmClock.ACTION_SHOW_ALARMS].
     *
     * ACTION_SET_ALARM is avoided: it needs the SET_ALARM permission and creates an alarm.
     */
    fun alarmIntent(context: Context): Intent {
        val pm = context.packageManager

        val samsungClock = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(SAMSUNG_CLOCK_PACKAGE, SAMSUNG_CLOCK_ACTIVITY)
            .putExtra(SAMSUNG_EXTRA_SELECT_TAB, SAMSUNG_TAB_ALARM)
        if (samsungClock.resolveActivity(pm) != null) return samsungClock

        val googleClock = Intent(AlarmClock.ACTION_SHOW_ALARMS).setPackage(GOOGLE_CLOCK_PACKAGE)
        if (googleClock.resolveActivity(pm) != null) return googleClock

        OTHER_CLOCK_PACKAGES.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
            ?.let { return it }

        return Intent(AlarmClock.ACTION_SHOW_ALARMS)
    }

    /** Reminder / to-do apps, most preferred first. */
    private val REMINDER_PACKAGES = listOf(
        "com.samsung.android.app.reminder",
        "com.google.android.apps.tasks",
        "com.google.android.keep",
    )

    /**
     * Opens the first installed reminders app from [REMINDER_PACKAGES]. If none is installed,
     * returns an intent for the first that nothing can handle, so the launch shows the usual
     * "no app found" toast.
     */
    fun remindersIntent(context: Context): Intent =
        REMINDER_PACKAGES.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
            ?: launchAppIntent(context, REMINDER_PACKAGES.first())

    /** Opens the phone app's keypad, empty, ready to dial a number (nothing is called). */
    fun dialerIntent(): Intent = Intent(Intent.ACTION_DIAL)

    /** Opens the camera app, ready to take a photo (the app itself saves it, not us). */
    fun cameraIntent(): Intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)

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
