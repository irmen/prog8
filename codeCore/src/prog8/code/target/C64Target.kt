package prog8.code.target

import prog8.code.core.Encoding
import prog8.code.core.ICompilationTarget
import prog8.code.core.IMemSizer
import prog8.code.core.IStringEncoding
import prog8.code.target.c64.C64MachineDefinition
import prog8.code.target.cbm.CbmMemorySizer


class C64Target: ICompilationTarget, IStringEncoding by Encoder, IMemSizer by CbmMemorySizer {
    override val name = NAME
    override val machine = C64MachineDefinition()
    override val defaultEncoding = Encoding.PETSCII

    companion object {
        const val NAME = "c64"

        fun viceMonListName(baseFilename: String) = "$baseFilename.vice-mon-list"
    }
}


val CompilationTargets = listOf(
    C64Target.NAME,
    C128Target.NAME,
    Cx16Target.NAME,
    PETTarget.NAME,
    AtariTarget.NAME,
    Neo6502Target.NAME,
    VMTarget.NAME
)

fun getCompilationTargetByName(name: String) = when(name.lowercase()) {
    C64Target.NAME -> C64Target()
    C128Target.NAME -> C128Target()
    Cx16Target.NAME -> Cx16Target()
    PETTarget.NAME -> PETTarget()
    AtariTarget.NAME -> AtariTarget()
    VMTarget.NAME -> VMTarget()
    Neo6502Target.NAME -> Neo6502Target()
    else -> throw IllegalArgumentException("invalid compilation target")
}
