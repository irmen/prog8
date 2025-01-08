package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.ast.IFunctionCall
import prog8.ast.expressions.IdentifierReference
import prog8.ast.statements.*
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.target.C64Target
import prog8.compiler.astprocessing.hasRtsInAsm
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText


class TestSubroutines: FunSpec({

    test("string arg for byte param proper errormessage") {
        val text="""
            main {
                sub func(ubyte bb) {
                    bb++
                }

                sub start() {
                    func("abc")
                }
            }"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, writeAssembly = true, errors=errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "type mismatch, was: str expected: ubyte"
    }

    test("stringParameter") {
        val text = """
            main {
                sub start() {
                    str text = "test"
                    
                    asmfunc("text")
                    asmfunc(text)
                    asmfunc($2000)
                    func("text")
                    func(text)
                    func($2000)
                }
                
                asmsub asmfunc(str thing @AY) {
                }

                sub func(str thing) {
                    uword t2 = thing as uword
                    asmfunc(thing)
                }
            }
        """
        val result = compileText(C64Target(), false, text, writeAssembly = false)!!
        val mainBlock = result.compilerAst.entrypoint.definingBlock
        val asmfunc = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="asmfunc"}
        val func = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="func"}
        asmfunc.isAsmSubroutine shouldBe true
        asmfunc.statements.isEmpty() shouldBe true
        func.isAsmSubroutine shouldBe false
        withClue("str param for subroutines should be changed into UWORD") {
            asmfunc.parameters.single().type shouldBe DataType.forDt(BaseDataType.UWORD)
            func.parameters.single().type shouldBe DataType.forDt(BaseDataType.UWORD)
            func.statements.size shouldBe 4
            val paramvar = func.statements[0] as VarDecl
            paramvar.name shouldBe "thing"
            paramvar.datatype shouldBe DataType.forDt(BaseDataType.UWORD)
        }
        val assign = func.statements[2] as Assignment
        assign.target.identifier!!.nameInSource shouldBe listOf("t2")
        withClue("str param in function body should have been transformed into just uword assignment") {
            assign.value shouldBe instanceOf<IdentifierReference>()
        }
        val call = func.statements[3] as FunctionCallStatement
        call.target.nameInSource.single() shouldBe "asmfunc"
        withClue("str param in function body should not be transformed by normal compiler steps") {
            call.args.single() shouldBe instanceOf<IdentifierReference>()
        }
        (call.args.single() as IdentifierReference).nameInSource.single() shouldBe "thing"
    }

    test("stringParameterAsmGen") {
        val text = """
            main {
                sub start() {
                    str text = "test"
                    
                    asmfunc("text")
                    asmfunc(text)
                    asmfunc($2000)
                    func("text")
                    func(text)
                    func($2000)
                    emptysub()
                }
                
                asmsub asmfunc(str thing @AY) {
                    %asm {{
                        rts
                    }}
                }

                sub func(str thing) {
                    uword t2 = thing as uword
                    asmfunc(thing)
                }
                
                sub emptysub() {
                }
            }
        """
        val result = compileText(C64Target(), false, text, writeAssembly = true)!!
        val mainBlock = result.compilerAst.entrypoint.definingBlock
        val asmfunc = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="asmfunc"}
        val func = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="func"}
        val emptysub = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="emptysub"}
        asmfunc.isAsmSubroutine shouldBe true
        asmfunc.statements.single() shouldBe instanceOf<InlineAssembly>()
        (asmfunc.statements.single() as InlineAssembly).assembly.trim() shouldBe "rts"
        asmfunc.hasRtsInAsm(false) shouldBe true
        func.isAsmSubroutine shouldBe false
        withClue("str param should have been changed to uword") {
            asmfunc.parameters.single().type shouldBe DataType.forDt(BaseDataType.UWORD)
            func.parameters.single().type shouldBe DataType.forDt(BaseDataType.UWORD)
        }

        func.statements.size shouldBe 5
        func.statements[4] shouldBe instanceOf<Return>()
        val paramvar = func.statements[0] as VarDecl
        paramvar.name shouldBe "thing"
        withClue("pre-asmgen should have changed str to uword type") {
            paramvar.datatype shouldBe DataType.forDt(BaseDataType.UWORD)
        }
        val assign = func.statements[2] as Assignment
        assign.target.identifier!!.nameInSource shouldBe listOf("t2")
        withClue("str param in function body should be treated as plain uword before asmgen") {
            assign.value shouldBe instanceOf<IdentifierReference>()
        }
        (assign.value as IdentifierReference).nameInSource.single() shouldBe "thing"
        val call = func.statements[3] as FunctionCallStatement
        call.target.nameInSource.single() shouldBe "asmfunc"
        withClue("str param in function body should be treated as plain uword and not been transformed") {
            call.args.single() shouldBe instanceOf<IdentifierReference>()
        }
        (call.args.single() as IdentifierReference).nameInSource.single() shouldBe "thing"

        emptysub.statements.size shouldBe 1
        emptysub.statements.single() shouldBe instanceOf<Return>()
    }

    test("ubyte[] array parameters") {
        val text = """
            main {
                sub start() {
                    ubyte[] array = [1,2,3]
                    
                    asmfunc(array)
                    asmfunc($2000)
                    asmfunc("zzzz")
                    func(array)
                    func($2000)
                    func("zzzz")
                }
                
                asmsub asmfunc(ubyte[] thing @AY) {
                }

                sub func(ubyte[] thing) {
                }
            }
        """

        val result = compileText(C64Target(), false, text, writeAssembly = false)!!
        val mainBlock = result.compilerAst.entrypoint.definingBlock
        val asmfunc = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="asmfunc"}
        val func = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="func"}
        withClue("ubyte array param should have been replaced by UWORD pointer") {
            asmfunc.parameters.single().type shouldBe DataType.forDt(BaseDataType.UWORD)
            func.parameters.single().type shouldBe DataType.forDt(BaseDataType.UWORD)
        }
    }

    test("not ubyte[] array parameters not allowed") {
        val text = """
            main {
                sub start() {
                }
                
                asmsub func1(uword[] thing @AY) {
                }
              
                sub func(byte[] thing) {
                }
            }
        """

        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "pass-by-reference type can't be used"
        errors.errors[1] shouldContain "pass-by-reference type can't be used"
    }

    test("invalid number of args check on normal subroutine") {
        val text="""
            main {
              sub thing(ubyte a1, ubyte a2) {
              }
            
              sub start() {
                  thing(1)
                  thing(1,2)
                  thing(1,2,3)
              }
            }
        """

        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "7:25: invalid number of arguments"
        errors.errors[1] shouldContain "9:25: invalid number of arguments"
    }

    test("invalid number of args check on asm subroutine") {
        val text="""
            main {
              asmsub thing(ubyte a1 @A, ubyte a2 @Y) {
              }
            
              sub start() {
                  thing(1)
                  thing(1,2)
                  thing(1,2,3)
              }
            }
        """

        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "7:25: invalid number of arguments"
        errors.errors[1] shouldContain "9:25: invalid number of arguments"
    }

    test("invalid number of args check on call to label and builtin func") {
        val text="""
            main {
        label:            
              sub start() {
                  label()
                  label(1)
                  void cmp(22,44)
                  void cmp(11)
              }
            }
        """

        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "cannot use arguments"
        errors.errors[1] shouldContain "invalid number of arguments"
    }

    test("fallthrough prevented") {
        val text = """
            main {
                sub start() {
                    func(1, 2, 3)

                    sub func(ubyte a, ubyte b, ubyte c) {
                        a++
                    }
                }
            }
        """
        val result = compileText(C64Target(), false, text, writeAssembly = true)!!
        val stmts = result.compilerAst.entrypoint.statements

        stmts.last() shouldBe instanceOf<Subroutine>()
        stmts.dropLast(1).last() shouldBe instanceOf<Return>()  // this prevents the fallthrough
        stmts.dropLast(2).last() shouldBe instanceOf<IFunctionCall>()
    }

    test("multi-value returns from regular (non-asmsub) subroutines") {
        val src= """
main {
    sub start() {
        uword a
        ubyte b
        bool c
        a, b, c = multi()
        a, void, c = multi()
        void, b, c = multi()
        void multi()
    }

    sub multi() -> uword, ubyte, bool {
        return 12345, 66, true
    }
}"""
        compileText(C64Target(), false, src, writeAssembly = true).shouldNotBeNull()
        // compileText(VMTarget(), false, src, writeAssembly = true).shouldNotBeNull()  TODO("multi-value return ; unittest")
    }
})
