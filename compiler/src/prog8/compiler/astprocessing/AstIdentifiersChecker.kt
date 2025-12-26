package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.FunctionCallExpression
import prog8.ast.expressions.StaticStructInitializer
import prog8.ast.expressions.StringLiteral
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.code.core.BuiltinFunctions
import prog8.code.core.ICompilationTarget
import prog8.code.core.IErrorReporter
import prog8.code.core.Position
import prog8.code.target.VMTarget

/**
 * This checks for naming conflicts, not for correct symbol references yet.
 */
internal class AstIdentifiersChecker(private val errors: IErrorReporter,
                                     private val program: Program,
                                     private val compTarget: ICompilationTarget
) : IAstVisitor {
    private var blocks = mutableMapOf<String, Block>()

    private fun nameError(name: String, position: Position, existing: Statement) {
        errors.err("name conflict '$name', also defined in ${existing.position.file} line ${existing.position.line}", position)
    }

    private fun nameShadowWarning(name: String, position: Position, existing: Statement) {
        errors.warn("name '$name' shadows the definition at ${existing.position.file} line ${existing.position.line}", position)
    }

    private fun invalidNumberOfArgsError(pos: Position, numArgs: Int, params: List<String>, zeroAllowed: Boolean=false) {
        val expected = if(zeroAllowed) "${params.size} or 0" else "${params.size}"
        if(numArgs<params.size) {
            val missing = params.drop(numArgs).joinToString(", ")
            errors.err("invalid number of arguments: expected $expected but got $numArgs, missing: $missing", pos)
        }
        else
            errors.err("invalid number of arguments: expected $expected but got $numArgs", pos)
    }

    override fun visit(alias: Alias) {
        if(alias.target.targetStatement(program.builtinFunctions)==null)
            errors.err("undefined symbol: ${alias.target.nameInSource.joinToString(".") }", alias.target.position)

        if(alias.alias == alias.target.nameInSource.first())
            errors.err("alias references itself", alias.position)
    }

    override fun visit(block: Block) {
        val existing = blocks[block.name]
        if(existing!=null) {
            if (block.isInLibrary)
                nameError(existing.name, existing.position, block)
            else
                nameError(block.name, block.position, existing)
        }
        else
            blocks[block.name] = block

        super.visit(block)
    }

    override fun visit(decl: VarDecl) {
        if(decl.name in BuiltinFunctions)
            errors.err("builtin function cannot be redefined", decl.position)

        fun checkNameForErrors(name: String) {
            val existingInSameScope = decl.definingScope.lookup(listOf(name))
            if (existingInSameScope != null && existingInSameScope !== decl)
                nameError(name, decl.position, existingInSameScope)

            val existingOuter = decl.parent.definingScope.lookup(listOf(name))
            if (existingOuter != null && existingOuter !== decl) {
                if (existingOuter is VarDecl) {
                    if (existingOuter.parent !== decl.parent)
                        nameShadowWarning(name, decl.position, existingOuter)
                    else
                        nameError(name, decl.position, existingOuter)
                }
            }
        }

        if(decl.names.size<2) {
            checkNameForErrors(decl.name)
        }
        else {
            for (name in decl.names)
                checkNameForErrors(name)
        }

        if(decl.definingBlock.name==decl.name)
            nameError(decl.name, decl.position, decl.definingBlock)
        if(decl.definingSubroutine?.name==decl.name)
            nameError(decl.name, decl.position, decl.definingSubroutine!!)

        super.visit(decl)
    }

    private val keywords = setOf(
        "void",
        "on",
        "in",
        "to",
        "if",
        "downto",
        "else",
        "step",
        "call",
        "goto",
        "inline",
        "struct",
        "then",
        "alias",
        "defer",
        "const",
        "sizeof",
        "as",
        "return",
        "break",
        "continue",
        "true",
        "false",
        "sub",
        "asmsub",
        "extsub",
        "clobbers",
        "while",
        "do",
        "until",
        "repeat",
        "unroll",
        "when"
    )

    override fun visit(struct: StructDecl) {
        if(struct.name in BuiltinFunctions)
            errors.err("builtin function cannot be redefined", struct.position)
        else if(struct.name in keywords)
            errors.err("struct name cannot be a keyword", struct.position)
        super.visit(struct)
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
                if(symbol!=null && (symbol as? VarDecl)?.origin!=VarDeclOrigin.SUBROUTINEPARAM)
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

    override fun visit(functionCallExpr: FunctionCallExpression) {
        visitFunctionCall(functionCallExpr)
        super.visit(functionCallExpr)
    }

    override fun visit(functionCallStatement: FunctionCallStatement) {
        visitFunctionCall(functionCallStatement)
        super.visit(functionCallStatement)
    }

    override fun visit(initializer: StaticStructInitializer) {
        val struct = initializer.structname.targetStructDecl()
        if(struct!=null) {
            if (initializer.args.isNotEmpty() && initializer.args.size != struct.fields.size) {
                val pos = (if (initializer.args.any()) initializer.args[0] else initializer).position
                invalidNumberOfArgsError(pos, initializer.args.size, struct.fields.map { it.second }, true)
            }
        }
    }

    private fun visitFunctionCall(call: IFunctionCall) {
        fun check(target: Statement?, aliasDepth: Int) {
            when (target) {
                is Subroutine -> {
                    val expectedNumberOfArgs: Int = target.parameters.size
                    if(call.args.size != expectedNumberOfArgs) {
                        val pos = (if(call.args.any()) call.args[0] else (call as Node)).position
                        invalidNumberOfArgsError(pos, call.args.size, target.parameters.map { it.name })
                    }
                }
                is BuiltinFunctionPlaceholder -> {
                    val func = BuiltinFunctions.getValue(target.name)
                    val expectedNumberOfArgs: Int = func.parameters.size
                    if(call.args.size != expectedNumberOfArgs) {
                        val pos = (if(call.args.any()) call.args[0] else (call as Node)).position
                        invalidNumberOfArgsError(pos, call.args.size, func.parameters.map {it.name })
                    }
                    if(target.name=="memory") {
                        val name = call.args[0] as? StringLiteral
                        if(name!=null) {
                            val processed = name.value.map {
                                if(it.isLetterOrDigit())
                                    it
                                else
                                    '_'
                            }.joinToString("")
                            val textEncoding = (call as Node).definingModule.textEncoding
                            call.args[0] = StringLiteral.create(processed, textEncoding, name.position)
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
                is VarDecl -> {
                    if(target.type!=VarDeclType.VAR || !target.datatype.isUnsignedWord)
                        errors.err("wrong address variable datatype, expected uword", call.target.position)
                }
                is Alias -> {
                    if(aliasDepth>1000) {
                        errors.err("circular alias", target.position)
                    } else {
                        val actualtarget = target.target.targetStatement(program.builtinFunctions)
                        check(actualtarget, aliasDepth + 1)
                    }
                }
                is StructDecl, is StructFieldRef -> {}
                null -> {}  // symbol error is given elsewhere
                else -> errors.err("cannot call this as a subroutine or function", call.target.position)
            }
        }

        check(call.target.targetStatement(program.builtinFunctions), 0)
    }
}
