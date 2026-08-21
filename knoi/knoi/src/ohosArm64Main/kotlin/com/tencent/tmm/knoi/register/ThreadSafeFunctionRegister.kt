package com.tencent.tmm.knoi.register


import com.tencent.tmm.knoi.getEnv
import com.tencent.tmm.knoi.logger.debug
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import platform.ohos.knoi.isThreadSafeFunctionRegistered
import platform.ohos.knoi.registerThreadSafeFunction
import platform.ohos.knoi.tryCallThreadSafeFunction
import platform.ohos.knoi.unregisterThreadSafeFunction as nativeUnregisterThreadSafeFunction
import platform.ohos.knoi.get_tid

class ThreadSafeFunctionRegister : SynchronizedObject() {

    /**
     * 注册 Thread Safe Function
     */
    fun registerThreadSafeFunctionIfNeed() {
        val env = getEnv() ?: return
        val tid = get_tid()
        registerThreadSafeFunction(env, tid)
        debug("register thread safe function success. tid = $tid")
    }

    /**
     * 释放 tid 对应的 Thread Safe Function。
     * Worker / napi_env 销毁时必须调用，否则 Kotlin Cleaner 会向已销毁的 uv loop 投递任务。
     */
    fun unregisterThreadSafeFunction(tid: Int = get_tid()) {
        nativeUnregisterThreadSafeFunction(tid)
        debug("unregister thread safe function. tid = $tid")
    }

    /**
     * tid 是否仍持有可用的 Thread Safe Function
     */
    fun isRegistered(tid: Int): Boolean {
        return isThreadSafeFunctionRegistered(tid) != 0
    }

    /**
     * 将 block 在 tid 的 JS 线程执行
     * @param tid 线程 ID
     * @param sync 是否同步调用
     * @param block 待执行的闭包
     * @return 返回值，未注册或已释放时返回 null
     */
    fun callFunctionInOtherThread(
        tid: Int,
        sync: Boolean,
        block: () -> COpaquePointer?
    ): COpaquePointer? {
        if (!isRegistered(tid)) {
            return null
        }
        val ref = StableRef.create(block)
        return memScoped {
            val outResult = alloc<COpaquePointerVar>()
            outResult.value = null
            val submitted = tryCallThreadSafeFunction(
                tid, staticCFunction(::callbackInJSThread), ref.asCPointer(), sync, tid, outResult.ptr
            )
            if (submitted == 0) {
                ref.dispose()
                return@memScoped null
            }
            outResult.value
        }
    }

    /**
     * 在 JS 线程在同步调用 闭包，并返回
     * @param tid JavaScript 线程 ID
     * @param block 待执行的闭包
     */
    inline fun <reified R : Any> callSyncSafe(
        tid: Int, crossinline block: () -> R?
    ): R? {
        if (get_tid() == tid) {
            return block.invoke()
        }
        if (!isRegistered(tid)) {
            return null
        }
        val ptr = callFunctionInOtherThread(tid, true) {
            val result = block.invoke() ?: return@callFunctionInOtherThread null
            val ref = StableRef.create(result)
            ref.asCPointer()
        }
        val ref = ptr?.asStableRef<R>() ?: return null
        val result = ref.get()
        ref.dispose()
        return result
    }

    /**
     * 在 tid 的 JS 线程异步调用 block
     * @param tid 线程 ID
     * @param block 带执行的闭包
     */
    fun callAsyncSafe(
        tid: Int, block: () -> Unit
    ) {
        if (get_tid() == tid) {
            return block.invoke()
        }
        if (!isRegistered(tid)) {
            return
        }
        callFunctionInOtherThread(tid, false) {
            block.invoke()
            return@callFunctionInOtherThread null
        }
    }

}

fun callbackInJSThread(ptr: COpaquePointer?): COpaquePointer? {
    val dataRef = ptr?.asStableRef<() -> COpaquePointer?>() ?: return null
    val block = dataRef.get()
    dataRef.dispose()
    return block.invoke()
}
