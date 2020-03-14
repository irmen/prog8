package prog8.ast.base

import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.processing.*
import prog8.compiler.CompilationOptions
import prog8.optimizer.FlattenAnonymousScopesAndRemoveNops


// the name of the subroutine that should be called for every block to initialize its variables
internal const val initvarsSubName="prog8_init_vars"


internal fun Program.removeNopsFlattenAnonScopes() {
    val flattener = FlattenAnonymousScopesAndRemoveNops()
    flattener.visit(this)
}


internal fun Program.checkValid(compilerOptions: CompilationOptions, errors: ErrorReporter) {
    val checker = AstChecker(this, compilerOptions, errors)
    checker.visit(this)
}


internal fun Program.anonscopeVarsCleanup(errors: ErrorReporter) {
    val mover = AnonymousScopeVarsCleanup(errors)
    mover.visit(this)
}


internal fun Program.reorderStatements() {
    val initvalueCreator = VarInitValueAndAddressOfCreator(this)
    initvalueCreator.visit(this)

    val checker = StatementReorderer(this)
    checker.visit(this)
}

internal fun Program.addTypecasts(errors: ErrorReporter) {
    val caster = TypecastsAdder(this, errors)
    caster.visit(this)
}

internal fun Module.checkImportedValid(errors: ErrorReporter) {
    val checker = ImportedModuleDirectiveRemover(errors)
    checker.visit(this)
}

internal fun Program.checkRecursion(errors: ErrorReporter) {
    val checker = AstRecursionChecker(namespace, errors)
    checker.visit(this)
    checker.processMessages(name)
}


internal fun Program.checkIdentifiers(errors: ErrorReporter) {
    val checker = AstIdentifiersChecker(this, errors)
    checker.visit(this)

    if(modules.map {it.name}.toSet().size != modules.size) {
        throw FatalAstException("modules should all be unique")
    }
}


internal fun Program.makeForeverLoops() {
    val checker = MakeForeverLoops()
    checker.visit(this)
}
