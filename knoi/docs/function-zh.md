### 方法调用

> 参数与返回值 支持类型及转换规则见 “类型转换” 章节

##### Kotlin 调用 ArkTS

- 1、注册 JS 方法 （ArkTS）


```javascript
import { bind } from "@kuiklybase/knoi"

bind("testStringReturnStringForKMM", (name: string) => {
      console.log("sample testStringReturnStringForKMM")
      return name + "forArkTS"
})

// 不再使用时记得取消绑定，防止内存泄露
unBind("testStringReturnStringForKMM")
```

- 2、KMM 调用 ArkTS （Kotlin）

```Kotlin
val strResult = getJSFunction("testStringReturnStringForKMM")?.invoke<String>("KMM")
```

##### ArkTS 调用 KMM

- 1、注册 KMM 方法 （Kotlin）

```Kotlin

// 业务代码
@KNExport
fun testStringFunction(name: String): String {
    return name + "forKMM"
}

```

- 2、ArkTS 调用 KMM （ArkTS）

```javascript
import { invoke } from "@kuiklybase/knoi"

let result: String = invoke<String>("testStringReturnString", "input")
console.log("invoke testStringReturnString result " + result);
```

##### 导出 `retPromise` 方法

当 ArkTS 侧需要拿到 `Promise` 时，使用 `@KNExportRetPromise`，而不是 `@KNExport`。ArkTS 侧应通过 `invokeRetPromise<R>(...)` 调用这类方法，而不是 `invoke<R>(...)`。

示例来自 `example/sample/src/ohosArm64Main/kotlin/com/tencent/tmm/knoi/sample/ArkTs2KNBenchmark.kt`：

```Kotlin
@KNExportRetPromise
fun testAsyncIntReturnInt(number: Int): Int {
    return number + 100
}

@KNExportRetPromise
fun testAsyncVoidReturnJSValue(): JSValue = runBlocking {
    val ownerTid = getCurrentAsyncInvokeOwnerTid() ?: getTid()
    val ioScope = CoroutineScope(Dispatchers.IO)
    ioScope.async {
        val result = JSValue.createJSObject(tid = ownerTid)
        result["type"] = JSValue.createJSValue("async-void-jsvalue", tid = ownerTid)
        result["ownerTid"] = JSValue.createJSValue(ownerTid, tid = ownerTid)
        result
    }.await()
}

@KNExportRetPromise
@OptIn(ExperimentalForeignApi::class)
fun testAsyncArrayBufferReturnArrayBuffer(buffer: ArrayBuffer): ArrayBuffer {
    info("testAsyncArrayBufferReturnArrayBuffer")
    buffer.getData<uint8_tVar>()?.set(0, 9u)
    return buffer
}
```

规格说明：

- 只有标注了 `@KNExportRetPromise` 的函数，才会被导出为 Promise 返回的方法。
- `@KNExportRetPromise(name = "...")` 的 `name` 参数与 `@KNExport` 使用相同的注册规则。
- 生成后的 ArkTS 签名会变为 `Promise<R>`。例如 `Int -> Promise<number>`、`Unit -> Promise<void>`、`ArrayBuffer -> Promise<ArrayBuffer>`。
- 异步参数和返回值支持的类型为 `Unit`、`Boolean`、`Int`、`String`、`Double`、`Long`、`Array`、`Function`、`List`、`ArrayList`、`Map`、`HashMap`、`JSValue`、`ArrayBuffer`。
- 异步导出的类型校验是递归的。`Array`、`List`、`ArrayList`、`Map`、`HashMap` 只有在元素类型同样受支持时才可使用。
- Kotlin 抛出的异常会在 ArkTS 侧表现为 `Promise reject`。
- 异步函数返回的 `JSValue`，以及容器中的嵌套 `JSValue`，必须保持在发起调用的 JS 线程上。如果在异步任务里构造 `JSValue`，需要像 `testAsyncVoidReturnJSValue()` 一样使用调用方 owner tid。

不支持的场景：

- 不支持未纳入转换集合的参数或返回值类型，例如 `KClass`，或未被支持的自定义类。
- 不支持在 `ohosArm64` 之外使用 `@KNExportRetPromise`；异步导出代码生成仅在 Harmony `ohosArm64` 目标启用。
- 不支持返回在其他 JS 线程创建的 `JSValue`；此时运行时会将 `Promise` 置为 reject。
