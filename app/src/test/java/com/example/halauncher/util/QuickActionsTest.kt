package com.example.halauncher.util

import com.example.halauncher.util.QuickAction.Alarm
import com.example.halauncher.util.QuickAction.AlarmOff
import com.example.halauncher.util.QuickAction.TimerStop
import com.example.halauncher.util.QuickAction.Call
import com.example.halauncher.util.QuickAction.Email
import com.example.halauncher.util.QuickAction.Message
import com.example.halauncher.util.QuickAction.Navigate
import com.example.halauncher.util.QuickAction.NewEvent
import com.example.halauncher.util.QuickAction.OpenLink
import com.example.halauncher.util.QuickAction.Timer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionsTest {

    private fun parse(query: String) = QuickActions.parse(query)

    // Timers

    @Test fun `timer with minutes`() {
        assertEquals(listOf(Timer(300)), parse("timer 5m"))
        assertEquals(listOf(Timer(300)), parse("timer 5 min"))
        assertEquals(listOf(Timer(300)), parse("Timer 5 minutes"))
    }

    @Test fun `bare number timer is minutes`() {
        assertEquals(listOf(Timer(600)), parse("timer 10"))
    }

    @Test fun `timer with hours, minutes and seconds`() {
        assertEquals(listOf(Timer(5400)), parse("timer 1h30m"))
        assertEquals(listOf(Timer(5400)), parse("timer 1 hour 30 minutes"))
        assertEquals(listOf(Timer(5400)), parse("timer 1.5 hours"))
        assertEquals(listOf(Timer(90)), parse("timer 90s"))
    }

    @Test fun `timer phrased other ways`() {
        assertEquals(listOf(Timer(600)), parse("set a timer for 10 minutes"))
        assertEquals(listOf(Timer(300)), parse("5 min timer"))
    }

    @Test fun `timer without a length is an ordinary search`() {
        assertEquals(emptyList<QuickAction>(), parse("timer"))
        assertEquals(emptyList<QuickAction>(), parse("egg timer"))
        assertEquals(emptyList<QuickAction>(), parse("timer app"))
    }

    @Test fun `timer longer than a day is refused`() {
        assertEquals(emptyList<QuickAction>(), parse("timer 25h"))
        assertEquals(emptyList<QuickAction>(), parse("timer 0"))
    }

    // Alarms

    @Test fun `alarm at a time`() {
        assertEquals(listOf(Alarm(7, 30)), parse("alarm 7:30"))
        assertEquals(listOf(Alarm(7, 0)), parse("alarm 7"))
        assertEquals(listOf(Alarm(19, 45)), parse("alarm 19:45"))
    }

    @Test fun `alarm with am or pm`() {
        assertEquals(listOf(Alarm(7, 0)), parse("alarm 7am"))
        assertEquals(listOf(Alarm(19, 30)), parse("alarm 7.30 pm"))
        assertEquals(listOf(Alarm(0, 15)), parse("alarm 12:15am"))
        assertEquals(listOf(Alarm(12, 0)), parse("alarm 12pm"))
    }

    @Test fun `alarm without a separator`() {
        assertEquals(listOf(Alarm(7, 30)), parse("alarm 730"))
        assertEquals(listOf(Alarm(7, 30)), parse("alarm 0730"))
        assertEquals(listOf(Alarm(19, 45)), parse("alarm 1945"))
        assertEquals(listOf(Alarm(0, 5)), parse("alarm 0005"))
        assertEquals(listOf(Alarm(19, 30)), parse("alarm 730pm"))
        assertEquals(listOf(Alarm(6, 15)), parse("alarm 615 am"))
        assertEquals(listOf(Alarm(0, 30)), parse("alarm 1230am"))
        assertEquals(listOf(AlarmOff(7, 30)), parse("alarm off 730"))
    }

    @Test fun `alarm phrased other ways`() {
        assertEquals(listOf(Alarm(6, 45)), parse("wake me up at 6:45"))
        assertEquals(listOf(Alarm(8, 0)), parse("set an alarm for 8"))
    }

    @Test fun `alarm with an impossible time is an ordinary search`() {
        assertEquals(emptyList<QuickAction>(), parse("alarm 25:00"))
        assertEquals(emptyList<QuickAction>(), parse("alarm 7:75"))
        assertEquals(emptyList<QuickAction>(), parse("alarm 13pm"))
        assertEquals(emptyList<QuickAction>(), parse("alarm 2500"))
        assertEquals(emptyList<QuickAction>(), parse("alarm 775"))
        assertEquals(emptyList<QuickAction>(), parse("alarm 1330pm"))
        assertEquals(emptyList<QuickAction>(), parse("alarm 73000"))
        assertEquals(emptyList<QuickAction>(), parse("alarm clock"))
    }

    // Turning off

    @Test fun `alarm off with no time is the next alarm`() {
        assertEquals(listOf(AlarmOff()), parse("alarm off"))
        assertEquals(listOf(AlarmOff()), parse("Alarms off"))
        assertEquals(listOf(AlarmOff()), parse("turn off my alarm"))
    }

    @Test fun `alarm off at a time`() {
        assertEquals(listOf(AlarmOff(7, 30)), parse("alarm off 7:30"))
        assertEquals(listOf(AlarmOff(19, 0)), parse("alarm off at 7pm"))
        assertEquals(listOf(AlarmOff(6, 45)), parse("cancel the alarm 6:45"))
    }

    @Test fun `alarm off with a time still being typed does nothing yet`() {
        assertEquals(emptyList<QuickAction>(), parse("alarm off 7:"))
        assertEquals(emptyList<QuickAction>(), parse("alarm off tomorrow"))
    }

    @Test fun `timer stop`() {
        assertEquals(listOf(TimerStop()), parse("timer stop"))
        assertEquals(listOf(TimerStop()), parse("Timer off"))
        assertEquals(listOf(TimerStop()), parse("stop the timer"))
        assertEquals(emptyList<QuickAction>(), parse("timer stopwatch"))
    }

    // Numbers and addresses

    @Test fun `call and text commands`() {
        assertEquals(listOf(Call("98765 43210")), parse("call 98765 43210"))
        assertEquals(listOf(Call("112")), parse("call 112"))
        assertEquals(listOf(Message("+91 98765-43210")), parse("text +91 98765-43210"))
    }

    @Test fun `call with a name is left to contact search`() {
        assertEquals(emptyList<QuickAction>(), parse("call mom"))
    }

    @Test fun `a bare phone number offers call and message`() {
        assertEquals(listOf(Call("9876543210"), Message("9876543210")), parse("9876543210"))
    }

    @Test fun `short numbers and sums aren't phone numbers`() {
        assertEquals(emptyList<QuickAction>(), parse("2024"))
        assertEquals(emptyList<QuickAction>(), parse("1234567+89"))
    }

    @Test fun `email address bare or after a command`() {
        assertEquals(listOf(Email("someone@example.com")), parse("someone@example.com"))
        assertEquals(listOf(Email("someone@example.com")), parse("email someone@example.com"))
    }

    @Test fun `web addresses`() {
        assertEquals(listOf(OpenLink("https://github.com")), parse("github.com"))
        assertEquals(listOf(OpenLink("https://www.example.xyz/Path")), parse("www.example.xyz/Path"))
        assertEquals(listOf(OpenLink("http://intranet.local")), parse("http://intranet.local"))
    }

    @Test fun `file names aren't web addresses`() {
        assertEquals(emptyList<QuickAction>(), parse("notes.txt"))
        assertEquals(emptyList<QuickAction>(), parse("photo.jpg"))
        assertEquals(emptyList<QuickAction>(), parse("report.pdf"))
    }

    // Free text

    @Test fun `navigate keeps the place as typed`() {
        assertEquals(listOf(Navigate("Café Coffee Day")), parse("Navigate to Café Coffee Day"))
        assertEquals(listOf(Navigate("airport")), parse("directions airport"))
        assertEquals(listOf(Navigate("India Gate")), parse("maps India Gate"))
    }

    @Test fun `new event keeps its title as typed`() {
        assertEquals(listOf(NewEvent("Team Lunch")), parse("event Team Lunch"))
        assertEquals(listOf(NewEvent("Dentist")), parse("add event Dentist"))
    }

    @Test fun `free-text actions don't take Go from the results`() {
        assertFalse(Navigate("x").isExact)
        assertFalse(NewEvent("x").isExact)
        assertTrue(Timer(60).isExact)
        assertTrue(Call("123").isExact)
    }

    @Test fun `ordinary searches have no actions`() {
        assertEquals(emptyList<QuickAction>(), parse(""))
        assertEquals(emptyList<QuickAction>(), parse("chrome"))
        assertEquals(emptyList<QuickAction>(), parse("maps"))
        assertEquals(emptyList<QuickAction>(), parse("5+3"))
    }

    @Test fun `extra spaces are ignored`() {
        assertEquals(listOf(Timer(300)), parse("  timer   5m  "))
    }

    // Hints

    @Test fun `every command's example does what it says`() {
        for (command in QuickCommand.entries) {
            val action = parse(command.example).firstOrNull()
            val expected = when (command) {
                QuickCommand.TIMER -> action is Timer
                QuickCommand.TIMER_STOP -> action is TimerStop
                QuickCommand.ALARM -> action is Alarm
                QuickCommand.ALARM_OFF -> action is AlarmOff
                QuickCommand.CALL -> action is Call
                QuickCommand.TEXT -> action is Message
                QuickCommand.EMAIL -> action is Email
                QuickCommand.MAPS -> action is Navigate
                QuickCommand.EVENT -> action is NewEvent
            }
            assertTrue("${command.example} -> $action", expected)
        }
    }

    @Test fun `the start of a command word hints at it`() {
        val timer = listOf(QuickCommand.TIMER, QuickCommand.TIMER_STOP)
        assertEquals(timer, QuickActions.hints("tim"))
        assertEquals(timer, QuickActions.hints("Timer"))
        assertEquals(timer, QuickActions.hints("timer "))
        assertEquals(listOf(QuickCommand.ALARM, QuickCommand.ALARM_OFF), QuickActions.hints("alarm"))
        assertEquals(listOf(QuickCommand.EVENT), QuickActions.hints("eve"))
    }

    @Test fun `too little typed, a finished command or another word, no hint`() {
        assertEquals(emptyList<QuickCommand>(), QuickActions.hints("ti"))
        assertEquals(emptyList<QuickCommand>(), QuickActions.hints("timer 5m"))
        assertEquals(emptyList<QuickCommand>(), QuickActions.hints("timer x"))
        assertEquals(emptyList<QuickCommand>(), QuickActions.hints("timers "))
        assertEquals(emptyList<QuickCommand>(), QuickActions.hints("chrome"))
    }

    // Contacts by name

    @Test fun `a command with a name looks the name up`() {
        assertEquals(ContactCommand(ContactCommand.Kind.CALL, "mom"), QuickActions.contactCommand("call mom"))
        assertEquals(ContactCommand(ContactCommand.Kind.MESSAGE, "Maya Patel"), QuickActions.contactCommand("text Maya Patel"))
        assertEquals(ContactCommand(ContactCommand.Kind.EMAIL, "Sam"), QuickActions.contactCommand("Email  Sam "))
    }

    @Test fun `numbers and addresses aren't names`() {
        assertEquals(null, QuickActions.contactCommand("call 112"))
        assertEquals(null, QuickActions.contactCommand("call 98"))
        assertEquals(null, QuickActions.contactCommand("email a@b.com"))
        assertEquals(null, QuickActions.contactCommand("email sam@"))
    }

    @Test fun `no command or no name, no lookup`() {
        assertEquals(null, QuickActions.contactCommand("call"))
        assertEquals(null, QuickActions.contactCommand("mom"))
        assertEquals(null, QuickActions.contactCommand("timer 5m"))
    }

    @Test fun `a name clearly means a contact when it's all of their name, their first name, or the only match`() {
        val command = ContactCommand(ContactCommand.Kind.CALL, "maya")
        assertTrue(command.isClearly("Maya Patel", matchCount = 3))
        assertTrue(command.isClearly("Maya", matchCount = 3))
        assertTrue(command.isClearly("Mayank Rao", matchCount = 1))
        assertFalse(command.isClearly("Mayank Rao", matchCount = 3))
    }

    @Test fun `a contact's action is exact only when the name was clear`() {
        assertTrue(Call("123", "Mom").isExact)
        assertFalse(Call("123", "Mayank Rao", isExact = false).isExact)
    }
}
