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
            if(numElements==null) return 2      // treat it as a pointer size
            return when(dt.sub) {
                BaseDataType.BOOL, BaseDataType.UBYTE, BaseDataType.BYTE -> numElements
                BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.STR -> numElements * 2
                BaseDataType.FLOAT-> numElements * machine.FLOAT_MEM_SIZE
                BaseDataType.UNDEFINED -> throw IllegalArgumentException("undefined has no memory size")
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        else if (dt.isString) {
            if(numElements!=null) return numElements        // treat it as the size of the given string with the length
            else return 2    // treat it as the size to store a string pointer
        }

        return when {
            dt.isByteOrBool -> 1 * (numElements ?: 1)
            dt.isFloat -> machine.FLOAT_MEM_SIZE * (numElements ?: 1)
            dt.isLong -> throw IllegalArgumentException("long can not yet be put into memory")
            dt.isUndefined -> throw IllegalArgumentException("undefined has no memory size")
            else -> 2 * (numElements ?: 1)
        }
    }
}
