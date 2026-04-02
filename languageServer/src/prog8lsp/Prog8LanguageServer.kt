package prog8lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.*
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.logging.Level
import java.util.logging.Logger

class Prog8LanguageServer: LanguageServer, LanguageClientAware, Closeable {
    private lateinit var client: LanguageClient
    private val textDocuments = Prog8TextDocumentService()
    private val workspaces = Prog8WorkspaceService()
    private val async = AsyncExecutor()
    private val logger = Logger.getLogger(Prog8LanguageServer::class.simpleName).apply {
        level = if (isLspVerbose) Level.CONFIG else Level.INFO
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> = async.compute {
        logger.config("Initializing LanguageServer")

        val result = InitializeResult()
        val capabilities = ServerCapabilities()
        
        // Text document synchronization
        capabilities.textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
        
        // Completion support
        val completionOptions = CompletionOptions()
        completionOptions.resolveProvider = true
        completionOptions.triggerCharacters = listOf(".", ":")
        capabilities.completionProvider = completionOptions
        
        // Document symbol support
        capabilities.documentSymbolProvider = Either.forLeft(true)
        
        // Hover support
        capabilities.hoverProvider = Either.forLeft(true)
        
        // Definition support
        capabilities.definitionProvider = Either.forLeft(true)

        // Document highlight support (highlight all occurrences of symbol under cursor)
        capabilities.documentHighlightProvider = Either.forLeft(true)

        // Document link support (clickable %import statements)
        capabilities.documentLinkProvider = DocumentLinkOptions()

        // Workspace symbol support (search for symbols across all files)
        capabilities.workspaceSymbolProvider = Either.forLeft(true)

        // References support
        capabilities.referencesProvider = Either.forLeft(true)

        // Signature help support
        val signatureHelpOptions = SignatureHelpOptions()
        signatureHelpOptions.triggerCharacters = listOf("(", ",")
        capabilities.signatureHelpProvider = signatureHelpOptions

        // Code action support
        val codeActionOptions = CodeActionOptions()
        codeActionOptions.codeActionKinds = listOf(CodeActionKind.QuickFix)
        capabilities.codeActionProvider = Either.forRight(codeActionOptions)
        
        // Document formatting support
        capabilities.documentFormattingProvider = Either.forLeft(true)
        capabilities.documentRangeFormattingProvider = Either.forLeft(true)
        
        // Rename support
        val renameOptions = RenameOptions()
        renameOptions.prepareProvider = true
        capabilities.renameProvider = Either.forRight(renameOptions)
        
        // Workspace symbol support
        capabilities.workspaceSymbolProvider = Either.forLeft(true)
        
        result.capabilities = capabilities
        result
    }

    override fun shutdown(): CompletableFuture<Any> {
        close()
        return completedFuture(null)
    }

    override fun exit() { }

    override fun getTextDocumentService(): TextDocumentService = textDocuments

    override fun getWorkspaceService(): WorkspaceService = workspaces

    override fun connect(client: LanguageClient) {
        logger.config("connecting to language client")
        this.client = client
        workspaces.connect(client)
        textDocuments.connect(client)
        // Give workspace service access to AST cache for workspace symbols
        workspaces.setAstCache(textDocuments.astCache)
    }

    override fun close() {
        logger.config("closing down")
        async.shutdown(awaitTermination = true)
    }
}
