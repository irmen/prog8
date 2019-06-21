package prog8.ast

fun Program.reorderStatements() {
    val initvalueCreator = VarInitValueAndAddressOfCreator(namespace)
    initvalueCreator.process(this)

    val checker = StatementReorderer(this)
    checker.process(this)
}

const val initvarsSubName="prog8_init_vars"    // the name of the subroutine that should be called for every block to initialize its variables


private class StatementReorderer(private val program: Program): IAstProcessor {
    // Reorders the statements in a way the compiler needs.
    // - 'main' block must be the very first statement UNLESS it has an address set.
    // - blocks are ordered by address, where blocks without address are put at the end.
    // - in every scope:
    //      -- the directives '%output', '%launcher', '%zeropage', '%zpreserved', '%address' and '%option' will come first.
    //      -- all vardecls then follow.
    //      -- the remaining statements then follow in their original order.
    //
    // - the 'start' subroutine in the 'main' block will be moved to the top immediately following the directives.
    // - all other subroutines will be moved to the end of their block.

    private val directivesToMove = setOf("%output", "%launcher", "%zeropage", "%zpreserved", "%address", "%option")

    override fun process(module: Module) {
        super.process(module)

        val (blocks, other) = module.statements.partition { it is Block }
        module.statements = other.asSequence().plus(blocks.sortedBy { (it as Block).address ?: Int.MAX_VALUE }).toMutableList()

        // make sure user-defined blocks come BEFORE library blocks, and move the "main" block to the top of everything
        val nonLibraryBlocks = module.statements.withIndex()
                .filter { it.value is Block && !(it.value as Block).isInLibrary }
                .map { it.index to it.value }
                .reversed()
        for(nonLibBlock in nonLibraryBlocks)
            module.statements.removeAt(nonLibBlock.first)
        for(nonLibBlock in nonLibraryBlocks)
            module.statements.add(0, nonLibBlock.second)
        val mainBlock = module.statements.singleOrNull { it is Block && it.name=="main" }
        if(mainBlock!=null && (mainBlock as Block).address==null) {
            module.statements.remove(mainBlock)
            module.statements.add(0, mainBlock)
        }

        val varDecls = module.statements.filterIsInstance<VarDecl>()
        module.statements.removeAll(varDecls)
        module.statements.addAll(0, varDecls)

        val directives = module.statements.filter {it is Directive && it.directive in directivesToMove}
        module.statements.removeAll(directives)
        module.statements.addAll(0, directives)

        sortConstantAssignments(module.statements)
    }

    override fun process(block: Block): IStatement {

        val subroutines = block.statements.filterIsInstance<Subroutine>()
        var numSubroutinesAtEnd = 0
        // move all subroutines to the end of the block
        for (subroutine in subroutines) {
            if(subroutine.name!="start" || block.name!="main") {
                block.statements.remove(subroutine)
                block.statements.add(subroutine)
            }
            numSubroutinesAtEnd++
        }
        // move the "start" subroutine to the top
        if(block.name=="main") {
            block.statements.singleOrNull { it is Subroutine && it.name == "start" } ?.let {
                block.statements.remove(it)
                block.statements.add(0, it)
                numSubroutinesAtEnd--
            }
        }

        // make sure there is a 'return' in front of the first subroutine
        // (if it isn't the first statement in the block itself, and isn't the program's entrypoint)
        if(numSubroutinesAtEnd>0 && block.statements.size > (numSubroutinesAtEnd+1)) {
            val firstSub = block.statements[block.statements.size - numSubroutinesAtEnd] as Subroutine
            if(firstSub.name != "start" && block.name != "main") {
                val stmtBeforeFirstSub = block.statements[block.statements.size - numSubroutinesAtEnd - 1]
                if (stmtBeforeFirstSub !is Return
                        && stmtBeforeFirstSub !is Jump
                        && stmtBeforeFirstSub !is Subroutine
                        && stmtBeforeFirstSub !is BuiltinFunctionStatementPlaceholder) {
                    val ret = Return(emptyList(), stmtBeforeFirstSub.position)
                    ret.linkParents(block)
                    block.statements.add(block.statements.size - numSubroutinesAtEnd, ret)
                }
            }
        }

        val varDecls = block.statements.filterIsInstance<VarDecl>()
        block.statements.removeAll(varDecls)
        block.statements.addAll(0, varDecls)
        val directives = block.statements.filter {it is Directive && it.directive in directivesToMove}
        block.statements.removeAll(directives)
        block.statements.addAll(0, directives)
        block.linkParents(block.parent)

        sortConstantAssignments(block.statements)

        val varInits = block.statements.withIndex().filter { it.value is VariableInitializationAssignment }
        if(varInits.isNotEmpty()) {
            val statements = varInits.map{it.value}.toMutableList()
            val varInitSub = Subroutine(initvarsSubName, emptyList(), emptyList(), emptyList(), emptyList(),
                    emptySet(), null, false, statements, block.position)
            varInitSub.keepAlways = true
            varInitSub.linkParents(block)
            block.statements.add(varInitSub)

            // remove the varinits from the block's statements
            for(index in varInits.map{it.index}.reversed())
                block.statements.removeAt(index)
        }

        return super.process(block)
    }

    override fun process(subroutine: Subroutine): IStatement {
        super.process(subroutine)

        sortConstantAssignments(subroutine.statements)

        val varDecls = subroutine.statements.filterIsInstance<VarDecl>()
        subroutine.statements.removeAll(varDecls)
        subroutine.statements.addAll(0, varDecls)
        val directives = subroutine.statements.filter {it is Directive && it.directive in directivesToMove}
        subroutine.statements.removeAll(directives)
        subroutine.statements.addAll(0, directives)

        if(subroutine.returntypes.isEmpty()) {
            // add the implicit return statement at the end (if it's not there yet), but only if it's not a kernel routine.
            // and if an assembly block doesn't contain a rts/rti
            if(subroutine.asmAddress==null && subroutine.amountOfRtsInAsm()==0) {
                if (subroutine.statements.lastOrNull {it !is VarDecl} !is Return) {
                    val returnStmt = Return(emptyList(), subroutine.position)
                    returnStmt.linkParents(subroutine)
                    subroutine.statements.add(returnStmt)
                }
            }
        }

        return subroutine
    }

    override fun process(scope: AnonymousScope): AnonymousScope {
        scope.statements = scope.statements.map { it.process(this)}.toMutableList()
        sortConstantAssignments(scope.statements)
        return scope
    }

    override fun process(decl: VarDecl): IStatement {
        if(decl.arraysize==null) {
            val array = decl.value as? LiteralValue
            if(array!=null && array.isArray) {
                val size = program.heap.get(array.heapId!!).arraysize
                decl.arraysize = ArrayIndex(LiteralValue.optimalInteger(size, decl.position), decl.position)
            }
        }
        return super.process(decl)
    }

    private fun sortConstantAssignments(statements: MutableList<IStatement>) {
        // sort assignments by datatype and value, so multiple initializations with the same value can be optimized (to load the value just once)
        val result = mutableListOf<IStatement>()
        val stmtIter = statements.iterator()
        for(stmt in stmtIter) {
            if(stmt is Assignment && !stmt.targets.any { it.isMemoryMapped(program.namespace) }) {
                val constval = stmt.value.constValue(program)
                if(constval!=null) {
                    val (sorted, trailing) = sortConstantAssignmentSequence(stmt, stmtIter)
                    result.addAll(sorted)
                    if(trailing!=null)
                        result.add(trailing)
                }
                else
                    result.add(stmt)
            }
            else
                result.add(stmt)
        }
        statements.clear()
        statements.addAll(result)
    }

    private fun sortConstantAssignmentSequence(first: Assignment, stmtIter: MutableIterator<IStatement>): Pair<List<Assignment>, IStatement?> {
        val sequence= mutableListOf(first)
        var trailing: IStatement? = null
        while(stmtIter.hasNext()) {
            val next = stmtIter.next()
            if(next is Assignment) {
                val constValue = next.value.constValue(program)
                if(constValue==null) {
                    trailing = next
                    break
                }
                sequence.add(next)
            }
            else {
                trailing=next
                break
            }
        }
        val sorted = sequence.sortedWith(compareBy({it.value.resultingDatatype(program)}, {it.singleTarget?.shortString(true)}))
        return Pair(sorted, trailing)
    }

}


private class VarInitValueAndAddressOfCreator(private val namespace: INameScope): IAstProcessor {
    // Replace the var decl with an assignment and add a new vardecl with the default constant value.
    // This makes sure the variables get reset to the intended value on a next run of the program.
    // Variable decls without a value don't get this treatment, which means they retain the last
    // value they had when restarting the program.
    // This is done in a separate step because it interferes with the namespace lookup of symbols
    // in other ast processors.

    // Also takes care to insert AddressOf (&) expression where required (string params to a UWORD function param etc).


    private val vardeclsToAdd = mutableMapOf<INameScope, MutableMap<String, VarDecl>>()

    override fun process(module: Module) {
        super.process(module)

        // add any new vardecls to the various scopes
        for(decl in vardeclsToAdd)
            for(d in decl.value) {
                d.value.linkParents(decl.key as Node)
                decl.key.statements.add(0, d.value)
            }
    }

    override fun process(decl: VarDecl): IStatement {
        super.process(decl)
        if(decl.type!=VarDeclType.VAR || decl.value==null)
            return decl

        if(decl.datatype in NumericDatatypes) {
            val scope = decl.definingScope()
            addVarDecl(scope, decl.asDefaultValueDecl(null))
            val declvalue = decl.value!!
            val value =
                    if(declvalue is LiteralValue) {
                        val converted = declvalue.intoDatatype(decl.datatype)
                        converted ?: declvalue
                    }
                    else
                        declvalue
            return VariableInitializationAssignment(
                    AssignTarget(null, IdentifierReference(decl.scopedname.split("."), decl.position), null, null, decl.position),
                    null,
                    value,
                    decl.position
            )
        }
        return decl
    }

    override fun process(functionCall: FunctionCall): IExpression {
        val targetStatement = functionCall.target.targetSubroutine(namespace)
        if(targetStatement!=null) {
            var node: Node = functionCall
            while(node !is IStatement)
                node=node.parent
            addAddressOfExprIfNeeded(targetStatement, functionCall.arglist, node)
        }
        return functionCall
    }

    override fun process(functionCallStatement: FunctionCallStatement): IStatement {
        val targetStatement = functionCallStatement.target.targetSubroutine(namespace)
        if(targetStatement!=null)
            addAddressOfExprIfNeeded(targetStatement, functionCallStatement.arglist, functionCallStatement)
        return functionCallStatement
    }

    private fun addAddressOfExprIfNeeded(subroutine: Subroutine, arglist: MutableList<IExpression>, parent: IStatement) {
        // functions that accept UWORD and are given an array type, or string, will receive the AddressOf (memory location) of that value instead.
        for(argparam in subroutine.parameters.withIndex().zip(arglist)) {
            if(argparam.first.value.type==DataType.UWORD || argparam.first.value.type in StringDatatypes) {
                if(argparam.second is AddressOf)
                    continue
                val idref = argparam.second as? IdentifierReference
                val strvalue = argparam.second as? LiteralValue
                if(idref!=null) {
                    val variable = idref.targetVarDecl(namespace)
                    if(variable!=null && (variable.datatype in StringDatatypes || variable.datatype in ArrayDatatypes)) {
                        val pointerExpr = AddressOf(idref, idref.position)
                        pointerExpr.scopedname = parent.makeScopedName(idref.nameInSource.single())
                        pointerExpr.linkParents(arglist[argparam.first.index].parent)
                        arglist[argparam.first.index] = pointerExpr
                    }
                }
                else if(strvalue!=null) {
                    if(strvalue.isString) {
                        // replace the argument with &autovar
                        val autoVarName = "$autoHeapValuePrefix${strvalue.heapId}"
                        val autoHeapvarRef = IdentifierReference(listOf(autoVarName), strvalue.position)
                        val pointerExpr = AddressOf(autoHeapvarRef, strvalue.position)
                        pointerExpr.scopedname = parent.makeScopedName(autoVarName)
                        pointerExpr.linkParents(arglist[argparam.first.index].parent)
                        arglist[argparam.first.index] = pointerExpr
                        // add a vardecl so that the autovar can be resolved in later lookups
                        val variable = VarDecl(VarDeclType.VAR, strvalue.type, false, null, false, autoVarName, strvalue, strvalue.position)
                        addVarDecl(strvalue.definingScope(), variable)
                    }
                }
            }
        }
    }

    private fun addVarDecl(scope: INameScope, variable: VarDecl) {
        if(scope !in vardeclsToAdd)
            vardeclsToAdd[scope] = mutableMapOf()
        vardeclsToAdd.getValue(scope)[variable.name]=variable
    }

}
