package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.StringLiteral
import prog8.ast.statements.FunctionCallStatement
import prog8.ast.statements.Label
import prog8.code.target.Cx16Target
import prog8tests.helpers.*
import kotlin.io.path.absolute
import kotlin.io.path.name


/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 */
class TestCompilerOnImportsAndIncludes: FunSpec({

    val outputDir = tempdir().toPath()

    context("Import") {

        test("testImportFromSameFolder") {
            val filepath = assumeReadableFile(fixturesDir, "importFromSameFolder.p8")
            assumeReadableFile(fixturesDir, "foo_bar.p8")

            val platform = Cx16Target()
            val result = compileFile(platform, optimize = false, fixturesDir, filepath.name, outputDir)!!

            val program = result.compilerAst
            val startSub = program.entrypoint
            val strLits = startSub.statements
                .filterIsInstance<FunctionCallStatement>()
                .map { it.args[0] as IdentifierReference }
                .map { it.targetVarDecl(program)!!.value as StringLiteral }

            strLits[0].value shouldBe "main.bar"
            strLits[1].value shouldBe "foo.bar"
            strLits[0].definingScope.name shouldBe "main"
            strLits[1].definingScope.name shouldBe "foobar"
        }
    }

    context("AsmInclude") {
        test("testAsmIncludeFromSameFolder") {
            val filepath = assumeReadableFile(fixturesDir, "asmIncludeFromSameFolder.p8")
            assumeReadableFile(fixturesDir, "foo_bar.asm")

            val platform = Cx16Target()
            val result = compileFile(platform, optimize = false, fixturesDir, filepath.name, outputDir)!!

            val program = result.compilerAst
            val startSub = program.entrypoint
            val args = startSub.statements
                .filterIsInstance<FunctionCallStatement>()
                .map { it.args[0] }

            val str0 = (args[0] as IdentifierReference).targetVarDecl(program)!!.value as StringLiteral
            str0.value shouldBe "main.bar"
            str0.definingScope.name shouldBe "main"

            val id1 = (args[1] as AddressOf).identifier
            val lbl1 = id1.targetStatement(program) as Label
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
                Triple("same ", "asmBinaryFromSameFolder.p8", "do_nothing1.bin"),
                Triple("sub", "asmBinaryFromSubFolder.p8", "subFolder/do_nothing2.bin"),
            )

        tests.forEach {
            val (where, p8Str, _) = it
            test("%asmbinary from ${where}folder") {
                val p8Path = assumeReadableFile(fixturesDir, p8Str)
                // val binPath = assumeReadableFile(fixturesDir, binStr)

                // the bug we're testing for (#54) was hidden if outputDir == workingDir
                withClue("sanity check: workingDir and outputDir should not be the same folder") {
                    outputDir.absolute().normalize() shouldNotBe workingDir.absolute().normalize()
                }

                withClue("argument to assembler directive .binary " +
                        "should be relative to the generated .asm file (in output dir), " +
                        "NOT relative to .p8 neither current working dir") {
                    compileFile(Cx16Target(), false, p8Path.parent, p8Path.name, outputDir) shouldNotBe null
                }
            }
        }
    }

})
