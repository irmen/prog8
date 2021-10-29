package prog8tests.helpers

import prog8.ast.base.DataType
import prog8.compiler.IMemSizer

val DummyMemsizer = object : IMemSizer {
    override fun memorySize(dt: DataType): Int = 0
}
