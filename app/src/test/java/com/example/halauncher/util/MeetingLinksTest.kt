package com.example.halauncher.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeetingLinksTest {

    @Test fun `finds a meeting link in the description`() {
        val description = "Agenda: https://docs.example.com/agenda\nJoin: https://meet.google.com/abc-defg-hij."
        assertEquals("https://meet.google.com/abc-defg-hij", MeetingLinks.find(null, description))
    }

    @Test fun `subdomains of meeting services count`() {
        assertEquals("https://us02web.zoom.us/j/123?pwd=x", MeetingLinks.find("https://us02web.zoom.us/j/123?pwd=x"))
    }

    @Test fun `location is checked before the description`() {
        assertEquals(
            "https://teams.microsoft.com/l/meetup-join/1",
            MeetingLinks.find("https://teams.microsoft.com/l/meetup-join/1", "https://meet.google.com/zzz"),
        )
    }

    @Test fun `other links aren't meetings`() {
        assertNull(MeetingLinks.find("Office", "Slides at https://example.com/zoom.us"))
        assertNull(MeetingLinks.find("https://notzoom.us/j/1"))
    }

    @Test fun `nothing to find`() {
        assertNull(MeetingLinks.find(null, null))
        assertNull(MeetingLinks.find("Conference room 4"))
    }
}
