package prog8tests.helpers

import prog8.ast.IMemSizer
import prog8.ast.base.DataType

val DummyMemsizer = object : IMemSizer {
    override fun memorySize(dt: DataType): Int = 0
}