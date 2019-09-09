package sim65

import sim65.components.*
import sim65.components.Cpu6502.Companion.RESET_vector


fun main(args: Array<String>) {
    printSoftwareHeader()
    startSimulator(args)
}

internal fun printSoftwareHeader() {
    val buildVersion = object {}.javaClass.getResource("/version.txt").readText().trim()
    println("\nSim65 6502 cpu simulator v$buildVersion by Irmen de Jong (irmen@razorvine.net)")
    println("This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html\n")
}


private fun startSimulator(args: Array<String>) {

    // create a computer system.
    // note that the order in which components are added to the bus, is important:
    // it determines the priority of reads and writes.
    val cpu = Cpu6502(true)
    val ram = Ram(0, 0xffff)
    ram[RESET_vector] = 0x00
    ram[RESET_vector + 1] = 0x10
//    // read the RTC and write the date+time to $2000
//    for(b in listOf(0xa0, 0x00, 0xb9, 0x00, 0xd1, 0x99, 0x00, 0x20, 0xc8, 0xc0, 0x09, 0xd0, 0xf5, 0x00).withIndex()) {
//        ram[0x1000+b.index] = b.value.toShort()
//    }

    // set the timer to $22aa00 and enable it on regular irq
    for(b in listOf(0xa9, 0x00, 0x8d, 0x00, 0xd2, 0xa9, 0x00, 0x8d, 0x01, 0xd2, 0xa9, 0xaa, 0x8d, 0x02,
            0xd2, 0xa9, 0x22, 0x8d, 0x03, 0xd2, 0xa9, 0x01, 0x8d, 0x00, 0xd2, 0x4c, 0x19, 0x10).withIndex()) {
        ram[0x1000+b.index] = b.value.toShort()
    }


    val parallel = ParallelPort(0xd000, 0xd001)
    val clock = RealTimeClock(0xd100, 0xd108)
    val timer = Timer(0xd200, 0xd203)

    val bus = Bus()
    bus.add(cpu)
    bus.add(parallel)
    bus.add(clock)
    bus.add(timer)
    bus.add(ram)
    bus.reset()

    try {
        while (true) {
            bus.clock()
        }
    } catch (ix: InstructionError) {
        // ignore
    }

    ram.hexDump(0x2000, 0x2008)
}
