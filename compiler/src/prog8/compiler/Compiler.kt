package prog8.compiler

import java.io.File
import java.io.InputStream
import java.nio.file.Path
import kotlin.math.abs

enum class OutputType {
    RAW,
    PRG
}

enum class LauncherType {
    BASIC,
    NONE
}

enum class ZeropageType {
    BASICSAFE,
    FLOATSAFE,
    KERNALSAFE,
    FULL,
    DONTUSE
}

data class CompilationOptions(val output: OutputType,
                              val launcher: LauncherType,
                              val zeropage: ZeropageType,
                              val zpReserved: List<IntRange>,
                              val floats: Boolean,
                              val compilationTarget: String?)


class CompilerException(message: String?) : Exception(message)

fun Number.toHex(): String {
    //  0..15 -> "0".."15"
    //  16..255 -> "$10".."$ff"
    //  256..65536 -> "$0100".."$ffff"
    // negative values are prefixed with '-'.
    val integer = this.toInt()
    if(integer<0)
        return '-' + abs(integer).toHex()
    return when (integer) {
        in 0 until 16 -> integer.toString()
        in 0 until 0x100 -> "$"+integer.toString(16).padStart(2,'0')
        in 0 until 0x10000 -> "$"+integer.toString(16).padStart(4,'0')
        else -> throw CompilerException("number too large for 16 bits $this")
    }
}

fun loadAsmIncludeFile(filename: String, source: Path): String {
    return if (filename.startsWith("library:")) {
        val resource = tryGetEmbeddedResource(filename.substring(8))
                ?: throw IllegalArgumentException("library file '$filename' not found")
        resource.bufferedReader().use { it.readText() }
    } else {
        // first try in the isSameAs folder as where the containing file was imported from
        val sib = source.resolveSibling(filename)
        if (sib.toFile().isFile)
            sib.toFile().readText()
        else
            File(filename).readText()
    }
}

internal fun tryGetEmbeddedResource(name: String): InputStream? {
    return object{}.javaClass.getResourceAsStream("/prog8lib/$name")
}
