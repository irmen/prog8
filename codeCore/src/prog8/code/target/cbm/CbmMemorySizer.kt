package prog8.code.target.cbm

import prog8.code.core.*


internal object CbmMemorySizer: IMemSizer {
    override fun memorySize(dt: DataType): Int {
        return when(dt) {
            in ByteDatatypes, DataType.BOOL -> 1
            in WordDatatypes, in PassByReferenceDatatypes -> 2
            DataType.FLOAT -> Mflpt5.FLOAT_MEM_SIZE
            else -> throw IllegalArgumentException("invalid datatype")
        }
    }

    override fun memorySize(arrayDt: DataType, numElements: Int) =
        memorySize(ArrayToElementTypes.getValue(arrayDt)) * numElements
}