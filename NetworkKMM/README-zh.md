[TOC]

## KMM Network

[English Documentation](./README.md)

### 简介

本项目基于Kotlin Multiplatform技术，构建了一套跨平台网络请求(http get/post 等)解决方案,支持Android、iOS及HarmonyOS三大移动端平台.
目前HarmonyOS端底层使用开源库libcurl作为网络请求引擎,Android/iOS端目前暂时使用ktor实现,后续会统一使用libcurl.

### 接入

#### Kotlin 接入
```kotlin
repositories {
    maven {
        url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
    }
}

// 在 build.gradle.kts / build.ohos.gradle.kts 添加依赖
implementation("com.tencent.kuiklybase:network:0.0.4")
// 如有疑问，参考各端 demo app 接入 (androidApp/, iosApp/, ohosApp/目录下的示例)
```

#### 网络权限声明
##### Android
```kotlin
// 在 AndroidManifest.xml 中添加
<uses-permission android:name="android.permission.INTERNET" />
```
##### HarmonyOS
```json5
// 工程的 entry/src/main/module.json5 中添加
"requestPermissions": [
      {
        "name": "ohos.permission.INTERNET",
        "reason": "$string:internet_permission_reason",
        "usedScene": {
          "abilities": [
            "EntryAbility"
          ],
          "when": "inuse"
        }
      },
      {
        "name": "ohos.permission.GET_NETWORK_INFO",
        "reason": "$string:network_info_permission_reason",
        "usedScene": {
          "abilities": [
            "EntryAbility"
          ],
          "when": "inuse"
        }
      },
      {
        "name": "ohos.permission.GET_WIFI_INFO",
        "reason": "$string:wifi_info_permission_reason",
        "usedScene": {
          "abilities": [
            "EntryAbility"
          ],
          "when": "inuse"
        }
      }
]
```

#### HarmonyOS Native 库接入
鸿蒙端底层通过 `pbcurlwrapper` 调用 libcurl，除了 Kotlin 依赖和权限，还必须集成 native 库。详细步骤见 [HarmonyOS 接入文档](./docs/harmonyos-integration.md)。

必须下载并放置以下两个 `.so` 文件：

| 库文件 | 作用 | 下载链接 |
| --- | --- | --- |
| `libpbcurlwrapper.so` | curl 网络请求封装库 | [下载](https://drive.weixin.qq.com/s?k=AJEAIQdfAAoOl8vBYTAbQAJgZ2AA8) |
| `libopenssl.so` | HTTPS/SSL 支持库 | [下载](https://drive.weixin.qq.com/s?k=AJEAIQdfAAohnXGmhhAbQAJgZ2AA8) |

放置路径：

```text
ohosApp/entry/libs/arm64-v8a/
├── libpbcurlwrapper.so
└── libopenssl.so
```

如果业务鸿蒙工程存在 native entry CMake target，还需要在 `ohosApp/entry/src/main/cpp/CMakeLists.txt` 中 `add_library(... IMPORTED)` 并在 `target_link_libraries` 中链接 `pbcurlwrapper openssl`。当前 demo 工程已在 `NetworkKMM/ohosApp/entry/libs/arm64-v8a/` 内置上述库文件；外部项目接入时需要自行拷贝或下载。

#### 初始化
```kotlin
val logImpl = object : IVBPBLog {
    override fun d(tag: String?, content: String?) {
        print("[$tag] $content\n")
    }

    override fun i(tag: String?, content: String?) {
        print("[$tag] $content\n")
    }

    override fun e(tag: String?, content: String?, throwable: Throwable?) {
        print("[$tag] $content\n")
    }
}
val config = VBTransportInitConfig()
config.logImpl = logImpl
VBTransportInitHelper.init(config)
```

### 常用网络请求示例
在 network/src/commonMain/service/VBTransportServiceTest.kt 中包含一些常用 http get/post/string/byte/自定义 method 类型请求示例,可以参考.

#### 自定义 Method 请求
```kotlin
val request = VBTransportRequest().apply {
    method = VBTransportMethod.PUT
    url = "https://httpbin.org/put"
    header["Content-Type"] = VBTransportContentType.JSON.toString()
    header["Authorization"] = "Bearer sample-token"
    data = """{"name":"Kuikly","method":"PUT"}"""
}

VBTransportService.sendRequest(request) { response ->
    println(response.data)
}
```

#### 二进制文件上传
```kotlin
val request = VBTransportRequest().apply {
    method = VBTransportMethod.POST
    url = "https://httpbin.org/post"
    header["Content-Type"] = VBTransportContentType.BYTE.toString()
    header["X-File-Name"] = "sample.bin"
    data = fileBytes
    totalTimeout = 10000
}

VBTransportService.sendRequest(request) { response ->
    println(response.errorMessage)
}
```

#### Multipart 文件上传
```kotlin
val multipartBody = VBTransportMultipartBodyBuilder()
    .addFormField("name", "Kuikly")
    .addFormField("uploadType", "multipart")
    .addFile("file", "sample.bin", fileBytes, VBTransportContentType.BYTE.toString())
    .build()

val request = VBTransportRequest().apply {
    method = VBTransportMethod.POST
    url = "https://httpbin.org/post"
    header["Authorization"] = "Bearer sample-token"
    setMultipartBody(multipartBody)
    totalTimeout = 10000
}

VBTransportService.sendRequest(request) { response ->
    println(response.data)
}
```

### demo 工程运行使用说明
#### Android
直接 run androidApp target 即可.

#### iOS
```kotlin
1) 编译 xcframework 产物, Gradle 任务路径在: NetworkKMM/network/Tasks/coocapods/podPublishDebugXCFramework
2) 打开 iosApp/Podfile 文件, 取消下面这行注释:
pod 'network', :path => '../network/build/cocoapods/publish/debug'
3) 在 iosApp/Podfile 路径下打开终端,执行 pod install
4) Xcode 打开 iosApp.xcworkspace 工程运行即可
```

#### HarmonyOS
```kotlin
1) 编译 libkn.so 产物, Gradle 任务路径在: NetworkKMM/network-sample/build/ohosArm64Binaries
2) 编译产生的 libkn.so 产物在 network-sample 的临时 build/bin/ohosArm64_debugShared/libkn.so 文件里可以找到
3) 将该 libkn.so 拷贝到 ohosApp/entry/libs/arm64-v8a/ 目录下, 覆盖已有的 libkn.so 文件.
4) 确认 ohosApp/entry/libs/arm64-v8a/ 中存在 libkn.so、libpbcurlwrapper.so、libopenssl.so
5) 鸿蒙 IDE(DevEco-Studio) 打开 ohosApp 工程运行即可.
```

### 说明
1. 鸿蒙端目前底层网络请求使用开源库libcurl, 但没有直接调用libcurl的接口,而是在其基础上封装了一层wrapper层,供kotlin端调用.封装代码在: ohosApp/pbcurlwrapper/下.
2. 若需修改封装层的逻辑,修改完打产物时,直接在 DevEco-Studio 里,针对 pbcurlwrapper 执行 Build/Make Module 'pbcurlwrapper' 即可, 其产物在 pbcurlwrapper 目录下的临时文件夹 build/default/intermediate/cmake/default/obj/arm64-v8a/libpbcurlwrapper.so 中找到.
3. 若修改完想在 ohosApp 这个 demo 工程里运行,可以将 build 下的 libpbcurlwrapper.so 手动拷贝到 entry/libs/arm64-v8a/ 下,覆盖原有的 so 文件即可.
4. 外部 HarmonyOS 工程接入时,请按 [HarmonyOS 接入文档](./docs/harmonyos-integration.md) 同时配置权限、native 库和 CMake 链接.

### ChangeLog

[版本更新记录](./docs/changelog.md)
