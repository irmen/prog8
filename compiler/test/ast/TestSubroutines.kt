package prog8tests.ast

import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.shouldBe
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.code.core.DataType
import prog8.code.core.SourceCode
import prog8.parser.Prog8Parser.parseModule


class TestSubroutines: AnnotationSpec() {

    @Test
    fun stringParameterAcceptedInParser() {
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

    @Test
    fun arrayParameterAcceptedInParser() {
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
}
