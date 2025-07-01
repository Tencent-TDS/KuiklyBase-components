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

import com.tencent.kmm.network.export.IVBPBRequestIdGenerator
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * 请求 id 生成
 */
@OptIn(ExperimentalObjCName::class)
object VBPBRequestIdGenerator {

    var requestIdGenerator: IVBPBRequestIdGenerator? = null

    private var requestId: AtomicInt = atomic(0)


    /**
     * 创建请求id
     *
     * @return 自增请求id
     */
    @ObjCName("pb_getRequestId")
    fun getRequestId(): Int {
        return requestIdGenerator?.getRequestId() ?: run {
            if (requestId.value == Int.MAX_VALUE) {
                requestId.value = 0
            }
            requestId.incrementAndGet()
        }
    }

}