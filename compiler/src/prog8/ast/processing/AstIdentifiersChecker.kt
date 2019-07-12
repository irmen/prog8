package prog8.ast.processing

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.base.autoHeapValuePrefix
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.functions.BuiltinFunctions


internal class AstIdentifiersChecker(private val namespace: INameScope) : IAstModifyingVisitor {
    private val checkResult: MutableList<AstException> = mutableListOf()

    private var blocks = mutableMapOf<String, Block>()
    internal val anonymousVariablesFromHeap = mutableMapOf<String, Pair<LiteralValue, VarDecl>>()

    internal fun result(): List<AstException> {
        return checkResult
    }

    private fun nameError(name: String, position: Position, existing: IStatement) {
        checkResult.add(NameError("name conflict '$name', also defined in ${existing.position.file} line ${existing.position.line}", position))
    }

    override fun visit(module: Module) {
        blocks.clear()  // blocks may be redefined within a different module
        super.visit(module)
    }

    override fun visit(block: Block): IStatement {
        val existing = blocks[block.name]
        if(existing!=null)
            nameError(block.name, block.position, existing)
        else
            blocks[block.name] = block

        return super.visit(block)
    }

    override fun visit(functionCall: FunctionCall): IExpression {
        if(functionCall.target.nameInSource.size==1 && functionCall.target.nameInSource[0]=="lsb") {
            // lsb(...) is just an alias for type cast to ubyte, so replace with "... as ubyte"
            val typecast = TypecastExpression(functionCall.arglist.single(), DataType.UBYTE, false, functionCall.position)
            typecast.linkParents(functionCall.parent)
            return super.visit(typecast)
        }
        return super.visit(functionCall)
    }

    override fun visit(decl: VarDecl): IStatement {
        // first, check if there are datatype errors on the vardecl
        decl.datatypeErrors.forEach { checkResult.add(it) }

        // now check the identifier
        if(decl.name in BuiltinFunctions)
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", decl.position))

        // is it a struct variable? then define all its struct members as mangled names,
        //    and include the original decl as well.
        if(decl.datatype==DataType.STRUCT) {
            if(decl.structHasBeenFlattened)
                return decl    // don't do this multiple times

            if(decl.struct!!.statements.any { (it as VarDecl).datatype !in NumericDatatypes})
                return decl     // a non-numeric member, not supported. proper error is given by AstChecker later

            val decls: MutableList<IStatement> = decl.struct!!.statements.withIndex().map {
                val member = it.value as VarDecl
                val initvalue = if(decl.value!=null) (decl.value as LiteralValue).arrayvalue!![it.index] else null
                VarDecl(
                        VarDeclType.VAR,
                        member.datatype,
                        false,
                        member.arraysize,
                        mangledStructMemberName(decl.name, member.name),
                        null,
                        initvalue,
                        member.isArray,
                        true,
                        member.position
                )
            }.toMutableList()
            decls.add(decl)
            decl.structHasBeenFlattened = true
            val result = AnonymousScope(decls, decl.position)
            result.linkParents(decl.parent)
            return result
        }

        val existing = namespace.lookup(listOf(decl.name), decl)
        if (existing != null && existing !== decl)
            nameError(decl.name, decl.position, existing)

        return super.visit(decl)
    }

    override fun visit(subroutine: Subroutine): IStatement {
        if(subroutine.name in BuiltinFunctions) {
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", subroutine.position))
        } else {
            if (subroutine.parameters.any { it.name in BuiltinFunctions })
                checkResult.add(NameError("builtin function name cannot be used as parameter", subroutine.position))

            val existing = namespace.lookup(listOf(subroutine.name), subroutine)
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

            // inject subroutine params as local variables (if they're not there yet) (for non-kernel subroutines and non-asm parameters)
            // NOTE:
            // - numeric types BYTE and WORD and FLOAT are passed by value;
            // - strings, arrays, matrices are passed by reference (their 16-bit address is passed as an uword parameter)
            // - do NOT do this is the statement can be transformed into an asm subroutine later!
            if(subroutine.asmAddress==null && !subroutine.canBeAsmSubroutine) {
                if(subroutine.asmParameterRegisters.isEmpty()) {
                    subroutine.parameters
                            .filter { it.name !in namesInSub }
                            .forEach {
                                val vardecl = VarDecl(VarDeclType.VAR, it.type, false, null, it.name, null, null,
                                        isArray = false, hiddenButDoNotRemove = true, position = subroutine.position)
                                vardecl.linkParents(subroutine)
                                subroutine.statements.add(0, vardecl)
                            }
                }
            }
        }
        return super.visit(subroutine)
    }

    override fun visit(label: Label): IStatement {
        if(label.name in BuiltinFunctions) {
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", label.position))
        } else {
            val existing = namespace.lookup(listOf(label.name), label)
            if (existing != null && existing !== label)
                nameError(label.name, label.position, existing)
        }
        return super.visit(label)
    }

    override fun visit(forLoop: ForLoop): IStatement {
        // If the for loop has a decltype, it means to declare the loopvar inside the loop body
        // rather than reusing an already declared loopvar from an outer scope.
        // For loops that loop over an interable variable (instead of a range of numbers) get an
        // additional interation count variable in their scope.
        if(forLoop.loopRegister!=null) {
            if(forLoop.decltype!=null)
                checkResult.add(SyntaxError("register loop variables have a fixed implicit datatype", forLoop.position))
            if(forLoop.loopRegister == Register.X)
                printWarning("writing to the X register is dangerous, because it's used as an internal pointer", forLoop.position)
        } else if(forLoop.loopVar!=null) {
            val varName = forLoop.loopVar.nameInSource.last()
            if(forLoop.decltype!=null) {
                val existing = if(forLoop.body.containsNoCodeNorVars()) null else forLoop.body.lookup(forLoop.loopVar.nameInSource, forLoop.body.statements.first())
                if(existing==null) {
                    // create the local scoped for loop variable itself
                    val vardecl = VarDecl(VarDeclType.VAR, forLoop.decltype, forLoop.zeropage, null, varName, null, null,
                            isArray = false, hiddenButDoNotRemove = true, position = forLoop.loopVar.position)
                    vardecl.linkParents(forLoop.body)
                    forLoop.body.statements.add(0, vardecl)
                    forLoop.loopVar.parent = forLoop.body   // loopvar 'is defined in the body'
                }

            }

            if(forLoop.iterable !is RangeExpr) {
                val existing = if(forLoop.body.containsNoCodeNorVars()) null else forLoop.body.lookup(listOf(ForLoop.iteratorLoopcounterVarname), forLoop.body.statements.first())
                if(existing==null) {
                    // create loop iteration counter variable (without value, to avoid an assignment)
                    val vardecl = VarDecl(VarDeclType.VAR, DataType.UBYTE, true, null, ForLoop.iteratorLoopcounterVarname, null, null,
                            isArray = false, hiddenButDoNotRemove = true, position = forLoop.loopVar.position)
                    vardecl.linkParents(forLoop.body)
                    forLoop.body.statements.add(0, vardecl)
                    forLoop.loopVar.parent = forLoop.body   // loopvar 'is defined in the body'
                }
            }
        }
        return super.visit(forLoop)
    }

    override fun visit(assignTarget: AssignTarget): AssignTarget {
        if(assignTarget.register== Register.X)
            printWarning("writing to the X register is dangerous, because it's used as an internal pointer", assignTarget.position)
        return super.visit(assignTarget)
    }

    override fun visit(returnStmt: Return): IStatement {
        if(returnStmt.value!=null) {
            // possibly adjust any literal values returned, into the desired returning data type
            val subroutine = returnStmt.definingSubroutine()!!
            if(subroutine.returntypes.size!=1)
                return returnStmt  // mismatch in number of return values, error will be printed later.
            val newValue: IExpression
            val lval = returnStmt.value as? LiteralValue
            if(lval!=null) {
                val adjusted = lval.cast(subroutine.returntypes.single())
                if(adjusted!=null && adjusted !== lval)
                    newValue = adjusted
                else
                    newValue = lval
            } else
                newValue = returnStmt.value!!

            returnStmt.value = newValue
        }
        return super.visit(returnStmt)
    }

    override fun visit(literalValue: LiteralValue): LiteralValue {
        if(literalValue.heapId!=null && literalValue.parent !is VarDecl) {
            // a literal value that's not declared as a variable, which refers to something on the heap.
            // we need to introduce an auto-generated variable for this to be able to refer to the value!
            // (note: ususally, this has been taken care of already when the var was created)
            val declaredType = if(literalValue.isArray) ArrayElementTypes.getValue(literalValue.type) else literalValue.type
            val variable = VarDecl(VarDeclType.VAR,
                    declaredType,
                    false,
                    null,
                    "$autoHeapValuePrefix${literalValue.heapId}",
                    null,
                    literalValue,
                    isArray = literalValue.isArray, hiddenButDoNotRemove = true, position = literalValue.position)
            anonymousVariablesFromHeap[variable.name] = Pair(literalValue, variable)
        }
        return super.visit(literalValue)
    }

    override fun visit(addressOf: AddressOf): IExpression {
        // register the scoped name of the referenced identifier
        val variable= addressOf.identifier.targetVarDecl(namespace) ?: return addressOf
        addressOf.scopedname = variable.scopedname
        return super.visit(addressOf)
    }

    override fun visit(structDecl: StructDecl): IStatement {
        for(member in structDecl.statements){
            val decl = member as? VarDecl
            if(decl!=null && decl.datatype !in NumericDatatypes)
                checkResult.add(SyntaxError("structs can only contain numerical types", decl.position))
        }

        return super.visit(structDecl)
    }

}
