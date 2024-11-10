package prog8.codegen.cpu6502

import com.github.michaelbull.result.fold
import prog8.code.StNode
import prog8.code.StNodeType
import prog8.code.SymbolTable
import prog8.code.SymbolTableMaker
import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.target.Cx16Target
import prog8.codegen.cpu6502.assignment.*
import kotlin.io.path.Path
import kotlin.io.path.writeLines


internal const val subroutineFloatEvalResultVar1 = "prog8_float_eval_result1"
internal const val subroutineFloatEvalResultVar2 = "prog8_float_eval_result2"

class AsmGen6502(val prefixSymbols: Boolean, private val lastGeneratedLabelSequenceNr: Int): ICodeGeneratorBackend {
    override fun generate(
        program: PtProgram,
        symbolTable: SymbolTable,
        options: CompilationOptions,
        errors: IErrorReporter,
    ): IAssemblyProgram? {
        val st = if(prefixSymbols) prefixSymbols(program, options, symbolTable) else symbolTable
        val asmgen = AsmGen6502Internal(program, st, options, errors, lastGeneratedLabelSequenceNr)
        return asmgen.compileToAssembly()
    }

    private fun prefixSymbols(program: PtProgram, options: CompilationOptions, st: SymbolTable): SymbolTable {
        val nodesToPrefix = mutableListOf<Pair<PtNode, Int>>()              // parent + index
        val functionCallsToPrefix = mutableListOf<Pair<PtNode, Int>>()      // parent + index

        fun prefixNamedNode(node: PtNamedNode) {
            when(node) {
                is PtAsmSub, is PtSub -> node.name = "p8s_${node.name}"
                is PtBlock -> node.name = "p8b_${node.name}"
                is PtLabel -> if(!node.name.startsWith(PtLabel.GeneratedLabelPrefix)) node.name = "p8l_${node.name}"
                is PtConstant -> node.name = "p8c_${node.name}"
                is PtVariable, is PtMemMapped, is PtSubroutineParameter -> node.name = "p8v_${node.name}"
                else -> node.name = "p8_${node.name}"
            }
        }

        fun prefixSymbols(node: PtNode) {
            when(node) {
                is PtAsmSub -> {
                    prefixNamedNode(node)
                    node.parameters.forEach { (_, param) -> prefixNamedNode(param) }
                    if(node.address?.varbank!=null) {
                        node.address!!.varbank = node.address!!.varbank!!.prefix(node, st)
                    }
                }
                is PtSub -> {
                    prefixNamedNode(node)
                    node.parameters.forEach { prefixNamedNode(it) }
                }
                is PtFunctionCall -> {
                    val stNode = st.lookup(node.name)!!
                    if(stNode.astNode.definingBlock()?.options?.noSymbolPrefixing!=true) {
                        val index = node.parent.children.indexOf(node)
                        functionCallsToPrefix += node.parent to index
                    }
                }
                is PtIdentifier -> {
                    var lookupName = node.name
                    if(node.type in SplitWordArrayTypes && (lookupName.endsWith("_lsb") || lookupName.endsWith("_msb"))) {
                        lookupName = lookupName.dropLast(4)
                    }
                    val stNode = st.lookup(lookupName) ?: throw AssemblyError("unknown identifier $node")
                    if(stNode.astNode.definingBlock()?.options?.noSymbolPrefixing!=true) {
                        val index = node.parent.children.indexOf(node)
                        nodesToPrefix += node.parent to index
                    }
                }
                is PtJump -> {
                    val stNode = st.lookup(node.identifier!!.name) ?: throw AssemblyError("name not found ${node.identifier}")
                    if(stNode.astNode.definingBlock()?.options?.noSymbolPrefixing!=true) {
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
            if (!block.options.noSymbolPrefixing) {
                prefixSymbols(block)
            }
        }

        nodesToPrefix.forEach { (parent, index) ->
            when(val node = parent.children[index]) {
                is PtIdentifier -> parent.children[index] = node.prefix(parent, st)
                is PtFunctionCall ->  throw AssemblyError("PtFunctionCall should be processed in their own list, last")
                is PtJump -> parent.children[index] = node.prefix(parent, st)
                is PtVariable -> parent.children[index] = node.prefix(parent, st)
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
    if('.' !in name) {
        if(name.startsWith(PtLabel.GeneratedLabelPrefix))
            return name
        return "p8${type}_$name"
    }
    val parts = name.split('.')
    val firstPrefixed = "p8b_${parts[0]}"
    val lastPart = parts.last()
    val lastPrefixed = if(lastPart.startsWith(PtLabel.GeneratedLabelPrefix)) lastPart else  "p8${type}_$lastPart"
    // the parts in between are assumed to be subroutine scopes.
    val inbetweenPrefixed = parts.drop(1).dropLast(1).map{ "p8s_$it" }
    val prefixed = listOf(firstPrefixed) + inbetweenPrefixed + listOf(lastPrefixed)
    return prefixed.joinToString(".")
}

private fun PtVariable.prefix(parent: PtNode, st: SymbolTable): PtVariable {
    name = prefixScopedName(name, 'v')
    if(value==null)
        return this

    val arrayValue = value as? PtArray
    return if(arrayValue!=null && arrayValue.children.any { it !is PtNumber && it !is PtBool } ) {
        val newValue = PtArray(arrayValue.type, arrayValue.position)
        arrayValue.children.forEach { elt ->
            when(elt) {
                is PtBool -> newValue.add(elt)
                is PtNumber -> newValue.add(elt)
                is PtAddressOf -> {
                    if(elt.definingBlock()?.options?.noSymbolPrefixing==true)
                        newValue.add(elt)
                    else {
                        val newAddr = PtAddressOf(elt.position)
                        newAddr.children.add(elt.identifier.prefix(newAddr, st))
                        if(elt.arrayIndexExpr!=null)
                            newAddr.children.add(elt.arrayIndexExpr!!)
                        newAddr.parent = arrayValue
                        newValue.add(newAddr)
                    }
                }
                else -> throw AssemblyError("weird array value element $elt")
            }
        }
        val result = PtVariable(name, type, zeropage, align, newValue, arraySize, position)
        result.parent = parent
        result
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
    if(target?.astNode?.definingBlock()?.options?.noSymbolPrefixing==true)
        return this

    if(target==null) {
        if(name.endsWith("_lsb") || name.endsWith("_msb")) {
            target = st.lookup(name.dropLast(4))
            if(target?.astNode?.definingBlock()?.options?.noSymbolPrefixing==true)
                return this
        }
    }

    val prefixType = when(target!!.type) {
        StNodeType.BLOCK -> 'b'
        StNodeType.SUBROUTINE, StNodeType.EXTSUB -> 's'
        StNodeType.LABEL -> 'l'
        StNodeType.STATICVAR, StNodeType.MEMVAR -> 'v'
        StNodeType.CONSTANT -> 'c'
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
    internal val errors: IErrorReporter,
    private var generatedLabelSequenceNumber: Int
) {

    internal val optimizedByteMultiplications = setOf(3,5,6,7,9,10,11,12,13,14,15,20,25,40,50,80,100)
    internal val optimizedWordMultiplications = setOf(3,5,6,7,9,10,12,15,20,25,40,50,80,100,320,640)
    internal val loopEndLabels = ArrayDeque<String>()
    private val zeropage = options.compTarget.machine.zeropage
    private val allocator = VariableAllocator(symbolTable, options, errors)
    private val assembly = mutableListOf<String>()
    private val breakpointLabels = mutableListOf<String>()
    private val forloopsAsmGen = ForLoopsAsmGen(this, zeropage)
    private val functioncallAsmGen = FunctionCallAsmGen(program, this)
    private val programGen = ProgramAndVarsGen(program, options, errors, symbolTable, functioncallAsmGen, this, allocator, zeropage)
    private val anyExprGen = AnyExprAsmGen(this)
    private val assignmentAsmGen = AssignmentAsmGen(program, this, anyExprGen, allocator)
    private val builtinFunctionsAsmGen = BuiltinFunctionsAsmGen(program, this, assignmentAsmGen)
    private val ifElseAsmgen = IfElseAsmGen(program, symbolTable, this, allocator, assignmentAsmGen, errors)

    fun compileToAssembly(): IAssemblyProgram? {

        assembly.clear()
        loopEndLabels.clear()

        println("Generating assembly code... ")
        programGen.generate()

        if(errors.noErrors()) {
            val output = options.outputDir.resolve("${program.name}.asm")
            val asmLines = assembly.flatMapTo(mutableListOf()) { it.split('\n') }
            if(options.compTarget.name==Cx16Target.NAME) {
                scanInvalid65816instructions(asmLines)
                if(!errors.noErrors()) {
                    errors.report()
                    return null
                }
            }
            if(options.optimize) {
                while(optimizeAssembly(asmLines, options.compTarget.machine, symbolTable)>0) {
                    // optimize the assembly source code
                }
                output.writeLines(asmLines)
            } else {
                // write the unmodified code
                output.writeLines(assembly)
            }

            if(options.dumpVariables)
                dumpVariables()

            return AssemblyProgram(program.name, options.outputDir, options.compTarget)
        } else {
            errors.report()
            return null
        }
    }

    private fun dumpVariables() {
        println("---- VARIABLES DUMP ----")
        if(allocator.globalFloatConsts.isNotEmpty()) {
            println("Floats:")
            allocator.globalFloatConsts.forEach { (value, name) ->
                println("  $name = $value")
            }
        }
        if(symbolTable.allMemorySlabs.isNotEmpty()) {
            println("Memory slabs:")
            symbolTable.allMemorySlabs.sortedBy { it.name }.forEach { slab ->
                println("  ${slab.name}  ${slab.size}  align ${slab.align}")
            }
        }
        if(symbolTable.allMemMappedVariables.isNotEmpty()) {
            println("Memory mapped:")
            symbolTable.allMemMappedVariables
                .sortedWith( compareBy( {it.address}, {it.scopedName} ))
                .forEach { mvar ->
                    println("  ${'$'}${mvar.address.toString(16).padStart(4, '0')}\t${mvar.dt}\t${mvar.scopedName}")
                }
        }
        if(allocator.zeropageVars.isNotEmpty()) {
            println("ZeroPage:")
            allocator.zeropageVars
                .asSequence()
                .sortedWith( compareBy( {it.value.address}, {it.key} ))
                .forEach { (name, alloc) ->
                    println("  ${'$'}${alloc.address.toString(16).padStart(2, '0')}\t${alloc.dt}\t$name")
                }
        }
        if(symbolTable.allVariables.isNotEmpty()) {
            println("Static variables (not in ZeroPage):")
            symbolTable.allVariables
                .filterNot { allocator.isZpVar(it.scopedName) }
                .sortedBy { it.scopedName }.forEach {
                    println("  ${it.dt}\t${it.scopedName}\t")
                }
        }
        println("---- VARIABLES DUMP END ----")
    }

    private fun scanInvalid65816instructions(asmLines: MutableList<String>) {
        // The CommanderX16 ships with a WDC 65C02 CPU or a WDC 65816 CPU
        // The latter is compatible with the 65C02 except for 4 instructions: RMB, SMB, BBS, BBR.
        // We cannot set a different 6502 CPU target for the 64tass assembler, because we still need to support the STP and WAI instructions...
        // so we have to scan for these instructions ourselves.
        val invalid = Regex("""\s*((rmb\s|smb\s|bbs\s|bbr\s)|(rmb[0-7]|smb[0-7]|bbs[0-7]|bbr[0-7]))""", RegexOption.IGNORE_CASE)
        for((index, line) in asmLines.withIndex()) {
            if(line.length>=4 && invalid.matchesAt(line, 0)) {
                errors.err(
                    "invalid assembly instruction used (not compatible with the 65816 CPU): ${line.trim()}",
                    Position("<output-assemblycode>", index, 1, 1)
                )
            }
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
                assembly.add(trimmed)
            }
        } else assembly.add(fragment)
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
        val subName = identifier.definingISub()?.scopedName
        return if (subName != null && name.length>subName.length && name.startsWith(subName) && name[subName.length] == '.')
            name.drop(subName.length + 1)
        else
            name
    }

    fun asmVariableName(identifier: PtIdentifier): String {
        val name = asmVariableName(identifier.name)

        // see if we're inside a subroutine, if so, remove the whole prefix and just make the variable name locally scoped (64tass scopes it to the proper .proc block)
        val subName = identifier.definingISub()?.scopedName
        return if (subName != null && name.length>subName.length && name.startsWith(subName) && name[subName.length] == '.')
            name.drop(subName.length+1)
        else
            name
    }

    fun asmVariableName(st: StNode, scope: IPtSubroutine?): String {
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


    internal val tempVarsCounters = mutableMapOf(
        DataType.BOOL to 0,
        DataType.BYTE to 0,
        DataType.UBYTE to 0,
        DataType.WORD to 0,
        DataType.UWORD to 0,
        DataType.FLOAT to 0
    )

    internal fun buildTempVarName(dt: DataType, counter: Int): String = "prog8_tmpvar_${dt.toString().lowercase()}_$counter"

    internal fun getTempVarName(dt: DataType): String {
        tempVarsCounters[dt] = tempVarsCounters.getValue(dt)+1
        return buildTempVarName(dt, tempVarsCounters.getValue(dt))
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
                        loadAFromZpPointerVar(sourceName, true)
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

    internal fun storeAIntoZpPointerVar(zpPointerVar: String, keepY: Boolean) {
        if (isTargetCpu(CpuType.CPU65c02))
            out("  sta  ($zpPointerVar)")
        else {
            if(keepY)
                out("  sty  P8ZP_SCRATCH_REG |  ldy  #0 |  sta  ($zpPointerVar),y |  ldy  P8ZP_SCRATCH_REG")
            else
                out("  ldy  #0 |  sta  ($zpPointerVar),y")
        }
    }

    internal fun loadAFromZpPointerVar(zpPointerVar: String, keepY: Boolean) {
        if (isTargetCpu(CpuType.CPU65c02))
            out("  lda  ($zpPointerVar)")
        else {
            if(keepY)
                out("  sty  P8ZP_SCRATCH_REG |  ldy  #0 |  lda  ($zpPointerVar),y |  php |  ldy  P8ZP_SCRATCH_REG |  plp")
            else
                out("  ldy  #0 |  lda  ($zpPointerVar),y")
        }
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
            is PtAlign -> if(stmt.align > 1u) out("  .align  ${stmt.align.toHex()}")
            is PtReturn -> translate(stmt)
            is PtSub -> programGen.translateSubroutine(stmt)
            is PtAsmSub -> programGen.translateAsmSubroutine(stmt)
            is PtInlineAssembly -> translate(stmt)
            is PtBuiltinFunctionCall -> builtinFunctionsAsmGen.translateFunctioncallStatement(stmt)
            is PtFunctionCall -> functioncallAsmGen.translateFunctionCallStatement(stmt)
            is PtAssignment -> {
                if(stmt.multiTarget) assignmentAsmGen.translateMultiAssign(stmt)
                else assignmentAsmGen.translate(stmt)
            }
            is PtAugmentedAssign -> assignmentAsmGen.translate(stmt)
            is PtJump -> {
                val (asmLabel, indirect) = getJumpTarget(stmt)
                jmp(asmLabel, indirect)
            }
            is PtLabel -> translate(stmt)
            is PtConditionalBranch -> translate(stmt)
            is PtIfElse -> ifElseAsmgen.translate(stmt)
            is PtForLoop -> forloopsAsmGen.translate(stmt)
            is PtRepeatLoop -> translate(stmt)
            is PtWhen -> translate(stmt)
            is PtIncludeBinary -> translate(stmt)
            is PtBreakpoint -> translateBrk()
            is PtVariable, is PtConstant, is PtMemMapped -> { /* do nothing; variables are handled elsewhere */ }
            is PtBlock -> throw AssemblyError("block should have been handled elsewhere")
            is PtDefer -> throw AssemblyError("defer should have been transformed")
            is PtNodeGroup -> stmt.children.forEach { translate(it) }
            is PtNop -> {}
            else -> throw AssemblyError("missing asm translation for $stmt")
        }
    }

    internal fun loadScaledArrayIndexIntoRegister(expr: PtArrayIndexer, register: CpuRegister) {
        val reg = register.toString().lowercase()
        val indexnum = expr.index.asConstInteger()
        if (indexnum != null) {
            val indexValue = if(expr.splitWords)
                indexnum
            else
                indexnum * options.compTarget.memorySize(expr.type)
            out("  ld$reg  #$indexValue")
            return
        }

        if(expr.splitWords) {
            assignExpressionToRegister(expr.index, RegisterOrPair.fromCpuRegister(register), false)
            return
        }

        when (expr.type) {
            in ByteDatatypesWithBoolean -> {
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
            in ByteDatatypesWithBoolean -> {
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

    private fun translate(stmt: PtRepeatLoop) {
        val endLabel = makeLabel("repeatend")
        loopEndLabels.add(endLabel)

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

        loopEndLabels.removeLast()
    }

    private fun repeatWordCount(iterations: Int, stmt: PtRepeatLoop) {
        require(iterations in 257..65535) { "invalid repeat count ${stmt.position}" }
        val repeatLabel = makeLabel("repeat")
        val counterVar = createRepeatCounterVar(DataType.UWORD, isTargetCpu(CpuType.CPU65c02), stmt)
        val loopcount = if(iterations and 0x00ff == 0) iterations else iterations + 0x0100   // so that the loop can simply use a double-dec
        out("""
            ldy  #>$loopcount
            lda  #<$loopcount
            sta  $counterVar
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
        val repeatLabel = makeLabel("repeat")
        out("  cpy  #0")
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

    internal fun getJumpTarget(jump: PtJump): Pair<String, Boolean> {
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

    private fun translate(ret: PtReturn) {
        ret.value?.let { returnvalue ->
            val sub = ret.definingSub()!!
            val returnReg = sub.returnRegister()!!
            when (sub.returntype) {
                in NumericDatatypes, DataType.BOOL -> {
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
        out("  rts")
    }

    private fun translate(asm: PtInlineAssembly) {
        if(asm.isIR)
            throw AssemblyError("%asm containing IR code cannot be translated to 6502 assembly")
        else
            assembly.add(asm.assembly.trimEnd().trimStart('\r', '\n'))
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

    private fun translateBrk() {
        val label = "_prog8_breakpoint_${breakpointLabels.size+1}"
        breakpointLabels.add(label)
        out(label)
        if(options.breakpointCpuInstruction!=null) {
            out("  ${options.breakpointCpuInstruction}")
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

    internal fun tryOptimizedPointerAccessWithA(addressExpr: PtBinaryExpression, write: Boolean): Boolean {
        // optimize pointer,indexregister if possible

        fun evalBytevalueWillClobberA(expr: PtExpression): Boolean {
            val dt = expr.type
            if(dt != DataType.UBYTE && dt != DataType.BYTE)
                return true
            return when(expr) {
                is PtIdentifier -> false
                is PtNumber -> false
                is PtBool -> false
                is PtMemoryByte -> expr.address !is PtIdentifier && expr.address !is PtNumber
                is PtTypeCast -> evalBytevalueWillClobberA(expr.value)
                else -> true
            }
        }

        if(addressExpr.operator=="+") {
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
                if(!tryOptimizedPointerAccessWithA(addrExpr, false)) {
                    assignViaExprEval()
                }
            }
            else -> assignViaExprEval()
        }
    }

    internal fun pushCpuStack(dt: DataType, value: PtExpression) {
        val signed = value.type.oneOf(DataType.BYTE, DataType.WORD)
        if(dt in ByteDatatypesWithBoolean) {
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
        arg !is PtNumber && arg !is PtBool && arg !is PtIdentifier && (arg !is PtMemoryByte || !arg.isSimple())

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

    internal fun makeLabel(postfix: String): String {
        generatedLabelSequenceNumber++
        return "${PtLabel.GeneratedLabelPrefix}${generatedLabelSequenceNumber}_$postfix"
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

    internal fun assignIfExpression(target: AsmAssignTarget, value: PtIfExpression) {
        ifElseAsmgen.assignIfExpression(target, value)
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