@file:OptIn(ExperimentalForeignApi::class)

package com.tencent.tmm.knoi.sample

import com.tencent.tmm.knoi.annotation.KNExport
import com.tencent.tmm.knoi.converter.dumpAllJSCallback
import com.tencent.tmm.knoi.definder.getService
import com.tencent.tmm.knoi.definder.getServiceRegisterTid
import com.tencent.tmm.knoi.exception.JavaScriptException
import com.tencent.tmm.knoi.handler.cancelBlock
import com.tencent.tmm.knoi.handler.runOnMainThread
import com.tencent.tmm.knoi.jsbind.getJSFunction
import com.tencent.tmm.knoi.logger.debug
import com.tencent.tmm.knoi.logger.info
import com.tencent.tmm.knoi.mainTid
import com.tencent.tmm.knoi.type.ArrayBuffer
import com.tencent.tmm.knoi.type.JSValue
import com.tencent.tmm.knoi.type.asPromise
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.ohos.napi_typedarray_type
import platform.posix.int32_tVar
import platform.posix.uint8_tVar
import kotlin.native.concurrent.Worker

fun testDebugLog() {
    debug("testDebugLog")
    debug("testDebugLog" + getPrintLog(1))
    debug("testDebugLog ${getPrintLog(2)}")
}

fun getPrintLog(i: Int) = "xxx$i"

@OptIn(ExperimentalForeignApi::class)
@KNExport
fun testService() {
    testDebugLog()
    getTestServiceAApi().methodWithUnit()
    info("TestServiceA methodWithUnit")
    val intResult = getTestServiceAApi().methodWithIntReturnInt(42)
    info("TestServiceA methodWithIntReturnInt $intResult")
    val longResult = getTestServiceAApi().methodWithLongReturnLong(42L)
    info("TestServiceA methodWithLongReturnLong $longResult")
    val boolResult = getTestServiceAApi().methodWithBooleanReturnBoolean(true)
    info("TestServiceA methodWithBooleanReturnBoolean $boolResult")
    val doubleResult = getTestServiceAApi().methodWithDoubleReturnDouble(42.1)
    info("TestServiceA methodWithDoubleReturnDouble $doubleResult")
    val stringResult = getTestServiceAApi().methodWithStringReturnString("param1 from kmm")
    info("TestServiceA methodWithStringReturnString $stringResult")
    val arrayResult = getTestServiceAApi().methodWithArrayStringReturnArrayString(arrayOf("array1"))
    info("TestServiceA methodWithArrayStringReturnArrayString $arrayResult")
    val callbackResult = getTestServiceAApi().methodWithCallbackReturnCallback {
        val callbackParams1 = it[0]
        info("TestServiceA methodWithCallbackReturnCallback ${callbackParams1.toKString()}")
        return@methodWithCallbackReturnCallback
    }
    val param1 = JSValue.createJSValue("param1", tid = mainTid)
    val param2 = JSValue.createJSValue("param2", tid = mainTid)
    callbackResult.invoke(arrayOf(param1, param2))

    val callbackResult2 =
        getTestServiceAApi().methodWithCallbackReturnCallback2({ a: String, b: Double, c: Long, d: Int, e: Boolean ->
            info("TestServiceA methodWithCallbackReturnCallback2 $a $b $c $d $e")
            "callbackResult"
        }, "string", 42)
    val callbackResultResult = callbackResult2.invoke("functionResult callback")
    info("TestServiceA methodWithCallbackReturnCallback2 callbackResultResult = ${callbackResultResult}")

    val methodWithCallbackReturnCallback4Result =
        getTestServiceAApi().methodWithCallbackReturnCallback4 {
            info("TestServiceA methodWithCallbackReturnCallback4 typeAlias")
            "$it modify from kmm"
        }
    info("TestServiceA methodWithCallbackReturnCallback4 callbackResultResult = ${methodWithCallbackReturnCallback4Result}")

    val map = mapOf<String, Any?>("name" to "KMM", "arg" to 42)
    val mapResult = getTestServiceAApi().methodWithMapReturnMap(map)
    info("TestServiceA methodWithMapReturnMap $mapResult")

    val int32Buffer = nativeHeap.allocArray<int32_tVar>(8)
    int32Buffer[0] = 17
    int32Buffer[1] = 18
    int32Buffer[2] = 19
    int32Buffer[3] = 20
    // 如非 uint8_tVar 需调用reinterpret，并传入 napi_typedarray_type
    val arrayBufferInt32 =
        ArrayBuffer(int32Buffer.reinterpret(), 8, type = napi_typedarray_type.napi_int32_array)
    val int32Result = getTestServiceAApi().methodWithArrayBufferReturnArrayBuffer(arrayBufferInt32)
    if (int32Result != null) {
        val buffer = int32Result.getData<int32_tVar>()
        info(
            "knoi-sample TestServiceA methodWithArrayBufferReturnArrayBuffer ${buffer?.get(4)} ${buffer?.get(5)} ${
                buffer?.get(
                    6
                )
            } ${buffer?.get(7)} "
        )
    } else {
        info(
            "knoi-sample TestServiceA methodWithArrayBufferReturnArrayBuffer return null"
        )
    }

    val byteArray = ByteArray(8)
    byteArray[0] = 1
    byteArray[1] = 2
    byteArray[2] = 3
    byteArray[3] = 4
    val int8Result = getTestServiceAApi().methodWithArrayBufferReturnArrayBufferUseByteArray(
        ArrayBuffer(byteArray)
    )
    if (int8Result != null) {
        val buffer = int8Result.getByteArray()
        info("TestServiceA methodWithArrayBufferReturnArrayBufferUseByteArray ${buffer[4]} ${buffer[5]} ${buffer[6]} ${buffer[7]} ")
    }

    val jsValueResult =
        getTestServiceAApi().methodWithJSValueReturnJSValue(JSValue.createJSObject(tid = mainTid))
    info("TestServiceA methodWithJSValueReturnJSValue ${jsValueResult["json"]}")

    val jsValueResult2 =
        getTestServiceAApi().method3Params("param1", 42, JSValue.createJSObject(tid = mainTid))
    info("TestServiceA method3Params ${jsValueResult2["json"]}")

    try {
        val noUseResult = getTestServiceAApi().methodWithException("methodWithException param1")
        info("TestServiceA methodWithException never Run this line.")
    } catch (e: JavaScriptException) {
        info("TestServiceA methodWithException: message = ${e.message}")
        info("TestServiceA methodWithException: JavaScriptTopStackTrace = ${e.getJavaScriptTopStackTrace()}")
        info(
            "TestServiceA methodWithException: JavaScriptStackTrace = ${
                e.getJavaScriptStackTrace()?.joinToString("\n")
            }"
        )
    }

    getTestServiceAApi().methodWithPromise("input").asPromise().then {
        val result = it[0]
        info("TestServiceA methodWithPromise promise then isUndefined = ${result.isUndefined()}, isObject = ${result.isObject()}")
        return@then result
    }.catch {
        info("TestServiceA methodWithPromise promise catch error")
    }

    info("SingletonTestServiceA method1")
    getSingletonTestServiceAApi().method1("param1")
    info("SingletonTestServiceA method2")
    getSingletonTestServiceAApi().method2("param1")
    info("SingletonTestServiceA method1")
    getSingletonTestServiceAApi().method1("param1")
    info("SingletonTestServiceA method2")
    getSingletonTestServiceAApi().method2("param1")

    // KMM 同层调用
    getService<TestServiceBApi>().methodWithUnit()

    // Service In Common
    val result =
        getService<TestServiceInCommonApi>("TestServiceBInCommon").methodWithIntReturnInt(42)
    info("TestServiceBInCommon methodWithIntReturnInt result = $result")
}

@KNExport
fun testJSValue() {
    info("knoi-sample testJSValue")
    val global = JSValue.global(tid = mainTid)
    val map = mapOf<String, Any?>("name" to "KMM", "arg" to 42)
    val result = global["JSON"]?.callMethod<String>("stringify", map)
    info("knoi-sample stringify result = $result")
}

@KNExport
fun testCoroutines() {
    val ioScope = CoroutineScope(Dispatchers.IO)
    val mainScope = CoroutineScope(Dispatchers.Main)
    ioScope.launch {
        info("knoi-sample io dispatcher.")
        mainScope.launch {
            info("knoi-sample main dispatcher.")
        }
    }

    ioScope.launch {
        info("knoi-sample io dispatcher delay 1000.")
        delay(1000)
        info("knoi-sample io dispatcher delay 1000 run.")
    }

    ioScope.launch {
        val deferredResult: Deferred<Int> = async { // 使用 async 启动一个新的协程
            delay(1000L) // 假设这是一个耗时的计算
            42 // 返回计算结果
        }

        info("knoi-sample waiting for the result...")
        // 等待异步计算的结果
        val result: Int = deferredResult.await()
        info("knoi-sample async result: $result")
    }

    ioScope.launch {
        val job = ioScope.launch {
            repeat(10) { i ->
                info("knoi-sample: job run $i ...")
                delay(1000L)
            }
        }
        delay(3000L)
        job.cancelAndJoin()
        info("knoi-sample: cancel and join.")
    }

    ioScope.launch {
        var result = 0
        try {
            result = withTimeout(300) {
                repeat(5) {
                    delay(100)
                }
                200
            }
        } catch (e: Exception) {
            info("with Timeout Exception")
        }
        info("with Timeout $result")

        GlobalScope.launch {
            withContext(Dispatchers.Main) {
                info("knoi-sample mainScope111")
            }
        }
    }

    GlobalScope.launch {
        withContext(Dispatchers.Main) {
            info("knoi-sample mainScope111")
        }
    }

    ioScope.launch {
        repeat(Int.MAX_VALUE) {
            dumpAllJSCallback()
            delay(2000)
        }
    }
}

@KNExport
fun testMainHandler() {
    info("knoi-sample testMainHandler")
    Worker.start().executeAfter {
        info("knoi-sample testMainHandler sub thread.")
        runOnMainThread {
            info("knoi-sample runOnMainThread run")
        }
        info("knoi-sample runOnMainThread delay 1000")
        runOnMainThread({
            info("knoi-sample runOnMainThread delay 1000 run 111")
        }, 1000)

        val block = {
            info("knoi-sample runOnMainThread cancel error, delay 2000 run")
        }
        info("knoi-sample runOnMainThread ${block.hashCode()} delay 2000")
        runOnMainThread(block, 2000)
        cancelBlock(block)
        info("knoi-sample cancelBlock ${block.hashCode()}")
    }
    info("knoi-sample testMainHandler main thread.")
    runOnMainThread {
        info("knoi-sample runOnMainThread run")
    }
    info("knoi-sample runOnMainThread delay 1000")
    runOnMainThread({
        info("knoi-sample runOnMainThread delay 1000 run 222")
    }, 1000)

}


@OptIn(DelicateCoroutinesApi::class)
@KNExport("testLoadBalance")
fun testLoadBalance() {
    GlobalScope.launch {
        val result = getTestServiceAInWorkerApi().methodWithCallbackReturnCallback {
            info("methodWithCallbackReturnCallback:" + it.first().toKString())
        }
        info(
            "knoi-sample getTestServiceAInWorkerApi result = ${
                result.invoke(
                    arrayOf(
                        JSValue.createJSValue(
                            "123",
                            getServiceRegisterTid("TestServiceAInWorker")
                        ), JSValue.createJSValue(
                            "234",
                            getServiceRegisterTid("TestServiceAInWorker")
                        )
                    )
                )
            }"
        )
    }
    val result = getTestServiceAInWorkerApi().methodWithCallbackReturnCallback {
        info("methodWithCallbackReturnCallback:" + it.first().toKString())
    }
    info(
        "knoi-sample getTestServiceAInWorkerApi result = ${
            result.invoke(
                arrayOf(
                    JSValue.createJSValue(
                        "456"
                    ), JSValue.createJSValue("567")
                )
            )
        }"
    )

}

@OptIn(ExperimentalForeignApi::class, DelicateCoroutinesApi::class)
@KNExport
fun testJSFunction() {
    //TEST CODE


    val jsCallback = getJSFunction("testJsCallbackReturnVoid")
    jsCallback?.invoke<Unit>({ result: Array<JSValue> ->
        info("knoi-sample testJsCallbackReturnVoid result = ${result[0].toKString()}")
    })

    val strResult = getJSFunction("testStringReturnStringForKMM")?.invoke<String>("KMM")
    info("knoi-sample testStringReturnStringForKMM result = $strResult")
    val uint8ArrayResult = getJSFunction("testUint8ArrayReturnVoidForKMM")?.invoke<ArrayBuffer>()
    info("knoi-sample testUint8ArrayReturnVoidForKMM result = ${uint8ArrayResult?.getCount()}")
    val doubleResult = getJSFunction("testNumberReturnNumberForKMM")?.invoke<Double>(42.0)
    info("knoi-sample testNumberReturnNumberForKMM result = $doubleResult")
    val boolResult = getJSFunction("testBooleanReturnBooleanForKMM")?.invoke<Boolean>(true)
    info("knoi-sample testBooleanReturnBooleanForKMM result = $boolResult")
    getJSFunction("testVoidReturnVoidForKMM")?.invoke<Unit>()

    val map = mapOf<String, Any?>("name" to "KMM", "arg" to 42)
    val mapResult =
        getJSFunction("testObjectReturnObjectForKMM")?.invoke<HashMap<String, Any?>>(map)
    info("knoi-sample testObjectReturnObjectForKMM result = $mapResult")

    val buffer = nativeHeap.allocArray<uint8_tVar>(8)
    buffer[0] = 0u
    buffer[1] = 1u
    buffer[2] = 2u
    buffer[3] = 3u
    val arrayBuffer = ArrayBuffer(buffer, 8, type = napi_typedarray_type.napi_uint8_array)
    val arrayBufferResult =
        getJSFunction("testArrayBufferReturnArrayBufferForKMM")?.invoke<ArrayBuffer>(arrayBuffer)!!
            .getData<uint8_tVar>()

    info(
        "knoi-sample testArrayBufferReturnArrayBufferForKMM result = ${arrayBufferResult?.get(4)} " + "${
            arrayBufferResult?.get(
                5
            )
        } ${arrayBufferResult?.get(6)} ${arrayBufferResult?.get(7)}"
    )

    val int32Buffer = nativeHeap.allocArray<int32_tVar>(8)
    int32Buffer[0] = 17
    int32Buffer[1] = 18
    int32Buffer[2] = 19
    int32Buffer[3] = 20
    val arrayBufferInt32 =
        ArrayBuffer(int32Buffer.reinterpret(), 8, type = napi_typedarray_type.napi_int32_array)
    val arrayBufferInt32Result =
        getJSFunction("testArrayBufferInt32ReturnArrayBufferInt32ForKMM")?.invoke<ArrayBuffer>(
            arrayBufferInt32
        )?.getData<int32_tVar>()
    info(
        "knoi-sample testArrayBufferInt32ReturnArrayBufferInt32ForKMM result = ${
            arrayBufferInt32Result?.get(
                4
            )
        } " + "${arrayBufferInt32Result?.get(5)} ${arrayBufferInt32Result?.get(6)} ${
            arrayBufferInt32Result?.get(
                7
            )
        }"
    )

    Worker.start().executeAfter {
        testJSFunctionSubThread()
    }
}

fun testJSFunctionSubThread() {
    info("knoi-sample Dispatchers.IO in sub thread")
    val jsFunction = getJSFunction("testStringReturnStringForKMM");
    info("knoi-sample getJSFunction testStringReturnStringForKMM in sub thread")
    val result = jsFunction?.invoke<String>("KMM")
    info("knoi-sample invoke testStringReturnStringForKMM $result in sub thread")
//    jsFunction?.invokeAsync<String>("KMM")
//    info("knoi-sample invokeAsync testStringReturnStringForKMM in sub thread")
}

@KNExport
fun testServiceInSubThread() {
    info("knoi-sample testServiceInSubThread in sub thread")
    Worker.start().executeAfter {
        testService()
    }
}

@KNExport
fun testJSValueInSubThread() {
    info("knoi-sample testJSValueInSubThread in sub thread")
    Worker.start().executeAfter {
        testJSValue()
    }
}

@KNExport
fun testServiceInJSWorker() {
    info("knoi-sample testServiceInJSWorker.")
    getTestServiceAInWorkerApi().methodWithUnit()
}