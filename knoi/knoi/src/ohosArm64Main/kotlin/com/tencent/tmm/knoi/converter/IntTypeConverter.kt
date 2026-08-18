package com.tencent.tmm.knoi.converter

import platform.ohos.knoi.convertIntToNapiValue
import platform.ohos.knoi.toDouble
import platform.ohos.napi_env
import platform.ohos.napi_value
import kotlin.reflect.KClass

class IntTypeConverter : TypeConverter<Int> {
    override fun convertJSValueToKotlinValue(env: napi_env?, value: napi_value?): Int {
        // JS Number is IEEE-754. Convert via Number then toInt(); napi_get_value_int32
        // can leave 0 for ArkTS heap numbers (see issue #25 Array<Int> all zeros).
        return toDouble(env, value).toInt()
    }

    override fun getKType(): KClass<out Any> = Int::class

    override fun convertKotlinValueToJSValue(env: napi_env?, value: Int?): napi_value? {
        if (value == null) {
            return null
        }
        return convertIntToNapiValue(env, value)
    }
}