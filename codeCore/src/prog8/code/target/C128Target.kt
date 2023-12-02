package prog8.code.target

import prog8.code.core.Encoding
import prog8.code.core.ICompilationTarget
import prog8.code.core.IMemSizer
import prog8.code.core.IStringEncoding
import prog8.code.target.c128.C128MachineDefinition
import prog8.code.target.cbm.CbmMemorySizer


class C128Target: ICompilationTarget, IStringEncoding by Encoder, IMemSizer by CbmMemorySizer {
    override val name = NAME
    override val machine = C128MachineDefinition()
    override val defaultEncoding = Encoding.PETSCII

    companion object {
        const val NAME = "c128"
    }
}
