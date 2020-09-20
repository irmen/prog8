package prog8.ast.processing

import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.target.CompilationTarget
import prog8.functions.BuiltinFunctions

internal class AstIdentifiersChecker(private val program: Program, private val errors: ErrorReporter) : IAstVisitor {
    private var blocks = mutableMapOf<String, Block>()

    private fun nameError(name: String, position: Position, existing: Statement) {
        errors.err("name conflict '$name', also defined in ${existing.position.file} line ${existing.position.line}", position)
    }

    override fun visit(module: Module) {
        blocks.clear()  // blocks may be redefined within a different module

        super.visit(module)
    }

    override fun visit(block: Block) {
        if(block.name in CompilationTarget.instance.machine.opcodeNames)
            errors.err("can't use a cpu opcode name as a symbol: '${block.name}'", block.position)

        val existing = blocks[block.name]
        if(existing!=null)
            nameError(block.name, block.position, existing)
        else
            blocks[block.name] = block

        super.visit(block)
    }

    override fun visit(directive: Directive) {
        if(directive.directive=="%target") {
            val compatibleTarget = directive.args.single().name
            if (compatibleTarget != CompilationTarget.instance.name)
                errors.err("module's compilation target ($compatibleTarget) differs from active target (${CompilationTarget.instance.name})", directive.position)
        }

        super.visit(directive)
    }

    override fun visit(decl: VarDecl) {
        decl.datatypeErrors.forEach { errors.err(it.message, it.position) }

        if(decl.name in BuiltinFunctions)
            errors.err("builtin function cannot be redefined", decl.position)

        if(decl.name in CompilationTarget.instance.machine.opcodeNames)
            errors.err("can't use a cpu opcode name as a symbol: '${decl.name}'", decl.position)

        if(decl.datatype==DataType.STRUCT) {
            if (decl.structHasBeenFlattened)
                return super.visit(decl)    // don't do this multiple times

            if (decl.struct == null) {
                errors.err("undefined struct type", decl.position)
                return super.visit(decl)
            }

            if (decl.struct!!.statements.any { (it as VarDecl).datatype !in NumericDatatypes })
                return super.visit(decl)     // a non-numeric member, not supported. proper error is given by AstChecker later

            if (decl.value is NumericLiteralValue) {
                errors.err("you cannot initialize a struct using a single value", decl.position)
                return super.visit(decl)
            }

            if (decl.value != null && decl.value !is ArrayLiteralValue) {
                errors.err("initializing a struct requires array literal value", decl.value?.position ?: decl.position)
                return super.visit(decl)
            }
        }

        val existing = program.namespace.lookup(listOf(decl.name), decl)
        if (existing != null && existing !== decl)
            nameError(decl.name, decl.position, existing)

        super.visit(decl)
    }

    override fun visit(subroutine: Subroutine) {
        if(subroutine.name in CompilationTarget.instance.machine.opcodeNames) {
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

            // check that there are no local variables, labels, or other subs that redefine the subroutine's parameters
            val symbolsInSub = subroutine.allDefinedSymbols()
            val namesInSub = symbolsInSub.map{ it.first }.toSet()
            val paramNames = subroutine.parameters.map { it.name }.toSet()
            val paramsToCheck = paramNames.intersect(namesInSub)
            for(name in paramsToCheck) {
                val labelOrVar = subroutine.getLabelOrVariable(name)
                if(labelOrVar!=null && labelOrVar.position != subroutine.position)
                    nameError(name, labelOrVar.position, subroutine)
                val sub = subroutine.statements.singleOrNull { it is Subroutine && it.name==name}
                if(sub!=null)
                    nameError(name, sub.position, subroutine)
            }

            if(subroutine.isAsmSubroutine && subroutine.statements.any{it !is InlineAssembly}) {
                errors.err("asmsub can only contain inline assembly (%asm)", subroutine.position)
            }
        }

        super.visit(subroutine)
    }

    override fun visit(label: Label) {
        if(label.name in CompilationTarget.instance.machine.opcodeNames)
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
        if (string.value.length !in 1..255)
            errors.err("string literal length must be between 1 and 255", string.position)

        super.visit(string)
    }

    override fun visit(structDecl: StructDecl) {
        for(member in structDecl.statements){
            val decl = member as? VarDecl
            if(decl!=null && decl.datatype !in NumericDatatypes)
                errors.err("structs can only contain numerical types", decl.position)
        }

        super.visit(structDecl)
    }
}
