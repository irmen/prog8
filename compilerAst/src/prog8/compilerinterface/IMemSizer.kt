package prog8.compilerinterface

import prog8.ast.base.DataType
import prog8.ast.statements.VarDecl


// note: this is a separate interface in the compilerAst module because
// otherwise a cyclic dependency with the compilerInterfaces module would be needed.

interface IMemSizer {
    fun memorySize(dt: DataType): Int
    fun memorySize(decl: VarDecl): Int
}
