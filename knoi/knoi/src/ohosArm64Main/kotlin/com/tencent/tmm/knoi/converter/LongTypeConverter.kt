package com.tencent.tmm.knoi.converter

import platform.ohos.knoi.convertLongToNapiValue
import platform.ohos.knoi.toDouble
import platform.ohos.napi_env
import platform.ohos.napi_value
import kotlin.reflect.KClass

class LongTypeConverter : TypeConverter<Long> {
    override fun convertJSValueToKotlinValue(env: napi_env?, value: napi_value?): Long {
        return toDouble(env, value).toLong()
    }

    override fun getKType(): KClass<out Any> = Long::class

    override fun convertKotlinValueToJSValue(env: napi_env?, value: Long?): napi_value? {
        if (value == null) {
            return null
        }
        return convertLongToNapiValue(env, value)
    }
}