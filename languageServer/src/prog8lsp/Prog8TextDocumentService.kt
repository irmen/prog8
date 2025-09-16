package prog8lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import kotlin.system.measureTimeMillis

// Document model to maintain in memory
data class Prog8Document(
    val uri: String,
    var text: String,
    var version: Int
)

class Prog8TextDocumentService: TextDocumentService {
    private var client: LanguageClient? = null
    private val async = AsyncExecutor()
    private val logger = Logger.getLogger(Prog8TextDocumentService::class.simpleName)
    
    // In-memory document store
    private val documents = mutableMapOf<String, Prog8Document>()

    fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        logger.info("didOpen: ${params.textDocument.uri}")
        
        // Create and store document model
        val document = Prog8Document(
            uri = params.textDocument.uri,
            text = params.textDocument.text,
            version = params.textDocument.version
        )
        documents[params.textDocument.uri] = document
        
        // Trigger diagnostics when a document is opened
        validateDocument(document)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        logger.info("didChange: ${params.textDocument.uri}")
        
        // Get the document from our store
        val document = documents[params.textDocument.uri]
        if (document != null) {
            // Update document version
            document.version = params.textDocument.version
            
            // Apply changes to the document text
            // For simplicity, we're assuming full document sync (TextDocumentSyncKind.Full)
            // In a real implementation, you might need to handle incremental changes
            val text = params.contentChanges.firstOrNull()?.text
            if (text != null) {
                document.text = text
            }
            
            // Trigger diagnostics when a document changes
            validateDocument(document)
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        logger.info("didClose: ${params.textDocument.uri}")
        
        // Remove document from our store
        documents.remove(params.textDocument.uri)
        
        // Clear diagnostics when a document is closed
        client?.publishDiagnostics(PublishDiagnosticsParams(params.textDocument.uri, listOf()))
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        logger.info("didSave: ${params.textDocument.uri}")
        // Handle save events if needed
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<MutableList<Either<SymbolInformation, DocumentSymbol>>> = async.compute {
        logger.info("Find symbols in ${params.textDocument.uri}")
        val result: MutableList<Either<SymbolInformation, DocumentSymbol>>
        val time = measureTimeMillis {
            result = mutableListOf()
            
            // Get document from our store
            val document = documents[params.textDocument.uri]
            if (document != null) {
                // Parse document and extract symbols
                // This is just a placeholder implementation
                val range = Range(Position(0, 0), Position(0, 10))
                val selectionRange = Range(Position(0, 0), Position(0, 10))
                val symbol = DocumentSymbol("exampleSymbol", SymbolKind.Function, range, selectionRange)
                result.add(Either.forRight(symbol))
            }
        }
        logger.info("Finished in $time ms")
        result
    }

    override fun completion(params: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> = async.compute {
        logger.info("Completion for ${params.textDocument.uri} at ${params.position}")
        val result: Either<MutableList<CompletionItem>, CompletionList>
        val time = measureTimeMillis {
            val items = mutableListOf<CompletionItem>()
            
            // Get document from our store
            val document = documents[params.textDocument.uri]
            if (document != null) {
                // Implement actual completion logic based on context
                // This is just a placeholder implementation
                val printItem = CompletionItem("print")
                printItem.kind = CompletionItemKind.Function
                printItem.detail = "Print text to console"
                printItem.documentation = Either.forLeft("Outputs the given text to the console")
                
                val forItem = CompletionItem("for")
                forItem.kind = CompletionItemKind.Keyword
                forItem.detail = "For loop"
                forItem.documentation = Either.forLeft("Iterates over a range or collection")
                
                val ifItem = CompletionItem("if")
                ifItem.kind = CompletionItemKind.Keyword
                ifItem.detail = "Conditional statement"
                ifItem.documentation = Either.forLeft("Executes code based on a condition")
                
                items.add(printItem)
                items.add(forItem)
                items.add(ifItem)
            }
            
            val list = CompletionList(false, items)
            result = Either.forRight(list)
        }
        logger.info("Finished in $time ms")
        result
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover?> = async.compute {
        logger.info("Hover for ${params.textDocument.uri} at ${params.position}")
        
        // Get document from our store
        val document = documents[params.textDocument.uri]
        if (document != null) {
            // Simple implementation that checks for keywords at the position
            val keyword = getWordAtPosition(document, params.position)
            
            when (keyword) {
                "print" -> {
                    val hover = Hover()
                    hover.contents = Either.forLeft(listOf(Either.forLeft("**print** - Outputs text to the console\n\n```prog8\nprint \"Hello, World!\"\n```")))
                    return@compute hover
                }
                "for" -> {
                    val hover = Hover()
                    hover.contents = Either.forLeft(listOf(Either.forLeft("**for** - Loop construct\n\n```prog8\nfor i in 0..10 {\n    print i\n}\n```")))
                    return@compute hover
                }
                "if" -> {
                    val hover = Hover()
                    hover.contents = Either.forLeft(listOf(Either.forLeft("**if** - Conditional statement\n\n```prog8\nif x > 5 {\n    print \"x is greater than 5\"\n}\n```")))
                    return@compute hover
                }
                "sub" -> {
                    val hover = Hover()
                    hover.contents = Either.forLeft(listOf(Either.forLeft("**sub** - Defines a subroutine\n\n```prog8\nsub myFunction() {\n    print \"Hello from function\"\n}\n```")))
                    return@compute hover
                }
                else -> {
                    // Return null for unknown symbols
                    return@compute null
                }
            }
        }
        
        // Return null if document not found
        null
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> = async.compute {
        logger.info("Definition request for ${params.textDocument.uri} at ${params.position}")
        
        // Get document from our store
        val document = documents[params.textDocument.uri]
        if (document != null) {
            // Implement actual definition lookup
            // This would involve parsing the document, finding the symbol at the position,
            // and then finding where that symbol is defined
            val locations = mutableListOf<Location>()
            
            // Placeholder implementation
            // locations.add(Location("file:///path/to/definition.p8", Range(Position(0, 0), Position(0, 10))))
            
            return@compute Either.forLeft(locations)
        }
        
        Either.forLeft(mutableListOf<Location>())
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<MutableList<out TextEdit>> = async.compute {
        logger.info("Formatting document ${params.textDocument.uri}")
        
        // Get document from our store
        val document = documents[params.textDocument.uri]
        if (document != null) {
            // Implement actual code formatting
            // This is just a placeholder implementation
            val edits = mutableListOf<TextEdit>()
            
            // Example of how you might implement formatting:
            // 1. Parse the document
            // 2. Apply formatting rules (indentation, spacing, etc.)
            // 3. Generate TextEdit objects for the changes
            
            return@compute edits
        }
        
        mutableListOf<TextEdit>()
    }

    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<MutableList<out TextEdit>> = async.compute {
        logger.info("Range formatting document ${params.textDocument.uri}")
        
        // Get document from our store
        val document = documents[params.textDocument.uri]
        if (document != null) {
            // Implement actual code formatting for range
            // This is just a placeholder implementation
            val edits = mutableListOf<TextEdit>()
            
            // Example of how you might implement range formatting:
            // 1. Parse the document range
            // 2. Apply formatting rules to the selected range
            // 3. Generate TextEdit objects for the changes
            
            return@compute edits
        }
        
        mutableListOf<TextEdit>()
    }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit> = async.compute {
        logger.info("Rename symbol in ${params.textDocument.uri} at ${params.position}")
        
        // Get document from our store
        val document = documents[params.textDocument.uri]
        if (document != null) {
            // Implement actual rename functionality
            // This would involve:
            // 1. Finding all references to the symbol at the given position
            // 2. Creating TextEdit objects to rename each reference
            // 3. Adding the edits to a WorkspaceEdit
            
            return@compute WorkspaceEdit()
        }
        
        WorkspaceEdit()
    }

    override fun codeAction(params: CodeActionParams): CompletableFuture<MutableList<Either<Command, CodeAction>>> = async.compute {
        logger.info("Code actions for ${params.textDocument.uri}")
        
        // Get document from our store
        val document = documents[params.textDocument.uri]
        if (document != null) {
            val actions = mutableListOf<Either<Command, CodeAction>>()
            
            // Check diagnostics to provide quick fixes
            for (diagnostic in params.context.diagnostics) {
                when (diagnostic.code?.left) {
                    "UnmatchedQuotes" -> {
                        val action = CodeAction()
                        action.title = "Add closing quote"
                        action.kind = CodeActionKind.QuickFix
                        action.diagnostics = listOf(diagnostic)
                        action.isPreferred = true
                        // TODO: Add actual TextEdit to fix the issue
                        actions.add(Either.forRight(action))
                    }
                    "InvalidCharacter" -> {
                        val action = CodeAction()
                        action.title = "Remove invalid characters"
                        action.kind = CodeActionKind.QuickFix
                        action.diagnostics = listOf(diagnostic)
                        // TODO: Add actual TextEdit to fix the issue
                        actions.add(Either.forRight(action))
                    }
                }
            }
            
            // Add some general code actions
            val organizeImportsAction = CodeAction()
            organizeImportsAction.title = "Organize imports"
            organizeImportsAction.kind = CodeActionKind.SourceOrganizeImports
            actions.add(Either.forRight(organizeImportsAction))
            
            return@compute actions
        }
        
        mutableListOf<Either<Command, CodeAction>>()
    }

    private fun getWordAtPosition(document: Prog8Document, position: Position): String {
        // Extract the word at the given position from the document text
        val lines = document.text.lines()
        if (position.line < lines.size) {
            val line = lines[position.line]
            // Simple word extraction - in a real implementation, you'd want a more robust solution
            val words = line.split(Regex("\\s+|[^a-zA-Z0-9_]"))
            var charIndex = 0
            for (word in words) {
                if (position.character >= charIndex && position.character <= charIndex + word.length) {
                    return word
                }
                charIndex += word.length + 1 // +1 for the separator
            }
        }
        return "" // Default to empty string
    }

    private fun validateDocument(document: Prog8Document) {
        logger.info("Validating document: ${document.uri}")
        val diagnostics = mutableListOf<Diagnostic>()
        
        // Split text into lines for easier processing
        val lines = document.text.lines()
        
        // Check for syntax errors
        for ((lineNumber, line) in lines.withIndex()) {
            // Check for unmatched quotes
            val quoteCount = line.count { it == '"' }
            if (quoteCount % 2 != 0) {
                val range = Range(Position(lineNumber, 0), Position(lineNumber, line.length))
                val diagnostic = Diagnostic(
                    range,
                    "Unmatched quotes",
                    DiagnosticSeverity.Error,
                    "prog8-lsp",
                    "UnmatchedQuotes"
                )
                diagnostics.add(diagnostic)
            }
            
            // Check for invalid characters
            if (line.contains(Regex("[^\\u0000-\\u007F]"))) {
                val range = Range(Position(lineNumber, 0), Position(lineNumber, line.length))
                val diagnostic = Diagnostic(
                    range,
                    "Invalid character found",
                    DiagnosticSeverity.Error,
                    "prog8-lsp",
                    "InvalidCharacter"
                )
                diagnostics.add(diagnostic)
            }
            
            // Check for common Prog8 syntax issues
            // For example, check if a line starts with a keyword but doesn't follow proper syntax
            if (line.trim().startsWith("sub ") && !line.contains("(")) {
                val range = Range(Position(lineNumber, 0), Position(lineNumber, line.length))
                val diagnostic = Diagnostic(
                    range,
                    "Subroutine declaration missing parentheses",
                    DiagnosticSeverity.Error,
                    "prog8-lsp",
                    "InvalidSubroutine"
                )
                diagnostics.add(diagnostic)
            }
        }
        
        // Check for other issues
        if (document.text.contains("error")) {
            val range = Range(Position(0, 0), Position(0, 5))
            val diagnostic = Diagnostic(
                range,
                "This is a sample diagnostic",
                DiagnosticSeverity.Warning,
                "prog8-lsp",
                "SampleDiagnostic"
            )
            diagnostics.add(diagnostic)
        }
        
        client?.publishDiagnostics(PublishDiagnosticsParams(document.uri, diagnostics))
    }
}