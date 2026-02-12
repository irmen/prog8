package prog8.ast

import prog8.ast.expressions.Expression
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteral
import prog8.code.core.Position

interface IBuiltinFunctions {
    val names: Set<String>
    val purefunctionNames: Set<String>
    fun constValue(funcName: String, args: List<Expression>, position: Position): NumericLiteral?
    fun returnTypes(funcName: String): Array<InferredTypes.InferredType>
}
