package com.tencent.tmm.knoi

import com.tencent.tmm.knoi.definder.registerDeclareForwarder
import com.tencent.tmm.knoi.definder.registerAsyncForwarder
import com.tencent.tmm.knoi.definder.registerForwarder
import com.tencent.tmm.knoi.definder.registerServiceExport
import com.tencent.tmm.knoi.definder.tsfnRegister
import com.tencent.tmm.knoi.jsbind.registerBindJSFunction
import com.tencent.tmm.knoi.logger.isDebug
import com.tencent.tmm.knoi.metric.initTraceFuncIfNeed
import com.tencent.tmm.knoi.metric.trace
import platform.ohos.knoi.get_pid
import platform.ohos.knoi.get_tid
import platform.ohos.napi_env
import platform.ohos.napi_value
import kotlin.native.concurrent.ThreadLocal

/**
 *  获取当前线程的 napi_env
 *  注意：在非 ArkTS 线程获取返回 null
 */
@ThreadLocal
private var tlsEnv: napi_env? = null

/**
 *  获取主线程 id
 */
var mainTid: Int = get_pid()

/**
 * 初始化 knoi 环境
 * @param ArkTs 环境
 * @param export 需注入的对象
 * @param debug 是否 debug 模式
 */
fun InitEnv(env: napi_env, export: napi_value, debug: Boolean) {
    isDebug = debug
    initTraceFuncIfNeed()
    trace("initEnv") {
        // 注入 napi_env
        injectEnv(env)
        // 注入 Declare 转发器
        registerDeclareForwarder(env, export)
        // 注入 Function 转发器
        registerForwarder(env, export)
        // 注入 Async Function 转发器
        registerAsyncForwarder(env, export)
        // 注入 bind 方法接口
        registerBindJSFunction(env, export)
        // 注入 Service 服务中转接口
        registerServiceExport(env, export)
    }
}

internal fun injectEnv(env: napi_env?) {
    tlsEnv = env
    tsfnRegister.registerThreadSafeFunctionIfNeed()
}

fun getEnv(): napi_env? = tlsEnv

fun getTid(): Int {
    return get_tid()
}
