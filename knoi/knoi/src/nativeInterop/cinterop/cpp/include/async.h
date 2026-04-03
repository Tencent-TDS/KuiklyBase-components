#ifndef NAPI_BRIDGE_ASYNC_H
#define NAPI_BRIDGE_ASYNC_H

#include "napi/native_api.h"
#include <js_native_api.h>

#ifdef __cplusplus
extern "C" {
#endif

napi_deferred createPromise(napi_env env, napi_value *promise);

napi_async_work createAsyncWork(
    napi_env env,
    const char *workName,
    void *execute,
    void *complete,
    void *data
);

void queueAsyncWork(napi_env env, napi_async_work work);

void deleteAsyncWork(napi_env env, napi_async_work work);

void resolveDeferred(napi_env env, napi_deferred deferred, napi_value value);

void rejectDeferred(napi_env env, napi_deferred deferred, napi_value value);

napi_value getAndClearLastException(napi_env env);

bool isExceptionPending(napi_env env);

napi_value createError(napi_env env, const char *message);

napi_value createErrorWithCode(napi_env env, const char *code, const char *message);

napi_value getNull(napi_env env);

#ifdef __cplusplus
}
#endif

#endif //NAPI_BRIDGE_ASYNC_H
