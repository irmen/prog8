package prog8.code.core

interface IMemSizer {
    fun memorySize(dt: DataType): Int
    fun memorySize(arrayDt: DataType, numElements: Int): Int
}
