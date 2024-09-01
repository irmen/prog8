package prog8.code.core

interface IMemSizer {
    fun memorySize(dt: DataTypeFull, numElements: Int?): Int
}
