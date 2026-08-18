#include <string.h>
#include <malloc.h>
#include "type.h"

char* toKString(napi_env env, napi_value value) {
    size_t length = 0;
    napi_get_value_string_utf8(env, value, NULL, 0, &length);
    char* c_str = (char*)malloc((length + 1) * sizeof(char));
    napi_get_value_string_utf8(env, value, c_str, length + 1, &length);
    return c_str;
}

napi_value convertStringToNapiValue(napi_env env, const char *value) {
    napi_value result = NULL;
    napi_create_string_utf8(env, value, strlen(value), &result);
    return result;
}

int toInt(napi_env env, napi_value value) {
    // JS Number is IEEE-754 double. napi_get_value_int32 can fail to write the
    // out-param for heap numbers on ArkTS, leaving 0. Read as double then truncate.
    double number = 0.0;
    napi_status status = napi_get_value_double(env, value, &number);
    if (status == napi_ok) {
        return (int32_t) number;
    }
    int32_t result = 0;
    napi_get_value_int32(env, value, &result);
    return result;
}

napi_value convertIntToNapiValue(napi_env env, int value) {
    napi_value result = NULL;
    int32_t num = (int32_t) (value);
    napi_create_int32(env, num, &result);
    return result;
}


long toLong(napi_env env, napi_value value) {
    double number = 0.0;
    napi_status status = napi_get_value_double(env, value, &number);
    if (status == napi_ok) {
        return (int64_t) number;
    }
    int64_t result = 0;
    napi_get_value_int64(env, value, &result);
    return result;
}

napi_value convertLongToNapiValue(napi_env env, long value) {
    napi_value result = NULL;
    int64_t num = (int64_t) (value);
    napi_create_int64(env, num, &result);
    return result;
}

bool toBoolean(napi_env env, napi_value value) {
    bool result;
    napi_get_value_bool(env, value, &result);
    return result;
}

napi_value convertBooleanToNapiValue(napi_env env, bool value) {
    napi_value result = NULL;
    napi_get_boolean(env, value, &result);
    return result;
}

double toDouble(napi_env env, napi_value value) {
    double result;
    napi_get_value_double(env, value, &result);
    return result;
}

napi_value convertDoubleToNapiValue(napi_env env, double value) {
    napi_value result = NULL;
    napi_create_double(env, value, &result);
    return result;
}

napi_valuetype typeOf(napi_env env, napi_value value) {
    napi_valuetype type;
    napi_typeof(env, value, &type);
    return type;
}