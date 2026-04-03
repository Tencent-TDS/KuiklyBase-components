package com.tencent.tmm.knoi.definder

import com.tencent.tmm.knoi.converter.jsValueToKTValue
import com.tencent.tmm.knoi.converter.ktValueToJSValue
import com.tencent.tmm.knoi.exception.FunctionNotRegisterException
import com.tencent.tmm.knoi.injectEnv
import com.tencent.tmm.knoi.napi.defineFunctionToExport
import com.tencent.tmm.knoi.napi.safeCaseNumberType
import com.tencent.tmm.knoi.register.AsyncFunctionBindInfo
import com.tencent.tmm.knoi.register.AsyncFunctionRegister
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
    val params: Array<out Any?>
) {
    var work: napi_async_work? = null
    var result: Any? = null
    var throwable: Throwable? = null
}

private class PromiseAsyncExecutionContext(
    bindInfo: AsyncFunctionBindInfo,
    params: Array<out Any?>,
    val deferred: napi_deferred?
) : AsyncExecutionContext(bindInfo, params)

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
            val value = jsValueToKTValue(env, params[index + 1], type)
            paramsValue.add(safeCaseNumberType(value, type))
        }
        paramsValue.toTypedArray()
    } finally {
        free(params)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun forwardInvokeRetPromise(env: napi_env?, callbackInfo: napi_callback_info?): napi_value? {
    injectEnv(env)
    val methodName = getAsyncForwardFunctionName(env, callbackInfo)
    val bindInfo = getAsyncBindInfo(methodName)
    val params = buildAsyncFunctionParams(env, callbackInfo, bindInfo.paramsType)
    memScoped {
        val promiseValue = alloc<napi_valueVar>()
        val deferred = createPromise(env, promiseValue.ptr)
        val context = PromiseAsyncExecutionContext(bindInfo, params, deferred)
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

private fun createAsyncThrowableValue(env: napi_env?, throwable: Throwable?): napi_value? {
    val message = throwable?.message ?: throwable?.toString() ?: "Unknown async error"
    return createError(env, message)
}

internal fun executePromiseAsyncFunction(env: napi_env?, data: COpaquePointer?) {
    val context = data?.asStableRef<PromiseAsyncExecutionContext>()?.get() ?: return
    try {
        context.result = context.bindInfo.originFun(context.params)
    } catch (t: Throwable) {
        context.throwable = t
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
        val value = ktValueToJSValue(env, context.result, context.bindInfo.returnType)
        resolveDeferred(env, context.deferred, value ?: getUndefined(env))
    } finally {
        deleteAsyncWork(env, context.work)
        ref.dispose()
    }
}
