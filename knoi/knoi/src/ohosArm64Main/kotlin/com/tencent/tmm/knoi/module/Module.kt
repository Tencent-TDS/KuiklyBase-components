package com.tencent.tmm.knoi.module

import com.tencent.tmm.knoi.getEnv
import com.tencent.tmm.knoi.type.JSValue
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.ohos.knoi.loadModuleWithInfo
import platform.ohos.napi_valueVar

/**
 * 参考文档
 * https://developer.huawei.com/consumer/cn/doc/harmonyos-guides-V5/use-napi-load-module-with-info-V5
 */
fun loadModuleToJSValueWithInfo(path: String?, moduleInfo: String?): JSValue {
    memScoped {
        val resultVar = alloc<napi_valueVar>()
        val status = loadModuleWithInfo(getEnv(), path, moduleInfo, resultVar.ptr)
        if (status.toInt() != 0) {
            throw Exception("load model $path $moduleInfo failed")
        }
        val resultValue = resultVar.value
        return JSValue(resultValue)
    }
}