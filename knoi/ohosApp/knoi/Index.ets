export {
  initEnvironment as init,
  invoke,
  invokeRetPromise,
  bind,
  unBind,
  registerServiceProvider,
  registerSingleServiceProvider,
  getDeclare,
  callService } from './src/main/ets/knoi';

export { setup } from "libknoi.so"

export { getService } from "./src/main/ets/ServiceProxy"

export { executeSync } from "./src/main/ets/SyncFunctionInvoker"
