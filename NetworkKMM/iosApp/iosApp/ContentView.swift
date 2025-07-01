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
import SwiftUI
import network

let logTag = "[iOS NetworkKMM]"
let useCurl = false

struct ContentView: View {
	let greet = "hello world"

	var body: some View {
		VStack(content: {
            Button("初始化", action: {
                VBTransportServiceTest().testServiceInit() // 初始化
            })
            Button("测试 Get", action: {
                VBTransportServiceTest().testSendGetRequest(logTag: logTag, useCurl: useCurl, isByteContentType: false)
            })
            Button("测试 Post", action: {
                VBTransportServiceTest().testSendPostRequest(logTag: logTag, useCurl: useCurl)
            })
            Button("测试 sendByte", action: {
                VBTransportServiceTest().testSendByteRequest(logTag: logTag, useCurl: useCurl)
            })
            Button("测试 sendString", action: {
                VBTransportServiceTest().testSendStringRequest(logTag: logTag, useCurl: useCurl)
            })
            Button("测试 Cancel", action: {
                VBTransportServiceTest().testCancelRequest()
            })
        })
	}
}

struct ContentView_Previews: PreviewProvider {
	static var previews: some View {
		ContentView()
	}
}
