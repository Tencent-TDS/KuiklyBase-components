#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD="${DIR}/build-host"
CXX_COMPILER="${CXX:-}"
if [[ -z "${CXX_COMPILER}" && -x "$(command -v g++)" ]]; then
  CXX_COMPILER="$(command -v g++)"
fi
if [[ -n "${CXX_COMPILER}" ]]; then
  cmake -S "${DIR}" -B "${BUILD}" -DCMAKE_CXX_COMPILER="${CXX_COMPILER}"
else
  cmake -S "${DIR}" -B "${BUILD}"
fi
cmake --build "${BUILD}"
"${BUILD}/knoi_tsfn_registry_test"
