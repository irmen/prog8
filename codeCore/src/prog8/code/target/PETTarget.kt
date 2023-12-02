package prog8.code.target

import prog8.code.core.Encoding
import prog8.code.core.ICompilationTarget
import prog8.code.core.IMemSizer
import prog8.code.core.IStringEncoding
import prog8.code.target.cbm.CbmMemorySizer
import prog8.code.target.pet.PETMachineDefinition


class PETTarget: ICompilationTarget, IStringEncoding by Encoder, IMemSizer by CbmMemorySizer {
    override val name = NAME
    override val machine = PETMachineDefinition()
    override val defaultEncoding = Encoding.PETSCII

    companion object {
        const val NAME = "pet32"
    }
}
