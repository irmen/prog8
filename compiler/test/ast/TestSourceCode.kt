package prog8tests.ast

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.source.ImportFileSystem
import prog8.code.source.SourceCode
import prog8tests.helpers.assumeNotExists
import prog8tests.helpers.assumeReadableFile
import prog8tests.helpers.fixturesDir
import prog8tests.helpers.resourcesDir
import kotlin.io.path.Path


class TestSourceCode: FunSpec({

    val outputDir = tempdir().toPath()

    // Helper function for normalizing line endings
    fun normalizeLineEndings(text: String): String {
        return text.replace("\\R".toRegex(), "\n")
    }

    // ============================================================================
    // SourceCode.Text Tests
    // ============================================================================

    test("testFromString") {
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

    test("testFromStringDOSLineEndings") {
        val text = "main {\r\nline2\r\nline3\r\n}\r\n"
        val src = SourceCode.Text(text)
        src.text shouldNotBe text     // because normalized line endings!
        src.text.split('\r', '\n').size shouldBe 5
    }

    // ============================================================================
    // SourceCode.File Tests (via ImportFileSystem)
    // ============================================================================

    test("testFromPathWithNonExistingPath") {
        val filename = "i_do_not_exist.p8"
        val path = assumeNotExists(fixturesDir, filename)
        shouldThrow<NoSuchFileException> { ImportFileSystem.getFile(path) }
    }

    test("testFromPathWithMissingExtension_p8") {
        val pathWithoutExt = assumeNotExists(fixturesDir,"simple_main")
        assumeReadableFile(fixturesDir,"ast_simple_main.p8")
        shouldThrow<NoSuchFileException> { ImportFileSystem.getFile(pathWithoutExt) }
    }

    test("testFromPathWithDirectory") {
        shouldThrow<FileSystemException> { ImportFileSystem.getFile(fixturesDir) }
    }

    test("testFromPathWithExistingPath") {
        val filename = "ast_simple_main.p8"
        val path = assumeReadableFile(fixturesDir, filename)
        val src = ImportFileSystem.getFile(path)
        val expectedOrigin = SourceCode.relative(path).toString()
        src.origin shouldBe expectedOrigin
        src.text shouldBe normalizeLineEndings(path.toFile().readText())
        src.isFromResources shouldBe false
        src.isFromFilesystem shouldBe true
    }

    test("testFromPathWithExistingPathDOSLineEndings") {
        val text = "main {\r\nline2\r\nline3\r\n}\r"
        val filePath = outputDir.resolve("on_the_fly_test_" + text.hashCode().toUInt().toString(16) + ".p8")
        filePath.toFile().writeText(text)
        val path = assumeReadableFile(fixturesDir, filePath)
        val src = ImportFileSystem.getFile(path)
        src.text shouldNotBe  path.toFile().readText()      // should be normalized!
        src.text.split('\r', '\n').size shouldBe 5
    }

    test("testFromPathWithExistingNonNormalizedPath") {
        val filename = "ast_simple_main.p8"
        val path = Path(".", "test", "..", "test", "fixtures", filename)
        val srcFile = assumeReadableFile(path).toFile()
        val src = ImportFileSystem.getFile(path)
        val expectedOrigin = SourceCode.relative(path).toString()
        src.origin shouldBe expectedOrigin
        src.text shouldBe normalizeLineEndings(srcFile.readText())
    }

    // ============================================================================
    // SourceCode.Resource Tests (via ImportFileSystem)
    // ============================================================================

    test("testFromResourcesWithExistingP8File_withoutLeadingSlash") {
        val pathString = "prog8lib/math.p8"
        val srcFile = assumeReadableFile(resourcesDir, pathString).toFile()
        val src = ImportFileSystem.getResource(pathString)

        src.origin shouldBe "library:/$pathString"
        src.text shouldBe normalizeLineEndings(srcFile.readText())
        src.isFromResources shouldBe true
        src.isFromFilesystem shouldBe false
    }

    test("testFromResourcesWithExistingP8File_withLeadingSlash") {
        val pathString = "/prog8lib/math.p8"
        val srcFile = assumeReadableFile(resourcesDir, pathString.substring(1)).toFile()
        val src = ImportFileSystem.getResource(pathString)

        src.origin shouldBe "library:$pathString"
        src.text shouldBe normalizeLineEndings(srcFile.readText())
    }

    test("testFromResourcesWithExistingAsmFile_withoutLeadingSlash") {
        val pathString = "prog8lib/math.asm"
        val srcFile = assumeReadableFile(resourcesDir, pathString).toFile()
        val src = ImportFileSystem.getResource(pathString)

        src.origin shouldBe "library:/$pathString"
        src.text shouldBe normalizeLineEndings(srcFile.readText())
        src.isFromResources shouldBe true
    }

    test("testFromResourcesWithExistingAsmFile_withLeadingSlash") {
        val pathString = "/prog8lib/math.asm"
        val srcFile = assumeReadableFile(resourcesDir, pathString.substring(1)).toFile()
        val src = ImportFileSystem.getResource(pathString)

        src.origin shouldBe "library:$pathString"
        src.text shouldBe normalizeLineEndings(srcFile.readText())
    }

    test("testFromResourcesWithNonNormalizedPath") {
        val pathString = "/prog8lib/../prog8lib/math.p8"
        val srcFile = assumeReadableFile(resourcesDir, pathString.substring(1)).toFile()
        val src = ImportFileSystem.getResource(pathString)

        src.origin shouldBe "library:/prog8lib/math.p8"
        src.text shouldBe normalizeLineEndings(srcFile.readText())
        src.isFromResources shouldBe true
    }


    test("testFromResourcesWithNonExistingFile_withLeadingSlash") {
        val pathString = "/prog8lib/i_do_not_exist"
        assumeNotExists(resourcesDir, pathString.substring(1))

        shouldThrow<NoSuchFileException> { ImportFileSystem.getResource(pathString) }
    }

    test("testFromResourcesWithNonExistingFile_withoutLeadingSlash") {
        val pathString = "prog8lib/i_do_not_exist"
        assumeNotExists(resourcesDir, pathString)

        shouldThrow<NoSuchFileException> { ImportFileSystem.getResource(pathString) }
    }
})
