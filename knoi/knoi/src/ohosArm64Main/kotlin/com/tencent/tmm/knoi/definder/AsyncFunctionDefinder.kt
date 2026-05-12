package com.tencent.tmm.knoi.definder

import com.tencent.tmm.knoi.converter.jsValueToKTValue
import com.tencent.tmm.knoi.converter.ktValueToJSValue
import com.tencent.tmm.knoi.exception.FunctionNotRegisterException
import com.tencent.tmm.knoi.injectEnv
import com.tencent.tmm.knoi.setCurrentAsyncInvokeOwnerTid
import com.tencent.tmm.knoi.napi.defineFunctionToExport
import com.tencent.tmm.knoi.napi.safeCaseNumberType
import com.tencent.tmm.knoi.register.AsyncFunctionBindInfo
import com.tencent.tmm.knoi.register.AsyncFunctionRegister
import com.tencent.tmm.knoi.type.ArrayBuffer
import com.tencent.tmm.knoi.type.JSValue
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import platform.ohos.knoi.createAsyncWork
import platform.ohos.knoi.createError
import platform.ohos.knoi.createPromise
import platform.ohos.knoi.deleteAsyncWork
import platform.ohos.knoi.get_tid
import platform.ohos.knoi.getAndClearLastException
import platform.ohos.knoi.getCallbackInfoParamsSize
import platform.ohos.knoi.getCbInfoWithSize
import platform.ohos.knoi.getUndefined
import platform.ohos.knoi.queueAsyncWork
import platform.ohos.knoi.rejectDeferred
import platform.ohos.knoi.resolveDeferred
import platform.ohos.napi_async_work
import platform.ohos.napi_callback_info
import platform.ohos.napi_deferred
import platform.ohos.napi_env
import platform.ohos.napi_pending_exception
import platform.ohos.napi_status
import platform.ohos.napi_value
import platform.ohos.napi_valueVar
import platform.posix.free
import kotlin.reflect.KClass

const val INVOKE_RET_PROMIS_METHOD_NAME = "invokeRetPromise"

private val asyncFunctionRegister = AsyncFunctionRegister()

fun bindAsync(
    name: String,
    function: (args: Array<out Any?>) -> Any?,
    returnType: KClass<out Any> = Unit::class,
    vararg paramsTypes: KClass<out Any>
) {
    asyncFunctionRegister.register(
        AsyncFunctionBindInfo(name, function, returnType, paramsTypes)
    )
}

internal fun registerAsyncForwarder(env: napi_env, export: napi_value) {
    defineFunctionToExport(
        env,
        export,
        INVOKE_RET_PROMIS_METHOD_NAME,
        staticCFunction(::forwardInvokeRetPromise)
    )
}

private sealed class AsyncExecutionContext(
    val bindInfo: AsyncFunctionBindInfo,
    val params: Array<out Any?>,
    val ownerTid: Int
) {
    var work: napi_async_work? = null
    var result: Any? = null
    var throwable: Throwable? = null
}

private class PromiseAsyncExecutionContext(
    bindInfo: AsyncFunctionBindInfo,
    params: Array<out Any?>,
    ownerTid: Int,
    val deferred: napi_deferred?
) : AsyncExecutionContext(bindInfo, params, ownerTid)

private fun getAsyncBindInfo(name: String): AsyncFunctionBindInfo {
    return asyncFunctionRegister.getBindInfo(name) ?: throw FunctionNotRegisterException(name)
}

private fun getAsyncForwardFunctionName(env: napi_env?, callbackInfo: napi_callback_info?): String {
    val params = getCbInfoWithSize(env, callbackInfo, 1) ?: error("unknown params.")
    return try {
        if (params[0] == null) {
            throw IllegalArgumentException("The first parameter must be the function name.")
        }
        jsValueToKTValue(env, params[0], String::class) as String
    } finally {
        free(params)
    }
}

private fun buildAsyncFunctionParams(
    env: napi_env?,
    callbackInfo: napi_callback_info?,
    paramsTypes: Array<out KClass<out Any>>
): Array<out Any?> {
    val jsParamsSize = getCallbackInfoParamsSize(env, callbackInfo)
    val expectedSize = paramsTypes.size + 1
    if (jsParamsSize != expectedSize) {
        throw IllegalArgumentException(
            "async params length error: expect $expectedSize actual $jsParamsSize"
        )
    }
    val params = getCbInfoWithSize(env, callbackInfo, jsParamsSize) ?: error("unknown params.")
    return try {
        val paramsValue = mutableListOf<Any?>()
        paramsTypes.forEachIndexed { index, type ->
            val value = jsValueToAsyncKTValue(env, params[index + 1], type)
            paramsValue.add(safeCaseNumberType(value, type))
        }
        paramsValue.toTypedArray()
    } finally {
        free(params)
    }
}

internal fun jsValueToAsyncKTValue(
    env: napi_env?,
    value: napi_value?,
    kType: KClass<out Any>
): Any? {
    if (env == null || value == null) {
        return null
    }
    val converted = when (kType) {
        JSValue::class -> JSValue(env, value)
        ArrayBuffer::class -> ArrayBuffer(value)
        else -> jsValueToKTValue(env, value, kType)
    }
    return normalizeAsyncInputValue(converted)
}

internal fun ktValueToAsyncJSValue(
    env: napi_env?,
    value: Any?,
    clazz: KClass<out Any>,
    ownerTid: Int
): napi_value? {
    if (env == null || value == null) {
        return null
    }
    val normalized = normalizeAsyncOutputValue(value, ownerTid)
    return when (clazz) {
        JSValue::class -> {
            val jsValue = normalized as? JSValue ?: return null
            jsValue.handle
        }
        ArrayBuffer::class -> {
            val arrayBuffer = normalized as? ArrayBuffer ?: return null
            arrayBuffer.handle
        }
        else -> ktValueToJSValue(env, normalized, clazz)
    }
}

internal fun normalizeAsyncInputValue(value: Any?): Any? {
    return when (value) {
        null -> null
        is List<*> -> value.map { normalizeAsyncInputValue(it) }
        is Array<*> -> Array(value.size) { index -> normalizeAsyncInputValue(value[index]) }
        is Map<*, *> -> value.entries.associate { it.key to normalizeAsyncInputValue(it.value) }
        else -> value
    }
}

internal fun normalizeAsyncOutputValue(value: Any?, ownerTid: Int): Any? {
    return when (value) {
        null -> null
        is JSValue -> {
            if (value.tid != ownerTid) {
                throw IllegalStateException(
                    "Async JSValue return must remain on the invoking JS thread. expectedTid=$ownerTid actualTid=${value.tid}"
                )
            }
            value
        }
        is List<*> -> value.map { normalizeAsyncOutputValue(it, ownerTid) }
        is Array<*> -> Array(value.size) { index -> normalizeAsyncOutputValue(value[index], ownerTid) }
        is Map<*, *> -> value.entries.associate { it.key to normalizeAsyncOutputValue(it.value, ownerTid) }
        else -> value
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun forwardInvokeRetPromise(env: napi_env?, callbackInfo: napi_callback_info?): napi_value? {
    injectEnv(env)
    val ownerTid = get_tid()
    val methodName = getAsyncForwardFunctionName(env, callbackInfo)
    val bindInfo = getAsyncBindInfo(methodName)
    val params = buildAsyncFunctionParams(env, callbackInfo, bindInfo.paramsType)
    memScoped {
        val promiseValue = alloc<napi_valueVar>()
        val deferred = createPromise(env, promiseValue.ptr)
        val context = PromiseAsyncExecutionContext(bindInfo, params, ownerTid, deferred)
        val ref = StableRef.create(context)
        val work = createAsyncWork(
            env,
            "KnoiInvokeRetPromise",
            staticCFunction(::executePromiseAsyncFunction),
            staticCFunction(::completePromiseAsyncFunction),
            ref.asCPointer()
        )
        context.work = work
        queueAsyncWork(env, work)
        return promiseValue.value
    }
}

internal fun createAsyncThrowableValue(env: napi_env?, throwable: Throwable?): napi_value? {
    val message = throwable?.message ?: throwable?.toString() ?: "Unknown async error"
    return createError(env, message)
}

internal fun executePromiseAsyncFunction(env: napi_env?, data: COpaquePointer?) {
    val context = data?.asStableRef<PromiseAsyncExecutionContext>()?.get() ?: return
    try {
        setCurrentAsyncInvokeOwnerTid(context.ownerTid)
        context.result = context.bindInfo.originFun(context.params)
    } catch (t: Throwable) {
        context.throwable = t
    } finally {
        setCurrentAsyncInvokeOwnerTid(null)
    }
}

internal fun completePromiseAsyncFunction(
    env: napi_env?,
    status: napi_status,
    data: COpaquePointer?
) {
    val ref = data?.asStableRef<PromiseAsyncExecutionContext>() ?: return
    val context = ref.get()
    try {
        injectEnv(env)
        if (status == napi_pending_exception) {
            rejectDeferred(env, context.deferred, getAndClearLastException(env))
            return
        }
        val throwable = context.throwable
        if (throwable != null) {
            rejectDeferred(env, context.deferred, createAsyncThrowableValue(env, throwable))
            return
        }
        val value = try {
            ktValueToAsyncJSValue(env, context.result, context.bindInfo.returnType, context.ownerTid)
        } catch (t: Throwable) {
            rejectDeferred(env, context.deferred, createAsyncThrowableValue(env, t))
            return
        }
        resolveDeferred(env, context.deferred, value ?: getUndefined(env))
    } finally {
        deleteAsyncWork(env, context.work)
        ref.dispose()
    }
}
