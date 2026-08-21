// Copyright 2025 Tencent Inc. All rights reserved.
//
// Author: qizhengchen@tencent.com
//
// napi Function C Api
//
#include "function.h"
#include "thread_safe_function_registry.h"
#include <unistd.h>
#include <future>
#include "hilog/log.h"

typedef void *(*callJSFunction)(void *data);

// 同步调用最大超时时间
constexpr int kThreadSafeFunctionMaxTimeoutSeconds = 10;

struct CallbackData {
    void *data;
    void *callback;
    std::promise<void *> result;
    bool sync;
};

napi_value
callFunction(napi_env env, napi_value recv, napi_value func, int size, const napi_value *argv,
             napi_value *exceptionObj) {
    napi_value result;
    auto status = napi_call_function(env, recv, func, size, argv, &result);
    if (exceptionObj != nullptr && status == napi_pending_exception) {
        napi_get_and_clear_last_exception(env, exceptionObj);
    }
    return result;
}

napi_value createFunction(napi_env env, const char *name, napi_callback callback, void *release) {
    napi_value result;
    napi_create_function(env, name, NAPI_AUTO_LENGTH, callback, release, &result);
    return result;
}

napi_threadsafe_function
createThreadSafeFunctionWithCallback(napi_env env, const char *workName, void *callback) {
    napi_value workNameNapiValue = 0;
    napi_create_string_utf8(env, workName, NAPI_AUTO_LENGTH, &workNameNapiValue);
    napi_threadsafe_function tsfn;
    napi_create_threadsafe_function(env, 0, NULL, workNameNapiValue, 0, 1, NULL, NULL, NULL,
                                    (napi_threadsafe_function_call_js) callback, &tsfn);
    return tsfn;
}

void callThreadSafeFunctionWithData(napi_threadsafe_function tsfn, void *data) {
    napi_acquire_threadsafe_function(tsfn);
    napi_call_threadsafe_function(tsfn, data, napi_tsfn_nonblocking);
}

static void callJS(napi_env env, napi_value noUsed, void *context, void *data) {
    auto *callbackData = reinterpret_cast<CallbackData *>(data);
    napi_handle_scope scope;
    napi_open_handle_scope(env, &scope);
    auto callback = (callJSFunction) callbackData->callback;
    auto result = callback(callbackData->data);
    if (callbackData->sync) {
        callbackData->result.set_value(result);
    }
    napi_close_handle_scope(env, scope);
}

napi_threadsafe_function
createThreadSafeFunctionWithSync(napi_env env, const char *workName) {
    napi_value workNameNapiValue = nullptr;
    napi_create_string_utf8(env, workName, NAPI_AUTO_LENGTH, &workNameNapiValue);
    napi_threadsafe_function tsfn;
    napi_create_threadsafe_function(env, 0, NULL, workNameNapiValue, 0, 1, NULL, NULL, NULL,
                                    (napi_threadsafe_function_call_js) callJS, &tsfn);
    return tsfn;
}

static napi_status
submitThreadSafeFunction(napi_threadsafe_function tsfn, CallbackData *callbackData, int tsfnOriginTid) {
    napi_status acquireStatus = napi_acquire_threadsafe_function(tsfn);
    if (acquireStatus != napi_ok) {
        return acquireStatus;
    }
    auto mainTid = getpid();
    napi_status status;
    if (tsfnOriginTid == mainTid) {
        status = napi_call_threadsafe_function_with_priority(tsfn, callbackData, napi_priority_high, true);
    } else {
        status = napi_call_threadsafe_function(tsfn, reinterpret_cast<void *>(callbackData), napi_tsfn_blocking);
    }
    if (status != napi_ok) {
        napi_release_threadsafe_function(tsfn, napi_tsfn_release);
    }
    return status;
}

static void *waitThreadSafeFunctionResult(CallbackData *callbackData, int tsfnOriginTid) {
    auto future = callbackData->result.get_future();
#ifdef DEBUG
    auto mainTid = getpid();
    if (tsfnOriginTid == mainTid) {
        return future.get();
    } else {
        auto state = future.wait_for(std::chrono::seconds(kThreadSafeFunctionMaxTimeoutSeconds));
        if (state == std::future_status::ready) {
            return future.get();
        } else {
            OH_LOG_Print(LogType::LOG_APP, LOG_INFO, 1u, "knoi", "thread safe function timeout.");
            abort();
        }
    }
#else
    (void) tsfnOriginTid;
    return future.get();
#endif
}

void *
callThreadSafeFunction(napi_threadsafe_function tsfn, void *callback, void *data, bool sync, int tsfnOriginTid) {
    auto *callbackData = new CallbackData{
            .data = data,
            .callback = callback,
            .result = {},
            .sync = sync
    };
    napi_status status = submitThreadSafeFunction(tsfn, callbackData, tsfnOriginTid);
    if (status != napi_ok) {
        delete callbackData;
        return nullptr;
    }
    if (sync) {
        return waitThreadSafeFunctionResult(callbackData, tsfnOriginTid);
    }
    return nullptr;
}

void releaseThreadSafeFunction(napi_threadsafe_function tsfn) {
    if (tsfn == nullptr) {
        return;
    }
    napi_release_threadsafe_function(tsfn, napi_tsfn_abort);
}

static knoi_tsfn_t DefaultCreateTsfn(knoi_env_t env, const char *workName) {
    return createThreadSafeFunctionWithSync(static_cast<napi_env>(env), workName);
}

static void DefaultReleaseTsfn(knoi_tsfn_t tsfn, int abort) {
    if (tsfn == nullptr) {
        return;
    }
    napi_release_threadsafe_function(
            static_cast<napi_threadsafe_function>(tsfn),
            abort ? napi_tsfn_abort : napi_tsfn_release);
}

static int DefaultAddEnvCleanupHook(knoi_env_t env, void (*fun)(void *), void *arg) {
    return napi_add_env_cleanup_hook(static_cast<napi_env>(env), fun, arg);
}

static int DefaultRemoveEnvCleanupHook(knoi_env_t env, void (*fun)(void *), void *arg) {
    return napi_remove_env_cleanup_hook(static_cast<napi_env>(env), fun, arg);
}

static int DefaultCallTsfn(knoi_tsfn_t tsfn, void *callback, void *data, int sync, int origin_tid,
                           void **out_result) {
    auto *callbackData = new CallbackData{
            .data = data,
            .callback = callback,
            .result = {},
            .sync = sync != 0
    };
    napi_status status = submitThreadSafeFunction(
            static_cast<napi_threadsafe_function>(tsfn), callbackData, origin_tid);
    if (status != napi_ok) {
        delete callbackData;
        return 0;
    }
    if (sync != 0) {
        void *value = waitThreadSafeFunctionResult(callbackData, origin_tid);
        if (out_result != nullptr) {
            *out_result = value;
        }
    } else if (out_result != nullptr) {
        *out_result = nullptr;
    }
    return 1;
}

static void InitTsfnRegistryOps() {
    KnoiTsfnNapiOps ops{};
    ops.create = DefaultCreateTsfn;
    ops.release = DefaultReleaseTsfn;
    ops.add_env_cleanup_hook = DefaultAddEnvCleanupHook;
    ops.remove_env_cleanup_hook = DefaultRemoveEnvCleanupHook;
    ops.call = DefaultCallTsfn;
    knoi_tsfn_set_ops(&ops);
}

__attribute__((constructor))
static void AutoInitTsfnRegistryOps() {
    InitTsfnRegistryOps();
}

void registerThreadSafeFunction(napi_env env, int tid) {
    knoi_tsfn_register(env, tid);
}

void unregisterThreadSafeFunction(int tid) {
    knoi_tsfn_unregister(tid);
}

int isThreadSafeFunctionRegistered(int tid) {
    return knoi_tsfn_is_registered(tid);
}

int tryCallThreadSafeFunction(int tid, void *callback, void *data, bool sync, int tsfnOriginTid,
                              void **outResult) {
    return knoi_tsfn_try_call(tid, callback, data, sync ? 1 : 0, tsfnOriginTid, outResult);
}

void setThreadSafeFunctionEnvDestroyedCallback(void (*cb)(int tid)) {
    knoi_tsfn_set_env_destroyed_callback(cb);
}
