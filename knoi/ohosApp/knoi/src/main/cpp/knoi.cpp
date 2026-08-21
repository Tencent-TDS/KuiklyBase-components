/*
 * Copyright (C) 2022 Huawei Device Co., Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include "async_invoker.h"
#include "napi/native_api.h"
#include "js_native_api.h"
#include <atomic>
#include <cstdlib>
#include <dlfcn.h>
#include <pthread.h>

void (*initEnvFuncPtr)(napi_env, napi_value, bool) = nullptr;
void (*initBridgeFuncPtr)() = nullptr;
void (*destroyEnvFuncPtr)() = nullptr;
char *name = nullptr;
bool debug = false;
std::atomic<bool> hasInitBridge(false);

pthread_mutex_t lock;

static napi_value setup(napi_env env, napi_callback_info info) {
    size_t size = 2;
    size_t argc = (size_t)size;
    napi_value *args = (napi_value *)malloc((size) * sizeof(napi_value));
    napi_get_cb_info(env, info, &argc, args, NULL, NULL);
    pthread_mutex_lock(&lock);
    if (name == nullptr) {
        size_t length = 0;
        napi_get_value_string_utf8(env, args[0], NULL, 0, &length);
        name = (char *)malloc((length + 1) * sizeof(char));
        napi_get_value_string_utf8(env, args[0], name, length + 1, &length);
        napi_get_value_bool(env, args[1], &debug);
        auto handle = dlopen(name, RTLD_LAZY);
        initEnvFuncPtr =
            reinterpret_cast<void (*)(napi_env, napi_value, bool)>(dlsym(handle, "com_tencent_tmm_knoi_initEnv"));
        initBridgeFuncPtr = reinterpret_cast<void (*)()>(dlsym(handle, "com_tencent_tmm_knoi_initBridge"));
        destroyEnvFuncPtr = reinterpret_cast<void (*)()>(dlsym(handle, "com_tencent_tmm_knoi_destroyEnv"));
        if (initBridgeFuncPtr == nullptr) {
            napi_throw_error(env, NULL, "can not found com_tencent_tmm_knoi_initBridge symbol, please check it.");
            return nullptr;
        }
    }
    pthread_mutex_unlock(&lock);
    return nullptr;
}

static napi_value init(napi_env env, napi_callback_info info) {
    if (name == nullptr) {
        napi_throw_error(env, NULL, "please call setup first!");
        return nullptr;
    }
    // 注入 knoi 到 global
    napi_value global;
    napi_get_global(env, &global);
    napi_value knoi;
    napi_create_object(env, &knoi);
    napi_set_named_property(env, global, "knoi", knoi);
    initEnvFuncPtr(env, knoi, debug);
    bool hasInit = false;
    if (hasInitBridge.compare_exchange_strong(hasInit, true)) {
        initBridgeFuncPtr();
    }
    return nullptr;
}

static napi_value destroy(napi_env env, napi_callback_info info) {
    (void)env;
    (void)info;
    if (destroyEnvFuncPtr != nullptr) {
        destroyEnvFuncPtr();
    }
    return nullptr;
}

EXTERN_C_START
static napi_value Init(napi_env env, napi_value exports) {
    napi_property_descriptor desc[] = {
        {"setup", nullptr, setup, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"init", nullptr, init, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"destroy", nullptr, destroy, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"create_function_waiter", nullptr, create_function_waiter, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"wait_on_function_waiter", nullptr, wait_on_function_waiter, nullptr, nullptr, nullptr, napi_default, nullptr},
        {"notify_function_waiter", nullptr, notify_function_waiter, nullptr, nullptr, nullptr, napi_default, nullptr}};
    napi_define_properties(env, exports, sizeof(desc) / sizeof(desc[0]), desc);
    return exports;
}
EXTERN_C_END

static napi_module knoiModule = {
    .nm_version = 1,
    .nm_flags = 0,
    .nm_filename = nullptr,
    .nm_register_func = Init,
    .nm_modname = "knoi",
    .nm_priv = ((void *)0),
    .reserved = {0},
};

extern "C" __attribute__((constructor)) void RegisterKNOIModule(void) { napi_module_register(&knoiModule); }
