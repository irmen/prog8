package prog8tests.codegen.m68k

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.core.*
import prog8.code.target.Qemu68kTarget
import prog8.codegen.m68k.M68kRegisterAllocator
import prog8.codegen.m68k.renumberVregsPerSubroutine
import prog8.intermediate.*
import prog8tests.helpers.DummyStringEncoder

class TestM68kRegisterAllocator : FunSpec({
    test("allocator runs on a simple subroutine and produces a map") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 1, 2))
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 3, 1))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)
        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        // should allocate at least one vreg
        alloc.isNotEmpty() shouldBe true
    }

    test("pointer-base vreg is constrained to the ADDRESS class (a2-a4)") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        // load byte from (r5) into r1 : r5 is the pointer base (PREFER_ADDRESS), r1 is data
        val mem = IRMemory.indirect(pointerRegister = 5, displacement = 0)
        chunk.instructions.add(IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, 1, mem))
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 1, 1))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        val ptrReg = alloc[VirtualRegister.int(5)]
        listOf("a2", "a3", "a4").contains(ptrReg) shouldBe true
        // the data vreg should be in a data register, not address
        val dataReg = alloc[VirtualRegister.int(1)]
        listOf("d2", "d3", "d4", "d5", "d6").contains(dataReg) shouldBe true
    }

    test("pointer call-argument vreg is hinted to ADDRESS pool") {
        // M1/F4: a pointer vreg used only as an a-register call argument should be
        // allocated to an address register rather than routed through a data register.
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        // Load pointer from memory (not an immediate), so the vreg is not eligible for
        // immediate-argument forwarding and must be allocated.
        chunk.instructions.add(IRInstructions.loadMemory(Opcode.LOADM, IRDataType.POINTER, 1, IRMemory.direct("p8b_test.p8v_ptr")))
        val callSite = CallSite(
            CallTarget.Direct(CodeReference.Label("test.other", 0)),
            arguments = listOf(
                Calls.argument(1, IRDataType.POINTER, CallLocation.HardwareRegister(CallingConventionSlot(18)))
            )
        )
        chunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = callSite))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val other = IRSubroutine("test.other", emptyList(), emptyList(), Position.DUMMY)
        other.chunks.add(IRCodeChunk(null, null))
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        block.children.add(other)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        listOf("a2", "a3", "a4").contains(alloc[VirtualRegister.int(1)]) shouldBe true
    }

    test("value live across a CALL is never allocated to a caller-saved register") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 1, 2))
        chunk.instructions.add(IRInstructions.load(IRDataType.WORD, 9, 5))
        val callSite = CallSite(CallTarget.Direct(CodeReference.Label("p8_other", 0)))
        chunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = callSite))
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 3, 1))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        // r1 is live across the call; it must be callee-saved (never d0/d1/a0/a1)
        val r1 = alloc[VirtualRegister.int(1)]
        listOf("d0", "d1", "a0", "a1", "fp0", "fp1").contains(r1) shouldBe false
    }

    test("callee-saved registers used by a subroutine are reported for the prologue/epilogue") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 1, 2))
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 3, 1))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        val used = result.calleeSavedBySub[sub]
        used shouldBe result.mapBySub[sub]!!.values.toSet()
        (used!!.size > 0) shouldBe true
    }

    test("spill demand is reported when register pressure exceeds the data pool") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        // 7 distinct vregs all live at once: exceeds the 5-register data pool
        for (i in 1..7) chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, i, i + 10))
        // keep them all live: use each in a final sum
        chunk.instructions.add(IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 100, 1))
        for (i in 2..7) chunk.instructions.add(IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 100, i))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        (result.spillDemandBySub[sub]!! > 0) shouldBe true
        result.spilledVregsBySub[sub]!!.isNotEmpty() shouldBe true
    }

    test("USE_DEF and USE in the same instruction do not share a register") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        // r1 is USE_DEF (read+written), r2 is USE in the same ADDR instruction.
        // They are simultaneously live and must not be colored identically.
        chunk.instructions.add(IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 1, 2))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        alloc[VirtualRegister.int(1)] shouldNotBe alloc[VirtualRegister.int(2)]
    }

    test("ordinary subroutine calls get no fixed-boundary metadata entry") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 1, 2))
        val callSite = CallSite(CallTarget.Direct(CodeReference.Label("p8_other", 0)))
        chunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = callSite))
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 3, 1))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        result.callSiteEffects[sub] shouldBe null
    }

    test("ordinary callee preserves only the registers its known callers require") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        // caller: r1 is live across the call to test.callee, so the callee must
        // preserve exactly the register r1 is colored to.
        val callerChunk = IRCodeChunk(null, null)
        callerChunk.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        val callSite = CallSite(CallTarget.Direct(CodeReference.Label("test.callee", 0)))
        callerChunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = callSite))
        callerChunk.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_out")))
        val caller = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        caller.chunks.add(callerChunk)
        // callee: five simultaneously live values color all five DATA registers.
        val calleeChunk = IRCodeChunk(null, null)
        for (i in 1..5) calleeChunk.instructions.add(IRInstructions.load(IRDataType.WORD, i, i))
        for (i in 1..5) calleeChunk.instructions.add(
            IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, i, IRMemory.direct("p8b_test.p8v_callee_out$i"))
        )
        val callee = IRSubroutine("test.callee", emptyList(), emptyList(), Position.DUMMY)
        callee.chunks.add(calleeChunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(caller)
        block.children.add(callee)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        val liveReg = result.mapBySub[caller]!![VirtualRegister.int(1)]!!
        val calleeColored = result.mapBySub[callee]!!.values.toSet()
        calleeColored shouldBe setOf("d2", "d3", "d4", "d5", "d6")
        (liveReg in calleeColored) shouldBe true
        result.calleeSavedBySub[callee] shouldBe setOf(liveReg)
    }

    test("callee with its address taken keeps all its colored registers") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        // The address of test.callee escapes, so unknown code can enter it even
        // though there is also a known ordinary call.
        val callerChunk = IRCodeChunk(null, null)
        callerChunk.instructions.add(IRInstructions.loadAddress(IRDataType.POINTER, 9, "test.callee"))
        callerChunk.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        val callSite = CallSite(CallTarget.Direct(CodeReference.Label("test.callee", 0)))
        callerChunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = callSite))
        callerChunk.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_out")))
        val caller = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        caller.chunks.add(callerChunk)
        val calleeChunk = IRCodeChunk(null, null)
        for (i in 1..5) calleeChunk.instructions.add(IRInstructions.load(IRDataType.WORD, i, i))
        for (i in 1..5) calleeChunk.instructions.add(
            IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, i, IRMemory.direct("p8b_test.p8v_callee_out$i"))
        )
        val callee = IRSubroutine("test.callee", emptyList(), emptyList(), Position.DUMMY)
        callee.chunks.add(calleeChunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(caller)
        block.children.add(callee)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        result.calleeSavedBySub[callee] shouldBe result.mapBySub[callee]!!.values.toSet()
    }

    test("callee without a known ordinary caller keeps all its colored registers") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        // Only an indirect call exists: no caller demand is observable for the callee.
        val callerChunk = IRCodeChunk(null, null)
        callerChunk.instructions.add(IRInstructions.loadAddress(IRDataType.POINTER, 9, "p8b_test.p8v_fnptr"))
        callerChunk.instructions.add(IRInstructions.call(Opcode.CALLI, CallSite(CallTarget.Direct(codeIndirect(9)))))
        callerChunk.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        callerChunk.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_out")))
        val caller = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        caller.chunks.add(callerChunk)
        val calleeChunk = IRCodeChunk(null, null)
        for (i in 1..5) calleeChunk.instructions.add(IRInstructions.load(IRDataType.WORD, i, i))
        for (i in 1..5) calleeChunk.instructions.add(
            IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, i, IRMemory.direct("p8b_test.p8v_callee_out$i"))
        )
        val callee = IRSubroutine("test.callee", emptyList(), emptyList(), Position.DUMMY)
        callee.chunks.add(calleeChunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(caller)
        block.children.add(callee)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        result.calleeSavedBySub[callee] shouldBe result.mapBySub[callee]!!.values.toSet()
    }

    test("callee entered by a tail call keeps all its colored registers") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        // Nothing is live across the call, but call+return becomes a tail branch,
        // so the callee returns for the caller and must keep its full save set.
        val callerChunk = IRCodeChunk(null, null)
        callerChunk.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        val callSite = CallSite(CallTarget.Direct(CodeReference.Label("test.callee", 0)))
        callerChunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = callSite))
        callerChunk.instructions.add(IRInstruction(Opcode.RETURN, null))
        val caller = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        caller.chunks.add(callerChunk)
        val calleeChunk = IRCodeChunk(null, null)
        for (i in 1..5) calleeChunk.instructions.add(IRInstructions.load(IRDataType.WORD, i, i))
        for (i in 1..5) calleeChunk.instructions.add(
            IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, i, IRMemory.direct("p8b_test.p8v_callee_out$i"))
        )
        val callee = IRSubroutine("test.callee", emptyList(), emptyList(), Position.DUMMY)
        callee.chunks.add(calleeChunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(caller)
        block.children.add(callee)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        result.calleeSavedBySub[callee] shouldBe result.mapBySub[callee]!!.values.toSet()
    }

    test("callee named in raw inline assembly keeps all its colored registers") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        // The raw asm chunk calls test.callee directly, so no caller demand covers it.
        val callerChunk = IRCodeChunk(null, null)
        callerChunk.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        val callSite = CallSite(CallTarget.Direct(CodeReference.Label("test.callee", 0)))
        callerChunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = callSite))
        callerChunk.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_out")))
        val caller = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        caller.chunks.add(callerChunk)
        caller.chunks.add(IRInlineAsmChunk(null, "jsr  test.callee", false, null))
        val calleeChunk = IRCodeChunk(null, null)
        for (i in 1..5) calleeChunk.instructions.add(IRInstructions.load(IRDataType.WORD, i, i))
        for (i in 1..5) calleeChunk.instructions.add(
            IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, i, IRMemory.direct("p8b_test.p8v_callee_out$i"))
        )
        val callee = IRSubroutine("test.callee", emptyList(), emptyList(), Position.DUMMY)
        callee.chunks.add(calleeChunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(caller)
        block.children.add(callee)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        result.calleeSavedBySub[callee] shouldBe result.mapBySub[callee]!!.values.toSet()
    }

    test("callee named by its assembler-mangled symbol in raw inline assembly keeps all its colored registers") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        // "test.x" is emitted as "test_x" (a trailing size-letter dot is mangled), so the raw asm names it that way.
        val callerChunk = IRCodeChunk(null, null)
        callerChunk.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        val callSite = CallSite(CallTarget.Direct(CodeReference.Label("test.x", 0)))
        callerChunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = callSite))
        callerChunk.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_out")))
        val caller = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        caller.chunks.add(callerChunk)
        caller.chunks.add(IRInlineAsmChunk(null, "jsr  test_x", false, null))
        val calleeChunk = IRCodeChunk(null, null)
        for (i in 1..5) calleeChunk.instructions.add(IRInstructions.load(IRDataType.WORD, i, i))
        for (i in 1..5) calleeChunk.instructions.add(
            IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, i, IRMemory.direct("p8b_test.p8v_callee_out$i"))
        )
        val callee = IRSubroutine("test.x", emptyList(), emptyList(), Position.DUMMY)
        callee.chunks.add(calleeChunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(caller)
        block.children.add(callee)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        result.calleeSavedBySub[callee] shouldBe result.mapBySub[callee]!!.values.toSet()
    }

    test("non-overlapping vregs share one hardware register") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        // r1 is dead before r2 is born (pure DEF then last USE each): no
        // interference, so they must coalesce onto one register.
        chunk.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        chunk.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_out1")))
        chunk.instructions.add(IRInstructions.load(IRDataType.WORD, 2, 6))
        chunk.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 2, IRMemory.direct("p8b_test.p8v_out2")))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        alloc[VirtualRegister.int(1)] shouldBe alloc[VirtualRegister.int(2)]
    }

    test("simultaneously live vregs get different hardware registers") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        // r1 and r2 overlap (both live between the second and third instruction).
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 1, 10))
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 2, 20))
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 3, 1))
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 4, 2))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        alloc[VirtualRegister.int(1)] shouldNotBe alloc[VirtualRegister.int(2)]
    }

    test("vreg live across chunks keeps one hardware register") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk1 = IRCodeChunk(null, null)
        chunk1.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        val chunk2 = IRCodeChunk(null, null)
        chunk2.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 3, 1))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk1)
        sub.chunks.add(chunk2)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        // r1 is defined in the first chunk and used in the second: cross-chunk
        // liveness must carry it, and it must differ from its simultaneous user r3.
        alloc.containsKey(VirtualRegister.int(1)) shouldBe true
        alloc[VirtualRegister.int(1)] shouldNotBe alloc[VirtualRegister.int(3)]
    }

    test("float vregs are routed to the FPU pool") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        // fr1 and fr2 are both used by the same call: simultaneously live floats.
        val callSite = CallSite(
            CallTarget.Direct(CodeReference.Label("p8_other", 0)),
            arguments = listOf(Calls.argument(1, IRDataType.FLOAT), Calls.argument(2, IRDataType.FLOAT))
        )
        chunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = callSite))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        val fr1 = alloc[VirtualRegister.float(1)]
        val fr2 = alloc[VirtualRegister.float(2)]
        listOf("fp2", "fp3", "fp4", "fp5", "fp6", "fp7").contains(fr1) shouldBe true
        listOf("fp2", "fp3", "fp4", "fp5", "fp6", "fp7").contains(fr2) shouldBe true
        fr1 shouldNotBe fr2
    }

    test("fixed argument slot register is avoided for live-across vregs") {
        // Slot 12 is D2: a pool register AND a conventional asmsub arg slot. Argument
        // marshalling overwrites it before the call, so live-across values must avoid it.
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        val slotD2 = CallLocation.HardwareRegister(CallingConventionSlot(12))
        chunk.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        chunk.instructions.add(IRInstructions.load(IRDataType.WORD, 5, 7))
        val callSite = CallSite(
            CallTarget.Direct(CodeReference.Label("p8_other", 0)),
            arguments = listOf(Calls.argument(5, IRDataType.WORD, slotD2))
        )
        chunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = callSite))
        chunk.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_out")))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        result.callSiteEffects[sub]?.get(0)?.reads shouldBe setOf("d2")
        result.mapBySub[sub]?.get(VirtualRegister.int(1)) shouldNotBe "d2"
    }

    test("constant call argument feeding a hardware argument takes no register or slot") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        val slotD2 = CallLocation.HardwareRegister(CallingConventionSlot(12))
        chunk.instructions.add(IRInstructions.load(IRDataType.WORD, 5, 7))
        val callSite = CallSite(
            CallTarget.Direct(CodeReference.Label("p8_other", 0)),
            arguments = listOf(Calls.argument(5, IRDataType.WORD, slotD2))
        )
        chunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = callSite))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        (VirtualRegister.int(5) in result.mapBySub[sub].orEmpty()) shouldBe false
        (VirtualRegister.int(5) in result.spilledVregsBySub[sub].orEmpty()) shouldBe false
    }

    test("fixed result slot register is avoided for live-across vregs") {
        // Slot 12 is D2: the callee writes the result there during the call, so
        // live-across values must avoid it even though nothing declares a clobber.
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        val slotD2 = CallLocation.HardwareRegister(CallingConventionSlot(12))
        chunk.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        val callSite = CallSite(
            CallTarget.Direct(CodeReference.Label("p8_other", 0)),
            results = listOf(Calls.result(0, IRDataType.WORD, slotD2))
        )
        chunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = callSite))
        chunk.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_out")))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        result.callSiteEffects[sub]?.get(0)?.defined shouldBe setOf("d2")
        result.mapBySub[sub]!![VirtualRegister.int(1)] shouldNotBe "d2"
    }

    test("default int call result is pinned to DATA despite a later address use") {
        // r5 is defined by a default-location CALL (arrives in d0) but used as an
        // indirect pointer base (PREFER_ADDRESS hint). The pin must win: DATA pool.
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        val callSite = CallSite(
            CallTarget.Direct(CodeReference.Label("p8_other", 0)),
            results = listOf(Calls.result(5, IRDataType.WORD))
        )
        chunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = callSite))
        val mem = IRMemory.indirect(pointerRegister = 5, displacement = 0)
        chunk.instructions.add(IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, 1, mem))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        listOf("d2", "d3", "d4", "d5", "d6").contains(alloc[VirtualRegister.int(5)]) shouldBe true
    }

    test("slot result takes the class of its slot register") {
        // r6 arrives in a2 (address slot 20); r7 arrives in d2 (data slot 12).
        // Neither has a hint, so without pinning both would land in DATA.
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        val siteA = CallSite(
            CallTarget.Direct(CodeReference.Label("test.other", 0)),
            results = listOf(Calls.result(6, IRDataType.LONG, CallLocation.HardwareRegister(CallingConventionSlot(20))))
        )
        chunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = siteA))
        val siteD = CallSite(
            CallTarget.Direct(CodeReference.Label("test.other", 0)),
            results = listOf(Calls.result(7, IRDataType.WORD, CallLocation.HardwareRegister(CallingConventionSlot(12))))
        )
        chunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = siteD))
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 7, 7))
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.LONG, 8, 6))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        // The call target is an ordinary Prog8 subroutine, so the boundary is
        // precise (slot reads/defs only) rather than whole-pool conservative.
        val other = IRSubroutine("test.other", emptyList(), emptyList(), Position.DUMMY)
        other.chunks.add(IRCodeChunk(null, null))
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        block.children.add(other)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        listOf("a2", "a3", "a4").contains(alloc[VirtualRegister.int(6)]) shouldBe true
        listOf("d2", "d3", "d4", "d5", "d6").contains(alloc[VirtualRegister.int(7)]) shouldBe true
    }

    test("asmsub that follows the register rule keeps pool values live across it") {
        // An asmsub body saves every callee-saved register it writes, so a call only
        // destroys the caller-saved set and live values can stay in the pool.
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val head = IRCodeChunk(null, null)
        head.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 50))
        val callChunk = IRCodeChunk("test.mid", null)
        val site = CallSite(CallTarget.Direct(CodeReference.Label("test.memlike", 0)))
        callChunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = site))
        val tail = IRCodeChunk("test.tail", null)
        tail.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_out")))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(head)
        sub.chunks.add(callChunk)
        sub.chunks.add(tail)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        val asmChunk = IRInlineAsmChunk("test.memlike", "rts", false, null)
        block.children.add(
            IRAsmSubroutine(
                "test.memlike", null, emptySet(), false,
                emptyList(), emptyList(), asmChunk, Position.DUMMY, false
            )
        )
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate()
        alloc.mapBySub[sub]?.get(VirtualRegister.int(1)) shouldNotBe null
    }

    test("call to an unresolved target spills values live across it") {
        // An extsub is an absolute target with no body the compiler can check, so the whole pool is treated as destroyed.
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val head = IRCodeChunk(null, null)
        head.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 50))
        val callChunk = IRCodeChunk("test.mid", null)
        val site = CallSite(CallTarget.Direct(CodeReference.Absolute(MemoryAddress(0x4000u))))
        callChunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = site))
        val tail = IRCodeChunk("test.tail", null)
        tail.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_out")))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(head)
        sub.chunks.add(callChunk)
        sub.chunks.add(tail)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate()
        alloc.mapBySub[sub]?.get(VirtualRegister.int(1)) shouldBe null
    }

    test("asmsub with declared empty clobbers keeps values in pool registers") {
        // When the source explicitly wrote clobbers(), the empty set is a
        // trustworthy declaration that the asmsub touches nothing else.
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val head = IRCodeChunk(null, null)
        head.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 50))
        val callChunk = IRCodeChunk("test.mid", null)
        val site = CallSite(CallTarget.Direct(CodeReference.Label("test.emptyclobber", 0)))
        callChunk.instructions.add(IRInstruction(Opcode.CALL, null, callSite = site))
        val tail = IRCodeChunk("test.tail", null)
        tail.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_out")))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(head)
        sub.chunks.add(callChunk)
        sub.chunks.add(tail)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        val asmChunk = IRInlineAsmChunk("test.emptyclobber", "rts", false, null)
        block.children.add(
            IRAsmSubroutine(
                "test.emptyclobber", null, emptySet(), true,
                emptyList(), emptyList(), asmChunk, Position.DUMMY, false
            )
        )
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate()
        alloc.mapBySub[sub]?.get(VirtualRegister.int(1)) shouldNotBe null
    }

    test("noframe subroutine does not receive allocated registers or saves") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 42))
        val sub = IRSubroutine("test.noframe", emptyList(), emptyList(), Position.DUMMY, noframe = true)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate()
        alloc.mapBySub[sub] shouldBe null
        alloc.calleeSavedBySub[sub] shouldBe emptySet()
        alloc.spilledVregsBySub[sub] shouldBe emptyList()
    }

    test("renumber compacts vreg numbers per subroutine, ints and floats separately") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 9, 5))
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 13, 9))
        chunk.instructions.add(IRInstructions.loadFloat(7, 1.5))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val types = renumberVregsPerSubroutine(program)
        types shouldNotBe null
        val intNums = mutableSetOf<Int>()
        val floatNums = mutableSetOf<Int>()
        program.forEachInstruction { ins ->
            for (ra in ins.registerAccesses) {
                if (ra.register.isFloat) floatNums.add(ra.register.num)
                else intNums.add(ra.register.num)
            }
        }
        // ints {5,9,13} compacted to {1,2,3} in order; float {fr7} to {fr1}
        intNums shouldBe setOf(1, 2, 3)
        floatNums shouldBe setOf(1)
        // rebuilt type map covers the renumbered vregs and verifies cleanly
        program.verifyRegisterTypes(types!!)
    }

    test("renumber is independent per subroutine") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        for ((name, base) in listOf("test.first" to 40, "test.second" to 90)) {
            val chunk = IRCodeChunk(null, null)
            chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, base + 2, base + 1))
            val sub = IRSubroutine(name, emptyList(), emptyList(), Position.DUMMY)
            sub.chunks.add(chunk)
            block.children.add(sub)
        }
        program.blocks.add(block)

        renumberVregsPerSubroutine(program) shouldNotBe null
        for (sub in program.allSubs()) {
            val nums = mutableSetOf<Int>()
            sub.forEachInstruction { ins ->
                for (ra in ins.registerAccesses) nums.add(ra.register.num)
            }
            nums shouldBe setOf(1, 2)
        }
        // same small numbers reused across subs: program-wide distinct count shrinks
        program.countUsedRegisters() shouldBe 2
    }

    test("renumber leaves reserved syscall vregs (>= 99000) untouched") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 99001, 5))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        renumberVregsPerSubroutine(program) shouldNotBe null
        val nums = mutableSetOf<Int>()
        program.forEachInstruction { ins ->
            for (ra in ins.registerAccesses) nums.add(ra.register.num)
        }
        (99001 in nums) shouldBe true
        (5 in nums) shouldBe false
    }

    test("renumber bails out when raw asm names the regfile") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 9, 5))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        sub.chunks.add(IRInlineAsmChunk("test.start.asm", "move.w  p8_regfile+0,d0", false, null))
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        renumberVregsPerSubroutine(program) shouldBe null
        val nums = mutableSetOf<Int>()
        program.forEachInstruction { ins ->
            for (ra in ins.registerAccesses) nums.add(ra.register.num)
        }
        nums shouldBe setOf(5, 9)
    }

    test("stats report residency and pool pressure under spill demand") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        // 7 distinct vregs live at once: exceeds the 5-register data pool
        for (i in 1..7) chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, i, i + 10))
        chunk.instructions.add(IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 100, 1))
        for (i in 2..7) chunk.instructions.add(IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 100, i))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        val stats = result.statsBySub[sub]!!
        (stats.spillCount > 0) shouldBe true
        (stats.maxLiveData > 5) shouldBe true
        (stats.residency < 1.0) shouldBe true
        (stats.residency > 0.0) shouldBe true
    }

    test("stats report full residency when nothing spills") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        chunk.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_out1")))
        chunk.instructions.add(IRInstructions.load(IRDataType.WORD, 2, 6))
        chunk.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 2, IRMemory.direct("p8b_test.p8v_out2")))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        val stats = result.statsBySub[sub]!!
        stats.spillCount shouldBe 0
        stats.residency shouldBe 1.0
    }

    test("value live across a nested loop gets its own register") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val head = IRCodeChunk(null, null)
        head.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        val bodyChunk = IRCodeChunk(null, null)
        bodyChunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 2, 3))
        val inner = IRLoopChunk("test.inner", 5, mutableListOf(bodyChunk))
        val outer = IRLoopChunk("test.outer", 10, mutableListOf(inner))
        val tail = IRCodeChunk(null, null)
        tail.instructions.add(IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 6, 1))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(head)
        sub.chunks.add(outer)
        sub.chunks.add(tail)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        // r1 lives across the whole nest; r2/r3 overlap inside the inner body
        alloc[VirtualRegister.int(1)] shouldNotBe null
        alloc[VirtualRegister.int(1)] shouldNotBe alloc[VirtualRegister.int(2)]
        alloc[VirtualRegister.int(2)] shouldNotBe alloc[VirtualRegister.int(3)]
    }

    test("value live across an early return keeps its own register") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val head = IRCodeChunk(null, null)
        head.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        val ret = IRCodeChunk(null, null)
        ret.instructions.add(IRInstructions.simple(Opcode.RETURN))
        val tail = IRCodeChunk(null, null)
        tail.instructions.add(IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 2, 1))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(head)
        sub.chunks.add(ret)
        sub.chunks.add(tail)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        alloc[VirtualRegister.int(1)] shouldNotBe null
        alloc[VirtualRegister.int(1)] shouldNotBe alloc[VirtualRegister.int(2)]
    }

    test("value live across a jump table keeps its own register") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val head = IRCodeChunk(null, null)
        head.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        head.instructions.add(IRInstructions.load(IRDataType.POINTER, 8, 0))
        val dispatch = IRCodeChunk(null, null)
        dispatch.instructions.add(IRInstructions.jumpIndirect(8))
        val caseA = IRCodeChunk("test.caseA", null)
        caseA.instructions.add(IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 4, 1))
        val caseB = IRCodeChunk("test.caseB", null)
        caseB.instructions.add(IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 5, 1))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(head)
        sub.chunks.add(dispatch)
        sub.chunks.add(caseA)
        sub.chunks.add(caseB)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        // r1 is live from the head into both case chunks
        alloc[VirtualRegister.int(1)] shouldNotBe null
        alloc[VirtualRegister.int(1)] shouldNotBe alloc[VirtualRegister.int(4)]
        alloc[VirtualRegister.int(1)] shouldNotBe alloc[VirtualRegister.int(5)]
    }

    test("renumber then allocate keeps small numbers and verifies") {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 30, 10))
        chunk.instructions.add(IRInstructions.binary(Opcode.ADDR, IRDataType.WORD, 20, 30))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val types = renumberVregsPerSubroutine(program)
        types shouldNotBe null
        program.verifyRegisterTypes(types!!)
        val alloc = M68kRegisterAllocator(program).allocate()
        alloc.mapBySub.isNotEmpty() shouldBe true
        alloc.mapBySub[sub]!!.keys.forEach { (it.num <= 3) shouldBe true }
    }

    test("subs sharing vreg numbers get independent allocations") {
        // Step 10: with per-subroutine vreg reuse the same number in two subs
        // must color independently (DATA in one, ADDRESS in the other).
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        val chunkA = IRCodeChunk(null, null)
        chunkA.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 5))
        chunkA.instructions.add(IRInstructions.binary(Opcode.MULR, IRDataType.WORD, 3, 1))
        val subA = IRSubroutine("test.data", emptyList(), emptyList(), Position.DUMMY)
        subA.chunks.add(chunkA)
        block.children.add(subA)
        val chunkB = IRCodeChunk(null, null)
        val mem = IRMemory.indirect(pointerRegister = 1, displacement = 0)
        chunkB.instructions.add(IRInstructions.loadMemory(Opcode.LOADI, IRDataType.BYTE, 2, mem))
        val subB = IRSubroutine("test.addr", emptyList(), emptyList(), Position.DUMMY)
        subB.chunks.add(chunkB)
        block.children.add(subB)
        program.blocks.add(block)

        val result = M68kRegisterAllocator(program).allocate()
        val aReg = result.mapBySub[subA]!![VirtualRegister.int(1)]
        val bReg = result.mapBySub[subB]!![VirtualRegister.int(1)]
        listOf("d2", "d3", "d4", "d5", "d6").contains(aReg) shouldBe true
        listOf("a2", "a3", "a4").contains(bReg) shouldBe true
    }

    test("value carried only via a branch edge keeps its own register") {
        // Forward skip with a killer on the fall-through path. r6 is defined in
        // head and used only in T; head's r5 is used in head and killed in mid.
        // Sequential-only liveness sees r6 die (mid redefines it) and shares one
        // register for r5/r6, but on the branch path both are live at head's
        // ADDR: the later LOAD r6 would clobber r5's value. The branch edge
        // head->T makes them interfere.
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val head = IRCodeChunk(null, null)
        head.instructions.add(IRInstructions.load(IRDataType.WORD, 5, 50))
        head.instructions.add(IRInstructions.load(IRDataType.WORD, 6, 60))
        // Pure register uses (no register dests): a USE_DEF dest would add its
        // own vreg to the entry set and mask the bug in both models.
        head.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 5, IRMemory.direct("p8b_test.p8v_sink")))
        head.instructions.add(IRInstructions.branch(Opcode.BSTEQ, CodeReference.Label("test.T")))
        val mid = IRCodeChunk("test.mid", null)
        mid.instructions.add(IRInstructions.load(IRDataType.WORD, 5, 51))
        mid.instructions.add(IRInstructions.load(IRDataType.WORD, 6, 61))
        // Pure register use (no register dest): a USE_DEF dest would act as a
        // liveness carrier and mask the bug in both models.
        val tail = IRCodeChunk("test.T", null)
        tail.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 6, IRMemory.direct("p8b_test.p8v_out")))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(head)
        sub.chunks.add(mid)
        sub.chunks.add(tail)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        alloc[VirtualRegister.int(5)] shouldNotBe null
        alloc[VirtualRegister.int(6)] shouldNotBe null
        alloc[VirtualRegister.int(5)] shouldNotBe alloc[VirtualRegister.int(6)]
    }

    test("unconditional jump has no fall-through: skipped killer does not hide a branch-carried value") {
        // Same family with JUMP: mid is unreachable dead code, but the branch
        // edge head->T still carries r6, so r5/r6 must differ.
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val head = IRCodeChunk(null, null)
        head.instructions.add(IRInstructions.load(IRDataType.WORD, 5, 50))
        head.instructions.add(IRInstructions.load(IRDataType.WORD, 6, 60))
        // Pure register uses (no register dests): a USE_DEF dest would add its
        // own vreg to the entry set and mask the bug in both models.
        head.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 5, IRMemory.direct("p8b_test.p8v_sink")))
        head.instructions.add(IRInstructions.jump(CodeReference.Label("test.T")))
        val mid = IRCodeChunk("test.mid", null)
        mid.instructions.add(IRInstructions.load(IRDataType.WORD, 5, 51))
        mid.instructions.add(IRInstructions.load(IRDataType.WORD, 6, 61))
        val tail = IRCodeChunk("test.T", null)
        tail.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 6, IRMemory.direct("p8b_test.p8v_out")))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(head)
        sub.chunks.add(mid)
        sub.chunks.add(tail)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        alloc[VirtualRegister.int(5)] shouldNotBe alloc[VirtualRegister.int(6)]
    }

    test("dead definition does not share with a value live across it") {
        // r9 is defined and never read afterwards. It must still interfere
        // with r1 (live across the definition): without def-interference it
        // would share r1's register and the direct store would clobber r1.
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW).zeropage(ZeropageType.FLOATSAFE)
            .compilerVersion("test").memtopAddress(0xffffu).optimize(true).build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        val head = IRCodeChunk(null, null)
        head.instructions.add(IRInstructions.load(IRDataType.WORD, 1, 50))
        head.instructions.add(IRInstructions.load(IRDataType.WORD, 9, 90))
        // Pure use (no register dest): a USE_DEF dest would add carriers that
        // mask the bug in both models (see the branch-edge tests).
        val tail = IRCodeChunk("test.tail", null)
        tail.instructions.add(IRInstructions.storeMemory(Opcode.STOREM, IRDataType.WORD, 1, IRMemory.direct("p8b_test.p8v_out")))
        val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
        sub.chunks.add(head)
        sub.chunks.add(tail)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        val alloc = M68kRegisterAllocator(program).allocate().mapBySub[sub]!!
        alloc[VirtualRegister.int(1)] shouldNotBe null
        alloc[VirtualRegister.int(9)] shouldNotBe null
        alloc[VirtualRegister.int(9)] shouldNotBe alloc[VirtualRegister.int(1)]
    }
})
