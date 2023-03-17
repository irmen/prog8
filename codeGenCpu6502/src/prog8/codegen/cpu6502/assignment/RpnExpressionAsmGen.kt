package prog8.codegen.cpu6502.assignment

import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.code.ast.PtRpn
import prog8.codegen.cpu6502.AsmGen6502Internal

internal class RpnExpressionAsmGen(
    val program: PtProgram,
    val symbolTable: SymbolTable,
    val asmgen: AsmGen6502Internal
) {

    fun attemptAssignOptimizedExpr(assign: AsmAssignment): Boolean {
        val value = assign.source.expression as PtRpn
        println("TODO: RPN: optimized assignment ${value.position}")   // TODO RPN: optimized assignment
        // NOTE: don't forgot to evaluate the rest of the RPN expr as well
        return false
    }

}
