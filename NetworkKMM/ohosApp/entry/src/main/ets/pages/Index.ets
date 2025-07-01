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
// import libs  from 'libnative.so';
import hilog from '@ohos.hilog';
import { invoke } from "@kuiklybase/knoi"
import { getFooService } from "../knoi/provider"

@Entry
@Component
struct Index {
  @State message: string = 'Hello World';

  build() {
    Row() {
      Column() {
        Text(this.message)
          .fontSize(50)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            hilog.debug(1, "Null", "test")
            // getFooService().test("param")
            invoke("testMainHandler")
            // libs.test_dump_stack()
          })
        createButton('KN 普通Get请求 (libcurl)', () => {
          invoke('testSendGetRequestForByteContentTypeWithCurl')
        })
        createButton('KN 图片Get请求 - 673 KB (libcurl)', () => {
          invoke('testSendGetRequestForImageWithCurlV1')
        })
        createButton('KN 图片Get请求 - 3.2 M (libcurl)', () => {
          invoke('testSendGetRequestForImageWithCurlV2')
        })
        createButton('KN 图片Get请求 - 12.6 M (libcurl)', () => {
          invoke('testSendGetRequestForImageWithCurlV3')
        })
        createButton('KN 图片Get请求 - 47 M (libcurl)', () => {
          invoke('testSendGetRequestForImageWithCurlV4')
        })
        createButton('KN 普通Post请求 - String (libcurl)', () => {
          invoke('testSendPostRequestForJsonDataWithCurl')
        })
        createButton('KN 普通Post请求V2 - String (libcurl)', () => {
          invoke('testSendPostRequestForEchoStringDataWithCurl')
        })
        createButton('KN 普通Post请求 - Byte (libcurl)', () => {
          invoke('testSendPostRequestForByteDataWithCurl')
        })
        createButton('KN 普通字符类型Get请求 (libcurl)', () => {
          invoke('testSendStringRequestWithCurl')
        })
        createButton('KN 普通字节数组Post请求 (libcurl)', () => {
          invoke('testSendByteRequestWithCurl')
        })
        createButton('KN Byte Content-Type 请求 (libcurl)', () => {
          invoke('testSendGetRequestForByteContentTypeWithCurl')
        })
        createButton('KN Json Content-Type 请求 (libcurl)', () => {
          invoke('testSendGetRequestForJsonContentTypeWithCurl')
        })
        Text(`以下是 libcurl 边界case 请求测试`)
          .fontSize(20)
          .fontWeight(FontWeight.Normal)
          .margin({
            top: 10, bottom: 10
          })
        createButton('KN 302重定向请求 (libcurl)', () => {
          invoke('testSend302RequestWithCurl')
        })
      }
      .width('100%')
    }
    .height('100%')
  }
}

@Builder
function createButton(name: string, callback: (event: ClickEvent) => void) {
  Button() {
    Text(name)
      .fontSize(15)
      .fontWeight(FontWeight.Normal);
  }
  .type(ButtonType.Normal)
  .margin({
    top: 10
  })
  .backgroundColor('#0D9FFB')
  .width('50%')
  .height('5%')
  .onClick(callback)
}