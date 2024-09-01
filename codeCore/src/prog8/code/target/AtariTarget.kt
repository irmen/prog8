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

    override fun memorySize(dt: DataTypeFull, numElements: Int?): Int {
        if(dt.isArray || dt.isSplitWordArray) {
            require(numElements!=null)
            return when(dt.sub) {
                SubBool, SubSignedByte, SubUnsignedByte -> numElements
                SubSignedWord, SubUnsignedWord -> numElements * 2
                SubFloat -> numElements * machine.FLOAT_MEM_SIZE
                null -> throw IllegalArgumentException("invalid sub type")
            }
        }
        require(numElements==null)
        return when {
            dt.isByteOrBool -> 1
            dt.isFloat -> machine.FLOAT_MEM_SIZE
            else -> 2
        }
    }
}
