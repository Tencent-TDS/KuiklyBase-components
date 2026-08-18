package com.tencent.tmm.knoi.function

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import com.tencent.tmm.knoi.annotation.KNExport
import com.tencent.tmm.knoi.service.parseFunctionInfo
import com.tencent.tmm.knoi.utils.OPTION_MODULE_NAME
import com.tencent.tmm.knoi.utils.arrayWithAny
import com.tencent.tmm.knoi.utils.capitalizeName
import com.tencent.tmm.knoi.utils.checkFunctionSupportType
import com.tencent.tmm.knoi.utils.getAnnotationValueByKey
import com.tencent.tmm.knoi.utils.isOhosArm64

fun processExportFunction(
    codeGenerator: CodeGenerator, resolver: Resolver, options: Map<String, String>
): List<ExportFunction> {
    if (!isOhosArm64(options)) {
        return emptyList()
    }
    val exportFunctionList = parseExportFunctionList(resolver)
    if (exportFunctionList.isEmpty()) {
        return exportFunctionList
    }
    val errorList = checkFunctionSupportType(exportFunctionList.map { it.function }.toList())
    assert(errorList.isEmpty()) {
        errorList.toTypedArray().joinToString("\n")
    }
    val moduleName = options[OPTION_MODULE_NAME]!!
    genExportFunctionList(codeGenerator, exportFunctionList, moduleName)
    return exportFunctionList
}

fun parseExportFunctionList(resolver: Resolver): List<ExportFunction> {
    val knExport = KNExport::class.qualifiedName!!
    val symbols = resolver.getSymbolsWithAnnotation(knExport)
    val ksAnnotatedList: List<KSAnnotated> =
        symbols.filter { it is KSFunctionDeclaration && it.validate() }.toList()

    val exportFunctionList = mutableListOf<ExportFunction>()
    if (ksAnnotatedList.isEmpty()) {
        return exportFunctionList
    }
    ksAnnotatedList.forEach {
        if (it !is KSFunctionDeclaration) {
            return@forEach
        }

        val functionInfo = parseFunctionInfo(it)
        val registerName = getAnnotationValueByKey(it, knExport, "name", functionInfo.functionName)
        val exportFunction = ExportFunction(
            registerName, functionInfo
        )
        exportFunctionList.add(exportFunction)
    }
    return exportFunctionList
}

fun genExportFunctionList(
    codeGenerator: CodeGenerator,
    exportFunctionList: List<ExportFunction>,
    moduleName: String
) {
    val packageNameToFileSpec = mutableMapOf<String, FileSpec.Builder>()

    val registerFileSpecBuilder =
        FileSpec.builder("com.tencent.tmm.knoi.modules.${moduleName}", "ExportFunctionRegister");
    registerFileSpecBuilder.addImport("com.tencent.tmm.knoi.definder", "bind")
    registerFileSpecBuilder.addImport("com.tencent.tmm.knoi.type", "JSValue")
    registerFileSpecBuilder.addImport("com.tencent.tmm.knoi.type", "ArrayBuffer")
    exportFunctionList.forEach {
        registerFileSpecBuilder.addImport(
            it.function.packageName, formatFunctionWithAnyName(it.function.functionName)
        )
        if (packageNameToFileSpec[it.function.packageName] == null) {
            packageNameToFileSpec[it.function.packageName] =
                FileSpec.builder(it.function.packageName, "ExportFunction");
        }
        val expandFileSpecBuilder = packageNameToFileSpec[it.function.packageName] ?: return@forEach
        expandFileSpecBuilder.addFunction(genExportFunctionWithArrayAny(it))
        registerFileSpecBuilder.addFunction(genBindFunction(it))
    }

    packageNameToFileSpec.forEach { (_, builder) ->
        builder.build().writeTo(codeGenerator = codeGenerator, aggregating = true)
    }

    registerFileSpecBuilder.build().writeTo(codeGenerator = codeGenerator, aggregating = true)
}

fun getFunctionBindName(name: String): String {
    return "bind${capitalizeName(name)}"
}

fun formatFunctionWithAnyName(name: String): String {
    return "${name}WithAny"
}

fun formatSupportTypeClassString(type: KSType?): String {
    if (type == null) {
        return "Unit::class"
    }
    var result = type.toClassName().simpleName

    if (result.startsWith("Function1")) {
        result = "Function"
    }
    return "${result}::class"
}

fun genBindFunction(exportFunction: ExportFunction): FunSpec {
    val func = FunSpec.builder(getFunctionBindName(exportFunction.registerName))
    var paramTypeListStr = ""
    exportFunction.function.parameters.forEach {
        paramTypeListStr += ","
        paramTypeListStr += " ${formatSupportTypeClassString(it.type)}"
    }

    func.addCode(
        """
        |bind(%S, ::${formatFunctionWithAnyName(exportFunction.function.functionName)},
        |   ${formatSupportTypeClassString(exportFunction.function.returnType)}${paramTypeListStr})
        |""".trimMargin(),
        exportFunction.registerName
    )
    return func.build()
}

fun genExportFunctionWithArrayAny(exportFunction: ExportFunction): FunSpec {
    val func = FunSpec.builder(formatFunctionWithAnyName(exportFunction.function.functionName))
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
                |""".trimMargin(), *paramTypeArray
    )
    if (exportFunction.function.returnType != null) {
        func.returns(exportFunction.function.returnType.toTypeName())
    } else {
        func.returns(UNIT)
    }
    return func.build()
}