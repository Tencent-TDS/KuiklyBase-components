# KNOI

KNOI (Kotlin Native Ohos Interaction)

### 简介

Kotlin Native & ArkTS 互相调用能力，无需写 C/C++ 桥接代码。

### 注意事项

1、由于需要 knoi（ Kotlin ）中的定义，需预先配置 Kotlin Native 共享库名字（ 默认为libkn.so，如需手动配置，需调用 setup("自定义 So 名字")）！！！

2、由于 knoi 会使用 dlopen 加载 libkn.so 会导致 libkn.so 链接的 so 也会被加载。但如果被动加载 so 同样提供 napi 接口（即有调用 napi_module_register）会导致ArkTS 使用时出现 not callable。解决方案可以在 调用 knoi 前先 import 该 so 所属 module。

3、为实现自动注册的能力。目前是通过sharedLib 配置来识别是否为最终的共享库，从而生成总注册表。如出现找不到 initBridge符号，需检查是否有其他 module 配置了 sharedLib

### 接入

- Kotlin 接入
```kotlin

   // 在根目录 build.gradle.kts 添加 ksp 和 knoi 插件 
   plugins {
     id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false 
     // knoi 最新版本见 changelog
     id("com.tencent.kuiklybase.knoi.plugin") version("0.0.2") apply false
   }

  // 在期望使用 knoi 模块的 gradle 文件添加
  plugins {
    id("com.tencent.kuiklybase.knoi.plugin")
  }
  

  // 如需要自动生成 TypeScript 文件输出路径可配置
  knoi {
      // 默认生成路径在 当前模块的 build/ts-api/ 下
      tsGenDir = projectDir.absolutePath + "/ts-api/"
  }
  // 如有疑问，参考 example/ 接入
```

- ArkTS 接入

```bash

// 添加依赖 （在 entry/oh-package.json5，非根目录的oh-package.json5）
// ArkTS 侧动态版本有缓存问题，生效慢，如有新增 API 无法调用，可以直接使用最新版本
  "dependencies": {
    "knoi":"0.1.8"
  }

// 初始化
import { setup, init } from "knoi"
setup("libkn.so", BuildProfile.DEBUG) // KMP 生成的动态库名字
init()

// 如有疑问，参考 ohosApp/entry 的接入
```

- 混淆配置

如鸿蒙侧开启 ArkTS 代码混淆功能，以下两个场景需要对部分代码进行 keep

1. 需将未 export 的 ArkTS 侧的 ServiceProvider 
2. 需将未 export 的 ArkTs 侧的 JSValue 访问的属性和方法
3. 在 obfuscation-rules.txt 新增 -keep-global-name

```JavaScript
-keep-global-name
knoi
```


### 使用指南

[类型转换](./docs/types.md)

[服务调用](./docs/service.md)

[服务发现](./docs/serviceDiscover.md)

[方法调用](./docs/function.md)

[JSValue](./docs/jsvalue.md)

[ArrayBuffer](./docs/arraybuffer.md)

[异常处理](./docs/execption.md)

[类型导出](./docs/declare.md)

[MainHandler](./docs/handler.md)

[性能优化](./docs/pref.md)

### benchmark

|API	|调用次数	|	Node-API (ms)|KNOI(ms)
|--|--|--|--|
bool (*)()	|10000	|	0.0031|0.0030(缓存 Service) 0.0036(不缓存 Service)
string (*)(string)	|10000	|	0.0057|0.0034(size 10) 0.015(size1000)
void (*)( std::function )	|10000		|0.0176|0.0072(单向) 0.010(双向)
unit	|1000		||0.0025
number	|1000		||0.0030
ArrayBuffer 2.4M	|10		||0.4943
ArrayBuffer 4.5K	|10		||0.0061

*node api 的数据参考华为 [aki gitee](https://gitee.com/openharmony-sig/aki/tree/master)*

### 已知问题

1. 无法使用 Array<JSValue?> 作为 ArkTS 侧返回值类型，请使用 JSValue，通过 JSValue.isArrayType 和 JSValue.toList 进行判断和转型。

2. 在将 ktValueToJSValue 转换 Kotlin 闭包为 ArkTS Function，闭包入参需要时 Array<JSValue?>，返回值是 JSValue?。如下所示：

```Kotlin
// 建议将闭包形参明确写出
ktValueToJSValue(getEnv(), { it: Array<JSValue?> ->
    return@ktValueToJSValue ktValueToJSValue(getEnv(), null, String::class)
}, Function::class)
```

### ChangeLog

[版本更新记录](./docs/changelog.md)