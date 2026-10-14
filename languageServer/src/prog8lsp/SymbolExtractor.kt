package prog8lsp

import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.SymbolKind
import prog8.ast.Module
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor

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

        module.statements.forEach { stmt ->
            symbolFor(stmt)?.let { symbols.add(it) }
        }

        return symbols
    }

    private fun symbolFor(stmt: Statement): DocumentSymbol? = when (stmt) {
        is Block -> visitBlock(stmt)
        is Subroutine -> visitSubroutine(stmt)
        is VarDecl -> visitVariable(stmt)
        is StructDecl -> visitStruct(stmt)
        is Enumeration -> visitEnum(stmt)
        is Alias -> visitAlias(stmt)
        is Label -> visitLabel(stmt)
        else -> null
    }

    private fun visitBlock(block: Block): DocumentSymbol {
        val range = block.position.toLspRange()

        val blockSymbol = DocumentSymbol(block.name, SymbolKind.Module, range, range)
        blockSymbol.children = block.statements.mapNotNull { symbolFor(it) }.toMutableList()

        return blockSymbol
    }

    private fun visitSubroutine(sub: Subroutine): DocumentSymbol {
        val range = sub.position.toLspRange()

        val subSymbol = DocumentSymbol(sub.name, SymbolKind.Function, range, range)
        val children = sub.parameters.map { visitParameter(it) }.toMutableList()
        // labels, locals and nested declarations can appear anywhere in the body
        children.addAll(collectBodyDeclarations(sub.statements))
        subSymbol.children = children

        return subSymbol
    }

    // Gathers locals, labels and nested type declarations from a statement list, descending
    // into control-flow bodies so deeply nested declarations are found too.
    private fun collectBodyDeclarations(statements: Iterable<Statement>): MutableList<DocumentSymbol> {
        val result = mutableListOf<DocumentSymbol>()
        val visitor = object : IAstVisitor {
            override fun visit(decl: VarDecl) {
                result.add(visitVariable(decl))
                super.visit(decl)
            }

            override fun visit(label: Label) {
                result.add(visitLabel(label))
            }

            override fun visit(enum: Enumeration) {
                result.add(visitEnum(enum))
                super.visit(enum)
            }

            override fun visit(struct: StructDecl) {
                result.add(visitStruct(struct))
                super.visit(struct)
            }

            override fun visit(alias: Alias) {
                result.add(visitAlias(alias))
                super.visit(alias)
            }

            override fun visit(subroutine: Subroutine) {
                // don't descend into nested subroutines
            }
        }
        statements.forEach { it.accept(visitor) }
        return result
    }

    private fun visitParameter(param: SubroutineParameter): DocumentSymbol {
        val range = param.position.toLspRange()
        return DocumentSymbol(param.name, SymbolKind.Variable, range, range)
    }

    private fun visitVariable(varDecl: VarDecl): DocumentSymbol {
        val range = varDecl.position.toLspRange()

        // Constants are VarDecls with type CONST
        val symbolKind = if (varDecl.type == VarDeclType.CONST) {
            SymbolKind.Constant
        } else {
            SymbolKind.Variable
        }

        return DocumentSymbol(varDecl.name, symbolKind, range, range)
    }

    private fun visitStruct(struct: StructDecl): DocumentSymbol {
        val range = struct.position.toLspRange()

        val structSymbol = DocumentSymbol(struct.name, SymbolKind.Struct, range, range)
        // struct fields don't carry individual positions, so they share the struct's range
        structSymbol.children = struct.fields.map { field ->
            DocumentSymbol(field.name, SymbolKind.Field, range, range)
        }.toMutableList()

        return structSymbol
    }

    private fun visitEnum(enum: Enumeration): DocumentSymbol {
        val range = enum.position.toLspRange()

        val enumSymbol = DocumentSymbol(enum.name, SymbolKind.Enum, range, range)
        // enum members don't carry individual positions, so they share the enum's range
        enumSymbol.children = enum.members.map { (name, _) ->
            DocumentSymbol(name, SymbolKind.EnumMember, range, range)
        }.toMutableList()

        return enumSymbol
    }

    private fun visitAlias(alias: Alias): DocumentSymbol {
        val range = alias.position.toLspRange()
        return DocumentSymbol(alias.alias, SymbolKind.Interface, range, range)
    }

    private fun visitLabel(label: Label): DocumentSymbol {
        val range = label.position.toLspRange()
        return DocumentSymbol(label.name, SymbolKind.Key, range, range)
    }
}
