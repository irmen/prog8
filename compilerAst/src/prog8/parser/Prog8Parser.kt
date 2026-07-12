package prog8.parser

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.atn.PredictionMode
import prog8.ast.Module
import prog8.ast.antlr.Antlr2KotlinVisitor
import prog8.code.core.ICompilationTarget
import prog8.code.core.Position
import prog8.code.source.SourceCode


class ParseError(override var message: String, val position: Position, cause: RuntimeException): Exception(message, cause)

class MultipleParseErrors(val errors: List<ParseError>) : Exception(
    "Found ${errors.size} parse errors:\n${errors.joinToString("\n") { it.message }}"
)

object Prog8Parser {

    fun parseModule(src: SourceCode, target: ICompilationTarget): Module {
        val lexer = Prog8ANTLRLexer(CharStreams.fromString(src.text, src.origin))
        lexer.removeErrorListeners()
        val tokens = CommonTokenStream(lexer)

        // Fast path: try SLL prediction mode first (faster on correct input)
        val sllErrorListener = CollectingErrorListener(src)
        lexer.addErrorListener(sllErrorListener)
        val parser = Prog8ANTLRParser(tokens)
        parser.interpreter.predictionMode = PredictionMode.SLL
        parser.errorHandler = DefaultErrorStrategy()
        parser.removeErrorListeners()
        parser.addErrorListener(sllErrorListener)

        val parseTree = parser.module()

        if(!sllErrorListener.hasErrors()) {
            // SLL succeeded cleanly — use this tree
            val visitor = Antlr2KotlinVisitor(src, target)
            val visitorResult = visitor.visit(parseTree)
            return visitorResult as Module
        }

        // SLL found errors — fall back to LL for full context parsing
        val llErrorListener = CollectingErrorListener(src)
        lexer.reset()
        parser.reset()
        parser.interpreter.predictionMode = PredictionMode.LL
        parser.removeErrorListeners()
        parser.addErrorListener(llErrorListener)

        val llParseTree = parser.module()

        if(llErrorListener.hasErrors()) {
            throw MultipleParseErrors(llErrorListener.getErrors())
        }

        val visitor = Antlr2KotlinVisitor(src, target)
        val visitorResult = visitor.visit(llParseTree)
        return visitorResult as Module
    }

    class CollectingErrorListener(private val src: SourceCode): BaseErrorListener() {
        private val errors = mutableListOf<ParseError>()

        private fun RecognitionException.getPosition(): Position {
            val offending = this.offendingToken ?: return Position(src.origin, 1, 1, 1)

            // Handle edge case: invalid token
            if (offending.line <= 0 || offending.charPositionInLine < 0) {
                return Position(src.origin, 1, 1, 1)
            }

            val line = offending.line
            val startCol = offending.charPositionInLine + 1

            // For EOF or invalid tokens, use startCol as endCol
            val endCol = if (offending.type == Token.EOF ||
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
                val error = ParseError(msg, Position(src.origin, line, charPositionInLine+1, charPositionInLine+1), RuntimeException("parse error"))
                errors.add(error)
            } else {
                if(e.offendingToken==null) {
                    val error = ParseError(msg, Position(src.origin, line, charPositionInLine+1, charPositionInLine+1), e)
                    errors.add(error)
                } else {
                    val error = ParseError(msg, e.getPosition(), e)
                    errors.add(error)
                }
            }
        }

        fun getErrors(): List<ParseError> = errors.toList()
        fun hasErrors(): Boolean = errors.isNotEmpty()
    }

}
