package sim65

import kotlinx.cli.*
import sim65.components.*
import sim65.components.Cpu6502.Companion.hexB
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    printSoftwareHeader2()
    startSimulator2(args)
}

internal fun printSoftwareHeader2() {
    val buildVersion = object {}.javaClass.getResource("/version.txt").readText().trim()
    println("\nSim65 6502 cpu simulator v$buildVersion by Irmen de Jong (irmen@razorvine.net)")
    println("This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html\n")
}


private fun startSimulator2(args: Array<String>) {
    val bootRom = listOf<UByte>(
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,
            0x00,0x90,   // NMI vector
            0x00,0x10,   // RESET vector
            0x00,0xa0    // IRQ vector
    ).toTypedArray()

    val cpu = Cpu6502(true)
    cpu.tracing = true

    // create the system bus and add device to it.
    // note that the order is relevant w.r.t. where reads and writes are going.
    val bus = Bus()
    bus.add(cpu)
    bus.add(Rom(0xff00, 0xffff, bootRom))
    bus.add(Parallel(0xd000, 0xd001))
    bus.add(Timer(0xd100, 0xd103))
    val ram = Ram(0, 0xffff)
    bus.add(ram)

    bus.reset()

    ram.load("sim65/test/testfiles/ram.bin", 0x8000)
    ram.load("sim65/test/testfiles/bcdtest.bin", 0x1000)
    //ram.dump(0x8000, 0x802f)
    //cpu.disassemble(ram, 0x8000, 0x802f)

    try {
        while (true) {
            bus.clock()
        }
    } catch(e: InstructionError) {

    }

    if(ram[0x0400] ==0.toShort())
        println("BCD TEST: OK!")
    else {
        val code = ram[0x0400]
        val v1 = ram[0x0401]
        val v2 = ram[0x0402]
        val predictedA = ram[0x00fc]
        val actualA = ram[0x00fd]
        val predictedF = ram[0x00fe]
        val actualF = ram[0x00ff]
        println("BCD TEST: FAIL!! code=${hexB(code)} value1=${hexB(v1)} value2=${hexB(v2)}")
        println("  predictedA=${hexB(predictedA)}")
        println("  actualA=${hexB(actualA)}")
        println("  predictedF=${predictedF.toString(2).padStart(8,'0')}")
        println("  actualF=${actualF.toString(2).padStart(8,'0')}")
    }

}
