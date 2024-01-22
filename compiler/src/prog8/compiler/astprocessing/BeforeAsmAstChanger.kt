package prog8.compiler.astprocessing

import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*
import prog8.code.target.VMTarget

internal class BeforeAsmAstChanger(val program: Program, private val options: CompilationOptions) : AstWalker() {

    override fun before(breakStmt: Break, parent: Node): Iterable<IAstModification> {
        throw InternalCompilerException("break should have been replaced by goto $breakStmt")
    }

    override fun before(whileLoop: WhileLoop, parent: Node): Iterable<IAstModification> {
        throw InternalCompilerException("while should have been converted to jumps")
    }

    override fun before(untilLoop: UntilLoop, parent: Node): Iterable<IAstModification> {
        throw InternalCompilerException("do..until should have been converted to jumps")
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if (decl.type == VarDeclType.VAR && decl.value != null && (decl.datatype in NumericDatatypes || decl.datatype==DataType.BOOL))
            throw InternalCompilerException("vardecls for variables, with initial numerical value, should have been rewritten as plain vardecl + assignment $decl")

        return noModifications
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<IAstModification> {
        if(scope.statements.any { it is VarDecl || it is IStatementContainer })
            throw InternalCompilerException("anonymousscope may no longer contain any vardecls or subscopes")
        return noModifications
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        // Most code generation targets only support subroutine inlining on asmsub subroutines
        // So we reset the flag here to be sure it doesn't cause problems down the line in the codegen.
        if(!subroutine.isAsmSubroutine && options.compTarget.name!=VMTarget.NAME)
            subroutine.inline = false

        val mods = mutableListOf<IAstModification>()

        // add the implicit return statement at the end (if it's not there yet), but only if it's not a kernal routine.
        // and if an assembly block doesn't contain a rts/rti.
        if (!subroutine.isAsmSubroutine) {
            if(subroutine.isEmpty()) {
                val returnStmt = Return(null, subroutine.position)
                mods += IAstModification.InsertLast(returnStmt, subroutine)
            } else {
                val last = subroutine.statements.last()
                if((last !is InlineAssembly || !last.hasReturnOrRts()) && last !is Return) {
                    val lastStatement = subroutine.statements.reversed().firstOrNull { it !is Subroutine }
                    if(lastStatement !is Return) {
                        val returnStmt = Return(null, subroutine.position)
                        mods += IAstModification.InsertLast(returnStmt, subroutine)
                    }
                }
            }
        }

        // precede a subroutine with a return to avoid falling through into the subroutine from code above it
        val outerScope = subroutine.definingScope
        val outerStatements = outerScope.statements
        val subroutineStmtIdx = outerStatements.indexOf(subroutine)
        if (subroutineStmtIdx > 0) {
            val prevStmt = outerStatements[subroutineStmtIdx-1]
            if(outerScope !is Block
                && (prevStmt !is Jump)
                && prevStmt !is Subroutine
                && prevStmt !is Return
            ) {
                val returnStmt = Return(null, subroutine.position)
                mods += IAstModification.InsertAfter(outerStatements[subroutineStmtIdx - 1], returnStmt, outerScope)
            }
        }

        if (!subroutine.inline) {
            if (subroutine.isAsmSubroutine && subroutine.asmAddress==null && !subroutine.hasRtsInAsm()) {
                // make sure the NOT INLINED asm subroutine actually has a rts at the end
                // (non-asm routines get a Return statement as needed, above)
                mods += if(options.compTarget.name==VMTarget.NAME)
                    IAstModification.InsertLast(InlineAssembly("  return\n", true, Position.DUMMY), subroutine)
                else
                    IAstModification.InsertLast(InlineAssembly("  rts\n", false, Position.DUMMY), subroutine)
            }
        }

        if(subroutine.isNotEmpty() && subroutine.statements.last() is Return) {
            // maybe the last return can be removed because there is a fall-through prevention above it
            val lastStatementBefore = subroutine.statements.reversed().drop(1).firstOrNull { it !is Subroutine }
            if(lastStatementBefore is Return) {
                mods += IAstModification.Remove(subroutine.statements.last(), subroutine)
            }
        }

        return mods
    }

    override fun after(ifElse: IfElse, parent: Node): Iterable<IAstModification> {
        val binExpr = ifElse.condition as? BinaryExpression
        if(binExpr!=null) {
        if(binExpr.operator !in ComparisonOperators) {
            val constRight = binExpr.right.constValue(program)
            if(constRight!=null) {
                if (binExpr.operator == "+") {
                    // if x+5  ->  if x != -5
                    val number = NumericLiteral(constRight.type, -constRight.number, constRight.position)
                    val booleanExpr = BinaryExpression(binExpr.left,"!=", number, ifElse.condition.position)
                    return listOf(IAstModification.ReplaceNode(ifElse.condition, booleanExpr, ifElse))
                }
                else if (binExpr.operator == "-") {
                    // if x-5  ->  if x != 5
                    val number = NumericLiteral(constRight.type, constRight.number, constRight.position)
                    val booleanExpr = BinaryExpression(binExpr.left,"!=", number, ifElse.condition.position)
                    return listOf(IAstModification.ReplaceNode(ifElse.condition, booleanExpr, ifElse))
                }
            }
        }

        if((binExpr.left as? NumericLiteral)?.number==0.0 &&
            (binExpr.right as? NumericLiteral)?.number!=0.0)
            throw InternalCompilerException("0==X should be just X")

        }

        return noModifications
    }
}
