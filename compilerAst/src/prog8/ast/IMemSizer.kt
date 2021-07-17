package prog8.ast

import prog8.ast.base.DataType

interface IMemSizer {
    fun memorySize(dt: DataType): Int
}