### 服务调用

> 参数与返回值 支持类型及转换规则见 “类型转换” 章节

##### Kotlin 调用 ArkTS 服务


- 1、声明接口为 ServiceConsumer （Kotlin）

```Kotlin
@ServiceConsumer
interface TestServiceA {

    fun method1(str: String)

    fun method2(str: String): String?
}
```   

- 2、ArkTS 绑定 （ArkTS）

***注：如 Release 开启混淆，需对 ArkTs 侧的 ServiceProvider 进行 keep。详细混淆配置见 [华为文档](https://developer.huawei.com/consumer/cn/doc/harmonyos-guides-V5/source-obfuscation-V5)***

```
import { registerServiceProvider, registerSingleServiceProvider } from "knoi"
//TestService 为自行定义的 Ark TS class
//TestService 可实现 Ksp 自动生成的 d.ts 文件（自动生成在 build/ts-api/ 下）,需自行在鸿蒙工程引用
registerServiceProvider("TestServiceA", /** 是否单例 **/ false, TestService)
//以对象方式注入 ArkTS 单例服务
registerSingleServiceProvider("SingletonTestServiceA", new SingletonTestServiceAImpl())

```

- 3、Kotlin调用（Kotlin）

编译后 KSP 插件会生成 get{ServiceName}Api 的方法，ServiceName 如无自定义，通常为类名
```Kotlin
getTestServiceAApi().method1("param1")
```

***注：服务调用暂时不支持 ArkTS class 静态方法***

##### Ark 调用 Kotlin 服务

- 1、声明 ServiceProvider (Kotlin)

> 注：该类必须为 open 且 no Final

```Koltin

// Service 名字通常为类名（不包含包名）,可传入 singleton 是否单例
@ServiceProvider
open class TestServiceB {

    fun method1(str: String) {
        info("TestServiceB method1 ${str}")
    }

}
```

- 2、ArkTS 调用（ArkTS）

> 注：此处的getTestServiceB 和接口定义自动生成在 build/ts-api/下，需自行在鸿蒙工程引用

```ArkTS
// 调用方式 1 ：辅助函数
// 辅助函数生成规则 get{ServiceName}，服务名如无自定义，通常是类名
getTestServiceB().method1("param1")
// 调用方式 2：服务名调用
getService<TestServiceB>("TestServiceB").method1("param1")
```

### 详细例子🌰

##### Kotlin 调用 ArkTS 

特定类型的转换：

1. JS 回调参数需以 Array<JSValue> 类型接收

2. JSValue 可接收任意类型

```Kotlin
@ServiceConsumer
interface TestServiceA {
    // 传入 Int 返回 Int
    fun methodWithIntReturnInt(a: Int): Int
    // 传入 Long 返回 Long
    fun methodWithLongReturnLong(a: Long): Long
    // 传入 Boolean 返回 Boolean
    fun methodWithBooleanReturnBoolean(a: Boolean): Boolean
    // 传入 Double 返回 Double
    fun methodWithDoubleReturnDouble(a: Double): Double
    // 传入 String 返回 String
    fun methodWithStringReturnString(a: String): String
    // 传入 Callback 返回 Callback
    fun methodWithCallbackReturnCallback(a: (Array<JSValue>) -> Unit): ((Array<JSValue>) -> String)
    // 传入 String 数组 返回 String 数组
    fun methodWithArrayStringReturnArrayString(a: Array<String>): Array<String>
    // 传入 JS 对象 返回 JS 对象
    fun methodWithMapReturnMap(a: Map<String, Any?>): Map<String, Any?>
    // 无参
    fun methodWithUnit()
    // 传入 任意 JS 类型，返回任意 JS类型
    fun methodWithJSValueReturnJSValue(a: JSValue): JSValue
    // 传入 ArrayBuffer 二进制数据，返回 二进制数据
    fun methodWithArrayBufferReturnArrayBuffer(a: ArrayBuffer): ArrayBuffer?
    // 多参数
    fun method3Params(a: String, b: Int, c: JSValue): JSValue
    // 返回类型为 Promise，需以 JSValue 接收
    fun methodWithPromise(a:String): JSValue
}
```   

##### ArkTS 调用 Kotlin

```kotlin
package com.tencent.tmm.knoi.sample

import com.tencent.tmm.knoi.annotation.Hidden
import com.tencent.tmm.knoi.annotation.ServiceProvider
import com.tencent.tmm.knoi.logger.info
import com.tencent.tmm.knoi.type.ArrayBuffer
import com.tencent.tmm.knoi.type.JSValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.set
import platform.posix.uint8_tVar
import kotlin.reflect.KClass

@ServiceProvider
open class TestServiceB {
    init {
        info("TestServiceB init.")
    }

    // 传入 Int 返回 Int
    fun methodWithIntReturnInt(a: Int): Int {
        return a + 1
    }

    // 传入 Long 返回 Long
    fun methodWithLongReturnLong(a: Long): Long {
        return a + 1
    }

    // 传入 Boolean 返回 Boolean
    fun methodWithBooleanReturnBoolean(a: Boolean): Boolean {
        return !a
    }

    // 传入 Boolean 返回 Double
    fun methodWithDoubleReturnDouble(a: Double): Double {
        return a + 0.1
    }

    // 传入 String 返回 String
    fun methodWithStringReturnString(a: String): String {
        return a + " modify from KMM"
    }

    // 传入 回调 返回 回调
    fun methodWithCallbackReturnCallback(a: (Array<JSValue>) -> Unit): ((Array<JSValue>) -> String) {
        a.invoke(arrayOf(ktValueToJSValue(getEnv(), "result", String::class)))
        return {
            "callback result"
        }
    }

    // 传入 String 数组 返回 String数组
    fun methodWithArrayStringReturnArrayString(a: Array<String>): Array<String> {
        val list = a.toMutableList()
        list.add("plus in KMM")
        return list.toTypedArray()
    }

    // 传入 JS 对象 返回 JS 对象
    fun methodWithMapReturnMap(a: Map<String, Any?>): Map<String, Any?> {
        val map = a.toMutableMap()
        map["kmm"] = "push in KMM"
        return a.toMap()
    }

    // 无参数调用
    fun methodWithUnit() {
        info("TestServiceB methodWithUnit")
    }

    // 传入任意 JS 类型，返回任意 JS 类型
    fun methodWithJSValueReturnJSValue(a: JSValue): JSValue {
        return a["json"]!!
    }

    // 传入二进制数据返回二进制数据
    @OptIn(ExperimentalForeignApi::class)
    fun methodWithArrayBufferReturnArrayBuffer(buffer: ArrayBuffer): ArrayBuffer {
        info("methodWithArrayBufferReturnArrayBuffer")
        val bufferArray = buffer.getData<uint8_tVar>()
        bufferArray[4] = 4u
        bufferArray[5] = 5u
        bufferArray[6] = 6u
        bufferArray[7] = 7u
        return buffer
    }

    // 多参数
    fun method3Params(a: String, b: Int, c: JSValue): JSValue {
        return c
    }

    // 隐藏方法，不生成到 d.ts
    @Hidden
    fun method3(str: KClass<Any>): String {
        return "Hidden"
    }

    // private 方法，不生成到 d.ts
    private fun method4(str: KClass<Any>): String {
        return "Hidden"
    }
}
```