package com.tencent.tmm.knoi.module

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.writeTo
import com.tencent.tmm.knoi.declare.Declare
import com.tencent.tmm.knoi.declare.getDeclareBindName
import com.tencent.tmm.knoi.function.AsyncExportFunction
import com.tencent.tmm.knoi.function.getAsyncFunctionBindName
import com.tencent.tmm.knoi.function.ExportFunction
import com.tencent.tmm.knoi.function.getFunctionBindName
import com.tencent.tmm.knoi.utils.isOhosArm64
import com.tencent.tmm.knoi.service.ServiceInfo
import com.tencent.tmm.knoi.service.getBindProxyFunctionName
import com.tencent.tmm.knoi.service.getRegisterServiceProviderFunctionName
import com.tencent.tmm.knoi.utils.KNOI_MODULE_PACKAGE_NAME
import com.tencent.tmm.knoi.utils.OPTION_MODULE_NAME
import com.tencent.tmm.knoi.utils.capitalizeName
import java.util.Locale

private fun capitalizeFirstLetter(input: String): String {
    if (input.isEmpty()) {
        return input
    }
    return input.substring(0, 1).uppercase(Locale.CHINA) + input.substring(1)
}

fun genModuleInitializer(
    codeGenerator: CodeGenerator,
    resolver: Resolver,
    consumers: List<ServiceInfo>,
    providers: List<ServiceInfo>,
    functions: List<ExportFunction>,
    asyncFunctions: List<AsyncExportFunction>,
    declares: List<Declare>,
    options: Map<String, String>
): Boolean {
    if (consumers.isEmpty() && providers.isEmpty() && functions.isEmpty() && asyncFunctions.isEmpty() && declares.isEmpty()) {
        return false
    }
    val moduleName = options[OPTION_MODULE_NAME]!!
    val initializerFileSpecBuilder =
        FileSpec.builder("com.tencent.tmm.knoi.modules", "${capitalizeFirstLetter(moduleName)}ModulesInitializer")

    if (isOhosArm64(options)) {
        initializerFileSpecBuilder.addImport("kotlin.experimental", "ExperimentalNativeApi")
    }
    val initializeFunSpec = FunSpec.builder("init${capitalizeName(moduleName)}")
    initializeFunSpec.addAnnotation(
        AnnotationSpec.builder(ClassName("com.tencent.tmm.knoi.annotation", "ModuleInitializer"))
            .build()
    )
    initializeFunSpec.addCode("""
        ${
        providers.map {
            "|${KNOI_MODULE_PACKAGE_NAME}.${moduleName}.${getRegisterServiceProviderFunctionName(it.serviceName)}()"
        }.joinToString("\n")
    }
        ${
        consumers.map {
            "|${KNOI_MODULE_PACKAGE_NAME}.${moduleName}.${getBindProxyFunctionName(it.serviceName)}()"
        }.joinToString("\n")
    }
        ${
        functions.filter { it.registerName.isNotBlank() }.map {
            "|${KNOI_MODULE_PACKAGE_NAME}.${moduleName}.${getFunctionBindName(it.registerName)}()"
        }.joinToString("\n")
    }
    ${
        asyncFunctions.filter { it.registerName.isNotBlank() }.map {
            "|${KNOI_MODULE_PACKAGE_NAME}.${moduleName}.${getAsyncFunctionBindName(it.registerName)}()"
        }.joinToString("\n")
    }
    ${
        declares.filter { it.getDeclareName().isNotBlank() }.map {
            "|${KNOI_MODULE_PACKAGE_NAME}.${moduleName}.${getDeclareBindName(it.getDeclareName())}()"
        }.joinToString("\n")
    }
        |
    """.trimMargin()
    )
    initializerFileSpecBuilder.addFunction(initializeFunSpec.build())
    initializerFileSpecBuilder.build().writeTo(codeGenerator = codeGenerator, aggregating = true)
    return true
}
