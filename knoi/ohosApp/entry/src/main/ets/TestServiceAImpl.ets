import { TestServiceA } from './knoi/consumer';
import { window } from '@kit.ArkUI';

export default class TestServiceAImpl {
  number: Number = 0

  constructor() {
    this.number = 42
    console.log("knoi-sample TestServiceA constructor...");
  }

  methodWithCallbackReturnCallback4(a: Function): string {
    console.log("knoi-sample methodWithCallbackReturnCallback4 ...");
    a("methodWithCallbackReturnCallback4 callback params")
    return "string in arkTS"
  }

  methodWithCallbackReturnCallback2(a: Function, b: string, c: number): Function {
    a("result", 42, 42, 42, false)
    return (result: String) => {
      return result + " modify ArkTS"
    }
  }

  methodWithCallbackReturnCallback3(a: Function, b: Function): string {
    return ""
  }

  methodWithLongReturnLong(a: number): number {
    return a + 1
  }

  methodWithBooleanReturnBoolean(a: boolean): boolean {
    return !a
  }

  methodWithUnit(): void {
    console.log("TestServiceA methodWithUnit...");
  }

  methodWithIntReturnInt(a: number): number {
    return a + 1
  }

  methodWithBooleanReturnInt(a: boolean): boolean {
    return !a
  }

  methodWithDoubleReturnDouble(a: number): number {
    return a + 0.1
  }

  methodWithStringReturnString(a: string): string | null {
    return a + "(modify by ArkTS)"
  }

  methodWithCallbackReturnCallback(a: Function): Function {
    a("callbackParam From ArkTS")
    return (param1: String, param2: String) => {
      console.log(`TestServiceAImpl methodWithCallbackReturnCallback ${param1} ${param2}`)
    }
  }

  methodWithArrayStringReturnArrayString(a: string[]): string[] {
    a.push("item from ArkTS")
    return a
  }

  methodWithMapReturnMap(a: object): object {
    a["json"] = JSON.stringify(a)
    return a
  }

  methodWithJSValueReturnJSValue(a: object): object {
    a["json"] = JSON.stringify(a)
    return a
  }

  methodWithArrayBufferReturnArrayBuffer(a: ArrayBuffer): ArrayBuffer | null {
    console.log("knoi-sample methodWithArrayBufferReturnArrayBuffer return null");
    let buffer = new Int32Array(a)
    buffer[4] = 88
    buffer[5] = 89
    buffer[6] = 90
    buffer[7] = 91
    //return buffer
    return null
  }

  methodWithArrayBufferReturnArrayBufferUseByteArray(a: ArrayBuffer): ArrayBuffer {
    let buffer = new Int8Array(a)
    buffer[4] = 5
    buffer[5] = 6
    buffer[6] = 7
    buffer[7] = 8
    return buffer
  }

  method3Params(a: string, b: number, c: object): object {
    return c
  }

  methodWithException(a: string): object {
    throw new Error(`Method not implemented. param = ${a}}`);
  }

  methodWithPromise(a: string): object {
    return window.getLastWindow(getContext())
  }
}