import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import prog8.code.SymbolTableMaker
import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.target.VMTarget
import prog8.codegen.vm.VmAssemblyProgram
import prog8.codegen.vm.VmCodeGen
import prog8.intermediate.IRSubroutine
import prog8.intermediate.Opcode

class TestVmCodeGen: FunSpec({

    fun getTestOptions(): CompilationOptions {
        val target = VMTarget()
        return CompilationOptions(
            OutputType.RAW,
            CbmPrgLauncherType.NONE,
            ZeropageType.DONTUSE,
            zpReserved = emptyList(),
            floats = true,
            noSysInit = false,
            compTarget = target,
            loadAddress = target.machine.PROGRAM_LOAD_ADDRESS
        )
    }

    test("augmented assigns") {
//main {
//    sub start() {
//        ubyte[] particleX = [1,2,3]
//        ubyte[] particleDX = [1,2,3]
//        particleX[2] += particleDX[2]
//
//        word @shared xx = 1
//        xx = -xx
//        xx += 42
//        xx += cx16.r0
//    }
//}
        val codegen = VmCodeGen()
        val program = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val block = PtBlock("main", null, false, false, false, PtBlock.BlockAlignment.NONE, SourceCode.Generated("test"), Position.DUMMY)
        val sub = PtSub("start", emptyList(), null, Position.DUMMY)
        sub.add(PtVariable("pi", DataType.UBYTE, ZeropageWish.DONTCARE, PtNumber(DataType.UBYTE, 0.0, Position.DUMMY), null, Position.DUMMY))
        sub.add(PtVariable("particleX", DataType.ARRAY_UB, ZeropageWish.DONTCARE, null, 3u, Position.DUMMY))
        sub.add(PtVariable("particleDX", DataType.ARRAY_UB, ZeropageWish.DONTCARE, null, 3u, Position.DUMMY))
        sub.add(PtVariable("xx", DataType.WORD, ZeropageWish.DONTCARE, PtNumber(DataType.WORD, 1.0, Position.DUMMY), null, Position.DUMMY))

        val assign = PtAugmentedAssign("+=", Position.DUMMY)
        val target = PtAssignTarget(Position.DUMMY).also {
            val targetIdx = PtArrayIndexer(DataType.UBYTE, Position.DUMMY).also { idx ->
                idx.add(PtIdentifier("main.start.particleX", DataType.ARRAY_UB, Position.DUMMY))
                idx.add(PtNumber(DataType.UBYTE, 2.0, Position.DUMMY))
            }
            it.add(targetIdx)
        }
        val value = PtArrayIndexer(DataType.UBYTE, Position.DUMMY)
        value.add(PtIdentifier("main.start.particleDX", DataType.ARRAY_UB, Position.DUMMY))
        value.add(PtNumber(DataType.UBYTE, 2.0, Position.DUMMY))
        assign.add(target)
        assign.add(value)
        sub.add(assign)

        val prefixAssign = PtAugmentedAssign("-", Position.DUMMY)
        val prefixTarget = PtAssignTarget(Position.DUMMY).also {
            it.add(PtIdentifier("main.start.xx", DataType.WORD, Position.DUMMY))
        }
        prefixAssign.add(prefixTarget)
        prefixAssign.add(PtIdentifier("main.start.xx", DataType.WORD, Position.DUMMY))
        sub.add(prefixAssign)

        val numberAssign = PtAugmentedAssign("+=", Position.DUMMY)
        val numberAssignTarget = PtAssignTarget(Position.DUMMY).also {
            it.add(PtIdentifier("main.start.xx", DataType.WORD, Position.DUMMY))
        }
        numberAssign.add(numberAssignTarget)
        numberAssign.add(PtNumber(DataType.WORD, 42.0, Position.DUMMY))
        sub.add(numberAssign)

        val cxregAssign = PtAugmentedAssign("+=", Position.DUMMY)
        val cxregAssignTarget = PtAssignTarget(Position.DUMMY).also {
            it.add(PtIdentifier("main.start.xx", DataType.WORD, Position.DUMMY))
        }
        cxregAssign.add(cxregAssignTarget)
        cxregAssign.add(PtIdentifier("cx16.r0", DataType.UWORD, Position.DUMMY))
        sub.add(cxregAssign)

        block.add(sub)
        program.add(block)

        // define the "cx16.r0" virtual register
        val cx16block = PtBlock("cx16", null, false, false, false, PtBlock.BlockAlignment.NONE, SourceCode.Generated("test"), Position.DUMMY)
        cx16block.add(PtMemMapped("r0", DataType.UWORD, 100u, null, Position.DUMMY))
        program.add(cx16block)

        val options = getTestOptions()
        val st = SymbolTableMaker(program, options).make()
        val errors = ErrorReporterForTests()
        val result = codegen.generate(program, st, options, errors) as VmAssemblyProgram
        val irChunks = (result.irProgram.blocks.first().children.single() as IRSubroutine).chunks
        irChunks.size shouldBe 1
    }

    test("float comparison expressions against zero") {
//main {
//    sub start() {
//        float @shared f1
//
//        if f1==0
//            nop
//        if f1!=0
//            nop
//        if f1>0
//            nop
//        if f1<0
//            nop
//    }
//}
        val codegen = VmCodeGen()
        val program = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val block = PtBlock("main", null, false, false, false, PtBlock.BlockAlignment.NONE, SourceCode.Generated("test"), Position.DUMMY)
        val sub = PtSub("start", emptyList(), null, Position.DUMMY)
        sub.add(PtVariable("f1", DataType.FLOAT, ZeropageWish.DONTCARE, null, null, Position.DUMMY))
        val if1 = PtIfElse(Position.DUMMY)
        val cmp1 = PtBinaryExpression("==", DataType.UBYTE, Position.DUMMY)
        cmp1.add(PtIdentifier("main.start.f1", DataType.FLOAT, Position.DUMMY))
        cmp1.add(PtNumber(DataType.FLOAT, 0.0, Position.DUMMY))
        if1.add(cmp1)
        if1.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if1.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if1)
        val if2 = PtIfElse(Position.DUMMY)
        val cmp2 = PtBinaryExpression("!=", DataType.UBYTE, Position.DUMMY)
        cmp2.add(PtIdentifier("main.start.f1", DataType.FLOAT, Position.DUMMY))
        cmp2.add(PtNumber(DataType.FLOAT, 0.0, Position.DUMMY))
        if2.add(cmp2)
        if2.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if2.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if2)
        val if3 = PtIfElse(Position.DUMMY)
        val cmp3 = PtBinaryExpression("<", DataType.UBYTE, Position.DUMMY)
        cmp3.add(PtIdentifier("main.start.f1", DataType.FLOAT, Position.DUMMY))
        cmp3.add(PtNumber(DataType.FLOAT, 0.0, Position.DUMMY))
        if3.add(cmp3)
        if3.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if3.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if3)
        val if4 = PtIfElse(Position.DUMMY)
        val cmp4 = PtBinaryExpression(">", DataType.UBYTE, Position.DUMMY)
        cmp4.add(PtIdentifier("main.start.f1", DataType.FLOAT, Position.DUMMY))
        cmp4.add(PtNumber(DataType.FLOAT, 0.0, Position.DUMMY))
        if4.add(cmp4)
        if4.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if4.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if4)
        block.add(sub)
        program.add(block)

        val options = getTestOptions()
        val st = SymbolTableMaker(program, options).make()
        val errors = ErrorReporterForTests()
        val result = codegen.generate(program, st, options, errors) as VmAssemblyProgram
        val irChunks = (result.irProgram.blocks.first().children.single() as IRSubroutine).chunks
        irChunks.size shouldBeGreaterThan 4
    }

    test("float comparison expressions against nonzero") {
//main {
//    sub start() {
//        float @shared f1
//
//        if f1==42
//            nop
//        if f1!=42
//            nop
//        if f1>42
//            nop
//        if f1<42
//            nop
//    }
//}
        val codegen = VmCodeGen()
        val program = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val block = PtBlock("main", null, false, false, false, PtBlock.BlockAlignment.NONE, SourceCode.Generated("test"), Position.DUMMY)
        val sub = PtSub("start", emptyList(), null, Position.DUMMY)
        sub.add(PtVariable("f1", DataType.FLOAT, ZeropageWish.DONTCARE, null, null, Position.DUMMY))
        val if1 = PtIfElse(Position.DUMMY)
        val cmp1 = PtBinaryExpression("==", DataType.UBYTE, Position.DUMMY)
        cmp1.add(PtIdentifier("main.start.f1", DataType.FLOAT, Position.DUMMY))
        cmp1.add(PtNumber(DataType.FLOAT, 42.0, Position.DUMMY))
        if1.add(cmp1)
        if1.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if1.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if1)
        val if2 = PtIfElse(Position.DUMMY)
        val cmp2 = PtBinaryExpression("!=", DataType.UBYTE, Position.DUMMY)
        cmp2.add(PtIdentifier("main.start.f1", DataType.FLOAT, Position.DUMMY))
        cmp2.add(PtNumber(DataType.FLOAT, 42.0, Position.DUMMY))
        if2.add(cmp2)
        if2.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if2.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if2)
        val if3 = PtIfElse(Position.DUMMY)
        val cmp3 = PtBinaryExpression("<", DataType.UBYTE, Position.DUMMY)
        cmp3.add(PtIdentifier("main.start.f1", DataType.FLOAT, Position.DUMMY))
        cmp3.add(PtNumber(DataType.FLOAT, 42.0, Position.DUMMY))
        if3.add(cmp3)
        if3.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if3.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if3)
        val if4 = PtIfElse(Position.DUMMY)
        val cmp4 = PtBinaryExpression(">", DataType.UBYTE, Position.DUMMY)
        cmp4.add(PtIdentifier("main.start.f1", DataType.FLOAT, Position.DUMMY))
        cmp4.add(PtNumber(DataType.FLOAT, 42.0, Position.DUMMY))
        if4.add(cmp4)
        if4.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if4.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if4)
        block.add(sub)
        program.add(block)

        val options = getTestOptions()
        val st = SymbolTableMaker(program, options).make()
        val errors = ErrorReporterForTests()
        val result = codegen.generate(program, st, options, errors) as VmAssemblyProgram
        val irChunks = (result.irProgram.blocks.first().children.single() as IRSubroutine).chunks
        irChunks.size shouldBeGreaterThan 4
    }

    test("float conditional jump") {
//main {
//    sub start() {
//        float @shared f1
//
//        if f1==42
//            goto $c000
//        if f1>42
//            goto $c000
//    }
//}
        val codegen = VmCodeGen()
        val program = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val block = PtBlock("main", null, false, false, false, PtBlock.BlockAlignment.NONE, SourceCode.Generated("test"), Position.DUMMY)
        val sub = PtSub("start", emptyList(), null, Position.DUMMY)
        sub.add(PtVariable("f1", DataType.FLOAT, ZeropageWish.DONTCARE, null, null, Position.DUMMY))
        val if1 = PtIfElse(Position.DUMMY)
        val cmp1 = PtBinaryExpression("==", DataType.UBYTE, Position.DUMMY)
        cmp1.add(PtIdentifier("main.start.f1", DataType.FLOAT, Position.DUMMY))
        cmp1.add(PtNumber(DataType.FLOAT, 42.0, Position.DUMMY))
        if1.add(cmp1)
        if1.add(PtNodeGroup().also { it.add(PtJump(null, 0xc000u, null, Position.DUMMY)) })
        if1.add(PtNodeGroup())
        sub.add(if1)
        val if2 = PtIfElse(Position.DUMMY)
        val cmp2 = PtBinaryExpression(">", DataType.UBYTE, Position.DUMMY)
        cmp2.add(PtIdentifier("main.start.f1", DataType.FLOAT, Position.DUMMY))
        cmp2.add(PtNumber(DataType.FLOAT, 42.0, Position.DUMMY))
        if2.add(cmp2)
        if2.add(PtNodeGroup().also { it.add(PtJump(null, 0xc000u, null, Position.DUMMY)) })
        if2.add(PtNodeGroup())
        sub.add(if2)
        block.add(sub)
        program.add(block)

        val options = getTestOptions()
        val st = SymbolTableMaker(program, options).make()
        val errors = ErrorReporterForTests()
        val result = codegen.generate(program, st, options, errors) as VmAssemblyProgram
        val irChunks = (result.irProgram.blocks.first().children.single() as IRSubroutine).chunks
        irChunks.size shouldBe 1
    }

    test("integer comparison expressions against zero") {
//main {
//    sub start() {
//        byte @shared sb1
//
//        if sb1==0
//            nop
//        if sb1!=0
//            nop
//        if sb1>0
//            nop
//        if sb1<0
//            nop
//    }
//}
        val codegen = VmCodeGen()
        val program = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val block = PtBlock("main", null, false, false, false, PtBlock.BlockAlignment.NONE, SourceCode.Generated("test"), Position.DUMMY)
        val sub = PtSub("start", emptyList(), null, Position.DUMMY)
        sub.add(PtVariable("sb1", DataType.BYTE, ZeropageWish.DONTCARE, null, null, Position.DUMMY))
        val if1 = PtIfElse(Position.DUMMY)
        val cmp1 = PtBinaryExpression("==", DataType.BYTE, Position.DUMMY)
        cmp1.add(PtIdentifier("main.start.sb1", DataType.BYTE, Position.DUMMY))
        cmp1.add(PtNumber(DataType.BYTE, 0.0, Position.DUMMY))
        if1.add(cmp1)
        if1.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if1.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if1)
        val if2 = PtIfElse(Position.DUMMY)
        val cmp2 = PtBinaryExpression("!=", DataType.BYTE, Position.DUMMY)
        cmp2.add(PtIdentifier("main.start.sb1", DataType.BYTE, Position.DUMMY))
        cmp2.add(PtNumber(DataType.BYTE, 0.0, Position.DUMMY))
        if2.add(cmp2)
        if2.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if2.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if2)
        val if3 = PtIfElse(Position.DUMMY)
        val cmp3 = PtBinaryExpression("<", DataType.BYTE, Position.DUMMY)
        cmp3.add(PtIdentifier("main.start.sb1", DataType.BYTE, Position.DUMMY))
        cmp3.add(PtNumber(DataType.BYTE, 0.0, Position.DUMMY))
        if3.add(cmp3)
        if3.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if3.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if3)
        val if4 = PtIfElse(Position.DUMMY)
        val cmp4 = PtBinaryExpression(">", DataType.BYTE, Position.DUMMY)
        cmp4.add(PtIdentifier("main.start.sb1", DataType.BYTE, Position.DUMMY))
        cmp4.add(PtNumber(DataType.BYTE, 0.0, Position.DUMMY))
        if4.add(cmp4)
        if4.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if4.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if4)
        block.add(sub)
        program.add(block)

        val options = getTestOptions()
        val st = SymbolTableMaker(program, options).make()
        val errors = ErrorReporterForTests()
        val result = codegen.generate(program, st, options, errors) as VmAssemblyProgram
        val irChunks = (result.irProgram.blocks.first().children.single() as IRSubroutine).chunks
        irChunks.size shouldBeGreaterThan 4
    }

    test("integer comparison expressions against nonzero") {
//main {
//    sub start() {
//        byte @shared sb1
//
//        if sb1==42
//            nop
//        if sb1!=42
//            nop
//        if sb1>42
//            nop
//        if sb1<42
//            nop
//    }
//}
        val codegen = VmCodeGen()
        val program = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val block = PtBlock("main", null, false, false, false, PtBlock.BlockAlignment.NONE, SourceCode.Generated("test"), Position.DUMMY)
        val sub = PtSub("start", emptyList(), null, Position.DUMMY)
        sub.add(PtVariable("sb1", DataType.BYTE, ZeropageWish.DONTCARE, null, null, Position.DUMMY))
        val if1 = PtIfElse(Position.DUMMY)
        val cmp1 = PtBinaryExpression("==", DataType.BYTE, Position.DUMMY)
        cmp1.add(PtIdentifier("main.start.sb1", DataType.BYTE, Position.DUMMY))
        cmp1.add(PtNumber(DataType.BYTE, 42.0, Position.DUMMY))
        if1.add(cmp1)
        if1.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if1.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if1)
        val if2 = PtIfElse(Position.DUMMY)
        val cmp2 = PtBinaryExpression("!=", DataType.BYTE, Position.DUMMY)
        cmp2.add(PtIdentifier("main.start.sb1", DataType.BYTE, Position.DUMMY))
        cmp2.add(PtNumber(DataType.BYTE, 42.0, Position.DUMMY))
        if2.add(cmp2)
        if2.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if2.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if2)
        val if3 = PtIfElse(Position.DUMMY)
        val cmp3 = PtBinaryExpression("<", DataType.BYTE, Position.DUMMY)
        cmp3.add(PtIdentifier("main.start.sb1", DataType.BYTE, Position.DUMMY))
        cmp3.add(PtNumber(DataType.BYTE, 42.0, Position.DUMMY))
        if3.add(cmp3)
        if3.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if3.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if3)
        val if4 = PtIfElse(Position.DUMMY)
        val cmp4 = PtBinaryExpression(">", DataType.BYTE, Position.DUMMY)
        cmp4.add(PtIdentifier("main.start.sb1", DataType.BYTE, Position.DUMMY))
        cmp4.add(PtNumber(DataType.BYTE, 42.0, Position.DUMMY))
        if4.add(cmp4)
        if4.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        if4.add(PtNodeGroup().also { it.add(PtNop(Position.DUMMY)) })
        sub.add(if4)
        block.add(sub)
        program.add(block)

        val options = getTestOptions()
        val st = SymbolTableMaker(program, options).make()
        val errors = ErrorReporterForTests()
        val result = codegen.generate(program, st, options, errors) as VmAssemblyProgram
        val irChunks = (result.irProgram.blocks.first().children.single() as IRSubroutine).chunks
        irChunks.size shouldBeGreaterThan 4
    }

    test("integer conditional jump") {
//main {
//    sub start() {
//        ubyte @shared ub1
//
//        if ub1==42
//            goto $c000
//        if ub1>42
//            goto $c000
//    }
//}
        val codegen = VmCodeGen()
        val program = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val block = PtBlock("main", null, false, false, false, PtBlock.BlockAlignment.NONE, SourceCode.Generated("test"), Position.DUMMY)
        val sub = PtSub("start", emptyList(), null, Position.DUMMY)
        sub.add(PtVariable("ub1", DataType.UBYTE, ZeropageWish.DONTCARE, null, null, Position.DUMMY))
        val if1 = PtIfElse(Position.DUMMY)
        val cmp1 = PtBinaryExpression("==", DataType.UBYTE, Position.DUMMY)
        cmp1.add(PtIdentifier("main.start.ub1", DataType.UBYTE, Position.DUMMY))
        cmp1.add(PtNumber(DataType.UBYTE, 42.0, Position.DUMMY))
        if1.add(cmp1)
        if1.add(PtNodeGroup().also { it.add(PtJump(null, 0xc000u, null, Position.DUMMY)) })
        if1.add(PtNodeGroup())
        sub.add(if1)
        val if2 = PtIfElse(Position.DUMMY)
        val cmp2 = PtBinaryExpression(">", DataType.UBYTE, Position.DUMMY)
        cmp2.add(PtIdentifier("main.start.ub1", DataType.UBYTE, Position.DUMMY))
        cmp2.add(PtNumber(DataType.UBYTE, 42.0, Position.DUMMY))
        if2.add(cmp2)
        if2.add(PtNodeGroup().also { it.add(PtJump(null, 0xc000u, null, Position.DUMMY)) })
        if2.add(PtNodeGroup())
        sub.add(if2)
        block.add(sub)
        program.add(block)

        val options = getTestOptions()
        val st = SymbolTableMaker(program, options).make()
        val errors = ErrorReporterForTests()
        val result = codegen.generate(program, st, options, errors) as VmAssemblyProgram
        val irChunks = (result.irProgram.blocks.first().children.single() as IRSubroutine).chunks
        irChunks.size shouldBe 1
    }

    test("romsub allowed in ir-codegen") {
//main {
//    romsub $5000 = routine()
//
//    sub start()  {
//        routine()
//    }
//}
        val codegen = VmCodeGen()
        val program = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val block = PtBlock("main", null, false, false, false, PtBlock.BlockAlignment.NONE, SourceCode.Generated("test"), Position.DUMMY)
        val romsub = PtAsmSub("routine", 0x5000u, setOf(CpuRegister.Y), emptyList(), emptyList(), false, Position.DUMMY)
        block.add(romsub)
        val sub = PtSub("start", emptyList(), null, Position.DUMMY)
        val call = PtFunctionCall("main.routine", true, DataType.UNDEFINED, Position.DUMMY)
        sub.add(call)
        block.add(sub)
        program.add(block)

        val options = getTestOptions()
        val st = SymbolTableMaker(program, options).make()
        val errors = ErrorReporterForTests()
        val result = codegen.generate(program, st, options, errors) as VmAssemblyProgram
        val irChunks = (result.irProgram.blocks.first().children.single() as IRSubroutine).chunks
        irChunks.size shouldBe 1
        val callInstr = irChunks.single().instructions.single()
        callInstr.opcode shouldBe Opcode.CALL
        callInstr.address shouldBe 0x5000
    }
})