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
package com.tencent.kmm.component.sample

import com.tencent.kmm.network.service.VBTransportServiceTest
import com.tencent.tmm.knoi.annotation.KNExport
import com.tencent.tmm.knoi.handler.cancelBlock
import com.tencent.tmm.knoi.handler.runOnMainThread
import com.tencent.tmm.knoi.logger.info
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.CName
import kotlin.native.concurrent.Worker

@OptIn(ExperimentalNativeApi::class)
@CName("test_print_stacks")
fun testPrintStacks() {
    try {
        val array = arrayOf(1, 2, 3)
        val value = array[4]
    } catch (exception: Exception) {
        throw UnsupportedOperationException("Failed to get array", exception)
    }
}

@KNExport
fun testMainHandler() {
    info("testMainHandler")
    Worker.start().executeAfter {
        info("testMainHandler sub thread.")
        runOnMainThread {
            info("runOnMainThread run")
        }
        info("runOnMainThread delay")
        runOnMainThread({
            info("runOnMainThread delay run")
        }, 1000)

        val block = {
            info("runOnMainThread cancel error, delay run")
        }
        info("runOnMainThread ${block.hashCode()} delay")
        runOnMainThread(block, 2000)
        cancelBlock(block)
        info("cancelBlock ${block.hashCode()}")
    }
    info("testMainHandler main thread.")
    runOnMainThread {
        info("runOnMainThread run")
    }
    info("runOnMainThread delay")
    runOnMainThread({
        info("runOnMainThread delay run")
    }, 1000)
}

@KNExport
fun testSendStringRequestWithCurl() {
    println("testSendStringRequestWithCurl")
    VBTransportServiceTest.testServiceInit()
    VBTransportServiceTest.testSendStringRequestWithCurl()
}

@KNExport
fun testSendByteRequestWithCurl() {
    println("testSendByteRequestWithCurl")
    VBTransportServiceTest.testServiceInit()
    VBTransportServiceTest.testSendByteRequestWithCurl()
}

@KNExport
fun testSendGetRequestForByteContentTypeWithCurl() {
    println("testSendGetRequestForByteContentTypeWithCurl")
    VBTransportServiceTest.testServiceInit()
    VBTransportServiceTest.testSendGetRequestForByteContentTypeWithCurl()
}

@KNExport
fun testSendGetRequestForJsonContentTypeWithCurl() {
    println("testSendGetRequestForJsonContentTypeWithCurl")
    VBTransportServiceTest.testServiceInit()
    VBTransportServiceTest.testSendGetRequestForJsonContentTypeWithCurl()
}

@KNExport
fun testSendGetRequestForImageWithCurlV1() {
    println("testSendGetRequestForImageWithCurlV1")
    VBTransportServiceTest.testServiceInit()
    VBTransportServiceTest.testSendGetRequestForImageWithCurlV1()
}

@KNExport
fun testSendGetRequestForImageWithCurlV2() {
    println("testSendGetRequestForImageWithCurlV2")
    VBTransportServiceTest.testServiceInit()
    VBTransportServiceTest.testSendGetRequestForImageWithCurlV2()
}

@KNExport
fun testSendGetRequestForImageWithCurlV3() {
    println("testSendGetRequestForImageWithCurlV3")
    VBTransportServiceTest.testServiceInit()
    VBTransportServiceTest.testSendGetRequestForImageWithCurlV3()
}

@KNExport
fun testSendGetRequestForImageWithCurlV4() {
    println("testSendGetRequestForImageWithCurlV4")
    VBTransportServiceTest.testServiceInit()
    VBTransportServiceTest.testSendGetRequestForImageWithCurlV4()
}

@KNExport
fun testSend302RequestWithCurl() {
    println("testSend302RequestWithCurl")
    VBTransportServiceTest.testServiceInit()
    VBTransportServiceTest.testSend302RequestWithCurl()
}

@KNExport
fun testSendPostRequestForJsonDataWithCurl() {
    println("testSendPostRequestForJsonDataWithCurl")
    VBTransportServiceTest.testServiceInit()
    VBTransportServiceTest.testSendPostRequestForJsonDataWithCurl()
}

@KNExport
fun testSendPostRequestForEchoStringDataWithCurl() {
    println("testSendPostRequestForEchoStringDataWithCurl")
    VBTransportServiceTest.testServiceInit()
    VBTransportServiceTest.testSendPostRequestForEchoStringDataWithCurl()
}

@KNExport
fun testSendPostRequestForByteDataWithCurl() {
    println("testSendPostRequestForByteDataWithCurl")
    VBTransportServiceTest.testServiceInit()
    VBTransportServiceTest.testSendPostRequestForByteDataWithCurl()
}