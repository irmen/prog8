package prog8.vm

import kotlin.math.min

/*
SYSCALLS:

0 = reset ; resets system
1 = exit ; stops program and returns statuscode from r0.w
2 = print_c ; print single character
3 = print_s ; print 0-terminated string from memory
4 = print_u8 ; print unsigned int byte
5 = print_u16 ; print unsigned int word
6 = input ; reads a line of text entered by the user, r0.w = memory buffer, r1.b = maxlength (0-255, 0=unlimited).  Zero-terminates the string. Returns length in r0.w
7 = sleep ; sleep amount of milliseconds
8 = gfx_enable  ; enable graphics window  r0.b = 0 -> lores 320x240,  r0.b = 1 -> hires 640x480
9 = gfx_clear   ; clear graphics window with shade in r0.b
10 = gfx_plot   ; plot pixel in graphics window, r0.w/r1.w contain X and Y coordinates, r2.b contains brightness
11 = decimal string to word (unsigned)
12 = decimal string to word (signed)
13 = wait       ; wait certain amount of jiffies (1/60 sec)
14 = waitvsync  ; wait on vsync
15 = sort_ubyte array
16 = sort_byte array
17 = sort_uword array
18 = sort_word array
19 = any_byte array
20 = any_word array
21 = any_float array
22 = all_byte array
23 = all_word array
24 = all_float array
25 = print_f  (floating point value in fp reg 0)
26 = reverse_bytes array
27 = reverse_words array
28 = reverse_floats array
29 = compare strings
30 = gfx_getpixel      ; get byte pixel value at coordinates r0.w/r1.w
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
    STR_TO_UWORD,
    STR_TO_WORD,
    WAIT,
    WAITVSYNC,
    SORT_UBYTE,
    SORT_BYTE,
    SORT_UWORD,
    SORT_WORD,
    ANY_BYTE,
    ANY_WORD,
    ANY_FLOAT,
    ALL_BYTE,
    ALL_WORD,
    ALL_FLOAT,
    PRINT_F,
    REVERSE_BYTES,
    REVERSE_WORDS,
    REVERSE_FLOATS,
    COMPARE_STRINGS,
    GFX_GETPIXEL
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
                vm.registers.setUW(0, input.length.toUShort())
            }
            Syscall.SLEEP -> {
                val duration = vm.registers.getUW(0).toLong()
                Thread.sleep(duration)
            }
            Syscall.GFX_ENABLE -> vm.gfx_enable()
            Syscall.GFX_CLEAR -> vm.gfx_clear()
            Syscall.GFX_PLOT -> vm.gfx_plot()
            Syscall.GFX_GETPIXEL ->vm.gfx_getpixel()
            Syscall.WAIT -> {
                val millis = vm.registers.getUW(0).toLong() * 1000/60
                Thread.sleep(millis)
            }
            Syscall.WAITVSYNC -> vm.waitvsync()
            Syscall.SORT_UBYTE -> {
                val address = vm.registers.getUW(0).toInt()
                val length = vm.registers.getUB(1).toInt()
                val array = IntProgression.fromClosedRange(address, address+length-1, 1).map {
                    vm.memory.getUB(it)
                }.sorted()
                array.withIndex().forEach { (index, value)->
                    vm.memory.setUB(address+index, value)
                }
            }
            Syscall.SORT_BYTE -> {
                val address = vm.registers.getUW(0).toInt()
                val length = vm.registers.getUB(1).toInt()
                val array = IntProgression.fromClosedRange(address, address+length-1, 1).map {
                    vm.memory.getSB(it)
                }.sorted()
                array.withIndex().forEach { (index, value)->
                    vm.memory.setSB(address+index, value)
                }
            }
            Syscall.SORT_UWORD -> {
                val address = vm.registers.getUW(0).toInt()
                val length = vm.registers.getUB(1).toInt()
                val array = IntProgression.fromClosedRange(address, address+length*2-2, 2).map {
                    vm.memory.getUW(it)
                }.sorted()
                array.withIndex().forEach { (index, value)->
                    vm.memory.setUW(address+index*2, value)
                }
            }
            Syscall.SORT_WORD -> {
                val address = vm.registers.getUW(0).toInt()
                val length = vm.registers.getUB(1).toInt()
                val array = IntProgression.fromClosedRange(address, address+length*2-2, 2).map {
                    vm.memory.getSW(it)
                }.sorted()
                array.withIndex().forEach { (index, value)->
                    vm.memory.setSW(address+index*2, value)
                }
            }
            Syscall.REVERSE_BYTES -> {
                val address = vm.registers.getUW(0).toInt()
                val length = vm.registers.getUB(1).toInt()
                val array = IntProgression.fromClosedRange(address, address+length-1, 1).map {
                    vm.memory.getUB(it)
                }.reversed()
                array.withIndex().forEach { (index, value)->
                    vm.memory.setUB(address+index, value)
                }
            }
            Syscall.REVERSE_WORDS -> {
                val address = vm.registers.getUW(0).toInt()
                val length = vm.registers.getUB(1).toInt()
                val array = IntProgression.fromClosedRange(address, address+length*2-2, 2).map {
                    vm.memory.getUW(it)
                }.reversed()
                array.withIndex().forEach { (index, value)->
                    vm.memory.setUW(address+index*2, value)
                }
            }
            Syscall.REVERSE_FLOATS -> {
                val address = vm.registers.getUW(0).toInt()
                val length = vm.registers.getUB(1).toInt()
                val array = IntProgression.fromClosedRange(address, address+length*4-2, 4).map {
                    vm.memory.getFloat(it)
                }.reversed()
                array.withIndex().forEach { (index, value)->
                    vm.memory.setFloat(address+index*4, value)
                }
            }
            Syscall.ANY_BYTE -> {
                val address = vm.registers.getUW(0).toInt()
                val length = vm.registers.getUB(1).toInt()
                val addresses = IntProgression.fromClosedRange(address, address+length*2-2, 2)
                if(addresses.any { vm.memory.getUB(it).toInt()!=0 })
                    vm.registers.setUB(0, 1u)
                else
                    vm.registers.setUB(0, 0u)
            }
            Syscall.ANY_WORD -> {
                val address = vm.registers.getUW(0).toInt()
                val length = vm.registers.getUB(1).toInt()
                val addresses = IntProgression.fromClosedRange(address, address+length*2-2, 2)
                if(addresses.any { vm.memory.getUW(it).toInt()!=0 })
                    vm.registers.setUB(0, 1u)
                else
                    vm.registers.setUB(0, 0u)
            }
            Syscall.ANY_FLOAT -> {
                val address = vm.registers.getUW(0).toInt()
                val length = vm.registers.getUB(1).toInt()
                val addresses = IntProgression.fromClosedRange(address, address+length*4-2, 4)
                if(addresses.any { vm.memory.getFloat(it).toInt()!=0 })
                    vm.registers.setUB(0, 1u)
                else
                    vm.registers.setUB(0, 0u)
            }
            Syscall.ALL_BYTE -> {
                val address = vm.registers.getUW(0).toInt()
                val length = vm.registers.getUB(1).toInt()
                val addresses = IntProgression.fromClosedRange(address, address+length*2-2, 2)
                if(addresses.all { vm.memory.getUB(it).toInt()!=0 })
                    vm.registers.setUB(0, 1u)
                else
                    vm.registers.setUB(0, 0u)
            }
            Syscall.ALL_WORD -> {
                val address = vm.registers.getUW(0).toInt()
                val length = vm.registers.getUB(1).toInt()
                val addresses = IntProgression.fromClosedRange(address, address+length*2-2, 2)
                if(addresses.all { vm.memory.getUW(it).toInt()!=0 })
                    vm.registers.setUB(0, 1u)
                else
                    vm.registers.setUB(0, 0u)
            }
            Syscall.ALL_FLOAT -> {
                val address = vm.registers.getUW(0).toInt()
                val length = vm.registers.getUB(1).toInt()
                val addresses = IntProgression.fromClosedRange(address, address+length*4-2, 4)
                if(addresses.all { vm.memory.getFloat(it).toInt()!=0 })
                    vm.registers.setUB(0, 1u)
                else
                    vm.registers.setUB(0, 0u)
            }
            Syscall.PRINT_F -> {
                val value = vm.registers.getFloat(0)
                print(value)
            }
            Syscall.STR_TO_UWORD -> {
                val stringAddr = vm.registers.getUW(0)
                val string = vm.memory.getString(stringAddr.toInt())
                vm.registers.setUW(0, string.toUShort())
            }
            Syscall.STR_TO_WORD -> {
                val stringAddr = vm.registers.getUW(0)
                val string = vm.memory.getString(stringAddr.toInt())
                vm.registers.setSW(0, string.toShort())
            }
            Syscall.COMPARE_STRINGS -> {
                val firstAddr = vm.registers.getUW(0)
                val secondAddr = vm.registers.getUW(1)
                val first = vm.memory.getString(firstAddr.toInt())
                val second = vm.memory.getString(secondAddr.toInt())
                val comparison = first.compareTo(second)
                vm.registers.setSB(0, comparison.toByte())
            }
            else -> TODO("syscall ${call.name}")
        }
    }
}
