package prog8.compilerinterface

import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl

interface IVariableAllocation {
    fun dump(memsizer: IMemSizer)

    val blockVars: Map<Block, Set<VarDecl>>
    val subroutineVars: Map<Subroutine, Set<VarDecl>>
}
