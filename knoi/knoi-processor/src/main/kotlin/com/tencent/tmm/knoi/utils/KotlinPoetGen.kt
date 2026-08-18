package com.tencent.tmm.knoi.utils

import com.squareup.kotlinpoet.CodeBlock

/**
 * KotlinPoet wraps emitted code at column 100 by default.
 *
 * Embedding a service name directly in a format-string literal such as
 * `throw IllegalArgumentException("Name#$method not found.")` makes that wrap
 * split the quotes. Emitted as a class method, a 37-character name stays on
 * one line and compiles; a 38-character name wraps inside the quotes and
 * fails with `Expecting '"'` / `Expecting ')'` (issue #24).
 *
 * Passing the name through `%S` lets KotlinPoet quote (and wrap) it as a real
 * string instead of raw source text.
 */
fun unknownServiceMethodException(serviceName: String): CodeBlock {
    return CodeBlock.of(
        "throw IllegalArgumentException(%S + method + %S)",
        "$serviceName#",
        " not found."
    )
}

fun quotedString(value: String): CodeBlock = CodeBlock.of("%S", value)
