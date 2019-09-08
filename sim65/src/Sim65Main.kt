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

    val parallel = ParallelPort(0xd000, 0xd001)
    val timer = Timer(0xd100, 0xd103)

    val bus = Bus()
    bus.add(cpu)
    bus.add(parallel)
    bus.add(timer)
    bus.add(ram)
    bus.reset()

    cpu.tracing = true

    while (true) {
        bus.clock()
    }
}
