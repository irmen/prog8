package prog8.compiler.astprocessing

import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.code.core.CompilationOptions
import prog8.code.core.IErrorReporter


/**
 * Postprocessing pass for the simplified AST.
 * Runs after AST simplification and before code generation.
 */
internal fun postprocessSimplifiedAst(
    program: PtProgram,
    st: SymbolTable,
    option: CompilationOptions,
    errors: IErrorReporter
) {
    DeferProcessor.process(program, st, errors)
    SubtypeResolver.resolve(program, st)
}
