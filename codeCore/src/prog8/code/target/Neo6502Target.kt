package prog8.code.target

import prog8.code.core.*
import prog8.code.target.neo6502.Neo6502MachineDefinition


class Neo6502Target: ICompilationTarget, IStringEncoding by Encoder, IMemSizer {
    override val name = NAME
    override val machine = Neo6502MachineDefinition()
    override val defaultEncoding = Encoding.ISO

    companion object {
        const val NAME = "neo"
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
        if(arrayDt== DataType.UWORD)
            numElements    // pointer to bytes.
        else
            memorySize(ArrayToElementTypes.getValue(arrayDt)) * numElements
}
