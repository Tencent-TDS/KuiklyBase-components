package com.tencent.tmm.knoi.sample

import com.tencent.tmm.knoi.annotation.Hidden
import com.tencent.tmm.knoi.annotation.ServiceProvider
import com.tencent.tmm.knoi.converter.ktValueToJSValue
import com.tencent.tmm.knoi.getEnv
import com.tencent.tmm.knoi.logger.info
import com.tencent.tmm.knoi.type.ArrayBuffer
import com.tencent.tmm.knoi.type.JSValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.set
import platform.posix.uint8_tVar
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.reflect.KClass

private var logImpl: JSValue? = null
private var status = true

@ServiceProvider(bind = TestServiceBApi::class, singleton = false)
open class TestServiceB : TestServiceBApi {

    init {
        info("TestServiceB init.")
    }

    // 传入 Int 返回 Int
    @OptIn(NativeRuntimeApi::class)
    override fun methodWithIntReturnInt(a: Int): Int {
        info("knoi-samplejsvalue methodWithIntReturnInt call info")
        logImpl?.callMethod<Unit>("info", "knoi-sample methodWithIntReturnInt logimpl")
        return a + 1
    }

    // 传入 Long 返回 Long
    override fun methodWithLongReturnLong(a: Long): Long {
        return a + 1
    }

    // 传入 Boolean 返回 Boolean
    override fun methodWithBooleanReturnBoolean(a: Boolean): Boolean {
        return !a
    }

    // 传入 Boolean 返回 Double
    override fun methodWithDoubleReturnDouble(a: Double): Double {
        return a + 0.1
    }

    // 传入 String 返回 String
    override fun methodWithStringReturnString(a: String): String {
        return a + " modify from KMM"
    }

    // 传入 回调 返回 回调
    @OptIn(ExperimentalForeignApi::class)
    override fun methodWithCallbackReturnCallback(a: (Array<JSValue>) -> Unit): ((Array<JSValue>) -> String) {
        a.invoke(arrayOf(JSValue(ktValueToJSValue(getEnv(), "test", String::class))))
        return {
            "callback result"
        }
    }

    // 传入 自定义回调 返回 自定义回调
    override fun methodWithCallbackReturnCallback2(
        a: (String, Int) -> Unit, b: String, c: Int
    ): ((String, Int) -> String) {
        a.invoke(b, c)
        return { name, age ->
            "callback result"
        }
    }

    // 传入 自定义回调 返回 String
    override fun methodWithCallbackReturnCallback3(
        a: (String, Int) -> String, b: (String, Int) -> String
    ): String {
        a.invoke("callback result", 42)
        b.invoke("callback result", 42)
        return "callback result"
    }

    // 传入 String 数组 返回 String数组
    override fun methodWithArrayStringReturnArrayString(a: Array<String>): Array<String> {
        val list = a.toMutableList()
        list.add("plus in KMM")
        return list.toTypedArray()
    }

    // 传入 JS 对象 返回 JS 对象
    override fun methodWithMapReturnMap(a: Map<String, Any?>): Map<String, Any?> {
        val map = a.toMutableMap()
        map.forEach {
            info("knoi-sample map ${it.key} ${it.value}")
        }
        map["kmm"] = "push in KMM"
        return a.toMap()
    }

    // 无参数调用
    override fun methodWithUnit() {
        info("TestServiceB methodWithUnit")
    }

    // 传入任意 JS 类型，返回任意 JS 类型
    override fun methodWithJSValueReturnJSValue(a: JSValue): JSValue {
        val arrayJSValue = a["array"]
        if (arrayJSValue != null) {
            val list = arrayJSValue.toList<String>()
            list?.forEach {
                info("TestServiceB methodWithJSValueReturnJSValue toList $it")
            }
        }
        return a["json"]!!
    }

    // 传入二进制数据返回二进制数据
    @OptIn(ExperimentalForeignApi::class)
    override fun methodWithArrayBufferReturnArrayBuffer(buffer: ArrayBuffer): ArrayBuffer {
        info("knoi-sample methodWithArrayBufferReturnArrayBuffer")
        val bufferArray = buffer.getData<uint8_tVar>()
        bufferArray?.set(4, 4u)
        bufferArray?.set(5, 5u)
        bufferArray?.set(6, 6u)
        bufferArray?.set(7, 7u)
        return buffer
    }

    // 多参数
    override fun method3Params(a: String, b: Int, c: JSValue): JSValue {
        return c
    }

    override fun methodWithMultiTypeMap(map: Map<String, Any?>): Map<String, Any?> {
        var arrayBuffer = map["arrayBuffer"] as ArrayBuffer
        var typedBuffer = map["typedBuffer"] as ArrayBuffer
        var function = map["callback"] as (Array<JSValue>) -> String
        var obj = map["object"] as Map<String, *>
        return map
    }

    override fun methodWithDefaultValueInSubType(a: Int, b: String) {
        info("knoi-sample methodWithDefaultValue $a $b")
    }

    fun methodWithDefaultValue(a: Int = 42, b: String = "default value") {
        info("knoi-sample methodWithDefaultValue $a $b")
    }

    // 隐藏方法，不生成到 d.ts
    @Hidden
    override fun method3(str: KClass<Any>): String {
        return "Hidden"
    }

    // private 方法，不生成到 d.ts
    private fun method4(str: KClass<Any>): String {
        return "Hidden"
    }

    fun testJSValueRef(jsValue: JSValue) {
        if (status) {
            info("knoi-sample testJSValueRef obtain JSValue")
            logImpl = jsValue["impl"]!!
            status = false
        } else {
            info("knoi-sample testJSValueRef release JSValue")
            logImpl = null
            status = true
        }
    }
}

//@OptIn(ExperimentalNativeApi::class)
//class Test1 {
//    val resource: Resource = Resource()
//
//    init {
////        createCleaner(resource) {
////            info("knoi-sample test clean. clean resource construct ${it.hashCode()}")
////        }
////        createCleaner(this) {
////            info("knoi-sample test clean. clean this construct ${it.hashCode()}")
////        }
//    }
//
//    private val clean = createCleaner(resource) {
//        info("knoi-sample test clean. clean resource ${it.hashCode()}")
//    }
////    private val clean1 = createCleaner(this) {
////        info("knoi-sample test clean. clean this ${it.hashCode()}")
////    }.freeze()
//}
//
//class Resource