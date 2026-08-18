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
import com.tencent.kmm.network.internal.utils.VBTransportCommonUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Long-lived IO scope for transport requests.
 *
 * [SupervisorJob] keeps one failed request from cancelling sibling requests.
 * [CoroutineExceptionHandler] prevents uncaught exceptions from reaching
 * CoroutineScheduler / the process uncaught handler.
 */
internal fun createNetworkIOScope(tag: String): CoroutineScope {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        VBPBLog.e(tag, "Uncaught exception in network IO scope: ${throwable.message}", throwable)
    }
    return CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
}

/**
 * Launch a platform request on [this] scope. Failures are logged and delivered
 * through [callback] instead of crashing the process. Cancellation is rethrown
 * so [Job.cancel] still behaves as before.
 */
internal fun CoroutineScope.launchTransportRequest(
    request: VBTransportBaseRequest,
    taskMap: MutableMap<Int, Job>,
    callback: (response: VBTransportBaseResponse) -> Unit,
    block: suspend () -> Unit
): Job {
    val job = launch {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            VBTransportCommonUtils.notifyRequestException(taskMap, request, error, callback)
        }
    }
    taskMap[request.requestId] = job
    return job
}
