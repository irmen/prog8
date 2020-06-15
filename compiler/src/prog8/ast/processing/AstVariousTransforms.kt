package prog8.ast.processing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*


internal class AstVariousTransforms(private val program: Program) : AstWalker() {
    private val noModifications = emptyList<IAstModification>()

    override fun before(functionCall: FunctionCall, parent: Node): Iterable<IAstModification> {
        if(functionCall.target.nameInSource.size==1 && functionCall.target.nameInSource[0]=="lsb") {
            // lsb(...) is just an alias for type cast to ubyte, so replace with "... as ubyte"
            val typecast = TypecastExpression(functionCall.args.single(), DataType.UBYTE, false, functionCall.position)
            return listOf(IAstModification.ReplaceNode(
                    functionCall, typecast, parent
            ))
        }

        return noModifications
    }

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
            if(subroutine.asmParameterRegisters.isEmpty()) {
                return subroutine.parameters
                        .filter { it.name !in namesInSub }
                        .map {
                            val vardecl = ParameterVarDecl(it.name, it.type, subroutine.position)
                            IAstModification.InsertFirst(vardecl, subroutine)
                        }
            }
        }

        return noModifications
    }

    override fun before(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        when {
            expr.left is StringLiteralValue ->
                return listOf(IAstModification.ReplaceNode(
                        expr,
                        processBinaryExprWithString(expr.left as StringLiteralValue, expr.right, expr),
                        parent
                ))
            expr.right is StringLiteralValue ->
                return listOf(IAstModification.ReplaceNode(
                        expr,
                        processBinaryExprWithString(expr.right as StringLiteralValue, expr.left, expr),
                        parent
                ))
        }

        return noModifications
    }

    override fun after(string: StringLiteralValue, parent: Node): Iterable<IAstModification> {
        if(string.parent !is VarDecl) {
            // replace the literal string by a identifier reference to a new local vardecl
            val vardecl = VarDecl.createAuto(string)
            val identifier = IdentifierReference(listOf(vardecl.name), vardecl.position)
            return listOf(
                    IAstModification.ReplaceNode(string, identifier, parent),
                    IAstModification.InsertFirst(vardecl, string.definingScope() as Node)
            )
        }
        return noModifications
    }

    override fun after(array: ArrayLiteralValue, parent: Node): Iterable<IAstModification> {
        val vardecl = array.parent as? VarDecl
        if(vardecl!=null) {
            // adjust the datatype of the array (to an educated guess)
            val arrayDt = array.type
            if(!arrayDt.istype(vardecl.datatype)) {
                val cast = array.cast(vardecl.datatype)
                if (cast != null && cast!=array)
                    return listOf(IAstModification.ReplaceNode(vardecl.value!!, cast, vardecl))
            }
        } else {
            val arrayDt = array.guessDatatype(program)
            if(arrayDt.isKnown) {
                // this array literal is part of an expression, turn it into an identifier reference
                val litval2 = array.cast(arrayDt.typeOrElse(DataType.STRUCT))
                if(litval2!=null && litval2!=array) {
                    val vardecl = VarDecl.createAuto(litval2)
                    val identifier = IdentifierReference(listOf(vardecl.name), vardecl.position)
                    return listOf(
                            IAstModification.ReplaceNode(array, identifier, parent),
                            IAstModification.InsertFirst(vardecl, array.definingScope() as Node)
                    )
                }
            }
        }
        return noModifications
    }

    private fun processBinaryExprWithString(string: StringLiteralValue, operand: Expression, expr: BinaryExpression): Expression {
        val constvalue = operand.constValue(program)
        if(constvalue!=null) {
            if (expr.operator == "*") {
                // repeat a string a number of times
                return StringLiteralValue(string.value.repeat(constvalue.number.toInt()), string.altEncoding, expr.position)
            }
        }
        if(expr.operator == "+" && operand is StringLiteralValue) {
            // concatenate two strings
            return StringLiteralValue("${string.value}${operand.value}", string.altEncoding, expr.position)
        }
        return expr
    }
}
