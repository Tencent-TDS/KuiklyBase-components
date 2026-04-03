package com.tencent.tmm.knoi.utils

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.tencent.tmm.knoi.function.FunctionInfo
import com.tencent.tmm.knoi.function.AsyncExportFunction
import java.util.Locale

const val OPTION_CONFIG_FILE = "CONFIG_FILE"
const val OPTION_MODULE_NAME = "MODULE_NAME"
const val OPTION_IS_ANDROID_APP = "IS_ANDROID_APP"
const val OPTION_TYPESCRIPT_GEN_DIR = "TS_GEN_DIR"
const val OPTION_IGNORE_TYPE_ASSERT = "IGNORE_TYPE_ASSERT"
const val OPTION_IS_BINARIES_MODULE = "IS_BINARIES_MODULE"
const val JSVALUE_CLASS_NAME = "com.tencent.tmm.knoi.type.JSValue"
const val KNOI_MODULE_PACKAGE_NAME = "com.tencent.tmm.knoi.modules"

val arrayWithAny = Array::class.asClassName()
        .parameterizedBy(WildcardTypeName.producerOf(ANY.copy(nullable = true)))
val arrayBufferClazzName = ClassName("com.tencent.tmm.knoi.type", "ArrayBuffer")
val jsValueClazzName = ClassName("com.tencent.tmm.knoi.type", "JSValue")
val supportTypeList = listOf(
    "kotlin.Unit",
    "kotlin.Boolean",
    "kotlin.Int",
    "kotlin.String",
    "kotlin.Double",
    "kotlin.Long",
    "kotlin.Array",
    "kotlin.Function",
    "kotlin.collections.List",
    "kotlin.collections.ArrayList",
    "kotlin.collections.Map",
    "kotlin.collections.HashMap",
    JSVALUE_CLASS_NAME,
    "com.tencent.tmm.knoi.type.ArrayBuffer"
)


const val PLATFORM_KEY = "platform"
const val PLATFORM_JVM = "JVM"
const val PLATFORM_Native = "Native"
const val PLATFORM_OhosArm64 = "OhosArm64"
fun isJVM(options:Map<String,String>):Boolean{
    return options[PLATFORM_KEY] == PLATFORM_JVM
}

fun isAndroidApp(options:Map<String,String>):Boolean{
    return options[OPTION_IS_ANDROID_APP] == "true"
}
fun isOhosArm64(options:Map<String,String>):Boolean{
    return options[PLATFORM_KEY] == PLATFORM_OhosArm64
}

fun isSupportType(clazz: String): Boolean {
    return supportTypeList.contains(clazz) || clazz.startsWith("kotlin.Function")
}

fun capitalizeName(name: String) =
    name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

fun checkFunctionSupportType(functionList: List<FunctionInfo>): MutableList<String> {
    val result = mutableListOf<String>()
    functionList.forEach { function ->
        val returnType = function.returnType?.toClassName()?.canonicalName ?: return@forEach
        val isSupportType = isSupportType(returnType)
        if (!isSupportType) {
            result.add(
                "${function.packageName}#${function.functionName}\n UnSupport return Type ${returnType}, Support Type : $supportTypeList"
            )
        }
        function.parameters.forEach { param ->
            val paramType = param.type.toClassName().canonicalName
            val isParamSupportType = isSupportType(paramType)
            if (!isParamSupportType) {
                result.add("${function.packageName}#${function.functionName}\n UnSupport Param Type ${paramType}, Support Type : $supportTypeList")
            }
        }
    }
    return result
}

private val asyncBlockedTypes = setOf(
    JSVALUE_CLASS_NAME,
    "com.tencent.tmm.knoi.type.ArrayBuffer"
)

private fun containsAsyncBlockedType(type: KSType?): Boolean {
    if (type == null) {
        return false
    }
    val className = type.toClassName().canonicalName
    if (asyncBlockedTypes.contains(className)) {
        return true
    }
    return type.arguments.any { containsAsyncBlockedType(it.type?.resolve()) }
}

fun checkAsyncFunctionSupportType(functionList: List<AsyncExportFunction>): MutableList<String> {
    val result = mutableListOf<String>()
    functionList.forEach { function ->
        if (containsAsyncBlockedType(function.function.returnType)) {
            result.add(
                "${function.function.packageName}#${function.function.functionName}\n Async export does not support return type containing JSValue or ArrayBuffer in V1."
            )
        }
        function.function.parameters.forEach { param ->
            if (containsAsyncBlockedType(param.type)) {
                result.add(
                    "${function.function.packageName}#${function.function.functionName}\n Async export does not support param type ${param.type} because V1 excludes JSValue and ArrayBuffer."
                )
            }
        }
    }
    return result
}

fun <T> getAnnotationValueByKey(
    annotation: KSAnnotated, annotationName: String, key: String, fallback: T
): T {
    val annotation = annotation.annotations.find { annotation ->
        annotation.annotationType.resolve().declaration.qualifiedName?.asString()
                .equals(annotationName)
    } ?: return fallback
    var value = annotation.arguments.find { it.name?.asString().equals(key) }?.value
    if (value == null || (value is String && value.isEmpty())) {
        value = fallback
    }
    return value as T
}

/**
 * 获取真正的 KSType，解析 TypeAlias
 */
fun getRealKSType(ksTypeRef: KSTypeReference): KSType {
    val ksTypeRefDeclaration = ksTypeRef.resolve().declaration
    return if (ksTypeRefDeclaration is KSTypeAlias) {
        ksTypeRefDeclaration.type.resolve()
    } else {
        ksTypeRef.resolve()
    }
}
