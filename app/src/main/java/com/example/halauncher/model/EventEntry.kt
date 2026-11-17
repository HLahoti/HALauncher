package com.example.halauncher.model

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract

/**
 * An event from the device's calendar database (the data behind the default calendar app). A
 * repeating event is one entry, listed by its next occurrence (or its last one, once they're all
 * past), so a weekly meeting doesn't fill search with copies of itself.
 *
 * Opens that occurrence in whichever app handles calendar events; if there is none, opens the
 * calendar on its day.
 *
 * @property begin when the occurrence starts, in epoch millis. For an all-day event that's midnight
 * UTC of its first day, as the calendar database stores it.
 * @property end when it ends; for an all-day event, midnight UTC after its last day.
 * @property location where it takes place, as typed into the event; null when it has none.
 * @property meetingUrl a video-call link found in the event (Meet, Zoom, Teams, …), for "Join".
 * @property time when it is ("Today, 14:00 – 15:00", "Tue, 18 Nov"), formatted when the event is read,
 * in the device's language and 12/24-hour setting.
 * @property startTime just when it starts ("Today, 14:00", "18 Nov"), for a grid cell's narrow line.
 */
data class EventEntry(
    val eventId: Long,
    override val name: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?,
    val meetingUrl: String?,
    val time: String,
    val startTime: String,
) : SearchEntry {
    /**
     * The line under the title in a list: [time], then [location] ("Today, 14:00 – 15:00 · Office").
     * A grid cell, having less room, shows [startTime] with a location pin instead.
     */
    override val detail: String get() = listOfNotNull(time, location).joinToString(DETAIL_SEPARATOR)

    override val id: String get() = idFor(eventId)
    override val category: SearchCategory get() = SearchCategory.EVENT

    override val link: Intent
        // The type is given, not looked up from the calendar provider, which the launcher may not see.
        get() = Intent(Intent.ACTION_VIEW)
            .setDataAndType(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), EVENT_TYPE)
            // Which occurrence of a repeating event to show.
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)

    override val fallbackLinks: List<Intent>
        get() = listOf(Intent(Intent.ACTION_VIEW, dayUri(begin)))

    /** Its location matches too, so "office" finds what's on there. */
    override val keywords: String? get() = location

    companion object {
        /** The MIME type of one calendar event, which calendar apps open. */
        const val EVENT_TYPE = "vnd.android.cursor.item/event"

        /** The MIME type of the calendar's events, which calendar apps add to. */
        const val EVENTS_TYPE = "vnd.android.cursor.dir/event"

        private const val DETAIL_SEPARATOR = " · "

        /** The [id] of the event with this calendar-database id. */
        fun idFor(eventId: Long): String = "event:$eventId"

        /** The calendar app's view of the day around [millis]. */
        fun dayUri(millis: Long): Uri =
            CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(millis.toString()).build()
    }
}
