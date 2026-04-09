package prog8lsp

import prog8.ast.Module
import prog8.ast.statements.Block
import prog8.code.core.Position
import prog8.code.source.SourceCode
import prog8.parser.MultipleParseErrors
import prog8.parser.ParseError
import prog8.parser.Prog8Parser as InternalParser

/**
 * Result of parsing that may contain errors.
 */
data class ParseResult(
    val module: Module?,
    val errors: List<ParseError>
)

/**
 * Wrapper around the internal Prog8 parser for use in the language server.
 * Provides safe parsing that captures errors instead of throwing exceptions.
 */
object Prog8Parser {

    /**
     * Parse Prog8 source text into a Module AST.
     * Returns a ParseResult containing the module (if successful) and any parse errors.
     */
    fun parseModule(text: String): ParseResult {
        return try {
            val sourceCode = SourceCode.Text(text)
            val module = InternalParser.parseModule(sourceCode)
            ParseResult(module, emptyList())
        } catch (e: MultipleParseErrors) {
            // Collect all parse errors into the result
            ParseResult(null, e.errors)
        } catch (e: ParseError) {
            // Capture the single parse error with position and message
            ParseResult(null, listOf(e))
        } catch (e: Exception) {
            // Wrap unexpected exceptions as parse errors
            val parseError = ParseError(
                message = e.message ?: "Unknown parsing error",
                position = Position.DUMMY,
                cause = RuntimeException(e)
            )
            ParseResult(null, listOf(parseError))
        }
    }

    /**
     * Extract the top-level block from a parsed module.
     * This is typically the "main" block containing the entry point.
     */
    fun getMainBlock(module: Module): Block? {
        return module.statements.filterIsInstance<Block>().firstOrNull { it.name == "main" }
            ?: module.statements.filterIsInstance<Block>().firstOrNull()
    }
}
