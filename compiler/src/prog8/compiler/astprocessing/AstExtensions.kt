package prog8.compiler.astprocessing

import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstModification
import prog8.ast.walk.AstRemove
import prog8.ast.walk.AstReplaceNode
import prog8.ast.walk.AstWalker
import prog8.code.core.*
import java.io.CharConversionException


internal fun invalidNumberOfArgsError(errors: IErrorReporter, pos: Position, numArgs: Int, params: List<String>, zeroAllowed: Boolean=false) {
    if (zeroAllowed && numArgs == 0) return
    val expected = if(zeroAllowed) "${params.size} or 0" else "${params.size}"
    if(numArgs<params.size) {
        val missing = params.drop(numArgs).joinToString(", ")
        errors.err("invalid number of arguments: expected $expected but got $numArgs, missing: $missing", pos)
    }
    else
        errors.err("invalid number of arguments: expected $expected but got $numArgs", pos)
}

internal fun Program.checkValid(errors: IErrorReporter, compilerOptions: CompilationOptions) {
    // semantic analysis to see if the program is valid.
    val parentChecker = ParentNodeChecker()
    parentChecker.visit(this)
    val checker = AstChecker(this, errors, compilerOptions)
    checker.visit(this)
}

internal fun Program.reorderStatements(options: CompilationOptions, errors: IErrorReporter) {
    val reorder = StatementReorderer(this, options, errors)
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
    for(numCycles in 0..2000) {
        if (errors.noErrors() && changer.applyModifications() > 0)
            changer.visit(this)
        else
            break

        if(numCycles==2000)
            throw InternalCompilerException("changeNotExpressionAndIfComparisonExpr() is looping endlessly")
    }
}

internal fun Program.charLiteralsToUByteLiterals(target: ICompilationTarget, errors: IErrorReporter) {
    val walker = object : AstWalker() {
        override fun after(char: CharLiteral, parent: Node): Iterable<AstModification> {
            require(char.encoding != Encoding.DEFAULT)
            return try {
                val encoded = target.encodeString(char.value.toString(), char.encoding)
                listOf(AstReplaceNode(
                    char,
                    NumericLiteral(BaseDataType.UBYTE, encoded[0].toDouble(), char.position),
                    parent
                ))
            } catch (x: CharConversionException) {
                errors.err(x.message ?: "can't encode character", char.position)
                noModifications
            }
        }

        override fun after(string: StringLiteral, parent: Node): Iterable<AstModification> {
            // this only *checks* for errors for string encoding. The actual encoding is done much later
            require(string.encoding != Encoding.DEFAULT)
            try {
                target.encodeString(string.value, string.encoding)
            } catch (x: CharConversionException) {
                errors.err(x.message ?: "can't encode string", string.position)
            }
            return noModifications
        }

        override fun after(decl: VarDecl, parent: Node): Iterable<AstModification> {
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

        override fun after(alias: Alias, parent: Node): Iterable<AstModification> {
            // remove all alias nodes, every aliased identifier should have been replaced in the previous step
            return listOf(AstRemove(alias, parent as IStatementContainer))
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

internal fun Expression.flatten(): List<Expression> {
    return if (this is ArrayLiteral) this.value.flatMap { it.flatten() } else listOf(this)
}

internal fun List<Expression>.flattenArgs(): List<Expression> {
    return this.flatMap { it.flatten() }
}

fun Program.desugaring(errors: IErrorReporter, options: CompilationOptions) {
    val desugar = CodeDesugarer(this, options.compTarget, errors)
    desugar.visit(this)
    for(numCycles in 0..2000) {
        if (errors.noErrors() && desugar.applyModifications() > 0)
            desugar.visit(this)
        else
            break

        if(numCycles==2000)
            throw InternalCompilerException("desugaring() is looping endlessly")
    }
}

internal fun Program.verifyFunctionArgTypes(errors: IErrorReporter, options: CompilationOptions) {
    val fixer = VerifyFunctionArgTypes(this, options, errors)
    fixer.visit(this)
}

internal fun Program.checkPrivateAccess(errors: IErrorReporter) {
    val checker = PrivateAccessChecker(this, errors)
    checker.visit(this)
}

internal fun Program.checkM68kSyntax(errors: IErrorReporter) {
    val checker = M68kAstChecker(errors)
    checker.visit(this)
}


internal fun Program.preprocessAst(errors: IErrorReporter, options: CompilationOptions) {
    val mergeBlocks = BlockMerger(errors)
    mergeBlocks.visit(this)
    if(errors.noErrors()) {
        val transforms = AstPreprocessor(this, errors, options)
        transforms.visit(this)
        for(numCycles in 0..2000) {
            if (errors.noErrors() && transforms.applyModifications() > 0)
                transforms.visit(this)
            else
                break

            if(numCycles==2000)
                throw InternalCompilerException("preprocessAst() is looping endlessly")
        }
    }
}

internal fun Program.checkIdentifiers(errors: IErrorReporter, options: CompilationOptions) {

    val checker2 = AstIdentifiersChecker(errors, this, options.compTarget)
    checker2.visit(this)

    if(errors.noErrors()) {
        val lit2decl = LiteralsToAutoVarsAndRecombineIdentifiers(this, errors)
        lit2decl.visit(this)
        for(numCycles in 0..1000) {
            if(errors.noErrors() && lit2decl.applyModifications() > 0)
                lit2decl.visit(this)
            else
                break

            if(numCycles==1000) {
                throw InternalCompilerException("checkIdentifiers() is looping endlessly - check for circular aliases")
            }
        }
    }
}

internal fun Program.variousCleanups(errors: IErrorReporter, options: CompilationOptions) {
    val process = VariousCleanups(this, errors, options)
    process.visit(this)
    for(numCycles in 0..2000) {
        if (errors.noErrors() && process.applyModifications() > 0)
            process.visit(this)
        else
            break

        if(numCycles==2000)
            throw InternalCompilerException("variousCleanups() is looping endlessly")
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
    when (val targetStatement = targetStatement(program.builtinFunctions)) {
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
        is StructFieldRef, is StructDecl, is Alias -> {
            return targetStatement
        }
        null -> {
            val firstName = this.nameInSource.firstOrNull()
            val alias = if(firstName != null) definingScope.lookup(firstName) else null
            if(alias !is Alias || alias.target.targetStatement(program.builtinFunctions)==null)
                errors.undefined(this.nameInSource, this.firstTarget(program.builtinFunctions)==null, this.position)
        }
        else -> errors.err("cannot call that: ${this.nameInSource.joinToString(".")}", this.position)
    }
    return null
}
