package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.ArrayLiteral
import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.StringLiteral
import prog8.ast.statements.Assignment
import prog8.ast.statements.VarDecl
import prog8.ast.statements.WhenChoice
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.DataType
import prog8.code.core.IErrorReporter
import prog8.code.core.NumericDatatypesWithBoolean
import prog8.code.core.SplitWordArrayTypes


internal class LiteralsToAutoVars(private val program: Program, private val errors: IErrorReporter) : AstWalker() {

    override fun after(string: StringLiteral, parent: Node): Iterable<IAstModification> {
        if(string.parent !is VarDecl && string.parent !is WhenChoice) {
            val binExpr = string.parent as? BinaryExpression
            if(binExpr!=null &&(binExpr.operator=="+" || binExpr.operator=="*"))
                return noModifications // allow string concatenation or repeats later, based on just string literals

            // replace the literal string by an identifier reference to the interned string
            val parentFunc = (string.parent as? IFunctionCall)?.target
            if(parentFunc!=null) {
                if(parentFunc.nameInSource.size==1 && parentFunc.nameInSource[0]=="memory") {
                    // memory() builtin function just uses the string as a label name
                    return noModifications
                }
            }
            val scopedName = program.internString(string)
            val identifier = IdentifierReference(scopedName, string.position)
            return listOf(IAstModification.ReplaceNode(string, identifier, parent))
        }
        return noModifications
    }

    override fun after(array: ArrayLiteral, parent: Node): Iterable<IAstModification> {
        val vardecl = array.parent as? VarDecl
        if(vardecl!=null) {
            // adjust the datatype of the array (to an educated guess from the vardecl type)
            val arrayDt = array.type
            if(arrayDt isnot vardecl.datatype) {
                val cast = array.cast(vardecl.datatype)
                if(cast!=null && cast !== array)
                    return listOf(IAstModification.ReplaceNode(vardecl.value!!, cast, vardecl))
            }
        } else {
            val arrayDt = array.guessDatatype(program)
            if(arrayDt.isKnown) {
                val parentAssign = parent as? Assignment
                val targetDt = parentAssign?.target?.inferType(program) ?: arrayDt
                // turn the array literal it into an identifier reference
                val litval2 = array.cast(targetDt.getOr(DataType.UNDEFINED))
                if(litval2!=null) {
                    val vardecl2 = VarDecl.createAuto(litval2, targetDt.getOr(DataType.UNDEFINED) in SplitWordArrayTypes)
                    val identifier = IdentifierReference(listOf(vardecl2.name), vardecl2.position)
                    return listOf(
                        IAstModification.ReplaceNode(array, identifier, parent),
                        IAstModification.InsertFirst(vardecl2, array.definingScope)
                    )
                }
            }
        }
        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if(decl.names.size>1) {
            // note: the desugaring of a multi-variable vardecl has to be done here
            // and not in CodeDesugarer, that one is too late (identifiers can't be found otherwise)
            if(decl.datatype !in NumericDatatypesWithBoolean)
                errors.err("can only multi declare numeric and boolean variables", decl.position)
            if(errors.noErrors()) {
                // desugar into individual vardecl per name.
                return decl.desugarMultiDecl().map {
                    IAstModification.InsertBefore(decl, it, parent as IStatementContainer)
                } + IAstModification.Remove(decl, parent as IStatementContainer)
            }
        }
        return noModifications
    }
}
