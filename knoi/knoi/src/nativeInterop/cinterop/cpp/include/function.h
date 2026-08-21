#ifndef NAPI_BRIDGE_FUNCTION_H
#define NAPI_BRIDGE_FUNCTION_H

#include "napi/native_api.h"
#include "js_native_api.h"
#include "js_native_api_types.h"

#ifdef __cplusplus
extern "C" {
#endif

napi_value
callFunction(napi_env env, napi_value recv, napi_value func, int size, const napi_value *argv, napi_value* exception_str);

napi_value createFunction(napi_env env, const char *name, napi_callback callback, void *release);

void callThreadSafeFunctionWithData(napi_threadsafe_function tsfn, void *data);

napi_threadsafe_function createThreadSafeFunctionWithCallback(napi_env env, const char *workName, void *callback);

napi_threadsafe_function
createThreadSafeFunctionWithSync(napi_env env, const char *workName);

void*
callThreadSafeFunction(napi_threadsafe_function tsfn, void* callback, void* data, bool sync, int tsfnOriginTid);

void releaseThreadSafeFunction(napi_threadsafe_function tsfn);

void registerThreadSafeFunction(napi_env env, int tid);

void unregisterThreadSafeFunction(int tid);

int isThreadSafeFunctionRegistered(int tid);

int tryCallThreadSafeFunction(int tid, void *callback, void *data, bool sync, int tsfnOriginTid,
                              void **outResult);

void setThreadSafeFunctionEnvDestroyedCallback(void (*cb)(int tid));

#ifdef __cplusplus
}
#endif
#endif //NAPI_BRIDGE_FUNCTION_H
