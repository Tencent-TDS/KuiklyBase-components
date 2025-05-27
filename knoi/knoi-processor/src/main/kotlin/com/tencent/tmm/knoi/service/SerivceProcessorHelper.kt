package com.tencent.tmm.knoi.service

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.tencent.tmm.knoi.function.FunctionInfo
import com.tencent.tmm.knoi.function.Param
import com.tencent.tmm.knoi.function.parseFunctionsList
import com.tencent.tmm.knoi.tsgen.HEADER_TIPS
import com.tencent.tmm.knoi.tsgen.genTypeScriptInterface
import com.tencent.tmm.knoi.utils.JSVALUE_CLASS_NAME
import com.tencent.tmm.knoi.utils.OPTION_IGNORE_TYPE_ASSERT
import com.tencent.tmm.knoi.utils.OPTION_TYPESCRIPT_GEN_DIR
import com.tencent.tmm.knoi.utils.getAnnotationValueByKey
import com.tencent.tmm.knoi.utils.getRealKSType
import java.io.File
import kotlin.reflect.KClass

val ignoreFunctionList = listOf(
    "equals", "toString", "hashCode", "<init>"
)

fun isIgnoreAssert(options: Map<String, String>): Boolean {
    return options.containsKey(OPTION_IGNORE_TYPE_ASSERT) && "true".equals(
        options[OPTION_IGNORE_TYPE_ASSERT], ignoreCase = true
    )
}

fun genServiceTypeScriptFile(
    list: List<ServiceInfo>, options: Map<String, String>, isProvider: Boolean = false
) {
    val genDir = options[OPTION_TYPESCRIPT_GEN_DIR]
    if (list.isEmpty() || genDir.isNullOrEmpty()) {
        return
    }
    val scriptName = if (isProvider) "provider.ets" else "consumer.d.ts"
    val fileName = genDir + File.separator + scriptName
    val file = File(fileName)
    file.delete()
    file.parentFile.mkdirs()
    var fileContent = HEADER_TIPS
    if (isProvider) {
        fileContent += "import { getService } from \"@kuiklybase/knoi\"\n\n"
    }
    list.forEach {
        fileContent += genTypeScriptInterface(it.serviceName, it.functionList)
        if (isProvider) {
            fileContent += genGetServiceFunctionTypeScript(it)
        }
    }
    file.setWritable(true)
    file.writeText(fileContent)
}

fun genGetServiceFunctionTypeScript(serviceInfo: ServiceInfo): String {
    return """
        |export function get${serviceInfo.serviceName}(): ${serviceInfo.serviceName} {
        |   return getService<${serviceInfo.serviceName}>("${serviceInfo.serviceName}")
        |}
        |
        |""".trimMargin()
}

fun parseServiceInfoList(resolver: Resolver, annotationKClass: KClass<out Any>): List<ServiceInfo> {
    val annotationName = annotationKClass.qualifiedName!!
    val symbols = resolver.getSymbolsWithAnnotation(annotationName)
    val ksAnnotatedList: List<KSAnnotated> =
        symbols.filter { it is KSClassDeclaration && it.validate() }.toList()

    val serviceInfos = mutableListOf<ServiceInfo>()
    ksAnnotatedList.forEach {
        if (it !is KSClassDeclaration) {
            return@forEach
        }
        val packageName = it.packageName.asString()
        val clazzName = it.simpleName.asString()
        val serviceName = getAnnotationValueByKey(it, annotationName, "name", clazzName)
        val isSingleton = getAnnotationValueByKey(it, annotationName, "singleton", false)
        val bind: KSType? = getAnnotationValueByKey(it, annotationName, "bind", null)

        val serviceInfo = ServiceInfo(
            serviceName, it.asStarProjectedType(), packageName, parseFunctionsList(it), it, isSingleton, bind
        )
        serviceInfos.add(serviceInfo)
    }
    return serviceInfos
}


fun parseFunctionInfo(functionDeclaration: KSFunctionDeclaration): FunctionInfo {
    val functionName = functionDeclaration.simpleName.asString()
    val parameterList = mutableListOf<Param>()
    functionDeclaration.parameters.forEachIndexed { index, parameter ->
        val parameterName = parameter.name!!.asString()
        val parameterValue = getRealKSType(parameter.type)
        var hasDefault = parameter.hasDefault
        // 存在 override 需查找父类是否有 default 暂时只查找一层
        if (!parameter.hasDefault && functionDeclaration.findOverridee() != null) {
            val overrideFunctionDeclaration = functionDeclaration.findOverridee() as KSFunctionDeclaration
            hasDefault = overrideFunctionDeclaration.parameters[index].hasDefault
        }
        parameterList.add(Param(parameterName, parameterValue, hasDefault, parameter.isVararg))
    }
    val packageName = functionDeclaration.packageName.asString()
    return FunctionInfo(
        packageName,
        functionName,
        parameterList,
        functionDeclaration.returnType?.let { getRealKSType(it) })
}

fun getKTTypeName(returnType: KSType?, toClassName: Boolean = true): TypeName {
    if (returnType == null) {
        return UNIT
    }
    //FunctionX 需要 Function::class
    if (returnType.toClassName().toString().startsWith("kotlin.Function")) {
        return if (toClassName) Function::class.asClassName() else Function::class.asTypeName()
    }
    return if (toClassName) returnType.toClassName() else returnType.toTypeName()
}

fun isFunctionType(type: KSType): Boolean {
    return type.toClassName().canonicalName.startsWith("kotlin.Function")
}

fun isFunction1WithArrayJSValue(type: KSType): Boolean {
    if (type.toClassName().canonicalName != "kotlin.Function1") {
        return false
    }
    val inputType = type.arguments[0].type?.resolve() ?: return false
    val isArray = inputType.toClassName().canonicalName == "kotlin.Array"
    if (!isArray) {
        return false
    }
    val arrayElementType = inputType.arguments[0].type?.resolve() ?: return false
    return arrayElementType.toClassName().canonicalName == JSVALUE_CLASS_NAME
}