package prog8.ast.processing

import prog8.ast.*
import prog8.ast.base.DataType
import prog8.ast.base.FatalAstException
import prog8.ast.base.NumericDatatypes
import prog8.ast.base.VarDeclType
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
    // - (syntax desugaring) augmented assignment is turned into regular assignment.
    // - (syntax desugaring) struct value assignment is expanded into several struct member assignments.
    // - sorts the choices in when statement.


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
        return emptyList()
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
                    IAstModification.InsertAfter(parent.statements.last(), block, parent)
            )
        }

        reorderVardeclsAndDirectives(block.statements)
        return emptyList()
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
        reorderVardeclsAndDirectives(subroutine.statements)
        return emptyList()
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        val declValue = decl.value
        if(declValue!=null && decl.type== VarDeclType.VAR && decl.datatype in NumericDatatypes) {
            val declConstValue = declValue.constValue(program)
            if(declConstValue==null) {
                // move the vardecl (without value) to the scope and replace this with a regular assignment
                decl.value = null
                val target = AssignTarget(null, IdentifierReference(listOf(decl.name), decl.position), null, null, decl.position)
                val assign = Assignment(target, null, declValue, decl.position)
                return listOf(
                        IAstModification.ReplaceNode(decl, assign, parent),
                        IAstModification.InsertFirst(decl, decl.definingScope() as Node)
                )
            }
        }
        return emptyList()
    }

    override fun after(whenStatement: WhenStatement, parent: Node): Iterable<IAstModification> {
        val choices = whenStatement.choiceValues(program).sortedBy {
            it.first?.first() ?: Int.MAX_VALUE
        }
        whenStatement.choices.clear()
        choices.mapTo(whenStatement.choices) { it.second }
        return emptyList()
    }

    override fun before(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        if(assignment.aug_op!=null) {
            // TODO instead of desugaring augmented assignments, instead just keep them and use them for possibly more efficient code generation ?
            //      this also means that we should actually reverse this stuff below:  A = A + 5   --->   A += 5
            val leftOperand: Expression =
                    when {
                        assignment.target.register != null -> RegisterExpr(assignment.target.register!!, assignment.target.position)
                        assignment.target.identifier != null -> assignment.target.identifier!!
                        assignment.target.arrayindexed != null -> assignment.target.arrayindexed!!
                        assignment.target.memoryAddress != null -> DirectMemoryRead(assignment.target.memoryAddress!!.addressExpression, assignment.value.position)
                        else -> throw FatalAstException("strange assignment")
                    }

            val expression = BinaryExpression(leftOperand, assignment.aug_op.substringBeforeLast('='), assignment.value, assignment.position)
            val convertedAssignment = Assignment(assignment.target, null, expression, assignment.position)
            return listOf(IAstModification.ReplaceNode(assignment, convertedAssignment, parent))
        }

        val valueType = assignment.value.inferType(program)
        val targetType = assignment.target.inferType(program, assignment)
        if(valueType.istype(DataType.STRUCT) && targetType.istype(DataType.STRUCT)) {
            val assignments = if (assignment.value is StructLiteralValue) {
                flattenStructAssignmentFromStructLiteral(assignment, program)    //  'structvar = { ..... } '
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

        return emptyList()
    }

    private fun flattenStructAssignmentFromStructLiteral(structAssignment: Assignment, program: Program): List<Assignment> {
        val identifier = structAssignment.target.identifier!!
        val identifierName = identifier.nameInSource.single()
        val targetVar = identifier.targetVarDecl(program.namespace)!!
        val struct = targetVar.struct!!

        val slv = structAssignment.value as? StructLiteralValue
        if(slv==null || slv.values.size != struct.numberOfElements)
            throw FatalAstException("element count mismatch")

        return struct.statements.zip(slv.values).map { (targetDecl, sourceValue) ->
            targetDecl as VarDecl
            val mangled = mangledStructMemberName(identifierName, targetDecl.name)
            val idref = IdentifierReference(listOf(mangled), structAssignment.position)
            val assign = Assignment(AssignTarget(null, idref, null, null, structAssignment.position),
                    null, sourceValue, sourceValue.position)
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
                if (sourceVar.struct == null)
                    throw FatalAstException("can only assign arrays or structs to structs")
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
                    val assign = Assignment(AssignTarget(null, idref, null, null, structAssignment.position),
                            null, sourceIdref, member.second.position)
                    assign.linkParents(structAssignment)
                    assign
                }
            }
            is StructLiteralValue -> {
                throw IllegalArgumentException("not going to flatten a structLv assignment here")
            }
            else -> throw FatalAstException("strange struct value")
        }
    }

}
