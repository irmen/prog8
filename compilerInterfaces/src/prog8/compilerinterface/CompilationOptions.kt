package prog8.compilerinterface

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
                              val noSysInit: Boolean,
                              val compTarget: ICompilationTarget
) {
    var slowCodegenWarnings = false
    var optimize = false
    var optimizeFloatExpressions = false
}
