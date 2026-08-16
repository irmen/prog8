package prog8.code.core

interface IMemSizer {
    val FLOAT_MEM_SIZE: UInt
    val POINTER_MEM_SIZE: UInt

    fun memorySize(dt: DataType, numElements: Int?): Int

    fun memorySize(dt: BaseDataType): Int {
        if(dt.isPassByRef)
            return POINTER_MEM_SIZE.toInt() 
        try {
            return memorySize(DataType.forDt(dt), null)
        } catch (x: NoSuchElementException) {
            throw IllegalArgumentException(x.message)
        }
    }

    fun isSplitWordArray(dt: DataType): Boolean {
        return dt.isSplitWordArray(this)
    }
}
