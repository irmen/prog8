package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.CharLiteral
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.Directive
import prog8.ast.statements.InlineAssembly
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDeclOrigin
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*
import prog8.code.target.VMTarget


internal fun Program.checkValid(errors: IErrorReporter, compilerOptions: CompilationOptions) {
    // semantic analysis to see if the program is valid.
    val parentChecker = ParentNodeChecker()
    parentChecker.visit(this)
    val checker = AstChecker(this, errors, compilerOptions)
    checker.visit(this)
}

internal fun Program.processAstBeforeAsmGeneration(compilerOptions: CompilationOptions, errors: IErrorReporter) {
    val boolRemover = BoolRemover(this)
    boolRemover.visit(this)
    boolRemover.applyModifications()

    if(compilerOptions.compTarget.name!=VMTarget.NAME) {
        val finder = AsmInstructionNamesFinder(compilerOptions.compTarget)
        finder.visit(this)
        if(finder.foundAny()) {
            val replacer = AsmInstructionNamesReplacer(
                finder.blocks,
                finder.subroutines,
                finder.variables,
                finder.labels)
            replacer.visit(this)
            replacer.applyModifications()
        }
    }

    val fixer = BeforeAsmAstChanger(this, compilerOptions, errors)
    fixer.visit(this)
    while (errors.noErrors() && fixer.applyModifications() > 0) {
        fixer.visit(this)
    }
    val cleaner = BeforeAsmTypecastCleaner(this, errors)
    cleaner.visit(this)
    while (errors.noErrors() && cleaner.applyModifications() > 0) {
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

internal fun Program.changeNotExpressionAndIfComparisonExpr(errors: IErrorReporter, target: ICompilationTarget) {
    val changer = NotExpressionAndIfComparisonExprChanger(this, errors, target)
    changer.visit(this)
    while(errors.noErrors() && changer.applyModifications()>0) {
        changer.visit(this)
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

internal fun Program.preprocessAst(errors: IErrorReporter, options: CompilationOptions) {
    val transforms = AstPreprocessor(this, errors, options)
    transforms.visit(this)
    var mods = transforms.applyModifications()
    while(mods>0)
        mods = transforms.applyModifications()
}

internal fun Program.checkIdentifiers(errors: IErrorReporter, options: CompilationOptions) {

    val checker2 = AstIdentifiersChecker(errors, this, options.compTarget)
    checker2.visit(this)

    if(errors.noErrors()) {
        val transforms = AstOnetimeTransforms(this, options)
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
    while(errors.noErrors() && process.applyModifications()>0) {        // TODO limit the number of cycles here?
        process.visit(this)
    }
}

internal fun Program.moveMainBlockAsFirst() {
    // The module containing the program entrypoint is moved to the first in the sequence.
    // the "main" block containing the entrypoint is moved to the top in there.

    val module = this.entrypoint.definingModule
    val block = this.entrypoint.definingBlock
    moveModuleToFront(module)
    module.remove(block)
    val afterDirective = module.statements.indexOfFirst { it !is Directive }
    if(afterDirective<0)
        module.statements.add(block)
    else
        module.statements.add(afterDirective, block)
}

internal fun IdentifierReference.isSubroutineParameter(program: Program): Boolean {
    val vardecl = this.targetVarDecl(program)
    if(vardecl!=null && vardecl.origin==VarDeclOrigin.SUBROUTINEPARAM) {
        return vardecl.definingSubroutine?.parameters?.any { it.name==vardecl.name } == true
    }
    return false
}

internal fun Subroutine.hasRtsInAsm(): Boolean {
    return statements
        .asSequence()
        .filterIsInstance<InlineAssembly>()
        .any { it.hasReturnOrRts() }
}