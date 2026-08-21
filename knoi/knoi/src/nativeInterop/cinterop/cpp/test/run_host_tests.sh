#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD="${DIR}/build-host"
cmake -S "${DIR}" -B "${BUILD}"
cmake --build "${BUILD}"
"${BUILD}/knoi_tsfn_registry_test"
