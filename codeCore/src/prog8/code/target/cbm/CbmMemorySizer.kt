package prog8.code.target.cbm

import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.IMemSizer
import prog8.code.core.SubType


internal object CbmMemorySizer: IMemSizer {
    override fun memorySize(dt: DataType, numElements: Int?): Int {
        if(dt.isArray) {
            require(numElements!=null)
            return when(dt.sub?.dt) {
                BaseDataType.BOOL, BaseDataType.UBYTE, BaseDataType.BYTE -> numElements
                BaseDataType.UWORD, BaseDataType.WORD -> numElements * 2
                BaseDataType.FLOAT-> numElements * Mflpt5.FLOAT_MEM_SIZE
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        return when {
            dt.isByteOrBool -> 1 * (numElements ?: 1)
            dt.isFloat -> Mflpt5.FLOAT_MEM_SIZE * (numElements ?: 1)
            else -> 2 * (numElements ?: 1)
        }
    }

    override fun memorySize(dt: SubType): Int {
        return memorySize(DataType.forDt(dt.dt), null)
    }
}