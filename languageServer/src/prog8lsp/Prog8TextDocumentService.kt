package prog8lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import kotlin.system.measureTimeMillis

class Prog8TextDocumentService: TextDocumentService {
    private var client: LanguageClient? = null
    private val async = AsyncExecutor()
    private val logger = Logger.getLogger(Prog8TextDocumentService::class.simpleName)

    fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        logger.info("didOpen: $params")
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        logger.info("didChange: $params")
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        logger.info("didClose: $params")
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        logger.info("didSave: $params")
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<MutableList<Either<SymbolInformation, DocumentSymbol>>> = async.compute {
        logger.info("Find symbols in ${params.textDocument.uri}")
        val result: MutableList<Either<SymbolInformation, DocumentSymbol>>
        val time = measureTimeMillis {
            result = mutableListOf()
            val range = Range(Position(1,1), Position(1,5))
            val selectionRange = Range(Position(1,2), Position(1,10))
            val symbol = DocumentSymbol("test-symbolName", SymbolKind.Constant, range, selectionRange)
            result.add(Either.forRight(symbol))
        }
        logger.info("Finished in $time ms")
        result
    }

    override fun completion(position: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>>  = async.compute{
        logger.info("Completion for ${position}")
        val result: Either<MutableList<CompletionItem>, CompletionList>
        val time = measureTimeMillis {
            val list = CompletionList(false, listOf(CompletionItem("test-completionItem")))
            result = Either.forRight(list)
        }
        logger.info("Finished in $time ms")
        result
    }

    // TODO add all other methods that get called.... :P
}
