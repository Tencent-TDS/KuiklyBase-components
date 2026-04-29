package com.tencent.tmm.knoi.function

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.tencent.tmm.knoi.annotation.Hidden
import com.tencent.tmm.knoi.service.ignoreFunctionList
import com.tencent.tmm.knoi.service.parseFunctionInfo

fun parseFunctionsList(
    ksClassDeclaration: KSClassDeclaration,
    trackMethodRetPromise: Boolean = false
): List<FunctionInfo> {
    val genFunctionList = ksClassDeclaration.getAllFunctions().filter { ksFunctionDeclaration ->
        !ignoreFunctionList.contains<String>(
            ksFunctionDeclaration.simpleName.asString()
        ) && ksFunctionDeclaration.annotations.find { methodAnnotation ->
            methodAnnotation.shortName.asString() == Hidden::class.simpleName
        } == null && !ksFunctionDeclaration.modifiers.contains(
            Modifier.PRIVATE
        )
    }
    val functionList = genFunctionList.map { ksFunctionDeclaration ->
        parseFunctionInfo(ksFunctionDeclaration, trackMethodRetPromise)
    }.toList()
    return functionList
}
