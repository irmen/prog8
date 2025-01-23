package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.AssociativeOperators
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.IErrorReporter

internal class StatementReorderer(
    val program: Program,
    val errors: IErrorReporter
) : AstWalker() {
    // Reorders the statements in a way the compiler needs.
    // - 'main' block must be the very first.
    // - blocks without address come next, after those, blocks with addresses (sorted bye ascending address)
    // - in every block and module, most directives and vardecls are moved to the top. (not in subroutines!)
    // - the 'start' subroutine is moved to the top.
    // - (syntax desugaring) a vardecl with a non-const initializer value is split into a regular vardecl and an assignment statement.
    // - in-place assignments are reordered a bit so that they are mostly of the form A = A <operator> <rest>
    // - sorts the choices in when statement.
    // - insert AddressOf (&) expression where required (string params to a UWORD function param etc.).

    private val directivesToMove = setOf("%output", "%launcher", "%zeropage", "%zpreserved", "%zpallowed", "%address", "%memtop", "%option", "%encoding")

    override fun after(module: Module, parent: Node): Iterable<IAstModification> {
        val (blocks, other) = module.statements.partition { it is Block }
        module.statements.clear()
        module.statements.addAll(other.asSequence().plus(blocks.sortedBy { (it as Block).address ?: UInt.MIN_VALUE }))
        // note: the block sort order is finalized in the second Ast, see IntermediateAstMaker

        val mainBlock = module.statements.asSequence().filterIsInstance<Block>().firstOrNull { it.name=="main" }
        if(mainBlock!=null) {
            module.statements.remove(mainBlock)
            module.statements.add(0, mainBlock)
        }

        directivesToTheTop(module.statements)
        return noModifications
    }

    private val declsProcessedWithInitAssignment = mutableSetOf<VarDecl>()

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if (decl.type == VarDeclType.VAR) {
            if(decl.dirty && decl.value!=null)
                errors.err("dirty variable can't have initialization value", decl.position)

            if (decl.datatype.isNumericOrBool) {
                if(decl !in declsProcessedWithInitAssignment) {
                    declsProcessedWithInitAssignment.add(decl)
                    if (decl.value == null) {
                        if (decl.origin==VarDeclOrigin.USERCODE && decl.allowInitializeWithZero) {
                            if(decl.dirty) {
                                // no initialization at all!
                                return noModifications
                            }
                            // A numeric vardecl without an initial value is initialized with zero,
                            // unless there's already an assignment below it, that initializes the value (or a for loop that uses it as loopvar).
                            // This allows you to restart the program and have the same starting values of the variables
                            // So basically consider 'ubyte xx' as a short form for 'ubyte xx; xx=0'
                            decl.value = null
                            val canskip = canSkipInitializationWith0(decl)
                            if (!canskip) {
                                // Add assignment to initialize with zero
                                // Note: for block-level vars, this will introduce assignments in the block scope. These have to be dealt with correctly later.
                                val identifier = IdentifierReference(listOf(decl.name), decl.position)
                                val assignzero = Assignment(AssignTarget(identifier, null, null, null, false, decl.position),
                                    decl.zeroElementValue(), AssignmentOrigin.VARINIT, decl.position)
                                return listOf(IAstModification.InsertAfter(
                                    decl, assignzero, parent as IStatementContainer
                                ))
                            }
                        }
                    } else {
                        // Transform the vardecl with initvalue to a plain vardecl + assignment
                        // this allows for other optimizations to kick in.
                        // So basically consider 'ubyte xx=99' as a short form for 'ubyte xx; xx=99'
                        val pos = decl.value!!.position
                        val identifier = IdentifierReference(listOf(decl.name), pos)
                        val assign = Assignment(AssignTarget(identifier, null, null, null, false, pos),
                            decl.value!!, AssignmentOrigin.VARINIT, pos)
                        decl.value = null
                        return listOf(IAstModification.InsertAfter(
                            decl, assign, parent as IStatementContainer
                        ))
                    }
                }
            }
            else if(decl.isArray) {
                // only if the initializer expression is a reference to another array, split it into a separate assignment.
                // this is so that it later can be changed into a memcopy.
                // (that code only triggers on regular assignment, not on variable initializers)
                val ident = decl.value as? IdentifierReference
                if(ident!=null) {
                    val target = ident.targetVarDecl(program)
                    if(target!=null && target.isArray) {
                        val pos = decl.value!!.position
                        val identifier = IdentifierReference(listOf(decl.name), pos)
                        val assign = Assignment(AssignTarget(identifier, null, null, null, false, pos),
                            decl.value!!, AssignmentOrigin.VARINIT, pos)
                        decl.value = null
                        return listOf(IAstModification.InsertAfter(
                            decl, assign, parent as IStatementContainer
                        ))
                    }
                }
            }
        }

        return noModifications
    }

    private fun canSkipInitializationWith0(decl: VarDecl): Boolean {
        // if there is an assignment to the variable below it (regular assign, or For loop),
        // and there is nothing important in between, we can skip the initialization.
        val statements = (decl.parent as? IStatementContainer)?.statements ?: return false
        val following = statements.asSequence().dropWhile { it!==decl }.drop(1)
        for(stmt in following) {
            when(stmt) {
                is Assignment -> {
                    if (!stmt.isAugmentable) {
                        var assignTargets = stmt.target.multi?.mapNotNull { it.identifier?.targetVarDecl(program) }
                        if(assignTargets!=null) {
                            if(decl in assignTargets) {
                                stmt.origin = AssignmentOrigin.VARINIT
                                return true
                            }
                        } else {
                            val assignTgt = stmt.target.identifier?.targetVarDecl(program)
                            if (assignTgt == decl) {
                                stmt.origin = AssignmentOrigin.VARINIT
                                return true
                            }
                        }
                    }
                    return false
                }

                is ChainedAssignment -> {
                    var chained: ChainedAssignment? = stmt
                    while(chained!=null) {
                        val assignTgt = chained.target.identifier?.targetVarDecl(program)
                        if (assignTgt == decl)
                            return true
                        if(chained.nested is Assignment) {
                            if ((chained.nested as Assignment).target.identifier?.targetVarDecl(program) == decl) {
                                (chained.nested as Assignment).origin = AssignmentOrigin.VARINIT
                                return true
                            }
                        }
                        chained = chained.nested as? ChainedAssignment
                    }
                }

                is ForLoop -> return stmt.loopVar.nameInSource == listOf(decl.name)

                is IFunctionCall,
                is Jump,
                is Break,
                is BuiltinFunctionPlaceholder,
                is ConditionalBranch,
                is Continue,
                is Return,
                is Subroutine,
                is InlineAssembly,
                is Block,
                is AnonymousScope,
                is IfElse,
                is RepeatLoop,
                is UnrollLoop,
                is UntilLoop,
                is When,
                is WhileLoop -> return false
                else -> {}
            }
        }
        return false
    }

    private fun directivesToTheTop(statements: MutableList<Statement>) {
        val directives = statements.filterIsInstance<Directive>().filter {it.directive in directivesToMove}
        statements.removeAll(directives.toSet())
        statements.addAll(0, directives)
    }

    override fun before(block: Block, parent: Node): Iterable<IAstModification> {
        directivesToTheTop(block.statements)
        return noModifications
    }

    override fun before(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        val modifications = mutableListOf<IAstModification>()

        val subs = subroutine.statements.filterIsInstance<Subroutine>()
        if(subs.isNotEmpty()) {
            // all subroutines defined within this subroutine are moved to the end
            // NOTE: this doesn't check if this has already been done!!!
            modifications +=
                subs.map { IAstModification.Remove(it, subroutine) } +
                subs.map { IAstModification.InsertLast(it, subroutine) }
        }

        // change 'str' and 'ubyte[]' parameters into 'uword' (just treat it as an address)
        val stringParams = subroutine.parameters.filter { it.type.isString || it.type.isUnsignedByteArray }
        val parameterChanges = stringParams.map {
            val uwordParam = SubroutineParameter(it.name, DataType.forDt(BaseDataType.UWORD), it.zp, it.registerOrPair, it.position)
            IAstModification.ReplaceNode(it, uwordParam, subroutine)
        }
        // change 'str' and 'ubyte[]' return types into 'uword' (just treat it as an address)
        subroutine.returntypes.withIndex().forEach { (index, type) ->
            if(type.isString || type.isUnsignedByteArray)
                subroutine.returntypes[index] = DataType.forDt(BaseDataType.UWORD)
        }

        val varsChanges = mutableListOf<IAstModification>()
        if(!subroutine.isAsmSubroutine) {
            val stringParamsByNames = stringParams.associateBy { it.name }
            varsChanges +=
                if(stringParamsByNames.isNotEmpty()) {
                    subroutine.statements
                        .asSequence()
                        .filterIsInstance<VarDecl>()
                        .filter { it.origin==VarDeclOrigin.SUBROUTINEPARAM && it.name in stringParamsByNames }
                        .map {
                            val newvar = VarDecl(it.type, it.origin, DataType.forDt(BaseDataType.UWORD),
                                it.zeropage,
                                it.splitwordarray,
                                null,
                                it.name,
                                emptyList(),
                                null,
                                it.sharedWithAsm,
                                it.alignment,
                                it.dirty,
                                it.position
                            )
                            IAstModification.ReplaceNode(it, newvar, subroutine)
                        }
                }
                else emptySequence()
        }

        return modifications + parameterChanges + varsChanges
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        // simplething <associative> X -> X <associative> simplething
        // (this should be done by the ExpressionSimplifier when optimizing is enabled,
        //  but the current assembly code generator for IF statements now also depends on it, so we do it here regardless of optimization.)
        if(expr.operator in AssociativeOperators) {
            if(expr.left is IdentifierReference || expr.left is NumericLiteral || expr.left is DirectMemoryRead || (expr.left as? ArrayIndexedExpression)?.indexer?.constIndex()!=null) {
                if(expr.right !is IdentifierReference && expr.right !is NumericLiteral && expr.right !is DirectMemoryRead) {
                    if(maySwapOperandOrder(expr)) {
                        return listOf(IAstModification.SwapOperands(expr))
                    }
                }
            }
        }
        return noModifications
    }

    override fun after(whenStmt: When, parent: Node): Iterable<IAstModification> {
        val lastChoiceValues = whenStmt.choices.lastOrNull()?.values
        if(lastChoiceValues?.isNotEmpty()==true) {
            val elseChoice = whenStmt.choices.indexOfFirst { it.values==null || it.values?.isEmpty()==true }
            if(elseChoice>=0)
                errors.err("else choice must be the last one", whenStmt.choices[elseChoice].position)
        }

        return noModifications
    }

    override fun before(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        val valueType = assignment.value.inferType(program)
        val targetType = assignment.target.inferType(program)

        if(targetType.isArray && valueType.isArray) {
            checkCopyArrayValue(assignment)
        }

        if(!assignment.isAugmentable) {
            if (valueType issimpletype BaseDataType.STR && targetType issimpletype BaseDataType.STR) {
                // replace string assignment by a call to stringcopy
                return copyStringValue(assignment)
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

            if(binExpr.operator in AssociativeOperators && maySwapOperandOrder(binExpr)) {
                if (binExpr.right isSameAs assignment.target) {
                    // A = v <associative-operator> A  ==>  A = A <associative-operator> v
                    return listOf(IAstModification.SwapOperands(binExpr))
                }
            }
        }

        return noModifications
    }

    override fun after(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> {
        if(whileLoop.body.isEmpty()) {
            // convert   while C {}   to   do {} until not C    (because codegen of the latter is more optimized)
            val invertedCondition = invertCondition(whileLoop.condition, program)
            val until = UntilLoop(whileLoop.body, invertedCondition, whileLoop.position)
            return listOf(IAstModification.ReplaceNode(whileLoop, until, parent))
        }

        return noModifications
    }

    private fun checkCopyArrayValue(assign: Assignment) {
        val identifier = assign.target.identifier!!
        val targetVar = identifier.targetVarDecl(program)!!

        if(targetVar.arraysize==null) {
            errors.err("array has no defined size", assign.position)
            return
        }

        if(assign.value is ArrayLiteral) {
            return  // invalid assignment of literals will be reported elsewhere
        }
        if(assign.value !is IdentifierReference) {
            return  // invalid assignment value will be reported elsewhere
        }

        val sourceIdent = assign.value as IdentifierReference
        val sourceVar = sourceIdent.targetVarDecl(program)!!
        if(!sourceVar.isArray) {
            errors.err("value must be an array", sourceIdent.position)
        } else {
            if (sourceVar.arraysize!!.constIndex() != targetVar.arraysize!!.constIndex())
                errors.err("array size mismatch (expecting ${targetVar.arraysize!!.constIndex()}, got ${sourceVar.arraysize!!.constIndex()})", assign.value.position)
            val sourceEltDt = sourceVar.datatype.elementType()
            val targetEltDt = targetVar.datatype.elementType()
            if (!sourceEltDt.equalsSize(targetEltDt)) {
                errors.err("element size mismatch", assign.position)
            }
        }
    }

    private fun copyStringValue(assign: Assignment): List<IAstModification> {
        val identifier = assign.target.identifier!!
        val strcopy = FunctionCallStatement(IdentifierReference(listOf("sys", "internal_stringcopy"), assign.position),
            mutableListOf(
                assign.value as? IdentifierReference ?: assign.value,
                identifier
            ),
            false,
            assign.position
        )
        return listOf(IAstModification.ReplaceNode(assign, strcopy, assign.parent))
    }
}
