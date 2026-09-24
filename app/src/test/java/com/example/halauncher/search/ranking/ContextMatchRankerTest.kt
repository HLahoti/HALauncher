package com.example.halauncher.search.ranking

import com.example.halauncher.model.SearchCategory
import com.example.halauncher.search.TestEntry
import com.example.halauncher.search.searchTerms
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextMatchRankerTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val ranker = ContextMatchRanker(timeZone = { utc })

    // 2026-09-24 10:00 UTC
    private val now = 1_790_244_000_000L
    private val day = TimeUnit.DAYS.toMillis(1)
    private val hour = TimeUnit.HOURS.toMillis(1)

    private fun context(query: String) = RankingContext(query, searchTerms(query), now)
    private fun score(query: String, name: String, usage: UsageHistory = UsageHistory.NEVER_OPENED) =
        ranker.score(context(query), RankingCandidate(TestEntry(name, SearchCategory.FILE), usage))

    @Test
    fun textTiersOrderExactThenPrefixThenWordPrefixThenOther() {
        val exact = score("maps", "Maps")
        val prefix = score("map", "Maps Go")
        val wordsInOrder = score("go ma", "Google Maps")
        val wordsAnyOrder = score("ma go", "Google Maps")
        val other = score("oogle", "Google")
        assertTrue(exact > prefix)
        assertTrue(prefix > wordsInOrder)
        assertTrue(wordsInOrder > wordsAnyOrder)
        assertTrue(wordsAnyOrder > other)
    }

    @Test
    fun accentsDoNotHurtTheMatch() {
        assertEquals(score("cafe", "cafe"), score("cafe", "Café"), 1e-9)
    }

    @Test
    fun shorterNamesWinWithinATier() {
        assertTrue(score("spo", "Spotify") > score("spo", "Spotify Podcasts Archive"))
    }

    @Test
    fun usageBreaksTiesBetweenEquallyGoodMatches() {
        val used = UsageHistory(listOf(now - day, now - 2 * day, now - 3 * day))
        assertTrue(score("rep", "report.pdf", used) > score("rep", "report.pdf"))
    }

    @Test
    fun heavyUsageCannotJumpTwoTextTiers() {
        val heavy = UsageHistory(List(50) { now - it * hour })
        // Exact match vs. a word-prefix match two tiers down.
        assertTrue(score("maps", "Maps") > score("maps", "Google Maps Offline Areas", heavy))
    }

    @Test
    fun heavyUsageCanOvertakeTheNextTier() {
        val heavy = UsageHistory(List(50) { now - it * hour })
        // Exact match vs. a heavily used name-prefix match one tier down.
        assertTrue(score("maps", "Maps Go", heavy) > score("maps", "Maps"))
    }

    @Test
    fun recencyHalvesEveryHalfLife() {
        val weekAgo = UsageHistory(listOf(now - 7 * day))
        assertEquals(0.5, ranker.recencyScore(weekAgo, now), 1e-6)
        assertEquals(0.0, ranker.recencyScore(UsageHistory.NEVER_OPENED, now), 0.0)
    }

    @Test
    fun frequencySaturates() {
        val few = ranker.frequencyScore(UsageHistory(List(3) { now }))
        val many = ranker.frequencyScore(UsageHistory(List(100) { now }))
        assertTrue(few < many)
        assertTrue(many <= 1.0)
    }

    @Test
    fun timeOfDayCountsOpensAroundNowAcrossDays() {
        // Opened at ~10:00 on three different days, and once at 22:00.
        val mornings = UsageHistory(listOf(now - day, now - 2 * day + 20 * 60_000, now - 3 * day, now - 12 * hour))
        assertEquals(0.75, ranker.timeOfDayScore(mornings, now), 1e-9)
    }

    @Test
    fun timeOfDayWrapsAroundMidnight() {
        val nearMidnight = now + 14 * hour - 10 * 60_000 // 23:50
        val justAfter = UsageHistory(List(3) { nearMidnight - it * day + 20 * 60_000 }) // 00:10 on past days
        assertEquals(1.0, ranker.timeOfDayScore(justAfter, nearMidnight), 1e-9)
    }

    @Test
    fun timeOfDayNeedsAFewOpensFirst() {
        assertEquals(0.0, ranker.timeOfDayScore(UsageHistory(listOf(now - day)), now), 0.0)
    }

    @Test
    fun emptyQueryScoresUsageOnly() {
        val recent = UsageHistory(List(5) { now - it * hour })
        assertTrue(score("", "Anything", recent) > score("", "Anything"))
        assertEquals(0.0, ranker.textScore(context(""), "Anything"), 0.0)
    }
}
