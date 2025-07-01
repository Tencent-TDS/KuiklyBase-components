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
package com.tencent.kmm.network.internal.utils

import com.tencent.kmm.network.export.VBTransportBaseRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout

actual suspend fun readKnownSize(
    channel: ByteReadChannelWrapper,
    contentLength: Long
): ByteArray = channel.readAvailable(contentLength)

actual suspend fun readUnknownSize(channel: ByteReadChannelWrapper) =
    channel.readBytes(DEFAULT_BUFFER_SIZE.toLong())

actual fun ByteArray.mergeFromChunks(chunks: List<ByteArray>) {
    var offset = 0
    chunks.forEach { chunk ->
        System.arraycopy(
            chunk,       // 源数组
            0,          // 源起始位置
            this,       // 目标数组
            offset,     // 目标起始位置
            chunk.size  // 拷贝长度
        )
        offset += chunk.size
    }
}

actual fun getHttpClient(kmmRequest: VBTransportBaseRequest): Any? = HttpClient(Android) {
    if (kmmRequest.totalTimeout > 0) {
        install(HttpTimeout) {
            requestTimeoutMillis = kmmRequest.totalTimeout
            connectTimeoutMillis = kmmRequest.totalTimeout
            socketTimeoutMillis = kmmRequest.totalTimeout
        }
    }
}