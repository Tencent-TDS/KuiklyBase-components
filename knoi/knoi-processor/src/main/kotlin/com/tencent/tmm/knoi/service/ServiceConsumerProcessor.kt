package com.tencent.tmm.knoi.service

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import com.tencent.tmm.knoi.annotation.ServiceConsumer
import com.tencent.tmm.knoi.function.FunctionInfo
import com.tencent.tmm.knoi.function.Param
import com.tencent.tmm.knoi.function.genProxyFunctionSpec
import com.tencent.tmm.knoi.utils.OPTION_MODULE_NAME
import com.tencent.tmm.knoi.utils.arrayBufferClazzName
import com.tencent.tmm.knoi.utils.capitalizeName
import com.tencent.tmm.knoi.utils.checkFunctionSupportType
import com.tencent.tmm.knoi.utils.isOhosArm64
import com.tencent.tmm.knoi.utils.jsValueClazzName
import com.tencent.tmm.knoi.utils.quotedString

fun getJSValueConvertFunc(typeName: TypeName): String {
    return when (typeName) {
        String::class.asTypeName() -> {
            "toKString()${if (typeName.isNullable) "" else "!!"}"
        }

        Int::class.asTypeName() -> "toInt()"
        Double::class.asTypeName() -> "toDouble()"
        Long::class.asTypeName() -> "toLong()"
        Boolean::class.asTypeName() -> "toBoolean()"
        List::class.asTypeName() -> "toList()"
        Array::class.asTypeName() -> "toArray()"
        Map::class.asTypeName() -> "toMap()"
        arrayBufferClazzName -> "toArrayBuffer()"
        else -> "unknow type!"
    }
}

fun processServiceConsumer(
    codeGenerator: CodeGenerator, resolver: Resolver, options: Map<String, String>
): List<ServiceInfo> {
    if (!isOhosArm64(options)) {
        return emptyList()
    }
    val serviceConsumerList = parseServiceInfoList(resolver, ServiceConsumer::class)
    if (!isIgnoreAssert(options)) {
        serviceConsumerList.forEach {
            val errorList = checkFunctionSupportType(it.functionList)
            assert(errorList.isEmpty()) {
                errorList.toTypedArray().joinToString("\n")
            }
        }
    }
    val moduleName = options[OPTION_MODULE_NAME]!!
    genServiceConsumerList(codeGenerator, serviceConsumerList, moduleName)
    genServiceTypeScriptFile(serviceConsumerList, options)
//    genServiceNameToIniFile(serviceConsumerList, "knoi-consumer", options)
    return serviceConsumerList
}

fun genServiceConsumerList(
    codeGenerator: CodeGenerator, serviceConsumerList: List<ServiceInfo>, moduleName: String
) {
    if (serviceConsumerList.isEmpty()) {
        return
    }
    val packageNameToFileSpec = mutableMapOf<String, FileSpec.Builder>()

    val registerFileSpecBuilder =
        FileSpec.builder("com.tencent.tmm.knoi.modules.${moduleName}", "ServiceConsumerRegister");
    serviceConsumerList.forEach {
        registerFileSpecBuilder.addImport("com.tencent.tmm.knoi.definder", "bindServiceProxy")
        registerFileSpecBuilder.addImport(
            it.packageName, getServiceConsumerProxyName(it)
        )
        if (packageNameToFileSpec[it.packageName] == null) {
            packageNameToFileSpec[it.packageName] =
                FileSpec.builder(it.packageName, "ServiceConsumer");
        }
        val serviceConsumerFileSpecBuilder = packageNameToFileSpec[it.packageName] ?: return@forEach
        serviceConsumerFileSpecBuilder.addImport("com.tencent.tmm.knoi.definder", "callService")
        serviceConsumerFileSpecBuilder.addImport("com.tencent.tmm.knoi.definder", "getService")
        serviceConsumerFileSpecBuilder.addImport(
            "com.tencent.tmm.knoi.definder",
            "getServiceRegisterTid"
        )
        serviceConsumerFileSpecBuilder.addImport("com.tencent.tmm.knoi.type", "JSValue")
        serviceConsumerFileSpecBuilder.addType(genServiceConsumerProxyClass(it))
        serviceConsumerFileSpecBuilder.addFunction(genApiFunction(it))
        registerFileSpecBuilder.addFunction(genBindProxyFunction(it))
    }

    packageNameToFileSpec.forEach { (_, builder) ->
        builder.build().writeTo(codeGenerator = codeGenerator, aggregating = true)
    }

    registerFileSpecBuilder.build().writeTo(codeGenerator = codeGenerator, aggregating = true)
}

fun getBindProxyFunctionName(serviceName: String): String {
    return "register${capitalizeName(serviceName)}Proxy"
}

fun genBindProxyFunction(serviceInfo: ServiceInfo): FunSpec {
    val func = FunSpec.builder(getBindProxyFunctionName(serviceInfo.serviceName))
    func.addCode(
        """
        |bindServiceProxy(%L, ${
            getServiceConsumerProxyName(
                serviceInfo
            )
        }())
        |""".trimMargin(),
        quotedString(serviceInfo.serviceName)
    )
    return func.build()
}

fun genApiFunction(serviceInfo: ServiceInfo): FunSpec {
    val func = FunSpec.builder("get${capitalizeName(serviceInfo.serviceName)}Api")
    func.returns(serviceInfo.clazzName.toTypeName())
    func.addCode(
        """
        |return getService(%L)
        |""".trimMargin(),
        quotedString(serviceInfo.serviceName)
    )
    return func.build()
}

fun getServiceConsumerProxyName(serviceInfo: ServiceInfo) = serviceInfo.serviceName + "Proxy"

fun genServiceConsumerProxyClass(serviceInfo: ServiceInfo): TypeSpec {
    val proxyClassTypeSpec = TypeSpec.classBuilder(getServiceConsumerProxyName(serviceInfo))
        .addSuperinterface(serviceInfo.clazzName.toTypeName())
    serviceInfo.functionList.forEach {
        proxyClassTypeSpec.addFunction(
            genProxyFunctionSpec(
                it,
                "callService",
                quotedString(serviceInfo.serviceName).toString(),
                ::genParamWrapper,
                ::genResultWrapper,
            )
        )
    }
    return proxyClassTypeSpec.build()
}

fun genResultWrapper(serviceName: String, functionInfo: FunctionInfo, result: String): String {
    val resultType = functionInfo.returnType

    return if (resultType != null && isFunctionType(resultType) && !isFunction1WithArrayJSValue(
            resultType
        )
    ) {
        val passTypeResult =
            result.replaceFirst("callService", "callService<Function1<Array<JSValue>, JSValue>>")

        val types = mutableListOf<TypeName>()
        var subParamStr = ""
        val subParamSize = resultType.arguments.size - 1
        var subReturnType: TypeName = UNIT
        // 最后一个为 Return Type
        resultType.arguments.forEachIndexed { paramIndex, ksTypeArgument ->
            if (paramIndex >= subParamSize) {
                subReturnType = ksTypeArgument.toTypeName()
            } else {
                subParamStr += "_result_param${paramIndex}: ${ksTypeArgument.toTypeName()}, "
                types.add(ksTypeArgument.toTypeName())
            }
        }
        var paramsWithJSValueStr = ""
        for (paramIndex in 0 until subParamSize) {
            paramsWithJSValueStr += """|
                |                       JSValue.createJSValue(_result_param${paramIndex}, getServiceRegisterTid($serviceName))
            """.trimMargin()
            if (paramIndex != subParamSize - 1) {
                paramsWithJSValueStr += ","
            }
        }
        var convertTypeStr = ""
        if (subReturnType != UNIT) {
            convertTypeStr = ".${getJSValueConvertFunc(subReturnType)}"
        }
        return """|
            |   val _result = $passTypeResult
            |   return { 
            |       $subParamStr -> _result.invoke(arrayOf(${paramsWithJSValueStr}))${convertTypeStr}
            |   }
        """.trimMargin()
    } else {
        "return $result"
    }
}

fun genParamWrapper(param: Param): String {
    return if (isFunctionType(param.type) && !isFunction1WithArrayJSValue(param.type)) {
        var paramConvertStr = ""
        param.type.arguments.forEachIndexed { index, ksTypeArgument ->
            if (index != param.type.arguments.size - 1) {
                val clazzName = ksTypeArgument.type?.resolve()?.toClassName() ?: UNIT
                paramConvertStr += if (clazzName == jsValueClazzName) {
                    "_params[${index}]"
                } else {
                    "_params[${index}].${getJSValueConvertFunc(clazzName)}, "
                }
            }
        }
        return """|
        |{ _params: Array<JSValue> ->
        |    ${param.name}.invoke(${paramConvertStr})
        }
        |""".trimMargin()
    } else {
        param.name
    }
}