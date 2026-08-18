### Type Conversion

***All method calls and service calls only support the types listed below***

Since Kotlin Native and ArkTs types cannot be perfectly matched, type conversion occurs during cross-Runtime calls with the following rules:

| **JavaScript**               | **Kotlin** |
|------------------------------| ------------------------ |
| Boolean                      | Boolean
| Number                       | Double, Int, Long |
| String                       | String               |
| Array                        | Array, List           |
| Function                     | (args: Array<JSValue?>) -> Any? |
| Object  <br> Map             | Map<String,Any?>|
| ArrayBuffer, <br> TypedArray | ArrayBuffer  |
| void                         | Unit  |
| any                          | JSValue |

***Note 1: Number type conversion will automatically convert to corresponding Int/Long/Double when parameter type is known. JS Number is IEEE-754 double; knoi converts to Int/Long via Number.toInt()/toLong(), not by reading raw i32 from a Float64 buffer.***

***Note 2: ArrayBuffer can directly manipulate napi pointers, but be careful⚠️ not to release them. ArrayBuffer is not Array. Pass a JS Array or TypedArray for Array/List. Int32Array is read as i32; other TypedArrays are read as JS Number.***

***Note 3: Function types currently don't support automatic inference, can only receive parameters as Array<JSValue?>***

***Note 4: `Array<Int>` / `List<Int>` are supported. JS `Array<number>` arrives as Doubles at runtime (type erasure). knoi coerces each element with Number.toInt() so `toIntArray()` is not all zeros. Prefer this over changing the API to `Array<Double>`.***