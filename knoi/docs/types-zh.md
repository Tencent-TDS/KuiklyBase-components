### 类型转换

***所有方法调用、服务调用均只支持下表中的类型***

由于 Kotlin Native 与 ArkTs 类型无法完全匹配，在跨 Runtime 调用时会有类型转换，转换规则如下：

| **JavaScript**               | **Kotlin** |
|------------------------------| ------------------------ |
| Boolean                      | Boolean
| Number                       | Double、Int、Long |
| String                       | String               |
| Array                        | Array、List           |
| Function                     | (args: Array<JSValue?>) -> Any? |
| Object  <br> Map             | Map<String,Any?>|
| ArrayBuffer, <br> TypedArray | ArrayBuffer  |
| void                         | Unit  |
| any                          | JSValue |

***注1：Number 类型的转换在已知参数类型的场景下，会自动转成对应的 Int/Long/Double。JS Number 是 IEEE-754 双精度；转为 Int/Long 时通过 Number.toInt()/toLong()，而不是把 Float64 字节当作 i32 读取。***

***注2：ArrayBuffer 可直接操作 napi 中的指针，但需注意⚠️不要进行释放。ArrayBuffer 不能当作 Array。Array/List 请传入 JS Array 或 TypedArray。Int32Array 按 i32 读取，其他 TypedArray 按 JS Number 读取。***

***注3：Function 类型暂时不支持自动推断，只能以 Array<JSValue?> 接收入参***

***注4：`Array<Int>` / `List<Int>` 是支持的类型。由于类型擦除，JS `Array<number>` 在运行时会先变成 Double 数组；knoi 会对每个元素执行 Number.toInt()，避免 `toIntArray()` 得到全 0。请不要为此把接口改成 `Array<Double>`。***
