package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.Assignment
import prog8.ast.statements.VarDecl
import prog8.ast.statements.WhenChoice
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.ast.PtContainmentCheck
import prog8.code.core.*


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
            val elementDt = ArrayToElementTypes.getValue(arrayDt.getOr(DataType.UNDEFINED))
            val maxSize = when(elementDt) {
                in ByteDatatypesWithBoolean -> PtContainmentCheck.MAX_SIZE_FOR_INLINE_CHECKS_BYTE
                in WordDatatypes -> PtContainmentCheck.MAX_SIZE_FOR_INLINE_CHECKS_WORD
                else -> 0
            }
            if(parent is ContainmentCheck && array.value.size <= maxSize) {
                // keep the array in the containmentcheck inline
                return noModifications
            }
            if(arrayDt.isKnown) {
                if((array.parent as? BinaryExpression)?.operator!="*") {
                    val parentAssign = parent as? Assignment
                    val targetDt = parentAssign?.target?.inferType(program) ?: arrayDt
                    // turn the array literal it into an identifier reference
                    val litval2 = array.cast(targetDt.getOr(DataType.UNDEFINED))
                    if (litval2 != null) {
                        val vardecl2 = VarDecl.createAuto(litval2, targetDt.getOr(DataType.UNDEFINED) in SplitWordArrayTypes)
                        val identifier = IdentifierReference(listOf(vardecl2.name), vardecl2.position)
                        return listOf(
                            IAstModification.ReplaceNode(array, identifier, parent),
                            IAstModification.InsertFirst(vardecl2, array.definingScope)
                        )
                    }
                }
            }
        }
        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if(decl.names.size>1) {

            val fcallTarget = (decl.value as? IFunctionCall)?.target?.targetSubroutine(program)
            if(fcallTarget!=null && fcallTarget.returntypes.size>1) {
                errors.err("ambiguous multi-variable initialization. Use separate variable declaration and assignment(s) instead.", decl.value!!.position)
                return noModifications
            }

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
