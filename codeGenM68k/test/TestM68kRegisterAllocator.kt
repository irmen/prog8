package prog8tests.codegen.m68k

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.CompilationOptions
import prog8.code.core.OutputType
import prog8.code.core.Position
import prog8.code.core.ZeropageType
import prog8.code.target.Qemu68kTarget
import prog8.codegen.m68k.M68kRegisterAllocator
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
        val alloc = M68kRegisterAllocator(program).allocate().map
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

        val alloc = M68kRegisterAllocator(program).allocate().map
        val ptrReg = alloc[VirtualRegister.int(5)]
        listOf("a2", "a3", "a4").contains(ptrReg) shouldBe true
        // the data vreg should be in a data register, not address
        val dataReg = alloc[VirtualRegister.int(1)]
        listOf("d2", "d3", "d4", "d5", "d6").contains(dataReg) shouldBe true
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

        val alloc = M68kRegisterAllocator(program).allocate().map
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
        used shouldBe result.map.values.toSet()
        (used!!.size > 0) shouldBe true
    }
})
