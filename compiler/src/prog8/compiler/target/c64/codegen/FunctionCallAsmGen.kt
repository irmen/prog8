package prog8.compiler.target.c64.codegen

import prog8.ast.IFunctionCall
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.RegisterOrStatusflag
import prog8.ast.statements.Subroutine
import prog8.ast.statements.SubroutineParameter
import prog8.compiler.AssemblyError
import prog8.compiler.target.c64.codegen.assignment.*


internal class FunctionCallAsmGen(private val program: Program, private val asmgen: AsmGen) {

    internal fun translateFunctionCall(stmt: IFunctionCall) {
        // output the code to setup the parameters and perform the actual call
        // does NOT output the code to deal with the result values!
        val sub = stmt.target.targetSubroutine(program.namespace) ?: throw AssemblyError("undefined subroutine ${stmt.target}")
        val saveX = CpuRegister.X in sub.asmClobbers || sub.regXasResult() || sub.regXasParam()
        if(saveX)
            asmgen.saveRegister(CpuRegister.X)

        val subName = asmgen.asmSymbolName(stmt.target)
        if(stmt.args.isNotEmpty()) {
            if(sub.asmParameterRegisters.isEmpty()) {
                // via variables
                for(arg in sub.parameters.withIndex().zip(stmt.args)) {
                    argumentViaVariable(sub, arg.first, arg.second)
                }
            } else {
                // via registers
                if(sub.parameters.size==1) {
                    // just a single parameter, no risk of clobbering registers
                    argumentViaRegister(sub, sub.parameters.withIndex().single(), stmt.args[0])
                } else {
                    // multiple register arguments, risk of register clobbering.
                    // evaluate arguments onto the stack, and load the registers from the evaluated values on the stack.
                    when {
                        stmt.args.all {it is AddressOf ||
                                it is NumericLiteralValue ||
                                it is StringLiteralValue ||
                                it is ArrayLiteralValue ||
                                it is IdentifierReference} -> {
                            // no risk of clobbering for these simple argument types. Optimize the register loading.
                            for(arg in sub.parameters.withIndex().zip(stmt.args)) {
                                argumentViaRegister(sub, arg.first, arg.second)
                            }
                        }
                        else -> {
                            // Risk of clobbering due to complex expression args. Work via the stack.
                            registerArgsViaStackEvaluation(stmt, sub)
                        }
                    }
                }
            }
        }
        asmgen.out("  jsr  $subName")

        if(saveX)
            asmgen.restoreRegister(CpuRegister.X)
    }

    private fun registerArgsViaStackEvaluation(stmt: IFunctionCall, sub: Subroutine) {
        // this is called when one or more of the arguments are 'complex' and
        // cannot be assigned to a register easily or risk clobbering other registers.

        if(sub.parameters.isEmpty())
            return

        // 1. load all arguments reversed onto the stack: first arg goes last (is on top).
        for (arg in stmt.args.reversed())
            asmgen.translateExpression(arg)

        var argForCarry: IndexedValue<Pair<Expression, RegisterOrStatusflag>>? = null
        var argForXregister: IndexedValue<Pair<Expression, RegisterOrStatusflag>>? = null
        var argForAregister: IndexedValue<Pair<Expression, RegisterOrStatusflag>>? = null

        asmgen.out("  inx")     // align estack pointer

        for(argi in stmt.args.zip(sub.asmParameterRegisters).withIndex()) {
            when {
                argi.value.second.stack -> TODO("asmsub @stack parameter")
                argi.value.second.statusflag == Statusflag.Pc -> {
                    require(argForCarry == null)
                    argForCarry = argi
                }
                argi.value.second.statusflag != null -> throw AssemblyError("can only use Carry as status flag parameter")
                argi.value.second.registerOrPair in setOf(RegisterOrPair.X, RegisterOrPair.AX, RegisterOrPair.XY) -> {
                    require(argForXregister==null)
                    argForXregister = argi
                }
                argi.value.second.registerOrPair in setOf(RegisterOrPair.A, RegisterOrPair.AY) -> {
                    require(argForAregister == null)
                    argForAregister = argi
                }
                argi.value.second.registerOrPair == RegisterOrPair.Y -> {
                    asmgen.out("  ldy  P8ESTACK_LO+${argi.index},x")
                }
                else -> throw AssemblyError("weird argument")
            }
        }

        if(argForCarry!=null) {
            asmgen.out("""
                lda  P8ESTACK_LO+${argForCarry.index},x
                beq  +
                sec
                bcs  ++
+               clc
+               php""")             // push the status flags
        }

        if(argForAregister!=null) {
            when(argForAregister.value.second.registerOrPair) {
                RegisterOrPair.A -> asmgen.out("  lda  P8ESTACK_LO+${argForAregister.index},x")
                RegisterOrPair.AY -> asmgen.out("  lda  P8ESTACK_LO+${argForAregister.index},x |  ldy  P8ESTACK_HI+${argForAregister.index},x")
                else -> throw AssemblyError("weird arg")
            }
        }

        if(argForXregister!=null) {

            if(argForAregister!=null)
                asmgen.out("  pha")
            when(argForXregister.value.second.registerOrPair) {
                RegisterOrPair.X -> asmgen.out("  lda  P8ESTACK_LO+${argForXregister.index},x |  tax")
                RegisterOrPair.AX -> asmgen.out("  ldy  P8ESTACK_LO+${argForXregister.index},x |  lda  P8ESTACK_HI+${argForXregister.index},x |  tax |  tya")
                RegisterOrPair.XY -> asmgen.out("  ldy  P8ESTACK_HI+${argForXregister.index},x |  lda  P8ESTACK_LO+${argForXregister.index},x |  tax")
                else -> throw AssemblyError("weird arg")
            }
            if(argForAregister!=null)
                asmgen.out("  pla")
        } else {
            repeat(sub.parameters.size - 1) { asmgen.out("  inx") }       // unwind stack
        }

        if(argForCarry!=null)
            asmgen.out("  plp")       // set the carry flag back to correct value
    }

    private fun argumentViaVariable(sub: Subroutine, parameter: IndexedValue<SubroutineParameter>, value: Expression) {
        // pass parameter via a regular variable (not via registers)
        val valueIDt = value.inferType(program)
        if(!valueIDt.isKnown)
            throw AssemblyError("arg type unknown")
        val valueDt = valueIDt.typeOrElse(DataType.STRUCT)
        if(!isArgumentTypeCompatible(valueDt, parameter.value.type))
            throw AssemblyError("argument type incompatible")

        val scopedParamVar = (sub.scopedname+"."+parameter.value.name).split(".")
        val identifier = IdentifierReference(scopedParamVar, sub.position)
        identifier.linkParents(value.parent)
        val tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, program, asmgen, parameter.value.type, variable = identifier)
        val source = AsmAssignSource.fromAstSource(value, program).adjustDataTypeToTarget(tgt)
        val asgn = AsmAssignment(source, tgt, false, Position.DUMMY)
        asmgen.translateNormalAssignment(asgn)
    }

    private fun argumentViaRegister(sub: Subroutine, parameter: IndexedValue<SubroutineParameter>, value: Expression) {
        // pass argument via a register parameter
        val valueIDt = value.inferType(program)
        if(!valueIDt.isKnown)
            throw AssemblyError("arg type unknown")
        val valueDt = valueIDt.typeOrElse(DataType.STRUCT)
        if(!isArgumentTypeCompatible(valueDt, parameter.value.type))
            throw AssemblyError("argument type incompatible")

        val paramRegister = sub.asmParameterRegisters[parameter.index]
        val statusflag = paramRegister.statusflag
        val register = paramRegister.registerOrPair
        val stack = paramRegister.stack
        when {
            stack -> {
                // push arg onto the stack
                // note: argument order is reversed (first argument will be deepest on the stack)
                asmgen.translateExpression(value)
            }
            statusflag!=null -> {
                if (statusflag == Statusflag.Pc) {
                    // this param needs to be set last, right before the jsr
                    // for now, this is already enforced on the subroutine definition by the Ast Checker
                    when(value) {
                        is NumericLiteralValue -> {
                            val carrySet = value.number.toInt() != 0
                            asmgen.out(if(carrySet) "  sec" else "  clc")
                        }
                        is IdentifierReference -> {
                            val sourceName = asmgen.asmVariableName(value)
                            asmgen.out("""
            pha
            lda  $sourceName
            beq  +
            sec  
            bcs  ++
+           clc
+           pla
""")
                        }
                        else -> {
                            asmgen.translateExpression(value)
                            asmgen.out("""
            inx
            pha
            lda  P8ESTACK_LO,x
            beq  +
            sec  
            bcs  ++
+           clc
+           pla
""")
                        }
                    }
                }
                else throw AssemblyError("can only use Carry as status flag parameter")
            }
            else -> {
                // via register or register pair
                val target = AsmAssignTarget.fromRegisters(register!!, program, asmgen)
                val src = if(valueDt in PassByReferenceDatatypes) {
                    if(value is IdentifierReference) {
                        val addr = AddressOf(value, Position.DUMMY)
                        AsmAssignSource.fromAstSource(addr, program).adjustDataTypeToTarget(target)
                    } else {
                        AsmAssignSource.fromAstSource(value, program).adjustDataTypeToTarget(target)
                    }
                } else {
                    AsmAssignSource.fromAstSource(value, program).adjustDataTypeToTarget(target)
                }

                asmgen.translateNormalAssignment(AsmAssignment(src, target, false, Position.DUMMY))
            }
        }
    }

    private fun isArgumentTypeCompatible(argType: DataType, paramType: DataType): Boolean {
        if(argType isAssignableTo paramType)
            return true
        if(argType in ByteDatatypes && paramType in ByteDatatypes)
            return true
        if(argType in WordDatatypes && paramType in WordDatatypes)
            return true

        // we have a special rule for some types.
        // strings are assignable to UWORD, for example, and vice versa
        if(argType==DataType.STR && paramType==DataType.UWORD)
            return true
        if(argType==DataType.UWORD && paramType == DataType.STR)
            return true

        return false
    }
}
