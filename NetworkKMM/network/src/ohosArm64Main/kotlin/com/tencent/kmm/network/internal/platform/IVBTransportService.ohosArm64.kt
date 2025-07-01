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

import com.tencent.kmm.network.curl.CurlRequestService
import com.tencent.kmm.network.export.VBTransportBytesRequest
import com.tencent.kmm.network.export.VBTransportBytesResponse
import com.tencent.kmm.network.export.VBTransportGetRequest
import com.tencent.kmm.network.export.VBTransportGetResponse
import com.tencent.kmm.network.export.VBTransportPostRequest
import com.tencent.kmm.network.export.VBTransportPostResponse
import com.tencent.kmm.network.export.VBTransportStringRequest
import com.tencent.kmm.network.export.VBTransportStringResponse
import com.tencent.kmm.network.internal.VBPBLog

object HmTransportImpl : IVBTransportService {
    override fun sendBytesRequest(
        kmmBytesRequest: VBTransportBytesRequest,
        kmmBytesResponseCallback: (response: VBTransportBytesResponse) -> Unit
    ) {
        val logTag = kmmBytesRequest.logTag + "_" + kmmBytesRequest.requestId
        // 使用 libcurl 进行请求
        CurlRequestService.sendBytesRequest(kmmBytesRequest, kmmBytesResponseCallback, logTag)
    }

    override fun sendStringRequest(
        kmmStringRequest: VBTransportStringRequest,
        kmmStringResponseCallback: (response: VBTransportStringResponse) -> Unit
    ) {
        val logTag = kmmStringRequest.logTag + "_" + kmmStringRequest.requestId
        logI("[${logTag}] send string request: ${kmmStringRequest.requestId}")

        // 使用 libcurl 进行请求
        CurlRequestService.sendStringRequest(kmmStringRequest, kmmStringResponseCallback, logTag)
    }

    override fun post(
        kmmPostRequest: VBTransportPostRequest,
        kmmPostResponseCallback: (response: VBTransportPostResponse) -> Unit
    ) {
        val logTag = kmmPostRequest.logTag + "_" + kmmPostRequest.requestId

        // 使用 libcurl 进行请求
        CurlRequestService.sendPostRequest(kmmPostRequest, kmmPostResponseCallback, logTag)
    }

    override fun get(
        kmmGetRequest: VBTransportGetRequest,
        kmmGetResponseCallback: (response: VBTransportGetResponse) -> Unit
    ) {
        val logTag = kmmGetRequest.logTag + "_" + kmmGetRequest.requestId

        // 使用 libcurl 进行请求
        CurlRequestService.sendGetRequest(kmmGetRequest, kmmGetResponseCallback, logTag)
    }

    override fun cancel(requestId: Int) {
        logI("cancel request id: $requestId")
        CurlRequestService.cancel(requestId)
    }

    private fun logI(content: String) {
        VBPBLog.i(VBPBLog.HMTRANSPORTIMPL, content)
    }
}

actual fun getIVBTransportService(): IVBTransportService = HmTransportImpl