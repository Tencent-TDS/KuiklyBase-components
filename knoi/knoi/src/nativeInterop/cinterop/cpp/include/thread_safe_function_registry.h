#ifndef KNOI_THREAD_SAFE_FUNCTION_REGISTRY_H
#define KNOI_THREAD_SAFE_FUNCTION_REGISTRY_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Host-testable TSFN registry. Production NAPI calls go through injected ops
 * so unit tests can spy on create / release / call without Harmony headers.
 */
typedef void *knoi_env_t;
typedef void *knoi_tsfn_t;

typedef struct KnoiTsfnNapiOps {
    knoi_tsfn_t (*create)(knoi_env_t env, const char *work_name);
    void (*release)(knoi_tsfn_t tsfn, int abort);
    int (*add_env_cleanup_hook)(knoi_env_t env, void (*fun)(void *), void *arg);
    int (*remove_env_cleanup_hook)(knoi_env_t env, void (*fun)(void *), void *arg);
    int (*call)(knoi_tsfn_t tsfn, void *callback, void *data, int sync, int origin_tid,
                void **out_result);
} KnoiTsfnNapiOps;

typedef void (*knoi_tsfn_env_destroyed_cb)(int tid);

void knoi_tsfn_set_ops(const KnoiTsfnNapiOps *ops);

void knoi_tsfn_set_env_destroyed_callback(knoi_tsfn_env_destroyed_cb cb);

int knoi_tsfn_register(knoi_env_t env, int tid);

int knoi_tsfn_unregister(int tid);

int knoi_tsfn_is_registered(int tid);

knoi_tsfn_t knoi_tsfn_get(int tid);

int knoi_tsfn_try_call(int tid, void *callback, void *data, int sync, int origin_tid,
                       void **out_result);

int knoi_tsfn_registered_count(void);

void knoi_tsfn_reset_for_test(void);

void knoi_tsfn_on_env_cleanup(void *arg);

#ifdef __cplusplus
}
#endif

#endif // KNOI_THREAD_SAFE_FUNCTION_REGISTRY_H
