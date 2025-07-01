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

object VBPBLog {
    private const val SERVICE_NAME = "NXNetwork_"
    const val TASK_MANAGER = SERVICE_NAME + "TaskManager"
    const val INIT_TASK = SERVICE_NAME + "InitTask"
    const val TASK = SERVICE_NAME + "TASK"
    const val HMTRANSPORTIMPL = SERVICE_NAME + "HmTransportImpl"
    const val HMCURLIMPL = SERVICE_NAME + "CurlImpl"

    var logImpl: IVBPBLog? = null

    fun i(tag: String?, content: String?) {
        logImpl?.i("[$tag]", content)
    }

    fun e(tag: String?, content: String?, throwable: Throwable? = null) {
        logImpl?.e("[$tag]", content, throwable)
    }
}
