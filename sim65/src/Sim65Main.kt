package sim65

import kotlinx.cli.*
import sim65.C64KernalStubs.handleBreakpoint
import sim65.components.*
import sim65.components.Cpu6502.Companion.IRQ_vector
import sim65.components.Cpu6502.Companion.RESET_vector
import kotlin.system.exitProcess


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

    val cpu = Cpu6502(stopOnBrk = false)
    cpu.tracing = false
    cpu.breakpoint(0xffd2, ::handleBreakpoint)
    cpu.breakpoint(0xffe4, ::handleBreakpoint)
    cpu.breakpoint(0xe16f, ::handleBreakpoint)

    // create the system bus and add device to it.
    // note that the order is relevant w.r.t. where reads and writes are going.
    val ram = Ram(0, 0xffff)
    ram.set(0x02, 0)
    ram.set(0xa002, 0)
    ram.set(0xa003, 0x80)
    ram.set(IRQ_vector, 0x48)
    ram.set(IRQ_vector+1, 0xff)
    ram.set(RESET_vector, 0x01)
    ram.set(RESET_vector+1, 0x08)
    ram.set(0x01fe, 0xff)
    ram.set(0x01ff, 0x7f)
    ram.set(0x8000, 2)
    ram.set(0xa474, 2)
    ram.loadPrg("c64tests/nopn")
    C64KernalStubs.ram = ram

    val bus = Bus()
    bus.add(cpu)
    bus.add(ram)
    bus.reset()

    require(cpu.SP==0xfd)
    require(cpu.Status.asByte().toInt()==0b00100100)

    try {
        while (true) {
            bus.clock()
        }
    } catch(e: InstructionError) {
        println(">>> INSTRUCTION ERROR: ${e.message}")
    }
}
