package prog8.codegen.cpu6502

import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.assignment.AsmAssignSource
import prog8.codegen.cpu6502.assignment.AsmAssignTarget
import prog8.codegen.cpu6502.assignment.AsmAssignment
import prog8.codegen.cpu6502.assignment.TargetStorageKind


internal class FunctionCallAsmGen(private val program: PtProgram, private val asmgen: AsmGen6502Internal) {

    internal fun translateFunctionCallStatement(stmt: PtFunctionCall) {
        translateFunctionCall(stmt)
        // just ignore any result values from the function call.
    }

    internal fun optimizeIntArgsViaRegisters(sub: PtSub) =
        (sub.parameters.size==1 && sub.parameters[0].type in IntegerDatatypesWithBoolean)
                || (sub.parameters.size==2 && sub.parameters[0].type in ByteDatatypesWithBoolean && sub.parameters[1].type in ByteDatatypesWithBoolean)

    internal fun translateFunctionCall(call: PtFunctionCall) {
        // Output only the code to set up the parameters and perform the actual call
        // NOTE: does NOT output the code to deal with the result values!
        // NOTE: does NOT output code to save/restore the X register for this call! Every caller should deal with this in their own way!!
        //       (you can use subroutine.shouldSaveX() and saveX()/restoreX() routines as a help for this)

        val symbol = asmgen.symbolTable.lookup(call.name)
        val sub = symbol?.astNode as IPtSubroutine
        val subAsmName = asmgen.asmSymbolName(call.name)

        if(sub is PtAsmSub) {
            argumentsViaRegisters(sub, call)
            if (sub.inline) {
                // inline the subroutine. (regardless of optimization settings!)
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


    private fun usesOtherRegistersWhileEvaluating(arg: PtExpression): Boolean {
        return when(arg) {
            is PtBuiltinFunctionCall -> {
                if (arg.name == "lsb" || arg.name == "msb")
                    return usesOtherRegistersWhileEvaluating(arg.args[0])
                if (arg.name == "mkword")
                    return usesOtherRegistersWhileEvaluating(arg.args[0]) || usesOtherRegistersWhileEvaluating(arg.args[1])
                return !arg.isSimple()
            }
            is PtAddressOf -> false
            is PtIdentifier -> false
            is PtMachineRegister -> false
            is PtMemoryByte -> false
            is PtNumber -> false
            is PtBool -> false
            else -> true
        }
    }

    private fun argumentsViaRegisters(sub: PtAsmSub, call: PtFunctionCall) {
        val registersUsed = mutableListOf<RegisterOrStatusflag>();

        fun usedA() = registersUsed.any {it.registerOrPair==RegisterOrPair.A || it.registerOrPair==RegisterOrPair.AX || it.registerOrPair==RegisterOrPair.AY}
        fun usedX() = registersUsed.any {it.registerOrPair==RegisterOrPair.X || it.registerOrPair==RegisterOrPair.AX || it.registerOrPair==RegisterOrPair.XY}
        fun usedY() = registersUsed.any {it.registerOrPair==RegisterOrPair.Y || it.registerOrPair==RegisterOrPair.AY || it.registerOrPair==RegisterOrPair.XY}

        if(sub.parameters.size==1) {
            argumentViaRegister(sub, IndexedValue(0, sub.parameters.single().second), call.args[0])
        } else {
            val optimalEvalOrder = asmsub6502ArgsEvalOrder(sub)
            optimalEvalOrder.forEach {
                val param = sub.parameters[it]
                val arg = call.args[it]
                registersUsed += if(usesOtherRegistersWhileEvaluating(arg)) {
                    if(!registersUsed.any{it.statusflag!=null || it.registerOrPair in CpuRegisters})
                        argumentViaRegister(sub, IndexedValue(it, param.second), arg)
                    else if(registersUsed.any {it.statusflag!=null}) {
                        throw AssemblyError("call argument evaluation problem: can't save cpu statusregister parameter ${call.position}")
                    }
                    else {
                        if(usedX()) asmgen.saveRegisterStack(CpuRegister.X, false)
                        if(usedY()) asmgen.saveRegisterStack(CpuRegister.Y, false)
                        if(usedA()) asmgen.saveRegisterStack(CpuRegister.A, false)
                        val used = argumentViaRegister(sub, IndexedValue(it, param.second), arg)
                        if(usedA()) asmgen.restoreRegisterStack(CpuRegister.A, false)
                        if(usedY()) asmgen.restoreRegisterStack(CpuRegister.Y, true)
                        if(usedX()) asmgen.restoreRegisterStack(CpuRegister.X, true)
                        used
                    }
                } else {
                    argumentViaRegister(sub, IndexedValue(it, param.second), arg)
                }
            }
        }
    }

    private fun argumentViaVariable(sub: PtSub, parameter: PtSubroutineParameter, value: PtExpression) {
        // pass parameter via a regular variable (not via registers)
        if(!isArgumentTypeCompatible(value.type, parameter.type))
            throw AssemblyError("argument type incompatible")

        val varName = asmgen.asmVariableName(sub.scopedName + "." + parameter.name)
        asmgen.assignExpressionToVariable(value, varName, parameter.type)
    }

    private fun argumentViaRegister(sub: IPtSubroutine, parameter: IndexedValue<PtSubroutineParameter>, value: PtExpression, registerOverride: RegisterOrPair? = null): RegisterOrStatusflag {
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
                throw AssemblyError("for statusflag, byte or bool value is required")
            if (statusflag == Statusflag.Pc) {
                // this boolean param needs to be set last, right before the jsr
                // for now, this is already enforced on the subroutine definition by the Ast Checker
                when(value) {
                    is PtNumber -> {
                        val carrySet = value.number.toInt() != 0
                        asmgen.out(if(carrySet) "  sec" else "  clc")
                    }
                    is PtBool -> {
                        asmgen.out(if(value.value) "  sec" else "  clc")
                    }
                    is PtIdentifier -> {
                        val sourceName = asmgen.asmVariableName(value)
                        // note: cannot use X register here to store A because it might be used for other arguments
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
                        asmgen.out("  ror  a")
                    }
                }
            } else throw AssemblyError("can only use Carry as status flag parameter")
            return RegisterOrStatusflag(null, statusflag)
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
                val scope = value.definingISub()
                val target: AsmAssignTarget =
                    if(parameter.value.type in ByteDatatypes && (register==RegisterOrPair.AX || register == RegisterOrPair.AY || register==RegisterOrPair.XY || register in Cx16VirtualRegisters))
                        AsmAssignTarget(TargetStorageKind.REGISTER, asmgen, parameter.value.type, scope, value.position, register = register)
                    else {
                        val signed = parameter.value.type == DataType.BYTE || parameter.value.type == DataType.WORD
                        AsmAssignTarget.fromRegisters(register, signed, value.position, scope, asmgen)
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
                asmgen.translateNormalAssignment(AsmAssignment(src, target, program.memsizer, Position.DUMMY), scope)
            }
            return RegisterOrStatusflag(register, null)
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


