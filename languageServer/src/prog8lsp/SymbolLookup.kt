package prog8lsp

import prog8.ast.Module
import prog8.ast.expressions.NumericLiteral
import prog8.ast.expressions.StringLiteral
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
    fun findSymbolAt(module: Module, line: Int, column: Int): SymbolAtPosition? {
        // LSP uses 0-based, Prog8 AST uses 1-based
        val targetLine = line + 1
        val targetCol = column + 1

        // First pass: look for declarations at this position
        for (stmt in module.statements) {
            val result = findSymbolInStatement(stmt, targetLine, targetCol)
            if (result != null) return result
        }
        
        // Second pass: look for references and resolve them
        return findReferenceInModule(module, targetLine, targetCol)
    }
    
    /**
     * Search for identifier references in the module and resolve them to declarations.
     * Simple unscoped lookup - just matches by name.
     */
    private fun findReferenceInModule(module: Module, targetLine: Int, targetCol: Int): SymbolAtPosition? {
        // Collect all blocks and subroutines for simple name-based lookup
        val blocks = mutableMapOf<String, SymbolAtPosition>()
        val subroutines = mutableMapOf<String, SymbolAtPosition>()
        
        for (stmt in module.statements) {
            collectSymbols(stmt, blocks, subroutines)
        }
        
        // Search for references
        for (stmt in module.statements) {
            if (stmt is Block) {
                val refResult = findReferenceInBlock(stmt, targetLine, targetCol, blocks, subroutines)
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
        blocks: Map<String, SymbolAtPosition>,
        subroutines: Map<String, SymbolAtPosition>
    ): SymbolAtPosition? {
        val visibleVars = mutableMapOf<String, SymbolAtPosition>()
        
        for (stmt in block.statements) {
            // Check for references before adding declarations
            val refResult = findReferenceInStatement(stmt, targetLine, targetCol, visibleVars, blocks, subroutines)
            if (refResult != null) return refResult
            
            // If this is a subroutine, search inside it too
            if (stmt is Subroutine) {
                val subRefResult = findReferenceInSubroutine(stmt, targetLine, targetCol, blocks, subroutines)
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
            val refResult = findReferenceInStatement(stmt, targetLine, targetCol, visibleVars, blocks, subroutines)
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

    private fun findSymbolInStatement(stmt: Statement, targetLine: Int, targetCol: Int): SymbolAtPosition? {
        return when (stmt) {
            is Block -> findSymbolInBlock(stmt, targetLine, targetCol)
            is Subroutine -> findSymbolInSubroutine(stmt, targetLine, targetCol)
            is VarDecl -> findSymbolInVarDecl(stmt, targetLine, targetCol)
            is StructDecl -> findSymbolInStruct(stmt, targetLine, targetCol)
            else -> null
        }
    }

    private fun findSymbolInBlock(block: Block, targetLine: Int, targetCol: Int): SymbolAtPosition? {
        // Check if position is on the block name itself
        if (isPositionOnName(block.position, targetLine, targetCol, block.name)) {
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
            val result = findSymbolInStatement(stmt, targetLine, targetCol)
            if (result != null) return result
        }

        return null
    }

    private fun findSymbolInSubroutine(sub: Subroutine, targetLine: Int, targetCol: Int): SymbolAtPosition? {
        // Check if position is on the subroutine name (declaration)
        if (isPositionOnName(sub.position, targetLine, targetCol, sub.name)) {
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
        visibleVars: Map<String, SymbolAtPosition>,
        blocks: Map<String, SymbolAtPosition>,
        subroutines: Map<String, SymbolAtPosition>
    ): SymbolAtPosition? {
        return when (stmt) {
            is Assignment -> {
                // Check if cursor is on the target identifier
                if (stmt.target.identifier != null && 
                    isPositionOnName(stmt.target.identifier!!.position, targetLine, targetCol, stmt.target.identifier!!.nameInSource.lastOrNull() ?: "")) {
                    val varName = stmt.target.identifier!!.nameInSource.lastOrNull() ?: ""
                    return visibleVars[varName]
                }
                // Check if cursor is on an identifier in the value expression
                findReferenceInExpression(stmt.value, targetLine, targetCol, visibleVars, blocks, subroutines)
            }
            is VarDecl -> {
                // Check if cursor is on the initializer expression
                stmt.value?.let { value ->
                    findReferenceInExpression(value, targetLine, targetCol, visibleVars, blocks, subroutines)
                }
            }
            is FunctionCallStatement -> {
                // Check if cursor is on the function name (handles both qualified and unqualified names)
                val funcName = stmt.target.nameInSource.lastOrNull() ?: ""
                if (isPositionOnName(stmt.target.position, targetLine, targetCol, funcName)) {
                    // Simple unscoped lookup - just match by name
                    return subroutines[funcName]
                }
                // Also check if cursor is on the qualifier (e.g., "data" in "data.set_both")
                if (stmt.target.nameInSource.size > 1) {
                    val qualifier = stmt.target.nameInSource.firstOrNull() ?: ""
                    if (isPositionOnName(stmt.target.position, targetLine, targetCol, qualifier)) {
                        return blocks[qualifier]
                    }
                }
                // Check arguments for variable references
                for (arg in stmt.args) {
                    val refResult = findReferenceInExpression(arg, targetLine, targetCol, visibleVars, blocks, subroutines)
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
        visibleVars: Map<String, SymbolAtPosition>,
        blocks: Map<String, SymbolAtPosition>,
        subroutines: Map<String, SymbolAtPosition>
    ): SymbolAtPosition? {
        return when (expr) {
            is prog8.ast.expressions.IdentifierReference -> {
                val identName = expr.nameInSource.lastOrNull() ?: ""
                if (isPositionOnName(expr.position, targetLine, targetCol, identName)) {
                    // Simple unscoped lookup: check vars first, then blocks, then subroutines
                    return visibleVars[identName] ?: blocks[identName] ?: subroutines[identName]
                }
                // Also check if cursor is on a qualifier in a qualified name
                for (part in expr.nameInSource) {
                    if (isPositionOnName(expr.position, targetLine, targetCol, part)) {
                        return visibleVars[part] ?: blocks[part] ?: subroutines[part]
                    }
                }
                null
            }
            is prog8.ast.expressions.BinaryExpression -> {
                findReferenceInExpression(expr.left, targetLine, targetCol, visibleVars, blocks, subroutines)
                    ?: findReferenceInExpression(expr.right, targetLine, targetCol, visibleVars, blocks, subroutines)
            }
            is prog8.ast.expressions.FunctionCallExpression -> {
                // Check function name (handles qualified names)
                val funcName = expr.target.nameInSource.lastOrNull() ?: ""
                if (isPositionOnName(expr.target.position, targetLine, targetCol, funcName)) {
                    return subroutines[funcName]
                }
                // Check qualifiers in qualified names
                for (part in expr.target.nameInSource.dropLast(1)) {
                    if (isPositionOnName(expr.target.position, targetLine, targetCol, part)) {
                        return blocks[part]
                    }
                }
                // Check arguments
                expr.args.firstNotNullOfOrNull { 
                    findReferenceInExpression(it, targetLine, targetCol, visibleVars, blocks, subroutines) 
                }
            }
            else -> null
        }
    }

    private fun findSymbolInVarDecl(varDecl: VarDecl, targetLine: Int, targetCol: Int): SymbolAtPosition? {
        // Check if position is on the variable name
        if (isPositionOnName(varDecl.position, targetLine, targetCol, varDecl.name)) {
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

    private fun findSymbolInStruct(struct: StructDecl, targetLine: Int, targetCol: Int): SymbolAtPosition? {
        // Check if position is on the struct name
        if (isPositionOnName(struct.position, targetLine, targetCol, struct.name)) {
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

    private fun isPositionOnName(position: Position, targetLine: Int, targetCol: Int, name: String): Boolean {
        if (position.line != targetLine) return false
        // Check if column is within the name's column range
        // AST positions are 1-based, name starts at startCol
        return targetCol >= position.startCol && targetCol <= position.startCol + name.length - 1
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
     * Find all references to a symbol by name in the module.
     */
    fun findAllReferences(module: Module, symbolName: String, includeDeclaration: Boolean, uri: String): List<org.eclipse.lsp4j.Location> {
        val locations = mutableListOf<org.eclipse.lsp4j.Location>()
        
        for (stmt in module.statements) {
            findReferencesInStatement(stmt, symbolName, locations, includeDeclaration, uri)
        }
        
        return locations
    }
    
    private fun findReferencesInStatement(
        stmt: prog8.ast.statements.Statement,
        symbolName: String,
        locations: MutableList<org.eclipse.lsp4j.Location>,
        includeDeclaration: Boolean,
        uri: String
    ) {
        when (stmt) {
            is prog8.ast.statements.VarDecl -> {
                if (includeDeclaration && stmt.name == symbolName) {
                    locations.add(stmt.position.toLspLocation(uri))
                }
                stmt.value?.let { value ->
                    findReferencesInExpression(value, symbolName, locations, uri)
                }
            }
            is prog8.ast.statements.Assignment -> {
                stmt.target.identifier?.let { ident ->
                    if (ident.nameInSource.lastOrNull() == symbolName) {
                        locations.add(ident.position.toLspLocation(uri))
                    }
                }
                findReferencesInExpression(stmt.value, symbolName, locations, uri)
            }
            is prog8.ast.statements.FunctionCallStatement -> {
                val funcName = stmt.target.nameInSource.lastOrNull()
                if (funcName == symbolName) {
                    locations.add(stmt.target.position.toLspLocation(uri))
                }
                for (arg in stmt.args) {
                    findReferencesInExpression(arg, symbolName, locations, uri)
                }
            }
            is prog8.ast.statements.Block -> {
                if (includeDeclaration && stmt.name == symbolName) {
                    locations.add(stmt.position.toLspLocation(uri))
                }
                for (innerStmt in stmt.statements) {
                    findReferencesInStatement(innerStmt, symbolName, locations, includeDeclaration, uri)
                }
            }
            is prog8.ast.statements.Subroutine -> {
                if (includeDeclaration && stmt.name == symbolName) {
                    locations.add(stmt.position.toLspLocation(uri))
                }
                for (innerStmt in stmt.statements) {
                    findReferencesInStatement(innerStmt, symbolName, locations, includeDeclaration, uri)
                }
            }
            else -> {}
        }
    }
    
    private fun findReferencesInExpression(
        expr: prog8.ast.expressions.Expression,
        symbolName: String,
        locations: MutableList<org.eclipse.lsp4j.Location>,
        uri: String
    ) {
        when (expr) {
            is prog8.ast.expressions.IdentifierReference -> {
                if (expr.nameInSource.lastOrNull() == symbolName) {
                    locations.add(expr.position.toLspLocation(uri))
                }
            }
            is prog8.ast.expressions.BinaryExpression -> {
                findReferencesInExpression(expr.left, symbolName, locations, uri)
                findReferencesInExpression(expr.right, symbolName, locations, uri)
            }
            is prog8.ast.expressions.FunctionCallExpression -> {
                if (expr.target.nameInSource.lastOrNull() == symbolName) {
                    locations.add(expr.target.position.toLspLocation(uri))
                }
                for (arg in expr.args) {
                    findReferencesInExpression(arg, symbolName, locations, uri)
                }
            }
            else -> {}
        }
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
        
        for (stmt in module.statements) {
            val result = findFunctionCallInStatement(stmt, targetLine, targetCol)
            if (result != null) return result
        }
        
        return null
    }
    
    private fun findFunctionCallInStatement(
        stmt: prog8.ast.statements.Statement,
        targetLine: Int,
        targetCol: Int
    ): FunctionCallInfo? {
        return when (stmt) {
            is prog8.ast.statements.FunctionCallStatement -> {
                if (stmt.target.position.line == targetLine &&
                    targetCol >= stmt.target.position.startCol &&
                    targetCol <= stmt.target.position.endCol) {
                    // Cursor is on the function name, count commas to find param index
                    return FunctionCallInfo(
                        stmt.target.nameInSource.lastOrNull() ?: "",
                        0 // Default to first parameter
                    )
                }
                null
            }
            is prog8.ast.statements.Block -> {
                for (innerStmt in stmt.statements) {
                    val result = findFunctionCallInStatement(innerStmt, targetLine, targetCol)
                    if (result != null) return result
                }
                null
            }
            is prog8.ast.statements.Subroutine -> {
                for (innerStmt in stmt.statements) {
                    val result = findFunctionCallInStatement(innerStmt, targetLine, targetCol)
                    if (result != null) return result
                }
                null
            }
            else -> null
        }
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
