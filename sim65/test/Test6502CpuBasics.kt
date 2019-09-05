import org.junit.jupiter.api.Test
import sim65.components.Bus
import sim65.components.Cpu6502
import kotlin.test.assertEquals

class Test6502CpuBasics {

    @Test
    fun testCpuFlagsAfterReset() {
        val cpu = Cpu6502(true)
        val bus = Bus()
        bus.add(cpu)
        cpu.reset()
        assertEquals(0xfd, cpu.SP)
        assertEquals(0xffff, cpu.PC)
        assertEquals(0, cpu.totalCycles)
        assertEquals(8, cpu.instrCycles)
        assertEquals(0, cpu.A)
        assertEquals(0, cpu.X)
        assertEquals(0, cpu.Y)
        assertEquals(0, cpu.currentOpcode)
        assertEquals(Cpu6502.StatusRegister(C = false, Z = false, I = true, D = false, B = false, V = false, N = false), cpu.Status)
        assertEquals(0b00100100, cpu.Status.asByte())
    }

}
