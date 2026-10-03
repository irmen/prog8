package prog8tests.codegen.m68k

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import prog8.code.core.*
import prog8.code.target.Qemu68kTarget
import prog8.codegen.m68k.AsmGen
import prog8.intermediate.*
import prog8tests.helpers.DummyStringEncoder
import kotlin.io.path.exists
import kotlin.io.path.readText

// Vertical Slice Prototype: m68k backend lowering of FrameSlot memory references
// and the link/unlk frame prologue/epilogue (m68k-stack-memory-model.md §17).
class TestStackFrameEmission : FunSpec({

    val tempRoot = tempdir().toPath()

    fun generateAsmWithIncoming(
        frameSize: Int,
        instructions: List<IRInstruction>,
        incomingSize: Int = 0,
        subLabel: String = "test.start"
    ): List<String> {
        val options = CompilationOptions.builder(Qemu68kTarget())
            .output(OutputType.RAW)
            .zeropage(ZeropageType.DONTUSE)
            .floats(false)
            .compilerVersion("test")
            .memtopAddress(0xffffu)
            .optimize(true)
            .build()
        val program = IRProgram("test", IRSymbolTable(), options, DummyStringEncoder)
        program.options.outputDir = tempRoot
        val chunk = IRCodeChunk(null, null)
        chunk.instructions.addAll(instructions)
        val params = if (incomingSize > 0)
            List(incomingSize / 4) { IRSubroutine.IRParam("p8v_p$it", DataType.UBYTE) }
        else emptyList()
        val sub = IRSubroutine(subLabel, params, emptyList(), Position.DUMMY, frameSize, incomingSize)
        sub.chunks.add(chunk)
        val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
        block.children.add(sub)
        program.blocks.add(block)

        tempRoot.toFile().deleteRecursively()
        tempRoot.toFile().mkdirs()
        AsmGen(program, Qemu68kTarget()).generate()
        val asmFile = tempRoot.resolve("test.asm")
        check(asmFile.exists()) { "Assembly file not written: $asmFile" }
        return asmFile.readText().lines().map { it.trim() }
    }

    fun generateAsm(frameSize: Int, instructions: List<IRInstruction>): List<String> =
        generateAsmWithIncoming(frameSize, instructions, 0, "test.start")

    test("frameless subroutine emits no frame setup") {
        val lines = generateAsm(0, listOf(
            IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, 1, IRMemory.direct("some.var")),
            IRInstructions.simple(Opcode.RETURN)
        ))
        lines.any { it.startsWith("link") } shouldBe false
        lines.any { it.startsWith("unlk") } shouldBe false
    }

    test("framed subroutine emits link prologue and unlk epilogue") {
        val lines = generateAsm(16, listOf(
            IRInstructions.storeZero(Opcode.STOREZM, IRDataType.BYTE, IRMemory.frameDirect(-1)),
            IRInstructions.simple(Opcode.RETURN)
        ))
        lines.any { it == "link  a5,#-16" } shouldBe true
        lines.count { it == "unlk  a5" } shouldBe 1
        lines.any { "-1(a5)" in it } shouldBe true
        // epilogue must directly precede the subroutine's return
        lines[lines.indexOf("unlk  a5") + 1] shouldBe "rts"
    }

    test("frame slot direct references lower to displacements relative to a5") {
        val lines = generateAsm(16, listOf(
            IRInstructions.storeImmediate(IRDataType.WORD, 5, IRMemory.frameDirect(-4)),
            IRInstructions.loadMemory(Opcode.LOADM, IRDataType.BYTE, 1, IRMemory.frameDirect(-1)),
            IRInstructions.memoryOpImmediate(Opcode.ADDIM, IRDataType.BYTE, IRMemory.frameDirect(-1), 3),
            IRInstructions.simple(Opcode.RETURN)
        ))
        lines.any { it.contains("-4(a5)") && it.startsWith("move.w") } shouldBe true
        lines.any { it.contains("-1(a5)") } shouldBe true
        lines.any { it.startsWith("addq.b") || it.startsWith("add.b") } shouldBe true
    }

    test("indexed frame slot access uses lea of the slot base plus index") {
        val lines = generateAsm(16, listOf(
            IRInstructions.loadMemory(Opcode.LOADX, IRDataType.BYTE, 1, IRMemory.frameIndexed(-8, 3, IRDataType.WORD, scale = 1)),
            IRInstructions.storeZero(Opcode.STOREZX, IRDataType.BYTE, IRMemory.frameIndexed(-8, 3, IRDataType.WORD, scale = 1)),
            IRInstructions.simple(Opcode.RETURN)
        ))
        lines.any { it.startsWith("lea") && "-8(a5)" in it } shouldBe true
        lines.count { it.contains("(a0,d0.w)") } shouldBe 2
    }

    test("frame displacement combines slot offset and reference displacement") {
        val lines = generateAsm(16, listOf(
            IRInstructions.storeZero(Opcode.STOREZM, IRDataType.BYTE, IRMemory.frameDirect(-8, 4)),
            IRInstructions.simple(Opcode.RETURN)
        ))
        // -8 slot + 4 displacement = -4(a5), a single combined displacement
        lines.any { it.contains("-4(a5)") } shouldBe true
        lines.any { it.contains("-8(a5)+4") } shouldBe false
    }

    test("incoming-only frame still emits link and unlk") {
        val lines = generateAsmWithIncoming(0, listOf(IRInstructions.simple(Opcode.RETURN)), incomingSize = 12)
        lines.any { it == "link  a5,#0" } shouldBe true
        lines.any { it == "unlk  a5" } shouldBe true
    }

    test("stack arguments are pushed right-justified and popped by the caller") {
        val calls = listOf(
            IRInstructions.call(CallSite(
                target = CallTarget.Direct(CodeReference.Label("test.sub")),
                arguments = listOf(
                    Calls.argument(1, IRDataType.BYTE, CallLocation.FrameSlot(16)),
                    Calls.argument(2, IRDataType.WORD, CallLocation.FrameSlot(12)),
                    Calls.argument(3, IRDataType.LONG, CallLocation.FrameSlot(8))
                )
            )),
            IRInstructions.simple(Opcode.RETURN)
        )
        val lines = generateAsmWithIncoming(0, calls, incomingSize = 12, subLabel = "test.caller")
        lines.count { it == "subq.l  #4,sp" } shouldBe 3
        lines.count { it.startsWith("move.b ") && it.endsWith(",3(sp)") } shouldBe 1
        lines.count { it.startsWith("move.w ") && it.endsWith(",2(sp)") } shouldBe 1
        lines.count { it.startsWith("move.l ") && it.endsWith(",(sp)") } shouldBe 1
        lines.any { it.startsWith("bsr") } shouldBe true
        lines.any { it == "lea  12(sp),sp" } shouldBe true
        // small argument areas use the short addq form
        val smallCalls = listOf(
            IRInstructions.call(CallSite(
                target = CallTarget.Direct(CodeReference.Label("test.sub")),
                arguments = listOf(Calls.argument(1, IRDataType.BYTE, CallLocation.FrameSlot(8)))
            )),
            IRInstructions.simple(Opcode.RETURN)
        )
        val smallLines = generateAsmWithIncoming(0, smallCalls, incomingSize = 4, subLabel = "test.caller")
        smallLines.any { it == "addq.l  #4,sp" } shouldBe true
    }

    test("status-flag results use CCR-preserving cleanup") {
        val calls = listOf(
            IRInstructions.call(CallSite(
                target = CallTarget.Direct(CodeReference.Label("test.sub")),
                arguments = listOf(Calls.argument(1, IRDataType.BYTE, CallLocation.FrameSlot(8))),
                results = listOf(CallResult(null, CallLocation.StatusFlag(Statusflag.Pz)))
            )),
            IRInstructions.simple(Opcode.RETURN)
        )
        val lines = generateAsmWithIncoming(0, calls, incomingSize = 4, subLabel = "test.caller")
        // a single byte argument would normally use addq.l #4,sp, but that clobbers CCR,
        // so status-flag returns must use lea instead.
        lines.any { it == "lea  4(sp),sp" } shouldBe true
        lines.none { it == "addq.l  #4,sp" } shouldBe true
    }

    test("float stack arguments travel through the FPU accumulator") {
        val calls = listOf(
            IRInstructions.call(CallSite(
                target = CallTarget.Direct(CodeReference.Label("test.sub")),
                arguments = listOf(Calls.argument(1, IRDataType.FLOAT, CallLocation.FrameSlot(8)))
            )),
            IRInstructions.simple(Opcode.RETURN)
        )
        val lines = generateAsmWithIncoming(0, calls, incomingSize = 4, subLabel = "test.caller")
        // the 68881 has no absolute-long addressing mode: the value must pass through fp0
        lines.any { it.startsWith("fmove.s  p8_fregfile+0,fp0") } shouldBe true
        lines.any { it == "fmove.s  fp0,(sp)" } shouldBe true
    }
})
