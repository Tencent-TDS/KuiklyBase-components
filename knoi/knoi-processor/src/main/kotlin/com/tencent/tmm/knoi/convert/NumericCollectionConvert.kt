package com.tencent.tmm.knoi.convert

import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.tencent.tmm.knoi.function.Param

internal val numericElementTypes = setOf(
    "kotlin.Int",
    "kotlin.Long",
    "kotlin.Double",
    "kotlin.Float"
)

fun isJsNumberCollection(collectionCanonical: String, elementCanonical: String): Boolean {
    if (elementCanonical !in numericElementTypes) {
        return false
    }
    return collectionCanonical == "kotlin.Array" ||
        collectionCanonical == "kotlin.collections.List" ||
        collectionCanonical == "kotlin.collections.ArrayList"
}

/**
 * JS Array<number> is converted as Array<Double> because runtime params only keep Array::class.
 * An unchecked `as Array<Int>` then toIntArray() unboxes those Doubles as Int and yields zeros.
 * Generate coerceJsArray/coerceJsList so Number elements become the declared Kotlin type.
 */
fun genNumericCollectionParamAccess(
    receiver: String,
    index: Int,
    param: Param
): Pair<String, List<TypeName>>? {
    val collectionName = param.type.toClassName().canonicalName
    val element = param.type.arguments.firstOrNull()?.type?.resolve() ?: return null
    val elementCanonical = element.toClassName().canonicalName
    if (!isJsNumberCollection(collectionName, elementCanonical)) {
        return null
    }
    val elementTypeName = element.toTypeName()
    return when (collectionName) {
        "kotlin.Array" ->
            Pair("coerceJsArray<%T>($receiver[$index])", listOf(elementTypeName))
        "kotlin.collections.List", "kotlin.collections.ArrayList" ->
            Pair("coerceJsList<%T>($receiver[$index])", listOf(elementTypeName))
        else -> null
    }
}

fun needsJsNumberCollectionCoerce(parameters: List<Param>): Boolean {
    return parameters.any { genNumericCollectionParamAccess("args", 0, it) != null }
}
