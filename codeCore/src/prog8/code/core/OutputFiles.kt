package prog8.code.core

import prog8.code.target.VMTarget
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name


/** VICE/box16 monitor (symbol) list file name for an artifact stem. */
fun viceMonListName(stem: String) = "$stem.vice-mon-list"


/**
 * Artifact stem of a user-supplied `-o` output file name.
 * A dot at index 0 is not an extension separator: ".pgz" keeps its whole name.
 * Only applied to `-o`; the legacy program stem (source name without extension) is used verbatim.
 */
fun outputStemOf(name: Path): String {
    val filename = name.name
    val dotIndex = filename.lastIndexOf('.')
    return if (dotIndex <= 0) filename else filename.substring(0, dotIndex)
}

/**
 * The final program artifact path: [CompilationOptions.outputFile] verbatim when set,
 * otherwise [defaultName] plus the extension for [CompilationOptions.output], in [CompilationOptions.outputDir].
 * RAW and LIBRARY both use ".bin"; AMIGAHUNK has no extension.
 * On the virtual target the artifact is the IR file, so ".p8ir" is always enforced on -o.
 */
fun CompilationOptions.programFile(defaultName: String): Path {
    // VMTarget's defaultOutputType = PRG is misleading: its artifact is the .p8ir file, never PRG.
    // The VM identifies the IR by its extension, so -o is not verbatim here: ".p8ir" is enforced.
    // Only the exact lowercase extension is recognized (same rule as -vm in CompilerMain).
    if(compTarget is VMTarget) {
        val ir = outputFile ?: return outputDir.resolve("$defaultName.p8ir")
        return if (ir.extension=="p8ir") ir else ir.resolveSibling("${ir.name}.p8ir")
    }
    outputFile?.let { return it }
    val filename = when(output) {
        OutputType.PRG -> "$defaultName.prg"
        OutputType.XEX -> "$defaultName.xex"
        OutputType.RAW -> "$defaultName.bin"
        OutputType.LIBRARY -> "$defaultName.bin"
        OutputType.ELF -> "$defaultName.elf"
        OutputType.AMIGAHUNK -> defaultName
    }
    return outputDir.resolve(filename)
}

/**
 * Output artifact naming.
 * Invariant: [asm] must resolve into the same directory as `CompilationOptions.outputDir`,
 * because 64tass resolves `.binary` includes relative to the generated `.asm`.
 */
class OutputFiles(val programFile: Path, val stem: String, val dir: Path) {
    fun asm() = dir.resolve("$stem.asm")
    fun list() = dir.resolve("$stem.list")
    fun viceMonList() = dir.resolve(viceMonListName(stem))
    fun binFile() = dir.resolve("$stem.bin")
    fun ir() = dir.resolve("$stem.p8ir")
    fun bankedCalls() = dir.resolve("$stem.bankedcalls")
    fun obj() = dir.resolve("$stem.o")
    fun linkScript() = dir.resolve("$stem.link.ld")

    companion object {
        /**
         * Builds the output file set. Without `-o` the stem is [defaultName] verbatim
         * (outputStemOf is only applied to the `-o` name). With `-o` the stem is derived
         * from the resolved artifact name, so it also covers the virtual target appending
         * ".p8ir". [dir] is the program file's parent, or [CompilationOptions.outputDir]
         * for a bare -o filename.
         */
        fun of(options: CompilationOptions, defaultName: String): OutputFiles {
            val programFile = options.programFile(defaultName)
            val stem = options.outputFile?.let { outputStemOf(programFile) } ?: defaultName
            val dir = programFile.parent ?: options.outputDir
            return OutputFiles(programFile, stem, dir)
        }
    }
}