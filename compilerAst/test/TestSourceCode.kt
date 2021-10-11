package prog8tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.parser.SourceCode
import prog8.parser.SourceCode.Companion.libraryFilePrefix
import prog8tests.helpers.*
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestSourceCode {

    @Test
    fun testFactoryMethod_Of() {
        val text = """
            main { }
        """.trimIndent()
        val src = SourceCode.of(text)
        val actualText = src.getCharStream().toString()

        assertContains(src.origin, Regex("^<String@[0-9a-f]+>$"))
        assertEquals(text, actualText)
    }

    @Test
    fun testFromPathWithNonExistingPath() {
        val filename = "i_do_not_exist.p8"
        val path = assumeNotExists(fixturesDir, filename)
        assertFailsWith<NoSuchFileException> { SourceCode.fromPath(path) }
    }

    @Test
    fun testFromPathWithMissingExtension_p8() {
        val pathWithoutExt = assumeNotExists(fixturesDir,"simple_main")
        assumeReadableFile(fixturesDir,"simple_main.p8")
        assertFailsWith<NoSuchFileException> { SourceCode.fromPath(pathWithoutExt) }
    }

    @Test
    fun testFromPathWithDirectory() {
        assertFailsWith<AccessDeniedException> { SourceCode.fromPath(fixturesDir) }
    }

    @Test
    fun testFromPathWithExistingPath() {
        val filename = "simple_main.p8"
        val path = assumeReadableFile(fixturesDir, filename)
        val src = SourceCode.fromPath(path)

        val expectedOrigin = path.normalize().absolutePathString()
        assertEquals(expectedOrigin, src.origin)
        assertEquals(path.toFile().readText(), src.asString())
    }

    @Test
    fun testFromPathWithExistingNonNormalizedPath() {
        val filename = "simple_main.p8"
        val path = Path(".", "test", "..", "test", "fixtures", filename)
        val srcFile = assumeReadableFile(path).toFile()
        val src = SourceCode.fromPath(path)

        val expectedOrigin = path.normalize().absolutePathString()
        assertEquals(expectedOrigin, src.origin)
        assertEquals(srcFile.readText(), src.asString())
    }

    @Test
    fun testFromResourcesWithExistingP8File_withoutLeadingSlash() {
        val pathString = "prog8lib/math.p8"
        val srcFile = assumeReadableFile(resourcesDir, pathString).toFile()
        val src = SourceCode.fromResources(pathString)

        assertEquals("$libraryFilePrefix/$pathString", src.origin)
        assertEquals(srcFile.readText(), src.asString())
    }

    @Test
    fun testFromResourcesWithExistingP8File_withLeadingSlash() {
        val pathString = "/prog8lib/math.p8"
        val srcFile = assumeReadableFile(resourcesDir, pathString.substring(1)).toFile()
        val src = SourceCode.fromResources(pathString)

        assertEquals("$libraryFilePrefix$pathString", src.origin)
        assertEquals(srcFile.readText(), src.asString())
    }

    @Test
    fun testFromResourcesWithExistingAsmFile_withoutLeadingSlash() {
        val pathString = "prog8lib/math.asm"
        val srcFile = assumeReadableFile(resourcesDir, pathString).toFile()
        val src = SourceCode.fromResources(pathString)

        assertEquals("$libraryFilePrefix/$pathString", src.origin)
        assertEquals(srcFile.readText(), src.asString())
        assertTrue(src.isFromResources, ".isFromResources")
    }

    @Test
    fun testFromResourcesWithExistingAsmFile_withLeadingSlash() {
        val pathString = "/prog8lib/math.asm"
        val srcFile = assumeReadableFile(resourcesDir, pathString.substring(1)).toFile()
        val src = SourceCode.fromResources(pathString)

        assertEquals("$libraryFilePrefix$pathString", src.origin)
        assertEquals(srcFile.readText(), src.asString())
    }

    @Test
    fun testFromResourcesWithNonNormalizedPath() {
        val pathString = "/prog8lib/../prog8lib/math.p8"
        val srcFile = assumeReadableFile(resourcesDir, pathString.substring(1)).toFile()
        val src = SourceCode.fromResources(pathString)

        assertEquals("$libraryFilePrefix/prog8lib/math.p8", src.origin)
        assertEquals(srcFile.readText(), src.asString())
        assertTrue(src.isFromResources, ".isFromResources")
    }


    @Test
    fun testFromResourcesWithNonExistingFile_withLeadingSlash() {
        val pathString = "/prog8lib/i_do_not_exist"
        assumeNotExists(resourcesDir, pathString.substring(1))

        assertFailsWith<NoSuchFileException> { SourceCode.fromResources(pathString) }
    }
    @Test
    fun testFromResourcesWithNonExistingFile_withoutLeadingSlash() {
        val pathString = "prog8lib/i_do_not_exist"
        assumeNotExists(resourcesDir, pathString)

        assertFailsWith<NoSuchFileException> { SourceCode.fromResources(pathString) }
    }

    @Test
    @Disabled("TODO: inside resources: cannot tell apart a folder from a file")
    fun testFromResourcesWithDirectory() {
        val pathString = "/prog8lib"
        assumeDirectory(resourcesDir, pathString.substring(1))
        assertFailsWith<AccessDeniedException> { SourceCode.fromResources(pathString) }
    }

}
