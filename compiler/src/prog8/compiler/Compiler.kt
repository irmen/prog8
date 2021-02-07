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
                              val noSysInit: Boolean) {
    var slowCodegenWarnings = false
    var optimize = false
}


class CompilerException(message: String?) : Exception(message)

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
