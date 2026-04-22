package com.tencent.tmm.knoi.utils

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toClassName

data class AsyncTypeShape(
    val className: String,
    val arguments: List<AsyncTypeShape> = emptyList()
)

fun KSType.toAsyncTypeShape(): AsyncTypeShape {
    return AsyncTypeShape(
        className = toClassName().canonicalName,
        arguments = arguments.mapNotNull { it.type?.resolve()?.toAsyncTypeShape() }
    )
}

fun validateAsyncExportTypeShape(type: AsyncTypeShape): String? {
    if (!isSupportType(type.className)) {
        return "Async export does not support type ${type.className}, Support Type : $supportTypeList"
    }
    return null
}
