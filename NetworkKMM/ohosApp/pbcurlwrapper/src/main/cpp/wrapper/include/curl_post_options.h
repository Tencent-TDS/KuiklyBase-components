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

#ifndef NETWORKKMM_OHOSAPP_PBCURLWRAPPER_MAIN_CPP_WRAPPER_INCLUDE_CURL_POST_OPTIONS_H_
#define NETWORKKMM_OHOSAPP_PBCURLWRAPPER_MAIN_CPP_WRAPPER_INCLUDE_CURL_POST_OPTIONS_H_

#ifdef __cplusplus
extern "C" {
#endif

/* Matches VBTransportMethod ordinal: GET = 0, POST = 1 */
#define CURL_HTTP_METHOD_GET  0
#define CURL_HTTP_METHOD_POST 1

/* How CURLOPT_POSTFIELDS / CURLOPT_COPYPOSTFIELDS should be applied */
#define CURL_POST_BODY_NONE    0
#define CURL_POST_BODY_EMPTY   1  /* empty POST: POSTFIELDS="" + POSTFIELDSIZE=0 */
#define CURL_POST_BODY_COPY    2  /* COPYPOSTFIELDS, body < 8MB */
#define CURL_POST_BODY_POINTER 3  /* POSTFIELDS pointer, body >= 8MB */

#define CURL_COPYPOSTFIELDS_MAX_BYTES (8 * 1024 * 1024)

typedef struct {
    int usePost;        /* 1: set CURLOPT_POST even if the body is empty/null */
    int postFieldSize;  /* CURLOPT_POSTFIELDSIZE */
    int bodyMode;       /* CURL_POST_BODY_* */
    const char *postFields;
} CurlPostOptions;

/*
 * Decide libcurl POST options from the request method and body.
 * POST is selected when method == POST, or when a non-empty body is present
 * (legacy C API callers that never set method).
 */
static inline CurlPostOptions ResolveCurlPostOptions(int method, int postBodyLen,
                                                     const char *postBody) {
    CurlPostOptions opts;
    opts.usePost = 0;
    opts.postFieldSize = 0;
    opts.bodyMode = CURL_POST_BODY_NONE;
    opts.postFields = 0;

    const int hasNonEmptyBody = postBodyLen > 0 && postBody != 0;
    if (method != CURL_HTTP_METHOD_POST && !hasNonEmptyBody) {
        return opts;
    }

    opts.usePost = 1;
    opts.postFieldSize = hasNonEmptyBody ? postBodyLen : 0;
    if (hasNonEmptyBody) {
        opts.postFields = postBody;
        opts.bodyMode = (postBodyLen >= CURL_COPYPOSTFIELDS_MAX_BYTES)
            ? CURL_POST_BODY_POINTER
            : CURL_POST_BODY_COPY;
    } else {
        opts.bodyMode = CURL_POST_BODY_EMPTY;
        opts.postFields = postBody != 0 ? postBody : "";
    }
    return opts;
}

#ifdef __cplusplus
}
#endif

#endif  // NETWORKKMM_OHOSAPP_PBCURLWRAPPER_MAIN_CPP_WRAPPER_INCLUDE_CURL_POST_OPTIONS_H_
