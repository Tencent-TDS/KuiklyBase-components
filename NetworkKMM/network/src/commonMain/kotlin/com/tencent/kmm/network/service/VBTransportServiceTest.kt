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

import com.tencent.kmm.network.export.IVBPBLog
import com.tencent.kmm.network.export.VBTransportBytesRequest
import com.tencent.kmm.network.export.VBTransportContentType
import com.tencent.kmm.network.export.VBTransportGetRequest
import com.tencent.kmm.network.export.VBTransportPostRequest
import com.tencent.kmm.network.export.VBTransportResultCode
import com.tencent.kmm.network.export.VBTransportStringRequest
import com.tencent.kmm.network.internal.VBPBLog
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("VBTransportServiceTest")
object VBTransportServiceTest {
    private val byteData = byteArrayOf(
        9,
        48,
        0,
        0,
        0,
        0,
        0,
        -72,
        0,
        92,
        0,
        0,
        0,
        0,
        0,
        0,
        24,
        1,
        50,
        38,
        116,
        114,
        112,
        99,
        46,
        105,
        97,
        115,
        46,
        97,
        99,
        99,
        101,
        115,
        115,
        68,
        105,
        115,
        112,
        81,
        117,
        101,
        114,
        121,
        46,
        68,
        105,
        115,
        112,
        83,
        101,
        114,
        118,
        105,
        99,
        101,
        86,
        49,
        58,
        48,
        47,
        116,
        114,
        112,
        99,
        46,
        105,
        97,
        115,
        46,
        97,
        99,
        99,
        101,
        115,
        115,
        68,
        105,
        115,
        112,
        81,
        117,
        101,
        114,
        121,
        46,
        68,
        105,
        115,
        112,
        83,
        101,
        114,
        118,
        105,
        99,
        101,
        86,
        49,
        47,
        100,
        105,
        115,
        112,
        97,
        116,
        99,
        104,
        10,
        24,
        116,
        101,
        110,
        99,
        101,
        110,
        116,
        86,
        105,
        100,
        101,
        111,
        46,
        86,
        66,
        80,
        66,
        83,
        101,
        114,
        118,
        105,
        99,
        101,
        26,
        36,
        57,
        100,
        102,
        49,
        99,
        55,
        97,
        97,
        45,
        54,
        100,
        56,
        48,
        45,
        52,
        97,
        53,
        98,
        45,
        56,
        57,
        48,
        102,
        45,
        99,
        57,
        100,
        50,
        100,
        51,
        53,
        56,
        102,
        48,
        55,
        48,
        34,
        10,
        97,
        99,
        99,
        46,
        113,
        113,
        46,
        99,
        111,
        109
    )

    // 673KB
    private val imgUrl1 = "https://vcover-vt-pic.puui.qpic.cn/vcover_vt_pic/0/" +
            "mzc00200g84o1hl1709172542707/450?imageView2/format/png"
    private val urlBase = "https://video-public-1258344701.shiply-cdn.qq.com/reshub/qqvideo_test/"
    // 3.2M
    private val imgUrl2 =
        urlBase + "pag_component_test1/formal/20241204161035/Production/nature-6565499.jpg"
    // 12.6M
    private val imgUrl3 =
        urlBase + "pag_component_test1/formal/20241204161456/Production/sunset-7760143.jpg"
    // 47M
    private val imgUrl4 =
        urlBase + "pag_component_test1/formal/20241204163258/Production/023A7786.png"

    private data class TestResponseData(val content: Any?, val len: Int)

    @ObjCName("testSendByteRequest")
    fun testSendByteRequest(
        logTag: String = "TestKMMBytesRequest",
        useCurl: Boolean = false
    ) {
        val bytesRequest = VBTransportBytesRequest()
        bytesRequest.url = "https://a.video.qq.com"
        bytesRequest.data = byteData
        bytesRequest.logTag = logTag
        bytesRequest.header["test_header_1"] = "test_value_1"
        bytesRequest.header["test_header_2"] = "test_value_2"
        bytesRequest.header["test_header_3"] = "test_value_3"
        bytesRequest.useCurl = useCurl
        VBTransportService.sendBytesRequest(bytesRequest) {
            val responseData = convertResponseData(it.data)
            println("[TRACE] [TestKMMBytesRequest] byte response code: ${it.errorCode}, message:${it.errorMessage}," +
                    " size:${responseData.len}, request: ${it.request}, header:${it.header}, server ip:${it.serverIP}, port:${it.serverPort}, data:${responseData.content}")
        }
    }

    @ObjCName("testSendStringRequest")
    fun testSendStringRequest(
        logTag: String = "TestKMMStringRequest",
        useCurl: Boolean = false
    ) {
        val stringRequest = VBTransportStringRequest()
        stringRequest.url = "https://postman-echo.com/get?test_key1=111&test_key2=222"
        stringRequest.logTag = logTag
        stringRequest.header["test_header_1"] = "test_value_1"
        stringRequest.header["test_header_2"] = "test_value_2"
        stringRequest.header["test_header_3"] = "test_value_3"
        stringRequest.useCurl = useCurl
        VBTransportService.sendStringRequest(stringRequest) {
            VBPBLog.i(
                "[TRACE]",
                "string response code:${it.errorCode}, message:${it.errorMessage}, data:${it.data}, request: ${it.request}, server ip:${it.serverIP}, port:${it.serverPort}"
            )
        }
    }

    @ObjCName("testSendPostRequest")
    fun testSendPostRequest(
        logTag: String = "TestKMMPostRequest",
        useCurl: Boolean = false
    ) {
        val postRequest = VBTransportPostRequest()
        postRequest.url = "https://test-tips.video.qq.com/device/registerDevice"
        postRequest.logTag = logTag
        postRequest.header["AccessId"] = "1000101"
        postRequest.header["Sign"] =
            "YjVmZTY2OWYxOTVkNGQwYzdhZjhjMTM5ZGRiNGIzZDFjNmVmMmYzMGY0NWY4NmIyOTY3MWFhZDE2NmY4MjY4MQ=="
        postRequest.header["TimeStamp"] = "1719212175"
        postRequest.header["Content-Type"] = VBTransportContentType.JSON.toString()
        postRequest.data = ""
        postRequest.useCurl = useCurl
        postRequest.totalTimeout = 1500
        VBTransportService.sendPostRequest(postRequest) {
            var content = it.data
            var len = 0
            if (it.data is ByteArray) {
                content = (it.data as ByteArray).decodeToString()
                len = (it.data as ByteArray).size
            } else if (it.data is String) {
                len = (it.data as String).length
            }
            println(
                "[TRACE] [testSendPostRequestWithCurl] string response code: ${it.errorCode}, message:${it.errorMessage}," +
                        " size:${len}, data:${content}, request: ${it.request}, header:${it.header}, server ip:${it.serverIP}, port:${it.serverPort}"
            )
        }
    }

    @ObjCName("testSendGetRequest")
    fun testSendGetRequest(
        logTag: String = "TestKMMGetRequest",
        useCurl: Boolean = false,
        isByteContentType: Boolean = true
    ) {
        val getRequest = VBTransportGetRequest()
        getRequest.url =
            "https://video-public-1258344701.shiply-cdn.qq.com/reshub/qqvideo_test/lottie_component_test/20240702134513/Production/test_str.txt"
        getRequest.logTag = logTag
        getRequest.header["Content-Type"] = if (isByteContentType) {
            VBTransportContentType.BYTE.toString()
        } else {
            VBTransportContentType.JSON.toString()
        }
        getRequest.useCurl = useCurl
        VBTransportService.sendGetRequest(getRequest) {
            VBPBLog.i(
                "[TRACE]",
                "get response code:${it.errorCode}, message:${it.errorMessage}, data:${it.data}, request: ${it.request}, server ip:${it.serverIP}, port:${it.serverPort}"
            )
        }
    }

    fun testSend302Request(
        logTag: String = "TestKMM302Request",
        useCurl: Boolean = false
    ) {
        val getRequest = VBTransportGetRequest()
        // 302 相对路径
//        getRequest.url = "http://httpbin.org/redirect/1"
        // 302 绝对路径
//        getRequest.url = "https://order.jd.com/center/list.action"
        getRequest.url = "https://postman-echo.com/redirect-to?url=http://www.baidu.com&status_code=302"
        getRequest.logTag = logTag
        getRequest.header["Content-Type"] = VBTransportContentType.JSON.toString()
        getRequest.useCurl = useCurl
        VBTransportService.sendGetRequest(getRequest) {
            VBPBLog.i(
                "[TRACE]",
                "302 response code:${it.errorCode}, message:${it.errorMessage}, url:${it.request.url}, " +
                        "header:${it.header}, data:${it.data}, request: ${it.request}, server ip:${it.serverIP}, port:${it.serverPort}"
            )
        }
    }

    @ObjCName("testCancelRequest")
    fun testCancelRequest() {
        // 以 Post 为例
        val postRequest = VBTransportPostRequest()
        postRequest.url = "https://test-tips.video.qq.com/device/registerDevice"
        postRequest.logTag = "TestKMMPostRequest"
        postRequest.header["AccessId"] = "1000101"
        postRequest.header["Sign"] =
            "YjVmZTY2OWYxOTVkNGQwYzdhZjhjMTM5ZGRiNGIzZDFjNmVmMmYzMGY0NWY4NmIyOTY3MWFhZDE2NmY4MjY4MQ=="
        postRequest.header["TimeStamp"] = "1719212175"
        postRequest.header["Content-Type"] = VBTransportContentType.JSON.toString()
        postRequest.data = ""
        VBTransportService.sendPostRequest(postRequest) {
            VBPBLog.i(
                "[TRACE]",
                "string response code:${it.errorCode}, message:${it.errorMessage}, data:${it.data}, request: ${it.request}"
            )
            VBTransportService.cancel(postRequest.requestId)
        }
    }

    fun testSendByteRequestWithCurl() {
        testSendByteRequest(
            logTag = "TestKMMByteRequestWithCurl",
            useCurl = true
        )
    }

    fun testSendStringRequestWithCurl() {
        testSendStringRequest(
            logTag = "TestKMMStringRequestWithCurl",
            useCurl = true
        )
    }

    fun testSendGetRequestForByteContentTypeWithCurl() {
        testSendGetRequest(
            logTag = "TestKMMGetRequestWithCurl",
            useCurl = true
        )
    }

    fun testSendGetRequestForJsonContentTypeWithCurl() {
        testSendGetRequest(
            logTag = "TestKMMGetRequestWithCurl",
            useCurl = true,
            isByteContentType = false
        )
    }

    private fun testFetchImageForGetRequest(
        url: String,
        imgFilename: String = "",
        logTag: String = "TestKMMGetRequestWithCurl",
        useCurl: Boolean = true
    ) {
        val getRequest = VBTransportGetRequest()
        getRequest.url = url
        getRequest.logTag = logTag
        getRequest.header = mutableMapOf("Accept" to "image/*", "Content-Type" to "image/jpeg")
        getRequest.useCurl = useCurl
//        getRequest.totalTimeout = 5000
        VBTransportService.sendGetRequest(getRequest) { result ->
            // 结果码
            val code = result.errorCode
            // 网络任务在取消后会明确告知业务方取消的错误码
            if (code == VBTransportResultCode.CODE_CANCELED) {
                println("[TRACE] [${logTag}] request is canceled.")
            } else {
                // 响应数据
                val byteArray = result.data as? ByteArray
                // 后续ByteArray的处理逻辑，比如存储或者显示等
                if (byteArray != null) {
//                    testSaveByteArray(byteArray, imgFilename)
                }
                println(
                    "[TRACE] [${logTag}] fetch image result, code: ${code}, " +
                            "size: ${byteArray?.size}, header:${result.header}"
                )
            }
        }
    }

    // 673KB
    fun testSendGetRequestForImageWithCurlV1() {
        testFetchImageForGetRequest(imgUrl1, "image1.png")
    }

    // 3.2M
    fun testSendGetRequestForImageWithCurlV2() {
        testFetchImageForGetRequest(imgUrl2, "nature-6565499.jpg")
    }

    // 12.6M
    fun testSendGetRequestForImageWithCurlV3() {
        testFetchImageForGetRequest(imgUrl3, "sunset-7760143.jpg")
    }

    // 47M
    fun testSendGetRequestForImageWithCurlV4() {
        testFetchImageForGetRequest(imgUrl4, "023A7786.png")
    }

    fun testSend302RequestWithCurl() {
        testSend302Request(
            logTag = "TestKMM302GetRequestWithCurl",
            useCurl = true
        )
    }

    fun testSendPostRequestForJsonDataWithCurl() {
        testSendPostRequest(
            logTag = "testSendPostRequestWithCurl",
            useCurl = true
        )
    }

    fun testSendPostRequestForEchoStringDataWithCurl() {
        val postRequest = VBTransportPostRequest()
        postRequest.url = "https://postman-echo.com/post"
        postRequest.logTag = "testSendPostRequestWithCurl"
        postRequest.header["Content-Type"] = VBTransportContentType.JSON.toString()
        postRequest.data = "123456"
        postRequest.useCurl = true
        VBTransportService.sendPostRequest(postRequest) {
            val responseData = convertResponseData(it.data)
            println("[TRACE] [testSendPostRequestWithCurl] string response code: ${it.errorCode}, message:${it.errorMessage}," +
                    " size:${responseData.len}, data:${responseData.content}, request: ${it.request}")
        }
    }

    fun testSendPostRequestForByteDataWithCurl() {
        val postRequest = VBTransportPostRequest()
        postRequest.url = "https://a.video.qq.com"
        postRequest.logTag = "testSendPostRequestWithCurl"
        postRequest.data = byteData
        postRequest.header["header1"] = "value1"
        postRequest.header["header2"] = "value2"
        postRequest.header["header3"] = "value3"
        postRequest.useCurl = true
        VBTransportService.sendPostRequest(postRequest) {
            val responseData = convertResponseData(it.data)
            println("[TRACE] [testSendPostRequestWithCurl] byte response code: ${it.errorCode}, message:${it.errorMessage}," +
                    " size:${responseData.len}, request: ${it.request}, header:${it.header}, data:${responseData.content}")
        }
    }

    private fun convertResponseData(data: Any?): TestResponseData {
        var content = data
        var len = 0
        if (data is ByteArray) {
            println("[TRACE] [convertResponseData] data is ByteArray.")
            content = (data as ByteArray).decodeToString()
            len = (data as ByteArray).size
        } else if (data is String) {
            println("[TRACE] [convertResponseData] data is String.")
            len = (data as String).length
        }
        return TestResponseData(content, len)
    }

    @ObjCName("testServiceInit")
    fun testServiceInit() {
        val logImpl = object : IVBPBLog {

            override fun d(tag: String?, content: String?) {
                print("[$tag] $content\n")
            }

            override fun i(tag: String?, content: String?) {
                print("[$tag] $content\n")
            }

            override fun e(tag: String?, content: String?, throwable: Throwable?) {
                print("[$tag] $content\n")
            }
        }
        VBTransportInitHelper.debugInit(logImpl)
    }
}