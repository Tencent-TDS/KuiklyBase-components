
### ArrayBuffer

ArrayBuffer 是对 JS 中 ArrayBuffer 和 TypedArray 的一种封装，可以直接操作 js 引擎中的二进制数据。

##### ByteArray 构造和获取

```kotlin
    val byteArray = ByteArray(8)
    byteArray[0] = 1
    byteArray[1] = 2
    byteArray[2] = 3
    byteArray[3] = 4
    val int8Result = getTestServiceAApi().methodWithArrayBufferReturnArrayBufferUseByteArray(ArrayBuffer(byteArray))
    if (int8Result != null) {
        val buffer = int8Result.getByteArray()
        info("TestServiceA methodWithArrayBufferReturnArrayBufferUseByteArray ${buffer[4]} ${buffer[5]} ${buffer[6]} ${buffer[7]} ")
    }
```

##### 指针构造和获取

```kotlin
    // 申请 int32 类型的 buffer，长度为 8
    val int32Buffer = nativeHeap.allocArray<int32_tVar>(8)
    int32Buffer[0] = 17
    int32Buffer[1] = 18
    int32Buffer[2] = 19
    int32Buffer[3] = 20
    // 创建 ArrayBuffer 对象 如非 uint8_tVar 需调用reinterpret，并传入 napi_typedarray_type
    val arrayBufferInt32 = ArrayBuffer(
        int32Buffer.reinterpret(),
        8L * Int.SIZE_BYTES,
        type = napi_typedarray_type.napi_int32_array
    )
    // arrayBufferInt32即可在方法调用和服务调用中互相传递
    // 此处记得释放 int32Buffer
```

```kotlin
     val arrayBuffer:ArrayBuffer = /**服务调用传入或者方法传入**/
     // 需知道 JS 传入的 buffer类型，int8/int32等等
     // 获取 二进制指针
     val ptr = arrayBuffer.getData<int32_tVar>()
     // 转换为 byteArray，不建议转换，存在一次数据拷贝
     val byteArray:ByteArray = ptr.readBytes(arrayBuffer.getByteLength().toInt())
```
