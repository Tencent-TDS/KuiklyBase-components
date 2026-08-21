// Host-runnable tests for the KNOI TSFN registry.
// Covers TaskPool worker reclaim: register → env destroy / unregister →
// map cleared, napi_release invoked, later calls do not use the released pointer.

#include "thread_safe_function_registry.h"

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

namespace {

int g_failed = 0;
int g_passed = 0;
const char *g_current_test = "";

#define EXPECT_TRUE(cond)                                                                      \
    do {                                                                                       \
        if (cond) {                                                                            \
            g_passed++;                                                                        \
        } else {                                                                               \
            g_failed++;                                                                        \
            std::fprintf(stderr, "FAIL %s:%d %s: %s\n", __FILE__, __LINE__, g_current_test,    \
                         #cond);                                                               \
        }                                                                                      \
    } while (0)

#define EXPECT_EQ(a, b)                                                                        \
    do {                                                                                       \
        auto _va = (a);                                                                        \
        auto _vb = (b);                                                                        \
        if (_va == _vb) {                                                                      \
            g_passed++;                                                                        \
        } else {                                                                               \
            g_failed++;                                                                        \
            std::fprintf(stderr, "FAIL %s:%d %s: %s == %s (%p vs %p)\n", __FILE__, __LINE__,   \
                         g_current_test, #a, #b, (void *)(uintptr_t)(_va),                     \
                         (void *)(uintptr_t)(_vb));                                            \
        }                                                                                      \
    } while (0)

#define EXPECT_NE(a, b)                                                                        \
    do {                                                                                       \
        auto _va = (a);                                                                        \
        auto _vb = (b);                                                                        \
        if (_va != _vb) {                                                                      \
            g_passed++;                                                                        \
        } else {                                                                               \
            g_failed++;                                                                        \
            std::fprintf(stderr, "FAIL %s:%d %s: %s != %s\n", __FILE__, __LINE__,              \
                         g_current_test, #a, #b);                                              \
        }                                                                                      \
    } while (0)

struct HookRec {
    knoi_env_t env;
    void (*fun)(void *);
    void *arg;
};

int g_create_count = 0;
int g_release_count = 0;
int g_call_count = 0;
int g_last_abort = -1;
knoi_tsfn_t g_last_created = nullptr;
knoi_tsfn_t g_last_released = nullptr;
knoi_tsfn_t g_last_called = nullptr;
int g_next_tsfn_id = 1;
std::vector<HookRec> g_hooks;
int g_destroyed_tid = -1;

knoi_tsfn_t FakeCreate(knoi_env_t env, const char *work_name) {
    (void)env;
    EXPECT_TRUE(work_name != nullptr);
    g_create_count++;
    g_last_created = reinterpret_cast<knoi_tsfn_t>(static_cast<uintptr_t>(g_next_tsfn_id++));
    return g_last_created;
}

void FakeRelease(knoi_tsfn_t tsfn, int abort) {
    g_release_count++;
    g_last_released = tsfn;
    g_last_abort = abort;
}

int FakeAddHook(knoi_env_t env, void (*fun)(void *), void *arg) {
    g_hooks.push_back(HookRec{env, fun, arg});
    return 0;
}

int FakeRemoveHook(knoi_env_t env, void (*fun)(void *), void *arg) {
    for (auto it = g_hooks.begin(); it != g_hooks.end(); ++it) {
        if (it->env == env && it->fun == fun && it->arg == arg) {
            g_hooks.erase(it);
            return 0;
        }
    }
    return 0;
}

int FakeCall(knoi_tsfn_t tsfn, void *callback, void *data, int sync, int origin_tid,
             void **out_result) {
    (void)callback;
    (void)data;
    (void)sync;
    (void)origin_tid;
    g_call_count++;
    g_last_called = tsfn;
    if (out_result != nullptr) {
        *out_result = reinterpret_cast<void *>(0xabc);
    }
    return 1;
}

void OnDestroyed(int tid) { g_destroyed_tid = tid; }

void InstallFakeOps() {
    KnoiTsfnNapiOps ops{};
    ops.create = FakeCreate;
    ops.release = FakeRelease;
    ops.add_env_cleanup_hook = FakeAddHook;
    ops.remove_env_cleanup_hook = FakeRemoveHook;
    ops.call = FakeCall;
    knoi_tsfn_set_ops(&ops);
    knoi_tsfn_set_env_destroyed_callback(OnDestroyed);
}

void ResetAll() {
    knoi_tsfn_reset_for_test();
    g_create_count = 0;
    g_release_count = 0;
    g_call_count = 0;
    g_last_abort = -1;
    g_last_created = nullptr;
    g_last_released = nullptr;
    g_last_called = nullptr;
    g_next_tsfn_id = 1;
    g_hooks.clear();
    g_destroyed_tid = -1;
    InstallFakeOps();
}

void FireHooksForEnv(knoi_env_t env) {
    std::vector<HookRec> snapshot = g_hooks;
    for (const auto &hook : snapshot) {
        if (hook.env == env && hook.fun != nullptr) {
            hook.fun(hook.arg);
        }
    }
}

void TestRegisterThenUnregisterClearsMap() {
    g_current_test = "register_then_unregister_clears_map";
    ResetAll();
    knoi_env_t env = reinterpret_cast<knoi_env_t>(0x100);
    const int tid = 42;

    EXPECT_EQ(knoi_tsfn_register(env, tid), 1);
    EXPECT_EQ(knoi_tsfn_registered_count(), 1);
    EXPECT_EQ(knoi_tsfn_is_registered(tid), 1);
    knoi_tsfn_t tsfn = knoi_tsfn_get(tid);
    EXPECT_TRUE(tsfn != nullptr);
    EXPECT_EQ(tsfn, g_last_created);
    EXPECT_EQ(g_create_count, 1);

    EXPECT_EQ(knoi_tsfn_unregister(tid), 1);
    EXPECT_EQ(knoi_tsfn_registered_count(), 0);
    EXPECT_EQ(knoi_tsfn_is_registered(tid), 0);
    EXPECT_TRUE(knoi_tsfn_get(tid) == nullptr);
}

void TestReleaseIsInvokedOnUnregister() {
    g_current_test = "release_is_invoked_on_unregister";
    ResetAll();
    knoi_env_t env = reinterpret_cast<knoi_env_t>(0x200);
    const int tid = 7;

    knoi_tsfn_register(env, tid);
    knoi_tsfn_t tsfn = knoi_tsfn_get(tid);
    EXPECT_EQ(knoi_tsfn_unregister(tid), 1);

    EXPECT_EQ(g_release_count, 1);
    EXPECT_EQ(g_last_released, tsfn);
    EXPECT_EQ(g_last_abort, 1);
    EXPECT_EQ(g_destroyed_tid, tid);
}

void TestCallAfterUnregisterDoesNotUseReleasedPointer() {
    g_current_test = "call_after_unregister_does_not_use_released_pointer";
    ResetAll();
    knoi_env_t env = reinterpret_cast<knoi_env_t>(0x300);
    const int tid = 99;

    knoi_tsfn_register(env, tid);
    knoi_tsfn_t tsfn = knoi_tsfn_get(tid);
    void *out = reinterpret_cast<void *>(0x1);
    EXPECT_EQ(knoi_tsfn_try_call(tid, nullptr, nullptr, 0, tid, &out), 1);
    EXPECT_EQ(g_call_count, 1);
    EXPECT_EQ(g_last_called, tsfn);

    knoi_tsfn_unregister(tid);
    knoi_tsfn_t released = g_last_released;
    EXPECT_EQ(released, tsfn);

    g_call_count = 0;
    g_last_called = reinterpret_cast<knoi_tsfn_t>(0xdead);
    out = reinterpret_cast<void *>(0x1);
    EXPECT_EQ(knoi_tsfn_try_call(tid, nullptr, nullptr, 1, tid, &out), 0);
    EXPECT_EQ(g_call_count, 0);
    EXPECT_NE(g_last_called, released);
    EXPECT_TRUE(knoi_tsfn_get(tid) == nullptr);
}

void TestEnvCleanupHookUnregistersAndReleases() {
    g_current_test = "env_cleanup_hook_unregisters_and_releases";
    ResetAll();
    knoi_env_t env = reinterpret_cast<knoi_env_t>(0x400);
    const int tid = 1234;

    knoi_tsfn_register(env, tid);
    knoi_tsfn_t tsfn = knoi_tsfn_get(tid);
    EXPECT_TRUE(!g_hooks.empty());

    // Simulate TaskPool worker / napi_env teardown.
    FireHooksForEnv(env);

    EXPECT_EQ(knoi_tsfn_registered_count(), 0);
    EXPECT_EQ(knoi_tsfn_is_registered(tid), 0);
    EXPECT_EQ(g_release_count, 1);
    EXPECT_EQ(g_last_released, tsfn);
    EXPECT_EQ(g_last_abort, 1);
    EXPECT_EQ(g_destroyed_tid, tid);

    g_call_count = 0;
    g_last_called = reinterpret_cast<knoi_tsfn_t>(0xdead);
    EXPECT_EQ(knoi_tsfn_try_call(tid, nullptr, nullptr, 0, tid, nullptr), 0);
    EXPECT_EQ(g_call_count, 0);
    EXPECT_NE(g_last_called, tsfn);
}

void TestDoubleUnregisterIsSafe() {
    g_current_test = "double_unregister_is_safe";
    ResetAll();
    knoi_env_t env = reinterpret_cast<knoi_env_t>(0x500);
    const int tid = 5;
    knoi_tsfn_register(env, tid);
    EXPECT_EQ(knoi_tsfn_unregister(tid), 1);
    EXPECT_EQ(g_release_count, 1);
    EXPECT_EQ(knoi_tsfn_unregister(tid), 0);
    EXPECT_EQ(g_release_count, 1);
}

void TestReregisterAfterUnregisterCreatesNewTsfn() {
    g_current_test = "reregister_after_unregister_creates_new_tsfn";
    ResetAll();
    knoi_env_t env = reinterpret_cast<knoi_env_t>(0x600);
    const int tid = 8;
    knoi_tsfn_register(env, tid);
    knoi_tsfn_t first = knoi_tsfn_get(tid);
    knoi_tsfn_unregister(tid);
    knoi_tsfn_register(env, tid);
    knoi_tsfn_t second = knoi_tsfn_get(tid);
    EXPECT_TRUE(second != nullptr);
    EXPECT_NE(first, second);
    EXPECT_EQ(g_create_count, 2);
    knoi_tsfn_unregister(tid);
}

void TestRegisterSameTidIsIdempotent() {
    g_current_test = "register_same_tid_is_idempotent";
    ResetAll();
    knoi_env_t env = reinterpret_cast<knoi_env_t>(0x700);
    const int tid = 11;
    knoi_tsfn_register(env, tid);
    knoi_tsfn_t first = knoi_tsfn_get(tid);
    knoi_tsfn_register(env, tid);
    EXPECT_EQ(g_create_count, 1);
    EXPECT_EQ(knoi_tsfn_get(tid), first);
    EXPECT_EQ(knoi_tsfn_registered_count(), 1);
    knoi_tsfn_unregister(tid);
}

void TestNullEnvDoesNotRegister() {
    g_current_test = "null_env_does_not_register";
    ResetAll();
    EXPECT_EQ(knoi_tsfn_register(nullptr, 1), 0);
    EXPECT_EQ(knoi_tsfn_registered_count(), 0);
    EXPECT_EQ(g_create_count, 0);
}

void TestWorkerAndMainTidsAreIndependent() {
    g_current_test = "worker_and_main_tids_are_independent";
    ResetAll();
    knoi_env_t main_env = reinterpret_cast<knoi_env_t>(0x800);
    knoi_env_t worker_env = reinterpret_cast<knoi_env_t>(0x801);
    knoi_tsfn_register(main_env, 1);
    knoi_tsfn_register(worker_env, 2);
    knoi_tsfn_t main_tsfn = knoi_tsfn_get(1);
    EXPECT_EQ(knoi_tsfn_registered_count(), 2);

    FireHooksForEnv(worker_env);

    EXPECT_EQ(knoi_tsfn_is_registered(2), 0);
    EXPECT_EQ(knoi_tsfn_is_registered(1), 1);
    EXPECT_EQ(knoi_tsfn_get(1), main_tsfn);
    EXPECT_EQ(g_last_released != main_tsfn ? 1 : 0, 1);
    knoi_tsfn_unregister(1);
}

} // namespace

int main() {
    TestRegisterThenUnregisterClearsMap();
    TestReleaseIsInvokedOnUnregister();
    TestCallAfterUnregisterDoesNotUseReleasedPointer();
    TestEnvCleanupHookUnregistersAndReleases();
    TestDoubleUnregisterIsSafe();
    TestReregisterAfterUnregisterCreatesNewTsfn();
    TestRegisterSameTidIsIdempotent();
    TestNullEnvDoesNotRegister();
    TestWorkerAndMainTidsAreIndependent();

    std::printf("knoi_tsfn_registry_test: %d passed, %d failed\n", g_passed, g_failed);
    return g_failed == 0 ? 0 : 1;
}
