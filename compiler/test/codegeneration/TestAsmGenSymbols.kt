package prog8tests.codegeneration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.*
import prog8.code.core.*
import prog8.code.target.C64Target
import prog8.code.target.c64.C64Zeropage
import prog8.codegen.cpu6502.AsmGen
import prog8.compiler.astprocessing.SymbolTableMaker
import prog8.parser.SourceCode
import prog8tests.helpers.DummyFunctions
import prog8tests.helpers.DummyMemsizer
import prog8tests.helpers.DummyStringEncoder
import prog8tests.helpers.ErrorReporterForTests

class TestAsmGenSymbols: StringSpec({
    fun createTestProgram(): Program {
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
        val varInSub = VarDecl(VarDeclType.VAR, VarDeclOrigin.USERCODE, DataType.UWORD, ZeropageWish.DONTCARE, null, "localvar", NumericLiteral.optimalInteger(1234, Position.DUMMY), false, false, null, Position.DUMMY)
        val var2InSub = VarDecl(VarDeclType.VAR, VarDeclOrigin.USERCODE, DataType.UWORD, ZeropageWish.DONTCARE, null, "tgt", null, false, false, null, Position.DUMMY)
        val labelInSub = Label("locallabel", Position.DUMMY)

        val tgt = AssignTarget(IdentifierReference(listOf("tgt"), Position.DUMMY), null, null, Position.DUMMY)
        val assign1 = Assignment(tgt, IdentifierReference(listOf("localvar"), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val assign2 = Assignment(tgt, AddressOf(IdentifierReference(listOf("locallabel"), Position.DUMMY), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val assign3 = Assignment(tgt, AddressOf(IdentifierReference(listOf("var_outside"), Position.DUMMY), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val assign4 = Assignment(tgt, AddressOf(IdentifierReference(listOf("label_outside"), Position.DUMMY), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val assign5 = Assignment(tgt, AddressOf(IdentifierReference(listOf("main","start","localvar"), Position.DUMMY), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val assign6 = Assignment(tgt, AddressOf(IdentifierReference(listOf("main","start","locallabel"), Position.DUMMY), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val assign7 = Assignment(tgt, AddressOf(IdentifierReference(listOf("main","var_outside"), Position.DUMMY), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val assign8 = Assignment(tgt, AddressOf(IdentifierReference(listOf("main","label_outside"), Position.DUMMY), Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)

        val statements = mutableListOf(varInSub, var2InSub, labelInSub, assign1, assign2, assign3, assign4, assign5, assign6, assign7, assign8)
        val subroutine = Subroutine("start", mutableListOf(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, statements, Position.DUMMY)
        val labelInBlock = Label("label_outside", Position.DUMMY)
        val varInBlock = VarDecl(VarDeclType.VAR, VarDeclOrigin.USERCODE, DataType.UWORD, ZeropageWish.DONTCARE, null, "var_outside", null, false, false, null, Position.DUMMY)
        val block = Block("main", null, mutableListOf(labelInBlock, varInBlock, subroutine), false, Position.DUMMY)

        val module = Module(mutableListOf(block), Position.DUMMY, SourceCode.Generated("test"))
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder).addModule(module)

        return program
    }

    fun createTestAsmGen(program: Program): AsmGen {
        val errors = ErrorReporterForTests()
        val options = CompilationOptions(OutputType.RAW, CbmPrgLauncherType.NONE, ZeropageType.FULL, emptyList(), false, true, C64Target())
        options.compTarget.machine.zeropage = C64Zeropage(options)
        val st = SymbolTableMaker().makeFrom(program)
        return AsmGen(program, errors, st, options)
    }

    "symbol and variable names from strings" {
        val program = createTestProgram()
        val asmgen = createTestAsmGen(program)
        asmgen.asmSymbolName("name") shouldBe "name"
        asmgen.asmSymbolName("name") shouldBe "name"
        asmgen.asmSymbolName("<name>") shouldBe "prog8_name"
        asmgen.asmSymbolName(RegisterOrPair.R15) shouldBe "cx16.r15"
        asmgen.asmSymbolName(listOf("a", "b", "name")) shouldBe "a.b.name"
        asmgen.asmVariableName("name") shouldBe "name"
        asmgen.asmVariableName("<name>") shouldBe "prog8_name"
        asmgen.asmVariableName(listOf("a", "b", "name")) shouldBe "a.b.name"
    }

    "symbol and variable names from variable identifiers" {
        val program = createTestProgram()
        val asmgen = createTestAsmGen(program)
        val sub = program.entrypoint

        val localvarIdent = sub.statements.asSequence().filterIsInstance<Assignment>().first { it.value is IdentifierReference }.value as IdentifierReference
        asmgen.asmSymbolName(localvarIdent) shouldBe "localvar"
        asmgen.asmVariableName(localvarIdent) shouldBe "localvar"
        val localvarIdentScoped = (sub.statements.asSequence().filterIsInstance<Assignment>().first { (it.value as? AddressOf)?.identifier?.nameInSource==listOf("main", "start", "localvar") }.value as AddressOf).identifier
        asmgen.asmSymbolName(localvarIdentScoped) shouldBe "main.start.localvar"
        asmgen.asmVariableName(localvarIdentScoped) shouldBe "main.start.localvar"

        val scopedVarIdent = (sub.statements.asSequence().filterIsInstance<Assignment>().first { (it.value as? AddressOf)?.identifier?.nameInSource==listOf("var_outside") }.value as AddressOf).identifier
        asmgen.asmSymbolName(scopedVarIdent) shouldBe "var_outside"
        asmgen.asmVariableName(scopedVarIdent) shouldBe "var_outside"
        val scopedVarIdentScoped = (sub.statements.asSequence().filterIsInstance<Assignment>().first { (it.value as? AddressOf)?.identifier?.nameInSource==listOf("main", "var_outside") }.value as AddressOf).identifier
        asmgen.asmSymbolName(scopedVarIdentScoped) shouldBe "main.var_outside"
        asmgen.asmVariableName(scopedVarIdentScoped) shouldBe "main.var_outside"
    }

    "symbol and variable names from label identifiers" {
        val program = createTestProgram()
        val asmgen = createTestAsmGen(program)
        val sub = program.entrypoint

        val localLabelIdent = (sub.statements.asSequence().filterIsInstance<Assignment>().first { (it.value as? AddressOf)?.identifier?.nameInSource==listOf("locallabel") }.value as AddressOf).identifier
        asmgen.asmSymbolName(localLabelIdent) shouldBe "locallabel"
        asmgen.asmVariableName(localLabelIdent) shouldBe "locallabel"
        val localLabelIdentScoped = (sub.statements.asSequence().filterIsInstance<Assignment>().first { (it.value as? AddressOf)?.identifier?.nameInSource==listOf("main","start","locallabel") }.value as AddressOf).identifier
        asmgen.asmSymbolName(localLabelIdentScoped) shouldBe "main.start.locallabel"
        asmgen.asmVariableName(localLabelIdentScoped) shouldBe "main.start.locallabel"

        val scopedLabelIdent = (sub.statements.asSequence().filterIsInstance<Assignment>().first { (it.value as? AddressOf)?.identifier?.nameInSource==listOf("label_outside") }.value as AddressOf).identifier
        asmgen.asmSymbolName(scopedLabelIdent) shouldBe "label_outside"
        asmgen.asmVariableName(scopedLabelIdent) shouldBe "label_outside"
        val scopedLabelIdentScoped = (sub.statements.asSequence().filterIsInstance<Assignment>().first { (it.value as? AddressOf)?.identifier?.nameInSource==listOf("main","label_outside") }.value as AddressOf).identifier
        asmgen.asmSymbolName(scopedLabelIdentScoped) shouldBe "main.label_outside"
        asmgen.asmVariableName(scopedLabelIdentScoped) shouldBe "main.label_outside"
    }

    "asm names for hooks to zp temp vars" {
        /*
main {

    sub start() {
        prog8_lib.P8ZP_SCRATCH_REG = 1
        prog8_lib.P8ZP_SCRATCH_B1 = 1
        prog8_lib.P8ZP_SCRATCH_W1 = 1
        prog8_lib.P8ZP_SCRATCH_W2 = 1
         */
        val program = createTestProgram()
        val asmgen = createTestAsmGen(program)
        asmgen.asmSymbolName("prog8_lib.P8ZP_SCRATCH_REG") shouldBe "P8ZP_SCRATCH_REG"
        asmgen.asmSymbolName("prog8_lib.P8ZP_SCRATCH_W2") shouldBe "P8ZP_SCRATCH_W2"
        asmgen.asmSymbolName(listOf("prog8_lib","P8ZP_SCRATCH_REG")) shouldBe "P8ZP_SCRATCH_REG"
        asmgen.asmSymbolName(listOf("prog8_lib","P8ZP_SCRATCH_W2")) shouldBe "P8ZP_SCRATCH_W2"
        val id1 = IdentifierReference(listOf("prog8_lib","P8ZP_SCRATCH_REG"), Position.DUMMY)
        id1.linkParents(program.toplevelModule)
        val id2 = IdentifierReference(listOf("prog8_lib","P8ZP_SCRATCH_W2"), Position.DUMMY)
        id2.linkParents(program.toplevelModule)
        asmgen.asmSymbolName(id1) shouldBe "P8ZP_SCRATCH_REG"
        asmgen.asmSymbolName(id2) shouldBe "P8ZP_SCRATCH_W2"
    }
})
