package com.tuckercr.hark

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the rule that brand colors live only in res/values/colors.xml (and are tinted from the
 * theme in Compose). Hex literals are allowed only as the black mask fill of a tintable vector and
 * the white required by system notification icons.
 */
class BrandColorsTest {
    private val resDir = File("src/main/res")
    private val hex = Regex("#[0-9A-Fa-f]{6,8}")
    private val allowed = setOf("#FF000000", "#FFFFFF", "#FFFFFFFF")

    @Test
    fun `no hardcoded hex colors in drawables or layouts`() {
        val offenders =
            resDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "xml" && it.parentFile.name != "values" }
                .flatMap { file ->
                    hex
                        .findAll(file.readText())
                        .map { it.value }
                        .filter { it.uppercase() !in allowed }
                        .map { "${file.name}: $it" }
                }.toList()
        assertTrue("Move these into colors.xml: $offenders", offenders.isEmpty())
    }

    @Test
    fun `colors xml defines only hark palette entries`() {
        val names = Regex("<color name=\"([^\"]+)\"").findAll(File(resDir, "values/colors.xml").readText()).map { it.groupValues[1] }
        assertTrue(names.all { it.startsWith("hark_") })
    }

    @Test
    fun `no hardcoded Color literals outside the palette in Kotlin sources`() {
        val offenders =
            File("src/main/java")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { Regex("Color\\(0x|Color\\.(Red|Green|Blue|Gray|Black|White)").containsMatchIn(it.readText()) }
                .map { it.name }
                .toList()
        assertTrue("Use theme colors instead: $offenders", offenders.isEmpty())
    }
}
