package prog8tests

import org.junit.jupiter.api.*
import kotlin.test.*
import prog8tests.helpers.*

import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.StringLiteralValue
import prog8.ast.statements.FunctionCallStatement
import prog8.ast.statements.Label
import prog8.compiler.compileProgram
import prog8.compiler.target.Cx16Target
import kotlin.io.path.name


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
        val result = compileFixture(filepath.name, platform)

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
        val result = compileFixture(filepath.name, platform)

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
}
