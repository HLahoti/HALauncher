package com.example.halauncher.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import net.objecthunter.exp4j.ExpressionBuilder

/**
 * Evaluates arithmetic expressions typed into search ("5+3", "sqrt(2) * pi"), so a running answer
 * can appear alongside the other search results.
 *
 * [evaluate] returns the formatted result, or null when the input doesn't look like a math
 * expression (no operator or no digit), won't parse, or doesn't produce a finite number. Plain
 * identifiers like app names never match, since they have no operator.
 */
object Calculator {
    /**
     * Characters that mark an input as an arithmetic expression rather than a word or an app
     * name. Parens are included so function calls like "sqrt(9)" are still picked up.
     */
    private val expressionChars = setOf('+', '-', '*', '/', '^', '%', '(', ')')

    /** Significant digits in a non-integer result; trailing zeros are stripped. */
    private const val SIGNIFICANT_DIGITS = 10

    /** Beyond this magnitude an integer-looking value stops being formatted as an integer. */
    private const val INTEGER_FORMAT_LIMIT = 1e15

    fun evaluate(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        // Require an operator (or parens) and a digit: skips plain words and bare numbers.
        if (!trimmed.any { it in expressionChars }) return null
        if (!trimmed.any { it.isDigit() }) return null
        val value = try {
            ExpressionBuilder(trimmed).build().evaluate()
        } catch (e: Exception) {
            return null
        }
        if (!value.isFinite()) return null
        return format(value)
    }

    private fun format(value: Double): String {
        if (abs(value) < INTEGER_FORMAT_LIMIT && value == value.toLong().toDouble()) {
            return value.toLong().toString()
        }
        val rounded = BigDecimal(value).round(MathContext(SIGNIFICANT_DIGITS, RoundingMode.HALF_UP))
        return rounded.stripTrailingZeros().toPlainString()
    }
}
