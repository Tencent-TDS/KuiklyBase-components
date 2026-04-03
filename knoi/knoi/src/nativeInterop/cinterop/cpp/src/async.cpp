#include "async.h"

napi_deferred createPromise(napi_env env, napi_value *promise) {
    napi_deferred deferred = nullptr;
    napi_create_promise(env, &deferred, promise);
    return deferred;
}

napi_async_work createAsyncWork(
    napi_env env,
    const char *workName,
    void *execute,
    void *complete,
    void *data
) {
    napi_value workNameValue = nullptr;
    napi_create_string_utf8(env, workName, NAPI_AUTO_LENGTH, &workNameValue);
    napi_async_work work = nullptr;
    napi_create_async_work(
        env,
        nullptr,
        workNameValue,
        reinterpret_cast<napi_async_execute_callback>(execute),
        reinterpret_cast<napi_async_complete_callback>(complete),
        data,
        &work
    );
    return work;
}

void queueAsyncWork(napi_env env, napi_async_work work) {
    napi_queue_async_work(env, work);
}

void deleteAsyncWork(napi_env env, napi_async_work work) {
    napi_delete_async_work(env, work);
}

void resolveDeferred(napi_env env, napi_deferred deferred, napi_value value) {
    napi_resolve_deferred(env, deferred, value);
}

void rejectDeferred(napi_env env, napi_deferred deferred, napi_value value) {
    napi_reject_deferred(env, deferred, value);
}

napi_value getAndClearLastException(napi_env env) {
    napi_value exception = nullptr;
    napi_get_and_clear_last_exception(env, &exception);
    return exception;
}

bool isExceptionPending(napi_env env) {
    bool pending = false;
    napi_is_exception_pending(env, &pending);
    return pending;
}

napi_value createError(napi_env env, const char *message) {
    napi_value errorMessage = nullptr;
    napi_value errorObject = nullptr;
    napi_create_string_utf8(env, message, NAPI_AUTO_LENGTH, &errorMessage);
    napi_create_error(env, nullptr, errorMessage, &errorObject);
    return errorObject;
}

napi_value createErrorWithCode(napi_env env, const char *code, const char *message) {
    napi_value errorMessage = nullptr;
    napi_value errorCode = nullptr;
    napi_value errorObject = nullptr;
    napi_create_string_utf8(env, message, NAPI_AUTO_LENGTH, &errorMessage);
    napi_create_string_utf8(env, code, NAPI_AUTO_LENGTH, &errorCode);
    napi_create_error(env, errorCode, errorMessage, &errorObject);
    return errorObject;
}

napi_value getNull(napi_env env) {
    napi_value value = nullptr;
    napi_get_null(env, &value);
    return value;
}
