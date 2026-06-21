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

enum class VBTransportMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS
}

enum class VBTransportContentType(private val description: String) {
    JSON("application/json"),
    BYTE("application/octet-stream");

    override fun toString(): String = description
}

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
