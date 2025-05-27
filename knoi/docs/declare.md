### 类型导出

##### 常量导出

支持将 Kotlin 类型的常量进行导出

***注：由于存在跨 Runtime 调用，损耗相比同 Runtime 常量较大，建议在数量较多或者性能较为敏感的场景，直接 ArkTS 中定义一份常量***

可在Kotlin中用 KNExport 进行常量导出如下

```kotlin
// 顶层常量
@KNExport
const val testString: String = "testStringDeclare"

// 自定义 ets 中生成的常量名字
@KNExport("testStringDeclareCustomName")
const val customName: String = "testStringDeclare"

// Class 中 companion 常量
class DeclareTest {
    companion object {
        @KNExport
        const val testString: String = "testStringDeclareInClass"
    }
}
```

生成 Typescript 代码如下：

```javascript
import { getDeclare } from "@kuiklybase/knoi"

export const testString = getDeclare<string>("com.tencent.tmm.knoi.sample.testString")

export const testStringDeclareCustomName = getDeclare<string>("com.tencent.tmm.knoi.sample.customName")

export const DeclareTest_testString = getDeclare<string>("com.tencent.tmm.knoi.sample.DeclareTest.testString")

```

##### 自定义类型导出

支持将 Kotlin 的对象通过 JSValue 进行导出

可以在 Kotlin 侧使用 JSValue.wrap 进行 Kotlin 对象的包装

```kotlin
val rect = Rect(1, 2, 3, 4)
// 包装 rect 对象
val rectJSValue = JSValue.wrap(rect)
// 定义 getLeft 方法, 返回值为 Int
rectJSValue["getLeft"] = JSValue.createJSFunction<Int> { jsThis, params ->
    // 解包装 jsThis，取到 rect 对象
    val nativeRect = JSValue.unwrap<Rect>(jsThis)
    // 获取 getLeft 方法第一个参数并转为 Int 类型
    val param1 = params[0]!!.toInt()
    return@createJSFunction nativeRect?.l!! + param1
}
```

如在 ArkTS 侧需要对该对象进行调用，需定义 TypeScript 类型定义
```javascript
export interface Rect {
  getLeft(left: number): number;
}
```
