package prog8lsp

import org.eclipse.lsp4j.*
import prog8.parser.ParseError
import prog8.code.core.Position as Prog8Position

/**
 * Convert Prog8 Position to LSP Range.
 * Prog8 uses 1-based line numbers, LSP uses 0-based.
 */
fun Prog8Position.toLspRange(): Range {
    // Prog8 positions are 1-based, LSP is 0-based
    val startLine = maxOf(0, this.line - 1)
    val startCol = maxOf(0, this.startCol - 1)
    val endLine = maxOf(0, this.line - 1)  // Same line for single-line positions
    val endCol = maxOf(0, this.endCol - 1)

    return Range(
        Position(startLine, startCol),
        Position(endLine, endCol)
    )
}

/**
 * Convert Prog8 Position to LSP Location (for a single-file reference).
 * The URI must be provided separately.
 */
fun Prog8Position.toLspLocation(uri: String): Location {
    return Location(uri, toLspRange())
}

/**
 * Convert a ParseError to an LSP Diagnostic.
 */
fun ParseError.toDiagnostic(uri: String): Diagnostic {
    return Diagnostic(
        this.position.toLspRange(),
        this.message,
        DiagnosticSeverity.Error,
        "prog8-parser",
        "SyntaxError"
    )
}
