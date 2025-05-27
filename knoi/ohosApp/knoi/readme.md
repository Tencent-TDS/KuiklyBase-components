# KNOI

KNOI (Kotlin Native Ohos Interaction)

### 简介

Kotlin Native & ArkTS 互相调用能力，无需写 C/C++ 桥接代码。

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
ohpm install @kuiklybase/knoi

// 初始化
import { setup, init } from "@kuiklybase/knoi"
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
