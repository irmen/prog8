package prog8.ast

class StatementReorderer: IAstProcessor {
    // Reorders the statements in a way the compiler needs.
    // - 'main' block must be the very first statement.
    // - in every scope:
    //      -- the directives '%output', '%launcher', '%zeropage', '%address' and '%option' will come first.
    //      -- all vardecls then follow.
    //      -- the remaining statements then follow in their original order.
    //
    // - the 'start' subroutine in the 'main' block will be moved to the top immediately following the directives.
    // - all other subroutines will be moved to the end of their block.

    private val directivesToMove = setOf("%output", "%launcher", "%zeropage", "%address", "%option")

    override fun process(module: Module) {
        val mainBlock = module.statements.single { it is Block && it.name=="main" }
        module.statements.remove(mainBlock)
        module.statements.add(0, mainBlock)
        val varDecls = module.statements.filter { it is VarDecl }
        module.statements.removeAll(varDecls)
        module.statements.addAll(0, varDecls)
        val directives = module.statements.filter {it is Directive && it.directive in directivesToMove}
        module.statements.removeAll(directives)
        module.statements.addAll(0, directives)
        super.process(module)
    }

    override fun process(block: Block): IStatement {
        val subroutines = block.statements.asSequence().filter { it is Subroutine }.map { it as Subroutine }.toList()
        var numSubroutinesAtEnd = 0
        // move all subroutines to the end of the block
        for (subroutine in subroutines) {
            if(subroutine.name!="start" || block.name!="main") {
                block.statements.remove(subroutine)
                block.statements.add(subroutine)
            }
            numSubroutinesAtEnd++
        }
        // move the "start" subroutine to the top
        if(block.name=="main") {
            block.statements.singleOrNull { it is Subroutine && it.name == "start" } ?.let {
                block.statements.remove(it)
                block.statements.add(0, it)
                numSubroutinesAtEnd--
            }
        }

        // make sure there is a 'return' in front of the first subroutine
        // (if it isn't the first statement in the block itself, and isn't the program's entrypoint)
        if(block.statements.size > numSubroutinesAtEnd) {
            val firstSub = block.statements[block.statements.size - numSubroutinesAtEnd] as Subroutine
            if(firstSub.name != "start" && block.name != "main") {
                val stmtBeforeFirstSub = block.statements[block.statements.size - numSubroutinesAtEnd - 1]
                if (stmtBeforeFirstSub !is Return
                        && stmtBeforeFirstSub !is Jump
                        && stmtBeforeFirstSub !is Subroutine
                        && stmtBeforeFirstSub !is BuiltinFunctionStatementPlaceholder) {
                    val ret = Return(emptyList(), stmtBeforeFirstSub.position)
                    ret.linkParents(stmtBeforeFirstSub.parent)
                    block.statements.add(block.statements.size - numSubroutinesAtEnd, ret)
                }
            }
        }

        val varDecls = block.statements.filter { it is VarDecl }
        block.statements.removeAll(varDecls)
        block.statements.addAll(0, varDecls)
        val directives = block.statements.filter {it is Directive && it.directive in directivesToMove}
        block.statements.removeAll(directives)
        block.statements.addAll(0, directives)
        return super.process(block)
    }

    override fun process(subroutine: Subroutine): IStatement {
        val varDecls = subroutine.statements.filter { it is VarDecl }
        subroutine.statements.removeAll(varDecls)
        subroutine.statements.addAll(0, varDecls)
        val directives = subroutine.statements.filter {it is Directive && it.directive in directivesToMove}
        subroutine.statements.removeAll(directives)
        subroutine.statements.addAll(0, directives)
        return super.process(subroutine)
    }
}
