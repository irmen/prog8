package prog8.compilerinterface

import java.nio.file.Path
import kotlin.io.path.Path

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

class CompilationOptions(val output: OutputType,
                         val launcher: LauncherType,
                         val zeropage: ZeropageType,
                         val zpReserved: List<UIntRange>,
                         val floats: Boolean,
                         val noSysInit: Boolean,
                         val compTarget: ICompilationTarget,
                         // these are set based on command line arguments:
                         var slowCodegenWarnings: Boolean = false,
                         var optimize: Boolean = false,
                         var optimizeFloatExpressions: Boolean = false,
                         var dontReinitGlobals: Boolean = false,
                         var asmQuiet: Boolean = false,
                         var asmListfile: Boolean = false,
                         var experimentalCodegen: Boolean = false,
                         var outputDir: Path = Path("")
)
