[TOC]

## KMM Network

[中文文档](./README-zh.md)

### Introduction

This project is based on Kotlin Multiplatform technology and provides a cross-platform network request (http get/post, etc.) solution, supporting Android, iOS, and HarmonyOS mobile platforms.  
Currently, the HarmonyOS side uses the open-source library **libcurl** as the network request engine, while the Android/iOS sides temporarily use **ktor**. In the future, all platforms will be unified to use libcurl.

### Integration

#### Kotlin Integration
```kotlin
repositories {
    maven {
        url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
    }
}

// Add the dependency in build.gradle.kts / build.ohos.gradle.kts
implementation("com.tencent.kuiklybase:network:0.0.4")
// For more details, refer to the demo apps for each platform (see androidApp/, iosApp/, ohosApp/ directories)
```

#### Network Permission Declaration
##### Android
```kotlin
// Add in AndroidManifest.xml
<uses-permission android:name="android.permission.INTERNET" />
```
##### HarmonyOS
```json5
// Add in the module.json5 file of your project
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

#### HarmonyOS Native Libraries
HarmonyOS uses libcurl through `pbcurlwrapper`, so app projects must integrate native libraries in addition to the Kotlin dependency and permissions. See [HarmonyOS Integration](./docs/harmonyos-integration.md) for the complete steps.

Required libraries:

| Library | Purpose | Download |
| --- | --- | --- |
| `libpbcurlwrapper.so` | curl request wrapper | [download](https://drive.weixin.qq.com/s?k=AJEAIQdfAAoOl8vBYTAbQAJgZ2AA8) |
| `libopenssl.so` | HTTPS/SSL support | [download](https://drive.weixin.qq.com/s?k=AJEAIQdfAAohnXGmhhAbQAJgZ2AA8) |

Place them here:

```text
ohosApp/entry/libs/arm64-v8a/
├── libpbcurlwrapper.so
└── libopenssl.so
```

If the app has a native entry CMake target, import both libraries with `add_library(... IMPORTED)` in `ohosApp/entry/src/main/cpp/CMakeLists.txt` and add `pbcurlwrapper openssl` to `target_link_libraries`. The sample project already includes these libraries under `NetworkKMM/ohosApp/entry/libs/arm64-v8a/`; external app projects must copy or download them into their own entry module.

#### Initialization
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

### Common Network Request Examples
Some common HTTP GET/POST/string/byte/custom method request examples can be found in ***network/src/commonMain/service/VBTransportServiceTest.kt*** for your reference.

#### Custom Method Request
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

#### Binary File Upload
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

#### Multipart File Upload
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

### Demo Project Usage Instructions
#### Android
Simply run the androidApp target.

#### iOS
```kotlin
1) Build the xcframework artifact. The Gradle task path is: NetworkKMM/network/Tasks/coocapods/podPublishDebugXCFramework
2) Open the iosApp/Podfile file and uncomment the following line:
   pod 'network', :path => '../network/build/cocoapods/publish/debug'
3) Open a terminal in the iosApp/Podfile directory and run: pod install
4) Open iosApp.xcworkspace in Xcode and run the project
```

#### HarmonyOS
```kotlin
1) Build the libkn.so artifact. The Gradle task path is: NetworkKMM/network-sample/build/ohosArm64Binaries
2) The generated libkn.so artifact can be found in the temporary build/bin/ohosArm64_debugShared/libkn.so folder of network-sample
3) Copy libkn.so to ohosApp/entry/libs/arm64-v8a/, overwriting the existing libkn.so file
4) Confirm ohosApp/entry/libs/arm64-v8a/ contains libkn.so, libpbcurlwrapper.so, and libopenssl.so
5) Open the ohosApp project in DevEco-Studio and run it
```

### Notes
1. The HarmonyOS side currently uses the open-source library libcurl for network requests, but does not call libcurl interfaces directly. Instead, a wrapper layer is implemented for Kotlin to call. The wrapper code is located in: ohosApp/pbcurlwrapper/.
2. If you need to modify the logic of the wrapper layer, after making changes, build the artifact by executing Build/Make Module 'pbcurlwrapper' in DevEco-Studio. The artifact can be found in the temporary folder: pbcurlwrapper/build/default/intermediate/cmake/default/obj/arm64-v8a/libpbcurlwrapper.so.
3. If you want to run the ohosApp demo project after modification, you can manually copy libpbcurlwrapper.so from the build folder to entry/libs/arm64-v8a/, overwriting the original file.
4. For external HarmonyOS app integration, follow [HarmonyOS Integration](./docs/harmonyos-integration.md) to configure permissions, native libraries, and CMake linkage.

### ChangeLog

[Version Update Log](./docs/changelog.md)
