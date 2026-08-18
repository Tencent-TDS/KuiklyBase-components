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

import com.tencent.kmm.network.export.IVBPBLog
import com.tencent.kmm.network.export.VBTransportBaseResponse
import com.tencent.kmm.network.export.VBTransportBytesRequest
import com.tencent.kmm.network.export.VBTransportBytesResponse
import com.tencent.kmm.network.export.VBTransportGetRequest
import com.tencent.kmm.network.export.VBTransportGetResponse
import com.tencent.kmm.network.export.VBTransportPostRequest
import com.tencent.kmm.network.export.VBTransportPostResponse
import com.tencent.kmm.network.export.VBTransportResultCode
import com.tencent.kmm.network.export.VBTransportStringRequest
import com.tencent.kmm.network.export.VBTransportStringResponse
import com.tencent.kmm.network.internal.utils.VBTransportCommonUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VBTransportIOScopeTest {

    private val loggedErrors = mutableListOf<String>()

    @AfterTest
    fun tearDown() {
        VBPBLog.logImpl = null
        loggedErrors.clear()
    }

    private fun installLogCapture() {
        VBPBLog.logImpl = object : IVBPBLog {
            override fun d(tag: String?, content: String?) = Unit

            override fun i(tag: String?, content: String?) = Unit

            override fun e(tag: String?, content: String?, throwable: Throwable?) {
                loggedErrors.add("${content.orEmpty()}|${throwable?.message.orEmpty()}")
            }
        }
    }

    @Test
    fun createExceptionResponseSurfacesCodeAndMessage() {
        val request = VBTransportGetRequest().apply {
            requestId = 7
            url = "https://example.test/fail"
        }
        val response = VBTransportCommonUtils.createExceptionResponse(
            request,
            RuntimeException("connection refused")
        )
        assertTrue(response is VBTransportGetResponse)
        assertEquals(VBTransportResultCode.CODE_EXCEPTION, response.errorCode)
        assertEquals("connection refused", response.errorMessage)
        assertEquals(request, (response as VBTransportGetResponse).request)
    }

    @Test
    fun createExceptionResponseMatchesRequestType() {
        assertTrue(
            VBTransportCommonUtils.createExceptionResponse(
                VBTransportStringRequest(),
                RuntimeException("x")
            ) is VBTransportStringResponse
        )
        assertTrue(
            VBTransportCommonUtils.createExceptionResponse(
                VBTransportBytesRequest(),
                RuntimeException("x")
            ) is VBTransportBytesResponse
        )
        assertTrue(
            VBTransportCommonUtils.createExceptionResponse(
                VBTransportPostRequest().apply { data = "" },
                RuntimeException("x")
            ) is VBTransportPostResponse
        )
    }

    @Test
    fun exceptionMessageFallsBackToClassName() {
        assertEquals("boom", VBTransportCommonUtils.exceptionMessage(IllegalStateException("boom")))
        assertEquals("IllegalStateException", VBTransportCommonUtils.exceptionMessage(IllegalStateException()))
    }

    @Test
    fun notifyRequestExceptionInvokesCallbackAndClearsTask() {
        installLogCapture()
        val request = VBTransportGetRequest().apply {
            requestId = 11
            logTag = "unit"
        }
        val taskMap = mutableMapOf<Int, Job>(11 to Job())
        var callbackResponse: VBTransportBaseResponse? = null

        VBTransportCommonUtils.notifyRequestException(
            taskMap,
            request,
            IllegalStateException("boom")
        ) { callbackResponse = it }

        assertEquals(VBTransportResultCode.CODE_EXCEPTION, callbackResponse?.errorCode)
        assertEquals("boom", callbackResponse?.errorMessage)
        assertTrue(taskMap.isEmpty())
        assertTrue(loggedErrors.any { it.contains("boom") })
    }

    @Test
    fun uncaughtExceptionIsLoggedAndDoesNotCancelScope() = runBlocking {
        installLogCapture()
        val scope = createNetworkIOScope("test-scope")
        val parentJob = scope.coroutineContext[Job]
        requireNotNull(parentJob)

        val failing = scope.launch {
            throw RuntimeException("io failure")
        }
        failing.join()

        assertTrue(loggedErrors.any { it.contains("io failure") })
        assertTrue(parentJob.isActive)

        var ran = false
        val followUp = scope.launch {
            ran = true
        }
        followUp.join()
        assertTrue(ran)
        assertTrue(followUp.isCompleted)
    }

    @Test
    fun launchTransportRequestDeliversExceptionToCallback() = runBlocking {
        installLogCapture()
        val scope = createNetworkIOScope("test-scope")
        val request = VBTransportGetRequest().apply {
            requestId = 21
            logTag = "unit"
        }
        val taskMap = mutableMapOf<Int, Job>()
        var callbackResponse: VBTransportBaseResponse? = null

        val job = scope.launchTransportRequest(request, taskMap, { callbackResponse = it }) {
            throw RuntimeException("socket timeout")
        }
        job.join()

        assertEquals(VBTransportResultCode.CODE_EXCEPTION, callbackResponse?.errorCode)
        assertEquals("socket timeout", callbackResponse?.errorMessage)
        assertTrue(callbackResponse is VBTransportGetResponse)
        assertTrue(taskMap.isEmpty())
        assertTrue(loggedErrors.any { it.contains("socket timeout") })
    }

    @Test
    fun launchTransportRequestDoesNotCallbackOnCancellation() = runBlocking {
        val scope = createNetworkIOScope("test-scope")
        val request = VBTransportGetRequest().apply { requestId = 22 }
        val taskMap = mutableMapOf<Int, Job>()
        var invoked = false

        val job = scope.launchTransportRequest(request, taskMap, { invoked = true }) {
            delay(10_000)
        }
        job.cancel()
        job.join()

        assertFalse(invoked)
        assertTrue(job.isCancelled)
    }
}
