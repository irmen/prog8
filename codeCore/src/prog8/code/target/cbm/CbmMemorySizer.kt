package prog8.code.target.cbm

import prog8.code.core.*


internal object CbmMemorySizer: IMemSizer {
    override fun memorySize(dt: DataTypeFull, numElements: Int?): Int {
        if(dt.isArray || dt.isSplitWordArray) {
            require(numElements!=null)
            return when(dt.sub) {
                SubBool, SubSignedByte, SubUnsignedByte -> numElements
                SubSignedWord, SubUnsignedWord -> numElements * 2
                SubFloat -> numElements * Mflpt5.FLOAT_MEM_SIZE
                null -> throw IllegalArgumentException("invalid sub type")
            }
        }
        require(numElements==null)
        return when {
            dt.isByteOrBool -> 1
            dt.isFloat -> Mflpt5.FLOAT_MEM_SIZE
            else -> 2
        }
    }

    override fun memorySize(dt: SubType): Int {
        return memorySize(DataTypeFull.forDt(dt.dt), null)
    }
}