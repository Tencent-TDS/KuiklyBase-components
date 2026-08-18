package com.tencent.tmm.knoi.type

import com.tencent.tmm.knoi.converter.convertJSCallbackInfoToKTParamList
import com.tencent.tmm.knoi.converter.coerceJsNumber
import com.tencent.tmm.knoi.converter.getJsArrayLikeLength
import com.tencent.tmm.knoi.converter.jsValueToKTValue
import com.tencent.tmm.knoi.converter.ktValueToJSValue
import com.tencent.tmm.knoi.definder.tsfnRegister
import com.tencent.tmm.knoi.getEnv
import com.tencent.tmm.knoi.handler.runOnMainThread
import com.tencent.tmm.knoi.jsbind.JSFunction
import com.tencent.tmm.knoi.jsbind.invokeDirect
import com.tencent.tmm.knoi.logger.debug
import com.tencent.tmm.knoi.logger.e
import com.tencent.tmm.knoi.logger.isDebug
import com.tencent.tmm.knoi.mainTid
import com.tencent.tmm.knoi.metric.trace
import com.tencent.tmm.knoi.napi.safeCaseNumberType
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import platform.ohos.knoi.createFunction
import platform.ohos.knoi.createObject
import platform.ohos.knoi.createReference
import platform.ohos.knoi.deleteReference
import platform.ohos.knoi.getArrayLength
import platform.ohos.knoi.getCbInfoData
import platform.ohos.knoi.getCbInfoThis
import platform.ohos.knoi.getElementInArray
import platform.ohos.knoi.getGlobal
import platform.ohos.knoi.getPropertyValue
import platform.ohos.knoi.getReferenceValue
import platform.ohos.knoi.get_tid
import platform.ohos.knoi.isArray
import platform.ohos.knoi.isEquals
import platform.ohos.knoi.setElementInArray
import platform.ohos.knoi.setPropertyValue
import platform.ohos.knoi.typeOf
import platform.ohos.napi_callback_info
import platform.ohos.napi_env
import platform.ohos.napi_ref
import platform.ohos.napi_value
import platform.ohos.napi_valuetype
import kotlin.native.ref.createCleaner
import kotlin.random.Random
import kotlin.reflect.KClass

private data class JSValueRef(
    var ref: napi_ref?,
    val tid: Int,
    val finalize: (() -> Unit)?
)

private const val JSValueCleanerSize = 2_000
private var jsValueCleaner = ArrayList<napi_ref?>(JSValueCleanerSize)

/**
 *  JSValue ：JavaScript 一个值
 *  封装 napi_value，可能是对象，方法，或任意值
 */
class JSValue(val env: napi_env?, origin: napi_value?, val tid: Int, val finalize: (() -> Unit)?) {

    constructor(origin: napi_value?) : this(getEnv(), origin, get_tid(), null)
    constructor(origin: napi_value?, tid: Int) : this(getEnv(), origin, tid, null)
    constructor(env: napi_env?, origin: napi_value?) : this(env, origin, get_tid(), null)
    constructor(env: napi_env?, origin: napi_value?, tid: Int) : this(env, origin, tid, null)

    private val ref = createReference(getEnv(), origin)
    private val jsValueRef = JSValueRef(ref, tid, finalize)

    private val clean = createCleaner(jsValueRef) {
        // Kotlin gc need delete Reference.
        if (it.tid == mainTid) {
            // 仅在主线程进行分批清理操作
            jsValueCleaner.add(it.ref)
            if (jsValueCleaner.size >= JSValueCleanerSize) {
                val jsValueCleanerRef = jsValueCleaner
                jsValueCleaner = ArrayList<napi_ref?>(JSValueCleanerSize)
                runOnMainThread({
                    val mainEnv = getEnv()
                    jsValueCleanerRef.forEach {
                        deleteReference(mainEnv, it)
                    }
                    jsValueCleanerRef.clear()
                }, Random.nextLong(2000, 10_000))
            }
        } else {
            // 在非主线程切换至该线程，进行清理操作
            tsfnRegister.callAsyncSafe(it.tid) {
                deleteReference(getEnv(), it.ref)
                it.finalize?.invoke()
            }
        }

    }

    /**
     * 被包装的 napi_value 指针
     */
    val handle: napi_value?
        get() {
            if (Platform.isDebugBinary && isDebug && get_tid() != tid) {
                throw RuntimeException("JSValue#handle cannot run in multi-thread! thread:${tid} currentThread:${get_tid()}")
            }
            return getReferenceValue(env, ref)
        }

    companion object {
        /**
         * 获取 global 对象
         * @param tid JavaScript 线程 id，默认值为当前线程
         * @return 当前线程的 global 对象
         */
        fun global(tid: Int = get_tid()): JSValue {
            return tsfnRegister.callSyncSafe(tid) {
                return@callSyncSafe JSValue(getGlobal(getEnv()))
            }!!
        }

        /**
         * 创建一个 JS 对象
         * @param tid JavaScript 线程 id, 默认值为当前线程
         * @return 创建的 JS 对象
         */
        fun createJSObject(tid: Int = get_tid()): JSValue {
            return trace("JSValue:createJSObject") {
                tsfnRegister.callSyncSafe(tid) {
                    return@callSyncSafe JSValue(createObject(getEnv()))
                }!!
            }
        }

        /**
         * 将 Native 指针 包装成 JS 对象。
         * @param value 任意 Native 指针
         * @param tid JavaScript 线程 id
         * @return 包装后的 JSValue
         */
        inline fun <reified T : Any> wrap(value: T, tid: Int = get_tid()): JSValue {
            return trace("JSValue:wrap") {
                tsfnRegister.callSyncSafe(tid) {
                    val jsThis = createJSObject()
                    val valuePtr = StableRef.create(value).asCPointer()
                    platform.ohos.knoi.wrap(
                        getEnv(),
                        jsThis.handle,
                        valuePtr,
                        staticCFunction { _: napi_env, data: COpaquePointer, _: COpaquePointer ->
                            val ref = data.asStableRef<T>()
                            ref.dispose()
                        })
                    return@callSyncSafe jsThis
                }!!
            }
        }

        /**
         * 将 JSValue 解包装为 Kotlin 类型
         * @param value JSValue
         * @param tid JavaScript 线程 id
         * @return 解包装 Kotlin 类型
         */
        inline fun <reified T : Any> unwrap(value: JSValue, tid: Int = get_tid()): T? {
            return trace("JSValue:unwrap") {
                tsfnRegister.callSyncSafe(tid) {
                    val ptr =
                        platform.ohos.knoi.unwrap(getEnv(), value.handle)
                            ?: return@callSyncSafe null
                    return@callSyncSafe ptr.asStableRef<T>().get()
                }
            }
        }

        /**
         * 使用 Kotlin 闭包创建 JavaScript 方法
         * @param block Kotlin闭包
         * @return JavaScript 方法
         */
        inline fun <reified T : Any> createJSFunction(
            noinline block: (jsThis: JSValue, params: Array<JSValue?>) -> T
        ): JSValue {
            return trace("JSValue:createJSFunction") {
                val ref = StableRef.create(block)
                val jsFunction = createFunction(
                    getEnv(),
                    "",
                    staticCFunction { env: napi_env?, info: napi_callback_info? ->
                        val jsThis = JSValue(getCbInfoThis(env, info))
                        val blockPtr = getCbInfoData(env, info)
                        val list = convertJSCallbackInfoToKTParamList<JSValue>(env, info)
                        if (blockPtr == null) {
                            return@staticCFunction null
                        }
                        val blockRef =
                            blockPtr.asStableRef<(jsThis: JSValue, params: Array<Any?>) -> T>()
                        val result = blockRef.get().invoke(jsThis, list.toTypedArray())
                        return@staticCFunction ktValueToJSValue(env, result, T::class)

                    },
                    ref.asCPointer()
                )
                JSValue(getEnv(), jsFunction, get_tid()) {
                    ref.dispose()
                }
            }
        }

        /**
         * 将 Kotlin 类型转换成 JSValue，类型转换规则见“类型转换文档”
         * @param value Kotlin 类型
         * @param tid 线程 ID
         * @return 创建的 JSValue
         */
        inline fun <reified T : Any> createJSValue(value: T, tid: Int = get_tid()): JSValue {
            return trace("JSValue:createJSValue") {
                tsfnRegister.callSyncSafe(tid) {
                    return@callSyncSafe JSValue(ktValueToJSValue(getEnv(), value, T::class));
                }!!
            }
        }
    }


    operator fun get(index: Int): JSValue? {
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe if (!checkArrayIndex(index)) {
                null
            } else {
                JSValue(getElementInArray(getEnv(), handle, index))
            }
        }
    }

    operator fun set(index: Int, value: JSValue): Boolean {
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe if (!checkArrayIndex(index)) {
                false
            } else {
                setElementInArray(getEnv(), handle, index, value.handle)
                true
            }
        }!!
    }

    private fun checkArrayIndex(index: Int): Boolean {
        return tsfnRegister.callSyncSafe(tid) {
            if (!isArrayType()) {
                return@callSyncSafe false
            }
            val length = getArrayLength(getEnv(), handle).toInt()
            return@callSyncSafe index < length
        }!!
    }

    operator fun get(key: String): JSValue? {
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe if (!checkObjectKey(key)) {
                null
            } else {
                JSValue(getPropertyValue(getEnv(), handle, key))
            }
        }
    }

    operator fun set(key: String, value: JSValue): Boolean {
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe if (!checkObjectKey(key)) {
                false
            } else {
                setPropertyValue(getEnv(), handle, key, value.handle)
                true
            }
        }!!
    }

    private fun checkObjectKey(key: String): Boolean {
        return tsfnRegister.callSyncSafe(tid) {
            if (!isObject()) {
                return@callSyncSafe false
            }
            return@callSyncSafe key.isNotEmpty()
        }!!
    }

    /**
     * 调用 JavaScript 对象的方法
     * @param name 方法米给你做
     * @param params 传入方法调用的参数包
     * @return 方法调用返回值
     */
    inline fun <reified T : Any> callMethod(name: String, vararg params: Any?): T? {
        return trace("JSValue callMethod $name") {
            var exception: Exception? = null
            val result = tsfnRegister.callSyncSafe<T>(tid) {
                try {
                    val func = this[name]
                    if (func == null) {
                        e("name = $name is null.")
                        return@callSyncSafe null
                    }
                    if (isDebug) {
                        if (func.isUndefined()) {
                            e("name = $name is undefined. Did forget to configure obfuscation?")
                            return@callSyncSafe null
                        }
                        if (!func.isFunction()) {
                            e("name = $name not a Function.")
                            return@callSyncSafe null
                        }
                    }
                    return@callSyncSafe safeCaseNumberType(
                        invokeDirect(
                            params,
                            T::class,
                            func,
                            this
                        ), T::class
                    ) as T?
                } catch (e: Exception) {
                    exception = e
                }
                return@callSyncSafe null
            }
            // 跨线程调用，重新抛出异常
            if (exception != null) {
                e("JSValue callMethod throw Exception. ${exception!!.message}")
                throw exception!!
            }
            result
        }
    }

    /**
     * 调用 JSValue 方法
     * @param params 参数包
     * @return 方法调用返回值
     */
    inline fun <reified T : Any> invoke(vararg params: Any?): T? {
        return trace("JSValue:invoke") {
            tsfnRegister.callSyncSafe(tid) {
                val jsFunction = JSFunction(getEnv(), "", handle)
                return@callSyncSafe jsFunction.invoke(*params)
            }
        }
    }

    private inline fun <reified T : Any> convertType(clazz: KClass<T>): T? {
        if (isNotAvailable()) return null
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe jsValueToKTValue(getEnv(), handle, clazz) as T
        }
    }

    /**
     * JSValue 转换 Int 类型
     */
    fun toInt(): Int {
        return convertType(Int::class) ?: 0
    }

    /**
     * JSValue 转换 Double 类型
     */
    fun toDouble(): Double = convertType(Double::class) ?: 0.0

    /**
     * JSValue 转换 Long 类型
     */
    fun toLong(): Long = convertType(Long::class) ?: 0L

    /**
     * JSValue 转换 字符串类型
     */
    fun toKString(): String? {
        return convertType(String::class)
    }

    /**
     * JSValue 转 Boolean
     */
    fun toBoolean(): Boolean = convertType(Boolean::class) ?: false

    /**
     * JSValue（JS 对象）转 Map<String, Any?>
     */
    @Suppress("UNCHECKED_CAST")
    fun toMap(): Map<String, Any?> = convertType(Map::class) as Map<String, Any?>

    /**
     * JSValue 转数组，仅支持同类型数组
     */
    inline fun <reified T : Any> toArray(): Array<T> {
        return toList<T>().toTypedArray()
    }

    /**
     * JSValue 转 List，仅支持同类型
     */
    inline fun <reified T : Any> toList(): List<T> {
        if (isNotAvailable()) return emptyList()
        return tsfnRegister.callSyncSafe(tid) {
            val result = mutableListOf<T>()
            val length = getJsArrayLikeLength(getEnv(), handle)
            for (index in 0 until length) {
                val converted = jsValueToKTValue(getEnv(), this[index]?.handle, T::class)
                result.add(coerceJsNumber(converted, T::class) as T)
            }
            return@callSyncSafe result
        }!!
    }

    /**
     * JSValue 转 ArrayBuffer
     */
    fun toArrayBuffer(): ArrayBuffer {
        if (isNotAvailable()) return ArrayBuffer()
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe ArrayBuffer(handle)
        }!!
    }

    /**
     * 获取 JSValue 包装的 napi_value 类型
     */
    private fun getType(): napi_valuetype {
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe typeOf(getEnv(), handle)
        }!!
    }

    /**
     * 判断是否为 null 或 undefined
     * JavaScript 中 undefined 和 null 都映射到 Kotlin 中的 null
     */
    fun isNotAvailable(): Boolean = isUndefined() || isNull()

    /**
     * 是否为 undefined
     */
    fun isUndefined(): Boolean = getType() == napi_valuetype.napi_undefined

    /**
     * 是否为 Boolean 类型
     */
    fun isBoolean(): Boolean = getType() == napi_valuetype.napi_boolean

    /**
     * 是否为 Function 类型
     */
    fun isFunction(): Boolean = getType() == napi_valuetype.napi_function

    /**
     * 是否为 Number 类型
     */
    fun isNumber(): Boolean = getType() == napi_valuetype.napi_number

    /**
     * 是否为 Object 类型
     */
    fun isObject(): Boolean = getType() == napi_valuetype.napi_object

    /**
     * 是否字符串类型
     */
    fun isString(): Boolean = getType() == napi_valuetype.napi_string

    /**
     * 是否为 null
     */
    fun isNull(): Boolean = getType() == napi_valuetype.napi_null

    /**
     * 是否为数组类型
     */
    fun isArrayType(): Boolean {
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe isArray(getEnv(), handle)
        }!!
    }

    /**
     * 是否为 ArrayBuffer 类型
     */
    fun isArrayBuffer(): Boolean {
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe platform.ohos.knoi.isArrayBuffer(
                getEnv(),
                handle
            ) || platform.ohos.knoi.isTypedArray(getEnv(), handle)
        }!!
    }

    /**
     * 判断 两个 JSValue 是否在 JavaScript 中全等
     */
    fun isStrictEquals(other: Any?): Boolean {
        if (other !is JSValue) {
            return false
        }
        return tsfnRegister.callSyncSafe(tid) {
            return@callSyncSafe isEquals(getEnv(), handle, other.handle)
        }!!
    }
}