package prog8.codegen.cpu6502

import com.github.michaelbull.result.fold
import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.codegen.cpu6502.assignment.*
import prog8.compilerinterface.*
import prog8.parser.SourceCode
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.writeLines


const val generatedLabelPrefix = "prog8_label_"
const val subroutineFloatEvalResultVar1 = "prog8_float_eval_result1"
const val subroutineFloatEvalResultVar2 = "prog8_float_eval_result2"


class AsmGen(internal val program: Program,
             internal val errors: IErrorReporter,
             internal val variables: IVariablesAndConsts,
             internal val options: CompilationOptions): IAssemblyGenerator {

    internal val optimizedByteMultiplications = setOf(3,5,6,7,9,10,11,12,13,14,15,20,25,40,50,80,100)
    internal val optimizedWordMultiplications = setOf(3,5,6,7,9,10,12,15,20,25,40,50,80,100,320,640)
    internal val zeropage = options.compTarget.machine.zeropage
    internal val globalFloatConsts = mutableMapOf<Double, String>()     // all float values in the entire program (value -> varname)
    internal val loopEndLabels = ArrayDeque<String>()
    private val memorySlabs = mutableMapOf<String, Pair<UInt, UInt>>()
    private val assemblyLines = mutableListOf<String>()
    private val breakpointLabels = mutableListOf<String>()
    private val forloopsAsmGen = ForLoopsAsmGen(program, this)
    private val postincrdecrAsmGen = PostIncrDecrAsmGen(program, this)
    private val functioncallAsmGen = FunctionCallAsmGen(program, this)
    private val expressionsAsmGen = ExpressionsAsmGen(program, this, functioncallAsmGen)
    private val assignmentAsmGen = AssignmentAsmGen(program, this)
    private val builtinFunctionsAsmGen = BuiltinFunctionsAsmGen(program, this, assignmentAsmGen)
    private val programGen = ProgramGen(program, variables, options, errors, functioncallAsmGen, this)
    internal val allMemorySlabs: Map<String, Pair<UInt, UInt>> = memorySlabs

    override fun compileToAssembly(): IAssemblyProgram? {
        assemblyLines.clear()
        loopEndLabels.clear()

        println("Generating assembly code... ")
        programGen.generate()

        if(errors.noErrors()) {
            val output = options.outputDir.resolve("${program.name}.asm")
            if(options.optimize) {
                val separateLines = assemblyLines.flatMapTo(mutableListOf()) { it.split('\n') }
                assemblyLines.clear()
                while(optimizeAssembly(separateLines, options.compTarget.machine, program)>0) {
                    // optimize the assembly source code
                }
                output.writeLines(separateLines)
            } else {
                output.writeLines(assemblyLines)
            }
            return AssemblyProgram(program.name, options.outputDir, options.compTarget)
        } else {
            errors.report()
            return null
        }
    }

    internal fun getMemorySlab(name: String) = memorySlabs[name]
    internal fun allocateMemorySlab(name: String, size: UInt, align: UInt) {
        memorySlabs[name] = Pair(size, align)
    }

    internal fun isTargetCpu(cpu: CpuType) = options.compTarget.machine.cpu == cpu
    internal fun haveFPWRcall() = options.compTarget.name=="cx16"

    internal fun asmsubArgsEvalOrder(sub: Subroutine) =
        options.compTarget.asmsubArgsEvalOrder(sub)
    internal fun asmsubArgsHaveRegisterClobberRisk(args: List<Expression>, paramRegisters: List<RegisterOrStatusflag>) =
        options.compTarget.asmsubArgsHaveRegisterClobberRisk(args, paramRegisters)

    private var generatedLabelSequenceNumber: Int = 0

    internal fun makeLabel(postfix: String): String {
        generatedLabelSequenceNumber++
        return "${generatedLabelPrefix}${generatedLabelSequenceNumber}_$postfix"
    }

    internal fun outputSourceLine(node: Node) {
        out(" ;\tsrc line: ${node.position.file}:${node.position.line}")
    }

    internal fun out(str: String, splitlines: Boolean = true) {
        val fragment = (if(" | " in str) str.replace("|", "\n") else str).trim('\n')
        if (splitlines) {
            for (line in fragment.splitToSequence('\n')) {
                val trimmed = if (line.startsWith(' ')) "\t" + line.trim() else line
                // trimmed = trimmed.replace(Regex("^\\+\\s+"), "+\t")  // sanitize local label indentation
                assemblyLines.add(trimmed)
            }
        } else assemblyLines.add(fragment)
    }

    internal fun getFloatAsmConst(number: Double): String {
        val asmName = globalFloatConsts[number]
        if(asmName!=null)
            return asmName

        val newName = "prog8_float_const_${globalFloatConsts.size}"
        globalFloatConsts[number] = newName
        return newName
    }

    fun asmSymbolName(regs: RegisterOrPair): String =
        if (regs in Cx16VirtualRegisters)
            "cx16." + regs.toString().lowercase()
        else
            throw AssemblyError("no symbol name for register $regs")

    fun asmSymbolName(name: String) = fixNameSymbols(name)
    fun asmVariableName(name: String) = fixNameSymbols(name)
    fun asmSymbolName(name: Iterable<String>) = fixNameSymbols(name.joinToString("."))
    fun asmVariableName(name: Iterable<String>) = fixNameSymbols(name.joinToString("."))
    fun asmSymbolName(identifier: IdentifierReference) = asmSymbolName(identifier.nameInSource)
    fun asmVariableName(identifier: IdentifierReference) = asmVariableName(identifier.nameInSource)

    fun getTempVarName(dt: DataType): List<String> {
        return when(dt) {
            DataType.UBYTE -> listOf("cx16", "r9L")
            DataType.BYTE -> listOf("cx16", "r9sL")
            DataType.UWORD -> listOf("cx16", "r9")
            DataType.WORD -> listOf("cx16", "r9s")
            DataType.FLOAT -> listOf("floats", "tempvar_swap_float")      // defined in floats.p8
            else -> throw FatalAstException("invalid dt $dt")
        }
    }

    internal fun loadByteFromPointerIntoA(pointervar: IdentifierReference): String {
        // returns the source name of the zero page pointervar if it's already in the ZP,
        // otherwise returns "P8ZP_SCRATCH_W1" which is the intermediary
        when (val target = pointervar.targetStatement(program)) {
            is Label -> {
                val sourceName = asmSymbolName(pointervar)
                out("  lda  $sourceName")
                return sourceName
            }
            is VarDecl -> {
                val sourceName = asmVariableName(pointervar)
                if (isTargetCpu(CpuType.CPU65c02)) {
                    return if (isZpVar(target.scopedName)) {
                        // pointervar is already in the zero page, no need to copy
                        out("  lda  ($sourceName)")
                        sourceName
                    } else {
                        out("""
                            lda  $sourceName
                            ldy  $sourceName+1
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            lda  (P8ZP_SCRATCH_W1)""")
                        "P8ZP_SCRATCH_W1"
                    }
                } else {
                    return if (isZpVar(target.scopedName)) {
                        // pointervar is already in the zero page, no need to copy
                        out("  ldy  #0 |  lda  ($sourceName),y")
                        sourceName
                    } else {
                        out("""
                            lda  $sourceName
                            ldy  $sourceName+1
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            ldy  #0
                            lda  (P8ZP_SCRATCH_W1),y""")
                        "P8ZP_SCRATCH_W1"
                    }
                }
            }
            else -> throw AssemblyError("invalid pointervar")
        }
    }

    internal fun storeAIntoPointerVar(pointervar: IdentifierReference) {
        val sourceName = asmVariableName(pointervar)
        val vardecl = pointervar.targetVarDecl(program)!!
        if (isTargetCpu(CpuType.CPU65c02)) {
            if (isZpVar(vardecl.scopedName)) {
                // pointervar is already in the zero page, no need to copy
                out("  sta  ($sourceName)")
            } else {
                out("""
                    ldy  $sourceName
                    sty  P8ZP_SCRATCH_W2
                    ldy  $sourceName+1
                    sty  P8ZP_SCRATCH_W2+1
                    sta  (P8ZP_SCRATCH_W2)""")
            }
        } else {
            if (isZpVar(vardecl.scopedName)) {
                // pointervar is already in the zero page, no need to copy
                out(" ldy  #0 |  sta  ($sourceName),y")
            } else {
                out("""
                    ldy  $sourceName
                    sty  P8ZP_SCRATCH_W2
                    ldy  $sourceName+1
                    sty  P8ZP_SCRATCH_W2+1
                    ldy  #0
                    sta  (P8ZP_SCRATCH_W2),y""")
            }
        }
    }

    internal fun storeAIntoZpPointerVar(zpPointerVar: String) {
        if (isTargetCpu(CpuType.CPU65c02))
            out("  sta  ($zpPointerVar)")
        else
            out("  ldy  #0 |  sta  ($zpPointerVar),y")
    }

    internal fun loadAFromZpPointerVar(zpPointerVar: String) {
        if (isTargetCpu(CpuType.CPU65c02))
            out("  lda  ($zpPointerVar)")
        else
            out("  ldy  #0 |  lda  ($zpPointerVar),y")
    }

    private  fun fixNameSymbols(name: String): String {
        val name2 = name.replace("<", "prog8_").replace(">", "")     // take care of the autogenerated invalid (anon) label names
        return name2.replace("prog8_lib.P8ZP_SCRATCH_", "P8ZP_SCRATCH_")    // take care of the 'hooks' to the temp vars
    }

    internal fun saveRegisterLocal(register: CpuRegister, scope: Subroutine) {
        if (isTargetCpu(CpuType.CPU65c02)) {
            // just use the cpu's stack for all registers, shorter code
            when (register) {
                CpuRegister.A -> out("  pha")
                CpuRegister.X -> out("  phx")
                CpuRegister.Y -> out("  phy")
            }
        } else {
            when (register) {
                CpuRegister.A -> {
                    // just use the stack, only for A
                    out("  pha")
                }
                CpuRegister.X -> {
                    out("  stx  prog8_regsaveX")
                    scope.asmGenInfo.usedRegsaveX = true
                }
                CpuRegister.Y -> {
                    out("  sty  prog8_regsaveY")
                    scope.asmGenInfo.usedRegsaveY = true
                }
            }
        }
    }

    internal fun saveRegisterStack(register: CpuRegister, keepA: Boolean) {
        when (register) {
            CpuRegister.A -> out("  pha")
            CpuRegister.X -> {
                if (isTargetCpu(CpuType.CPU65c02)) out("  phx")
                else {
                    if(keepA)
                        out("  sta  P8ZP_SCRATCH_REG |  txa |  pha  |  lda  P8ZP_SCRATCH_REG")
                    else
                        out("  txa |  pha")
                }
            }
            CpuRegister.Y -> {
                if (isTargetCpu(CpuType.CPU65c02)) out("  phy")
                else {
                    if(keepA)
                        out("  sta  P8ZP_SCRATCH_REG |  tya |  pha  |  lda  P8ZP_SCRATCH_REG")
                    else
                        out("  tya |  pha")
                }
            }
        }
    }

    internal fun restoreRegisterLocal(register: CpuRegister) {
        if (isTargetCpu(CpuType.CPU65c02)) {
            when (register) {
                // this just used the stack, for all registers. Shorter code.
                CpuRegister.A -> out("  pla")
                CpuRegister.X -> out("  plx")
                CpuRegister.Y -> out("  ply")
            }

        } else {
            when (register) {
                CpuRegister.A -> out("  pla")   // this just used the stack but only for A
                CpuRegister.X -> out("  ldx  prog8_regsaveX")
                CpuRegister.Y -> out("  ldy  prog8_regsaveY")
            }
        }
    }

    internal fun restoreRegisterStack(register: CpuRegister, keepA: Boolean) {
        when (register) {
            CpuRegister.A -> {
                if(keepA)
                    throw AssemblyError("can't set keepA if A is restored")
                out("  pla")
            }
            CpuRegister.X -> {
                if (isTargetCpu(CpuType.CPU65c02)) out("  plx")
                else {
                    if(keepA)
                        out("  sta  P8ZP_SCRATCH_REG |  pla |  tax |  lda  P8ZP_SCRATCH_REG")
                    else
                        out("  pla |  tax")
                }
            }
            CpuRegister.Y -> {
                if (isTargetCpu(CpuType.CPU65c02)) out("  ply")
                else {
                    if(keepA)
                        out("  sta  P8ZP_SCRATCH_REG |  pla |  tay |  lda  P8ZP_SCRATCH_REG")
                    else
                        out("  pla |  tay")
                }
            }
        }
    }

    internal fun translate(stmt: Statement) {
        outputSourceLine(stmt)
        when(stmt) {
            is VarDecl -> translate(stmt)
            is Directive -> translate(stmt)
            is Return -> translate(stmt)
            is Subroutine -> programGen.translateSubroutine(stmt)
            is InlineAssembly -> translate(stmt)
            is FunctionCallStatement -> {
                val functionName = stmt.target.nameInSource.last()
                val builtinFunc = BuiltinFunctions[functionName]
                if (builtinFunc != null) {
                    builtinFunctionsAsmGen.translateFunctioncallStatement(stmt, builtinFunc)
                } else {
                    functioncallAsmGen.translateFunctionCallStatement(stmt)
                }
            }
            is Assignment -> assignmentAsmGen.translate(stmt)
            is Jump -> {
                val (asmLabel, indirect) = getJumpTarget(stmt)
                jmp(asmLabel, indirect)
            }
            is GoSub -> translate(stmt)
            is PostIncrDecr -> postincrdecrAsmGen.translate(stmt)
            is Label -> translate(stmt)
            is ConditionalBranch -> translate(stmt)
            is IfElse -> translate(stmt)
            is ForLoop -> forloopsAsmGen.translate(stmt)
            is RepeatLoop -> translate(stmt)
            is When -> translate(stmt)
            is AnonymousScope -> translate(stmt)
            is Pipe -> translatePipeExpression(stmt.expressions, stmt, true, false)
            is BuiltinFunctionPlaceholder -> throw AssemblyError("builtin function should not have placeholder anymore")
            is UntilLoop -> throw AssemblyError("do..until should have been converted to jumps")
            is WhileLoop -> throw AssemblyError("while should have been converted to jumps")
            is Block -> throw AssemblyError("block should have been handled elsewhere")
            is Break -> throw AssemblyError("break should have been replaced by goto")
            else -> throw AssemblyError("missing asm translation for $stmt")
        }
    }

    internal fun loadScaledArrayIndexIntoRegister(
        expr: ArrayIndexedExpression,
        elementDt: DataType,
        register: CpuRegister,
        addOneExtra: Boolean = false
    ) {
        val reg = register.toString().lowercase()
        val indexnum = expr.indexer.constIndex()
        if (indexnum != null) {
            val indexValue = indexnum * options.compTarget.memorySize(elementDt) + if (addOneExtra) 1 else 0
            out("  ld$reg  #$indexValue")
            return
        }

        val indexVar = expr.indexer.indexExpr as? IdentifierReference
            ?: throw AssemblyError("array indexer should have been replaced with a temp var @ ${expr.indexer.position}")

        val indexName = asmVariableName(indexVar)
        if (addOneExtra) {
            // add 1 to the result
            when (elementDt) {
                in ByteDatatypes -> {
                    out("  ldy  $indexName |  iny")
                    when (register) {
                        CpuRegister.A -> out(" tya")
                        CpuRegister.X -> out(" tyx")
                        CpuRegister.Y -> {
                        }
                    }
                }
                in WordDatatypes -> {
                    out("  lda  $indexName |  sec |  rol  a")
                    when (register) {
                        CpuRegister.A -> {
                        }
                        CpuRegister.X -> out(" tax")
                        CpuRegister.Y -> out(" tay")
                    }
                }
                DataType.FLOAT -> {
                    require(options.compTarget.memorySize(DataType.FLOAT) == 5)
                    out(
                        """
                                lda  $indexName
                                asl  a
                                asl  a
                                sec
                                adc  $indexName"""
                    )
                    when (register) {
                        CpuRegister.A -> {
                        }
                        CpuRegister.X -> out(" tax")
                        CpuRegister.Y -> out(" tay")
                    }
                }
                else -> throw AssemblyError("weird dt")
            }
        } else {
            when (elementDt) {
                in ByteDatatypes -> out("  ld$reg  $indexName")
                in WordDatatypes -> {
                    out("  lda  $indexName |  asl  a")
                    when (register) {
                        CpuRegister.A -> {
                        }
                        CpuRegister.X -> out(" tax")
                        CpuRegister.Y -> out(" tay")
                    }
                }
                DataType.FLOAT -> {
                    require(options.compTarget.memorySize(DataType.FLOAT) == 5)
                    out(
                        """
                                lda  $indexName
                                asl  a
                                asl  a
                                clc
                                adc  $indexName"""
                    )
                    when (register) {
                        CpuRegister.A -> {
                        }
                        CpuRegister.X -> out(" tax")
                        CpuRegister.Y -> out(" tay")
                    }
                }
                else -> throw AssemblyError("weird dt")
            }
        }
    }

    @Deprecated("avoid calling this as it generates slow evalstack based code")
    internal fun translateExpression(expression: Expression) =
            expressionsAsmGen.translateExpression(expression)

    internal fun translateBuiltinFunctionCallExpression(functionCallExpr: FunctionCallExpression, signature: FSignature, resultToStack: Boolean, resultRegister: RegisterOrPair?) =
            builtinFunctionsAsmGen.translateFunctioncallExpression(functionCallExpr, signature, resultToStack, resultRegister)

    internal fun translateBuiltinFunctionCallExpression(name: String, args: List<AsmAssignSource>, scope: Subroutine): DataType =
            builtinFunctionsAsmGen.translateFunctioncall(name, args, false, scope)

    internal fun translateBuiltinFunctionCallStatement(name: String, args: List<AsmAssignSource>, scope: Subroutine) =
            builtinFunctionsAsmGen.translateFunctioncall(name, args, true, scope)

    internal fun translateFunctionCall(functionCallExpr: FunctionCallExpression, isExpression: Boolean) =
            functioncallAsmGen.translateFunctionCall(functionCallExpr, isExpression)

    internal fun saveXbeforeCall(functionCall: IFunctionCall)  =
            functioncallAsmGen.saveXbeforeCall(functionCall)

    internal fun saveXbeforeCall(gosub: GoSub)  =
            functioncallAsmGen.saveXbeforeCall(gosub)

    internal fun restoreXafterCall(functionCall: IFunctionCall) =
            functioncallAsmGen.restoreXafterCall(functionCall)

    internal fun restoreXafterCall(gosub: GoSub) =
            functioncallAsmGen.restoreXafterCall(gosub)

    internal fun translateNormalAssignment(assign: AsmAssignment) =
            assignmentAsmGen.translateNormalAssignment(assign)

    internal fun assignExpressionToRegister(expr: Expression, register: RegisterOrPair, signed: Boolean=false) =
            assignmentAsmGen.assignExpressionToRegister(expr, register, signed)

    internal fun assignExpressionToVariable(expr: Expression, asmVarName: String, dt: DataType, scope: Subroutine?) =
            assignmentAsmGen.assignExpressionToVariable(expr, asmVarName, dt, scope)

    internal fun assignVariableToRegister(asmVarName: String, register: RegisterOrPair, signed: Boolean=false) =
            assignmentAsmGen.assignVariableToRegister(asmVarName, register, signed)

    internal fun assignRegister(reg: RegisterOrPair, target: AsmAssignTarget) {
        when(reg) {
            RegisterOrPair.A,
            RegisterOrPair.X,
            RegisterOrPair.Y -> assignmentAsmGen.assignRegisterByte(target, reg.asCpuRegister())
            RegisterOrPair.AX,
            RegisterOrPair.AY,
            RegisterOrPair.XY,
            in Cx16VirtualRegisters -> assignmentAsmGen.assignRegisterpairWord(target, reg)
            RegisterOrPair.FAC1 -> assignmentAsmGen.assignFAC1float(target)
            RegisterOrPair.FAC2 -> assignmentAsmGen.assignFAC2float(target)
            else -> throw AssemblyError("invalid register")
        }
    }

    internal fun assignExpressionTo(value: Expression, target: AsmAssignTarget) {
        // don't use translateExpression() to avoid evalstack
        when (target.datatype) {
            in ByteDatatypes -> {
                assignExpressionToRegister(value, RegisterOrPair.A)
                assignRegister(RegisterOrPair.A, target)
            }
            in WordDatatypes, in PassByReferenceDatatypes -> {
                assignExpressionToRegister(value, RegisterOrPair.AY)
                translateNormalAssignment(
                    AsmAssignment(
                        AsmAssignSource(SourceStorageKind.REGISTER, program, this, target.datatype, register=RegisterOrPair.AY),
                        target, false, program.memsizer, value.position
                    )
                )
            }
            DataType.FLOAT -> {
                assignExpressionToRegister(value, RegisterOrPair.FAC1)
                assignRegister(RegisterOrPair.FAC1, target)
            }
            else -> throw AssemblyError("weird dt ${target.datatype}")
        }
    }

    private fun branchInstruction(condition: BranchCondition, complement: Boolean) =
            if(complement) {
                when (condition) {
                    BranchCondition.CS -> "bcc"
                    BranchCondition.CC -> "bcs"
                    BranchCondition.EQ, BranchCondition.Z -> "bne"
                    BranchCondition.NE, BranchCondition.NZ -> "beq"
                    BranchCondition.VS -> "bvc"
                    BranchCondition.VC -> "bvs"
                    BranchCondition.MI, BranchCondition.NEG -> "bpl"
                    BranchCondition.PL, BranchCondition.POS -> "bmi"
                }
            } else {
                when (condition) {
                    BranchCondition.CS -> "bcs"
                    BranchCondition.CC -> "bcc"
                    BranchCondition.EQ, BranchCondition.Z -> "beq"
                    BranchCondition.NE, BranchCondition.NZ -> "bne"
                    BranchCondition.VS -> "bvs"
                    BranchCondition.VC -> "bvc"
                    BranchCondition.MI, BranchCondition.NEG -> "bmi"
                    BranchCondition.PL, BranchCondition.POS -> "bpl"
                }
            }

    private fun translate(stmt: IfElse) {
        requireComparisonExpression(stmt.condition)  // IfStatement: condition must be of form  'x <comparison> <value>'
        val booleanCondition = stmt.condition as BinaryExpression

        if (stmt.elsepart.isEmpty()) {
            val jump = stmt.truepart.statements.singleOrNull()
            if(jump is Jump) {
                translateCompareAndJumpIfTrue(booleanCondition, jump)
            } else {
                val endLabel = makeLabel("if_end")
                translateCompareAndJumpIfFalse(booleanCondition, endLabel)
                translate(stmt.truepart)
                out(endLabel)
            }
        }
        else {
            // both true and else parts
            val elseLabel = makeLabel("if_else")
            val endLabel = makeLabel("if_end")
            translateCompareAndJumpIfFalse(booleanCondition, elseLabel)
            translate(stmt.truepart)
            jmp(endLabel)
            out(elseLabel)
            translate(stmt.elsepart)
            out(endLabel)
        }
    }

    private fun requireComparisonExpression(condition: Expression) {
        if(condition !is BinaryExpression || condition.operator !in ComparisonOperators)
            throw AssemblyError("expected boolean comparison expression $condition")
    }

    private fun translate(stmt: RepeatLoop) {
        val endLabel = makeLabel("repeatend")
        loopEndLabels.push(endLabel)

        when (stmt.iterations) {
            null -> {
                // endless loop
                val repeatLabel = makeLabel("repeat")
                out(repeatLabel)
                translate(stmt.body)
                jmp(repeatLabel)
                out(endLabel)
            }
            is NumericLiteralValue -> {
                val iterations = (stmt.iterations as NumericLiteralValue).number.toInt()
                when {
                    iterations == 0 -> {}
                    iterations == 1 -> translate(stmt.body)
                    iterations<0 || iterations>65535 -> throw AssemblyError("invalid number of iterations")
                    iterations <= 256 -> repeatByteCount(iterations, stmt)
                    else -> repeatWordCount(iterations, stmt)
                }
            }
            is IdentifierReference -> {
                val vardecl = (stmt.iterations as IdentifierReference).targetStatement(program) as VarDecl
                val name = asmVariableName(stmt.iterations as IdentifierReference)
                when(vardecl.datatype) {
                    DataType.UBYTE, DataType.BYTE -> {
                        assignVariableToRegister(name, RegisterOrPair.Y)
                        repeatCountInY(stmt, endLabel)
                    }
                    DataType.UWORD, DataType.WORD -> {
                        assignVariableToRegister(name, RegisterOrPair.AY)
                        repeatWordCountInAY(endLabel, stmt)
                    }
                    else -> throw AssemblyError("invalid loop variable datatype $vardecl")
                }
            }
            else -> {
                val dt = stmt.iterations!!.inferType(program)
                if(!dt.isKnown)
                    throw AssemblyError("unknown dt")
                when (dt.getOr(DataType.UNDEFINED)) {
                    in ByteDatatypes -> {
                        assignExpressionToRegister(stmt.iterations!!, RegisterOrPair.Y)
                        repeatCountInY(stmt, endLabel)
                    }
                    in WordDatatypes -> {
                        assignExpressionToRegister(stmt.iterations!!, RegisterOrPair.AY)
                        repeatWordCountInAY(endLabel, stmt)
                    }
                    else -> throw AssemblyError("invalid loop expression datatype $dt")
                }
            }
        }

        loopEndLabels.pop()
    }

    private fun repeatWordCount(count: Int, stmt: RepeatLoop) {
        require(count in 257..65535)
        val repeatLabel = makeLabel("repeat")
        if(isTargetCpu(CpuType.CPU65c02)) {
            val counterVar = createRepeatCounterVar(DataType.UWORD, true, stmt)
            out("""
                lda  #<$count
                ldy  #>$count
                sta  $counterVar
                sty  $counterVar+1
$repeatLabel""")
            translate(stmt.body)
            out("""
                lda  $counterVar
                bne  +
                dec  $counterVar+1
+               dec  $counterVar
                lda  $counterVar
                ora  $counterVar+1
                bne  $repeatLabel""")
        } else {
            val counterVar = createRepeatCounterVar(DataType.UWORD, false, stmt)
            out("""
                lda  #<$count
                ldy  #>$count
                sta  $counterVar
                sty  $counterVar+1
$repeatLabel""")
            translate(stmt.body)
            out("""
                lda  $counterVar
                bne  +
                dec  $counterVar+1
+               dec  $counterVar
                lda  $counterVar
                ora  $counterVar+1
                bne  $repeatLabel""")
        }
    }

    private fun repeatWordCountInAY(endLabel: String, stmt: RepeatLoop) {
        // note: A/Y must have been loaded with the number of iterations!
        // no need to explicitly test for 0 iterations as this is done in the countdown logic below
        val repeatLabel = makeLabel("repeat")
        val counterVar = createRepeatCounterVar(DataType.UWORD, false, stmt)
        out("""
                sta  $counterVar
                sty  $counterVar+1
$repeatLabel    lda  $counterVar
                bne  +
                lda  $counterVar+1
                beq  $endLabel
                lda  $counterVar
                bne  +
                dec  $counterVar+1
+               dec  $counterVar
""")
        translate(stmt.body)
        jmp(repeatLabel)
        out(endLabel)
    }

    private fun repeatByteCount(count: Int, stmt: RepeatLoop) {
        require(count in 2..256)
        val repeatLabel = makeLabel("repeat")
        if(isTargetCpu(CpuType.CPU65c02)) {
            val counterVar = createRepeatCounterVar(DataType.UBYTE, true, stmt)
            out("  lda  #${count and 255} |  sta  $counterVar")
            out(repeatLabel)
            translate(stmt.body)
            out("  dec  $counterVar |  bne  $repeatLabel")
        } else {
            val counterVar = createRepeatCounterVar(DataType.UBYTE, false, stmt)
            out("  lda  #${count and 255} |  sta  $counterVar")
            out(repeatLabel)
            translate(stmt.body)
            out("  dec  $counterVar |  bne  $repeatLabel")
        }
    }

    private fun repeatCountInY(stmt: RepeatLoop, endLabel: String) {
        // note: Y must just have been loaded with the (variable) number of loops to be performed!
        val repeatLabel = makeLabel("repeat")
        if(isTargetCpu(CpuType.CPU65c02)) {
            val counterVar = createRepeatCounterVar(DataType.UBYTE, true, stmt)
            out("  beq  $endLabel |  sty  $counterVar")
            out(repeatLabel)
            translate(stmt.body)
            out("  dec  $counterVar |  bne  $repeatLabel")
        } else {
            val counterVar = createRepeatCounterVar(DataType.UBYTE, false, stmt)
            out("  beq  $endLabel |  sty  $counterVar")
            out(repeatLabel)
            translate(stmt.body)
            out("  dec  $counterVar |  bne  $repeatLabel")
        }
        out(endLabel)
    }

    private fun createRepeatCounterVar(dt: DataType, preferZeropage: Boolean, stmt: RepeatLoop): String {
        val asmInfo = stmt.definingSubroutine!!.asmGenInfo
        var parent = stmt.parent
        while(parent !is ParentSentinel) {
            if(parent is RepeatLoop)
                break
            parent = parent.parent
        }
        val isNested = parent is RepeatLoop

        if(!isNested) {
            // we can re-use a counter var from the subroutine if it already has one for that datatype
            val existingVar = asmInfo.extraVars.firstOrNull { it.first==dt }
            if(existingVar!=null) {
                if(!preferZeropage || existingVar.third!=null)
                    return existingVar.second
            }
        }

        val counterVar = makeLabel("counter")
        when(dt) {
            DataType.UBYTE, DataType.UWORD -> {
                val result = zeropage.allocate(listOf(counterVar), dt, null, stmt.position, errors)
                result.fold(
                    success = { (address, _) -> asmInfo.extraVars.add(Triple(dt, counterVar, address)) },
                    failure = { asmInfo.extraVars.add(Triple(dt, counterVar, null)) }  // allocate normally
                )
                return counterVar
            }
            else -> throw AssemblyError("invalidt dt")
        }
    }

    private fun translate(stmt: When) {
        val endLabel = makeLabel("choice_end")
        val choiceBlocks = mutableListOf<Pair<String, AnonymousScope>>()
        val conditionDt = stmt.condition.inferType(program)
        if(!conditionDt.isKnown)
            throw AssemblyError("unknown condition dt")
        if(conditionDt.getOr(DataType.BYTE) in ByteDatatypes)
            assignExpressionToRegister(stmt.condition, RegisterOrPair.A)
        else
            assignExpressionToRegister(stmt.condition, RegisterOrPair.AY)

        for(choice in stmt.choices) {
            val choiceLabel = makeLabel("choice")
            if(choice.values==null) {
                // the else choice
                translate(choice.statements)
            } else {
                choiceBlocks.add(choiceLabel to choice.statements)
                for (cv in choice.values!!) {
                    val value = (cv as NumericLiteralValue).number.toInt()
                    if(conditionDt.getOr(DataType.BYTE) in ByteDatatypes) {
                        out("  cmp  #${value.toHex()} |  beq  $choiceLabel")
                    } else {
                        out("""
                            cmp  #<${value.toHex()}
                            bne  +
                            cpy  #>${value.toHex()}
                            beq  $choiceLabel
+
                            """)
                    }
                }
            }
        }
        jmp(endLabel)
        for(choiceBlock in choiceBlocks.withIndex()) {
            out(choiceBlock.value.first)
            translate(choiceBlock.value.second)
            if(choiceBlock.index<choiceBlocks.size-1)
                jmp(endLabel)
        }
        out(endLabel)
    }

    private fun translate(stmt: Label) {
        out(stmt.name)
    }

    private fun translate(scope: AnonymousScope) {
        // note: the variables defined in an anonymous scope have been moved to their defining subroutine's scope
        scope.statements.forEach{ translate(it) }
    }

    private fun translate(stmt: ConditionalBranch) {
        if(stmt.truepart.isEmpty() && stmt.elsepart.isNotEmpty())
            throw AssemblyError("only else part contains code, shoud have been switched already")

        val jump = stmt.truepart.statements.first() as? Jump
        if(jump!=null) {
            // branch with only a jump (goto)
            val instruction = branchInstruction(stmt.condition, false)
            val (asmLabel, indirect) = getJumpTarget(jump)
            if(indirect) {
                val complementedInstruction = branchInstruction(stmt.condition, true)
                out("""
                    $complementedInstruction +
                    jmp  ($asmLabel)
+""")
            }
            else {
                out("  $instruction  $asmLabel")
            }
            translate(stmt.elsepart)
        } else {
            if(stmt.elsepart.isEmpty()) {
                val instruction = branchInstruction(stmt.condition, true)
                val elseLabel = makeLabel("branch_else")
                out("  $instruction  $elseLabel")
                translate(stmt.truepart)
                out(elseLabel)
            } else {
                val instruction = branchInstruction(stmt.condition, true)
                val elseLabel = makeLabel("branch_else")
                val endLabel = makeLabel("branch_end")
                out("  $instruction  $elseLabel")
                translate(stmt.truepart)
                jmp(endLabel)
                out(elseLabel)
                translate(stmt.elsepart)
                out(endLabel)
            }
        }
    }

    private fun translate(decl: VarDecl) {
        if(decl.type==VarDeclType.VAR && decl.value != null && decl.datatype in NumericDatatypes)
            throw AssemblyError("vardecls for variables, with initial numerical value, should have been rewritten as plain vardecl + assignment $decl")
        // at this time, nothing has to be done here anymore code-wise
    }

    private fun translate(stmt: Directive) {
        when(stmt.directive) {
            "%asminclude" -> {
                val includedName = stmt.args[0].str!!
                if(stmt.definingModule.source is SourceCode.Generated)
                    TODO("%asminclude inside non-library, non-filesystem module")
                loadAsmIncludeFile(includedName, stmt.definingModule.source).fold(
                    success = { assemblyLines.add(it.trimEnd().trimStart('\n')) },
                    failure = { errors.err(it.toString(), stmt.position) }
                )
            }
            "%asmbinary" -> {
                val includedName = stmt.args[0].str!!
                val offset = if(stmt.args.size>1) ", ${stmt.args[1].int}" else ""
                val length = if(stmt.args.size>2) ", ${stmt.args[2].int}" else ""
                if(stmt.definingModule.source is SourceCode.Generated)
                    TODO("%asmbinary inside non-library, non-filesystem module")
                val sourcePath = Path(stmt.definingModule.source.origin)
                val includedPath = sourcePath.resolveSibling(includedName)
                val pathForAssembler = options.outputDir // #54: 64tass needs the path *relative to the .asm file*
                    .toAbsolutePath()
                    .relativize(includedPath.toAbsolutePath())
                    .normalize() // avoid assembler warnings (-Wportable; only some, not all)
                    .toString().replace('\\', '/')
                out("  .binary \"$pathForAssembler\" $offset $length")
            }
            "%breakpoint" -> {
                val label = "_prog8_breakpoint_${breakpointLabels.size+1}"
                breakpointLabels.add(label)
                out(label)
            }
        }
    }

    private fun translate(gosub: GoSub) {
        val tgt = gosub.identifier!!.targetSubroutine(program)
        if(tgt!=null && tgt.isAsmSubroutine) {
            // no need to rescue X , this has been taken care of already
            out("  jsr  ${getJumpTarget(gosub)}")
        } else {
            saveXbeforeCall(gosub)
            out("  jsr  ${getJumpTarget(gosub)}")
            restoreXafterCall(gosub)
        }
    }

    private fun getJumpTarget(jump: Jump): Pair<String, Boolean> {
        val ident = jump.identifier
        val label = jump.generatedLabel
        val addr = jump.address
        return when {
            ident!=null -> {
                // can be a label, or a pointer variable
                val target = ident.targetVarDecl(program)
                if(target!=null)
                    Pair(asmSymbolName(ident), true)        // indirect
                else
                    Pair(asmSymbolName(ident), false)
            }
            label!=null -> Pair(label, false)
            addr!=null -> Pair(addr.toHex(), false)
            else -> Pair("????", false)
        }
    }

    private fun getJumpTarget(gosub: GoSub): String {
        val ident = gosub.identifier
        val label = gosub.generatedLabel
        val addr = gosub.address
        return when {
            ident!=null -> asmSymbolName(ident)
            label!=null -> label
            addr!=null -> addr.toHex()
            else -> "????"
        }
    }

    private fun translate(ret: Return, withRts: Boolean=true) {
        ret.value?.let { returnvalue ->
            val sub = ret.definingSubroutine!!
            val returnType = sub.returntypes.single()
            val returnReg = sub.asmReturnvaluesRegisters.single()
            if(returnReg.registerOrPair==null)
                throw AssemblyError("normal subroutines can't return value in status register directly")

            when (returnType) {
                in NumericDatatypes -> {
                    assignExpressionToRegister(returnvalue, returnReg.registerOrPair!!)
                }
                else -> {
                    // all else take its address and assign that also to AY register pair
                    val addrofValue = AddressOf(returnvalue as IdentifierReference, returnvalue.position)
                    assignmentAsmGen.assignExpressionToRegister(addrofValue, returnReg.registerOrPair!!, false)
                }
            }
        }

        if(withRts)
            out("  rts")
    }

    private fun translate(asm: InlineAssembly) {
        val assembly = asm.assembly.trimEnd().trimStart('\n')
        assemblyLines.add(assembly)
    }

    internal fun returnRegisterOfFunction(it: IdentifierReference): RegisterOrPair {
        return when (val targetRoutine = it.targetStatement(program)!!) {
            is BuiltinFunctionPlaceholder -> {
                when (BuiltinFunctions.getValue(targetRoutine.name).known_returntype) {
                    in ByteDatatypes -> RegisterOrPair.A
                    in WordDatatypes -> RegisterOrPair.AY
                    DataType.FLOAT -> RegisterOrPair.FAC1
                    else -> throw AssemblyError("weird returntype")
                }
            }
            is Subroutine -> targetRoutine.asmReturnvaluesRegisters.single().registerOrPair!!
            else -> throw AssemblyError("invalid call target")
        }
    }

    internal fun signExtendAYlsb(valueDt: DataType) {
        // sign extend signed byte in A to full word in AY
        when(valueDt) {
            DataType.UBYTE -> out("  ldy  #0")
            DataType.BYTE -> out("""
                ldy  #0
                cmp  #$80
                bcc  +
                dey
+
            """)
            else -> throw AssemblyError("need byte type")
        }
    }

    internal fun signExtendStackLsb(valueDt: DataType) {
        // sign extend signed byte on stack to signed word on stack
        when(valueDt) {
            DataType.UBYTE -> {
                if(isTargetCpu(CpuType.CPU65c02))
                    out("  stz  P8ESTACK_HI+1,x")
                else
                    out("  lda  #0 |  sta  P8ESTACK_HI+1,x")
            }
            DataType.BYTE -> out("  jsr  prog8_lib.sign_extend_stack_byte")
            else -> throw AssemblyError("need byte type")
        }
    }

    internal fun signExtendVariableLsb(asmvar: String, valueDt: DataType) {
        // sign extend signed byte in a var to a full word in that variable
        when(valueDt) {
            DataType.UBYTE -> {
                if(isTargetCpu(CpuType.CPU65c02))
                    out("  stz  $asmvar+1")
                else
                    out("  lda  #0 |  sta  $asmvar+1")
            }
            DataType.BYTE -> {
                out("""
                    lda  $asmvar
                    ora  #$7f
                    bmi  +
                    lda  #0
+                   sta  $asmvar+1""")
            }
            else -> throw AssemblyError("need byte type")
        }
    }

    internal fun isZpVar(scopedName: List<String>) = scopedName in zeropage.variables

    internal fun isZpVar(variable: IdentifierReference): Boolean {
        val vardecl = variable.targetVarDecl(program)!!
        return vardecl.scopedName in zeropage.variables
    }

    internal fun jmp(asmLabel: String, indirect: Boolean=false) {
        if(indirect) {
            out("  jmp  ($asmLabel)")
        } else {
            if (isTargetCpu(CpuType.CPU65c02))
                out("  bra  $asmLabel")     // note: 64tass will convert this automatically to a jmp if the relative distance is too large
            else
                out("  jmp  $asmLabel")
        }
    }

    internal fun pointerViaIndexRegisterPossible(pointerOffsetExpr: Expression): Pair<Expression, Expression>? {
        if(pointerOffsetExpr is BinaryExpression && pointerOffsetExpr.operator=="+") {
            val leftDt = pointerOffsetExpr.left.inferType(program)
            val rightDt = pointerOffsetExpr.left.inferType(program)
            if(leftDt istype DataType.UWORD && rightDt istype DataType.UBYTE)
                return Pair(pointerOffsetExpr.left, pointerOffsetExpr.right)
            if(leftDt istype DataType.UBYTE && rightDt istype DataType.UWORD)
                return Pair(pointerOffsetExpr.right, pointerOffsetExpr.left)
            if(leftDt istype DataType.UWORD && rightDt istype DataType.UWORD) {
                // could be that the index was a constant numeric byte but converted to word, check that
                val constIdx = pointerOffsetExpr.right.constValue(program)
                if(constIdx!=null && constIdx.number.toInt()>=0 && constIdx.number.toInt()<=255) {
                    return Pair(pointerOffsetExpr.left, NumericLiteralValue(DataType.UBYTE, constIdx.number, constIdx.position))
                }
                // could be that the index was typecasted into uword, check that
                val rightTc = pointerOffsetExpr.right as? TypecastExpression
                if(rightTc!=null && rightTc.expression.inferType(program) istype DataType.UBYTE)
                    return Pair(pointerOffsetExpr.left, rightTc.expression)
                val leftTc = pointerOffsetExpr.left as? TypecastExpression
                if(leftTc!=null && leftTc.expression.inferType(program) istype DataType.UBYTE)
                    return Pair(pointerOffsetExpr.right, leftTc.expression)
            }

        }
        return null
    }

    internal fun tryOptimizedPointerAccessWithA(expr: BinaryExpression, write: Boolean): Boolean {
        // optimize pointer,indexregister if possible

        fun evalBytevalueWillClobberA(expr: Expression): Boolean {
            val dt = expr.inferType(program)
            if(dt isnot DataType.UBYTE && dt isnot DataType.BYTE)
                return true
            return when(expr) {
                is IdentifierReference -> false
                is NumericLiteralValue -> false
                is DirectMemoryRead -> expr.addressExpression !is IdentifierReference && expr.addressExpression !is NumericLiteralValue
                is TypecastExpression -> evalBytevalueWillClobberA(expr.expression)
                else -> true
            }
        }


        if(expr.operator=="+") {
            val ptrAndIndex = pointerViaIndexRegisterPossible(expr)
            if(ptrAndIndex!=null) {
                val pointervar = ptrAndIndex.first as? IdentifierReference
                when(pointervar?.targetStatement(program)) {
                    is Label -> {
                        assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                        out("  lda  ${asmSymbolName(pointervar)},y")
                        return true
                    }
                    is VarDecl, null -> {
                        if(write) {
                            if(pointervar!=null && isZpVar(pointervar)) {
                                val saveA = evalBytevalueWillClobberA(ptrAndIndex.second)
                                if(saveA)
                                    out("  pha")
                                assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                if(saveA)
                                    out("  pla")
                                out("  sta  (${asmSymbolName(pointervar)}),y")
                            } else {
                                // copy the pointer var to zp first
                                val saveA = evalBytevalueWillClobberA(ptrAndIndex.first) || evalBytevalueWillClobberA(ptrAndIndex.second)
                                if(saveA)
                                    out("  pha")
                                assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
                                assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                if(saveA)
                                    out("  pla")
                                out("  sta  (P8ZP_SCRATCH_W2),y")
                            }
                        } else {
                            if(pointervar!=null && isZpVar(pointervar)) {
                                assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                out("  lda  (${asmSymbolName(pointervar)}),y")
                            } else {
                                // copy the pointer var to zp first
                                assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
                                assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                out("  lda  (P8ZP_SCRATCH_W2),y")
                            }
                        }
                        return true
                    }
                    else -> throw AssemblyError("invalid pointervar")
                }
            }
        }
        return false
    }

    private fun translateCompareAndJumpIfTrue(expr: BinaryExpression, jump: Jump) {
        if(expr.operator !in ComparisonOperators)
            throw AssemblyError("must be comparison expression")

        // invert the comparison, so we can reuse the JumpIfFalse code generation routines
        val invertedComparisonOperator = invertedComparisonOperator(expr.operator)
            ?: throw AssemblyError("can't invert comparison $expr")

        val left = expr.left
        val right = expr.right
        val rightConstVal = right.constValue(program)

        val label = when {
            jump.generatedLabel!=null -> jump.generatedLabel!!
            jump.identifier!=null -> asmSymbolName(jump.identifier!!)
            jump.address!=null -> jump.address!!.toHex()
            else -> throw AssemblyError("weird jump")
        }
        if (rightConstVal!=null && rightConstVal.number == 0.0)
            testZeroAndJump(left, invertedComparisonOperator, label)
        else {
            val leftConstVal = left.constValue(program)
            testNonzeroComparisonAndJump(left, invertedComparisonOperator, right, label, leftConstVal, rightConstVal)
        }
    }

    private fun translateCompareAndJumpIfFalse(expr: BinaryExpression, jumpIfFalseLabel: String) {
        val left = expr.left
        val right = expr.right
        val operator = expr.operator
        val leftConstVal = left.constValue(program)
        val rightConstVal = right.constValue(program)

        if (rightConstVal!=null && rightConstVal.number == 0.0)
            testZeroAndJump(left, operator, jumpIfFalseLabel)
        else
            testNonzeroComparisonAndJump(left, operator, right, jumpIfFalseLabel, leftConstVal, rightConstVal)
    }

    private fun testZeroAndJump(
        left: Expression,
        operator: String,
        jumpIfFalseLabel: String
    ) {
        val dt = left.inferType(program).getOr(DataType.UNDEFINED)
        if(dt in IntegerDatatypes && left is IdentifierReference)
            return testVariableZeroAndJump(left, dt, operator, jumpIfFalseLabel)

        when(dt) {
            DataType.UBYTE, DataType.UWORD -> {
                if(operator=="<") {
                    out("  jmp  $jumpIfFalseLabel")
                    return
                } else if(operator==">=") {
                    return
                }
                if(dt==DataType.UBYTE) {
                    assignExpressionToRegister(left, RegisterOrPair.A, false)
                    if (left is FunctionCallExpression && !left.isSimple)
                        out("  cmp  #0")
                } else {
                    assignExpressionToRegister(left, RegisterOrPair.AY, false)
                    out("  sty  P8ZP_SCRATCH_B1 |  ora  P8ZP_SCRATCH_B1")
                }
                when (operator) {
                    "==" -> out("  bne  $jumpIfFalseLabel")
                    "!=" -> out("  beq  $jumpIfFalseLabel")
                    ">" -> out("  beq  $jumpIfFalseLabel")
                    "<=" -> out("  bne  $jumpIfFalseLabel")
                    else -> throw AssemblyError("invalid comparison operator $operator")
                }
            }
            DataType.BYTE -> {
                assignExpressionToRegister(left, RegisterOrPair.A, true)
                if (left is FunctionCallExpression && !left.isSimple)
                    out("  cmp  #0")
                when (operator) {
                    "==" -> out("  bne  $jumpIfFalseLabel")
                    "!=" -> out("  beq  $jumpIfFalseLabel")
                    ">" -> out("  beq  $jumpIfFalseLabel |  bmi  $jumpIfFalseLabel")
                    "<" -> out("  bpl  $jumpIfFalseLabel")
                    ">=" -> out("  bmi  $jumpIfFalseLabel")
                    "<=" -> out("""
                          beq  +
                          bpl  $jumpIfFalseLabel
                      +   """)
                    else -> throw AssemblyError("invalid comparison operator $operator")
                }
            }
            DataType.WORD -> {
                assignExpressionToRegister(left, RegisterOrPair.AY, true)
                when (operator) {
                    "==" -> out("  bne  $jumpIfFalseLabel |  cpy  #0 |  bne  $jumpIfFalseLabel")
                    "!=" -> out("  sty  P8ZP_SCRATCH_B1 |  ora  P8ZP_SCRATCH_B1 |  beq  $jumpIfFalseLabel")
                    ">" -> out("""
                            cpy  #0
                            bmi  $jumpIfFalseLabel
                            bne  +
                            cmp  #0
                            beq  $jumpIfFalseLabel
                        +   """)
                    "<" -> out("  cpy  #0 |  bpl  $jumpIfFalseLabel")
                    ">=" -> out("  cpy  #0 |  bmi  $jumpIfFalseLabel")
                    "<=" -> out("""
                            cpy  #0
                            bmi  +
                            bne  $jumpIfFalseLabel
                            cmp  #0
                            bne  $jumpIfFalseLabel
                        +   """)
                    else -> throw AssemblyError("invalid comparison operator $operator")
                }
            }
            DataType.FLOAT -> {
                assignExpressionToRegister(left, RegisterOrPair.FAC1)
                out("  jsr  floats.SIGN")   // SIGN(fac1) to A, $ff, $0, $1 for negative, zero, positive
                when (operator) {
                    "==" -> out("  bne  $jumpIfFalseLabel")
                    "!=" -> out("  beq  $jumpIfFalseLabel")
                    ">" -> out("  bmi  $jumpIfFalseLabel |  beq  $jumpIfFalseLabel")
                    "<" -> out("  bpl  $jumpIfFalseLabel")
                    ">=" -> out("  bmi  $jumpIfFalseLabel")
                    "<=" -> out("  cmp  #1 |  beq  $jumpIfFalseLabel")
                    else -> throw AssemblyError("invalid comparison operator $operator")
                }
            }
            else -> {
                throw AssemblyError("invalid dt")
            }
        }
    }

    private fun testVariableZeroAndJump(variable: IdentifierReference, dt: DataType, operator: String, jumpIfFalseLabel: String) {
        // optimized code if the expression is just an identifier (variable)
        val varname = asmVariableName(variable)
        when(dt) {
            DataType.UBYTE -> when(operator) {
                "==" -> out("  lda  $varname |  bne  $jumpIfFalseLabel")
                "!=" -> out("  lda  $varname |  beq  $jumpIfFalseLabel")
                ">"  -> out("  lda  $varname |  beq  $jumpIfFalseLabel")
                "<"  -> out("  bra  $jumpIfFalseLabel")
                ">=" -> {}
                "<=" -> out("  lda  $varname |  bne  $jumpIfFalseLabel")
                else -> throw AssemblyError("invalid operator")
            }
            DataType.BYTE -> when(operator) {
                "==" -> out("  lda  $varname |  bne  $jumpIfFalseLabel")
                "!=" -> out("  lda  $varname |  beq  $jumpIfFalseLabel")
                ">"  -> out("  lda  $varname |  beq  $jumpIfFalseLabel |  bmi  $jumpIfFalseLabel")
                "<"  -> out("  lda  $varname |  bpl  $jumpIfFalseLabel")
                ">=" -> out("  lda  $varname |  bmi  $jumpIfFalseLabel")
                "<=" -> out("""
                            lda  $varname
                            beq  +
                            bpl  $jumpIfFalseLabel
                      +     """)
                else -> throw AssemblyError("invalid operator")
            }
            DataType.UWORD -> when(operator) {
                "==" -> out("  lda  $varname |  ora  $varname+1 |  bne  $jumpIfFalseLabel")
                "!=" -> out("  lda  $varname |  ora  $varname+1 |  beq  $jumpIfFalseLabel")
                ">"  -> out("  lda  $varname |  ora  $varname+1 |  beq  $jumpIfFalseLabel")
                "<"  -> out("  bra  $jumpIfFalseLabel")
                ">=" -> {}
                "<=" -> out("  lda  $varname |  ora  $varname+1 |  bne  $jumpIfFalseLabel")
                else -> throw AssemblyError("invalid operator")
            }
            DataType.WORD -> when (operator) {
                "==" -> out("  lda  $varname |  bne  $jumpIfFalseLabel |  lda  $varname+1  |  bne  $jumpIfFalseLabel")
                "!=" -> out("  lda  $varname |  ora  $varname+1 |  beq  $jumpIfFalseLabel")
                ">"  -> out("""
                            lda  $varname+1
                            bmi  $jumpIfFalseLabel
                            bne  +
                            lda  $varname
                            beq  $jumpIfFalseLabel
                        +   """)
                "<"  -> out("  lda  $varname+1 |  bpl  $jumpIfFalseLabel")
                ">=" -> out("  lda  $varname+1 |  bmi  $jumpIfFalseLabel")
                "<=" -> out("""
                            lda  $varname+1
                            bmi  +
                            bne  $jumpIfFalseLabel
                            lda  $varname
                            bne  $jumpIfFalseLabel
                        +   """)
                else -> throw AssemblyError("invalid comparison operator $operator")
            }
            else -> throw AssemblyError("invalid dt")
        }
    }

    private fun testNonzeroComparisonAndJump(
        left: Expression,
        operator: String,
        right: Expression,
        jumpIfFalseLabel: String,
        leftConstVal: NumericLiteralValue?,
        rightConstVal: NumericLiteralValue?
    ) {
        val dt = left.inferType(program).getOrElse { throw AssemblyError("unknown dt") }

        when (operator) {
            "==" -> {
                when (dt) {
                    in ByteDatatypes -> translateByteEqualsJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    in WordDatatypes -> translateWordEqualsJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> translateFloatEqualsJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.STR -> translateStringEqualsJump(left as IdentifierReference, right as IdentifierReference, jumpIfFalseLabel)
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            "!=" -> {
                when (dt) {
                    in ByteDatatypes -> translateByteNotEqualsJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    in WordDatatypes -> translateWordNotEqualsJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> translateFloatNotEqualsJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.STR -> translateStringNotEqualsJump(left as IdentifierReference, right as IdentifierReference, jumpIfFalseLabel)
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            "<" -> {
                when(dt) {
                    DataType.UBYTE -> translateUbyteLessJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.BYTE -> translateByteLessJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.UWORD -> translateUwordLessJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.WORD -> translateWordLessJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> translateFloatLessJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.STR -> translateStringLessJump(left as IdentifierReference, right as IdentifierReference, jumpIfFalseLabel)
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            "<=" -> {
                when(dt) {
                    DataType.UBYTE -> translateUbyteLessOrEqualJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.BYTE -> translateByteLessOrEqualJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.UWORD -> translateUwordLessOrEqualJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.WORD -> translateWordLessOrEqualJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> translateFloatLessOrEqualJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.STR -> translateStringLessOrEqualJump(left as IdentifierReference, right as IdentifierReference, jumpIfFalseLabel)
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            ">" -> {
                when(dt) {
                    DataType.UBYTE -> translateUbyteGreaterJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.BYTE -> translateByteGreaterJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.UWORD -> translateUwordGreaterJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.WORD -> translateWordGreaterJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> translateFloatGreaterJump(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.STR -> translateStringGreaterJump(left as IdentifierReference, right as IdentifierReference, jumpIfFalseLabel)
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            ">=" -> {
                when(dt) {
                    DataType.UBYTE -> translateUbyteGreaterOrEqualJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.BYTE -> translateByteGreaterOrEqualJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.UWORD -> translateUwordGreaterOrEqualJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.WORD -> translateWordGreaterOrEqualJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> translateFloatGreaterOrEqualJump(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.STR -> translateStringGreaterOrEqualJump(left as IdentifierReference, right as IdentifierReference, jumpIfFalseLabel)
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
        }
    }

    private fun translateFloatLessJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(leftConstVal!=null && rightConstVal!=null) {
            throw AssemblyError("const-compare should have been optimized away")
        }
        else if(leftConstVal!=null && right is IdentifierReference) {
            throw AssemblyError("const-compare should have been optimized to have const as right operand")
        }
        else if(left is IdentifierReference && rightConstVal!=null) {
            val leftName = asmVariableName(left)
            val rightName = getFloatAsmConst(rightConstVal.number)
            out("""
                lda  #<$rightName
                ldy  #>$rightName
                sta  P8ZP_SCRATCH_W2
                sty  P8ZP_SCRATCH_W2+1
                lda  #<$leftName
                ldy  #>$leftName
                jsr  floats.vars_less_f
                beq  $jumpIfFalseLabel""")
        }
        else if(left is IdentifierReference && right is IdentifierReference) {
            val leftName = asmVariableName(left)
            val rightName = asmVariableName(right)
            out("""
                lda  #<$rightName
                ldy  #>$rightName
                sta  P8ZP_SCRATCH_W2
                sty  P8ZP_SCRATCH_W2+1
                lda  #<$leftName
                ldy  #>$leftName
                jsr  floats.vars_less_f
                beq  $jumpIfFalseLabel""")
        } else {
            val subroutine = left.definingSubroutine!!
            subroutine.asmGenInfo.usedFloatEvalResultVar1 = true
            assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT, subroutine)
            assignExpressionToRegister(left, RegisterOrPair.FAC1)
            out("""
                lda  #<$subroutineFloatEvalResultVar1
                ldy  #>$subroutineFloatEvalResultVar1
                jsr  floats.var_fac1_less_f
                beq  $jumpIfFalseLabel""")
        }
    }

    private fun translateFloatLessOrEqualJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(leftConstVal!=null && rightConstVal!=null) {
            throw AssemblyError("const-compare should have been optimized away")
        }
        else if(leftConstVal!=null && right is IdentifierReference) {
            throw AssemblyError("const-compare should have been optimized to have const as right operand")
        }
        else if(left is IdentifierReference && rightConstVal!=null) {
            val leftName = asmVariableName(left)
            val rightName = getFloatAsmConst(rightConstVal.number)
            out("""
                lda  #<$rightName
                ldy  #>$rightName
                sta  P8ZP_SCRATCH_W2
                sty  P8ZP_SCRATCH_W2+1
                lda  #<$leftName
                ldy  #>$leftName
                jsr  floats.vars_lesseq_f
                beq  $jumpIfFalseLabel""")
        }
        else if(left is IdentifierReference && right is IdentifierReference) {
            val leftName = asmVariableName(left)
            val rightName = asmVariableName(right)
            out("""
                lda  #<$rightName
                ldy  #>$rightName
                sta  P8ZP_SCRATCH_W2
                sty  P8ZP_SCRATCH_W2+1
                lda  #<$leftName
                ldy  #>$leftName
                jsr  floats.vars_lesseq_f
                beq  $jumpIfFalseLabel""")
        } else {
            val subroutine = left.definingSubroutine!!
            subroutine.asmGenInfo.usedFloatEvalResultVar1 = true
            assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT, subroutine)
            assignExpressionToRegister(left, RegisterOrPair.FAC1)
            out("""
                lda  #<$subroutineFloatEvalResultVar1
                ldy  #>$subroutineFloatEvalResultVar1
                jsr  floats.var_fac1_lesseq_f
                beq  $jumpIfFalseLabel""")
        }
    }

    private fun translateFloatGreaterJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(leftConstVal!=null && rightConstVal!=null) {
            throw AssemblyError("const-compare should have been optimized away")
        }
        else if(left is IdentifierReference && rightConstVal!=null) {
            val leftName = asmVariableName(left)
            val rightName = getFloatAsmConst(rightConstVal.number)
            out("""
                lda  #<$leftName
                ldy  #>$leftName
                sta  P8ZP_SCRATCH_W2
                sty  P8ZP_SCRATCH_W2+1
                lda  #<$rightName
                ldy  #>$rightName
                jsr  floats.vars_less_f
                beq  $jumpIfFalseLabel""")
        }
        else if(leftConstVal!=null && right is IdentifierReference) {
            throw AssemblyError("const-compare should have been optimized to have const as right operand")
        }
        else if(left is IdentifierReference && right is IdentifierReference) {
            val leftName = asmVariableName(left)
            val rightName = asmVariableName(right)
            out("""
                lda  #<$leftName
                ldy  #>$leftName
                sta  P8ZP_SCRATCH_W2
                sty  P8ZP_SCRATCH_W2+1
                lda  #<$rightName
                ldy  #>$rightName
                jsr  floats.vars_less_f
                beq  $jumpIfFalseLabel""")
        } else {
            val subroutine = left.definingSubroutine!!
            subroutine.asmGenInfo.usedFloatEvalResultVar1 = true
            assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT, subroutine)
            assignExpressionToRegister(left, RegisterOrPair.FAC1)
            out("""
                lda  #<$subroutineFloatEvalResultVar1
                ldy  #>$subroutineFloatEvalResultVar1
                jsr  floats.var_fac1_greater_f
                beq  $jumpIfFalseLabel""")
        }
    }

    private fun translateFloatGreaterOrEqualJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(leftConstVal!=null && rightConstVal!=null) {
            throw AssemblyError("const-compare should have been optimized away")
        }
        else if(left is IdentifierReference && rightConstVal!=null) {
            val leftName = asmVariableName(left)
            val rightName = getFloatAsmConst(rightConstVal.number)
            out("""
                lda  #<$leftName
                ldy  #>$leftName
                sta  P8ZP_SCRATCH_W2
                sty  P8ZP_SCRATCH_W2+1
                lda  #<$rightName
                ldy  #>$rightName
                jsr  floats.vars_lesseq_f
                beq  $jumpIfFalseLabel""")
        }
        else if(leftConstVal!=null && right is IdentifierReference) {
            throw AssemblyError("const-compare should have been optimized to have const as right operand")
        }
        else if(left is IdentifierReference && right is IdentifierReference) {
            val leftName = asmVariableName(left)
            val rightName = asmVariableName(right)
            out("""
                lda  #<$leftName
                ldy  #>$leftName
                sta  P8ZP_SCRATCH_W2
                sty  P8ZP_SCRATCH_W2+1
                lda  #<$rightName
                ldy  #>$rightName
                jsr  floats.vars_lesseq_f
                beq  $jumpIfFalseLabel""")
        } else {
            val subroutine = left.definingSubroutine!!
            subroutine.asmGenInfo.usedFloatEvalResultVar1 = true
            assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT, subroutine)
            assignExpressionToRegister(left, RegisterOrPair.FAC1)
            out("""
                lda  #<$subroutineFloatEvalResultVar1
                ldy  #>$subroutineFloatEvalResultVar1
                jsr  floats.var_fac1_greatereq_f
                beq  $jumpIfFalseLabel""")
        }
    }

    private fun translateUbyteLessJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(cmpOperand: String) {
            out("  cmp  $cmpOperand |  bcs  $jumpIfFalseLabel")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    return if(rightConstVal.number.toInt()!=0) {
                        assignExpressionToRegister(left, RegisterOrPair.A)
                        code("#${rightConstVal.number.toInt()}")
                    }
                    else
                        jmp(jumpIfFalseLabel)
                }
                else if (left is DirectMemoryRead) {
                    return if(rightConstVal.number.toInt()!=0) {
                        translateDirectMemReadExpressionToRegAorStack(left, false)
                        code("#${rightConstVal.number.toInt()}")
                    }
                    else
                        jmp(jumpIfFalseLabel)
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE, null)
        assignExpressionToRegister(left, RegisterOrPair.A)
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateByteLessJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(sbcOperand: String) {
            out("""
                sec
                sbc  $sbcOperand
                bvc  +
                eor  #$80
+               bpl  $jumpIfFalseLabel""")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    assignExpressionToRegister(left, RegisterOrPair.A)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number}")
                    else
                        out("  bpl  $jumpIfFalseLabel")
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE, null)
        assignExpressionToRegister(left, RegisterOrPair.A)
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateUwordLessJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(msbCpyOperand: String, lsbCmpOperand: String) {
            out("""
                cpy  $msbCpyOperand
                bcc  +
                bne  $jumpIfFalseLabel
                cmp  $lsbCmpOperand
                bcs  $jumpIfFalseLabel
+""")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    return if(rightConstVal.number.toInt()!=0) {
                        assignExpressionToRegister(left, RegisterOrPair.AY)
                        code("#>${rightConstVal.number.toInt()}", "#<${rightConstVal.number.toInt()}")
                    }
                    else
                        jmp(jumpIfFalseLabel)
                }
            }
        }

        if(wordJumpForSimpleRightOperands(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
        assignExpressionToRegister(left, RegisterOrPair.AY)
        return out("  jsr  prog8_lib.reg_less_uw |  beq  $jumpIfFalseLabel")
    }

    private fun translateWordLessJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(msbCpyOperand: String, lsbCmpOperand: String) {
            out("""
                cmp  $lsbCmpOperand
                tya
                sbc  $msbCpyOperand
                bvc  +
                eor  #$80
+               bpl  $jumpIfFalseLabel""")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    return if(rightConstVal.number.toInt()!=0) {
                        assignExpressionToRegister(left, RegisterOrPair.AY)
                        code("#>${rightConstVal.number.toInt()}", "#<${rightConstVal.number.toInt()}")
                    }
                    else {
                        val name = asmVariableName(left)
                        out("  lda  $name+1 |  bpl  $jumpIfFalseLabel")
                    }
                }
            }
        }

        if(wordJumpForSimpleRightOperands(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
        assignExpressionToRegister(left, RegisterOrPair.AY)
        return out("  jsr  prog8_lib.reg_less_w |  beq  $jumpIfFalseLabel")
    }

    private fun translateUbyteGreaterJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(cmpOperand: String) {
            out("""
                cmp  $cmpOperand
                bcc  $jumpIfFalseLabel
                beq  $jumpIfFalseLabel""")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    assignExpressionToRegister(left, RegisterOrPair.A)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  beq $jumpIfFalseLabel")
                }
                else if (left is DirectMemoryRead) {
                    translateDirectMemReadExpressionToRegAorStack(left, false)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  beq  $jumpIfFalseLabel")
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE, null)
        assignExpressionToRegister(left, RegisterOrPair.A)
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateByteGreaterJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(sbcOperand: String) {
            out("""
                clc
                sbc  $sbcOperand
                bvc  +
                eor  #$80
+               bpl  +
                bmi  $jumpIfFalseLabel
+""")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    assignExpressionToRegister(left, RegisterOrPair.A)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number}")
                    else
                        out("  bmi  $jumpIfFalseLabel |  beq  $jumpIfFalseLabel")
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE, null)
        assignExpressionToRegister(left, RegisterOrPair.A)
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateUwordGreaterJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(msbCpyOperand: String, lsbCmpOperand: String) {
            out("""
                cpy  $msbCpyOperand
                bcc  $jumpIfFalseLabel
                bne  +
                cmp  $lsbCmpOperand
                bcc  $jumpIfFalseLabel
+               beq  $jumpIfFalseLabel
""")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    return if(rightConstVal.number.toInt()!=0) {
                        assignExpressionToRegister(left, RegisterOrPair.AY)
                        code("#>${rightConstVal.number.toInt()}", "#<${rightConstVal.number.toInt()}")
                    }
                    else {
                        val name = asmVariableName(left)
                        out("""
                            lda  $name
                            ora  $name+1
                            beq  $jumpIfFalseLabel""")
                    }
                }
            }
        }

        if(wordJumpForSimpleRightOperands(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
        assignExpressionToRegister(left, RegisterOrPair.AY)
        return code("P8ZP_SCRATCH_W2+1", "P8ZP_SCRATCH_W2")
    }

    private fun translateWordGreaterJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(msbCmpOperand: String, lsbCmpOperand: String) {
            out("""
                cmp  $lsbCmpOperand
                tya
                sbc  $msbCmpOperand
                bvc  +
                eor  #$80
+               bpl  $jumpIfFalseLabel""")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    return if(rightConstVal.number.toInt()!=0) {
                        assignExpressionToRegister(right, RegisterOrPair.AY)
                        val varname = asmVariableName(left)
                        code("$varname+1", varname)
                    }
                    else {
                        val name = asmVariableName(left)
                        out("""
                            lda  $name+1
                            bmi  $jumpIfFalseLabel
                            lda  $name
                            beq  $jumpIfFalseLabel""")
                    }
                }
            }
        }

        if(wordJumpForSimpleLeftOperand(left, right, ::code))
            return

        assignExpressionToVariable(left, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
        assignExpressionToRegister(right, RegisterOrPair.AY)
        return out("  jsr  prog8_lib.reg_less_w |  beq  $jumpIfFalseLabel")
    }

    private fun translateUbyteLessOrEqualJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(cmpOperand: String) {
            out("""
                cmp  $cmpOperand
                beq  +
                bcs  $jumpIfFalseLabel
+""")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    assignExpressionToRegister(left, RegisterOrPair.A)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  bne  $jumpIfFalseLabel")
                }
                else if (left is DirectMemoryRead) {
                    translateDirectMemReadExpressionToRegAorStack(left, false)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  bne  $jumpIfFalseLabel")
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE, null)
        assignExpressionToRegister(left, RegisterOrPair.A)
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateByteLessOrEqualJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        fun code(sbcOperand: String) {
            out("""
                clc
                sbc  $sbcOperand
                bvc  +
                eor  #$80
+               bpl  $jumpIfFalseLabel""")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    assignExpressionToRegister(left, RegisterOrPair.A)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number}")
                    else
                        out("""
                            beq  +
                            bpl  $jumpIfFalseLabel
+""")
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE, null)
        assignExpressionToRegister(left, RegisterOrPair.A)
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateUwordLessOrEqualJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(msbCpyOperand: String, lsbCmpOperand: String) {
            out("""
                cpy  $msbCpyOperand
                beq  +
                bcc  ++
                bcs  $jumpIfFalseLabel
+               cmp  $lsbCmpOperand
                bcc  +
                beq  +
                bne  $jumpIfFalseLabel
+""")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    return if(rightConstVal.number.toInt()!=0) {
                        assignExpressionToRegister(left, RegisterOrPair.AY)
                        code("#>${rightConstVal.number.toInt()}", "#<${rightConstVal.number.toInt()}")
                    }
                    else {
                        val name = asmVariableName(left)
                        out("""
                            lda  $name
                            ora  $name+1
                            bne  $jumpIfFalseLabel""")
                    }
                }
            }
        }

        if(wordJumpForSimpleRightOperands(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
        assignExpressionToRegister(left, RegisterOrPair.AY)
        return out("  jsr  prog8_lib.reg_lesseq_uw |  beq  $jumpIfFalseLabel")
    }

    private fun translateWordLessOrEqualJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(leftName: String) {
            out("""
                cmp  $leftName
                tya
                sbc  $leftName+1
                bvc  +
                eor  #$80
+	        	bmi  $jumpIfFalseLabel
""")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    return if(rightConstVal.number.toInt()!=0) {
                        assignExpressionToRegister(right, RegisterOrPair.AY)
                        code(asmVariableName(left))
                    }
                    else {
                        val name = asmVariableName(left)
                        out("""
                            lda  $name+1
                            bmi  +
                            bne  $jumpIfFalseLabel
                            lda  $name
                            bne  $jumpIfFalseLabel
+""")
                    }
                }
            }
        }

        if(left is IdentifierReference) {
            assignExpressionToRegister(right, RegisterOrPair.AY)
            return code(asmVariableName(left))
        }

        assignExpressionToVariable(left, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
        assignExpressionToRegister(right, RegisterOrPair.AY)
        return out("  jsr  prog8_lib.reg_lesseq_w |  beq  $jumpIfFalseLabel")
    }

    private fun translateUbyteGreaterOrEqualJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(cmpOperand: String) {
            out("  cmp  $cmpOperand |  bcc  $jumpIfFalseLabel")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    if(rightConstVal.number.toInt()!=0) {
                        assignExpressionToRegister(left, RegisterOrPair.A)
                        code("#${rightConstVal.number.toInt()}")
                    }
                    return
                }
                else if (left is DirectMemoryRead) {
                    if(rightConstVal.number.toInt()!=0) {
                        translateDirectMemReadExpressionToRegAorStack(left, false)
                        code("#${rightConstVal.number.toInt()}")
                    }
                    return
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE, null)
        assignExpressionToRegister(left, RegisterOrPair.A)
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateByteGreaterOrEqualJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        fun code(sbcOperand: String) {
            out("""
                sec
                sbc  $sbcOperand
                bvc  +
                eor  #$80
+               bmi  $jumpIfFalseLabel""")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    assignExpressionToRegister(left, RegisterOrPair.A)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number}")
                    else
                        out("  bmi  $jumpIfFalseLabel")
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE, null)
        assignExpressionToRegister(left, RegisterOrPair.A)
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateUwordGreaterOrEqualJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(msbCpyOperand: String, lsbCmpOperand: String) {
            out("""
                cpy  $msbCpyOperand
                bcc  $jumpIfFalseLabel
                bne  +
                cmp  $lsbCmpOperand
                bcc  $jumpIfFalseLabel
+""")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    if(rightConstVal.number.toInt()!=0) {
                        assignExpressionToRegister(left, RegisterOrPair.AY)
                        code("#>${rightConstVal.number.toInt()}", "#<${rightConstVal.number.toInt()}")
                        return
                    }
                }
            }
        }

        if(wordJumpForSimpleRightOperands(left, right, ::code))
            return

        assignExpressionToVariable(left, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
        assignExpressionToRegister(right, RegisterOrPair.AY)
        return out("  jsr  prog8_lib.reg_lesseq_uw |  beq  $jumpIfFalseLabel")
    }

    private fun translateWordGreaterOrEqualJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(msbCpyOperand: String, lsbCmpOperand: String) {
            out("""
        		cmp  $lsbCmpOperand
        		tya
        		sbc  $msbCpyOperand
        		bvc  +
        		eor  #$80
+       		bmi  $jumpIfFalseLabel""")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    return if(rightConstVal.number.toInt()!=0) {
                        assignExpressionToRegister(left, RegisterOrPair.AY)
                        code("#>${rightConstVal.number.toInt()}", "#<${rightConstVal.number.toInt()}")
                    }
                    else {
                        val name = asmVariableName(left)
                        out(" lda  $name+1 |  bmi  $jumpIfFalseLabel")
                    }
                }
            }
        }

        if(wordJumpForSimpleRightOperands(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
        assignExpressionToRegister(left, RegisterOrPair.AY)
        return out("  jsr  prog8_lib.reg_lesseq_w |  beq  $jumpIfFalseLabel")
    }

    private fun translateByteEqualsJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        fun code(cmpOperand: String) {
            out("  cmp  $cmpOperand |  bne  $jumpIfFalseLabel")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal!=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    assignExpressionToRegister(left, RegisterOrPair.A)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  bne  $jumpIfFalseLabel")
                }
                else if (left is DirectMemoryRead) {
                    translateDirectMemReadExpressionToRegAorStack(left, false)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  bne  $jumpIfFalseLabel")
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE, null)
        assignExpressionToRegister(left, RegisterOrPair.A)
        out("  cmp  P8ZP_SCRATCH_B1 |  bne  $jumpIfFalseLabel")
    }

    private fun translateByteNotEqualsJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        fun code(cmpOperand: String) {
            out("  cmp  $cmpOperand |  beq  $jumpIfFalseLabel")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal==leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    assignExpressionToRegister(left, RegisterOrPair.A)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  beq  $jumpIfFalseLabel")
                }
                else if (left is DirectMemoryRead) {
                    translateDirectMemReadExpressionToRegAorStack(left, false)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  beq  $jumpIfFalseLabel")
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE, null)
        assignExpressionToRegister(left, RegisterOrPair.A)
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateWordEqualsJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal!=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmVariableName(left)
                    if(rightConstVal.number!=0.0) {
                        val rightNum = rightConstVal.number.toHex()
                        out("""
                        lda  $name
                        cmp  #<$rightNum
                        bne  $jumpIfFalseLabel
                        lda  $name+1
                        cmp  #>$rightNum
                        bne  $jumpIfFalseLabel""")
                    }
                    else {
                        out("""
                        lda  $name
                        bne  $jumpIfFalseLabel
                        lda  $name+1
                        bne  $jumpIfFalseLabel""")
                    }
                    return
                }
            }
        }

        when (right) {
            is NumericLiteralValue -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                val number = right.number.toHex()
                out("""
                    cmp  #<$number
                    bne  $jumpIfFalseLabel
                    cpy  #>$number
                    bne  $jumpIfFalseLabel
                    """)
            }
            is IdentifierReference -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                out("""
                    cmp  ${asmVariableName(right)}
                    bne  $jumpIfFalseLabel
                    cpy  ${asmVariableName(right)}+1
                    bne  $jumpIfFalseLabel
                    """)
            }
            is AddressOf -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                val name = asmSymbolName(right.identifier)
                out("""
                    cmp  #<$name
                    bne  $jumpIfFalseLabel
                    cpy  #>$name
                    bne  $jumpIfFalseLabel
                    """)
            }
            else -> {
                assignExpressionToVariable(right, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
                assignExpressionToRegister(left, RegisterOrPair.AY)
                out("""
                    cmp  P8ZP_SCRATCH_W2
                    bne  $jumpIfFalseLabel
                    cpy  P8ZP_SCRATCH_W2+1
                    bne  $jumpIfFalseLabel""")
            }
        }

    }

    private fun translateWordNotEqualsJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal==leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmVariableName(left)
                    if(rightConstVal.number.toInt()!=0) {
                        val number = rightConstVal.number.toHex()
                        out("""
                        lda  $name
                        cmp  #<$number
                        bne  +
                        lda  $name+1
                        cmp  #>$number
                        beq  $jumpIfFalseLabel
+""")
                    }
                    else
                        out("""
                        lda  $name
                        bne  +
                        lda  $name+1
                        beq  $jumpIfFalseLabel
+""")
                    return
                }
            }
        }

        when (right) {
            is NumericLiteralValue -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                val number = right.number.toHex()
                out("""
                    cmp  #<$number
                    bne  +
                    cpy  #>$number
                    beq  $jumpIfFalseLabel
+""")
            }
            is IdentifierReference -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                out("""
                    cmp  ${asmVariableName(right)}
                    bne  +
                    cpy  ${asmVariableName(right)}+1
                    beq  $jumpIfFalseLabel
+""")
            }
            is AddressOf -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                val name = asmSymbolName(right.identifier)
                out("""
                    cmp  #<$name
                    bne  +
                    cpy  #>$name
                    beq  $jumpIfFalseLabel
+""")
            }
            else -> {
                assignExpressionToVariable(right, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
                assignExpressionToRegister(left, RegisterOrPair.AY)
                out("""
                    cmp  P8ZP_SCRATCH_W2
                    bne  +
                    cpy  P8ZP_SCRATCH_W2+1
                    beq  $jumpIfFalseLabel
+"""
                )
            }
        }

    }

    private fun translateFloatEqualsJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal!=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmVariableName(left)
                    when(rightConstVal.number)
                    {
                        0.0 -> {
                            out("""
                                lda  $name
                                clc
                                adc  $name+1
                                adc  $name+2
                                adc  $name+3
                                adc  $name+4
                                bne  $jumpIfFalseLabel""")
                            return
                        }
                        1.0 -> {
                            out("""
                                lda  $name
                                cmp  #129
                                bne  $jumpIfFalseLabel
                                lda  $name+1
                                clc
                                adc  $name+2
                                adc  $name+3
                                adc  $name+4
                                bne  $jumpIfFalseLabel""")
                            return
                        }
                    }
                }
            }
        }

        if(leftConstVal!=null && rightConstVal!=null) {
            throw AssemblyError("const-compare should have been optimized away")
        }
        else if(leftConstVal!=null && right is IdentifierReference) {
            throw AssemblyError("const-compare should have been optimized to have const as right operand")
        }
        else if(left is IdentifierReference && rightConstVal!=null) {
            val leftName = asmVariableName(left)
            val rightName = getFloatAsmConst(rightConstVal.number)
            out("""
                lda  #<$leftName
                ldy  #>$leftName
                sta  P8ZP_SCRATCH_W1
                sty  P8ZP_SCRATCH_W1+1
                lda  #<$rightName
                ldy  #>$rightName
                jsr  floats.vars_equal_f
                beq  $jumpIfFalseLabel""")
        }
        else if(left is IdentifierReference && right is IdentifierReference) {
            val leftName = asmVariableName(left)
            val rightName = asmVariableName(right)
            out("""
                lda  #<$leftName
                ldy  #>$leftName
                sta  P8ZP_SCRATCH_W1
                sty  P8ZP_SCRATCH_W1+1
                lda  #<$rightName
                ldy  #>$rightName
                jsr  floats.vars_equal_f
                beq  $jumpIfFalseLabel""")
        } else {
            val subroutine = left.definingSubroutine!!
            subroutine.asmGenInfo.usedFloatEvalResultVar1 = true
            assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT, subroutine)
            assignExpressionToRegister(left, RegisterOrPair.FAC1)
            out("""
                lda  #<$subroutineFloatEvalResultVar1
                ldy  #>$subroutineFloatEvalResultVar1
                jsr  floats.var_fac1_notequal_f
                bne  $jumpIfFalseLabel""")
        }
    }

    private fun translateFloatNotEqualsJump(left: Expression, right: Expression, leftConstVal: NumericLiteralValue?, rightConstVal: NumericLiteralValue?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal==leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is IdentifierReference) {
                    val name = asmVariableName(left)
                    when(rightConstVal.number)
                    {
                        0.0 -> {
                            out("""
                                lda  $name
                                clc
                                adc  $name+1
                                adc  $name+2
                                adc  $name+3
                                adc  $name+4
                                beq  $jumpIfFalseLabel""")
                            return
                        }
                        1.0 -> {
                            out("""
                                lda  $name
                                cmp  #129
                                bne  +
                                lda  $name+1
                                clc
                                adc  $name+2
                                adc  $name+3
                                adc  $name+4
                                beq  $jumpIfFalseLabel
+""")
                            return
                        }
                    }
                }
            }
        }

        if(leftConstVal!=null && rightConstVal!=null) {
            throw AssemblyError("const-compare should have been optimized away")
        }
        else if(leftConstVal!=null && right is IdentifierReference) {
            throw AssemblyError("const-compare should have been optimized to have const as right operand")
        }
        else if(left is IdentifierReference && rightConstVal!=null) {
            val leftName = asmVariableName(left)
            val rightName = getFloatAsmConst(rightConstVal.number)
            out("""
                lda  #<$leftName
                ldy  #>$leftName
                sta  P8ZP_SCRATCH_W1
                sty  P8ZP_SCRATCH_W1+1
                lda  #<$rightName
                ldy  #>$rightName
                jsr  floats.vars_equal_f
                bne  $jumpIfFalseLabel""")
        }
        else if(left is IdentifierReference && right is IdentifierReference) {
            val leftName = asmVariableName(left)
            val rightName = asmVariableName(right)
            out("""
                lda  #<$leftName
                ldy  #>$leftName
                sta  P8ZP_SCRATCH_W1
                sty  P8ZP_SCRATCH_W1+1
                lda  #<$rightName
                ldy  #>$rightName
                jsr  floats.vars_equal_f
                bne  $jumpIfFalseLabel""")
        } else {
            val subroutine = left.definingSubroutine!!
            subroutine.asmGenInfo.usedFloatEvalResultVar1 = true
            assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT, subroutine)
            assignExpressionToRegister(left, RegisterOrPair.FAC1)
            out("""
                lda  #<$subroutineFloatEvalResultVar1
                ldy  #>$subroutineFloatEvalResultVar1
                jsr  floats.var_fac1_notequal_f
                beq  $jumpIfFalseLabel""")
        }
    }

    private fun translateStringEqualsJump(left: IdentifierReference, right: IdentifierReference, jumpIfFalseLabel: String) {
        val leftNam = asmVariableName(left)
        val rightNam = asmVariableName(right)
        out("""
            lda  #<$rightNam
            sta  P8ZP_SCRATCH_W2
            lda  #>$rightNam
            sta  P8ZP_SCRATCH_W2+1
            lda  #<$leftNam
            ldy  #>$leftNam
            jsr  prog8_lib.strcmp_mem
            cmp  #0
            bne  $jumpIfFalseLabel""")
    }

    private fun translateStringNotEqualsJump(left: IdentifierReference, right: IdentifierReference, jumpIfFalseLabel: String) {
        val leftNam = asmVariableName(left)
        val rightNam = asmVariableName(right)
        out("""
            lda  #<$rightNam
            sta  P8ZP_SCRATCH_W2
            lda  #>$rightNam
            sta  P8ZP_SCRATCH_W2+1
            lda  #<$leftNam
            ldy  #>$leftNam
            jsr  prog8_lib.strcmp_mem
            cmp  #0
            beq  $jumpIfFalseLabel""")
    }

    private fun translateStringLessJump(left: IdentifierReference, right: IdentifierReference, jumpIfFalseLabel: String) {
        val leftNam = asmVariableName(left)
        val rightNam = asmVariableName(right)
        out("""
            lda  #<$rightNam
            sta  P8ZP_SCRATCH_W2
            lda  #>$rightNam
            sta  P8ZP_SCRATCH_W2+1
            lda  #<$leftNam
            ldy  #>$leftNam
            jsr  prog8_lib.strcmp_mem
            bpl  $jumpIfFalseLabel""")
    }

    private fun translateStringGreaterJump(left: IdentifierReference, right: IdentifierReference, jumpIfFalseLabel: String) {
        val leftNam = asmVariableName(left)
        val rightNam = asmVariableName(right)
        out("""
            lda  #<$rightNam
            sta  P8ZP_SCRATCH_W2
            lda  #>$rightNam
            sta  P8ZP_SCRATCH_W2+1
            lda  #<$leftNam
            ldy  #>$leftNam
            jsr  prog8_lib.strcmp_mem
            beq  $jumpIfFalseLabel
            bmi  $jumpIfFalseLabel""")
    }

    private fun translateStringLessOrEqualJump(left: IdentifierReference, right: IdentifierReference, jumpIfFalseLabel: String) {
        val leftNam = asmVariableName(left)
        val rightNam = asmVariableName(right)
        out("""
            lda  #<$rightNam
            sta  P8ZP_SCRATCH_W2
            lda  #>$rightNam
            sta  P8ZP_SCRATCH_W2+1
            lda  #<$leftNam
            ldy  #>$leftNam
            jsr  prog8_lib.strcmp_mem
            beq  +
            bpl  $jumpIfFalseLabel
+""")
    }

    private fun translateStringGreaterOrEqualJump(left: IdentifierReference, right: IdentifierReference, jumpIfFalseLabel: String) {
        val leftNam = asmVariableName(left)
        val rightNam = asmVariableName(right)
        out("""
            lda  #<$rightNam
            sta  P8ZP_SCRATCH_W2
            lda  #>$rightNam
            sta  P8ZP_SCRATCH_W2+1
            lda  #<$leftNam
            ldy  #>$leftNam
            jsr  prog8_lib.strcmp_mem
            beq  +
            bmi  $jumpIfFalseLabel
+""")
    }

    internal fun translateDirectMemReadExpressionToRegAorStack(expr: DirectMemoryRead, pushResultOnEstack: Boolean) {

        fun assignViaExprEval() {
            assignExpressionToVariable(expr.addressExpression, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
            if (isTargetCpu(CpuType.CPU65c02)) {
                if (pushResultOnEstack) {
                    out("  lda  (P8ZP_SCRATCH_W2) |  dex |  sta  P8ESTACK_LO+1,x")
                } else {
                    out("  lda  (P8ZP_SCRATCH_W2)")
                }
            } else {
                if (pushResultOnEstack) {
                    out("  ldy  #0 |  lda  (P8ZP_SCRATCH_W2),y |  dex |  sta  P8ESTACK_LO+1,x")
                } else {
                    out("  ldy  #0 |  lda  (P8ZP_SCRATCH_W2),y")
                }
            }
        }

        when(expr.addressExpression) {
            is NumericLiteralValue -> {
                val address = (expr.addressExpression as NumericLiteralValue).number.toInt()
                out("  lda  ${address.toHex()}")
                if(pushResultOnEstack)
                    out("  sta  P8ESTACK_LO,x |  dex")
            }
            is IdentifierReference -> {
                // the identifier is a pointer variable, so read the value from the address in it
                loadByteFromPointerIntoA(expr.addressExpression as IdentifierReference)
                if(pushResultOnEstack)
                    out("  sta  P8ESTACK_LO,x |  dex")
            }
            is BinaryExpression -> {
                if(tryOptimizedPointerAccessWithA(expr.addressExpression as BinaryExpression, false)) {
                    if(pushResultOnEstack)
                        out("  sta  P8ESTACK_LO,x |  dex")
                } else {
                    assignViaExprEval()
                }
            }
            else -> assignViaExprEval()
        }
    }

    private fun wordJumpForSimpleLeftOperand(left: Expression, right: Expression, code: (String, String)->Unit): Boolean {
        when (left) {
            is NumericLiteralValue -> {
                assignExpressionToRegister(right, RegisterOrPair.AY)
                val number = left.number.toHex()
                code("#>$number", "#<$number")
                return true
            }
            is AddressOf -> {
                assignExpressionToRegister(right, RegisterOrPair.AY)
                val name = asmSymbolName(left.identifier)
                code("#>$name", "#<$name")
                return true
            }
            is IdentifierReference -> {
                assignExpressionToRegister(right, RegisterOrPair.AY)
                val varname = asmVariableName(left)
                code("$varname+1", varname)
                return true
            }
            else -> return false
        }
    }

    private fun byteJumpForSimpleRightOperand(left: Expression, right: Expression, code: (String)->Unit): Boolean {
        if(right is NumericLiteralValue) {
            assignExpressionToRegister(left, RegisterOrPair.A)
            code("#${right.number.toHex()}")
            return true
        }
        if(right is IdentifierReference) {
            assignExpressionToRegister(left, RegisterOrPair.A)
            code(asmVariableName(right))
            return true
        }
        var memread = right as? DirectMemoryRead
        if(memread==null && right is TypecastExpression)
            memread = right.expression as? DirectMemoryRead
        if(memread!=null) {
            val address = memread.addressExpression as? NumericLiteralValue
            if(address!=null) {
                assignExpressionToRegister(left, RegisterOrPair.A)
                code(address.number.toHex())
                return true
            }
        }
        return false
    }

    private fun wordJumpForSimpleRightOperands(left: Expression, right: Expression, code: (String, String)->Unit): Boolean {
        when (right) {
            is NumericLiteralValue -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                val number = right.number.toHex()
                code("#>$number", "#<$number")
                return true
            }
            is AddressOf -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                val name = asmSymbolName(right.identifier)
                code("#>$name", "#<$name")
                return true
            }
            is IdentifierReference -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                val varname = asmVariableName(right)
                code("$varname+1", varname)
                return true
            }
            else -> return false
        }
    }

    internal fun translatePipeExpression(expressions: Iterable<Expression>, scope: Node, isStatement: Boolean, pushResultOnEstack: Boolean) {

        // TODO more efficient code generation to avoid needless assignments to the temp var

        // the first term: an expression (could be anything) producing a value.
        val subroutine = scope.definingSubroutine!!
        val firstTerm = expressions.first()
        var valueDt = firstTerm.inferType(program).getOrElse { throw FatalAstException("invalid dt") }
        var valueSource: AsmAssignSource =
            if(firstTerm is IFunctionCall) {
                val resultReg = returnRegisterOfFunction(firstTerm.target)
                assignExpressionToRegister(firstTerm, resultReg, valueDt in listOf(DataType.BYTE, DataType.WORD, DataType.FLOAT))
                AsmAssignSource(SourceStorageKind.REGISTER, program, this, valueDt, register = resultReg)
            } else {
                AsmAssignSource.fromAstSource(firstTerm, program, this)
            }

        // the 2nd to N-1 terms: unary function calls taking a single param and producing a value.
        //   directly assign their argument from the previous call's returnvalue.
        expressions.drop(1).dropLast(1).forEach {
            valueDt = functioncallAsmGen.translateUnaryFunctionCallWithArgSource(it as IdentifierReference, valueSource, false, subroutine)
            val resultReg = returnRegisterOfFunction(it)
            valueSource = AsmAssignSource(SourceStorageKind.REGISTER, program, this, valueDt, register = resultReg)
        }

        // the last term: unary function call taking a single param and optionally producing a result value.
        if(isStatement) {
            // the last term in the pipe, don't care about return var:
            functioncallAsmGen.translateUnaryFunctionCallWithArgSource(
                expressions.last() as IdentifierReference, valueSource, true, subroutine)
        } else {
            // the last term in the pipe, regular function call with returnvalue:
            valueDt = functioncallAsmGen.translateUnaryFunctionCallWithArgSource(
                expressions.last() as IdentifierReference, valueSource, false, subroutine)
            if(pushResultOnEstack) {
                when (valueDt) {
                    in ByteDatatypes -> {
                        out("  sta  P8ESTACK_LO,x |  dex")
                    }
                    in WordDatatypes -> {
                        out("  sta  P8ESTACK_LO,x |  tya |  sta  P8ESTACK_HI,x |  dex")
                    }
                    DataType.FLOAT -> {
                        out("  jsr  floats.push_fac1")
                    }
                    else -> throw AssemblyError("invalid dt")
                }
            }
        }
    }

}
