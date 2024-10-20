package prog8.code.ast

import prog8.code.SymbolTable
import prog8.code.core.*

fun verifyFinalAstBeforeAsmGen(program: PtProgram, options: CompilationOptions, st: SymbolTable, errors: IErrorReporter) {
/*
    walkAst(program) { node, _ ->
        if(node is PtVariable) {
            if(node.value!=null) {
                // require(node.type in ArrayDatatypes || node.type==DataType.STR) { "final check before asmgen: only string and array variables can still have an init value ${node.name} ${node.type} ${node.position}"}
            }
        }
    }
*/
}
