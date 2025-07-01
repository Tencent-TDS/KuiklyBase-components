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
package com.tencent.kmm.network.internal

import com.tencent.kmm.network.export.VBTransportBaseRequest
import com.tencent.kmm.network.export.VBTransportBaseResponse
import com.tencent.kmm.network.export.VBTransportBytesCompletionHandler
import com.tencent.kmm.network.export.VBTransportBytesRequest
import com.tencent.kmm.network.export.VBTransportBytesResponse
import com.tencent.kmm.network.export.VBTransportGetHandler
import com.tencent.kmm.network.export.VBTransportGetRequest
import com.tencent.kmm.network.export.VBTransportGetResponse
import com.tencent.kmm.network.export.VBTransportPostHandler
import com.tencent.kmm.network.export.VBTransportPostRequest
import com.tencent.kmm.network.export.VBTransportPostResponse
import com.tencent.kmm.network.export.VBTransportResultCode
import com.tencent.kmm.network.export.VBTransportStringCompletionHandler
import com.tencent.kmm.network.export.VBTransportStringRequest
import com.tencent.kmm.network.export.VBTransportStringResponse
import com.tencent.kmm.network.internal.platform.getIVBTransportService

class VBTransportTask(
    val requestId: Int,
    val useCurl: Boolean,
    val logTag: String,
    private val taskManager: VBTransportManager
) {

    private var state: VBTransportState = VBTransportState.Create

    private fun wrapGetResponse(
        getCallback: ((getResponse: VBTransportGetResponse) -> Unit)?
    ): ((baseResponse: VBTransportBaseResponse) -> Unit)? {
        getCallback ?: return null
        return { response ->
            val res = response as? VBTransportGetResponse
            res?.let { getCallback(it) }
        }
    }

    private fun wrapPostResponse(
        postCallback: ((postResponse: VBTransportPostResponse) -> Unit)?
    ): ((baseResponse: VBTransportBaseResponse) -> Unit)? {
        postCallback ?: return null
        return { response ->
            val res = response as? VBTransportPostResponse
            res?.let { postCallback(it) }
        }
    }

    private fun wrapStringResponse(
        stringCallback: ((stringResponse: VBTransportStringResponse) -> Unit)?
    ): ((baseResponse: VBTransportBaseResponse) -> Unit)? {
        stringCallback ?: return null
        return { response ->
            val res = response as? VBTransportStringResponse
            res?.let { stringCallback(it) }
        }
    }

    private fun wrapBytesResponse(
        bytesCallback: ((bytesResponse: VBTransportBytesResponse) -> Unit)?
    ): ((baseResponse: VBTransportBaseResponse) -> Unit)? {
        bytesCallback ?: return null
        return { response ->
            val res = response as? VBTransportBytesResponse
            res?.let { bytesCallback(it) }
        }
    }

    private fun handleResponse(
        request: VBTransportBaseRequest,
        response: VBTransportBaseResponse,
        handler: ((response: VBTransportBaseResponse) -> Unit)?
    ) {
        handler?.let {
            if (isCanceledOrRemoved()) {
                logI("execute() request task is canceled")
                response.errorCode = VBTransportResultCode.CODE_CANCELED
                response.errorMessage = "请求已被取消"
                logI("execute() invoke failHandler，task has been canceled")
                it(response)
                return@let
            }
            it(response)
            taskManager.onTaskFinish(requestId)
        } ?: run {
            logI("handler is null!")
        }
    }

    fun sendBytesRequest(
        request: VBTransportBytesRequest,
        handler: VBTransportBytesCompletionHandler?,
    ) {
        if (isCanceledOrRemoved()) {
            val response = VBTransportBytesResponse()
            logI("execute() request task is canceled")
            handler?.let {
                response.errorCode = VBTransportResultCode.CODE_CANCELED
                response.errorMessage = "请求已被取消"
                logI("execute() invoke failHandler，task has been canceled")
                it(response)
            } ?: run {
                logI("task has been canceled and handler is null!")
            }
            return
        }
        state = VBTransportState.Running
        getIVBTransportService().sendBytesRequest(request) { response ->
            handleResponse(request, response, wrapBytesResponse(handler))
        }
    }

    private fun isCanceledOrRemoved(): Boolean =
        state == VBTransportState.Canceled || state == VBTransportState.Unknown

    // 发送字符类型Get类型网络请求
    fun sendStringRequest(
        request: VBTransportStringRequest,
        handler: VBTransportStringCompletionHandler?,
    ) {
        if (isCanceledOrRemoved()) {
            val response = VBTransportStringResponse()
            logI("execute() request task is canceled")
            handler?.let {
                response.errorCode = VBTransportResultCode.CODE_CANCELED
                response.errorMessage = "请求已被取消"
                logI("execute() invoke failHandler，task has been canceled")
                it(response)
            } ?: run {
                logI("task has been canceled and handler is null!")
            }
            return
        }
        state = VBTransportState.Running
        getIVBTransportService().sendStringRequest(request) { response ->
            handleResponse(request, response, wrapStringResponse(handler))
        }
    }

    fun sendPostRequest(
        request: VBTransportPostRequest,
        handler: VBTransportPostHandler?
    ) {
        if (isCanceledOrRemoved()) {
            val response = VBTransportPostResponse()
            logI("execute() request task is canceled before")
            handler?.let {
                response.errorCode = VBTransportResultCode.CODE_CANCELED
                response.errorMessage = "请求已被取消"
                logI("execute() invoke failHandler，task has been canceled")
                it(response)
            } ?: run {
                logI("task has been canceled and handler is null!")
            }
            return
        }
        state = VBTransportState.Running
        getIVBTransportService().post(request) { response ->
            handleResponse(request, response, wrapPostResponse(handler))
        }
    }

    fun sendGetRequest(
        request: VBTransportGetRequest,
        handler: VBTransportGetHandler?
    ) {
        if (isCanceledOrRemoved()) {
            val response = VBTransportGetResponse()
            logI("execute() request task is canceled before")
            handler?.let {
                response.errorCode = VBTransportResultCode.CODE_CANCELED
                response.errorMessage = "请求已被取消"
                logI("execute() invoke failHandler，task has been canceled")
                it(response)
            } ?: run {
                logI("task has been canceled and handler is null!")
            }
            return
        }
        state = VBTransportState.Running
        getIVBTransportService().get(request) { response ->
            handleResponse(request, response, wrapGetResponse(handler))
        }
    }

    fun getState(): VBTransportState = this.state

    fun setState(state: VBTransportState) {
        this.state = state
    }

    fun cancel() {
        state = VBTransportState.Canceled
        getIVBTransportService().cancel(requestId)
    }

    private fun logI(content: String) {
        VBPBLog.i(VBPBLog.TASK, "$logTag $content")
    }

}