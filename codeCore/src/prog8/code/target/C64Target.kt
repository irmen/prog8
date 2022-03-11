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
    override val supportedEncodings = setOf(Encoding.PETSCII, Encoding.SCREENCODES)
    override val defaultEncoding = Encoding.PETSCII

    companion object {
        const val NAME = "c64"
    }
}
