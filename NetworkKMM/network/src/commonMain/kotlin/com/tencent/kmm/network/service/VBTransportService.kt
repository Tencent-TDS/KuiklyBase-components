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
package com.tencent.kmm.network.service

import com.tencent.kmm.network.export.VBTransportBaseRequest
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
import com.tencent.kmm.network.internal.VBPBLog
import com.tencent.kmm.network.internal.VBPBRequestIdGenerator
import com.tencent.kmm.network.internal.VBTransportManager
import com.tencent.kmm.network.internal.VBTransportState
import com.tencent.kmm.network.internal.VBTransportTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object VBTransportService {

    private val networkScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val taskManager = VBTransportManager

    // 发送字节数组Post类型网络请求
    fun sendBytesRequest(
        request: VBTransportBytesRequest,
        handler: VBTransportBytesCompletionHandler?,
    ) {
        request.requestId = VBPBRequestIdGenerator.getRequestId()
        networkScope.launch(track = true) {
            val task = VBTransportTask(request.requestId, request.useCurl, request.logTag, taskManager)
            taskManager.onTaskBegin(task)
            task.sendBytesRequest(request) { response ->
                if (task.getState() != VBTransportState.Done) {
                    task.setState(VBTransportState.Done)
                    handler?.let { it(response) }
                }
            }
        }
        startTimeoutCheckTask(request.totalTimeout, request.requestId, request) {
            val timeoutResponse = VBTransportBytesResponse().apply {
                this.request = request
                this.errorCode = VBTransportResultCode.CODE_FORCE_TIMEOUT
                this.errorMessage = "请求超时"
            }
            handler?.invoke(timeoutResponse)
        }
    }

    // 发送字符类型Get类型网络请求
    fun sendStringRequest(
        request: VBTransportStringRequest,
        handler: VBTransportStringCompletionHandler?,
    ) {
        request.requestId = VBPBRequestIdGenerator.getRequestId()
        networkScope.launch(track = true) {
            val task = VBTransportTask(request.requestId, request.useCurl, request.logTag, taskManager)
            taskManager.onTaskBegin(task)
            task.sendStringRequest(request) { response ->
                if (task.getState() != VBTransportState.Done) {
                    task.setState(VBTransportState.Done)
                    handler?.let { it(response) }
                }
            }
        }
        startTimeoutCheckTask(request.totalTimeout, request.requestId, request) {
            val timeoutResponse = VBTransportStringResponse().apply {
                this.request = request
                this.errorCode = VBTransportResultCode.CODE_FORCE_TIMEOUT
                this.errorMessage = "请求超时"
            }
            handler?.invoke(timeoutResponse)
        }
    }

    fun sendPostRequest(
        request: VBTransportPostRequest,
        handler: VBTransportPostHandler?
    ) {
        request.requestId = VBPBRequestIdGenerator.getRequestId()
        networkScope.launch(track = true) {
            val task = VBTransportTask(request.requestId, request.useCurl, request.logTag, taskManager)
            taskManager.onTaskBegin(task)
            task.sendPostRequest(request) { response ->
                if (task.getState() != VBTransportState.Done) {
                    task.setState(VBTransportState.Done)
                    handler?.let { it(response) }
                }
            }
        }
        startTimeoutCheckTask(request.totalTimeout, request.requestId, request) {
            val timeoutResponse = VBTransportPostResponse().apply {
                this.request = request
                this.errorCode = VBTransportResultCode.CODE_FORCE_TIMEOUT
                this.errorMessage = "请求超时"
            }
            handler?.invoke(timeoutResponse)
        }
    }

    fun sendGetRequest(
        request: VBTransportGetRequest,
        handler: VBTransportGetHandler?
    ) {
        request.requestId = VBPBRequestIdGenerator.getRequestId()
        networkScope.launch(track = true) {
            val task = VBTransportTask(request.requestId, request.useCurl, request.logTag, taskManager)
            taskManager.onTaskBegin(task)
            task.sendGetRequest(request) { response ->
                if (task.getState() != VBTransportState.Done) {
                    task.setState(VBTransportState.Done)
                    handler?.let { it(response) }
                }
            }
        }
        startTimeoutCheckTask(request.totalTimeout, request.requestId, request) {
            val timeoutResponse = VBTransportGetResponse().apply {
                this.request = request
                this.errorCode = VBTransportResultCode.CODE_FORCE_TIMEOUT
                this.errorMessage = "请求超时"
            }
            handler?.invoke(timeoutResponse)
        }
    }

    private fun startTimeoutCheckTask(
        timeout: Long,
        requestId: Int,
        request: VBTransportBaseRequest,
        handlerBlock: () -> Unit
    ) {
        networkScope.launch(track = true) {
            VBPBLog.i(
                VBPBLog.TASK_MANAGER, "${request.logTag} execute() coroutine " +
                    "totalTimeout: ${timeout}, requestId: $requestId")
            if (timeout <= 0) return@launch
            delay(timeout)
            taskManager.getTask(requestId)?.let { task ->
                if (task.getState() != VBTransportState.Done) {
                    task.setState(VBTransportState.Done)
                    handlerBlock()
                }
            }
        }
    }

    /**
     * 获取http请求状态
     * @param requestId 请求编号
     * @return 请求状态
     *         Create - 已创建
     *         Running - 正在执行
     *         Canceled - 已取消
     *         Done - 已完成
     *         Unknown - 已取消或已完成后删除
     */
    fun getState(requestId: Int): VBTransportState = taskManager.getState(requestId)

    fun cancel(requestId: Int) {
        taskManager.cancel(requestId)
    }
}