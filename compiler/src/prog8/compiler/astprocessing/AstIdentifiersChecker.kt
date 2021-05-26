package prog8.compiler.astprocessing

import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.base.Position
import prog8.ast.expressions.StringLiteralValue
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.compiler.IErrorReporter
import prog8.compiler.functions.BuiltinFunctions
import prog8.compiler.target.ICompilationTarget

internal class AstIdentifiersChecker(private val program: Program, private val errors: IErrorReporter, private val compTarget: ICompilationTarget) : IAstVisitor {
    private var blocks = mutableMapOf<String, Block>()

    private fun nameError(name: String, position: Position, existing: Statement) {
        errors.err("name conflict '$name', also defined in ${existing.position.file} line ${existing.position.line}", position)
    }

    override fun visit(module: Module) {
        blocks.clear()  // blocks may be redefined within a different module

        super.visit(module)
    }

    override fun visit(block: Block) {
        if(block.name in compTarget.machine.opcodeNames)
            errors.err("can't use a cpu opcode name as a symbol: '${block.name}'", block.position)

        val existing = blocks[block.name]
        if(existing!=null)
            nameError(block.name, block.position, existing)
        else
            blocks[block.name] = block

        if(!block.isInLibrary) {
            val libraries = program.modules.filter { it.isLibraryModule }
            val libraryBlockNames = libraries.flatMap { it.statements.filterIsInstance<Block>().map { b -> b.name } }
            if(block.name in libraryBlockNames)
                errors.err("block is already defined in an included library module", block.position)
        }

        super.visit(block)
    }

    override fun visit(directive: Directive) {
        if(directive.directive=="%target") {
            val compatibleTarget = directive.args.single().name
            if (compatibleTarget != compTarget.name)
                errors.err("module's compilation target ($compatibleTarget) differs from active target (${compTarget.name})", directive.position)
        }

        super.visit(directive)
    }

    override fun visit(decl: VarDecl) {
        decl.datatypeErrors.forEach { errors.err(it.message, it.position) }

        if(decl.name in BuiltinFunctions)
            errors.err("builtin function cannot be redefined", decl.position)

        if(decl.name in compTarget.machine.opcodeNames)
            errors.err("can't use a cpu opcode name as a symbol: '${decl.name}'", decl.position)

        val existing = program.namespace.lookup(listOf(decl.name), decl)
        if (existing != null && existing !== decl)
            nameError(decl.name, decl.position, existing)

        if(decl.definingBlock().name==decl.name)
            nameError(decl.name, decl.position, decl.definingBlock())
        if(decl.definingSubroutine()?.name==decl.name)
            nameError(decl.name, decl.position, decl.definingSubroutine()!!)

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

            val existing = program.namespace.lookup(listOf(subroutine.name), subroutine)
            if (existing != null && existing !== subroutine)
                nameError(subroutine.name, subroutine.position, existing)

            // check that there are no local variables, labels, or other subs that redefine the subroutine's parameters. Blocks are okay.
            val symbolsInSub = subroutine.allDefinedSymbols()
            val namesInSub = symbolsInSub.map{ it.first }.toSet()
            val paramNames = subroutine.parameters.map { it.name }.toSet()
            val paramsToCheck = paramNames.intersect(namesInSub)
            for(name in paramsToCheck) {
                val labelOrVar = subroutine.getLabelOrVariable(name)
                if(labelOrVar!=null && labelOrVar.position != subroutine.position)
                    nameError(name, labelOrVar.position, subroutine)
                val sub = subroutine.statements.firstOrNull { it is Subroutine && it.name==name}
                if(sub!=null)
                    nameError(name, subroutine.position, sub)
            }

            if(subroutine.isAsmSubroutine && subroutine.statements.any{it !is InlineAssembly}) {
                errors.err("asmsub can only contain inline assembly (%asm)", subroutine.position)
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
            val existing = label.definingSubroutine()?.getAllLabels(label.name) ?: emptyList()
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
}
