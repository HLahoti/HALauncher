package com.example.halauncher.search.ranking

import com.example.halauncher.config.RankingConfig
import com.example.halauncher.search.normalizeForSearch
import com.example.halauncher.search.searchTerms
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * The default [SearchRanker]: a weighted sum of how well the name matches the query and how
 * the entry fits the user's habits right now.
 *
 * - **Text match** (0–1): exact name, then name starts with the query, then every query word
 *   starts a word of the name in order, then in any order, then a weaker inner match. Shorter
 *   names get a small bonus, since the query covers more of them.
 * - **Frequency** (0–1): opens in the retention window, saturating so a few favourites don't
 *   drown out everything else.
 * - **Recency** (0–1): halves every [RankingConfig.RECENCY_HALF_LIFE_DAYS] since the last open.
 * - **Time of day** (0–1): share of past opens near the current time of day, so things used
 *   around this hour rise.
 * - **Category prior**: a small constant per category.
 *
 * All weights and thresholds live in [RankingConfig].
 */
class ContextMatchRanker(
    private val timeZone: () -> TimeZone = TimeZone::getDefault,
) : SearchRanker {

    override fun score(context: RankingContext, candidate: RankingCandidate): Double {
        val usage = candidate.usage
        return RankingConfig.TEXT_WEIGHT * textScore(context, candidate.name) +
            RankingConfig.FREQUENCY_WEIGHT * frequencyScore(usage) +
            RankingConfig.RECENCY_WEIGHT * recencyScore(usage, context.nowMillis) +
            RankingConfig.TIME_OF_DAY_WEIGHT * timeOfDayScore(usage, context.nowMillis) +
            (RankingConfig.CATEGORY_PRIORS[candidate.category] ?: 0.0)
    }

    internal fun textScore(context: RankingContext, name: String): Double {
        if (context.terms.isEmpty()) return 0.0
        val query = normalizeForSearch(context.query).trim()
        val normalizedName = normalizeForSearch(name)
        val words = searchTerms(name)
        val tier = when {
            normalizedName == query -> RankingConfig.MATCH_EXACT
            normalizedName.startsWith(query) -> RankingConfig.MATCH_NAME_PREFIX
            wordPrefixesInOrder(context.terms, words) -> RankingConfig.MATCH_WORD_PREFIX_IN_ORDER
            context.terms.all { term -> words.any { it.startsWith(term) } } -> RankingConfig.MATCH_WORD_PREFIX
            else -> RankingConfig.MATCH_OTHER
        }
        val coverage = min(1.0, query.length.toDouble() / normalizedName.length.coerceAtLeast(1))
        return tier + RankingConfig.SHORT_NAME_BONUS * coverage
    }

    internal fun frequencyScore(usage: UsageHistory): Double =
        1 - exp(-usage.openCount / RankingConfig.FREQUENCY_SATURATION)

    internal fun recencyScore(usage: UsageHistory, nowMillis: Long): Double {
        val last = usage.lastOpenedMillis ?: return 0.0
        val ageDays = (nowMillis - last).coerceAtLeast(0).toDouble() / TimeUnit.DAYS.toMillis(1)
        return exp(-ln(2.0) * ageDays / RankingConfig.RECENCY_HALF_LIFE_DAYS)
    }

    internal fun timeOfDayScore(usage: UsageHistory, nowMillis: Long): Double {
        if (usage.openCount < RankingConfig.TIME_OF_DAY_MIN_OPENS) return 0.0
        val now = minuteOfDay(nowMillis)
        val window = RankingConfig.TIME_OF_DAY_WINDOW_HOURS * 60
        val nearby = usage.openTimesMillis.count { circularMinutes(minuteOfDay(it), now) <= window }
        return nearby.toDouble() / usage.openCount
    }

    /** True if each term starts a word, each word after the previous term's word. */
    private fun wordPrefixesInOrder(terms: List<String>, words: List<String>): Boolean {
        var next = 0
        for (term in terms) {
            val at = (next until words.size).firstOrNull { words[it].startsWith(term) } ?: return false
            next = at + 1
        }
        return true
    }

    private fun minuteOfDay(millis: Long): Int {
        val calendar = Calendar.getInstance(timeZone()).apply { timeInMillis = millis }
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    /** Distance between two times of day, wrapping at midnight (23:50 is 20 min from 00:10). */
    private fun circularMinutes(a: Int, b: Int): Int {
        val diff = abs(a - b)
        return min(diff, MINUTES_PER_DAY - diff)
    }

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}
