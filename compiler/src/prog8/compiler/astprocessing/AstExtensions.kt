package prog8.compiler.astprocessing

import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.base.ErrorReporter
import prog8.ast.base.FatalAstException
import prog8.ast.statements.Directive
import prog8.compiler.BeforeAsmGenerationAstChanger
import prog8.compiler.CompilationOptions


internal fun Program.checkValid(compilerOptions: CompilationOptions, errors: ErrorReporter) {
    val checker = AstChecker(this, compilerOptions, errors)
    checker.visit(this)
}

internal fun Program.processAstBeforeAsmGeneration(errors: ErrorReporter) {
    val fixer = BeforeAsmGenerationAstChanger(this, errors)
    fixer.visit(this)
    fixer.applyModifications()
}

internal fun Program.reorderStatements(errors: ErrorReporter) {
    val reorder = StatementReorderer(this, errors)
    reorder.visit(this)
    reorder.applyModifications()
}

internal fun Program.addTypecasts(errors: ErrorReporter) {
    val caster = TypecastsAdder(this, errors)
    caster.visit(this)
    caster.applyModifications()
}

internal fun Program.verifyFunctionArgTypes() {
    val fixer = VerifyFunctionArgTypes(this)
    fixer.visit(this)
}

internal fun Module.checkImportedValid() {
    val imr = ImportedModuleDirectiveRemover()
    imr.visit(this, this.parent)
    imr.applyModifications()
}

internal fun Program.checkIdentifiers(errors: ErrorReporter) {

    val checker2 = AstIdentifiersChecker(this, errors)
    checker2.visit(this)

    if(errors.isEmpty()) {
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

internal fun Program.variousCleanups() {
    val process = VariousCleanups()
    process.visit(this)
    process.applyModifications()
}

internal fun Program.moveMainAndStartToFirst() {
    // the module containing the program entrypoint is moved to the first in the sequence.
    // the "main" block containing the entrypoint is moved to the top in there,
    // and finally the entrypoint subroutine "start" itself is moved to the top in that block.

    val directives = modules[0].statements.filterIsInstance<Directive>()
    val start = this.entrypoint()
    if(start!=null) {
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
}
