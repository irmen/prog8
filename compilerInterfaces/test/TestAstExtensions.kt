package prog8tests.interfaces

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.base.Position
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.RangeExpr
import prog8.compilerinterface.size
import kotlin.test.assertEquals


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestAstExtensions {

    @Test
    fun testRangeExprNumericSize() {
        val expr = RangeExpr(
            NumericLiteralValue.optimalInteger(10, Position.DUMMY),
            NumericLiteralValue.optimalInteger(20, Position.DUMMY),
            NumericLiteralValue.optimalInteger(2, Position.DUMMY),
            Position.DUMMY)
        assertEquals(6, expr.size())
    }
}
