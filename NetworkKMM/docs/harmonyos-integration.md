# KMM Network HarmonyOS 接入指南

本文补充 HarmonyOS 侧接入 KMM Network 时必须处理的 native 库、权限、CMake 链接和初始化步骤。Android/iOS 主要接入 Kotlin 依赖和平台网络权限；HarmonyOS 侧底层通过 `pbcurlwrapper` 封装 libcurl，因此还需要额外集成 `.so` 文件。

参考文档：[KMM Network 鸿蒙端接入使用指南](https://github.com/Kuikly-contrib/KuiklyLibGallery/blob/main/docs/KMM_Network_HarmonyOS_Integration_Guide.md)。

## 1. 添加 Kotlin 依赖

在 KMM 共享模块和 OHOS Gradle 配置中添加腾讯 Maven 仓库和 network 依赖，例如 `build.gradle.kts` / `build.ohos.gradle.kts`。

```kotlin
repositories {
    maven {
        url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
    }
}

dependencies {
    implementation("com.tencent.kuiklybase:network:0.0.4")
}
```

## 2. 声明 HarmonyOS 网络权限

在 `ohosApp/entry/src/main/module.json5` 中添加网络权限。

```json5
{
  "module": {
    "requestPermissions": [
      {
        "name": "ohos.permission.INTERNET",
        "reason": "$string:internet_permission_reason",
        "usedScene": {
          "abilities": ["EntryAbility"],
          "when": "inuse"
        }
      },
      {
        "name": "ohos.permission.GET_NETWORK_INFO",
        "reason": "$string:network_info_permission_reason",
        "usedScene": {
          "abilities": ["EntryAbility"],
          "when": "inuse"
        }
      },
      {
        "name": "ohos.permission.GET_WIFI_INFO",
        "reason": "$string:wifi_info_permission_reason",
        "usedScene": {
          "abilities": ["EntryAbility"],
          "when": "inuse"
        }
      }
    ]
  }
}
```

在 `ohosApp/entry/src/main/resources/base/element/string.json` 中补充权限说明字符串。

```json
{
  "string": [
    {
      "name": "internet_permission_reason",
      "value": "应用需要访问网络以获取数据"
    },
    {
      "name": "network_info_permission_reason",
      "value": "应用需要获取网络状态信息"
    },
    {
      "name": "wifi_info_permission_reason",
      "value": "应用需要获取 WiFi 信息"
    }
  ]
}
```

## 3. 添加必需 Native 库

HarmonyOS 接入必须包含以下 native 库：

| 库文件 | 作用 | 下载链接 |
| --- | --- | --- |
| `libpbcurlwrapper.so` | KMM Network 调用 libcurl 的封装库 | [下载](https://drive.weixin.qq.com/s?k=AJEAIQdfAAoOl8vBYTAbQAJgZ2AA8) |
| `libopenssl.so` | HTTPS/SSL/TLS 支持库 | [下载](https://drive.weixin.qq.com/s?k=AJEAIQdfAAohnXGmhhAbQAJgZ2AA8) |

把两个文件放到 OHOS entry 模块的 native 库目录：

```text
ohosApp/
└── entry/
    └── libs/
        └── arm64-v8a/
            ├── libpbcurlwrapper.so
            └── libopenssl.so
```

如果 `entry/libs/arm64-v8a/` 不存在，需要手动创建。本仓库 demo 已经在 `NetworkKMM/ohosApp/entry/libs/arm64-v8a/` 内置这两个文件；业务项目接入时仍需要把它们下载或拷贝到自己的 entry 模块。

## 4. 链接 Native 库

如果业务 OHOS app 有 native entry CMake target，需要在 `ohosApp/entry/src/main/cpp/CMakeLists.txt` 中导入并链接这两个库。

```cmake
add_library(pbcurlwrapper SHARED IMPORTED)
set_target_properties(pbcurlwrapper
    PROPERTIES
    IMPORTED_LOCATION ${NATIVERENDER_ROOT_PATH}/../../../libs/${OHOS_ARCH}/libpbcurlwrapper.so
)

add_library(openssl SHARED IMPORTED)
set_target_properties(openssl
    PROPERTIES
    IMPORTED_LOCATION ${NATIVERENDER_ROOT_PATH}/../../../libs/${OHOS_ARCH}/libopenssl.so
)
```

然后在 `target_link_libraries` 中加入 `pbcurlwrapper` 和 `openssl`。

```cmake
target_link_libraries(entry PUBLIC
    libace_napi.z.so
    libhilog_ndk.z.so
    kuikly_shared
    kuikly_render
    pbcurlwrapper
    openssl
)
```

如果 entry 模块使用 `externalNativeOptions`，需要确保 `build-profile.json5` 指向对应 CMake 文件，并限制 ABI 为 `arm64-v8a`，和下载的 native 库架构保持一致。

```json5
{
  "buildOption": {
    "externalNativeOptions": {
      "path": "./src/main/cpp/CMakeLists.txt",
      "abiFilters": ["arm64-v8a"]
    }
  }
}
```

## 5. 初始化 KMM Network

在第一次发起网络请求前初始化网络模块。推荐放在应用启动流程，或者首个 Kuikly 页面初始化阶段。

```kotlin
import com.tencent.kmm.network.export.IVBPBLog
import com.tencent.kmm.network.export.VBTransportInitConfig
import com.tencent.kmm.network.service.VBTransportInitHelper

val logImpl = object : IVBPBLog {
    override fun d(tag: String?, content: String?) {
        println("[$tag] DEBUG: $content")
    }

    override fun i(tag: String?, content: String?) {
        println("[$tag] INFO: $content")
    }

    override fun e(tag: String?, content: String?, throwable: Throwable?) {
        println("[$tag] ERROR: $content")
        throwable?.printStackTrace()
    }
}

val config = VBTransportInitConfig()
config.logImpl = logImpl
VBTransportInitHelper.init(config)
```

## 6. 接入检查清单

- [ ] 已添加 `com.tencent.kuiklybase:network:0.0.4`
- [ ] 已添加腾讯 Maven 仓库
- [ ] 已声明 `INTERNET`、`GET_NETWORK_INFO`、`GET_WIFI_INFO`
- [ ] 已添加权限 reason 字符串
- [ ] 已下载 `libpbcurlwrapper.so`
- [ ] 已下载 `libopenssl.so`
- [ ] 两个 `.so` 已放到 `entry/libs/arm64-v8a/`
- [ ] 有 native entry target 时，已在 CMake 中添加 imported library
- [ ] 有 native entry target 时，已链接 `pbcurlwrapper` 和 `openssl`
- [ ] 已在发起请求前调用 `VBTransportInitHelper.init()`

## 常见问题

### 编译时报找不到 `.so`

检查 `entry/libs/arm64-v8a/` 下是否存在 `libpbcurlwrapper.so` 和 `libopenssl.so`，文件名是否完全一致，以及 CMake 中 `IMPORTED_LOCATION` 是否指向 `../../../libs/${OHOS_ARCH}/`。

### 运行时 native library not found

确认运行设备是 ARM64，并且 entry target 已链接 `pbcurlwrapper` 和 `openssl`。当前下载库只覆盖 `arm64-v8a`，不支持 x86/x86_64 模拟器。

### HTTPS 请求失败

确认 `libopenssl.so` 已放置到正确目录，CMake 已链接 `openssl`，并检查设备系统时间是否正确。
