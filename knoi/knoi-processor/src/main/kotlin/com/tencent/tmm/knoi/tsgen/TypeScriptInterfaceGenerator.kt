package com.tencent.tmm.knoi.tsgen

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.ksp.toClassName
import com.tencent.tmm.knoi.convert.classNameToJsType
import com.tencent.tmm.knoi.function.FunctionInfo

const val HEADER_TIPS = """/***
*    !!!  GEN CODE DO NOT EDIT  !!!
***/
"""

fun getJSTypeByKsType(ksType: KSType?): String {
    if (ksType == null) {
        return "void"
    }
    val className = ksType.toClassName()

    val nullable = if(ksType.isMarkedNullable) " | null" else ""
    if (ksType.isFunctionType) {
        return "Function$nullable"
    }

    if (className.toString() == ARRAY.toString()) {
        val arrayElementType = ksType.arguments[0].type?.resolve()
        val test = getJSTypeByKsType(arrayElementType)
        return "Array<${test}>$nullable"
    }
    return if (classNameToJsType.containsKey(className)) {
        classNameToJsType[className]!! + nullable
    } else {
        "void"
    }
}

fun genTypeScriptInterface(name: String, functionList: List<FunctionInfo>): String {
    val genParamsFun = { function: FunctionInfo ->
        var paramsStr = ""
        function.parameters.forEachIndexed { index, param ->
            paramsStr += "${param.name}${if (param.hasDefault) "?" else ""}: ${getJSTypeByKsType(param.type)}"
            if (index != function.parameters.size - 1) {
                paramsStr += ", "
            }
        }
        paramsStr
    }
    val genReturnFun = { function: FunctionInfo ->
        val jsType = getJSTypeByKsType(function.returnType)
        if (function.retPromise) {
            "Promise<$jsType>"
        } else {
            jsType
        }
    }
    return """
        |export interface $name {
            ${
        functionList.joinToString(separator = "") {
            """
            |   ${it.functionName}(${genParamsFun(it)}): ${genReturnFun(it)};
            |"""
        }
    }
        |}
        |
        |""".trimMargin()
}
