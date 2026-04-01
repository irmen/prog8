package prog8lsp

import prog8.ast.Module
import prog8.ast.statements.Block
import prog8.code.source.SourceCode
import prog8.parser.Prog8Parser as InternalParser

/**
 * Wrapper around the internal Prog8 parser for use in the language server.
 * Provides safe parsing that returns null on errors instead of throwing exceptions.
 */
object Prog8Parser {

    /**
     * Parse Prog8 source text into a Module AST.
     * Returns null if parsing fails (syntax errors, etc.).
     */
    fun parseModule(text: String): Module? {
        return try {
            val sourceCode = SourceCode.Text(text)
            InternalParser.parseModule(sourceCode)
        } catch (e: Exception) {
            // Parsing failed - return null
            // In a real implementation, we might want to collect and report these errors
            null
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
