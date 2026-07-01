package prog8.codegen.intermediate

import prog8.code.core.IErrorReporter
import prog8.intermediate.*


class IRUnusedCodeRemover(
    private val irprog: IRProgram,
    private val errors: IErrorReporter
) {
    fun optimize(): Int {
        var numRemoved = removeUnusedSubroutines() + removeUnusedAsmSubroutines()

        // remove empty blocks
        irprog.blocks.reversed().forEach { block ->
            if(block.isEmpty()) {
                irprog.blocks.remove(block)
                pruneSymboltable(block.label)
                numRemoved++
            }
        }

        return numRemoved
    }

    private fun pruneSymboltable(blockLabel: String) {
        // we could clean up the SymbolTable as well, but ONLY if these symbols aren't referenced somewhere still in an instruction or variable initializer value
        val prefix = "$blockLabel."
        val blockVars = irprog.st.allVariables().filter { it.name.startsWith(prefix) }

        // check if there are symbols referenced elsewhere that we should not prune (even though the rest of the block is empty)

        blockVars.forEach { stVar ->
            irprog.allSubs().flatMap { it.chunks }.forEach { chunk ->
                chunk.instructions.forEach { ins ->
                    if(ins.labelSymbol == stVar.name) {
                        return  // symbol occurs in an instruction
                    }
                }
            }

            irprog.st.allVariables().forEach { variable ->
                val initValue = variable.initializationValue
                if(initValue is IRVariableInitializer.Array) {
                    if(initValue.elements.any {
                        it is IRStSymbolicReference.Symbol && it.name.startsWith(blockLabel)
                    })
                        return   // symbol occurs in an initializer value (address-of this symbol)_
                }
            }
        }

        val blockStructs = irprog.st.allStructDefs().filter { it.name.startsWith(prefix) }
        blockStructs.forEach { struct ->
            irprog.st.allStructInstances().forEach { instance ->
                if(instance.structName == struct.name)
                    return  // a struct instance is declared using this struct type
            }
            irprog.st.allVariables().forEach { variable ->
                if(variable.dt.isPointer || variable.dt.isStructInstance)
                    if(struct.name == variable.dt.subType!!.scopedNameString)
                        return   // a variable exists with the struct as (pointer) type
            }
        }

        irprog.st.removeTree(blockLabel)
        removeBlockInits(irprog, blockLabel)
    }

    private fun removeBlockInits(code: IRProgram, blockLabel: String) {
        val instructions = code.globalInits.instructions
        instructions.toTypedArray().forEach {ins ->
            if(ins.labelSymbol?.startsWith(blockLabel)==true) {
                instructions.remove(ins)
            }
        }

        // remove stray loads
        val readRegs = mutableSetOf<Int>()
        val readFpRegs = mutableSetOf<Int>()
        instructions.forEach { ins ->
            val readRegsCounts = mutableMapOf<RegisterNum, Int>()
            val readFpRegsCounts = mutableMapOf<RegisterNum, Int>()
            val writeRegsCounts = mutableMapOf<RegisterNum, Int>()
            val writeFpRegsCounts = mutableMapOf<RegisterNum, Int>()
            val regsTypes = mutableMapOf<RegisterNum, IRDataType>()
            ins.addUsedRegistersCounts(readRegsCounts, writeRegsCounts, readFpRegsCounts, writeFpRegsCounts, regsTypes, null)
            readRegs.addAll(readRegsCounts.keys.map { it.value })
            readFpRegs.addAll(readFpRegsCounts.keys.map { it.value })
        }
        instructions.toTypedArray().forEach { ins ->
            if(ins.opcode in arrayOf(Opcode.LOAD, Opcode.LOADR, Opcode.LOADM)) {
                val reg1 = ins.reg1
                val fpReg1 = ins.fpReg1
                if(reg1!=null) {
                    if(reg1 !in readRegs) {
                        if(ins.labelSymbol!=null)
                            code.st.removeIfExists(ins.labelSymbol!!)
                        instructions.remove(ins)
                    }
                }
                else if(fpReg1!=null) {
                    if(fpReg1.value !in readFpRegs) {
                        if(ins.labelSymbol!=null)
                            code.st.removeIfExists(ins.labelSymbol!!)
                        instructions.remove(ins)
                    }
                }
            }
        }
    }

    private fun removeUnusedSubroutines(): Int {
        val allLabeledChunks = mutableMapOf<String, IRCodeChunkBase>()
        irprog.foreachCodeChunk { chunk ->
            chunk.label?.let { allLabeledChunks[it] = chunk }
        }

        var numRemoved = removeSimpleUnlinked(allLabeledChunks) + removeUnreachable(allLabeledChunks)
        irprog.blocks.forEach { block ->
            block.children.filterIsInstance<IRSubroutine>().reversed().forEach { sub ->
                if(sub.isEmpty()) {
                    // Don't remove subroutines with @shared variables
                    val hasVariables = irprog.st.allVariables().any { it.name.startsWith(sub.label + ".") }
                    if(!hasVariables) {
                        if(!block.options.ignoreUnused) {
                            errors.info("unused subroutine '${sub.label}'", sub.position)
                        }
                        block.children.remove(sub)
                        irprog.st.removeTree(sub.label)
                        numRemoved++
                    }
                }
            }
        }

        return numRemoved
    }

    private fun removeUnusedAsmSubroutines(): Int {
        val allLabeledAsmsubs = irprog.blocks.asSequence().flatMap { it.children.filterIsInstance<IRAsmSubroutine>() }
            .associateBy { it.label }

        var numRemoved = removeSimpleUnlinkedAsmsubs(allLabeledAsmsubs)
        irprog.blocks.forEach { block ->
            block.children.filterIsInstance<IRAsmSubroutine>().reversed().forEach { sub ->
                if(sub.isEmpty()) {
                    if(!block.options.ignoreUnused) {
                        errors.info("unused subroutine '${sub.label}'", sub.position)
                    }
                    block.children.remove(sub)
                    irprog.st.removeTree(sub.label)
                    numRemoved++
                }
            }
        }

        return numRemoved
    }

    private fun removeSimpleUnlinkedAsmsubs(allSubs: Map<String, IRAsmSubroutine>): Int {
        val linkedAsmSubs = mutableSetOf<IRAsmSubroutine>()

        // TODO: asmsubs in library modules are never removed, we can't really tell here if they're actually being called or not...

        // check if asmsub is called from another asmsub
        irprog.blocks.asSequence().forEach { block ->
            block.children.filterIsInstance<IRAsmSubroutine>().forEach { sub ->
                if (block.options.forceOutput || block.library)
                    linkedAsmSubs += sub
                if (sub.asmChunk.isNotEmpty()) {
                    allSubs.forEach { (label, asmsub) ->
                        if (sub.asmChunk.assembly.contains(label))
                            linkedAsmSubs += asmsub
                    }
                }
                val inlineAsm = sub.asmChunk.next as? IRInlineAsmChunk
                if(inlineAsm!=null) {
                    allSubs.forEach { (label, asmsub) ->
                        if (inlineAsm.assembly.contains(label))
                            linkedAsmSubs += asmsub
                    }
                }
            }
        }

        // check if asmsub is linked or called from another regular subroutine
        irprog.foreachCodeChunk { chunk ->
            chunk.instructions.forEach {
                it.labelSymbol?.let { label -> allSubs[label]?.let { cc -> linkedAsmSubs += cc } }
                // note: branchTarget can't yet point to another IRAsmSubroutine, so do nothing when it's set
            }
        }

        return removeUnlinkedAsmsubs(linkedAsmSubs)
    }

    private fun removeUnlinkedAsmsubs(linkedAsmSubs: Set<IRAsmSubroutine>): Int {
        var numRemoved = 0
        irprog.blocks.asSequence().forEach { block ->
            block.children.withIndex().reversed().forEach { (index, child) ->
                if(child is IRAsmSubroutine && child !in linkedAsmSubs) {
                    block.children.removeAt(index)
                    numRemoved++
                }
            }
        }
        return numRemoved
    }
    

    private val entrypointNames = setOf("p8b_main.p8s_start", "main.start")
    private val mainBlockNames = setOf("p8b_main", "main")
        

    private fun removeUnreachable(allLabeledChunks: MutableMap<String, IRCodeChunkBase>): Int {
        val entrypointSub = irprog.blocks.single { it.label in mainBlockNames }
            .children.single { it is IRSubroutine && it.label in entrypointNames }
        val reachable = mutableSetOf((entrypointSub as IRSubroutine).chunks.first())
        reachable.add(irprog.globalInits)

        // all chunks referenced in array initializer values are also 'reachable':
        irprog.st.allVariables()
            .filter { !it.uninitialized }
            .forEach {
                val initValue = it.initializationValue
                if(initValue is IRVariableInitializer.Array) {
                    initValue.elements.forEach {elt ->
                        if(elt is IRStSymbolicReference.Symbol && irprog.st.lookup(elt.name)==null)
                            reachable.add(irprog.getChunkWithLabel(elt.name))
                    }
                }
            }

        fun grow() {
            val new = mutableSetOf<IRCodeChunkBase>()
            reachable.forEach {
                it.next?.let { next -> new += next }
                it.instructions.forEach { instr ->
                    if (instr.branchTarget == null)
                        instr.labelSymbol?.let { label ->
                            val chunk = allLabeledChunks[label] ?: allLabeledChunks[label.substringBeforeLast('.')]
                            if(chunk!=null)
                                new+=chunk
                            else
                                allLabeledChunks[label]?.let { c -> new += c }
                        }
                    else
                        new += instr.branchTarget!!
                }
            }
            reachable += new
        }

        var previousCount = reachable.size
        while(true) {
            grow()
            if(reachable.size<=previousCount)
                break
            previousCount = reachable.size
        }

        return removeUnlinkedChunks(reachable)
    }

    private fun removeSimpleUnlinked(allLabeledChunks: Map<String, IRCodeChunkBase>): Int {
        val linkedChunks = mutableSetOf<IRCodeChunkBase>()

        // all chunks referenced in array initializer values are linked as well!:
        irprog.st.allVariables()
            .filter { !it.uninitialized }
            .forEach {
                if(it.initializationValue is IRVariableInitializer.Array) {
                    val initValue = it.initializationValue as IRVariableInitializer.Array
                    initValue.elements.forEach {elt ->
                        if(elt is IRStSymbolicReference.Symbol && irprog.st.lookup(elt.name)==null)
                            linkedChunks += irprog.getChunkWithLabel(elt.name)
                    }
                }
            }

        irprog.foreachCodeChunk { chunk ->
            chunk.next?.let { next -> linkedChunks += next }
            chunk.instructions.forEach {
                if(it.branchTarget==null) {
                    it.labelSymbol?.let { label -> allLabeledChunks[label]?.let { cc -> linkedChunks += cc } }
                } else {
                    linkedChunks += it.branchTarget!!
                }
            }
            if (chunk.label in entrypointNames)
                linkedChunks += chunk
        }

        // make sure that chunks that are only used as a prefix of a label, are also marked as linked
        linkedChunks.toList().forEach { chunk ->
            chunk.instructions.forEach {
                if(it.labelSymbol!=null) {
                    val chunkName = it.labelSymbol!!.substringBeforeLast('.')
                    allLabeledChunks[chunkName]?.let { c -> linkedChunks += c }
                }
            }
        }

        linkedChunks.add(irprog.globalInits)
        return removeUnlinkedChunks(linkedChunks)
    }

    private fun removeUnlinkedChunks(linkedChunks: Set<IRCodeChunkBase>): Int {
        var numRemoved = 0
        irprog.foreachSub { sub ->
            // don't remove stuff in library modules or with %forceoutput
            // TODO this is needed to keep the subroutine body of sqrt_long from being wiped !??!   Sounds like the bad solution to the problem: why is the *body* being cleared , making an empty sqrt_long subrotuine end up in the p8ir output (and eventually in the .asm, which crashes the resulting program) 
            // TODO this causes other stuff to end up in the output IR that is not used......
            // note: this wasn't a problem on the virtual target, but it is a problem when using the newcodegen targets (because on the virtual target, the VM simply executes the SQRT instruction directly without needing a library routine)
            val block = irprog.blocks.first { b -> b.children.any { it === sub } }
            if (block.library || block.options.forceOutput)
                return@foreachSub
            
            sub.chunks.withIndex().reversed().forEach { (index, chunk) ->
                if (chunk !in linkedChunks) {
                    if (chunk === sub.chunks[0]) {
                        when(chunk) {
                            is IRCodeChunk -> {
                                if (chunk.isNotEmpty()) {
                                    // don't remove the first chunk of the sub itself because it has to have the name of the sub as label
                                    chunk.instructions.clear()
                                    numRemoved++
                                }
                            }
                            is IRInlineAsmChunk, is IRInlineBinaryChunk -> {
                                sub.chunks[index] = IRCodeChunk(chunk.label, chunk.next)
                                numRemoved++
                            }
                        }
                    } else {
                        sub.chunks.removeAt(index)
                        numRemoved++
                    }
                }
            }
        }
        return numRemoved
    }
}
