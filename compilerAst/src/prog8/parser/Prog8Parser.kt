package prog8.parser

import org.antlr.v4.runtime.*
import prog8.ast.Module
import prog8.ast.antlr.Antlr2KotlinVisitor
import prog8.code.core.Position
import prog8.code.source.SourceCode


class ParseError(override var message: String, val position: Position, cause: RuntimeException): Exception(message, cause)

object Prog8Parser {

    fun parseModule(src: SourceCode): Module {
        val antlrErrorListener = AntlrErrorListener(src)
        val lexer = Prog8ANTLRLexer(CharStreams.fromString(src.text, src.origin))
        lexer.removeErrorListeners()
        lexer.addErrorListener(antlrErrorListener)
        val tokens = CommonTokenStream(lexer)
        val parser = Prog8ANTLRParser(tokens)
        parser.errorHandler = Prog8ErrorStrategy
        parser.removeErrorListeners()
        parser.addErrorListener(antlrErrorListener)

        val parseTree = parser.module()

        val visitor = Antlr2KotlinVisitor(src)
        val visitorResult = visitor.visit(parseTree)
        return visitorResult as Module
    }


    private object Prog8ErrorStrategy: BailErrorStrategy() {
        private fun fillIn(e: RecognitionException?, ctx: ParserRuleContext?) {
            var context = ctx
            while (context != null) {
                context.exception = e
                context = context.getParent()
            }
        }

        override fun reportInputMismatch(recognizer: Parser?, e: InputMismatchException?) {
            super.reportInputMismatch(recognizer, e)
        }

        override fun recover(recognizer: Parser?, e: RecognitionException?) {
            fillIn(e, recognizer!!.context)
            reportError(recognizer, e)
        }

        override fun recoverInline(recognizer: Parser?): Token {
            val e = InputMismatchException(recognizer)
            fillIn(e, recognizer!!.context)
            reportError(recognizer, e)
            throw e
        }
    }

    private class AntlrErrorListener(val src: SourceCode): BaseErrorListener() {
    
        private fun RecognitionException.getPosition(): Position {
            val offending = this.offendingToken ?: return Position(src.origin, 1, 1, 1)

            // Handle edge case: invalid token
            if (offending.line <= 0 || offending.charPositionInLine < 0) {
                return Position(src.origin, 1, 1, 1)
            }

            val line = offending.line
            val startCol = offending.charPositionInLine + 1

            // For EOF or invalid tokens, use startCol as endCol
            val endCol = if (offending.type == org.antlr.v4.runtime.Token.EOF ||
                             offending.startIndex < 0 || offending.stopIndex < 0) {
                startCol
            } else if (offending.line == line) {
                // Same line: column of the last character of the token
                offending.charPositionInLine + (offending.stopIndex - offending.startIndex) + 1
            } else {
                // Multi-line token: since Position is single-line only, use startCol as minimum
                maxOf(startCol, offending.charPositionInLine + 1)
            }

            return Position(src.origin, line, startCol, endCol)
        }

        override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int, charPositionInLine: Int, msg: String, e: RecognitionException?) {
            if (e == null) {
                // Simple case: no exception, just use the provided line/col
                throw ParseError(msg, Position(src.origin, line, charPositionInLine+1, charPositionInLine+1), RuntimeException("parse error"))
            } else {
                if(e.offendingToken==null) {
                    throw ParseError(msg, Position(src.origin, line, charPositionInLine+1, charPositionInLine+1), e)
                } else {
                    throw ParseError(msg, e.getPosition(), e)
                }
            }
        }
    }

}
