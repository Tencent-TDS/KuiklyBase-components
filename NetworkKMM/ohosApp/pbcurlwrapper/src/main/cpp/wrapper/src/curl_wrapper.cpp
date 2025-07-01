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

#include "curl_wrapper.h"
#include <string>
#include "curl/curl.h"
#include "log/curl_log.h"
#include "utils/curl_utils.h"
#include "zlib.h"

using namespace std;

bool curlGlobalInited = false;
bool curlGlobalCleanuped = false;
// 为了和 libcurl 错误码区分, 这里加一个偏移量
static const int gDefaultZipErrorCodeOffset = 150;

class CurlClient {
 public:
    explicit CurlClient(std::string logTag) {
        log_tag_ = logTag;
        logI(log_tag_, "CurlClient() execute.");
        // 初始化 curl
        if (!curlGlobalInited) {
            curl_global_init(CURL_GLOBAL_DEFAULT);
            curlGlobalInited = true;
        }
        curl_ = curl_easy_init();

        // 重置错误描述信息
        memset(curl_error_msg_, 0, sizeof(curl_error_msg_));
    }

    ~CurlClient() {
        logI(log_tag_, "~CurlClient() execute.");
        // 清理 curl 任务参数
        CleanupCurl();

        // 手动销毁 new 的数据
        if (curl_response_ != nullptr) {
            delete curl_response_;
            curl_response_ = nullptr;
        }
    }

 private:
    // 处理响应头的回调函数
    static size_t HeaderCallback(char *contents, size_t size, size_t nmemb, void *userp) {
        CurlClient *client = static_cast<CurlClient *>(userp);
        if (client == nullptr) {
            logE(gDefaultTag, "HeaderCallback, client is nullptr!!!");
            return 0;
        }

        size_t realsize = size * nmemb;
        std::string line(contents, realsize);
        // 检测是否为HTTP状态行（如"HTTP/1.1 200 OK"）
        if (line.find("HTTP/") == 0) {
            int32_t httpCode = 0;
            if (client->curl_ != nullptr) {
                curl_easy_getinfo(client->curl_, CURLINFO_RESPONSE_CODE, &httpCode);
            }
            logI(client->log_tag_, "HeaderCallback httpCode:" + std::to_string(httpCode));
            // 仅处理非重定向状态码（如200）
            // 检测重定向状态码（3xx）
            if (httpCode >= 300 && httpCode < 400) {
                client->redirect_url_.clear();  // 开始新重定向时清空旧值
            } else {
                if (!client->headers_.empty()) {
                    logI(client->log_tag_, "redirect header:" + client->headers_);
                }
                client->headers_.clear();  // 重置，准备记录最终头部.主要针对重定向场景,记录重定向之后的header信息
            }
            client->headers_ += line;
        } else if (line.find("Location:") == 0 || line.find("location:") == 0) {
            client->headers_ += line;
            // 提取并存储重定向URL
            client->redirect_url_ = line.substr(line.find(":") + 1);
            // 去除末尾换行符（\r\n）
            if (client->redirect_url_.size() >= 2) {
                client->redirect_url_.resize(client->redirect_url_.size() - 2);
            }
        } else {
            // 处理响应的头部信息是否包含 Content-Encoding = gzip
            HandleGzipContentEncoding(client, line);
        }
        return realsize;
    }

    static void HandleGzipContentEncoding(CurlClient *client, std::string line) {
        if (client == nullptr) {
            logE(gDefaultTag, "HandleGzipContentEncoding, client is nullptr!!!");
            return;
        }

        size_t colon_pos = line.find(':');
        if (colon_pos != std::string::npos) {
            std::string key = line.substr(0, colon_pos);
            std::string value = line.substr(colon_pos + 1);

            // 去除键值前后空格
            auto trim = [](std::string &s) {
                s.erase(s.begin(), std::find_if(s.begin(), s.end(), [](int ch) { return !std::isspace(ch); }));
                s.erase(std::find_if(s.rbegin(), s.rend(), [](int ch) { return !std::isspace(ch); }).base(), s.end());
            };
            trim(key);
            trim(value);

            // 统一转换为小写（避免大小写敏感问题）
            std::transform(key.begin(), key.end(), key.begin(), ::tolower);
            // 检测 Content-Encoding: gzip
            if (key == "content-encoding") {
                std::transform(value.begin(), value.end(), value.begin(), ::tolower);
                if (value.find("gzip") != std::string::npos) {
                    client->gzip_content_encoding_ = true;
                }
            }
        }
        client->headers_ += line + "\n";  // 保留原始头部信息
    }

    // 处理响应正文的回调函数
    static size_t DataWriteCallback(char *contents, size_t size, size_t nmemb, void *userp) {
        size_t realsize = size * nmemb;
        reinterpret_cast<std::string *>(userp)->append(reinterpret_cast<char *>(contents), realsize);
        return realsize;
    }

    static int ProgressCallback(void *clientp, curl_off_t dltotal, curl_off_t dlnow, curl_off_t ultotal,
                                curl_off_t ulnow) {
        CurlClient *client = static_cast<CurlClient *>(clientp);
        if (client == nullptr) {
            logE(gDefaultTag, "ProgressCallback client is nullptr!!!");
            return 0;
        }
        if (client->cancel_flag_) {
            logI(gDefaultTag, "ProgressCallback cancel by user.");
            return 1;
        }
        return 0;
    }

    // 调试回调函数
    static int DebugCallback(CURL *handle, curl_infotype type, char *data, size_t size, void *userptr) {
        if (type == CURLINFO_TEXT) {
            // 输出调试信息（包含 DNS 解析结果）
            std::string info = "DebugCallback: " + std::string(data, size);
            logI(gDefaultTag, info);
        }
        return 0;
    }

 public:
    void StartRequest(CurlRequest request, CurlCallback *callback) {
        if (!curl_) {
            logE(log_tag_, "curl_easy_init() failed.");
            return;
        }

        if (request.url == nullptr) {
            logE(log_tag_, "request url is nullptr!!!");
            return;
        }

        // 请求信息
        const char *ver = curl_version();
        std::string curlVer = "";
        if (ver != nullptr) {
            curlVer = ver;
        }
        const char *url = request.url;
        int64_t timeout = request.timeout;
        StringDic *headers = request.headers;
        int size = headers->size;
        int postBodyLen = request.postBodyLen;
        const char *postBody = request.postBody;
        logI(log_tag_, "libcurl ver:" + curlVer + ", request url:" + url + ", header size:" + std::to_string(size)
            + ", timeout:" + std::to_string(timeout));

        // 拼接 Header
        for (int i = 0; i < size; i++) {
            StringPair header = headers->stringPairs[i];
            std::string key = std::string(header.first);
            std::string tmpKey = key;
            std::string value = std::string(header.second);
            // 比较时转换为小写（避免大小写敏感问题）
            std::transform(tmpKey.begin(), tmpKey.end(), tmpKey.begin(), ::tolower);
            if (tmpKey == "accept-encoding" && value == "gzip") {
                gzip_accept_encoding_ = true;
            }
            std::string header_opt = key + ": " + value;
            logI(log_tag_, "request header[" + std::to_string(i) + "]: " + header_opt);
            header_list_ = curl_slist_append(header_list_, header_opt.c_str());
            if (header_list_ == nullptr) {
                header_list_ = curl_slist_append(header_list_, header_opt.c_str());
            } else {
                curl_slist_append(header_list_, header_opt.c_str());
            }
        }

        // url
        curl_easy_setopt(curl_, CURLOPT_URL, url);
        // 收集错误描述信息
        curl_easy_setopt(curl_, CURLOPT_ERRORBUFFER, curl_error_msg_);

        // 拼接header
        if (header_list_) {
            curl_easy_setopt(curl_, CURLOPT_HTTPHEADER, header_list_);
        }
        // 设置解码支持格式 true:支持gzip解码 false:默认
        if (gzip_accept_encoding_) {
            // 支持gzip，优先服务器发送gzip数据，如果服务器无法提供gzip其他格式同样支持，优先级低于gzip
            logI(log_tag_, "libcurl set gzip accept encoding.");
            curl_easy_setopt(curl_, CURLOPT_ACCEPT_ENCODING, "gzip");
        } else {
            // 默认原数据请求，其他编码格式也支持，优先级低于原数据请求
            // 由于该项设置开启后只能修改参数或通过初始化和销毁关闭；避免调用时开启gzip再关闭gzip，gzip配置未更新，故加上
            logI(log_tag_, "libcurl set identity accept encoding.");
            curl_easy_setopt(curl_, CURLOPT_ACCEPT_ENCODING, "identity");
        }
        // 超时配置
        if (timeout > 0) {
            curl_easy_setopt(curl_, CURLOPT_TIMEOUT_MS, timeout);
        }
        // SSL
        curl_easy_setopt(curl_, CURLOPT_SSL_VERIFYPEER, 0L);
        curl_easy_setopt(curl_, CURLOPT_SSL_VERIFYHOST, 0L);
        // 使用curl的内部重定向逻辑
        curl_easy_setopt(curl_, CURLOPT_FOLLOWLOCATION, 1L);

        // 进度回调
        curl_easy_setopt(curl_, CURLOPT_XFERINFOFUNCTION, ProgressCallback);
        curl_easy_setopt(curl_, CURLOPT_XFERINFODATA, this);
        curl_easy_setopt(curl_, CURLOPT_NOPROGRESS, 0L);

        // 根据post body长度是否大于0, 判断是否是post请求
        if (postBodyLen > 0 && postBody != nullptr) {
            logI(log_tag_, "curl post request, body len:" + std::to_string(postBodyLen) + ", body:" + postBody);
            // size of the POST input data
            curl_easy_setopt(curl_, CURLOPT_POSTFIELDSIZE, postBodyLen);
            curl_easy_setopt(curl_, CURLOPT_POST, 1L);
            if (postBodyLen >= 8 * 1024 * 1024) {
                logI(log_tag_, "Enter above 8MB branch.");
                // 传 body 指针，不会拷贝数据，因此必须确保 perform 之前数据有效
                curl_easy_setopt(curl_, CURLOPT_POSTFIELDS, postBody);
            } else {
                logI(log_tag_, "Enter libcurl below 8MB branch.");
                // 7.17.1 以上 libcurl 版本支持，最大不能超过 8MB 数据
                // 传 body 指针，会拷贝数据，因此必须先设置 body 大小，否则按空字符串处理
                curl_easy_setopt(curl_, CURLOPT_COPYPOSTFIELDS, postBody);
            }
        }

        // 配置调试选项
        curl_easy_setopt(curl_, CURLOPT_VERBOSE, 1L);
        curl_easy_setopt(curl_, CURLOPT_DEBUGFUNCTION, DebugCallback);

        // 响应头处理
        curl_easy_setopt(curl_, CURLOPT_HEADERFUNCTION, HeaderCallback);
        curl_easy_setopt(curl_, CURLOPT_HEADERDATA, this);
        // 响应数据 body 处理
        curl_easy_setopt(curl_, CURLOPT_WRITEFUNCTION, DataWriteCallback);
        curl_easy_setopt(curl_, CURLOPT_WRITEDATA, &content_data_);
        // curl 请求处理
        CURLcode res = curl_easy_perform(curl_);

        // 处理 gzip 响应数据
        int errorCode = res;
        if (res == CURLE_OK) {
            // 请求成功才执行 gzip 解压
            int upzipCode = HandleGzipDataIfNeed();
            if (upzipCode != Z_OK) {
                // gzip 解压失败, 更新错误码. 为了和 libcurl 错误码区分, 这里加一个偏移量
                errorCode = upzipCode + gDefaultZipErrorCodeOffset;
                // 更新错误描述信息
                memset(curl_error_msg_, 0, sizeof(curl_error_msg_));
                std::string error_msg = "gzip unzip failed";
                std::memcpy(curl_error_msg_, error_msg.c_str(), error_msg.length());
            }
        }

        char *ip = nullptr;
        curl_easy_getinfo(curl_, CURLINFO_PRIMARY_IP, &ip);
        logI(log_tag_, "ret code:" + std::to_string(errorCode) + ", errorMsg:" + curl_error_msg_
            + ",ip:" + ip + ", dataLen:" + std::to_string(content_data_.length()) + ", redirect url:"
            + redirect_url_ + "\nheader:\n" + headers_);
        logD(log_tag_, "data:\n" + content_data_);

        curl_response_ = new CurlResponse();
        curl_response_->code = errorCode;
        curl_response_->headerLen = headers_.length();
        curl_response_->headers = const_cast<char *>(reinterpret_cast<const char *>(headers_.c_str()));
        curl_response_->redirectUrl = const_cast<char *>(reinterpret_cast<const char *>(redirect_url_.c_str()));
        curl_response_->dataLen = 0;
        curl_response_->data = nullptr;
        // 失败场景不赋值响应数据,防止某些不规范调用的业务方即使在失败场景也按成功请求时一样去处理data,造成问题
        if (errorCode == 0) {
            curl_response_->dataLen = content_data_.length();
            curl_response_->data = const_cast<char *>(reinterpret_cast<const char *>(content_data_.c_str()));
        }
        curl_response_->errorMsg = curl_error_msg_;
        curl_response_->errorMsgLen = strlen(curl_error_msg_);
        HandleElapseStatisticsInfo(curl_response_);

        logI(log_tag_, "libcurl callback.");
        shared_ptr<CurlCallback> fetchCallbackBlockPtr(callback);
        fetchCallbackBlockPtr->callback(fetchCallbackBlockPtr->callbackRef, curl_response_);
    }

 private:
    // 请求结束清理任务
    void CleanupCurl() {
        logI(log_tag_, "cleanup curl client");
        if (curl_) {
            curl_easy_cleanup(curl_);
        }
        if (header_list_ != nullptr) {
            curl_slist_free_all(header_list_);
            header_list_ = nullptr;
        }

        if (!curlGlobalCleanuped) {
            curlGlobalCleanuped = true;
            curl_global_cleanup();
        }
    }

    int HandleGzipDataIfNeed() {
        if (!gzip_content_encoding_) {
            return Z_OK;
        }

        int ret = GzipDecompress(content_data_, content_data_, log_tag_);
        gzip_content_encoding_ = false;
        return ret;
    }

    void HandleElapseStatisticsInfo(CurlResponse *curlResponse) {
        if (curl_ == nullptr || curlResponse == nullptr) {
            return;
        }

        // 获取各阶段耗时统计, libcurl中的这些耗时指标的起始计算点是从发起请求开始,需要减去前面环节的耗时
        double tmpNameLookupTime = 0, tmpConnectTime = 0, tmpSslCostTime = 0, tmpPreTransferTime = 0;
        double tmpStartTransferTime = 0, tmpRedirectTime = 0, tmpTotalTime = 0;
        curl_easy_getinfo(curl_, CURLINFO_NAMELOOKUP_TIME, &tmpNameLookupTime);
        curl_easy_getinfo(curl_, CURLINFO_CONNECT_TIME, &tmpConnectTime);
        curl_easy_getinfo(curl_, CURLINFO_APPCONNECT_TIME, &tmpSslCostTime);
        curl_easy_getinfo(curl_, CURLINFO_PRETRANSFER_TIME, &tmpPreTransferTime);
        curl_easy_getinfo(curl_, CURLINFO_STARTTRANSFER_TIME, &tmpStartTransferTime);
        curl_easy_getinfo(curl_, CURLINFO_REDIRECT_TIME, &tmpRedirectTime);
        curl_easy_getinfo(curl_, CURLINFO_TOTAL_TIME, &tmpTotalTime);

        // 计算真正各阶段的网络耗时
        double nameLookupTime = 0, connectTime = 0, sslCostTime = 0, preTransferTime = 0;
        double startTransferTime = 0, redirectTime = 0, totalTime = 0, recvTime = 0;
        nameLookupTime = tmpNameLookupTime * 1000;
        connectTime = tmpConnectTime * 1000 - tmpNameLookupTime * 1000;
        sslCostTime = tmpSslCostTime * 1000 - tmpConnectTime * 1000;
        preTransferTime = tmpPreTransferTime * 1000 - tmpSslCostTime * 1000;
        // startTransferTime 统计的是数据发送耗时 和 数据发送完毕到首字节返回耗时 之和
        startTransferTime = tmpStartTransferTime * 1000 - tmpPreTransferTime * 1000;
        recvTime = tmpTotalTime * 1000 - tmpStartTransferTime * 1000;
        redirectTime = tmpRedirectTime * 1000;
        totalTime = tmpTotalTime * 1000;

        logI(log_tag_, "request statistics, nameLookupTime:" + std::to_string(nameLookupTime) + ", connectTime:"
            + std::to_string(connectTime) + ", sslCostTime:" + std::to_string(sslCostTime) + ", preTransferTime:"
            + std::to_string(preTransferTime) + ", startTransferTime:" + std::to_string(startTransferTime)
            + ", redirectTime:" + std::to_string(redirectTime) + ", recvTime:" + std::to_string(recvTime)
            + ", totalTime:" + std::to_string(totalTime));
        curlResponse->elapse.nameLookupTimeMs = nameLookupTime;
        curlResponse->elapse.connectTimeMs = connectTime;
        curlResponse->elapse.sslCostTimeMs = sslCostTime;
        curlResponse->elapse.preTransferTime = preTransferTime;
        curlResponse->elapse.startTransferTimeMs = startTransferTime;
        curlResponse->elapse.redirectTime = redirectTime;
        curlResponse->elapse.recvTime = recvTime;
        curlResponse->elapse.totalTimeMs = totalTime;
    }

 public:
    bool cancel_flag_;

 private:
    std::string log_tag_;
    CURL *curl_;
    struct curl_slist *header_list_ = nullptr;
    char curl_error_msg_[CURL_ERROR_SIZE];
    std::string headers_;
    std::string redirect_url_;
    std::string content_data_;
    CurlResponse *curl_response_;
    bool gzip_accept_encoding_ = false;
    bool gzip_content_encoding_ = false;
};

void StartRequest(CurClientHandle handle, CurlRequest request, CurlCallback *callback) {
    if (handle == nullptr) {
        logE(gDefaultTag, "client is nullptr!!!");
        return;
    }
    reinterpret_cast<CurlClient *>(handle)->StartRequest(request, callback);
}

CurClientHandle CreateCurlClient(const char *logTag) {
    if (logTag == nullptr) {
        return new CurlClient(gDefaultTag);
    }
    return new CurlClient(logTag);
}

void DeleteCurlClient(CurClientHandle handle) {
    if (handle == nullptr) {
        return;
    }
    CurlClient *curl = reinterpret_cast<CurlClient *>(handle);
    delete curl;
}

void Cancel(CurClientHandle handle) {
    if (handle == nullptr) {
        logE(gDefaultTag, "cancel fail, handler is nullptr");
        return;
    }
    logI(gDefaultTag, "cancel request");
    CurlClient *curl = reinterpret_cast<CurlClient *>(handle);
    curl->cancel_flag_ = 1;
}
