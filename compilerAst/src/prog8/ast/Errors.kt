package prog8.ast

import prog8.ast.expressions.IdentifierReference
import prog8.code.core.Position

/**
 * A severe problem in the Ast (such as internal inconsistency or failed invariant)
 * It is not useful to continue processing, this aborts the compiler immediately
 */
open class FatalAstException (override var message: String) : Exception(message)


abstract class AstException (override var message: String) : Exception(message)

open class SyntaxError(override var message: String, val position: Position) : AstException(message) {
    override fun toString() = "${position.toClickableStr()} Syntax error: $message"
}

class ExpressionError(message: String, val position: Position) : AstException(message) {
    override fun toString() = "${position.toClickableStr()} Error: $message"
}

class UndefinedSymbolError(symbol: IdentifierReference)
    : SyntaxError("undefined symbol: ${symbol.nameInSource.joinToString(".")}", symbol.position)
