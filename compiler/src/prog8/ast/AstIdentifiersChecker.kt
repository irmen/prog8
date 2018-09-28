package prog8.ast

import prog8.functions.BuiltinFunctionNames

/**
 * Checks the validity of all identifiers (no conflicts)
 * Also builds a list of all (scoped) symbol definitions
 * Also makes sure that subroutine's parameters also become local variable decls in the subroutine's scope.
 * Finally, it also makes sure the datatype of all Var decls is set correctly.
 */

fun Module.checkIdentifiers(): MutableMap<String, IStatement> {
    val checker = AstIdentifiersChecker()
    this.process(checker)
    printErrors(checker.result(), name)
    return checker.symbols
}


class AstIdentifiersChecker : IAstProcessor {
    private val checkResult: MutableList<AstException> = mutableListOf()

    var symbols: MutableMap<String, IStatement> = mutableMapOf()
        private set

    fun result(): List<AstException> {
        return checkResult
    }

    private fun nameError(name: String, position: Position, existing: IStatement) {
        checkResult.add(NameError("name conflict '$name', first defined in ${existing.position.file} line ${existing.position.line}", position))
    }

    override fun process(block: Block): IStatement {
        val scopedName = block.scopedname
        val existing = symbols[scopedName]
        if(existing!=null) {
            nameError(block.name, block.position, existing)
        } else {
            symbols[scopedName] = block
        }
        return super.process(block)
    }

    override fun process(decl: VarDecl): IStatement {
        // first, check if there are datatype errors on the vardecl
        decl.datatypeErrors.forEach { checkResult.add(it) }

        // now check the identifier
        if(BuiltinFunctionNames.contains(decl.name))
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", decl.position))

        val scopedName = decl.scopedname
        val existing = symbols[scopedName]
        if(existing!=null) {
            nameError(decl.name, decl.position, existing)
        } else {
            symbols[scopedName] = decl
        }
        return super.process(decl)
    }

    override fun process(subroutine: Subroutine): IStatement {
        if(BuiltinFunctionNames.contains(subroutine.name)) {
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", subroutine.position))
        } else {
            if (subroutine.parameters.any { BuiltinFunctionNames.contains(it.name) })
                checkResult.add(NameError("builtin function name cannot be used as parameter", subroutine.position))

            val scopedName = subroutine.scopedname
            val existing = symbols[scopedName]
            if (existing != null) {
                nameError(subroutine.name, subroutine.position, existing)
            } else {
                symbols[scopedName] = subroutine
            }

            // check that there are no local variables that redefine the subroutine's parameters
            val definedNames = subroutine.labelsAndVariables()
            val paramNames = subroutine.parameters.map { it.name }
            val definedNamesCorrespondingToParameters = definedNames.filter { paramNames.contains(it.key) }
            for(name in definedNamesCorrespondingToParameters) {
                if(name.value.position != subroutine.position)
                    nameError(name.key, name.value.position, subroutine)
            }

            // inject subroutine params as local variables (if they're not there yet)
            subroutine.parameters
                    .filter { !definedNames.containsKey(it.name) }
                    .forEach {
                        val vardecl = VarDecl(VarDeclType.VAR, it.type, null, it.name, null, subroutine.position)
                        vardecl.linkParents(subroutine)
                        subroutine.statements.add(0, vardecl)
                    }
        }
        return super.process(subroutine)
    }

    override fun process(label: Label): IStatement {
        if(BuiltinFunctionNames.contains(label.name)) {
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", label.position))
        } else {
            val scopedName = label.scopedname
            val existing = symbols[scopedName]
            if (existing != null) {
                nameError(label.name, label.position, existing)
            } else {
                symbols[scopedName] = label
            }
        }
        return super.process(label)
    }
}
