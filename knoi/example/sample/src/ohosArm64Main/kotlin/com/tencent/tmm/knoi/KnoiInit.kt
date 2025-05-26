package com.tencent.tmm.knoi

import com.tencent.tmm.knoi.logger.logProxy
import kotlinx.cinterop.ExperimentalForeignApi
import platform.ohos.LOG_APP
import platform.ohos.LogLevel
import platform.ohos.OH_LOG_Print
import platform.ohos.napi_env
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalForeignApi::class)
public fun preInitEnv(env: napi_env, debug: Boolean) {
    configureGC()
    logProxy = {level: LogLevel, message: String ->
        OH_LOG_Print(LOG_APP, level, 1u, "knoi-sample", message)
    }
}

@OptIn(NativeRuntimeApi::class) fun configureGC() {
    //调大堆内存使用的目标比例可减少GC频率
    GC.targetHeapUtilization = 0.9
    //调大触发GC的堆空间满载阈值降低GC频率
    GC.heapTriggerCoefficient = 0.95
    //调大定期GC运行的时间间隔，降低忙碌期有gc干扰的概率
    GC.regularGCInterval = 5.seconds
    //调大初始heap size以避免频繁扩容
    GC.minHeapBytes = 250 * 1024 * 1024
    GC.targetHeapBytes = 250 * 1024 * 1024
}