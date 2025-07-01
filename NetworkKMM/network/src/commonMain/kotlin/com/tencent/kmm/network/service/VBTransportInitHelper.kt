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

import com.tencent.kmm.network.curl.CurlRequestService
import com.tencent.kmm.network.export.IVBPBLog
import com.tencent.kmm.network.export.VBTransportInitConfig
import com.tencent.kmm.network.internal.VBPBLog
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("VBTransportInitHelper")
object VBTransportInitHelper {
    @ObjCName("init")
    fun init(initConfig: VBTransportInitConfig) {
        initConfig.logImpl?.let {
            VBPBLog.logImpl = it

            // 注入 libcurl 日志实现
            CurlRequestService.initNativeCurlLog(it)
        }
        VBPBLog.i(VBPBLog.INIT_TASK, "PBService init")
    }

    /**
     * 用默认参数初始化 PB 组件
     * NOTE: 仅供 腾讯视频 debug 使用
     */
    @ObjCName("debugInit")
    fun debugInit(logImpl: IVBPBLog) {
        val defaultInitConfig = VBTransportInitConfig()
        defaultInitConfig.logImpl = logImpl
        init(defaultInitConfig)
    }
}