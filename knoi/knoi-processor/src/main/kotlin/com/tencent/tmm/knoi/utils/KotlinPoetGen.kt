package com.tencent.tmm.knoi.utils

import com.squareup.kotlinpoet.CodeBlock

/**
 * KotlinPoet wraps emitted code at column 100 by default.
 *
 * Embedding a service name directly in a format-string literal such as
 * `throw IllegalArgumentException("Name#$method not found.")` makes that wrap
 * split the quotes. With the indent used by [com.tencent.tmm.knoi.service.genGetReturnTypeFuncSpec],
 * a 37-character name stays at 99 columns and compiles; a 38-character name
 * hits 100 columns and fails with `Expecting '"'` / `Expecting ')'` (issue #24).
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
