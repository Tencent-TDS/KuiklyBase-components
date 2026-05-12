### Function Calling

> For supported parameter and return value types and conversion rules, see the "Type Conversion" section

##### Kotlin Calling ArkTS

- 1、Register JS Method (ArkTS)

```javascript
import { bind } from "@kuiklybase/knoi"

bind("testStringReturnStringForKMM", (name: string) => {
      console.log("sample testStringReturnStringForKMM")
      return name + "forArkTS"
})

// Remember to unbind when no longer in use to prevent memory leaks
unBind("testStringReturnStringForKMM")
```

- 2、KMM Calling ArkTS (Kotlin)

```Kotlin
val strResult = getJSFunction("testStringReturnStringForKMM")?.invoke<String>("KMM")
```

##### ArkTS Calling KMM

- 1、Register KMM Method (Kotlin)

```Kotlin

// Business logic
@KNExport
fun testStringFunction(name: String): String {
    return name + "forKMM"
}

```

- 2、ArkTS Calling KMM (ArkTS)

```javascript
import { invoke } from "@kuiklybase/knoi"

let result: String = invoke<String>("testStringReturnString", "input")
console.log("invoke testStringReturnString result " + result);
```

##### `retPromise` for Exported Functions

Use `@KNExportRetPromise` when ArkTS should receive a `Promise` instead of a synchronous return value. On the ArkTS side, call these methods with `invokeRetPromise<R>(...)`, not `invoke<R>(...)`.

Examples from `example/sample/src/ohosArm64Main/kotlin/com/tencent/tmm/knoi/sample/ArkTs2KNBenchmark.kt`:

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

Specification:

- Only functions annotated with `@KNExportRetPromise` are exported as Promise-returning functions.
- `name` on `@KNExportRetPromise(name = "...")` follows the same registration rule as `@KNExport`.
- The generated ArkTS signature becomes `Promise<R>`. For example, `Int -> Promise<number>`, `Unit -> Promise<void>`, and `ArrayBuffer -> Promise<ArrayBuffer>`.
- Supported async parameter and return types are `Unit`, `Boolean`, `Int`, `String`, `Double`, `Long`, `Array`, `Function`, `List`, `ArrayList`, `Map`, `HashMap`, `JSValue`, and `ArrayBuffer`.
- Async export type checking is recursive. `Array`, `List`, `ArrayList`, `Map`, and `HashMap` are supported only when their element types are also supported.
- Thrown Kotlin exceptions reject the ArkTS `Promise`.
- `JSValue` and nested `JSValue` values returned from async functions must stay on the invoking JS thread. If you construct a `JSValue` inside async work, use the invoking owner tid as shown in `testAsyncVoidReturnJSValue()`.

Not supported:

- Unsupported parameter or return types, such as `KClass` or arbitrary custom classes that are not in the supported conversion set.
- Using `@KNExportRetPromise` outside `ohosArm64`; async export code generation is only enabled for the Harmony `ohosArm64` target.
- Returning a `JSValue` created on a different JS thread; runtime will reject the `Promise` in this case.
