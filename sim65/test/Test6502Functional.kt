import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import sim65.components.Bus
import sim65.components.Cpu6502
import sim65.components.Ram
import java.lang.Exception

class Test6502Functional {

    private class SuccessfulTestResult: Exception()

    @Test
    fun testFunctional() {
        val cpu = Cpu6502(false)
        val bus = Bus()
        val ram = Ram(0, 0xffff)
        ram.load("test/6502_functional_tests/bin_files/6502_functional_test.bin", 0)
        bus.add(cpu)
        bus.add(ram)
        cpu.reset()
        cpu.PC = 0x0400
        cpu.breakpoint(0x3469) { _, _ ->
            // reaching this address means successful test result
            if(cpu.currentOpcode==0x4c)
                throw SuccessfulTestResult()
        }

        try {
            while (cpu.totalCycles < 900000000) {
                cpu.clock()
            }
        } catch (sx: SuccessfulTestResult) {
            println("test successful")
            return
        }

        cpu.printState()
        val d = cpu.disassemble(ram, cpu.PC-20, cpu.PC+20)
        println(d.joinToString ("\n"))
        fail("test failed")
    }

}
