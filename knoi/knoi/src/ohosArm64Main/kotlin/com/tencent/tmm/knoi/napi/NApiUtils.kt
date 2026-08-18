package com.tencent.tmm.knoi.napi

import com.tencent.tmm.knoi.converter.coerceJsNumber
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import platform.ohos.napi_callback
import platform.ohos.napi_default
import platform.ohos.napi_define_properties
import platform.ohos.napi_env
import platform.ohos.napi_property_descriptor
import platform.ohos.napi_value
import kotlin.reflect.KClass

/**
 * 往 export 对象定义一个方法
 * @param env napi_env 环境
 * @param export 需注入方法的对象
 * @param properties 方法名
 * @param func 方法回调
 */
fun defineFunctionToExport(env: napi_env, export: napi_value, properties: String, func: napi_callback?) {
    memScoped {
        val desc = cValue<napi_property_descriptor> {
            utf8name = properties.cstr.getPointer(this@memScoped)
            name = null
            method = func
            getter = null
            setter = null
            value = null
            attributes = napi_default
            data = null
        }
        napi_define_properties(env, export, 1u, desc)
    }
}

/**
 * JS Value Number 类型映射为多个数值类型（Int, Long, Double）
 * 在已知参数类型 KClass 时，对类型进行转换
 */
fun safeCaseNumberType(value: Any?, type: KClass<out Any>): Any? {
    return coerceJsNumber(value, type)
}