package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.FunctionCallExpression
import prog8.ast.expressions.StringLiteral
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.code.core.ICompilationTarget
import prog8.code.core.IErrorReporter
import prog8.code.core.Position
import prog8.code.target.VMTarget
import prog8.compiler.BuiltinFunctions


internal class AstIdentifiersChecker(private val errors: IErrorReporter,
                                     private val program: Program,
                                     private val compTarget: ICompilationTarget
) : IAstVisitor {
    private var blocks = mutableMapOf<String, Block>()

    private fun nameError(name: String, position: Position, existing: Statement) {
        errors.err("name conflict '$name', also defined in ${existing.position.file} line ${existing.position.line}", position)
    }

    private fun nameShadowWarning(name: String, position: Position, existing: Statement) {
        errors.warn("name '$name' shadows occurrence at ${existing.position.file} line ${existing.position.line}", position)
    }

    override fun visit(block: Block) {
        val existing = blocks[block.name]
        if(existing!=null) {
            if(block.isInLibrary)
                nameError(existing.name, existing.position, block)
            else
                nameError(block.name, block.position, existing)
        }
        else
            blocks[block.name] = block

        super.visit(block)
    }

    override fun visit(decl: VarDecl) {
        decl.datatypeErrors.forEach { errors.err(it.message, it.position) }

        if(decl.name in BuiltinFunctions)
            errors.err("builtin function cannot be redefined", decl.position)

        val existingInSameScope = decl.definingScope.lookup(listOf(decl.name))
        if(existingInSameScope!=null && existingInSameScope!==decl)
            nameError(decl.name, decl.position, existingInSameScope)

        val existingOuter = decl.parent.definingScope.lookup(listOf(decl.name))
        if (existingOuter != null && existingOuter !== decl && existingOuter is VarDecl) {
            if(existingOuter.parent!==decl.parent)
                nameShadowWarning(decl.name, decl.position, existingOuter)
            else
                nameError(decl.name, decl.position, existingOuter)
        }

        if(decl.definingBlock.name==decl.name)
            nameError(decl.name, decl.position, decl.definingBlock)
        if(decl.definingSubroutine?.name==decl.name)
            nameError(decl.name, decl.position, decl.definingSubroutine!!)

        super.visit(decl)
    }

    override fun visit(subroutine: Subroutine) {
        if(subroutine.name in BuiltinFunctions) {
            // the builtin functions can't be redefined
            errors.err("builtin function cannot be redefined", subroutine.position)
        } else {
            // already reported elsewhere:
            // if (subroutine.parameters.any { it.name in BuiltinFunctions })
            //    checkResult.add(NameError("builtin function name cannot be used as parameter", subroutine.position))

            val existing = subroutine.lookup(listOf(subroutine.name))
            if (existing != null && existing !== subroutine) {
                if(existing.parent!==subroutine.parent && existing is Subroutine)
                    nameShadowWarning(subroutine.name, existing.position, subroutine)
                else
                    nameError(subroutine.name, existing.position, subroutine)
            }

            // check that there are no local symbols (variables, labels, subs) that redefine the subroutine's parameters.
            val symbolsInSub = subroutine.allDefinedSymbols
            val namesInSub = symbolsInSub.map{ it.first }.toSet()
            val paramNames = subroutine.parameters.map { it.name }.toSet()
            val paramsToCheck = paramNames.intersect(namesInSub)
            for(name in paramsToCheck) {
                val symbol = subroutine.searchSymbol(name)
                if(symbol!=null && (symbol as? VarDecl)?.subroutineParameter==null)
                    nameError(name, symbol.position, subroutine)
            }

            if(subroutine.isAsmSubroutine && subroutine.statements.any{it !is InlineAssembly}) {
                errors.err("asmsub can only contain inline assembly (%asm)", subroutine.position)
            }

            if(compTarget.name != VMTarget.NAME) {
                if (subroutine.name == subroutine.definingBlock.name) {
                    // subroutines cannot have the same name as their enclosing block,
                    // because this causes symbol scoping issues in the resulting assembly source
                    errors.err("name conflict '${subroutine.name}', also defined at ${subroutine.definingBlock.position} (64tass scope nesting limitation)", subroutine.position)
                }
            }
        }

        super.visit(subroutine)
    }

    override fun visit(label: Label) {
        if(label.name in BuiltinFunctions) {
            errors.err("builtin function cannot be redefined", label.position)
        } else {
            val existing = (label.definingSubroutine ?: label.definingBlock).getAllLabels(label.name)
            for(el in existing) {
                if(el === label || el.name != label.name)
                    continue
                else {
                    nameError(label.name, label.position, el)
                    break
                }
            }
        }

        super.visit(label)
    }

    override fun visit(string: StringLiteral) {
        if (string.value.length > 255)
            errors.err("string literal length max is 255", string.position)

        super.visit(string)
    }

    override fun visit(functionCallExpr: FunctionCallExpression) =  visitFunctionCall(functionCallExpr)
    override fun visit(functionCallStatement: FunctionCallStatement) =  visitFunctionCall(functionCallStatement)

    private fun visitFunctionCall(call: IFunctionCall) {
        if(call.target.nameInSource==listOf("rnd") || call.target.nameInSource==listOf("rndw")) {
            val target = call.target.targetStatement(program)
            if(target==null) {
                errors.err("rnd() and rndw() builtin functions have been moved into the math module", call.position)
                return
            }
        }
        when (val target = call.target.targetStatement(program)) {
            is Subroutine -> {
                val expectedNumberOfArgs: Int = target.parameters.size
                if(call.args.size != expectedNumberOfArgs) {
                    val pos = (if(call.args.any()) call.args[0] else (call as Node)).position
                    errors.err("invalid number of arguments", pos)
                }
            }
            is BuiltinFunctionPlaceholder -> {
                val func = BuiltinFunctions.getValue(target.name)
                val expectedNumberOfArgs: Int = func.parameters.size
                if(call.args.size != expectedNumberOfArgs) {
                    val pos = (if(call.args.any()) call.args[0] else (call as Node)).position
                    errors.err("invalid number of arguments", pos)
                }
                if(func.name=="memory") {
                    val name = call.args[0] as? StringLiteral
                    if(name!=null) {
                        val processed = name.value.map {
                            if(it.isLetterOrDigit())
                                it
                            else
                                '_'
                        }.joinToString("")
                        call.args[0] = StringLiteral(processed, compTarget.defaultEncoding, name.position)
                        call.args[0].linkParents(call as Node)
                    }
                }
            }
            is Label -> {
                if(call.args.isNotEmpty()) {
                    val pos = (if(call.args.any()) call.args[0] else (call as Node)).position
                    errors.err("cannot use arguments when calling a label", pos)
                }
            }
            null -> {}
            else -> errors.err("cannot call this as a subroutine or function", call.target.position)
        }
    }
}
