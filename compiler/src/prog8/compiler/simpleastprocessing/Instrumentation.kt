package prog8.compiler.simpleastprocessing

import prog8.code.StExtSub
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.DataType
import prog8.code.core.Encoding
import prog8.code.core.IErrorReporter


fun profilingInstrumentation(program: PtProgram, st: SymbolTable, errors: IErrorReporter) {
    if(errors.noErrors()) {

        val instrumentationsToAdd = mutableListOf<Pair<PtNode, PtNode>>()

        walkAst(program) { node, depth ->
            when(node) {
                is PtSub -> {
                    // add instrumentation as first thing in the subroutine
                    val blockname = node.definingBlock()?.name
                    if(blockname!=null && blockname!="emudbg" && !node.scopedName.startsWith("prog8_lib.profile_sub_entry")) {
                        val profilingCall = createCall(node, node.scopedName, program, st)
                        val index = if(node.children.firstOrNull() is PtSubSignature) 1 else 0
                        val loadStackpointer = PtInlineAssembly("  tsx", false, node.position)
                        node.add(index, profilingCall)
                        node.add(index, loadStackpointer)
                        if(node.scopedName=="main.start") {
                            val resetCycles = PtInlineAssembly(
"""    stz  $9fb8 ; reset emulator cycles to 0
    lda  #'-'   ; print a line at the start of the profile dump
    ldy  #30
-   sta  $9fbb
    dey
    bne  -
    lda  #10
    sta  $9fbb""", false, node.position)
                            node.add(index, resetCycles)
                        }
                    }
                }
                is PtFunctionCall -> {
                    // add instrumentation just before calls to extsubs
                    // we can only instrument call *statements* not call *expressions* because there is no easy place to insert the instrumentation subroutine call inside expressions...
                    val target = st.lookup(node.name)
                    if (target is StExtSub) {
                        if (!target.name.startsWith("emudbg.") && !target.name.startsWith("prog8_lib.profile_sub_entry")) {
                            val blockname = node.definingBlock()?.name
                            if (blockname != null && blockname != "emudbg" && blockname != "prog8_lib") {
                                if(!node.void) {
                                    errors.info("cannot instrument extsub call expression ${node.name}", node.position)
                                } else {
                                    // simulate being inside the called extsub by subtracing 2 from the current stack pointer
                                    val loadStackpointer = PtInlineAssembly("  tsx\n  dex\n  dex", false, node.position)
                                    instrumentationsToAdd.add(node to loadStackpointer)
                                    val profilingCall = createCall(node, target.scopedNameString, program, st)
                                    instrumentationsToAdd.add(node to profilingCall)
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
            true
        }

        instrumentationsToAdd.forEach { (node, profilingCall) ->
            val index = node.parent.children.indexOf(node)
            node.parent.add(index, profilingCall)
        }

        st.resetCachedFlat()
    }
}


private fun createCall(node: PtNode, scopedName: String, program: PtProgram, st: SymbolTable): PtFunctionCall {
    val profilingCall = PtFunctionCall("prog8_lib.profile_sub_entry", false, false, emptyArray(), node.position)
    val string = PtString(scopedName, Encoding.ISO, node.position)
    val stringName = program.internString(string, st)
    val stringAddress = PtAddressOf(DataType.pointer(DataType.UBYTE), true, node.position)
    stringAddress.add(PtIdentifier(stringName, DataType.STR, node.position))
    profilingCall.add(stringAddress)
    return profilingCall
}


private fun walkAst(root: PtNode, act: (node: PtNode, depth: Int) -> Boolean) {
    fun recurse(node: PtNode, depth: Int) {
        if(act(node, depth))
            node.children.forEach { recurse(it, depth+1) }
    }
    recurse(root, 0)
}

