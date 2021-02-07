package prog8.ast.processing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification


internal class AstVariousTransforms(private val program: Program) : AstWalker() {
    private val noModifications = emptyList<IAstModification>()

    override fun before(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        // is it a struct variable? then define all its struct members as mangled names,
        //    and include the original decl as well.
        if(decl.datatype==DataType.STRUCT && !decl.structHasBeenFlattened) {
            val decls = decl.flattenStructMembers()
            decls.add(decl)
            val result = AnonymousScope(decls, decl.position)
            return listOf(IAstModification.ReplaceNode(
                    decl, result, parent
            ))
        }

        return noModifications
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        // For non-kernel subroutines and non-asm parameters:
        // inject subroutine params as local variables (if they're not there yet).
        val symbolsInSub = subroutine.allDefinedSymbols()
        val namesInSub = symbolsInSub.map{ it.first }.toSet()
        if(subroutine.asmAddress==null) {
            if(subroutine.asmParameterRegisters.isEmpty() && subroutine.parameters.isNotEmpty()) {
                val vars = subroutine.statements.filterIsInstance<VarDecl>().map { it.name }.toSet()
                if(!vars.containsAll(subroutine.parameters.map{it.name})) {
                    return subroutine.parameters
                            .filter { it.name !in namesInSub }
                            .map {
                                val vardecl = ParameterVarDecl(it.name, it.type, subroutine.position)
                                IAstModification.InsertFirst(vardecl, subroutine)
                            }
                }
            }
        }

        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        val leftStr = expr.left as? StringLiteralValue
        val rightStr = expr.right as? StringLiteralValue
        if(expr.operator == "+") {
            val concatenatedString = concatString(expr)
            if(concatenatedString!=null)
                return listOf(IAstModification.ReplaceNode(expr, concatenatedString, parent))
        }
        else if(expr.operator == "*") {
            if (leftStr!=null) {
                val amount = expr.right.constValue(program)
                if(amount!=null) {
                    val string = leftStr.value.repeat(amount.number.toInt())
                    val strval = StringLiteralValue(string, leftStr.altEncoding, expr.position)
                    return listOf(IAstModification.ReplaceNode(expr, strval, parent))
                }
            }
            else if (rightStr!=null) {
                val amount = expr.right.constValue(program)
                if(amount!=null) {
                    val string = rightStr.value.repeat(amount.number.toInt())
                    val strval = StringLiteralValue(string, rightStr.altEncoding, expr.position)
                    return listOf(IAstModification.ReplaceNode(expr, strval, parent))
                }
            }
        }

        return noModifications
    }

    private fun concatString(expr: BinaryExpression): StringLiteralValue? {
        val rightStrval = expr.right as? StringLiteralValue
        val leftStrval = expr.left as? StringLiteralValue
        return when {
            expr.operator!="+" -> null
            expr.left is BinaryExpression && rightStrval!=null -> {
                val subStrVal = concatString(expr.left as BinaryExpression)
                if(subStrVal==null)
                    null
                else
                    StringLiteralValue("${subStrVal.value}${rightStrval.value}", subStrVal.altEncoding, rightStrval.position)
            }
            expr.right is BinaryExpression && leftStrval!=null -> {
                val subStrVal = concatString(expr.right as BinaryExpression)
                if(subStrVal==null)
                    null
                else
                    StringLiteralValue("${leftStrval.value}${subStrVal.value}", subStrVal.altEncoding, leftStrval.position)
            }
            leftStrval!=null && rightStrval!=null -> {
                StringLiteralValue("${leftStrval.value}${rightStrval.value}", leftStrval.altEncoding, leftStrval.position)
            }
            else -> null
        }
    }
}
