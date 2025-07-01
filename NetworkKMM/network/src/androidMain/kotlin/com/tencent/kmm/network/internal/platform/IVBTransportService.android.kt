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
package com.tencent.kmm.network.internal.platform

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
import com.tencent.kmm.network.internal.utils.ByteReadChannelWrapper
import com.tencent.kmm.network.internal.utils.VBTransportCommonUtils.buildResponseAndCallback
import com.tencent.kmm.network.internal.utils.VBTransportCommonUtils.wrapBytesCallback
import com.tencent.kmm.network.internal.utils.VBTransportCommonUtils.wrapGetCallback
import com.tencent.kmm.network.internal.utils.VBTransportCommonUtils.wrapPostCallback
import com.tencent.kmm.network.internal.utils.VBTransportCommonUtils.wrapStringCallback
import com.tencent.kmm.network.internal.utils.getHttpClient
import com.tencent.kmm.network.internal.utils.readKnownSize
import com.tencent.kmm.network.internal.utils.readUnknownSize
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val scope = CoroutineScope(Dispatchers.IO)
private val taskMap: MutableMap<Int, Job> = mutableMapOf()

object AndroidTransportImpl : IVBTransportService {
    private fun triggerRequest(
        request: VBTransportBaseRequest,
        kmmCallback: (response: VBTransportBaseResponse) -> Unit
    ) {
        val job = scope.launch {
            val client = getHttpClient(request) as HttpClient
            val response = if (request is VBTransportGetRequest || request is VBTransportStringRequest) {
                client.get(request.url) {
                    constructRequest(request)
                }
            } else {
                client.post(request.url) {
                    constructRequest(request)
                }
            }

            var errMsg = ""
            var errorCode = 0
            if (response.status != HttpStatusCode.OK) {
                errorCode = response.status.value
                errMsg = response.status.description
            }

            val channel = response.bodyAsChannel()
            val contentLength = response.contentLength()
            val data = if (contentLength == null) {
                // 动态扩容方案
                readUnknownSize(ByteReadChannelWrapper(channel))
            } else {
                // 预分配方案
                readKnownSize(ByteReadChannelWrapper(channel), contentLength)
            }

            buildResponseAndCallback(
                taskMap,
                errorCode,
                errMsg,
                response.headers.entries().associate { it.key to it.value },
                data,
                request,
                kmmCallback
            )
        }
        taskMap[request.requestId] = job
    }

    override fun sendBytesRequest(
        kmmBytesRequest: VBTransportBytesRequest,
        kmmBytesResponseCallback: (response: VBTransportBytesResponse) -> Unit
    ) {
        logI("send bytes request, id:${kmmBytesRequest.requestId}, url:${kmmBytesRequest.url}, " +
                "header:${kmmBytesRequest.header}", kmmBytesRequest.logTag)
        triggerRequest(kmmBytesRequest, wrapBytesCallback(kmmBytesResponseCallback))
    }

    override fun sendStringRequest(
        kmmStringRequest: VBTransportStringRequest,
        kmmStringResponseCallback: (response: VBTransportStringResponse) -> Unit
    ) {
        logI("send string request, id:${kmmStringRequest.requestId}, url:${kmmStringRequest.url}, " +
                "header:${kmmStringRequest.header}", kmmStringRequest.logTag)
        triggerRequest(kmmStringRequest, wrapStringCallback(kmmStringResponseCallback))
    }

    override fun post(
        kmmPostRequest: VBTransportPostRequest,
        kmmPostResponseCallback: (response: VBTransportPostResponse) -> Unit
    ) {
        logI("send post request, id:${kmmPostRequest.requestId}, url:${kmmPostRequest.url}, " +
                "header:${kmmPostRequest.header}", kmmPostRequest.logTag)

        if (!kmmPostRequest.isDataInitialize()) {
            throw IllegalArgumentException("Data is not initialized")
        }

        triggerRequest(kmmPostRequest, wrapPostCallback(kmmPostResponseCallback))
    }

    override fun get(
        kmmGetRequest: VBTransportGetRequest,
        kmmGetResponseCallback: (response: VBTransportGetResponse) -> Unit
    ) {
        logI("send get request, id:${kmmGetRequest.requestId}, url:${kmmGetRequest.url}, " +
                "header:${kmmGetRequest.header}", kmmGetRequest.logTag)
        triggerRequest(kmmGetRequest, wrapGetCallback(kmmGetResponseCallback))
    }

    private fun HttpRequestBuilder.constructRequest(kmmRequest: VBTransportBaseRequest) {
        // 设置 header
        kmmRequest.header
            .filter { (k, v) -> k.isNotEmpty() && v.isNotEmpty() }
            .forEach { (k, v) -> header(k, v) }

        // 设置 post body
        if (kmmRequest is VBTransportBytesRequest) {
            setBody(kmmRequest.data)
        } else if (kmmRequest is VBTransportPostRequest) {
            setBody(kmmRequest.data)
        }

        val requestContentType = kmmRequest.header["Content-Type"]?.let { contentType ->
            if (contentType.contains(VBTransportContentType.JSON.toString(), ignoreCase = true)) {
                ContentType.Application.Json
            } else {
                ContentType.Application.OctetStream
            }
        } ?: ContentType.Application.OctetStream
        contentType(requestContentType)
    }

    private fun logI(content: String, logTag: String = "") {
        VBPBLog.i(VBPBLog.HMTRANSPORTIMPL, "$logTag $content")
    }

    override fun cancel(requestId: Int) {
        logI("requestID -> $requestId task cancel by user")
        taskMap[requestId]?.cancel()
    }
}

actual fun getIVBTransportService(): IVBTransportService = AndroidTransportImpl