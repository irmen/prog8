package prog8lsp

import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.SymbolKind
import prog8.ast.Module
import prog8.ast.statements.*

/**
 * Extracts document symbols from a Prog8 AST.
 * Produces a hierarchical structure of DocumentSymbol objects.
 */
class SymbolExtractor {
    private val symbols = mutableListOf<DocumentSymbol>()

    /**
     * Extract symbols from a Prog8 module.
     * Returns a list of DocumentSymbol representing the hierarchical structure.
     */
    fun extract(module: Module): List<DocumentSymbol> {
        symbols.clear()
        
        // Visit all top-level statements in the module
        module.statements.forEach { stmt ->
            when (stmt) {
                is Block -> visitBlock(stmt)
                is Subroutine -> visitSubroutine(stmt)
                is VarDecl -> visitVariable(stmt)
                is StructDecl -> visitStruct(stmt)
                else -> {} // Ignore other statement types
            }
        }
        
        return symbols
    }

    private fun visitBlock(block: Block) {
        val range = block.position.toLspRange()
        val selectionRange = range // Could be more precise (just the name)

        val blockSymbol = DocumentSymbol(
            block.name,
            SymbolKind.Module,
            range,
            selectionRange
        )
        blockSymbol.children = mutableListOf() // Initialize children list

        // Visit children of the block
        block.statements.forEach { stmt ->
            when (stmt) {
                is Subroutine -> {
                    val subSymbol = visitSubroutineInternal(stmt)
                    if (subSymbol != null) {
                        blockSymbol.children.add(subSymbol)
                    }
                }
                is VarDecl -> {
                    val varSymbol = visitVariableInternal(stmt)
                    if (varSymbol != null) {
                        blockSymbol.children.add(varSymbol)
                    }
                }
                is StructDecl -> {
                    val structSymbol = visitStructInternal(stmt)
                    if (structSymbol != null) {
                        blockSymbol.children.add(structSymbol)
                    }
                }
                else -> {} // Ignore other statement types
            }
        }

        symbols.add(blockSymbol)
    }

    private fun visitSubroutine(sub: Subroutine) {
        val symbol = visitSubroutineInternal(sub)
        if (symbol != null) {
            symbols.add(symbol)
        }
    }

    private fun visitSubroutineInternal(sub: Subroutine): DocumentSymbol? {
        val range = sub.position.toLspRange()
        val selectionRange = range // Could be just the name
        
        return DocumentSymbol(
            sub.name,
            SymbolKind.Function,
            range,
            selectionRange
        )
    }

    private fun visitVariable(varDecl: VarDecl) {
        val symbol = visitVariableInternal(varDecl)
        if (symbol != null) {
            symbols.add(symbol)
        }
    }

    private fun visitVariableInternal(varDecl: VarDecl): DocumentSymbol? {
        val range = varDecl.position.toLspRange()
        val selectionRange = range
        
        // Constants are VarDecls with type CONST
        val symbolKind = if (varDecl.type == VarDeclType.CONST) {
            SymbolKind.Constant
        } else {
            SymbolKind.Variable
        }
        
        return DocumentSymbol(
            varDecl.name,
            symbolKind,
            range,
            selectionRange
        )
    }

    private fun visitStruct(struct: StructDecl) {
        val symbol = visitStructInternal(struct)
        if (symbol != null) {
            symbols.add(symbol)
        }
    }

    private fun visitStructInternal(struct: StructDecl): DocumentSymbol? {
        val range = struct.position.toLspRange()
        val selectionRange = range

        val structSymbol = DocumentSymbol(
            struct.name,
            SymbolKind.Struct,
            range,
            selectionRange
        )
        structSymbol.children = mutableListOf() // Initialize children list

        // Add struct fields as children
        // Note: struct fields are just (DataType, String) pairs without individual positions
        struct.fields.forEachIndexed { index, field ->
            // Use the struct's range for all fields (not ideal but works for now)
            val fieldSymbol = DocumentSymbol(
                field.second,  // field name
                SymbolKind.Field,
                range,  // Use struct's range since fields don't have individual positions
                range
            )
            structSymbol.children.add(fieldSymbol)
        }
        
        return structSymbol
    }
}
