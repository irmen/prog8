import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.*
import prog8.code.target.VMTarget
import prog8.codegen.intermediate.IRPeepholeOptimizer
import prog8.intermediate.*

class TestIRPeepholeOpt: FunSpec({
    fun makeIRProgram(chunks: List<IRCodeChunkBase>): IRProgram {
        require(chunks.first().label=="main.start")
        val block = IRBlock("main", null, IRBlock.BlockAlignment.NONE, Position.DUMMY)
        val sub = IRSubroutine("main.start", emptyList(), null, Position.DUMMY)
        chunks.forEach { sub += it }
        block += sub
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
        val prog = IRProgram("test", IRSymbolTable(null), options, target)
        prog.addBlock(block)
        prog.linkChunks()
        prog.validate()
        return prog
    }

    fun makeIRProgram(instructions: List<IRInstruction>): IRProgram {
        val chunk = IRCodeChunk("main.start", null)
        instructions.forEach { chunk += it }
        return makeIRProgram(listOf(chunk))
    }

    fun IRProgram.chunks(): List<IRCodeChunkBase> = this.blocks.flatMap { it.children.filterIsInstance<IRSubroutine>() }.flatMap { it.chunks }

    test("remove nops") {
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=1, value=42),
            IRInstruction(Opcode.NOP),
            IRInstruction(Opcode.NOP)
        ))
        irProg.chunks().single().instructions.size shouldBe 3
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        irProg.chunks().single().instructions.size shouldBe 1
    }

    test("remove jmp to label below") {
        val c1 = IRCodeChunk("main.start", null)
        c1 += IRInstruction(Opcode.JUMP, labelSymbol = "label")  // removed, but chunk stays because of label
        val c2 = IRCodeChunk("label", null)
        c2 += IRInstruction(Opcode.JUMP, labelSymbol = "label2") // removed, but chunk stays because of label
        c2 += IRInstruction(Opcode.NOP)  // removed
        val c3 = IRCodeChunk("label2", null)
        c3 += IRInstruction(Opcode.JUMP, labelSymbol = "label3")
        c3 += IRInstruction(Opcode.INC, IRDataType.BYTE, reg1=1)
        val c4 = IRCodeChunk("label3", null)
        val irProg = makeIRProgram(listOf(c1, c2, c3, c4))

        irProg.chunks().size shouldBe 4
        irProg.chunks().flatMap { it.instructions }.size shouldBe 5
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        irProg.chunks().size shouldBe 4
        irProg.chunks()[0].label shouldBe "main.start"
        irProg.chunks()[1].label shouldBe "label"
        irProg.chunks()[2].label shouldBe "label2"
        irProg.chunks()[3].label shouldBe "label3"
        irProg.chunks()[0].isEmpty() shouldBe true
        irProg.chunks()[1].isEmpty() shouldBe true
        irProg.chunks()[2].isEmpty() shouldBe false
        irProg.chunks()[3].isEmpty() shouldBe true
        val instr = irProg.chunks().flatMap { it.instructions }
        instr.size shouldBe 2
        instr[0].opcode shouldBe Opcode.JUMP
        instr[1].opcode shouldBe Opcode.INC
    }

    test("remove double sec/clc") {
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.SEC),
            IRInstruction(Opcode.SEC),
            IRInstruction(Opcode.SEC),
            IRInstruction(Opcode.CLC),
            IRInstruction(Opcode.CLC),
            IRInstruction(Opcode.CLC)
        ))
        irProg.chunks().single().instructions.size shouldBe 6
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 1
        instr[0].opcode shouldBe Opcode.CLC
    }

    test("push followed by pop") {
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.PUSH, IRDataType.BYTE, reg1=42),
            IRInstruction(Opcode.POP, IRDataType.BYTE, reg1=42),
            IRInstruction(Opcode.PUSH, IRDataType.BYTE, reg1=99),
            IRInstruction(Opcode.POP, IRDataType.BYTE, reg1=222)
        ))
        irProg.chunks().single().instructions.size shouldBe 4
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 1
        instr[0].opcode shouldBe Opcode.LOADR
        instr[0].reg1 shouldBe 222
        instr[0].reg2 shouldBe 99
    }

    test("remove useless div/mul, add/sub") {
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.DIV, IRDataType.BYTE, reg1=42, value = 1),
            IRInstruction(Opcode.DIVS, IRDataType.BYTE, reg1=42, value = 1),
            IRInstruction(Opcode.MUL, IRDataType.BYTE, reg1=42, value = 1),
            IRInstruction(Opcode.MOD, IRDataType.BYTE, reg1=42, value = 1),
            IRInstruction(Opcode.DIV, IRDataType.BYTE, reg1=42, value = 2),
            IRInstruction(Opcode.DIVS, IRDataType.BYTE, reg1=42, value = 2),
            IRInstruction(Opcode.MUL, IRDataType.BYTE, reg1=42, value = 2),
            IRInstruction(Opcode.MOD, IRDataType.BYTE, reg1=42, value = 2),
            IRInstruction(Opcode.ADD, IRDataType.BYTE, reg1=42, value = 0),
            IRInstruction(Opcode.SUB, IRDataType.BYTE, reg1=42, value = 0)
        ))
        irProg.chunks().single().instructions.size shouldBe 10
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        irProg.chunks().single().instructions.size shouldBe 4
    }

    test("replace add/sub 1 by inc/dec") {
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.ADD, IRDataType.BYTE, reg1=42, value = 1),
            IRInstruction(Opcode.SUB, IRDataType.BYTE, reg1=42, value = 1)
        ))
        irProg.chunks().single().instructions.size shouldBe 2
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 2
        instr[0].opcode shouldBe Opcode.INC
        instr[1].opcode shouldBe Opcode.DEC
    }

    test("remove useless and/or/xor") {
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.AND, IRDataType.BYTE, reg1=42, value = 255),
            IRInstruction(Opcode.AND, IRDataType.WORD, reg1=42, value = 65535),
            IRInstruction(Opcode.OR, IRDataType.BYTE, reg1=42, value = 0),
            IRInstruction(Opcode.XOR, IRDataType.BYTE, reg1=42, value = 0),
            IRInstruction(Opcode.AND, IRDataType.BYTE, reg1=42, value = 200),
            IRInstruction(Opcode.AND, IRDataType.WORD, reg1=42, value = 60000),
            IRInstruction(Opcode.OR, IRDataType.BYTE, reg1=42, value = 1),
            IRInstruction(Opcode.XOR, IRDataType.BYTE, reg1=42, value = 1)
        ))
        irProg.chunks().single().instructions.size shouldBe 8
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        irProg.chunks().single().instructions.size shouldBe 4
    }

    test("replace and/or/xor by constant number") {
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.AND, IRDataType.BYTE, reg1=42, value = 0),
            IRInstruction(Opcode.AND, IRDataType.WORD, reg1=42, value = 0),
            IRInstruction(Opcode.OR, IRDataType.BYTE, reg1=42, value = 255),
            IRInstruction(Opcode.OR, IRDataType.WORD, reg1=42, value = 65535)
        ))
        irProg.chunks().single().instructions.size shouldBe 4
        val opt = IRPeepholeOptimizer(irProg)
        opt.optimize()
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 4
        instr[0].opcode shouldBe Opcode.LOAD
        instr[1].opcode shouldBe Opcode.LOAD
        instr[2].opcode shouldBe Opcode.LOAD
        instr[3].opcode shouldBe Opcode.LOAD
        instr[0].value shouldBe 0
        instr[1].value shouldBe 0
        instr[2].value shouldBe 255
        instr[3].value shouldBe 65535
    }
})