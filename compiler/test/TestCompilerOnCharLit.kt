package prog8tests

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import kotlin.test.*
import prog8tests.helpers.*

import prog8.ast.IFunctionCall
import prog8.ast.base.DataType
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteralValue
import prog8.compiler.target.Cx16Target


/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCompilerOnCharLit {

    @BeforeAll
    fun setUp() {
        sanityCheckDirectories("compiler")
    }

    @Test
    fun testCharLitAsRomsubArg() {
        val platform = Cx16Target
        val result = compileFixture("charLitAsRomsubArg.p8", platform)

        val program = result.programAst
        val startSub = program.entrypoint()
        val funCall = startSub.statements.filterIsInstance<IFunctionCall>()[0]

        assertIs<NumericLiteralValue>(funCall.args[0],
            "char literal should have been replaced by ubyte literal")
        val arg = funCall.args[0] as NumericLiteralValue
        assertEquals(DataType.UBYTE, arg.type)
        assertEquals(platform.encodeString("\n", false)[0], arg.number)
    }

    @Test
    fun testCharVarAsRomsubArg() {
        val platform = Cx16Target
        val result = compileFixture("charVarAsRomsubArg.p8", platform)

        val program = result.programAst
        val startSub = program.entrypoint()
        val funCall = startSub.statements.filterIsInstance<IFunctionCall>()[0]

        assertIs<IdentifierReference>(funCall.args[0])
        val arg = funCall.args[0] as IdentifierReference
        val decl = arg.targetVarDecl(program)!!
        assertEquals(VarDeclType.VAR, decl.type)
        assertEquals(DataType.UBYTE, decl.datatype)

        // TODO: assertIs<CharLiteral>(decl.value,
        //          "char literals should be kept until code gen")
        //       val initializerValue = decl.value as CharLiteral
        //       assertEquals('\n', (initializerValue as CharLiteral).value)

        assertIs<NumericLiteralValue>(decl.value,
            "char literal should have been replaced by ubyte literal")
        val initializerValue = decl.value as NumericLiteralValue
        assertEquals(DataType.UBYTE, initializerValue.type)
        assertEquals(platform.encodeString("\n", false)[0], initializerValue.number)
    }

    @Test
    fun testCharConstAsRomsubArg() {
        val platform = Cx16Target
        val result = compileFixture("charConstAsRomsubArg.p8", platform)

        val program = result.programAst
        val startSub = program.entrypoint()
        val funCall = startSub.statements.filterIsInstance<IFunctionCall>()[0]

        // Now, both is ok for the arg: a) still the IdRef or b) replaced by numeric literal
        when (val arg = funCall.args[0]) {
            is IdentifierReference -> {
                val decl = arg.targetVarDecl(program)!!
                assertEquals(VarDeclType.CONST, decl.type)
                assertEquals(DataType.UBYTE, decl.datatype)
                assertEquals(
                    platform.encodeString("\n", false)[0],
                    (decl.value as NumericLiteralValue).number)
            }
            is NumericLiteralValue -> {
                assertEquals(
                    platform.encodeString("\n", false)[0],
                    arg.number)
            }
            else -> assertIs<IdentifierReference>(funCall.args[0]) // make test fail
        }

    }
}
