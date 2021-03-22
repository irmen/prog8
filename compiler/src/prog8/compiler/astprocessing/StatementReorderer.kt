package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compiler.IErrorReporter
import prog8.compiler.functions.BuiltinFunctions


internal class StatementReorderer(val program: Program, val errors: IErrorReporter) : AstWalker() {
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

        val arrayVar = arrayIndexedExpression.arrayvar.targetVarDecl(program)
        if(arrayVar!=null && arrayVar.datatype ==  DataType.UWORD) {
            // rewrite   pointervar[index]  into  @(pointervar+index)
            val indexer = arrayIndexedExpression.indexer
            val add = BinaryExpression(arrayIndexedExpression.arrayvar, "+", indexer.indexExpr, arrayIndexedExpression.position)
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

        return noModifications
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
                    when (val callee = parent.target.targetStatement(program)) {
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
        else if(expr.operator in logicalOperators) {
            // make sure that logical expressions like "var and other-logical-expression
            // is rewritten as "var!=0 and other-logical-expression", to avoid bitwise boolean and
            // generating the wrong results later

            fun wrapped(expr: Expression): Expression =
                BinaryExpression(expr, "!=", NumericLiteralValue(DataType.UBYTE, 0, expr.position), expr.position)

            fun isLogicalExpr(expr: Expression?): Boolean {
                if(expr is BinaryExpression && expr.operator in (logicalOperators + comparisonOperators))
                    return true
                if(expr is PrefixExpression && expr.operator in logicalOperators)
                    return true
                return false
            }

            return if(isLogicalExpr(expr.left)) {
                if(isLogicalExpr(expr.right))
                    noModifications
                else
                    listOf(IAstModification.ReplaceNode(expr.right, wrapped(expr.right), expr))
            } else {
                if(isLogicalExpr(expr.right))
                    listOf(IAstModification.ReplaceNode(expr.left, wrapped(expr.left), expr))
                else {
                    listOf(
                        IAstModification.ReplaceNode(expr.left, wrapped(expr.left), expr),
                        IAstModification.ReplaceNode(expr.right, wrapped(expr.right), expr)
                    )
                }
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

        if(targetType.istype(DataType.STRUCT) && (valueType.istype(DataType.STRUCT) || valueType.typeOrElse(DataType.STRUCT) in ArrayDatatypes )) {
            if (assignment.value is ArrayLiteralValue) {
                errors.err("cannot assign non-const array value, use separate assignment per field", assignment.position)
            } else {
                return copyStructValue(assignment)
            }
        }

        if(targetType.typeOrElse(DataType.STRUCT) in ArrayDatatypes && valueType.typeOrElse(DataType.STRUCT) in ArrayDatatypes ) {
            if (assignment.value is ArrayLiteralValue) {
                errors.err("cannot assign non-const array value, use separate assignment per element", assignment.position)
            } else {
                return copyArrayValue(assignment)
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

    private fun copyArrayValue(assign: Assignment): List<IAstModification> {
        val identifier = assign.target.identifier!!
        val targetVar = identifier.targetVarDecl(program)!!

        if(targetVar.arraysize==null)
            errors.err("array has no defined size", assign.position)

        val sourceIdent = assign.value as IdentifierReference
        val sourceVar = sourceIdent.targetVarDecl(program)!!
        if(!sourceVar.isArray) {
            errors.err("value must be an array", sourceIdent.position)
        } else {
            if (sourceVar.arraysize!!.constIndex() != targetVar.arraysize!!.constIndex())
                errors.err("element count mismatch", assign.position)
            if (sourceVar.datatype != targetVar.datatype)
                errors.err("element type mismatch", assign.position)
        }

        if(!errors.isEmpty())
            return emptyList()

        val memcopy = FunctionCallStatement(IdentifierReference(listOf("sys", "memcopy"), assign.position),
            mutableListOf(
                AddressOf(sourceIdent, assign.position),
                AddressOf(identifier, assign.position),
                NumericLiteralValue.optimalInteger(targetVar.arraysize!!.constIndex()!!, assign.position)
            ),
            true,
            assign.position
        )
        return listOf(IAstModification.ReplaceNode(assign, memcopy, assign.parent))
    }

    private fun copyStructValue(structAssignment: Assignment): List<IAstModification> {
        val identifier = structAssignment.target.identifier!!
        val targetVar = identifier.targetVarDecl(program)!!
        val struct = targetVar.struct!!
        when (structAssignment.value) {
            is IdentifierReference -> {
                val sourceVar = (structAssignment.value as IdentifierReference).targetVarDecl(program)!!
                val memsize = struct.memsize(program.memsizer)
                when {
                    sourceVar.struct!=null -> {
                        // struct memberwise copy
                        val sourceStruct = sourceVar.struct!!
                        if(sourceStruct!==targetVar.struct) {
                            errors.err("struct type mismatch", structAssignment.position)
                            return listOf()
                        }
                        if(struct.statements.size!=sourceStruct.statements.size) {
                            errors.err("struct element count mismatch", structAssignment.position)
                            return listOf()
                        }
                        if(memsize!=sourceStruct.memsize(program.memsizer)) {
                            errors.err("memory size mismatch", structAssignment.position)
                            return listOf()
                        }
                        val memcopy = FunctionCallStatement(IdentifierReference(listOf("sys", "memcopy"), structAssignment.position),
                            mutableListOf(
                                AddressOf(structAssignment.value as IdentifierReference, structAssignment.position),
                                AddressOf(identifier, structAssignment.position),
                                NumericLiteralValue.optimalInteger(memsize, structAssignment.position)
                            ),
                            true,
                            structAssignment.position
                        )
                        return listOf(IAstModification.ReplaceNode(structAssignment, memcopy, structAssignment.parent))
                    }
                    sourceVar.isArray -> {
                        val array = sourceVar.value as ArrayLiteralValue
                        if(struct.statements.size!=array.value.size) {
                            errors.err("struct element count mismatch", structAssignment.position)
                            return listOf()
                        }
                        if(memsize!=array.memsize(program.memsizer)) {
                            errors.err("memory size mismatch", structAssignment.position)
                            return listOf()
                        }
                        val memcopy = FunctionCallStatement(IdentifierReference(listOf("sys", "memcopy"), structAssignment.position),
                            mutableListOf(
                                AddressOf(structAssignment.value as IdentifierReference, structAssignment.position),
                                AddressOf(identifier, structAssignment.position),
                                NumericLiteralValue.optimalInteger(memsize, structAssignment.position)
                            ),
                            true,
                            structAssignment.position
                        )
                        return listOf(IAstModification.ReplaceNode(structAssignment, memcopy, structAssignment.parent))
                    }
                    else -> {
                        throw FatalAstException("can only assign arrays or structs to structs")
                    }
                }
            }
            is ArrayLiteralValue -> {
                throw IllegalArgumentException("not going to do a structLv assignment here")
            }
            else -> throw FatalAstException("strange struct value")
        }
    }

}
