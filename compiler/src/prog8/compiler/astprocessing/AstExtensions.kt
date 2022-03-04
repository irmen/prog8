package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.expressions.CharLiteral
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.Directive
import prog8.ast.statements.VarDeclOrigin
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compilerinterface.*


internal fun Program.checkValid(errors: IErrorReporter, compilerOptions: CompilationOptions) {
    // semantic analysis to see if the program is valid.
    val parentChecker = ParentNodeChecker()
    parentChecker.visit(this)
    val checker = AstChecker(this, errors, compilerOptions)
    checker.visit(this)
}

internal fun Program.processAstBeforeAsmGeneration(compilerOptions: CompilationOptions, errors: IErrorReporter) {
    val fixer = BeforeAsmAstChanger(this, compilerOptions, errors)
    fixer.visit(this)
    while(errors.noErrors() && fixer.applyModifications()>0) {
        fixer.visit(this)
    }
    val cleaner = BeforeAsmTypecastCleaner(this, errors)
    cleaner.visit(this)
    while(errors.noErrors() && cleaner.applyModifications()>0) {
        cleaner.visit(this)
    }
}

internal fun Program.reorderStatements(errors: IErrorReporter, options: CompilationOptions) {
    val reorder = StatementReorderer(this, errors, options)
    reorder.visit(this)
    if(errors.noErrors()) {
        reorder.applyModifications()
        reorder.visit(this)
        if(errors.noErrors())
            reorder.applyModifications()
    }
}

internal fun Program.charLiteralsToUByteLiterals(target: ICompilationTarget, errors: IErrorReporter) {
    val walker = object : AstWalker() {
        override fun after(char: CharLiteral, parent: Node): Iterable<IAstModification> {
            require(char.encoding != Encoding.DEFAULT)
            if(char.encoding != Encoding.DEFAULT && char.encoding !in target.supportedEncodings) {
                errors.err("compilation target doesn't support this text encoding", char.position)
                return noModifications
            }
            return listOf(IAstModification.ReplaceNode(
                char,
                NumericLiteral(DataType.UBYTE, target.encodeString(char.value.toString(), char.encoding)[0].toDouble(), char.position),
                parent
            ))
        }
    }
    walker.visit(this)
    walker.applyModifications()
}

internal fun Program.addTypecasts(errors: IErrorReporter, options: CompilationOptions) {
    val caster = TypecastsAdder(this, options, errors)
    caster.visit(this)
    caster.applyModifications()
}

fun Program.desugaring(errors: IErrorReporter): Int {
    val desugar = CodeDesugarer(this, errors)
    desugar.visit(this)
    return desugar.applyModifications()
}

internal fun Program.verifyFunctionArgTypes(errors: IErrorReporter) {
    val fixer = VerifyFunctionArgTypes(this, errors)
    fixer.visit(this)
}

internal fun Program.preprocessAst(errors: IErrorReporter, target: ICompilationTarget) {
    val transforms = AstPreprocessor(this, errors, target)
    transforms.visit(this)
    var mods = transforms.applyModifications()
    while(mods>0)
        mods = transforms.applyModifications()
}

internal fun Program.checkIdentifiers(errors: IErrorReporter, options: CompilationOptions) {

    val checker2 = AstIdentifiersChecker(errors, this, options.compTarget)
    checker2.visit(this)

    if(errors.noErrors()) {
        val transforms = AstVariousTransforms(this)
        transforms.visit(this)
        transforms.applyModifications()
        val lit2decl = LiteralsToAutoVars(this, options.compTarget, errors)
        lit2decl.visit(this)
        if(errors.noErrors())
            lit2decl.applyModifications()
    }
}

internal fun Program.variousCleanups(errors: IErrorReporter, options: CompilationOptions) {
    val process = VariousCleanups(this, errors, options)
    process.visit(this)
    if(errors.noErrors())
        process.applyModifications()
}

internal fun Program.moveMainAndStartToFirst() {
    // the module containing the program entrypoint is moved to the first in the sequence.
    // the "main" block containing the entrypoint is moved to the top in there,
    // and finally the entrypoint subroutine "start" itself is moved to the top in that block.

    val directives = modules[0].statements.filterIsInstance<Directive>()
    val start = this.entrypoint
    val mod = start.definingModule
    val block = start.definingBlock
    moveModuleToFront(mod)
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

internal fun IdentifierReference.isSubroutineParameter(program: Program): Boolean {
    val vardecl = this.targetVarDecl(program)
    if(vardecl!=null && vardecl.origin==VarDeclOrigin.SUBROUTINEPARAM) {
        return vardecl.definingSubroutine?.parameters?.any { it.name==vardecl.name } == true
    }
    return false
}
