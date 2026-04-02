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
    private var astCache: MutableMap<String, AstCache>? = null

    fun connect(client: LanguageClient) {
        this.client = client
    }

    fun setAstCache(cache: MutableMap<String, AstCache>) {
        astCache = cache
    }

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
        logger.info("executeCommand $params")
        return super.executeCommand(params)
    }

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<Either<MutableList<out SymbolInformation>, MutableList<out WorkspaceSymbol>>> {
        logger.info("workspaceSymbol query: '${params.query}'")
        val symbols = mutableListOf<WorkspaceSymbol>()
        
        // Search through all cached documents
        astCache?.forEach { (uri, cache) ->
            val module = cache.module ?: return@forEach
            
            // Collect symbols from this module
            for (stmt in module.statements) {
                collectWorkspaceSymbols(stmt, params.query, symbols, uri)
            }
        }
        
        return CompletableFuture.completedFuture(Either.forRight(symbols))
    }

    /**
     * Recursively collect workspace symbols from AST nodes.
     */
    private fun collectWorkspaceSymbols(
        stmt: prog8.ast.statements.Statement,
        query: String,
        symbols: MutableList<WorkspaceSymbol>,
        uri: String
    ) {
        when (stmt) {
            is prog8.ast.statements.Block -> {
                // Add block as a symbol
                if (query.isBlank() || stmt.name.contains(query, ignoreCase = true)) {
                    val location = org.eclipse.lsp4j.Location(uri, Range(Position(0, 0), Position(0, 0)))
                    symbols.add(WorkspaceSymbol(
                        stmt.name,
                        org.eclipse.lsp4j.SymbolKind.Module,
                        Either.forLeft(location),
                        ""  // Container name
                    ))
                }
                // Recursively collect from block's statements
                for (innerStmt in stmt.statements) {
                    collectWorkspaceSymbols(innerStmt, query, symbols, uri)
                }
            }
            is prog8.ast.statements.Subroutine -> {
                // Add subroutine as a symbol
                if (query.isBlank() || stmt.name.contains(query, ignoreCase = true)) {
                    val location = org.eclipse.lsp4j.Location(uri, Range(Position(0, 0), Position(0, 0)))
                    symbols.add(WorkspaceSymbol(
                        stmt.name,
                        org.eclipse.lsp4j.SymbolKind.Function,
                        Either.forLeft(location),
                        ""  // Container name
                    ))
                }
                // Recursively collect from subroutine body
                for (innerStmt in stmt.statements) {
                    collectWorkspaceSymbols(innerStmt, query, symbols, uri)
                }
            }
            else -> {
                // Other statements don't add workspace symbols
            }
        }
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
