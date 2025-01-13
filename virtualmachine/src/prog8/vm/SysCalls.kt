package prog8.vm

import prog8.intermediate.FunctionCallArgs
import prog8.intermediate.IRDataType
import java.io.File
import kotlin.io.path.*
import kotlin.math.*

/*
SYSCALLS:     DO NOT RENUMBER THESE OR YOU WILL BREAK EXISTING CODE

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
15 = print_f  (floating point value in fp reg 0)
16 = compare strings
17 = gfx_getpixel      ; get byte pixel value at coordinates r0.w/r1.w
18 = rndseed
19 = rndfseed
20 = RND
21 = RNDW
22 = RNDF
23 = STRING_CONTAINS
24 = BYTEARRAY_CONTAINS
25 = WORDARRAY_CONTAINS
26 = CLAMP_BYTE
27 = CLAMP_UBYTE
28 = CLAMP_WORD
29 = CLAMP_UWORD
30 = CLAMP_FLOAT
31 = ATAN
32 = str to float
33 = MUL16_LAST_UPPER
34 = float to str
35 = FLOATARRAY_CONTAINS
36 = memcopy
37 = memset
38 = memsetw
39 = stringcopy
40 = load
41 = load_raw
42 = save
43 = delete
44 = rename
45 = directory
46 = getconsolesize
47 = memcmp
48 = CURDIR
49 = MKDIR
50 = CHDIR
51 = RMDIR
52 = OPEN_FILE
53 = OPEN_FILE_WRITE
54 = READ_FILE_BYTE
55 = WRITE_FILE_BYTE
56 = CLOSE_FILE
57 = CLOSE_FILE_WRITE
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
    PRINT_F,
    COMPARE_STRINGS,
    GFX_GETPIXEL,
    RNDSEED,
    RNDFSEED,
    RND,
    RNDW,
    RNDF,
    STRING_CONTAINS,
    BYTEARRAY_CONTAINS,
    WORDARRAY_CONTAINS,
    CLAMP_BYTE,
    CLAMP_UBYTE,
    CLAMP_WORD,
    CLAMP_UWORD,
    CLAMP_FLOAT,
    ATAN,
    STR_TO_FLOAT,
    MUL16_LAST_UPPER,
    FLOAT_TO_STR,
    FLOATARRAY_CONTAINS,
    MEMCOPY,
    MEMSET,
    MEMSETW,
    STRINGCOPY,
    LOAD,
    LOAD_RAW,
    SAVE,
    DELETE,
    RENAME,
    DIRECTORY,
    GETGONSOLESIZE,
    MEMCMP,
    CURDIR,
    MKDIR,
    CHDIR,
    RMDIR,
    OPEN_FILE,
    OPEN_FILE_WRITE,
    READ_FILE_BYTE,
    WRITE_FILE_BYTE,
    CLOSE_FILE,
    CLOSE_FILE_WRITE,
    ;

    companion object {
        fun fromInt(value: Int) = entries[value]
    }
}

object SysCalls {
    private fun getArgValues(argspec: List<FunctionCallArgs.ArgumentSpec>, vm: VirtualMachine): List<Comparable<Nothing>> {
        return argspec.map {
            when(it.reg.dt) {
                IRDataType.BYTE -> vm.registers.getUB(it.reg.registerNum)
                IRDataType.WORD -> vm.registers.getUW(it.reg.registerNum)
                IRDataType.FLOAT -> vm.registers.getFloat(it.reg.registerNum)
            }
        }
    }

    private fun returnValue(returns: FunctionCallArgs.RegSpec, value: Comparable<Nothing>, vm: VirtualMachine) {
        val vv: Double = when(value) {
            is UByte -> value.toDouble()
            is UShort -> value.toDouble()
            is UInt -> value.toDouble()
            is Byte -> value.toDouble()
            is Short -> value.toDouble()
            is Int -> value.toDouble()
            is Float -> value.toDouble()
            is Double -> value
            is Boolean -> if(value) 1.0 else 0.0
            else -> (value as Number).toDouble()
        }
        when(returns.dt) {
            IRDataType.BYTE -> vm.registers.setUB(returns.registerNum, vv.toInt().toUByte())
            IRDataType.WORD -> vm.registers.setUW(returns.registerNum, vv.toInt().toUShort())
            IRDataType.FLOAT -> vm.registers.setFloat(returns.registerNum, vv)
        }
    }

    fun call(call: Syscall, callspec: FunctionCallArgs, vm: VirtualMachine) {

        when(call) {
            Syscall.RESET -> {
                vm.reset(false)
            }
            Syscall.EXIT ->{
                val exitValue = getArgValues(callspec.arguments, vm).single() as UByte
                vm.exit(exitValue.toInt())
            }
            Syscall.PRINT_C -> {
                val char = getArgValues(callspec.arguments, vm).single() as UByte
                print(Char(char.toInt()))
            }
            Syscall.PRINT_S -> {
                var addr = (getArgValues(callspec.arguments, vm).single() as UShort).toInt()
                while(true) {
                    val char = vm.memory.getUB(addr).toInt()
                    if(char==0)
                        break
                    print(Char(char))
                    addr++
                }
            }
            Syscall.PRINT_U8 -> {
                val value = getArgValues(callspec.arguments, vm).single()
                print(value)
            }
            Syscall.PRINT_U16 -> {
                val value = getArgValues(callspec.arguments, vm).single()
                print(value)
            }
            Syscall.INPUT -> {
                val (address, maxlen) = getArgValues(callspec.arguments, vm)
                var input = readln()
                val maxlenvalue = (maxlen as UByte).toInt()
                if(maxlenvalue>0)
                    input = input.substring(0, min(input.length, maxlenvalue))
                vm.memory.setString((address as UShort).toInt(), input, true)
                returnValue(callspec.returns.single(), input.length, vm)
            }
            Syscall.SLEEP -> {
                val duration = getArgValues(callspec.arguments, vm).single() as UShort
                Thread.sleep(duration.toLong())
            }
            Syscall.GFX_ENABLE -> {
                val mode = getArgValues(callspec.arguments, vm).single() as UByte
                vm.gfx_enable(mode)
            }
            Syscall.GFX_CLEAR -> {
                val color = getArgValues(callspec.arguments, vm).single() as UByte
                vm.gfx_clear(color)
            }
            Syscall.GFX_PLOT -> {
                val (x,y,color) = getArgValues(callspec.arguments, vm)
                vm.gfx_plot(x as UShort, y as UShort, color as UByte)
            }
            Syscall.GFX_GETPIXEL -> {
                val (x,y) = getArgValues(callspec.arguments, vm)
                val color = vm.gfx_getpixel(x as UShort, y as UShort)
                returnValue(callspec.returns.single(), color, vm)
            }
            Syscall.WAIT -> {
                val time = getArgValues(callspec.arguments, vm).single() as UShort
                Thread.sleep(time.toLong() * 1000/60)
            }
            Syscall.WAITVSYNC -> vm.waitvsync()
            Syscall.PRINT_F -> {
                val value = getArgValues(callspec.arguments, vm).single() as Double
                if(value.toInt().toDouble()==value)
                    print(value.toInt())
                else
                    print(value)
            }
            Syscall.STR_TO_UWORD -> {
                val stringAddr = getArgValues(callspec.arguments, vm).single() as UShort
                val string = vm.memory.getString(stringAddr.toInt()).takeWhile { it.isDigit() }
                val value = try {
                    string.toUShort()
                } catch(_: NumberFormatException) {
                    0u
                }
                returnValue(callspec.returns.single(), value, vm)
            }
            Syscall.STR_TO_WORD -> {
                val stringAddr = getArgValues(callspec.arguments, vm).single() as UShort
                val memstring = vm.memory.getString(stringAddr.toInt())
                val match = Regex("^[+-]?\\d+").find(memstring) ?: return returnValue(callspec.returns.single(), 0, vm)
                val value = try {
                    match.value.toShort()
                } catch(_: NumberFormatException) {
                    0
                }
                return returnValue(callspec.returns.single(), value, vm)
            }
            Syscall.STR_TO_FLOAT -> {
                val stringAddr = getArgValues(callspec.arguments, vm).single() as UShort
                val memstring = vm.memory.getString(stringAddr.toInt()).replace(" ", "")
                val result = if(memstring.isEmpty())
                    0.0
                else {
                    val trimmed = memstring.takeWhile { it in " +-0123456789.eE" }
                    try {
                        trimmed.toDouble()
                    } catch(_: NumberFormatException) {
                        0.0
                    }
                }
                returnValue(callspec.returns.single(), result, vm)
            }
            Syscall.COMPARE_STRINGS -> {
                val (firstV, secondV) = getArgValues(callspec.arguments, vm)
                val firstAddr = firstV as UShort
                val secondAddr = secondV as UShort
                val first = vm.memory.getString(firstAddr.toInt())
                val second = vm.memory.getString(secondAddr.toInt())
                val comparison = first.compareTo(second)
                if(comparison==0)
                    returnValue(callspec.returns.single(), 0, vm)
                else if(comparison<0)
                    returnValue(callspec.returns.single(), -1, vm)
                else
                    returnValue(callspec.returns.single(), 1, vm)
            }
            Syscall.MEMCMP -> {
                val (firstV, secondV, sizeV) = getArgValues(callspec.arguments, vm)
                var firstAddr = (firstV as UShort).toInt()
                var secondAddr = (secondV as UShort).toInt()
                var size = (sizeV as UShort).toInt()
                while(size>0) {
                    val comparison = vm.memory.getUB(firstAddr).compareTo(vm.memory.getUB(secondAddr))
                    if(comparison<0)
                        return returnValue(callspec.returns.single(), -1, vm)
                    else if(comparison>0)
                        return returnValue(callspec.returns.single(), 1, vm)
                    firstAddr++
                    secondAddr++
                    size--
                }
                return returnValue(callspec.returns.single(), 0, vm)
            }
            Syscall.RNDFSEED -> {
                val seed = getArgValues(callspec.arguments, vm).single() as Double
                if(seed>0)  // always use negative seed, this mimics the behavior on CBM machines
                    vm.randomSeedFloat(-seed)
                else
                    vm.randomSeedFloat(seed)
            }
            Syscall.RNDSEED -> {
                val (seed1, seed2) = getArgValues(callspec.arguments, vm)
                vm.randomSeed(seed1 as UShort, seed2 as UShort)
            }
            Syscall.RND -> {
                returnValue(callspec.returns.single(), vm.randomGenerator.nextInt().toUByte(), vm)
            }
            Syscall.RNDW -> {
                returnValue(callspec.returns.single(), vm.randomGenerator.nextInt().toUShort(), vm)
            }
            Syscall.RNDF -> {
                returnValue(callspec.returns.single(), vm.randomGeneratorFloats.nextFloat(), vm)
            }
            Syscall.STRING_CONTAINS -> {
                val (charV, addr) = getArgValues(callspec.arguments, vm)
                val stringAddr = addr as UShort
                val char = (charV as UByte).toInt().toChar()
                val string = vm.memory.getString(stringAddr.toInt())
                returnValue(callspec.returns.single(), if(char in string) 1u else 0u, vm)
            }
            Syscall.BYTEARRAY_CONTAINS -> {
                val (value, arrayV, lengthV) = getArgValues(callspec.arguments, vm)
                var length = lengthV as UByte
                var array = (arrayV as UShort).toInt()
                while(length>0u) {
                    if(vm.memory.getUB(array)==value)
                        return returnValue(callspec.returns.single(), 1u, vm)
                    array++
                    length--
                }
                returnValue(callspec.returns.single(), 0u, vm)
            }
            Syscall.WORDARRAY_CONTAINS -> {
                val (value, arrayV, lengthV) = getArgValues(callspec.arguments, vm)
                var length = lengthV as UByte
                var array = (arrayV as UShort).toInt()
                while(length>0u) {
                    if(vm.memory.getUW(array)==value)
                        return returnValue(callspec.returns.single(), 1u, vm)
                    array += 2
                    length--
                }
                returnValue(callspec.returns.single(), 0u, vm)
            }
            Syscall.FLOATARRAY_CONTAINS -> {
                val (value, arrayV, lengthV) = getArgValues(callspec.arguments, vm)
                var length = lengthV as UByte
                var array = (arrayV as UShort).toInt()
                while(length>0u) {
                    if(vm.memory.getFloat(array)==value)
                        return returnValue(callspec.returns.single(), 1u, vm)
                    array += vm.machine.FLOAT_MEM_SIZE
                    length--
                }
                returnValue(callspec.returns.single(), 0u, vm)
            }
            Syscall.CLAMP_BYTE -> {
                val (valueU, minimumU, maximumU) = getArgValues(callspec.arguments, vm)
                val value = (valueU as UByte).toByte().toInt()
                val minimum = (minimumU as UByte).toByte().toInt()
                val maximum = (maximumU as UByte).toByte().toInt()
                val result = min(max(value, minimum), maximum)
                returnValue(callspec.returns.single(), result, vm)
            }
            Syscall.CLAMP_UBYTE -> {
                val (valueU, minimumU, maximumU) = getArgValues(callspec.arguments, vm)
                val value = (valueU as UByte).toInt()
                val minimum = (minimumU as UByte).toInt()
                val maximum = (maximumU as UByte).toInt()
                val result = min(max(value, minimum), maximum)
                returnValue(callspec.returns.single(), result, vm)
            }
            Syscall.CLAMP_WORD -> {
                val (valueU, minimumU, maximumU) = getArgValues(callspec.arguments, vm)
                val value = (valueU as UShort).toShort().toInt()
                val minimum = (minimumU as UShort).toShort().toInt()
                val maximum = (maximumU as UShort).toShort().toInt()
                val result = min(max(value, minimum), maximum)
                returnValue(callspec.returns.single(), result, vm)
            }
            Syscall.CLAMP_UWORD -> {
                val (valueU, minimumU, maximumU) = getArgValues(callspec.arguments, vm)
                val value = (valueU as UShort).toInt()
                val minimum = (minimumU as UShort).toInt()
                val maximum = (maximumU as UShort).toInt()
                val result = min(max(value, minimum), maximum)
                returnValue(callspec.returns.single(), result, vm)
            }
            Syscall.CLAMP_FLOAT -> {
                val (valueU, minimumU, maximumU) = getArgValues(callspec.arguments, vm)
                val value = valueU as Double
                val minimum = minimumU as Double
                val maximum = maximumU as Double
                val result = min(max(value, minimum), maximum)
                returnValue(callspec.returns.single(), result, vm)
            }
            Syscall.ATAN -> {
                val (x1, y1, x2, y2) = getArgValues(callspec.arguments, vm)
                val x1f = (x1 as UByte).toDouble()
                val y1f = (y1 as UByte).toDouble()
                val x2f = (x2 as UByte).toDouble()
                val y2f = (y2 as UByte).toDouble()
                var radians = atan2(y2f-y1f, x2f-x1f)
                if(radians<0)
                    radians+=2*PI
                val result = floor(radians/2.0/PI*256.0)
                returnValue(callspec.returns.single(), result, vm)
            }
            Syscall.MUL16_LAST_UPPER -> {
                returnValue(callspec.returns.single(), vm.mul16LastUpper, vm)
            }
            Syscall.FLOAT_TO_STR -> {
                val (buffer, number) = getArgValues(callspec.arguments, vm)
                val bufferAddr = (buffer as UShort).toShort().toInt()
                val numf = number as Double
                val numStr = if(numf.toInt().toDouble()==numf) numf.toInt().toString() else numf.toString()
                vm.memory.setString(bufferAddr, numStr, true)
            }
            Syscall.MEMCOPY -> {
                val (fromA, toA, countA) = getArgValues(callspec.arguments, vm)
                val from = (fromA as UShort).toInt()
                val to = (toA as UShort).toInt()
                val count = (countA as UShort).toInt()
                for(offset in 0..<count) {
                    vm.memory.setUB(to+offset, vm.memory.getUB(from+offset))
                }
            }
            Syscall.MEMSET -> {
                val (memA, numbytesA, valueA) = getArgValues(callspec.arguments, vm)
                val mem = (memA as UShort).toInt()
                val numbytes = (numbytesA as UShort).toInt()
                val value = valueA as UByte
                for(addr in mem..<mem+numbytes) {
                    vm.memory.setUB(addr, value)
                }
            }
            Syscall.MEMSETW -> {
                val (memA, numwordsA, valueA) = getArgValues(callspec.arguments, vm)
                val mem = (memA as UShort).toInt()
                val numwords = (numwordsA as UShort).toInt()
                val value = valueA as UShort
                for(addr in mem..<mem+numwords*2 step 2) {
                    vm.memory.setUW(addr, value)
                }
            }
            Syscall.STRINGCOPY -> {
                val (sourceA, targetA) = getArgValues(callspec.arguments, vm)
                val source = (sourceA as UShort).toInt()
                val target = (targetA as UShort).toInt()
                val string = vm.memory.getString(source)
                vm.memory.setString(target, string, true)
                returnValue(callspec.returns.single(), string.length, vm)
            }
            Syscall.LOAD -> {
                val (filenameA, addrA) = getArgValues(callspec.arguments, vm)
                val filename = vm.memory.getString((filenameA as UShort).toInt())
                if(File(filename).exists()) {
                    val data = File(filename).readBytes()
                    val addr = if (addrA == 0) data[0] + data[1] * 256 else (addrA as UShort).toInt()
                    for (i in 0..<data.size - 2) {
                        vm.memory.setUB(addr + i, data[i + 2].toUByte())
                    }
                    returnValue(callspec.returns.single(), (addr + data.size - 2).toUShort(), vm)
                } else {
                    returnValue(callspec.returns.single(), 0u, vm)
                }
            }
            Syscall.LOAD_RAW -> {
                val (filenameA, addrA) = getArgValues(callspec.arguments, vm)
                val filename = vm.memory.getString((filenameA as UShort).toInt())
                val addr = (addrA as UShort).toInt()
                if(File(filename).exists()) {
                    val data = File(filename).readBytes()
                    for (i in data.indices) {
                        vm.memory.setUB(addr + i, data[i].toUByte())
                    }
                    returnValue(callspec.returns.single(), (addr + data.size).toUShort(), vm)
                } else {
                    returnValue(callspec.returns.single(), 0u, vm)
                }
            }
            Syscall.SAVE -> {
                val (rawA, filenamePtr, startA, sizeA) = getArgValues(callspec.arguments, vm)
                val raw = (rawA as UByte).toInt()
                val size = (sizeA as UShort).toInt()
                val startPtr = (startA as UShort).toInt()
                val data: ByteArray
                if(raw==0) {
                    // save with 2 byte PRG load address header
                    data = ByteArray(size+2)
                    data[0] = (startPtr and 255).toByte()
                    data[1] = (startPtr shr 8).toByte()
                    for (i in 0..<size) {
                        data[i + 2] = vm.memory.getUB(startPtr + i).toByte()
                    }
                } else {
                    // 'raw' save, without header
                    data = ByteArray(size)
                    for (i in 0..<size) {
                        data[i] = vm.memory.getUB(startPtr + i).toByte()
                    }
                }
                val filename = vm.memory.getString((filenamePtr as UShort).toInt())
                if (File(filename).exists())
                    returnValue(callspec.returns.single(), 0u, vm)
                else {
                    File(filename).writeBytes(data)
                    returnValue(callspec.returns.single(), 1u, vm)
                }
            }
            Syscall.DELETE -> {
                val filenamePtr = getArgValues(callspec.arguments, vm).single() as UShort
                val filename = vm.memory.getString(filenamePtr.toInt())
                File(filename).delete()
            }
            Syscall.RENAME -> {
                val (origFilenamePtr, newFilenamePtr) = getArgValues(callspec.arguments, vm)
                val origFilename = vm.memory.getString((origFilenamePtr as UShort).toInt())
                val newFilename = vm.memory.getString((newFilenamePtr as UShort).toInt())
                File(origFilename).renameTo(File(newFilename))
            }
            Syscall.DIRECTORY -> {
                // no arguments
                val directory = Path("")
                println("Directory listing for ${directory.absolute().normalize()}")
                directory.listDirectoryEntries().sorted().forEach {
                    println("${it.toFile().length()}\t${it.normalize()}")
                }
                returnValue(callspec.returns.single(), 1u, vm)
            }
            Syscall.GETGONSOLESIZE -> {
                // no arguments
                if(System.console()==null) {
                    return returnValue(callspec.returns.single(), 30*256 + 80, vm)    // just return some defaults in this case 80*30
                }

                val linesS = System.getenv("LINES")
                val columnsS = System.getenv("COLUMNS")
                if(linesS!=null && columnsS!=null) {
                    val lines = linesS.toInt()
                    val columns = columnsS.toInt()
                    return returnValue(callspec.returns.single(), lines*256 + columns, vm)
                }

                try {
                    val process = ProcessBuilder("tput", "cols", "lines").inheritIO().redirectOutput(ProcessBuilder.Redirect.PIPE).start()
                    val result=process.waitFor()
                    if (result == 0) {
                        val response = process.inputStream.bufferedReader().lineSequence().iterator()
                        val width = response.next().toInt()
                        val height = response.next().toInt()
                        return returnValue(callspec.returns.single(), height*256 + width, vm)
                    }
                } catch (_: Exception) {
                    // don't know what happened...
                }
                return returnValue(callspec.returns.single(), 30*256 + 80, vm)    // just return some defaults in this case 80*30
            }

            Syscall.CURDIR -> {
                val curdir = Path("").absolute().toString()
                vm.memory.setString(0xfe00, curdir, true)
                return returnValue(callspec.returns.single(), 0xfe00, vm)
            }
            Syscall.MKDIR -> {
                val namePtr = getArgValues(callspec.arguments, vm).single() as UShort
                val name = vm.memory.getString(namePtr.toInt())
                Path(name).createDirectory()
            }
            Syscall.RMDIR -> {
                val namePtr = getArgValues(callspec.arguments, vm).single() as UShort
                val name = vm.memory.getString(namePtr.toInt())
                Path(name).deleteIfExists()
            }
            Syscall.CHDIR -> throw NotImplementedError("chdir is not possible in java/kotlin")
            Syscall.OPEN_FILE -> {
                val namePtr = getArgValues(callspec.arguments, vm).single() as UShort
                val name = vm.memory.getString(namePtr.toInt())
                val success = vm.open_file_read(name)
                return returnValue(callspec.returns.single(), success, vm)
            }
            Syscall.OPEN_FILE_WRITE -> {
                val namePtr = getArgValues(callspec.arguments, vm).single() as UShort
                val name = vm.memory.getString(namePtr.toInt())
                val success = vm.open_file_write(name)
                return returnValue(callspec.returns.single(), success, vm)
            }
            Syscall.READ_FILE_BYTE -> {
                val (success, byte) = vm.read_file_byte()
                return if(success)
                    returnValue(callspec.returns.single(), 0x0100 or byte.toInt(), vm)
                else
                    returnValue(callspec.returns.single(), 0x0000, vm)
            }
            Syscall.WRITE_FILE_BYTE -> {
                val byte = getArgValues(callspec.arguments, vm).single() as UByte
                return if(vm.write_file_byte(byte))
                    returnValue(callspec.returns.single(), 1, vm)
                else
                    returnValue(callspec.returns.single(), 0, vm)
            }
            Syscall.CLOSE_FILE -> vm.close_file_read()
            Syscall.CLOSE_FILE_WRITE -> vm.close_file_write()
        }
    }
}
