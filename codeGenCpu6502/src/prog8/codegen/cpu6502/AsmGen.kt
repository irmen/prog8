package prog8.codegen.cpu6502

import com.github.michaelbull.result.fold
import prog8.code.*
import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.source.ImportFileSystem
import prog8.code.source.SourceCode
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
        val expressionsToFixSubtype = mutableListOf<PtExpression>()
        val variablesToFixSubtype = mutableListOf<IPtVariable>()

        fun prefixNamedNode(node: PtNamedNode) {
            when(node) {
                is PtAsmSub, is PtSub -> node.name = "p8s_${node.name}"
                is PtBlock -> node.name = "p8b_${node.name}"
                is PtLabel -> if(!node.name.startsWith(GENERATED_LABEL_PREFIX)) node.name = "p8l_${node.name}"   // only prefix user defined labels
                is PtConstant -> node.name = "p8c_${node.name}"
                is PtVariable, is PtMemMapped, is PtSubroutineParameter -> node.name = "p8v_${node.name}"
                is PtStructDecl -> node.name = "p8t_${node.name}"
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
                is PtSub -> prefixNamedNode(node)
                is PtFunctionCall -> {
                    val stNode = st.lookup(node.name)!!
                    if(stNode.astNode!!.definingBlock()?.options?.noSymbolPrefixing!=true) {
                        val index = node.parent.children.indexOf(node)
                        functionCallsToPrefix += node.parent to index
                    }
                }
                is PtIdentifier -> {
                    // check if the identifier is part of a pointer dereference (which means you cannot look it up in the symboltable because it's a struct field)
                    val pexpr = node.parent as? PtBinaryExpression
                    if(pexpr?.operator==".") {
                        if(pexpr.left is PtArrayIndexer) {
                            val arrayvar = (pexpr.left as PtArrayIndexer).variable!!
                            if(arrayvar.type.subType!=null) {
                                // don't prefix a struct field name here, we take care of that at asm generation time, which was a lot easier
                                //nodesToPrefix += node.parent to node.parent.children.indexOf(node)
                                return
                            } else
                                throw AssemblyError("can only use deref expression on struct type")
                        } else {
                            TODO("handle other left operand ${pexpr.left }in dereferencing of struct fields ${node.position}")
                        }
                    }
                    // normal (non pointer deref) expression
                    var lookupName = node.name
                    if(node.type.isSplitWordArray && (lookupName.endsWith("_lsb") || lookupName.endsWith("_msb"))) {
                        lookupName = lookupName.dropLast(4)
                    }
                    val stNode = st.lookup(lookupName) ?:
                        throw AssemblyError("unknown identifier $node")
                    if(stNode.astNode!!.definingBlock()?.options?.noSymbolPrefixing!=true) {
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
                is PtStructDecl -> prefixNamedNode(node)        // note: field names are not prefixed here, we take care of that at asm generation time, which was a lot easier
                is PtBuiltinFunctionCall -> {
                    // could be a struct instance creation
                    if(node.name=="prog8_lib_structalloc") {
                        val struct = node.type.subType!!
                        if(struct is StStruct) {
                            // update link to active symboltable node
                            node.type.subType = st.lookup(struct.scopedNameString) as StStruct
                        }
                    }
                }
                else -> { }
            }

            if(node is IPtVariable) {
                if(node.type.subType!=null)
                    variablesToFixSubtype.add(node)
            } else if(node is PtExpression) {
                if(node.type.subType!=null)
                    expressionsToFixSubtype.add(node)
            }

            node.children.forEach { prefixSymbols(it) }
        }

        fun maybePrefixFunctionCallsAndIdentifierReferences(node: PtNode) {
            if(node is PtFunctionCall) {
                // function calls to subroutines defined in a block that does NOT have NoSymbolPrefixing, still have to be prefixed at the call site
                val stNode = st.lookup(node.name)!!
                if(stNode.astNode!!.definingBlock()?.options?.noSymbolPrefixing!=true) {
                    val index = node.parent.children.indexOf(node)
                    functionCallsToPrefix += node.parent to index
                }
            }
            else if (node is PtIdentifier) {
                // identifier references to things defined in a block that does NOT have NoSymbolPrefixing, still have to be prefixed at the referencing point
                var lookupName = node.name
                if(node.type.isSplitWordArray && (lookupName.endsWith("_lsb") || lookupName.endsWith("_msb"))) {
                    lookupName = lookupName.dropLast(4)
                }
                val stNode = st.lookup(lookupName) ?: throw AssemblyError("unknown identifier $node")
                if(stNode.astNode!!.definingBlock()?.options?.noSymbolPrefixing!=true) {
                    val index = node.parent.children.indexOf(node)
                    nodesToPrefix += node.parent to index
                }
            }
            node.children.forEach { maybePrefixFunctionCallsAndIdentifierReferences(it) }
        }

        program.allBlocks().forEach { block ->
            if (!block.options.noSymbolPrefixing) {
                prefixSymbols(block)
            } else {
                maybePrefixFunctionCallsAndIdentifierReferences(block)
            }
        }

        nodesToPrefix.forEach { (parent, index) ->
            when(val node = parent.children[index]) {
                is PtIdentifier -> parent.children[index] = node.prefix(parent, st)
                is PtFunctionCall ->  throw AssemblyError("PtFunctionCall should be processed in their own list, last")
                is PtVariable -> parent.children[index] = node.prefix(parent, st)
                else -> throw AssemblyError("weird node to prefix $node")
            }
        }

        // reversed so inner calls (such as arguments to a function call) get processed before the actual function call itself
        functionCallsToPrefix.reversed().forEach { (parent, index) ->
            val node = parent.children[index]
            if(node is PtFunctionCall) {
                val prefixedName = PtIdentifier(node.name, DataType.UNDEFINED, Position.DUMMY).prefix(parent, st)
                val prefixedNode = node.withNewName(prefixedName.name)
                parent.children[index] = prefixedNode
            } else {
                throw AssemblyError("expected PtFunctionCall")
            }
        }


        val updatedSt = SymbolTableMaker(program, options).make()

        fun findSubtypeReplacement(sub: ISubType): StStruct? {
            if(updatedSt.lookup(sub.scopedNameString)!=null)
                return null
            val old = st.lookup(sub.scopedNameString)
            if(old==null)
                throw AssemblyError("old subtype not found: ${sub.scopedNameString}")

            val prefixed = ArrayDeque<String>()
            var node: StNode = old
            while(node.type!= StNodeType.GLOBAL) {
                val typeChar = typePrefixChar(node.type)
                prefixed.addFirst("p8${typeChar}_${node.name}")
                node=node.parent
            }
            return updatedSt.lookup(prefixed.joinToString(".")) as StStruct
        }

        expressionsToFixSubtype.forEach { node ->
            findSubtypeReplacement(node.type.subType!!)?.let {
                node.type.subType = it
            }
        }
        variablesToFixSubtype.forEach { node ->
            findSubtypeReplacement(node.type.subType!!)?.let {
                node.type.subType = it
            }
        }

        return updatedSt
    }

}

private fun prefixScopedName(name: String, type: Char): String {
    if('.' !in name) {
        if(name.startsWith(GENERATED_LABEL_PREFIX))
            return name
        return "p8${type}_$name"
    }
    val parts = name.split('.')
    val firstPrefixed = "p8b_${parts[0]}"
    val lastPart = parts.last()
    val lastPrefixed = if(lastPart.startsWith(GENERATED_LABEL_PREFIX)) lastPart else  "p8${type}_$lastPart"
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
                        val newAddr = PtAddressOf(elt.type, false, elt.position)
                        newAddr.add(elt.identifier!!.prefix(newAddr, st))
                        if (elt.arrayIndexExpr != null)
                            newAddr.add(elt.arrayIndexExpr!!)
                        newAddr.parent = arrayValue
                        newValue.add(newAddr)
                    }
                }
                is PtBuiltinFunctionCall -> {
                    // could be a struct instance or memory slab "allocation"
                    if (elt.name != "prog8_lib_structalloc" && elt.name != "memory")
                        throw AssemblyError("weird array value element $elt")
                    else {
                        newValue.add(elt)
                    }
                }
                else -> throw AssemblyError("weird array value element $elt")
            }
        }
        val result = PtVariable(name, type, zeropage, align, dirty, newValue, arraySize, position)
        result.parent = parent
        result
    }
    else this
}

//private fun PtFunctionCall.prefix(targetType: Char): PtFunctionCall {
//    val newName = prefixScopedName(name, targetType)
//    return this.withNewName(newName)
//}

private fun PtFunctionCall.withNewName(name: String): PtFunctionCall {
    val call = PtFunctionCall(name, void, type, position)
    call.children.addAll(children)
    call.children.forEach { it.parent = call }
    call.parent = parent
    return call
}

private fun PtIdentifier.prefix(parent: PtNode, st: SymbolTable): PtIdentifier {
    val targetNt: StNodeType
    val target = st.lookup(name)
    if(target?.astNode?.definingBlock()?.options?.noSymbolPrefixing==true)
        return this

    if(target==null) {
        if(name.endsWith("_lsb") || name.endsWith("_msb")) {
            val target2 = st.lookup(name.dropLast(4))
            if(target2?.astNode?.definingBlock()?.options?.noSymbolPrefixing==true)
                return this
            targetNt = target2!!.type
        } else {
            // if no target found, assume that the identifier is a struct field
            targetNt = StNodeType.STATICVAR
        }
    } else {
        targetNt = target.type
    }

    val prefixType = typePrefixChar(targetNt)
    val newName = prefixScopedName(name, prefixType)
    val node = PtIdentifier(newName, type, position)
    node.parent = parent
    return node
}

private fun typePrefixChar(targetNt: StNodeType): Char {
    return when(targetNt) {
        StNodeType.BLOCK -> 'b'
        StNodeType.SUBROUTINE, StNodeType.EXTSUB -> 's'
        StNodeType.LABEL -> 'l'
        StNodeType.STATICVAR, StNodeType.MEMVAR -> 'v'
        StNodeType.CONSTANT -> 'c'
        StNodeType.BUILTINFUNC -> 's'
        StNodeType.MEMORYSLAB -> 'v'
        StNodeType.STRUCT -> 't'
        StNodeType.STRUCTINSTANCE -> 'i'
        else -> '?'
    }
}


class AsmGen6502Internal (
    val program: PtProgram,
    internal val symbolTable: SymbolTable,
    internal val options: CompilationOptions,
    internal val errors: IErrorReporter,
    private var generatedLabelSequenceNumber: Int
) {

    internal val optimizedByteMultiplications = arrayOf(3,5,6,7,9,10,11,12,13,14,15,20,25,40,50,80,100)
    internal val optimizedWordMultiplications = arrayOf(3,5,6,7,9,10,12,15,20,25,40,50,80,100,320,640)
    internal val loopEndLabels = ArrayDeque<String>()
    private val zeropage = options.compTarget.zeropage
    private val allocator = VariableAllocator(symbolTable, options, errors)
    private val assembly = mutableListOf<String>()
    private val breakpointLabels = mutableListOf<String>()
    private val forloopsAsmGen = ForLoopsAsmGen(this, zeropage)
    private val functioncallAsmGen = FunctionCallAsmGen(program, this)
    private val programGen = ProgramAndVarsGen(program, options, errors, symbolTable, functioncallAsmGen, this, allocator, zeropage)
    private val anyExprGen = AnyExprAsmGen(this)
    private val pointerGen = PointerAssignmentsGen(this, allocator)
    private val assignmentAsmGen = AssignmentAsmGen(program, this, pointerGen, anyExprGen, allocator)
    private val builtinFunctionsAsmGen = BuiltinFunctionsAsmGen(program, this, assignmentAsmGen)
    private val ifElseAsmgen = IfElseAsmGen(program, symbolTable, this, pointerGen, assignmentAsmGen, errors)
    private val ifExpressionAsmgen = IfExpressionAsmGen(this, assignmentAsmGen, errors)
    private val augmentableAsmGen = AugmentableAssignmentAsmGen(program, assignmentAsmGen, this, pointerGen, allocator)

    init {
        assignmentAsmGen.augmentableAsmGen = augmentableAsmGen
        pointerGen.augmentableAsmGen = augmentableAsmGen
    }

    fun compileToAssembly(): IAssemblyProgram? {

        assembly.clear()
        loopEndLabels.clear()

        if(!options.quiet)
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
                while(optimizeAssembly(asmLines, options.compTarget, symbolTable)>0) {
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
        if(allocator.zeropageVars.isNotEmpty()) {
            println("ZeroPage:")
            val allvars = allocator.zeropageVars.map { (name, alloc) -> Triple(name, alloc.address, alloc.dt) } + symbolTable.allMemMappedVariables.map { Triple(it.name, it.address, it.dt) }
            allvars
                .filter { it.second in 0u..255u }
                .distinct()
                .sortedWith( compareBy( {it.second}, {it.first} ))
                .forEach {
                    println("  $${it.second.toString(16).padStart(2, '0')}\t${it.third}\t${it.first}")
                }
        }
        if(symbolTable.allVariables.isNotEmpty()) {
            println("Static variables (not in ZeroPage):")
            symbolTable.allVariables
                .filterNot { allocator.isZpVar(it.scopedNameString) }
                .sortedBy { it.scopedNameString }.forEach {
                    println("  ${it.dt}\t${it.scopedNameString}\t")
                }
        }
        if(allocator.globalFloatConsts.isNotEmpty()) {
            println("Floats:")
            allocator.globalFloatConsts.forEach { (value, name) ->
                println("  $name = $value")
            }
        }
        if(symbolTable.allMemMappedVariables.isNotEmpty()) {
            println("Memory mapped:")
            symbolTable.allMemMappedVariables
                .sortedWith( compareBy( {it.address}, {it.scopedNameString} ))
                .forEach { mvar ->
                    println("  $${mvar.address.toString(16).padStart(4, '0')}\t${mvar.dt}\t${mvar.scopedNameString}")
                }
        }
        if(symbolTable.allMemorySlabs.isNotEmpty()) {
            println("Memory slabs:")
            symbolTable.allMemorySlabs.sortedBy { it.name }.forEach { slab ->
                println("  ${slab.name}  ${slab.size}  align ${slab.align}")
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

    internal fun isTargetCpu(cpu: CpuType) = options.compTarget.cpu == cpu

    private var lastSourceLineNumber: Int = -1

    internal fun outputSourceLine(node: PtNode) {
        if(!options.includeSourcelines || node.position===Position.DUMMY || node.position.line==lastSourceLineNumber)
            return

        lastSourceLineNumber = node.position.line
        val srcComment = "\t; source: ${node.position.file}:${node.position.line}"
        if(node.position.line>0) {
            val line = ImportFileSystem.retrieveSourceLine(node.position)
            out("$srcComment   $line", false)
        }
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
        val name = asmVariableName(st.scopedNameString)
        if(scope==null)
            return name
        // remove the whole prefix and just make the variable name locally scoped (64tass scopes it to the proper .proc block)
        val subName = scope.scopedName
        return if (name.length>subName.length && name.startsWith(subName) && name[subName.length] == '.')
            name.drop(subName.length+1)
        else
            name
    }


    internal fun loadByteFromPointerIntoA(pointervar: PtIdentifier): String {
        // returns the source name of the zero page pointervar if it's already in the ZP,
        // otherwise returns "P8ZP_SCRATCH_PTR" which is the intermediary
        val symbol = symbolTable.lookup(pointervar.name)
        when (val target = symbol!!.astNode) {
            is PtLabel -> {
                val sourceName = asmSymbolName(pointervar)
                out("  lda  $sourceName")
                return sourceName
            }
            is PtVariable, is PtMemMapped -> {
                val sourceName = asmVariableName(pointervar)
                if (isTargetCpu(CpuType.CPU65C02)) {
                    return if (allocator.isZpVar((target as PtNamedNode).scopedName)) {
                        // pointervar is already in the zero page, no need to copy
                        out("  lda  ($sourceName)")
                        sourceName
                    } else {
                        out("""
                            lda  $sourceName
                            ldy  $sourceName+1
                            sta  P8ZP_SCRATCH_PTR
                            sty  P8ZP_SCRATCH_PTR+1
                            lda  (P8ZP_SCRATCH_PTR)""")
                        "P8ZP_SCRATCH_PTR"
                    }
                } else {
                    return if (allocator.isZpVar((target as PtNamedNode).scopedName)) {
                        // pointervar is already in the zero page, no need to copy
                        loadAFromZpPointerVar(sourceName)
                        sourceName
                    } else {
                        out("""
                            lda  $sourceName
                            ldy  $sourceName+1
                            sta  P8ZP_SCRATCH_PTR
                            sty  P8ZP_SCRATCH_PTR+1
                            ldy  #0
                            lda  (P8ZP_SCRATCH_PTR),y""")
                        "P8ZP_SCRATCH_PTR"
                    }
                }
            }
            else -> throw AssemblyError("invalid pointervar $target")
        }
    }

    internal fun storeAIntoPointerVar(pointervar: PtIdentifier) {
        val sourceName = asmVariableName(pointervar)
        if (isTargetCpu(CpuType.CPU65C02)) {
            if (allocator.isZpVar(pointervar.name)) {
                // pointervar is already in the zero page, no need to copy
                out("  sta  ($sourceName)")
            } else {
                out("""
                    ldy  $sourceName
                    sty  P8ZP_SCRATCH_PTR
                    ldy  $sourceName+1
                    sty  P8ZP_SCRATCH_PTR+1
                    sta  (P8ZP_SCRATCH_PTR)""")
            }
        } else {
            if (allocator.isZpVar(pointervar.name)) {
                // pointervar is already in the zero page, no need to copy
                out(" ldy  #0 |  sta  ($sourceName),y")
            } else {
                out("""
                    ldy  $sourceName
                    sty  P8ZP_SCRATCH_PTR
                    ldy  $sourceName+1
                    sty  P8ZP_SCRATCH_PTR+1
                    ldy  #0
                    sta  (P8ZP_SCRATCH_PTR),y""")
            }
        }
    }

    internal fun storeAIntoZpPointerVar(zpPointerVar: String, keepY: Boolean) {
        if (isTargetCpu(CpuType.CPU65C02))
            out("  sta  ($zpPointerVar)")
        else {
            if(keepY)
                out("  sty  P8ZP_SCRATCH_REG |  ldy  #0 |  sta  ($zpPointerVar),y |  ldy  P8ZP_SCRATCH_REG")
            else
                out("  ldy  #0 |  sta  ($zpPointerVar),y")
        }
    }

    internal fun loadAFromZpPointerVar(zpPointerVar: String, keepY: Boolean=false) {
        if (isTargetCpu(CpuType.CPU65C02))
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
                if (isTargetCpu(CpuType.CPU65C02)) out("  phx")
                else {
                    if(keepA)
                        out("  sta  P8ZP_SCRATCH_REG |  txa |  pha  |  lda  P8ZP_SCRATCH_REG")
                    else
                        out("  txa |  pha")
                }
            }
            CpuRegister.Y -> {
                if (isTargetCpu(CpuType.CPU65C02)) out("  phy")
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
                if (isTargetCpu(CpuType.CPU65C02)) out("  plx")
                else {
                    if(keepA)
                        out("  sta  P8ZP_SCRATCH_REG |  pla |  tax |  lda  P8ZP_SCRATCH_REG")
                    else
                        out("  pla |  tax")
                }
            }
            CpuRegister.Y -> {
                if (isTargetCpu(CpuType.CPU65C02)) out("  ply")
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
                val target = getJumpTarget(stmt)
                require(!target.needsExpressionEvaluation)
                jmp(target.asmLabel, target.indirect, target.indexedX)
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
            is PtJmpTable -> translate(stmt)
            is PtNop, is PtStructDecl, is PtSubSignature -> {}
            else -> throw AssemblyError("missing asm translation for $stmt")
        }
    }

    internal fun loadScaledArrayIndexIntoRegister(expr: PtArrayIndexer, register: CpuRegister) {
        val reg = register.toString().lowercase()
        val indexnum = expr.index.asConstInteger()
        if (indexnum != null) {
            if(indexnum > 255)
                throw AssemblyError("array index $indexnum is larger than a byte ${expr.position}")
            val indexValue = if(expr.splitWords)
                indexnum
            else
                indexnum * options.compTarget.memorySize(expr.type, null)
            out("  ld$reg  #$indexValue")
            return
        }

        if(!expr.index.type.isByte)
            throw AssemblyError("array index $indexnum is larger than a byte ${expr.position}")

        if(expr.splitWords) {
            assignExpressionToRegister(expr.index, RegisterOrPair.fromCpuRegister(register))
            return
        }

        when  {
            expr.type.isByteOrBool -> {
                assignExpressionToRegister(expr.index, RegisterOrPair.fromCpuRegister(register))
            }
            expr.type.isWord -> {
                assignExpressionToRegister(expr.index, RegisterOrPair.A)
                out("  asl  a")
                when (register) {
                    CpuRegister.A -> {}
                    CpuRegister.X -> out(" tax")
                    CpuRegister.Y -> out(" tay")
                }
            }
            expr.type.isFloat -> {
                if(options.compTarget.FLOAT_MEM_SIZE != 5)
                    TODO("support float size other than 5 ${expr.position}")
                assignExpressionToRegister(expr.index, RegisterOrPair.A)
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

    internal fun translateBuiltinFunctionCallExpression(bfc: PtBuiltinFunctionCall, resultRegister: RegisterOrPair?): BaseDataType? =
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
            RegisterOrPair.Y -> assignmentAsmGen.assignRegisterByte(target, reg.asCpuRegister(), target.datatype.isSigned, true)
            RegisterOrPair.AX,
            RegisterOrPair.AY,
            RegisterOrPair.XY -> assignmentAsmGen.assignRegisterpairWord(target, reg)
            in Cx16VirtualRegisters -> assignmentAsmGen.assignVirtualRegister(target, reg)
            RegisterOrPair.FAC1 -> assignmentAsmGen.assignFAC1float(target)
            RegisterOrPair.FAC2 -> assignmentAsmGen.assignFAC2float(target)
            else -> throw AssemblyError("invalid register")
        }
    }

    internal fun assignExpressionTo(value: PtExpression, target: AsmAssignTarget) {

        // this is basically the fallback assignment routine, it is RARELY called

        when {
            target.datatype.isByteOrBool -> {
                if (value.asConstInteger()==0) {
                    when(target.kind) {
                        TargetStorageKind.VARIABLE -> {
                            if (isTargetCpu(CpuType.CPU6502))
                                out("  lda  #0 |  sta  ${target.asmVarname}")
                            else
                                out("  stz  ${target.asmVarname}")
                        }
                        TargetStorageKind.MEMORY -> {
                            val address = target.memory!!.address.asConstInteger()
                            if(address!=null) {
                                if (isTargetCpu(CpuType.CPU6502))
                                    out("  lda  #0 |  sta  ${address.toHex()}")
                                else
                                    out("  stz  ${address.toHex()}")
                                return
                            }
                        }
                        TargetStorageKind.REGISTER -> {
                            val zero = PtNumber(BaseDataType.UBYTE, 0.0, value.position)
                            zero.parent = value
                            assignExpressionToRegister(zero, target.register!!)
                            return
                        }
                        TargetStorageKind.POINTER -> {
                            TODO("assign to pointer ${target.position}")
                            return
                        }
                        else -> { }
                    }
                }

                assignExpressionToRegister(value, RegisterOrPair.A)
                assignmentAsmGen.assignRegisterByte(target, CpuRegister.A, target.datatype.isSigned, false)
            }
            target.datatype.isPointer -> TODO("assign expression to pointer ${target.position}")
            target.datatype.isWord || target.datatype.isPassByRef -> {
                assignExpressionToRegister(value, RegisterOrPair.AY)
                translateNormalAssignment(
                    AsmAssignment(
                        AsmAssignSource(SourceStorageKind.REGISTER, program, this, target.datatype, register=RegisterOrPair.AY),
                            listOf(target), program.memsizer, value.position
                    ), value.definingISub()
                )
            }
            target.datatype.isLong -> {
                if(value is PtNumber) {
                    val hex = value.asConstInteger()!!.toString(16).padStart(8, '0')
                    when(target.kind) {
                        TargetStorageKind.VARIABLE -> {
                            out("""
                                lda  #${hex.substring(6,8)}
                                sta  ${target.asmVarname}
                                lda  #${hex.substring(4, 6)}
                                sta  ${target.asmVarname}+1
                                lda  #${hex.substring(2, 4)}
                                sta  ${target.asmVarname}+2
                                lda  #${hex.take(2)}
                                sta  ${target.asmVarname}+3""")
                        }
                        TargetStorageKind.ARRAY -> TODO("assign long to array  ${target.position}")
                        TargetStorageKind.MEMORY -> throw AssemblyError("memory is bytes not long ${target.position}")
                        TargetStorageKind.REGISTER -> TODO("32 bits register assign? (we have no 32 bits registers right now) ${target.position}")
                        TargetStorageKind.POINTER -> throw AssemblyError("can't assign long to pointer, pointers are 16 bits ${target.position}")
                        TargetStorageKind.VOID -> { /* do nothing */ }
                    }
                } else if(value is PtIdentifier && value.type.isLong) {
                    when (target.kind) {
                        TargetStorageKind.VARIABLE -> {
                            val valuesym = asmSymbolName(value)
                            out(
                                """
                                lda  $valuesym
                                sta  ${target.asmVarname}
                                lda  $valuesym+1
                                sta  ${target.asmVarname}+1
                                lda  $valuesym+2
                                sta  ${target.asmVarname}+2
                                lda  $valuesym+3
                                sta  ${target.asmVarname}+3"""
                            )
                        }

                        TargetStorageKind.ARRAY -> TODO("assign long to array  ${target.position}")
                        TargetStorageKind.MEMORY -> throw AssemblyError("memory is bytes not long ${target.position}")
                        TargetStorageKind.REGISTER -> TODO("32 bits register assign? (we have no 32 bits registers right now) ${target.position}")
                        TargetStorageKind.POINTER -> throw AssemblyError("can't assign long to pointer, pointers are 16 bits ${target.position}")
                        TargetStorageKind.VOID -> { /* do nothing */ }
                    }
                } else if(value is PtTypeCast && value.type.isLong) {
                    if(value.value is PtIdentifier) {
                        val valuesym = asmSymbolName((value.value as PtIdentifier).name)
                        if(value.value.type.isByte) {
                            when (target.kind) {
                                TargetStorageKind.VARIABLE -> {
                                    out("  lda  $valuesym |  sta  ${target.asmVarname}")
                                    signExtendLongVariable(target.asmVarname, value.value.type.base)
                                }
                                TargetStorageKind.ARRAY -> TODO("assign long to array  ${target.position}")
                                TargetStorageKind.MEMORY -> throw AssemblyError("memory is bytes not long ${target.position}")
                                TargetStorageKind.REGISTER -> TODO("32 bits register assign? (we have no 32 bits registers right now) ${target.position}")
                                TargetStorageKind.POINTER -> throw AssemblyError("can't assign long to pointer, pointers are 16 bits ${target.position}")
                                TargetStorageKind.VOID -> { /* do nothing */ }
                            }
                        } else if(value.value.type.isWord) {
                            when (target.kind) {
                                TargetStorageKind.VARIABLE -> {
                                    out("""
                                        lda  $valuesym
                                        sta  ${target.asmVarname}
                                        lda  $valuesym+1
                                        sta  ${target.asmVarname}+1""")
                                    signExtendLongVariable(target.asmVarname, value.value.type.base)
                                }
                                TargetStorageKind.ARRAY -> TODO("assign long to array  ${target.position}")
                                TargetStorageKind.MEMORY -> throw AssemblyError("memory is bytes not long ${target.position}")
                                TargetStorageKind.REGISTER -> {
                                    require(target.register in combinedLongRegisters)
                                    val startreg = target.register.toString().take(2).lowercase()
                                    out("""
                                        lda  $valuesym
                                        sta  cx16.$startreg
                                        lda  $valuesym+1
                                        sta  cx16.$startreg+1""")
                                    signExtendLongVariable("cx16.$startreg", value.value.type.base)
                                }
                                TargetStorageKind.POINTER -> throw AssemblyError("can't assign long to pointer, pointers are 16 bits ${target.position}")
                                TargetStorageKind.VOID -> { /* do nothing */ }
                            }
                        } else throw AssemblyError("weird casted type")
                    } else {
                        TODO("assign typecasted expression $value to a long target  ${target.position}  - use simple expressions and temporary variables for now")
                    }
                } else {
                    TODO("assign long expression $value to a target  ${target.position} - use simple expressions and temporary variables for now")
                }
            }
            target.datatype.isFloat -> {
                assignExpressionToRegister(value, RegisterOrPair.FAC1)
                assignRegister(RegisterOrPair.FAC1, target)
            }
            else -> throw AssemblyError("weird dt ${target.datatype}")
        }
    }

    internal fun branchInstruction(condition: BranchCondition, complement: Boolean) =
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
                    iterations !in 0..65536 -> throw AssemblyError("invalid number of iterations")
                    iterations <= 256 -> repeatByteCount(iterations, stmt)
                    else -> repeatWordCount(iterations, stmt)
                }
            }
            is PtIdentifier -> {
                val symbol = symbolTable.lookup((stmt.count as PtIdentifier).name)
                val vardecl = symbol!!.astNode as IPtVariable
                val name = asmVariableName(stmt.count as PtIdentifier)
                when {
                    vardecl.type.isByte -> {
                        assignVariableToRegister(name, RegisterOrPair.Y, stmt.definingISub(), stmt.count.position)
                        repeatCountInY(stmt, endLabel)
                    }
                    vardecl.type.isWord -> {
                        assignVariableToRegister(name, RegisterOrPair.AY, stmt.definingISub(), stmt.count.position)
                        repeatWordCountInAY(endLabel, stmt)
                    }
                    else -> throw AssemblyError("invalid loop variable datatype ${vardecl.type}")
                }
            }
            else -> {
                when {
                    stmt.count.type.isByte -> {
                        assignExpressionToRegister(stmt.count, RegisterOrPair.Y)
                        repeatCountInY(stmt, endLabel)
                    }
                    stmt.count.type.isWord -> {
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
        require(iterations in 257..65536) { "invalid repeat count ${stmt.position}" }
        val repeatLabel = makeLabel("repeat")
        val counterVar = createTempVarReused(BaseDataType.UWORD, true, stmt)
        val loopcount = if(iterations==65536) 0 else if(iterations and 0x00ff == 0) iterations else iterations + 0x0100   // so that the loop can simply use a double-dec
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
        val counterVar = createTempVarReused(BaseDataType.UWORD, true, stmt)
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
        if(isTargetCpu(CpuType.CPU65C02)) {
            val counterVar = createTempVarReused(BaseDataType.UBYTE, true, stmt)
            out("  lda  #${count and 255} |  sta  $counterVar")
            out(repeatLabel)
            translate(stmt.statements)
            out("  dec  $counterVar |  bne  $repeatLabel")
        } else {
            val counterVar = createTempVarReused(BaseDataType.UBYTE, false, stmt)
            out("  lda  #${count and 255} |  sta  $counterVar")
            out(repeatLabel)
            translate(stmt.statements)
            out("  dec  $counterVar |  bne  $repeatLabel")
        }
    }

    private fun repeatCountInY(stmt: PtRepeatLoop, endLabel: String) {
        val repeatLabel = makeLabel("repeat")
        out("  cpy  #0")
        if(isTargetCpu(CpuType.CPU65C02)) {
            val counterVar = createTempVarReused(BaseDataType.UBYTE, true, stmt)
            out("  beq  $endLabel |  sty  $counterVar")
            out(repeatLabel)
            translate(stmt.statements)
            out("  dec  $counterVar |  bne  $repeatLabel")
        } else {
            val counterVar = createTempVarReused(BaseDataType.UBYTE, false, stmt)
            out("  beq  $endLabel |  sty  $counterVar")
            out(repeatLabel)
            translate(stmt.statements)
            out("  dec  $counterVar |  bne  $repeatLabel")
        }
        out(endLabel)
    }

    private fun translate(stmt: PtWhen) {
        val endLabel = makeLabel("when_end")
        val choiceBlocks = mutableListOf<Pair<String, PtWhenChoice>>()
        val conditionDt = stmt.value.type
        if(conditionDt.isByte)
            assignExpressionToRegister(stmt.value, RegisterOrPair.A)
        else
            assignExpressionToRegister(stmt.value, RegisterOrPair.AY)

        for(choiceNode in stmt.choices.children) {
            val choice = choiceNode as PtWhenChoice
            var choiceLabel = makeLabel("choice")
            if(choice.isElse) {
                require(choice.parent.children.last() === choice)
                translate(choice.statements)
                // is always the last node so can fall through
            } else {
                if(choice.statements.children.isEmpty()) {
                    // no statements for this choice value, jump to the end immediately
                    choiceLabel = endLabel
                } else {
                    val onlyJumpLabel = ((choice.statements.children.singleOrNull() as? PtJump)?.target as? PtIdentifier)?.name
                    if(onlyJumpLabel==null) {
                        choiceBlocks.add(choiceLabel to choice)
                    } else {
                        choiceLabel = onlyJumpLabel
                    }
                }
                for (cv in choice.values.children) {
                    val value = (cv as PtNumber).number.toInt()
                    if(conditionDt.isByte) {
                        out("  cmp  #${value.toHex()} |  beq  $choiceLabel")
                    } else {
                        out("""
                        cmp  #<${value.toHex()}
                        bne  +
                        cpy  #>${value.toHex()}
                        beq  $choiceLabel
+""")
                    }
                }
            }
        }

        if(choiceBlocks.isNotEmpty())
            jmp(endLabel)

        for(choiceBlock in choiceBlocks.withIndex()) {
            out(choiceBlock.value.first)
            translate(choiceBlock.value.second.statements)
            if (choiceBlock.index < choiceBlocks.size - 1 && !choiceBlock.value.second.isOnlyGotoOrReturn())
                jmp(endLabel)
        }
        out(endLabel)
    }

    private fun translate(stmt: PtLabel) {
        out(stmt.name)
    }

    private fun translate(stmt: PtJmpTable) {
        out("  ; jumptable")
        for(name in stmt.children) {
            out("  jmp  ${asmSymbolName((name as PtIdentifier).name)}")
        }
        out("  ; end jumptable")
    }

    private fun translate(stmt: PtConditionalBranch) {
        if(stmt.trueScope.children.isEmpty() && stmt.falseScope.children.isNotEmpty())
            throw AssemblyError("only else part contains code, shoud have been switched already")

        val jump = stmt.trueScope.children.firstOrNull() as? PtJump
        if(jump!=null) {
            // branch with only a jump (goto)
            val instruction = branchInstruction(stmt.condition, false)
            val target = getJumpTarget(jump)
            require(!target.needsExpressionEvaluation)
            if(target.indirect) {
                require(!target.indexedX)
                val complementedInstruction = branchInstruction(stmt.condition, true)
                out("""
                    $complementedInstruction +
                    jmp  (${target.asmLabel})
+""")
            }
            else {
                out("  $instruction  ${target.asmLabel}")
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

    class JumpTarget(val asmLabel: String, val indirect: Boolean, val indexedX: Boolean, val needsExpressionEvaluation: Boolean)

    internal fun getJumpTarget(jump: PtJump, evaluateAddressExpression: Boolean = true): JumpTarget {
        val ident = jump.target as? PtIdentifier
        if(ident!=null) {
            // can be a label, or a pointer variable
            val symbol = symbolTable.lookup(ident.name)
            return if(symbol?.type in arrayOf(StNodeType.STATICVAR, StNodeType.MEMVAR, StNodeType.CONSTANT))
                JumpTarget(asmSymbolName(ident), true, false,false)        // indirect jump if the jump symbol is a variable
            else
                JumpTarget(asmSymbolName(ident), false, false,false)
        }
        val addr = jump.target.asConstInteger()
        if(addr!=null)
            return JumpTarget(addr.toHex(), false, false,false)
        else {
            if(evaluateAddressExpression) {
                val arrayIdx = jump.target as? PtArrayIndexer
                if (arrayIdx!=null) {
                    val arrayVariable = arrayIdx.variable ?: TODO("support for ptr indexing ${arrayIdx.position}")
                    if (isTargetCpu(CpuType.CPU65C02)) {
                        if (!arrayIdx.splitWords) {
                            // if the jump target is an address in a non-split array (like a jump table of only pointers),
                            // on the 65c02, more optimal assembly can be generated using JMP (address,X)
                            assignExpressionToRegister(arrayIdx.index, RegisterOrPair.A)
                            out("  asl  a |  tax")
                            return JumpTarget(asmSymbolName(arrayVariable), true, true, false)
                        } else {
                            // print a message when more optimal code is possible for 65C02 cpu
                            val variable = symbolTable.lookup(arrayVariable.name)!!
                            if(variable is StStaticVariable && variable.length!!<=128u)
                                errors.info("the jump address array is @split, but @nosplit would create more efficient code here", jump.position)
                        }
                    } else {
                        // print a message when more optimal code is possible for 6502 cpu
                        if(!arrayIdx.splitWords)
                            errors.info("the jump address array is @nosplit, but @split would create more efficient code here", jump.position)
                    }
                }
                // we can do the address evaluation right now and just use a temporary pointer variable
                assignExpressionToVariable(jump.target, "P8ZP_SCRATCH_W1", DataType.UWORD)
                return JumpTarget("P8ZP_SCRATCH_W1", true, false,false)
            } else {
                return JumpTarget("PROG8_JUMP_TARGET_IS_UNEVALUATED_ADDRESS_EXPRESSION", true, false,true)
            }
        }
    }

    private fun translate(ret: PtReturn) {
        val returnvalue = ret.children.singleOrNull() as? PtExpression
        val sub = ret.definingSub()!!
        val returnRegs = sub.returnsWhatWhere()

        if(returnvalue!=null) {
            val returnDt = sub.signature.returns.single()
            if (returnDt.isNumericOrBool || returnDt.isPointer) {
                assignExpressionToRegister(returnvalue, returnRegs.single().first.registerOrPair!!)
            }
            else {
                // all else take its address and assign that also to AY register pair
                val addrOfDt = returnvalue.type.typeForAddressOf(false)
                val addrofValue = PtAddressOf(addrOfDt, false, returnvalue.position)
                addrofValue.add(returnvalue)
                addrofValue.parent = ret.parent
                assignmentAsmGen.assignExpressionToRegister(addrofValue, returnRegs.single().first.registerOrPair!!, false)
            }
        }
        else if(ret.children.size>1) {
            // note: multi-value returns are passed throug A or AY (for the first value) then cx16.R15 down to R0
            // (this allows unencumbered use of many Rx registers if you don't return that many values)
            // to avoid register clobbering, assign the first return value last in row.
            val assigns = ret.children.zip(returnRegs).map { it.first to it.second }
            assigns.drop(1).forEach {
                val tgt = AsmAssignTarget(TargetStorageKind.REGISTER, this, it.second.second, null, it.first.position, register = it.second.first.registerOrPair!!)
                assignExpressionTo(it.first as PtExpression, tgt)
            }
            assigns.first().also {
                assignExpressionToRegister(it.first as PtExpression, it.second.first.registerOrPair!!)
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
            .sanitize()
            .relativize(includedPath.sanitize())
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

    internal fun signExtendAYlsb(valueDt: BaseDataType) {
        // sign extend signed byte in A to full word in AY
        when(valueDt) {
            BaseDataType.UBYTE -> out("  ldy  #0")
            BaseDataType.BYTE -> out("""
                ldy  #0
                cmp  #$80
                bcc  +
                dey
+""")
            else -> throw AssemblyError("need byte type")
        }
    }

    internal fun signExtendAXlsb(valueDt: BaseDataType) {
        // sign extend signed byte in A to full word in AX
        when(valueDt) {
            BaseDataType.UBYTE -> out("  ldx  #0")
            BaseDataType.BYTE -> out("""
                ldx  #0
                cmp  #$80
                bcc  +
                dex
+""")
            else -> throw AssemblyError("need byte type")
        }
    }

    internal fun signExtendVariableLsb(asmvar: String, valueDt: BaseDataType) {
        // sign extend signed byte in a var to a full word in that variable
        when(valueDt) {
            BaseDataType.UBYTE -> {
                if(isTargetCpu(CpuType.CPU65C02))
                    out("  stz  $asmvar+1")
                else
                    out("  lda  #0 |  sta  $asmvar+1")
            }
            BaseDataType.BYTE -> {
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

    internal fun signExtendLongVariable(asmvar: String, valueDt: BaseDataType) {
        // sign extend signed word in a var to a full long in that variable
        when(valueDt) {
            BaseDataType.UBYTE -> {
                if(isTargetCpu(CpuType.CPU65C02)) {
                    out("""
                        stz  $asmvar+1
                        stz  $asmvar+2
                        stz  $asmvar+3""")
                }
                else {
                    out("""
                        lda  #0
                        sta  $asmvar+1
                        sta  $asmvar+2
                        sta  $asmvar+3""")
                }
            }
            BaseDataType.BYTE -> {
                out("""
                    lda  $asmvar
                    ora  #$7f
                    bmi  +
                    lda  #0
+                   sta  $asmvar+1
                    sta  $asmvar+2
                    sta  $asmvar+3""")
            }
            BaseDataType.WORD -> {
                out("""
                    lda  $asmvar+1
                    ora  #$7f
                    bmi  +
                    lda  #0
+                   sta  $asmvar+2
                    sta  $asmvar+3""")
            }
            else -> throw AssemblyError("need byte or word type")
        }
    }

    internal fun isZpVar(variable: PtIdentifier): Boolean = allocator.isZpVar(variable.name)

    internal fun jmp(asmLabel: String, indirect: Boolean=false, indexedX: Boolean=false) {
        if(indirect) {
            if(indexedX)
                out("  jmp  ($asmLabel,x)")
            else
                out("  jmp  ($asmLabel)")
        } else {
            require(!indexedX) { "indexedX only allowed for indirect jumps" }
            if (isTargetCpu(CpuType.CPU65C02))
                out("  bra  $asmLabel")     // note: 64tass will convert this automatically to a jmp if the relative distance is too large
            else
                out("  jmp  $asmLabel")
        }
    }

    internal fun pointerViaIndexRegisterPossible(pointerOffsetExpr: PtExpression, allowNegativeIndex: Boolean=false): Pair<PtExpression, PtExpression>? {
        if (pointerOffsetExpr !is PtBinaryExpression) return null
        val operator = pointerOffsetExpr.operator
        val left = pointerOffsetExpr.left
        val right = pointerOffsetExpr.right
        if (operator != "+" && (operator != "-" || !allowNegativeIndex))
            return null
        val leftDt = left.type
        val rightDt = right.type
        if((leftDt.isUnsignedWord || leftDt.isPointer) && rightDt.isUnsignedByte)
            return Pair(left, right)
        if(leftDt.isUnsignedByte && rightDt.isUnsignedWord)
            return Pair(right, left)
        if((leftDt.isUnsignedWord || leftDt.isPointer) && rightDt.isUnsignedWord) {
            // could be that the index was a constant numeric byte but converted to word, check that
            val constIdx = right as? PtNumber
            if(constIdx!=null && constIdx.number.toInt()>=0 && constIdx.number.toInt()<=255) {
                val num = PtNumber(BaseDataType.UBYTE, constIdx.number, constIdx.position)
                num.parent = right.parent
                return Pair(left, num)
            }
            // could be that the index was typecasted into uword, check that
            val rightTc = right as? PtTypeCast
            if(rightTc!=null && rightTc.value.type.isUnsignedByte)
                return Pair(left, rightTc.value)
            val leftTc = left as? PtTypeCast
            if(leftTc!=null && leftTc.value.type.isUnsignedByte)
                return Pair(right, leftTc.value)
        }
        return null
    }

    internal fun tryOptimizedPointerAccessWithA(addressExpr: PtBinaryExpression, write: Boolean): Boolean {
        // optimize pointer,indexregister if possible

        fun evalBytevalueWillClobberA(expr: PtExpression): Boolean {
            if(!expr.type.isByte)
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
            val ptrAndIndex = pointerViaIndexRegisterPossible(addressExpr) ?: return false
            if(write) {

                // WRITING TO pointer + offset

                val addrOf = ptrAndIndex.first as? PtAddressOf
                val constOffset = (ptrAndIndex.second as? PtNumber)?.number?.toInt()
                if(addrOf!=null && constOffset!=null) {
                    if(addrOf.isFromArrayElement) {
                        TODO("address-of array element $addrOf")
                    } else if(addrOf.dereference!=null) {
                        throw AssemblyError("write &dereference, makes no sense at ${addrOf.position}")
                    } else {
                        out("  sta  ${asmSymbolName(addrOf.identifier!!)}+${constOffset}")
                        return true
                    }
                }

                val pointervar = ptrAndIndex.first as? PtIdentifier
                if(pointervar!=null && isZpVar(pointervar)) {
                    val saveA = evalBytevalueWillClobberA(ptrAndIndex.second)
                    if(saveA) out("  pha")
                    assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                    if(saveA) out("  pla")
                    out("  sta  (${asmSymbolName(pointervar)}),y")
                } else {
                    // copy the pointer var to zp first
                    val saveA = evalBytevalueWillClobberA(ptrAndIndex.first) || evalBytevalueWillClobberA(ptrAndIndex.second)
                    if(saveA) out("  pha")
                    if(ptrAndIndex.second.isSimple()) {
                        assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD)
                        assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                        if(saveA) out("  pla")
                        out("  sta  (P8ZP_SCRATCH_W2),y")
                    } else {
                        pushCpuStack(BaseDataType.UBYTE,  ptrAndIndex.second)
                        assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD)
                        restoreRegisterStack(CpuRegister.Y, true)
                        if(saveA) out("  pla")
                        out("  sta  (P8ZP_SCRATCH_W2),y")
                    }
                }
                return true
            }

            // READING FROM pointer + offset

            val addrOf = ptrAndIndex.first as? PtAddressOf
            val constOffset = (ptrAndIndex.second as? PtNumber)?.number?.toInt()
            if(addrOf!=null && constOffset!=null) {
                if(addrOf.isFromArrayElement) {
                    TODO("address-of array element $addrOf")
                } else if(addrOf.dereference!=null) {
                    TODO("read &dereference")
                } else {
                    out("  lda  ${asmSymbolName(addrOf.identifier!!)}+${constOffset}")
                    return true
                }
            }

            val pointervar = ptrAndIndex.first as? PtIdentifier
            val targetVariable = if(pointervar==null) null else symbolTable.lookup(pointervar.name)!!.astNode
            when(targetVariable) {
                is PtLabel -> {
                    assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                    out("  lda  ${asmSymbolName(pointervar!!)},y")
                    return true
                }
                is IPtVariable, null -> {
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
                            pushCpuStack(BaseDataType.UBYTE, ptrAndIndex.second)
                            assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD)
                            restoreRegisterStack(CpuRegister.Y, false)
                            out("  lda  (P8ZP_SCRATCH_W2),y")
                        }
                    }
                    return true
                }
                else -> throw AssemblyError("invalid pointervar $pointervar")
            }
        }

        else if(addressExpr.operator=="-") {
            val ptrAndIndex = pointerViaIndexRegisterPossible(addressExpr, true) ?: return false
            if(write) {

                // WRITING TO pointer - offset

                val addrOf = ptrAndIndex.first as? PtAddressOf
                val constOffset = (ptrAndIndex.second as? PtNumber)?.number?.toInt()
                if(addrOf!=null && constOffset!=null) {
                    if(addrOf.isFromArrayElement) {
                        TODO("address-of array element $addrOf")
                    } else if(addrOf.dereference!=null) {
                        throw AssemblyError("write &dereference, makes no sense at ${addrOf.position}")
                    } else {
                        out("  sta  ${asmSymbolName(addrOf.identifier!!)}-${constOffset}")
                        return true
                    }
                }

                if(constOffset!=null) {
                    val pointervar = ptrAndIndex.first as? PtIdentifier
                    if(pointervar!=null && isZpVar(pointervar)) {
                        val varname = asmSymbolName(pointervar)
                        out("  ldy  #${256-constOffset}     ; negative offset $constOffset")
                        out("  dec  $varname+1 |  sta  ($varname),y |  inc  $varname+1")        // temporarily make MSB 1 less to be able to use the negative Y offset
                        return true
                    } else {
                        // copy the pointer var to zp first
                        out("  pha")
                        assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD)
                        out("  ldy  #${256-constOffset}     ; negative offset $constOffset")
                        out("  dec  P8ZP_SCRATCH_W2+1 |  pla |  sta  (P8ZP_SCRATCH_W2),y")        // temporarily make MSB 1 less to be able to use the negative Y offset
                        return true
                    }
                }
            } else {

                // READING FROM pointer - offset

                val addrOf = ptrAndIndex.first as? PtAddressOf
                val constOffset = (ptrAndIndex.second as? PtNumber)?.number?.toInt()
                if(addrOf!=null && constOffset!=null) {
                    if(addrOf.isFromArrayElement) {
                        TODO("address-of array element $addrOf")
                    } else if(addrOf.dereference!=null) {
                        TODO("read &dereference")
                    } else {
                        out("  lda  ${asmSymbolName(addrOf.identifier!!)}-${constOffset}")
                        return true
                    }
                }

                if(constOffset!=null) {
                    val pointervar = ptrAndIndex.first as? PtIdentifier
                    if(pointervar!=null && isZpVar(pointervar)) {
                        val varname = asmSymbolName(pointervar)
                        out("  ldy  #${256-constOffset}     ; negative offset $constOffset")
                        out("  dec  $varname+1 |  lda  ($varname),y |  inc  $varname+1")        // temporarily make MSB 1 less to be able to use the negative Y offset
                        return true
                    } else {
                        // copy the pointer var to zp first
                        assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD)
                        out("  ldy  #${256-constOffset}     ; negative offset $constOffset")
                        out("  dec  P8ZP_SCRATCH_W2+1 |  lda  (P8ZP_SCRATCH_W2),y")        // temporarily make MSB 1 less to be able to use the negative Y offset
                        return true
                    }
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
        val params = node!!.definingSub()?.signature?.children
        if(params!=null) {
            for(param in params) {
                param as PtSubroutineParameter
                if(param.scopedName==name)
                    return param
            }
        }
        return null
    }

    internal fun assignByteOperandsToAAndVar(left: PtExpression, right: PtExpression, rightVarName: String) {
        if(left.isSimple()) {
            assignExpressionToVariable(right, rightVarName, DataType.UBYTE)
            assignExpressionToRegister(left, RegisterOrPair.A)
        } else {
            pushCpuStack(BaseDataType.UBYTE, left)
            assignExpressionToVariable(right, rightVarName, DataType.UBYTE)
            out("  pla")
        }
    }

    internal fun assignWordOperandsToAYAndVar(left: PtExpression, right: PtExpression, rightVarname: String) {
        if(left.isSimple()) {
            assignExpressionToVariable(right, rightVarname, DataType.UWORD)
            assignExpressionToRegister(left, RegisterOrPair.AY)
        }  else {
            pushCpuStack(BaseDataType.UWORD, left)
            assignExpressionToVariable(right, rightVarname, DataType.UWORD)
            restoreRegisterStack(CpuRegister.Y, false)
            restoreRegisterStack(CpuRegister.A, false)
        }
    }

    internal fun translateDirectMemReadExpressionToRegA(expr: PtMemoryByte) {

        fun assignViaExprEval() {
            assignExpressionToVariable(expr.address, "P8ZP_SCRATCH_W2", DataType.UWORD)
            if (isTargetCpu(CpuType.CPU65C02)) {
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

    internal fun pushCpuStack(dt: BaseDataType, value: PtExpression) {
        val signed = value.type.isSigned
        if(dt.isByteOrBool) {
            assignExpressionToRegister(value, RegisterOrPair.A, signed)
            out("  pha")
        } else if(dt.isWord) {
            assignExpressionToRegister(value, RegisterOrPair.AY, signed)
            if (isTargetCpu(CpuType.CPU65C02))
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
        return "$GENERATED_LABEL_PREFIX${generatedLabelSequenceNumber}_$postfix"
    }

    internal fun createTempVarReused(dt: BaseDataType, preferZeropage: Boolean, stmt: PtNode): String {
        val scope = stmt.definingISub()!!
        val asmInfo = subroutineExtra(scope)
        var parent = stmt.parent
        while(parent !is PtProgram) {
            if(parent is PtRepeatLoop || parent is PtForLoop)
                break
            parent = parent.parent
        }
        val isNested = parent is PtRepeatLoop || parent is PtForLoop

        if(!isNested) {
            // we can re-use a counter var from the subroutine if it already has one for that datatype
            val existingVar = asmInfo.extraVars.firstOrNull { it.first==dt && it.second.endsWith("tempv") }
            if(existingVar!=null) {
                if(!preferZeropage || existingVar.third!=null) {
                    // println("reuse temp counter var: $dt ${existingVar.second}  @${stmt.position}")
                    return existingVar.second
                }
            }
        }

        val counterVar = makeLabel("tempv")
        // println("new temp counter var: $dt $counterVar  @${stmt.position}")
        when {
            dt.isIntegerOrBool -> {
                if(preferZeropage) {
                    val result = zeropage.allocate(counterVar, DataType.forDt(dt), null, stmt.position, errors)
                    result.fold(
                        success = { (address, _, _) -> asmInfo.extraVars.add(Triple(dt, counterVar, address)) },
                        failure = { asmInfo.extraVars.add(Triple(dt, counterVar, null)) }  // allocate normally
                    )
                } else {
                    asmInfo.extraVars.add(Triple(dt, counterVar, null))  // allocate normally
                }
                return counterVar
            }
            dt == BaseDataType.FLOAT -> {
                asmInfo.extraVars.add(Triple(dt, counterVar, null)) // allocate normally, floats never on zeropage
                return counterVar
            }
            else -> throw AssemblyError("invalid dt")
        }
    }

    internal fun assignConstFloatToPointerAY(number: PtNumber) {
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
        ifExpressionAsmgen.assignIfExpression(target, value)
    }

    internal fun assignBranchCondExpression(target: AsmAssignTarget, value: PtBranchCondExpression) {
        ifExpressionAsmgen.assignBranchCondExpression(target, value)
    }

    internal fun cmpAwithByteValue(value: PtExpression, useSbc: Boolean) {
        val compare = if(useSbc) "sec |  sbc" else "cmp"
        fun cmpViaScratch() {
            if(assignmentAsmGen.directIntoY(value)) {
                assignExpressionToRegister(value, RegisterOrPair.Y)
                out("  sty  P8ZP_SCRATCH_REG")
            } else {
                out("  pha")
                assignExpressionToVariable(value, "P8ZP_SCRATCH_REG", value.type)
                out("  pla")
            }
            out("  $compare  P8ZP_SCRATCH_REG")
        }

        when(value) {
            is PtArrayIndexer -> {
                val constIndex = value.index.asConstInteger()
                if(constIndex!=null) {
                    val offset = program.memsizer.memorySize(value.type, constIndex)
                    if(offset<256) {
                        if(value.variable==null)
                            TODO("support for ptr indexing ${value.position}")
                        return out("  ldy  #$offset |  $compare  ${asmVariableName(value.variable!!)},y")
                    }
                }
                cmpViaScratch()
            }
            is PtMemoryByte -> {
                val constAddr = value.address.asConstInteger()
                if(constAddr!=null) {
                    out("  $compare  ${constAddr.toHex()}")
                } else {
                    cmpViaScratch()
                }
            }
            is PtIdentifier -> {
                out("  $compare  ${asmVariableName(value)}")
            }
            is PtNumber -> {
                if(value.number!=0.0)
                    out("  $compare  #${value.number.toInt()}")
            }
            else -> {
                cmpViaScratch()
            }
        }
    }

    internal fun immediateAndInplace(name: String, value: Int) {
        if(isTargetCpu(CpuType.CPU65C02)) {
            out(" lda  #${value xor 255} |  trb  $name")     // reset bit
        } else  {
            out(" lda  $name |  and  #$value |  sta  $name")
        }
    }

    internal fun immediateOrInplace(name: String, value: Int) {
        if(isTargetCpu(CpuType.CPU65C02)) {
            out(" lda  #$value |  tsb  $name")       // set bit
        } else  {
            out(" lda  $name |  ora  #$value |  sta  $name")
        }
    }

    internal fun assignConditionValueToRegisterAndTest(condition: PtExpression) {
        assignExpressionToRegister(condition, RegisterOrPair.A)
        when(condition) {
            is PtNumber,
            is PtBool,
            is PtIdentifier,
            is PtIrRegister,
            is PtArrayIndexer,
            is PtPrefix,
            is PtIfExpression,
            is PtBinaryExpression -> { /* no cmp necessary the lda has been done just prior */ }
            is PtTypeCast -> {
                if(!condition.value.type.isByte && !condition.value.type.isWord)
                    out("  cmp  #0")
            }
            else -> out("  cmp  #0")
        }
    }

    internal fun translateFloatsEqualsConditionIntoA(left: PtExpression, right: PtExpression) {
        fun equalf(leftName: String, rightName: String) {
            out("""
                    lda  #<$leftName
                    ldy  #>$leftName
                    sta  P8ZP_SCRATCH_W1
                    sty  P8ZP_SCRATCH_W1+1
                    lda  #<$rightName
                    ldy  #>$rightName
                    jsr  floats.vars_equal_f""")
        }
        fun equalf(expr: PtExpression, rightName: String) {
            assignExpressionToRegister(expr, RegisterOrPair.FAC1, true)
            out("""
                    lda  #<$rightName
                    ldy  #>$rightName
                    jsr  floats.var_fac1_equal_f""")
        }
        if(left is PtIdentifier) {
            when (right) {
                is PtIdentifier -> equalf(asmVariableName(left), asmVariableName(right))
                is PtNumber -> equalf(asmVariableName(left), allocator.getFloatAsmConst(right.number))
                else -> {
                    assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
                    equalf(asmVariableName(left), subroutineFloatEvalResultVar1)
                    subroutineExtra(left.definingISub()!!).usedFloatEvalResultVar1 = true
                }
            }
        } else {
            when (right) {
                is PtIdentifier -> equalf(left, asmVariableName(right))
                is PtNumber -> equalf(left, allocator.getFloatAsmConst(right.number))
                else -> {
                    assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
                    equalf(left, subroutineFloatEvalResultVar1)
                    subroutineExtra(left.definingISub()!!).usedFloatEvalResultVar1 = true
                }
            }
        }
    }

    internal fun translateFloatsLessConditionIntoA(left: PtExpression, right: PtExpression, lessOrEquals: Boolean) {
        fun lessf(leftName: String, rightName: String) {
            out("""
                lda  #<$rightName
                ldy  #>$rightName
                sta  P8ZP_SCRATCH_W2
                sty  P8ZP_SCRATCH_W2+1
                lda  #<$leftName
                ldy  #>$leftName""")
            if(lessOrEquals)
                out("jsr  floats.vars_lesseq_f")
            else
                out("jsr  floats.vars_less_f")
        }
        fun lessf(expr: PtExpression, rightName: String) {
            assignExpressionToRegister(expr, RegisterOrPair.FAC1, true)
            out("  lda  #<$rightName |  ldy  #>$rightName")
            if(lessOrEquals)
                out("  jsr  floats.var_fac1_lesseq_f")
            else
                out("  jsr  floats.var_fac1_less_f")
        }
        if(left is PtIdentifier) {
            when (right) {
                is PtIdentifier -> lessf(asmVariableName(left), asmVariableName(right))
                is PtNumber -> lessf(asmVariableName(left), allocator.getFloatAsmConst(right.number))
                else -> {
                    assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
                    lessf(asmVariableName(left), subroutineFloatEvalResultVar1)
                    subroutineExtra(left.definingISub()!!).usedFloatEvalResultVar1 = true
                }
            }
        } else {
            when (right) {
                is PtIdentifier -> lessf(left, asmVariableName(right))
                is PtNumber -> lessf(left, allocator.getFloatAsmConst(right.number))
                else -> {
                    assignExpressionToVariable(right, subroutineFloatEvalResultVar1, DataType.FLOAT)
                    lessf(left, subroutineFloatEvalResultVar1)
                    subroutineExtra(left.definingISub()!!).usedFloatEvalResultVar1 = true
                }
            }
        }
    }

    internal fun checkIfConditionCanUseBIT(condition: PtBinaryExpression): Triple<Boolean, PtIdentifier, Int>? {
        if(condition.operator == "==" || condition.operator == "!=") {
            if (condition.right.asConstInteger() == 0) {
                val and = condition.left as? PtBinaryExpression
                if (and != null && and.operator == "&" && and.type.isUnsignedByte) {
                    val bitmask = and.right.asConstInteger()
                    if(bitmask==128 || bitmask==64) {
                        val variable = and.left as? PtIdentifier
                        if (variable != null && variable.type.isByte) {
                            return Triple(condition.operator=="!=", variable, bitmask)
                        }
                        val typecast = and.left as? PtTypeCast
                        if (typecast != null && typecast.type.isUnsignedByte) {
                            val castedVariable = typecast.value as? PtIdentifier
                            if(castedVariable!=null && castedVariable.type.isByte)
                                return Triple(condition.operator=="!=", castedVariable, bitmask)
                        }
                    }
                }
            }
        }
        return null
    }

    internal fun loadIndirectByte(zpPtrVar: String, offset: UByte) {
        // loads byte pointed to by the ptrvar into A
        if (offset > 0u) {
            out("  ldy  #$offset |  lda  ($zpPtrVar),y")
        } else {
            if(isTargetCpu(CpuType.CPU65C02))
                out("  lda  ($zpPtrVar)")
            else
                out("  ldy  #0 |  lda  ($zpPtrVar),y")
        }
    }

    internal fun loadIndirectFloat(zpPtrVar: String, offset: UByte) {
        // loads float pointed to by the ptrvar into FAC1
        if (offset > 0u) {
            out("""
                lda  $zpPtrVar
                ldy  $zpPtrVar+1
                clc
                adc  #$offset
                bcc  +
                iny
+               jsr  floats.MOVFM""")
            return
        }

        out("""
            lda  $zpPtrVar
            ldy  $zpPtrVar+1
            jsr  floats.MOVFM""")
    }

    internal fun loadIndirectWord(zpPtrVar: String, offset: UByte) {
        // loads word pointed to by the ptr var into AY
        if (offset > 0u) {
            out("""
                    ldy  #$offset
                    lda  ($zpPtrVar),y
                    tax
                    iny
                    lda  ($zpPtrVar),y
                    tay
                    txa""")
        } else {
            if(isTargetCpu(CpuType.CPU65C02))
                out("""
                    ldy  #1
                    lda  ($zpPtrVar),y
                    tay
                    lda  ($zpPtrVar)""")
            else
                out("""
                    ldy  #0
                    lda  ($zpPtrVar),y
                    tax
                    iny
                    lda  ($zpPtrVar),y
                    tay
                    txa""")
        }
    }

    internal fun storeIndirectByte(byte: Int, zpPtrVar: String, offset: UByte) {
        if (offset > 0u) {
            out("  lda  #$byte |  ldy  #$offset |  sta  ($zpPtrVar),y")
        } else {
            if(isTargetCpu(CpuType.CPU65C02)) {
                out("  lda  #$byte |  sta  ($zpPtrVar)")
            } else {
                if (byte == 0)
                    out("  lda  #0 |  tay |  sta  ($zpPtrVar),y")
                else
                    out("  lda  #$byte |  ldy  #0 |  sta  ($zpPtrVar),y")
            }
        }
    }

    internal fun storeIndirectByteVar(varname: String, zpPtrVar: String, offset: UByte) {
        if (offset > 0u) {
            out("  lda  $varname |  ldy  #$offset |  sta  ($zpPtrVar),y")
        } else {
            if(isTargetCpu(CpuType.CPU65C02))
                out("  lda  $varname |  sta  ($zpPtrVar)")
            else
                out("  lda  $varname |  ldy  #0 |  sta  ($zpPtrVar),y")
        }
    }

    internal fun storeIndirectWord(word: Int, zpPtrVar: String, offset: UByte) {
        if (offset > 0u) {
            out("""
                lda  #<$word
                ldy  #$offset
                sta  ($zpPtrVar),y
                lda  #>$word
                iny
                sta  ($zpPtrVar),y""")
        } else {
            if(word==0) {
                out("""
                    lda  #0
                    tay
                    sta  ($zpPtrVar),y
                    iny
                    sta  ($zpPtrVar),y""")
            } else {
                out("""
                    lda  #<$word
                    ldy  #0
                    sta  ($zpPtrVar),y
                    lda  #>$word
                    iny
                    sta  ($zpPtrVar),y""")
            }
        }
    }

    internal fun storeIndirectByteReg(
        register: CpuRegister,
        zpPtrVar: String,
        offset: UByte,
        signed: Boolean,
        extendWord: Boolean
    ) {
        if(extendWord) {
            when(register) {
                CpuRegister.A -> {}
                CpuRegister.X -> out("  txa")
                CpuRegister.Y -> out("  tya")
            }
            signExtendAXlsb(if(signed) BaseDataType.BYTE else BaseDataType.UBYTE)
            out("""
                ldy  #$offset
                sta  ($zpPtrVar),y
                iny
                txa
                sta  ($zpPtrVar),y""")
            return
        }

        if(offset > 0u) {
            when(register) {
                CpuRegister.A -> out("  ldy  #$offset |  sta  ($zpPtrVar),y")
                CpuRegister.X -> out("  txa |  ldy  #$offset |  sta  ($zpPtrVar),y")
                CpuRegister.Y -> out("  tya |  ldy  #$offset |  sta  ($zpPtrVar),y")
            }
            return
        }

        when(register) {
            CpuRegister.A -> {
                if(isTargetCpu(CpuType.CPU65C02)) out("  sta  ($zpPtrVar)") else out("  ldy  #0 |  sta  ($zpPtrVar),y")
            }
            CpuRegister.X -> {
                out("  txa")
                if(isTargetCpu(CpuType.CPU65C02)) out("  sta  ($zpPtrVar)") else out("  ldy  #0 |  sta  ($zpPtrVar),y")
            }
            CpuRegister.Y -> {
                out("  tya")
                if(isTargetCpu(CpuType.CPU65C02)) out("  sta  ($zpPtrVar)") else out("  ldy  #0 |  sta  ($zpPtrVar),y")
            }
        }
    }

    internal fun storeIndirectWordReg(regs: RegisterOrPair, zpPtrVar: String, offset: UByte) {
        if (offset > 0u) {
            when(regs) {
                RegisterOrPair.AX -> {
                    out("""
                        ldy  #$offset
                        sta  ($zpPtrVar),y
                        txa
                        iny
                        sta  ($zpPtrVar),y""")
                }

                RegisterOrPair.AY -> {
                    out("""
                        sty  P8ZP_SCRATCH_REG
                        ldy  #$offset
                        sta  ($zpPtrVar),y
                        lda  P8ZP_SCRATCH_REG
                        iny
                        sta  ($zpPtrVar),y""")
                }

                RegisterOrPair.XY -> {
                    out("""
                        sty  P8ZP_SCRATCH_REG
                        txa
                        ldy  #$offset
                        sta  ($zpPtrVar),y
                        lda  P8ZP_SCRATCH_REG
                        iny
                        sta  ($zpPtrVar),y""")
                }

                in Cx16VirtualRegisters -> {
                    val regname = regs.asScopedNameVirtualReg(DataType.UWORD)
                    out("""
                        lda  $regname
                        ldy  #$offset
                        sta  ($zpPtrVar),y
                        lda  $regname+1
                        iny
                        sta  ($zpPtrVar),y""")
                }

                else -> throw AssemblyError("wrong word reg")
            }
        } else {
            when(regs) {
                RegisterOrPair.AX -> {
                    if(isTargetCpu(CpuType.CPU65C02))
                        out("""
                            sta  ($zpPtrVar)
                            txa
                            ldy  #1
                            sta  ($zpPtrVar),y""")
                    else
                        out("""
                            ldy  #0
                            sta  ($zpPtrVar),y
                            txa
                            iny
                            sta  ($zpPtrVar),y""")
                }

                RegisterOrPair.AY -> {
                    if(isTargetCpu(CpuType.CPU65C02))
                        out("""
                            sta  ($zpPtrVar)
                            tya
                            ldy  #1
                            sta  ($zpPtrVar),y""")
                    else
                        out("""
                            sty  P8ZP_SCRATCH_REG
                            ldy  #0
                            sta  ($zpPtrVar),y
                            lda  P8ZP_SCRATCH_REG
                            iny
                            sta  ($zpPtrVar),y""")
                }

                RegisterOrPair.XY -> {
                    if(isTargetCpu(CpuType.CPU65C02))
                        out("""
                            txa
                            sta  ($zpPtrVar)
                            tya
                            ldy  #1
                            sta  ($zpPtrVar),y""")
                    else
                        out("""
                            sty  P8ZP_SCRATCH_REG
                            txa
                            ldy  #0
                            sta  ($zpPtrVar),y
                            lda  P8ZP_SCRATCH_REG
                            iny
                            sta  ($zpPtrVar),y""")
                }

                in Cx16VirtualRegisters -> {
                    val regname = regs.asScopedNameVirtualReg(DataType.UWORD)
                    out("""
                        lda  $regname
                        ldy  #0
                        sta  ($zpPtrVar),y
                        lda  $regname+1
                        iny
                        sta  ($zpPtrVar),y""")
                }

                else -> throw AssemblyError("wrong word reg")
            }
        }
    }

    internal fun storeIndirectWordVar(varname: String, sourceDt: DataType, zpPtrVar: String, offset: UByte) {
        if(sourceDt.isByteOrBool) TODO("implement byte/bool to word pointer assignment")
        if (offset > 0u) {
            out("""
                lda  $varname
                ldy  #$offset
                sta  ($zpPtrVar),y
                lda  $varname+1
                iny
                sta  ($zpPtrVar),y""")
        } else {
            if(isTargetCpu(CpuType.CPU65C02))
                out("""
                    lda  $varname
                    sta  ($zpPtrVar)
                    lda  $varname+1
                    ldy  #1
                    sta  ($zpPtrVar),y""")
            else
                out("""
                    lda  $varname
                    ldy  #0
                    sta  ($zpPtrVar),y
                    lda  $varname+1
                    iny
                    sta  ($zpPtrVar),y""")
        }
    }

    internal fun storeIndirectFloat(float: Double, zpPtrVar: String, offset: UByte) {
        val floatConst = allocator.getFloatAsmConst(float)
        if (offset > 0u) {
            out("""
                lda  #<$floatConst
                ldy  #>$floatConst
                sta  P8ZP_SCRATCH_W2
                sty  P8ZP_SCRATCH_W2+1
                lda  $zpPtrVar
                ldy  $zpPtrVar+1
                clc
                adc  #$offset
                bcc  +
                iny
+               jsr  floats.copy_float2""")
            return
        }

        out("""
            lda  #<$floatConst
            ldy  #>$floatConst
            sta  P8ZP_SCRATCH_W2
            sty  P8ZP_SCRATCH_W2+1
            lda  $zpPtrVar
            ldy  $zpPtrVar+1
            jsr  floats.copy_float2""")
    }

    internal fun storeIndirectFloatVar(varname: String, zpPtrVar: String, offset: UByte) {
        if (offset > 0u) {
            out("""
                lda  #<$varname
                ldy  #>$varname+1
                sta  P8ZP_SCRATCH_W1
                sty  P8ZP_SCRATCH_W1+1
                lda  $zpPtrVar
                ldy  $zpPtrVar+1
                clc
                adc  #$offset
                bcc  +
                iny
+               jsr  floats.copy_float""")
            return
        }

        out("""
            lda  #<$varname
            ldy  #>$varname+1
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            lda  $zpPtrVar
            ldy  $zpPtrVar+1
            jsr  floats.copy_float""")
    }


    internal fun romableError(problem: String, pos: Position, assemblerShouldFail: Boolean = true) {
        if(options.romable) {
            // until the code generation can provide an alternative, we have to report about code generated that is incompatible with ROMable code mode...
            errors.warn("problem for ROMable code: $problem", pos)
            if(assemblerShouldFail) {
                out("  .error \"ROMable code selected but incompatible code was generated: $problem  $pos\"")
            }
        }
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

    val extraVars = mutableListOf<Triple<BaseDataType, String, UInt?>>()
}