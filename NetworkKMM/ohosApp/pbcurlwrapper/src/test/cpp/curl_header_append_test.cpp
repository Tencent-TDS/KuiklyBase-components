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

#include <cstdlib>
#include <iostream>
#include <string>
#include <vector>

#include "utils/curl_header_list.h"

static int g_failures = 0;

static void ExpectTrue(bool cond, const char *msg) {
    if (!cond) {
        std::cerr << "FAIL: " << msg << std::endl;
        ++g_failures;
    }
}

static void TestBuildsOneLinePerHeaderWithoutDuplicating() {
    StringPair pairs[] = {
        {"Authorization", "Bearer token"},
        {"Accept", "application/json"},
        {"X-Custom", "1"},
    };
    StringDic headers;
    headers.stringPairs = pairs;
    headers.size = 3;

    bool gzip = false;
    const std::vector<std::string> lines = BuildCurlRequestHeaderLines(&headers, &gzip);

    ExpectTrue(lines.size() == 3, "expected one formatted line per input header");
    ExpectTrue(lines[0] == "Authorization: Bearer token", "Authorization line mismatch");
    ExpectTrue(lines[1] == "Accept: application/json", "Accept line mismatch");
    ExpectTrue(lines[2] == "X-Custom: 1", "X-Custom line mismatch");
    ExpectTrue(!gzip, "gzip should stay false when Accept-Encoding is absent");

    int auth_count = 0;
    for (size_t i = 0; i < lines.size(); ++i) {
        if (lines[i].find("Authorization:") == 0) {
            ++auth_count;
        }
    }
    ExpectTrue(auth_count == 1, "Authorization must appear exactly once in formatted lines");
}

static void TestAppendsEachHeaderOnce() {
    StringPair pairs[] = {
        {"Authorization", "Bearer token"},
        {"Content-Type", "application/json"},
    };
    StringDic headers;
    headers.stringPairs = pairs;
    headers.size = 2;

    bool gzip = false;
    const std::vector<std::string> lines = BuildCurlRequestHeaderLines(&headers, &gzip);

    std::vector<std::string> appended;
    auto mock_append = [&appended](int list, const char *header_opt, int index) {
        (void)index;
        appended.push_back(header_opt);
        return list + 1;
    };

    const int new_list = AppendCurlHeaderLinesOnce(0, lines, mock_append);
    ExpectTrue(new_list == 2, "mock list size should equal header count");
    ExpectTrue(appended.size() == lines.size(), "append must be called once per formatted line");
    ExpectTrue(appended.size() == 2, "two headers must produce two slist appends");
    ExpectTrue(appended[0] == "Authorization: Bearer token", "first appended header mismatch");
    ExpectTrue(appended[1] == "Content-Type: application/json", "second appended header mismatch");
}

static void TestDetectsGzipAcceptEncoding() {
    StringPair pairs[] = {
        {"Accept-Encoding", "gzip"},
    };
    StringDic headers;
    headers.stringPairs = pairs;
    headers.size = 1;

    bool gzip = false;
    const std::vector<std::string> lines = BuildCurlRequestHeaderLines(&headers, &gzip);
    ExpectTrue(gzip, "Accept-Encoding: gzip should set gzip flag");
    ExpectTrue(lines.size() == 1, "gzip header should still be appended once");
    ExpectTrue(lines[0] == "Accept-Encoding: gzip", "gzip header line mismatch");
}

int main() {
    TestBuildsOneLinePerHeaderWithoutDuplicating();
    TestAppendsEachHeaderOnce();
    TestDetectsGzipAcceptEncoding();
    if (g_failures != 0) {
        std::cerr << g_failures << " assertion(s) failed" << std::endl;
        return EXIT_FAILURE;
    }
    std::cout << "curl_header_append_test: ok" << std::endl;
    return EXIT_SUCCESS;
}
