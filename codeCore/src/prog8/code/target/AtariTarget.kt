package prog8.code.target

import prog8.code.core.*
import prog8.code.target.atari.AtariMachineDefinition


class AtariTarget: ICompilationTarget, IStringEncoding by Encoder, IMemSizer {
    override val name = NAME
    override val machine = AtariMachineDefinition()
    override val defaultEncoding = Encoding.ATASCII

    companion object {
        const val NAME = "atari"
    }

    override fun memorySize(dt: DataType): Int {
        return when(dt) {
            in ByteDatatypesWithBoolean -> 1
            in WordDatatypes, in PassByReferenceDatatypes -> 2
            DataType.FLOAT -> machine.FLOAT_MEM_SIZE
            else -> throw IllegalArgumentException("invalid datatype")
        }
    }

    override fun memorySize(arrayDt: DataType, numElements: Int) =
        memorySize(ArrayToElementTypes.getValue(arrayDt)) * numElements
}
