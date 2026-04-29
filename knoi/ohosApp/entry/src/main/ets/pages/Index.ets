import { bind, getService, invoke, invokeRetPromise, registerServiceProvider, registerSingleServiceProvider, setup, unBind } from "@kuiklybase/knoi"
import { MultiCallbackListenerImpl } from '../knoi/MultlCallbackListener'
import {
  SingletonTestServiceB,
  TestServiceB,
  getSingletonTestServiceB,
  getTestServiceB,
  getMultiCallbackSample,
  TestModuleLoad
} from '../knoi/provider'
import SingletonTestServiceAImpl from '../SingletonTestServiceAImpl'
import TestServiceAImpl from '../TestServiceAImpl'
import { process, worker } from '@kit.ArkTS'
import { DeclareTest_testString, testString } from '../knoi/declare'
import { Rect } from '../knoi/rect'
import router from '@ohos.router';
import { display } from '@kit.ArkUI'
import TestServiceAInWorkerImpl from '../TestServiceAInWorkerImpl'

interface PromiseServiceObjectPayload {
  name: string
  count: number
  enabled: boolean
}

@Entry
@Component
struct Index {
  // ！！！测试 JSCallback 泄露，请不要这样使用！！！
  private func: Function | undefined
  onPageShow(): void {
    registerServiceProvider("TestServiceAInWorker", true, TestServiceAInWorkerImpl)
    bind("testStringReturnStringForKMM", (name: string) => {
      console.log("knoi-sample testStringReturnStringForKMM")
    })
    bind("testNumberReturnNumberForKMM", (age: number) => {
      console.log("knoi-sample testNumberReturnNumberForKMM")
      return age + 1
    })
    bind("testObjectReturnObjectForKMM", (obj: object) => {
      console.log("knoi-sample testObjectReturnObjectForKMM")
      return obj
    })
    bind("testBooleanReturnBooleanForKMM", (result: Boolean) => {
      console.log("knoi-sample testBooleanReturnBooleanForKMM")
      return !result
    })

    bind("testVoidReturnVoidForKMM", () => {
      console.log("knoi-sample testVoidReturnVoidForKMM")
    })

    bind("testUint8ArrayReturnVoidForKMM", () => {
      console.log("knoi-sample testUint8ArrayReturnVoidForKMM")
      return new Uint8Array(0)
    })

    bind("testJsCallbackReturnVoid", (func: Function) => {
      console.log("knoi-sample testJsCallbackReturnVoid")
      func("testJsCallbackReturnVoidCallback")
    })

    bind("testArrayBufferReturnArrayBufferForKMM", (buffer: ArrayBuffer): ArrayBuffer => {
      let inputBuffer: Int8Array = new Int8Array(buffer);
      console.log(`knoi-sample testArrayBufferReturnArrayBufferForKMM ${inputBuffer[0]} ${inputBuffer[1]} ${inputBuffer[2]} ${inputBuffer[3]}`)
      inputBuffer[4] = 4
      inputBuffer[5] = 5
      inputBuffer[6] = 6
      inputBuffer[7] = 7
      return inputBuffer
    })

    bind("testArrayBufferInt32ReturnArrayBufferInt32ForKMM", (buffer: Int32Array): Int32Array => {
      // let inputBuffer: Int8Array = new Int8Array(buffer);
      console.log(`knoi-sample testArrayBufferInt32ReturnArrayBufferInt32ForKMM ${buffer[0]} ${buffer[1]} ${buffer[2]} ${buffer[3]}`)
      buffer[4] = 4
      buffer[5] = 5
      buffer[6] = 6
      buffer[7] = 7
      return buffer
    })

    registerServiceProvider("TestServiceA", false, TestServiceAImpl)
    registerSingleServiceProvider("SingletonTestServiceA", new SingletonTestServiceAImpl())
  }

  build() {
    Row() {
      Column() {
        Text("BenchMark")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            onJumpClick()
          })
        Text("LoadBalance")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            invoke("testLoadBalance")
          })
        Text("ArkTS call KN")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            console.log("ohos tid:"+process.tid + "pid:"+process.pid)

            let result: String = invoke<String>("testStringReturnString", "input")
            console.log("knoi-sample invoke testStringReturnString result " + result);
            result = invoke<String>("testVoidReturnString",)
            console.log("knoi-sample invoke testVoidReturnString result " + result);
            invoke<void>("testVoidReturnVoid",)
            console.log("knoi-sample invoke testVoidReturnVoid result void");
            let numberResult: Number = invoke<number>("testIntReturnInt", 42)
            console.log("knoi-sample invoke testIntReturnInt result " + numberResult);
            numberResult = invoke<number>("testLongReturnLong", 42)
            console.log("knoi-sample invoke testLongReturnLong result " + numberResult);

            numberResult = invoke<number>("testDoubleReturnDouble", 42.1)
            console.log("knoi-sample invoke testDoubleReturnDouble result " + numberResult);

            let boolResult: Boolean = invoke<Boolean>("testBooleanReturnBoolean", true)
            console.log("knoi-sample invoke testBooleanReturnBoolean result " + boolResult);

            let objResult: Object = invoke<Object>("testMapReturnMap", {
              name: "ArkTs", age: 42, children: {
                name: "ArkTS", age: 18
              }
            })
            console.log("knoi-sample invoke testMapReturnMap result " + JSON.stringify(objResult));

            invoke("testJSCallbackReturnVoid", (result: string) => {
              console.log("knoi-sample invoke testJSCallbackReturnVoid log in ArkTs result = " + result)
            })

            invoke("testJSCallbackReturnString", (result: string) => {
              return result + "ArkTS"
            })
            invoke("testJSCallbackReturnMap", (result: Object) => {
              console.log("knoi-sample invoke testJSCallbackReturnMap result " + JSON.stringify(result));
              return result
            })

            this.func = invoke<Function>("testJSCallbackReturnJSCallback", (result: Object) => {
              console.log("knoi-sample invoke testJSCallbackReturnJSCallback result " + JSON.stringify(result));
              return result
            })
            console.log("knoi-sample invoke testJSCallbackReturnJSCallback func " + this.func);
            this.func("testJSCallbackReturnJSCallback invoke")
            let arrayResult: Array<String> =
              invoke<Array<String>>("testArrayReturnArray", ["ArkTsArray1", "ArkTsArray2"])
            console.log("knoi-sample invoke testArrayReturnArray result " + JSON.stringify(arrayResult));

            arrayResult = invoke<Array<String>>("testListReturnList", ["ArkTsArray1", "ArkTsArray2"])
            console.log("knoi-sample invoke testListReturnList result " + JSON.stringify(arrayResult));

            let buff: ArrayBuffer = new ArrayBuffer(8);
            let int8Buff: Int8Array = new Int8Array(buff);
            int8Buff[0] = 0;
            int8Buff[1] = 1;
            int8Buff[2] = 2;
            int8Buff[3] = 3;
            let bufferResult: ArrayBuffer = invoke<ArrayBuffer>("testArrayBufferReturnArrayBuffer", buff)
            let resultInt8Buff: Int8Array = new Int8Array(bufferResult);
            console.log(`knoi-sample invoke testArrayBufferReturnArrayBuffer result:${resultInt8Buff[4]} ${resultInt8Buff[5]} ${resultInt8Buff[6]} ${resultInt8Buff[7]}`)

            let obj: Map<String, ArrayBuffer> = invoke<Map<String, ArrayBuffer>>("testVoidReturnArrayBufferInMap")
            let data: ArrayBuffer = obj["data"]
            let dataInt32Buff: Int32Array = new Int32Array(data);
            console.log(`knoi-sample invoke testVoidReturnArrayBufferInMap result = ${dataInt32Buff[0]} ${dataInt32Buff[1]} ${dataInt32Buff[2]} ${dataInt32Buff[3]}`);

            let sharedNativeBuffer: ArrayBuffer = invoke<ArrayBuffer>("testCreateNativeOwnedArrayBufferForExternal")
            let sharedNativeBytes: Uint8Array = new Uint8Array(sharedNativeBuffer)
            console.log(
              `knoi-sample external_arraybuffer initial = ${sharedNativeBytes[0]} ${sharedNativeBytes[1]} ${sharedNativeBytes[2]} ${sharedNativeBytes[3]}`
            )
            let sharedNativeReturned: ArrayBuffer =
              invoke<ArrayBuffer>("testMutateStoredNativeOwnedArrayBufferForExternal")
            console.log(
              `knoi-sample external_arraybuffer after Kotlin mutation = ${sharedNativeBytes[0]} ${sharedNativeBytes[1]} ${sharedNativeBytes[2]} ${sharedNativeBytes[3]} sameRef=${sharedNativeReturned === sharedNativeBuffer}`
            )
            if (
              sharedNativeReturned === sharedNativeBuffer &&
                sharedNativeBytes[0] === 51 &&
                sharedNativeBytes[1] === 52 &&
                sharedNativeBytes[2] === 53 &&
                sharedNativeBytes[3] === 54
            ) {
              console.log("knoi-sample external_arraybuffer verification passed")
            } else {
              console.error("knoi-sample external_arraybuffer verification failed")
            }

            let ownershipInput: ArrayBuffer = new ArrayBuffer(4)
            let ownershipInputBytes: Uint8Array = new Uint8Array(ownershipInput)
            ownershipInputBytes[0] = 1
            ownershipInputBytes[1] = 2
            ownershipInputBytes[2] = 3
            ownershipInputBytes[3] = 4
            let jsOwnershipReport = invoke<Object>(
              "testVerifyArrayBufferOwnershipOps",
              ownershipInput
            ) as Record<string, Object>
            console.log(
              "knoi-sample testVerifyArrayBufferOwnershipOps = " + JSON.stringify(jsOwnershipReport)
            )
            let jsEnsuredSameInstance: boolean = jsOwnershipReport["ensuredSameInstance"] as boolean
            let jsDetachedSameInstance: boolean = jsOwnershipReport["detachedSameInstance"] as boolean
            let jsOriginalBytes: Array<number> = jsOwnershipReport["originalBytes"] as Array<number>
            let jsEnsuredBytes: Array<number> = jsOwnershipReport["ensuredBytes"] as Array<number>
            let jsDetachedBytes: Array<number> = jsOwnershipReport["detachedBytes"] as Array<number>
            if (
              !jsEnsuredSameInstance &&
                !jsDetachedSameInstance &&
                jsOriginalBytes[0] === 1 &&
                jsEnsuredBytes[0] === 101 &&
                jsDetachedBytes[0] === 111
            ) {
              console.log("knoi-sample JS-backed ensureNativeOwned/toDetachedCopy verification passed")
            } else {
              console.error("knoi-sample JS-backed ensureNativeOwned/toDetachedCopy verification failed")
            }

            let nativeOwnershipReport = invoke<Object>(
              "testVerifyNativeOwnedArrayBufferOwnershipOps"
            ) as Record<string, Object>
            console.log(
              "knoi-sample testVerifyNativeOwnedArrayBufferOwnershipOps = " + JSON.stringify(nativeOwnershipReport)
            )
            let nativeEnsuredSameInstance: boolean = nativeOwnershipReport["ensuredSameInstance"] as boolean
            let nativeDetachedSameInstance: boolean = nativeOwnershipReport["detachedSameInstance"] as boolean
            let nativeSourceBytes: Array<number> = nativeOwnershipReport["sourceBytes"] as Array<number>
            let nativeDetachedBytes: Array<number> = nativeOwnershipReport["detachedBytes"] as Array<number>
            if (
              nativeEnsuredSameInstance &&
                !nativeDetachedSameInstance &&
                nativeSourceBytes[0] === 91 &&
                nativeDetachedBytes[0] === 131
            ) {
              console.log("knoi-sample native-owned ensureNativeOwned/toDetachedCopy verification passed")
            } else {
              console.error("knoi-sample native-owned ensureNativeOwned/toDetachedCopy verification failed")
            }
          })
        Text("ArkTS call KN Async")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            invokeRetPromise<number>("testAsyncIntReturnInt", 42)
              .then((result: number) => {
                console.log("knoi-sample invokeRetPromise testAsyncIntReturnInt result = " + result)
              })
              .catch((error: Error) => {
                console.error("knoi-sample invokeRetPromise testAsyncIntReturnInt error = " + error.message)
              })

            invokeRetPromise<void>("testAsyncVoidReturnVoid")
              .then(() => {
                console.log("knoi-sample invokeRetPromise testAsyncVoidReturnVoid resolved")
              })
              .catch((error: Error) => {
                console.error("knoi-sample invokeRetPromise testAsyncVoidReturnVoid error = " + error.message)
              })

            invokeRetPromise<string>("testAsyncJSCallbackReturnString", (message: string) => {
              console.log("knoi-sample invokeRetPromise testAsyncJSCallbackReturnString callback = " + message)
              return message + " from ArkTS"
            })
              .then((result: string) => {
                console.log("knoi-sample invokeRetPromise testAsyncJSCallbackReturnString result = " + result)
              })
              .catch((error: Error) => {
                console.error("knoi-sample invokeRetPromise testAsyncJSCallbackReturnString error = " + error.message)
              })

            invokeRetPromise<Object>("testAsyncJSValueReturnJSValue", {
              name: "ArkTS async object",
              count: 7,
              enabled: true
            })
              .then((result: Object) => {
                console.log("knoi-sample invokeRetPromise testAsyncJSValueReturnJSValue result = " + JSON.stringify(result))
              })
              .catch((error: Error) => {
                console.error("knoi-sample invokeRetPromise testAsyncJSValueReturnJSValue error = " + error.message)
              })

            invokeRetPromise<Object>("testAsyncVoidReturnJSValue")
              .then((result: Object) => {
                console.log("knoi-sample invokeRetPromise testAsyncVoidReturnJSValue result = " + JSON.stringify(result))
              })
              .catch((error: Error) => {
                console.error("knoi-sample invokeRetPromise testAsyncVoidReturnJSValue error = " + error.message)
              })

            invokeRetPromise<Array<Object>>("testAsyncListJSValueReturnList", [
              {
                name: "ArkTS async list item 1",
                count: 1,
                enabled: true
              },
              {
                name: "ArkTS async list item 2",
                count: 2,
                enabled: false
              }
            ])
              .then((result: Array<Object>) => {
                console.log("knoi-sample invokeRetPromise testAsyncListJSValueReturnList result = " + JSON.stringify(result))
              })
              .catch((error: Error) => {
                console.error("knoi-sample invokeRetPromise testAsyncListJSValueReturnList error = " + error.message)
              })

            let asyncBuffer: ArrayBuffer = new ArrayBuffer(4)
            let asyncBytes: Uint8Array = new Uint8Array(asyncBuffer)
            asyncBytes[0] = 1
            asyncBytes[1] = 2
            asyncBytes[2] = 3
            asyncBytes[3] = 4
            invokeRetPromise<ArrayBuffer>("testAsyncArrayBufferReturnArrayBuffer", asyncBuffer)
              .then((result: ArrayBuffer) => {
                let resultBytes: Uint8Array = new Uint8Array(result)
                console.log(`knoi-sample invokeRetPromise testAsyncArrayBufferReturnArrayBuffer result = ${resultBytes[0]} ${resultBytes[1]} ${resultBytes[2]} ${resultBytes[3]}`)
              })
              .catch((error: Error) => {
                console.error("knoi-sample invokeRetPromise testAsyncArrayBufferReturnArrayBuffer error = " + error.message)
              })
            invokeRetPromise<ArrayBuffer>("testAsyncArrayBufferReturnArrayBuffer2", asyncBuffer)
              .then((result: ArrayBuffer) => {
                let resultBytes: Uint8Array = new Uint8Array(result)
                console.log(`knoi-sample invokeRetPromise testAsyncArrayBufferReturnArrayBuffer2 result = ${resultBytes[0]} ${resultBytes[1]} ${resultBytes[2]} ${resultBytes[3]}`)
              })
              .catch((error: Error) => {
                console.error("knoi-sample invokeRetPromise testAsyncArrayBufferReturnArrayBuffer2 error = " + error.message)
              })

            let nestedMapInput: Map<String, ArrayBuffer> = new Map();
            let nestedBufferA: ArrayBuffer = new ArrayBuffer(4);
            let nestedBytesA: Uint8Array = new Uint8Array(nestedBufferA);
            nestedBytesA[0] = 10;
            nestedBytesA[1] = 11;
            nestedBytesA[2] = 12;
            nestedBytesA[3] = 13;
            let nestedBufferB: ArrayBuffer = new ArrayBuffer(4);
            let nestedBytesB: Uint8Array = new Uint8Array(nestedBufferB);
            nestedBytesB[0] = 20;
            nestedBytesB[1] = 21;
            nestedBytesB[2] = 22;
            nestedBytesB[3] = 23;
            nestedMapInput.set("first", nestedBufferA);
            nestedMapInput.set("second", nestedBufferB);
            invokeRetPromise<Map<String, ArrayBuffer>>("testAsyncMapArrayBufferReturnMap", nestedMapInput)
              .then((result: Map<String, ArrayBuffer>) => {
                let firstBytes: Uint8Array = new Uint8Array(result["first"])
                let secondBytes: Uint8Array = new Uint8Array(result["second"])
                console.log(`knoi-sample invokeRetPromise testAsyncMapArrayBufferReturnMap first = ${firstBytes[0]} ${firstBytes[1]} ${firstBytes[2]} ${firstBytes[3]}`)
                console.log(`knoi-sample invokeRetPromise testAsyncMapArrayBufferReturnMap second = ${secondBytes[0]} ${secondBytes[1]} ${secondBytes[2]} ${secondBytes[3]}`)
              })
              .catch((error: Error) => {
                console.error("knoi-sample invokeRetPromise testAsyncMapArrayBufferReturnMap error = " + error.message)
              })
          })
        Text("Kotlin Multi Download")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            console.log("knoi-sample invokeRetPromise testDownloadResourcePackagesInKotlin start")
            invokeRetPromise<Object>("testDownloadResourcePackagesInKotlin")
              .then((result: Object) => {
                console.log(
                  "knoi-sample invokeRetPromise testDownloadResourcePackagesInKotlin result = " +
                    JSON.stringify(result)
                )
              })
              .catch((error: Error) => {
                console.error(
                  "knoi-sample invokeRetPromise testDownloadResourcePackagesInKotlin error = " +
                    error.message
                )
              })
          })
        Text("KN call ArkTS")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            invoke("testJSFunction")
            // unBind("testVoidReturnVoidForKMM")
          })

        Text("JSValue Test")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            invoke("testJSValue")
          })

        Text("MainHandle Test")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            invoke("testMainHandler")
          })

        Text("Coroutines Test")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            invoke("testCoroutines")
          })

        Text("Service Test")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            invoke("testService")
            let serviceB = getTestServiceB()
            serviceB.methodWithStringReturnString("method1 from ArkTs")
            let result = serviceB.methodWithStringReturnString("method2 from ArkTs")
            console.log("knoi-sample TestServiceB method2 result = " + result)

            let intResult = serviceB.methodWithIntReturnInt(42)
            console.log("knoi-sample TestServiceB methodWithIntReturnInt result = " + intResult)
            let strResult = serviceB.methodWithStringReturnString("param in ArkTS")
            console.log("knoi-sample TestServiceB methodWithStringReturnString result = " + strResult)
            serviceB.methodWithPromiseString("promise param in ArkTS")
              .then((promiseResult: string) => {
                console.log("knoi-sample TestServiceB methodWithPromiseString result = " + promiseResult)
              })
              .catch((error: Error) => {
                console.error("knoi-sample TestServiceB methodWithPromiseString error = " + error.message)
              })
            let promiseJSValueInput: PromiseServiceObjectPayload = {
              name: "arkts",
              count: 1,
              enabled: true
            }
            serviceB.methodWithPromiseJSValueReturnJSValue(promiseJSValueInput)
              .then((promiseResult: Object) => {
                console.log("knoi-sample TestServiceB methodWithPromiseJSValueReturnJSValue result = " + JSON.stringify(promiseResult))
              })
              .catch((error: Error) => {
                console.error("knoi-sample TestServiceB methodWithPromiseJSValueReturnJSValue error = " + error.message)
              })
            serviceB.methodWithPromiseUnitReturnJSValue()
              .then((promiseResult: Object) => {
                console.log("knoi-sample TestServiceB methodWithPromiseUnitReturnJSValue result = " + JSON.stringify(promiseResult))
              })
              .catch((error: Error) => {
                console.error("knoi-sample TestServiceB methodWithPromiseUnitReturnJSValue error = " + error.message)
              })
            let longResult = serviceB.methodWithLongReturnLong(42)
            console.log("knoi-sample TestServiceB methodWithLongReturnLong result = " + longResult)
            let doubleResult = serviceB.methodWithDoubleReturnDouble(42.0)
            console.log("knoi-sample TestServiceB methodWithDoubleReturnDouble result = " + doubleResult)
            let booleanResult = serviceB.methodWithBooleanReturnBoolean(true)
            console.log("knoi-sample TestServiceB methodWithBooleanReturnBoolean result = " + booleanResult)
            let arrayResult = serviceB.methodWithArrayStringReturnArrayString(["1", "2"])
            console.log("knoi-sample TestServiceB methodWithArrayStringReturnArrayString result = " + arrayResult)
            let map : Map<string,string | undefined> = new Map()
            map.set("key1","value1")
            map.set("key2","value2")
            map.set("key3","value3")
            map.set("key4","value4")
            map.set("key5",undefined)
            let objResult = serviceB.methodWithMapReturnMap(map)
            console.log("knoi-sample TestServiceB methodWithMapReturnMap result = " + objResult)
            serviceB.methodWithUnit()
            console.log("knoi-sample TestServiceB methodWithUnit result")
            let promiseBuffer: ArrayBuffer = new ArrayBuffer(8)
            let promiseBufferArray: Uint8Array = new Uint8Array(promiseBuffer)
            promiseBufferArray[0] = 1
            promiseBufferArray[1] = 2
            promiseBufferArray[2] = 3
            promiseBufferArray[3] = 4
            serviceB.methodWithPromiseArrayBufferReturnArrayBuffer(promiseBuffer)
              .then((promiseResult: ArrayBuffer) => {
                let bytes = Array.from(new Uint8Array(promiseResult)).join(",")
                console.log("knoi-sample TestServiceB methodWithPromiseArrayBufferReturnArrayBuffer result = " + bytes)
              })
              .catch((error: Error) => {
                console.error("knoi-sample TestServiceB methodWithPromiseArrayBufferReturnArrayBuffer error = " + error.message)
              })
            let promiseMapInput: PromiseServiceObjectPayload = {
              name: "promise-map",
              count: 2,
              enabled: false
            }
            serviceB.methodWithPromiseMapReturnMap(promiseMapInput)
              .then((promiseResult: Object) => {
                console.log("knoi-sample TestServiceB methodWithPromiseMapReturnMap result = " + JSON.stringify(promiseResult))
              })
              .catch((error: Error) => {
                console.error("knoi-sample TestServiceB methodWithPromiseMapReturnMap error = " + error.message)
              })
            let func: Function = serviceB.methodWithCallbackReturnCallback((a: string) => {
              console.log("knoi-sample TestServiceB methodWithCallbackReturnCallback result = " + a)
            })
            let callbackResult: string = func()
            console.log("knoi-sample TestServiceB methodWithCallbackReturnCallback callback result = " + callbackResult)

            // let func1: Function = serviceB.methodWithCallbackReturnCallback2((a: string, b: Number) => {
            //   console.log(`knoi-sample TestServiceB methodWithCallbackReturnCallback2 result a = ${a} , b = ${b}`)
            // }, "string", 42)
            // let func1Result: string = func1()
            // console.log("knoi-sample TestServiceB methodWithCallbackReturnCallback2 callback result = " + func1Result)

            serviceB.methodWithCallbackReturnCallback3(() => {
              console.log(`knoi-sample TestServiceB methodWithCallbackReturnCallback3 callback1`)
            }, () => {
              console.log(`knoi-sample TestServiceB methodWithCallbackReturnCallback3 callback2`)
            })

            let buff: ArrayBuffer = new ArrayBuffer(8);
            let int8Buff: Int8Array = new Int8Array(buff);

            let multiTypeMap: Record<string, Function | ArrayBuffer | Object> = {
              "callback": (result: string) => {
                console.log("knoi-sample multiTypeMap callback result " + result);
              },
              "arrayBuffer": buff,
              "typedBuffer": int8Buff,
              "object": this.getUIContext()
            }
            serviceB.methodWithDefaultValue()
            serviceB.methodWithDefaultValueInSubType()

            let multiTypeObject = serviceB.methodWithMultiTypeMap(multiTypeMap)
            let jsMap = new Map<string,string>()
            jsMap.set("key", "value")
            let map1: Record<string, number | string | Array<String> | Map<string,string>> = {
              "name": "arkTS",
              "arg": 42,
              "json": "jsonValue",
              "map" : jsMap
            }
            map1["array"] = ["element1", "element2", "element3"]
            let jsValueResult: Object = serviceB.methodWithJSValueReturnJSValue(map1)
            console.log("knoi-sample TestServiceB methodWithJSValueReturnJSValue result = " + jsValueResult)

            let singletonB = getSingletonTestServiceB()
            getSingletonTestServiceB().method1("method1 from ArkTs")
            singletonB.method1("method1 from ArkTs")
            result = singletonB.method2("method2 from ArkTs")
            console.log("knoi-sample SingletonTestServiceB method2 result = " + result)
          })

        Text("MultiCallbackTest")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            getMultiCallbackSample().init(new MultiCallbackListenerImpl())
            getMultiCallbackSample().callListener()
          })

        Text("JSValue In Sub")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            invoke("testJSValueInSubThread")
          })
        Text("Service In Sub")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            invoke("testServiceInSubThread")
          })
        Text("Service In JSWorker")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            invoke("testServiceInJSWorker")
          })
        Text("Custom Class Wrap")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            let result = invoke<Rect>("testCustomClassWrap")
            console.log("knoi-sample result.getLeft = " + result.getLeft(42))
            console.log("knoi-sample result.getLeft = " + result.getLeft(42))
          })
        Text("Declare Test")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            let result = testString
            console.log("knoi-sample Test Declare result = " + result)
            result = DeclareTest_testString
            console.log("knoi-sample Test Declare result = " + result)
          })
        Text("测试 JSValue 引用问题")
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            let logProxy: Record<string,object> = {};
            logProxy["impl"] = new LogImpl()
            getTestServiceB().testJSValueRef(logProxy)
          })

        Text("Test Module Load:"+display.getDefaultDisplaySync().height)
          .fontSize(40)
          .fontWeight(FontWeight.Bold)
          .onClick(() => {
            getService<TestModuleLoad>("TestModuleLoad").testModule()
          })

      }
      .width('100%')
    }
    .height('100%')
  }


}

// 在Home页面中
function onJumpClick(): void {
  router.pushUrl({
    url: 'pages/benchmark' // 目标url
  }, router.RouterMode.Standard, (err) => {
    if (err) {
      console.error(`Invoke pushUrl failed, code is ${err.code}, message is ${err.message}`);
      return;
    }
    console.info('Invoke pushUrl succeeded.');
  });
}

class LogImpl {
  info(msg: string) {
    console.log("knoi-samplejsvalue" + msg)
  }
}
