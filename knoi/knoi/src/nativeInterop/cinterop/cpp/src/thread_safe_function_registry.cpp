// Copyright 2025 Tencent Inc. All rights reserved.
//
// Per-tid napi_threadsafe_function registry. TaskPool / worker napi_env
// teardown must unregister and release; otherwise Kotlin Cleaner later
// posts to a dead uv handle and libuv aborts.

#include "thread_safe_function_registry.h"

#include <mutex>
#include <unordered_map>

struct TsfnCleanupHint {
    int tid;
};

struct TsfnEntry {
    knoi_tsfn_t tsfn = nullptr;
    knoi_env_t env = nullptr;
    TsfnCleanupHint *hint = nullptr;
};

namespace {

std::mutex g_mutex;
std::unordered_map<int, TsfnEntry> g_tid_to_tsfn;
KnoiTsfnNapiOps g_ops{};
knoi_tsfn_env_destroyed_cb g_env_destroyed_cb = nullptr;

int unregister_locked(int tid, bool from_hook, TsfnEntry *out_entry) {
    auto it = g_tid_to_tsfn.find(tid);
    if (it == g_tid_to_tsfn.end()) {
        return 0;
    }
    if (out_entry != nullptr) {
        *out_entry = it->second;
    }
    (void) from_hook;
    g_tid_to_tsfn.erase(it);
    return 1;
}

void finish_unregister(const TsfnEntry &entry, bool from_hook, int tid) {
    if (!from_hook && g_ops.remove_env_cleanup_hook != nullptr && entry.env != nullptr &&
        entry.hint != nullptr) {
        g_ops.remove_env_cleanup_hook(entry.env, knoi_tsfn_on_env_cleanup, entry.hint);
    }
    if (entry.tsfn != nullptr && g_ops.release != nullptr) {
        // Worker / env is going away: abort so leftover work is not delivered
        // onto a destroyed uv loop.
        g_ops.release(entry.tsfn, 1);
    }
    delete entry.hint;
    if (g_env_destroyed_cb != nullptr) {
        g_env_destroyed_cb(tid);
    }
}

} // namespace

void knoi_tsfn_set_ops(const KnoiTsfnNapiOps *ops) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (ops == nullptr) {
        g_ops = {};
        return;
    }
    g_ops = *ops;
}

void knoi_tsfn_set_env_destroyed_callback(knoi_tsfn_env_destroyed_cb cb) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_env_destroyed_cb = cb;
}

int knoi_tsfn_register(knoi_env_t env, int tid) {
    if (env == nullptr) {
        return 0;
    }
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (g_tid_to_tsfn.find(tid) != g_tid_to_tsfn.end()) {
            return 1;
        }
        if (g_ops.create == nullptr) {
            return 0;
        }
    }

    knoi_tsfn_t tsfn = g_ops.create(env, "tsfn-worker");
    if (tsfn == nullptr) {
        return 0;
    }
    auto *hint = new TsfnCleanupHint{tid};

    {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (g_tid_to_tsfn.find(tid) != g_tid_to_tsfn.end()) {
            if (g_ops.release != nullptr) {
                g_ops.release(tsfn, 1);
            }
            delete hint;
            return 1;
        }
        TsfnEntry entry;
        entry.tsfn = tsfn;
        entry.env = env;
        entry.hint = hint;
        g_tid_to_tsfn[tid] = entry;
    }

    if (g_ops.add_env_cleanup_hook != nullptr) {
        g_ops.add_env_cleanup_hook(env, knoi_tsfn_on_env_cleanup, hint);
    }
    return 1;
}

int knoi_tsfn_unregister(int tid) {
    TsfnEntry entry;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (!unregister_locked(tid, false, &entry)) {
            return 0;
        }
    }
    finish_unregister(entry, false, tid);
    return 1;
}

int knoi_tsfn_is_registered(int tid) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_tid_to_tsfn.find(tid) != g_tid_to_tsfn.end() ? 1 : 0;
}

knoi_tsfn_t knoi_tsfn_get(int tid) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_tid_to_tsfn.find(tid);
    if (it == g_tid_to_tsfn.end()) {
        return nullptr;
    }
    return it->second.tsfn;
}

int knoi_tsfn_try_call(int tid, void *callback, void *data, int sync, int origin_tid,
                       void **out_result) {
    knoi_tsfn_t tsfn = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        auto it = g_tid_to_tsfn.find(tid);
        if (it == g_tid_to_tsfn.end() || it->second.tsfn == nullptr || g_ops.call == nullptr) {
            return 0;
        }
        tsfn = it->second.tsfn;
    }
    return g_ops.call(tsfn, callback, data, sync, origin_tid, out_result);
}

int knoi_tsfn_registered_count(void) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return static_cast<int>(g_tid_to_tsfn.size());
}

void knoi_tsfn_reset_for_test(void) {
    std::lock_guard<std::mutex> lock(g_mutex);
    for (auto &pair : g_tid_to_tsfn) {
        delete pair.second.hint;
        pair.second.hint = nullptr;
    }
    g_tid_to_tsfn.clear();
    g_env_destroyed_cb = nullptr;
}

void knoi_tsfn_on_env_cleanup(void *arg) {
    if (arg == nullptr) {
        return;
    }
    int tid = static_cast<TsfnCleanupHint *>(arg)->tid;
    TsfnEntry entry;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (!unregister_locked(tid, true, &entry)) {
            return;
        }
    }
    finish_unregister(entry, true, tid);
}
