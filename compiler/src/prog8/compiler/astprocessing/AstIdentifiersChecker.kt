package prog8.compiler.astprocessing

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.ast.base.Position
import prog8.ast.expressions.FunctionCall
import prog8.ast.expressions.StringLiteralValue
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.compilerinterface.BuiltinFunctions
import prog8.compilerinterface.ICompilationTarget
import prog8.compilerinterface.IErrorReporter

internal class AstIdentifiersChecker(private val errors: IErrorReporter,
                                     private val program: Program,
                                     private val compTarget: ICompilationTarget) : IAstVisitor {
    private var blocks = mutableMapOf<String, Block>()

    private fun nameError(name: String, position: Position, existing: Statement) {
        errors.err("name conflict '$name', also defined in ${existing.position.file} line ${existing.position.line}", position)
    }

    override fun visit(block: Block) {
        if(block.name in compTarget.machine.opcodeNames)
            errors.err("can't use a cpu opcode name as a symbol: '${block.name}'", block.position)

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

        if(decl.name in compTarget.machine.opcodeNames)
            errors.err("can't use a cpu opcode name as a symbol: '${decl.name}'", decl.position)

        val existing = decl.definingScope.lookup(listOf(decl.name))
        if (existing != null && existing !== decl)
            nameError(decl.name, decl.position, existing)

        if(decl.definingBlock.name==decl.name)
            nameError(decl.name, decl.position, decl.definingBlock)
        if(decl.definingSubroutine?.name==decl.name)
            nameError(decl.name, decl.position, decl.definingSubroutine!!)

        super.visit(decl)
    }

    override fun visit(subroutine: Subroutine) {
        if(subroutine.name in compTarget.machine.opcodeNames) {
            errors.err("can't use a cpu opcode name as a symbol: '${subroutine.name}'", subroutine.position)
        } else if(subroutine.name in BuiltinFunctions) {
            // the builtin functions can't be redefined
            errors.err("builtin function cannot be redefined", subroutine.position)
        } else {
            // already reported elsewhere:
            // if (subroutine.parameters.any { it.name in BuiltinFunctions })
            //    checkResult.add(NameError("builtin function name cannot be used as parameter", subroutine.position))

            val existing = subroutine.lookup(listOf(subroutine.name))
            if (existing != null && existing !== subroutine)
                nameError(subroutine.name, subroutine.position, existing)

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

            if(subroutine.name == subroutine.definingBlock.name) {
                // subroutines cannot have the same name as their enclosing block,
                // because this causes symbol scoping issues in the resulting assembly source
                nameError(subroutine.name, subroutine.position, subroutine.definingBlock)
            }
        }

        super.visit(subroutine)
    }

    override fun visit(label: Label) {
        if(label.name in compTarget.machine.opcodeNames)
            errors.err("can't use a cpu opcode name as a symbol: '${label.name}'", label.position)

        if(label.name in BuiltinFunctions) {
            // the builtin functions can't be redefined
            errors.err("builtin function cannot be redefined", label.position)
        } else {
            val existing = label.definingSubroutine?.getAllLabels(label.name) ?: emptyList()
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

    override fun visit(string: StringLiteralValue) {
        if (string.value.length > 255)
            errors.err("string literal length max is 255", string.position)

        super.visit(string)
    }

    override fun visit(functionCall: FunctionCall) =  visitFunctionCall(functionCall)
    override fun visit(functionCallStatement: FunctionCallStatement) =  visitFunctionCall(functionCallStatement)

    private fun visitFunctionCall(call: IFunctionCall) {
        when (val target = call.target.targetStatement(program)) {
            is Subroutine -> {
                if(call.args.size != target.parameters.size)
                    errors.err("invalid number of arguments", call.args[0].position)
            }
            is BuiltinFunctionStatementPlaceholder -> {
                val func = BuiltinFunctions.getValue(target.name)
                if(call.args.size != func.parameters.size)
                    errors.err("invalid number of arguments", call.args[0].position)
            }
            is Label -> {
                if(call.args.isNotEmpty())
                    errors.err("cannot use arguments when calling a label", call.args[0].position)
            }
            null -> {}
            else -> throw FatalAstException("weird call target")
        }
    }
}
