package com.tencent.tmm.knoi.converter

import kotlin.reflect.KClass

/**
 * Converts a JS [Number] (IEEE-754 double on ArkTS / JavaScript) to a Kotlin numeric type.
 *
 * knoi documents Number as Double, Int, and Long. JS has no integer type, so the correct
 * Int/Long path is [Number.toInt] / [Number.toLong] after the value is read as a Number.
 * Reading a heap-number with napi_get_value_int32, or treating Float64 bytes as i32,
 * silently yields 0 for typical small integers.
 *
 * Non-Number values are returned unchanged so this can be used as a no-op for already-typed
 * Kotlin values (for example Int already boxed as Int).
 */
fun coerceJsNumber(value: Any?, type: KClass<*>): Any? {
    val number = value as? Number ?: return value
    return when (type) {
        Int::class -> number.toInt()
        Long::class -> number.toLong()
        Double::class -> number.toDouble()
        Float::class -> number.toFloat()
        else -> value
    }
}

/**
 * Convert a JS Number-backed value to [Int].
 *
 * @throws IllegalArgumentException if [value] is null or not a [Number]
 */
fun jsNumberToInt(value: Any?): Int {
    return jsNumberTo(value, Int::class) { it.toInt() }
}

/**
 * Convert a JS Number-backed value to [Long].
 *
 * @throws IllegalArgumentException if [value] is null or not a [Number]
 */
fun jsNumberToLong(value: Any?): Long {
    return jsNumberTo(value, Long::class) { it.toLong() }
}

/**
 * Convert a JS Number-backed value to [Double].
 *
 * @throws IllegalArgumentException if [value] is null or not a [Number]
 */
fun jsNumberToDouble(value: Any?): Double {
    return jsNumberTo(value, Double::class) { it.toDouble() }
}

private inline fun <T> jsNumberTo(value: Any?, type: KClass<*>, map: (Number) -> T): T {
    val number = value as? Number
        ?: throw IllegalArgumentException(
            "Cannot convert ${value?.let { it::class.simpleName } ?: "null"} to ${type.simpleName}. " +
                "JS Number / Array<number> elements must be converted via Number, not silent 0."
        )
    return map(number)
}

/**
 * Coerce a JS-origin array (typically [Array] of [Double]) to a typed Kotlin [Array].
 *
 * Service/export bindings only retain `Array::class` at runtime, so JS `Array<number>`
 * arrives as `Array<Double>`. An unchecked `as Array<Int>` followed by [Array.toIntArray]
 * then unboxes those Doubles as Int and produces all zeros.
 */
inline fun <reified T : Any> coerceJsArray(value: Any?): Array<T> {
    return coerceJsArray(value, T::class)
}

fun <T : Any> coerceJsArray(value: Any?, elementClass: KClass<T>): Array<T> {
    val array = value as? Array<*>
        ?: throw IllegalArgumentException(
            "Expected Array<${elementClass.simpleName}>, got ${value?.let { it::class.simpleName } ?: "null"}"
        )
    return Array(array.size) { index ->
        coerceJsArrayElement(array[index], elementClass, index)
    }
}

/**
 * Coerce a JS-origin list to a typed Kotlin [List].
 */
inline fun <reified T : Any> coerceJsList(value: Any?): List<T> {
    return coerceJsList(value, T::class)
}

fun <T : Any> coerceJsList(value: Any?, elementClass: KClass<T>): List<T> {
    val list = when (value) {
        is List<*> -> value
        is Array<*> -> value.asList()
        else -> throw IllegalArgumentException(
            "Expected List<${elementClass.simpleName}>, got ${value?.let { it::class.simpleName } ?: "null"}"
        )
    }
    return list.mapIndexed { index, element ->
        coerceJsArrayElement(element, elementClass, index)
    }
}

private fun <T : Any> coerceJsArrayElement(value: Any?, elementClass: KClass<T>, index: Int): T {
    if (value == null) {
        throw IllegalArgumentException(
            "Array/List element at index $index is null; cannot convert to ${elementClass.simpleName}"
        )
    }
    val coerced = coerceJsNumber(value, elementClass)
    if (elementClass == Int::class || elementClass == Long::class ||
        elementClass == Double::class || elementClass == Float::class
    ) {
        if (coerced !is Number) {
            throw IllegalArgumentException(
                "Array/List element at index $index is ${value::class.simpleName}, " +
                    "expected JS Number convertible to ${elementClass.simpleName}"
            )
        }
    }
    @Suppress("UNCHECKED_CAST")
    return coerced as? T
        ?: throw IllegalArgumentException(
            "Array/List element at index $index is ${value::class.simpleName}, " +
                "cannot convert to ${elementClass.simpleName}"
        )
}
