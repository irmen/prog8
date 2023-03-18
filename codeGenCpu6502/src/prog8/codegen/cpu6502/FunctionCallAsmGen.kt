package prog8.codegen.cpu6502

import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.assignment.AsmAssignSource
import prog8.codegen.cpu6502.assignment.AsmAssignTarget
import prog8.codegen.cpu6502.assignment.AsmAssignment
import prog8.codegen.cpu6502.assignment.TargetStorageKind


internal class FunctionCallAsmGen(private val program: PtProgram, private val asmgen: AsmGen6502Internal) {

    internal fun translateFunctionCallStatement(stmt: PtFunctionCall) {
        saveXbeforeCall(stmt)
        translateFunctionCall(stmt)
        restoreXafterCall(stmt)
        // just ignore any result values from the function call.
    }

    internal fun saveXbeforeCall(stmt: PtFunctionCall) {
        val symbol = asmgen.symbolTable.lookup(stmt.name)
        val sub = symbol!!.astNode as IPtSubroutine
        if(sub.shouldSaveX()) {
            if(sub is PtAsmSub) {
                val regSaveOnStack = sub.address == null       // rom-routines don't require registers to be saved on stack, normal subroutines do because they can contain nested calls
                if (regSaveOnStack)
                    asmgen.saveRegisterStack(CpuRegister.X, sub.shouldKeepA().saveOnEntry)
                else
                    asmgen.saveRegisterLocal(CpuRegister.X, stmt.definingISub()!!)
            } else
                asmgen.saveRegisterLocal(CpuRegister.X, stmt.definingISub()!!)
        }
    }

    internal fun restoreXafterCall(stmt: PtFunctionCall) {
        val symbol = asmgen.symbolTable.lookup(stmt.name)
        val sub = symbol!!.astNode as IPtSubroutine
        if(sub.shouldSaveX()) {
            if(sub is PtAsmSub) {
                val regSaveOnStack = sub.address == null       // rom-routines don't require registers to be saved on stack, normal subroutines do because they can contain nested calls
                if (regSaveOnStack)
                    asmgen.restoreRegisterStack(CpuRegister.X, sub.shouldKeepA().saveOnReturn)
                else
                    asmgen.restoreRegisterLocal(CpuRegister.X)
            } else
                asmgen.restoreRegisterLocal(CpuRegister.X)
        }
    }

    internal fun optimizeIntArgsViaRegisters(sub: PtSub) =
        (sub.parameters.size==1 && sub.parameters[0].type in IntegerDatatypes)
                || (sub.parameters.size==2 && sub.parameters[0].type in ByteDatatypes && sub.parameters[1].type in ByteDatatypes)

    internal fun translateFunctionCall(call: PtFunctionCall) {
        // Output only the code to set up the parameters and perform the actual call
        // NOTE: does NOT output the code to deal with the result values!
        // NOTE: does NOT output code to save/restore the X register for this call! Every caller should deal with this in their own way!!
        //       (you can use subroutine.shouldSaveX() and saveX()/restoreX() routines as a help for this)

        val symbol = asmgen.symbolTable.lookup(call.name)
        val sub = symbol!!.astNode as IPtSubroutine
        val subAsmName = asmgen.asmSymbolName(call.name)

        if(sub is PtAsmSub) {
            argumentsViaRegisters(sub, call)
            if (sub.inline && asmgen.options.optimize) {
                // inline the subroutine.
                // we do this by copying the subroutine's statements at the call site.
                // NOTE: *if* there is a return statement, it will be the only one, and the very last statement of the subroutine
                // (this condition has been enforced by an ast check earlier)
                asmgen.out("  \t; inlined routine follows: ${sub.name}")
                sub.children.forEach { asmgen.translate(it as PtInlineAssembly) }
                asmgen.out("  \t; inlined routine end: ${sub.name}")
            } else {
                asmgen.out("  jsr  $subAsmName")
            }
        }
        else if(sub is PtSub) {
            if(optimizeIntArgsViaRegisters(sub)) {
                if(sub.parameters.size==1) {
                    val register = if (sub.parameters[0].type in ByteDatatypes) RegisterOrPair.A else RegisterOrPair.AY
                    argumentViaRegister(sub, IndexedValue(0, sub.parameters[0]), call.args[0], register)
                } else {
                    // 2 byte params, second in Y, first in A
                    argumentViaRegister(sub, IndexedValue(0, sub.parameters[0]), call.args[0], RegisterOrPair.A)
                    if(asmgen.needAsaveForExpr(call.args[1]))
                        asmgen.out("  pha")
                    argumentViaRegister(sub, IndexedValue(1, sub.parameters[1]), call.args[1], RegisterOrPair.Y)
                    if(asmgen.needAsaveForExpr(call.args[1]))
                        asmgen.out("  pla")
                }
            } else {
                // arguments via variables
                for(arg in sub.parameters.withIndex().zip(call.args))
                    argumentViaVariable(sub, arg.first.value, arg.second)
            }
            asmgen.out("  jsr  $subAsmName")
        }
        else throw AssemblyError("invalid sub type")

        // remember: dealing with the X register and/or dealing with return values is the responsibility of the caller
    }

    private fun argumentsViaRegisters(sub: PtAsmSub, call: PtFunctionCall) {
        if(sub.parameters.size==1) {
            argumentViaRegister(sub, IndexedValue(0, sub.parameters.single().second), call.args[0])
        } else {
            if(asmsub6502ArgsHaveRegisterClobberRisk(call.args, sub.parameters)) {
                registerArgsViaCpuStackEvaluation(call, sub)
            } else {
                asmsub6502ArgsEvalOrder(sub).forEach {
                    val param = sub.parameters[it]
                    val arg = call.args[it]
                    argumentViaRegister(sub, IndexedValue(it, param.second), arg)
                }
            }
        }
    }

    private fun registerArgsViaCpuStackEvaluation(call: PtFunctionCall, callee: PtAsmSub) {
        // this is called when one or more of the arguments are 'complex' and
        // cannot be assigned to a register easily or risk clobbering other registers.

        if(callee.parameters.isEmpty())
            return

        // use the cpu hardware stack as intermediate storage for the arguments.
        val argOrder = asmsub6502ArgsEvalOrder(callee)
        argOrder.reversed().forEach {
            asmgen.pushCpuStack(callee.parameters[it].second.type, call.args[it])
        }
        argOrder.forEach {
            val param = callee.parameters[it]
            asmgen.popCpuStack(callee, param.second, param.first)
        }
    }

    private fun argumentViaVariable(sub: PtSub, parameter: PtSubroutineParameter, value: PtExpression) {
        // pass parameter via a regular variable (not via registers)
        if(!isArgumentTypeCompatible(value.type, parameter.type))
            throw AssemblyError("argument type incompatible")

        val varName = asmgen.asmVariableName(sub.scopedName + "." + parameter.name)
        asmgen.assignExpressionToVariable(value, varName, parameter.type)
    }

    private fun argumentViaRegister(sub: IPtSubroutine, parameter: IndexedValue<PtSubroutineParameter>, value: PtExpression, registerOverride: RegisterOrPair? = null) {
        // pass argument via a register parameter
        if(!isArgumentTypeCompatible(value.type, parameter.value.type))
            throw AssemblyError("argument type incompatible")

        val paramRegister: RegisterOrStatusflag = when(sub) {
            is PtAsmSub -> if(registerOverride==null) sub.parameters[parameter.index].first else RegisterOrStatusflag(registerOverride, null)
            is PtSub -> RegisterOrStatusflag(registerOverride!!, null)
        }
        val statusflag = paramRegister.statusflag
        val register = paramRegister.registerOrPair
        val requiredDt = parameter.value.type
        if(requiredDt!=value.type) {
            if(value.type largerThan requiredDt)
                throw AssemblyError("can only convert byte values to word param types")
        }
        if (statusflag!=null) {
            if(requiredDt!=value.type)
                throw AssemblyError("for statusflag, byte value is required")
            if (statusflag == Statusflag.Pc) {
                // this param needs to be set last, right before the jsr
                // for now, this is already enforced on the subroutine definition by the Ast Checker
                when(value) {
                    is PtNumber -> {
                        val carrySet = value.number.toInt() != 0
                        asmgen.out(if(carrySet) "  sec" else "  clc")
                    }
                    is PtIdentifier -> {
                        val sourceName = asmgen.asmVariableName(value)
                        asmgen.out("""
                            pha
                            clc
                            lda  $sourceName
                            beq  +
                            sec  
+                           pla""")
                    }
                    else -> {
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.out("""
                            beq  +
                            sec
                            bcs  ++
+                           clc
+""")
                    }
                }
            } else throw AssemblyError("can only use Carry as status flag parameter")
        }
        else {
            // via register or register pair
            register!!
            if(requiredDt largerThan value.type) {
                // we need to sign extend the source, do this via temporary word variable
                asmgen.assignExpressionToVariable(value, "P8ZP_SCRATCH_W1", DataType.UBYTE)
                asmgen.signExtendVariableLsb("P8ZP_SCRATCH_W1", value.type)
                asmgen.assignVariableToRegister("P8ZP_SCRATCH_W1", register, null, Position.DUMMY)
            } else {
                val target: AsmAssignTarget =
                    if(parameter.value.type in ByteDatatypes && (register==RegisterOrPair.AX || register == RegisterOrPair.AY || register==RegisterOrPair.XY || register in Cx16VirtualRegisters))
                        AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, parameter.value.type, sub, value.position, register = register)
                    else {
                        val signed = parameter.value.type == DataType.BYTE || parameter.value.type == DataType.WORD
                        AsmAssignTarget.fromRegisters(register, signed, value.position, sub, asmgen)
                    }
                val src = if(value.type in PassByReferenceDatatypes) {
                    if(value is PtIdentifier) {
                        val addr = PtAddressOf(Position.DUMMY)
                        addr.add(value)
                        addr.parent = sub as PtNode
                        AsmAssignSource.fromAstSource(addr, program, asmgen).adjustSignedUnsigned(target)
                    } else {
                        AsmAssignSource.fromAstSource(value, program, asmgen).adjustSignedUnsigned(target)
                    }
                } else {
                    AsmAssignSource.fromAstSource(value, program, asmgen).adjustSignedUnsigned(target)
                }
                asmgen.translateNormalAssignment(AsmAssignment(src, target, program.memsizer, Position.DUMMY), sub)
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
