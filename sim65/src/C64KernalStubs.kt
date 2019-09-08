package sim65

import sim65.components.Address
import sim65.components.Cpu6502
import sim65.components.ICpu
import sim65.components.Ram
import kotlin.system.exitProcess

object C64KernalStubs {

    lateinit var ram: Ram

    fun handleBreakpoint(cpu: ICpu, pc: Address) {
        cpu as Cpu6502
        when(pc) {
            0xffd2 -> {
                // CHROUT
                ram[0x030c] = 0
                val char = Petscii.decodePetscii(listOf(cpu.A.toShort()), true).first()
                if(char==13.toChar())
                    println()
                else if(char in ' '..'~')
                    print(char)
                cpu.currentOpcode = 0x60    // rts to end the stub
            }
            0xffe4 -> {
                // GETIN
                print("[Input required:] ")
                val s = readLine()
                if(s.isNullOrEmpty())
                    cpu.A = 3
                else
                    cpu.A = Petscii.encodePetscii(s, true).first().toInt()
                cpu.currentOpcode = 0x60    // rts to end the stub
            }
            0xe16f -> {
                // LOAD/VERIFY
                val loc = ram[0xbb].toInt() or (this.ram[0xbc].toInt() shl 8)
                val len = ram[0xb7].toInt()
                val filename = Petscii.decodePetscii((loc until loc+len).map { ram[it] }.toList(), true).toLowerCase()
                ram.loadPrg("c64tests/$filename")
                cpu.popStackAddr()
                cpu.PC = 0x0816     // continue in next module
            }
        }
    }
}
