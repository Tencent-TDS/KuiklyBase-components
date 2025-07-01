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

#include "curl_utils.h"
#include <sys/stat.h>
#include <iostream>
#include <fstream>
#include "zlib.h"

int GzipDecompress(const std::string &input, std::string &output, const std::string &tag) {
    logI(tag, "libcurl unzip gzip data. before GzipDecompress, size:" + std::to_string(input.size()));
    z_stream strm = {};
    inflateInit2(&strm, 16 + MAX_WBITS);

    std::vector<uint8_t> res(4096);
    size_t total_out = 0;
    int ret;

    strm.avail_in = input.length();
    strm.next_in = reinterpret_cast<unsigned char *>(const_cast<char *>(input.c_str()));

    do {
        strm.avail_out = res.size() - total_out;
        strm.next_out = res.data() + total_out;

        ret = inflate(&strm, Z_NO_FLUSH);
        if (ret == Z_STREAM_ERROR) break;

        total_out += (res.size() - total_out - strm.avail_out);
        res.resize(res.size() * 2);  // 动态扩展缓冲区
    } while (strm.avail_out == 0);

    inflateEnd(&strm);
    if (ret != Z_STREAM_END) {
        logW(tag, "libcurl unzip gzip data. GzipDecompress failed, error code:" + std::to_string(ret));
        return ret;
    }

    res.resize(total_out);
    output = std::string(res.begin(), res.end());
    logI(tag, "libcurl unzip gzip data. finish GzipDecompress, size:" + std::to_string(output.length()));
    return Z_OK;
}

bool FileExists(const std::string& path) {
    struct stat buffer;
    return (stat(path.c_str(), &buffer) == 0);  // 返回 0 表示文件存在
}

std::string ReadFileToString(const char *filename) {
    logW(gDefaultTag, "Enter readFileToString.");

    if (FileExists(filename)) {
        logI(gDefaultTag, "readFileToString 文件存在.");
    } else {
        logW(gDefaultTag, "readFileToString 文件不存在!!! filename:" + std::string(filename));
    }

    std::ifstream file(filename, std::ios::binary);
    // 检查文件是否成功打开
    if (!file.is_open()) {
        logW(gDefaultTag, "readFileToString file is null.");
        return "";
    }

    // 读取文件内容到 std::string
    std::string result((std::istreambuf_iterator<char>(file)), std::istreambuf_iterator<char>());

    // 关闭文件
    file.close();
    logI(gDefaultTag, "readFileToString result:" + result);
    return result;
}
