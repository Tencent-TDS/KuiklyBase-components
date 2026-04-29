package com.tencent.tmm.knoi.function

import com.google.devtools.ksp.symbol.KSType


data class ExportFunction(
    val registerName: String, val function: FunctionInfo
)

data class FunctionInfo(
    val packageName: String,
    val functionName: String,
    val parameters: List<Param>,
    val returnType: KSType?,
    val retPromise: Boolean = false
) {
    override fun toString(): String {
        return "ExportFunction(packageName='$packageName', functionName='$functionName', parameters=$parameters, returnType='$returnType')"
    }
}

data class Param(val name: String, val type: KSType, val hasDefault: Boolean, val isVararg: Boolean = false) {
    override fun toString(): String {
        return "Param(name='$name', type='$type')"
    }
}
