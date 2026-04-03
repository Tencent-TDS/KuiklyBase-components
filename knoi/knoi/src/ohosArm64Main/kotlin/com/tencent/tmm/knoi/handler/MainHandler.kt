package com.tencent.tmm.knoi.handler

import com.tencent.tmm.knoi.collections.SafeHashMap
import com.tencent.tmm.knoi.definder.tsfnRegister
import com.tencent.tmm.knoi.logger.info
import com.tencent.tmm.knoi.mainTid
import platform.ohos.knoi.get_tid
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.Worker

@OptIn(ObsoleteWorkersApi::class)
private var worker = Worker.start(false, "MainHandlerTimer")
private var blockToTimerIDMap: SafeHashMap<Int, () -> Unit> = SafeHashMap()

/**
 * 是否为主线程
 */
fun isMainThread(): Boolean {
    return get_tid() == mainTid
}

/**
 * 在主线程执行 block
 */
fun runOnMainThread(block: () -> Unit) {
    tsfnRegister.callAsyncSafe(mainTid) {
        block.invoke()
    }
}

/**
 * 取消执行 block
 */
fun cancelBlock(block: () -> Unit) {
    val timerID = blockToTimerIDMap.remove(block.hashCode())
    if (timerID == null) {
        info("timer maybe run or remove.")
    }
}

/**
 * 在主线程执行 block
 * @param block 待执行的闭包
 * @param delayMs 延迟的时间，单位为毫秒
 */
@OptIn(ObsoleteWorkersApi::class)
fun runOnMainThread(block: () -> Unit, delayMs: Long) {
    val hashcode = block.hashCode()
    blockToTimerIDMap.put(hashcode, block)
    worker.executeAfter(delayMs * 1000) {
        if (blockToTimerIDMap.get(hashcode) == null) {
            info("timer maybe run or remove.")
            return@executeAfter
        }
        blockToTimerIDMap.remove(hashcode)
        runOnMainThread(block)
    }
}
