package com.tencent.tmm.knoi.converter

import com.tencent.tmm.knoi.exception.UnSupportTypeException
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import platform.ohos.knoi.createArray
import platform.ohos.knoi.getArrayLength
import platform.ohos.knoi.getElementInArray
import platform.ohos.knoi.getTypeArrayLength
import platform.ohos.knoi.getTypeArrayType
import platform.ohos.knoi.getTypeArrayValue
import platform.ohos.knoi.isArrayBuffer
import platform.ohos.knoi.isTypedArray
import platform.ohos.knoi.setElementInArray
import platform.ohos.knoi.typeOf
import platform.ohos.napi_env
import platform.ohos.napi_typedarray_type
import platform.ohos.napi_value
import platform.ohos.napi_valuetype
import platform.posix.int32_tVar
import kotlin.reflect.KClass

open class ArrayTypeConverter : TypeConverter<Array<Any?>> {
    override fun convertJSValueToKotlinValue(env: napi_env?, value: napi_value?): Array<Any?>? {
        if (typeOf(env, value) == napi_valuetype.napi_undefined) {
            return null
        }
        if (isArrayBuffer(env, value) && !isTypedArray(env, value)) {
            throw UnSupportTypeException(
                "ArrayBuffer cannot be converted to Array/List. " +
                    "Use ArrayBuffer, or pass a JS Array / TypedArray."
            )
        }
        if (isTypedArray(env, value)) {
            return convertTypedArrayToKotlinArray(env, value)
        }
        val length = getArrayLength(env, value).toInt()
        val result = Array<Any?>(length) {}
        if (length == 0) {
            return result
        }
        for (index in 0 until length) {
            val element = getElementInArray(env, value, index)
            val ktElement =
                getFirstSupportConverter(env, element).convertJSValueToKotlinValue(env, element)
            result[index] = (ktElement)
        }
        return result
    }

    override fun convertKotlinValueToJSValue(env: napi_env?, value: Array<Any?>?): napi_value? {
        if (value == null) {
            return null
        }
        val array = createArray(env) ?: return null
        value.forEachIndexed { index, element ->
            val jsElement = if (element == null) {
                null
            } else {
                ktValueToJSValue(env, element, element::class)
            }
            setElementInArray(env, array, index, jsElement)
        }
        return array
    }

    override fun isSupportKType(type: KClass<out Any>): Boolean {
        return type == getKType()
    }

    override fun getKType(): KClass<out Any> {
        return Array::class
    }
}

/**
 * Length of a JS Array or TypedArray. [getArrayLength] is only valid for real JS arrays;
 * TypedArray must use [getTypeArrayLength] or conversion yields an empty/zero-filled result.
 */
internal fun getJsArrayLikeLength(env: napi_env?, value: napi_value?): Int {
    if (isTypedArray(env, value)) {
        return getTypeArrayLength(env, value).toInt()
    }
    return getArrayLength(env, value).toInt()
}

/**
 * Convert TypedArray to a Kotlin array of boxed numbers.
 *
 * Int32Array is read as i32. Float64Array / other views are read by index as JS Number
 * (Double). Do not reinterpret Float64 bytes as i32 — small integers are 0 in the low word.
 */
internal fun convertTypedArrayToKotlinArray(env: napi_env?, value: napi_value?): Array<Any?> {
    val typed = getTypeArrayType(env, value)
    if (typed == napi_typedarray_type.napi_int32_array) {
        val count = getTypeArrayLength(env, value).toInt()
        val data = getTypeArrayValue(env, value)?.reinterpret<int32_tVar>()
            ?: throw UnSupportTypeException("Int32Array has no data pointer")
        return Array(count) { index -> data[index] }
    }
    val length = getTypeArrayLength(env, value).toInt()
    return Array(length) { index ->
        val element = getElementInArray(env, value, index)
        getFirstSupportConverter(env, element).convertJSValueToKotlinValue(env, element)
    }
}
