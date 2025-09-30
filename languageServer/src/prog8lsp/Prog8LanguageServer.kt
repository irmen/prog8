package prog8lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.*
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.logging.Logger

class Prog8LanguageServer: LanguageServer, LanguageClientAware, Closeable {
    private lateinit var client: LanguageClient
    private val textDocuments = Prog8TextDocumentService()
    private val workspaces = Prog8WorkspaceService()
    private val async = AsyncExecutor()
    private val logger = Logger.getLogger(Prog8LanguageServer::class.simpleName)

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> = async.compute {
        logger.info("Initializing LanguageServer")

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
        logger.info("connecting to language client")
        this.client = client
        workspaces.connect(client)
        textDocuments.connect(client)
    }

    override fun close() {
        logger.info("closing down")
        async.shutdown(awaitTermination = true)
    }
}
