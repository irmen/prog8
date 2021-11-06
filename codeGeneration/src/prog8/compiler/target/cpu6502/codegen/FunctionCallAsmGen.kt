package prog8.compiler.target.cpu6502.codegen

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.InlineAssembly
import prog8.ast.statements.RegisterOrStatusflag
import prog8.ast.statements.Subroutine
import prog8.ast.statements.SubroutineParameter
import prog8.compiler.target.AssemblyError
import prog8.compiler.target.cpu6502.codegen.assignment.AsmAssignSource
import prog8.compiler.target.cpu6502.codegen.assignment.AsmAssignTarget
import prog8.compiler.target.cpu6502.codegen.assignment.AsmAssignment
import prog8.compiler.target.cpu6502.codegen.assignment.TargetStorageKind
import prog8.compilerinterface.CpuType


internal class FunctionCallAsmGen(private val program: Program, private val asmgen: AsmGen) {

    internal fun translateFunctionCallStatement(stmt: IFunctionCall) {
        saveXbeforeCall(stmt)
        translateFunctionCall(stmt)
        restoreXafterCall(stmt)
        // just ignore any result values from the function call.
    }

    internal fun saveXbeforeCall(stmt: IFunctionCall) {
        val sub = stmt.target.targetSubroutine(program) ?: throw AssemblyError("undefined subroutine ${stmt.target}")
        if(sub.shouldSaveX()) {
            val regSaveOnStack = sub.asmAddress==null       // rom-routines don't require registers to be saved on stack, normal subroutines do because they can contain nested calls
            if(regSaveOnStack)
                asmgen.saveRegisterStack(CpuRegister.X, sub.shouldKeepA().saveOnEntry)
            else
                asmgen.saveRegisterLocal(CpuRegister.X, (stmt as Node).definingSubroutine!!)
        }
    }

    internal fun restoreXafterCall(stmt: IFunctionCall) {
        val sub = stmt.target.targetSubroutine(program) ?: throw AssemblyError("undefined subroutine ${stmt.target}")
        if(sub.shouldSaveX()) {
            val regSaveOnStack = sub.asmAddress==null       // rom-routines don't require registers to be saved on stack, normal subroutines do because they can contain nested calls
            if(regSaveOnStack)
                asmgen.restoreRegisterStack(CpuRegister.X, sub.shouldKeepA().saveOnReturn)
            else
                asmgen.restoreRegisterLocal(CpuRegister.X)
        }
    }

    internal fun translateFunctionCall(stmt: IFunctionCall) {
        // Output only the code to set up the parameters and perform the actual call
        // NOTE: does NOT output the code to deal with the result values!
        // NOTE: does NOT output code to save/restore the X register for this call! Every caller should deal with this in their own way!!
        //       (you can use subroutine.shouldSaveX() and saveX()/restoreX() routines as a help for this)

        val sub = stmt.target.targetSubroutine(program) ?: throw AssemblyError("undefined subroutine ${stmt.target}")
        val subName = asmgen.asmSymbolName(stmt.target)
        if(stmt.args.isNotEmpty()) {

            if(sub.asmParameterRegisters.isEmpty()) {
                // via variables
                for(arg in sub.parameters.withIndex().zip(stmt.args)) {
                    argumentViaVariable(sub, arg.first, arg.second)
                }
            } else {
                require(sub.isAsmSubroutine)
                if(sub.parameters.size==1) {
                    // just a single parameter, no risk of clobbering registers
                    argumentViaRegister(sub, IndexedValue(0, sub.parameters.single()), stmt.args[0])
                } else {

                    fun isNoClobberRisk(expr: Expression): Boolean {
                        if(expr is AddressOf ||
                                expr is NumericLiteralValue ||
                                expr is StringLiteralValue ||
                                expr is ArrayLiteralValue ||
                                expr is IdentifierReference)
                            return true

                        if(expr is FunctionCall) {
                            if(expr.target.nameInSource==listOf("lsb") || expr.target.nameInSource==listOf("msb"))
                                return isNoClobberRisk(expr.args[0])
                            if(expr.target.nameInSource==listOf("mkword"))
                                return isNoClobberRisk(expr.args[0]) && isNoClobberRisk(expr.args[1])
                        }

                        return false
                    }

                    when {
                        stmt.args.all {isNoClobberRisk(it)} -> {
                            // There's no risk of clobbering for these simple argument types. Optimize the register loading directly from these values.
                            // register assignment order: 1) cx16 virtual word registers, 2) actual CPU registers, 3) CPU Carry status flag.
                            val argsInfo = sub.parameters.withIndex().zip(stmt.args).zip(sub.asmParameterRegisters)
                            val (cx16virtualRegs, args2) = argsInfo.partition { it.second.registerOrPair in Cx16VirtualRegisters }
                            val (cpuRegs, statusRegs) = args2.partition { it.second.registerOrPair!=null }
                            for(arg in cx16virtualRegs)
                                argumentViaRegister(sub, arg.first.first, arg.first.second)
                            for(arg in cpuRegs)
                                argumentViaRegister(sub, arg.first.first, arg.first.second)
                            for(arg in statusRegs)
                                argumentViaRegister(sub, arg.first.first, arg.first.second)
                        }
                        else -> {
                            // Risk of clobbering due to complex expression args. Evaluate first, then assign registers.
                            registerArgsViaStackEvaluation(stmt, sub)
                        }
                    }
                }
            }
        }

        if(!sub.inline || !asmgen.options.optimize) {
            asmgen.out("  jsr  $subName")
        } else {
            // inline the subroutine.
            // we do this by copying the subroutine's statements at the call site.
            // NOTE: *if* there is a return statement, it will be the only one, and the very last statement of the subroutine
            // (this condition has been enforced by an ast check earlier)

            // note: for now, this is only reliably supported for asmsubs.
            if(!sub.isAsmSubroutine)
                throw AssemblyError("can only reliably inline asmsub routines at this time")

            asmgen.out("  \t; inlined routine follows: ${sub.name}")
            val assembly = sub.statements.single() as InlineAssembly
            asmgen.translate(assembly)
            asmgen.out("  \t; inlined routine end: ${sub.name}")
        }

        // remember: dealing with the X register and/or dealing with return values is the responsibility of the caller
    }

    private fun registerArgsViaStackEvaluation(stmt: IFunctionCall, sub: Subroutine) {
        // this is called when one or more of the arguments are 'complex' and
        // cannot be assigned to a register easily or risk clobbering other registers.
        // TODO find another way to prepare the arguments, without using the eval stack

        if(sub.parameters.isEmpty())
            return


        // 1. load all arguments reversed onto the stack: first arg goes last (is on top).

        for (arg in stmt.args.reversed())
            asmgen.translateExpression(arg)

        // TODO here's an alternative to the above, but for now generates bigger code due to intermediate register steps:
//        for (arg in stmt.args.reversed()) {
//            // note this stuff below is needed to (eventually) avoid calling asmgen.translateExpression()
//            // TODO also This STILL requires the translateNormalAssignment() to be fixed to avoid stack eval for expressions...
//            val dt = arg.inferType(program).getOr(DataType.UNDEFINED)
//            asmgen.assignExpressionTo(arg, AsmAssignTarget(TargetStorageKind.STACK, program, asmgen, dt, sub))
//        }

        var argForCarry: IndexedValue<Pair<Expression, RegisterOrStatusflag>>? = null
        var argForXregister: IndexedValue<Pair<Expression, RegisterOrStatusflag>>? = null
        var argForAregister: IndexedValue<Pair<Expression, RegisterOrStatusflag>>? = null

        asmgen.out("  inx")     // align estack pointer

        for(argi in stmt.args.zip(sub.asmParameterRegisters).withIndex()) {
            val plusIdxStr = if(argi.index==0) "" else "+${argi.index}"
            when {
                argi.value.second.statusflag == Statusflag.Pc -> {
                    require(argForCarry == null)
                    argForCarry = argi
                }
                argi.value.second.statusflag != null -> throw AssemblyError("can only use Carry as status flag parameter")
                argi.value.second.registerOrPair in arrayOf(RegisterOrPair.X, RegisterOrPair.AX, RegisterOrPair.XY) -> {
                    require(argForXregister==null)
                    argForXregister = argi
                }
                argi.value.second.registerOrPair in arrayOf(RegisterOrPair.A, RegisterOrPair.AY) -> {
                    require(argForAregister == null)
                    argForAregister = argi
                }
                argi.value.second.registerOrPair == RegisterOrPair.Y -> {
                    asmgen.out("  ldy  P8ESTACK_LO$plusIdxStr,x")
                }
                argi.value.second.registerOrPair in Cx16VirtualRegisters -> {
                    // immediately output code to load the virtual register, to avoid clobbering the A register later
                    when (sub.parameters[argi.index].type) {
                        in ByteDatatypes -> {
                            // only load the lsb of the virtual register
                            asmgen.out(
                                """
                                lda  P8ESTACK_LO$plusIdxStr,x
                                sta  cx16.${argi.value.second.registerOrPair.toString().lowercase()}
                            """)
                            if (asmgen.isTargetCpu(CpuType.CPU65c02))
                                asmgen.out(
                                    "  stz  cx16.${
                                        argi.value.second.registerOrPair.toString().lowercase()
                                    }+1")
                            else
                                asmgen.out(
                                    "  lda  #0 |  sta  cx16.${
                                        argi.value.second.registerOrPair.toString().lowercase()
                                    }+1")
                        }
                        in WordDatatypes, in IterableDatatypes ->
                            asmgen.out(
                                """
                                lda  P8ESTACK_LO$plusIdxStr,x
                                sta  cx16.${argi.value.second.registerOrPair.toString().lowercase()}
                                lda  P8ESTACK_HI$plusIdxStr,x
                                sta  cx16.${
                                    argi.value.second.registerOrPair.toString().lowercase()
                                }+1
                            """)
                        else -> throw AssemblyError("weird dt")
                    }
                }
                else -> throw AssemblyError("weird argument")
            }
        }

        if(argForCarry!=null) {
            val plusIdxStr = if(argForCarry.index==0) "" else "+${argForCarry.index}"
            asmgen.out("""
                lda  P8ESTACK_LO$plusIdxStr,x
                beq  +
                sec
                bcs  ++
+               clc
+               php""")             // push the status flags
        }

        if(argForAregister!=null) {
            val plusIdxStr = if(argForAregister.index==0) "" else "+${argForAregister.index}"
            when(argForAregister.value.second.registerOrPair) {
                RegisterOrPair.A -> asmgen.out("  lda  P8ESTACK_LO$plusIdxStr,x")
                RegisterOrPair.AY -> asmgen.out("  lda  P8ESTACK_LO$plusIdxStr,x |  ldy  P8ESTACK_HI$plusIdxStr,x")
                else -> throw AssemblyError("weird arg")
            }
        }

        if(argForXregister!=null) {
            val plusIdxStr = if(argForXregister.index==0) "" else "+${argForXregister.index}"

            if(argForAregister!=null)
                asmgen.out("  pha")
            when(argForXregister.value.second.registerOrPair) {
                RegisterOrPair.X -> asmgen.out("  lda  P8ESTACK_LO$plusIdxStr,x |  tax")
                RegisterOrPair.AX -> asmgen.out("  ldy  P8ESTACK_LO$plusIdxStr,x |  lda  P8ESTACK_HI$plusIdxStr,x |  tax |  tya")
                RegisterOrPair.XY -> asmgen.out("  ldy  P8ESTACK_HI$plusIdxStr,x |  lda  P8ESTACK_LO$plusIdxStr,x |  tax")
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
            throw AssemblyError("unknown dt")
        val valueDt = valueIDt.getOr(DataType.UNDEFINED)
        if(!isArgumentTypeCompatible(valueDt, parameter.value.type))
            throw AssemblyError("argument type incompatible")

        val varName = asmgen.asmVariableName(sub.scopedname+"."+parameter.value.name)
        asmgen.assignExpressionToVariable(value, varName, parameter.value.type, sub)
    }

    private fun argumentViaRegister(sub: Subroutine, parameter: IndexedValue<SubroutineParameter>, value: Expression) {
        // pass argument via a register parameter
        val valueIDt = value.inferType(program)
        if(!valueIDt.isKnown)
            throw AssemblyError("unknown dt")
        val valueDt = valueIDt.getOr(DataType.UNDEFINED)
        if(!isArgumentTypeCompatible(valueDt, parameter.value.type))
            throw AssemblyError("argument type incompatible")

        val paramRegister = sub.asmParameterRegisters[parameter.index]
        val statusflag = paramRegister.statusflag
        val register = paramRegister.registerOrPair
        val requiredDt = parameter.value.type
        if(requiredDt!=valueDt) {
            if(valueDt largerThan requiredDt)
                throw AssemblyError("can only convert byte values to word param types")
        }
        if (statusflag!=null) {
            if(requiredDt!=valueDt)
                throw AssemblyError("for statusflag, byte value is required")
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
                        asmgen.assignExpressionToRegister(value, RegisterOrPair.A)
                        asmgen.out("""
                                    beq  +
                                    sec
                                    bcs  ++
    +                               clc
    +""")
                    }
                }
            } else throw AssemblyError("can only use Carry as status flag parameter")
        }
        else {
            // via register or register pair
            register!!
            if(requiredDt largerThan valueDt) {
                // we need to sign extend the source, do this via temporary word variable
                asmgen.assignExpressionToVariable(value, "P8ZP_SCRATCH_W1", DataType.UBYTE, sub)
                asmgen.signExtendVariableLsb("P8ZP_SCRATCH_W1", valueDt)
                asmgen.assignVariableToRegister("P8ZP_SCRATCH_W1", register)
            } else {
                val target: AsmAssignTarget =
                    if(parameter.value.type in ByteDatatypes && (register==RegisterOrPair.AX || register == RegisterOrPair.AY || register==RegisterOrPair.XY || register in Cx16VirtualRegisters))
                        AsmAssignTarget(TargetStorageKind.REGISTER, program, asmgen, parameter.value.type, sub, register = register)
                    else
                        AsmAssignTarget.fromRegisters(register, sub, program, asmgen)
                val src = if(valueDt in PassByReferenceDatatypes) {
                    if(value is IdentifierReference) {
                        val addr = AddressOf(value, Position.DUMMY)
                        AsmAssignSource.fromAstSource(addr, program, asmgen).adjustSignedUnsigned(target)
                    } else {
                        AsmAssignSource.fromAstSource(value, program, asmgen).adjustSignedUnsigned(target)
                    }
                } else {
                    AsmAssignSource.fromAstSource(value, program, asmgen).adjustSignedUnsigned(target)
                }
                asmgen.translateNormalAssignment(AsmAssignment(src, target, false, program.memsizer, Position.DUMMY))
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
