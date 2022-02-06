package prog8.compilerinterface

import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl

/**
 * Experimental attempt for:
 * A more convenient way to pass variable (and constant values) definitions to the code generator,
 * so that it doesn't have to scavenge all VerDecl nodes in the AST for this information.
 */
interface IVariablesAndConsts {
    data class ConstantNumberSymbol(val type: DataType, val name: String, val value: Double, val position: Position)
    data class MemoryMappedVariable(val type: DataType, val name: String, val address: UInt, val position: Position)
    // TODO should get rid of origVar altogether in the following two:
    data class StaticBlockVariable(val type: DataType, val name: String, val initialValue: Expression?, val position: Position, val origVar: VarDecl)
    data class StaticSubroutineVariable(val type: DataType, val name: String, val position: Position, val origVar: VarDecl)

    fun dump(memsizer: IMemSizer)

    val blockVars: Map<Block, Set<StaticBlockVariable>>
    val blockConsts: Map<Block, Set<ConstantNumberSymbol>>
    val blockMemvars: Map<Block, Set<MemoryMappedVariable>>
    val subroutineVars: Map<Subroutine, Set<StaticSubroutineVariable>>
    val subroutineConsts: Map<Subroutine, Set<ConstantNumberSymbol>>
    val subroutineMemvars: Map<Subroutine, Set<MemoryMappedVariable>>
}
