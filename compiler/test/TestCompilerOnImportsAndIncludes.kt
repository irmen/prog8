package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.StringLiteral
import prog8.ast.statements.FunctionCallStatement
import prog8.ast.statements.Label
import prog8.code.sanitize
import prog8.code.target.Cx16Target
import prog8tests.helpers.*
import kotlin.io.path.name

class TestCompilerOnImportsAndIncludes: FunSpec({
    val outputDir = tempdir().toPath()

    context("Import") {

        test("testImportFromSameFolder") {
            val outputDir = tempdir().toPath()
            val filepath = assumeReadableFile(fixturesDir, "importFromSameFolder.p8")
            assumeReadableFile(fixturesDir, "foo_bar.p8")

            val platform = Cx16Target()
            val result = compileFile(platform, optimize = false, fixturesDir, filepath.name, outputDir)!!

            val program = result.compilerAst
            val startSub = program.entrypoint
            val strLits = startSub.statements
                .filterIsInstance<FunctionCallStatement>()
                .map { it.args[0] as IdentifierReference }
                .map { it.targetVarDecl()!!.value as StringLiteral }

            strLits[0].value shouldBe "main.bar"
            strLits[1].value shouldBe "foo.bar"
            strLits[0].definingScope.name shouldBe "main"
            strLits[1].definingScope.name shouldBe "foobar"
        }
    }

    context("AsmInclude") {
        test("AsmIncludeFromSameFolder") {
            val filepath = assumeReadableFile(fixturesDir, "asmIncludeFromSameFolder.p8")
            assumeReadableFile(fixturesDir, "foo_bar.asm")

            val platform = Cx16Target()
            val result = compileFile(platform, optimize = false, fixturesDir, filepath.name, outputDir)!!

            val program = result.compilerAst
            val startSub = program.entrypoint
            val args = startSub.statements
                .filterIsInstance<FunctionCallStatement>()
                .map { it.args[0] }

            val str0 = (args[0] as IdentifierReference).targetVarDecl()!!.value as StringLiteral
            str0.value shouldBe "main.bar"
            str0.definingScope.name shouldBe "main"

            val id1 = (args[1] as AddressOf).identifier!!
            val lbl1 = id1.targetStatement() as Label
            lbl1.name shouldBe "foo_bar"
            lbl1.definingScope.name shouldBe "main"
        }
    }

    context("Asmbinary") {
        test("testAsmbinaryDirectiveWithNonExistingFile") {
            val p8Path = assumeReadableFile(fixturesDir, "asmBinaryNonExisting.p8")
            assumeNotExists(fixturesDir, "i_do_not_exist.bin")

            compileFile(Cx16Target(), false, p8Path.parent, p8Path.name, outputDir) shouldBe null
        }

        test("testAsmbinaryDirectiveWithNonReadableFile") {
            val p8Path = assumeReadableFile(fixturesDir, "asmBinaryNonReadable.p8")
            assumeDirectory(fixturesDir, "subFolder")

            compileFile(Cx16Target(), false, p8Path.parent, p8Path.name, outputDir) shouldBe null
        }

        val tests = listOf(
                "same" to "asmBinaryFromSameFolder.p8",
                "sub" to "asmBinaryFromSubFolder.p8"
            )

        withData<Pair<String, String>>({"%asmbinary from ${it.first} folder"}, tests) { (_, p8Str) ->
            val p8Path = assumeReadableFile(fixturesDir, p8Str)

            // the bug we're testing for (#54) was hidden if outputDir == workingDir
            withClue("sanity check: workingDir and outputDir should not be the same folder") {
                outputDir.sanitize() shouldNotBe workingDir.sanitize()
            }

            withClue("argument to assembler directive .binary " +
                    "should be relative to the generated .asm file (in output dir), " +
                    "NOT relative to .p8 neither current working dir") {
                compileFile(Cx16Target(), false, p8Path.parent, p8Path.name, outputDir) shouldNotBe null
            }
        }
    }

})
