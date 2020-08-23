package prog8.compiler.target.c64.codegen

import prog8.ast.IFunctionCall
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Assignment
import prog8.ast.statements.Subroutine
import prog8.ast.statements.SubroutineParameter
import prog8.compiler.AssemblyError
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_HI_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_LO_HEX
import prog8.compiler.toHex


internal class FunctionCallAsmGen(private val program: Program, private val asmgen: AsmGen) {

    internal fun translateFunctionCall(stmt: IFunctionCall) {
        // output the code to setup the parameters and perform the actual call
        // does NOT output the code to deal with the result values!
        val sub = stmt.target.targetSubroutine(program.namespace) ?: throw AssemblyError("undefined subroutine ${stmt.target}")
        val saveX = CpuRegister.X in sub.asmClobbers || sub.regXasResult()
        if(saveX)
            asmgen.out("  stx  c64.SCRATCH_ZPREGX")        // we only save X for now (required! is the eval stack pointer), screw A and Y...

        val subName = asmgen.asmIdentifierName(stmt.target)
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
                            argsViaStackEvaluation(stmt, sub)
                        }
                    }
                }
            }
        }
        asmgen.out("  jsr  $subName")

        if(saveX)
            asmgen.out("  ldx  c64.SCRATCH_ZPREGX")        // restore X again
    }

    private fun argsViaStackEvaluation(stmt: IFunctionCall, sub: Subroutine) {
        for (arg in stmt.args.reversed())
            asmgen.translateExpression(arg)
        for (regparam in sub.asmParameterRegisters) {
            when (regparam.registerOrPair) {
                RegisterOrPair.A -> asmgen.out(" inx |  lda  $ESTACK_LO_HEX,x")
                RegisterOrPair.X -> throw AssemblyError("can't pop into X register - use a variable instead")
                RegisterOrPair.Y -> asmgen.out(" inx |  ldy  $ESTACK_LO_HEX,x")
                RegisterOrPair.AX -> throw AssemblyError("can't pop into X register - use a variable instead")
                RegisterOrPair.AY -> asmgen.out(" inx |  lda  $ESTACK_LO_HEX,x |  ldy  $ESTACK_HI_HEX,x")
                RegisterOrPair.XY -> throw AssemblyError("can't pop into X register - use a variable instead")
                null -> {
                }
            }
            when (regparam.statusflag) {
                Statusflag.Pc -> asmgen.out("""
                        inx
                        pha
                        lda  $ESTACK_LO_HEX,x
                        beq  +
                        sec  
                        bcs  ++
            +           clc
            +           pla
            """)
                null -> {
                }
                else -> throw AssemblyError("can only use Carry as status flag parameter")
            }
        }
    }

    private fun argumentViaVariable(sub: Subroutine, parameter: IndexedValue<SubroutineParameter>, value: Expression) {
        // pass parameter via a regular variable (not via registers)
        val valueIDt = value.inferType(program)
        if(!valueIDt.isKnown)
            throw AssemblyError("arg type unknown")
        val valueDt = valueIDt.typeOrElse(DataType.STRUCT)
        if(!argumentTypeCompatible(valueDt, parameter.value.type))
            throw AssemblyError("argument type incompatible")

        val paramVar = parameter.value

        val scopedParamVar = (sub.scopedname+"."+paramVar.name).split(".")
        val target = AssignTarget(IdentifierReference(scopedParamVar, sub.position), null, null, sub.position)
        val assign = Assignment(target, value, value.position)
        assign.linkParents(value.parent)
        asmgen.translate(assign)
    }

    private fun argumentViaRegister(sub: Subroutine, parameter: IndexedValue<SubroutineParameter>, value: Expression) {
        // pass argument via a register parameter
        val valueIDt = value.inferType(program)
        if(!valueIDt.isKnown)
            throw AssemblyError("arg type unknown")
        val valueDt = valueIDt.typeOrElse(DataType.STRUCT)
        if(!argumentTypeCompatible(valueDt, parameter.value.type))
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
                            val sourceName = asmgen.asmIdentifierName(value)
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
            lda  $ESTACK_LO_HEX,x
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
            register!=null && register.name.length==1 -> {
                when (value) {
                    is NumericLiteralValue -> {
                        asmgen.out("  ld${register.name.toLowerCase()}  #${value.number.toInt().toHex()}")
                    }
                    is IdentifierReference -> {
                        asmgen.out("  ld${register.name.toLowerCase()}  ${asmgen.asmIdentifierName(value)}")
                    }
                    // TODO more special cases to optimize argument passing in registers without intermediate stack usage
                    else -> {
                        asmgen.translateExpression(value)
                        when(register) {
                            RegisterOrPair.A -> asmgen.out("  inx | lda  $ESTACK_LO_HEX,x")
                            RegisterOrPair.X -> throw AssemblyError("can't pop into X register - use a variable instead")
                            RegisterOrPair.Y -> asmgen.out("  inx | ldy  $ESTACK_LO_HEX,x")
                            else -> throw AssemblyError("cannot assign to register pair")
                        }
                    }
                }
            }
            register!=null && register.name.length==2 -> {
                // register pair as a 16-bit value (only possible for subroutine parameters)
                when (value) {
                    is NumericLiteralValue -> {
                        // optimize when the argument is a constant literal
                        val hex = value.number.toHex()
                        when (register) {
                            RegisterOrPair.AX -> asmgen.out("  lda  #<$hex  |  ldx  #>$hex")
                            RegisterOrPair.AY -> asmgen.out("  lda  #<$hex  |  ldy  #>$hex")
                            RegisterOrPair.XY -> asmgen.out("  ldx  #<$hex  |  ldy  #>$hex")
                            else -> {}
                        }
                    }
                    is AddressOf -> {
                        // optimize when the argument is an address of something
                        val sourceName = asmgen.asmIdentifierName(value.identifier)
                        when (register) {
                            RegisterOrPair.AX -> asmgen.out("  lda  #<$sourceName  |  ldx  #>$sourceName")
                            RegisterOrPair.AY -> asmgen.out("  lda  #<$sourceName  |  ldy  #>$sourceName")
                            RegisterOrPair.XY -> asmgen.out("  ldx  #<$sourceName  |  ldy  #>$sourceName")
                            else -> {}
                        }
                    }
                    is IdentifierReference -> {
                        val sourceName = asmgen.asmIdentifierName(value)
                        if(valueDt in PassByReferenceDatatypes) {
                            when (register) {
                                RegisterOrPair.AX -> asmgen.out("  lda  #<$sourceName  |  ldx  #>$sourceName")
                                RegisterOrPair.AY -> asmgen.out("  lda  #<$sourceName  |  ldy  #>$sourceName")
                                RegisterOrPair.XY -> asmgen.out("  ldx  #<$sourceName  |  ldy  #>$sourceName")
                                else -> {}
                            }
                        } else {
                            when (register) {
                                RegisterOrPair.AX -> asmgen.out("  lda  $sourceName  |  ldx  $sourceName+1")
                                RegisterOrPair.AY -> asmgen.out("  lda  $sourceName  |  ldy  $sourceName+1")
                                RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName  |  ldy  $sourceName+1")
                                else -> {}
                            }
                        }
                    }
                    else -> {
                        asmgen.translateExpression(value)
                        if (register == RegisterOrPair.AX || register == RegisterOrPair.XY)
                            throw AssemblyError("can't use X register here - use a variable")
                        else if (register == RegisterOrPair.AY)
                            asmgen.out("  inx |  lda  $ESTACK_LO_HEX,x  |  ldy  $ESTACK_HI_HEX,x")
                    }
                }
            }
        }
    }

    private fun argumentTypeCompatible(argType: DataType, paramType: DataType): Boolean {
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
