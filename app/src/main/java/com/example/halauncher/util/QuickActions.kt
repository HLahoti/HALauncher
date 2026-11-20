package com.example.halauncher.util

import java.util.Locale

/**
 * Something search can do straight from what's typed, handed to the app that does it best (the
 * clock, the dialer, maps, …): "timer 5m", "alarm 7:30", "call 98765 43210". Shown above the
 * results, like the calculator's answer.
 */
sealed interface QuickAction {
    /**
     * Whether the query can only have meant this, so Go runs it ahead of the top result. A free-text
     * one ("maps coffee", "event lunch") may just be a search for something named like that, so Go
     * runs it only when nothing else matches.
     */
    val isExact: Boolean get() = true

    /** A countdown of [seconds], in the clock app. */
    data class Timer(val seconds: Int) : QuickAction

    /** An alarm at [hour]:[minute] (24-hour), in the clock app. */
    data class Alarm(val hour: Int, val minute: Int) : QuickAction

    /**
     * The clock app's alarm at [hour]:[minute] (24-hour) turned off; with no [hour], whichever of its
     * alarms rings next. [nextAt], when known, is when that next one rings (epoch millis), to show
     * which it is. [opensList] is for clocks that can't turn off an alarm for another app (Samsung's):
     * their alarm list opens instead, to switch it off there. Both are found on the device, not typed.
     */
    data class AlarmOff(
        val hour: Int? = null,
        val minute: Int = 0,
        val nextAt: Long? = null,
        val opensList: Boolean = false,
    ) : QuickAction

    /**
     * A timer stopped. Android lets other apps stop only a timer that's ringing, not one still
     * counting down, so with none ringing ([opensList], found on the device, not typed) the clock's
     * timers open instead, to stop it there.
     */
    data class TimerStop(val opensList: Boolean = false) : QuickAction

    /**
     * [number] dialled (not called) in the phone app; for a contact found by name ("call mom"),
     * [contactName] is who it is.
     */
    data class Call(
        val number: String,
        override val contactName: String? = null,
        override val isExact: Boolean = true,
    ) : QuickAction, ForContact

    /** A new text message to [number], or to [contactName]'s number. */
    data class Message(
        val number: String,
        override val contactName: String? = null,
        override val isExact: Boolean = true,
    ) : QuickAction, ForContact

    /** A new email to [address], or to [contactName]'s address. */
    data class Email(
        val address: String,
        override val contactName: String? = null,
        override val isExact: Boolean = true,
    ) : QuickAction, ForContact

    /**
     * An action that can be for a contact found by name. [isExact] is false when the name typed may
     * mean someone else too, so Go leaves it to the results.
     */
    interface ForContact {
        val contactName: String?
    }

    /** [place] in the maps app, to get directions. */
    data class Navigate(val place: String) : QuickAction {
        override val isExact: Boolean get() = false
    }

    /** A web address, in the browser. [url] always has its scheme. */
    data class OpenLink(val url: String) : QuickAction

    /** A new calendar event called [title], for the user to finish in the calendar app. */
    data class NewEvent(val title: String) : QuickAction {
        override val isExact: Boolean get() = false
    }
}

/**
 * A typed command search understands, with an [example] of it that parses to its action. Used to
 * teach the commands: search hints at one as its word is typed ([QuickActions.hints]), and the
 * Search tips page lists them all.
 *
 * @property word what the command starts with, as typed.
 */
enum class QuickCommand(val word: String, val example: String) {
    TIMER("timer", "timer 10m"),
    TIMER_STOP("timer", "timer stop"),
    ALARM("alarm", "alarm 730am"),
    ALARM_OFF("alarm", "alarm off"),
    CALL("call", "call 555 0100"),
    TEXT("text", "text 555 0100"),
    EMAIL("email", "email name@example.com"),
    MAPS("maps", "maps coffee near me"),
    EVENT("event", "event Lunch with Sam"),
}

/**
 * "call mom", "text mom", "email mom": a [kind] of action for whoever in the contacts is called [name].
 * Search looks them up and offers the action for each match (see [QuickActions.contactCommand]).
 */
data class ContactCommand(val kind: Kind, val name: String) {
    enum class Kind { CALL, MESSAGE, EMAIL }

    /**
     * Whether [contactName], one of [matchCount] contacts found, is clearly who [name] meant: the
     * only match, or [name] is their whole name or first name.
     */
    fun isClearly(contactName: String, matchCount: Int): Boolean {
        if (matchCount == 1) return true
        val typed = name.trim()
        return contactName.equals(typed, ignoreCase = true) ||
            contactName.substringBefore(' ').equals(typed, ignoreCase = true)
    }
}

/**
 * Reads [QuickAction]s out of a search query. English command words, matched loosely, since this
 * runs on every keystroke and an action that turns up while the user is still typing is fine: it's
 * one extra row, and only Go or a tap runs it.
 */
object QuickActions {
    /** The longest timer clock apps accept (AlarmClock.EXTRA_LENGTH): 24 hours. */
    const val MAX_TIMER_SECONDS = 24 * 60 * 60

    /**
     * Fewest digits for a bare number (nothing else typed) to be offered as a phone number, so a
     * year or a sum isn't. After "call" or "text", fewer will do (emergency numbers).
     */
    private const val BARE_NUMBER_MIN_DIGITS = 7
    private const val COMMAND_NUMBER_MIN_DIGITS = 3

    private val timerStopCommand = Regex(
        """^(?:timers? (?:stop|off|cancel|dismiss)|(?:stop|cancel|dismiss) (?:the |my )?timers?)$""",
        RegexOption.IGNORE_CASE,
    )
    private val alarmOffCommand = Regex(
        """^(?:alarms? off|(?:turn off|cancel) (?:the |my )?alarm)(?: (?:at |for )?(.+))?$""",
        RegexOption.IGNORE_CASE,
    )
    private val timerCommand = Regex("""^(?:set (?:a )?)?timer(?: for)? (.+)$|^(.+?) timer$""", RegexOption.IGNORE_CASE)
    private val alarmCommand = Regex("""^(?:(?:set (?:an )?)?alarm(?: for| at)?|wake me(?: up)?(?: at)?) (.+)$""", RegexOption.IGNORE_CASE)
    private val callCommand = Regex("""^(?:call|dial|phone) (.+)$""", RegexOption.IGNORE_CASE)
    private val messageCommand = Regex("""^(?:text|sms|message|msg) (.+)$""", RegexOption.IGNORE_CASE)
    private val emailCommand = Regex("""^(?:e-?mail|mail) (.+)$""", RegexOption.IGNORE_CASE)
    private val navigateCommand = Regex("""^(?:navigate(?: to)?|directions(?: to)?|take me to|maps?) (.+)$""", RegexOption.IGNORE_CASE)
    private val eventCommand = Regex("""^(?:(?:new|add|create) )?event (.+)$""", RegexOption.IGNORE_CASE)

    private val whitespace = Regex("""\s+""")
    // Longer unit names first: alternatives are tried in order, so "h" would stop "hours" at its first letter.
    private val durationPart = Regex("""(\d+(?:\.\d+)?)\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)?""")
    // The separator is optional, so "730" and "1945" read as 7:30 and 19:45.
    private val clockTime = Regex("""^(\d{1,2})(?:[:.]?(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)?$""")
    private val phoneNumber = Regex("""^\+?[\d\s\-().]+$""")
    private val emailAddress = Regex("""^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$""")
    private val webAddress = Regex(
        """^(?:(https?)://)?((?:[a-z0-9](?:[a-z0-9\-]*[a-z0-9])?\.)+([a-z]{2,}))(?::\d+)?(/\S*)?$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Top-level domains a bare address must end in to count as one ("example.com"), so a file
     * name like "notes.txt" or "photo.jpg" isn't mistaken for a website. With "http(s)://" or
     * "www." any domain counts.
     */
    private val commonDomains = setOf(
        "com", "org", "net", "io", "dev", "app", "ai", "co", "edu", "gov", "info", "me", "tv",
        "in", "uk", "us", "ca", "au", "de", "fr", "jp", "eu", "ly", "gg", "xyz", "news", "wiki",
    )

    /** Fewest letters of a command word typed before search hints at the command. */
    const val HINT_MIN_LENGTH = 3

    /**
     * The commands [query] looks like the start of ("tim", "timer", "timer "), so search can show how
     * they're used before the user knows them. Empty once the query does something itself ([parse]
     * finds an action), and for anything that isn't the start of a command word.
     */
    fun hints(query: String): List<QuickCommand> {
        val typed = query.trimStart().lowercase(Locale.ROOT)
        val word = typed.trimEnd()
        // Only the command word so far: "timer" or "timer ", not "timer x".
        if (word.length < HINT_MIN_LENGTH || ' ' in word) return emptyList()
        if (parse(query).isNotEmpty()) return emptyList()
        return QuickCommand.entries.filter { command ->
            if (typed.length > word.length) command.word == word else command.word.startsWith(word)
        }
    }

    /** The actions [query] asks for, most likely first; empty for an ordinary search. */
    fun parse(query: String): List<QuickAction> {
        val text = query.trim().replace(whitespace, " ")
        if (text.isEmpty()) return emptyList()

        // Before setting ones: "timer stop" and "alarm off 7:30" start with the same words.
        if (timerStopCommand.matches(text)) return listOf(QuickAction.TimerStop())
        alarmOffCommand.matchEntire(text)?.let { match ->
            val time = match.groupValues[1]
            if (time.isEmpty()) return listOf(QuickAction.AlarmOff())
            parseClockTime(time.lowercase(Locale.ROOT))?.let { (hour, minute) ->
                return listOf(QuickAction.AlarmOff(hour, minute))
            }
            // Not a time yet ("alarm off 7:"): nothing, rather than an alarm at "off 7:".
            return emptyList()
        }
        timerCommand.matchEntire(text)?.let { match ->
            val duration = match.groupValues[1].ifEmpty { match.groupValues[2] }
            parseDuration(duration.lowercase(Locale.ROOT))?.let { return listOf(QuickAction.Timer(it)) }
        }
        alarmCommand.matchEntire(text)?.let { match ->
            parseClockTime(match.groupValues[1].lowercase(Locale.ROOT))?.let { (hour, minute) ->
                return listOf(QuickAction.Alarm(hour, minute))
            }
        }
        callCommand.matchEntire(text)?.let { match ->
            phoneNumberIn(match.groupValues[1], COMMAND_NUMBER_MIN_DIGITS)?.let { return listOf(QuickAction.Call(it)) }
        }
        messageCommand.matchEntire(text)?.let { match ->
            phoneNumberIn(match.groupValues[1], COMMAND_NUMBER_MIN_DIGITS)?.let { return listOf(QuickAction.Message(it)) }
        }
        emailCommand.matchEntire(text)?.let { match ->
            val address = match.groupValues[1]
            if (emailAddress.matches(address)) return listOf(QuickAction.Email(address))
        }
        navigateCommand.matchEntire(text)?.let { match ->
            return listOf(QuickAction.Navigate(match.groupValues[1]))
        }
        eventCommand.matchEntire(text)?.let { match ->
            return listOf(QuickAction.NewEvent(match.groupValues[1]))
        }

        // Nothing but a phone number, an email address or a web address: offer what's usually done with it.
        phoneNumberIn(text, BARE_NUMBER_MIN_DIGITS)?.let { return listOf(QuickAction.Call(it), QuickAction.Message(it)) }
        if (emailAddress.matches(text)) return listOf(QuickAction.Email(text))
        webUrl(text)?.let { return listOf(QuickAction.OpenLink(it)) }
        return emptyList()
    }

    /**
     * The contact command in [query] ("call mom"): a call, text or email command followed by
     * something that isn't a number or address, so it must be a name. Null for anything else,
     * including what [parse] already handles ("call 112", "email a@b.com").
     */
    fun contactCommand(query: String): ContactCommand? {
        val text = query.trim().replace(whitespace, " ")
        if (text.isEmpty() || parse(text).isNotEmpty()) return null
        val (kind, rest) = callCommand.matchEntire(text)?.let { ContactCommand.Kind.CALL to it.groupValues[1] }
            ?: messageCommand.matchEntire(text)?.let { ContactCommand.Kind.MESSAGE to it.groupValues[1] }
            ?: emailCommand.matchEntire(text)?.let { ContactCommand.Kind.EMAIL to it.groupValues[1] }
            ?: return null
        val name = rest.trim()
        // Digits or an @ mean a number or address still being typed, not a name.
        if (name.isEmpty() || name.any(Char::isDigit) || '@' in name) return null
        return ContactCommand(kind, name)
    }

    /**
     * "5m", "5 min", "1h 30m", "90s", "1.5 hours"; a bare number is minutes. Null for anything else,
     * a zero length or one past [MAX_TIMER_SECONDS].
     */
    internal fun parseDuration(text: String): Int? {
        val compact = text.trim().replace(" and ", " ")
        if (compact.isEmpty()) return null
        var seconds = 0.0
        var consumed = 0
        for (part in durationPart.findAll(compact)) {
            // Only spaces may come between the parts.
            if (compact.substring(consumed, part.range.first).isNotBlank()) return null
            consumed = part.range.last + 1
            val amount = part.groupValues[1].toDouble()
            seconds += amount * when (part.groupValues[2].firstOrNull()) {
                'h' -> 3600
                's' -> 1
                else -> 60
            }
        }
        if (consumed == 0 || compact.substring(consumed).isNotBlank()) return null
        val total = seconds.toInt()
        return total.takeIf { it in 1..MAX_TIMER_SECONDS }
    }

    /**
     * "7", "7:30", "730", "7.30pm", "730pm", "7 am", "19:45", "1945" as (hour, minute) on a 24-hour
     * clock. Without am/pm the hour is taken as written ("7" is 07:00), as clock apps do. Null for
     * anything else.
     */
    internal fun parseClockTime(text: String): Pair<Int, Int>? {
        val match = clockTime.matchEntire(text.trim()) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
        val half = match.groupValues[3].firstOrNull()
        if (minute > 59) return null
        if (half == null) {
            if (hour > 23) return null
        } else {
            if (hour !in 1..12) return null
            hour = hour % 12 + if (half == 'p') 12 else 0
        }
        return hour to minute
    }

    /** [text] as a phone number, if that's all it is, with at least [minDigits] digits. */
    private fun phoneNumberIn(text: String, minDigits: Int): String? {
        val trimmed = text.trim()
        if (!phoneNumber.matches(trimmed)) return null
        // A "+" only leads; "5+3" is a sum.
        if (trimmed.lastIndexOf('+') > 0) return null
        return trimmed.takeIf { number -> number.count(Char::isDigit) >= minDigits }
    }

    /** [this] as a full http(s) URL, if it's a web address and nothing else. */
    private fun webUrl(text: String): String? {
        val match = webAddress.matchEntire(text) ?: return null
        val scheme = match.groupValues[1]
        val host = match.groupValues[2]
        val tld = match.groupValues[3]
        if (scheme.isEmpty() && !host.startsWith("www.", ignoreCase = true) && tld.lowercase(Locale.ROOT) !in commonDomains) return null
        return if (scheme.isEmpty()) "https://$text" else text
    }
}
