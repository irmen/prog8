package prog8lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Mock LanguageClient for testing - captures notifications and requests.
 */
class MockLanguageClient : LanguageClient {
    private val logger = Logger.getLogger(MockLanguageClient::class.simpleName).apply {
        level = if (isLspVerbose) Level.FINE else Level.INFO
    }

    val publishedDiagnostics = mutableListOf<PublishDiagnosticsParams>()
    val logMessages = mutableListOf<MessageParams>()

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
        publishedDiagnostics.add(diagnostics)
        if (isLspVerbose) {
            logger.fine("Published diagnostics for ${diagnostics.uri}: ${diagnostics.diagnostics.size} issues")
        }
    }

    override fun logMessage(message: MessageParams) {
        logMessages.add(message)
        if (isLspVerbose) {
            logger.fine("Log message: ${message.message}")
        }
    }

    override fun showMessage(message: MessageParams) {
        logMessages.add(message)
        if (isLspVerbose) {
            logger.fine("Show message: ${message.message}")
        }
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem> {
        return CompletableFuture.completedFuture(MessageActionItem("OK"))
    }

    override fun telemetryEvent(`object`: Any) {
        // Ignore telemetry in tests
    }

    override fun registerCapability(capabilities: RegistrationParams): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    override fun unregisterCapability(capabilities: UnregistrationParams): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    fun clear() {
        publishedDiagnostics.clear()
        logMessages.clear()
    }
}

/**
 * Test harness for setting up and testing the Prog8 Language Server.
 * Provides helper methods for common LSP operations.
 */
class LspTestHarness {
    val server = Prog8LanguageServer()
    val client = MockLanguageClient()
    
    private val textDocumentService: Prog8TextDocumentService
        get() = server.textDocumentService as Prog8TextDocumentService

    fun setup() {
        server.connect(client)
        client.clear()
        
        // Initialize the server
        val initParams = InitializeParams().apply {
            capabilities = ClientCapabilities()
        }
        server.initialize(initParams).get()
    }

    fun shutdown() {
        server.shutdown()
        server.exit()
    }

    /**
     * Open a document in the language server.
     */
    fun openDocument(uri: String, text: String, version: Int = 1) {
        val params = DidOpenTextDocumentParams(
            TextDocumentItem(uri, "prog8", version, text)
        )
        textDocumentService.didOpen(params)
    }

    /**
     * Close a document.
     */
    fun closeDocument(uri: String) {
        textDocumentService.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(uri)))
    }

    /**
     * Update document content.
     */
    fun changeDocument(uri: String, text: String, version: Int = 2) {
        val params = DidChangeTextDocumentParams(
            VersionedTextDocumentIdentifier(uri, version),
            listOf(TextDocumentContentChangeEvent(text))
        )
        textDocumentService.didChange(params)
    }

    /**
     * Request hover information at a position.
     */
    fun hover(uri: String, line: Int, character: Int): Hover? {
        val params = HoverParams(TextDocumentIdentifier(uri), Position(line, character))
        return textDocumentService.hover(params).get()
    }

    /**
     * Request go to definition at a position.
     */
    fun definition(uri: String, line: Int, character: Int): List<Location> {
        val params = DefinitionParams(TextDocumentIdentifier(uri), Position(line, character))
        val result = textDocumentService.definition(params).get()
        return result.left ?: emptyList()
    }

    /**
     * Request find references at a position.
     */
    fun references(uri: String, line: Int, character: Int, includeDeclaration: Boolean = true): List<Location> {
        val params = ReferenceParams(
            TextDocumentIdentifier(uri),
            Position(line, character),
            ReferenceContext(includeDeclaration)
        )
        return textDocumentService.references(params).get()
    }

    /**
     * Request signature help at a position.
     */
    fun signatureHelp(uri: String, line: Int, character: Int): SignatureHelp? {
        val params = SignatureHelpParams(TextDocumentIdentifier(uri), Position(line, character))
        return textDocumentService.signatureHelp(params).get()
    }

    /**
     * Request completions at a position.
     */
    fun completions(uri: String, line: Int, character: Int): List<CompletionItem> {
        val params = CompletionParams(TextDocumentIdentifier(uri), Position(line, character))
        val result = textDocumentService.completion(params).get()
        return result.right?.items ?: emptyList()
    }

    /**
     * Request document symbols.
     */
    fun documentSymbols(uri: String): List<DocumentSymbol> {
        val params = DocumentSymbolParams(TextDocumentIdentifier(uri))
        val result = textDocumentService.documentSymbol(params).get()
        return result.map { it.right }.filterNotNull()
    }

    /**
     * Request document highlights at a position.
     */
    fun documentHighlight(uri: String, line: Int, character: Int): List<DocumentHighlight> {
        val params = DocumentHighlightParams(TextDocumentIdentifier(uri), Position(line, character))
        val result = textDocumentService.documentHighlight(params).get()
        return result
    }

    /**
     * Get diagnostics for a document.
     * Returns the most recently published diagnostics for the given URI.
     */
    fun getDiagnostics(uri: String): List<Diagnostic> {
        // Get the last (most recent) diagnostics for this URI
        return client.publishedDiagnostics
            .filter { it.uri == uri }
            .lastOrNull()
            ?.diagnostics ?: emptyList()
    }
}

/**
 * Helper to create a test file URI.
 */
fun testUri(filename: String): String = "file:///test/$filename"

/**
 * Helper to create a position.
 */
fun pos(line: Int, character: Int): Position = Position(line, character)
