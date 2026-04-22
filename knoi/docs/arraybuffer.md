### ArrayBuffer

ArrayBuffer is a wrapper for ArrayBuffer and TypedArray in JavaScript, allowing direct manipulation of binary data in the JS engine.

##### ByteArray Construction and Access

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

##### Pointer Construction and Access

```kotlin
    // Allocate int32 type buffer with length 8
    val int32Buffer = nativeHeap.allocArray<int32_tVar>(8)
    int32Buffer[0] = 17
    int32Buffer[1] = 18
    int32Buffer[2] = 19
    int32Buffer[3] = 20
    // Create ArrayBuffer object - reinterpret required for non-uint8_tVar types, with napi_typedarray_type parameter
    val arrayBufferInt32 = ArrayBuffer(
        int32Buffer.reinterpret(),
        8L * Int.SIZE_BYTES,
        type = napi_typedarray_type.napi_int32_array
    )
    // arrayBufferInt32 can now be passed between method calls and service calls
    // Remember to release int32Buffer here
```

```kotlin
     val arrayBuffer:ArrayBuffer = /** Passed from service call or method **/
     // Need to know the buffer type from JS side (int8/int32 etc.)
     // Get binary pointer
     val ptr = arrayBuffer.getData<int32_tVar>()
     // Convert to byteArray (not recommended as it involves data copy)
     val byteArray:ByteArray = ptr.readBytes(arrayBuffer.getByteLength().toInt())
```
