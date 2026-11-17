package com.example.halauncher.util

/**
 * Finds the video-call link in a calendar event's location or description, so search can offer
 * "Join" on it. Only links to known meeting services count: an event's description is full of
 * other links (agendas, documents, unsubscribe pages) that "Join" mustn't open.
 */
object MeetingLinks {
    /** Hosts (and their subdomains) of video-call services. */
    private val meetingHosts = listOf(
        "meet.google.com",
        "zoom.us",
        "zoom.com",
        "teams.microsoft.com",
        "teams.live.com",
        "webex.com",
        "gotomeeting.com",
        "meet.jit.si",
        "whereby.com",
        "chime.aws",
        "bluejeans.com",
        "skype.com",
    )

    private val urlPattern = Regex("""https://[^\s<>"'()\[\]]+""", RegexOption.IGNORE_CASE)

    /** The first meeting link in [texts], in order, or null when none of them has one. */
    fun find(vararg texts: String?): String? {
        for (text in texts) {
            if (text.isNullOrEmpty()) continue
            for (match in urlPattern.findAll(text)) {
                val url = match.value.trimEnd('.', ',', ';', ':', '!', '?')
                if (isMeetingUrl(url)) return url
            }
        }
        return null
    }

    private fun isMeetingUrl(url: String): Boolean {
        val host = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfter('@').substringBefore(':').lowercase()
        return meetingHosts.any { host == it || host.endsWith(".$it") }
    }
}
