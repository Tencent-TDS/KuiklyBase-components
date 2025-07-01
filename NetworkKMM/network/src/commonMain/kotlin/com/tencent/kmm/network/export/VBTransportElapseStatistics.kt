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

data class VBTransportElapseStatistics(
    /**
     * DNS 解析耗时
     */
    var nameLookupTimeMs: Double = 0.0,
    /**
     * 连接耗时
     */
    var connectTimeMs: Double = 0.0,
    /**
     * https ssl 握手耗时
     */
    var sslCostTimeMs: Double = 0.0,
    /**
     * 传输准备耗时, 例如发送 HTTP 请求头前的处理时间
     */
    var preTransferTime: Double = 0.0,
    /**
     * 首字节到达耗时（TTFB）
     * 统计的是 数据发送耗时 和 数据发送完毕到首字节返回耗时 之和
     */
    var startTransferTimeMs: Double = 0.0,
    /**
     * 所有重定向过程的总耗时
     */
    var redirectTime: Double = 0.0,
    /**
     * 数据接收耗时
     */
    var recvTime: Double = 0.0,
    /**
     * 整个请求的总耗时
     */
    var totalTimeMs: Double = 0.0,
)
