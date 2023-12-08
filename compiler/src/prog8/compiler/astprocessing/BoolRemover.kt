package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.*
import prog8.ast.statements.Subroutine
import prog8.ast.statements.SubroutineParameter
import prog8.ast.statements.VarDecl
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*

internal class BoolRemover(val program: Program) : AstWalker() {

    override fun before(typecast: TypecastExpression, parent: Node): Iterable<IAstModification> {
        if(typecast.type == DataType.BOOL) {
            val valueDt = typecast.expression.inferType(program).getOrElse { throw FatalAstException("unknown dt") }
            val notZero = BinaryExpression(typecast.expression, "!=", NumericLiteral(valueDt, 0.0, typecast.position), typecast.position)
            return listOf(IAstModification.ReplaceNode(typecast, notZero, parent))
        }
        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if(decl.datatype==DataType.BOOL) {
            var newvalue = decl.value
            if(newvalue is NumericLiteral) {
                if(newvalue.number!=0.0)
                    newvalue = NumericLiteral(DataType.UBYTE, 1.0, newvalue.position)
            }
            val ubyteDecl = VarDecl(decl.type, decl.origin, DataType.UBYTE, decl.zeropage, decl.arraysize, decl.name, emptyList(),
                newvalue, decl.isArray, decl.sharedWithAsm, decl.splitArray, decl.position)
            return listOf(IAstModification.ReplaceNode(decl, ubyteDecl, parent))
        }

        if(decl.datatype==DataType.ARRAY_BOOL) {
            var newarray = decl.value
            if(decl.value is ArrayLiteral) {
                val oldArray = (decl.value as ArrayLiteral).value
                val convertedArray = oldArray.map {
                    var number: Expression = it
                    if (it is NumericLiteral && it.type == DataType.BOOL)
                        number = NumericLiteral(DataType.UBYTE, if (it.number == 0.0) 0.0 else 1.0, number.position)
                    number
                }.toTypedArray()
                newarray = ArrayLiteral(InferredTypes.InferredType.known(DataType.ARRAY_UB), convertedArray, decl.position)
            }
            val ubyteArrayDecl = VarDecl(decl.type, decl.origin, DataType.ARRAY_UB, decl.zeropage, decl.arraysize, decl.name, emptyList(),
                newarray, true, decl.sharedWithAsm, decl.splitArray, decl.position)
            return listOf(IAstModification.ReplaceNode(decl, ubyteArrayDecl, parent))
        }

        return noModifications
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        // replace BOOL return type and parameters by UBYTE
        if(subroutine.returntypes.any { it==DataType.BOOL } || subroutine.parameters.any {it.type==DataType.BOOL}) {
            val newReturnTypes = subroutine.returntypes.map {
                if(it==DataType.BOOL) DataType.UBYTE else it
            }
            val newParams = subroutine.parameters.map {
                if(it.type==DataType.BOOL) SubroutineParameter(it.name, DataType.UBYTE, it.position) else it
            }.toMutableList()
            val newSubroutine = Subroutine(subroutine.name, newParams, newReturnTypes,
                subroutine.asmParameterRegisters, subroutine.asmReturnvaluesRegisters, subroutine.asmClobbers,
                subroutine.asmAddress, subroutine.isAsmSubroutine, subroutine.inline, false, subroutine.statements,
                subroutine.position)
            return listOf(IAstModification.ReplaceNode(subroutine, newSubroutine, parent))
        }

        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<IAstModification> {
        // convert boolean and/or/xor/not operators to bitwise equivalents.
        // so that codegen only has to work with bitwise boolean operations from now on.
        // note: this has to be done here and not in BeforeAsmTypecastCleaner! (code size will increase if done there...)
        if(expr.operator in setOf("and", "or", "xor")) {
            expr.operator = when(expr.operator) {
                "and" -> "&"
                "or" -> "|"
                "xor" -> "^"
                else -> "invalid"
            }
            val mods = mutableListOf<IAstModification>()
            val newLeft = wrapWithBooleanCastIfNeeded(expr.left, program)
            val newRight = wrapWithBooleanCastIfNeeded(expr.right, program)
            if(newLeft!=null)
                mods += IAstModification.ReplaceNodeSafe(expr.left, newLeft, expr)
            if(newRight!=null)
                mods += IAstModification.ReplaceNodeSafe(expr.right, newRight, expr)
            return mods
        }
        return noModifications
    }
}

internal fun wrapWithBooleanCastIfNeeded(expr: Expression, program: Program): Expression? {
    fun isBoolean(expr: Expression): Boolean {
        return if(expr.inferType(program) istype DataType.BOOL)
            true
        else if(expr is NumericLiteral && expr.type in IntegerDatatypes && (expr.number==0.0 || expr.number==1.0))
            true
        else if(expr is BinaryExpression && expr.operator in ComparisonOperators + LogicalOperators)
            true
        else if(expr is PrefixExpression && expr.operator == "not")
            true
        else if(expr is BinaryExpression && expr.operator in BitwiseOperators) {
            if(isBoolean(expr.left) && isBoolean(expr.right))
                true
            else expr.operator=="&" && expr.right.constValue(program)?.number==1.0          //  x & 1   is also a boolean result
        }
        else
            false
    }

    return if(isBoolean(expr))
        null
    else
        TypecastExpression(expr, DataType.BOOL, true, expr.position)
}
