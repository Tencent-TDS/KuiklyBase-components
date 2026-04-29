package com.tencent.tmm.knoi.sample

import com.tencent.tmm.knoi.annotation.Hidden
import com.tencent.tmm.knoi.annotation.KNMethodRetPromise
import com.tencent.tmm.knoi.type.ArrayBuffer
import com.tencent.tmm.knoi.type.JSValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.reflect.KClass

interface TestServiceBApi {
    // 传入 Int 返回 Int
    fun methodWithIntReturnInt(a: Int): Int

    // 传入 Long 返回 Long
    fun methodWithLongReturnLong(a: Long): Long

    // 传入 Boolean 返回 Boolean
    fun methodWithBooleanReturnBoolean(a: Boolean): Boolean

    // 传入 Boolean 返回 Double
    fun methodWithDoubleReturnDouble(a: Double): Double

    // 传入 String 返回 String
    fun methodWithStringReturnString(a: String): String

    // 传入 回调 返回 回调
    @OptIn(ExperimentalForeignApi::class)
    fun methodWithCallbackReturnCallback(a: (Array<JSValue>) -> Unit): ((Array<JSValue>) -> String)

    // 传入 自定义回调 返回 自定义回调
    fun methodWithCallbackReturnCallback2(a: (String, Int) -> Unit, b: String, c: Int): ((String, Int) -> String)

    // 传入 自定义回调 返回 String
    fun methodWithCallbackReturnCallback3(a: (String, Int) -> String, b: (String, Int) -> String): String

    // 传入 String 数组 返回 String数组
    fun methodWithArrayStringReturnArrayString(a: Array<String>): Array<String>

    // 传入 JS 对象 返回 JS 对象
    fun methodWithMapReturnMap(a: Map<String, Any?>): Map<String, Any?>

    // 无参数调用
    fun methodWithUnit()

    // Promise 基础类型
    @KNMethodRetPromise
    fun methodWithPromiseString(a: String): String

    // Promise JSValue 输入输出
    @KNMethodRetPromise
    fun methodWithPromiseJSValueReturnJSValue(a: JSValue): JSValue

    // Promise 无参数返回 JSValue
    @KNMethodRetPromise
    fun methodWithPromiseUnitReturnJSValue(): JSValue

    // 传入任意 JS 类型，返回任意 JS 类型
    fun methodWithJSValueReturnJSValue(a: JSValue): JSValue

    // 传入二进制数据返回二进制数据
    @OptIn(ExperimentalForeignApi::class)
    fun methodWithArrayBufferReturnArrayBuffer(buffer: ArrayBuffer): ArrayBuffer

    // Promise 二进制数据输入输出
    @OptIn(ExperimentalForeignApi::class)
    @KNMethodRetPromise
    fun methodWithPromiseArrayBufferReturnArrayBuffer(buffer: ArrayBuffer): ArrayBuffer

    // 多参数
    fun method3Params(a: String, b: Int, c: JSValue): JSValue

    fun methodWithMultiTypeMap(map: Map<String, Any?>): Map<String, Any?>

    // Promise Map 容器类型输入输出
    @KNMethodRetPromise
    fun methodWithPromiseMapReturnMap(map: Map<String, Any?>): Map<String, Any?>

    fun methodWithDefaultValueInSubType(a: Int = 42, b: String = "default value in sub type")

    // 隐藏方法，不生成到 d.ts
    @Hidden
    fun method3(str: KClass<Any>): String

}
