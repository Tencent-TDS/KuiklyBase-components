package com.tencent.tmm.knoi.convert

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NumericCollectionConvertTest {

    @Test
    fun arrayIntAndListDoubleAreCoerced() {
        assertTrue(isJsNumberCollection("kotlin.Array", "kotlin.Int"))
        assertTrue(isJsNumberCollection("kotlin.collections.List", "kotlin.Double"))
        assertTrue(isJsNumberCollection("kotlin.collections.ArrayList", "kotlin.Long"))
    }

    @Test
    fun nonNumericOrNonCollectionIsNotCoerced() {
        assertFalse(isJsNumberCollection("kotlin.Array", "kotlin.String"))
        assertFalse(isJsNumberCollection("kotlin.Array", "kotlin.Any"))
        assertFalse(isJsNumberCollection("kotlin.Map", "kotlin.Int"))
    }
}
