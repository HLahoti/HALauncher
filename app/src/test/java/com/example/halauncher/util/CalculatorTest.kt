package com.example.halauncher.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {

    @Test fun `simple addition evaluates to integer`() {
        assertEquals("8", Calculator.evaluate("5+3"))
    }

    @Test fun `whitespace around expression is tolerated`() {
        assertEquals("8", Calculator.evaluate("  5 + 3  "))
    }

    @Test fun `division keeps non-integer precision`() {
        assertEquals("3.5", Calculator.evaluate("7/2"))
    }

    @Test fun `grouped expression respects precedence`() {
        assertEquals("20", Calculator.evaluate("(2+3)*4"))
    }

    @Test fun `exp4j function call evaluates`() {
        assertEquals("3", Calculator.evaluate("sqrt(9)"))
    }

    @Test fun `plain word is not treated as expression`() {
        assertNull(Calculator.evaluate("maps"))
    }

    @Test fun `bare number is not treated as expression`() {
        assertNull(Calculator.evaluate("42"))
    }

    @Test fun `blank input returns null`() {
        assertNull(Calculator.evaluate(""))
        assertNull(Calculator.evaluate("   "))
    }

    @Test fun `invalid expression returns null`() {
        assertNull(Calculator.evaluate("5+"))
        assertNull(Calculator.evaluate("(5+3"))
    }

    @Test fun `division by zero returns null`() {
        assertNull(Calculator.evaluate("1/0"))
    }
}
