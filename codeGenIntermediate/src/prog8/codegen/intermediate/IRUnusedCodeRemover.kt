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
        blockVars.forEach { stVar ->
            irprog.allSubs().flatMap { it.chunks }.forEach { chunk ->
                chunk.instructions.forEach { ins ->
                    if(ins.labelSymbol == stVar.name) {
                        return  // symbol occurs in an instruction
                    }
                }
            }

            irprog.st.allVariables().forEach { variable ->
                val initValue = variable.onetimeInitializationArrayValue
                if(!initValue.isNullOrEmpty()) {
                    if(initValue.any {
                        it.addressOfSymbol?.startsWith(blockLabel)==true
                    })
                        return   // symbol occurs in an initializer value (address-of this symbol)_
                }
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
        instructions.toTypedArray().forEach { ins ->
            if(ins.opcode in arrayOf(Opcode.LOAD, Opcode.LOADR, Opcode.LOADM)) {
                if(ins.reg1!=0) {
                    if(instructions.count { it.reg1==ins.reg1 || it.reg2==ins.reg1 } <2) {
                        if(ins.labelSymbol!=null)
                            code.st.removeIfExists(ins.labelSymbol!!)
                        instructions.remove(ins)
                    }
                }
                else if(ins.fpReg1!=0) {
                    if (instructions.count { it.fpReg1 == ins.fpReg1 || it.fpReg2 == ins.fpReg1 } < 2) {
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

    private fun removeUnreachable(allLabeledChunks: MutableMap<String, IRCodeChunkBase>): Int {
        val entrypointSub = irprog.blocks.single { it.label=="main" }
            .children.single { it is IRSubroutine && it.label=="main.start" }
        val reachable = mutableSetOf((entrypointSub as IRSubroutine).chunks.first())
        reachable.add(irprog.globalInits)

        // all chunks referenced in array initializer values are also 'reachable':
        irprog.st.allVariables()
            .filter { !it.uninitialized }
            .forEach {
                it.onetimeInitializationArrayValue?.let { array ->
                    array.forEach {elt ->
                        if(elt.addressOfSymbol!=null && irprog.st.lookup(elt.addressOfSymbol!!)==null)
                            reachable.add(irprog.getChunkWithLabel(elt.addressOfSymbol!!))
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
                it.onetimeInitializationArrayValue?.let { array ->
                    array.forEach {elt ->
                        if(elt.addressOfSymbol!=null && irprog.st.lookup(elt.addressOfSymbol!!)==null)
                            linkedChunks += irprog.getChunkWithLabel(elt.addressOfSymbol!!)
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
            if (chunk.label == "main.start")
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