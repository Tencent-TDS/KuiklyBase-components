package com.tencent.tmm.knoi.service

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import com.tencent.tmm.knoi.annotation.ServiceProvider
import com.tencent.tmm.knoi.function.Param
import com.tencent.tmm.knoi.utils.isOhosArm64
import com.tencent.tmm.knoi.utils.OPTION_MODULE_NAME
import com.tencent.tmm.knoi.utils.capitalizeName
import com.tencent.tmm.knoi.utils.checkAsyncServiceSupportType
import com.tencent.tmm.knoi.utils.checkFunctionSupportType
import kotlin.reflect.KClass

fun processServiceProvider(
    codeGenerator: CodeGenerator, resolver: Resolver, options: Map<String, String>
): List<ServiceInfo> {
    val serviceInfoList = parseServiceInfoList(resolver, ServiceProvider::class)
    val errorList = mutableListOf<String>()
    serviceInfoList.forEach {
        errorList.addAll(checkFunctionSupportType(it.functionList))
        errorList.addAll(checkAsyncServiceSupportType(it.functionList))
    }
    serviceInfoList.forEach {
        if (!it.declaration.modifiers.contains(Modifier.OPEN) || it.declaration.modifiers.contains(
                Modifier.FINAL
            )
        ) {
            errorList.add("Serivce Provider Error: ${it.packageName}#${it.clazzName} must be open AND not final.")
        }
    }
    if (!isIgnoreAssert(options)) {
        assert(errorList.isEmpty()) {
            errorList.toTypedArray().joinToString("\n")
        }
    }

    val moduleName = options[OPTION_MODULE_NAME]!!
    genServiceProviderList(codeGenerator, serviceInfoList, moduleName, options)
    if (isOhosArm64(options)) {
        genServiceTypeScriptFile(serviceInfoList, options, true)
    }
    return serviceInfoList
}

fun getServiceProviderName(serviceInfo: ServiceInfo) = serviceInfo.serviceName + "Provider"


fun genServiceProviderList(
    codeGenerator: CodeGenerator,
    serviceInfos: List<ServiceInfo>,
    moduleName: String,
    options: Map<String, String>
) {
    if (serviceInfos.isEmpty()) {
        return
    }
    val packageNameToFileSpec = mutableMapOf<String, FileSpec.Builder>()

    val registerFileSpecBuilder =
        FileSpec.builder("com.tencent.tmm.knoi.modules.${moduleName}", "ServiceProvderRegister");
    serviceInfos.forEach {
        registerFileSpecBuilder.addImport(
            "com.tencent.tmm.knoi.definder", "registerServiceProvider"
        )
        registerFileSpecBuilder.addImport(
            it.packageName, getServiceProviderName(it)
        )
        if (packageNameToFileSpec[it.packageName] == null) {
            packageNameToFileSpec[it.packageName] =
                FileSpec.builder(it.packageName, "ServiceProvider")
        }
        val serviceProviderFileSpecBuilder = packageNameToFileSpec[it.packageName] ?: return@forEach

        serviceProviderFileSpecBuilder.addImport("com.tencent.tmm.knoi.service", "Invokable")
        serviceProviderFileSpecBuilder.addImport("kotlin.reflect", "KClass")
        if (isOhosArm64(options)) {
            serviceProviderFileSpecBuilder.addImport("com.tencent.tmm.knoi.type", "JSValue")
        }
        serviceProviderFileSpecBuilder.addType(genServiceProviderClass(it))
        registerFileSpecBuilder.addFunction(genRegisterServiceProviderFunction(it))
    }

    packageNameToFileSpec.forEach { (_, builder) ->
        builder.build().writeTo(codeGenerator = codeGenerator, aggregating = true)
    }

    registerFileSpecBuilder.build().writeTo(codeGenerator = codeGenerator, aggregating = true)
}

fun getRegisterServiceProviderFunctionName(serviceName: String): String {
    return "register${capitalizeName(serviceName)}Provider"
}

fun genRegisterServiceProviderFunction(serviceInfo: ServiceInfo): FunSpec {
    val func = FunSpec.builder(getRegisterServiceProviderFunctionName(serviceInfo.serviceName))
    var hasBindApi = false
    if (serviceInfo.bind != null && serviceInfo.bind.toTypeName() != UNIT) {
        hasBindApi = true
    }
    val typeArray = mutableListOf<TypeName>()
    if (hasBindApi) {
        typeArray.add(serviceInfo.bind!!.toTypeName())
    }
    func.addCode(
        """
        |registerServiceProvider("${serviceInfo.serviceName}",${if (hasBindApi) " %T::class," else ""} ${serviceInfo.singleton}) {
        |  return@registerServiceProvider ${getServiceProviderName(serviceInfo)}()
        |}""".trimMargin(), *(typeArray.toTypedArray())
    )
    return func.build()
}

fun genServiceProviderClass(serviceInfo: ServiceInfo): TypeSpec {
    val serviceProviderTypeSpec = TypeSpec.classBuilder(getServiceProviderName(serviceInfo))
        .superclass(serviceInfo.clazzName.toTypeName()).addSuperinterface(Invokable::class)
    serviceProviderTypeSpec.addFunction(genGetReturnTypeFuncSpec(serviceInfo))
    serviceProviderTypeSpec.addFunction(genGetParamTypeListFuncSpec(serviceInfo))
    serviceProviderTypeSpec.addFunction(genIsRetPromiseFunSpec(serviceInfo))
    serviceProviderTypeSpec.addFunction(genInvokeFuncSpec(serviceInfo))
    serviceProviderTypeSpec.addFunction(genMinParamsSizeFunSpec(serviceInfo))
    return serviceProviderTypeSpec.build()
}

fun genIsRetPromiseFunSpec(serviceInfo: ServiceInfo): FunSpec {
    val func = FunSpec.builder("isRetPromise").addModifiers(KModifier.OVERRIDE)
    func.addParameter("method", String::class)
    var totalCode = ""
    serviceInfo.functionList.forEach {
        totalCode += "|    \"${it.functionName}\" -> ${it.retPromise}\n"
    }
    func.addCode(
        """
        |return when (method) {
            $totalCode
        |    else -> {
        |        throw IllegalArgumentException("${serviceInfo.serviceName}#${'$'}method not found.")
        |    }
        |}
        |""".trimMargin()
    )
    func.returns(BOOLEAN)
    return func.build()
}

fun genMinParamsSizeFunSpec(serviceInfo: ServiceInfo): FunSpec {
    val func = FunSpec.builder("getMinParamsSize").addModifiers(KModifier.OVERRIDE)
    func.addParameter("method", String::class)

    val allTypeList = mutableListOf<TypeName>()
    val methodNameToMinParamsSize = mutableMapOf<String, Int>()
    serviceInfo.functionList.forEach {
        val hasDefaultCount = it.parameters.reversed().filter { param: Param ->
            param.hasDefault
        }.size
        if (hasDefaultCount != 0) {
            methodNameToMinParamsSize[it.functionName] = it.parameters.size - hasDefaultCount
        }
    }

    var totalCode = ""
    methodNameToMinParamsSize.forEach {
        totalCode += "|    \"${it.key}\" -> ${it.value}\n"
    }

    func.addCode(
        """
        |return when (method) {
            $totalCode
        |    else -> {
        |         getParamsTypeList(method).size
        |    }
        |}
        |""".trimMargin(), *(allTypeList.toTypedArray())
    )
    func.returns(INT)
    return func.build()
}

fun genParamsWrapper(index: Int, param: Param): Pair<String, List<TypeName>> {
    // Function1<Array<JSValue,Any>> 为默认类型，不需要转换
    if (isFunctionType(param.type) && !isFunction1WithArrayJSValue(param.type)) {
        // 其他回调需要包装为 Function1<Array<JSValue,Any>>
        val types = mutableListOf<TypeName>()
        var subParamStr = ""
        val subParamSize = param.type.arguments.size - 1
        var returnType: TypeName = UNIT
        // 最后一个为 Return Type
        param.type.arguments.forEachIndexed { paramIndex, ksTypeArgument ->
            if (paramIndex >= subParamSize) {
                returnType = ksTypeArgument.toTypeName()
            } else {
                subParamStr += "_param${paramIndex}: %T, "
                types.add(ksTypeArgument.toTypeName())
            }
        }
        var paramsWithJSValueStr = ""
        for (paramIndex in 0 until subParamSize) {
            paramsWithJSValueStr += """|
                |                       JSValue.createJSValue(_param${paramIndex})
            """.trimMargin()
            if (paramIndex != subParamSize - 1) {
                paramsWithJSValueStr += ","
            }
        }
        val paramWrapperStr = """|
            |   { 
            |       $subParamStr ->
            |           (params[${index}] as Function1<Array<JSValue>, %T>)
            |                       .invoke(arrayOf(${paramsWithJSValueStr}))
            |   }
        """.trimMargin()
        types.add(returnType)
        return Pair(paramWrapperStr, types.toList())
    } else {
        return Pair("params[${index}] as %T", listOf(param.type.toTypeName()))
    }
}

fun genInvokeFuncSpec(serviceInfo: ServiceInfo): FunSpec {
    val func = FunSpec.builder("invoke").addModifiers(KModifier.OVERRIDE)
    func.addParameter("method", String::class)
    func.addParameter(
        ParameterSpec.builder("params", ANY.copy(nullable = true), KModifier.VARARG).build()
    )
    var totalCode = ""
    val allTypeList = mutableListOf<TypeName>()

    serviceInfo.functionList.forEach {
        var paramsStr = "|    \"${it.functionName}\" -> "
        val hasDefaultParamCount = it.parameters.filter { it.hasDefault }.size
        val hasDefaultParam = hasDefaultParamCount > 0
        val fillParamFunc = { count: Int ->
            var result = ""
            for (index in 0 until count) {
                val paramWrapper = genParamsWrapper(index, it.parameters[index])
                allTypeList.addAll(paramWrapper.second)
                result += paramWrapper.first
                if (index != count - 1) {
                    result += ", "
                }
            }
            result
        }
        if (hasDefaultParam) {
            paramsStr += "\n|     {\n|          when(params.size) {\n"
            for (count in hasDefaultParamCount downTo 1) {
                paramsStr += "|             ${it.parameters.size - count} -> this.${it.functionName}(${
                    fillParamFunc.invoke(
                        it.parameters.size - count
                    )
                })\n"
            }
            paramsStr += "|             else -> this.${it.functionName}(${fillParamFunc.invoke(it.parameters.size)})\n"
            paramsStr += "|         }\n     }\n"
        } else {
            paramsStr += "this.${it.functionName}(${fillParamFunc.invoke(it.parameters.size)})\n"
        }
        totalCode += paramsStr
    }

    func.addCode(
        """
        |return when (method) {
            ${totalCode}
        |    else -> {
        |        throw IllegalArgumentException("${serviceInfo.serviceName}#${'$'}method not found.")
        |    }
        |}
        |""".trimMargin(), *(allTypeList.toTypedArray())
    )
    func.returns(ANY.copy(nullable = true))
    return func.build()
}


fun genGetParamTypeListFuncSpec(serviceInfo: ServiceInfo): FunSpec {
    val func = FunSpec.builder("getParamsTypeList").addModifiers(KModifier.OVERRIDE)
    func.addParameter("method", String::class)
    var totalCode = ""
    val allTypeList = mutableListOf<TypeName>()
    serviceInfo.functionList.forEach {
        var paramsTypeStr = "|    \"${it.functionName}\" -> arrayOf("
        it.parameters.forEachIndexed { index, param ->
            allTypeList.add(getKTTypeName(param.type))
            paramsTypeStr += "%T::class"
            if (index != it.parameters.size - 1) {
                paramsTypeStr += ","
            }
        }
        paramsTypeStr += ")\n"
        totalCode += paramsTypeStr
    }

    func.addCode(
        """
        |return when (method) {
            $totalCode
        |    else -> {
        |        throw IllegalArgumentException("${serviceInfo.serviceName}#${'$'}method not found.")
        |    }
        |}
        |""".trimMargin(), *(allTypeList.toTypedArray())
    )
    func.returns(
        Array::class.asClassName().parameterizedBy(
            KClass::class.asClassName().parameterizedBy(WildcardTypeName.producerOf(ANY))
        )
    )
    return func.build()
}


fun genGetReturnTypeFuncSpec(serviceInfo: ServiceInfo): FunSpec {
    val func = FunSpec.builder("getReturnType").addModifiers(KModifier.OVERRIDE)
    func.addParameter("method", String::class)
    var returnTypeStr = ""
    var returnTypeList = mutableListOf<TypeName>()
    serviceInfo.functionList.forEach {
        returnTypeList.add(getKTTypeName(it.returnType))
        returnTypeStr += "|    \"${it.functionName}\" -> %T::class\n"
    }

    func.addCode(
        """
        |return when (method) {
            $returnTypeStr
        |    else -> {
        |        throw IllegalArgumentException("${serviceInfo.serviceName}#${'$'}method not found.")
        |    }
        |}
        |""".trimMargin(), *(returnTypeList.toTypedArray())
    )
    func.returns(KClass::class.asClassName().parameterizedBy(WildcardTypeName.producerOf(ANY)))
    return func.build()
}
