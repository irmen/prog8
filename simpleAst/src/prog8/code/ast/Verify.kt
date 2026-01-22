package prog8.code.ast

import prog8.code.SymbolTable
import prog8.code.core.CompilationOptions
import prog8.code.core.IErrorReporter

fun verifyFinalAstBeforeAsmGen(program: PtProgram, options: CompilationOptions, st: SymbolTable, errors: IErrorReporter) {
    walkAst(program) { node, _ ->
        if(node is PtSub)
            require(node.children[0] is PtSubSignature)

        require(node is PtProgram || node.parentHasBeenSet()) {
            "node $node hasn't got a parent node"
        }

        if(node is PtExpression) {
            if(node.type.isStructInstance) {
                if(node.parent is PtTypeCast || node.parent !is PtExpression)
                    errors.err("no support for using struct instances in expressions in this way yet (use pointer to struct instead)", node.position)
            }
        }
    }
}
