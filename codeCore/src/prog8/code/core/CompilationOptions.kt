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
                         var warnSymbolShadowing: Boolean = false,
                         var optimize: Boolean = false,
                         var asmQuiet: Boolean = false,
                         var asmListfile: Boolean = false,
                         var includeSourcelines: Boolean = false,
                         var experimentalCodegen: Boolean = false,
                         var varsHighBank: Int? = null,
                         var splitWordArrays: Boolean = false,
                         var outputDir: Path = Path(""),
                         var symbolDefs: Map<String, String> = emptyMap()
) {
    init {
        compTarget.machine.initializeMemoryAreas(this)
    }
}
