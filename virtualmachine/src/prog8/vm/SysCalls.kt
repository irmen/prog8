package prog8.vm

import kotlin.math.min
import kotlin.random.Random

/*
SYSCALLS:

0 = reset ; resets system
1 = exit ; stops program and returns statuscode from r0.w
2 = print_c ; print single character
3 = print_s ; print 0-terminated string from memory
4 = print_u8 ; print unsigned int byte
5 = print_u16 ; print unsigned int word
6 = input ; reads a line of text entered by the user, r0.w = memory buffer, r1.b = maxlength (0-255, 0=unlimited).  Zero-terminates the string. Returns length in r65535.w
7 = sleep ; sleep amount of milliseconds
8 = gfx_enable  ; enable graphics window  r0.b = 0 -> lores 320x240,  r0.b = 1 -> hires 640x480
9 = gfx_clear   ; clear graphics window with shade in r0.b
10 = gfx_plot   ; plot pixel in graphics window, r0.w/r1.w contain X and Y coordinates, r2.b contains brightness
11 = rnd        ; random BYTE
12 = wait       ; wait certain amount of jiffies (1/60 sec)
13 = waitvsync  ; wait on vsync
*/

enum class Syscall {
    RESET,
    EXIT,
    PRINT_C,
    PRINT_S,
    PRINT_U8,
    PRINT_U16,
    INPUT,
    SLEEP,
    GFX_ENABLE,
    GFX_CLEAR,
    GFX_PLOT,
    RND,
    WAIT,
    WAITVSYNC,
    TMP_PRINT_UW
}

object SysCalls {
    fun call(call: Syscall, vm: VirtualMachine) {
        when(call) {
            Syscall.RESET -> {
                vm.reset()
            }
            Syscall.EXIT ->{
                vm.exit()
            }
            Syscall.PRINT_C -> {
                val char = vm.registers.getUB(0).toInt()
                print(Char(char))
            }
            Syscall.PRINT_S -> {
                var addr = vm.registers.getUW(0).toInt()
                while(true) {
                    val char = vm.memory.getUB(addr).toInt()
                    if(char==0)
                        break
                    print(Char(char))
                    addr++
                }
            }
            Syscall.PRINT_U8 -> {
                print(vm.registers.getUB(0))
            }
            Syscall.PRINT_U16 -> {
                print(vm.registers.getUW(0))
            }
            Syscall.INPUT -> {
                var input = readln()
                val maxlen = vm.registers.getUB(1).toInt()
                if(maxlen>0)
                    input = input.substring(0, min(input.length, maxlen))
                vm.memory.setString(vm.registers.getUW(0).toInt(), input, true)
                vm.registers.setUW(65535, input.length.toUShort())
            }
            Syscall.SLEEP -> {
                val duration = vm.registers.getUW(0).toLong()
                Thread.sleep(duration)
            }
            Syscall.GFX_ENABLE -> vm.gfx_enable()
            Syscall.GFX_CLEAR -> vm.gfx_clear()
            Syscall.GFX_PLOT -> vm.gfx_plot()
            Syscall.RND -> {
                vm.registers.setUB(0, (Random.nextInt() ushr 3).toUByte())
            }
            Syscall.WAIT -> {
                val millis = vm.registers.getUW(0).toLong() * 1000/60
                Thread.sleep(millis)
            }
            Syscall.WAITVSYNC -> vm.waitvsync()
            Syscall.TMP_PRINT_UW -> {
                println("VM: UW=${vm.registers.getUW(0)}")
            }
            else -> TODO("syscall ${call.name}")
        }
    }
}
