package prog8.code.target

import prog8.code.core.*
import prog8.code.target.virtual.VirtualMachineDefinition

class VMTarget: ICompilationTarget, IStringEncoding by Encoder, IMemSizer {
    override val name = NAME
    override val machine = VirtualMachineDefinition()
    override val defaultEncoding = Encoding.ISO

    companion object {
        const val NAME = "virtual"
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

    override fun memorySize(dt: SubType): Int {
        return memorySize(DataTypeFull.forDt(dt.dt), null)
    }

}