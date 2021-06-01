package prog8tests

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import prog8.ast.IBuiltinFunctions
import prog8.ast.IMemSizer
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.*
import prog8.compiler.target.C64Target
import prog8.compiler.target.c64.C64MachineDefinition
import prog8.compiler.target.cpu6502.codegen.AsmGen
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestAsmGen6502 {
    private class DummyFunctions: IBuiltinFunctions {
        override val names: Set<String> = emptySet()
        override val purefunctionNames: Set<String> = emptySet()
        override fun constValue(name: String, args: List<Expression>, position: Position, memsizer: IMemSizer): NumericLiteralValue? = null
        override fun returnType(name: String, args: MutableList<Expression>) = InferredTypes.InferredType.unknown()
    }

    private class DummyMemsizer: IMemSizer {
        override fun memorySize(dt: DataType): Int = 0
    }

    private fun createTestProgram(): Program {
        /*
block1  {

    sub test () {
        uword var_in_sub = 1234
        uword var2_in_sub

label_in_sub:
        var2_in_sub = var_in_sub
        var2_in_sub = &label_in_sub
    }
}

         */
        val varInSub = VarDecl(VarDeclType.VAR, DataType.UWORD, ZeropageWish.DONTCARE, null, "var_in_sub", NumericLiteralValue.optimalInteger(1234, Position.DUMMY), false, false, false, Position.DUMMY)
        val var2InSub = VarDecl(VarDeclType.VAR, DataType.UWORD, ZeropageWish.DONTCARE, null, "var2_in_sub", null, false, false, false, Position.DUMMY)
        val labelInSub = Label("label_in_sub", Position.DUMMY)

        val tgt = AssignTarget(IdentifierReference(listOf("var2_in_sub"), Position.DUMMY), null, null, Position.DUMMY)
        val assign1 = Assignment(tgt, IdentifierReference(listOf("var_in_sub"), Position.DUMMY), Position.DUMMY)
        val assign2 = Assignment(tgt, AddressOf(IdentifierReference(listOf("label_in_sub"), Position.DUMMY), Position.DUMMY), Position.DUMMY)

        val statements = mutableListOf(varInSub, var2InSub, labelInSub, assign1, assign2)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, statements, Position.DUMMY)
        val block1 = Block("block1", null, mutableListOf(subroutine), false, Position.DUMMY)

        val block2 = Block("block2", null, mutableListOf(), false, Position.DUMMY)

        val module = Module("test", mutableListOf(block1, block2), Position.DUMMY, false, Path.of(""))
        module.linkParents(ParentSentinel)
        val program = Program("test", mutableListOf(module), DummyFunctions(), DummyMemsizer())
        return program
    }

    private fun createTestAsmGen(program: Program): AsmGen {
        val errors = ErrorReporter()
        val options = CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.FULL, emptyList(), false, true, C64Target)
        val zp = C64MachineDefinition.C64Zeropage(options)
        val asmgen = AsmGen(program, errors, zp, options, C64Target, Path.of("."))
        return asmgen
    }

    @Test
    fun testSymbolNameFromStrings() {
        val program = createTestProgram()
        val asmgen = createTestAsmGen(program)

        printAst(program)       // TODO weg

        assertThat(asmgen.asmSymbolName("name"), equalTo("name"))
        assertThat(asmgen.asmSymbolName("<name>"), equalTo("prog8_name"))
        assertThat(asmgen.asmSymbolName(RegisterOrPair.R15), equalTo("cx16.r15"))
        assertThat(asmgen.asmSymbolName(listOf("a", "b", "name")), equalTo("a.b.name"))
        assertThat(asmgen.asmVariableName("name"), equalTo("name"))
        assertThat(asmgen.asmVariableName("<name>"), equalTo("prog8_name"))
        assertThat(asmgen.asmVariableName(listOf("a", "b", "name")), equalTo("a.b.name"))
    }

    @Test
    @Disabled("TODO") // TODO
    fun testSymbolNameFromVarIdentifier() {
        val program = createTestProgram()
        val asmgen = createTestAsmGen(program)

        val varIdent = IdentifierReference(listOf("variable"), Position.DUMMY)
        assertThat(asmgen.asmSymbolName(varIdent), equalTo("variable"))
        assertThat(asmgen.asmVariableName(varIdent), equalTo("variable"))

        // TODO also do a scoped reference
//        val scopedVarIdent = IdentifierReference(listOf("scope", "variable"), Position.DUMMY)
//        assertThat(asmgen.asmSymbolName(scopedVarIdent), equalTo("scope.variable"))
//        assertThat(asmgen.asmVariableName(scopedVarIdent), equalTo("scope.variable"))
    }

    @Test
    @Disabled("TODO") // TODO
    fun testSymbolNameFromLabelIdentifier() {
        val program = createTestProgram()
        val asmgen = createTestAsmGen(program)

        val labelIdent = IdentifierReference(listOf("label"), Position.DUMMY)
        assertThat(asmgen.asmSymbolName(labelIdent), equalTo("_label"))
        assertThat(asmgen.asmVariableName(labelIdent), equalTo("_label"))

        // TODO also do a scoped reference
//        val scopedLabelIdent = IdentifierReference(listOf("scope", "label"), Position.DUMMY)
//        assertThat(asmgen.asmSymbolName(scopedLabelIdent), equalTo("scope._label"))
//        assertThat(asmgen.asmVariableName(scopedLabelIdent), equalTo("scope._label"))
    }
}
