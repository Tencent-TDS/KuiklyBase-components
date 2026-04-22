package com.tencent.tmm.knoi.type

import com.tencent.tmm.knoi.definder.tsfnRegister
import com.tencent.tmm.knoi.getEnv
import com.tencent.tmm.knoi.metric.trace
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.ohos.knoi.createArrayBuffer
import platform.ohos.knoi.createExternalArrayBuffer
import platform.ohos.knoi.createExternalTypedArray
import platform.ohos.knoi.createTypedArray
import platform.ohos.knoi.createReference
import platform.ohos.knoi.getArrayBufferLength
import platform.ohos.knoi.getArrayBufferValue
import platform.ohos.knoi.getReferenceValue
import platform.ohos.knoi.getTypeArrayLength
import platform.ohos.knoi.getTypeArrayType
import platform.ohos.knoi.getTypeArrayValue
import platform.ohos.knoi.getTypedArrayItemSize
import platform.ohos.knoi.get_tid
import platform.ohos.knoi.isArrayBuffer
import platform.ohos.napi_typedarray_type
import platform.ohos.napi_env
import platform.ohos.napi_ref
import platform.ohos.napi_value
import platform.ohos.knoi.deleteReference
import platform.posix.free
import platform.posix.memcpy
import platform.posix.uint8_tVar
import kotlin.native.ref.createCleaner

private data class NativeOwnedBuffer(var pointer: CPointer<uint8_tVar>?)
private data class JSArrayBufferRef(var ref: napi_ref?, var tid: Int?)

class ArrayBuffer private constructor(
    private var type: napi_typedarray_type = napi_typedarray_type.napi_uint8_array,
    private var length: Long = 0L,
    private var data: CPointer<uint8_tVar>? = null,
    private val nativeOwnedData: NativeOwnedBuffer? = null,
    private val jsReference: JSArrayBufferRef = JSArrayBufferRef(null, null)
) {
    private val nativeClean = nativeOwnedData?.let { owner ->
        createCleaner(owner) {
            it.pointer?.let { pointer ->
                free(pointer)
            }
            it.pointer = null
        }
    }
    private val jsClean = createCleaner(jsReference) {
        val ref = it.ref ?: return@createCleaner
        val tid = it.tid ?: return@createCleaner
        tsfnRegister.callAsyncSafe(tid) {
            deleteReference(getEnv(), ref)
        }
        it.ref = null
    }

    var handle: napi_value? = null
        get() = getOrCreateHandle()

    constructor() : this(
        type = napi_typedarray_type.napi_uint8_array,
        length = 0L,
        data = null,
        nativeOwnedData = null
    )

    /**
     * 以 napi_value 构造需在 JS 线程中
     */
    constructor(handle: napi_value?) : this() {
        jsReference.tid = get_tid()
        initJSBacked(handle)
    }

    constructor(
        data: CPointer<uint8_tVar>,
        length: Long,
        type: napi_typedarray_type = napi_typedarray_type.napi_uint8_array
    ) : this(type = type, length = length, nativeOwnedData = NativeOwnedBuffer(null)) {
        val owner = nativeOwnedData ?: error("nativeOwnedData holder missing")
        owner.pointer = copyNativeBuffer(data, length)?.pointer
        this.data = owner.pointer
    }

    constructor(data: UByteArray) : this(
        type = napi_typedarray_type.napi_uint8_array,
        length = data.size.toLong(),
        nativeOwnedData = NativeOwnedBuffer(null)
    ) {
        val owner = nativeOwnedData ?: error("nativeOwnedData holder missing")
        owner.pointer = copyNativeBuffer(data)?.pointer
        this.data = owner.pointer
    }

    constructor(
        data: ByteArray,
    ) : this(data.toUByteArray())

    companion object {
        private fun createNativeOwned(
            type: napi_typedarray_type,
            length: Long,
            pointer: CPointer<uint8_tVar>?
        ): ArrayBuffer {
            return ArrayBuffer(
                type = type,
                length = length,
                data = pointer,
                nativeOwnedData = NativeOwnedBuffer(pointer)
            )
        }

        private fun getByteLength(count: Long, type: napi_typedarray_type): Long {
            return count * getTypedArrayItemSize(type)
        }

        private fun getCount(length: Long, type: napi_typedarray_type): Long {
            return length / getTypedArrayItemSize(type)
        }

        private fun copyNativeBuffer(
            source: CPointer<uint8_tVar>,
            length: Long
        ): NativeOwnedBuffer? {
            if (length <= 0) {
                return null
            }
            val buffer = nativeHeap.allocArray<uint8_tVar>(length.toInt())
            memcpy(buffer, source, length.toULong())
            return NativeOwnedBuffer(buffer)
        }

        private fun copyNativeBuffer(data: UByteArray): NativeOwnedBuffer? {
            if (data.isEmpty()) {
                return null
            }
            val buffer = nativeHeap.allocArray<uint8_tVar>(data.size)
            data.toByteArray().usePinned { pinned ->
                memcpy(buffer, pinned.addressOf(0), data.size.toULong())
            }
            return NativeOwnedBuffer(buffer)
        }
    }

    private fun ensureJsThread() {
        val ownerTid = jsReference.tid ?: return
        check(get_tid() == ownerTid) {
            "ArrayBuffer JS-backed operation must run on owner JS thread. expectedTid=$ownerTid actualTid=${get_tid()}"
        }
    }

    private fun getOrCreateHandle(): napi_value? {
        val ref = jsReference.ref
        if (ref != null) {
            ensureJsThread()
            return getReferenceValue(getEnv(), ref)
        }
        val ownedPointer = nativeOwnedData?.pointer
        val nativeSource = ownedPointer ?: data
        if (nativeOwnedData != null || nativeSource != null || length == 0L) {
            memScoped {
                val externalHandle = ownedPointer?.let { pointer ->
                    if (type != napi_typedarray_type.napi_uint8_array) {
                        createExternalTypedArray(getEnv(), pointer, getCount(), type)
                    } else {
                        createExternalArrayBuffer(getEnv(), pointer, length)
                    }
                }
                val handle = externalHandle ?: if (type != napi_typedarray_type.napi_uint8_array) {
                    createTypedArray(getEnv(), nativeSource, getCount(), type)
                } else {
                    createArrayBuffer(getEnv(), nativeSource, length)
                }
                if (handle == null) {
                    return null
                }
                if (externalHandle != null) {
                    transferNativeOwnershipToHandle(handle)
                } else {
                    adoptCreatedHandle(handle)
                }
                jsReference.tid = get_tid()
                jsReference.ref = createReference(getEnv(), handle)
                return handle
            }
        }
        return null
    }

    /**
     * External ArrayBuffer export keeps the same native pointer and transfers lifetime
     * management from Kotlin to the JS ArrayBuffer finalizer.
     */
    private fun transferNativeOwnershipToHandle(handle: napi_value?) {
        if (handle == null || nativeOwnedData?.pointer == null) {
            return
        }
        data = if (isArrayBuffer(getEnv(), handle)) {
            getArrayBufferValue(getEnv(), handle)
        } else {
            getTypeArrayValue(getEnv(), handle)
        }
        nativeOwnedData.pointer = null
    }

    /**
     * Native-backed buffers are exported by creating a fresh JS ArrayBuffer/TypedArray.
     * After that first export, switch this instance to the JS backing store so future
     * native writes observe the same bytes as the cached JS handle.
     */
    private fun adoptCreatedHandle(handle: napi_value?) {
        if (handle == null || nativeOwnedData?.pointer == null) {
            return
        }
        data = if (isArrayBuffer(getEnv(), handle)) {
            getArrayBufferValue(getEnv(), handle)
        } else {
            getTypeArrayValue(getEnv(), handle)
        }
        nativeOwnedData.pointer?.let { pointer ->
            free(pointer)
        }
        nativeOwnedData.pointer = null
    }

    private fun initJSBacked(handle: napi_value?) {
        if (handle == null) {
            return
        }
        ensureJsThread()
        jsReference.tid = get_tid()
        jsReference.ref = createReference(getEnv(), handle)
        if (isArrayBuffer(getEnv(), handle)) {
            length = getArrayBufferLength(getEnv(), handle).toLong()
            type = napi_typedarray_type.napi_uint8_array
            data = getArrayBufferValue(getEnv(), handle)
        } else {
            type = getTypeArrayType(getEnv(), handle)
            val count = getTypeArrayLength(getEnv(), handle).toLong()
            length = getByteLength(count, type)
            data = getTypeArrayValue(getEnv(), handle)
        }
    }

    /**
     * 获取 JS ArrayBuffer 指向的数据指针
     */
    fun <T : CPointed> getData(): CPointer<T>? {
        return (nativeOwnedData?.pointer ?: data)?.reinterpret()
    }

    /**
     * 获取 JS ArrayBuffer 指向的数据指针
     */
    fun getByteArray(): ByteArray {
        return trace("ArrayBuffer:getByteArray") {
            val source = nativeOwnedData?.pointer ?: data ?: return@trace ByteArray(length.toInt())
            source.toByteArray(length.toInt())
        }
    }

    fun getCount(): Long {
        return getCount(length, type)
    }

    fun getByteLength(): Long {
        return length
    }

    /**
     * Whether this buffer is backed by Kotlin-owned native memory rather than JS-owned storage.
     */
    fun isNativeOwned(): Boolean {
        return nativeOwnedData?.pointer != null
    }

    /**
     * Returns this buffer if it is already backed by Kotlin-owned native memory,
     * otherwise creates a Kotlin-owned copy that is independent from JS backing storage.
     */
    fun ensureNativeOwned(): ArrayBuffer {
        return when {
            nativeOwnedData?.pointer != null -> this
            data != null -> {
                val source = data ?: return ArrayBuffer()
                val ownerTid = jsReference.tid
                if (ownerTid != null) {
                    return tsfnRegister.callSyncSafe(ownerTid) {
                        val pointer = data ?: return@callSyncSafe ArrayBuffer()
                        val copiedPointer = copyNativeBuffer(pointer, length)?.pointer
                        createNativeOwned(type, length, copiedPointer)
                    } ?: ArrayBuffer()
                }
                val copiedPointer = copyNativeBuffer(source, length)?.pointer
                createNativeOwned(type, length, copiedPointer)
            }
            else -> ArrayBuffer()
        }
    }

    /**
     * Returns a distinct Kotlin-owned copy, even if this buffer is already Kotlin-owned.
     */
    fun toDetachedCopy(): ArrayBuffer {
        return when {
            nativeOwnedData?.pointer != null -> {
                val pointer = nativeOwnedData.pointer ?: return ArrayBuffer()
                val copiedPointer = copyNativeBuffer(pointer, length)?.pointer
                createNativeOwned(type, length, copiedPointer)
            }
            data != null -> ensureNativeOwned()
            else -> ArrayBuffer()
        }
    }
}

fun CPointer<uint8_tVar>.toByteArray(count: Int): ByteArray {
    val result = ByteArray(count)
    if (result.isEmpty()) return result
    result.usePinned {
        memcpy(it.addressOf(0), this, count.toULong())
    }
    return result
}
