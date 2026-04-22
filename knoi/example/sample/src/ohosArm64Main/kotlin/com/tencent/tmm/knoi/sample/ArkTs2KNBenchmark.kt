@file:Suppress("UNCHECKED_CAST")

package com.tencent.tmm.knoi.sample

import com.tencent.tmm.knoi.getCurrentAsyncInvokeOwnerTid
import com.tencent.tmm.knoi.getTid
import com.tencent.tmm.knoi.annotation.KNExport
import com.tencent.tmm.knoi.annotation.KNExportRetPromise
import com.tencent.tmm.knoi.logger.info
import com.tencent.tmm.knoi.type.ArrayBuffer
import com.tencent.tmm.knoi.type.JSValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.ohos.LOG_APP
import platform.ohos.LOG_INFO
import platform.ohos.OH_LOG_Print
import platform.ohos.napi_typedarray_type
import platform.posix.free
import platform.posix.int32_tVar
import platform.posix.uint8_tVar
import kotlin.time.TimeSource

private var storedNativeOwnedArrayBufferForExportTest: ArrayBuffer? = null

private val benchmarkResourcePackages = listOf(
    "home-page.zip",
    "checkout-assets.zip",
    "feed-media.zip",
    "profile-skins.zip"
)

private fun ArrayBuffer.byteValues(): List<Int> {
    return getByteArray().map { it.toInt() and 0xFF }
}

@OptIn(ExperimentalForeignApi::class)
private fun ArrayBuffer.writeBytes(vararg values: UByte) {
    val pointer = getData<uint8_tVar>() ?: return
    val maxCount = minOf(values.size, getByteLength().toInt())
    for (index in 0 until maxCount) {
        pointer[index] = values[index]
    }
}

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

@KNExportRetPromise
fun testAsyncIntReturnInt(number: Int): Int {
    return number + 100
}

@KNExportRetPromise
fun testAsyncVoidReturnVoid() {
    info("testAsyncVoidReturnVoid")
}

@KNExportRetPromise
fun testAsyncVoidReturnJSValue(): JSValue = runBlocking {
    val ownerTid = getCurrentAsyncInvokeOwnerTid() ?: getTid()
    val ioScope = CoroutineScope(Dispatchers.IO)
    ioScope.async {
        val ioTid = getTid()
        info("testAsyncVoidReturnJSValue ownerTid=$ownerTid ioTid=$ioTid")
        val result = JSValue.createJSObject(tid = ownerTid)
        result["type"] = JSValue.createJSValue("async-void-jsvalue", tid = ownerTid)
        result["ownerTid"] = JSValue.createJSValue(ownerTid, tid = ownerTid)
        result["workerTid"] = JSValue.createJSValue(ioTid, tid = ownerTid)
        result["message"] = JSValue.createJSValue(
            "created with JS owner tid from async context",
            tid = ownerTid
        )
        result
    }.await()
}

@KNExportRetPromise
fun testAsyncJSCallbackReturnString(function: (args: Array<out Any?>) -> Any): String {
    val result = function.invoke(arrayOf("async callback from KMM"))
    return if (result is JSValue) {
        result.toKString().orEmpty()
    } else {
        result.toString()
    }
}


@KNExportRetPromise
fun testAsyncJSValueReturnJSValue(value: JSValue): JSValue {
    info("testAsyncJSValueReturnJSValue")
    val tid = value.tid
    val result = JSValue.createJSObject(tid = tid)
    result["original"] = value
    result["type"] = JSValue.createJSValue(
        when {
            value.isArrayBuffer() -> "arrayBuffer"
            value.isArrayType() -> "array"
            value.isString() -> "string"
            value.isNumber() -> "number"
            value.isBoolean() -> "boolean"
            value.isObject() -> "object"
            else -> "unknown"
        },
        tid = tid
    )
    result["description"] = JSValue.createJSValue(
        when {
            value.isString() -> value.toKString().orEmpty().uppercase()
            value.isNumber() -> "number:${value.toDouble() + 10}"
            value.isBoolean() -> "boolean:${!value.toBoolean()}"
            value.isArrayBuffer() -> "bytes:${value.toArrayBuffer().getByteLength()}"
            value.isArrayType() -> "array"
            value.isObject() -> "object"
            else -> "unsupported"
        },
        tid = tid
    )
    if (value.isObject() && !value.isArrayType() && !value.isArrayBuffer()) {
        val inputName = value["name"]?.toKString().orEmpty()
        val inputCount = value["count"]?.toInt() ?: 0
        val inputEnabled = value["enabled"]?.toBoolean() ?: false
        result["name"] = JSValue.createJSValue("${inputName}-processed", tid = tid)
        result["count"] = JSValue.createJSValue(inputCount + 100, tid = tid)
        result["enabled"] = JSValue.createJSValue(!inputEnabled, tid = tid)
    }
    return result
}

@KNExportRetPromise
fun testAsyncListJSValueReturnList(values: List<JSValue>): List<JSValue> {
    info("testAsyncListJSValueReturnList size=${values.size}")
    return values
}

@KNExportRetPromise
fun testAsyncMapArrayBufferReturnMap(values: Map<String, ArrayBuffer>): Map<String, ArrayBuffer> {
    info("testAsyncMapArrayBufferReturnMap size=${values.size}")
    return values
}

@KNExportRetPromise
fun testDownloadResourcePackagesInKotlin(): Map<String, Any?> = runBlocking {
    val callerTid = getTid()
    val startedAt = TimeSource.Monotonic.markNow()
    info(
        "knoi-sample testDownloadResourcePackagesInKotlin start " +
            "callerTid=$callerTid packageCount=${benchmarkResourcePackages.size}"
    )
    val ioScope = CoroutineScope(Dispatchers.IO)
    val downloadTasks = benchmarkResourcePackages.mapIndexed { index, packageName ->
        ioScope.async {
            downloadResourcePackage(packageName, index)
        }
    }
    val downloads = downloadTasks.map { it.await() }
    val workerTids = downloads
        .flatMap { download ->
            listOf(
                download["startTid"] as Int,
                download["finishTid"] as Int
            )
        }
        .distinct()
    val totalBytes = downloads.sumOf { it["bytes"] as Long }
    val elapsedMs = startedAt.elapsedNow().inWholeMilliseconds
    val result = mapOf(
        "callerTid" to callerTid,
        "workerTids" to workerTids,
        "packageCount" to downloads.size,
        "totalBytes" to totalBytes,
        "elapsedMs" to elapsedMs,
        "downloads" to downloads
    )
    info("knoi-sample testDownloadResourcePackagesInKotlin finish result=$result")
    result
}

private suspend fun downloadResourcePackage(packageName: String, index: Int): Map<String, Any?> {
    val startTid = getTid()
    var downloadedBytes = 0L
    var checksum = 0L
    info("knoi-sample downloadResourcePackage start name=$packageName tid=$startTid")
    repeat(6) { chunkIndex ->
        delay(120L + index * 35L + chunkIndex * 10L)
        val chunkBytes = (index + 1L) * 64L * 1024L + chunkIndex * 4L * 1024L
        downloadedBytes += chunkBytes
        checksum = (checksum * 31L + chunkBytes + packageName.length + chunkIndex) % 1_000_000_007L
    }
    val finishTid = getTid()
    info(
        "knoi-sample downloadResourcePackage finish " +
            "name=$packageName bytes=$downloadedBytes checksum=$checksum startTid=$startTid finishTid=$finishTid"
    )
    return mapOf(
        "name" to packageName,
        "bytes" to downloadedBytes,
        "checksum" to checksum,
        "startTid" to startTid,
        "finishTid" to finishTid
    )
}

@KNExportRetPromise
@OptIn(ExperimentalForeignApi::class)
fun testAsyncArrayBufferReturnArrayBuffer(buffer: ArrayBuffer): ArrayBuffer {
    info("testAsyncArrayBufferReturnArrayBuffer")
    buffer.getData<uint8_tVar>()?.set(0, 9u)
    return buffer
}

@KNExportRetPromise
@OptIn(ExperimentalForeignApi::class)
fun testAsyncArrayBufferReturnArrayBuffer2(buffer: ArrayBuffer): ArrayBuffer {
    info("testAsyncArrayBufferReturnArrayBuffer2 inputByteLength=${buffer.getByteLength()}")
    val nativeBuffer = nativeHeap.allocArray<uint8_tVar>(4)
    nativeBuffer[0] = 11u
    nativeBuffer[1] = 12u
    nativeBuffer[2] = 13u
    nativeBuffer[3] = 14u
    return try {
        ArrayBuffer(nativeBuffer, 4)
    } finally {
        free(nativeBuffer)
    }
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
fun testCreateNativeOwnedArrayBufferForExternal(): ArrayBuffer {
    val buffer = ArrayBuffer(byteArrayOf(21, 22, 23, 24))
    storedNativeOwnedArrayBufferForExportTest = buffer
    info(
        "knoi-sample testCreateNativeOwnedArrayBufferForExternal " +
            "nativeOwned=${buffer.isNativeOwned()} bytes=${buffer.byteValues()}"
    )
    return buffer
}

@KNExport
@OptIn(ExperimentalForeignApi::class)
fun testMutateStoredNativeOwnedArrayBufferForExternal(): ArrayBuffer {
    val buffer = storedNativeOwnedArrayBufferForExportTest ?: return ArrayBuffer()
    buffer.writeBytes(51u, 52u, 53u, 54u)
    info(
        "knoi-sample testMutateStoredNativeOwnedArrayBufferForExternal " +
            "nativeOwned=${buffer.isNativeOwned()} bytes=${buffer.byteValues()}"
    )
    return buffer
}

@KNExport
@OptIn(ExperimentalForeignApi::class)
fun testVerifyArrayBufferOwnershipOps(buffer: ArrayBuffer): Map<String, Any?> {
    val ensured = buffer.ensureNativeOwned()
    val detached = buffer.toDetachedCopy()
    ensured.writeBytes(101u, 102u, 103u, 104u)
    detached.writeBytes(111u, 112u, 113u, 114u)
    return mutableMapOf(
        "inputNativeOwned" to buffer.isNativeOwned(),
        "ensuredNativeOwned" to ensured.isNativeOwned(),
        "ensuredSameInstance" to (buffer === ensured),
        "detachedNativeOwned" to detached.isNativeOwned(),
        "detachedSameInstance" to (buffer === detached),
        "originalBytes" to buffer.byteValues(),
        "ensuredBytes" to ensured.byteValues(),
        "detachedBytes" to detached.byteValues()
    )
}

@KNExport
@OptIn(ExperimentalForeignApi::class)
fun testVerifyNativeOwnedArrayBufferOwnershipOps(): Map<String, Any?> {
    val source = ArrayBuffer(byteArrayOf(31, 32, 33, 34))
    val ensured = source.ensureNativeOwned()
    val detached = source.toDetachedCopy()
    ensured.writeBytes(91u, 92u, 93u, 94u)
    detached.writeBytes(131u, 132u, 133u, 134u)
    return mutableMapOf(
        "sourceNativeOwned" to source.isNativeOwned(),
        "ensuredNativeOwned" to ensured.isNativeOwned(),
        "ensuredSameInstance" to (source === ensured),
        "detachedNativeOwned" to detached.isNativeOwned(),
        "detachedSameInstance" to (source === detached),
        "sourceBytes" to source.byteValues(),
        "ensuredBytes" to ensured.byteValues(),
        "detachedBytes" to detached.byteValues()
    )
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
        ArrayBuffer(
            int32Buffer.reinterpret(),
            8L * Int.SIZE_BYTES,
            type = napi_typedarray_type.napi_int32_array
        )
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
