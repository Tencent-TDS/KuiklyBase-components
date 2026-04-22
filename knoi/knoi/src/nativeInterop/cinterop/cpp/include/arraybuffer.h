
#ifndef NAPI_BRIDGE_ARRAYBUFFER_H
#define NAPI_BRIDGE_ARRAYBUFFER_H

#include <js_native_api.h>

#ifdef __cplusplus
extern "C" {
#endif

bool isArrayBuffer(napi_env env, napi_value value);

bool isTypedArray(napi_env env, napi_value value);

size_t getArrayBufferLength(napi_env env, napi_value value);

uint8_t *getArrayBufferValue(napi_env env, napi_value value);

uint8_t *getTypeArrayValue(napi_env env, napi_value value);

size_t getTypeArrayLength(napi_env env, napi_value value);

napi_typedarray_type getTypeArrayType(napi_env env, napi_value value);

napi_value createExternalArrayBuffer(napi_env env, uint8_t *inputBuffer, long length);

napi_value createExternalTypedArray(napi_env env, uint8_t *data, long count, napi_typedarray_type type);

napi_value createArrayBuffer(napi_env env, uint8_t *inputBuffer, long length);

napi_value createTypedArray(napi_env env, uint8_t *data, long count, napi_typedarray_type type);

int getTypedArrayItemSize(napi_typedarray_type typed);

#ifdef __cplusplus
}
#endif
#endif //NAPI_BRIDGE_ARRAYBUFFER_H
