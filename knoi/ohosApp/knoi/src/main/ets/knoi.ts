import { init, setup } from "libknoi.so"

export function invoke<R>(method: string, ...params: any[]): R {
  Environment.get().initIfNeed()
  return globalThis.knoi.invoke(method, ...params) as R
}

export function invokeRetPromise<R>(method: string, ...params: any[]): Promise<R> {
  Environment.get().initIfNeed()
  return globalThis.knoi.invokeRetPromise(method, ...params) as Promise<R>
}

export function bind(name: string, func: Function) {
  Environment.get().initIfNeed()
  return globalThis.knoi.bind(name, func)
}

export function unBind(name: string) {
  Environment.get().initIfNeed()
  return globalThis.knoi.unBind(name)
}

export function registerServiceProvider(name: string, isSingleton: Boolean, provider: Function | object) {
  Environment.get().initIfNeed()
  return globalThis.knoi.registerServiceProvider(name, isSingleton, provider)
}

export function registerSingleServiceProvider(name: string, provider: object) {
  Environment.get().initIfNeed()
  registerServiceProvider(name, true, provider)
}

export function callService<R>(name: string, proxy: object, method: String, ...params: any[]): R {
  Environment.get().initIfNeed()
  return globalThis.knoi.callService(name, proxy, method, ...params) as R
}

export function getDeclare<R>(name: string): R {
  Environment.get().initIfNeed()
  return globalThis.knoi.getDeclare(name) as R
}

export function initEnvironment() {
  Environment.get().initIfNeed()
}

class Environment {
  private static env: Environment

  constructor() {
    // 兜底设置 so name, 如已提前设置，该配置无效
    setup("libkn.so", false)
  }

  static get(): Environment {
    if (Environment.env == null) {
      Environment.env = new Environment()
    }
    return Environment.env;
  }

  initIfNeed() {
    if (globalThis.knoi == undefined) {
      init()
    }
  }
}
