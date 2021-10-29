package prog8.compilerinterface

import prog8.ast.base.DataType


// note: this is a separate interface in the compilerAst module because
// otherwise a cyclic dependency with the compilerInterfaces module would be needed.

interface IMemSizer {
    fun memorySize(dt: DataType): Int
}
