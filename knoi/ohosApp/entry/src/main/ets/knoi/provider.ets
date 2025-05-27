/***
*    !!!  GEN CODE DO NOT EDIT  !!!
***/
import { getService } from "@kuiklybase/knoi"

export interface ServiceAInCommon {
            
   methodWithIntReturnInt(): string;

}

export function getServiceAInCommon(): ServiceAInCommon {
   return getService<ServiceAInCommon>("ServiceAInCommon")
}

export interface TestServiceBInCommon {
            
   methodWithIntReturnInt(a: number): number;

   a(): void;

}

export function getTestServiceBInCommon(): TestServiceBInCommon {
   return getService<TestServiceBInCommon>("TestServiceBInCommon")
}

export interface TestServiceCInCommon {
            
   methodWithIntReturnInt(a: number): number;

   a(): void;

}

export function getTestServiceCInCommon(): TestServiceCInCommon {
   return getService<TestServiceCInCommon>("TestServiceCInCommon")
}

export interface LooooooooooongNameServiceProvider {
            
   methodWithUnit(): void;

}

export function getLooooooooooongNameServiceProvider(): LooooooooooongNameServiceProvider {
   return getService<LooooooooooongNameServiceProvider>("LooooooooooongNameServiceProvider")
}

export interface MultiCallbackSample {
            
   init(value: object): void;

   callListener(): void;

}

export function getMultiCallbackSample(): MultiCallbackSample {
   return getService<MultiCallbackSample>("MultiCallbackSample")
}

export interface SingletonTestServiceB {
            
   method1(str?: string): void;

   method2(str: string): string;

}

export function getSingletonTestServiceB(): SingletonTestServiceB {
   return getService<SingletonTestServiceB>("SingletonTestServiceB")
}

export interface TestModuleLoad {
            
   testModule(): void;

}

export function getTestModuleLoad(): TestModuleLoad {
   return getService<TestModuleLoad>("TestModuleLoad")
}

export interface TestServiceB {
            
   methodWithIntReturnInt(a: number): number;

   methodWithLongReturnLong(a: number): number;

   methodWithBooleanReturnBoolean(a: boolean): boolean;

   methodWithDoubleReturnDouble(a: number): number;

   methodWithStringReturnString(a: string): string;

   methodWithCallbackReturnCallback(a: Function): Function;

   methodWithCallbackReturnCallback2(a: Function, b: string, c: number): Function;

   methodWithCallbackReturnCallback3(a: Function, b: Function): string;

   methodWithArrayStringReturnArrayString(a: Array<string>): Array<string>;

   methodWithMapReturnMap(a: object): object;

   methodWithUnit(): void;

   methodWithJSValueReturnJSValue(a: object): object;

   methodWithArrayBufferReturnArrayBuffer(buffer: ArrayBuffer): ArrayBuffer;

   method3Params(a: string, b: number, c: object): object;

   methodWithMultiTypeMap(map: object): object;

   methodWithDefaultValueInSubType(a?: number, b?: string): void;

   methodWithDefaultValue(a?: number, b?: string): void;

   testJSValueRef(jsValue: object): void;

}

export function getTestServiceB(): TestServiceB {
   return getService<TestServiceB>("TestServiceB")
}

