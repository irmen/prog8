package prog8.ast

import prog8.functions.BuiltinFunctions

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
        if(decl.name in BuiltinFunctions)
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
        if(subroutine.name in BuiltinFunctions) {
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", subroutine.position))
        } else {
            if (subroutine.parameters.any { it.name in BuiltinFunctions })
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
            val definedNamesCorrespondingToParameters = definedNames.filter { it.key in paramNames }
            for(name in definedNamesCorrespondingToParameters) {
                if(name.value.position != subroutine.position)
                    nameError(name.key, name.value.position, subroutine)
            }

            // inject subroutine params as local variables (if they're not there yet) (for non-kernel subroutines)
            if(subroutine.asmAddress==null) {
                subroutine.parameters
                        .filter { !definedNames.containsKey(it.name) }
                        .forEach {
                            val vardecl = VarDecl(VarDeclType.VAR, it.type, null, it.name, null, subroutine.position)
                            vardecl.linkParents(subroutine)
                            subroutine.statements.add(0, vardecl)
                        }
            }
        }
        return super.process(subroutine)
    }

    override fun process(label: Label): IStatement {
        if(label.name in BuiltinFunctions) {
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

    override fun process(forLoop: ForLoop): IStatement {
        // if the for loop as a decltype, it means to declare the loopvar inside the loop body
        // rather than reusing an already declared loopvar from an outer scope.
        if(forLoop.loopRegister!=null && forLoop.decltype!=null) {
            checkResult.add(SyntaxError("register loop variables cannot be explicitly declared with a datatype", forLoop.position))
        } else {
            val loopVar = forLoop.loopVar!!
            val varName = loopVar.nameInSource.last()
            when (forLoop.decltype) {
                DataType.UBYTE, DataType.UWORD -> {
                    val existing = if(forLoop.body.isEmpty()) null else forLoop.body.lookup(loopVar.nameInSource, forLoop.body.statements.first())
                    if(existing==null) {
                        val vardecl = VarDecl(VarDeclType.VAR, forLoop.decltype, null, varName, null, loopVar.position)
                        vardecl.linkParents(forLoop.body)
                        forLoop.body.statements.add(0, vardecl)
                        forLoop.loopVar.parent = forLoop.body   // loopvar 'is defined in the body'
                    }
                }
                null -> {}
                else -> checkResult.add(SyntaxError("loop variables can only be a byte or word", forLoop.position))
            }
        }
        return super.process(forLoop)
    }
}
