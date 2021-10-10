package prog8tests

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.*
import prog8.compiler.*
import prog8.compiler.target.C64Target
import prog8.compiler.target.c64.C64MachineDefinition
import prog8.compiler.target.cpu6502.codegen.AsmGen
import prog8tests.helpers.DummyFunctions
import prog8tests.helpers.DummyMemsizer
import java.nio.file.Path


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestAsmGen6502 {

    private fun createTestProgram(): Program {
        /*
main  {

label_outside:
    uword var_outside

    sub start () {
        uword localvar = 1234
        uword tgt

locallabel:
        tgt = localvar
        tgt = &locallabel
        tgt = &var_outside
        tgt = &label_outside
        tgt = &main.start.localvar
        tgt = &main.start.locallabel
        tgt = &main.var_outside
        tgt = &main.label_outside
    }
}

         */
        val varInSub = VarDecl(VarDeclType.VAR, DataType.UWORD, ZeropageWish.DONTCARE, null, "localvar", NumericLiteralValue.optimalInteger(1234, Position.DUMMY), false, false, false, Position.DUMMY)
        val var2InSub = VarDecl(VarDeclType.VAR, DataType.UWORD, ZeropageWish.DONTCARE, null, "tgt", null, false, false, false, Position.DUMMY)
        val labelInSub = Label("locallabel", Position.DUMMY)

        val tgt = AssignTarget(IdentifierReference(listOf("tgt"), Position.DUMMY), null, null, Position.DUMMY)
        val assign1 = Assignment(tgt, IdentifierReference(listOf("localvar"), Position.DUMMY), Position.DUMMY)
        val assign2 = Assignment(tgt, AddressOf(IdentifierReference(listOf("locallabel"), Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val assign3 = Assignment(tgt, AddressOf(IdentifierReference(listOf("var_outside"), Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val assign4 = Assignment(tgt, AddressOf(IdentifierReference(listOf("label_outside"), Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val assign5 = Assignment(tgt, AddressOf(IdentifierReference(listOf("main","start","localvar"), Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val assign6 = Assignment(tgt, AddressOf(IdentifierReference(listOf("main","start","locallabel"), Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val assign7 = Assignment(tgt, AddressOf(IdentifierReference(listOf("main","var_outside"), Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val assign8 = Assignment(tgt, AddressOf(IdentifierReference(listOf("main","label_outside"), Position.DUMMY), Position.DUMMY), Position.DUMMY)

        val statements = mutableListOf(varInSub, var2InSub, labelInSub, assign1, assign2, assign3, assign4, assign5, assign6, assign7, assign8)
        val subroutine = Subroutine("start", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, statements, Position.DUMMY)
        val labelInBlock = Label("label_outside", Position.DUMMY)
        val varInBlock = VarDecl(VarDeclType.VAR, DataType.UWORD, ZeropageWish.DONTCARE, null, "var_outside", null, false, false, false, Position.DUMMY)
        val block = Block("main", null, mutableListOf(labelInBlock, varInBlock, subroutine), false, Position.DUMMY)

        val module = Module("test", mutableListOf(block), Position.DUMMY, null)
        val program = Program("test", DummyFunctions, DummyMemsizer)
            .addModule(module)
        module.linkParents(program.namespace)
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

        assertThat(asmgen.asmSymbolName("name"), equalTo("name"))
        assertThat(asmgen.asmSymbolName("<name>"), equalTo("prog8_name"))
        assertThat(asmgen.asmSymbolName(RegisterOrPair.R15), equalTo("cx16.r15"))
        assertThat(asmgen.asmSymbolName(listOf("a", "b", "name")), equalTo("a.b.name"))
        assertThat(asmgen.asmVariableName("name"), equalTo("name"))
        assertThat(asmgen.asmVariableName("<name>"), equalTo("prog8_name"))
        assertThat(asmgen.asmVariableName(listOf("a", "b", "name")), equalTo("a.b.name"))
    }

    @Test
    fun testSymbolNameFromVarIdentifier() {
        val program = createTestProgram()
        val asmgen = createTestAsmGen(program)
        val sub = program.entrypoint

        // local variable
        val localvarIdent = sub.statements.filterIsInstance<Assignment>().first { it.value is IdentifierReference }.value as IdentifierReference
        assertThat(asmgen.asmSymbolName(localvarIdent), equalTo("localvar"))
        assertThat(asmgen.asmVariableName(localvarIdent), equalTo("localvar"))
        val localvarIdentScoped = (sub.statements.filterIsInstance<Assignment>().first { (it.value as? AddressOf)?.identifier?.nameInSource==listOf("main", "start", "localvar") }.value as AddressOf).identifier
        assertThat(asmgen.asmSymbolName(localvarIdentScoped), equalTo("main.start.localvar"))
        assertThat(asmgen.asmVariableName(localvarIdentScoped), equalTo("main.start.localvar"))

        // variable from outer scope (note that for Variables, no scoping prefix symbols are required,
        //   because they're not outputted as locally scoped symbols for the assembler
        val scopedVarIdent = (sub.statements.filterIsInstance<Assignment>().first { (it.value as? AddressOf)?.identifier?.nameInSource==listOf("var_outside") }.value as AddressOf).identifier
        assertThat(asmgen.asmSymbolName(scopedVarIdent), equalTo("main.var_outside"))
        assertThat(asmgen.asmVariableName(scopedVarIdent), equalTo("var_outside"))
        val scopedVarIdentScoped = (sub.statements.filterIsInstance<Assignment>().first { (it.value as? AddressOf)?.identifier?.nameInSource==listOf("main", "var_outside") }.value as AddressOf).identifier
        assertThat(asmgen.asmSymbolName(scopedVarIdentScoped), equalTo("main.var_outside"))
        assertThat(asmgen.asmVariableName(scopedVarIdentScoped), equalTo("main.var_outside"))
    }

    @Test
    fun testSymbolNameFromLabelIdentifier() {
        val program = createTestProgram()
        val asmgen = createTestAsmGen(program)
        val sub = program.entrypoint

        // local label
        val localLabelIdent = (sub.statements.filterIsInstance<Assignment>().first { (it.value as? AddressOf)?.identifier?.nameInSource==listOf("locallabel") }.value as AddressOf).identifier
        assertThat(asmgen.asmSymbolName(localLabelIdent), equalTo("_locallabel"))
        assertThat("as a variable it uses different naming rules (no underscore prefix)", asmgen.asmVariableName(localLabelIdent), equalTo("locallabel"))
        val localLabelIdentScoped = (sub.statements.filterIsInstance<Assignment>().first { (it.value as? AddressOf)?.identifier?.nameInSource==listOf("main","start","locallabel") }.value as AddressOf).identifier
        assertThat(asmgen.asmSymbolName(localLabelIdentScoped), equalTo("main.start._locallabel"))
        assertThat("as a variable it uses different naming rules (no underscore prefix)", asmgen.asmVariableName(localLabelIdentScoped), equalTo("main.start.locallabel"))

        // label from outer scope needs sope prefixes because it is outputted as a locally scoped symbol for the assembler
        val scopedLabelIdent = (sub.statements.filterIsInstance<Assignment>().first { (it.value as? AddressOf)?.identifier?.nameInSource==listOf("label_outside") }.value as AddressOf).identifier
        assertThat(asmgen.asmSymbolName(scopedLabelIdent), equalTo("main._label_outside"))
        assertThat("as a variable it uses different naming rules (no underscore prefix)", asmgen.asmVariableName(scopedLabelIdent), equalTo("label_outside"))
        val scopedLabelIdentScoped = (sub.statements.filterIsInstance<Assignment>().first { (it.value as? AddressOf)?.identifier?.nameInSource==listOf("main","label_outside") }.value as AddressOf).identifier
        assertThat(asmgen.asmSymbolName(scopedLabelIdentScoped), equalTo("main._label_outside"))
        assertThat("as a variable it uses different naming rules (no underscore prefix)", asmgen.asmVariableName(scopedLabelIdentScoped), equalTo("main.label_outside"))
    }
}
