package prog8lsp

import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
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

        InitializeResult()
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
