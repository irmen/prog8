package prog8lsp

import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.IFunctionCall
import prog8.ast.walk.IAstVisitor
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.code.core.BaseDataType
import prog8.code.core.Position

/**
 * Helper for looking up symbols at a given position in the AST.
 */
object SymbolLookup {

    data class SymbolAtPosition(
        val name: String,
        val kind: SymbolKind,
        val definitionPosition: Position,
        val type: String?,
        val signature: String?
    )

    enum class SymbolKind {
        VARIABLE,
        CONSTANT,
        SUBROUTINE,
        BLOCK,
        STRUCT,
        STRUCT_FIELD,
        PARAMETER,
        UNKNOWN
    }

    /**
     * Find the symbol at the given line/column position.
     * Returns null if no symbol is found at that position.
     */
    fun findSymbolAt(module: Module, line: Int, column: Int, wordAtCursor: String): SymbolAtPosition? {
        // LSP uses 0-based, Prog8 AST uses 1-based
        val targetLine = line + 1
        val targetCol = column + 1

        // First pass: look for declarations at this position
        for (stmt in module.statements) {
            val result = findSymbolInStatement(stmt, targetLine, targetCol, wordAtCursor)
            if (result != null) return result
        }
        
        // Second pass: look for references and resolve them
        return findReferenceInModule(module, targetLine, targetCol, wordAtCursor)
    }
    
    /**
     * Search for identifier references in the module and resolve them to declarations.
     * Simple unscoped lookup - just matches by name.
     */
    private fun findReferenceInModule(module: Module, targetLine: Int, targetCol: Int, wordAtCursor: String): SymbolAtPosition? {
        // Collect all blocks and subroutines for simple name-based lookup
        val blocks = mutableMapOf<String, SymbolAtPosition>()
        val subroutines = mutableMapOf<String, SymbolAtPosition>()
        
        for (stmt in module.statements) {
            collectSymbols(stmt, blocks, subroutines)
        }
        
        // Search for references
        for (stmt in module.statements) {
            if (stmt is Block) {
                val refResult = findReferenceInBlock(stmt, targetLine, targetCol, wordAtCursor, blocks, subroutines)
                if (refResult != null) return refResult
            }
        }
        
        return null
    }
    
    /**
     * Recursively collect blocks and subroutines from a statement tree.
     * Handles nested subroutines at any depth.
     */
    private fun collectSymbols(
        stmt: prog8.ast.statements.Statement,
        blocks: MutableMap<String, SymbolAtPosition>,
        subroutines: MutableMap<String, SymbolAtPosition>
    ) {
        when (stmt) {
            is Block -> {
                blocks[stmt.name] = SymbolAtPosition(
                    name = stmt.name,
                    kind = SymbolKind.BLOCK,
                    definitionPosition = stmt.position,
                    type = null,
                    signature = "block ${stmt.name}"
                )
                // Recursively collect from block's statements
                for (innerStmt in stmt.statements) {
                    collectSymbols(innerStmt, blocks, subroutines)
                }
            }
            is Subroutine -> {
                val params = stmt.parameters.joinToString(", ") { "${it.name}: ${it.type}" }
                val returnType = stmt.returntypes.joinToString(", ") { it.toString() }
                val fullSignature = if (returnType.isNotEmpty()) {
                    "sub ${stmt.name}($params) -> $returnType"
                } else {
                    "sub ${stmt.name}($params)"
                }
                subroutines[stmt.name] = SymbolAtPosition(
                    name = stmt.name,
                    kind = SymbolKind.SUBROUTINE,
                    definitionPosition = stmt.position,
                    type = null,
                    signature = fullSignature
                )
                // Recursively collect from subroutine body (for nested subroutines)
                for (innerStmt in stmt.statements) {
                    collectSymbols(innerStmt, blocks, subroutines)
                }
            }
            else -> {} // Ignore other statement types
        }
    }

    private fun findReferenceInBlock(
        block: Block,
        targetLine: Int,
        targetCol: Int,
        wordAtCursor: String,
        blocks: Map<String, SymbolAtPosition>,
        subroutines: Map<String, SymbolAtPosition>
    ): SymbolAtPosition? {
        val visibleVars = mutableMapOf<String, SymbolAtPosition>()
        
        for (stmt in block.statements) {
            // Check for references before adding declarations
            val refResult = findReferenceInStatement(stmt, targetLine, targetCol, wordAtCursor, visibleVars, blocks, subroutines)
            if (refResult != null) return refResult
            
            // If this is a subroutine, search inside it too
            if (stmt is Subroutine) {
                val subRefResult = findReferenceInSubroutine(stmt, targetLine, targetCol, wordAtCursor, blocks, subroutines)
                if (subRefResult != null) return subRefResult
            }
            
            // Add variable declarations to visible set (including subroutine parameters)
            if (stmt is VarDecl) {
                val declPrefix = if (stmt.type == VarDeclType.CONST) "const " else ""
                visibleVars[stmt.name] = SymbolAtPosition(
                    name = stmt.name,
                    kind = if (stmt.type == VarDeclType.CONST) SymbolKind.CONSTANT else SymbolKind.VARIABLE,
                    definitionPosition = stmt.position,
                    type = stmt.type.toString(),
                    signature = "${declPrefix}${stmt.name}: ${stmt.type}"
                )
            }
        }
        
        return null
    }
    
    private fun findReferenceInSubroutine(
        sub: Subroutine,
        targetLine: Int,
        targetCol: Int,
        wordAtCursor: String,
        blocks: Map<String, SymbolAtPosition>,
        subroutines: Map<String, SymbolAtPosition>
    ): SymbolAtPosition? {
        val visibleVars = mutableMapOf<String, SymbolAtPosition>()
        
        // Add parameters first
        for (param in sub.parameters) {
            // Parameters don't have individual positions, but we can still track them by name
            // We'll use the subroutine's position as a placeholder
            visibleVars[param.name] = SymbolAtPosition(
                name = param.name,
                kind = SymbolKind.PARAMETER,
                definitionPosition = sub.position,
                type = param.type.toString(),
                signature = "${param.name}: ${param.type}"
            )
        }
        
        for (stmt in sub.statements) {
            // Check for references before adding declarations
            val refResult = findReferenceInStatement(stmt, targetLine, targetCol, wordAtCursor, visibleVars, blocks, subroutines)
            if (refResult != null) return refResult
            
            // Add variable declarations to visible set
            if (stmt is VarDecl && stmt.origin != VarDeclOrigin.SUBROUTINEPARAM) {
                val declPrefix = if (stmt.type == VarDeclType.CONST) "const " else ""
                visibleVars[stmt.name] = SymbolAtPosition(
                    name = stmt.name,
                    kind = if (stmt.type == VarDeclType.CONST) SymbolKind.CONSTANT else SymbolKind.VARIABLE,
                    definitionPosition = stmt.position,
                    type = stmt.type.toString(),
                    signature = "${declPrefix}${stmt.name}: ${stmt.type}"
                )
            }
        }
        
        return null
    }

    private fun findSymbolInStatement(stmt: Statement, targetLine: Int, targetCol: Int, wordAtCursor: String): SymbolAtPosition? {
        return when (stmt) {
            is Block -> findSymbolInBlock(stmt, targetLine, targetCol, wordAtCursor)
            is Subroutine -> findSymbolInSubroutine(stmt, targetLine, targetCol, wordAtCursor)
            is VarDecl -> findSymbolInVarDecl(stmt, targetLine, targetCol, wordAtCursor)
            is StructDecl -> findSymbolInStruct(stmt, targetLine, targetCol, wordAtCursor)
            else -> null
        }
    }

    private fun findSymbolInBlock(block: Block, targetLine: Int, targetCol: Int, wordAtCursor: String): SymbolAtPosition? {
        // Check if position is on the block name itself
        if (isPositionOnName(block.position, targetLine, targetCol, block.name, wordAtCursor)) {
            return SymbolAtPosition(
                name = block.name,
                kind = SymbolKind.BLOCK,
                definitionPosition = block.position,
                type = null,
                signature = "block ${block.name}"
            )
        }

        // Search in block's statements
        for (stmt in block.statements) {
            val result = findSymbolInStatement(stmt, targetLine, targetCol, wordAtCursor)
            if (result != null) return result
        }

        return null
    }

    private fun findSymbolInSubroutine(sub: Subroutine, targetLine: Int, targetCol: Int, wordAtCursor: String): SymbolAtPosition? {
        // Check if position is on the subroutine name (declaration)
        if (isPositionOnName(sub.position, targetLine, targetCol, sub.name, wordAtCursor)) {
            val params = sub.parameters.joinToString(", ") { "${it.name}: ${it.type}" }
            val returnType = sub.returntypes.joinToString(", ") { it.toString() }
            val signature = if (returnType.isNotEmpty()) {
                "sub ${sub.name}($params) -> $returnType"
            } else {
                "sub ${sub.name}($params)"
            }

            return SymbolAtPosition(
                name = sub.name,
                kind = SymbolKind.SUBROUTINE,
                definitionPosition = sub.position,
                type = null,
                signature = signature
            )
        }

        // Check parameters
        for (param in sub.parameters) {
            if (isPositionOnName(sub.position, targetLine, targetCol, param.name, wordAtCursor)) {
                return SymbolAtPosition(
                    name = param.name,
                    kind = SymbolKind.PARAMETER,
                    definitionPosition = sub.position,
                    type = param.type.toString(),
                    signature = "${param.name}: ${param.type}"
                )
            }
        }

        // Search in subroutine's body
        for (stmt in sub.statements) {
            val result = findSymbolInStatement(stmt, targetLine, targetCol, wordAtCursor)
            if (result != null) return result
        }

        return null
    }

    /**
     * Search for an identifier reference in a statement and resolve it to its declaration.
     * This enables "Go to Definition" from use sites.
     */
    private fun findReferenceInStatement(
        stmt: Statement, 
        targetLine: Int, 
        targetCol: Int,
        wordAtCursor: String,
        visibleVars: Map<String, SymbolAtPosition>,
        blocks: Map<String, SymbolAtPosition>,
        subroutines: Map<String, SymbolAtPosition>
    ): SymbolAtPosition? {
        return when (stmt) {
            is Assignment -> {
                // Check if cursor is on the target identifier
                if (stmt.target.identifier != null && 
                    isPositionOnName(stmt.target.identifier!!.position, targetLine, targetCol, stmt.target.identifier!!.nameInSource.lastOrNull() ?: "", wordAtCursor)) {
                    val varName = stmt.target.identifier!!.nameInSource.lastOrNull() ?: ""
                    return visibleVars[varName]
                }
                // Check if cursor is on an identifier in the value expression
                findReferenceInExpression(stmt.value, targetLine, targetCol, wordAtCursor, visibleVars, blocks, subroutines)
            }
            is VarDecl -> {
                // Check if cursor is on the initializer expression
                stmt.value?.let { value ->
                    findReferenceInExpression(value, targetLine, targetCol, wordAtCursor, visibleVars, blocks, subroutines)
                }
            }
            is FunctionCallStatement -> {
                // Check if cursor is on the function name (handles both qualified and unqualified names)
                val funcName = stmt.target.nameInSource.lastOrNull() ?: ""
                if (isPositionOnName(stmt.target.position, targetLine, targetCol, funcName, wordAtCursor)) {
                    // Simple unscoped lookup - just match by name
                    return subroutines[funcName]
                }
                // Also check if cursor is on the qualifier (e.g., "data" in "data.set_both")
                if (stmt.target.nameInSource.size > 1) {
                    val qualifier = stmt.target.nameInSource.firstOrNull() ?: ""
                    if (isPositionOnName(stmt.target.position, targetLine, targetCol, qualifier, wordAtCursor)) {
                        return blocks[qualifier]
                    }
                }
                // Check arguments for variable references
                for (arg in stmt.args) {
                    val refResult = findReferenceInExpression(arg, targetLine, targetCol, wordAtCursor, visibleVars, blocks, subroutines)
                    if (refResult != null) return refResult
                }
                null
            }
            else -> null
        }
    }
    
    private fun findReferenceInExpression(
        expr: prog8.ast.expressions.Expression,
        targetLine: Int,
        targetCol: Int,
        wordAtCursor: String,
        visibleVars: Map<String, SymbolAtPosition>,
        blocks: Map<String, SymbolAtPosition>,
        subroutines: Map<String, SymbolAtPosition>
    ): SymbolAtPosition? {
        return when (expr) {
            is prog8.ast.expressions.IdentifierReference -> {
                val identName = expr.nameInSource.lastOrNull() ?: ""
                if (isPositionOnName(expr.position, targetLine, targetCol, identName, wordAtCursor)) {
                    // Simple unscoped lookup: check vars first, then blocks, then subroutines
                    return visibleVars[identName] ?: blocks[identName] ?: subroutines[identName]
                }
                // Also check if cursor is on a qualifier in a qualified name
                for (part in expr.nameInSource) {
                    if (isPositionOnName(expr.position, targetLine, targetCol, part, wordAtCursor)) {
                        return visibleVars[part] ?: blocks[part] ?: subroutines[part]
                    }
                }
                null
            }
            is prog8.ast.expressions.BinaryExpression -> {
                findReferenceInExpression(expr.left, targetLine, targetCol, wordAtCursor, visibleVars, blocks, subroutines)
                    ?: findReferenceInExpression(expr.right, targetLine, targetCol, wordAtCursor, visibleVars, blocks, subroutines)
            }
            is prog8.ast.expressions.FunctionCallExpression -> {
                // Check function name (handles qualified names)
                val funcName = expr.target.nameInSource.lastOrNull() ?: ""
                if (isPositionOnName(expr.target.position, targetLine, targetCol, funcName, wordAtCursor)) {
                    return subroutines[funcName]
                }
                // Check qualifiers in qualified names
                for (part in expr.target.nameInSource.dropLast(1)) {
                    if (isPositionOnName(expr.target.position, targetLine, targetCol, part, wordAtCursor)) {
                        return blocks[part]
                    }
                }
                // Check arguments
                expr.args.firstNotNullOfOrNull { 
                    findReferenceInExpression(it, targetLine, targetCol, wordAtCursor, visibleVars, blocks, subroutines) 
                }
            }
            else -> null
        }
    }

    private fun findSymbolInVarDecl(varDecl: VarDecl, targetLine: Int, targetCol: Int, wordAtCursor: String): SymbolAtPosition? {
        // Check if position is on the variable name
        if (isPositionOnName(varDecl.position, targetLine, targetCol, varDecl.name, wordAtCursor)) {
            val kind = if (varDecl.type == VarDeclType.CONST) {
                SymbolKind.CONSTANT
            } else {
                SymbolKind.VARIABLE
            }

            val valueStr = varDecl.value?.let { v ->
                when (v) {
                    is NumericLiteral -> {
                        if (v.type == BaseDataType.BOOL) {
                            if (v.number == 0.0) "false" else "true"
                        } else {
                            v.number.toString()
                        }
                    }
                    is StringLiteral -> "\"${v.value}\""
                    else -> null
                }
            }

            return SymbolAtPosition(
                name = varDecl.name,
                kind = kind,
                definitionPosition = varDecl.position,
                type = varDecl.type.toString(),
                signature = buildString {
                    append(if (kind == SymbolKind.CONSTANT) "const " else "var ")
                    append("${varDecl.name}: ${varDecl.type}")
                    if (valueStr != null) {
                        append(" = $valueStr")
                    }
                }
            )
        }

        return null
    }

    private fun findSymbolInStruct(struct: StructDecl, targetLine: Int, targetCol: Int, wordAtCursor: String): SymbolAtPosition? {
        // Check if position is on the struct name
        if (isPositionOnName(struct.position, targetLine, targetCol, struct.name, wordAtCursor)) {
            return SymbolAtPosition(
                name = struct.name,
                kind = SymbolKind.STRUCT,
                definitionPosition = struct.position,
                type = "struct",
                signature = "struct ${struct.name} { ${struct.fields.joinToString(", ") { "${it.second}: ${it.first}" }} }"
            )
        }

        // Check field names (approximate - fields don't have individual positions)
        // This is a simplification
        return null
    }

    private fun isPositionOnName(position: prog8.code.core.Position, targetLine: Int, targetCol: Int, name: String, wordAtCursor: String): Boolean {
        if (position.line != targetLine) return false
        // Check if column is within the name's column range
        if (targetCol < position.startCol || targetCol > position.endCol) return false
        return name == wordAtCursor
    }

    /**
     * Collect all symbols from the module for completions.
     */
    fun collectAllSymbols(module: Module): List<CompletionSymbol> {
        val symbols = mutableListOf<CompletionSymbol>()

        for (stmt in module.statements) {
            collectSymbolsFromStatement(stmt, symbols)
        }

        return symbols
    }

    data class CompletionSymbol(
        val name: String,
        val kind: org.eclipse.lsp4j.CompletionItemKind,
        val detail: String?,
        val insertText: String
    )

    private fun collectSymbolsFromStatement(stmt: Statement, symbols: MutableList<CompletionSymbol>) {
        when (stmt) {
            is Block -> {
                symbols.add(CompletionSymbol(
                    name = stmt.name,
                    kind = org.eclipse.lsp4j.CompletionItemKind.Module,
                    detail = "block",
                    insertText = stmt.name
                ))
                // Also collect symbols from inside the block
                for (innerStmt in stmt.statements) {
                    collectSymbolsFromStatement(innerStmt, symbols)
                }
            }
            is Subroutine -> {
                val params = stmt.parameters.joinToString(", ") { "${it.name}: ${it.type}" }
                symbols.add(CompletionSymbol(
                    name = stmt.name,
                    kind = org.eclipse.lsp4j.CompletionItemKind.Function,
                    detail = "sub ${stmt.name}($params)",
                    insertText = stmt.name
                ))
            }
            is VarDecl -> {
                val kind = if (stmt.type == VarDeclType.CONST) {
                    org.eclipse.lsp4j.CompletionItemKind.Constant
                } else {
                    org.eclipse.lsp4j.CompletionItemKind.Variable
                }
                symbols.add(CompletionSymbol(
                    name = stmt.name,
                    kind = kind,
                    detail = "${stmt.type}",
                    insertText = stmt.name
                ))
            }
            is StructDecl -> {
                symbols.add(CompletionSymbol(
                    name = stmt.name,
                    kind = org.eclipse.lsp4j.CompletionItemKind.Struct,
                    detail = "struct",
                    insertText = stmt.name
                ))
            }
            else -> {
                // Ignore other statement types
            }
        }
    }

    /**
     * Find all references to a symbol in the module, with scope awareness.
     */
    fun findAllReferences(module: Module, targetSymbol: SymbolAtPosition, includeDeclaration: Boolean, uri: String): List<org.eclipse.lsp4j.Location> {
        val locations = mutableListOf<org.eclipse.lsp4j.Location>()
        
        // Collect global symbols for lookup
        val blocks = mutableMapOf<String, SymbolAtPosition>()
        val subroutines = mutableMapOf<String, SymbolAtPosition>()
        for (stmt in module.statements) {
            collectSymbols(stmt, blocks, subroutines)
        }
        
        // Search in all top-level blocks
        for (stmt in module.statements) {
            if (stmt is Block) {
                findReferencesInBlockScoped(stmt, targetSymbol, includeDeclaration, locations, uri, blocks, subroutines)
            }
        }
        
        return locations
    }
    
    private fun findReferencesInBlockScoped(
        block: Block,
        targetSymbol: SymbolAtPosition,
        includeDeclaration: Boolean,
        locations: MutableList<org.eclipse.lsp4j.Location>,
        uri: String,
        blocks: Map<String, SymbolAtPosition>,
        subroutines: Map<String, SymbolAtPosition>
    ) {
        val visibleVars = mutableMapOf<String, SymbolAtPosition>()
        
        // Check if the block itself is the target
        if (includeDeclaration && block.name == targetSymbol.name && block.position == targetSymbol.definitionPosition) {
            locations.add(block.position.toLspLocation(uri))
        }
        
        for (stmt in block.statements) {
            // Check for references in the statement BEFORE adding declaration to visibleVars
            // (Prog8 doesn't allow using a variable before declaration in the same block)
            findReferencesInStatementScoped(stmt, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
            
            // If it's a declaration, add to visibleVars and check if it's the target
            if (stmt is VarDecl) {
                val declPrefix = if (stmt.type == VarDeclType.CONST) "const " else ""
                val currentDecl = SymbolAtPosition(
                    name = stmt.name,
                    kind = if (stmt.type == VarDeclType.CONST) SymbolKind.CONSTANT else SymbolKind.VARIABLE,
                    definitionPosition = stmt.position,
                    type = stmt.type.toString(),
                    signature = "${declPrefix}${stmt.name}: ${stmt.type}"
                )
                visibleVars[stmt.name] = currentDecl
                
                if (includeDeclaration && stmt.name == targetSymbol.name && stmt.position == targetSymbol.definitionPosition) {
                    locations.add(stmt.position.toLspLocation(uri))
                }
            }
            
            // If it's a subroutine, search inside it
            if (stmt is Subroutine) {
                findReferencesInSubroutineScoped(stmt, targetSymbol, includeDeclaration, locations, uri, blocks, subroutines)
                
                // Also check if the subroutine itself is the target
                if (includeDeclaration && stmt.name == targetSymbol.name && stmt.position == targetSymbol.definitionPosition) {
                    locations.add(stmt.position.toLspLocation(uri))
                }
            }
        }
    }
    
    private fun findReferencesInSubroutineScoped(
        sub: Subroutine,
        targetSymbol: SymbolAtPosition,
        includeDeclaration: Boolean,
        locations: MutableList<org.eclipse.lsp4j.Location>,
        uri: String,
        blocks: Map<String, SymbolAtPosition>,
        subroutines: Map<String, SymbolAtPosition>
    ) {
        val visibleVars = mutableMapOf<String, SymbolAtPosition>()
        
        // Add parameters to visible variables and check if any is the target
        for (param in sub.parameters) {
            val currentParam = SymbolAtPosition(
                name = param.name,
                kind = SymbolKind.PARAMETER,
                definitionPosition = sub.position, // Parameters use sub's position as declaration
                type = param.type.toString(),
                signature = "${param.name}: ${param.type}"
            )
            visibleVars[param.name] = currentParam
            
            if (includeDeclaration && param.name == targetSymbol.name && sub.position == targetSymbol.definitionPosition) {
                // For parameters, we highlight the parameter in the signature if we can find it
                // For now, just use the sub's position
                locations.add(sub.position.toLspLocation(uri))
            }
        }
        
        for (stmt in sub.statements) {
            findReferencesInStatementScoped(stmt, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
            
            if (stmt is VarDecl && stmt.origin != VarDeclOrigin.SUBROUTINEPARAM) {
                val declPrefix = if (stmt.type == VarDeclType.CONST) "const " else ""
                val currentDecl = SymbolAtPosition(
                    name = stmt.name,
                    kind = if (stmt.type == VarDeclType.CONST) SymbolKind.CONSTANT else SymbolKind.VARIABLE,
                    definitionPosition = stmt.position,
                    type = stmt.type.toString(),
                    signature = "${declPrefix}${stmt.name}: ${stmt.type}"
                )
                visibleVars[stmt.name] = currentDecl
                
                if (includeDeclaration && stmt.name == targetSymbol.name && stmt.position == targetSymbol.definitionPosition) {
                    locations.add(stmt.position.toLspLocation(uri))
                }
            }
            
            // Subroutines can be nested
            if (stmt is Subroutine) {
                findReferencesInSubroutineScoped(stmt, targetSymbol, includeDeclaration, locations, uri, blocks, subroutines)
                if (includeDeclaration && stmt.name == targetSymbol.name && stmt.position == targetSymbol.definitionPosition) {
                    locations.add(stmt.position.toLspLocation(uri))
                }
            }
        }
    }
    
    private fun findReferencesInStatementScoped(
        stmt: Statement,
        targetSymbol: SymbolAtPosition,
        locations: MutableList<org.eclipse.lsp4j.Location>,
        uri: String,
        visibleVars: Map<String, SymbolAtPosition>,
        blocks: Map<String, SymbolAtPosition>,
        subroutines: Map<String, SymbolAtPosition>
    ) {
        when (stmt) {
            is Assignment -> {
                stmt.target.identifier?.let { ident ->
                    val resolved = resolveIdentifierScoped(ident.nameInSource.lastOrNull() ?: "", visibleVars, blocks, subroutines)
                    if (resolved?.definitionPosition == targetSymbol.definitionPosition) {
                        locations.add(ident.position.toLspLocation(uri))
                    }
                }
                findReferencesInExpressionScoped(stmt.value, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
            }
            is VarDecl -> {
                stmt.value?.let { value ->
                    findReferencesInExpressionScoped(value, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
                }
            }
            is FunctionCallStatement -> {
                val funcName = stmt.target.nameInSource.lastOrNull() ?: ""
                val resolved = resolveIdentifierScoped(funcName, visibleVars, blocks, subroutines)
                if (resolved?.definitionPosition == targetSymbol.definitionPosition) {
                    locations.add(stmt.target.position.toLspLocation(uri))
                }
                for (arg in stmt.args) {
                    findReferencesInExpressionScoped(arg, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
                }
            }
            is IfElse -> {
                findReferencesInExpressionScoped(stmt.condition, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
                for (s in stmt.truepart.statements) findReferencesInStatementScoped(s, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
                for (s in stmt.elsepart.statements) findReferencesInStatementScoped(s, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
            }
            is ForLoop -> {
                // Loop variable
                val resolved = resolveIdentifierScoped(stmt.loopVar.nameInSource.lastOrNull() ?: "", visibleVars, blocks, subroutines)
                if (resolved?.definitionPosition == targetSymbol.definitionPosition) {
                    locations.add(stmt.loopVar.position.toLspLocation(uri))
                }
                findReferencesInExpressionScoped(stmt.iterable, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
                for (s in stmt.body.statements) findReferencesInStatementScoped(s, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
            }
            is When -> {
                findReferencesInExpressionScoped(stmt.condition, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
                for (choice in stmt.choices) {
                    choice.values?.forEach { findReferencesInExpressionScoped(it, targetSymbol, locations, uri, visibleVars, blocks, subroutines) }
                    for (s in choice.statements.statements) findReferencesInStatementScoped(s, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
                }
            }
            is RepeatLoop -> {
                stmt.iterations?.let { findReferencesInExpressionScoped(it, targetSymbol, locations, uri, visibleVars, blocks, subroutines) }
                for (s in stmt.body.statements) findReferencesInStatementScoped(s, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
            }
            is WhileLoop -> {
                findReferencesInExpressionScoped(stmt.condition, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
                for (s in stmt.body.statements) findReferencesInStatementScoped(s, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
            }
            is Return -> {
                for (expr in stmt.values) findReferencesInExpressionScoped(expr, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
            }
            else -> {}
        }
    }
    
    private fun findReferencesInExpressionScoped(
        expr: prog8.ast.expressions.Expression,
        targetSymbol: SymbolAtPosition,
        locations: MutableList<org.eclipse.lsp4j.Location>,
        uri: String,
        visibleVars: Map<String, SymbolAtPosition>,
        blocks: Map<String, SymbolAtPosition>,
        subroutines: Map<String, SymbolAtPosition>
    ) {
        when (expr) {
            is prog8.ast.expressions.IdentifierReference -> {
                val identName = expr.nameInSource.lastOrNull() ?: ""
                val resolved = resolveIdentifierScoped(identName, visibleVars, blocks, subroutines)
                if (resolved?.definitionPosition == targetSymbol.definitionPosition) {
                    locations.add(expr.position.toLspLocation(uri))
                }
            }
            is prog8.ast.expressions.BinaryExpression -> {
                findReferencesInExpressionScoped(expr.left, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
                findReferencesInExpressionScoped(expr.right, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
            }
            is prog8.ast.expressions.PrefixExpression -> {
                findReferencesInExpressionScoped(expr.expression, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
            }
            is prog8.ast.expressions.FunctionCallExpression -> {
                val funcName = expr.target.nameInSource.lastOrNull() ?: ""
                val resolved = resolveIdentifierScoped(funcName, visibleVars, blocks, subroutines)
                if (resolved?.definitionPosition == targetSymbol.definitionPosition) {
                    locations.add(expr.target.position.toLspLocation(uri))
                }
                for (arg in expr.args) {
                    findReferencesInExpressionScoped(arg, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
                }
            }
            is prog8.ast.expressions.TypecastExpression -> {
                findReferencesInExpressionScoped(expr.expression, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
            }
            is prog8.ast.expressions.IfExpression -> {
                findReferencesInExpressionScoped(expr.condition, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
                findReferencesInExpressionScoped(expr.truevalue, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
                findReferencesInExpressionScoped(expr.falsevalue, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
            }
            is prog8.ast.expressions.ArrayIndexedExpression -> {
                expr.plainarrayvar?.let {
                    val resolved = resolveIdentifierScoped(it.nameInSource.lastOrNull() ?: "", visibleVars, blocks, subroutines)
                    if (resolved?.definitionPosition == targetSymbol.definitionPosition) {
                        locations.add(it.position.toLspLocation(uri))
                    }
                }
                expr.nestedArray?.let { findReferencesInExpressionScoped(it, targetSymbol, locations, uri, visibleVars, blocks, subroutines) }
                findReferencesInExpressionScoped(expr.indexer.indexExpr, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
            }
            is prog8.ast.expressions.RangeExpression -> {
                findReferencesInExpressionScoped(expr.from, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
                findReferencesInExpressionScoped(expr.to, targetSymbol, locations, uri, visibleVars, blocks, subroutines)
                expr.step?.let { findReferencesInExpressionScoped(it, targetSymbol, locations, uri, visibleVars, blocks, subroutines) }
            }
            else -> {}
        }
    }
    
    private fun resolveIdentifierScoped(
        name: String,
        visibleVars: Map<String, SymbolAtPosition>,
        blocks: Map<String, SymbolAtPosition>,
        subroutines: Map<String, SymbolAtPosition>
    ): SymbolAtPosition? {
        return visibleVars[name] ?: blocks[name] ?: subroutines[name]
    }

    /**
     * Find the function call at the given position and return the function name and parameter index.
     */
    data class FunctionCallInfo(
        val funcName: String,
        val paramIndex: Int
    )
    
    fun findFunctionCallAt(module: Module, line: Int, column: Int): FunctionCallInfo? {
        val targetLine = line + 1
        val targetCol = column + 1
        
        var bestCall: IFunctionCall? = null
        
        fun checkCall(call: IFunctionCall) {
            val pos = (call as Node).position
            // Check if cursor is on the name OR within the parentheses
            if (pos.line == targetLine && targetCol >= pos.startCol && targetCol <= pos.endCol) {
                if (bestCall == null) {
                    bestCall = call
                } else {
                    val bestPos = (bestCall as Node).position
                    // Pick the innermost call (one with the smallest range)
                    if (pos.endCol - pos.startCol <= bestPos.endCol - bestPos.startCol) {
                        bestCall = call
                    }
                }
            }
        }

        val visitor = object : IAstVisitor {
            override fun visit(module: Module) {
                module.statements.forEach { it.accept(this) }
            }

            override fun visit(functionCallExpr: FunctionCallExpression) {
                checkCall(functionCallExpr)
                super.visit(functionCallExpr)
            }

            override fun visit(functionCallStatement: FunctionCallStatement) {
                checkCall(functionCallStatement)
                super.visit(functionCallStatement)
            }
        }
        
        module.accept(visitor)
        
        bestCall?.let { call ->
            val funcName = call.target.nameInSource.lastOrNull() ?: ""
            var paramIndex = 0
            
            // Find which parameter we are in by checking argument positions.
            // Arguments in the AST are always in order of appearance in source.
            for ((i, arg) in call.args.withIndex()) {
                if (targetCol > arg.position.endCol) {
                    paramIndex = i + 1
                } else {
                    break
                }
            }
            
            return FunctionCallInfo(funcName, paramIndex)
        }
        
        return null
    }

    /**
     * Collect all subroutines from the module for signature help.
     */
    fun collectAllSubroutines(
        module: Module,
        subroutines: MutableMap<String, SymbolAtPosition>
    ) {
        // Reuse collectSymbols which already builds full signatures
        val blocks = mutableMapOf<String, SymbolAtPosition>()
        for (stmt in module.statements) {
            collectSymbols(stmt, blocks, subroutines)
        }
    }
}
