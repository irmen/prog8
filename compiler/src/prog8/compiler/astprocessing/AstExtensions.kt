package prog8.compiler.astprocessing

import prog8.ast.IStringEncoding
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.FatalAstException
import prog8.ast.expressions.CharLiteral
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.Directive
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
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
    while(errors.noErrors() && fixer.applyModifications()>0) {
        fixer.visit(this)
    }
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

internal fun Program.charLiteralsToUByteLiterals(errors: IErrorReporter, enc: IStringEncoding) {
    val walker = object : AstWalker() {
        override fun after(char: CharLiteral, parent: Node): Iterable<IAstModification> {
            return listOf(IAstModification.ReplaceNode(
                char,
                NumericLiteralValue(DataType.UBYTE, enc.encodeString(char.value.toString(), char.altEncoding)[0].toInt(), char.position),
                parent
            ))
        }
    }
    walker.visit(this)
    walker.applyModifications()
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

internal fun Program.checkIdentifiers(errors: IErrorReporter, options: CompilationOptions) {

    val checker2 = AstIdentifiersChecker(this, errors, options.compTarget)
    checker2.visit(this)

    if(errors.noErrors()) {
        val transforms = AstVariousTransforms(this)
        transforms.visit(this)
        transforms.applyModifications()
        val lit2decl = LiteralsToAutoVars(this)
        lit2decl.visit(this)
        lit2decl.applyModifications()
    }

    // Check if each module has a unique name.
    // If not report those that haven't.
    // TODO: move check for unique module names to earlier stage and/or to unit tests
    val namesToModules = mapOf<String, MutableList<prog8.ast.Module>>().toMutableMap()
    for (m in modules) {
        var others = namesToModules[m.name]
        if (others == null) {
            namesToModules.put(m.name, listOf(m).toMutableList())
        } else {
            others.add(m)
        }
    }
    val nonUniqueNames = namesToModules.keys
        .map { Pair(it, namesToModules[it]!!.size) }
        .filter { it.second > 1 }
        .map { "\"${it.first}\" (x${it.second})"}
    if (nonUniqueNames.size > 0) {
        throw FatalAstException("modules must have unique names; of the ttl ${modules.size} these have not: $nonUniqueNames")
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
