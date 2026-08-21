cmake_minimum_required(VERSION 3.16)
# Prefer g++ so the host test does not pick clang against a missing libstdc++.
if(NOT CMAKE_CXX_COMPILER)
    find_program(KNOI_HOST_GXX g++)
    if(KNOI_HOST_GXX)
        set(CMAKE_CXX_COMPILER "${KNOI_HOST_GXX}" CACHE FILEPATH "C++ compiler" FORCE)
    endif()
endif()
project(knoi_tsfn_registry_test LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_executable(knoi_tsfn_registry_test
        thread_safe_function_registry_test.cpp
        ../src/thread_safe_function_registry.cpp
)

target_include_directories(knoi_tsfn_registry_test PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/../include
)
