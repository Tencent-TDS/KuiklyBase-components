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

#include "curl_log.h"
#include "../../include/curl_wrapper.h"

curlLog gCurlLog = nullptr;

static const char gPrefixTag[] = "[NXNetwork_CurlWrapper]";

static const int gCurlLogLevelDebug = 0;
static const int gCurlLogLevelInfo = 1;
static const int gCurlLogLevelWarn = 2;
static const int gCurlLogLevelError = 3;

void setCurlLogImpl(curlLog logImpl) {
    gCurlLog = logImpl;
}

void curlLogImpl(int level, const char *tag, const char *content) {
    if (gCurlLog == nullptr) {
        return;
    }
    gCurlLog(level, tag, content);
}

void logD(const std::string &tag, const std::string &content) {
    curlLogImpl(gCurlLogLevelDebug, (std::string(gPrefixTag) + tag).c_str(), content.c_str());
}

void logI(const std::string &tag, const std::string &content) {
    curlLogImpl(gCurlLogLevelInfo, (std::string(gPrefixTag) + tag).c_str(), content.c_str());
}

void logW(const std::string &tag, const std::string &content) {
    curlLogImpl(gCurlLogLevelWarn, (std::string(gPrefixTag) + tag).c_str(), content.c_str());
}

void logE(const std::string &tag, const std::string &content) {
    curlLogImpl(gCurlLogLevelError, (std::string(gPrefixTag) + tag).c_str(), content.c_str());
}
