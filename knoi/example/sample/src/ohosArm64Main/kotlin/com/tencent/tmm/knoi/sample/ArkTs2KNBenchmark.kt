@file:Suppress("UNCHECKED_CAST")

package com.tencent.tmm.knoi.sample

import com.tencent.tmm.knoi.annotation.KNExport
import com.tencent.tmm.knoi.logger.info
import com.tencent.tmm.knoi.type.ArrayBuffer
import com.tencent.tmm.knoi.type.JSValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import platform.ohos.LOG_APP
import platform.ohos.LOG_INFO
import platform.ohos.OH_LOG_Print
import platform.ohos.napi_typedarray_type
import platform.posix.int32_tVar
import platform.posix.uint8_tVar

/**
 * 业务定义
 */
@KNExport(name = "testStringReturnString")
fun testStringFunction(name: String): String {
    info(name)
    return name + "forKMM"
}

@KNExport(name = "test3ParamFunction")
fun test3ParamFunction(name: String, age: Int, height: Int): String {
    info(name)
    return name + "forKMM"
}

@KNExport
fun testVoidReturnString(): String {
    return "forKMM"
}

@KNExport
fun testVoidReturnVoid() {
    OH_LOG_Print(LOG_APP, LOG_INFO, 65416u, "knoi-sample", "testVoidReturnVoid");
}

@KNExport
fun testIntReturnInt(number: Int): Int {
    return number + 1
}

@KNExport
fun testLongReturnLong(number: Long): Long {
    return number + 1
}

@KNExport(name = "testBooleanReturnBoolean")
fun testBoolReturnBool(result: Boolean): Boolean {
    return !result
}

@KNExport
fun testDoubleReturnDouble(result: Double): Double {
    return result + 1
}

@KNExport
fun testJSValueReturnJSValue(value: JSValue): JSValue {
    info("testJSValueReturnJSValue")
    return value
}

//不支持的类型测试
//@KNExport
//fun testKClass(value: KClass<Any>): JSValue? {
//    info("testJSValueReturnJSValue")
//    return null
//}

@KNExport
fun testMapReturnMap(result: HashMap<String, Any?>): Map<String, Any?> {
    val value = result["name"] as String
    val age = result["age"] as Double
    result["name"] = value + "forKMM"
    result["age"] = age + 1

    val children = result["children"] as Map<String, Any?>
    val childrenName = children["name"] as String
    val childrenAge = children["age"] as Double
    val mutableMap = children.toMutableMap()
    mutableMap["name"] = childrenName + "forKMM"
    mutableMap["age"] = childrenAge + 1
    return mutableMap
}

@KNExport
fun testJSCallbackReturnVoid(function: (args: Array<out Any?>) -> Any) {
    info("knoi-sample testJSCallbackReturnVoid")
    function.invoke(arrayOf("result callback for kmm"))
}

@KNExport
fun testJSCallbackReturnString(function: (args: Array<out Any?>) -> Any) {
    info("knoi-sample testJSCallbackReturnString")
    val resultFromJS = function.invoke(arrayOf("result callback for kmm"))
    info("knoi-sample testJSCallbackReturnString result = $resultFromJS")
}

@KNExport
fun testJSCallbackReturnMap(function: (args: Array<out Any?>) -> Any) {
    info("knoi-sample testJSCallbackReturnMap")
    val map = mapOf<String, Any?>("name" to "KMM", "arg" to 42)
    val resultFromJS = function.invoke(arrayOf(map))
    info("knoi-sample testJSCallbackReturnMap result = $resultFromJS")
}

@KNExport
fun testJSCallbackReturnJSCallback(function: (args: Array<out Any?>) -> Any): (args: Array<JSValue?>) -> Any? {
    info("knoi-sample testJSCallbackReturnJSCallback")
    val map = mapOf<String, Any?>("name" to "KMM", "arg" to 42)
    val resultFromJS = function.invoke(arrayOf(map))
    info("knoi-sample testJSCallbackReturnJSCallback result = $resultFromJS")
    val funcWrapper: (args: Array<JSValue?>) -> Any? = { args ->
        val result = args[0]?.toKString()
        info("knoi-sample testJSCallbackReturnJSCallback funcWrapper $result ")
    }
    return funcWrapper
}

@KNExport
fun testListReturnList(array: List<Any?>): List<Any?> {
    info("knoi-sample testListReturnList $array")
    val kmmList = array.toMutableList()
    kmmList.add("LastAddInKMM")
    return kmmList
}

@KNExport
fun testArrayReturnArray(array: Array<Any?>): Array<Any?> {
    info("knoi-sample testArrayReturnArray $array")
    val kmmList = array.toMutableList()
    kmmList.add("LastAddInKMM")
    return kmmList.toTypedArray()
}

@KNExport
@OptIn(ExperimentalForeignApi::class)
fun testArrayBufferReturnArrayBuffer(buffer: ArrayBuffer): ArrayBuffer {
    info("knoi-sample testArrayBufferReturnArrayBuffer")
    val bufferArray = buffer.getData<uint8_tVar>()
    bufferArray?.set(4, 4u)
    bufferArray?.set(5, 5u)
    bufferArray?.set(6, 6u)
    bufferArray?.set(7, 7u)
    return buffer
}

@KNExport
@OptIn(ExperimentalForeignApi::class)
fun testVoidReturnArrayBufferInMap(): Map<String, *> {
    info("knoi-sample testVoidReturnArrayBufferInMap")
    val int32Buffer = nativeHeap.allocArray<int32_tVar>(8)
    int32Buffer[0] = 17
    int32Buffer[1] = 18
    int32Buffer[2] = 19
    int32Buffer[3] = 20
    val result = mutableMapOf<String, Any?>()
    result["data"] =
        ArrayBuffer(int32Buffer.reinterpret(), 8, type = napi_typedarray_type.napi_int32_array)
    return result
}


data class Rect(val l: Int, val r: Int, val t: Int, val b: Int)

@KNExport
fun testCustomClassWrap(): JSValue {
    val rect = Rect(1, 2, 3, 4)
    val rectJSValue = JSValue.wrap(rect)
    rectJSValue["getLeft"] = JSValue.createJSFunction<Int> { jsThis, params ->
        val nativeRect = JSValue.unwrap<Rect>(jsThis)
        val param1 = params[0]!!.toInt()
        return@createJSFunction nativeRect?.l!! + param1
    }
    return rectJSValue
}