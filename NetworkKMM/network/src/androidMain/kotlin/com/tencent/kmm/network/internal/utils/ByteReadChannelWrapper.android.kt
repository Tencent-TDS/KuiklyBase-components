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

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class ByteReadChannelWrapper(private val byteReadChannel: Any) {
    actual suspend fun readBytes(limit: Long): ByteArray = when (byteReadChannel) {
        is ByteReadChannel -> {
            val chunksData = mutableListOf<ByteArray>()
            val readChannel = byteReadChannel
            var totalSize = 0

            while (true) {
                val dataPacket = readChannel.readRemaining(limit)
                if (dataPacket.isEmpty) {
                    break
                }

                val bytesData = dataPacket.readBytes()
                chunksData.add(bytesData)
                totalSize += bytesData.size
            }

            // 数据合并
            ByteArray(totalSize).apply {
                mergeFromChunks(chunksData)
            }
        }

        else -> ByteArray(0)
    }

    actual suspend fun readAvailable(size: Long): ByteArray = when (byteReadChannel) {
        is ByteReadChannel ->
            ByteArray(size.toInt()).apply {
                var curPos = 0
                while (curPos < size) {
                    curPos += byteReadChannel.readAvailable(this, curPos, (size - curPos).toInt())
                }
            }

        else -> ByteArray(0)
    }
}