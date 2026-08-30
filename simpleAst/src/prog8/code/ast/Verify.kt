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
        if(node is PtExpression) {
            if(node.type.isString && node !is PtIdentifier) {
                errors.err("str type of expression should have been converted to a concrete pointer type (uword/long/^^ubyte) before SimpleAST", node.position)
            }
        }
        if(node is PtSub) {
            if(node.signature.returns.any { it.isString })
                errors.err("str type in subroutine return type should have been converted to a concrete pointer type (uword/long/^^ubyte) before SimpleAST", node.position)
        }
        if(node is PtSubroutineParameter) {
            if(node.type.isString)
                errors.err("str type in subroutine parameter should have been converted to a concrete pointer type (uword/long/^^ubyte) before SimpleAST", node.position)
        }
        if(node is PtTypeCast) {
            if (node.type.isString)
                errors.err("str type in type cast should have been converted to a concrete pointer type (uword/long/^^ubyte) before SimpleAST", node.position)
        }
        if(node is PtStructDecl) {
            for(f in node.fields) {
                if (f.type.isString)
                    errors.err("str type in struct field should have been converted to a concrete pointer type (uword/long/^^ubyte) before SimpleAST", node.position)
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
        true  // Continue traversal
    }
}
