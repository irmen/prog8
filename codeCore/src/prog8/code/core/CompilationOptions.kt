package prog8.code.core

import java.nio.file.Path
import kotlin.io.path.Path


class CompilationOptions(val output: OutputType,
                         val launcher: CbmPrgLauncherType,
                         val zeropage: ZeropageType,
                         val zpReserved: List<UIntRange>,
                         val floats: Boolean,
                         val noSysInit: Boolean,
                         val compTarget: ICompilationTarget,
                         // these are set later, based on command line arguments or options in the source code:
                         var loadAddress: UInt,
                         var slowCodegenWarnings: Boolean = false,
                         var optimize: Boolean = false,
                         var optimizeFloatExpressions: Boolean = false,
                         var dontReinitGlobals: Boolean = false,
                         var asmQuiet: Boolean = false,
                         var asmListfile: Boolean = false,
                         var experimentalCodegen: Boolean = false,
                         var evalStackBaseAddress: UInt? = null,
                         var outputDir: Path = Path(""),
                         var symbolDefs: Map<String, String> = emptyMap()
)
