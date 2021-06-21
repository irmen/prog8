package prog8tests

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.*
import java.nio.file.Path   // TODO: use kotlin.io.path.Path instead
import kotlin.io.path.*

import prog8.parser.SourceCode


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
        val path = Path.of("test", "fixtures", filename)

        assertFalse(path.exists(), "sanity check: file should not exist: ${path.absolute()}")
        assertFailsWith<NoSuchFileException> { SourceCode.fromPath(path) }
    }

    @Test
    fun testFromPathWithMissingExtension_p8() {
        val pathWithoutExt = Path.of("test", "fixtures", "simple_main")
        val pathWithExt = Path.of(pathWithoutExt.toString() + ".p8")

        assertTrue(pathWithExt.isRegularFile(), "sanity check: should be normal file: ${pathWithExt.absolute()}")
        assertTrue(pathWithExt.isReadable(), "sanity check: should be readable: ${pathWithExt.absolute()}")
        assertFailsWith<NoSuchFileException> { SourceCode.fromPath(pathWithoutExt) }
    }

    @Test
    fun testFromPathWithDirectory() {
        val path = Path.of("test", "fixtures")

        assertTrue(path.isDirectory(), "sanity check: should be a directory")
        assertFailsWith<AccessDeniedException> { SourceCode.fromPath(path) }
    }

    @Test
    fun testFromPathWithExistingPath() {
        val filename = "simple_main.p8"
        val path = Path.of("test", "fixtures", filename)
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
        val path = Path.of(".", "test", "..", "test", "fixtures", filename)
        val src = SourceCode.fromPath(path)

        val expectedOrigin = path.normalize().absolutePathString()
        assertEquals(expectedOrigin, src.origin)

        val expectedSrcText = path.toFile().readText()
        val actualSrcText = src.getCharStream().toString()
        assertEquals(expectedSrcText, actualSrcText)
    }

}
