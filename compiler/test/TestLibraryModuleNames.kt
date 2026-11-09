package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8tests.helpers.assumeDirectory
import prog8tests.helpers.resourcesDir
import prog8tests.helpers.workingDir
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines


class TestLibraryModuleNames: FunSpec({

    test("languageServer builtinModules does not drift from compiler/res/prog8lib") {
        val libDir = assumeDirectory(resourcesDir, "prog8lib")
        val libraryModules = libDir.toFile().walkTopDown()
            .filter { it.isFile && it.extension == "p8" }
            .map { it.nameWithoutExtension }
            .filterNot { it.startsWith("shared_") }
            .toSortedSet()

        val lspSource = (workingDir / ".." / "languageServer/src/prog8lsp/Prog8TextDocumentService.kt").normalize()
        withClue("LSP source should exist: $lspSource") {
            lspSource.isRegularFile() shouldBe true
        }
        val lines = lspSource.readLines()
        val start = lines.indexOfFirst { "val builtinModules = setOf(" in it }
        withClue("builtinModules declaration not found in $lspSource") {
            (start >= 0) shouldBe true
        }
        val end = (start + 1..lines.lastIndex).first { lines[it].trim() == ")" }
        val block = lines.subList(start, end + 1)
            .joinToString("\n")
            .lineSequence()
            .map { it.substringBefore("//") }
            .joinToString("\n")
        val lspModules = Regex("\"(\\w+)\"").findAll(block)
            .map { it.groupValues[1] }
            .toSortedSet()

        withClue("no library modules found") {
            libraryModules.isNotEmpty() shouldBe true
        }
        withClue("no builtinModules entries parsed from the LSP source") {
            lspModules.isNotEmpty() shouldBe true
        }
        withClue("modules missing from languageServer builtinModules") {
            (libraryModules - lspModules) shouldBe emptySet<String>()
        }
        withClue("stale entries in languageServer builtinModules") {
            (lspModules - libraryModules) shouldBe emptySet<String>()
        }
    }
})
