package prog8.ast

import prog8.compiler.HeapValues
import prog8.functions.BuiltinFunctions

/**
 * Checks the validity of all identifiers (no conflicts)
 * Also builds a list of all (scoped) symbol definitions
 * Also makes sure that subroutine's parameters also become local variable decls in the subroutine's scope.
 * Finally, it also makes sure the datatype of all Var decls and sub Return values is set correctly.
 */

fun Module.checkIdentifiers(heap: HeapValues): MutableMap<String, IStatement> {
    val checker = AstIdentifiersChecker(heap)
    this.process(checker)

    // add any anonymous variables for heap values that are used, and replace literalvalue by identifierref
    for (variable in checker.anonymousVariablesFromHeap) {
        val scope = variable.first.definingScope()
        scope.statements.add(variable.second)
        val parent = variable.first.parent
        when {
            parent is Assignment && parent.value === variable.first -> {
                parent.value = IdentifierReference(listOf("auto_heap_value_${variable.first.heapId}"), variable.first.position)
            }
            else -> TODO("replace literalvalue by identifierref $variable")
        }
    }

    printErrors(checker.result(), name)
    return checker.symbols
}


class AstIdentifiersChecker(val heap: HeapValues) : IAstProcessor {
    private val checkResult: MutableList<AstException> = mutableListOf()

    var symbols: MutableMap<String, IStatement> = mutableMapOf()
        private set

    fun result(): List<AstException> {
        return checkResult
    }

    private fun nameError(name: String, position: Position, existing: IStatement) {
        checkResult.add(NameError("name conflict '$name', first defined in ${existing.position.file} line ${existing.position.line}", position))
    }

    override fun process(block: Block): IStatement {
        val scopedName = block.scopedname
        val existing = symbols[scopedName]
        if(existing!=null) {
            nameError(block.name, block.position, existing)
        } else {
            symbols[scopedName] = block
        }
        return super.process(block)
    }

    override fun process(decl: VarDecl): IStatement {
        // first, check if there are datatype errors on the vardecl
        decl.datatypeErrors.forEach { checkResult.add(it) }

        // now check the identifier
        if(decl.name in BuiltinFunctions)
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", decl.position))

        val scopedName = decl.scopedname
        val existing = symbols[scopedName]
        if(existing!=null) {
            nameError(decl.name, decl.position, existing)
        } else {
            symbols[scopedName] = decl
        }
        return super.process(decl)
    }

    override fun process(subroutine: Subroutine): IStatement {
        if(subroutine.name in BuiltinFunctions) {
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", subroutine.position))
        } else {
            if (subroutine.parameters.any { it.name in BuiltinFunctions })
                checkResult.add(NameError("builtin function name cannot be used as parameter", subroutine.position))

            val scopedName = subroutine.scopedname
            val existing = symbols[scopedName]
            if (existing != null) {
                nameError(subroutine.name, subroutine.position, existing)
            } else {
                symbols[scopedName] = subroutine
            }

            // check that there are no local variables that redefine the subroutine's parameters
            val definedNames = subroutine.labelsAndVariables()
            val paramNames = subroutine.parameters.map { it.name }
            val definedNamesCorrespondingToParameters = definedNames.filter { it.key in paramNames }
            for(name in definedNamesCorrespondingToParameters) {
                if(name.value.position != subroutine.position)
                    nameError(name.key, name.value.position, subroutine)
            }

            // inject subroutine params as local variables (if they're not there yet) (for non-kernel subroutines and non-asm parameters)
            // NOTE:
            // - numeric types BYTE and WORD and FLOAT are passed by value;
            // - strings, arrays, matrices are passed by reference (their 16-bit address is passed as an uword parameter)
            if(subroutine.asmAddress==null) {
                if(subroutine.asmParameterRegisters.isEmpty()) {
                    subroutine.parameters
                            .filter { !definedNames.containsKey(it.name) }
                            .forEach {
                                val vardecl = VarDecl(VarDeclType.VAR, it.type, null, it.name, null, subroutine.position)
                                vardecl.linkParents(subroutine)
                                subroutine.statements.add(0, vardecl)
                            }
                }
            }
        }
        return super.process(subroutine)
    }

    override fun process(label: Label): IStatement {
        if(label.name in BuiltinFunctions) {
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", label.position))
        } else {
            val scopedName = label.scopedname
            val existing = symbols[scopedName]
            if (existing != null) {
                nameError(label.name, label.position, existing)
            } else {
                symbols[scopedName] = label
            }
        }
        return super.process(label)
    }

    override fun process(forLoop: ForLoop): IStatement {
        // if the for loop has a decltype, it means to declare the loopvar inside the loop body
        // rather than reusing an already declared loopvar from an outer scope.
        if(forLoop.loopRegister!=null) {
            if(forLoop.decltype!=null)
                checkResult.add(SyntaxError("register loop variables cannot be explicitly declared with a datatype", forLoop.position))
            if(forLoop.loopRegister == Register.X)
                printWarning("writing to the X register is dangerous, because it's used as an internal pointer", forLoop.position)
        } else if(forLoop.loopVar!=null) {
            val varName = forLoop.loopVar.nameInSource.last()
            when (forLoop.decltype) {
                DataType.UBYTE, DataType.UWORD -> {
                    val existing = if(forLoop.body.isEmpty()) null else forLoop.body.lookup(forLoop.loopVar.nameInSource, forLoop.body.statements.first())
                    if(existing==null) {
                        val vardecl = VarDecl(VarDeclType.VAR, forLoop.decltype, null, varName, null, forLoop.loopVar.position)
                        vardecl.linkParents(forLoop.body)
                        forLoop.body.statements.add(0, vardecl)
                        forLoop.loopVar.parent = forLoop.body   // loopvar 'is defined in the body'
                    }
                }
                null -> {}
                else -> checkResult.add(SyntaxError("loop variables can only be a byte or word", forLoop.position))
            }
        }
        return super.process(forLoop)
    }

    override fun process(assignTarget: AssignTarget): AssignTarget {
        if(assignTarget.register==Register.X)
            printWarning("writing to the X register is dangerous, because it's used as an internal pointer", assignTarget.position)
        return super.process(assignTarget)
    }

    override fun process(returnStmt: Return): IStatement {
        if(returnStmt.values.isNotEmpty()) {
            // possibly adjust any literal values returned, into the desired returning data type
            val subroutine = returnStmt.definingSubroutine()!!
            val newValues = mutableListOf<IExpression>()
            for(returnvalue in returnStmt.values.zip(subroutine.returntypes)) {
                val lval = returnvalue.first as? LiteralValue
                if(lval!=null) {
                    val adjusted = lval.intoDatatype(returnvalue.second)
                    if(adjusted!=null && adjusted !== lval)
                        newValues.add(adjusted)
                    else
                        newValues.add(lval)
                }
                else
                    newValues.add(returnvalue.first)
            }
            returnStmt.values = newValues
        }
        return super.process(returnStmt)
    }


    internal val anonymousVariablesFromHeap = mutableSetOf<Pair<LiteralValue, VarDecl>>()

    override fun process(literalValue: LiteralValue): LiteralValue {
        if(literalValue.heapId!=null && literalValue.parent !is VarDecl) {
            // a literal value that's not declared as a variable, which refers to something on the heap.
            // we need to introduce an auto-generated variable for this to be able to refer to the value!
            val variable = VarDecl(VarDeclType.VAR, literalValue.type, null, "auto_heap_value_${literalValue.heapId}", literalValue, literalValue.position)
            anonymousVariablesFromHeap.add(Pair(literalValue, variable))
        }
        return super.process(literalValue)
    }
}
