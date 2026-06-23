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
package com.tencent.kmm.network.export

import kotlin.random.Random

enum class VBTransportMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS
}

enum class VBTransportContentType(private val description: String) {
    JSON("application/json"),
    BYTE("application/octet-stream");

    override fun toString(): String = description
}

class VBTransportMultipartBody(
    val boundary: String,
    val contentType: String,
    val data: ByteArray
)

class VBTransportMultipartBodyBuilder(
    private val boundary: String = defaultMultipartBoundary()
) {
    private val chunks = mutableListOf<ByteArray>()

    fun addFormField(
        name: String,
        value: String
    ): VBTransportMultipartBodyBuilder {
        appendString("--$boundary\r\n")
        appendString("Content-Disposition: form-data; name=\"${sanitizeHeaderValue(name)}\"\r\n")
        appendString("\r\n")
        appendString(value)
        appendString("\r\n")
        return this
    }

    fun addFile(
        name: String,
        fileName: String,
        bytes: ByteArray,
        contentType: String = VBTransportContentType.BYTE.toString()
    ): VBTransportMultipartBodyBuilder {
        appendString("--$boundary\r\n")
        appendString(
            "Content-Disposition: form-data; name=\"${sanitizeHeaderValue(name)}\"; " +
                    "filename=\"${sanitizeHeaderValue(fileName)}\"\r\n"
        )
        appendString("Content-Type: $contentType\r\n")
        appendString("\r\n")
        chunks.add(bytes)
        appendString("\r\n")
        return this
    }

    fun build(): VBTransportMultipartBody {
        val body = mergeChunks(chunks + "--$boundary--\r\n".encodeToByteArray())
        return VBTransportMultipartBody(
            boundary = boundary,
            contentType = "multipart/form-data; boundary=$boundary",
            data = body
        )
    }

    private fun appendString(value: String) {
        chunks.add(value.encodeToByteArray())
    }

    private fun sanitizeHeaderValue(value: String): String =
        value.replace("\"", "%22")
            .replace("\r", "")
            .replace("\n", "")

    private fun mergeChunks(chunks: List<ByteArray>): ByteArray {
        var totalSize = 0
        chunks.forEach { totalSize += it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(result, destinationOffset = offset)
            offset += chunk.size
        }
        return result
    }
}

private fun defaultMultipartBoundary(): String =
    "KuiklyNetworkBoundary${Random.nextInt(0, Int.MAX_VALUE)}"

open class VBTransportBaseRequest {
    var requestId: Int = 0
    var header = mutableMapOf<String, String>()
    var logTag: String = ""
    var url: String = ""
    var method: VBTransportMethod = VBTransportMethod.GET
    var quicForceQuic = false
    var totalTimeout: Long = 0L
    // 底层是否使用 libcurl 进行请求
    var useCurl: Boolean = true

    internal open fun bodyData(): Any? = null
}

class VBTransportStringRequest : VBTransportBaseRequest() {
    init {
        method = VBTransportMethod.GET
    }
}

class VBTransportBytesRequest : VBTransportBaseRequest() {
    var data: ByteArray = byteArrayOf()

    init {
        method = VBTransportMethod.POST
    }

    internal override fun bodyData(): Any = data
}

class VBTransportPostRequest : VBTransportBaseRequest() {
    lateinit var data: Any

    init {
        method = VBTransportMethod.POST
    }

    fun isDataInitialize(): Boolean = this::data.isInitialized

    internal override fun bodyData(): Any? = if (isDataInitialize()) data else null
}

class VBTransportGetRequest : VBTransportBaseRequest() {
    init {
        method = VBTransportMethod.GET
    }
}

class VBTransportRequest : VBTransportBaseRequest() {
    var data: Any? = null

    internal override fun bodyData(): Any? = data
}

fun VBTransportRequest.setMultipartBody(body: VBTransportMultipartBody) {
    header["Content-Type"] = body.contentType
    data = body.data
}
