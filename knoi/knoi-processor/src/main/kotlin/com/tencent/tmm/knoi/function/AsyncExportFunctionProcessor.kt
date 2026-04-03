package com.tencent.tmm.knoi.function

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import com.tencent.tmm.knoi.annotation.KNExportRetPromise
import com.tencent.tmm.knoi.service.parseFunctionInfo
import com.tencent.tmm.knoi.utils.OPTION_MODULE_NAME
import com.tencent.tmm.knoi.utils.arrayWithAny
import com.tencent.tmm.knoi.utils.capitalizeName
import com.tencent.tmm.knoi.utils.checkAsyncFunctionSupportType
import com.tencent.tmm.knoi.utils.getAnnotationValueByKey
import com.tencent.tmm.knoi.utils.isOhosArm64

fun processAsyncExportFunction(
    codeGenerator: CodeGenerator,
    resolver: Resolver,
    options: Map<String, String>
): List<AsyncExportFunction> {
    if (!isOhosArm64(options)) {
        return emptyList()
    }
    val exportFunctionList = parseAsyncExportFunctionList(resolver, KNExportRetPromise::class.qualifiedName!!)
    if (exportFunctionList.isEmpty()) {
        return exportFunctionList
    }
    val errorList = checkAsyncFunctionSupportType(exportFunctionList)
    assert(errorList.isEmpty()) {
        errorList.toTypedArray().joinToString("\n")
    }
    val moduleName = options[OPTION_MODULE_NAME]!!
    genAsyncExportFunctionList(codeGenerator, exportFunctionList, moduleName)
    return exportFunctionList
}

private fun parseAsyncExportFunctionList(
    resolver: Resolver,
    annotationName: String
): List<AsyncExportFunction> {
    val symbols = resolver.getSymbolsWithAnnotation(annotationName)
    val ksAnnotatedList: List<KSAnnotated> =
        symbols.filter { it is KSFunctionDeclaration && it.validate() }.toList()

    val exportFunctionList = mutableListOf<AsyncExportFunction>()
    ksAnnotatedList.forEach {
        if (it !is KSFunctionDeclaration) {
            return@forEach
        }
        val functionInfo = parseFunctionInfo(it)
        val registerName = getAnnotationValueByKey(it, annotationName, "name", functionInfo.functionName)
        exportFunctionList.add(AsyncExportFunction(registerName, functionInfo))
    }
    return exportFunctionList
}

fun getAsyncFunctionBindName(name: String): String {
    return "bindAsync" + capitalizeName(name)
}

private fun formatAsyncFunctionWithAnyName(name: String): String {
    return "${name}WithAnyAsync"
}

fun genAsyncExportFunctionList(
    codeGenerator: CodeGenerator,
    exportFunctionList: List<AsyncExportFunction>,
    moduleName: String
) {
    val packageNameToFileSpec = mutableMapOf<String, FileSpec.Builder>()
    val registerFileSpecBuilder =
        FileSpec.builder("com.tencent.tmm.knoi.modules.${moduleName}", "AsyncExportFunctionRegister")

    registerFileSpecBuilder.addImport("com.tencent.tmm.knoi.definder", "bindAsync")
    exportFunctionList.forEach {
        registerFileSpecBuilder.addImport(
            it.function.packageName,
            formatAsyncFunctionWithAnyName(it.function.functionName)
        )
        if (packageNameToFileSpec[it.function.packageName] == null) {
            packageNameToFileSpec[it.function.packageName] =
                FileSpec.builder(it.function.packageName, "AsyncExportFunction")
        }
        val expandFileSpecBuilder = packageNameToFileSpec[it.function.packageName] ?: return@forEach
        expandFileSpecBuilder.addFunction(genAsyncExportFunctionWithArrayAny(it))
        registerFileSpecBuilder.addFunction(genAsyncBindFunction(it))
    }

    packageNameToFileSpec.forEach { (_, builder) ->
        builder.build().writeTo(codeGenerator = codeGenerator, aggregating = true)
    }
    registerFileSpecBuilder.build().writeTo(codeGenerator = codeGenerator, aggregating = true)
}

fun genAsyncBindFunction(exportFunction: AsyncExportFunction): FunSpec {
    val func = FunSpec.builder(getAsyncFunctionBindName(exportFunction.registerName))
    var paramTypeListStr = ""
    exportFunction.function.parameters.forEach {
        paramTypeListStr += ","
        paramTypeListStr += " ${formatSupportTypeClassString(it.type)}"
    }
    func.addCode(
        """
        |bindAsync("${exportFunction.registerName}", ::${formatAsyncFunctionWithAnyName(exportFunction.function.functionName)},
        |   ${formatSupportTypeClassString(exportFunction.function.returnType)}${paramTypeListStr})
        |""".trimMargin()
    )
    return func.build()
}

fun genAsyncExportFunctionWithArrayAny(exportFunction: AsyncExportFunction): FunSpec {
    val func = FunSpec.builder(formatAsyncFunctionWithAnyName(exportFunction.function.functionName))
    var paramStr = ""
    exportFunction.function.parameters.forEachIndexed { index, param ->
        paramStr += "args[${index}] as %T"
        if (index != exportFunction.function.parameters.size - 1) {
            paramStr += ", "
        }
    }
    val paramTypeArray = exportFunction.function.parameters.map {
        it.type.toTypeName()
    }.toTypedArray()
    func.addParameter("args", arrayWithAny).addCode(
        """
        |return ${exportFunction.function.functionName}(${paramStr})
        |""".trimMargin(),
        *paramTypeArray
    )
    if (exportFunction.function.returnType != null) {
        func.returns(exportFunction.function.returnType.toTypeName())
    } else {
        func.returns(UNIT)
    }
    return func.build()
}
