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
package com.tencent.kmm.network.internal.utils

import com.tencent.kmm.network.export.VBTransportBaseRequest
import com.tencent.kmm.network.export.VBTransportBaseResponse
import com.tencent.kmm.network.export.VBTransportBytesRequest
import com.tencent.kmm.network.export.VBTransportBytesResponse
import com.tencent.kmm.network.export.VBTransportContentType
import com.tencent.kmm.network.export.VBTransportGetRequest
import com.tencent.kmm.network.export.VBTransportGetResponse
import com.tencent.kmm.network.export.VBTransportPostRequest
import com.tencent.kmm.network.export.VBTransportPostResponse
import com.tencent.kmm.network.export.VBTransportStringRequest
import com.tencent.kmm.network.export.VBTransportStringResponse
import com.tencent.kmm.network.internal.VBPBLog
import kotlinx.coroutines.Job

object VBTransportCommonUtils {
    private const val TAG = "NXNetwork_VBTransportCommonUtils"
    const val DEFAULT_BUFFER_SIZE = 8 * 1024

    fun wrapGetCallback(
        getCallback: (VBTransportGetResponse) -> Unit
    ): (VBTransportBaseResponse) -> Unit {
        return { baseResponse ->
            val getResponse = baseResponse as? VBTransportGetResponse
            getResponse?.let { getCallback(it) } ?: run {
                VBPBLog.i(TAG, "wrapGetCallback getResponse is null !!!")
            }
        }
    }

    fun wrapStringCallback(
        stringCallback: (VBTransportStringResponse) -> Unit
    ): (VBTransportBaseResponse) -> Unit {
        return { baseResponse ->
            val stringResponse = baseResponse as? VBTransportStringResponse
            stringResponse?.let { stringCallback(it) } ?: run {
                VBPBLog.i(TAG, "wrapStringCallback stringResponse is null!!!")
            }
        }
    }

    fun wrapPostCallback(
        postCallback: (VBTransportPostResponse) -> Unit
    ): (VBTransportBaseResponse) -> Unit {
        return { baseResponse ->
            val postResponse = baseResponse as? VBTransportPostResponse
            postResponse?.let { postCallback(it) } ?: run {
                VBPBLog.i(TAG, "wrapPostCallback postResponse is null !!!")
            }
        }
    }

    fun wrapBytesCallback(
        byteCallback: (VBTransportBytesResponse) -> Unit
    ): (VBTransportBaseResponse) -> Unit {
        return { baseResponse ->
            val byteResponse = baseResponse as? VBTransportBytesResponse
            byteResponse?.let { byteCallback(it) } ?: run {
                VBPBLog.i(TAG, "wrapBytesCallback byteResponse is null !!!")
            }
        }
    }

    fun buildResponseAndCallback(
        taskMap: MutableMap<Int, Job>,
        errorCode: Int,
        errorMsg: String,
        headers: Map<String, List<String>>,
        data: ByteArray,
        request: VBTransportBaseRequest,
        kmmCallback: (response: VBTransportBaseResponse) -> Unit
    ) {
        VBPBLog.i(TAG, "${request.logTag} receive response, errCode:${errorCode}, " +
                "errMsg:${errorMsg}, headers:${headers}, data size:${data.size}")
        if (request is VBTransportGetRequest) {
            // Get 请求回调
            val kmmGetResponse = VBTransportGetResponse().apply {
                updateResponse(errorCode, errorMsg, headers, data, request, this)
            }
            kmmCallback(kmmGetResponse)
        } else if (request is VBTransportStringRequest) {
            // String 请求回调
            val kmmStringResponse = VBTransportStringResponse().apply {
                updateResponse(errorCode, errorMsg, headers, data, request, this)
            }
            kmmCallback(kmmStringResponse)
        } else if (request is VBTransportPostRequest) {
            // Post 请求回调
            val kmmPostResponse = VBTransportPostResponse().apply {
                updateResponse(errorCode, errorMsg, headers, data, request, this)
            }
            kmmCallback(kmmPostResponse)
        } else if ((request is VBTransportBytesRequest)) {
            // Bytes 请求回调
            val kmmBytesResponse = VBTransportBytesResponse().apply {
                updateResponse(errorCode, errorMsg, headers, data, request, this)
            }
            kmmCallback(kmmBytesResponse)
        }
        taskMap.remove(request.requestId)
    }

    private fun updateResponse(
        errorCode: Int,
        errorMsg: String,
        headers: Map<String, List<String>>,
        data: ByteArray,
        request: VBTransportBaseRequest,
        response: VBTransportBaseResponse
    ) {
        response.errorCode = errorCode
        response.errorMessage = errorMsg
        response.header = headers
        when (response) {
            is VBTransportGetResponse -> {
                response.data = convertDataWithContentType(data, request)
                response.request = request as VBTransportGetRequest
            }

            is VBTransportPostResponse -> {
                response.data = convertDataWithContentType(data, request)
                response.request = request as VBTransportPostRequest
            }

            is VBTransportBytesResponse -> {
                response.data = data
                response.request = request as VBTransportBytesRequest
            }

            is VBTransportStringResponse -> {
                response.data = data.decodeToString()
                response.request = request as VBTransportStringRequest
            }
        }
    }

    private fun convertDataWithContentType(
        responseBytes: ByteArray?,
        request: VBTransportBaseRequest
    ): Any? {
        val headers = request.header.mapKeys { it.key.lowercase() }
        return if (responseBytes != null &&
            headers.containsKey("content-type") &&
            headers["content-type"] == VBTransportContentType.JSON.toString()) {
            responseBytes.decodeToString()
        } else {
            responseBytes
        }
    }
}

expect suspend fun readKnownSize(channel: ByteReadChannelWrapper, contentLength: Long): ByteArray
expect suspend fun readUnknownSize(channel: ByteReadChannelWrapper): ByteArray
expect fun ByteArray.mergeFromChunks(chunks: List<ByteArray>)
expect fun getHttpClient(kmmRequest: VBTransportBaseRequest): Any?