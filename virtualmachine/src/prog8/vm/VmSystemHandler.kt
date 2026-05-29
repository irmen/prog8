package prog8.vm

import java.awt.Color
import java.awt.GraphicsEnvironment
import java.io.IOException
import kotlin.random.Random

internal fun VirtualMachine.gfx_enable(mode: UByte) {
    window = when(mode.toInt()) {
        0 -> GraphicsWindow(320, 240, 3)
        1 -> GraphicsWindow(640, 480, 2)
        else -> throw IllegalArgumentException("invalid screen mode")
    }
    window!!.start()
}

internal fun VirtualMachine.gfx_clear(color: UByte) {
    window?.clear(color.toInt())
}

internal fun VirtualMachine.gfx_plot(x: UShort, y: UShort, color: UByte) {
    window?.plot(x.toInt(), y.toInt(), color.toInt())
}

internal fun VirtualMachine.gfx_getpixel(x: UShort, y: UShort): UByte {
    return if(window==null)
        0u
    else {
        val color = Color(window!!.getpixel(x.toInt(), y.toInt()))
        color.green.toUByte()
    }
}

internal fun VirtualMachine.gfx_text(xx: UShort, yy: UShort, textptr: UShort, color: UByte) {
    val text = memory.getString(textptr.toUInt())
    window?.drawText(xx.toInt(), yy.toInt(), text, color.toInt())
}

internal fun VirtualMachine.gfx_close() {
    window?.close()
}

internal fun VirtualMachine.waitvsync() {
    // note: not a real vsync, but a sleep for approx. the duration of a frame
    val ms = try {
        val mode = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode
        if(mode!=null && mode.refreshRate>0) 1000L / mode.refreshRate else 16L
    } catch(_: Exception) {
        16L
    }
    try {
        Thread.sleep(ms)
    } catch(_: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}

internal fun VirtualMachine.randomSeed(seed1: UShort, seed2: UShort) {
    randomGenerator = Random(((seed1.toUInt() shl 16) or seed2.toUInt()).toInt())
}

internal fun VirtualMachine.randomSeedFloat(seed: Double) {
    randomGeneratorFloats = Random(seed.toBits())
}

internal fun VirtualMachine.open_file_read(name: String): Int {
    try {
        fileInputStream = java.io.RandomAccessFile(name, "r")
    } catch (_: IOException) {
        return 0
    }
    return 1
}

internal fun VirtualMachine.open_file_write(name: String): Int {
    try {
        fileOutputStream = java.io.RandomAccessFile(name, "rw")
    } catch (_: IOException) {
        return 0
    }
    return 1
}

internal fun VirtualMachine.close_file_read() {
    fileInputStream?.close()
    fileInputStream = null
}

internal fun VirtualMachine.close_file_write() {
    fileOutputStream?.close()
    fileOutputStream = null
}

internal fun VirtualMachine.read_file_byte(): Pair<Boolean, UByte> {
    return if (fileInputStream == null)
        false to 0u
    else {
        try {
            val byte = fileInputStream!!.read()
            if (byte >= 0)
                true to byte.toUByte()
            else
                false to 0u
        } catch (_: IOException) {
            false to 0u
        }
    }
}

internal fun VirtualMachine.write_file_byte(byte: UByte): Boolean {
    if (fileOutputStream == null)
        return false
    try {
        fileOutputStream!!.write(byte.toInt())
        return true
    } catch (_: IOException) {
        return false
    }
}

internal fun VirtualMachine.seek_file(position: Int): Boolean {
    return if (fileInputStream == null)
        false
    else {
        try {
            fileInputStream!!.seek(position.toLong())
            true
        } catch (_: IOException) {
            false
        }
    }
}

internal fun VirtualMachine.tell_file_pos(): Int {
    return if (fileInputStream == null)
        0
    else {
        try {
            fileInputStream!!.filePointer.toInt()
        } catch (_: IOException) {
            0
        }
    }
}

internal fun VirtualMachine.tell_file_size(): Int {
    return if (fileInputStream == null)
        0
    else {
        try {
            fileInputStream!!.length().toInt()
        } catch (_: IOException) {
            0
        }
    }
}
