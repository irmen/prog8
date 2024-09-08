package prog8.code.core

interface IMemSizer {
    fun memorySize(dt: DataType, numElements: Int?): Int
    fun memorySize(dt: SubType): Int
}
