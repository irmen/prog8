import sim65.Petscii
import sim65.components.Address
import sim65.components.Cpu6502
import sim65.components.ICpu
import sim65.components.Ram

class C64KernalStubs(private val ram: Ram) {

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
                throw InputRequired()
//                print("[Input required:] ")
//                val s = readLine()
//                if(s.isNullOrEmpty())
//                    cpu.A = 3
//                else
//                    cpu.A = Petscii.encodePetscii(s, true).first().toInt()
//                cpu.currentOpcode = 0x60    // rts to end the stub
            }
            0xe16f -> {
                throw LoadNextPart()
                // LOAD/VERIFY
//                val loc = ram[0xbb].toInt() or (ram[0xbc].toInt() shl 8)
//                val len = ram[0xb7].toInt()
//                val filename = Petscii.decodePetscii((loc until loc + len).map { ram[it] }.toList(), true).toLowerCase()
//                ram.loadPrg("test/6502testsuite/$filename")
//                cpu.popStackAddr()
//                cpu.PC = 0x0816     // continue in next module
            }
        }
    }
}

class LoadNextPart: Exception()
class InputRequired: Exception()

