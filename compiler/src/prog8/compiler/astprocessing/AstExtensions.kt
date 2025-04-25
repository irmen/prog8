package prog8.compiler.astprocessing

import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.*
import prog8.code.target.VMTarget
import java.io.CharConversionException


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
                    NumericLiteral(BaseDataType.UBYTE, encoded[0].toDouble(), char.position),
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

        override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
            if(decl.datatype.isString) {
                val initvalue = decl.value
                if(initvalue!=null && initvalue is BinaryExpression) {
                    if(initvalue.left is CharLiteral || initvalue.right is CharLiteral) {
                        errors.err("using a char literal in a string initialization expression, should probably be a string literal with one character in it instead", initvalue.position)
                    }
                }
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

fun Program.desugaring(errors: IErrorReporter) {
    val desugar = CodeDesugarer(this, errors)
    desugar.visit(this)
    while(errors.noErrors() && desugar.applyModifications()>0)
        desugar.visit(this)
}

internal fun Program.verifyFunctionArgTypes(errors: IErrorReporter, options: CompilationOptions) {
    val fixer = VerifyFunctionArgTypes(this, options, errors)
    fixer.visit(this)
}

internal fun Program.preprocessAst(errors: IErrorReporter, options: CompilationOptions) {
    val mergeBlocks = BlockMerger(errors)
    mergeBlocks.visit(this)
    if(errors.noErrors()) {
        val transforms = AstPreprocessor(this, errors, options)
        transforms.visit(this)
        while (errors.noErrors() && transforms.applyModifications() > 0)
            transforms.visit(this)
    }
}

internal fun Program.checkIdentifiers(errors: IErrorReporter, options: CompilationOptions) {

    val checker2 = AstIdentifiersChecker(errors, this, options.compTarget)
    checker2.visit(this)

    if(errors.noErrors()) {
        val lit2decl = LiteralsToAutoVars(this, errors)
        lit2decl.visit(this)
        while(errors.noErrors() && lit2decl.applyModifications()>0)
            lit2decl.visit(this)
    }
}

internal fun Program.variousCleanups(errors: IErrorReporter, options: CompilationOptions) {
    val process = VariousCleanups(this, errors, options)
    process.visit(this)
    while(errors.noErrors() && process.applyModifications()>0) {
        process.visit(this)
    }
}

internal fun Program.moveMainBlockAsFirst(target: ICompilationTarget) {
    // The module containing the program entrypoint is moved to the first in the sequence.
    // The "main" block containing the entrypoint is moved to the top in there.
    // The startup and cleanup machinery is moved to the front as well.

    val module = this.entrypoint.definingModule
    val block = this.entrypoint.definingBlock
    moveModuleToFront(module)
    module.remove(block)
    val afterDirective = module.statements.indexOfFirst { it !is Directive }
    if(afterDirective<0)
        module.statements.add(block)
    else
        module.statements.add(afterDirective, block)


    if(target.name != VMTarget.NAME) {
        // the program startup and cleanup machinery needs to be located in system ram
        // so in an attempt to not be pushed into ROM space at the end of the program,
        // this moves that block to the beginning of the program as much as possible.
        val startupBlock = this.allBlocks.singleOrNull { it.name == "p8_sys_startup" }
        if(startupBlock!=null) {
            val mainBlockIdx = module.statements.indexOf(block)
            (startupBlock.parent as IStatementContainer).remove(startupBlock)
            if (block.address == null) {
                module.statements.add(mainBlockIdx, startupBlock)
            } else {
                module.statements.add(mainBlockIdx + 1, startupBlock)
            }
            startupBlock.parent = module
        }
    }
}

internal fun IdentifierReference.isSubroutineParameter(): Boolean {
    val vardecl = this.targetVarDecl()
    if(vardecl!=null && vardecl.origin==VarDeclOrigin.SUBROUTINEPARAM) {
        return vardecl.definingSubroutine?.parameters?.any { it.name==vardecl.name } == true
    }
    return false
}

internal fun Subroutine.hasRtsInAsm(checkOnlyLastInstruction: Boolean): Boolean {
    val asms = statements
        .asSequence()
        .filterIsInstance<InlineAssembly>()
    if(checkOnlyLastInstruction) {
        val lastAsm = asms.lastOrNull() ?: return false
        val lastLine = lastAsm.assembly.lineSequence().map { it.trim() }.lastOrNull {
            it.isNotBlank() && (!it.startsWith(';') || it.contains("!notreached!"))
        }
        if(lastLine?.contains("!notreached!")==true)
            return true
        val inlineAsm = InlineAssembly("  $lastLine", lastAsm.isIR, lastAsm.position)
        return inlineAsm.hasReturnOrRts()
    } else {
        val allAsms = asms.toList()
        val lastAsm = allAsms.lastOrNull() ?: return false
        val lastLine = lastAsm.assembly.lineSequence().map { it.trim() }.last {
            it.isNotBlank() && (!it.startsWith(';') || it.contains("!notreached!"))
        }
        if(lastLine.contains("!notreached!"))
            return true
        return allAsms.any { it.hasReturnOrRts() }
    }
}

internal fun IdentifierReference.checkFunctionOrLabelExists(program: Program, statement: Statement, errors: IErrorReporter): Statement? {
    when (val targetStatement = this.targetStatement(program)) {
        is Label, is Subroutine, is BuiltinFunctionPlaceholder -> return targetStatement
        is VarDecl -> {
            if(statement is Jump) {
                if (targetStatement.datatype.isUnsignedWord)
                    return targetStatement
                else
                    errors.err("wrong address variable datatype, expected uword", this.position)
            }
            else
                errors.err("cannot call that: ${this.nameInSource.joinToString(".")}", this.position)
        }
        is Alias -> {
            return targetStatement
        }
        null -> {
            errors.undefined(this.nameInSource, this.position)
        }
        else -> errors.err("cannot call that: ${this.nameInSource.joinToString(".")}", this.position)
    }
    return null
}