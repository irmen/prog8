package prog8.compilerinterface

import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl


interface IVariableAllocation {
    data class ConstantNumberSymbol(val type: DataType, val name: String, val value: Double, val position: Position)
    data class MemoryMappedSymbol(val type: DataType, val name: String, val address: UInt, val position: Position)
    data class StaticBlockVariable(val type: DataType, val name: String, val initialValue: Expression?, val position: Position, val origVar: VarDecl)      // TODO should get rid of origVar altogether
    data class StaticSubroutineVariable(val type: DataType, val name: String, val position: Position, val origVar: VarDecl)      // TODO should get rid of origVar altogether
    data class ZeropageVariable(val type: DataType, val scopedname: List<String>, val position: Position)

    fun dump(memsizer: IMemSizer)

    val zeropageVars: Set<ZeropageVariable>     // also present in the Zeropage object after this allocation
    val blockVars: Map<Block, Set<StaticBlockVariable>>
    val blockConsts: Map<Block, Set<ConstantNumberSymbol>>
    val blockMemvars: Map<Block, Set<MemoryMappedSymbol>>
    val subroutineVars: Map<Subroutine, Set<StaticSubroutineVariable>>
    val subroutineConsts: Map<Subroutine, Set<ConstantNumberSymbol>>
    val subroutineMemvars: Map<Subroutine, Set<MemoryMappedSymbol>>
}
