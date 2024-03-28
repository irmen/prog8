package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.code.ast.PtAssignTarget
import prog8.code.ast.PtAssignment
import prog8.code.core.DataType
import prog8.code.core.SourceCode
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
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

    test("multi-assign from asmsub") {
        val src="""
main {
    sub start() {
        bool @shared flag

        cx16.r0L, flag = test2(12345, 5566, flag, -42)
        cx16.r1, flag = test3()
    }

    asmsub test2(uword arg @AY, uword arg2 @R1, bool flag @Pc, byte value @X) -> ubyte @A, bool @Pc {
        %asm {{
            txa
            sec
            rts
        }}
    }

    asmsub test3() -> uword @R1, bool @Pc {
        %asm {{
            lda  #0
            ldy  #0
            rts
        }}
    }
}"""
        val errors = ErrorReporterForTests()
        val result = compileText(Cx16Target(), false, src, errors, true)!!
        errors.errors.size shouldBe 0
        val start = result.codegenAst!!.entrypoint()!!
        start.children.size shouldBe 5
        val a1_1 = start.children[2] as PtAssignment
        val a1_2 = start.children[3] as PtAssignment
        a1_1.multiTarget shouldBe true
        a1_2.multiTarget shouldBe true
        (a1_1.children[0] as PtAssignTarget).identifier!!.name shouldBe("cx16.r0L")
        (a1_1.children[1] as PtAssignTarget).identifier!!.name shouldBe("p8b_main.p8s_start.p8v_flag")
        (a1_2.children[0] as PtAssignTarget).identifier!!.name shouldBe("cx16.r1")
        (a1_2.children[1] as PtAssignTarget).identifier!!.name shouldBe("p8b_main.p8s_start.p8v_flag")

        errors.clear()
        val result2=compileText(VMTarget(), false, src, errors, true)!!
        errors.errors.size shouldBe 0
        val start2 = result2.codegenAst!!.entrypoint()!!
        start2.children.size shouldBe 5
        val a2_1 = start2.children[2] as PtAssignment
        val a2_2 = start2.children[3] as PtAssignment
        a2_1.multiTarget shouldBe true
        a2_2.multiTarget shouldBe true
        (a2_1.children[0] as PtAssignTarget).identifier!!.name shouldBe("cx16.r0L")
        (a2_1.children[1] as PtAssignTarget).identifier!!.name shouldBe("main.start.flag")
        (a2_2.children[0] as PtAssignTarget).identifier!!.name shouldBe("cx16.r1")
        (a2_2.children[1] as PtAssignTarget).identifier!!.name shouldBe("main.start.flag")
    }

    test("multi-assign from romsub") {
        val src="""
main {
    sub start() {
        bool @shared flag

        flag = test(42)
        cx16.r0L, flag = test2(12345, 5566, flag, -42)
        cx16.r1, flag = test3()
    }

    romsub ${'$'}8000 = test(ubyte arg @A) -> bool @Pc
    romsub ${'$'}8002 = test2(uword arg @AY, uword arg2 @R1, bool flag @Pc, byte value @X) -> ubyte @A, bool @Pc
    romsub ${'$'}8003 = test3() -> uword @R1, bool @Pc
}"""

        val errors = ErrorReporterForTests()
        val result = compileText(Cx16Target(), false, src, errors, true)!!
        errors.errors.size shouldBe 0
        val start = result.codegenAst!!.entrypoint()!!
        start.children.size shouldBe 5
        val a1_1 = start.children[2] as PtAssignment
        val a1_2 = start.children[3] as PtAssignment
        a1_1.multiTarget shouldBe true
        a1_2.multiTarget shouldBe true
        (a1_1.children[0] as PtAssignTarget).identifier!!.name shouldBe("cx16.r0L")
        (a1_1.children[1] as PtAssignTarget).identifier!!.name shouldBe("p8b_main.p8s_start.p8v_flag")
        (a1_2.children[0] as PtAssignTarget).identifier!!.name shouldBe("cx16.r1")
        (a1_2.children[1] as PtAssignTarget).identifier!!.name shouldBe("p8b_main.p8s_start.p8v_flag")

        errors.clear()
        val result2=compileText(VMTarget(), false, src, errors, true)!!
        errors.errors.size shouldBe 0
        val start2 = result2.codegenAst!!.entrypoint()!!
        start2.children.size shouldBe 5
        val a2_1 = start2.children[2] as PtAssignment
        val a2_2 = start2.children[3] as PtAssignment
        a2_1.multiTarget shouldBe true
        a2_2.multiTarget shouldBe true
        (a2_1.children[0] as PtAssignTarget).identifier!!.name shouldBe("cx16.r0L")
        (a2_1.children[1] as PtAssignTarget).identifier!!.name shouldBe("main.start.flag")
        (a2_2.children[0] as PtAssignTarget).identifier!!.name shouldBe("cx16.r1")
        (a2_2.children[1] as PtAssignTarget).identifier!!.name shouldBe("main.start.flag")
    }
})
