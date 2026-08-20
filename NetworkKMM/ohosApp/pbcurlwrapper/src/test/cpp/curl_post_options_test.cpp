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

/*
 * Host compile-level test for empty-body POST (issue #42).
 * Does not require a Harmony/OpenHarmony device or libcurl.
 *
 * Build and run:
 *   g++ -std=c++11 -Wall -Werror \
 *     -I../../main/cpp/wrapper/include \
 *     curl_post_options_test.cpp -o /tmp/curl_post_options_test \
 *     && /tmp/curl_post_options_test
 */

#include "curl_post_options.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>

static int gFailures = 0;

static void expectTrue(const char *name, int cond) {
    if (!cond) {
        std::fprintf(stderr, "FAIL: %s\n", name);
        ++gFailures;
    } else {
        std::printf("PASS: %s\n", name);
    }
}

static void expectEq(const char *name, int actual, int expected) {
    if (actual != expected) {
        std::fprintf(stderr, "FAIL: %s (got %d, want %d)\n", name, actual, expected);
        ++gFailures;
    } else {
        std::printf("PASS: %s\n", name);
    }
}

int main() {
    // Empty-body POST (null body): still CURLOPT_POST with POSTFIELDSIZE 0.
    CurlPostOptions emptyNull = ResolveCurlPostOptions(CURL_HTTP_METHOD_POST, 0, 0);
    expectEq("empty POST + null body sets usePost", emptyNull.usePost, 1);
    expectEq("empty POST + null body POSTFIELDSIZE is 0", emptyNull.postFieldSize, 0);
    expectEq("empty POST + null body uses EMPTY mode", emptyNull.bodyMode, CURL_POST_BODY_EMPTY);
    expectTrue("empty POST + null body POSTFIELDS is empty string",
               emptyNull.postFields != 0 && emptyNull.postFields[0] == '\0');

    // Empty-body POST (empty string pointer): still CURLOPT_POST.
    const char *emptyStr = "";
    CurlPostOptions emptyPtr = ResolveCurlPostOptions(CURL_HTTP_METHOD_POST, 0, emptyStr);
    expectEq("empty POST + empty ptr sets usePost", emptyPtr.usePost, 1);
    expectEq("empty POST + empty ptr POSTFIELDSIZE is 0", emptyPtr.postFieldSize, 0);
    expectEq("empty POST + empty ptr uses EMPTY mode", emptyPtr.bodyMode, CURL_POST_BODY_EMPTY);
    expectTrue("empty POST + empty ptr keeps caller pointer", emptyPtr.postFields == emptyStr);

    // GET with no body stays GET.
    CurlPostOptions getNoBody = ResolveCurlPostOptions(CURL_HTTP_METHOD_GET, 0, 0);
    expectEq("GET without body does not set POST", getNoBody.usePost, 0);
    expectEq("GET without body has NONE mode", getNoBody.bodyMode, CURL_POST_BODY_NONE);

    // Legacy caller: method unset (GET) but non-empty body still POSTs.
    const char *small = "hello";
    CurlPostOptions legacyBody = ResolveCurlPostOptions(CURL_HTTP_METHOD_GET, 5, small);
    expectEq("legacy non-empty body still POSTs", legacyBody.usePost, 1);
    expectEq("legacy non-empty body uses COPY (<8MB)", legacyBody.bodyMode, CURL_POST_BODY_COPY);
    expectEq("legacy non-empty body size", legacyBody.postFieldSize, 5);
    expectTrue("legacy non-empty body pointer", legacyBody.postFields == small);

    // Non-empty POST below 8MB uses COPYPOSTFIELDS.
    CurlPostOptions smallPost = ResolveCurlPostOptions(CURL_HTTP_METHOD_POST, 5, small);
    expectEq("small POST uses COPY", smallPost.bodyMode, CURL_POST_BODY_COPY);
    expectEq("small POST usePost", smallPost.usePost, 1);

    // Non-empty POST at 8MB uses POSTFIELDS pointer (no copy).
    const int eightMb = CURL_COPYPOSTFIELDS_MAX_BYTES;
    CurlPostOptions largePost = ResolveCurlPostOptions(CURL_HTTP_METHOD_POST, eightMb, small);
    expectEq("8MB POST uses POINTER", largePost.bodyMode, CURL_POST_BODY_POINTER);
    expectEq("8MB POST size", largePost.postFieldSize, eightMb);

    if (gFailures != 0) {
        std::fprintf(stderr, "%d test(s) failed\n", gFailures);
        return 1;
    }
    std::printf("All curl post option tests passed.\n");
    return 0;
}
