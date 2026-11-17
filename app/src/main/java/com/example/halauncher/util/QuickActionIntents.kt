package com.example.halauncher.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import com.example.halauncher.model.EventEntry

/**
 * A [QuickAction] some installed app can do, with the name of the app that will (null when the user
 * hasn't chosen a default and Android will ask), so its row can say where it's going.
 */
data class QuickActionTarget(val action: QuickAction, val appName: String?)

/** What starts [this] in the app that does it. Nothing happens until it's started. */
val QuickAction.intent: Intent
    get() = when (this) {
        // The clock app shows the timer or alarm it set, so it's clear it worked.
        is QuickAction.Timer -> Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        is QuickAction.Alarm -> Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        is QuickAction.Call -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
        is QuickAction.Message -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
        is QuickAction.Email -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null))
        is QuickAction.Navigate -> Intent(Intent.ACTION_VIEW, ("geo:0,0?q=" + Uri.encode(place)).toUri())
        is QuickAction.OpenLink -> Intent(Intent.ACTION_VIEW, url.toUri())
        is QuickAction.NewEvent -> Intent(Intent.ACTION_INSERT)
            .setDataAndType(CalendarContract.Events.CONTENT_URI, EventEntry.EVENTS_TYPE)
            .putExtra(CalendarContract.Events.TITLE, title)
    }

/**
 * [actions] that some installed app handles, each with the app that will. Asks the package
 * manager, so it's kept off the main thread.
 */
@WorkerThread
fun resolveQuickActions(context: Context, actions: List<QuickAction>): List<QuickActionTarget> {
    if (actions.isEmpty()) return emptyList()
    val pm = context.packageManager
    return actions.mapNotNull { action ->
        val intent = action.intent
        // No handler at all: leave the action out rather than offer something that can't run.
        if (intent.resolveActivity(pm) == null) return@mapNotNull null
        val default = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        // With no default chosen, Android resolves to its own chooser; there's no one app to name then.
        val appName = default?.activityInfo
            ?.takeIf { it.packageName != ANDROID_PACKAGE }
            ?.applicationInfo?.loadLabel(pm)?.toString()
        QuickActionTarget(action, appName)
    }
}

private const val ANDROID_PACKAGE = "android"
