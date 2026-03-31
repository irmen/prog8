package prog8.code.optimize

import prog8.code.StExtSub
import prog8.code.SymbolTable
import prog8.code.ast.*

/**
 * Variable optimization passes.
 * Handles assignment target optimization and redundant variable initialization removal.
 */
internal object VariableOptimizers {

    /**
     * Optimizes assignment targets for function calls returning values in registers.
     * If the target register matches the return register, the target can become void.
     */
    fun optimizeAssignTargets(program: PtProgram, st: SymbolTable): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            if(node is PtAssignment) {
                val value = node.value
                val functionName = if (value is PtFunctionCall) value.name else null
                if(functionName!=null) {
                    val stNode = st.lookup(functionName)
                    if (stNode is StExtSub) {
                        require(node.children.size==stNode.returns.size+1) {
                            "number of targets must match return values"
                        }
                        node.children.zip(stNode.returns).withIndex().forEach { (index, xx) ->
                            val target = xx.first as PtAssignTarget
                            val returnedRegister = xx.second.register.registerOrPair
                            if(returnedRegister!=null && !target.void && target.identifier!=null) {
                                if(Helpers.isSame(target.identifier!!, xx.second.type, returnedRegister)) {
                                    // output register is already identical to target register, so it can become void
                                    val voidTarget = PtAssignTarget(true, target.position)
                                    node.setChild(index, voidTarget)
                                    voidTarget.parent = node
                                    changes++
                                }
                            }
                        }
                    }
                    if(node.children.dropLast(1).all { (it as PtAssignTarget).void }) {
                        // all targets are now void, the whole assignment can be discarded and replaced by just a (void) call to the subroutine
                        val index = node.parent.children.indexOf(node)
                        val voidCall = PtFunctionCall(functionName, false, false, emptyArray(), value.position)
                        value.children.forEach { voidCall.add(it) }
                        node.parent.setChild(index, voidCall)
                        voidCall.parent = node.parent
                        changes++
                    }
                }
            }
            true
        }
        return changes
    }

    /**
     * Removes redundant variable initializations.
     * If a variable is initialized and then overwritten before being read, the initialization is removed.
     */
    fun optimizeRedundantVarInits(program: PtProgram): Int {
        fun statementsFromVarInitToFirstAssignment(varInit: PtAssignment, variable: PtIdentifier, parent: PtNode): Pair<Int, Int> {
            val varInitIndex = parent.children.indexOf(varInit)
            for (stmt in parent.children.asSequence().withIndex().drop(varInitIndex)) {
                (stmt.value as? PtAssignment)?.let { assignment ->
                    if(!assignment.isVarInitializer) {
                        if (assignment.multiTarget) {
                            assignment.children.dropLast(1).forEach { target ->
                                target as PtAssignTarget
                                if(!target.void && variable.same(target.identifier))
                                    return varInitIndex to stmt.index
                            }
                        } else {
                            if (!assignment.target.void && variable.same(assignment.target.identifier))
                                return varInitIndex to stmt.index
                        }
                    }
                }
            }
            return -1 to -1
        }

        val removeInitializations = mutableListOf<Pair<PtNode, PtAssignment>>()

        fun potentiallyOptimize(identifier: PtIdentifier, parent: PtNode, initializerIndex: Int, assignIndex: Int) {
            if (assignIndex>initializerIndex) {
                val inbetween = parent.children.subList(initializerIndex+1, assignIndex)
                if(!inbetween.any { stmt -> referencesIdentifier(stmt, identifier) }) {
                    // var initializer is redundant, it will be overwritten by an assignment later. remove the initializer
                    removeInitializations.add(parent to parent.children[initializerIndex] as PtAssignment)
                }
            }
        }

        walkAst(program) { node: PtNode, depth: Int ->
            if(node is PtAssignment && node.isVarInitializer) {
                if(node.multiTarget) {
                    node.children.dropLast(1).forEach { target ->
                        target as PtAssignTarget
                        if(!target.void) {
                            target.identifier?.let { identifier ->
                                val statements = statementsFromVarInitToFirstAssignment(node, identifier, node.parent)
                                if(statements.first>=0)
                                    potentiallyOptimize(identifier, node.parent, statements.first, statements.second)
                            }
                        }
                    }
                } else {
                    node.target.identifier?.let { identifier ->
                        val statements = statementsFromVarInitToFirstAssignment(node, identifier, node.parent)
                        if(statements.first>=0)
                            potentiallyOptimize(identifier, node.parent, statements.first, statements.second)
                    }
                }
            }
            true
        }

        removeInitializations.forEach { (parent, varInit) ->
            parent.removeChild(varInit)
        }

        return removeInitializations.size
    }
}
