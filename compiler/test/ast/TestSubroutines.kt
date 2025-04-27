package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.code.ast.PtAssignTarget
import prog8.code.ast.PtAssignment
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.source.SourceCode
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.parser.Prog8Parser.parseModule
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText


class TestSubroutines: FunSpec({

    val outputDir = tempdir().toPath()

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
        asmfunc.parameters.single().type shouldBe DataType.arrayFor(BaseDataType.UBYTE)
        asmfunc.statements.isEmpty() shouldBe true
        func.isAsmSubroutine shouldBe false
        func.parameters.single().type shouldBe DataType.arrayFor(BaseDataType.UBYTE)
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
        compileText(Cx16Target(), false, src, outputDir, errors, false) shouldBe null
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
        uword[] pointers = [1234,6789]
        void call(func)        ; ok
        void call(12345)       ; ok
        void call(pointers[1]) ; ok
        void call(cx16.r0+10)  ; ok
        cx16.r0 = call(func)   ; ok
        void call(&start)      ; error
        void call(start)       ; error
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, src, outputDir, errors, false) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "can't call this indirectly"
        errors.errors[1] shouldContain "can't call this indirectly"
    }

    test("multi-assign from asmsub") {
        val src="""
main {
    sub start() {
        bool @shared flag
        ubyte @shared bytevar

        cx16.r0L, flag = test2(12345, 5566, flag, -42)
        cx16.r1, flag, bytevar = test3()
        cx16.r1, void, bytevar = test3()
        void, void, void = test3()
    }

    asmsub test2(uword arg @AY, uword arg2 @R1, bool flag @Pc, byte value @X) -> ubyte @A, bool @Pc {
        %asm {{
            txa
            sec
            rts
        }}
    }

    asmsub test3() -> uword @R1, bool @Pc, ubyte @X {
        %asm {{
            lda  #0
            ldy  #0
            ldx  #0
            rts
        }}
    }
}"""
        compileText(C64Target(), false, src, outputDir, writeAssembly = true) shouldNotBe null
        val errors = ErrorReporterForTests()
        val result = compileText(Cx16Target(), false, src, outputDir, errors, true)!!
        errors.errors.size shouldBe 0
        val start = result.codegenAst!!.entrypoint()!!
        start.children.size shouldBe 9
        val a1_1 = start.children[4] as PtAssignment
        val a1_2 = start.children[5] as PtAssignment
        val a1_3 = start.children[6] as PtAssignment
        val a1_4 = start.children[7] as PtAssignment
        a1_1.multiTarget shouldBe true
        a1_2.multiTarget shouldBe true
        a1_3.multiTarget shouldBe true
        a1_4.multiTarget shouldBe true
        a1_1.children.size shouldBe 3
        a1_2.children.size shouldBe 4
        a1_3.children.size shouldBe 4
        a1_4.children.size shouldBe 4
        (a1_1.children[0] as PtAssignTarget).identifier!!.name shouldBe("cx16.r0L")
        (a1_1.children[1] as PtAssignTarget).identifier!!.name shouldBe("p8b_main.p8s_start.p8v_flag")
        (a1_2.children[0] as PtAssignTarget).identifier!!.name shouldBe("cx16.r1")
        (a1_2.children[1] as PtAssignTarget).identifier!!.name shouldBe("p8b_main.p8s_start.p8v_flag")
        (a1_2.children[2] as PtAssignTarget).identifier!!.name shouldBe("p8b_main.p8s_start.p8v_bytevar")
        (a1_3.children[0] as PtAssignTarget).identifier!!.name shouldBe("cx16.r1")
        (a1_3.children[1] as PtAssignTarget).void shouldBe true
        (a1_3.children[2] as PtAssignTarget).identifier!!.name shouldBe("p8b_main.p8s_start.p8v_bytevar")
        (a1_4.children[0] as PtAssignTarget).void shouldBe true
        (a1_4.children[1] as PtAssignTarget).void shouldBe true
        (a1_4.children[2] as PtAssignTarget).void shouldBe true
    }

    test("multi-assign from extsub") {
        val src="""
main {
    sub start() {
        bool @shared flag
        ubyte @shared bytevar 

        flag = test(42)
        cx16.r0L, flag = test2(12345, 5566, flag, -42)
        cx16.r1, flag, bytevar = test3()
        cx16.r1, void, bytevar = test3()
        void, void, void = test3()
    }

    extsub ${'$'}8000 = test(ubyte arg @A) -> bool @Pc
    extsub ${'$'}8002 = test2(uword arg @AY, uword arg2 @R1, bool flag @Pc, byte value @X) -> ubyte @A, bool @Pc
    extsub ${'$'}8003 = test3() -> uword @R1, bool @Pc, ubyte @X
}"""

        compileText(C64Target(), false, src, outputDir, writeAssembly = true) shouldNotBe null
        val errors = ErrorReporterForTests()
        val result = compileText(Cx16Target(), false, src, outputDir, errors, true)!!
        errors.errors.size shouldBe 0
        val start = result.codegenAst!!.entrypoint()!!
        start.children.size shouldBe 10
        val a1_1 = start.children[5] as PtAssignment
        val a1_2 = start.children[6] as PtAssignment
        val a1_3 = start.children[7] as PtAssignment
        val a1_4 = start.children[8] as PtAssignment
        a1_1.multiTarget shouldBe true
        a1_2.multiTarget shouldBe true
        a1_3.multiTarget shouldBe true
        a1_4.multiTarget shouldBe true
        a1_1.children.size shouldBe 3
        a1_2.children.size shouldBe 4
        a1_3.children.size shouldBe 4
        a1_4.children.size shouldBe 4
        (a1_1.children[0] as PtAssignTarget).identifier!!.name shouldBe("cx16.r0L")
        (a1_1.children[1] as PtAssignTarget).identifier!!.name shouldBe("p8b_main.p8s_start.p8v_flag")
        (a1_2.children[0] as PtAssignTarget).identifier!!.name shouldBe("cx16.r1")
        (a1_2.children[1] as PtAssignTarget).identifier!!.name shouldBe("p8b_main.p8s_start.p8v_flag")
        (a1_2.children[2] as PtAssignTarget).identifier!!.name shouldBe("p8b_main.p8s_start.p8v_bytevar")
        (a1_3.children[0] as PtAssignTarget).identifier!!.name shouldBe("cx16.r1")
        (a1_3.children[1] as PtAssignTarget).void shouldBe true
        (a1_3.children[2] as PtAssignTarget).identifier!!.name shouldBe("p8b_main.p8s_start.p8v_bytevar")
        (a1_4.children[0] as PtAssignTarget).void shouldBe true
        (a1_4.children[1] as PtAssignTarget).void shouldBe true
        (a1_4.children[2] as PtAssignTarget).void shouldBe true
    }

    test("extsub with (non)const addresses") {
        val src="""
main {
    const uword address = ${'$'}2000
    uword nonconst = ${'$'}3000

    extsub address = foo1()
    extsub address+3 = foo2()
    extsub nonconst = foo3()

    sub start() {
    }
}"""

        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), false, src, outputDir, errors, false) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain ":8:5: address must be a constant"
    }
})
