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