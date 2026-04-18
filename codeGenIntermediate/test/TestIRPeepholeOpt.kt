import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.*
import prog8.code.target.VMTarget
import prog8.codegen.intermediate.IRPeepholeOptimizer
import prog8.intermediate.*
import prog8tests.helpers.ErrorReporterForTests

class TestIRPeepholeOpt: FunSpec({
    fun makeIRProgram(chunks: List<IRCodeChunkBase>): IRProgram {
        require(chunks.first().label=="main.start")
        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        val sub = IRSubroutine("main.start", emptyList(), emptyList(), Position.DUMMY)
        chunks.forEach { sub += it }
        block += sub
        val target = VMTarget()
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.DONTUSE)
            .noSysInit(true)
            .compilerVersion("99.99")
            .build()
        val prog = IRProgram("test", IRSymbolTable(), options, target)
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

    fun IRProgram.chunks(): List<IRCodeChunkBase> = this.allSubs().flatMap { it.chunks }.toList()

    test("remove nops") {
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=1, immediate=42),
            IRInstruction(Opcode.NOP),
            IRInstruction(Opcode.NOP)
        ))
        irProg.chunks().single().instructions.size shouldBe 3
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        irProg.chunks().single().instructions.size shouldBe 1
    }

    test("remove jmp to label below but keep labels") {
        val c1 = IRCodeChunk("main.start", null)
        c1 += IRInstruction(Opcode.JUMP, labelSymbol = "label")
        val c2 = IRCodeChunk("label", null)
        c2 += IRInstruction(Opcode.JUMP, labelSymbol = "label2")
        c2 += IRInstruction(Opcode.NOP)  // removed
        val c3 = IRCodeChunk("label2", null)
        c3 += IRInstruction(Opcode.JUMP, labelSymbol = "label3")
        c3 += IRInstruction(Opcode.INC, IRDataType.BYTE, reg1=1)
        val c4 = IRCodeChunk("label3", null)
        val irProg = makeIRProgram(listOf(c1, c2, c3, c4))

        irProg.chunks().size shouldBe 4
        irProg.chunks().flatMap { it.instructions }.size shouldBe 5
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val chunks = irProg.chunks()
        chunks.size shouldBe 4
        chunks[0].label shouldBe "main.start"
        chunks[1].label shouldBe "label"
        chunks[2].label shouldBe "label2"
        chunks[3].label shouldBe "label3"
        chunks[0].isEmpty() shouldBe true
        chunks[1].isEmpty() shouldBe true
        chunks[2].isEmpty() shouldBe false
        chunks[3].isEmpty() shouldBe true
        val instr = irProg.chunks().flatMap { it.instructions }
        instr.size shouldBe 2
        instr[0].opcode shouldBe Opcode.JUMP
        instr[1].opcode shouldBe Opcode.INC
    }

    test("remove double sec/clc/sei/cli") {
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.SEC),
            IRInstruction(Opcode.SEC),
            IRInstruction(Opcode.SEC),
            IRInstruction(Opcode.CLC),
            IRInstruction(Opcode.CLC),
            IRInstruction(Opcode.CLC),
            IRInstruction(Opcode.SEI),
            IRInstruction(Opcode.SEI),
            IRInstruction(Opcode.SEI),
            IRInstruction(Opcode.CLI),
            IRInstruction(Opcode.CLI),
            IRInstruction(Opcode.CLI),
        ))
        irProg.chunks().single().instructions.size shouldBe 12
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 2
        instr[0].opcode shouldBe Opcode.CLC
        instr[1].opcode shouldBe Opcode.CLI
    }

    test("remove double sec/clc/sei/cli reversed") {
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.CLC),
            IRInstruction(Opcode.CLC),
            IRInstruction(Opcode.CLC),
            IRInstruction(Opcode.SEC),
            IRInstruction(Opcode.SEC),
            IRInstruction(Opcode.SEC),
            IRInstruction(Opcode.CLI),
            IRInstruction(Opcode.CLI),
            IRInstruction(Opcode.CLI),
            IRInstruction(Opcode.SEI),
            IRInstruction(Opcode.SEI),
            IRInstruction(Opcode.SEI),
        ))
        irProg.chunks().single().instructions.size shouldBe 12
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 2
        instr[0].opcode shouldBe Opcode.SEC
        instr[1].opcode shouldBe Opcode.SEI
    }

    test("push followed by pop") {
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.PUSH, IRDataType.BYTE, reg1=42),
            IRInstruction(Opcode.POP, IRDataType.BYTE, reg1=42),
            IRInstruction(Opcode.PUSH, IRDataType.BYTE, reg1=99),
            IRInstruction(Opcode.POP, IRDataType.BYTE, reg1=222)
        ))
        irProg.chunks().single().instructions.size shouldBe 4
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 1
        instr[0].opcode shouldBe Opcode.LOADR
        instr[0].reg1 shouldBe 222
        instr[0].reg2 shouldBe 99
    }

    test("remove useless div/mul, add/sub") {
        // Use different registers for each test case to avoid dead store elimination removing them
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.DIV, IRDataType.BYTE, reg1=1, immediate = 1),
            IRInstruction(Opcode.DIVS, IRDataType.BYTE, reg1=2, immediate = 1),
            IRInstruction(Opcode.MUL, IRDataType.BYTE, reg1=3, immediate = 1),
            IRInstruction(Opcode.MOD, IRDataType.BYTE, reg1=4, immediate = 1),
            IRInstruction(Opcode.DIV, IRDataType.BYTE, reg1=5, immediate = 2),
            IRInstruction(Opcode.DIVS, IRDataType.BYTE, reg1=6, immediate = 2),
            IRInstruction(Opcode.MUL, IRDataType.BYTE, reg1=7, immediate = 2),
            IRInstruction(Opcode.MOD, IRDataType.BYTE, reg1=8, immediate = 2),
            IRInstruction(Opcode.ADD, IRDataType.BYTE, reg1=9, immediate = 0),
            IRInstruction(Opcode.SUB, IRDataType.BYTE, reg1=10, immediate = 0)
        ))
        irProg.chunks().single().instructions.size shouldBe 10
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        // First 4 are removed (div/mul/mod by 1), last 2 become no-ops (add/sub 0)
        irProg.chunks().single().instructions.size shouldBe 4
    }

    test("replace add/sub 1 by inc/dec") {
        // Use different registers for each test case to avoid dead store elimination removing them
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.ADD, IRDataType.BYTE, reg1=1, immediate = 1),
            IRInstruction(Opcode.SUB, IRDataType.BYTE, reg1=2, immediate = 1)
        ))
        irProg.chunks().single().instructions.size shouldBe 2
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 2
        instr[0].opcode shouldBe Opcode.INC
        instr[1].opcode shouldBe Opcode.DEC
    }

    test("remove useless and/or/xor") {
        // Use different registers for each test case to avoid dead store elimination removing them
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.AND, IRDataType.BYTE, reg1=1, immediate = 0),
            IRInstruction(Opcode.AND, IRDataType.WORD, reg1=2, immediate = 0),
            IRInstruction(Opcode.AND, IRDataType.LONG, reg1=3, immediate = 0),
            IRInstruction(Opcode.AND, IRDataType.BYTE, reg1=4, immediate = 255),
            IRInstruction(Opcode.AND, IRDataType.WORD, reg1=5, immediate = 65535),
            IRInstruction(Opcode.AND, IRDataType.LONG, reg1=6, immediate = 2147483647),
            IRInstruction(Opcode.AND, IRDataType.LONG, reg1=7, immediate = -1),
            IRInstruction(Opcode.OR, IRDataType.BYTE, reg1=8, immediate = 0),
            IRInstruction(Opcode.OR, IRDataType.BYTE, reg1=9, immediate = 255),
            IRInstruction(Opcode.OR, IRDataType.WORD, reg1=10, immediate = 65535),
            IRInstruction(Opcode.OR, IRDataType.LONG, reg1=11, immediate = 2147483647),
            IRInstruction(Opcode.OR, IRDataType.LONG, reg1=12, immediate = -1),
            IRInstruction(Opcode.XOR, IRDataType.BYTE, reg1=13, immediate = 0),
            IRInstruction(Opcode.AND, IRDataType.BYTE, reg1=14, immediate = 200),
            IRInstruction(Opcode.AND, IRDataType.WORD, reg1=15, immediate = 60000),
            IRInstruction(Opcode.OR, IRDataType.BYTE, reg1=16, immediate = 1),
            IRInstruction(Opcode.XOR, IRDataType.BYTE, reg1=17, immediate = 1)
        ))
        irProg.chunks().single().instructions.size shouldBe 17
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        // After optimization:
        // - AND #0 (3x) -> LOAD #0
        // - AND #max (2x) -> removed
        // - AND #other (3x) -> stays
        // - OR #0 (1x) -> removed
        // - OR #max (3x) -> LOAD #max
        // - XOR #0 (1x) -> removed
        // - other (3x) -> stays
        // Total: 17 - 5 (removed) = 12, with 6 LOAD instructions
        irProg.chunks().single().instructions.size shouldBe 12
        irProg.chunks().single().instructions.count { it.opcode == Opcode.LOAD } shouldBe 6
    }

    test("replace and/or/xor by constant number") {
        // Use different registers for each test case to avoid dead store elimination removing them
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.AND, IRDataType.BYTE, reg1=1, immediate = 0),
            IRInstruction(Opcode.AND, IRDataType.WORD, reg1=2, immediate = 0),
            IRInstruction(Opcode.AND, IRDataType.LONG, reg1=3, immediate = 0),
            IRInstruction(Opcode.OR, IRDataType.BYTE, reg1=4, immediate = 255),
            IRInstruction(Opcode.OR, IRDataType.WORD, reg1=5, immediate = 65535),
            IRInstruction(Opcode.OR, IRDataType.LONG, reg1=6, immediate = -1)
        ))
        irProg.chunks().single().instructions.size shouldBe 6
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 6
        instr[0].opcode shouldBe Opcode.LOAD
        instr[1].opcode shouldBe Opcode.LOAD
        instr[2].opcode shouldBe Opcode.LOAD
        instr[3].opcode shouldBe Opcode.LOAD
        instr[4].opcode shouldBe Opcode.LOAD
        instr[5].opcode shouldBe Opcode.LOAD
        instr[0].immediate shouldBe 0
        instr[1].immediate shouldBe 0
        instr[2].immediate shouldBe 0
        instr[3].immediate shouldBe 255
        instr[4].immediate shouldBe 65535
        instr[5].immediate shouldBe -1
    }

    test("replace register arithmetic by immediate operations") {
        // Test the new optimization: LOAD + SUBR/DIVR/MODR → immediate operation
        // Pattern: LOAD r1, #const  followed by  SUBR r2, r1  →  SUB r2, #const
        
        // SUBR optimization
        val subrProg = makeIRProgram(listOf(
            IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=1, immediate = 5),
            IRInstruction(Opcode.SUBR, IRDataType.BYTE, reg1=2, reg2=1)
        ))
        subrProg.chunks().single().instructions.size shouldBe 2
        val opt1 = IRPeepholeOptimizer(subrProg, false)
        opt1.optimize(true, ErrorReporterForTests())
        val subInstr = subrProg.chunks().single().instructions
        subInstr.size shouldBe 1
        subInstr[0].opcode shouldBe Opcode.SUB
        subInstr[0].immediate shouldBe 5
        
        // DIVR optimization
        val divrProg = makeIRProgram(listOf(
            IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1=10, immediate = 100),
            IRInstruction(Opcode.DIVR, IRDataType.WORD, reg1=20, reg2=10)
        ))
        divrProg.chunks().single().instructions.size shouldBe 2
        val opt2 = IRPeepholeOptimizer(divrProg, false)
        opt2.optimize(true, ErrorReporterForTests())
        val divInstr = divrProg.chunks().single().instructions
        divInstr.size shouldBe 1
        divInstr[0].opcode shouldBe Opcode.DIV
        divInstr[0].immediate shouldBe 100
        
        // MODR optimization
        val modrProg = makeIRProgram(listOf(
            IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=30, immediate = 7),
            IRInstruction(Opcode.MODR, IRDataType.BYTE, reg1=40, reg2=30)
        ))
        modrProg.chunks().single().instructions.size shouldBe 2
        val opt3 = IRPeepholeOptimizer(modrProg, false)
        opt3.optimize(true, ErrorReporterForTests())
        val modInstr = modrProg.chunks().single().instructions
        modInstr.size shouldBe 1
        modInstr[0].opcode shouldBe Opcode.MOD
        modInstr[0].immediate shouldBe 7
    }
})