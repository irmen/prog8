package prog8.ast.base

import prog8.ast.expressions.IdentifierReference

class FatalAstException (override var message: String) : Exception(message)

open class AstException (override var message: String) : Exception(message)

class SyntaxError(override var message: String, val position: Position) : AstException(message) {
    override fun toString() = "$position Syntax error: $message"
}

class NameError(override var message: String, val position: Position) : AstException(message) {
    override fun toString() = "$position Name error: $message"
}

open class ExpressionError(message: String, val position: Position) : AstException(message) {
    override fun toString() = "$position Error: $message"
}

class UndefinedSymbolError(symbol: IdentifierReference)
    : ExpressionError("undefined symbol: ${symbol.nameInSource.joinToString(".")}", symbol.position)
