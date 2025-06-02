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
        override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int, charPositionInLine: Int, msg: String, e: RecognitionException?) {
            if (e == null) {
                throw ParseError(msg, Position(src.origin, line, charPositionInLine, charPositionInLine), RuntimeException("parse error"))
            } else {
                if(e.offendingToken==null) {
                    throw ParseError(msg, Position(src.origin, line, charPositionInLine, charPositionInLine), e)
                } else {
                    throw ParseError(msg, e.getPosition(src.origin), e)
                }
            }
        }
    }

    private fun RecognitionException.getPosition(file: String): Position {
        val offending = this.offendingToken
        val line = offending.line
        val beginCol = offending.charPositionInLine
        val endCol = beginCol + offending.stopIndex - offending.startIndex
        return Position(file, line, beginCol, endCol)
    }

}
