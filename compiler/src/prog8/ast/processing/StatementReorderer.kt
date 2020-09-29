package prog8.ast.processing

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*


internal class StatementReorderer(val program: Program) : AstWalker() {
    // Reorders the statements in a way the compiler needs.
    // - 'main' block must be the very first statement UNLESS it has an address set.
    // - library blocks are put last.
    // - blocks are ordered by address, where blocks without address are placed last.
    // - in every scope, most directives and vardecls are moved to the top.
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
        return noModifications
    }


    override fun after(whenStatement: WhenStatement, parent: Node): Iterable<IAstModification> {
        val choices = whenStatement.choiceValues(program).sortedBy {
            it.first?.first() ?: Int.MAX_VALUE
        }
        whenStatement.choices.clear()
        choices.mapTo(whenStatement.choices) { it.second }
        return noModifications
    }

    override fun before(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        val valueType = assignment.value.inferType(program)
        val targetType = assignment.target.inferType(program, assignment)
        if(targetType.istype(DataType.STRUCT) && (valueType.istype(DataType.STRUCT) || valueType.typeOrElse(DataType.STRUCT) in ArrayDatatypes )) {
            val assignments = if (assignment.value is ArrayLiteralValue) {
                flattenStructAssignmentFromStructLiteral(assignment, program)    //  'structvar = [ ..... ] '
            } else {
                flattenStructAssignmentFromIdentifier(assignment, program)    //   'structvar1 = structvar2'
            }
            if(assignments.isNotEmpty()) {
                val modifications = mutableListOf<IAstModification>()
                assignments.reversed().mapTo(modifications) { IAstModification.InsertAfter(assignment, it, parent) }
                modifications.add(IAstModification.Remove(assignment, parent))
                return modifications
            }
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

    private fun flattenStructAssignmentFromStructLiteral(structAssignment: Assignment, program: Program): List<Assignment> {
        val identifier = structAssignment.target.identifier!!
        val identifierName = identifier.nameInSource.single()
        val targetVar = identifier.targetVarDecl(program.namespace)!!
        val struct = targetVar.struct!!

        val slv = structAssignment.value as? ArrayLiteralValue
        if(slv==null || slv.value.size != struct.numberOfElements)
            throw FatalAstException("element count mismatch")

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

    private fun flattenStructAssignmentFromIdentifier(structAssignment: Assignment, program: Program): List<Assignment> {
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
                        TODO("assign struct array $structAssignment")
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
