package prog8tests

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import kotlin.test.*
import kotlin.io.path.*
import prog8tests.helpers.*

import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.StringLiteralValue
import prog8.ast.statements.FunctionCallStatement
import prog8.ast.statements.Label
import prog8.compiler.target.Cx16Target


/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCompilerOnImportsAndIncludes {

    @BeforeAll
    fun setUp() {
        sanityCheckDirectories("compiler")
    }

    @Test
    fun testImportFromSameFolder() {
        val filepath = fixturesDir.resolve("importFromSameFolder.p8")
        val imported = fixturesDir.resolve("foo_bar.p8")
        assumeReadableFile(filepath)
        assumeReadableFile(imported)

        val platform = Cx16Target
        val result = compileFile(platform, false, fixturesDir, filepath.name)
            .assertSuccess()

        val program = result.programAst
        val startSub = program.entrypoint()
        val strLits = startSub.statements
            .filterIsInstance<FunctionCallStatement>()
            .map { it.args[0] as IdentifierReference }
            .map { it.targetVarDecl(program)!!.value as StringLiteralValue }

        assertEquals("main.bar", strLits[0].value)
        assertEquals("foo.bar", strLits[1].value)
        assertEquals("main", strLits[0].definingScope().name)
        assertEquals("foo", strLits[1].definingScope().name)
    }

    @Test
    fun testAsmIncludeFromSameFolder() {
        val filepath = fixturesDir.resolve("asmIncludeFromSameFolder.p8")
        val included = fixturesDir.resolve("foo_bar.asm")
        assumeReadableFile(filepath)
        assumeReadableFile(included)

        val platform = Cx16Target
        val result = compileFile(platform, false, fixturesDir, filepath.name)
            .assertSuccess()

        val program = result.programAst
        val startSub = program.entrypoint()
        val args = startSub.statements
            .filterIsInstance<FunctionCallStatement>()
            .map { it.args[0] }

        val str0 = (args[0] as IdentifierReference).targetVarDecl(program)!!.value as StringLiteralValue
        assertEquals("main.bar", str0.value)
        assertEquals("main", str0.definingScope().name)

        val id1 = (args[1] as AddressOf).identifier
        val lbl1 = id1.targetStatement(program) as Label
        assertEquals("foo_bar", lbl1.name)
        assertEquals("start", lbl1.definingScope().name)
    }

    @Test
    fun testAsmbinaryDirectiveWithNonExistingFile() {
        val p8Path = fixturesDir.resolve("asmBinaryNonExisting.p8")
        val binPath = fixturesDir.resolve("i_do_not_exist.bin")
        assumeReadableFile(p8Path)
        assumeNotExists(binPath)

        compileFile(Cx16Target, false, p8Path.parent, p8Path.name, outputDir)
            .assertFailure()
    }

    @Test
    fun testAsmbinaryDirectiveWithNonReadableFile() {
        val p8Path = fixturesDir.resolve("asmBinaryNonReadable.p8")
        val binPath = fixturesDir.resolve("subFolder")
        assumeReadableFile(p8Path)
        assumeDirectory(binPath)

        compileFile(Cx16Target, false, p8Path.parent, p8Path.name, outputDir)
            .assertFailure()
    }

    @TestFactory
    fun asmbinaryDirectiveWithExistingBinFile(): Iterable<DynamicTest> =
        listOf(
            Triple("same ", "asmBinaryFromSameFolder.p8", "do_nothing1.bin"),
            Triple("sub", "asmBinaryFromSubFolder.p8", "subFolder/do_nothing2.bin"),
        ).map {
            val (where, p8Str, binStr) = it
            val p8Path = fixturesDir.resolve(p8Str)
            val binPath = fixturesDir.resolve(binStr)
            val displayName = "%asmbinary from ${where}folder"
            dynamicTest(displayName) {
                assumeReadableFile(p8Path)
                assumeReadableFile(binPath)
                assertNotEquals( // the bug we're testing for (#54) was hidden if outputDir == workinDir
                    workingDir.normalize().toAbsolutePath(),
                    outputDir.normalize().toAbsolutePath(),
                    "sanity check: workingDir and outputDir should not be the same folder")

                compileFile(Cx16Target, false, p8Path.parent, p8Path.name, outputDir)
                    .assertSuccess(
                        "argument to assembler directive .binary " +
                                "should be relative to the generated .asm file (in output dir), " +
                                "NOT relative to .p8 neither current working dir"
                    )
            }
        }

    }

