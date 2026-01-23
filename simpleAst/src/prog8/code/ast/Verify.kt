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

        if(options.warnImplicitTypeCast) {
            if (node is PtTypeCast) {
                if (node.type.largerSizeThan(node.value.type)) {
                    if (node.implicit) {
                        errors.warn("implicit type cast to larger type: ${node.value.type} to ${node.type}", node.position)
                    }
                }
            }
            else if (node is PtAssignment) {
                val targetDt = (node.children.first() as PtAssignTarget).type
                if (targetDt.largerSizeThan(node.value.type)) {
                    errors.warn("assignment to larger type: ${node.value.type} to $targetDt", node.position)
                }
            }
        }
    }
}
