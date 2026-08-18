package com.tencent.tmm.knoi.utils

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinPoetGenTest {

    @Test
    fun issue24_37CharNameStaysOnOneLineWithLegacyLiteral() {
        val generated = writeReturnTypeSpec(legacyUnknownMethodThrow(NAME_37))
        assertFalse(hasWrappedStringLiteral(generated), generated)
        assertEquals(99, legacyThrowLineLength(NAME_37))
    }

    @Test
    fun issue24_38CharNameWrapsAndBreaksQuotesWithLegacyLiteral() {
        val generated = writeReturnTypeSpec(legacyUnknownMethodThrow(NAME_38))
        assertEquals(100, legacyThrowLineLength(NAME_38))
        assertTrue(
            hasWrappedStringLiteral(generated),
            "expected KotlinPoet column-100 wrap to split the 38-char literal:\n$generated"
        )
    }

    @Test
    fun unknownServiceMethodException_keeps37And38CharNamesAsValidKotlin() {
        listOf(NAME_37, NAME_38, NAME_80).forEach { serviceName ->
            val generated = writeReturnTypeSpec("%L", unknownServiceMethodException(serviceName))
            assertFalse(hasWrappedStringLiteral(generated), generated)
            assertTrue(hasBalancedQuotes(generated), generated)
            assertContains(generated, serviceName)
            assertContains(generated, " + method + ")
            assertContains(generated, " not found.")
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

    private fun writeReturnTypeSpec(format: String, vararg args: Any): String {
        val spec = FunSpec.builder("getReturnType")
            .addParameter("method", String::class)
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
                .addFunction(spec)
                .build()
                .writeTo(output)
        }.toString()
    }

    private fun legacyUnknownMethodThrow(serviceName: String): String {
        return "throw IllegalArgumentException(\"${serviceName}#\$method not found.\")"
    }

    private fun legacyThrowLineLength(serviceName: String): Int {
        return "        throw IllegalArgumentException(\"${serviceName}#\$method not found.\")".length
    }

    private fun hasWrappedStringLiteral(generated: String): Boolean {
        return generated.contains("not found.").not() &&
            generated.contains("not") &&
            generated.contains("found.")
    }

    private fun hasBalancedQuotes(generated: String): Boolean {
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

    companion object {
        const val NAME_37 = "A123456789012345678901234567890123456"
        const val NAME_38 = "A1234567890123456789012345678901234567"
        const val NAME_80 = "A1234567890123456789012345678901234567890123456789012345678901234567890123456789"
    }
}
