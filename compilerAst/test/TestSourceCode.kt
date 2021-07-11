package prog8tests

import org.junit.jupiter.api.*
import kotlin.test.*
import prog8tests.helpers.*
import kotlin.io.path.*

import prog8.parser.SourceCode



@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestSourceCode {

    @BeforeAll
    fun setUp() {
        sanityCheckDirectories("compilerAst")
    }

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
        val path = fixturesDir.resolve(filename)
        assumeNotExists(path)
        assertFailsWith<NoSuchFileException> { SourceCode.fromPath(path) }
    }

    @Test
    fun testFromPathWithMissingExtension_p8() {
        val pathWithoutExt = fixturesDir.resolve("simple_main")
        val pathWithExt = Path(pathWithoutExt.toString() + ".p8")
        assumeReadableFile(pathWithExt)
        assertFailsWith<NoSuchFileException> { SourceCode.fromPath(pathWithoutExt) }
    }

    @Test
    fun testFromPathWithDirectory() {
        assertFailsWith<AccessDeniedException> { SourceCode.fromPath(fixturesDir) }
    }

    @Test
    fun testFromPathWithExistingPath() {
        val filename = "simple_main.p8"
        val path = fixturesDir.resolve(filename)
        val src = SourceCode.fromPath(path)

        val expectedOrigin = path.normalize().absolutePathString()
        assertEquals(expectedOrigin, src.origin)

        val expectedSrcText = path.toFile().readText()
        val actualSrcText = src.getCharStream().toString()
        assertEquals(expectedSrcText, actualSrcText)
    }

    @Test
    fun testFromPathWithExistingNonNormalizedPath() {
        val filename = "simple_main.p8"
        val path = Path(".", "test", "..", "test", "fixtures", filename)
        val src = SourceCode.fromPath(path)

        val expectedOrigin = path.normalize().absolutePathString()
        assertEquals(expectedOrigin, src.origin)

        val expectedSrcText = path.toFile().readText()
        val actualSrcText = src.getCharStream().toString()
        assertEquals(expectedSrcText, actualSrcText)
    }

    @Test
    fun testFromResourcesWithExistingP8File_withoutLeadingSlash() {
        val pathString = "prog8lib/math.p8"
        val src = SourceCode.fromResources(pathString)

        assertEquals("@embedded@/$pathString", src.origin)

        val expectedSrcText = resourcesDir.resolve(pathString).toFile().readText()
        val actualSrcText = src.asString()
        assertEquals(expectedSrcText, actualSrcText)
    }

    @Test
    fun testFromResourcesWithExistingP8File_withLeadingSlash() {
        val pathString = "/prog8lib/math.p8"
        val src = SourceCode.fromResources(pathString)

        assertEquals("@embedded@$pathString", src.origin)

        val expectedSrcText = resourcesDir.resolve(pathString.substring(1)).toFile().readText()
        val actualSrcText = src.asString()
        assertEquals(expectedSrcText, actualSrcText)
    }

    @Test
    fun testFromResourcesWithExistingAsmFile_withoutLeadingSlash() {
        val pathString = "prog8lib/math.asm"
        val src = SourceCode.fromResources(pathString)

        assertEquals("@embedded@/$pathString", src.origin)

        val expectedSrcText = resourcesDir.resolve(pathString).toFile().readText()
        val actualSrcText = src.asString()
        assertEquals(expectedSrcText, actualSrcText)
        assertTrue(src.isFromResources, ".isFromResources")
    }

    @Test
    fun testFromResourcesWithExistingAsmFile_withLeadingSlash() {
        val pathString = "/prog8lib/math.asm"
        val src = SourceCode.fromResources(pathString)

        assertEquals("@embedded@$pathString", src.origin)

        val expectedSrcText = resourcesDir.resolve(pathString.substring(1)).toFile().readText()
        val actualSrcText = src.asString()
        assertEquals(expectedSrcText, actualSrcText)
    }

    @Test
    fun testFromResourcesWithNonNormalizedPath() {
        val pathString = "/prog8lib/../prog8lib/math.p8"
        val src = SourceCode.fromResources(pathString)

        assertEquals("@embedded@/prog8lib/math.p8", src.origin)

        val expectedSrcText = resourcesDir.resolve(pathString.substring(1)).toFile().readText()
        val actualSrcText = src.asString()
        assertEquals(expectedSrcText, actualSrcText)
        assertTrue(src.isFromResources, ".isFromResources")
    }


    @Test
    fun testFromResourcesWithNonExistingFile_withLeadingSlash() {
        val pathString = "/prog8lib/i_do_not_exist"
        val resPath = resourcesDir.resolve(pathString.substring(1))
        assumeNotExists(resPath)
        assertThrows<NoSuchFileException> { SourceCode.fromResources(pathString) }
    }
    @Test
    fun testFromResourcesWithNonExistingFile_withoutLeadingSlash() {
        val pathString = "prog8lib/i_do_not_exist"
        val resPath = resourcesDir.resolve(pathString)
        assumeNotExists(resPath)
        assertThrows<NoSuchFileException> { SourceCode.fromResources(pathString) }
    }

    /**
     * TODO("inside resources: cannot tell apart a folder from a file")
     */
    //@Test
    fun testFromResourcesWithDirectory() {
        val pathString = "/prog8lib"
        assertThrows<AccessDeniedException> { SourceCode.fromResources(pathString) }
    }


}
