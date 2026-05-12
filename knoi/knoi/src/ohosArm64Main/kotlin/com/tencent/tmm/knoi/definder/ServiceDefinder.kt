package com.tencent.tmm.knoi.definder

import com.tencent.tmm.knoi.converter.convertJSCallbackInfoToKTParamList
import com.tencent.tmm.knoi.converter.jsValueToKTValue
import com.tencent.tmm.knoi.converter.ktValueToJSValue
import com.tencent.tmm.knoi.getEnv
import com.tencent.tmm.knoi.injectEnv
import com.tencent.tmm.knoi.setCurrentAsyncInvokeOwnerTid
import com.tencent.tmm.knoi.logger.debug
import com.tencent.tmm.knoi.logger.info
import com.tencent.tmm.knoi.metric.trace
import com.tencent.tmm.knoi.napi.defineFunctionToExport
import com.tencent.tmm.knoi.napi.safeCaseNumberType
import com.tencent.tmm.knoi.register.ServiceProvider
import com.tencent.tmm.knoi.register.ServiceProviderRegister
import com.tencent.tmm.knoi.register.ServiceProxyRegister
import com.tencent.tmm.knoi.register.getInvokable
import com.tencent.tmm.knoi.service.Invokable
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
import platform.ohos.knoi.createPromise
import platform.ohos.knoi.deleteAsyncWork
import platform.ohos.knoi.getAndClearLastException
import platform.ohos.knoi.getCbInfoWithSize
import platform.ohos.knoi.getCallbackInfoParamsSize
import platform.ohos.knoi.getUndefined
import platform.ohos.knoi.get_tid
import platform.ohos.knoi.queueAsyncWork
import platform.ohos.knoi.rejectDeferred
import platform.ohos.knoi.resolveDeferred
import platform.ohos.knoi.typeOf
import platform.ohos.napi_async_work
import platform.ohos.napi_callback_info
import platform.ohos.napi_deferred
import platform.ohos.napi_env
import platform.ohos.napi_pending_exception
import platform.ohos.napi_status
import platform.ohos.napi_value
import platform.ohos.napi_valueVar
import platform.ohos.napi_valuetype
import platform.posix.free
import kotlin.reflect.KClass

const val JS_REGISTER_SERVICE_METHOD_NAME = "registerServiceProvider"
const val JS_CALL_SERVICE_METHOD_NAME = "callService"

val serviceProxyRegister = ServiceProxyRegister()

private class PromiseAsyncServiceExecutionContext(
    val invokable: Invokable,
    val methodName: String,
    val params: Array<out Any?>,
    val ownerTid: Int,
    val returnType: KClass<out Any>,
    val deferred: napi_deferred?
) {
    var work: napi_async_work? = null
    var result: Any? = null
    var throwable: Throwable? = null
}

/**
 * 注册服务调用相关 API 至 export 对象
 * @param env napi 环境
 * @param export export 对象
 */
fun registerServiceExport(env: napi_env, export: napi_value) {
    defineFunctionToExport(
        env, export, JS_REGISTER_SERVICE_METHOD_NAME, staticCFunction(::registerService)
    )

    defineFunctionToExport(
        env, export, JS_CALL_SERVICE_METHOD_NAME, staticCFunction(::forwardServiceCall)
    )
    info("registerServiceExport successful.")
}

/**
 * 调用服务
 * @param name 服务名
 * @param method 方法名
 * @param params 参数包
 * @return 服务调用返回值
 */
inline fun <reified T> callService(name: String, method: String, vararg params: Any?): T? {
    if (name.isEmpty() || method.isEmpty()) {
        return null
    }
    return serviceProxyRegister.callService<T>(name, method, *params)
}

/**
 * 获取服务注册 tid
 * @param name 服务名
 * @return 线程 ID
 */
fun getServiceRegisterTid(name: String): Int {
    return serviceProxyRegister.getServiceRegisterTid(name)
}

/**
 * 绑定服务代理对象
 * @param name 服务名
 * @param proxy 静态代理对象
 */
fun <T> bindServiceProxy(name: String, proxy: T) {
    serviceProxyRegister.bindProxy(name, proxy)
}

/**
 * 转发服务调用
 * @param env JS 环境
 * @param callbackInfo napi callback 回调
 * @return JS 返回值
 */
internal fun forwardServiceCall(env: napi_env?, callbackInfo: napi_callback_info?): napi_value? {
    injectEnv(env)
    val params = getCbInfoWithSize(env, callbackInfo, 3) ?: error("unknown params.")
    try {
        if (params[0] == null || params[1] == null || params[2] == null) {
            return null
        }
        val serviceNameJSValue = JSValue(params[0])
        if (!serviceNameJSValue.isString() || serviceNameJSValue.toKString().isNullOrEmpty()) {
            throw IllegalArgumentException("The first parameter must be the service name.")
        }

        val proxyJSValue = JSValue(params[1])
        val methodName = jsValueToKTValue(getEnv(), params[2], String::class)
        if (methodName !is String) {
            throw IllegalArgumentException("The third parameter must be the method name.")
        }

        val serviceName = serviceNameJSValue.toKString()!!
        val invokable = serviceProviderRegister.getInvokable(proxyJSValue, serviceName)
        val paramsTypes = invokable.getParamsTypeList(methodName)
        val expectedSize = invokable.getMinParamsSize(methodName)

        if (invokable.isRetPromise(methodName)) {
            return forwardPromiseServiceCall(
                env,
                callbackInfo,
                serviceName,
                methodName,
                invokable,
                paramsTypes,
                expectedSize
            )
        }

        val paramsValue =
            convertJSCallbackInfoToKTParamList<Any>(env, callbackInfo, paramsTypes.toList(), 3)
        if (paramsValue.size < expectedSize) {
            throw IllegalArgumentException(
                "callService method = $methodName " + "params length error: expect $expectedSize actual ${paramsValue.size}"
            )
        }
        val transformParamValues = paramsValue.mapIndexed { index: Int, param: Any? ->
            return@mapIndexed safeCaseNumberType(param, paramsTypes[index])
        }.toTypedArray()
        return trace("forwardServiceCall $serviceName#$methodName") {
            debug("callService $serviceName#$methodName : params = ${transformParamValues.contentToString()}")
            val result = invokable.invoke(methodName, *transformParamValues)
            debug("callService $serviceName#$methodName : result = $result")
            ktValueToJSValue(env, result, invokable.getReturnType(methodName))
        }
    } finally {
        free(params)
    }
}

private fun buildAsyncServiceParams(
    env: napi_env?,
    callbackInfo: napi_callback_info?,
    methodName: String,
    paramsTypes: Array<out KClass<out Any>>,
    offset: Int = 3
): Array<out Any?> {
    val jsParamsSize = getCallbackInfoParamsSize(env, callbackInfo)
    val maxExpectedSize = paramsTypes.size + offset
    if (jsParamsSize > maxExpectedSize) {
        throw IllegalArgumentException(
            "callService method = $methodName params length error: expect at most ${paramsTypes.size} actual ${jsParamsSize - offset}"
        )
    }
    val params = getCbInfoWithSize(env, callbackInfo, jsParamsSize) ?: error("unknown params.")
    return try {
        val paramsValue = mutableListOf<Any?>()
        for (index in offset until jsParamsSize) {
            val type = paramsTypes[index - offset]
            val value = jsValueToAsyncKTValue(env, params[index], type)
            paramsValue.add(safeCaseNumberType(value, type))
        }
        paramsValue.toTypedArray()
    } finally {
        free(params)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun forwardPromiseServiceCall(
    env: napi_env?,
    callbackInfo: napi_callback_info?,
    serviceName: String,
    methodName: String,
    invokable: Invokable,
    paramsTypes: Array<out KClass<out Any>>,
    expectedSize: Int
): napi_value? {
    val paramsValue = buildAsyncServiceParams(env, callbackInfo, methodName, paramsTypes)
    if (paramsValue.size < expectedSize) {
        throw IllegalArgumentException(
            "callService method = $methodName params length error: expect $expectedSize actual ${paramsValue.size}"
        )
    }
    val ownerTid = get_tid()
    val returnType = invokable.getReturnType(methodName)
    return trace("forwardPromiseServiceCall $serviceName#$methodName") {
        debug("callService promise $serviceName#$methodName : params = ${paramsValue.contentToString()}")
        memScoped {
            val promiseValue = alloc<napi_valueVar>()
            val deferred = createPromise(env, promiseValue.ptr)
            val context = PromiseAsyncServiceExecutionContext(
                invokable = invokable,
                methodName = methodName,
                params = paramsValue,
                ownerTid = ownerTid,
                returnType = returnType,
                deferred = deferred
            )
            val ref = StableRef.create(context)
            val work = createAsyncWork(
                env,
                "KnoiCallServicePromise",
                staticCFunction(::executePromiseServiceCall),
                staticCFunction(::completePromiseServiceCall),
                ref.asCPointer()
            )
            context.work = work
            queueAsyncWork(env, work)
            return@memScoped promiseValue.value
        }
    }
}

internal fun executePromiseServiceCall(env: napi_env?, data: COpaquePointer?) {
    val context = data?.asStableRef<PromiseAsyncServiceExecutionContext>()?.get() ?: return
    try {
        setCurrentAsyncInvokeOwnerTid(context.ownerTid)
        context.result = context.invokable.invoke(context.methodName, *context.params)
    } catch (t: Throwable) {
        context.throwable = t
    } finally {
        setCurrentAsyncInvokeOwnerTid(null)
    }
}

internal fun completePromiseServiceCall(
    env: napi_env?,
    status: napi_status,
    data: COpaquePointer?
) {
    val ref = data?.asStableRef<PromiseAsyncServiceExecutionContext>() ?: return
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
            ktValueToAsyncJSValue(env, context.result, context.returnType, context.ownerTid)
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

/**
 * 注册服务
 */
internal fun registerService(env: napi_env?, callbackInfo: napi_callback_info?): napi_value? {
    injectEnv(env)
    val params = getCbInfoWithSize(env, callbackInfo, 3) ?: error("unknown params.")
    try {
        if (params[0] == null || params[1] == null || params[2] == null) {
            return null
        }
        val nameJSValue = JSValue(params[0])
        if (!nameJSValue.isString() || nameJSValue.toKString().isNullOrEmpty()) {
            throw IllegalArgumentException("The first parameter must be the service name.")
        }
        val serviceName = nameJSValue.toKString()!!

        val singletonJSValue = JSValue(params[1])
        if (!singletonJSValue.isBoolean()) {
            throw IllegalArgumentException("The second parameter must be Boolean.")
        }
        val type = typeOf(env, params[2])
        if (type != napi_valuetype.napi_object && type != napi_valuetype.napi_function) {
            throw IllegalArgumentException("serviceName $serviceName The third parameter must be a function or object.")
        }
        val isSingleton = singletonJSValue.toBoolean()
        info("registerService service = $serviceName  isSingleton = $isSingleton")
        serviceProxyRegister.register(env, serviceName, isSingleton, params[2])
        return null
    } finally {
        free(params)
    }
}
