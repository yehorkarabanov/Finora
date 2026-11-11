package com.yehor.finora.designsystem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesignSystemBoundaryTest {
    private fun kt(path: String) =
        File(path).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `designsystem has no feature, data or viewmodel dependencies`() {
        val files = kt("src/main/java/com/yehor/finora/designsystem")
        assertTrue(files.isNotEmpty())
        files.forEach { f ->
            val t = f.readText()
            listOf("import com.yehor.finora.ui.", "import com.yehor.finora.data.", "ViewModel").forEach { bad ->
                assertFalse("${f.name} uses $bad", t.contains(bad))
            }
        }
    }

    @Test
    fun `screens hard-code no colors`() =
        kt("src/main/java/com/yehor/finora/ui").forEach { f ->
            assertFalse("${f.name} hard-codes a color", Regex("""Color\(0x""").containsMatchIn(f.readText()))
        }

    @Test
    fun `old theme and chart packages are gone`() {
        assertFalse(File("src/main/java/com/yehor/finora/ui/theme").exists())
        assertFalse(File("src/main/java/com/yehor/finora/ui/common/charts").exists())
    }
}
