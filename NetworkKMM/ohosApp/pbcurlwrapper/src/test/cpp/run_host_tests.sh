#!/bin/sh
# Host compile-level tests for pbcurlwrapper. No Harmony device required.
set -eu
cd "$(dirname "$0")"
CXX="${CXX:-g++}"
OUT="${TMPDIR:-/tmp}/curl_post_options_test"
"$CXX" -std=c++11 -Wall -Werror \
  -I../../main/cpp/wrapper/include \
  curl_post_options_test.cpp -o "$OUT"
"$OUT"
