package prog8tests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.ast.expressions.FunctionCallExpression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.PipeExpression
import prog8.ast.statements.Assignment
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
        stmts.size shouldBe 3
        val pipef = stmts[0] as Pipe
        pipef.expressions.size shouldBe 2
        pipef.expressions[0] shouldBe instanceOf<FunctionCallExpression>()
        pipef.expressions[1] shouldBe instanceOf<IdentifierReference>()

        val pipew = stmts[1] as Pipe
        pipew.expressions.size shouldBe 2
        pipew.expressions[0] shouldBe instanceOf<FunctionCallExpression>()
        pipew.expressions[1] shouldBe instanceOf<IdentifierReference>()
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
        stmts.size shouldBe 5
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
})
