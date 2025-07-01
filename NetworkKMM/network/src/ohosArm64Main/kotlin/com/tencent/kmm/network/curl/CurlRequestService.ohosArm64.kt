/*
 * Tencent is pleased to support the open source community by making KuiklyBase available.
 * Copyright (C) 2025 Tencent. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tencent.kmm.network.curl

import com.tencent.kmm.network.export.IVBPBLog
import com.tencent.kmm.network.internal.VBPBLog
import com.tencent.kmm.network.internal.utils.VBTransportCommonUtils.wrapBytesCallback
import com.tencent.kmm.network.internal.utils.VBTransportCommonUtils.wrapGetCallback
import com.tencent.kmm.network.internal.utils.VBTransportCommonUtils.wrapPostCallback
import com.tencent.kmm.network.internal.utils.VBTransportCommonUtils.wrapStringCallback
import com.tencent.qqlive.kmm.native.libcurl.Cancel
import com.tencent.qqlive.kmm.native.libcurl.CreateCurlClient
import com.tencent.qqlive.kmm.native.libcurl.CurlCallback
import com.tencent.qqlive.kmm.native.libcurl.CurlRequest
import com.tencent.qqlive.kmm.native.libcurl.CurlResponse
import com.tencent.qqlive.kmm.native.libcurl.DeleteCurlClient
import com.tencent.qqlive.kmm.native.libcurl.StartRequest
import com.tencent.qqlive.kmm.native.libcurl.StringDic
import com.tencent.qqlive.kmm.native.libcurl.StringPair
import com.tencent.qqlive.kmm.native.libcurl.setCurlLogImpl
import com.tencent.kmm.network.export.VBTransportBaseRequest
import com.tencent.kmm.network.export.VBTransportBaseResponse
import com.tencent.kmm.network.export.VBTransportBytesRequest
import com.tencent.kmm.network.export.VBTransportBytesResponse
import com.tencent.kmm.network.export.VBTransportContentType
import com.tencent.kmm.network.export.VBTransportElapseStatistics
import com.tencent.kmm.network.export.VBTransportGetRequest
import com.tencent.kmm.network.export.VBTransportGetResponse
import com.tencent.kmm.network.export.VBTransportPostRequest
import com.tencent.kmm.network.export.VBTransportPostResponse
import com.tencent.kmm.network.export.VBTransportStringRequest
import com.tencent.kmm.network.export.VBTransportStringResponse
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.int8_tVar
import platform.posix.memcpy
import kotlin.reflect.KFunction1

private const val TAG = "CurlRequestServiceHM"

var curlLogNative: IVBPBLog? = null
private const val CURL_LOG_LEVEL_DEBUG = 0
private const val CURL_LOG_LEVEL_INFO = 1
private const val CURL_LOG_LEVEL_WARN = 2
private const val CURL_LOG_LEVEL_ERROR = 3

fun curlLogImpl(level: Int, tag: CPointer<ByteVar>?, content: CPointer<ByteVar>?): Int {
    when (level) {
        CURL_LOG_LEVEL_DEBUG -> {
            curlLogNative?.d(toSafeString(tag), "[Debug] " + toSafeString(content))
        }
        CURL_LOG_LEVEL_INFO -> {
            curlLogNative?.i(toSafeString(tag), "[Info] " + toSafeString(content))
        }
        CURL_LOG_LEVEL_WARN -> {
            curlLogNative?.e(toSafeString(tag), "[Warn] " + toSafeString(content))
        }
        CURL_LOG_LEVEL_ERROR -> {
            curlLogNative?.e(toSafeString(tag), "[Error] " + toSafeString(content))
        }
        else ->
            VBPBLog.i(toSafeString(tag), "Leve:$level: Content:${toSafeString(content)}")
    }
    return 1
}

private fun toSafeString(content: CPointer<ByteVar>?): String {
    return content?.toKString() ?: ""
}

// Curl 鸿蒙平台实现
object CurlRequestServiceHM : ICurlRequestService {

    private val taskMap: MutableMap<Int, CPointer<out CPointed>?> = mutableMapOf()

    override fun initNativeCurlLog(log: IVBPBLog) {
        curlLogNative = log
        memScoped {
            setCurlLogImpl(staticCFunction(::curlLogImpl))
        }
    }

    // String kmm -> c
    private fun toCSTR(string: String?, memScope: MemScope): CPointer<ByteVar> {
        return string?.cstr?.getPointer(memScope) ?: "".cstr.getPointer(memScope)
    }

    // Map kmm -> c
    private fun toStringDic(stringPair: Map<String, String>, memScope: MemScope): CPointer<StringDic> {
        val listSize = stringPair.size
        val stringPairList = stringPair.toList()
        val stringPairsNative = memScope.allocArray<StringPair>(stringPairList.size)
        for (i in stringPairList.indices) {
            val (key, value) = stringPairList[i]
            stringPairsNative[i].first = toCSTR(key, memScope)
            stringPairsNative[i].second = toCSTR(value, memScope)
        }
        return memScope.alloc<StringDic> {
            this.size = listSize
            this.stringPairs = stringPairsNative
        }.ptr
    }

    override fun cancel(requestId: Int) {
        logI("TaskManager remove task, id:${requestId}")
        taskMap[requestId]?.let {
            logI("TaskManager remove task, id:${requestId} handler:${it}")
            Cancel(it)
        }
    }

    private fun getCurlRequestParams(
        request: VBTransportBaseRequest,
        headers: StringDic,
        memScope: MemScope,
        logTag: String
    ): CValue<CurlRequest> {
        return cValue<CurlRequest> {
            this.url = toCSTR(request.url, memScope)
            this.headers = headers.ptr
            this.timeout = request.totalTimeout
            this.postBodyLen = 0
            if (request is VBTransportPostRequest && request.isDataInitialize()) {
                if (request.data is ByteArray) {
                    val byteData = (request.data as ByteArray)
                    logI("[$logTag] generate native Post curl params with bytearray data. " +
                            "size: ${byteData.size}, data: $byteData")
                    val buffer = nativeHeap.allocArray<int8_tVar>(byteData.size)
                    if (byteData.isNotEmpty()) {
                        byteData.usePinned { pinnedData ->
                            memcpy(buffer, pinnedData.addressOf(0), byteData.size.convert())
                        }
                    }
                    this.postBodyLen = byteData.size
                    this.postBody = buffer
                } else {
                    // String 类型
                    val strData = (request.data as? String) ?: ""
                    this.postBodyLen = strData.encodeToByteArray().size
                    this.postBody = toCSTR(strData, memScope)
                    logI("[$logTag] generate native Post params with string data. " +
                            "size: ${postBodyLen}, data: $strData")
                }
            } else if (request is VBTransportBytesRequest) {
                logI("[$logTag] generate native Bytes curl params with bytearray data. " +
                        "size: ${request.data.size}, data: ${request.data}")
                val buffer = nativeHeap.allocArray<int8_tVar>(request.data.size)
                if (request.data.isNotEmpty()) {
                    request.data.usePinned { pinnedData ->
                        memcpy(buffer, pinnedData.addressOf(0), request.data.size.convert())
                    }
                }
                this.postBodyLen = request.data.size
                this.postBody = buffer
            }
        }
    }

    override fun get(
        kmmGetRequest: VBTransportGetRequest,
        kmmGetResponseCallback: (response: VBTransportGetResponse) -> Unit,
        logTag: String
    ) {
        logI("[${logTag}] send get request, id: ${kmmGetRequest.requestId}, " +
                "url: ${kmmGetRequest.url}, header: ${kmmGetRequest.header}")
        startRequest(kmmGetRequest, wrapGetCallback(kmmGetResponseCallback), logTag)
    }

    override fun post(
        kmmPostRequest: VBTransportPostRequest,
        kmmPostResponseCallback: (response: VBTransportPostResponse) -> Unit,
        logTag: String
    ) {
        logI("[${logTag}] send post request, id: ${kmmPostRequest.requestId}, " +
                "url: ${kmmPostRequest.url}, header: ${kmmPostRequest.header}")
        startRequest(kmmPostRequest, wrapPostCallback(kmmPostResponseCallback), logTag)
    }

    override fun sendStringRequest(
        kmmStringRequest: VBTransportStringRequest,
        kmmStringResponseCallback: (response: VBTransportStringResponse) -> Unit,
        logTag: String
    ) {
        logI("[${logTag}] send string request, id: ${kmmStringRequest.requestId}, " +
                "url: ${kmmStringRequest.url}, header: ${kmmStringRequest.header}")
        startRequest(kmmStringRequest, wrapStringCallback(kmmStringResponseCallback), logTag)
    }

    override fun sendBytesRequest(
        kmmBytesRequest: VBTransportBytesRequest,
        kmmBytesResponseCallback: (response: VBTransportBytesResponse) -> Unit,
        logTag: String
    ) {
        logI("[${logTag}] send byte request, id: ${kmmBytesRequest.requestId}, " +
                "url: ${kmmBytesRequest.url}, header: ${kmmBytesRequest.header}")
        startRequest(kmmBytesRequest, wrapBytesCallback(kmmBytesResponseCallback), logTag)
    }

    private fun buildPBRequestHeader(headers: Map<String, String>): Map<String, String> {
        // 如果没有显式指定 Content-Type,需要添加默认 Content-Type 为 application/octet-stream
        val tmpHeaders = headers.takeIf {
            it.keys.any { key -> key.equals("Content-Type", ignoreCase = true) }
        } ?: run {
            headers + mapOf("Content-Type" to "application/octet-stream")
        }
        return tmpHeaders
    }

    private fun buildRequestHeader(
        request: VBTransportBaseRequest
    ): Map<String, String> {
        // 如果没有显式指定 Content-Type,需要添加默认 Content-Type
        val tmpHeaders = request.header.takeIf {
            it.keys.any { key -> key.equals("Content-Type", ignoreCase = true) }
        } ?: run {
            val contentType = when (request) {
                is VBTransportStringRequest -> "application/json"
                is VBTransportBytesRequest,
                is VBTransportPostRequest,
                is VBTransportGetRequest -> "application/octet-stream"
                else -> "application/octet-stream"
            }
            request.header + mapOf("Content-Type" to contentType)
        }
        request.header = tmpHeaders.toMutableMap()
        return tmpHeaders
    }

    private fun handleCurlNativeResponse(result: CurlResponse, logTag: String): CurlNativeResponse {
        val response = CurlNativeResponse(
            code = result.code,
            errorMsg = result.errorMsg?.toKString() ?: "",
            headers = result.headers?.toKString() ?: "",
            redirectUrl = result.redirectUrl?.toKString() ?: "",
            elapse = VBTransportElapseStatistics(
                nameLookupTimeMs = result.elapse.nameLookupTimeMs,
                connectTimeMs = result.elapse.connectTimeMs,
                sslCostTimeMs = result.elapse.sslCostTimeMs,
                preTransferTime = result.elapse.preTransferTime,
                startTransferTimeMs = result.elapse.startTransferTimeMs,
                redirectTime = result.elapse.redirectTime,
                recvTime = result.elapse.recvTime,
                totalTimeMs = result.elapse.totalTimeMs
            ),
            data = result.data?.let { data ->
                memScoped {
                    val size = result.dataLen
                    if (size > 0) data.readBytes(size) else ByteArray(0)
                }
            }
        )
//        VBPBLog.i(logTag, "[$logTag] libcurl request callback, code:${response.code}," +
//                " errorMsg:${response.errorMsg}, dataSize:${result.dataLen}, " +
//                "nameLookupTimeMs:${response.elapse.nameLookupTimeMs}, connectTimeMs:" +
//                "${response.elapse.connectTimeMs}, sslCostTimeMs:${response.elapse.sslCostTimeMs}, " +
//                "preTransferTime:${response.elapse.preTransferTime}, " +
//                "startTransferTimeMs:${response.elapse.startTransferTimeMs}, " +
//                "redirectTime:${response.elapse.redirectTime}, recvTime:${response.elapse.recvTime}, " +
//                "totalTimeMs:${response.elapse.totalTimeMs}, redirectUrl:${response.redirectUrl}, " +
//                "header:${response.headers}")
        return response
    }

    private fun startRequest(
        request: VBTransportBaseRequest,
        responseCallback: (response: VBTransportBaseResponse) -> Unit,
        logTag: String
    ) {
        memScoped {
            // header kmm-> c
            val headers = toStringDic(buildRequestHeader(request), memScope)
            val curlRequest = getCurlRequestParams(request, headers.pointed, memScope, logTag)
            var nativeResponse = CurlNativeResponse()
            val callback = object : ICurlCallback {
                override fun onResponse(result: CurlResponse) {
                    nativeResponse = handleCurlNativeResponse(result, logTag)
                }
            }

            // 使用 libcurl 发起请求
            val callbackWrapper = CurlCallbackWrapper(callback)
            val callbackWrapperPtr = callbackWrapper.getCallbackNativePtr()
            val handle = CreateCurlClient(logTag)
            taskMap[request.requestId] = handle
            logI("[$logTag] TaskManager add transport task, id:${request.requestId}, handle:${handle}")
            StartRequest(handle, curlRequest, callbackWrapperPtr)
            DeleteCurlClient(handle)
            taskMap.remove(request.requestId)

            callbackWrapper.release()

            // 构造回调信息
            buildResponseAndCallback(request, nativeResponse, responseCallback)
            logI("[$logTag] invoke callback.")
        }
    }

    private fun buildResponseAndCallback(
        request: VBTransportBaseRequest,
        nativeResponse: CurlNativeResponse,
        responseCallback: (response: VBTransportBaseResponse) -> Unit
    ) {
        if (request is VBTransportGetRequest) {
            // Get 请求回调
            val kmmGetResponse = VBTransportGetResponse().apply {
                updateResponse(request.logTag, nativeResponse, request, this)
            }
            responseCallback(kmmGetResponse)
        } else if (request is VBTransportStringRequest) {
            // String 请求回调
            val kmmStringResponse = VBTransportStringResponse().apply {
                updateResponse(request.logTag, nativeResponse, request, this)
            }
            responseCallback(kmmStringResponse)
        } else if (request is VBTransportPostRequest) {
            // Post 请求回调
            val kmmPostResponse = VBTransportPostResponse().apply {
                updateResponse(request.logTag, nativeResponse, request, this)
            }
            responseCallback(kmmPostResponse)
        } else if ((request is VBTransportBytesRequest)) {
            // Bytes 请求回调
            val kmmBytesResponse = VBTransportBytesResponse().apply {
                updateResponse(request.logTag, nativeResponse, request, this)
            }
            responseCallback(kmmBytesResponse)
        }
    }

    private fun updateResponse(
        logTag: String,
        nativeResponse: CurlNativeResponse,
        request: VBTransportBaseRequest,
        response: VBTransportBaseResponse
    ) {
        val data = convertDataWithContentType(nativeResponse.data, request)
        response.errorCode = nativeResponse.code
        response.errorMessage = nativeResponse.errorMsg
        response.header = convertHeaderMap(nativeResponse.headers)
        // 处理重定向情况
        if (nativeResponse.redirectUrl.isNotEmpty()) {
            logI("[$logTag] curl redirect url, old: ${request.url}, new: ${nativeResponse.redirectUrl}")
            request.url = nativeResponse.redirectUrl
        }

        response.elapseStatis = nativeResponse.elapse
        when (response) {
            is VBTransportPostResponse -> {
                response.data = data
                response.request = request as VBTransportPostRequest
            }

            is VBTransportGetResponse -> {
                response.data = data
                response.request = request as VBTransportGetRequest
            }

            is VBTransportBytesResponse -> {
                data?.let { response.data = data as ByteArray }
                response.request = request as VBTransportBytesRequest
            }

            is VBTransportStringResponse -> {
                data?.let { response.data = data as String }
                response.request = request as VBTransportStringRequest
            }
        }
    }

    private fun convertHeaderMap(headerStr: String): Map<String, List<String>> {
        return headerStr.lines()
            // 过滤空行和 HTTP 状态行（如 "HTTP/1.1 200 OK"）
            .filter { it.isNotBlank() && !it.startsWith("HTTP/") }
            // 处理每一行，分割键值对
            .mapNotNull { line ->
                val colonIndex = line.indexOf(':')
                if (colonIndex == -1) {
                    null
                } else {
                    val key = line.substring(0, colonIndex).trim()
                    val value = line.substring(colonIndex + 1).trim()
                    key to value
                }
            }
            // 合并相同键的值到列表（兼容多值头字段）
            .groupBy({ it.first }, { it.second })
    }

    private fun convertDataWithContentType(
        bodyBytes: ByteArray?,
        request: VBTransportBaseRequest
    ): Any? {
        val headers = request.header.mapKeys { it.key.lowercase() }
        return if (bodyBytes != null
            && headers.containsKey("content-type")
            && headers["content-type"] == VBTransportContentType.JSON.toString()) {
            bodyBytes.decodeToString()
        } else {
            bodyBytes
        }
    }

    private fun logI(content: String) {
        VBPBLog.i(VBPBLog.HMCURLIMPL, content)
    }
}

interface ICurlCallback {
    fun onResponse(result: CurlResponse)
}

// 图片加载回调 kotlin->c
class CurlCallbackWrapper(private val curlCallback: ICurlCallback) {
    private var callbackPtr: CPointer<CFunction<(COpaquePointer?, CPointer<CurlResponse>?) -> Unit>>
    private var callbackStableRef: StableRef<KFunction1<CPointer<CurlResponse>, Unit>>
    private var callBlackNative: CurlCallback

    init {
        callbackStableRef = StableRef.create(::onResponse)
        callbackPtr = staticCFunction(::createStableRef)
        callBlackNative = nativeHeap.alloc()
        callBlackNative.callbackRef = callbackStableRef.asCPointer()
        callBlackNative.callback = callbackPtr
    }

    private fun onResponse(result: CPointer<CurlResponse>) =
        curlCallback.onResponse(result.pointed)

    fun getCallbackNativePtr(): CPointer<CurlCallback> = callBlackNative.ptr

    fun release() {
        callbackStableRef.dispose()
    }
}

internal fun createStableRef(
    callbackRef: COpaquePointer?, result: CPointer<CurlResponse>?
) {
    callbackRef?.asStableRef<(CPointer<CurlResponse>?) -> Unit>()?.get()?.invoke(result)
}

actual fun getCurlRequestService(): ICurlRequestService = CurlRequestServiceHM