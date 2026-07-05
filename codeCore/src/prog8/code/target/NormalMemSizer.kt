package prog8.code.target

import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.IMemSizer

internal class NormalMemSizer(val floatsize: Int, val pointerSize: Int = 2): IMemSizer {

    override fun memorySize(dt: DataType, numElements: Int?): Int {
        if(dt.isPointerArray)
            return pointerSize * numElements!!        // array of pointers
        else if(dt.isArray) {
            if(numElements==null) return pointerSize      // treat it as a pointer size
            return when(dt.sub) {
                BaseDataType.BOOL, BaseDataType.UBYTE, BaseDataType.BYTE -> numElements
                BaseDataType.UWORD, BaseDataType.WORD  -> numElements * 2
                BaseDataType.STR, BaseDataType.POINTER -> numElements * pointerSize
                BaseDataType.LONG -> numElements * 4
                BaseDataType.FLOAT-> numElements * floatsize
                BaseDataType.UNDEFINED -> throw IllegalArgumentException("undefined has no memory size")
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        else if (dt.isString) {
            return numElements        // treat it as the size of the given string with the length
                ?: pointerSize    // treat it as the size to store a string pointer
        }

        return when {
            dt.isByteOrBool -> 1 * (numElements ?: 1)
            dt.isFloat -> floatsize * (numElements ?: 1)
            dt.isLong -> 4 * (numElements ?: 1)
            dt.isWord -> 2 * (numElements ?: 1)
            dt.isPointer -> pointerSize * (numElements ?: 1)
            dt.isStructInstance -> dt.subType!!.memsize(this)
            dt.isUndefined -> throw IllegalArgumentException("undefined has no memory size")
            else -> throw IllegalArgumentException("invalid dt $dt")
        }
    }

}
