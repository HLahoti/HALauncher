package com.example.halauncher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.text.format.DateUtils
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import com.example.halauncher.R
import com.example.halauncher.config.SearchLayersConfig
import com.example.halauncher.model.EventEntry
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.util.MeetingLinks
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Events from the device's shared calendar database, which every calendar app (Google Calendar,
 * Samsung Calendar, …) and account sync reads and writes. Returns nothing until the user grants
 * [Manifest.permission.READ_CALENDAR].
 *
 * Only occurrences from [SearchLayersConfig.EVENTS_PAST_DAYS] ago to
 * [SearchLayersConfig.EVENTS_FUTURE_DAYS] ahead are read. Events from calendars hidden in the
 * calendar app, cancelled ones and ones the user declined are left out.
 */
class CalendarProvider(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : SearchEntryProvider {

    override val category = SearchCategory.EVENT

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * One entry per event: its next occurrence that hasn't ended, or its latest one if every
     * occurrence in range is over.
     */
    @WorkerThread
    override fun loadEntries(): List<SearchEntry> {
        if (!hasPermission()) return emptyList()
        val now = clock()
        val from = now - TimeUnit.DAYS.toMillis(SearchLayersConfig.EVENTS_PAST_DAYS.toLong())
        val to = now + TimeUnit.DAYS.toMillis(SearchLayersConfig.EVENTS_FUTURE_DAYS.toLong())
        val chosen = LinkedHashMap<Long, Occurrence>()
        val read = queryInstances(from, to, ENTRY_PROJECTION) { cursor ->
            val occurrence = Occurrence(
                eventId = cursor.getLong(0),
                title = cursor.getString(1)?.trim().orEmpty(),
                begin = cursor.getLong(2),
                end = cursor.getLong(3),
                allDay = cursor.getInt(4) != 0,
                location = cursor.getString(5)?.trim()?.takeIf { it.isNotEmpty() },
                description = cursor.getString(6),
            )
            if (occurrence.title.isEmpty()) return@queryInstances
            // Rows come oldest first: a past occurrence is replaced by any later one, and the first
            // one that hasn't ended is kept.
            val held = chosen[occurrence.eventId]
            if (held == null || held.end < now) chosen[occurrence.eventId] = occurrence
        }
        if (!read) return emptyList()
        return chosen.values.map { it.toEntry(now) }
    }

    /**
     * The search ids of up to [limit] events with an occurrence that hasn't ended yet and starts
     * within [days], soonest first; the entries themselves come from the search index.
     */
    @WorkerThread
    fun loadUpcomingIds(limit: Int, days: Int): List<String> {
        if (!hasPermission() || limit <= 0) return emptyList()
        val now = clock()
        val ids = LinkedHashSet<String>()
        // Instances are looked up by overlap, so an event that's already under way is included.
        queryInstances(now, now + TimeUnit.DAYS.toMillis(days.toLong()), arrayOf(Instances.EVENT_ID, Instances.END)) { cursor ->
            if (ids.size < limit && cursor.getLong(1) >= now) ids += EventEntry.idFor(cursor.getLong(0))
        }
        return ids.toList()
    }

    /**
     * Runs [onRow] on each occurrence overlapping [from]..[to], oldest first.
     *
     * @return false if the permission was revoked part-way.
     */
    private fun queryInstances(
        from: Long,
        to: Long,
        projection: Array<String>,
        onRow: (Cursor) -> Unit,
    ): Boolean {
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        return try {
            context.contentResolver.query(uri, projection, SELECTION, null, "${Instances.BEGIN} ASC")?.use { cursor ->
                while (cursor.moveToNext()) onRow(cursor)
            }
            true
        } catch (e: SecurityException) {
            // Permission revoked between the check and the query.
            false
        }
    }

    private fun Occurrence.toEntry(now: Long) = EventEntry(
        eventId = eventId,
        name = title,
        begin = begin,
        end = end,
        allDay = allDay,
        location = location,
        meetingUrl = MeetingLinks.find(location, description),
        time = describeWhen(now),
        startTime = describeStart(now),
    )

    /** "Today, 14:00 – 15:00", "Tomorrow", "Tue, 18 Nov, 14:00 – 15:00", in the device's language. */
    private fun Occurrence.describeWhen(now: Long): String {
        // All-day events are stored as UTC midnights, so they're formatted in UTC to keep their day.
        val utc = if (allDay) DateUtils.FORMAT_UTC else 0
        val day = when {
            isOnDay(now) -> context.getString(R.string.event_today)
            isOnDay(now + DateUtils.DAY_IN_MILLIS) -> context.getString(R.string.event_tomorrow)
            else -> null
        }
        if (day != null) {
            if (allDay) return day
            val times = DateUtils.formatDateRange(context, begin, end, DateUtils.FORMAT_SHOW_TIME)
            return context.getString(R.string.event_day_and_time, day, times)
        }
        val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL or utc or
            if (allDay) 0 else DateUtils.FORMAT_SHOW_TIME
        return DateUtils.formatDateRange(context, begin, end, flags)
    }

    /** Just when it starts, briefly: "Today, 14:00", "Tomorrow", "18 Nov, 14:00", "22 Nov". */
    private fun Occurrence.describeStart(now: Long): String {
        val utc = if (allDay) DateUtils.FORMAT_UTC else 0
        val time = if (allDay) 0 else DateUtils.FORMAT_SHOW_TIME
        val day = when {
            isOnDay(now) -> context.getString(R.string.event_today)
            isOnDay(now + DateUtils.DAY_IN_MILLIS) -> context.getString(R.string.event_tomorrow)
            else -> null
        }
        if (day != null) {
            if (allDay) return day
            return context.getString(R.string.event_day_and_time, day, DateUtils.formatDateTime(context, begin, time))
        }
        val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR or utc or time
        return DateUtils.formatDateTime(context, begin, flags)
    }

    /**
     * Whether this occurrence is on the local day of [millis]: a timed event starts that day; an
     * all-day event covers it (its dates are UTC days, which are the same calendar dates).
     */
    private fun Occurrence.isOnDay(millis: Long): Boolean {
        val day = localDay(millis)
        return if (allDay) day >= begin / DateUtils.DAY_IN_MILLIS && day < end / DateUtils.DAY_IN_MILLIS else localDay(begin) == day
    }

    /** Days since the epoch at [millis], counted in local time. */
    private fun localDay(millis: Long): Long =
        Math.floorDiv(millis + TimeZone.getDefault().getOffset(millis), DateUtils.DAY_IN_MILLIS)

    private class Occurrence(
        val eventId: Long,
        val title: String,
        val begin: Long,
        val end: Long,
        val allDay: Boolean,
        val location: String?,
        val description: String?,
    )

    private companion object {
        val ENTRY_PROJECTION = arrayOf(
            Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END,
            Instances.ALL_DAY, Instances.EVENT_LOCATION, Instances.DESCRIPTION,
        )

        /** Visible calendars only, without cancelled events or ones the user said no to. */
        val SELECTION = "${Instances.VISIBLE} = 1" +
            " AND ${Instances.STATUS} IS NOT ${Events.STATUS_CANCELED}" +
            " AND ${Instances.SELF_ATTENDEE_STATUS} IS NOT ${Attendees.ATTENDEE_STATUS_DECLINED}"
    }
}
