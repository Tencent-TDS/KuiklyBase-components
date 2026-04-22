
#include "arraybuffer.h"
#include <cstdlib>

bool isArrayBuffer(napi_env env, napi_value value) {
    bool isArrayBuffer;
    napi_is_arraybuffer(env, value, &isArrayBuffer);
    return isArrayBuffer;
}

bool isTypedArray(napi_env env, napi_value value) {
    bool isTypedArray;
    napi_is_typedarray(env, value, &isTypedArray);
    return isTypedArray;
}

size_t getArrayBufferLength(napi_env env, napi_value value) {
    size_t length;
    void *data;
    napi_get_arraybuffer_info(env, value, &data, &length);
    return length;
}

uint8_t *getArrayBufferValue(napi_env env, napi_value value) {
    void *data;
    size_t length;
    napi_get_arraybuffer_info(env, value, &data, &length);
    return static_cast<uint8_t *>(data);
}

napi_typedarray_type getTypeArrayType(napi_env env, napi_value value) {
    napi_typedarray_type type;
    napi_value buffer;
    size_t length;
    size_t offset;
    void *data;
    napi_get_typedarray_info(env, value, &type, &length, &data, &buffer, &offset);
    return type;
}

uint8_t *getTypeArrayValue(napi_env env, napi_value value) {
    napi_typedarray_type type;
    napi_value buffer;
    size_t length;
    size_t offset;
    void *data;
    napi_get_typedarray_info(env, value, &type, &length, &data, &buffer, &offset);
    return static_cast<uint8_t *>(data);
}

size_t getTypeArrayLength(napi_env env, napi_value value) {
    napi_typedarray_type type;
    napi_value buffer;
    size_t length;
    size_t offset;
    void *data;
    napi_get_typedarray_info(env, value, &type, &length, &data, &buffer, &offset);
    return length;
}

void finalizeExternalArrayBuffer(napi_env env, void *finalizeData, void *finalizeHint) {
    if (finalizeData != nullptr) {
        free(finalizeData);
    }
}

napi_value createExternalArrayBuffer(napi_env env, uint8_t *ptr, long length) {
    napi_value arrayBuffer = nullptr;
    napi_status status = napi_create_external_arraybuffer(
        env,
        ptr,
        length,
        finalizeExternalArrayBuffer,
        nullptr,
        &arrayBuffer
    );
    if (status != napi_ok) {
        return nullptr;
    }
    return arrayBuffer;
}

napi_value createExternalTypedArray(napi_env env, uint8_t *data, long count, napi_typedarray_type typed) {
    int length = count * getTypedArrayItemSize(typed);
    napi_value arrayBuffer = createExternalArrayBuffer(env, data, length);
    if (arrayBuffer == nullptr) {
        return nullptr;
    }
    napi_value typedArray = nullptr;
    napi_status status = napi_create_typedarray(env, typed, count, arrayBuffer, 0, &typedArray);
    if (status != napi_ok) {
        return nullptr;
    }
    return typedArray;
}

napi_value createArrayBuffer(napi_env env, uint8_t *ptr, long length) {
    napi_value arrayBuffer;
    uint8_t *outputBuff = nullptr;
    napi_create_arraybuffer(env, length, reinterpret_cast<void **>(&outputBuff), &arrayBuffer);
    uint8_t *inputBytes = ptr;
    for (size_t i = 0; i < length; i++) {
        outputBuff[i] = inputBytes[i];
    }
    return arrayBuffer;
}

napi_value createTypedArray(napi_env env, uint8_t *data, long count, napi_typedarray_type typed) {
    int length = count * getTypedArrayItemSize(typed);
    napi_value arrayBuffer = createArrayBuffer(env, data, length);
    napi_value typedArray;
    napi_create_typedarray(env, typed, count, arrayBuffer, 0, &typedArray);
    return typedArray;
}

int getTypedArrayItemSize(napi_typedarray_type typed) {
    size_t size = 1;
    switch (typed) {
        case napi_int8_array:
        case napi_uint8_array:
        case napi_uint8_clamped_array:
            size = 1;
            break;
        case napi_int16_array:
        case napi_uint16_array:
            size = sizeof(uint16_t);
            break;
        case napi_int32_array:
        case napi_uint32_array:
            size = sizeof(uint32_t);
            break;
        case napi_float32_array:
            size = sizeof(float);
            break;
        case napi_float64_array:
            size = sizeof(double);
            break;
        case napi_bigint64_array:
        case napi_biguint64_array:
            size = sizeof(uint64_t);
            break;
        default:
            size = 1;
            break;
    }
    return size;
}
