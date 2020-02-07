package prog8.compiler.target.c64.codegen

import prog8.ast.IFunctionCall
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Subroutine
import prog8.ast.statements.SubroutineParameter
import prog8.compiler.toHex
import prog8.compiler.AssemblyError
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_HI_HEX
import prog8.compiler.target.c64.C64MachineDefinition.ESTACK_LO_HEX


internal class FunctionCallAsmGen(private val program: Program, private val asmgen: AsmGen) {

    internal fun translateFunctionCall(stmt: IFunctionCall) {
        // output the code to setup the parameters and perform the actual call
        // does NOT output the code to deal with the result values!
        val sub = stmt.target.targetSubroutine(program.namespace) ?: throw AssemblyError("undefined subroutine ${stmt.target}")
        if(Register.X in sub.asmClobbers)
            asmgen.out("  stx  c64.SCRATCH_ZPREGX")        // we only save X for now (required! is the eval stack pointer), screw A and Y...

        val subName = asmgen.asmIdentifierName(stmt.target)
        if(stmt.arglist.isNotEmpty()) {
            for(arg in sub.parameters.withIndex().zip(stmt.arglist)) {
                translateFuncArguments(arg.first, arg.second, sub)
            }
        }
        asmgen.out("  jsr  $subName")

        if(Register.X in sub.asmClobbers)
            asmgen.out("  ldx  c64.SCRATCH_ZPREGX")        // restore X again
    }

    private fun translateFuncArguments(parameter: IndexedValue<SubroutineParameter>, value: Expression, sub: Subroutine) {
        val sourceIDt = value.inferType(program)
        if(!sourceIDt.isKnown)
            throw AssemblyError("arg type unknown")
        val sourceDt = sourceIDt.typeOrElse(DataType.STRUCT)
        if(!argumentTypeCompatible(sourceDt, parameter.value.type))
            throw AssemblyError("argument type incompatible")
        if(sub.asmParameterRegisters.isEmpty()) {
            // pass parameter via a variable
            val paramVar = parameter.value
            val scopedParamVar = (sub.scopedname+"."+paramVar.name).split(".")
            val target = AssignTarget(null, IdentifierReference(scopedParamVar, sub.position), null, null, sub.position)
            target.linkParents(value.parent)
            when (value) {
                is NumericLiteralValue -> {
                    // optimize when the argument is a constant literal
                    when(parameter.value.type) {
                        in ByteDatatypes -> asmgen.assignFromByteConstant(target, value.number.toShort())
                        in WordDatatypes -> asmgen.assignFromWordConstant(target, value.number.toInt())
                        DataType.FLOAT -> asmgen.assignFromFloatConstant(target, value.number.toDouble())
                        in PassByReferenceDatatypes -> throw AssemblyError("can't pass string/array as arguments?")
                        else -> throw AssemblyError("weird parameter datatype")
                    }
                }
                is IdentifierReference -> {
                    // optimize when the argument is a variable
                    when (parameter.value.type) {
                        in ByteDatatypes -> asmgen.assignFromByteVariable(target, value)
                        in WordDatatypes -> asmgen.assignFromWordVariable(target, value)
                        DataType.FLOAT -> asmgen.assignFromFloatVariable(target, value)
                        in PassByReferenceDatatypes -> throw AssemblyError("can't pass string/array as arguments?")
                        else -> throw AssemblyError("weird parameter datatype")
                    }
                }
                is RegisterExpr -> {
                    asmgen.assignFromRegister(target, value.register)
                }
                is DirectMemoryRead -> {
                    when(value.addressExpression) {
                        is NumericLiteralValue -> {
                            val address = (value.addressExpression as NumericLiteralValue).number.toInt()
                            asmgen.assignFromMemoryByte(target, address, null)
                        }
                        is IdentifierReference -> {
                            asmgen.assignFromMemoryByte(target, null, value.addressExpression as IdentifierReference)
                        }
                        else -> {
                            asmgen.translateExpression(value.addressExpression)
                            asmgen.out("  jsr  prog8_lib.read_byte_from_address |  inx")
                            asmgen.assignFromRegister(target, Register.A)
                        }
                    }
                }
                else -> {
                    asmgen.translateExpression(value)
                    asmgen.assignFromEvalResult(target)
                }
            }
        } else {
            // pass parameter via a register parameter
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
            lda  $sourceName
            beq  +
            sec  
            bcs  ++
+           clc
+
""")
                            }
                            is RegisterExpr -> {
                                when(value.register) {
                                    Register.A -> asmgen.out("  cmp  #0")
                                    Register.X -> asmgen.out("  txa")
                                    Register.Y -> asmgen.out("  tya")
                                }
                                asmgen.out("""
            beq  +
            sec
            bcs  ++
+           clc
+
""")
                            }
                            else -> {
                                asmgen.translateExpression(value)
                                asmgen.out("""
            inx                        
            lda  $ESTACK_LO_HEX,x
            beq  +
            sec  
            bcs  ++
+           clc
+
""")
                            }
                        }
                    }
                    else throw AssemblyError("can only use Carry as status flag parameter")
                }
                register!=null && register.name.length==1 -> {
                    when (value) {
                        is NumericLiteralValue -> {
                            val target = AssignTarget(Register.valueOf(register.name), null, null, null, sub.position)
                            target.linkParents(value.parent)
                            asmgen.assignFromByteConstant(target, value.number.toShort())
                        }
                        is IdentifierReference -> {
                            val target = AssignTarget(Register.valueOf(register.name), null, null, null, sub.position)
                            target.linkParents(value.parent)
                            asmgen.assignFromByteVariable(target, value)
                        }
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
                            when (register) {
                                RegisterOrPair.AX -> asmgen.out("  lda  $sourceName  |  ldx  $sourceName+1")
                                RegisterOrPair.AY -> asmgen.out("  lda  $sourceName  |  ldy  $sourceName+1")
                                RegisterOrPair.XY -> asmgen.out("  ldx  $sourceName  |  ldy  $sourceName+1")
                                else -> {}
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
    }

    private fun argumentTypeCompatible(argType: DataType, paramType: DataType): Boolean {
        if(argType isAssignableTo paramType)
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
