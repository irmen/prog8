package prog8.ast.processing

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.functions.BuiltinFunctions
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification


internal class StatementReorderer(val program: Program, val errors: ErrorReporter) : AstWalker() {
    // Reorders the statements in a way the compiler needs.
    // - 'main' block must be the very first statement UNLESS it has an address set.
    // - library blocks are put last.
    // - blocks are ordered by address, where blocks without address are placed last.
    // - in every block and module, most directives and vardecls are moved to the top. (not in subroutines!)
    // - the 'start' subroutine is moved to the top.
    // - (syntax desugaring) a vardecl with a non-const initializer value is split into a regular vardecl and an assignment statement.
    // - (syntax desugaring) struct value assignment is expanded into several struct member assignments.
    // - in-place assignments are reordered a bit so that they are mostly of the form A = A <operator> <rest>
    // - sorts the choices in when statement.
    // - insert AddressOf (&) expression where required (string params to a UWORD function param etc).

    private val noModifications = emptyList<IAstModification>()
    private val directivesToMove = setOf("%output", "%launcher", "%zeropage", "%zpreserved", "%address", "%option")

    override fun after(module: Module, parent: Node): Iterable<IAstModification> {
        val (blocks, other) = module.statements.partition { it is Block }
        module.statements = other.asSequence().plus(blocks.sortedBy { (it as Block).address ?: Int.MAX_VALUE }).toMutableList()

        val mainBlock = module.statements.filterIsInstance<Block>().firstOrNull { it.name=="main" }
        if(mainBlock!=null && mainBlock.address==null) {
            module.statements.remove(mainBlock)
            module.statements.add(0, mainBlock)
        }

        reorderVardeclsAndDirectives(module.statements)
        return noModifications
    }

    private fun reorderVardeclsAndDirectives(statements: MutableList<Statement>) {
        val varDecls = statements.filterIsInstance<VarDecl>()
        statements.removeAll(varDecls)
        statements.addAll(0, varDecls)

        val directives = statements.filterIsInstance<Directive>().filter {it.directive in directivesToMove}
        statements.removeAll(directives)
        statements.addAll(0, directives)
    }

    override fun before(block: Block, parent: Node): Iterable<IAstModification> {
        parent as Module
        if(block.isInLibrary) {
            return listOf(
                    IAstModification.Remove(block, parent),
                    IAstModification.InsertLast(block, parent)
            )
        }

        reorderVardeclsAndDirectives(block.statements)
        return noModifications
    }

    override fun before(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        if(subroutine.name=="start" && parent is Block) {
            if(parent.statements.filterIsInstance<Subroutine>().first().name!="start") {
                return listOf(
                        IAstModification.Remove(subroutine, parent),
                        IAstModification.InsertFirst(subroutine, parent)
                )
            }
        }

        val subs = subroutine.statements.filterIsInstance<Subroutine>()
        if(subs.isNotEmpty()) {
            // all subroutines defined within this subroutine are moved to the end
            return subs.map { IAstModification.Remove(it, subroutine) } +
                    subs.map { IAstModification.InsertLast(it, subroutine) }
        }

        return noModifications
    }

    override fun after(arrayIndexedExpression: ArrayIndexedExpression, parent: Node): Iterable<IAstModification> {

        val arrayVar = arrayIndexedExpression.arrayvar.targetVarDecl(program.namespace)
        if(arrayVar!=null && arrayVar.datatype ==  DataType.UWORD) {
            // rewrite   pointervar[index]  into  @(pointervar+index)
            val indexer = arrayIndexedExpression.indexer
            val index = (indexer.indexNum ?: indexer.indexVar)!!
            val add = BinaryExpression(arrayIndexedExpression.arrayvar, "+", index, arrayIndexedExpression.position)
            return if(parent is AssignTarget) {
                // we're part of the target of an assignment, we have to actually change the assign target itself
                val memwrite = DirectMemoryWrite(add, arrayIndexedExpression.position)
                val newtarget = AssignTarget(null, null, memwrite, arrayIndexedExpression.position)
                listOf(IAstModification.ReplaceNode(parent, newtarget, parent.parent))
            } else {
                val memread = DirectMemoryRead(add, arrayIndexedExpression.position)
                listOf(IAstModification.ReplaceNode(arrayIndexedExpression, memread, parent))
            }
        }

        when (val expr2 = arrayIndexedExpression.indexer.origExpression) {
            is NumericLiteralValue -> {
                arrayIndexedExpression.indexer.indexNum = expr2
                arrayIndexedExpression.indexer.origExpression = null
                return noModifications
            }
            is IdentifierReference -> {
                arrayIndexedExpression.indexer.indexVar = expr2
                arrayIndexedExpression.indexer.origExpression = null
                return noModifications
            }
            is Expression -> {
                // replace complex indexing with a temp variable
                return getAutoIndexerVarFor(arrayIndexedExpression)
            }
            else -> return noModifications
        }
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        // when using a simple bit shift and assigning it to a variable of a different type,
        // try to make the bit shifting 'wide enough' to fall into the variable's type.
        // with this, for instance, uword x = 1 << 10  will result in 1024 rather than 0 (the ubyte result).
        if(expr.operator=="<<" || expr.operator==">>") {
            val leftDt = expr.left.inferType(program)
            when (parent) {
                is Assignment -> {
                    val targetDt = parent.target.inferType(program)
                    if(leftDt != targetDt) {
                        val cast = TypecastExpression(expr.left, targetDt.typeOrElse(DataType.STRUCT), true, parent.position)
                        return listOf(IAstModification.ReplaceNode(expr.left, cast, expr))
                    }
                }
                is VarDecl -> {
                    if(!leftDt.istype(parent.datatype)) {
                        val cast = TypecastExpression(expr.left, parent.datatype, true, parent.position)
                        return listOf(IAstModification.ReplaceNode(expr.left, cast, expr))
                    }
                }
                is IFunctionCall -> {
                    val argnum = parent.args.indexOf(expr)
                    when (val callee = parent.target.targetStatement(program.namespace)) {
                        is Subroutine -> {
                            val paramType = callee.parameters[argnum].type
                            if(leftDt isAssignableTo paramType) {
                                val cast = TypecastExpression(expr.left, paramType, true, parent.position)
                                return listOf(IAstModification.ReplaceNode(expr.left, cast, expr))
                            }
                        }
                        is BuiltinFunctionStatementPlaceholder -> {
                            val func = BuiltinFunctions.getValue(callee.name)
                            val paramTypes = func.parameters[argnum].possibleDatatypes
                            for(type in paramTypes) {
                                if(leftDt isAssignableTo type) {
                                    val cast = TypecastExpression(expr.left, type, true, parent.position)
                                    return listOf(IAstModification.ReplaceNode(expr.left, cast, expr))
                                }
                            }
                        }
                        else -> throw FatalAstException("weird callee")
                    }
                }
                else -> return noModifications
            }
        }
        return noModifications
    }

    private fun getAutoIndexerVarFor(expr: ArrayIndexedExpression): MutableList<IAstModification> {
        val modifications = mutableListOf<IAstModification>()
        val subroutine = expr.definingSubroutine()!!
        val statement = expr.containingStatement()
        val indexerVarPrefix = "prog8_autovar_index_"
        val repo = subroutine.asmGenInfo.usedAutoArrayIndexerForStatements

        // TODO make this a bit smarter so it can reuse indexer variables. BUT BEWARE of scoping+initialization problems then
        // add another loop index var to be used for this expression
        val indexerVarName = "$indexerVarPrefix${expr.indexer.hashCode()}"
        val indexerVar = AsmGenInfo.ArrayIndexerInfo(indexerVarName, expr.indexer)
        repo.add(indexerVar)
        // create the indexer var at block level scope
        val vardecl = VarDecl(VarDeclType.VAR, DataType.UBYTE, ZeropageWish.PREFER_ZEROPAGE,
                null, indexerVarName, null, null, isArray = false, autogeneratedDontRemove = true, position = expr.position)
        modifications.add(IAstModification.InsertFirst(vardecl, subroutine))

        // replace the indexer with just the variable
        // assign the indexing expression to the helper variable, but only if that hasn't been done already
        val indexerExpression = expr.indexer.origExpression!!
        val target = AssignTarget(IdentifierReference(listOf(indexerVar.name), indexerExpression.position), null, null, indexerExpression.position)
        val assign = Assignment(target, indexerExpression, indexerExpression.position)
        modifications.add(IAstModification.InsertBefore(statement, assign, statement.definingScope()))
        modifications.add(IAstModification.SetExpression( {
            expr.indexer.indexVar = it as IdentifierReference
            expr.indexer.indexNum = null
            expr.indexer.origExpression = null
        }, target.identifier!!.copy(), expr.indexer))

        return modifications
    }

    override fun after(whenStatement: WhenStatement, parent: Node): Iterable<IAstModification> {
        val choices = whenStatement.choiceValues(program).sortedBy {
            it.first?.first() ?: Int.MAX_VALUE
        }
        whenStatement.choices.clear()
        choices.mapTo(whenStatement.choices) { it.second }
        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        val declValue = decl.value
        if(declValue!=null && decl.type== VarDeclType.VAR && decl.datatype in NumericDatatypes) {
            val declConstValue = declValue.constValue(program)
            if(declConstValue==null) {
                // move the vardecl (without value) to the scope and replace this with a regular assignment
                // Unless we're dealing with a floating point variable because that will actually make things less efficient at the moment (because floats are mostly calcualated via the stack)
                if(decl.datatype!=DataType.FLOAT) {
                    decl.value = null
                    decl.allowInitializeWithZero = false
                    val target = AssignTarget(IdentifierReference(listOf(decl.name), decl.position), null, null, decl.position)
                    val assign = Assignment(target, declValue, decl.position)
                    return listOf(
                            IAstModification.ReplaceNode(decl, assign, parent),
                            IAstModification.InsertFirst(decl, decl.definingScope())
                    )
                }
            }
        }
        return noModifications
    }

    override fun before(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        val valueType = assignment.value.inferType(program)
        val targetType = assignment.target.inferType(program)
        var assignments = emptyList<Assignment>()

        if(targetType.istype(DataType.STRUCT) && (valueType.istype(DataType.STRUCT) || valueType.typeOrElse(DataType.STRUCT) in ArrayDatatypes )) {
            assignments = if (assignment.value is ArrayLiteralValue) {
                flattenStructAssignmentFromStructLiteral(assignment)    //  'structvar = [ ..... ] '
            } else {
                flattenStructAssignmentFromIdentifier(assignment)    //   'structvar1 = structvar2'
            }
        }

        if(targetType.typeOrElse(DataType.STRUCT) in ArrayDatatypes && valueType.typeOrElse(DataType.STRUCT) in ArrayDatatypes ) {
            assignments = if (assignment.value is ArrayLiteralValue) {
                flattenArrayAssignmentFromArrayLiteral(assignment)    //  'arrayvar = [ ..... ] '
            } else {
                flattenArrayAssignmentFromIdentifier(assignment)    //   'arrayvar1 = arrayvar2'
            }
        }

        if(assignments.isNotEmpty()) {
            val modifications = mutableListOf<IAstModification>()
            val scope = assignment.definingScope()
            assignments.reversed().mapTo(modifications) { IAstModification.InsertAfter(assignment, it, scope) }
            modifications.add(IAstModification.Remove(assignment, scope))
            return modifications
        }

        return noModifications
    }

    override fun after(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        // rewrite in-place assignment expressions a bit so that the assignment target usually is the leftmost operand
        val binExpr = assignment.value as? BinaryExpression
        if(binExpr!=null) {
            if(binExpr.left isSameAs assignment.target) {
                // A = A <operator> 5, unchanged
                return noModifications
            }

            if(binExpr.operator in associativeOperators) {
                if (binExpr.right isSameAs assignment.target) {
                    // A = v <associative-operator> A  ==>  A = A <associative-operator> v
                    return listOf(IAstModification.SwapOperands(binExpr))
                }

                val leftBinExpr = binExpr.left as? BinaryExpression
                if(leftBinExpr?.operator == binExpr.operator) {
                    return if(leftBinExpr.left isSameAs assignment.target) {
                        // A = (A <associative-operator> x) <same-operator> y ==> A = A <associative-operator> (x <same-operator> y)
                        val newRight = BinaryExpression(leftBinExpr.right, binExpr.operator, binExpr.right, binExpr.position)
                        val newValue = BinaryExpression(leftBinExpr.left, binExpr.operator, newRight, binExpr.position)
                        listOf(IAstModification.ReplaceNode(binExpr, newValue, assignment))
                    } else {
                        // A = (x <associative-operator> A) <same-operator> y ==> A = A <associative-operator> (x <same-operator> y)
                        val newRight = BinaryExpression(leftBinExpr.left, binExpr.operator, binExpr.right, binExpr.position)
                        val newValue = BinaryExpression(leftBinExpr.right, binExpr.operator, newRight, binExpr.position)
                        listOf(IAstModification.ReplaceNode(binExpr, newValue, assignment))
                    }
                }
                val rightBinExpr = binExpr.right as? BinaryExpression
                if(rightBinExpr?.operator == binExpr.operator) {
                    return if(rightBinExpr.left isSameAs assignment.target) {
                        // A = x <associative-operator> (A <same-operator> y) ==> A = A <associative-operator> (x <same-operator> y)
                        val newRight = BinaryExpression(binExpr.left, binExpr.operator, rightBinExpr.right, binExpr.position)
                        val newValue = BinaryExpression(rightBinExpr.left, binExpr.operator, newRight, binExpr.position)
                        listOf(IAstModification.ReplaceNode(binExpr, newValue, assignment))
                    } else {
                        // A = x <associative-operator> (y <same-operator> A) ==> A = A <associative-operator> (x <same-operator> y)
                        val newRight = BinaryExpression(binExpr.left, binExpr.operator, rightBinExpr.left, binExpr.position)
                        val newValue = BinaryExpression(rightBinExpr.right, binExpr.operator, newRight, binExpr.position)
                        listOf(IAstModification.ReplaceNode(binExpr, newValue, assignment))
                    }
                }
            }
        }

        return noModifications
    }

    private fun flattenArrayAssignmentFromArrayLiteral(assign: Assignment): List<Assignment> {
        val identifier = assign.target.identifier!!
        val targetVar = identifier.targetVarDecl(program.namespace)!!
        val alv = assign.value as? ArrayLiteralValue
        return flattenArrayAssign(targetVar, alv, identifier, assign.position)
    }

    private fun flattenArrayAssignmentFromIdentifier(assign: Assignment): List<Assignment> {
        val identifier = assign.target.identifier!!
        val targetVar = identifier.targetVarDecl(program.namespace)!!
        val sourceIdent = assign.value as IdentifierReference
        val sourceVar = sourceIdent.targetVarDecl(program.namespace)!!
        if(!sourceVar.isArray) {
            errors.err("value must be an array",  sourceIdent.position)
            return emptyList()
        }
        val alv = sourceVar.value as? ArrayLiteralValue
        return flattenArrayAssign(targetVar, alv, identifier, assign.position)
    }

    private fun flattenArrayAssign(targetVar: VarDecl, alv: ArrayLiteralValue?, identifier: IdentifierReference, position: Position): List<Assignment> {
        if(targetVar.arraysize==null) {
            errors.err("array has no defined size", identifier.position)
            return emptyList()
        }

        if(alv==null || alv.value.size != targetVar.arraysize!!.constIndex()) {
            errors.err("element count mismatch", position)
            return emptyList()
        }

        // TODO use a pointer loop instead of individual assignments
        return alv.value.mapIndexed { index, value ->
            val idx = ArrayIndexedExpression(identifier, ArrayIndex(NumericLiteralValue(DataType.UBYTE, index, position), position), position)
            Assignment(AssignTarget(null, idx, null, position), value, value.position)
        }
    }

    private fun flattenStructAssignmentFromStructLiteral(structAssignment: Assignment): List<Assignment> {
        val identifier = structAssignment.target.identifier!!
        val identifierName = identifier.nameInSource.single()
        val targetVar = identifier.targetVarDecl(program.namespace)!!
        val struct = targetVar.struct!!

        val slv = structAssignment.value as? ArrayLiteralValue
        if(slv==null || slv.value.size != struct.numberOfElements) {
            errors.err("element count mismatch", structAssignment.position)
            return emptyList()
        }

        return struct.statements.zip(slv.value).map { (targetDecl, sourceValue) ->
            targetDecl as VarDecl
            val mangled = mangledStructMemberName(identifierName, targetDecl.name)
            val idref = IdentifierReference(listOf(mangled), structAssignment.position)
            val assign = Assignment(AssignTarget(idref, null, null, structAssignment.position),
                    sourceValue, sourceValue.position)
            assign.linkParents(structAssignment)
            assign
        }
    }

    private fun flattenStructAssignmentFromIdentifier(structAssignment: Assignment): List<Assignment> {
        // TODO use memcopy beyond a certain number of elements
        val identifier = structAssignment.target.identifier!!
        val identifierName = identifier.nameInSource.single()
        val targetVar = identifier.targetVarDecl(program.namespace)!!
        val struct = targetVar.struct!!
        when (structAssignment.value) {
            is IdentifierReference -> {
                val sourceVar = (structAssignment.value as IdentifierReference).targetVarDecl(program.namespace)!!
                when {
                    sourceVar.struct!=null -> {
                        // struct memberwise copy
                        val sourceStruct = sourceVar.struct!!
                        if(sourceStruct!==targetVar.struct) {
                            // structs are not the same in assignment
                            return listOf()     // error will be printed elsewhere
                        }
                        if(struct.statements.size!=sourceStruct.statements.size)
                            return listOf()     // error will be printed elsewhere
                        return struct.statements.zip(sourceStruct.statements).map { member ->
                            val targetDecl = member.first as VarDecl
                            val sourceDecl = member.second as VarDecl
                            if(targetDecl.name != sourceDecl.name)
                                throw FatalAstException("struct member mismatch")
                            val mangled = mangledStructMemberName(identifierName, targetDecl.name)
                            val idref = IdentifierReference(listOf(mangled), structAssignment.position)
                            val sourcemangled = mangledStructMemberName(sourceVar.name, sourceDecl.name)
                            val sourceIdref = IdentifierReference(listOf(sourcemangled), structAssignment.position)
                            val assign = Assignment(AssignTarget(idref, null, null, structAssignment.position), sourceIdref, member.second.position)
                            assign.linkParents(structAssignment)
                            assign
                        }
                    }
                    sourceVar.isArray -> {
                        val array = (sourceVar.value as ArrayLiteralValue).value
                        if(struct.statements.size!=array.size)
                            return listOf()     // error will be printed elsewhere
                        return struct.statements.zip(array).map {
                            val decl = it.first as VarDecl
                            val mangled = mangledStructMemberName(identifierName, decl.name)
                            val targetName = IdentifierReference(listOf(mangled), structAssignment.position)
                            val target = AssignTarget(targetName, null, null, structAssignment.position)
                            val assign = Assignment(target, it.second, structAssignment.position)
                            assign.linkParents(structAssignment)
                            assign
                        }
                    }
                    else -> {
                        throw FatalAstException("can only assign arrays or structs to structs")
                    }
                }
            }
            is ArrayLiteralValue -> {
                throw IllegalArgumentException("not going to flatten a structLv assignment here")
            }
            else -> throw FatalAstException("strange struct value")
        }
    }

}
