# 指定CMake的最小版本
CMAKE_MINIMUM_REQUIRED(VERSION 3.16)
PROJECT(napi_bridge)

add_definitions(-DOHOS_PLATFORM)

set(NAPI_BRIDGE_ROOT_PATH ${CMAKE_CURRENT_SOURCE_DIR})

set(TARGET_NAME napi_bridge)

SET(NAPI_BRIDGE_SRC ${NAPI_BRIDGE_ROOT_PATH}/src)

include_directories(${NAPI_BRIDGE_ROOT_PATH}/include)

add_library(${TARGET_NAME} STATIC
        src/cb_info.cpp
        src/async.cpp
        include/cb_info.h
        include/async.h
        src/type.cpp
        include/type.h
        include/property.h
        src/property.cpp
        src/array.cpp
        include/module.h
        src/module.cpp
        include/array.h
        include/function.h
        src/function.cpp
        include/reference.h
        src/reference.cpp
        include/object.h
        src/object.cpp
        include/arraybuffer.h
        src/arraybuffer.cpp
        src/object.cpp
        src/clazz.cpp
        include/clazz.h
        include/clazz.h
)

set_target_properties(${TARGET_NAME} PROPERTIES IMPORTED_NO_SONAME TRUE)

SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2")

SET(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,--emit-relocs --verbose")

target_link_libraries(${TARGET_NAME} PUBLIC libace_napi.z.so libhilog_ndk.z.so)
target_compile_features(${TARGET_NAME} PUBLIC cxx_std_17)
