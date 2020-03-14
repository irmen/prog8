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


internal fun Program.checkValid(compilerOptions: CompilationOptions, compilerMessages: MutableList<CompilerMessage>) {
    val checker = AstChecker(this, compilerOptions, compilerMessages)
    checker.visit(this)
}


internal fun Program.anonscopeVarsCleanup(compilerMessages: MutableList<CompilerMessage>) {
    val mover = AnonymousScopeVarsCleanup(this, compilerMessages)
    mover.visit(this)
}


internal fun Program.reorderStatements() {
    val initvalueCreator = VarInitValueAndAddressOfCreator(this)
    initvalueCreator.visit(this)

    val checker = StatementReorderer(this)
    checker.visit(this)
}

internal fun Program.addTypecasts(compilerMessages: MutableList<CompilerMessage>) {
    val caster = TypecastsAdder(this, compilerMessages)
    caster.visit(this)
}

internal fun Module.checkImportedValid(compilerMessages: MutableList<CompilerMessage>) {
    val checker = ImportedModuleDirectiveRemover(compilerMessages)
    checker.visit(this)
}

internal fun Program.checkRecursion(compilerMessages: MutableList<CompilerMessage>) {
    val checker = AstRecursionChecker(namespace, compilerMessages)
    checker.visit(this)
    checker.processMessages(name)
}


internal fun Program.checkIdentifiers(compilerMessages: MutableList<CompilerMessage>) {
    val checker = AstIdentifiersChecker(this, compilerMessages)
    checker.visit(this)

    if(modules.map {it.name}.toSet().size != modules.size) {
        throw FatalAstException("modules should all be unique")
    }
}


internal fun Program.makeForeverLoops() {
    val checker = MakeForeverLoops()
    checker.visit(this)
}
