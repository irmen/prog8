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

        fun builder(compTarget: ICompilationTarget) = Builder(compTarget)

        class Builder(private val compTarget: ICompilationTarget) {
            private var output: OutputType = compTarget.defaultOutputType
            private var launcher: CbmPrgLauncherType = CbmPrgLauncherType.NONE
            private var zeropage: ZeropageType = ZeropageType.DONTUSE
            private var zpReserved: List<UIntRange> = emptyList()
            private var zpAllowed: List<UIntRange> = AllZeropageAllowed
            private var floats: Boolean = false
            private var noSysInit: Boolean = false
            private var romable: Boolean = false
            private var compilerVersion: String = "unknown"
            private var loadAddress: UInt = compTarget.PROGRAM_LOAD_ADDRESS
            private var memtopAddress: UInt = compTarget.PROGRAM_MEMTOP_ADDRESS
            private var warnSymbolShadowing: Boolean = false
            private var warnImplicitTypeCast: Boolean = false
            private var optimize: Boolean = false
            private var asmQuiet: Boolean = false
            private var asmListfile: Boolean = false
            private var includeSourcelines: Boolean = false
            private var dumpVariables: Boolean = false
            private var dumpSymbols: Boolean = false
            private var experimentalCodegen: Boolean = false
            private var varsHighBank: Int? = null
            private var varsGolden: Boolean = false
            private var slabsHighBank: Int? = null
            private var slabsGolden: Boolean = false
            private var breakpointCpuInstruction: String? = null
            private var ignoreFootguns: Boolean = false
            private var outputDir: Path = Path("")
            private var quiet: Boolean = false
            private var profilingInstrumentation: Boolean = false
            private var symbolDefs: Map<String, String> = emptyMap()

            fun output(output: OutputType) = apply { this.output = output }
            fun launcher(launcher: CbmPrgLauncherType) = apply { this.launcher = launcher }
            fun zeropage(zeropage: ZeropageType) = apply { this.zeropage = zeropage }
            fun zpReserved(zpReserved: List<UIntRange>) = apply { this.zpReserved = zpReserved }
            fun zpAllowed(zpAllowed: List<UIntRange>) = apply { this.zpAllowed = zpAllowed }
            fun floats(floats: Boolean) = apply { this.floats = floats }
            fun noSysInit(noSysInit: Boolean) = apply { this.noSysInit = noSysInit }
            fun romable(romable: Boolean) = apply { this.romable = romable }
            fun compilerVersion(compilerVersion: String) = apply { this.compilerVersion = compilerVersion }
            fun loadAddress(loadAddress: UInt) = apply { this.loadAddress = loadAddress }
            fun memtopAddress(memtopAddress: UInt) = apply { this.memtopAddress = memtopAddress }
            fun warnSymbolShadowing(warnSymbolShadowing: Boolean) = apply { this.warnSymbolShadowing = warnSymbolShadowing }
            fun warnImplicitTypeCast(warnImplicitTypeCast: Boolean) = apply { this.warnImplicitTypeCast = warnImplicitTypeCast }
            fun optimize(optimize: Boolean) = apply { this.optimize = optimize }
            fun asmQuiet(asmQuiet: Boolean) = apply { this.asmQuiet = asmQuiet }
            fun asmListfile(asmListfile: Boolean) = apply { this.asmListfile = asmListfile }
            fun includeSourcelines(includeSourcelines: Boolean) = apply { this.includeSourcelines = includeSourcelines }
            fun dumpVariables(dumpVariables: Boolean) = apply { this.dumpVariables = dumpVariables }
            fun dumpSymbols(dumpSymbols: Boolean) = apply { this.dumpSymbols = dumpSymbols }
            fun experimentalCodegen(experimentalCodegen: Boolean) = apply { this.experimentalCodegen = experimentalCodegen }
            fun varsHighBank(varsHighBank: Int?) = apply { this.varsHighBank = varsHighBank }
            fun varsGolden(varsGolden: Boolean) = apply { this.varsGolden = varsGolden }
            fun slabsHighBank(slabsHighBank: Int?) = apply { this.slabsHighBank = slabsHighBank }
            fun slabsGolden(slabsGolden: Boolean) = apply { this.slabsGolden = slabsGolden }
            fun breakpointCpuInstruction(breakpointCpuInstruction: String?) = apply { this.breakpointCpuInstruction = breakpointCpuInstruction }
            fun ignoreFootguns(ignoreFootguns: Boolean) = apply { this.ignoreFootguns = ignoreFootguns }
            fun outputDir(outputDir: Path) = apply { this.outputDir = outputDir }
            fun quiet(quiet: Boolean) = apply { this.quiet = quiet }
            fun profilingInstrumentation(profilingInstrumentation: Boolean) = apply { this.profilingInstrumentation = profilingInstrumentation }
            fun symbolDefs(symbolDefs: Map<String, String>) = apply { this.symbolDefs = symbolDefs }

            fun build(): CompilationOptions {
                return CompilationOptions(
                    output, launcher, zeropage, zpReserved, zpAllowed, floats, noSysInit, romable, compTarget, compilerVersion,
                    loadAddress, memtopAddress, warnSymbolShadowing, warnImplicitTypeCast, optimize, asmQuiet, asmListfile,
                    includeSourcelines, dumpVariables, dumpSymbols, experimentalCodegen, varsHighBank, varsGolden,
                    slabsHighBank, slabsGolden, breakpointCpuInstruction, ignoreFootguns, outputDir, quiet,
                    profilingInstrumentation, symbolDefs
                )
            }
        }
    }
}
