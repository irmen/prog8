package sim65

import kotlinx.cli.*
import sim65.C64KernalStubs.handleBreakpoint
import sim65.components.*
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
    val cli = CommandLineInterface("sim65", printHelpByDefault = false)
    val enableIllegal by cli.flagArgument("-ill", "enable the illegal instructions")

    try {
        cli.parse(args)
    } catch (e: Exception) {
        exitProcess(1)
    }

    val cpu = Cpu6502(enableIllegal, stopOnBrk=true)
    cpu.tracing = false

    // create the system bus and add device to it.
    // note that the order is relevant w.r.t. where reads and writes are going.
    val bus = Bus()
    bus.add(cpu)
    val ram = Ram(0, 0xffff)
    ram.set(0xc000, 0xa9)   // lda #0
    ram.set(0xc001, 0x00)
    ram.set(0xc002, 0x85)   // sta $02
    ram.set(0xc003, 0x02)
    ram.set(0xc004, 0x4c)   // jmp $0816
    ram.set(0xc005, 0x16)
    ram.set(0xc006, 0x08)
    ram.set(RESET_vector, 0x00)
    ram.set(RESET_vector+1, 0xc0)
    bus.add(ram)

    ram.loadPrg("c64tests/0start")
    C64KernalStubs.ram = ram

    cpu.breakpoint(0xffd2, ::handleBreakpoint)
    cpu.breakpoint(0xffe4, ::handleBreakpoint)
    cpu.breakpoint(0xe16f, ::handleBreakpoint)
    bus.reset()

    try {
        while (true) {
            bus.clock()
        }
    } catch(e: InstructionError) {
        println(">>> INSTRUCTION ERROR: ${e.message}")
    }
}
