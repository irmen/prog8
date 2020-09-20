package prog8.ast.base

import prog8.ast.expressions.IdentifierReference

open class FatalAstException (override var message: String) : Exception(message)

open class AstException (override var message: String) : Exception(message)

open class SyntaxError(override var message: String, val position: Position) : AstException(message) {
    override fun toString() = "${position.toClickableStr()} Syntax error: $message"
}

class ExpressionError(message: String, val position: Position) : AstException(message) {
    override fun toString() = "${position.toClickableStr()} Error: $message"
}

class UndefinedSymbolError(symbol: IdentifierReference)
    : SyntaxError("undefined symbol: ${symbol.nameInSource.joinToString(".")}", symbol.position)
