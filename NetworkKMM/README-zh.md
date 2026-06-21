[TOC]

## KMM Network

[English Documentation](./README.md)

### 简介

本项目基于Kotlin Multiplatform技术，构建了一套跨平台网络请求(http get/post 等)解决方案,支持Android、iOS及HarmonyOS三大移动端平台.
目前HarmonyOS端底层使用开源库libcurl作为网络请求引擎,Android/iOS端目前暂时使用ktor实现,后续会统一使用libcurl.

### 接入

#### Kotlin 接入
```kotlin

  // 在build.gradle.kts 添加依赖
  implementation("com.tencent.kuiklybase:network:0.0.3")
  // 如有疑问，参考各端demo app接入 (androidApp/, iosApp/, ohosApp/目录下的示例)
```

#### 网络权限声明
##### Android
```kotlin
// 在 AndroidManifest.xml 中添加
<uses-permission android:name="android.permission.INTERNET" />
```
##### HarmonyOS
```kotlin
// 工程的module.json5中添加
"requestPermissions": [
      {
        "name": "ohos.permission.INTERNET",
        "reason": "$string:reason",
        "usedScene": {
          "abilities": [
            "FormAbility"
          ],
          "when": "inuse"
        }
      },
      {
        "name": "ohos.permission.GET_NETWORK_INFO",
        "reason": "$string:reason",
        "usedScene": {
          "abilities": [
            "FormAbility"
          ],
          "when": "inuse"
        }
      },
      {
        "name": "ohos.permission.GET_WIFI_INFO",
        "reason": "$string:reason",
        "usedScene": {
          "abilities": [
            "FormAbility"
          ],
          "when": "inuse"
        }
      }
]
```

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
3) 将该 libkn.so 拷贝到 ohosApp/entry/libs/ 目录下, 覆盖已有的 libkn.so 文件.
4) 鸿蒙 IDE(DevEco-Studio) 打开 ohosApp工程运行即可.
```

### 说明
1. 鸿蒙端目前底层网络请求使用开源库libcurl, 但没有直接调用libcurl的接口,而是在其基础上封装了一层wrapper层,供kotlin端调用.封装代码在: ohosApp/pbcurlwrapper/下.
2. 若需修改封装层的逻辑,修改完打产物时,直接在 DevEco-Studio 里,针对 pbcurlwrapper 执行 Build/Make Module 'pbcurlwrapper' 即可, 其产物在 pbcurlwrapper 目录下的临时文件夹 build/default/intermediate/cmake/default/obj/arm64-v8a/libpbcurlwrapper.so 中找到.
3. 若修改完想在 ohosApp 这个demo工程里运行,可以将build下的这个so手动拷贝到entry/libs/arm64-v8a/下,覆盖原有的so文件即可.

### ChangeLog

[版本更新记录](./docs/changelog.md)
