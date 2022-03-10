package prog8.compiler.astprocessing

import prog8.ast.IPipe
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.SyntaxError
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.Encoding
import prog8.code.core.NumericDatatypes
import prog8.compilerinterface.ICompilationTarget
import prog8.compilerinterface.IErrorReporter
import prog8.compilerinterface.InternalCompilerException


class AstPreprocessor(val program: Program, val errors: IErrorReporter, val compTarget: ICompilationTarget) : AstWalker() {

    override fun before(char: CharLiteral, parent: Node): Iterable<IAstModification> {
        if(char.encoding== Encoding.DEFAULT)
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

    override fun before(pipe: Pipe, parent: Node): Iterable<IAstModification> {
        if(pipe.source is PipeExpression) {
            // correct Antlr parse tree quirk: turn nested pipe into single flat pipe
            val psrc = pipe.source as PipeExpression
            val newSource = psrc.source
            val newSegments = psrc.segments
            newSegments += pipe.segments.single()
            return listOf(IAstModification.ReplaceNode(pipe as Node, Pipe(newSource, newSegments, pipe.position), parent))
        }

        return process(pipe, parent)
    }

    override fun before(pipeExpr: PipeExpression, parent: Node): Iterable<IAstModification> {
        if(pipeExpr.source is PipeExpression) {
            // correct Antlr parse tree quirk; turn nested pipe into single flat pipe
            val psrc = pipeExpr.source as PipeExpression
            val newSource = psrc.source
            val newSegments = psrc.segments
            newSegments += pipeExpr.segments.single()
            return listOf(IAstModification.ReplaceNode(pipeExpr as Node, PipeExpression(newSource, newSegments, pipeExpr.position), parent))
        }

        return process(pipeExpr, parent)
    }

    private fun process(pipe: IPipe, parent: Node): Iterable<IAstModification> {
        if(pipe.source is IPipe)
            throw InternalCompilerException("pipe source should have been adjusted to be a normal expression")

        return noModifications

// TODO don't use artifical inserted args, fix the places that check for arg numbers instead.
        // add the "missing" first argument to each function call in the pipe segments
        // so that all function call related checks just pass
        // might have to remove it again when entering code generation pass, or just replace it there
        // with the proper output value of the previous pipe segment.

//        val mutations = mutableListOf<IAstModification>()
//        var valueDt = pipe.source.inferType(program).getOrElse { throw FatalAstException("invalid dt") }
//        pipe.segments.forEach { call->
//            val dummyFirstArg = when (valueDt) {
//                DataType.UBYTE -> FunctionCallExpression(IdentifierReference(listOf("rnd"), pipe.position), mutableListOf(), pipe.position)
//                DataType.UWORD -> FunctionCallExpression(IdentifierReference(listOf("rndw"), pipe.position), mutableListOf(), pipe.position)
//                DataType.BYTE, DataType.WORD -> IdentifierReference(
//                    getTempRegisterName(InferredTypes.InferredType.known(valueDt)),
//                    pipe.position
//                ) // there's no builtin function we can abuse that returns a signed byte or word type   // TODO maybe use a typecasted expression around rnd?
//                DataType.FLOAT -> FunctionCallExpression(IdentifierReference(listOf("rndf"), pipe.position), mutableListOf(), pipe.position)
//                else -> throw FatalAstException("invalid dt")
//            }
//
//            mutations += IAstModification.SetExpression(
//                { newexpr -> call.args.add(0, newexpr) },
//                dummyFirstArg, parent
//            )
//
//            if(call!==pipe.segments.last())
//                valueDt = call.inferType(program).getOrElse { throw FatalAstException("invalid dt") }
//        }
//        return mutations

    }
}
