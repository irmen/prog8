package prog8.vm

import prog8.code.core.AssemblyError
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
31 = rndseed
32 = rndfseed
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
    GFX_GETPIXEL,
    RNDSEED,
    RNDFSEED,
    RND,
    RNDW,
    RNDF,
    STRING_CONTAINS,
    BYTEARRAY_CONTAINS,
    WORDARRAY_CONTAINS;

    companion object {
        private val VALUES = values()
        fun fromInt(value: Int) = VALUES[value]
    }
}

object SysCalls {
    fun call(call: Syscall, vm: VirtualMachine) {

        when(call) {
            Syscall.RESET -> {
                vm.reset(false)
            }
            Syscall.EXIT ->{
                vm.exit(vm.valueStack.pop().toInt())
            }
            Syscall.PRINT_C -> {
                val char = vm.valueStack.pop().toInt()
                print(Char(char))
            }
            Syscall.PRINT_S -> {
                var addr = vm.valueStack.popw().toInt()
                while(true) {
                    val char = vm.memory.getUB(addr).toInt()
                    if(char==0)
                        break
                    print(Char(char))
                    addr++
                }
            }
            Syscall.PRINT_U8 -> {
                print(vm.valueStack.pop())
            }
            Syscall.PRINT_U16 -> {
                print(vm.valueStack.popw())
            }
            Syscall.INPUT -> {
                var input = readln()
                val maxlen = vm.valueStack.pop().toInt()
                if(maxlen>0)
                    input = input.substring(0, min(input.length, maxlen))
                vm.memory.setString(vm.valueStack.popw().toInt(), input, true)
                vm.valueStack.push(input.length.toUByte())
            }
            Syscall.SLEEP -> {
                val duration = vm.valueStack.popw().toLong()
                Thread.sleep(duration)
            }
            Syscall.GFX_ENABLE -> vm.gfx_enable()
            Syscall.GFX_CLEAR -> vm.gfx_clear()
            Syscall.GFX_PLOT -> vm.gfx_plot()
            Syscall.GFX_GETPIXEL ->vm.gfx_getpixel()
            Syscall.WAIT -> {
                val millis = vm.valueStack.popw().toLong() * 1000/60
                Thread.sleep(millis)
            }
            Syscall.WAITVSYNC -> vm.waitvsync()
            Syscall.SORT_UBYTE -> {
                val length = vm.valueStack.pop().toInt()
                val address = vm.valueStack.popw().toInt()
                val array = IntProgression.fromClosedRange(address, address+length-1, 1).map {
                    vm.memory.getUB(it)
                }.sorted()
                array.withIndex().forEach { (index, value)->
                    vm.memory.setUB(address+index, value)
                }
            }
            Syscall.SORT_BYTE -> {
                val length = vm.valueStack.pop().toInt()
                val address = vm.valueStack.popw().toInt()
                val array = IntProgression.fromClosedRange(address, address+length-1, 1).map {
                    vm.memory.getSB(it)
                }.sorted()
                array.withIndex().forEach { (index, value)->
                    vm.memory.setSB(address+index, value)
                }
            }
            Syscall.SORT_UWORD -> {
                val length = vm.valueStack.pop().toInt()
                val address = vm.valueStack.popw().toInt()
                val array = IntProgression.fromClosedRange(address, address+length*2-2, 2).map {
                    vm.memory.getUW(it)
                }.sorted()
                array.withIndex().forEach { (index, value)->
                    vm.memory.setUW(address+index*2, value)
                }
            }
            Syscall.SORT_WORD -> {
                val length = vm.valueStack.pop().toInt()
                val address = vm.valueStack.popw().toInt()
                val array = IntProgression.fromClosedRange(address, address+length*2-2, 2).map {
                    vm.memory.getSW(it)
                }.sorted()
                array.withIndex().forEach { (index, value)->
                    vm.memory.setSW(address+index*2, value)
                }
            }
            Syscall.REVERSE_BYTES -> {
                val length = vm.valueStack.pop().toInt()
                val address = vm.valueStack.popw().toInt()
                val array = IntProgression.fromClosedRange(address, address+length-1, 1).map {
                    vm.memory.getUB(it)
                }.reversed()
                array.withIndex().forEach { (index, value)->
                    vm.memory.setUB(address+index, value)
                }
            }
            Syscall.REVERSE_WORDS -> {
                val length = vm.valueStack.pop().toInt()
                val address = vm.valueStack.popw().toInt()
                val array = IntProgression.fromClosedRange(address, address+length*2-2, 2).map {
                    vm.memory.getUW(it)
                }.reversed()
                array.withIndex().forEach { (index, value)->
                    vm.memory.setUW(address+index*2, value)
                }
            }
            Syscall.REVERSE_FLOATS -> {
                val length = vm.valueStack.pop().toInt()
                val address = vm.valueStack.popw().toInt()
                val array = IntProgression.fromClosedRange(address, address+length*4-2, 4).map {
                    vm.memory.getFloat(it)
                }.reversed()
                array.withIndex().forEach { (index, value)->
                    vm.memory.setFloat(address+index*4, value)
                }
            }
            Syscall.ANY_BYTE -> {
                val length = vm.valueStack.pop().toInt()
                val address = vm.valueStack.popw().toInt()
                val addresses = IntProgression.fromClosedRange(address, address+length*2-2, 2)
                if(addresses.any { vm.memory.getUB(it).toInt()!=0 })
                    vm.valueStack.push(1u)
                else
                    vm.valueStack.push(0u)
            }
            Syscall.ANY_WORD -> {
                val length = vm.valueStack.pop().toInt()
                val address = vm.valueStack.popw().toInt()
                val addresses = IntProgression.fromClosedRange(address, address+length*2-2, 2)
                if(addresses.any { vm.memory.getUW(it).toInt()!=0 })
                    vm.valueStack.push(1u)
                else
                    vm.valueStack.push(0u)
            }
            Syscall.ANY_FLOAT -> {
                val length = vm.valueStack.pop().toInt()
                val address = vm.valueStack.popw().toInt()
                val addresses = IntProgression.fromClosedRange(address, address+length*4-2, 4)
                if(addresses.any { vm.memory.getFloat(it).toInt()!=0 })
                    vm.valueStack.push(1u)
                else
                    vm.valueStack.push(0u)
            }
            Syscall.ALL_BYTE -> {
                val length = vm.valueStack.pop().toInt()
                val address = vm.valueStack.popw().toInt()
                val addresses = IntProgression.fromClosedRange(address, address+length*2-2, 2)
                if(addresses.all { vm.memory.getUB(it).toInt()!=0 })
                    vm.valueStack.push(1u)
                else
                    vm.valueStack.push(0u)
            }
            Syscall.ALL_WORD -> {
                val length = vm.valueStack.pop().toInt()
                val address = vm.valueStack.popw().toInt()
                val addresses = IntProgression.fromClosedRange(address, address+length*2-2, 2)
                if(addresses.all { vm.memory.getUW(it).toInt()!=0 })
                    vm.valueStack.push(1u)
                else
                    vm.valueStack.push(0u)
            }
            Syscall.ALL_FLOAT -> {
                val length = vm.valueStack.pop().toInt()
                val address = vm.valueStack.popw().toInt()
                val addresses = IntProgression.fromClosedRange(address, address+length*4-2, 4)
                if(addresses.all { vm.memory.getFloat(it).toInt()!=0 })
                    vm.valueStack.push(1u)
                else
                    vm.valueStack.push(0u)
            }
            Syscall.PRINT_F -> {
                print(vm.valueStack.popf())
            }
            Syscall.STR_TO_UWORD -> {
                val stringAddr = vm.valueStack.popw()
                val string = vm.memory.getString(stringAddr.toInt()).takeWhile { it.isDigit() }
                val value = try {
                    string.toUShort()
                } catch(_: NumberFormatException) {
                    0u
                }
                vm.valueStack.pushw(value)
            }
            Syscall.STR_TO_WORD -> {
                val stringAddr = vm.valueStack.popw()
                val memstring = vm.memory.getString(stringAddr.toInt())
                val match = Regex("^[+-]?\\d+").find(memstring)
                if(match==null) {
                    vm.valueStack.pushw(0u)
                    return
                }
                val value = try {
                    match.value.toShort()
                } catch(_: NumberFormatException) {
                    0
                }
                vm.valueStack.pushw(value.toUShort())
            }
            Syscall.COMPARE_STRINGS -> {
                val secondAddr = vm.valueStack.popw()
                val firstAddr = vm.valueStack.popw()
                val first = vm.memory.getString(firstAddr.toInt())
                val second = vm.memory.getString(secondAddr.toInt())
                val comparison = first.compareTo(second)
                if(comparison==0)
                    vm.valueStack.push(0u)
                else if(comparison<0)
                    vm.valueStack.push((-1).toUByte())
                else
                    vm.valueStack.push(1u)
            }
            Syscall.RNDFSEED -> {
                val seed = vm.valueStack.popf()
                if(seed>0)  // always use negative seed, this mimics the behavior on CBM machines
                    vm.randomSeedFloat(-seed)
                else
                    vm.randomSeedFloat(seed)
            }
            Syscall.RNDSEED -> {
                val seed2 = vm.valueStack.popw()
                val seed1 = vm.valueStack.popw()
                vm.randomSeed(seed1, seed2)
            }
            Syscall.RND -> {
                vm.valueStack.push(vm.randomGenerator.nextInt().toUByte())
            }
            Syscall.RNDW -> {
                vm.valueStack.pushw(vm.randomGenerator.nextInt().toUShort())
            }
            Syscall.RNDF -> {
                vm.valueStack.pushf(vm.randomGeneratorFloats.nextFloat())
            }
            Syscall.STRING_CONTAINS -> {
                val stringAddr = vm.valueStack.popw()
                val char = vm.valueStack.pop().toInt().toChar()
                val string = vm.memory.getString(stringAddr.toInt())
                vm.valueStack.push(if(char in string) 1u else 0u)
            }
            Syscall.BYTEARRAY_CONTAINS -> {
                var length = vm.valueStack.pop()
                var array = vm.valueStack.popw().toInt()
                val value = vm.valueStack.pop()
                while(length>0u) {
                    if(vm.memory.getUB(array)==value) {
                        vm.valueStack.push(1u)
                        return
                    }
                    array++
                    length--
                }
                vm.valueStack.push(0u)
            }
            Syscall.WORDARRAY_CONTAINS -> {
                var length = vm.valueStack.pop()
                var array = vm.valueStack.popw().toInt()
                val value = vm.valueStack.popw()
                while(length>0u) {
                    if(vm.memory.getUW(array)==value) {
                        vm.valueStack.push(1u)
                        return
                    }
                    array += 2
                    length--
                }
                vm.valueStack.push(0u)
            }
            else -> throw AssemblyError("missing syscall ${call.name}")
        }
    }
}
