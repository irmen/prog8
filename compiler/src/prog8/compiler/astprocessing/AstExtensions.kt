package prog8.compiler.astprocessing

import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.statements.Directive
import prog8.compiler.BeforeAsmGenerationAstChanger
import prog8.compiler.CompilationOptions
import prog8.compiler.IErrorReporter
import prog8.compiler.target.ICompilationTarget


internal fun Program.checkValid(compilerOptions: CompilationOptions, errors: IErrorReporter, compTarget: ICompilationTarget) {
    val checker = AstChecker(this, compilerOptions, errors, compTarget)
    checker.visit(this)
}

internal fun Program.processAstBeforeAsmGeneration(errors: IErrorReporter, compTarget: ICompilationTarget) {
    val fixer = BeforeAsmGenerationAstChanger(this, errors, compTarget)
    fixer.visit(this)
    fixer.applyModifications()
}

internal fun Program.reorderStatements(errors: IErrorReporter) {
    val reorder = StatementReorderer(this, errors)
    reorder.visit(this)
    if(errors.noErrors()) {
        reorder.applyModifications()
        reorder.visit(this)
        if(errors.noErrors())
            reorder.applyModifications()
    }
}

internal fun Program.addTypecasts(errors: IErrorReporter) {
    val caster = TypecastsAdder(this, errors)
    caster.visit(this)
    caster.applyModifications()
}

internal fun Program.verifyFunctionArgTypes() {
    val fixer = VerifyFunctionArgTypes(this)
    fixer.visit(this)
}

internal fun Program.checkIdentifiers(errors: IErrorReporter, compTarget: ICompilationTarget) {

    val checker2 = AstIdentifiersChecker(this, errors, compTarget)
    checker2.visit(this)

    if(errors.noErrors()) {
        val transforms = AstVariousTransforms(this)
        transforms.visit(this)
        transforms.applyModifications()
        val lit2decl = LiteralsToAutoVars(this)
        lit2decl.visit(this)
        lit2decl.applyModifications()
    }

    if (modules.map { it.name }.toSet().size != modules.size) {
        throw FatalAstException("modules should all be unique")
    }
}

internal fun Program.variousCleanups(program: Program, errors: IErrorReporter) {
    val process = VariousCleanups(program, errors)
    process.visit(this)
    if(errors.noErrors())
        process.applyModifications()
}


internal fun Program.moveMainAndStartToFirst() {
    // the module containing the program entrypoint is moved to the first in the sequence.
    // the "main" block containing the entrypoint is moved to the top in there,
    // and finally the entrypoint subroutine "start" itself is moved to the top in that block.

    val directives = modules[0].statements.filterIsInstance<Directive>()
    val start = this.entrypoint()
    val mod = start.definingModule()
    val block = start.definingBlock()
    if(!modules.remove(mod))
        throw FatalAstException("module wrong")
    modules.add(0, mod)
    mod.remove(block)
    var afterDirective = mod.statements.indexOfFirst { it !is Directive }
    if(afterDirective<0)
        mod.statements.add(block)
    else
        mod.statements.add(afterDirective, block)
    block.remove(start)
    afterDirective = block.statements.indexOfFirst { it !is Directive }
    if(afterDirective<0)
        block.statements.add(start)
    else
        block.statements.add(afterDirective, start)

    // overwrite the directives in the module containing the entrypoint
    for(directive in directives) {
        modules[0].statements.removeAll { it is Directive && it.directive == directive.directive }
        modules[0].statements.add(0, directive)
    }
}
