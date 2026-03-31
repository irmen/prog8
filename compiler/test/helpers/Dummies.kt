package prog8tests.helpers

import prog8.ast.IBuiltinFunctions
import prog8.ast.expressions.Expression
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteral
import prog8.code.core.Position


/**
 * Dummy IBuiltinFunctions implementation for testing.
 * Used only by compiler module tests that need AST functionality.
 * For other modules, use DummyMemsizer and DummyStringEncoder from testHelpers.
 */
internal object DummyFunctions : IBuiltinFunctions {
    override val names: Set<String> = emptySet()
    override val purefunctionNames: Set<String> = emptySet()
    
    override fun constValue(
        funcName: String,
        args: List<Expression>,
        position: Position,
    ): NumericLiteral? = null

    override fun returnTypes(funcName: String) = emptyArray<InferredTypes.InferredType>()
}
