package prog8.ast.processing

import prog8.ast.INameScope
import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.HeapValues
import prog8.compiler.target.c64.AssemblyProgram
import prog8.functions.BuiltinFunctions


internal class AstIdentifiersChecker(private val program: Program) : IAstModifyingVisitor {

    private val checkResult: MutableList<AstException> = mutableListOf()

    private var blocks = mutableMapOf<String, Block>()
    private val vardeclsToAdd = mutableMapOf<INameScope, MutableList<VarDecl>>()

    internal fun result(): List<AstException> {
        return checkResult
    }

    private fun nameError(name: String, position: Position, existing: Statement) {
        checkResult.add(NameError("name conflict '$name', also defined in ${existing.position.file} line ${existing.position.line}", position))
    }

    override fun visit(module: Module) {
        vardeclsToAdd.clear()
        blocks.clear()  // blocks may be redefined within a different module
        super.visit(module)
        // add any new vardecls to the various scopes
        for((where, decls) in vardeclsToAdd) {
            where.statements.addAll(0, decls)
            decls.forEach { it.linkParents(where as Node) }
        }
    }

    override fun visit(block: Block): Statement {
        val existing = blocks[block.name]
        if(existing!=null)
            nameError(block.name, block.position, existing)
        else
            blocks[block.name] = block

        return super.visit(block)
    }

    override fun visit(functionCall: FunctionCall): Expression {
        if(functionCall.target.nameInSource.size==1 && functionCall.target.nameInSource[0]=="lsb") {
            // lsb(...) is just an alias for type cast to ubyte, so replace with "... as ubyte"
            val typecast = TypecastExpression(functionCall.arglist.single(), DataType.UBYTE, false, functionCall.position)
            typecast.linkParents(functionCall.parent)
            return super.visit(typecast)
        }
        return super.visit(functionCall)
    }

    override fun visit(decl: VarDecl): Statement {
        // first, check if there are datatype errors on the vardecl
        decl.datatypeErrors.forEach { checkResult.add(it) }

        // now check the identifier
        if(decl.name in BuiltinFunctions)
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", decl.position))

        if(decl.name in AssemblyProgram.reservedNames)
            checkResult.add(NameError("can't use a symbol name reserved by the assembler program", decl.position))
        if(decl.name in AssemblyProgram.opcodeNames)
            checkResult.add(NameError("can't use a cpu opcode name as a symbol", decl.position))

        // is it a struct variable? then define all its struct members as mangled names,
        //    and include the original decl as well.
        if(decl.datatype==DataType.STRUCT) {
            if(decl.structHasBeenFlattened)
                return super.visit(decl)    // don't do this multiple times

            if(decl.struct==null) {
                checkResult.add(NameError("undefined struct type", decl.position))
                return super.visit(decl)
            }

            if(decl.struct!!.statements.any { (it as VarDecl).datatype !in NumericDatatypes})
                return super.visit(decl)     // a non-numeric member, not supported. proper error is given by AstChecker later

            if(decl.value is NumericLiteralValue) {
                checkResult.add(ExpressionError("you cannot initialize a struct using a single value", decl.position))
                return super.visit(decl)
            }

            val decls = decl.flattenStructMembers()
            decls.add(decl)
            val result = AnonymousScope(decls, decl.position)
            result.linkParents(decl.parent)
            return result
        }

        val existing = program.namespace.lookup(listOf(decl.name), decl)
        if (existing != null && existing !== decl)
            nameError(decl.name, decl.position, existing)

        return super.visit(decl)
    }

    override fun visit(subroutine: Subroutine): Statement {
        if(subroutine.name in BuiltinFunctions) {
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", subroutine.position))
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
                                val vardecl = VarDecl(VarDeclType.VAR, it.type, ZeropageWish.NOT_IN_ZEROPAGE, null, it.name, null, null,
                                        isArray = false, autogeneratedDontRemove = true, position = subroutine.position)
                                vardecl.linkParents(subroutine)
                                subroutine.statements.add(0, vardecl)
                            }
                }
            }
        }
        return super.visit(subroutine)
    }

    override fun visit(label: Label): Statement {
        if(label.name in BuiltinFunctions) {
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", label.position))
        } else {
            val existing = program.namespace.lookup(listOf(label.name), label)
            if (existing != null && existing !== label)
                nameError(label.name, label.position, existing)
        }
        return super.visit(label)
    }

    override fun visit(forLoop: ForLoop): Statement {
        // If the for loop has a decltype, it means to declare the loopvar inside the loop body
        // rather than reusing an already declared loopvar from an outer scope.
        // For loops that loop over an interable variable (instead of a range of numbers) get an
        // additional interation count variable in their scope.
        if(forLoop.loopRegister!=null) {
            if(forLoop.decltype!=null)
                checkResult.add(SyntaxError("register loop variables have a fixed implicit datatype", forLoop.position))
            if(forLoop.loopRegister == Register.X)
                printWarning("writing to the X register is dangerous, because it's used as an internal pointer", forLoop.position)
        } else {
            val loopVar = forLoop.loopVar
            if (loopVar != null) {
                val varName = loopVar.nameInSource.last()
                if (forLoop.decltype != null) {
                    val existing = if (forLoop.body.containsNoCodeNorVars()) null else forLoop.body.lookup(loopVar.nameInSource, forLoop.body.statements.first())
                    if (existing == null) {
                        // create the local scoped for loop variable itself
                        val vardecl = VarDecl(VarDeclType.VAR, forLoop.decltype, forLoop.zeropage, null, varName, null, null,
                                isArray = false, autogeneratedDontRemove = true, position = loopVar.position)
                        vardecl.linkParents(forLoop.body)
                        forLoop.body.statements.add(0, vardecl)
                        loopVar.parent = forLoop.body   // loopvar 'is defined in the body'
                    }
                }

                if (forLoop.iterable !is RangeExpr) {
                    val existing = if (forLoop.body.containsNoCodeNorVars()) null else forLoop.body.lookup(listOf(ForLoop.iteratorLoopcounterVarname), forLoop.body.statements.first())
                    if (existing == null) {
                        // create loop iteration counter variable (without value, to avoid an assignment)
                        val vardecl = VarDecl(VarDeclType.VAR, DataType.UBYTE, ZeropageWish.PREFER_ZEROPAGE, null, ForLoop.iteratorLoopcounterVarname, null, null,
                                isArray = false, autogeneratedDontRemove = true, position = loopVar.position)
                        vardecl.linkParents(forLoop.body)
                        forLoop.body.statements.add(0, vardecl)
                        loopVar.parent = forLoop.body   // loopvar 'is defined in the body'
                    }
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

    override fun visit(returnStmt: Return): Statement {
        if(returnStmt.value!=null) {
            // possibly adjust any literal values returned, into the desired returning data type
            val subroutine = returnStmt.definingSubroutine()!!
            if(subroutine.returntypes.size!=1)
                return returnStmt  // mismatch in number of return values, error will be printed later.
            val newValue: Expression
            val lval = returnStmt.value as? NumericLiteralValue
            if(lval!=null) {
                val adjusted = lval.cast(subroutine.returntypes.single())
                newValue = if(adjusted!=null && adjusted !== lval) adjusted else lval
            } else {
                newValue = returnStmt.value!!
            }

            returnStmt.value = newValue
        }
        return super.visit(returnStmt)
    }

    override fun visit(refLiteral: ReferenceLiteralValue): Expression {
        val litval = super.visit(refLiteral)
        if(litval is ReferenceLiteralValue) {
            val vardecl = litval.parent as? VarDecl
            if (litval.isString) {
                // intern the string; move it into the heap
                if (litval.str!!.length !in 1..255)
                    checkResult.add(ExpressionError("string literal length must be between 1 and 255", litval.position))
                else {
                    litval.addToHeap(program.heap)
                }
                return if(vardecl!=null)
                    litval
                else
                    makeIdentifierFromRefLv(litval)  // replace the literal string by a identifier reference.
            } else if (litval.isArray) {
                if (vardecl!=null) {
                    return fixupArrayDatatype(litval, vardecl, program.heap)
                } else {
                    // fix the datatype of the array (also on the heap) to the 'biggest' datatype in the array
                    // (we don't know the desired datatype here exactly so we guess)
                    val datatype = determineArrayDt(litval.array!!) ?: return litval
                    val litval2 = litval.cast(datatype)!!
                    litval2.parent = litval.parent
                    // finally, replace the literal array by a identifier reference.
                    return makeIdentifierFromRefLv(litval2)
                }
            }
        }

        return litval
    }

    private fun determineArrayDt(array: Array<Expression>): DataType? {
        val datatypesInArray = array.mapNotNull { it.inferType(program) }
        if(datatypesInArray.isEmpty())
            return null
        if(DataType.FLOAT in datatypesInArray)
            return DataType.ARRAY_F
        if(DataType.WORD in datatypesInArray)
            return DataType.ARRAY_W
        if(DataType.UWORD in datatypesInArray)
            return DataType.ARRAY_UW
        if(DataType.BYTE in datatypesInArray)
            return DataType.ARRAY_B
        if(DataType.UBYTE in datatypesInArray)
            return DataType.ARRAY_UB
        return null
    }

    private fun makeIdentifierFromRefLv(refLiteral: ReferenceLiteralValue): IdentifierReference {
        // a referencetype literal value that's not declared as a variable
        // we need to introduce an auto-generated variable for this to be able to refer to the value
        refLiteral.addToHeap(program.heap)
        val scope = refLiteral.definingScope()
        var variable = VarDecl.createAuto(refLiteral, program.heap)
        variable = addVarDecl(scope, variable)
        // replace the reference literal by a identifier reference
        val identifier = IdentifierReference(listOf(variable.name), variable.position)
        identifier.parent = refLiteral.parent
        return identifier
    }

    override fun visit(addressOf: AddressOf): Expression {
        // register the scoped name of the referenced identifier
        val variable= addressOf.identifier.targetVarDecl(program.namespace) ?: return addressOf
        addressOf.scopedname = variable.scopedname
        return super.visit(addressOf)
    }

    override fun visit(structDecl: StructDecl): Statement {
        for(member in structDecl.statements){
            val decl = member as? VarDecl
            if(decl!=null && decl.datatype !in NumericDatatypes)
                checkResult.add(SyntaxError("structs can only contain numerical types", decl.position))
        }

        return super.visit(structDecl)
    }

    override fun visit(expr: BinaryExpression): Expression {
        return when {
            expr.left is ReferenceLiteralValue ->
                processBinaryExprWithReferenceVal(expr.left as ReferenceLiteralValue, expr.right, expr)
            expr.right is ReferenceLiteralValue ->
                processBinaryExprWithReferenceVal(expr.right as ReferenceLiteralValue, expr.left, expr)
            else -> super.visit(expr)
        }
    }

    private fun processBinaryExprWithReferenceVal(refLv: ReferenceLiteralValue, operand: Expression, expr: BinaryExpression): Expression {
        // expressions on strings or arrays
        if(refLv.isString) {
            val constvalue = operand.constValue(program)
            if(constvalue!=null) {
                if (expr.operator == "*") {
                    // repeat a string a number of times
                    return ReferenceLiteralValue(refLv.inferType(program),
                            refLv.str!!.repeat(constvalue.number.toInt()), null, null, expr.position)
                }
            }
            if(expr.operator == "+" && operand is ReferenceLiteralValue) {
                if (operand.isString) {
                    // concatenate two strings
                    return ReferenceLiteralValue(refLv.inferType(program),
                            "${refLv.str}${operand.str}", null, null, expr.position)
                }
            }
        }
        return expr
    }

    private fun addVarDecl(scope: INameScope, variable: VarDecl): VarDecl {
        if(scope !in vardeclsToAdd)
            vardeclsToAdd[scope] = mutableListOf()
        val declList = vardeclsToAdd.getValue(scope)
        val existing = declList.singleOrNull { it.name==variable.name }
        return if(existing!=null) {
            existing
        } else {
            declList.add(variable)
            variable
        }
    }

}

internal fun fixupArrayDatatype(array: ReferenceLiteralValue, vardecl: VarDecl, heap: HeapValues): ReferenceLiteralValue {
    if(array.heapId!=null) {
        val arrayDt = array.type
        if(arrayDt!=vardecl.datatype) {
            // fix the datatype of the array (also on the heap) to match the vardecl
            val litval2 = array.cast(vardecl.datatype)!!
            vardecl.value = litval2
            litval2.linkParents(vardecl)
            litval2.addToHeap(heap)     // TODO is the previous array discarded from the resulting asm code?
            return litval2
        }
    } else {
        array.addToHeap(heap)
    }
    return array
}
