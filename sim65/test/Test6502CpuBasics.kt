import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import sim65.components.Bus
import sim65.components.Cpu6502
import sim65.components.InstructionError
import sim65.components.Ram
import kotlin.system.measureNanoTime
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

    @Test
    fun testCpuPerformance() {
        val cpu = Cpu6502(true)
        val ram = Ram(0x1000, 0x2000)
        // load a simple program that loops a few instructions
        for(b in listOf(0xa9, 0x63, 0xaa, 0x86, 0x22, 0x8e, 0x22, 0x22, 0x91, 0x22, 0x6d, 0x33, 0x33, 0xcd, 0x55, 0x55, 0xd0, 0xee, 0xf0, 0xec).withIndex()) {
            ram[0x1000+b.index] = b.value.toShort()
        }

        val bus = Bus()
        bus.add(cpu)
        bus.add(ram)
        cpu.reset()
        cpu.PC = 0x1000

        // warmup
        while(cpu.totalCycles<1000000)
            cpu.clock()

        // timing
        val cycles = 50000000
        val duration = measureNanoTime {
            while (cpu.totalCycles < cycles)
                cpu.clock()
        }
        val seconds = duration.toDouble() / 1e9
        val mhz = (cycles.toDouble() / seconds) / 1e6
        println("duration $seconds sec  for $cycles = $mhz Mhz")

    }

    @Test
    fun testBCD() {
        val cpu = Cpu6502(true)
        val bus = Bus()
        bus.add(cpu)
        val ram = Ram(0, 0xffff)
        ram[Cpu6502.RESET_vector] = 0x00
        ram[Cpu6502.RESET_vector +1] = 0x10
        ram.load("test/testfiles/bcdtest.bin", 0x1000)
        bus.add(ram)
        bus.reset()

        try {
            while (true) {
                bus.clock()
            }
        } catch(e: InstructionError) {
            // do nothing
        }

        if(ram[0x0400] ==0.toShort()) {
            println("BCD TEST: OK!")
        }
        else {
            val code = ram[0x0400]
            val v1 = ram[0x0401]
            val v2 = ram[0x0402]
            val predictedA = ram[0x00fc]
            val actualA = ram[0x00fd]
            val predictedF = ram[0x00fe]
            val actualF = ram[0x00ff]
            println("BCD TEST: FAIL!! code=${Cpu6502.hexB(code)} value1=${Cpu6502.hexB(v1)} value2=${Cpu6502.hexB(v2)}")
            println("  predictedA=${Cpu6502.hexB(predictedA)}")
            println("  actualA=${Cpu6502.hexB(actualA)}")
            println("  predictedF=${predictedF.toString(2).padStart(8,'0')}")
            println("  actualF=${actualF.toString(2).padStart(8,'0')}")
            fail("BCD test failed")
        }
    }

}
