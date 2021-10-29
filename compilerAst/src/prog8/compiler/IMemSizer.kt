package prog8.compiler

import prog8.ast.base.DataType

interface IMemSizer {
    fun memorySize(dt: DataType): Int
}
