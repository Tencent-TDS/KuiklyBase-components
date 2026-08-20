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

#ifndef NETWORKKMM_OHOSAPP_PBCURLWRAPPER_MAIN_CPP_WRAPPER_UTILS_CURL_HEADER_LIST_H_
#define NETWORKKMM_OHOSAPP_PBCURLWRAPPER_MAIN_CPP_WRAPPER_UTILS_CURL_HEADER_LIST_H_

#include <algorithm>
#include <string>
#include <vector>

#include "curl_wrapper.h"

// Format request headers as libcurl slist entries ("Name: value").
// Each input pair produces exactly one line; callers must append each line once.
inline std::vector<std::string> BuildCurlRequestHeaderLines(const StringDic *headers,
                                                            bool *gzip_accept_encoding) {
    std::vector<std::string> lines;
    if (headers == nullptr || headers->stringPairs == nullptr) {
        return lines;
    }
    for (int i = 0; i < headers->size; ++i) {
        const StringPair header = headers->stringPairs[i];
        std::string key = std::string(header.first);
        std::string tmpKey = key;
        std::string value = std::string(header.second);
        std::transform(tmpKey.begin(), tmpKey.end(), tmpKey.begin(), ::tolower);
        if (tmpKey == "accept-encoding" && value == "gzip" && gzip_accept_encoding != nullptr) {
            *gzip_accept_encoding = true;
        }
        lines.push_back(key + ": " + value);
    }
    return lines;
}

// Append each formatted header exactly once. `append` is typically curl_slist_append.
template <typename List, typename AppendFn>
List AppendCurlHeaderLinesOnce(List list, const std::vector<std::string> &lines, AppendFn append) {
    for (size_t i = 0; i < lines.size(); ++i) {
        list = append(list, lines[i].c_str(), static_cast<int>(i));
    }
    return list;
}

#endif  // NETWORKKMM_OHOSAPP_PBCURLWRAPPER_MAIN_CPP_WRAPPER_UTILS_CURL_HEADER_LIST_H_
