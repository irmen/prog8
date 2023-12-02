package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.CharLiteral
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteral
import prog8.ast.expressions.StringLiteral
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*
import java.io.CharConversionException


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

    val fixer = BeforeAsmAstChanger(this, compilerOptions)
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
            return try {
                val encoded = target.encodeString(char.value.toString(), char.encoding)
                listOf(IAstModification.ReplaceNode(
                    char,
                    NumericLiteral(DataType.UBYTE, encoded[0].toDouble(), char.position),
                    parent
                ))
            } catch (x: CharConversionException) {
                errors.err(x.message ?: "can't encode character", char.position)
                noModifications
            }
        }

        override fun after(string: StringLiteral, parent: Node): Iterable<IAstModification> {
            // this only *checks* for errors for string encoding. The actual encoding is done much later
            require(string.encoding != Encoding.DEFAULT)
            try {
                target.encodeString(string.value, string.encoding)
            } catch (x: CharConversionException) {
                errors.err(x.message ?: "can't encode string", string.position)
            }
            return noModifications
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
        val lit2decl = LiteralsToAutoVars(this, options.compTarget, errors)
        lit2decl.visit(this)
        if(errors.noErrors())
            lit2decl.applyModifications()
    }
}

internal fun Program.variousCleanups(errors: IErrorReporter, options: CompilationOptions) {
    val process = VariousCleanups(this, errors, options)
    process.visit(this)
    while(errors.noErrors() && process.applyModifications()>0) {
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

internal fun IdentifierReference.checkFunctionOrLabelExists(program: Program, statement: Statement, errors: IErrorReporter): Statement? {
    when (val targetStatement = this.targetStatement(program)) {
        is Label, is Subroutine, is BuiltinFunctionPlaceholder -> return targetStatement
        is VarDecl -> {
            if(statement is Jump) {
                if (targetStatement.datatype == DataType.UWORD)
                    return targetStatement
                else
                    errors.err("wrong address variable datatype, expected uword", this.position)
            }
            else
                errors.err("cannot call that: ${this.nameInSource.joinToString(".")}", this.position)
        }
        null -> {
            errors.undefined(this.nameInSource, this.position)
        }
        else -> errors.err("cannot call that: ${this.nameInSource.joinToString(".")}", this.position)
    }
    return null
}