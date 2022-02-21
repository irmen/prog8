package prog8tests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.ast.expressions.*
import prog8.ast.statements.Assignment
import prog8.ast.statements.FunctionCallStatement
import prog8.ast.statements.Pipe
import prog8.codegen.target.C64Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.assertFailure
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText


class TestPipes: FunSpec({

    test("correct pipe statements") {
        val text = """
            %import floats
            %import textio
            
            main {
                sub start() {

                    1.234 |> addfloat 
                          |> floats.print_f
                    
                    9999 |> addword
                         |> txt.print_uw

                    ; these are optimized into just the function calls:
                    9999 |> abs |> txt.print_uw
                    9999 |> txt.print_uw
                    99 |> abs |> txt.print_ub
                    99 |> txt.print_ub
                }

                sub addfloat(float fl) -> float {
                    return fl+2.22
                }
                sub addword(uword ww) -> uword {
                    return ww+2222
                }
            }
        """
        val result = compileText(C64Target(), true, text, writeAssembly = true).assertSuccess()
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 7
        val pipef = stmts[0] as Pipe
        pipef.expressions.size shouldBe 2
        pipef.expressions[0] shouldBe instanceOf<FunctionCallExpression>()
        pipef.expressions[1] shouldBe instanceOf<IdentifierReference>()

        val pipew = stmts[1] as Pipe
        pipew.expressions.size shouldBe 2
        pipew.expressions[0] shouldBe instanceOf<FunctionCallExpression>()
        pipew.expressions[1] shouldBe instanceOf<IdentifierReference>()

        var stmt = stmts[2] as FunctionCallStatement
        stmt.target.nameInSource shouldBe listOf("txt", "print_uw")
        stmt = stmts[3] as FunctionCallStatement
        stmt.target.nameInSource shouldBe listOf("txt", "print_uw")
        stmt = stmts[4] as FunctionCallStatement
        stmt.target.nameInSource shouldBe listOf("txt", "print_ub")
        stmt = stmts[5] as FunctionCallStatement
        stmt.target.nameInSource shouldBe listOf("txt", "print_ub")
    }

    test("incorrect type in pipe statement") {
        val text = """
            %option enable_floats
            
            main {
                sub start() {

                    1.234 |> addfloat 
                          |> addword |> addword
                }

                sub addfloat(float fl) -> float {
                    return fl+2.22
                }
                sub addword(uword ww) -> uword {
                    return ww+2222
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, errors=errors).assertFailure()
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "incompatible"
    }

    test("correct pipe expressions") {
        val text = """
            %import floats
            %import textio
            
            main {
                sub start() {
                    float @shared fl = 1.234 |> addfloat 
                                        |> addfloat
                    
                    uword @shared ww = 9999 |> addword
                                        |> addword
                                        
                    ubyte @shared cc = 30 |> sin8u |> cos8u     ; will be optimized away into a const number
                    cc = cc |> sin8u |> cos8u
                }

                sub addfloat(float fl) -> float {
                    return fl+2.22
                }
                sub addword(uword ww) -> uword {
                    return ww+2222
                }
                sub addbyte(ubyte bb) -> ubyte {
                    return bb+1
                }
        }
        """
        val result = compileText(C64Target(), true, text, writeAssembly = true).assertSuccess()
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 8
        val assignf = stmts[1] as Assignment
        val pipef = assignf.value as PipeExpression
        pipef.expressions.size shouldBe 2
        pipef.expressions[0] shouldBe instanceOf<FunctionCallExpression>()
        pipef.expressions[1] shouldBe instanceOf<IdentifierReference>()

        val assignw = stmts[3] as Assignment
        val pipew = assignw.value as PipeExpression
        pipew.expressions.size shouldBe 2
        pipew.expressions[0] shouldBe instanceOf<FunctionCallExpression>()
        pipew.expressions[1] shouldBe instanceOf<IdentifierReference>()

        var assigncc = stmts[5] as Assignment
        val value = assigncc.value as NumericLiteral
        value.number shouldBe 194.0

        assigncc = stmts[6] as Assignment
        val pipecc = assignw.value as PipeExpression
        pipecc.expressions.size shouldBe 2
        pipecc.expressions[0] shouldBe instanceOf<FunctionCallExpression>()
        pipecc.expressions[1] shouldBe instanceOf<IdentifierReference>()
    }

    test("incorrect type in pipe expression") {
        val text = """
            %option enable_floats
            
            main {
                sub start() {
                    uword result = 1.234 |> addfloat 
                                         |> addword |> addword
                }

                sub addfloat(float fl) -> float {
                    return fl+2.22
                }
                sub addword(uword ww) -> uword {
                    return ww+2222
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, errors=errors).assertFailure()
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "incompatible"
    }

    test("correct pipe statement with builtin expression") {
        val text = """
            %import textio
            
            main {
                sub start() {
                    uword ww = 9999
                    ubyte bb = 99
                    ww |> abs |> txt.print_uw
                    bb |> abs |> txt.print_ub
                }
            }
        """
        val result = compileText(C64Target(), true, text, writeAssembly = true).assertSuccess()
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 7
        val pipef = stmts[4] as Pipe
        pipef.expressions.size shouldBe 2
        pipef.expressions[0] shouldBe instanceOf<BuiltinFunctionCall>()
        pipef.expressions[1] shouldBe instanceOf<IdentifierReference>()

        val pipew = stmts[5] as Pipe
        pipew.expressions.size shouldBe 2
        pipew.expressions[0] shouldBe instanceOf<BuiltinFunctionCall>()
        pipew.expressions[1] shouldBe instanceOf<IdentifierReference>()
    }

    test("pipe statement with type errors") {
        val text = """
            %import textio
            
            main {
                sub start() {
                    uword ww = 9999
                    9999 |> abs |> txt.print_ub
                    ww |> abs |> txt.print_ub
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), true, text, writeAssembly = true, errors=errors).assertFailure()
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "UWORD incompatible"
        errors.errors[1] shouldContain "UWORD incompatible"
    }
})
