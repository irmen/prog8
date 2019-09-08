import org.junit.jupiter.api.Test
import sim65.components.Bus
import sim65.components.Cpu6502
import sim65.components.Ram
import kotlin.test.assertEquals

class Test6502Functional {

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

        while(cpu.totalCycles < 50000000) {
            cpu.clock()
        }

        cpu.printState()
        val d = cpu.disassemble(ram, cpu.PC-20, cpu.PC+20)
        println(d.joinToString ("\n"))
    }

}
