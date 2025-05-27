package com.tencent.tmm.knoi

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.PlatformInfo
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.tencent.tmm.knoi.callback.processCallbackList
import com.tencent.tmm.knoi.declare.processDeclareList
import com.tencent.tmm.knoi.function.processExportFunction
import com.tencent.tmm.knoi.module.genModuleInitializer
import com.tencent.tmm.knoi.module.processAutoRegister
import com.tencent.tmm.knoi.service.processServiceConsumer
import com.tencent.tmm.knoi.service.processServiceProvider
import com.tencent.tmm.knoi.utils.OPTION_CONFIG_FILE
import com.tencent.tmm.knoi.utils.PLATFORM_JVM
import com.tencent.tmm.knoi.utils.PLATFORM_KEY
import com.tencent.tmm.knoi.utils.PLATFORM_Native
import com.tencent.tmm.knoi.utils.PLATFORM_OhosArm64
import java.io.File


class KnoiProcessor(val environment: SymbolProcessorEnvironment) :
    SymbolProcessor {

    val codeGenerator: CodeGenerator = environment.codeGenerator

    fun isJVMPlatform(platforms: List<PlatformInfo>): Boolean {
        return platforms.firstOrNull {
            it.platformName == PLATFORM_JVM
        } != null
    }

    fun isNativePlatform(platforms: List<PlatformInfo>): Boolean {
        return platforms.firstOrNull {
            it.platformName == PLATFORM_Native
        } != null
    }

    fun isOhosArms64Path(environment: SymbolProcessorEnvironment): Boolean {
        return isNativePlatform(environment.platforms) && run {
            val field = environment.codeGenerator::class.java.getDeclaredField("classDir")
            field.isAccessible = true
            val value = (field.get(environment.codeGenerator) as File).absoluteFile.path
            return value.contains("ohosArm64")
        }
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val options = environment.options.toMutableMap()
        if (isJVMPlatform(environment.platforms)) {
            options[PLATFORM_KEY] = PLATFORM_JVM
        } else if (isOhosArms64Path(environment)) {
            options[PLATFORM_KEY] = PLATFORM_OhosArm64
        }
        fillOptionsFromConfigFile(options)
        val functions = processExportFunction(codeGenerator, resolver, options)
        val consumers = processServiceConsumer(codeGenerator, resolver, options)
        val providers = processServiceProvider(codeGenerator, resolver, options)
        val declares = processDeclareList(codeGenerator, resolver, options)
        processCallbackList(codeGenerator, resolver, options)



        genModuleInitializer(
            codeGenerator, resolver, consumers, providers, functions, declares, options
        )
        // 无生成的文件视为最后一轮 process，生成自动注册方法
        if (codeGenerator.generatedFile.isEmpty()) {
            processAutoRegister(codeGenerator, resolver, options)
        }
        return emptyList()
    }

    private fun fillOptionsFromConfigFile(options: MutableMap<String, String>) {
        val configFilePath = options[OPTION_CONFIG_FILE] ?: return
        File(configFilePath).readText().split("\n").forEach {
            val entry = it.split("=")
            if (entry.isEmpty() || entry.size != 2) {
                return@forEach
            }
            options[entry[0]] = entry[1]
        }
    }

}

lateinit var environmentGlobal: SymbolProcessorEnvironment
fun warming(string: String) {
    environmentGlobal.logger.warn(string)
}

class KnoiProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        environmentGlobal = environment
        return KnoiProcessor(environment)
    }
}
