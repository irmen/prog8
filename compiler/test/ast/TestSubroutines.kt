package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.code.core.DataType
import prog8.code.core.SourceCode
import prog8.code.target.Cx16Target
import prog8.parser.Prog8Parser.parseModule
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText


class TestSubroutines: FunSpec({

    test("stringParameter AcceptedInParser") {
        // note: the *parser* should accept this as it is valid *syntax*,
        // however, the compiler itself may or may not complain about it later.
        val text = """
            main {
                asmsub asmfunc(str thing @AY) {
                }

                sub func(str thing) {
                }
            }
        """
        val src = SourceCode.Text(text)
        val module = parseModule(src)
        val mainBlock = module.statements.single() as Block
        val asmfunc = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="asmfunc"}
        val func = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="func"}
        asmfunc.isAsmSubroutine shouldBe true
        asmfunc.parameters.single().type shouldBe DataType.STR
        asmfunc.statements.isEmpty() shouldBe true
        func.isAsmSubroutine shouldBe false
        func.parameters.single().type shouldBe DataType.STR
        func.statements.isEmpty() shouldBe true
    }

    test("arrayParameter AcceptedInParser") {
        // note: the *parser* should accept this as it is valid *syntax*,
        // however, the compiler itself may or may not complain about it later.
        val text = """
            main {
                asmsub asmfunc(ubyte[] thing @AY) {
                }

                sub func(ubyte[22] thing) {
                }
            }
        """
        val src = SourceCode.Text(text)
        val module = parseModule(src)
        val mainBlock = module.statements.single() as Block
        val asmfunc = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="asmfunc"}
        val func = mainBlock.statements.filterIsInstance<Subroutine>().single { it.name=="func"}
        asmfunc.isAsmSubroutine shouldBe true
        asmfunc.parameters.single().type shouldBe DataType.ARRAY_UB
        asmfunc.statements.isEmpty() shouldBe true
        func.isAsmSubroutine shouldBe false
        func.parameters.single().type shouldBe DataType.ARRAY_UB
        func.statements.isEmpty() shouldBe true
    }

    test("cannot call a subroutine via pointer") {
        val src="""
main {
    sub start() {
        uword func = 12345
        func()              ; error
        func(1,2,3)         ; error
        cx16.r0 = func()    ; error
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, src, errors, false) shouldBe null
        errors.errors.size shouldBe 3
        errors.errors[0] shouldContain "cannot call that"
        errors.errors[1] shouldContain "cannot call that"
        errors.errors[2] shouldContain "cannot call that"
    }

    test("can call a subroutine pointer using call") {
        val src="""
main {
    sub start() {
        uword func = 12345
        void call(func)        ; ok
        void call(12345)       ; ok
        cx16.r0 = call(func)   ; ok
        void call(&start)      ; error
        void call(start)       ; error
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, src, errors, false) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "can't call this indirectly"
        errors.errors[1] shouldContain "can't call this indirectly"
    }
})
