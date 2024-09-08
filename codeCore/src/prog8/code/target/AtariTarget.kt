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

    override fun memorySize(dt: DataType, numElements: Int?): Int {
        if(dt.isArray) {
            require(numElements!=null)
            return when(dt.sub?.dt) {
                BaseDataType.BOOL, BaseDataType.UBYTE, BaseDataType.BYTE -> numElements
                BaseDataType.UWORD, BaseDataType.WORD -> numElements * 2
                BaseDataType.FLOAT-> numElements * machine.FLOAT_MEM_SIZE
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }

        return when {
            dt.isByteOrBool -> 1 * (numElements ?: 1)
            dt.isFloat -> machine.FLOAT_MEM_SIZE * (numElements ?: 1)
            else -> 2 * (numElements ?: 1)
        }
    }

    override fun memorySize(dt: SubType): Int {
        return memorySize(DataType.forDt(dt.dt), null)
    }
}
