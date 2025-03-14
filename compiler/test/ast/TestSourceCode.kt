package prog8tests.ast

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.source.ImportFileSystem
import prog8.code.source.SourceCode
import prog8tests.helpers.*
import kotlin.io.path.Path


class TestSourceCode: AnnotationSpec() {

    val outputDir = tempdir().toPath()

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
        src.toString().startsWith(SourceCode::class.qualifiedName!!) shouldBe true
    }

    @Test
    fun testFromStringDOSLineEndings() {
        val text = "main {\r\nline2\r\nline3\r\n}\r\n"
        val src = SourceCode.Text(text)
        src.text shouldNotBe text     // because normalized line endings!
        src.text.split('\r', '\n').size shouldBe 5
    }

    @Test
    fun testFromPathWithNonExistingPath() {
        val filename = "i_do_not_exist.p8"
        val path = assumeNotExists(fixturesDir, filename)
        shouldThrow<NoSuchFileException> { ImportFileSystem.getFile(path) }
    }

    @Test
    fun testFromPathWithMissingExtension_p8() {
        val pathWithoutExt = assumeNotExists(fixturesDir,"simple_main")
        assumeReadableFile(fixturesDir,"ast_simple_main.p8")
        shouldThrow<NoSuchFileException> { ImportFileSystem.getFile(pathWithoutExt) }
    }

    @Test
    fun testFromPathWithDirectory() {
        shouldThrow<FileSystemException> { ImportFileSystem.getFile(fixturesDir) }
    }


    private fun normalizeLineEndings(text: String): String {
    	return text.replace("\\R".toRegex(), "\n")
    }

    @Test
    fun testFromPathWithExistingPath() {
        val filename = "ast_simple_main.p8"
        val path = assumeReadableFile(fixturesDir, filename)
        val src = ImportFileSystem.getFile(path)
        val expectedOrigin = SourceCode.relative(path).toString()
        src.origin shouldBe expectedOrigin
        src.text shouldBe normalizeLineEndings(path.toFile().readText())
        src.isFromResources shouldBe false
        src.isFromFilesystem shouldBe true
    }

    @Test
    fun testFromPathWithExistingPathDOSLineEndings() {
        val text = "main {\r\nline2\r\nline3\r\n}\r"
        val filePath = outputDir.resolve("on_the_fly_test_" + text.hashCode().toUInt().toString(16) + ".p8")
        filePath.toFile().writeText(text)
        val path = assumeReadableFile(fixturesDir, filePath)
        val src = ImportFileSystem.getFile(path)
        src.text shouldNotBe  path.toFile().readText()      // should be normalized!
        src.text.split('\r', '\n').size shouldBe 5
    }

    @Test
    fun testFromPathWithExistingNonNormalizedPath() {
        val filename = "ast_simple_main.p8"
        val path = Path(".", "test", "..", "test", "fixtures", filename)
        val srcFile = assumeReadableFile(path).toFile()
        val src = ImportFileSystem.getFile(path)
        val expectedOrigin = SourceCode.relative(path).toString()
        src.origin shouldBe expectedOrigin
        src.text shouldBe normalizeLineEndings(srcFile.readText())
    }

    @Test
    fun testFromResourcesWithExistingP8File_withoutLeadingSlash() {
        val pathString = "prog8lib/math.p8"
        val srcFile = assumeReadableFile(resourcesDir, pathString).toFile()
        val src = ImportFileSystem.getResource(pathString)

        src.origin shouldBe "library:/$pathString"
        src.text shouldBe normalizeLineEndings(srcFile.readText())
        src.isFromResources shouldBe true
        src.isFromFilesystem shouldBe false
    }

    @Test
    fun testFromResourcesWithExistingP8File_withLeadingSlash() {
        val pathString = "/prog8lib/math.p8"
        val srcFile = assumeReadableFile(resourcesDir, pathString.substring(1)).toFile()
        val src = ImportFileSystem.getResource(pathString)

        src.origin shouldBe "library:$pathString"
        src.text shouldBe normalizeLineEndings(srcFile.readText())
    }

    @Test
    fun testFromResourcesWithExistingAsmFile_withoutLeadingSlash() {
        val pathString = "prog8lib/math.asm"
        val srcFile = assumeReadableFile(resourcesDir, pathString).toFile()
        val src = ImportFileSystem.getResource(pathString)

        src.origin shouldBe "library:/$pathString"
        src.text shouldBe normalizeLineEndings(srcFile.readText())
        src.isFromResources shouldBe true
    }

    @Test
    fun testFromResourcesWithExistingAsmFile_withLeadingSlash() {
        val pathString = "/prog8lib/math.asm"
        val srcFile = assumeReadableFile(resourcesDir, pathString.substring(1)).toFile()
        val src = ImportFileSystem.getResource(pathString)

        src.origin shouldBe "library:$pathString"
        src.text shouldBe normalizeLineEndings(srcFile.readText())
    }

    @Test
    fun testFromResourcesWithNonNormalizedPath() {
        val pathString = "/prog8lib/../prog8lib/math.p8"
        val srcFile = assumeReadableFile(resourcesDir, pathString.substring(1)).toFile()
        val src = ImportFileSystem.getResource(pathString)

        src.origin shouldBe "library:/prog8lib/math.p8"
        src.text shouldBe normalizeLineEndings(srcFile.readText())
        src.isFromResources shouldBe true
    }


    @Test
    fun testFromResourcesWithNonExistingFile_withLeadingSlash() {
        val pathString = "/prog8lib/i_do_not_exist"
        assumeNotExists(resourcesDir, pathString.substring(1))

        shouldThrow<NoSuchFileException> { ImportFileSystem.getResource(pathString) }
    }
    @Test
    fun testFromResourcesWithNonExistingFile_withoutLeadingSlash() {
        val pathString = "prog8lib/i_do_not_exist"
        assumeNotExists(resourcesDir, pathString)

        shouldThrow<NoSuchFileException> { ImportFileSystem.getResource(pathString) }
    }
}
