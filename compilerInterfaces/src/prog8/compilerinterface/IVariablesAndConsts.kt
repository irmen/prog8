package prog8.compilerinterface

import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.ast.statements.ZeropageWish

/**
 * A more convenient way to pass variable (and constant values) definitions to the code generator,
 * so that it doesn't have to scavenge and manipulate the VerDecl nodes in the AST for this information.
 *
 * note: the string variables are in here as well, they're in blockVars for the block named 'prog8_interned_strings'.
 */

// TODO remove this, and replace with SymbolTable

interface IVariablesAndConsts {
    data class ConstantNumberSymbol(val type: DataType, val scopedname: List<String>, val value: Double, val position: Position)
    data class MemoryMappedVariable(val type: DataType, val scopedname: List<String>, val address: UInt, val position: Position)
    data class StaticVariable(val type: DataType,
                              val scopedname: List<String>,
                              val initialValue: Expression?,
                              val arraysize: Int?,
                              val zp: ZeropageWish,
                              val position: Position)

    val blockVars: Map<Block, Set<StaticVariable>>
    val blockConsts: Map<Block, Set<ConstantNumberSymbol>>
    val blockMemvars: Map<Block, Set<MemoryMappedVariable>>
    val subroutineVars: Map<Subroutine, Set<StaticVariable>>
    val subroutineConsts: Map<Subroutine, Set<ConstantNumberSymbol>>
    val subroutineMemvars: Map<Subroutine, Set<MemoryMappedVariable>>
}
