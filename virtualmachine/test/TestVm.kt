import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import prog8.code.core.*
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
            floats = true,
            noSysInit = false,
            compTarget = target,
            loadAddress = target.machine.PROGRAM_LOAD_ADDRESS
        )
    }

    test("vm execution: empty program") {
        val program = IRProgram("test", IRSymbolTable(null), getTestOptions(), VMTarget())
        val vm = VirtualMachine(program)
        vm.callStack.shouldBeEmpty()
        vm.valueStack.shouldBeEmpty()
        vm.pc shouldBe 0
        vm.stepCount shouldBe 0
        vm.run()
        vm.callStack.shouldBeEmpty()
        vm.valueStack.shouldBeEmpty()
        vm.pc shouldBe 0
        vm.stepCount shouldBe 1
    }

    test("vm execution: modify memory") {
        val program = IRProgram("test", IRSymbolTable(null), getTestOptions(), VMTarget())
        val block = IRBlock("main", null, IRBlock.BlockAlignment.NONE, Position.DUMMY)
        val startSub = IRSubroutine("main.start2222", emptyList(), null, Position.DUMMY)        // TODO proper name main.start
        val code = IRCodeChunk(Position.DUMMY)
        code += IRInstruction(Opcode.LOAD, VmDataType.WORD, reg1=1, value=12345)
        code += IRInstruction(Opcode.STOREM, VmDataType.WORD, reg1=1, value=1000)
        code += IRInstruction(Opcode.RETURN)
        startSub += code
        block += startSub
        program.addBlock(block)
        val vm = VirtualMachine(program)

        vm.memory.getUW(1000) shouldBe 0u
        vm.callStack.shouldBeEmpty()
        vm.valueStack.shouldBeEmpty()
        vm.pc shouldBe 0
        vm.stepCount shouldBe 0
        vm.run()
        vm.memory.getUW(1000) shouldBe 12345u
        vm.callStack.shouldBeEmpty()
        vm.valueStack.shouldBeEmpty()
        vm.pc shouldBe 2
        vm.stepCount shouldBe 3
    }

    test("vmrunner") {
        val runner = VmRunner()
        val irSource="""<PROGRAM NAME=test>
<OPTIONS>
</OPTIONS>

<VARIABLES>
</VARIABLES>

<MEMORYMAPPEDVARIABLES>
</MEMORYMAPPEDVARIABLES>

<MEMORYSLABS>
</MEMORYSLABS>

<INITGLOBALS>
</INITGLOBALS>

<BLOCK NAME=main ADDRESS=null ALIGN=NONE POS=[unittest: line 42 col 1-9]>
</BLOCK>
</PROGRAM>
"""
        runner.runProgram(irSource)
    }
})
