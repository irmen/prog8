package prog8.code.core

interface IMemSizer {
    fun memorySize(dt: DataType, numElements: Int?): Int

    fun memorySize(dt: BaseDataType): Int {
        if(dt.isPassByRef)
            return memorySize(DataType.forDt(BaseDataType.UWORD), null)      // a pointer size
        try {
            return memorySize(DataType.forDt(dt), null)
        } catch (x: NoSuchElementException) {
            throw IllegalArgumentException(x.message)
        }
    }
}
