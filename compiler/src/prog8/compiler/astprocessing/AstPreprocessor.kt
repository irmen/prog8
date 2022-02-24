package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compilerinterface.BuiltinFunctions
import prog8.compilerinterface.Encoding
import prog8.compilerinterface.ICompilationTarget
import prog8.compilerinterface.IErrorReporter


class AstPreprocessor(val program: Program, val errors: IErrorReporter, val compTarget: ICompilationTarget) : AstWalker() {

    override fun before(char: CharLiteral, parent: Node): Iterable<IAstModification> {
        if(char.encoding==Encoding.DEFAULT)
            char.encoding = compTarget.defaultEncoding
        return noModifications
    }

    override fun before(string: StringLiteral, parent: Node): Iterable<IAstModification> {
        if(string.encoding==Encoding.DEFAULT)
            string.encoding = compTarget.defaultEncoding
        return super.before(string, parent)
    }

    override fun after(range: RangeExpression, parent: Node): Iterable<IAstModification> {
        // has to be done before the constant folding, otherwise certain checks there will fail on invalid range sizes
        val modifications = mutableListOf<IAstModification>()
        if(range.from !is NumericLiteral) {
            try {
                val constval = range.from.constValue(program)
                if (constval != null)
                    modifications += IAstModification.ReplaceNode(range.from, constval, range)
            } catch (x: SyntaxError) {
                // syntax errors will be reported later
            }
        }
        if(range.to !is NumericLiteral) {
            try {
                val constval = range.to.constValue(program)
                if(constval!=null)
                    modifications += IAstModification.ReplaceNode(range.to, constval, range)
            } catch (x: SyntaxError) {
                // syntax errors will be reported later
            }
        }
        if(range.step !is NumericLiteral) {
            try {
                val constval = range.step.constValue(program)
                if(constval!=null)
                    modifications += IAstModification.ReplaceNode(range.step, constval, range)
            } catch (x: SyntaxError) {
                // syntax errors will be reported later
            }
        }
        return modifications
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {

        // move vardecls in Anonymous scope up to the containing subroutine
        // and add initialization assignment in its place if needed
        val vars = scope.statements.asSequence().filterIsInstance<VarDecl>()
        val parentscope = scope.definingScope
        if(vars.any() && parentscope !== parent) {
            val movements = mutableListOf<IAstModification>()
            val replacements = mutableListOf<IAstModification>()

            for(decl in vars) {
                if(decl.type != VarDeclType.VAR) {
                    movements.add(IAstModification.InsertFirst(decl, parentscope))
                    replacements.add(IAstModification.Remove(decl, scope))
                } else {
                    if(decl.value!=null && decl.datatype in NumericDatatypes) {
                        val target = AssignTarget(IdentifierReference(listOf(decl.name), decl.position), null, null, decl.position)
                        val assign = Assignment(target, decl.value!!, AssignmentOrigin.VARINIT, decl.position)
                        replacements.add(IAstModification.ReplaceNode(decl, assign, scope))
                        decl.value = null
                        decl.allowInitializeWithZero = false
                    } else {
                        replacements.add(IAstModification.Remove(decl, scope))
                    }
                    movements.add(IAstModification.InsertFirst(decl, parentscope))
                }
            }
            return movements + replacements
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        // this has to be done here becuse otherwise the string / range literal values will have been replaced by variables
        if(expr.operator=="in") {
            val containment = ContainmentCheck(expr.left, expr.right, expr.position)
            return listOf(IAstModification.ReplaceNode(expr, containment, parent))
        }
        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        val nextAssignment = decl.nextSibling() as? Assignment
        if(nextAssignment!=null && nextAssignment.origin!=AssignmentOrigin.VARINIT) {
            // check if it's a proper initializer assignment for the variable
            if(decl.value==null && nextAssignment.target.identifier?.targetVarDecl(program)===decl) {
                if(!nextAssignment.value.referencesIdentifier(nextAssignment.target.identifier!!.nameInSource))
                    nextAssignment.origin = AssignmentOrigin.VARINIT
            }
        }
        return noModifications
    }

    override fun after(pipe: Pipe, parent: Node): Iterable<IAstModification> {
        return process(pipe, parent)
    }

    override fun after(pipeExpr: PipeExpression, parent: Node): Iterable<IAstModification> {
        return process(pipeExpr, parent)
    }

    private fun process(pipe: IPipe, parent: Node): Iterable<IAstModification> {
        // add the "missing" first argument to each function call in the pipe segments
        // so that all function call related checks just pass
        // might have to remove it again when entering code generation pass, or just replace it there
        // with the proper output value of the previous pipe segment.
        return pipe.segments.map {
            val firstArgDt = when (val target = it.target.targetStatement(program)) {
                is Subroutine -> target.parameters.first().type
                is BuiltinFunctionPlaceholder -> BuiltinFunctions.getValue(target.name).parameters.first().possibleDatatypes.first()
                else -> DataType.UNDEFINED
            }
            val dummyFirstArg = when (firstArgDt) {
                in IntegerDatatypes -> {
                    IdentifierReference(
                        getTempRegisterName(InferredTypes.InferredType.known(firstArgDt)),
                        pipe.position
                    )
                }
                DataType.FLOAT -> {
                    val (name, _) = program.getTempVar(DataType.FLOAT)
                    IdentifierReference(name, pipe.position)
                }
                else -> throw FatalAstException("weird dt")
            }

            IAstModification.SetExpression(
                { newexpr -> it.args.add(0, newexpr) },
                dummyFirstArg, parent
            )
        }
    }
}
