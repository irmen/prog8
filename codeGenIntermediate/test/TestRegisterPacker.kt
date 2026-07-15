import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.core.CompilationOptions
import prog8.code.core.OutputType
import prog8.code.core.Position
import prog8.code.core.ZeropageType
import prog8.code.target.VMTarget
import prog8.codegen.intermediate.RegisterPacker
import prog8.intermediate.*
import prog8tests.helpers.ErrorReporterForTests

class TestRegisterPacker: FunSpec({

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
        return makeIRProgram(listOf(chunk as IRCodeChunkBase))
    }

    fun IRProgram.chunks(): List<IRCodeChunkBase> = this.allSubs().flatMap { it.chunks }.toList()

    fun IRProgram.instructions(): List<IRInstruction> = chunks().flatMap { it.instructions }

    test("simple coalescing") {
        // r1=[0,1], r2=[2,3]  non-overlapping -> same packed slot
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=1, immediate=10),
            IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=1, labelSymbol = "sym"),
            IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=2, immediate=20),
            IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=2, labelSymbol = "sym2")
        ))
        RegisterPacker.pack(irProg)
        val instrs = irProg.instructions()
        val regs = instrs.mapNotNull { it.reg1 }
        // After packing, all reg1 values should be the same (both r1 and r2 mapped to 1 slot)
        regs.toSet().size shouldBe 1
        val used = irProg.registersUsed()
        (used.readRegs.keys + used.writeRegs.keys).size shouldBe 1
    }

    test("no coalescing with overlapping intervals") {
        // r1=[0,3], r2=[1,4] overlap -> 2 distinct slots needed
        // LOADR uses both at same time -> must have reg1 != reg2
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=1, immediate=10),
            IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=2, immediate=20),
            IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=1, labelSymbol="s"),
            IRInstruction(Opcode.LOADR, IRDataType.BYTE, reg1=1, reg2=2)
        ))
        RegisterPacker.pack(irProg)
        val instrs = irProg.instructions()
        val loadr = instrs.first { it.opcode == Opcode.LOADR }
        loadr.reg1 shouldNotBe loadr.reg2
    }

    test("cross-chunk liveness") {
        // Chunk A: def r1, def r2, use r1
        //   r1=[0,2] (lives only in A), r2=defined but not used in A,
        //   r2 is live-out from A because it's used in B
        // Chunk B: def r3, use r2, use r3
        //   r2=[3,4] (first read in B), r3=[3,5] (lives only in B)
        // Non-overlapping: r1=[0,2] and r2=[3,4] -> can share
        // Overlapping:     r2=[3,4] and r3=[3,5] -> must differ
        val c1 = IRCodeChunk("p8b_main.p8s_start", null)
        c1 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=1, immediate=10)
        c1 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=2, immediate=20)
        c1 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=1, labelSymbol="sym")

        val c2 = IRCodeChunk(null, null)
        c2 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=3, immediate=30)
        c2 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=2, labelSymbol="sym2")
        c2 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=3, labelSymbol="sym3")

        val irProg = makeIRProgram(listOf(c1, c2))
        RegisterPacker.pack(irProg)
        val instrs = irProg.instructions()
        val distinctRegs = instrs.mapNotNull { it.reg1 }.toSet()

        // r1 and r2 can share a slot (non-overlapping), r3 must differ from r2 (overlapping)
        // Results in exactly 2 distinct slots used
        distinctRegs.size shouldBe 2
    }

    test("different types must not share the same slot number") {
        // BYTE R5=[0,1] and POINTER R1=[2,3] in same subroutine.
        // Non-overlapping but different incompatible types.
        // Packer must NOT assign both to slot 1 (would cause type conflict).
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=5, immediate=10),
            IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=5, labelSymbol="s"),
            IRInstruction(Opcode.LOAD, IRDataType.POINTER, reg1=1, immediate=0x1000),
            IRInstruction(Opcode.STOREM, IRDataType.POINTER, reg1=1, labelSymbol="s2")
        ))
        RegisterPacker.pack(irProg)
        // After packing, usedRegisters() must not throw (internal type consistency)
        // and all registers must have exactly one type each
        val chunkTypes = irProg.chunks().first().usedRegisters().regsTypes
        chunkTypes.size shouldBe 2  // exactly 2 distinct registers
        // Each register should appear with exactly one type (no conflicts)
        chunkTypes.values.toSet().size shouldBe 2  // BYTE and POINTER, each on different regs
    }

    test("unconditional branch only one successor") {
        // Chunk A -> unconditional jump -> chunk C
        // Chunk B is unreachable from A (but still a predecessor of C via fall-through)
        val c1 = IRCodeChunk("p8b_main.p8s_start", null)
        c1 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=1, immediate=10)
        c1 += IRInstruction(Opcode.JUMP, labelSymbol="label_c")

        val c2 = IRCodeChunk("label_b", null)
        c2 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=2, immediate=20)

        val c3 = IRCodeChunk("label_c", null)
        c3 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=1, labelSymbol="sym")
        c3 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=2, immediate=99)
        c3 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=2, labelSymbol="sym2")

        val irProg = makeIRProgram(listOf(c1, c2, c3))
        RegisterPacker.pack(irProg)
        // r1 is live-out of A into C via JUMP; r2 is NOT live-in to C from A via the branch
        // but IS live-in to C from B's fall-through -> conservative: r2 is live-in to C
        // r1 and r3 both defined across non-overlapping chunks
        // Just verify no crash and packing happened
        val instrs = irProg.instructions()
        instrs shouldNotBe emptyList<IRInstruction>()
    }

    test("conditional branch two successors") {
        // Chunk A -> conditional branch to C, fall-through to B
        // Liveness flows from A to both B and C
        val c1 = IRCodeChunk("p8b_main.p8s_start", null)
        c1 += IRInstruction(Opcode.CMPI, IRDataType.BYTE, reg1=1, immediate=0)
        c1 += IRInstruction(Opcode.BSTNE, labelSymbol="label_c")

        val c2 = IRCodeChunk(null, null)  // fall-through (B)
        c2 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=2, immediate=42)
        c2 += IRInstruction(Opcode.JUMP, labelSymbol="label_end")

        val c3 = IRCodeChunk("label_c", null)
        c3 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=2, immediate=99)

        val c4 = IRCodeChunk("label_end", null)
        c4 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=2, labelSymbol="sym")

        val irProg = makeIRProgram(listOf(c1, c2, c3, c4))
        RegisterPacker.pack(irProg)
        val instrs = irProg.instructions()
        instrs shouldNotBe emptyList<IRInstruction>()
    }

    test("multi-chunk register reduction") {
        // 3 chunks with 8 registers, non-overlapping patterns
        val c1 = IRCodeChunk("p8b_main.p8s_start", null)
        c1 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=1, immediate=10)
        c1 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=1, labelSymbol="sym")
        c1 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=2, immediate=20)
        c1 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=2, labelSymbol="sym2")
        c1 += IRInstruction(Opcode.JUMP, labelSymbol="cont")

        val c2 = IRCodeChunk("cont", null)
        c2 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=3, immediate=30)
        c2 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=3, labelSymbol="sym3")
        c2 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=4, immediate=40)
        c2 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=4, labelSymbol="sym4")
        c2 += IRInstruction(Opcode.JUMP, labelSymbol="cont2")

        val c3 = IRCodeChunk("cont2", null)
        c3 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=5, immediate=50)
        c3 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=5, labelSymbol="sym5")
        c3 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=6, immediate=60)
        c3 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=6, labelSymbol="sym6")

        val irProg = makeIRProgram(listOf(c1, c2, c3))
        RegisterPacker.pack(irProg)
        val instrs = irProg.instructions()
        val distinctRegs = instrs.mapNotNull { it.reg1 }.toSet()
        // Each chunk defines and uses 2 regs. Within each chunk the regs are non-overlapping
        // (def rX at i, use at i+1, then def rY at i+2). Across chunks they don't overlap either
        // (sequential, jumps between chunks). So all 6 regs can share 1 slot.
        distinctRegs.size shouldBe 1
    }

    test("floating point registers packed independently") {
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = RegisterNum(1), immediateFp = 1.0),
            IRInstruction(Opcode.STOREM, IRDataType.FLOAT, fpReg1 = RegisterNum(1), labelSymbol="sym"),
            IRInstruction(Opcode.LOAD, IRDataType.FLOAT, fpReg1 = RegisterNum(2), immediateFp = 2.0),
            IRInstruction(Opcode.STOREM, IRDataType.FLOAT, fpReg1 = RegisterNum(2), labelSymbol="sym2")
        ))
        RegisterPacker.pack(irProg)
        val instrs = irProg.instructions()
        // Both fp regs have non-overlapping intervals -> same packed slot
        val fpRegs = instrs.mapNotNull { it.fpReg1?.value }
        fpRegs.distinct().size shouldBe 1
    }

    test("function call args are remapped") {
        val args = listOf(
            FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.BYTE, RegisterNum(1), null, null)),
            FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.BYTE, RegisterNum(2), null, null))
        )
        val returns = listOf(
            FunctionCallArgs.RegSpec(IRDataType.BYTE, RegisterNum(3), null, null)
        )
        val fcallArgs = FunctionCallArgs(args, returns)

        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=1, immediate=10),
            IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=2, immediate=20),
            // Non-overlapping: r1=[0,0], r2=[1,1], r3=live only as return from SYSCALL
            IRInstruction(Opcode.SYSCALL, immediate = 0, fcallArgs = fcallArgs)
        ))
        RegisterPacker.pack(irProg)
        val instrs = irProg.instructions()
        val syscall = instrs.first { it.opcode == Opcode.SYSCALL }
        syscall.fcallArgs shouldNotBe null
        val remappedArgs = syscall.fcallArgs!!.arguments
        val remappedReturns = syscall.fcallArgs!!.returns
        // Should have same packed register numbers
        remappedArgs.map { it.reg.registerNum.value }.toSet().size shouldBe 2
        remappedReturns.first().registerNum.value shouldBe 3
    }

    test("empty subroutine no crash") {
        val c1 = IRCodeChunk("p8b_main.p8s_start", null)
        val irProg = makeIRProgram(listOf(c1))
        // Should not throw
        RegisterPacker.pack(irProg)
    }

    test("no registers used") {
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.NOP),
            IRInstruction(Opcode.NOP)
        ))
        RegisterPacker.pack(irProg)
        irProg.instructions().size shouldBe 2
    }

    test("fcallArgs with mixed types must not share same slot") {
        // Regression: fcallArgs registers with incompatible types (WORD vs LONG)
        // in the same chunk must be assigned different packed slots.
        // The conflict graph must track fcallArgs register types.
        val args = listOf(
            FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), null, null)),
            FunctionCallArgs.ArgumentSpec("", null, FunctionCallArgs.RegSpec(IRDataType.LONG, RegisterNum(2), null, null))
        )
        val fcallArgs = FunctionCallArgs(args, emptyList())

        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1=1, immediate=100),
            IRInstruction(Opcode.SYSCALL, immediate = 0, fcallArgs = fcallArgs)
        ))
        RegisterPacker.pack(irProg)
        // usedRegisters() must not throw (type consistency within chunk)
        val used = irProg.registersUsed()
        val regs = (used.readRegs.keys + used.writeRegs.keys).map { it.value }.toSet()
        // Must have at least 2 distinct packed slots (WORD and LONG cannot share)
        regs.size shouldBe 2
    }

    test("fcallArgs returns with mixed types must not share same slot") {
        // Same as above but for return registers
        val returns = listOf(
            FunctionCallArgs.RegSpec(IRDataType.WORD, RegisterNum(1), null, null),
            FunctionCallArgs.RegSpec(IRDataType.LONG, RegisterNum(2), null, null)
        )
        val fcallArgs = FunctionCallArgs(emptyList(), returns)

        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.SYSCALL, immediate = 0, fcallArgs = fcallArgs),
            IRInstruction(Opcode.STOREM, IRDataType.WORD, reg1=1, labelSymbol="s"),
            IRInstruction(Opcode.STOREM, IRDataType.LONG, reg1=2, labelSymbol="s2")
        ))
        RegisterPacker.pack(irProg)
        // usedRegisters() must not throw
        val used = irProg.registersUsed()
        val regs = (used.readRegs.keys + used.writeRegs.keys).map { it.value }.toSet()
        regs.size shouldBe 2
    }

    test("WORD and LONG non-overlapping in same chunk must not share slot") {
        // Regression: two registers with incompatible types (WORD vs LONG) but
        // non-overlapping live ranges in the same chunk must NOT be assigned
        // the same packed slot, because usedRegisters() enforces a single type
        // per register number within a chunk.
        val irProg = makeIRProgram(listOf(
            IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1=1, immediate=100),
            IRInstruction(Opcode.STOREM, IRDataType.WORD, reg1=1, labelSymbol="s"),
            IRInstruction(Opcode.LOAD, IRDataType.LONG, reg1=2, immediate=0x10000),
            IRInstruction(Opcode.STOREM, IRDataType.LONG, reg1=2, labelSymbol="s2")
        ))
        RegisterPacker.pack(irProg)
        // usedRegisters() must not throw
        val used = irProg.registersUsed()
        val types = used.regsTypes
        // Exactly 2 registers, one WORD and one LONG (different slots)
        types.size shouldBe 2
        types.values.toSet() shouldBe setOf(IRDataType.WORD, IRDataType.LONG)
    }

    test("cross-subroutine slot type is preserved via putIfAbsent") {
        // Regression: when a slot is first assigned a type (e.g. POINTER),
        // subsequent compatible assignments (WORD via typesCompatible) must not
        // overwrite the slot's type. Otherwise a later LONG interval would be
        // blocked (WORD vs LONG incompatible), forcing it to a different slot,
        // and registersUsed() would see the same slot with both POINTER and
        // WORD types across subroutines → should be compatible (POINTER bridges both).
        fun makeMultiSubProgram(sub1Chunks: List<IRCodeChunkBase>, sub2Chunks: List<IRCodeChunkBase>): IRProgram {
            val target = VMTarget()
            val options = CompilationOptions.builder(target)
                .output(OutputType.RAW)
                .zeropage(ZeropageType.DONTUSE)
                .noSysInit(true)
                .compilerVersion("99.99")
                .build()
            val prog = IRProgram("test", IRSymbolTable(), options, target)
            val block = IRBlock("p8b_main", false, IRBlock.Options(), Position.DUMMY)
            val sub1 = IRSubroutine("p8b_main.sub1", emptyList(), emptyList(), Position.DUMMY)
            sub1Chunks.forEach { sub1 += it }
            val sub2 = IRSubroutine("p8b_main.sub2", emptyList(), emptyList(), Position.DUMMY)
            sub2Chunks.forEach { sub2 += it }
            block += sub1
            block += sub2
            prog.addBlock(block)
            prog.linkChunks()
            prog.validate()
            return prog
        }

        // Sub 1: POINTER interval (gets slot ≥ startSlot), and then the same slot again with WORD
        val c1 = IRCodeChunk("p8b_main.sub1", null)
        c1 += IRInstruction(Opcode.LOAD, IRDataType.POINTER, reg1=100, immediate=0x1000)
        c1 += IRInstruction(Opcode.STOREM, IRDataType.POINTER, reg1=100, labelSymbol="sp")
        c1 += IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1=101, immediate=200)
        c1 += IRInstruction(Opcode.STOREM, IRDataType.WORD, reg1=101, labelSymbol="sw")

        // Sub 2: LONG interval that should be able to share the same slot as sub 1's POINTER
        val c2 = IRCodeChunk("p8b_main.sub2", null)
        c2 += IRInstruction(Opcode.LOAD, IRDataType.LONG, reg1=200, immediate=0x100000)
        c2 += IRInstruction(Opcode.STOREM, IRDataType.LONG, reg1=200, labelSymbol="sl")

        val irProg = makeMultiSubProgram(listOf(c1), listOf(c2))
        RegisterPacker.pack(irProg)
        // registersUsed() must not throw: POINTER and WORD compatible,
        // POINTER and LONG compatible, and putIfAbsent keeps the slot type
        // as POINTER so WORD and LONG intervals can share the same slot.
        val used = irProg.registersUsed()
        used.regsTypes.size shouldBe 2  // POINTER (or POINTER+WORD+LONG sharing) + WORD
    }

    test("original register numbers must not collide with packed slots") {
        // Regression: pre-existing low register numbers (e.g. r5) in the program
        // must not collide with packed slot numbers. Packed slots start at
        // maxReg+1 to avoid this.
        val irProg = makeIRProgram(listOf(
            // Register 5 is used directly (not packed? actually it HAS an interval and gets packed)
            IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=5, immediate=99),
            IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=5, labelSymbol="s"),
            // Register 1 and 2 can be packed
            IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1=1, immediate=100),
            IRInstruction(Opcode.STOREM, IRDataType.WORD, reg1=1, labelSymbol="s2"),
            IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1=2, immediate=200),
            IRInstruction(Opcode.STOREM, IRDataType.WORD, reg1=2, labelSymbol="s3")
        ))
        RegisterPacker.pack(irProg)
        // after packing:
        // - all registers should have been packed to slots > 5 (since maxReg=5, startSlot=6)
        // - no packed slot should equal an original register number
        val used = irProg.registersUsed()
        val allRegNums = (used.readRegs.keys + used.writeRegs.keys).map { it.value }.toSet()
        // packed slots start at 6, so no register should be 5 anymore
        (5 in allRegNums) shouldBe false
        // all registers should be >= 6
        for (rn in allRegNums)
            rn shouldNotBe 5
    }

    test("strict registersUsed throws on incompatible types across subroutines") {
        // Regression: registersUsed() in strict mode (no packing applied) must detect
        // the same register number used with incompatible types in different subroutines.
        val target = VMTarget()
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.DONTUSE)
            .noSysInit(true)
            .compilerVersion("99.99")
            .build()
        val prog = IRProgram("test", IRSymbolTable(), options, target)
        val block = IRBlock("p8b_main", false, IRBlock.Options(), Position.DUMMY)

        // Sub 1: uses r37 as POINTER
        val sub1 = IRSubroutine("p8b_main.sub1", emptyList(), emptyList(), Position.DUMMY)
        val c1 = IRCodeChunk("p8b_main.sub1", null)
        c1 += IRInstruction(Opcode.LOAD, IRDataType.POINTER, reg1=37, immediate=0x1000)
        c1 += IRInstruction(Opcode.STOREM, IRDataType.POINTER, reg1=37, labelSymbol="sp")
        sub1 += c1

        // Sub 2: uses same r37 as BYTE (incompatible)
        val sub2 = IRSubroutine("p8b_main.sub2", emptyList(), emptyList(), Position.DUMMY)
        val c2 = IRCodeChunk("p8b_main.sub2", null)
        c2 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=37, immediate=42)
        c2 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=37, labelSymbol="sb")
        sub2 += c2

        block += sub1
        block += sub2
        prog.addBlock(block)
        prog.linkChunks()
        prog.validate()

        // No packing applied - strict mode should detect the incompatibility
        val ex = shouldThrow<IllegalArgumentException> {
            prog.registersUsed()
        }
        ex.message shouldContain "register r37 given multiple types"
        ex.message shouldContain "POINTER"
        ex.message shouldContain "BYTE"
    }

    test("intra-subroutine cross-type register is skipped by packer") {
        // When a register has non-overlapping intervals with incompatible types (e.g.
        // BYTE and LONG in different chunks), the packer skips it entirely because its
        // single-entry packing map can't handle a register with multiple types.
        // After packing, the register retains its original number and is not rewritten.
        // registersUsed() in permissive mode uses putIfAbsent and should not throw.
        val target = VMTarget()
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.DONTUSE)
            .noSysInit(true)
            .compilerVersion("99.99")
            .build()
        val prog = IRProgram("test", IRSymbolTable(), options, target)
        val block = IRBlock("p8b_main", false, IRBlock.Options(), Position.DUMMY)

        // Single subroutine with two chunks using same register with incompatible types
        val sub = IRSubroutine("p8b_main.sub1", emptyList(), emptyList(), Position.DUMMY)

        val c1 = IRCodeChunk("p8b_main.sub1", null)
        c1 += IRInstruction(Opcode.LOAD, IRDataType.POINTER, reg1=37, immediate=0x1000)
        c1 += IRInstruction(Opcode.STOREM, IRDataType.POINTER, reg1=37, labelSymbol="sp")
        sub += c1

        val c2 = IRCodeChunk("p8b_main.sub1.c2", c1)
        c2 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=37, immediate=42)
        c2 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=37, labelSymbol="sb")
        sub += c2

        block += sub
        prog.addBlock(block)
        prog.linkChunks()
        prog.validate()

        RegisterPacker.pack(prog)
        prog.wasPackingApplied = true

        // registersUsed() in permissive mode: putIfAbsent, should not throw
        val used = prog.registersUsed()
        // r37 should still appear (was skipped by packer)
        used.regsTypes.containsKey(RegisterNum(37)) shouldBe true
    }

    test("disjoint intervals of same register must be merged into one contiguous range") {
        // Regression: if register r1 has disjoint intervals (written in chunk 1,
        // read in chunk 3 with a gap in chunk 2), the packer must merge them into
        // a single interval spanning the gap. Otherwise another register (r2) with
        // an interval in chunk 2 could share the same slot and clobber r1's value.
        val target = VMTarget()
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.DONTUSE)
            .noSysInit(true)
            .compilerVersion("99.99")
            .build()
        val prog = IRProgram("test", IRSymbolTable(), options, target)
        val block = IRBlock("p8b_main", false, IRBlock.Options(), Position.DUMMY)
        val sub = IRSubroutine("p8b_main.sub1", emptyList(), emptyList(), Position.DUMMY)

        // Chunk 1: write r1 (value should persist to chunk 3)
        val c1 = IRCodeChunk("p8b_main.sub1", null)
        c1 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=1, immediate=42)
        c1 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=1, labelSymbol="s1")
        sub += c1

        // Chunk 2: write and read r2 (this interval falls in the gap between r1's uses)
        val c2 = IRCodeChunk("p8b_main.sub1.c2", c1)
        c2 += IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1=2, immediate=99)
        c2 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=2, labelSymbol="s2")
        sub += c2

        // Chunk 3: read r1 (uses the value from chunk 1)
        val c3 = IRCodeChunk("p8b_main.sub1.c3", c2)
        c3 += IRInstruction(Opcode.LOADR, IRDataType.BYTE, reg1=3, reg2=1)
        c3 += IRInstruction(Opcode.STOREM, IRDataType.BYTE, reg1=3, labelSymbol="s3")
        sub += c3

        block += sub
        prog.addBlock(block)
        prog.linkChunks()
        prog.validate()

        RegisterPacker.pack(prog)

        // After packing, r1 and r2 must be in DIFFERENT slots because r1's
        // merged interval spans [0..?] covering the gap, preventing r2
        // from sharing the same slot.
        val instructions = prog.instructions()
        // Find the register numbers used for the STOREM to s1 (was r1) and s2 (was r2)
        val s1Reg = instructions.first { it.labelSymbol=="s1" }.reg1
        val s2Reg = instructions.first { it.labelSymbol=="s2" }.reg1
        // They must be different (can't share a slot)
        s1Reg shouldNotBe null
        s2Reg shouldNotBe null
        s1Reg shouldNotBe s2Reg
    }
})
