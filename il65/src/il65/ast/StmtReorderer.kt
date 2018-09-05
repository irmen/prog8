package il65.ast

class StatementReorderer: IAstProcessor {
    // Reorders the statements in a way the compiler needs.
    // - 'main' block must be the very first statement.
    // - in every scope:
    //      -- the directives '%output', '%launcher', '%zeropage', '%address' and '%option' will come first.
    //      -- all vardecls then follow.
    //      -- the remaining statements then follow in their original order.
    // - the 'start' subroutine in the 'main' block will be moved to the top immediately following the directives.

    val directivesToMove = setOf("%output", "%launcher", "%zeropage", "%address", "%option")

    override fun process(node: Module) {
        val mainBlock = node.statements.single { it is Block && it.name=="main" }
        node.statements.remove(mainBlock)
        node.statements.add(0, mainBlock)
        val varDecls = node.statements.filter { it is VarDecl }
        node.statements.removeAll(varDecls)
        node.statements.addAll(0, varDecls)
        val directives = node.statements.filter {it is Directive && directivesToMove.contains(it.directive)}
        node.statements.removeAll(directives)
        node.statements.addAll(0, directives)
        super.process(node)
    }

    override fun process(node: Block): IStatement {
        val startSub = node.statements.singleOrNull {it is Subroutine && it.name=="start"}
        if(startSub!=null) {
            node.statements.remove(startSub)
            node.statements.add(0, startSub)
        }
        val varDecls = node.statements.filter { it is VarDecl }
        node.statements.removeAll(varDecls)
        node.statements.addAll(0, varDecls)
        val directives = node.statements.filter {it is Directive && directivesToMove.contains(it.directive)}
        node.statements.removeAll(directives)
        node.statements.addAll(0, directives)
        return super.process(node)
    }

    override fun process(node: Subroutine): IStatement {
        val varDecls = node.statements.filter { it is VarDecl }
        node.statements.removeAll(varDecls)
        node.statements.addAll(0, varDecls)
        val directives = node.statements.filter {it is Directive && directivesToMove.contains(it.directive)}
        node.statements.removeAll(directives)
        node.statements.addAll(0, directives)
        return super.process(node)
    }
}
