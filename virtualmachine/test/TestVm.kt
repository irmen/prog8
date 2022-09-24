import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import prog8.intermediate.Instruction
import prog8.intermediate.Opcode
import prog8.intermediate.VmDataType
import prog8.vm.Memory
import prog8.vm.VirtualMachine
import prog8.vm.VmRunner

class TestVm: FunSpec( {
    test("vm execution: empty program") {
        val memory = Memory()
        val vm = VirtualMachine(memory, emptyList(), 0xff00)
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
        val memory = Memory()
        val program = listOf(
            Instruction(Opcode.LOAD, VmDataType.WORD, reg1=1, value=12345),
            Instruction(Opcode.STOREM, VmDataType.WORD, reg1=1, value=1000),
            Instruction(Opcode.RETURN)
        )
        val vm = VirtualMachine(memory, program, 0xff00)

        memory.getUW(1000) shouldBe 0u
        vm.callStack.shouldBeEmpty()
        vm.valueStack.shouldBeEmpty()
        vm.pc shouldBe 0
        vm.stepCount shouldBe 0
        vm.run()
        memory.getUW(1000) shouldBe 12345u
        vm.callStack.shouldBeEmpty()
        vm.valueStack.shouldBeEmpty()
        vm.pc shouldBe 2
        vm.stepCount shouldBe 3
    }

    test("vmrunner") {
        val runner = VmRunner()
        runner.runProgram(";comment\n------PROGRAM------\n;comment\n")
    }
})
