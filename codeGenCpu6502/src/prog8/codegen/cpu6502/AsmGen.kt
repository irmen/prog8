package prog8.codegen.cpu6502

import com.github.michaelbull.result.fold
import prog8.code.StNode
import prog8.code.StNodeType
import prog8.code.SymbolTable
import prog8.code.SymbolTableMaker
import prog8.code.ast.*
import prog8.code.core.*
import prog8.codegen.cpu6502.assignment.*
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.writeLines


internal const val subroutineFloatEvalResultVar1 = "prog8_float_eval_result1"
internal const val subroutineFloatEvalResultVar2 = "prog8_float_eval_result2"

class AsmGen6502(val prefixSymbols: Boolean): ICodeGeneratorBackend {
    override fun generate(
        program: PtProgram,
        symbolTable: SymbolTable,
        options: CompilationOptions,
        errors: IErrorReporter
    ): IAssemblyProgram? {
        val st = if(prefixSymbols) prefixSymbols(program, options, symbolTable) else symbolTable
        val asmgen = AsmGen6502Internal(program, st, options, errors)
        return asmgen.compileToAssembly()
    }

    private fun prefixSymbols(program: PtProgram, options: CompilationOptions, st: SymbolTable): SymbolTable {
        val nodesToPrefix = mutableListOf<Pair<PtNode, Int>>()              // parent + index
        val functionCallsToPrefix = mutableListOf<Pair<PtNode, Int>>()      // parent + index

        fun prefixNamedNode(node: PtNamedNode) {
            when(node) {
                is PtAsmSub, is PtSub -> node.name = "p8s_${node.name}"
                is PtBlock -> node.name = "p8b_${node.name}"
                is PtLabel -> node.name = "p8l_${node.name}"
                is PtVariable, is PtConstant, is PtMemMapped, is PtSubroutineParameter -> node.name = "p8v_${node.name}"
                else -> node.name = "p8_${node.name}"
            }
        }

        fun prefixSymbols(node: PtNode) {
            when(node) {
                is PtAsmSub -> {
                    prefixNamedNode(node)
                    node.parameters.forEach { (_, param) -> prefixNamedNode(param) }
                }
                is PtSub -> {
                    prefixNamedNode(node)
                    node.parameters.forEach { prefixNamedNode(it) }
                }
                is PtFunctionCall -> {
                    val stNode = st.lookup(node.name)!!
                    if(stNode.astNode.definingBlock()?.noSymbolPrefixing!=true) {
                        val index = node.parent.children.indexOf(node)
                        functionCallsToPrefix += node.parent to index
                    }
                }
                is PtIdentifier -> {
                    var lookupName = node.name
                    if(node.type in SplitWordArrayTypes && (lookupName.endsWith("_lsb") || lookupName.endsWith("_msb"))) {
                        lookupName = lookupName.dropLast(4)
                    }
                    val stNode = st.lookup(lookupName)!!
                    if(stNode.astNode.definingBlock()?.noSymbolPrefixing!=true) {
                        val index = node.parent.children.indexOf(node)
                        nodesToPrefix += node.parent to index
                    }
                }
                is PtJump -> {
                    val stNode = st.lookup(node.identifier!!.name) ?: throw AssemblyError("name not found ${node.identifier}")
                    if(stNode.astNode.definingBlock()?.noSymbolPrefixing!=true) {
                        val index = node.parent.children.indexOf(node)
                        nodesToPrefix += node.parent to index
                    }
                }
                is PtBlock -> prefixNamedNode(node)
                is PtConstant -> prefixNamedNode(node)
                is PtLabel -> prefixNamedNode(node)
                is PtMemMapped -> prefixNamedNode(node)
                is PtSubroutineParameter -> prefixNamedNode(node)
                is PtVariable -> {
                    val index = node.parent.children.indexOf(node)
                    nodesToPrefix += node.parent to index
                }
                else -> { }
            }
            node.children.forEach { prefixSymbols(it) }
        }

        program.allBlocks().forEach { block ->
            if (!block.noSymbolPrefixing) {
                prefixSymbols(block)
            }
        }

        nodesToPrefix.forEach { (parent, index) ->
            val node = parent.children[index]
            when(node) {
                is PtIdentifier -> parent.children[index] = node.prefix(parent, st)
                is PtFunctionCall ->  throw AssemblyError("PtFunctionCall should be processed in their own list, last")
                is PtJump -> parent.children[index] = node.prefix(parent, st)
                is PtVariable -> parent.children[index] = node.prefix(st)
                else -> throw AssemblyError("weird node to prefix $node")
            }
        }

        // reversed so inner calls (such as arguments to a function call) get processed before the actual function call itself
        functionCallsToPrefix.reversed().forEach { (parent, index) ->
            val node = parent.children[index]
            if(node is PtFunctionCall) {
                parent.children[index] = node.prefix(parent)
            } else {
                throw AssemblyError("expected PtFunctionCall")
            }
        }

        return SymbolTableMaker(program, options).make()
    }
}

private fun prefixScopedName(name: String, type: Char): String {
    if('.' !in name)
        return "p8${type}_$name"
    val parts = name.split('.')
    val firstPrefixed = "p8b_${parts[0]}"
    val lastPrefixed = "p8${type}_${parts.last()}"
    // the parts in between are assumed to be subroutine scopes.
    val inbetweenPrefixed = parts.drop(1).dropLast(1).map{ "p8s_$it" }
    val prefixed = listOf(firstPrefixed) + inbetweenPrefixed + listOf(lastPrefixed)
    return prefixed.joinToString(".")
}

private fun PtVariable.prefix(st: SymbolTable): PtVariable {
    name = prefixScopedName(name, 'v')
    if(value==null)
        return this

    val arrayValue = value as? PtArray
    return if(arrayValue!=null && arrayValue.children.any { it !is PtNumber} ) {
        val newValue = PtArray(arrayValue.type, arrayValue.position)
        arrayValue.children.forEach { elt ->
            when(elt) {
                is PtIdentifier -> newValue.add(elt.prefix(arrayValue, st))
                is PtNumber -> newValue.add(elt)
                is PtAddressOf -> {
                    if(elt.definingBlock()?.noSymbolPrefixing==true)
                        newValue.add(elt)
                    else {
                        val newAddr = PtAddressOf(elt.position)
                        newAddr.children.add(elt.identifier.prefix(newAddr, st))
                        newAddr.parent = arrayValue
                        newValue.add(newAddr)
                    }
                }
                else -> throw AssemblyError("weird array value element $elt")
            }
        }
        PtVariable(name, type, zeropage, newValue, arraySize, position)
    }
    else this
}

private fun PtJump.prefix(parent: PtNode, st: SymbolTable): PtJump {
    val prefixedIdent = identifier!!.prefix(this, st)
    val jump = PtJump(prefixedIdent, address, position)
    jump.parent = parent
    return jump
}

private fun PtFunctionCall.prefix(parent: PtNode): PtFunctionCall {
    val newName = prefixScopedName(name, 's')
    val call = PtFunctionCall(newName, void, type, position)
    call.children.addAll(children)
    call.children.forEach { it.parent = call }
    call.parent = parent
    return call
}

private fun PtIdentifier.prefix(parent: PtNode, st: SymbolTable): PtIdentifier {
    var target = st.lookup(name)
    if(target?.astNode?.definingBlock()?.noSymbolPrefixing==true)
        return this

    if(target==null) {
        if(name.endsWith("_lsb") || name.endsWith("_msb")) {
            target = st.lookup(name.dropLast(4))
            if(target?.astNode?.definingBlock()?.noSymbolPrefixing==true)
                return this
        }
    }

    val prefixType = when(target!!.type) {
        StNodeType.BLOCK -> 'b'
        StNodeType.SUBROUTINE, StNodeType.ROMSUB -> 's'
        StNodeType.LABEL -> 'l'
        StNodeType.STATICVAR, StNodeType.MEMVAR, StNodeType.CONSTANT -> 'v'
        StNodeType.BUILTINFUNC -> 's'
        StNodeType.MEMORYSLAB -> 'v'
        else -> '?'
    }
    val newName = prefixScopedName(name, prefixType)
    val node = PtIdentifier(newName, type, position)
    node.parent = parent
    return node
}


class AsmGen6502Internal (
    val program: PtProgram,
    internal val symbolTable: SymbolTable,
    internal val options: CompilationOptions,
    internal val errors: IErrorReporter
) {

    internal val optimizedByteMultiplications = setOf(3,5,6,7,9,10,11,12,13,14,15,20,25,40,50,80,100)
    internal val optimizedWordMultiplications = setOf(3,5,6,7,9,10,12,15,20,25,40,50,80,100,320,640)
    internal val loopEndLabels = ArrayDeque<String>()
    private val zeropage = options.compTarget.machine.zeropage
    private val allocator = VariableAllocator(symbolTable, options, errors)
    private val assemblyLines = mutableListOf<String>()
    private val breakpointLabels = mutableListOf<String>()
    private val forloopsAsmGen = ForLoopsAsmGen(this, zeropage)
    private val postincrdecrAsmGen = PostIncrDecrAsmGen(program, this)
    private val functioncallAsmGen = FunctionCallAsmGen(program, this)
    private val programGen = ProgramAndVarsGen(program, options, errors, symbolTable, functioncallAsmGen, this, allocator, zeropage)
    private val anyExprGen = AnyExprAsmGen(this)
    private val assignmentAsmGen = AssignmentAsmGen(program, this, anyExprGen, allocator)
    private val builtinFunctionsAsmGen = BuiltinFunctionsAsmGen(program, this, assignmentAsmGen)

    fun compileToAssembly(): IAssemblyProgram? {

        assemblyLines.clear()
        loopEndLabels.clear()

        println("Generating assembly code... ")
        programGen.generate()

        if(errors.noErrors()) {
            val output = options.outputDir.resolve("${program.name}.asm")
            if(options.optimize) {
                val separateLines = assemblyLines.flatMapTo(mutableListOf()) { it.split('\n') }
                assemblyLines.clear()
                while(optimizeAssembly(separateLines, options.compTarget.machine, symbolTable)>0) {
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

    internal fun isTargetCpu(cpu: CpuType) = options.compTarget.machine.cpu == cpu

    private var lastSourceLineNumber: Int = -1

    internal fun outputSourceLine(node: PtNode) {
        if(!options.includeSourcelines || node.position===Position.DUMMY || node.position.line==lastSourceLineNumber)
            return

        lastSourceLineNumber = node.position.line
        val srcComment = "\t; source: ${node.position.file}:${node.position.line}"
        val line = SourceLineCache.retrieveLine(node.position)
        if(line==null)
            out(srcComment, false)
        else
            out("$srcComment   $line", false)
    }

    internal fun out(str: String, splitlines: Boolean = true) {
        val fragment = (if(splitlines && " | " in str) str.replace("|", "\n") else str).trim('\r', '\n')
        if (splitlines) {
            for (line in fragment.splitToSequence('\n')) {
                val trimmed = if (line.startsWith(' ')) "\t" + line.trim() else line
                // trimmed = trimmed.replace(Regex("^\\+\\s+"), "+\t")  // sanitize local label indentation
                assemblyLines.add(trimmed)
            }
        } else assemblyLines.add(fragment)
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
    fun asmSymbolName(identifier: PtIdentifier): String {
        val name = asmSymbolName(identifier.name)

        // see if we're inside a subroutine, if so, remove the whole prefix and just make the variable name locally scoped (64tass scopes it to the proper .proc block)
        val subName = identifier.definingSub()?.scopedName
        return if (subName != null && name.length>subName.length && name.startsWith(subName) && name[subName.length] == '.')
            name.drop(subName.length + 1)
        else
            name
    }

    fun asmVariableName(identifier: PtIdentifier): String {
        val name = asmVariableName(identifier.name)

        // see if we're inside a subroutine, if so, remove the whole prefix and just make the variable name locally scoped (64tass scopes it to the proper .proc block)
        val subName = identifier.definingSub()?.scopedName
        return if (subName != null && name.length>subName.length && name.startsWith(subName) && name[subName.length] == '.')
            name.drop(subName.length+1)
        else
            name
    }

    fun asmVariableName(st: StNode, scope: PtSub?): String {
        val name = asmVariableName(st.scopedName)
        if(scope==null)
            return name
        // remove the whole prefix and just make the variable name locally scoped (64tass scopes it to the proper .proc block)
        val subName = scope.scopedName
        return if (name.length>subName.length && name.startsWith(subName) && name[subName.length] == '.')
            name.drop(subName.length+1)
        else
            name
    }

    internal fun getTempVarName(dt: DataType): String {
        return when(dt) {
            DataType.UBYTE -> "cx16.r9L"
            DataType.BYTE -> "cx16.r9sL"
            DataType.UWORD -> "cx16.r9"
            DataType.WORD -> "cx16.r9s"
            DataType.FLOAT -> "floats.floats_temp_var"
            else -> throw AssemblyError("invalid dt $dt")
        }
    }

    internal fun loadByteFromPointerIntoA(pointervar: PtIdentifier): String {
        // returns the source name of the zero page pointervar if it's already in the ZP,
        // otherwise returns "P8ZP_SCRATCH_W1" which is the intermediary
        val symbol = symbolTable.lookup(pointervar.name)
        when (val target = symbol!!.astNode) {
            is PtLabel -> {
                val sourceName = asmSymbolName(pointervar)
                out("  lda  $sourceName")
                return sourceName
            }
            is PtVariable, is PtMemMapped -> {
                val sourceName = asmVariableName(pointervar)
                if (isTargetCpu(CpuType.CPU65c02)) {
                    return if (allocator.isZpVar((target as PtNamedNode).scopedName)) {
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
                    return if (allocator.isZpVar((target as PtNamedNode).scopedName)) {
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
            else -> throw AssemblyError("invalid pointervar $target")
        }
    }

    internal fun storeAIntoPointerVar(pointervar: PtIdentifier) {
        val sourceName = asmVariableName(pointervar)
        if (isTargetCpu(CpuType.CPU65c02)) {
            if (allocator.isZpVar(pointervar.name)) {
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
            if (allocator.isZpVar(pointervar.name)) {
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
        return name2.replace("prog8_lib.P8ZP_SCRATCH_", "P8ZP_SCRATCH_")    // take care of the 'hooks' to the temp vars -> reference zp symbols directly
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

    internal fun translate(stmt: PtNode) {
        outputSourceLine(stmt)
        when(stmt) {
            is PtReturn -> translate(stmt)
            is PtSub -> programGen.translateSubroutine(stmt)
            is PtAsmSub -> programGen.translateAsmSubroutine(stmt)
            is PtInlineAssembly -> translate(stmt)
            is PtBuiltinFunctionCall -> builtinFunctionsAsmGen.translateFunctioncallStatement(stmt)
            is PtFunctionCall -> functioncallAsmGen.translateFunctionCallStatement(stmt)
            is PtAssignment -> assignmentAsmGen.translate(stmt)
            is PtAugmentedAssign -> assignmentAsmGen.translate(stmt)
            is PtJump -> {
                val (asmLabel, indirect) = getJumpTarget(stmt)
                jmp(asmLabel, indirect)
            }
            is PtPostIncrDecr -> postincrdecrAsmGen.translate(stmt)
            is PtLabel -> translate(stmt)
            is PtConditionalBranch -> translate(stmt)
            is PtIfElse -> translate(stmt)
            is PtForLoop -> forloopsAsmGen.translate(stmt)
            is PtRepeatLoop -> translate(stmt)
            is PtWhen -> translate(stmt)
            is PtIncludeBinary -> translate(stmt)
            is PtBreakpoint -> translate(stmt)
            is PtVariable, is PtConstant, is PtMemMapped -> { /* do nothing; variables are handled elsewhere */ }
            is PtBlock -> throw AssemblyError("block should have been handled elsewhere")
            is PtNodeGroup -> stmt.children.forEach { translate(it) }
            is PtNop -> {}
            else -> throw AssemblyError("missing asm translation for $stmt")
        }
    }

    internal fun loadScaledArrayIndexIntoRegister(
        expr: PtArrayIndexer,
        elementDt: DataType,
        register: CpuRegister
    ) {
        val reg = register.toString().lowercase()
        val indexnum = expr.index.asConstInteger()
        if (indexnum != null) {
            val indexValue = if(expr.splitWords)
                indexnum
            else
                indexnum * options.compTarget.memorySize(elementDt)
            out("  ld$reg  #$indexValue")
            return
        }

        if(expr.splitWords) {
            assignExpressionToRegister(expr.index, RegisterOrPair.fromCpuRegister(register), false)
            return
        }

        when (elementDt) {
            in ByteDatatypes -> {
                assignExpressionToRegister(expr.index, RegisterOrPair.fromCpuRegister(register), false)
            }
            in WordDatatypes -> {
                assignExpressionToRegister(expr.index, RegisterOrPair.A, false)
                out("  asl  a")
                when (register) {
                    CpuRegister.A -> {}
                    CpuRegister.X -> out(" tax")
                    CpuRegister.Y -> out(" tay")
                }
            }
            DataType.FLOAT -> {
                require(options.compTarget.memorySize(DataType.FLOAT) == 5) {"invalid float size ${expr.position}"}
                assignExpressionToRegister(expr.index, RegisterOrPair.A, false)
                out("""
                    sta  P8ZP_SCRATCH_REG
                    asl  a
                    asl  a
                    clc
                    adc  P8ZP_SCRATCH_REG"""
                )
                when (register) {
                    CpuRegister.A -> {}
                    CpuRegister.X -> out(" tax")
                    CpuRegister.Y -> out(" tay")
                }
            }
            else -> throw AssemblyError("weird dt")
        }
    }

    internal fun translateBuiltinFunctionCallExpression(bfc: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?): DataType? =
            builtinFunctionsAsmGen.translateFunctioncallExpression(bfc, resultRegister)

    internal fun translateFunctionCall(functionCallExpr: PtFunctionCall) =
            functioncallAsmGen.translateFunctionCall(functionCallExpr)

    internal fun translateNormalAssignment(assign: AsmAssignment, scope: IPtSubroutine?) =
            assignmentAsmGen.translateNormalAssignment(assign, scope)

    internal fun assignExpressionToRegister(expr: PtExpression, register: RegisterOrPair, signed: Boolean=false) =
            assignmentAsmGen.assignExpressionToRegister(expr, register, signed)

    internal fun assignExpressionToVariable(expr: PtExpression, asmVarName: String, dt: DataType) =
            assignmentAsmGen.assignExpressionToVariable(expr, asmVarName, dt)

    internal fun assignVariableToRegister(asmVarName: String, register: RegisterOrPair, scope: IPtSubroutine?, pos: Position, signed: Boolean=false) =
            assignmentAsmGen.assignVariableToRegister(asmVarName, register, signed, scope, pos)

    internal fun assignRegister(reg: RegisterOrPair, target: AsmAssignTarget) {
        when(reg) {
            RegisterOrPair.A,
            RegisterOrPair.X,
            RegisterOrPair.Y -> assignmentAsmGen.assignRegisterByte(target, reg.asCpuRegister(), target.datatype in SignedDatatypes, true)
            RegisterOrPair.AX,
            RegisterOrPair.AY,
            RegisterOrPair.XY,
            in Cx16VirtualRegisters -> assignmentAsmGen.assignRegisterpairWord(target, reg)
            RegisterOrPair.FAC1 -> assignmentAsmGen.assignFAC1float(target)
            RegisterOrPair.FAC2 -> assignmentAsmGen.assignFAC2float(target)
            else -> throw AssemblyError("invalid register")
        }
    }

    internal fun assignExpressionTo(value: PtExpression, target: AsmAssignTarget) {
        when (target.datatype) {
            in ByteDatatypes -> {
                if (value.asConstInteger()==0) {
                    when(target.kind) {
                        TargetStorageKind.VARIABLE -> {
                            if (isTargetCpu(CpuType.CPU6502))
                                out("lda  #0 |  sta  ${target.asmVarname}")
                            else
                                out("stz  ${target.asmVarname}")
                        }
                        TargetStorageKind.MEMORY -> {
                            val address = target.memory!!.address.asConstInteger()
                            if(address!=null) {
                                if (isTargetCpu(CpuType.CPU6502))
                                    out("lda  #0 |  sta  ${address.toHex()}")
                                else
                                    out("  stz  ${address.toHex()}")
                                return
                            }
                        }
                        TargetStorageKind.REGISTER -> {
                            val zero = PtNumber(DataType.UBYTE, 0.0, value.position)
                            zero.parent = value
                            assignExpressionToRegister(zero, target.register!!, false)
                            return
                        }
                        else -> { }
                    }
                }

                assignExpressionToRegister(value, RegisterOrPair.A)
                assignRegister(RegisterOrPair.A, target)
            }
            in WordDatatypes, in PassByReferenceDatatypes -> {
                assignExpressionToRegister(value, RegisterOrPair.AY)
                translateNormalAssignment(
                    AsmAssignment(
                        AsmAssignSource(SourceStorageKind.REGISTER, program, this, target.datatype, register=RegisterOrPair.AY),
                        target, program.memsizer, value.position
                    ), value.definingISub()
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

    private fun translate(stmt: PtIfElse) {
        val condition = stmt.condition as? PtBinaryExpression
        if(condition!=null) {
            requireComparisonExpression(condition)  // IfStatement: condition must be of form  'x <comparison> <value>'
            if (stmt.elseScope.children.isEmpty()) {
                val jump = stmt.ifScope.children.singleOrNull()
                if (jump is PtJump) {
                    translateCompareAndJumpIfTrue(condition, jump)
                } else {
                    val endLabel = makeLabel("if_end")
                    translateCompareOrJumpIfFalse(condition, endLabel)
                    translate(stmt.ifScope)
                    out(endLabel)
                }
            } else {
                // both true and else parts
                val elseLabel = makeLabel("if_else")
                val endLabel = makeLabel("if_end")
                translateCompareOrJumpIfFalse(condition, elseLabel)
                translate(stmt.ifScope)
                jmp(endLabel)
                out(elseLabel)
                translate(stmt.elseScope)
                out(endLabel)
            }
        } else {
            // condition is a simple expression  "if X"   -->  "if X!=0"
            val zero = PtNumber(DataType.UBYTE,0.0, stmt.position)
            val leftConst = stmt.condition as? PtNumber
            if (stmt.elseScope.children.isEmpty()) {
                val jump = stmt.ifScope.children.singleOrNull()
                if (jump is PtJump) {
                    // jump somewhere if X!=0
                    val label = when {
                        jump.identifier!=null -> asmSymbolName(jump.identifier!!)
                        jump.address!=null -> jump.address!!.toHex()
                        else -> throw AssemblyError("weird jump")
                    }
                    when(stmt.condition.type) {
                        in WordDatatypes -> translateWordEqualsOrJumpElsewhere(stmt.condition, zero, leftConst, zero, label)
                        in ByteDatatypes -> translateByteEqualsOrJumpElsewhere(stmt.condition, zero, leftConst, zero, label)
                        else -> throw AssemblyError("weird condition dt")
                    }
                } else {
                    // skip the true block (=jump to end label) if X==0
                    val endLabel = makeLabel("if_end")
                    when(stmt.condition.type) {
                        in WordDatatypes -> translateWordNotEqualsOrJumpElsewhere(stmt.condition, zero, leftConst, zero, endLabel)
                        in ByteDatatypes -> translateByteNotEqualsOrJumpElsewhere(stmt.condition, zero, leftConst, zero, endLabel)
                        else -> throw AssemblyError("weird condition dt")
                    }
                    translate(stmt.ifScope)
                    out(endLabel)
                }
            } else {
                // both true and else parts
                val elseLabel = makeLabel("if_else")
                val endLabel = makeLabel("if_end")
                when(stmt.condition.type) {
                    in WordDatatypes -> translateWordNotEqualsOrJumpElsewhere(stmt.condition, zero, leftConst, zero, elseLabel)
                    in ByteDatatypes -> translateByteNotEqualsOrJumpElsewhere(stmt.condition, zero, leftConst, zero, elseLabel)
                    else -> throw AssemblyError("weird condition dt")
                }
                translate(stmt.ifScope)
                jmp(endLabel)
                out(elseLabel)
                translate(stmt.elseScope)
                out(endLabel)
            }
        }
    }

    private fun requireComparisonExpression(condition: PtExpression) {
        if (!(condition is PtBinaryExpression && condition.operator in ComparisonOperators))
            throw AssemblyError("expected boolean comparison expression $condition")
    }

    private fun translate(stmt: PtRepeatLoop) {
        val endLabel = makeLabel("repeatend")
        loopEndLabels.push(endLabel)

        when (stmt.count) {
            is PtNumber -> {
                val iterations = (stmt.count as PtNumber).number.toInt()
                when {
                    iterations == 0 -> {}
                    iterations == 1 -> translate(stmt.statements)
                    iterations<0 || iterations>65535 -> throw AssemblyError("invalid number of iterations")
                    iterations <= 256 -> repeatByteCount(iterations, stmt)
                    else -> repeatWordCount(iterations, stmt)
                }
            }
            is PtIdentifier -> {
                val symbol = symbolTable.lookup((stmt.count as PtIdentifier).name)
                val vardecl = symbol!!.astNode as IPtVariable
                val name = asmVariableName(stmt.count as PtIdentifier)
                when(vardecl.type) {
                    DataType.UBYTE, DataType.BYTE -> {
                        assignVariableToRegister(name, RegisterOrPair.Y, stmt.definingISub(), stmt.count.position)
                        repeatCountInY(stmt, endLabel)
                    }
                    DataType.UWORD, DataType.WORD -> {
                        assignVariableToRegister(name, RegisterOrPair.AY, stmt.definingISub(), stmt.count.position)
                        repeatWordCountInAY(endLabel, stmt)
                    }
                    else -> throw AssemblyError("invalid loop variable datatype $vardecl")
                }
            }
            else -> {
                when (stmt.count.type) {
                    in ByteDatatypes -> {
                        assignExpressionToRegister(stmt.count, RegisterOrPair.Y)
                        repeatCountInY(stmt, endLabel)
                    }
                    in WordDatatypes -> {
                        assignExpressionToRegister(stmt.count, RegisterOrPair.AY)
                        repeatWordCountInAY(endLabel, stmt)
                    }
                    else -> throw AssemblyError("invalid loop expression datatype ${stmt.count.type}")
                }
            }
        }

        loopEndLabels.pop()
    }

    private fun repeatWordCount(count: Int, stmt: PtRepeatLoop) {
        require(count in 257..65535) { "invalid repeat count ${stmt.position}" }
        val repeatLabel = makeLabel("repeat")
        val counterVar = createRepeatCounterVar(DataType.UWORD, isTargetCpu(CpuType.CPU65c02), stmt)
        // the iny + double dec is microoptimization of the 16 bit loop
        out("""
            ldy  #>$count
            lda  #<$count
            beq  +
            iny
+           sta  $counterVar
            sty  $counterVar+1
$repeatLabel""")
        translate(stmt.statements)
        out("""
            dec  $counterVar
            bne  $repeatLabel
            dec  $counterVar+1
            bne  $repeatLabel""")
    }

    private fun repeatWordCountInAY(endLabel: String, stmt: PtRepeatLoop) {
        // note: A/Y must have been loaded with the number of iterations!
        // the iny + double dec is microoptimization of the 16 bit loop
        val repeatLabel = makeLabel("repeat")
        val counterVar = createRepeatCounterVar(DataType.UWORD, false, stmt)
        out("""
            cmp  #0
            beq  +
            iny
+           sta  $counterVar
            sty  $counterVar+1
            ora  $counterVar+1
            beq  $endLabel
$repeatLabel""")
        translate(stmt.statements)
        out("""
            dec  $counterVar
            bne  $repeatLabel
            dec  $counterVar+1
            bne  $repeatLabel""")
        out(endLabel)
    }


    private fun repeatByteCount(count: Int, stmt: PtRepeatLoop) {
        require(count in 2..256) { "invalid repeat count ${stmt.position}" }
        val repeatLabel = makeLabel("repeat")
        if(isTargetCpu(CpuType.CPU65c02)) {
            val counterVar = createRepeatCounterVar(DataType.UBYTE, true, stmt)
            out("  lda  #${count and 255} |  sta  $counterVar")
            out(repeatLabel)
            translate(stmt.statements)
            out("  dec  $counterVar |  bne  $repeatLabel")
        } else {
            val counterVar = createRepeatCounterVar(DataType.UBYTE, false, stmt)
            out("  lda  #${count and 255} |  sta  $counterVar")
            out(repeatLabel)
            translate(stmt.statements)
            out("  dec  $counterVar |  bne  $repeatLabel")
        }
    }

    private fun repeatCountInY(stmt: PtRepeatLoop, endLabel: String) {
        // note: Y must just have been loaded with the (variable) number of loops to be performed!
        val repeatLabel = makeLabel("repeat")
        if(isTargetCpu(CpuType.CPU65c02)) {
            val counterVar = createRepeatCounterVar(DataType.UBYTE, true, stmt)
            out("  beq  $endLabel |  sty  $counterVar")
            out(repeatLabel)
            translate(stmt.statements)
            out("  dec  $counterVar |  bne  $repeatLabel")
        } else {
            val counterVar = createRepeatCounterVar(DataType.UBYTE, false, stmt)
            out("  beq  $endLabel |  sty  $counterVar")
            out(repeatLabel)
            translate(stmt.statements)
            out("  dec  $counterVar |  bne  $repeatLabel")
        }
        out(endLabel)
    }

    private fun createRepeatCounterVar(dt: DataType, preferZeropage: Boolean, stmt: PtRepeatLoop): String {
        val scope = stmt.definingISub()!!
        val asmInfo = subroutineExtra(scope)
        var parent = stmt.parent
        while(parent !is PtProgram) {
            if(parent is PtRepeatLoop)
                break
            parent = parent.parent
        }
        val isNested = parent is PtRepeatLoop

        if(!isNested) {
            // we can re-use a counter var from the subroutine if it already has one for that datatype
            val existingVar = asmInfo.extraVars.firstOrNull { it.first==dt && it.second.endsWith("counter") }
            if(existingVar!=null) {
                if(!preferZeropage || existingVar.third!=null)
                    return existingVar.second
            }
        }

        val counterVar = makeLabel("counter")
        when(dt) {
            DataType.UBYTE, DataType.UWORD -> {
                val result = zeropage.allocate(counterVar, dt, null, stmt.position, errors)
                result.fold(
                    success = { (address, _, _) -> asmInfo.extraVars.add(Triple(dt, counterVar, address)) },
                    failure = { asmInfo.extraVars.add(Triple(dt, counterVar, null)) }  // allocate normally
                )
                return counterVar
            }
            else -> throw AssemblyError("invalidt dt")
        }
    }

    private fun translate(stmt: PtWhen) {
        val endLabel = makeLabel("choice_end")
        val choiceBlocks = mutableListOf<Pair<String, PtNodeGroup>>()
        val conditionDt = stmt.value.type
        if(conditionDt in ByteDatatypes)
            assignExpressionToRegister(stmt.value, RegisterOrPair.A)
        else
            assignExpressionToRegister(stmt.value, RegisterOrPair.AY)

        for(choiceNode in stmt.choices.children) {
            val choice = choiceNode as PtWhenChoice
            val choiceLabel = makeLabel("choice")
            if(choice.isElse) {
                translate(choice.statements)
            } else {
                choiceBlocks.add(choiceLabel to choice.statements)
                for (cv in choice.values.children) {
                    val value = (cv as PtNumber).number.toInt()
                    if(conditionDt in ByteDatatypes) {
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

    private fun translate(stmt: PtLabel) {
        out(stmt.name)
    }

    private fun translate(stmt: PtConditionalBranch) {
        if(stmt.trueScope.children.isEmpty() && stmt.falseScope.children.isNotEmpty())
            throw AssemblyError("only else part contains code, shoud have been switched already")

        val jump = stmt.trueScope.children.firstOrNull() as? PtJump
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
            translate(stmt.falseScope)
        } else {
            if(stmt.falseScope.children.isEmpty()) {
                if(stmt.trueScope.children.isNotEmpty()) {
                    val instruction = branchInstruction(stmt.condition, true)
                    val elseLabel = makeLabel("branch_else")
                    out("  $instruction  $elseLabel")
                    translate(stmt.trueScope)
                    out(elseLabel)
                }
            } else {
                val instruction = branchInstruction(stmt.condition, true)
                val elseLabel = makeLabel("branch_else")
                val endLabel = makeLabel("branch_end")
                out("  $instruction  $elseLabel")
                translate(stmt.trueScope)
                jmp(endLabel)
                out(elseLabel)
                translate(stmt.falseScope)
                out(endLabel)
            }
        }
    }

    private fun getJumpTarget(jump: PtJump): Pair<String, Boolean> {
        val ident = jump.identifier
        val addr = jump.address
        return when {
            ident!=null -> {
                // can be a label, or a pointer variable
                val symbol = symbolTable.lookup(ident.name)
                if(symbol?.type in arrayOf(StNodeType.STATICVAR, StNodeType.MEMVAR, StNodeType.CONSTANT))
                    Pair(asmSymbolName(ident), true)        // indirect jump if the jump symbol is a variable
                else
                    Pair(asmSymbolName(ident), false)
            }
            addr!=null -> Pair(addr.toHex(), false)
            else -> Pair("????", false)
        }
    }

    private fun translate(ret: PtReturn, withRts: Boolean=true) {
        ret.value?.let { returnvalue ->
            val sub = ret.definingSub()!!
            val returnReg = sub.returnRegister()!!
            when (sub.returntype) {
                in NumericDatatypes -> {
                    assignExpressionToRegister(returnvalue, returnReg.registerOrPair!!)
                }
                else -> {
                    // all else take its address and assign that also to AY register pair
                    val addrofValue = PtAddressOf(returnvalue.position)
                    addrofValue.add(returnvalue as PtIdentifier)
                    addrofValue.parent = ret.parent
                    assignmentAsmGen.assignExpressionToRegister(addrofValue, returnReg.registerOrPair!!, false)
                }
            }
        }

        if(withRts)
            out("  rts")
    }

    private fun translate(asm: PtInlineAssembly) {
        if(asm.isIR)
            throw AssemblyError("%asm containing IR code cannot be translated to 6502 assembly")
        else
            assemblyLines.add(asm.assembly.trimEnd().trimStart('\r', '\n'))
    }

    private fun translate(incbin: PtIncludeBinary) {
        val offset = if(incbin.offset!=null) ", ${incbin.offset}" else ""
        val length = if(incbin.length!=null) ", ${incbin.length}" else ""
        if(incbin.definingBlock()!!.source is SourceCode.Generated)
            throw AssemblyError("%asmbinary inside non-library/non-filesystem module not yet supported")
        val sourcePath = Path(incbin.definingBlock()!!.source.origin)
        val includedPath = sourcePath.resolveSibling(incbin.file)
        val pathForAssembler = options.outputDir // #54: 64tass needs the path *relative to the .asm file*
            .toAbsolutePath()
            .relativize(includedPath.toAbsolutePath())
            .normalize() // avoid assembler warnings (-Wportable; only some, not all)
            .toString().replace('\\', '/')
        out("  .binary \"$pathForAssembler\" $offset $length")
    }

    private fun translate(brk: PtBreakpoint) {
        val label = "_prog8_breakpoint_${breakpointLabels.size+1}"
        breakpointLabels.add(label)
        out(label)
        if(options.breakpointCpuInstruction) {
            val instruction =
                when(options.compTarget.machine.cpu) {
                    CpuType.CPU6502 -> "brk"
                    CpuType.CPU65c02 -> "stp"
                    else -> throw AssemblyError("invalid cpu type")
                }
            out("  $instruction")
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

    internal fun isZpVar(variable: PtIdentifier): Boolean = allocator.isZpVar(variable.name)

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

    internal fun pointerViaIndexRegisterPossible(pointerOffsetExpr: PtExpression): Pair<PtExpression, PtExpression>? {
        if (pointerOffsetExpr !is PtBinaryExpression) return null
        val operator = pointerOffsetExpr.operator
        val left = pointerOffsetExpr.left
        val right = pointerOffsetExpr.right
        if (operator != "+") return null
        val leftDt = left.type
        val rightDt = right.type
        if(leftDt == DataType.UWORD && rightDt == DataType.UBYTE)
            return Pair(left, right)
        if(leftDt == DataType.UBYTE && rightDt == DataType.UWORD)
            return Pair(right, left)
        if(leftDt == DataType.UWORD && rightDt == DataType.UWORD) {
            // could be that the index was a constant numeric byte but converted to word, check that
            val constIdx = right as? PtNumber
            if(constIdx!=null && constIdx.number.toInt()>=0 && constIdx.number.toInt()<=255) {
                val num = PtNumber(DataType.UBYTE, constIdx.number, constIdx.position)
                num.parent = right.parent
                return Pair(left, num)
            }
            // could be that the index was typecasted into uword, check that
            val rightTc = right as? PtTypeCast
            if(rightTc!=null && rightTc.value.type == DataType.UBYTE)
                return Pair(left, rightTc.value)
            val leftTc = left as? PtTypeCast
            if(leftTc!=null && leftTc.value.type == DataType.UBYTE)
                return Pair(right, leftTc.value)
        }
        return null
    }

    internal fun tryOptimizedPointerAccessWithA(addressExpr: PtExpression, operator: String, write: Boolean): Boolean {
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

        if(operator=="+") {
            val ptrAndIndex = pointerViaIndexRegisterPossible(addressExpr)
            if(ptrAndIndex!=null) {
                val pointervar = ptrAndIndex.first as? PtIdentifier
                val target = if(pointervar==null) null else symbolTable.lookup(pointervar.name)!!.astNode
                when(target) {
                    is PtLabel -> {
                        assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                        out("  lda  ${asmSymbolName(pointervar!!)},y")
                        return true
                    }
                    is IPtVariable, null -> {
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
                                if(ptrAndIndex.second.isSimple()) {
                                    assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD)
                                    assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                    if(saveA)
                                        out("  pla")
                                    out("  sta  (P8ZP_SCRATCH_W2),y")
                                } else {
                                    pushCpuStack(DataType.UBYTE,  ptrAndIndex.second)
                                    assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD)
                                    restoreRegisterStack(CpuRegister.Y, true)
                                    if(saveA)
                                        out("  pla")
                                    out("  sta  (P8ZP_SCRATCH_W2),y")
                                }
                            }
                        } else {
                            if(pointervar!=null && isZpVar(pointervar)) {
                                assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                out("  lda  (${asmSymbolName(pointervar)}),y")
                            } else {
                                // copy the pointer var to zp first
                                if(ptrAndIndex.second.isSimple()) {
                                    assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD)
                                    assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                    out("  lda  (P8ZP_SCRATCH_W2),y")
                                } else {
                                    pushCpuStack(DataType.UBYTE, ptrAndIndex.second)
                                    assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD)
                                    restoreRegisterStack(CpuRegister.Y, false)
                                    out("  lda  (P8ZP_SCRATCH_W2),y")
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

    internal fun findSubroutineParameter(name: String, asmgen: AsmGen6502Internal): PtSubroutineParameter? {
        val stScope = asmgen.symbolTable.lookup(name) ?: return null
        val node = stScope.astNode
        if(node is PtSubroutineParameter)
            return node
        return node.definingSub()?.parameters?.singleOrNull { it.name===name }
    }

    private fun translateCompareAndJumpIfTrue(expr: PtBinaryExpression, jump: PtJump) {
        if(expr.operator !in ComparisonOperators)
            throw AssemblyError("must be comparison expression")

        // invert the comparison, so we can reuse the JumpIfFalse code generation routines
        val invertedComparisonOperator = invertedComparisonOperator(expr.operator)
            ?: throw AssemblyError("can't invert comparison $expr")

        val left = expr.left
        val right = expr.right
        val rightConstVal = right as? PtNumber

        val label = when {
            jump.identifier!=null -> asmSymbolName(jump.identifier!!)
            jump.address!=null -> jump.address!!.toHex()
            else -> throw AssemblyError("weird jump")
        }
        if (rightConstVal!=null && rightConstVal.number == 0.0) {
            testZeroOrJumpElsewhere(left, invertedComparisonOperator, label)
        }
        else {
            val leftConstVal = left as? PtNumber
            testNonzeroComparisonOrJumpElsewhere(left, invertedComparisonOperator, right, label, leftConstVal, rightConstVal)
        }
    }

    private fun translateCompareOrJumpIfFalse(expr: PtBinaryExpression, jumpIfFalseLabel: String) {
        val left = expr.left
        val right = expr.right
        val operator = expr.operator
        val leftConstVal = left as? PtNumber
        val rightConstVal = right as? PtNumber

        if (rightConstVal!=null && rightConstVal.number == 0.0)
            testZeroOrJumpElsewhere(left, operator, jumpIfFalseLabel)
        else
            testNonzeroComparisonOrJumpElsewhere(left, operator, right, jumpIfFalseLabel, leftConstVal, rightConstVal)
    }

    private fun testZeroOrJumpElsewhere(
        left: PtExpression,
        operator: String,
        jumpIfFalseLabel: String
    ) {
        val dt = left.type
        if(dt in IntegerDatatypes && left is PtIdentifier)
            return testVariableZeroOrJumpElsewhere(left, dt, operator, jumpIfFalseLabel)

        when(dt) {
            DataType.BOOL, DataType.UBYTE, DataType.UWORD -> {
                if(operator=="<") {
                    out("  jmp  $jumpIfFalseLabel")
                    return
                } else if(operator==">=") {
                    return
                }
                if(dt==DataType.UBYTE || dt==DataType.BOOL) {
                    assignExpressionToRegister(left, RegisterOrPair.A, false)
                    if (left is PtFunctionCall && !left.isSimple())
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
                if (left is PtFunctionCall && !left.isSimple())
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

    private fun testVariableZeroOrJumpElsewhere(variable: PtIdentifier, dt: DataType, operator: String, jumpIfFalseLabel: String) {
        // optimized code if the expression is just an identifier (variable)
        val varname = asmVariableName(variable)
        when(dt) {
            DataType.UBYTE, DataType.BOOL -> when(operator) {
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

    internal fun testNonzeroComparisonOrJumpElsewhere(
        left: PtExpression,
        operator: String,
        right: PtExpression,
        jumpIfFalseLabel: String,
        leftConstVal: PtNumber?,
        rightConstVal: PtNumber?
    ) {
        val dt = left.type

        when (operator) {
            "==" -> {
                when (dt) {
                    in ByteDatatypes -> translateByteEqualsOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    in WordDatatypes -> translateWordEqualsOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> translateFloatEqualsOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.STR -> translateStringEqualsOrJumpElsewhere(left as PtIdentifier, right as PtIdentifier, jumpIfFalseLabel)
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            "!=" -> {
                when (dt) {
                    in ByteDatatypes -> translateByteNotEqualsOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    in WordDatatypes -> translateWordNotEqualsOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> translateFloatNotEqualsOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.STR -> translateStringNotEqualsOrJumpElsewhere(left as PtIdentifier, right as PtIdentifier, jumpIfFalseLabel)
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            "<" -> {
                when(dt) {
                    DataType.UBYTE -> translateUbyteLessOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.BYTE -> translateByteLessOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.UWORD -> translateUwordLessOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.WORD -> translateWordLessOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> translateFloatLessOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.STR -> translateStringLessOrJumpElsewhere(left as PtIdentifier, right as PtIdentifier, jumpIfFalseLabel)
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            "<=" -> {
                when(dt) {
                    DataType.UBYTE -> translateUbyteLessOrEqualOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.BYTE -> translateByteLessOrEqualOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.UWORD -> translateUwordLessOrEqualOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.WORD -> translateWordLessOrEqualOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> translateFloatLessOrEqualOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.STR -> translateStringLessOrEqualOrJumpElsewhere(left as PtIdentifier, right as PtIdentifier, jumpIfFalseLabel)
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            ">" -> {
                when(dt) {
                    DataType.UBYTE -> translateUbyteGreaterOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.BYTE -> translateByteGreaterOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.UWORD -> translateUwordGreaterOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.WORD -> translateWordGreaterOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> translateFloatGreaterOrJumpElsewhere(left, right, leftConstVal,rightConstVal, jumpIfFalseLabel)
                    DataType.STR -> translateStringGreaterOrJumpElsewhere(left as PtIdentifier, right as PtIdentifier, jumpIfFalseLabel)
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
            ">=" -> {
                when(dt) {
                    DataType.UBYTE -> translateUbyteGreaterOrEqualOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.BYTE -> translateByteGreaterOrEqualOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.UWORD -> translateUwordGreaterOrEqualOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.WORD -> translateWordGreaterOrEqualOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.FLOAT -> translateFloatGreaterOrEqualOrJumpElsewhere(left, right, leftConstVal, rightConstVal, jumpIfFalseLabel)
                    DataType.STR -> translateStringGreaterOrEqualOrJumpElsewhere(left as PtIdentifier, right as PtIdentifier, jumpIfFalseLabel)
                    else -> throw AssemblyError("weird operand datatype")
                }
            }
        }
    }

    private fun translateFloatLessOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {
        if(leftConstVal!=null && rightConstVal!=null) {
            throw AssemblyError("const-compare should have been optimized away")
        }
        else if(leftConstVal!=null && right is PtIdentifier) {
            throw AssemblyError("const-compare should have been optimized to have const as right operand")
        }
        else if(left is PtIdentifier && rightConstVal!=null) {
            val leftName = asmVariableName(left)
            val rightName = allocator.getFloatAsmConst(rightConstVal.number)
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
        else if(left is PtIdentifier && right is PtIdentifier) {
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
            val subroutine = left.definingSub()!!
            subroutineExtra(subroutine).usedFloatEvalResultVar1 = true
            assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
            assignExpressionToRegister(left, RegisterOrPair.FAC1)
            out("""
                lda  #<$subroutineFloatEvalResultVar1
                ldy  #>$subroutineFloatEvalResultVar1
                jsr  floats.var_fac1_less_f
                beq  $jumpIfFalseLabel""")
        }
    }

    private fun translateFloatLessOrEqualOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {
        if(leftConstVal!=null && rightConstVal!=null) {
            throw AssemblyError("const-compare should have been optimized away")
        }
        else if(leftConstVal!=null && right is PtIdentifier) {
            throw AssemblyError("const-compare should have been optimized to have const as right operand")
        }
        else if(left is PtIdentifier && rightConstVal!=null) {
            val leftName = asmVariableName(left)
            val rightName = allocator.getFloatAsmConst(rightConstVal.number)
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
        else if(left is PtIdentifier && right is PtIdentifier) {
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
            val subroutine = left.definingSub()!!
            subroutineExtra(subroutine).usedFloatEvalResultVar1 = true
            assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
            assignExpressionToRegister(left, RegisterOrPair.FAC1)
            out("""
                lda  #<$subroutineFloatEvalResultVar1
                ldy  #>$subroutineFloatEvalResultVar1
                jsr  floats.var_fac1_lesseq_f
                beq  $jumpIfFalseLabel""")
        }
    }

    private fun translateFloatGreaterOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {
        if(leftConstVal!=null && rightConstVal!=null) {
            throw AssemblyError("const-compare should have been optimized away")
        }
        else if(left is PtIdentifier && rightConstVal!=null) {
            val leftName = asmVariableName(left)
            val rightName = allocator.getFloatAsmConst(rightConstVal.number)
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
        else if(leftConstVal!=null && right is PtIdentifier) {
            throw AssemblyError("const-compare should have been optimized to have const as right operand")
        }
        else if(left is PtIdentifier && right is PtIdentifier) {
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
            val subroutine = left.definingSub()!!
            subroutineExtra(subroutine).usedFloatEvalResultVar1 = true
            assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
            assignExpressionToRegister(left, RegisterOrPair.FAC1)
            out("""
                lda  #<$subroutineFloatEvalResultVar1
                ldy  #>$subroutineFloatEvalResultVar1
                jsr  floats.var_fac1_greater_f
                beq  $jumpIfFalseLabel""")
        }
    }

    private fun translateFloatGreaterOrEqualOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {
        if(leftConstVal!=null && rightConstVal!=null) {
            throw AssemblyError("const-compare should have been optimized away")
        }
        else if(left is PtIdentifier && rightConstVal!=null) {
            val leftName = asmVariableName(left)
            val rightName = allocator.getFloatAsmConst(rightConstVal.number)
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
        else if(leftConstVal!=null && right is PtIdentifier) {
            throw AssemblyError("const-compare should have been optimized to have const as right operand")
        }
        else if(left is PtIdentifier && right is PtIdentifier) {
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
            val subroutine = left.definingSub()!!
            subroutineExtra(subroutine).usedFloatEvalResultVar1 = true
            assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
            assignExpressionToRegister(left, RegisterOrPair.FAC1)
            out("""
                lda  #<$subroutineFloatEvalResultVar1
                ldy  #>$subroutineFloatEvalResultVar1
                jsr  floats.var_fac1_greatereq_f
                beq  $jumpIfFalseLabel""")
        }
    }

    private fun translateUbyteLessOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

        fun code(cmpOperand: String) {
            out("  cmp  $cmpOperand |  bcs  $jumpIfFalseLabel")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal>=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is PtIdentifier) {
                    return if(rightConstVal.number.toInt()!=0) {
                        assignExpressionToRegister(left, RegisterOrPair.A)
                        code("#${rightConstVal.number.toInt()}")
                    }
                    else
                        jmp(jumpIfFalseLabel)
                }
                else if (left is PtMemoryByte) {
                    return if(rightConstVal.number.toInt()!=0) {
                        translateDirectMemReadExpressionToRegA(left)
                        code("#${rightConstVal.number.toInt()}")
                    }
                    else
                        jmp(jumpIfFalseLabel)
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        assignByteOperandsToAAndVar(left, right, "P8ZP_SCRATCH_B1")
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateByteLessOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

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
                if (left is PtIdentifier) {
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

        assignByteOperandsToAAndVar(left, right, "P8ZP_SCRATCH_B1")
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateUwordLessOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

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
                if (left is PtIdentifier) {
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

        assignWordOperandsToAYAndVar(left, right, "P8ZP_SCRATCH_W2")
        return out("  jsr  prog8_lib.reg_less_uw |  beq  $jumpIfFalseLabel")
    }

    private fun translateWordLessOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

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
                if (left is PtIdentifier) {
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

        assignWordOperandsToAYAndVar(left, right, "P8ZP_SCRATCH_W2")
        return out("  jsr  prog8_lib.reg_less_w |  beq  $jumpIfFalseLabel")
    }

    private fun translateUbyteGreaterOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

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
                if (left is PtIdentifier) {
                    assignExpressionToRegister(left, RegisterOrPair.A)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  beq $jumpIfFalseLabel")
                }
                else if (left is PtMemoryByte) {
                    translateDirectMemReadExpressionToRegA(left)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  beq  $jumpIfFalseLabel")
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        assignByteOperandsToAAndVar(left, right, "P8ZP_SCRATCH_B1")
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateByteGreaterOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

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
                if (left is PtIdentifier) {
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

        assignByteOperandsToAAndVar(left, right, "P8ZP_SCRATCH_B1")
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateUwordGreaterOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

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
                if (left is PtIdentifier) {
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

        assignWordOperandsToAYAndVar(left, right, "P8ZP_SCRATCH_W2")
        return code("P8ZP_SCRATCH_W2+1", "P8ZP_SCRATCH_W2")
    }

    private fun translateWordGreaterOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

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
                if (left is PtIdentifier) {
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

        assignWordOperandsToAYAndVar(right, left, "P8ZP_SCRATCH_W2")
        return out("  jsr  prog8_lib.reg_less_w |  beq  $jumpIfFalseLabel")
    }

    private fun translateUbyteLessOrEqualOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

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
                if (left is PtIdentifier) {
                    assignExpressionToRegister(left, RegisterOrPair.A)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  bne  $jumpIfFalseLabel")
                }
                else if (left is PtMemoryByte) {
                    translateDirectMemReadExpressionToRegA(left)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  bne  $jumpIfFalseLabel")
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        assignByteOperandsToAAndVar(left, right, "P8ZP_SCRATCH_B1")
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateByteLessOrEqualOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {
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
                if (left is PtIdentifier) {
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

        assignByteOperandsToAAndVar(left, right, "P8ZP_SCRATCH_B1")
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateUwordLessOrEqualOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

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
                if (left is PtIdentifier) {
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

        assignWordOperandsToAYAndVar(left, right, "P8ZP_SCRATCH_W2")
        return out("  jsr  prog8_lib.reg_lesseq_uw |  beq  $jumpIfFalseLabel")
    }

    private fun translateWordLessOrEqualOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

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
                if (left is PtIdentifier) {
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

        if(left is PtIdentifier) {
            assignExpressionToRegister(right, RegisterOrPair.AY)
            return code(asmVariableName(left))
        }

        assignWordOperandsToAYAndVar(right, left, "P8ZP_SCRATCH_W2")
        return out("  jsr  prog8_lib.reg_lesseq_w |  beq  $jumpIfFalseLabel")
    }

    private fun translateUbyteGreaterOrEqualOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

        fun code(cmpOperand: String) {
            out("  cmp  $cmpOperand |  bcc  $jumpIfFalseLabel")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal<leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is PtIdentifier) {
                    if(rightConstVal.number.toInt()!=0) {
                        assignExpressionToRegister(left, RegisterOrPair.A)
                        code("#${rightConstVal.number.toInt()}")
                    }
                    return
                }
                else if (left is PtMemoryByte) {
                    if(rightConstVal.number.toInt()!=0) {
                        translateDirectMemReadExpressionToRegA(left)
                        code("#${rightConstVal.number.toInt()}")
                    }
                    return
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        assignByteOperandsToAAndVar(left, right, "P8ZP_SCRATCH_B1")
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateByteGreaterOrEqualOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {
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
                if (left is PtIdentifier) {
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

        assignByteOperandsToAAndVar(left, right, "P8ZP_SCRATCH_B1")
        return code("P8ZP_SCRATCH_B1")
    }

    internal fun assignByteOperandsToAAndVar(left: PtExpression, right: PtExpression, rightVarName: String) {
        if(left.isSimple()) {
            assignExpressionToVariable(right, rightVarName, DataType.UBYTE)
            assignExpressionToRegister(left, RegisterOrPair.A)
        } else {
            pushCpuStack(DataType.UBYTE, left)
            assignExpressionToVariable(right, rightVarName, DataType.UBYTE)
            out("  pla")
        }
    }

    internal fun assignWordOperandsToAYAndVar(left: PtExpression, right: PtExpression, rightVarname: String) {
        if(left.isSimple()) {
            assignExpressionToVariable(right, rightVarname, DataType.UWORD)
            assignExpressionToRegister(left, RegisterOrPair.AY)
        }  else {
            pushCpuStack(DataType.UWORD, left)
            assignExpressionToVariable(right, rightVarname, DataType.UWORD)
            restoreRegisterStack(CpuRegister.Y, false)
            restoreRegisterStack(CpuRegister.A, false)
        }
    }

    private fun translateUwordGreaterOrEqualOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

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
                if (left is PtIdentifier) {
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

        assignWordOperandsToAYAndVar(right, left, "P8ZP_SCRATCH_W2")
        return out("  jsr  prog8_lib.reg_lesseq_uw |  beq  $jumpIfFalseLabel")
    }

    private fun translateWordGreaterOrEqualOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

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
                if (left is PtIdentifier) {
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

        assignWordOperandsToAYAndVar(left, right, "P8ZP_SCRATCH_W2")
        return out("  jsr  prog8_lib.reg_lesseq_w |  beq  $jumpIfFalseLabel")
    }

    private fun translateByteEqualsOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {
        fun code(cmpOperand: String) {
            out("  cmp  $cmpOperand |  bne  $jumpIfFalseLabel")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal!=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is PtIdentifier) {
                    assignExpressionToRegister(left, RegisterOrPair.A)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  bne  $jumpIfFalseLabel")
                }
                else if (left is PtMemoryByte) {
                    translateDirectMemReadExpressionToRegA(left)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  bne  $jumpIfFalseLabel")
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        if(left.isSimple()) {
            assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
            assignExpressionToRegister(left, RegisterOrPair.A)
        } else if(right.isSimple()) {
            assignExpressionToVariable(left, "P8ZP_SCRATCH_B1", DataType.UBYTE)
            assignExpressionToRegister(right, RegisterOrPair.A)
        } else {
            pushCpuStack(DataType.UBYTE, left)
            assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
            restoreRegisterStack(CpuRegister.A, false)
        }
        out("  cmp  P8ZP_SCRATCH_B1 |  bne  $jumpIfFalseLabel")
    }

    private fun translateByteNotEqualsOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

        fun code(cmpOperand: String) {
            out("  cmp  $cmpOperand |  beq  $jumpIfFalseLabel")
        }

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal==leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is PtIdentifier) {
                    assignExpressionToRegister(left, RegisterOrPair.A)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  beq  $jumpIfFalseLabel")
                }
                else if (left is PtMemoryByte) {
                    translateDirectMemReadExpressionToRegA(left)
                    return if(rightConstVal.number.toInt()!=0)
                        code("#${rightConstVal.number.toInt()}")
                    else
                        out("  beq  $jumpIfFalseLabel")
                }
            }
        }

        if(byteJumpForSimpleRightOperand(left, right, ::code))
            return

        if(left.isSimple()) {
            assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
            assignExpressionToRegister(left, RegisterOrPair.A)
        } else if(right.isSimple()) {
            assignExpressionToVariable(left, "P8ZP_SCRATCH_B1", DataType.UBYTE)
            assignExpressionToRegister(right, RegisterOrPair.A)
        } else {
            pushCpuStack(DataType.UBYTE, left)
            assignExpressionToVariable(right, "P8ZP_SCRATCH_B1", DataType.UBYTE)
            restoreRegisterStack(CpuRegister.A, false)
        }
        return code("P8ZP_SCRATCH_B1")
    }

    private fun translateWordEqualsOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal!=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is PtIdentifier) {
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
            is PtNumber -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                val number = right.number.toHex()
                out("""
                    cmp  #<$number
                    bne  $jumpIfFalseLabel
                    cpy  #>$number
                    bne  $jumpIfFalseLabel""")
            }
            is PtIdentifier -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                out("""
                    cmp  ${asmVariableName(right)}
                    bne  $jumpIfFalseLabel
                    cpy  ${asmVariableName(right)}+1
                    bne  $jumpIfFalseLabel""")
            }
            is PtAddressOf -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                val name = asmSymbolName(right.identifier)
                if(right.isFromArrayElement) {
                    TODO("address-of array element $name at ${right.position}")
                    // assignmentAsmGen.assignAddressOf(target, name, right.arrayIndexExpr)
                } else {
                    out("""
                        cmp  #<$name
                        bne  $jumpIfFalseLabel
                        cpy  #>$name
                        bne  $jumpIfFalseLabel""")
                }
            }
            else -> {
                if(left.isSimple()) {
                    assignExpressionToVariable(right, "P8ZP_SCRATCH_W2", DataType.UWORD)
                    assignExpressionToRegister(left, RegisterOrPair.AY)
                } else if(right.isSimple()) {
                    assignExpressionToVariable(left, "P8ZP_SCRATCH_W2", DataType.UWORD)
                    assignExpressionToRegister(right, RegisterOrPair.AY)
                } else {
                    pushCpuStack(DataType.UWORD, left)
                    assignExpressionToVariable(right, "P8ZP_SCRATCH_W2", DataType.UWORD)
                    restoreRegisterStack(CpuRegister.Y, false)
                    restoreRegisterStack(CpuRegister.A, false)
                }
                out("""
                    cmp  P8ZP_SCRATCH_W2
                    bne  $jumpIfFalseLabel
                    cpy  P8ZP_SCRATCH_W2+1
                    bne  $jumpIfFalseLabel""")
            }
        }

    }

    private fun translateWordNotEqualsOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {

        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal==leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is PtIdentifier) {
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
            is PtNumber -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                val number = right.number.toHex()
                out("""
                    cmp  #<$number
                    bne  +
                    cpy  #>$number
                    beq  $jumpIfFalseLabel
+""")
            }
            is PtIdentifier -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                out("""
                    cmp  ${asmVariableName(right)}
                    bne  +
                    cpy  ${asmVariableName(right)}+1
                    beq  $jumpIfFalseLabel
+""")
            }
            is PtAddressOf -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                val name = asmSymbolName(right.identifier)
                if(right.isFromArrayElement) {
                    TODO("address-of array element $name at ${right.position}")
                } else {
                    out("""
                        cmp  #<$name
                        bne  +
                        cpy  #>$name
                        beq  $jumpIfFalseLabel
    +""")
                }
            }
            else -> {
                if(left.isSimple()) {
                    assignExpressionToVariable(right, "P8ZP_SCRATCH_W2", DataType.UWORD)
                    assignExpressionToRegister(left, RegisterOrPair.AY)
                } else if (right.isSimple()) {
                    assignExpressionToVariable(left, "P8ZP_SCRATCH_W2", DataType.UWORD)
                    assignExpressionToRegister(right, RegisterOrPair.AY)
                } else {
                    pushCpuStack(DataType.UWORD, left)
                    assignExpressionToVariable(right, "P8ZP_SCRATCH_W2", DataType.UWORD)
                    restoreRegisterStack(CpuRegister.Y, false)
                    restoreRegisterStack(CpuRegister.A, false)
                }
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

    private fun translateFloatEqualsOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal!=leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is PtIdentifier) {
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
        else if(leftConstVal!=null && right is PtIdentifier) {
            throw AssemblyError("const-compare should have been optimized to have const as right operand")
        }
        else if(left is PtIdentifier && rightConstVal!=null) {
            val leftName = asmVariableName(left)
            val rightName = allocator.getFloatAsmConst(rightConstVal.number)
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
        else if(left is PtIdentifier && right is PtIdentifier) {
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
            val subroutine = left.definingSub()!!
            subroutineExtra(subroutine).usedFloatEvalResultVar1 = true
            assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
            assignExpressionToRegister(left, RegisterOrPair.FAC1)
            out("""
                lda  #<$subroutineFloatEvalResultVar1
                ldy  #>$subroutineFloatEvalResultVar1
                jsr  floats.var_fac1_notequal_f
                bne  $jumpIfFalseLabel""")
        }
    }

    private fun translateFloatNotEqualsOrJumpElsewhere(left: PtExpression, right: PtExpression, leftConstVal: PtNumber?, rightConstVal: PtNumber?, jumpIfFalseLabel: String) {
        if(rightConstVal!=null) {
            if(leftConstVal!=null) {
                if(rightConstVal==leftConstVal)
                    jmp(jumpIfFalseLabel)
                return
            } else {
                if (left is PtIdentifier) {
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
        else if(leftConstVal!=null && right is PtIdentifier) {
            throw AssemblyError("const-compare should have been optimized to have const as right operand")
        }
        else if(left is PtIdentifier && rightConstVal!=null) {
            val leftName = asmVariableName(left)
            val rightName = allocator.getFloatAsmConst(rightConstVal.number)
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
        else if(left is PtIdentifier && right is PtIdentifier) {
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
            val subroutine = left.definingSub()!!
            subroutineExtra(subroutine).usedFloatEvalResultVar1 = true
            assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
            assignExpressionToRegister(left, RegisterOrPair.FAC1)
            out("""
                lda  #<$subroutineFloatEvalResultVar1
                ldy  #>$subroutineFloatEvalResultVar1
                jsr  floats.var_fac1_notequal_f
                beq  $jumpIfFalseLabel""")
        }
    }

    private fun translateStringEqualsOrJumpElsewhere(left: PtIdentifier, right: PtIdentifier, jumpIfFalseLabel: String) {
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
            bne  $jumpIfFalseLabel""")
    }

    private fun translateStringNotEqualsOrJumpElsewhere(left: PtIdentifier, right: PtIdentifier, jumpIfFalseLabel: String) {
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
            beq  $jumpIfFalseLabel""")
    }

    private fun translateStringLessOrJumpElsewhere(left: PtIdentifier, right: PtIdentifier, jumpIfFalseLabel: String) {
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

    private fun translateStringGreaterOrJumpElsewhere(left: PtIdentifier, right: PtIdentifier, jumpIfFalseLabel: String) {
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

    private fun translateStringLessOrEqualOrJumpElsewhere(left: PtIdentifier, right: PtIdentifier, jumpIfFalseLabel: String) {
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

    private fun translateStringGreaterOrEqualOrJumpElsewhere(left: PtIdentifier, right: PtIdentifier, jumpIfFalseLabel: String) {
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

    internal fun translateDirectMemReadExpressionToRegA(expr: PtMemoryByte) {

        fun assignViaExprEval() {
            assignExpressionToVariable(expr.address, "P8ZP_SCRATCH_W2", DataType.UWORD)
            if (isTargetCpu(CpuType.CPU65c02)) {
                out("  lda  (P8ZP_SCRATCH_W2)")
            } else {
                out("  ldy  #0 |  lda  (P8ZP_SCRATCH_W2),y")
            }
        }

        when(expr.address) {
            is PtNumber -> {
                val address = (expr.address as PtNumber).number.toInt()
                out("  lda  ${address.toHex()}")
            }
            is PtIdentifier -> {
                // the identifier is a pointer variable, so read the value from the address in it
                loadByteFromPointerIntoA(expr.address as PtIdentifier)
            }
            is PtBinaryExpression -> {
                val addrExpr = expr.address as PtBinaryExpression
                if(!tryOptimizedPointerAccessWithA(addrExpr, addrExpr.operator, false)) {
                    assignViaExprEval()
                }
            }
            else -> assignViaExprEval()
        }
    }

    private fun wordJumpForSimpleLeftOperand(left: PtExpression, right: PtExpression, code: (String, String)->Unit): Boolean {
        when (left) {
            is PtNumber -> {
                assignExpressionToRegister(right, RegisterOrPair.AY)
                val number = left.number.toHex()
                code("#>$number", "#<$number")
                return true
            }
            is PtAddressOf -> {
                assignExpressionToRegister(right, RegisterOrPair.AY)
                val name = asmSymbolName(left.identifier)
                if(left.isFromArrayElement) {
                    TODO("address-of array element $name at ${left.position}")
                } else {
                    code("#>$name", "#<$name")
                    return true
                }
            }
            is PtIdentifier -> {
                assignExpressionToRegister(right, RegisterOrPair.AY)
                val varname = asmVariableName(left)
                code("$varname+1", varname)
                return true
            }
            else -> return false
        }
    }

    private fun byteJumpForSimpleRightOperand(left: PtExpression, right: PtExpression, code: (String)->Unit): Boolean {
        if(right is PtNumber) {
            assignExpressionToRegister(left, RegisterOrPair.A)
            code("#${right.number.toHex()}")
            return true
        }
        if(right is PtIdentifier) {
            assignExpressionToRegister(left, RegisterOrPair.A)
            code(asmVariableName(right))
            return true
        }
        var memread = right as? PtMemoryByte
        if(memread==null && right is PtTypeCast)
            memread = right.value as? PtMemoryByte
        if(memread!=null) {
            val address = memread.address as? PtNumber
            if(address!=null) {
                assignExpressionToRegister(left, RegisterOrPair.A)
                code(address.number.toHex())
                return true
            }
        }
        return false
    }

    private fun wordJumpForSimpleRightOperands(left: PtExpression, right: PtExpression, code: (String, String)->Unit): Boolean {
        when (right) {
            is PtNumber -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                val number = right.number.toHex()
                code("#>$number", "#<$number")
                return true
            }
            is PtAddressOf -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                val name = asmSymbolName(right.identifier)
                if(right.isFromArrayElement) {
                    TODO("address-of array element $name at ${right.position}")
                } else {
                    code("#>$name", "#<$name")
                    return true
                }
            }
            is PtIdentifier -> {
                assignExpressionToRegister(left, RegisterOrPair.AY)
                val varname = asmVariableName(right)
                code("$varname+1", varname)
                return true
            }
            else -> return false
        }
    }

    internal fun popCpuStack(asmsub: PtAsmSub, parameter: PtSubroutineParameter, reg: RegisterOrStatusflag) {
        val shouldKeepA = asmsub.parameters.any { it.first.registerOrPair==RegisterOrPair.AX || it.first.registerOrPair==RegisterOrPair.AY}
        if(reg.statusflag!=null) {
            if(shouldKeepA)
                out("  sta  P8ZP_SCRATCH_REG")
            out("""
                    clc
                    pla
                    beq  +
                    sec
+""")
            if(shouldKeepA)
                out("  lda  P8ZP_SCRATCH_REG")
        }
        else {
            if (parameter.type in ByteDatatypes) {
                if (isTargetCpu(CpuType.CPU65c02)) {
                    when (reg.registerOrPair) {
                        RegisterOrPair.A -> out("  pla")
                        RegisterOrPair.X -> out("  plx")
                        RegisterOrPair.Y -> out("  ply")
                        in Cx16VirtualRegisters -> out("  pla |  sta  cx16.${reg.registerOrPair!!.name.lowercase()}")
                        else -> throw AssemblyError("invalid target register ${reg.registerOrPair}")
                    }
                } else {
                    when (reg.registerOrPair) {
                        RegisterOrPair.A -> out("  pla")
                        RegisterOrPair.X -> {
                            if(shouldKeepA)
                                out("  sta  P8ZP_SCRATCH_REG |  pla |  tax |  lda  P8ZP_SCRATCH_REG")
                            else
                                out("  pla |  tax")
                        }
                        RegisterOrPair.Y -> {
                            if(shouldKeepA)
                                out("  sta  P8ZP_SCRATCH_REG |  pla |  tay |  lda  P8ZP_SCRATCH_REG")
                            else
                                out("  pla |  tay")
                        }
                        in Cx16VirtualRegisters -> out("  pla |  sta  cx16.${reg.registerOrPair!!.name.lowercase()}")
                        else -> throw AssemblyError("invalid target register ${reg.registerOrPair}")
                    }
                }
            } else {
                // word pop
                if (isTargetCpu(CpuType.CPU65c02))
                    when (reg.registerOrPair) {
                        RegisterOrPair.AX -> out("  plx |  pla")
                        RegisterOrPair.AY -> out("  ply |  pla")
                        RegisterOrPair.XY -> out("  ply |  plx")
                        in Cx16VirtualRegisters -> {
                            val regname = reg.registerOrPair!!.name.lowercase()
                            out("  pla |  sta  cx16.$regname+1 |  pla |  sta  cx16.$regname")
                        }
                        else -> throw AssemblyError("invalid target register ${reg.registerOrPair}")
                    }
                else {
                    when (reg.registerOrPair) {
                        RegisterOrPair.AX -> out("  pla |  tax |  pla")
                        RegisterOrPair.AY -> out("  pla |  tay |  pla")
                        RegisterOrPair.XY -> out("  pla |  tay |  pla |  tax")
                        in Cx16VirtualRegisters -> {
                            val regname = reg.registerOrPair!!.name.lowercase()
                            out("  pla |  sta  cx16.$regname+1 |  pla |  sta  cx16.$regname")
                        }
                        else -> throw AssemblyError("invalid target register ${reg.registerOrPair}")
                    }
                }
            }
        }
    }

    internal fun popCpuStack(dt: DataType, target: IPtVariable, scope: IPtSubroutine?) {
        // note: because A is pushed first so popped last, saving A is often not required here.
        val targetAsmSub = (target as PtNode).definingAsmSub()
        if(targetAsmSub != null) {
            val parameter = targetAsmSub.parameters.first { it.second.name==target.name }
            popCpuStack(targetAsmSub, parameter.second, parameter.first)
            return
        }
        val scopedName = when(target) {
            is PtConstant -> target.scopedName
            is PtMemMapped -> target.scopedName
            is PtVariable -> target.scopedName
            else -> throw AssemblyError("weird target var")
        }
        val tgt = AsmAssignTarget(TargetStorageKind.VARIABLE, this, target.type, scope, target.position, variableAsmName = asmVariableName(scopedName))
        if (dt in ByteDatatypes) {
            out("  pla")
            assignRegister(RegisterOrPair.A, tgt)
        } else {
            if (isTargetCpu(CpuType.CPU65c02))
                out("  ply |  pla")
            else
                out("  pla |  tay |  pla")
            assignRegister(RegisterOrPair.AY, tgt)
        }
    }

    internal fun pushCpuStack(dt: DataType, value: PtExpression) {
        val signed = value.type.oneOf(DataType.BYTE, DataType.WORD)
        if(dt in ByteDatatypes) {
            assignExpressionToRegister(value, RegisterOrPair.A, signed)
            out("  pha")
        } else if(dt in WordDatatypes) {
            assignExpressionToRegister(value, RegisterOrPair.AY, signed)
            if (isTargetCpu(CpuType.CPU65c02))
                out("  pha |  phy")
            else
                out("  pha |  tya |  pha")
        } else {
            throw AssemblyError("can't push $dt")
        }
    }

    internal fun pushFAC1() {
        out("  jsr  floats.pushFAC1")
    }

    internal fun popFAC1() {
        out("  clc |  jsr  floats.popFAC")
    }

    internal fun popFAC2() {
        out("  sec |  jsr  floats.popFAC")
    }

    internal fun needAsaveForExpr(arg: PtExpression): Boolean =
        arg !is PtNumber && arg !is PtIdentifier && (arg !is PtMemoryByte || !arg.isSimple())

    private val subroutineExtrasCache = mutableMapOf<IPtSubroutine, SubroutineExtraAsmInfo>()

    internal fun subroutineExtra(sub: IPtSubroutine): SubroutineExtraAsmInfo {
        var extra = subroutineExtrasCache[sub]
        return if(extra==null) {
            extra = SubroutineExtraAsmInfo()
            subroutineExtrasCache[sub] = extra
            extra
        }
        else
            extra
    }

    private var generatedLabelSequenceNumber: Int = 0

    internal fun makeLabel(postfix: String): String {
        generatedLabelSequenceNumber++
        return "label_asm_${generatedLabelSequenceNumber}_$postfix"
    }

    fun assignConstFloatToPointerAY(number: PtNumber) {
        val floatConst = allocator.getFloatAsmConst(number.number)
        out("""
            pha
            lda  #<$floatConst
            sta  P8ZP_SCRATCH_W1
            lda  #>$floatConst
            sta  P8ZP_SCRATCH_W1+1
            pla
            jsr  floats.copy_float""")
    }
}

/**
 * Contains various attributes that influence the assembly code generator.
 * Conceptually it should be part of any INameScope.
 * But because the resulting code only creates "real" scopes on a subroutine level,
 * it's more consistent to only define these attributes on a Subroutine node.
 */
internal class SubroutineExtraAsmInfo {
    var usedFloatEvalResultVar1 = false
    var usedFloatEvalResultVar2 = false

    val extraVars = mutableListOf<Triple<DataType, String, UInt?>>()
}