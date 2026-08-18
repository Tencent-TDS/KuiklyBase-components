package com.tencent.tmm.knoi.definder

import com.tencent.tmm.knoi.converter.convertJSCallbackInfoToKTParamList
import com.tencent.tmm.knoi.converter.jsValueToKTValue
import com.tencent.tmm.knoi.converter.ktValueToJSValue
import com.tencent.tmm.knoi.getEnv
import com.tencent.tmm.knoi.injectEnv
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
import kotlinx.cinterop.get
import kotlinx.cinterop.staticCFunction
import platform.ohos.knoi.getCbInfoWithSize
import platform.ohos.knoi.typeOf
import platform.ohos.napi_callback_info
import platform.ohos.napi_env
import platform.ohos.napi_value
import platform.ohos.napi_valuetype
import kotlin.reflect.KClass

const val JS_REGISTER_SERVICE_METHOD_NAME = "registerServiceProvider"
const val JS_CALL_SERVICE_METHOD_NAME = "callService"

/**
 * Method names produced when JS/ArkTS coerces a service Proxy via ToPrimitive.
 * `String(Symbol.toPrimitive)` becomes `"Symbol(Symbol.toPrimitive)"`.
 */
internal fun isJsWellKnownCoercionMethod(methodName: String): Boolean {
    return methodName == "valueOf" ||
        methodName == "toString" ||
        methodName == "Symbol(Symbol.toPrimitive)"
}

val serviceProxyRegister = ServiceProxyRegister()

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
    if (isJsWellKnownCoercionMethod(methodName)) {
        debug("callService $serviceName#$methodName ignored (JS primitive coercion)")
        return ktValueToJSValue(env, serviceName, String::class)
    }
    val invokable = serviceProviderRegister.getInvokable(proxyJSValue, serviceName)
    val paramsTypes = invokable.getParamsTypeList(methodName)
    val expectedSize = invokable.getMinParamsSize(methodName)

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
}

/**
 * 注册服务
 */
internal fun registerService(env: napi_env?, callbackInfo: napi_callback_info?): napi_value? {
    injectEnv(env)
    val params = getCbInfoWithSize(env, callbackInfo, 3) ?: error("unknown params.")
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
}