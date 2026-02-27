package prog8.code.core

import java.nio.file.Path
import kotlin.io.path.Path


class CompilationOptions(val output: OutputType,
                         val launcher: CbmPrgLauncherType,
                         val zeropage: ZeropageType,
                         val zpReserved: List<UIntRange>,
                         val zpAllowed: List<UIntRange>,
                         val floats: Boolean,
                         val noSysInit: Boolean,
                         val romable: Boolean,
                         val compTarget: ICompilationTarget,
                         val compilerVersion: String,
                         // these are set later, based on command line arguments or options in the source code:
                         var loadAddress: UInt,
                         var memtopAddress: UInt,
                         var warnSymbolShadowing: Boolean = false,
                         var warnImplicitTypeCast: Boolean = false,
                         var optimize: Boolean = false,
                         var asmQuiet: Boolean = false,
                         var asmListfile: Boolean = false,
                         var includeSourcelines: Boolean = false,
                         var dumpVariables: Boolean = false,
                         var dumpSymbols: Boolean = false,
                         var experimentalCodegen: Boolean = false,
                         var varsHighBank: Int? = null,
                         var varsGolden: Boolean = false,
                         var slabsHighBank: Int? = null,
                         var slabsGolden: Boolean = false,
                         var breakpointCpuInstruction: String? = null,
                         var ignoreFootguns: Boolean = false,
                         var outputDir: Path = Path(""),
                         var quiet: Boolean = false,
                         var profilingInstrumentation: Boolean = false,
                         var symbolDefs: Map<String, String> = emptyMap()
) {
    init {
        compTarget.initializeMemoryAreas(this)
    }

    companion object {
        val AllZeropageAllowed: List<UIntRange> = listOf(0u..255u)
    }
}
