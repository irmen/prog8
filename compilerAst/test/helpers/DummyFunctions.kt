package prog8tests.helpers

import prog8.ast.IBuiltinFunctions
import prog8.ast.IMemSizer
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteralValue

val DummyFunctions = object : IBuiltinFunctions {
    override val names: Set<String> = emptySet()
    override val purefunctionNames: Set<String> = emptySet()
    override fun constValue(
        name: String,
        args: List<Expression>,
        position: Position,
        memsizer: IMemSizer
    ): NumericLiteralValue? = null

    override fun returnType(name: String, args: MutableList<Expression>) = InferredTypes.InferredType.unknown()
}