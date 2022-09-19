import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.SymbolTable
import prog8.code.core.*
import prog8.code.target.VMTarget
import prog8.codegen.intermediate.IRPeepholeOptimizer
import prog8.intermediate.*

class TestIRPeepholeOpt: FunSpec({
    fun makeIRProgram(lines: List<IRCodeLine>): IRProgram {
        val block = IRBlock("main", null, IRBlock.BlockAlignment.NONE, Position.DUMMY)
        val sub = IRSubroutine("main.start", emptyList(), null, Position.DUMMY)
        val chunk = IRCodeChunk(Position.DUMMY)
        for(line in lines)
            chunk += line
        sub += chunk
        block += sub
        val st = SymbolTable()
        val target = VMTarget()
        val options = CompilationOptions(
            OutputType.RAW,
            CbmPrgLauncherType.NONE,
            ZeropageType.DONTUSE,
            emptyList(),
            floats = false,
            noSysInit = true,
            compTarget = target,
            loadAddress = target.machine.PROGRAM_LOAD_ADDRESS
        )
        val prog = IRProgram("test", st, options, target)
        prog.addBlock(block)
        return prog
    }

    fun IRProgram.lines(): List<IRCodeLine> = this.blocks.flatMap { it.subroutines }.flatMap { it.chunks }.flatMap { it.lines }

    test("remove nops") {
        val irProg = makeIRProgram(listOf(
            IRCodeInstruction(Opcode.JUMP, labelSymbol = "dummy"),
            IRCodeInstruction(Opcode.NOP),
            IRCodeInstruction(Opcode.NOP)
        ))
        irProg.lines().size shouldBe 3
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        irProg.lines().size shouldBe 1
    }

    test("remove jmp to label below") {
        val irProg = makeIRProgram(listOf(
            IRCodeInstruction(Opcode.JUMP, labelSymbol = "label"),  // removed
            IRCodeLabel("label"),
            IRCodeInstruction(Opcode.JUMP, labelSymbol = "label2"), // removed
            IRCodeInstruction(Opcode.NOP),  // removed
            IRCodeLabel("label2"),
            IRCodeInstruction(Opcode.JUMP, labelSymbol = "label3"),
            IRCodeInstruction(Opcode.INC, VmDataType.BYTE, reg1=1),
            IRCodeLabel("label3")
        ))
        irProg.lines().size shouldBe 8
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        val lines = irProg.lines()
        lines.size shouldBe 5
        (lines[0] as IRCodeLabel).name shouldBe "label"
        (lines[1] as IRCodeLabel).name shouldBe "label2"
        (lines[2] as IRCodeInstruction).ins.opcode shouldBe Opcode.JUMP
        (lines[3] as IRCodeInstruction).ins.opcode shouldBe Opcode.INC
        (lines[4] as IRCodeLabel).name shouldBe "label3"
    }

    test("remove double sec/clc") {
        val irProg = makeIRProgram(listOf(
            IRCodeInstruction(Opcode.SEC),
            IRCodeInstruction(Opcode.SEC),
            IRCodeInstruction(Opcode.SEC),
            IRCodeInstruction(Opcode.CLC),
            IRCodeInstruction(Opcode.CLC),
            IRCodeInstruction(Opcode.CLC)
        ))
        irProg.lines().size shouldBe 6
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        val lines = irProg.lines()
        lines.size shouldBe 1
        (lines[0] as IRCodeInstruction).ins.opcode shouldBe Opcode.CLC
    }

    test("push followed by pop") {
        val irProg = makeIRProgram(listOf(
            IRCodeInstruction(Opcode.PUSH, VmDataType.BYTE, reg1=42),
            IRCodeInstruction(Opcode.POP, VmDataType.BYTE, reg1=42),
            IRCodeInstruction(Opcode.PUSH, VmDataType.BYTE, reg1=99),
            IRCodeInstruction(Opcode.POP, VmDataType.BYTE, reg1=222)
        ))
        irProg.lines().size shouldBe 4
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        val lines = irProg.lines()
        lines.size shouldBe 1
        (lines[0] as IRCodeInstruction).ins.opcode shouldBe Opcode.LOADR
        (lines[0] as IRCodeInstruction).ins.reg1 shouldBe 222
        (lines[0] as IRCodeInstruction).ins.reg2 shouldBe 99
    }

    test("remove useless div/mul, add/sub") {
        val irProg = makeIRProgram(listOf(
            IRCodeInstruction(Opcode.DIV, VmDataType.BYTE, reg1=42, value = 1),
            IRCodeInstruction(Opcode.DIVS, VmDataType.BYTE, reg1=42, value = 1),
            IRCodeInstruction(Opcode.MUL, VmDataType.BYTE, reg1=42, value = 1),
            IRCodeInstruction(Opcode.MOD, VmDataType.BYTE, reg1=42, value = 1),
            IRCodeInstruction(Opcode.DIV, VmDataType.BYTE, reg1=42, value = 2),
            IRCodeInstruction(Opcode.DIVS, VmDataType.BYTE, reg1=42, value = 2),
            IRCodeInstruction(Opcode.MUL, VmDataType.BYTE, reg1=42, value = 2),
            IRCodeInstruction(Opcode.MOD, VmDataType.BYTE, reg1=42, value = 2),
            IRCodeInstruction(Opcode.ADD, VmDataType.BYTE, reg1=42, value = 0),
            IRCodeInstruction(Opcode.SUB, VmDataType.BYTE, reg1=42, value = 0)
        ))
        irProg.lines().size shouldBe 10
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        val lines = irProg.lines()
        lines.size shouldBe 4
    }

    test("replace add/sub 1 by inc/dec") {
        val irProg = makeIRProgram(listOf(
            IRCodeInstruction(Opcode.ADD, VmDataType.BYTE, reg1=42, value = 1),
            IRCodeInstruction(Opcode.SUB, VmDataType.BYTE, reg1=42, value = 1)
        ))
        irProg.lines().size shouldBe 2
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        val lines = irProg.lines()
        lines.size shouldBe 2
        (lines[0] as IRCodeInstruction).ins.opcode shouldBe Opcode.INC
        (lines[1] as IRCodeInstruction).ins.opcode shouldBe Opcode.DEC
    }

    test("remove useless and/or/xor") {
        val irProg = makeIRProgram(listOf(
            IRCodeInstruction(Opcode.AND, VmDataType.BYTE, reg1=42, value = 255),
            IRCodeInstruction(Opcode.AND, VmDataType.WORD, reg1=42, value = 65535),
            IRCodeInstruction(Opcode.OR, VmDataType.BYTE, reg1=42, value = 0),
            IRCodeInstruction(Opcode.XOR, VmDataType.BYTE, reg1=42, value = 0),
            IRCodeInstruction(Opcode.AND, VmDataType.BYTE, reg1=42, value = 200),
            IRCodeInstruction(Opcode.AND, VmDataType.WORD, reg1=42, value = 60000),
            IRCodeInstruction(Opcode.OR, VmDataType.BYTE, reg1=42, value = 1),
            IRCodeInstruction(Opcode.XOR, VmDataType.BYTE, reg1=42, value = 1)
        ))
        irProg.lines().size shouldBe 8
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        val lines = irProg.lines()
        lines.size shouldBe 4
    }

    test("replace and/or/xor by constant number") {
        val irProg = makeIRProgram(listOf(
            IRCodeInstruction(Opcode.AND, VmDataType.BYTE, reg1=42, value = 0),
            IRCodeInstruction(Opcode.AND, VmDataType.WORD, reg1=42, value = 0),
            IRCodeInstruction(Opcode.OR, VmDataType.BYTE, reg1=42, value = 255),
            IRCodeInstruction(Opcode.OR, VmDataType.WORD, reg1=42, value = 65535)
        ))
        irProg.lines().size shouldBe 4
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        val lines = irProg.lines()
        lines.size shouldBe 4
        (lines[0] as IRCodeInstruction).ins.opcode shouldBe Opcode.LOAD
        (lines[1] as IRCodeInstruction).ins.opcode shouldBe Opcode.LOAD
        (lines[2] as IRCodeInstruction).ins.opcode shouldBe Opcode.LOAD
        (lines[3] as IRCodeInstruction).ins.opcode shouldBe Opcode.LOAD
        (lines[0] as IRCodeInstruction).ins.value shouldBe 0
        (lines[1] as IRCodeInstruction).ins.value shouldBe 0
        (lines[2] as IRCodeInstruction).ins.value shouldBe 255
        (lines[3] as IRCodeInstruction).ins.value shouldBe 65535
    }
})