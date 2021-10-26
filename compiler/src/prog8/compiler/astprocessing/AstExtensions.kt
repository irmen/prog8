package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.*
import prog8.ast.statements.AssignTarget
import prog8.ast.statements.Directive
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.compiler.BeforeAsmGenerationAstChanger
import prog8.compiler.CompilationOptions
import prog8.compiler.IErrorReporter
import prog8.compiler.IStringEncoding
import prog8.compiler.target.ICompilationTarget
import prog8.compiler.target.IMachineDefinition
import kotlin.math.abs


fun RangeExpr.size(encoding: IStringEncoding): Int? {
    val fromLv = (from as? NumericLiteralValue)
    val toLv = (to as? NumericLiteralValue)
    if(fromLv==null || toLv==null)
        return null
    return toConstantIntegerRange(encoding)?.count()
}

fun RangeExpr.toConstantIntegerRange(encoding: IStringEncoding): IntProgression? {
    val fromVal: Int
    val toVal: Int
    val fromString = from as? StringLiteralValue
    val toString = to as? StringLiteralValue
    if(fromString!=null && toString!=null ) {
        // string range -> int range over character values
        fromVal = encoding.encodeString(fromString.value, fromString.altEncoding)[0].toInt()
        toVal = encoding.encodeString(toString.value, fromString.altEncoding)[0].toInt()
    } else {
        val fromLv = from as? NumericLiteralValue
        val toLv = to as? NumericLiteralValue
        if(fromLv==null || toLv==null)
            return null         // non-constant range
        // integer range
        fromVal = fromLv.number.toInt()
        toVal = toLv.number.toInt()
    }
    val stepVal = (step as? NumericLiteralValue)?.number?.toInt() ?: 1
    return makeRange(fromVal, toVal, stepVal)
}

private fun makeRange(fromVal: Int, toVal: Int, stepVal: Int): IntProgression {
    return when {
        fromVal <= toVal -> when {
            stepVal <= 0 -> IntRange.EMPTY
            stepVal == 1 -> fromVal..toVal
            else -> fromVal..toVal step stepVal
        }
        else -> when {
            stepVal >= 0 -> IntRange.EMPTY
            stepVal == -1 -> fromVal downTo toVal
            else -> fromVal downTo toVal step abs(stepVal)
        }
    }
}


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

internal fun Program.preprocessAst() {
    val transforms = AstPreprocessor()
    transforms.visit(this)
    var mods = transforms.applyModifications()
    while(mods>0)
        mods = transforms.applyModifications()
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

internal fun AssignTarget.isInRegularRAMof(machine: IMachineDefinition): Boolean {
    val memAddr = memoryAddress
    val arrayIdx = arrayindexed
    val ident = identifier
    when {
        memAddr != null -> {
            return when (memAddr.addressExpression) {
                is NumericLiteralValue -> {
                    machine.isRegularRAMaddress((memAddr.addressExpression as NumericLiteralValue).number.toInt())
                }
                is IdentifierReference -> {
                    val program = definingModule.program
                    val decl = (memAddr.addressExpression as IdentifierReference).targetVarDecl(program)
                    if ((decl?.type == VarDeclType.VAR || decl?.type == VarDeclType.CONST) && decl.value is NumericLiteralValue)
                        machine.isRegularRAMaddress((decl.value as NumericLiteralValue).number.toInt())
                    else
                        false
                }
                else -> false
            }
        }
        arrayIdx != null -> {
            val program = definingModule.program
            val targetStmt = arrayIdx.arrayvar.targetVarDecl(program)
            return if (targetStmt?.type == VarDeclType.MEMORY) {
                val addr = targetStmt.value as? NumericLiteralValue
                if (addr != null)
                    machine.isRegularRAMaddress(addr.number.toInt())
                else
                    false
            } else true
        }
        ident != null -> {
            val program = definingModule.program
            val decl = ident.targetVarDecl(program)!!
            return if (decl.type == VarDeclType.MEMORY && decl.value is NumericLiteralValue)
                machine.isRegularRAMaddress((decl.value as NumericLiteralValue).number.toInt())
            else
                true
        }
        else -> return true
    }
}


internal fun IdentifierReference.isSubroutineParameter(program: Program): Boolean {
    val vardecl = this.targetVarDecl(program)
    if(vardecl!=null && vardecl.autogeneratedDontRemove) {
        return vardecl.definingSubroutine?.parameters?.any { it.name==vardecl.name } == true
    }
    return false
}
