package prog8.codegen.cpu6502.assignment

import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.AssemblyError
import prog8.code.core.CpuRegister
import prog8.code.core.DataType
import prog8.code.core.RegisterOrPair
import prog8.codegen.cpu6502.AsmGen6502Internal

internal class RpnExpressionAsmGen(
    val program: PtProgram,
    val symbolTable: SymbolTable,
    val assignmentAsmGen: AssignmentAsmGen,
    val asmgen: AsmGen6502Internal
) {

    internal fun tryOptimizedPointerAccessWithA(expr: PtRpn, write: Boolean): Boolean {
        // optimize pointer,indexregister if possible

        fun evalBytevalueWillClobberA(expr: PtExpression): Boolean {
            val dt = expr.type
            if(dt != DataType.UBYTE && dt != DataType.BYTE)
                return true
            return when(expr) {
                is PtIdentifier -> false
                is PtNumber -> false
                is PtMemoryByte -> expr.address !is PtIdentifier && expr.address !is PtNumber
                is PtTypeCast -> evalBytevalueWillClobberA(expr.value)
                else -> true
            }
        }

        if(expr.finalOperator().operator=="+") {
            val ptrAndIndex = asmgen.pointerViaIndexRegisterPossible(expr)
            if(ptrAndIndex!=null) {
                val pointervar = ptrAndIndex.first as? PtIdentifier
                val target = if(pointervar==null) null else symbolTable.lookup(pointervar.name)!!.astNode
                when(target) {
                    is PtLabel -> {
                        asmgen.assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                        asmgen.out("  lda  ${asmgen.asmSymbolName(pointervar!!)},y")
                        return true
                    }
                    is IPtVariable, null -> {
                        if(write) {
                            if(pointervar!=null && asmgen.isZpVar(pointervar)) {
                                val saveA = evalBytevalueWillClobberA(ptrAndIndex.second)
                                if(saveA)
                                    asmgen.out("  pha")
                                asmgen.assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                if(saveA)
                                    asmgen.out("  pla")
                                asmgen.out("  sta  (${asmgen.asmSymbolName(pointervar)}),y")
                            } else {
                                // copy the pointer var to zp first
                                val saveA = evalBytevalueWillClobberA(ptrAndIndex.first) || evalBytevalueWillClobberA(ptrAndIndex.second)
                                if(saveA)
                                    asmgen.out("  pha")
                                if(ptrAndIndex.second.isSimple()) {
                                    asmgen. assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
                                    asmgen.assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                    if(saveA)
                                        asmgen.out("  pla")
                                    asmgen.out("  sta  (P8ZP_SCRATCH_W2),y")
                                } else {
                                    asmgen.pushCpuStack(DataType.UBYTE,  ptrAndIndex.second)
                                    asmgen.assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
                                    asmgen.restoreRegisterStack(CpuRegister.Y, true)
                                    if(saveA)
                                        asmgen.out("  pla")
                                    asmgen.out("  sta  (P8ZP_SCRATCH_W2),y")
                                }
                            }
                        } else {
                            if(pointervar!=null && asmgen.isZpVar(pointervar)) {
                                asmgen.assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                asmgen.out("  lda  (${asmgen.asmSymbolName(pointervar)}),y")
                            } else {
                                // copy the pointer var to zp first
                                if(ptrAndIndex.second.isSimple()) {
                                    asmgen.assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
                                    asmgen.assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                    asmgen.out("  lda  (P8ZP_SCRATCH_W2),y")
                                } else {
                                    asmgen.pushCpuStack(DataType.UBYTE, ptrAndIndex.second)
                                    asmgen.assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
                                    asmgen.restoreRegisterStack(CpuRegister.Y, false)
                                    asmgen.out("  lda  (P8ZP_SCRATCH_W2),y")
                                }
                            }
                        }
                        return true
                    }
                    else -> throw AssemblyError("invalid pointervar $pointervar")
                }
            }
        }
        return false
    }

    fun attemptAssignOptimizedExpr(expr: PtRpn, assign: AsmAssignment): Boolean {
        println("TODO: RPN: optimized assignment ${expr.position}")   // TODO RPN: optimized assignment
        return false
    }

    fun funcPeekW(
        fcall: PtBuiltinFunctionCall,
        resultToStack: Boolean,
        resultRegister: RegisterOrPair?
    ) {
        println("TODO: RPN: peekw optimized pointer+index ${fcall.position}")   // TODO RPN: peekw optimized pointer+index
        // val (left, oper, right) = addrExpr.finalOperation()
        asmgen.assignExpressionToRegister(fcall.args[0], RegisterOrPair.AY)
        asmgen.out("  jsr  prog8_lib.func_peekw")
    }

    fun funcPokeW(fcall: PtBuiltinFunctionCall): Boolean {
        println("TODO: RPN: pokew optimized pointer+index ${fcall.position}")   // TODO RPN: pokew optimized pointer+index
        // val (left, oper, right) = addrExpr.finalOperation()
        // for now: fall through
        return false
    }

    fun pointerViaIndexRegisterPossible(pointerOffsetExpr: PtRpn): Pair<PtExpression, PtExpression>? {
        TODO("RPN determine pointer+index via reg.")   // however, is this ever getting called from RPN code?
    }

}
