package il65.ast

import il65.functions.BuiltinFunctionNames
import il65.parser.ParsingFailedError

/**
 * Checks the validity of all identifiers (no conflicts)
 * Also builds a list of all (scoped) symbol definitions
 */

fun Module.checkIdentifiers(): MutableMap<String, IStatement> {
    val checker = AstIdentifiersChecker()
    this.process(checker)
    val checkResult = checker.result()
    checkResult.forEach {
        System.err.println(it)
    }
    if(checkResult.isNotEmpty())
        throw ParsingFailedError("There are ${checkResult.size} errors in module '$name'.")
    return checker.symbols
}


class AstIdentifiersChecker : IAstProcessor {
    private val checkResult: MutableList<AstException> = mutableListOf()

    var symbols: MutableMap<String, IStatement> = mutableMapOf()
        private set

    fun result(): List<AstException> {
        return checkResult
    }

    private fun nameError(name: String, position: Position?, existing: IStatement) {
        checkResult.add(NameError("name conflict '$name', first defined in ${existing.position?.file} line ${existing.position?.line}", position))
    }

    override fun process(block: Block): IStatement {
        val scopedName = block.scopedname.joinToString(".")
        val existing = symbols[scopedName]
        if(existing!=null) {
            nameError(block.name, block.position, existing)
        } else {
            symbols[scopedName] = block
        }
        return super.process(block)
    }

    override fun process(decl: VarDecl): IStatement {
        if(BuiltinFunctionNames.contains(decl.name))
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", decl.position))

        val scopedName = decl.scopedname.joinToString(".")
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
            val scopedName = subroutine.scopedname.joinToString(".")
            val existing = symbols[scopedName]
            if (existing != null) {
                nameError(subroutine.name, subroutine.position, existing)
            } else {
                symbols[scopedName] = subroutine
            }
        }
        return super.process(subroutine)
    }

    override fun process(label: Label): IStatement {
        if(BuiltinFunctionNames.contains(label.name)) {
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", label.position))
        } else {
            val scopedName = label.scopedname.joinToString(".")
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
