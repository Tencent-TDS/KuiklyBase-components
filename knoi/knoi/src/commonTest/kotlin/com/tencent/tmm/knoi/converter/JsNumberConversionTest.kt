package com.tencent.tmm.knoi.converter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsNumberConversionTest {

    @Test
    fun jsNumberToIntReadsIeee754Double() {
        assertEquals(1, jsNumberToInt(1.0))
        assertEquals(2, jsNumberToInt(2.9))
        assertEquals(-4, jsNumberToInt(-4.2))
        assertEquals(0, jsNumberToInt(0.9))
        assertEquals(61, jsNumberToInt(61))
    }

    @Test
    fun jsNumberToIntRejectsNonNumberInsteadOfSilentZero() {
        assertFailsWith<IllegalArgumentException> { jsNumberToInt(null) }
        assertFailsWith<IllegalArgumentException> { jsNumberToInt("61") }
    }

    @Test
    fun coerceJsNumberOnlyMapsNumericTargets() {
        assertEquals(3, coerceJsNumber(3.7, Int::class))
        assertEquals(3L, coerceJsNumber(3.7, Long::class))
        assertEquals(3.7, coerceJsNumber(3.7, Double::class))
        assertEquals("keep", coerceJsNumber("keep", Int::class))
        assertEquals(5, coerceJsNumber(5, Int::class))
    }

    @Test
    fun coerceJsArrayConvertsDoubleElementsToInt() {
        val incoming: Array<Any?> = arrayOf(1.0, 2.0, 3.9, -4.2)
        val result = coerceJsArray<Int>(incoming)
        assertEquals(listOf(1, 2, 3, -4), result.toList())
        assertEquals(intArrayOf(1, 2, 3, -4).toList(), result.toIntArray().toList())
    }

    @Test
    fun coerceJsArrayKeepsIntElements() {
        val result = coerceJsArray<Int>(arrayOf(1, 2, 3))
        assertEquals(listOf(1, 2, 3), result.toList())
    }

    @Test
    fun coerceJsArrayConvertsIntElementsToDouble() {
        val result = coerceJsArray<Double>(arrayOf(1, 2, 3))
        assertEquals(listOf(1.0, 2.0, 3.0), result.toList())
    }

    @Test
    fun coerceJsListAcceptsArrayOrListOfDoubles() {
        assertEquals(listOf(10, 20), coerceJsList<Int>(listOf(10.0, 20.5)))
        assertEquals(listOf(10, 20), coerceJsList<Int>(arrayOf(10.0, 20.5)))
    }

    @Test
    fun coerceJsArrayRejectsNonNumberElements() {
        assertFailsWith<IllegalArgumentException> {
            coerceJsArray<Int>(arrayOf("nope"))
        }
        assertFailsWith<IllegalArgumentException> {
            coerceJsArray<Int>(arrayOf(1.0, null))
        }
        assertFailsWith<IllegalArgumentException> {
            coerceJsArray<Int>("not-an-array")
        }
    }
}
