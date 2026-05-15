package prog8lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import prog8lsp.SymbolLookup.SymbolAtPosition
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.measureTimeMillis

/**
 * Check if verbose logging is enabled via system property.
 * Enable with: gradle test -Dlsp.verbose=true
 */
internal val isLspVerbose: Boolean = System.getProperty("lsp.verbose")?.toBoolean() ?: false

// Document model to maintain in memory
data class Prog8Document(
    val uri: String,
    var text: String,
    var version: Int
)

/**
 * Cached AST for a document.
 * Holds the parsed module and tracks if it's stale (needs reparsing).
 */
data class AstCache(
    var module: prog8.ast.Module?,
    var isStale: Boolean
)

class Prog8TextDocumentService: TextDocumentService {
    private var client: LanguageClient? = null
    internal val async = AsyncExecutor()
    private val logger = Logger.getLogger(Prog8TextDocumentService::class.simpleName).apply {
        level = if (isLspVerbose) Level.CONFIG else Level.INFO
    }
    private val symbolExtractor = SymbolExtractor()

    // In-memory document store
    private val documents = ConcurrentHashMap<String, Prog8Document>()
    
    // Store last diagnostics for code actions
    private val lastDiagnostics = ConcurrentHashMap<String, List<Diagnostic>>()
    
    // Cached ASTs per document (to avoid reparsing on every request)
    internal val astCache = ConcurrentHashMap<String, AstCache>()

    fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        logger.config("didOpen: ${params.textDocument.uri}")

        // Create and store document model
        val document = Prog8Document(
            uri = params.textDocument.uri,
            text = params.textDocument.text,
            version = params.textDocument.version
        )
        documents[params.textDocument.uri] = document
        
        async.execute {
            // Parse and cache AST for the document
            astCache[params.textDocument.uri] = AstCache(
                module = Prog8Parser.parseModule(params.textDocument.text).module,
                isStale = false
            )

            // Trigger diagnostics when a document is opened
            validateDocument(document)
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        logger.config("didChange: ${params.textDocument.uri}")

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
                // Mark AST as stale - will be reparsed on next request
                astCache[params.textDocument.uri]?.isStale = true
            }

            // Trigger diagnostics when a document changes
            async.execute {
                validateDocument(document)
            }
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        logger.config("didClose: ${params.textDocument.uri}")

        // Remove document from our store
        documents.remove(params.textDocument.uri)
        
        // Remove cached AST
        astCache.remove(params.textDocument.uri)
        
        // Clear diagnostics when a document is closed
        client?.publishDiagnostics(PublishDiagnosticsParams(params.textDocument.uri, listOf()))
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        logger.config("didSave: ${params.textDocument.uri}")
        // Handle save events if needed
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<MutableList<Either<SymbolInformation, DocumentSymbol>>> = async.compute {
        logger.config("Find symbols in ${params.textDocument.uri}")
        val result: MutableList<Either<SymbolInformation, DocumentSymbol>>
        val time = measureTimeMillis {
            result = mutableListOf()

            // Get document from our store
            val document = documents[params.textDocument.uri]
            if (document != null) {
                // Get or parse AST
                val cache = astCache.getOrPut(params.textDocument.uri) { 
                    AstCache(module = null, isStale = true) 
                }
                
                // Reparse if AST is missing or stale
                if (cache.module == null || cache.isStale) {
                    cache.module = Prog8Parser.parseModule(document.text).module
                    cache.isStale = false
                }
                
                // Extract symbols from AST
                cache.module?.let { module ->
                    val symbols = symbolExtractor.extract(module)
                    result.addAll(symbols.map { sym -> Either.forRight<SymbolInformation, DocumentSymbol>(sym) })
                }
            }
        }
        logger.config("Finished in $time ms")
        result
    }

    override fun completion(params: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> = async.compute {
        logger.config("Completion for ${params.textDocument.uri} at ${params.position}")
        val result: Either<MutableList<CompletionItem>, CompletionList>
        val time = measureTimeMillis {
            val items = mutableListOf<CompletionItem>()

            // Get document from our store
            val document = documents[params.textDocument.uri]
            if (document != null) {
                // Get or parse AST
                val cache = astCache.getOrPut(params.textDocument.uri) {
                    AstCache(module = null, isStale = true)
                }
                if (cache.module == null || cache.isStale) {
                    cache.module = Prog8Parser.parseModule(document.text).module
                    cache.isStale = false
                }

                // Collect symbols from AST for completions
                cache.module?.let { module ->
                    val symbols = SymbolLookup.collectAllSymbols(module)
                    for (sym in symbols) {
                        val item = CompletionItem(sym.name)
                        item.kind = sym.kind
                        item.detail = sym.detail
                        item.insertText = sym.insertText
                        items.add(item)
                    }
                }

                // Add keywords from Prog8 language
                // From syntax-files/NotepadPlusPlus/Prog8.xml
                val keywords = listOf(
                    // Keywords3: Control flow and declarations
                    "inline" to CompletionItemKind.Keyword,
                    "sub" to CompletionItemKind.Keyword,
                    "asmsub" to CompletionItemKind.Keyword,
                    "extsub" to CompletionItemKind.Keyword,
                    "clobbers" to CompletionItemKind.Keyword,
                    "asm" to CompletionItemKind.Keyword,
                    "if" to CompletionItemKind.Keyword,
                    "when" to CompletionItemKind.Keyword,
                    "then" to CompletionItemKind.Keyword,
                    "else" to CompletionItemKind.Keyword,
                    "if_cc" to CompletionItemKind.Keyword,
                    "if_cs" to CompletionItemKind.Keyword,
                    "if_eq" to CompletionItemKind.Keyword,
                    "if_mi" to CompletionItemKind.Keyword,
                    "if_neg" to CompletionItemKind.Keyword,
                    "if_nz" to CompletionItemKind.Keyword,
                    "if_pl" to CompletionItemKind.Keyword,
                    "if_pos" to CompletionItemKind.Keyword,
                    "if_vc" to CompletionItemKind.Keyword,
                    "if_vs" to CompletionItemKind.Keyword,
                    "if_z" to CompletionItemKind.Keyword,
                    "for" to CompletionItemKind.Keyword,
                    "in" to CompletionItemKind.Keyword,
                    "step" to CompletionItemKind.Keyword,
                    "do" to CompletionItemKind.Keyword,
                    "while" to CompletionItemKind.Keyword,
                    "repeat" to CompletionItemKind.Keyword,
                    "unroll" to CompletionItemKind.Keyword,
                    "break" to CompletionItemKind.Keyword,
                    "continue" to CompletionItemKind.Keyword,
                    "return" to CompletionItemKind.Keyword,
                    "goto" to CompletionItemKind.Keyword,
                    
                    // Keywords5: Operators and literals
                    "true" to CompletionItemKind.Keyword,
                    "false" to CompletionItemKind.Keyword,
                    "not" to CompletionItemKind.Keyword,
                    "and" to CompletionItemKind.Keyword,
                    "or" to CompletionItemKind.Keyword,
                    "xor" to CompletionItemKind.Keyword,
                    "as" to CompletionItemKind.Keyword,
                    "to" to CompletionItemKind.Keyword,
                    "downto" to CompletionItemKind.Keyword,
                    
                    // Keywords1: Data types
                    "void" to CompletionItemKind.Keyword,
                    "const" to CompletionItemKind.Keyword,
                    "str" to CompletionItemKind.Keyword,
                    "byte" to CompletionItemKind.Keyword,
                    "ubyte" to CompletionItemKind.Keyword,
                    "bool" to CompletionItemKind.Keyword,
                    "long" to CompletionItemKind.Keyword,
                    "word" to CompletionItemKind.Keyword,
                    "uword" to CompletionItemKind.Keyword,
                    "float" to CompletionItemKind.Keyword,
                    "struct" to CompletionItemKind.Keyword,
                    "enum" to CompletionItemKind.Keyword,
                    
                    // Other common keywords
                    "alias" to CompletionItemKind.Keyword,
                    "defer" to CompletionItemKind.Keyword,
                    "swap" to CompletionItemKind.Keyword,
                    "import" to CompletionItemKind.Keyword,
                    
                    // CPU status flag conditions
                    "if_ne" to CompletionItemKind.Keyword
                )
                for ((keyword, kind) in keywords) {
                    val item = CompletionItem(keyword)
                    item.kind = kind
                    items.add(item)
                }
                
                // Add builtin functions (from docs/source/libraries.rst)
                val builtinFunctions = listOf(
                    // Array operations
                    "len",
                    // Math
                    "abs", "clamp", "divmod", "gcd", "max", "min", "sgn", "sqrt",
                    // CPU Stack
                    "push", "pushw", "pushl", "pushf", "pop", "popw", "popl", "popf",
                    // Memory access
                    "peek", "peekw", "peekl", "peekf", "peekbool",
                    "poke", "pokew", "pokel", "pokef", "pokebool",
                    // Byte manipulation
                    "lsb", "msb", "lsw", "msw", "lmh", "lsl", "lsr",
                    "setlsb", "setmsb", "mkword", "rol", "rol2", "ror", "ror2",
                    // Other builtins
                    "alias", "call", "callfar", "callfar2", "cmp", "defer",
                    "memory", "offsetof", "sizeof", "swap",
                    "rsave", "rsavex", "rrestore", "rrestorex",
                    "rnd", "rndw", "sqrtw"
                    // Note: 'psg' and 'psg2' are library modules, not builtin functions
                )
                // Note: Tags like @zp, @shared, @nosplit, etc. are NOT included here
                // because they're context-specific modifiers (used after datatype in declarations)
                // and always require the @ prefix.
                for (funcName in builtinFunctions) {
                    val item = CompletionItem(funcName)
                    item.kind = CompletionItemKind.Function
                    item.detail = "builtin function"
                    item.insertText = funcName
                    items.add(item)
                }
            }

            val list = CompletionList(false, items)
            result = Either.forRight(list)
        }
        logger.config("Finished in $time ms")
        result
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover?> = async.compute {
        logger.config("Hover for ${params.textDocument.uri} at ${params.position}")

        // Get document from our store
        val document = documents[params.textDocument.uri]
        if (document != null) {
            // Get or parse AST
            val cache = astCache.getOrPut(params.textDocument.uri) {
                AstCache(module = null, isStale = true)
            }
            if (cache.module == null || cache.isStale) {
                cache.module = Prog8Parser.parseModule(document.text).module
                cache.isStale = false
            }

            // Try to find symbol at position
            cache.module?.let { module ->
                val wordAtCursor = getWordAtPosition(document, params.position)
                val symbol = SymbolLookup.findSymbolAt(module, params.position.line, params.position.character, wordAtCursor)
                if (symbol != null) {
                    val hover = Hover()
                    val contents = buildString {
                        append("```prog8\n")
                        if (symbol.signature != null) {
                            append(symbol.signature)
                        } else {
                            append("${symbol.name}: ${symbol.type ?: "unknown"}")
                        }
                        append("\n```")
                    }
                    hover.contents = Either.forLeft(listOf(Either.forLeft(contents)))
                    return@compute hover
                }
            }

            // Fallback to keyword hover
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
            }
        }

        // Return null if document not found or no symbol at position
        null
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> = async.compute {
        logger.config("Definition request for ${params.textDocument.uri} at ${params.position}")

        // Get document from our store
        val document = documents[params.textDocument.uri]
        if (document != null) {
            // Get the word at the cursor position for logging
            val wordAtCursor = getWordAtPosition(document, params.position)
            logger.config("Word at cursor: '$wordAtCursor'")
            
            // Get or parse AST
            val cache = astCache.getOrPut(params.textDocument.uri) {
                AstCache(module = null, isStale = true)
            }
            if (cache.module == null || cache.isStale) {
                cache.module = Prog8Parser.parseModule(document.text).module
                cache.isStale = false
            }

            // Find symbol at position and return its definition location
            cache.module?.let { module ->
                val wordAtCursor = getWordAtPosition(document, params.position)
                val symbol = SymbolLookup.findSymbolAt(module, params.position.line, params.position.character, wordAtCursor)
                if (symbol != null) {
                    logger.config("Found symbol: ${symbol.name}")
                    val locations = mutableListOf<Location>()
                    // Convert Prog8 position to LSP location
                    val defRange = symbol.definitionPosition.toLspRange()
                    // Use the same document URI for now (single-file support)
                    locations.add(Location(params.textDocument.uri, defRange))
                    return@compute Either.forLeft(locations)
                } else {
                    logger.config("No symbol found at position")
                }
            }
        }

        Either.forLeft(mutableListOf<Location>())
    }

    override fun references(params: ReferenceParams): CompletableFuture<MutableList<out Location>> = async.compute {
        logger.config("Find references for ${params.textDocument.uri} at ${params.position}")
        val locations = mutableListOf<Location>()

        val document = documents[params.textDocument.uri]
        if (document != null) {
            // Get or parse AST
            val cache = astCache.getOrPut(params.textDocument.uri) {
                AstCache(module = null, isStale = true)
            }
            if (cache.module == null || cache.isStale) {
                cache.module = Prog8Parser.parseModule(document.text).module
                cache.isStale = false
            }

            cache.module?.let { module ->
                // Find the symbol at the cursor position
                val wordAtCursor = getWordAtPosition(document, params.position)
                val symbol = SymbolLookup.findSymbolAt(module, params.position.line, params.position.character, wordAtCursor)
                if (symbol != null) {
                    // Now search the entire document for all references to this symbol
                    val allRefs = SymbolLookup.findAllReferences(module, symbol, params.context.isIncludeDeclaration, params.textDocument.uri)
                    locations.addAll(allRefs)
                }
            }
        }

        locations
    }

    override fun documentHighlight(params: DocumentHighlightParams): CompletableFuture<MutableList<out DocumentHighlight>> = async.compute {
        logger.config("Document highlight for ${params.textDocument.uri} at ${params.position}")
        val highlights = mutableListOf<DocumentHighlight>()

        val document = documents[params.textDocument.uri]
        if (document != null) {
            // Get or parse AST
            val cache = astCache.getOrPut(params.textDocument.uri) {
                AstCache(module = null, isStale = true)
            }
            if (cache.module == null || cache.isStale) {
                cache.module = Prog8Parser.parseModule(document.text).module
                cache.isStale = false
            }

            cache.module?.let { module ->
                // Find the symbol at the cursor position
                val wordAtCursor = getWordAtPosition(document, params.position)
                val symbol = SymbolLookup.findSymbolAt(module, params.position.line, params.position.character, wordAtCursor)
                if (symbol != null) {
                    // Find all references in the document
                    val allRefs = SymbolLookup.findAllReferences(module, symbol, true, params.textDocument.uri)
                    // Convert locations to document highlights
                    for (location in allRefs) {
                        highlights.add(DocumentHighlight(location.range))
                    }
                }
            }
        }

        highlights
    }

    override fun documentLink(params: DocumentLinkParams): CompletableFuture<MutableList<out DocumentLink>> = async.compute {
        logger.config("Document links for ${params.textDocument.uri}")
        val links = mutableListOf<DocumentLink>()

        val document = documents[params.textDocument.uri]
        if (document != null) {
            val lines = document.text.lines()
            for ((lineNum, line) in lines.withIndex()) {
                // Look for %import directives
                val importMatch = Regex("""%import\s+(\w+)""").find(line)
                if (importMatch != null) {
                    val moduleName = importMatch.groupValues[1]
                    val startCol = importMatch.range.first
                    val endCol = importMatch.range.last + 1

                    // Create a link for the module name
                    val range = Range(
                        Position(lineNum, startCol),
                        Position(lineNum, endCol)
                    )
                    
                    // Check if it's a built-in library module
                    // These are the module names users actually use in %import statements
                    // The compiler resolves these to target-specific versions automatically
                    val builtinModules = setOf(
                        // Core libraries (available on all targets)
                        "bcd", "buffers", "compression", "conv", "coroutines", "lineclip",
                        "math", "prog8_lib", "prog8_math", "sorting", "strings",
                        "test_stack", "wavfile",
                        
                        // Target-specific modules (compiler resolves to correct version)
                        "diskio", "floats", "graphics", "syslib", "textio", "sys",
                        "petsnd", "petgfx",
                        
                        // CX16 specific modules
                        "adpcm", "bmx", "emudbg", "gfx_hires", "gfx_lores",
                        "monogfx", "palette", "psg", "psg2", "sprites", "verafx",
                        
                        // Common aliases
                        "cbm", "memory", "txt"
                    )
                    
                    val link = if (moduleName in builtinModules) {
                        // For built-in modules, use a special URI that indicates it's a library module
                        // The actual file is in the compiler JAR at /prog8lib/
                        DocumentLink(range, "prog8lib://$moduleName.p8")
                    } else {
                        // For user modules, try to resolve relative to current file
                        DocumentLink(range, "file:///${moduleName}.p8")
                    }
                    link.tooltip = if (moduleName in builtinModules) {
                        "Built-in library module: $moduleName"
                    } else {
                        "Open module: $moduleName"
                    }
                    links.add(link)
                }
            }
        }

        links
    }

    override fun codeAction(params: CodeActionParams): CompletableFuture<MutableList<Either<Command, CodeAction>>> = async.compute {
        logger.config("Code actions for ${params.textDocument.uri}")
        val actions = mutableListOf<Either<Command, CodeAction>>()

        val diagnostics = params.context.diagnostics
        val uri = params.textDocument.uri

        for (diagnostic in diagnostics) {
            when (diagnostic.code?.left) {
                "UnmatchedQuotes" -> {
                    // Offer to add closing quote at end of line
                    val action = CodeAction()
                    action.title = "Add closing quote"
                    action.kind = CodeActionKind.QuickFix
                    action.diagnostics = listOf(diagnostic)
                    action.isPreferred = true
                    
                    // Create text edit to add closing quote
                    val edit = TextEdit(
                        Range(diagnostic.range.end, diagnostic.range.end),
                        "\""
                    )
                    val workspaceEdit = WorkspaceEdit(mapOf(uri to listOf(edit)))
                    action.edit = workspaceEdit
                    
                    actions.add(Either.forRight(action))
                }
                "SyntaxError" -> {
                    // For syntax errors, we can't auto-fix but can show the error message
                    val action = CodeAction()
                    action.title = "Syntax error: ${diagnostic.message}"
                    action.kind = CodeActionKind.QuickFix
                    action.diagnostics = listOf(diagnostic)
                    // Mark as disabled (can't auto-fix)
                    action.isPreferred = false
                    
                    actions.add(Either.forRight(action))
                }
            }
        }

        actions
    }

    override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp?> = async.compute {
        logger.config("Signature help for ${params.textDocument.uri} at ${params.position}")

        val document = documents[params.textDocument.uri]
        if (document != null) {
            // Get or parse AST
            val cache = astCache.getOrPut(params.textDocument.uri) {
                AstCache(module = null, isStale = true)
            }
            if (cache.module == null || cache.isStale) {
                cache.module = Prog8Parser.parseModule(document.text).module
                cache.isStale = false
            }

            cache.module?.let { module ->
                // Find the function call at the cursor position
                val callInfo = SymbolLookup.findFunctionCallAt(module, params.position.line, params.position.character)
                if (callInfo != null) {
                    val subroutines = mutableMapOf<String, SymbolAtPosition>()
                    SymbolLookup.collectAllSubroutines(module, subroutines)
                    
                    val subroutine = subroutines[callInfo.funcName]
                    if (subroutine != null) {
                        val signatureHelp = SignatureHelp()
                        val signatures = mutableListOf<SignatureInformation>()
                        
                        // Parse the signature to extract parameters
                        val sigMatch = Regex("sub ([^(]+)\\(([^)]*)\\)(?: -> (.+))?").find(subroutine.signature ?: "")
                        if (sigMatch != null) {
                            val sigName = sigMatch.groupValues[1]
                            val paramsStr = sigMatch.groupValues[2]
                            val returnType = sigMatch.groupValues[3]
                            
                            val sigInfo = SignatureInformation()
                            sigInfo.label = subroutine.signature ?: ""
                            
                            // Parse parameters
                            val paramInfos = mutableListOf<ParameterInformation>()
                            if (paramsStr.isNotBlank()) {
                                val paramParts = paramsStr.split(",").map { it.trim() }
                                for ((i, param) in paramParts.withIndex()) {
                                    val paramInfo = ParameterInformation()
                                    paramInfo.label = Either.forLeft(param)
                                    paramInfos.add(paramInfo)
                                }
                            }
                            
                            sigInfo.parameters = paramInfos
                            signatures.add(sigInfo)
                        }
                        
                        signatureHelp.signatures = signatures
                        signatureHelp.activeSignature = 0
                        signatureHelp.activeParameter = callInfo.paramIndex.coerceAtMost(signatures.firstOrNull()?.parameters?.size?.minus(1) ?: 0)
                        
                        return@compute signatureHelp
                    }
                }
            }
        }

        null
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<MutableList<out TextEdit>> = async.compute {
        logger.config("Formatting document ${params.textDocument.uri}")
        
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
        logger.config("Range formatting document ${params.textDocument.uri}")
        
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
        logger.config("Rename symbol in ${params.textDocument.uri} at ${params.position} to ${params.newName}")
        
        // Get document from our store
        val document = documents[params.textDocument.uri]
        if (document != null) {
            // Get or parse AST
            val cache = astCache.getOrPut(params.textDocument.uri) {
                AstCache(module = null, isStale = true)
            }
            if (cache.module == null || cache.isStale) {
                cache.module = Prog8Parser.parseModule(document.text).module
                cache.isStale = false
            }

            cache.module?.let { module ->
                // 1. Find the symbol at the given position
                val wordAtCursor = getWordAtPosition(document, params.position)
                val symbol = SymbolLookup.findSymbolAt(module, params.position.line, params.position.character, wordAtCursor)
                if (symbol != null) {
                    // 2. Find all references to the symbol
                    // Now scope-aware!
                    val allRefs = SymbolLookup.findAllReferences(module, symbol, true, params.textDocument.uri)
                    
                    // 3. Create TextEdit objects to rename each reference
                    val edits = allRefs.map { loc ->
                        TextEdit(loc.range, params.newName)
                    }
                    
                    // 4. Return the edits in a WorkspaceEdit
                    val workspaceEdit = WorkspaceEdit()
                    workspaceEdit.changes = mapOf(params.textDocument.uri to edits)
                    return@compute workspaceEdit
                }
            }
        }

        WorkspaceEdit()
    }

    private fun getWordAtPosition(document: Prog8Document, position: Position): String {
        // Extract the word at the given position from the document text
        val lines = document.text.lines()
        if (position.line < lines.size) {
            val line = lines[position.line]
            if (position.character < line.length) {
                // Find word boundaries around the cursor position
                // Prog8 identifiers: letters, numbers, underscores
                var start = position.character
                var end = position.character
                
                // Move start backwards to find word beginning
                while (start > 0 && (line[start - 1].isLetterOrDigit() || line[start - 1] == '_')) {
                    start--
                }
                
                // Move end forwards to find word end
                while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) {
                    end++
                }
                
                if (start < end) {
                    return line.substring(start, end)
                }
            }
        }
        return "" // Default to empty string
    }

    private fun validateDocument(document: Prog8Document) {
        logger.config("Validating document: ${document.uri}")
        val diagnostics = mutableListOf<Diagnostic>()

        // Parse the document and collect parser errors
        val parseResult = Prog8Parser.parseModule(document.text)

        // Convert parse errors to diagnostics
        for (error in parseResult.errors) {
            diagnostics.add(error.toDiagnostic(document.uri))
        }

        // If parsing succeeded, we can do additional semantic checks
        // For now, keep the unmatched quotes check as a fast pre-parse validation
        // (though the parser will also catch this)
        if (parseResult.errors.isEmpty()) {
            val lines = document.text.lines()
            for ((lineNumber, line) in lines.withIndex()) {
                // Check for unmatched quotes (quick check without full parse)
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
            }
        }

        // Store diagnostics for code actions
        lastDiagnostics[document.uri] = diagnostics

        client?.publishDiagnostics(PublishDiagnosticsParams(document.uri, diagnostics))
    }
}
