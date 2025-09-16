package prog8lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

class Prog8WorkspaceService: WorkspaceService {
    private var client: LanguageClient? = null
    private val logger = Logger.getLogger(Prog8WorkspaceService::class.simpleName)

    fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
        logger.info("executeCommand $params")
        return super.executeCommand(params)
    }

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<Either<MutableList<out SymbolInformation>, MutableList<out WorkspaceSymbol>>> {
        logger.info("symbol $params")
        // TODO: Implement workspace symbol search
        // This is just a placeholder implementation
        val symbols = mutableListOf<WorkspaceSymbol>()
        val symbol = WorkspaceSymbol(
            "workspaceSymbol",
            SymbolKind.Function,
            Either.forLeft(Location("file:///example.p8", Range(Position(0, 0), Position(0, 10))))
        )
        symbols.add(symbol)
        return CompletableFuture.completedFuture(Either.forRight(symbols))
    }

    override fun resolveWorkspaceSymbol(workspaceSymbol: WorkspaceSymbol): CompletableFuture<WorkspaceSymbol> {
        logger.info("resolveWorkspaceSymbol $workspaceSymbol")
        return CompletableFuture.completedFuture(workspaceSymbol)
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        logger.info("didChangeConfiguration: $params")
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        logger.info("didChangeWatchedFiles: $params")
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        logger.info("didChangeWorkspaceFolders $params")
        super.didChangeWorkspaceFolders(params)
    }

    override fun willCreateFiles(params: CreateFilesParams): CompletableFuture<WorkspaceEdit> {
        logger.info("willCreateFiles $params")
        return super.willCreateFiles(params)
    }

    override fun didCreateFiles(params: CreateFilesParams) {
        logger.info("didCreateFiles $params")
        super.didCreateFiles(params)
    }

    override fun willRenameFiles(params: RenameFilesParams): CompletableFuture<WorkspaceEdit> {
        logger.info("willRenameFiles $params")
        return super.willRenameFiles(params)
    }

    override fun didRenameFiles(params: RenameFilesParams) {
        logger.info("didRenameFiles $params")
        super.didRenameFiles(params)
    }

    override fun willDeleteFiles(params: DeleteFilesParams): CompletableFuture<WorkspaceEdit> {
        logger.info("willDeleteFiles $params")
        return super.willDeleteFiles(params)
    }

    override fun didDeleteFiles(params: DeleteFilesParams) {
        logger.info("didDeleteFiles $params")
        super.didDeleteFiles(params)
    }

    override fun diagnostic(params: WorkspaceDiagnosticParams): CompletableFuture<WorkspaceDiagnosticReport> {
        logger.info("diagnostic $params")
        return super.diagnostic(params)
    }
}
