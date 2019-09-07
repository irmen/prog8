package sim65

import sim65.components.Address
import sim65.components.Cpu6502
import sim65.components.ICpu
import sim65.components.Ram

object C64KernalStubs {

    lateinit var ram: Ram

    fun handleBreakpoint(cpu: ICpu, pc: Address) {
        cpu as Cpu6502
        when(pc) {
            0xffd2 -> {
                // CHROUT
                val char = C64Screencodes.decodeScreencode(listOf(cpu.A.toShort()), true)
                if(char=="m")
                    println()
                else
                    print(char)
                cpu.currentOpcode = 0x60    // rts to end the stub
            }
            0xffe4 -> {
                // GETIN
                print("[Input required:] ")
                val s = readLine()
                if(s.isNullOrEmpty())
                    cpu.A = 0
                else
                    cpu.A = C64Screencodes.encodeScreencode(s, true).first().toInt()
                cpu.currentOpcode = 0x60    // rts to end the stub
            }
            0xe16f -> {
                // LOAD/VERIFY
                val loc = ram.read(0xbb).toInt() or (ram.read(0xbc).toInt() shl 8)
                val len = ram.read(0xb7).toInt()
                val filename = C64Screencodes.decodeScreencode((loc until loc+len).map { ram.read(it) }.toList(), true).toLowerCase()
                println("\n[loading $filename ...]")
                ram.loadPrg("c64tests/$filename")
                cpu.PC = 0x0816     // continue in next module
            }
        }
    }
}
