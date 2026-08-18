package com.tencent.tmm.knoi.utils

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinPoetGenTest {

    @Test
    fun issue24_legacyLiteral_37CharNameStaysOneLine() {
        val generated = writeProviderReturnType(NAME_37, legacyUnknownMethodThrow(NAME_37))
        assertFalse(hasIssue24BrokenStringWrap(generated), generated)
        assertTrue(hasBalancedQuotes(generated), generated)
        assertTrue(
            generated.lineSequence().any { it.contains("not found.") },
            generated
        )
    }

    @Test
    fun issue24_legacyLiteral_38CharNameSplitsQuotes() {
        val generated = writeProviderReturnType(NAME_38, legacyUnknownMethodThrow(NAME_38))
        assertTrue(
            hasIssue24BrokenStringWrap(generated),
            "expected KotlinPoet column-100 wrap to split the 38-char literal:\n$generated"
        )
    }

    @Test
    fun unknownServiceMethodException_keepsLongNamesAsValidKotlin() {
        listOf(NAME_37, NAME_38, NAME_80).forEach { serviceName ->
            val generated = writeProviderReturnType(
                serviceName,
                "%L",
                unknownServiceMethodException(serviceName)
            )
            assertFalse(hasIssue24BrokenStringWrap(generated), generated)
            assertTrue(hasBalancedQuotes(generated), generated)
            assertContains(generated, serviceName)
            assertContains(generated, "method")
            assertContains(generated, "not found.")
            assertContains(generated, "IllegalArgumentException")
        }
    }

    @Test
    fun quotedString_emitsEscapedKotlinLiteral() {
        assertEquals("\"TestServiceB\"", quotedString("TestServiceB").toString())
        assertEquals(
            "\"name with \\\"quotes\\\"\"",
            quotedString("name with \"quotes\"").toString()
        )
    }

    private fun writeProviderReturnType(
        serviceName: String,
        format: String,
        vararg args: Any
    ): String {
        val spec = FunSpec.builder("getReturnType")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("method", String::class)
            .returns(
                KClass::class.asClassName().parameterizedBy(WildcardTypeName.producerOf(ANY))
            )
            .addCode(
                """
                |return when (method) {
                |    else -> {
                |        $format
                |    }
                |}
                |""".trimMargin(),
                *args
            )
            .build()
        return StringBuilder().also { output ->
            FileSpec.builder("com.tencent.tmm.knoi.sample", "ServiceProvider")
                .addType(
                    TypeSpec.classBuilder(serviceName + "Provider")
                        .addFunction(spec)
                        .build()
                )
                .build()
                .writeTo(output)
        }.toString()
    }

    private fun legacyUnknownMethodThrow(serviceName: String): String {
        return "throw IllegalArgumentException(\"${serviceName}#\$method not found.\")"
    }

    companion object {
        const val NAME_37 = "A123456789012345678901234567890123456"
        const val NAME_38 = "A1234567890123456789012345678901234567"
        const val NAME_80 =
            "A1234567890123456789012345678901234567890123456789012345678901234567890123456789"

        fun hasIssue24BrokenStringWrap(generated: String): Boolean {
            return Regex("""not\s*\n\s*found\.""").containsMatchIn(generated)
        }

        fun hasBalancedQuotes(generated: String): Boolean {
            var quotes = 0
            var escaped = false
            generated.forEach { char ->
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> quotes++
                }
            }
            return quotes % 2 == 0
        }
    }
}
