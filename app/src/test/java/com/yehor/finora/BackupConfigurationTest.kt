package com.yehor.finora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupConfigurationTest {

    private val expectedDomains = setOf(
        "root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref",
    )

    private fun parse(path: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(path)).documentElement

    private fun children(parent: Element): List<Element> {
        val out = mutableListOf<Element>()
        for (i in 0 until parent.childNodes.length) {
            (parent.childNodes.item(i) as? Element)?.let(out::add)
        }
        return out
    }

    private fun assertExcludesEverything(section: Element) {
        val kids = children(section)
        assertTrue("${section.tagName} has no children", kids.isNotEmpty())
        kids.forEach { assertEquals("only <exclude> allowed in ${section.tagName}", "exclude", it.tagName) }
        assertEquals(expectedDomains, kids.map { it.getAttribute("domain") }.toSet())
        assertEquals(expectedDomains.size, kids.size)
        kids.forEach { assertEquals(".", it.getAttribute("path")) }
    }

    @Test
    fun dataExtractionRulesExcludeEverythingInBothSections() {
        val root = parse("src/main/res/xml/data_extraction_rules.xml")
        val sections = children(root).associateBy { it.tagName }
        assertNotNull("cloud-backup missing", sections["cloud-backup"])
        assertNotNull("device-transfer missing", sections["device-transfer"])
        assertEquals(2, sections.size)
        assertExcludesEverything(sections.getValue("cloud-backup"))
        assertExcludesEverything(sections.getValue("device-transfer"))
    }

    @Test
    fun manifestUsesDataExtractionRulesAndPlaceholder() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
        assertFalse(manifest.contains("android:fullBackupContent"))
        assertTrue(manifest.contains("""android:allowBackup="${'$'}{allowBackup}""""))
        assertFalse(File("src/main/res/xml/backup_rules.xml").exists())
    }

    @Test
    fun gradleDefaultForAllowBackupPlaceholderIsFalse() {
        val gradle = File("build.gradle.kts").readText()
        val line = gradle.lines().firstOrNull { it.contains("manifestPlaceholders[\"allowBackup\"]") }
        assertNotNull("placeholder assignment missing", line)
        val idx = gradle.indexOf("manifestPlaceholders[\"allowBackup\"]")
        val expr = gradle.substring(idx).lineSequence().take(2).joinToString(" ")
        assertTrue(expr, expr.contains("""if (project.hasProperty("backupVerification")) "true" else "false""""))
        assertNull(Regex("""else\s+"true"""").find(expr))
    }
}
