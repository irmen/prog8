import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.CompilationOptions
import prog8.code.core.OutputType
import prog8.code.core.Position
import prog8.code.core.ZeropageType
import prog8.code.target.VMTarget
import prog8.codegen.intermediate.IRPeepholeOptimizer
import prog8.intermediate.*
import prog8tests.helpers.ErrorReporterForTests

class TestIRPeepholeOpt: FunSpec({
    val indexRegType = VMTarget().indexRegType

    fun makeIRProgram(chunks: List<IRCodeChunkBase>): IRProgram {
        require(chunks.first().label=="p8b_main.p8s_start")
        val block = IRBlock("p8b_main", false, IRBlock.Options(), Position.DUMMY)
        val sub = IRSubroutine("p8b_main.p8s_start", emptyList(), emptyList(), Position.DUMMY)
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
        val chunk = IRCodeChunk("p8b_main.p8s_start", null)
        instructions.forEach { chunk += it }
        return makeIRProgram(listOf(chunk))
    }

    fun IRProgram.chunks(): List<IRCodeChunkBase> = this.allSubs().flatMap { it.chunks }.toList()

    test("remove nops") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.load(IRDataType.BYTE, 1, 42),
            IRInstructions.simple(Opcode.NOP),
            IRInstructions.simple(Opcode.NOP)
        ))
        irProg.chunks().single().instructions.size shouldBe 3
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        irProg.chunks().single().instructions.size shouldBe 1
    }

    test("remove jmp to label below but keep labels") {
        val c1 = IRCodeChunk("p8b_main.p8s_start", null)
        c1 += IRInstructions.jump(codeLabel("label"))
        val c2 = IRCodeChunk("label", null)
        c2 += IRInstructions.jump(codeLabel("label2"))
        c2 += IRInstructions.simple(Opcode.NOP)  // removed
        val c3 = IRCodeChunk("label2", null)
        c3 += IRInstructions.jump(codeLabel("label3"))
        c3 += IRInstructions.unary(Opcode.INC, IRDataType.BYTE, 1)
        val c4 = IRCodeChunk("label3", null)
        val irProg = makeIRProgram(listOf(c1, c2, c3, c4))

        irProg.chunks().size shouldBe 4
        irProg.chunks().flatMap { it.instructions }.size shouldBe 5
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val chunks = irProg.chunks()
        chunks.size shouldBe 4
        chunks[0].label shouldBe "p8b_main.p8s_start"
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
            IRInstructions.simple(Opcode.SEC),
            IRInstructions.simple(Opcode.SEC),
            IRInstructions.simple(Opcode.SEC),
            IRInstructions.simple(Opcode.CLC),
            IRInstructions.simple(Opcode.CLC),
            IRInstructions.simple(Opcode.CLC),
            IRInstructions.simple(Opcode.SEI),
            IRInstructions.simple(Opcode.SEI),
            IRInstructions.simple(Opcode.SEI),
            IRInstructions.simple(Opcode.CLI),
            IRInstructions.simple(Opcode.CLI),
            IRInstructions.simple(Opcode.CLI),
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
            IRInstructions.simple(Opcode.CLC),
            IRInstructions.simple(Opcode.CLC),
            IRInstructions.simple(Opcode.CLC),
            IRInstructions.simple(Opcode.SEC),
            IRInstructions.simple(Opcode.SEC),
            IRInstructions.simple(Opcode.SEC),
            IRInstructions.simple(Opcode.CLI),
            IRInstructions.simple(Opcode.CLI),
            IRInstructions.simple(Opcode.CLI),
            IRInstructions.simple(Opcode.SEI),
            IRInstructions.simple(Opcode.SEI),
            IRInstructions.simple(Opcode.SEI),
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
            IRInstructions.push(IRDataType.BYTE, 42),
            IRInstructions.pop(IRDataType.BYTE, 42),
            IRInstructions.push(IRDataType.BYTE, 99),
            IRInstructions.pop(IRDataType.BYTE, 222)
        ))
        irProg.chunks().single().instructions.size shouldBe 4
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 1
        instr[0].opcode shouldBe Opcode.LOADR
        instr[0].requireDest().register shouldBe VirtualRegister.int(222)
        instr[0].requireSrcA().register shouldBe VirtualRegister.int(99)
    }

    test("remove useless div/mul, add/sub") {
        // Use different registers for each test case to avoid dead store elimination removing them
        val irProg = makeIRProgram(listOf(
            IRInstructions.binaryImmediate(Opcode.DIV, IRDataType.BYTE, 1, 1),
            IRInstructions.binaryImmediate(Opcode.DIVS, IRDataType.BYTE, 2, 1),
            IRInstructions.binaryImmediate(Opcode.MUL, IRDataType.BYTE, 3, 1),
            IRInstructions.binaryImmediate(Opcode.MOD, IRDataType.BYTE, 4, 1),
            IRInstructions.binaryImmediate(Opcode.DIV, IRDataType.BYTE, 5, 2),
            IRInstructions.binaryImmediate(Opcode.DIVS, IRDataType.BYTE, 6, 2),
            IRInstructions.binaryImmediate(Opcode.MUL, IRDataType.BYTE, 7, 2),
            IRInstructions.binaryImmediate(Opcode.MOD, IRDataType.BYTE, 8, 2),
            IRInstructions.binaryImmediate(Opcode.ADD, IRDataType.BYTE, 9, 0),
            IRInstructions.binaryImmediate(Opcode.SUB, IRDataType.BYTE, 10, 0)
        ))
        irProg.chunks().single().instructions.size shouldBe 10
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        // First 4 are removed (div/mul/mod by 1), last 2 become no-ops (add/sub 0)
        irProg.chunks().single().instructions.size shouldBe 4
    }

    test("replace integer multiply by zero with load") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.binaryImmediate(Opcode.MUL, IRDataType.BYTE, 1, 0),
            IRInstructions.binaryImmediate(Opcode.MULS, IRDataType.WORD, 2, 0),
            IRInstructions.binaryImmediate(Opcode.MULS, IRDataType.LONG, 3, 0),
            IRInstructions.binaryImmediateFloat(Opcode.MUL, 4, 0.0),
            IRInstructions.returnVoid()
        ))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.count { it.opcode == Opcode.LOAD } shouldBe 3
        instr.count { it.opcode == Opcode.MUL } shouldBe 1
    }

    test("fold adjacent integer immediate multiplications") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.binaryImmediate(Opcode.MUL, IRDataType.BYTE, 1, 7),
            IRInstructions.binaryImmediate(Opcode.MUL, IRDataType.BYTE, 1, 9),
            IRInstructions.binaryImmediate(Opcode.MULS, IRDataType.WORD, 2, -3),
            IRInstructions.binaryImmediate(Opcode.MULS, IRDataType.WORD, 2, 4),
            IRInstructions.binaryImmediate(Opcode.MULS, IRDataType.LONG, 3, 100000),
            IRInstructions.binaryImmediate(Opcode.MULS, IRDataType.LONG, 3, 30000),
            IRInstructions.returnVoid()
        ))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 4
        instr[0].requireImmediateInt() shouldBe 63
        instr[1].requireImmediateInt() shouldBe 65524
        instr[2].requireImmediateInt() shouldBe 3000000000L.toInt()
    }

    test("collapse adjacent integer and float loadr chains") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.move(IRDataType.BYTE, 2, 1),
            IRInstructions.move(IRDataType.BYTE, 3, 2),
            IRInstructions.move(IRDataType.FLOAT, 5, 4),
            IRInstructions.move(IRDataType.FLOAT, 6, 5),
            IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, 3, IRMemory.direct(MemoryAddress(100u))),
            IRInstructions.storeMemory(Opcode.STOREM, IRDataType.FLOAT, 6, IRMemory.direct(MemoryAddress(200u)))
        ))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.map { it.opcode } shouldBe listOf(Opcode.LOADR, Opcode.LOADR, Opcode.STOREM, Opcode.STOREM)
        instr[0].requireDest().register shouldBe VirtualRegister.int(3)
        instr[0].requireSrcA().register shouldBe VirtualRegister.int(1)
        instr[1].requireDest().register shouldBe VirtualRegister.float(6)
        instr[1].requireSrcA().register shouldBe VirtualRegister.float(4)
        instr[2].requireSrcA().register shouldBe VirtualRegister.int(3)
        instr[3].requireSrcA().register shouldBe VirtualRegister.float(6)
    }

    test("replace add/sub 1 by inc/dec") {
        // Use different registers for each test case to avoid dead store elimination removing them
        val irProg = makeIRProgram(listOf(
            IRInstructions.binaryImmediate(Opcode.ADD, IRDataType.BYTE, 1, 1),
            IRInstructions.binaryImmediate(Opcode.SUB, IRDataType.BYTE, 2, 1)
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
            IRInstructions.binaryImmediate(Opcode.AND, IRDataType.BYTE, 1, 0),
            IRInstructions.binaryImmediate(Opcode.AND, IRDataType.WORD, 2, 0),
            IRInstructions.binaryImmediate(Opcode.AND, IRDataType.LONG, 3, 0),
            IRInstructions.binaryImmediate(Opcode.AND, IRDataType.BYTE, 4, 255),
            IRInstructions.binaryImmediate(Opcode.AND, IRDataType.WORD, 5, 65535),
            IRInstructions.binaryImmediate(Opcode.AND, IRDataType.LONG, 6, 2147483647),
            IRInstructions.binaryImmediate(Opcode.AND, IRDataType.LONG, 7, -1),
            IRInstructions.binaryImmediate(Opcode.OR, IRDataType.BYTE, 8, 0),
            IRInstructions.binaryImmediate(Opcode.OR, IRDataType.BYTE, 9, 255),
            IRInstructions.binaryImmediate(Opcode.OR, IRDataType.WORD, 10, 65535),
            IRInstructions.binaryImmediate(Opcode.OR, IRDataType.LONG, 11, 2147483647),
            IRInstructions.binaryImmediate(Opcode.OR, IRDataType.LONG, 12, -1),
            IRInstructions.binaryImmediate(Opcode.XOR, IRDataType.BYTE, 13, 0),
            IRInstructions.binaryImmediate(Opcode.AND, IRDataType.BYTE, 14, 200),
            IRInstructions.binaryImmediate(Opcode.AND, IRDataType.WORD, 15, 60000),
            IRInstructions.binaryImmediate(Opcode.OR, IRDataType.BYTE, 16, 1),
            IRInstructions.binaryImmediate(Opcode.XOR, IRDataType.BYTE, 17, 1)
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
            IRInstructions.binaryImmediate(Opcode.AND, IRDataType.BYTE, 1, 0),
            IRInstructions.binaryImmediate(Opcode.AND, IRDataType.WORD, 2, 0),
            IRInstructions.binaryImmediate(Opcode.AND, IRDataType.LONG, 3, 0),
            IRInstructions.binaryImmediate(Opcode.OR, IRDataType.BYTE, 4, 255),
            IRInstructions.binaryImmediate(Opcode.OR, IRDataType.WORD, 5, 65535),
            IRInstructions.binaryImmediate(Opcode.OR, IRDataType.LONG, 6, -1)
        ))
        irProg.chunks().single().instructions.size shouldBe 6
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 6
        instr.map { it.opcode } shouldBe List(6) { Opcode.LOAD }
        instr.map { it.requireImmediateInt() } shouldBe listOf(0, 0, 0, 255, 65535, -1)
    }

    test("combine adjacent immediate and/or operations") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.binaryImmediate(Opcode.AND, IRDataType.BYTE, 1, 0xf0),
            IRInstructions.binaryImmediate(Opcode.AND, IRDataType.BYTE, 1, 0xcc),
            IRInstructions.binaryImmediate(Opcode.OR, IRDataType.WORD, 2, 0x0010),
            IRInstructions.binaryImmediate(Opcode.OR, IRDataType.WORD, 2, 0x0003),
        ))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 2
        instr[0].opcode shouldBe Opcode.AND
        instr[0].requireImmediateInt() shouldBe 0xc0
        instr[1].opcode shouldBe Opcode.OR
        instr[1].requireImmediateInt() shouldBe 0x0013
    }

    test("cancel identical adjacent immediate xor operations") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.binaryImmediate(Opcode.XOR, IRDataType.BYTE, 1, 3),
            IRInstructions.binaryImmediate(Opcode.XOR, IRDataType.BYTE, 1, 3),
            IRInstructions.load(IRDataType.BYTE, 2, 42)
        ))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 1
        instr[0].opcode shouldBe Opcode.LOAD
        instr[0].requireImmediateInt() shouldBe 42
    }

    test("fold adjacent immediate arithmetic with inc/dec") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.binaryImmediate(Opcode.ADD, IRDataType.BYTE, 1, 4),
            IRInstructions.unary(Opcode.INC, IRDataType.BYTE, 1),
            IRInstructions.unary(Opcode.INC, IRDataType.BYTE, 2),
            IRInstructions.binaryImmediate(Opcode.ADD, IRDataType.BYTE, 2, 4),
            IRInstructions.binaryImmediate(Opcode.SUB, IRDataType.BYTE, 3, 4),
            IRInstructions.unary(Opcode.DEC, IRDataType.BYTE, 3),
            IRInstructions.unary(Opcode.DEC, IRDataType.BYTE, 4),
            IRInstructions.binaryImmediate(Opcode.SUB, IRDataType.BYTE, 4, 4)
        ))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 4
        instr[0].opcode shouldBe Opcode.ADD
        instr[0].requireImmediateInt() shouldBe 5
        instr[1].opcode shouldBe Opcode.ADD
        instr[1].requireImmediateInt() shouldBe 5
        instr[2].opcode shouldBe Opcode.SUB
        instr[2].requireImmediateInt() shouldBe 5
        instr[3].opcode shouldBe Opcode.SUB
        instr[3].requireImmediateInt() shouldBe 5
    }

    test("replace register arithmetic by immediate operations") {
        // Test the new optimization: LOAD + SUBR/DIVR/MODR → immediate operation
        // Pattern: LOAD r1, #const  followed by  SUBR r2, r1  →  SUB r2, #const

        // SUBR optimization
        val subrProg = makeIRProgram(listOf(
            IRInstructions.load(IRDataType.BYTE, 1, 5),
            IRInstructions.binary(Opcode.SUBR, IRDataType.BYTE, 2, 1)
        ))
        subrProg.chunks().single().instructions.size shouldBe 2
        val opt1 = IRPeepholeOptimizer(subrProg, false)
        opt1.optimize(true, ErrorReporterForTests())
        val subInstr = subrProg.chunks().single().instructions
        subInstr.size shouldBe 1
        subInstr[0].opcode shouldBe Opcode.SUB
        subInstr[0].requireImmediateInt() shouldBe 5

        // DIVR optimization
        val divrProg = makeIRProgram(listOf(
            IRInstructions.load(IRDataType.WORD, 10, 100),
            IRInstructions.binary(Opcode.DIVR, IRDataType.WORD, 20, 10)
        ))
        divrProg.chunks().single().instructions.size shouldBe 2
        val opt2 = IRPeepholeOptimizer(divrProg, false)
        opt2.optimize(true, ErrorReporterForTests())
        val divInstr = divrProg.chunks().single().instructions
        divInstr.size shouldBe 1
        divInstr[0].opcode shouldBe Opcode.DIV
        divInstr[0].requireImmediateInt() shouldBe 100

        // MODR optimization
        val modrProg = makeIRProgram(listOf(
            IRInstructions.load(IRDataType.BYTE, 30, 7),
            IRInstructions.binary(Opcode.MODR, IRDataType.BYTE, 40, 30)
        ))
        modrProg.chunks().single().instructions.size shouldBe 2
        val opt3 = IRPeepholeOptimizer(modrProg, false)
        opt3.optimize(true, ErrorReporterForTests())
        val modInstr = modrProg.chunks().single().instructions
        modInstr.size shouldBe 1
        modInstr[0].opcode shouldBe Opcode.MOD
        modInstr[0].requireImmediateInt() shouldBe 7
    }

    test("remove self identity operations") {
        // The IR is not SSA: a written register may also be a read-only source register.
        // Self-identity LOADR/ANDR/ORR become NOP, self-identity XORR becomes load 0.
        val irProg = makeIRProgram(listOf(
            IRInstructions.move(IRDataType.FLOAT, 10, 10),
            IRInstructions.move(IRDataType.BYTE, 11, 11),
            IRInstructions.binary(Opcode.ANDR, IRDataType.BYTE, 12, 12),
            IRInstructions.binary(Opcode.ORR, IRDataType.BYTE, 13, 13),
            IRInstructions.binary(Opcode.XORR, IRDataType.BYTE, 14, 14),
            IRInstructions.load(IRDataType.BYTE, 99, 42)       // keep chunk non-empty
        ))
        irProg.chunks().single().instructions.size shouldBe 6
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions.filter { it.opcode != Opcode.NOP }
        instr.size shouldBe 2
        instr[0].opcode shouldBe Opcode.LOAD
        instr[0].requireDest().register shouldBe VirtualRegister.int(14)
        instr[0].requireImmediateInt() shouldBe 0
        instr[1].opcode shouldBe Opcode.LOAD
        instr[1].requireDest().register shouldBe VirtualRegister.int(99)
        instr[1].requireImmediateInt() shouldBe 42
    }

    test("remove shift by zero") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.load(IRDataType.BYTE, 10, 0),
            IRInstructions.binary(Opcode.LSLN, IRDataType.BYTE, 1, 10),
            IRInstructions.load(IRDataType.BYTE, 11, 0),
            IRInstructions.binary(Opcode.LSRN, IRDataType.BYTE, 2, 11),
            IRInstructions.load(IRDataType.BYTE, 12, 0),
            IRInstructions.binary(Opcode.ASRN, IRDataType.BYTE, 3, 12),
            IRInstructions.load(IRDataType.BYTE, 99, 42)       // keep chunk non-empty
        ))
        irProg.chunks().single().instructions.size shouldBe 7
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 1
        instr[0].opcode shouldBe Opcode.LOAD
        instr[0].requireDest().register shouldBe VirtualRegister.int(99)
        instr[0].requireImmediateInt() shouldBe 42
    }

    test("replace concat with ext after harmless instruction") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.load(IRDataType.BYTE, 10, 0),
            IRInstructions.unary(Opcode.INC, IRDataType.BYTE, 20),
            IRInstructions.concat(IRDataType.BYTE, 1, 10, 11),
            IRInstructions.load(IRDataType.BYTE, 99, 42)
        ))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.any {
            it.opcode == Opcode.EXT &&
                    it.requireDest().register == VirtualRegister.int(1) &&
                    it.requireSrcA().register == VirtualRegister.int(11)
        } shouldBe true
        instr.any { it.opcode == Opcode.CONCAT } shouldBe false
    }

    test("do not replace concat after intervening write") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.load(IRDataType.BYTE, 10, 0),
            IRInstructions.unary(Opcode.INC, IRDataType.BYTE, 10),
            IRInstructions.concat(IRDataType.BYTE, 1, 10, 11),
            IRInstructions.load(IRDataType.BYTE, 99, 42)
        ))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        irProg.chunks().single().instructions.any { it.opcode == Opcode.CONCAT } shouldBe true
    }

    test("do not replace concat across control flow") {
        val c1 = IRCodeChunk("p8b_main.p8s_start", null)
        c1 += IRInstructions.load(IRDataType.BYTE, 10, 0)
        c1 += IRInstructions.jump(codeLabel("after"))
        c1 += IRInstructions.concat(IRDataType.BYTE, 1, 10, 11)
        c1 += IRInstructions.load(IRDataType.BYTE, 99, 42)
        val c2 = IRCodeChunk("after", null)
        val irProg = makeIRProgram(listOf(c1, c2))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        irProg.chunks().flatMap { it.instructions }.any { it.opcode == Opcode.CONCAT } shouldBe true
    }

    test("cancel adjacent ops") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.unary(Opcode.INV, IRDataType.BYTE, 1),
            IRInstructions.unary(Opcode.INV, IRDataType.BYTE, 1),
            IRInstructions.unary(Opcode.NEG, IRDataType.BYTE, 2),
            IRInstructions.unary(Opcode.NEG, IRDataType.BYTE, 2),
            IRInstructions.binary(Opcode.EXT, IRDataType.BYTE, 3, 33),
            IRInstructions.binary(Opcode.EXT, IRDataType.BYTE, 3, 33),
            IRInstructions.binary(Opcode.EXTS, IRDataType.WORD, 4, 44),
            IRInstructions.binary(Opcode.EXTS, IRDataType.WORD, 4, 44),
            IRInstructions.unary(Opcode.INC, IRDataType.BYTE, 5),
            IRInstructions.unary(Opcode.DEC, IRDataType.BYTE, 5),
            IRInstructions.unary(Opcode.DEC, IRDataType.BYTE, 6),
            IRInstructions.unary(Opcode.INC, IRDataType.BYTE, 6),
            IRInstructions.load(IRDataType.BYTE, 99, 42)       // keep chunk non-empty
        ))
        irProg.chunks().single().instructions.size shouldBe 13
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.size shouldBe 3
        instr[0].opcode shouldBe Opcode.EXT
        instr[0].requireDest().register shouldBe VirtualRegister.int(3)
        instr[0].requireSrcA().register shouldBe VirtualRegister.int(33)
        instr[1].opcode shouldBe Opcode.EXTS
        instr[1].requireDest().register shouldBe VirtualRegister.int(4)
        instr[1].requireSrcA().register shouldBe VirtualRegister.int(44)
        instr[2].opcode shouldBe Opcode.LOAD
        instr[2].requireImmediateInt() shouldBe 42
    }

    test("coalesce redundant LOADX/STOREX to same index is removed") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, 1, IRMemory.indexed("myArray", 10, indexRegType)),
            IRInstructions.unary(Opcode.INC, IRDataType.BYTE, 99),
            IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, 1, IRMemory.indexed("myArray", 10, indexRegType)),
            IRInstructions.returnVoid()
        ))
        irProg.chunks().single().instructions.count { it.opcode==Opcode.STOREX } shouldBe 1
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.count { it.opcode==Opcode.STOREX } shouldBe 0
        instr.count { it.opcode==Opcode.LOADX } shouldBe 1
        instr.size shouldBe 3
    }

    test("do not coalesce LOADX/STOREX when index differs") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, 1, IRMemory.indexed("myArray", 10, indexRegType)),
            IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, 1, IRMemory.indexed("myArray", 11, indexRegType)),
            IRInstructions.returnVoid()
        ))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.count { it.opcode==Opcode.LOADX } shouldBe 1
        instr.count { it.opcode==Opcode.STOREX } shouldBe 1
    }

    test("do not coalesce LOADX/STOREX with intervening read of value") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, 1, IRMemory.indexed("myArray", 10, indexRegType)),
            IRInstructions.binary(Opcode.ADDR, IRDataType.BYTE, 1, 2),
            IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, 1, IRMemory.indexed("myArray", 10, indexRegType)),
            IRInstructions.returnVoid()
        ))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.count { it.opcode==Opcode.STOREX } shouldBe 1
    }

    test("do not coalesce LOADX/STOREX across chunks single-chunk") {
        val c1 = IRCodeChunk("p8b_main.p8s_start", null)
        c1 += IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, 1, IRMemory.indexed("myArray", 10, indexRegType))
        val c2 = IRCodeChunk("other", null)
        c2 += IRInstructions.storeMemory(Opcode.STOREX, IRDataType.BYTE, 1, IRMemory.indexed("myArray", 10, indexRegType))
        c2 += IRInstructions.returnVoid()
        val irProg = makeIRProgram(listOf(c1, c2))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val all = irProg.chunks().flatMap { it.instructions }
        all.count { it.opcode==Opcode.LOADX } shouldBe 1
        all.count { it.opcode==Opcode.STOREX } shouldBe 1
    }

    test("do not fold storem+loadm same symbol different offsets") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, 5, IRMemory.direct("myVar", 0)),
            IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, 6, IRMemory.direct("myVar", 1)),
            IRInstructions.returnVoid()
        ))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.count { it.opcode==Opcode.STOREM } shouldBe 1
        instr.count { it.opcode==Opcode.LOADM } shouldBe 1
        instr.count { it.opcode==Opcode.LOADR } shouldBe 0
    }

    test("do not fold storem+loadm across chunks single-chunk") {
        val c1 = IRCodeChunk("p8b_main.p8s_start", null)
        c1 += IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, 5, IRMemory.direct("myVar"))
        val c2 = IRCodeChunk("other", null)
        c2 += IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, 6, IRMemory.direct("myVar"))
        c2 += IRInstructions.returnVoid()
        val irProg = makeIRProgram(listOf(c1, c2))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val all = irProg.chunks().flatMap { it.instructions }
        all.count { it.opcode==Opcode.STOREM } shouldBe 1
        all.count { it.opcode==Opcode.LOADM } shouldBe 1
        all.count { it.opcode==Opcode.LOADR } shouldBe 0
    }

    test("do not fold storem+loadm different types") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, 5, IRMemory.direct("myVar")),
            IRInstructions.loadMemory(Opcode.LOADM, IRDataType.WORD, 6, IRMemory.direct("myVar")),
            IRInstructions.returnVoid()
        ))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.count { it.opcode==Opcode.STOREM } shouldBe 1
        instr.count { it.opcode==Opcode.LOADM } shouldBe 1
        instr.count { it.opcode==Opcode.LOADR } shouldBe 0
    }

    test("dead store analysis sees reads via secondary operand slots") {
        // r10 is only read as the INDEX register inside the LOADX memory reference. The dead
        // store detector must see that read, otherwise the first LOAD r10 is wrongly removed
        // and the array would be indexed with an uninitialized register.
        val irProg = makeIRProgram(listOf(
            IRInstructions.load(indexRegType, 10, 3),
            IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, 1, IRMemory.indexed("myArray", 10, indexRegType)),
            IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, 1, IRMemory.direct("sym")),
            IRInstructions.load(indexRegType, 10, 7),
            IRInstructions.binaryImmediate(Opcode.ADD, indexRegType, 10, 1),
            IRInstructions.storeMemory(Opcode.STOREM, indexRegType, 10, IRMemory.direct("sym2")),
            IRInstructions.returnVoid()
        ))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.count { it.opcode == Opcode.LOADX } shouldBe 1
        instr.any { it.opcode == Opcode.LOAD && it.requireImmediateInt() == 3 } shouldBe true
    }

    test("dead store to the same register is removed") {
        val irProg = makeIRProgram(listOf(
            IRInstructions.load(IRDataType.BYTE, 1, 5),
            IRInstructions.load(IRDataType.BYTE, 1, 10),
            IRInstructions.unary(Opcode.INC, IRDataType.BYTE, 1),
            IRInstructions.storeMemory(Opcode.STOREM, IRDataType.BYTE, 1, IRMemory.direct("sym")),
            IRInstructions.returnVoid()
        ))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val instr = irProg.chunks().single().instructions
        instr.any { it.opcode == Opcode.LOAD && it.requireImmediateInt() == 5 } shouldBe false
        instr.any { it.opcode == Opcode.LOAD && it.requireImmediateInt() == 10 } shouldBe true
    }

    test("loadr forwarding does not delete live-out source") {
        // r1 is live-out to next chunk, so LOAD r1 should be kept even though r1 has single use in first chunk.
        // The forwarder is conservative: it keeps the source live when it may be needed in a successor chunk,
        // preventing the previous bug where the source LOAD was deleted.
        val c1 = IRCodeChunk("p8b_main.p8s_start", null)
        c1 += IRInstructions.load(IRDataType.BYTE, 1, 42)
        c1 += IRInstructions.move(IRDataType.BYTE, 2, 1)
        c1 += IRInstructions.jump(codeLabel("next"))
        val c2 = IRCodeChunk("next", null)
        c2 += IRInstructions.move(IRDataType.BYTE, 3, 1)
        c2 += IRInstructions.returnVoid()
        val irProg = makeIRProgram(listOf(c1, c2))
        val opt = IRPeepholeOptimizer(irProg, false)
        opt.optimize(true, ErrorReporterForTests())
        val all = irProg.chunks().flatMap { it.instructions }
        // LOAD r1 must be kept because r1 is live-out; whether LOADR is forwarded to LOAD r2 is secondary.
        all.count { it.opcode == Opcode.LOAD && it.dest?.register == VirtualRegister.int(1) } shouldBe 1
    }
})
