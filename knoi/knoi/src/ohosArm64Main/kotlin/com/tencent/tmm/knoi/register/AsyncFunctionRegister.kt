package com.tencent.tmm.knoi.register
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.reflect.KClass

data class AsyncFunctionBindInfo(
    val name: String,
    val originFun: (args: Array<out Any?>) -> Any?,
    val returnType: KClass<out Any>,
    val paramsType: Array<out KClass<out Any>>
)

class AsyncFunctionRegister : SynchronizedObject() {
    private val bindInfoMap = mutableMapOf<String, AsyncFunctionBindInfo>()

    fun register(bindInfo: AsyncFunctionBindInfo) {
        synchronized(this) {
            bindInfoMap[bindInfo.name] = bindInfo
        }
    }

    fun getBindInfo(name: String): AsyncFunctionBindInfo? {
        synchronized(this) {
            return bindInfoMap[name]
        }
    }
}
