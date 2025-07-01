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
package com.tencent.kmm.network.internal.platform

// 跨端HTTP协议类型枚举
val PROTOCOL_TYPE_HTTP1 = 1
val PROTOCOL_TYPE_HTTP2 = 2
val PROTOCOL_TYPE_HTTP3 = 3

// 网络类型
val NETWORK_TYPE_NET_DISCONNECT = -1
val NETWORK_TYPE_NET_UNKNOWN = 1
val NETWORK_TYPE_NET_2G = 2
val NETWORK_TYPE_NET_3G = 3
val NETWORK_TYPE_NET_4G = 4
val NETWORK_TYPE_NET_5G = 5
val NETWORK_TYPE_NET_WIFI = 6