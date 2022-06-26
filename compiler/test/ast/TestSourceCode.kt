package prog8tests.ast

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import prog8.code.core.SourceCode
import prog8.code.core.SourceCode.Companion.libraryFilePrefix
import prog8tests.helpers.assumeNotExists
import prog8tests.helpers.assumeReadableFile
import prog8tests.helpers.fixturesDir
import prog8tests.helpers.resourcesDir
import kotlin.io.path.Path


class TestSourceCode: AnnotationSpec() {

    @Test
    fun testFromString() {
        val text = """
            main { }
        """
        val src = SourceCode.Text(text)

        src.origin shouldContain Regex("^string:[0-9a-f\\-]+$")
        src.text shouldBe text
        src.isFromResources shouldBe false
        src.isFromFilesystem shouldBe false
        src.toString().startsWith("prog8.code.core.SourceCode") shouldBe true
    }

    @Test
    fun testFromPathWithNonExistingPath() {
        val filename = "i_do_not_exist.p8"
        val path = assumeNotExists(fixturesDir, filename)
        shouldThrow<NoSuchFileException> { SourceCode.File(path) }
    }

    @Test
    fun testFromPathWithMissingExtension_p8() {
        val pathWithoutExt = assumeNotExists(fixturesDir,"simple_main")
        assumeReadableFile(fixturesDir,"ast_simple_main.p8")
        shouldThrow<NoSuchFileException> { SourceCode.File(pathWithoutExt) }
    }

    @Test
    fun testFromPathWithDirectory() {
        shouldThrow<FileSystemException> { SourceCode.File(fixturesDir) }
    }

    @Test
    fun testFromPathWithExistingPath() {
        val filename = "ast_simple_main.p8"
        val path = assumeReadableFile(fixturesDir, filename)
        val src = SourceCode.File(path)
        val expectedOrigin = SourceCode.relative(path).toString()
        src.origin shouldBe expectedOrigin
        src.text shouldBe path.toFile().readText()
        src.isFromResources shouldBe false
        src.isFromFilesystem shouldBe true
    }

    @Test
    fun testFromPathWithExistingNonNormalizedPath() {
        val filename = "ast_simple_main.p8"
        val path = Path(".", "test", "..", "test", "fixtures", filename)
        val srcFile = assumeReadableFile(path).toFile()
        val src = SourceCode.File(path)
        val expectedOrigin = SourceCode.relative(path).toString()
        src.origin shouldBe expectedOrigin
        src.text shouldBe srcFile.readText()
    }

    @Test
    fun testFromResourcesWithExistingP8File_withoutLeadingSlash() {
        val pathString = "prog8lib/math.p8"
        val srcFile = assumeReadableFile(resourcesDir, pathString).toFile()
        val src = SourceCode.Resource(pathString)

        src.origin shouldBe "$libraryFilePrefix/$pathString"
        src.text shouldBe srcFile.readText()
        src.isFromResources shouldBe true
        src.isFromFilesystem shouldBe false
    }

    @Test
    fun testFromResourcesWithExistingP8File_withLeadingSlash() {
        val pathString = "/prog8lib/math.p8"
        val srcFile = assumeReadableFile(resourcesDir, pathString.substring(1)).toFile()
        val src = SourceCode.Resource(pathString)

        src.origin shouldBe "$libraryFilePrefix$pathString"
        src.text shouldBe srcFile.readText()
    }

    @Test
    fun testFromResourcesWithExistingAsmFile_withoutLeadingSlash() {
        val pathString = "prog8lib/math.asm"
        val srcFile = assumeReadableFile(resourcesDir, pathString).toFile()
        val src = SourceCode.Resource(pathString)

        src.origin shouldBe "$libraryFilePrefix/$pathString"
        src.text shouldBe srcFile.readText()
        src.isFromResources shouldBe true
    }

    @Test
    fun testFromResourcesWithExistingAsmFile_withLeadingSlash() {
        val pathString = "/prog8lib/math.asm"
        val srcFile = assumeReadableFile(resourcesDir, pathString.substring(1)).toFile()
        val src = SourceCode.Resource(pathString)

        src.origin shouldBe "$libraryFilePrefix$pathString"
        src.text shouldBe srcFile.readText()
    }

    @Test
    fun testFromResourcesWithNonNormalizedPath() {
        val pathString = "/prog8lib/../prog8lib/math.p8"
        val srcFile = assumeReadableFile(resourcesDir, pathString.substring(1)).toFile()
        val src = SourceCode.Resource(pathString)

        src.origin shouldBe "$libraryFilePrefix/prog8lib/math.p8"
        src.text shouldBe srcFile.readText()
        src.isFromResources shouldBe true
    }


    @Test
    fun testFromResourcesWithNonExistingFile_withLeadingSlash() {
        val pathString = "/prog8lib/i_do_not_exist"
        assumeNotExists(resourcesDir, pathString.substring(1))

        shouldThrow<NoSuchFileException> { SourceCode.Resource(pathString) }
    }
    @Test
    fun testFromResourcesWithNonExistingFile_withoutLeadingSlash() {
        val pathString = "prog8lib/i_do_not_exist"
        assumeNotExists(resourcesDir, pathString)

        shouldThrow<NoSuchFileException> { SourceCode.Resource(pathString) }
    }
}
