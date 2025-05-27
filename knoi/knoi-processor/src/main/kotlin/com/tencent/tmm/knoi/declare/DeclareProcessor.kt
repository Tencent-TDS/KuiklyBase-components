package com.tencent.tmm.knoi.declare

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import com.tencent.tmm.knoi.annotation.KNExport
import com.tencent.tmm.knoi.tsgen.HEADER_TIPS
import com.tencent.tmm.knoi.tsgen.getJSTypeByKsType
import com.tencent.tmm.knoi.utils.OPTION_MODULE_NAME
import com.tencent.tmm.knoi.utils.OPTION_TYPESCRIPT_GEN_DIR
import com.tencent.tmm.knoi.utils.capitalizeName
import com.tencent.tmm.knoi.utils.getAnnotationValueByKey
import com.tencent.tmm.knoi.utils.isOhosArm64
import java.io.File

val supportTypeList = listOf(
    "kotlin.Boolean",
    "kotlin.Int",
    "kotlin.String",
    "kotlin.Double",
    "kotlin.Long"
)

fun processDeclareList(
    codeGenerator: CodeGenerator, resolver: Resolver, options: Map<String, String>
): List<Declare> {
    if (!isOhosArm64(options)) {
        return emptyList()
    }
    val declareList = parseDeclareList(resolver)
    if (declareList.isEmpty()) {
        return declareList
    }
    val errorList = checkSupportType(declareList)
    assert(errorList.isEmpty()) {
        errorList.toTypedArray().joinToString("\n")
    }
    val moduleName = options[OPTION_MODULE_NAME]!!
    genDeclareList(codeGenerator, declareList, moduleName)
    genDeclareTypeScriptFile(declareList, options)
    return declareList
}

fun genDeclareTypeScriptFile(
    list: List<Declare>, options: Map<String, String>
) {
    val genDir = options[OPTION_TYPESCRIPT_GEN_DIR]
    if (list.isEmpty() || genDir.isNullOrEmpty()) {
        return
    }
    val fileName = genDir + File.separator + "declare.ets"
    val file = File(fileName)
    file.delete()
    file.parentFile.mkdirs()
    var fileContent = HEADER_TIPS
    fileContent += "import { getDeclare } from \"@kuiklybase/knoi\"\n\n"
    list.forEach {
        fileContent += genGetDeclareTypeScript(it)
    }
    file.setWritable(true)
    file.writeText(fileContent)
}

fun genGetDeclareTypeScript(declare: Declare): String {
    if (declare.type != DeclareType.CONST) {
        return ""
    }
    return """
        |export const ${
        if (declare.customName.isEmpty()) declare.getShoreDeclareName()
            .replace(".", "_") else declare.customName
    } = getDeclare<${getJSTypeByKsType(declare.fieldType)}>("${declare.getDeclareName()}")
        |
        |""".trimMargin()
}

fun genDeclareList(codeGenerator: CodeGenerator, declares: List<Declare>, moduleName: String) {
    if (declares.isEmpty()) {
        return
    }
    val registerFileSpecBuilder =
        FileSpec.builder("com.tencent.tmm.knoi.modules.${moduleName}", "DeclareRegister");
    declares.forEach {
        registerFileSpecBuilder.addImport("com.tencent.tmm.knoi.definder", "registerDeclare")
        registerFileSpecBuilder.addImport(it.packageName, it.getShortImportName())
        registerFileSpecBuilder.addFunction(genRegisterDeclareFunction(it))
    }
    registerFileSpecBuilder.build().writeTo(codeGenerator = codeGenerator, aggregating = true)
}

fun getDeclareBindName(declareNameWithType: String): String {
    return "bind${capitalizeName(getDeclareNameWithType(declareNameWithType).replace(".", "_"))}"
}

fun formatDeclareFieldName(declare: Declare): String {
    return "bind${capitalizeName(declare.getDeclareName().replace(".", "_"))}"
}

fun genRegisterDeclareFunction(declare: Declare): FunSpec {
    val func = FunSpec.builder(formatDeclareFieldName(declare))
    func.addCode(
        """
        |registerDeclare("${declare.getDeclareName()}") {
        |    ${declare.getShoreDeclareName()}
        |}
        |""".trimMargin()
    )
    return func.build()
}

fun checkSupportType(declares: List<Declare>): List<String> {
    val result = mutableListOf<String>()
    declares.forEach { declare ->
        val typeName = declare.fieldType.toClassName().canonicalName
        val isSupportType = supportTypeList.contains(typeName)
        if (!isSupportType) {
            result.add(
                "${declare.getDeclareName()}\n UnSupport Type ${typeName}, Support Type : $supportTypeList"
            )
        }
    }
    return result

}

fun parseDeclareList(resolver: Resolver): List<Declare> {
    val knExport = KNExport::class.qualifiedName!!
    val symbols = resolver.getSymbolsWithAnnotation(knExport).filter {
        it is KSPropertyDeclaration && it.validate() && it.modifiers.contains(Modifier.CONST)
    }
    val declares = mutableListOf<Declare>()
    symbols.forEach {
        if (it !is KSPropertyDeclaration) {
            return@forEach
        }
        if (it.qualifiedName == null) {
            return@forEach
        }
        val customName = getAnnotationValueByKey(it, knExport, "name", "")
        val qualifier = it.qualifiedName ?: return@forEach
        declares.add(
            Declare(
                it.packageName.asString(),
                it.parentDeclaration,
                qualifier.getShortName(),
                customName,
                it.type.resolve()
            )
        )
    }
    return declares
}
