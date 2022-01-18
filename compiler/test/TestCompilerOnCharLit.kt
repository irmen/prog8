package prog8tests

import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import prog8.ast.IFunctionCall
import prog8.ast.base.DataType
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteralValue
import prog8.codegen.target.Cx16Target
import prog8.compilerinterface.Encoding
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText


/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 */
class TestCompilerOnCharLit: FunSpec({

    test("testCharLitAsRomsubArg") {
        val platform = Cx16Target
        val result = compileText(platform, false, """
            main {
                romsub ${"$"}FFD2 = chrout(ubyte ch @ A)
                sub start() {
                    chrout('\n')
                }
            }
        """).assertSuccess()

        val program = result.program
        val startSub = program.entrypoint
        val funCall = startSub.statements.filterIsInstance<IFunctionCall>()[0]

        withClue("char literal should have been replaced by ubyte literal") {
            funCall.args[0] shouldBe instanceOf<NumericLiteralValue>()
        }
        val arg = funCall.args[0] as NumericLiteralValue
        arg.type shouldBe DataType.UBYTE
        arg.number shouldBe platform.encodeString("\n", Encoding.PETSCII)[0].toDouble()
    }

    test("testCharVarAsRomsubArg") {
        val platform = Cx16Target
        val result = compileText(platform, false, """
            main {
                romsub ${"$"}FFD2 = chrout(ubyte ch @ A)
                sub start() {
                    ubyte ch = '\n'
                    chrout(ch)
                }
            }
        """).assertSuccess()

        val program = result.program
        val startSub = program.entrypoint
        val funCall = startSub.statements.filterIsInstance<IFunctionCall>()[0]

        funCall.args[0] shouldBe instanceOf<IdentifierReference>()
        val arg = funCall.args[0] as IdentifierReference
        val decl = arg.targetVarDecl(program)!!
        decl.type shouldBe VarDeclType.VAR
        decl.datatype shouldBe DataType.UBYTE

        // TODO: assertIs<CharLiteral>(decl.value,
        //          "char literals should be kept until code gen")
        //       val initializerValue = decl.value as CharLiteral
        //       assertEquals('\n', (initializerValue as CharLiteral).value)

        withClue("initializer value should have been moved to separate assignment"){
            decl.value shouldBe null
        }
        val assignInitialValue = decl.findInitializer(program)!!
        assignInitialValue.target.identifier!!.nameInSource shouldBe listOf("ch")
        withClue("char literal should have been replaced by ubyte literal") {
            assignInitialValue.value shouldBe instanceOf<NumericLiteralValue>()
        }
        val initializerValue = assignInitialValue.value as NumericLiteralValue
        initializerValue.type shouldBe DataType.UBYTE
        initializerValue.number shouldBe platform.encodeString("\n", Encoding.PETSCII)[0].toDouble()
    }

    test("testCharConstAsRomsubArg") {
        val platform = Cx16Target
        val result = compileText(platform, false, """
            main {
                romsub ${"$"}FFD2 = chrout(ubyte ch @ A)
                sub start() {
                    const ubyte ch = '\n'
                    chrout(ch)
                }
            }
        """).assertSuccess()

        val program = result.program
        val startSub = program.entrypoint
        val funCall = startSub.statements.filterIsInstance<IFunctionCall>()[0]

        // Now, both is ok for the arg: a) still the IdRef or b) replaced by numeric literal
        when (val arg = funCall.args[0]) {
            is IdentifierReference -> {
                val decl = arg.targetVarDecl(program)!!
                decl.type shouldBe VarDeclType.CONST
                decl.datatype shouldBe DataType.UBYTE
                (decl.value as NumericLiteralValue).number shouldBe platform.encodeString("\n", Encoding.PETSCII)[0]
            }
            is NumericLiteralValue -> {
                arg.number shouldBe platform.encodeString("\n", Encoding.PETSCII)[0].toDouble()
            }
            else -> fail("invalid arg type") // funCall.args[0] shouldBe instanceOf<IdentifierReference>() // make test fail
        }
    }

})
