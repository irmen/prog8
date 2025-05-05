import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import prog8.code.core.*
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8.intermediate.*
import prog8.vm.VirtualMachine
import prog8.vm.VmRunner

class TestVm: FunSpec( {

    fun getTestOptions(): CompilationOptions {
        val target = VMTarget()
        return CompilationOptions(
            OutputType.RAW,
            CbmPrgLauncherType.NONE,
            ZeropageType.DONTUSE,
            zpReserved = emptyList(),
            zpAllowed = CompilationOptions.AllZeropageAllowed,
            floats = true,
            noSysInit = false,
            romable = false,
            compTarget = target,
            loadAddress = target.PROGRAM_LOAD_ADDRESS,
            memtopAddress = 0xffffu
        )
    }

    test("vm execution: empty program") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val vm = VirtualMachine(program)
        vm.callStack.shouldBeEmpty()
        vm.valueStack.shouldBeEmpty()
        vm.pcIndex shouldBe 0
        vm.stepCount shouldBe 0
        vm.run(false)
        vm.callStack.shouldBeEmpty()
        vm.valueStack.shouldBeEmpty()
        vm.pcIndex shouldBe 0
        vm.stepCount shouldBe 0
    }

    test("vm execution: modify memory") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val block = IRBlock("testmain", false, IRBlock.Options(), Position.DUMMY)
        val startSub = IRSubroutine("testmain.testsub", emptyList(), emptyList(), Position.DUMMY)
        val code = IRCodeChunk(startSub.label, null)
        code += IRInstruction(Opcode.NOP)
        code += IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1=1, immediate=12345)
        code += IRInstruction(Opcode.STOREM, IRDataType.WORD, reg1=1, address=1000)
        code += IRInstruction(Opcode.RETURN)
        startSub += code
        block += startSub
        program.addBlock(block)
        val vm = VirtualMachine(program)
        vm.memory.setUW(1000, 0u)

        vm.memory.getUW(1000) shouldBe 0u
        vm.callStack.shouldBeEmpty()
        vm.valueStack.shouldBeEmpty()
        vm.pcIndex shouldBe 0
        vm.stepCount shouldBe 0
        vm.run(false)
        vm.stepCount shouldBe 4
        vm.memory.getUW(1000) shouldBe 12345u
        vm.callStack.shouldBeEmpty()
        vm.valueStack.shouldBeEmpty()
        vm.pcIndex shouldBe code.instructions.size-1
        vm.stepCount shouldBe code.instructions.size
    }

    test("asmsub not supported in vm even with IR") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        val startSub = IRAsmSubroutine(
            "main.asmstart",
            0x2000u,
            emptySet(),
            emptyList(),
            emptyList(),
            IRInlineAsmChunk("main.asmstart", "return", false, null),
            Position.DUMMY
        )
        block += startSub
        program.addBlock(block)
        shouldThrowWithMessage<IRParseException>("vm does not support asmsubs (use normal sub): main.asmstart") {
            VirtualMachine(program)
        }
    }

    test("vmrunner") {
        val runner = VmRunner()
        val irSource="""<?xml version="1.0" encoding="utf-8"?>
<PROGRAM NAME="test">
<OPTIONS>
</OPTIONS>

<ASMSYMBOLS>
</ASMSYMBOLS>

<VARIABLESNOINIT>
</VARIABLESNOINIT>
<VARIABLESNOINITDIRTY>
</VARIABLESNOINITDIRTY>
<VARIABLESWITHINIT>
</VARIABLESWITHINIT>
<CONSTANTS>
</CONSTANTS>

<MEMORYMAPPEDVARIABLES>
</MEMORYMAPPEDVARIABLES>

<MEMORYSLABS>
</MEMORYSLABS>

<INITGLOBALS>
</INITGLOBALS>

<BLOCK NAME="main" ADDRESS="" LIBRARY="false" FORCEOUTPUT="false" ALIGN="NONE" POS="[unittest: line 42 col 1-9]">
</BLOCK>
</PROGRAM>
"""
        runner.runProgram(irSource, false)
    }

    test("vm machine float bits") {
        val cx16machine = Cx16Target()
        cx16machine.getFloatAsmBytes(Math.PI) shouldBe "\$82, \$49, \$0f, \$da, \$a2"
        val c64machine = C64Target()
        c64machine.getFloatAsmBytes(Math.PI) shouldBe "\$82, \$49, \$0f, \$da, \$a2"

        val vm = VMTarget()
        vm.getFloatAsmBytes(Math.PI) shouldBe "\$40, \$09, \$21, \$fb, \$54, \$44, \$2d, \$18"
    }
})
