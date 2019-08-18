package prog8.ast.processing

import prog8.ast.INameScope
import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
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

        if(decl.name in AssemblyProgram.opcodeNames)
            checkResult.add(NameError("can't use a cpu opcode name as a symbol: '${decl.name}'", decl.position))

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
        if(subroutine.name in AssemblyProgram.opcodeNames) {
            checkResult.add(NameError("can't use a cpu opcode name as a symbol: '${subroutine.name}'", subroutine.position))
        } else if(subroutine.name in BuiltinFunctions) {
            // the builtin functions can't be redefined
            checkResult.add(NameError("builtin function cannot be redefined", subroutine.position))
        } else {
            // already reported elsewhere:
            // if (subroutine.parameters.any { it.name in BuiltinFunctions })
            //    checkResult.add(NameError("builtin function name cannot be used as parameter", subroutine.position))

            val existing = program.namespace.lookup(listOf(subroutine.name), subroutine)
            if (existing != null && existing !== subroutine)
                nameError(subroutine.name, subroutine.position, existing)

            // does the parameter redefine a variable declared elsewhere?
            for(param in subroutine.parameters) {
                val existingVar = subroutine.lookup(listOf(param.name), subroutine)
                if (existingVar != null && existingVar.parent !== subroutine) {
                    nameError(param.name, param.position, existingVar)
                }
            }

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
            if(subroutine.asmAddress==null) {
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

            if(subroutine.isAsmSubroutine && subroutine.statements.any{it !is InlineAssembly}) {
                checkResult.add(SyntaxError("asmsub can only contain inline assembly (%asm)", subroutine.position))
            }
        }
        return super.visit(subroutine)
    }

    override fun visit(label: Label): Statement {
        if(label.name in AssemblyProgram.opcodeNames)
            checkResult.add(NameError("can't use a cpu opcode name as a symbol: '${label.name}'", label.position))

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
            if(forLoop.loopRegister == Register.X)
                printWarning("writing to the X register is dangerous, because it's used as an internal pointer", forLoop.position)
        } else {
            val loopVar = forLoop.loopVar
            if (loopVar != null) {
                val validName = forLoop.body.name.replace("<", "").replace(">", "").replace("-", "")
                val loopvarName = "prog8_loopvar_$validName"
                if (forLoop.iterable !is RangeExpr) {
                    val existing = if (forLoop.body.containsNoCodeNorVars()) null else forLoop.body.lookup(listOf(loopvarName), forLoop.body.statements.first())
                    if (existing == null) {
                        // create loop iteration counter variable (without value, to avoid an assignment)
                        val vardecl = VarDecl(VarDeclType.VAR, DataType.UBYTE, ZeropageWish.PREFER_ZEROPAGE, null, loopvarName, null, null,
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
                newValue = lval.cast(subroutine.returntypes.single())
            } else {
                newValue = returnStmt.value!!
            }

            returnStmt.value = newValue
        }
        return super.visit(returnStmt)
    }

    override fun visit(arrayLiteral: ArrayLiteralValue): Expression {
        val array = super.visit(arrayLiteral)
        if(array is ArrayLiteralValue) {
            val vardecl = array.parent as? VarDecl
            return if (vardecl!=null) {
                fixupArrayDatatype(array, vardecl, program)
            } else if(array.heapId!=null) {
                // fix the datatype of the array (also on the heap) to the 'biggest' datatype in the array
                // (we don't know the desired datatype here exactly so we guess)
                val datatype = determineArrayDt(array.value)
                val litval2 = array.cast(datatype)!!
                litval2.parent = array.parent
                // finally, replace the literal array by a identifier reference.
                makeIdentifierFromRefLv(litval2)
            } else
                array
        }
        return array
    }

    override fun visit(stringLiteral: StringLiteralValue): Expression {
        val string = super.visit(stringLiteral)
        if(string is StringLiteralValue) {
            val vardecl = string.parent as? VarDecl
            // intern the string; move it into the heap
            if (string.value.length !in 1..255)
                checkResult.add(ExpressionError("string literal length must be between 1 and 255", string.position))
            else {
                string.addToHeap(program.heap)
            }
            return if (vardecl != null)
                string
            else
                makeIdentifierFromRefLv(string)  // replace the literal string by a identifier reference.
        }
        return string
    }

    private fun determineArrayDt(array: Array<Expression>): DataType {
        val datatypesInArray = array.map { it.inferType(program) }
        if(datatypesInArray.isEmpty() || datatypesInArray.any { !it.isKnown })
            throw IllegalArgumentException("can't determine type of empty array")
        val dts = datatypesInArray.map { it.typeOrElse(DataType.STRUCT) }
        return when {
            DataType.FLOAT in dts -> DataType.ARRAY_F
            DataType.WORD in dts -> DataType.ARRAY_W
            DataType.UWORD in dts -> DataType.ARRAY_UW
            DataType.BYTE in dts -> DataType.ARRAY_B
            DataType.UBYTE in dts -> DataType.ARRAY_UB
            else -> throw IllegalArgumentException("can't determine type of array")
        }
    }

    private fun makeIdentifierFromRefLv(array: ArrayLiteralValue): IdentifierReference {
        // a referencetype literal value that's not declared as a variable
        // we need to introduce an auto-generated variable for this to be able to refer to the value
        // note: if the var references the same literal value, it is not yet de-duplicated here.
        array.addToHeap(program.heap)
        val scope = array.definingScope()
        val variable = VarDecl.createAuto(array)
        return replaceWithIdentifier(variable, scope, array.parent)
    }

    private fun makeIdentifierFromRefLv(string: StringLiteralValue): IdentifierReference {
        // a referencetype literal value that's not declared as a variable
        // we need to introduce an auto-generated variable for this to be able to refer to the value
        // note: if the var references the same literal value, it is not yet de-duplicated here.
        string.addToHeap(program.heap)
        val scope = string.definingScope()
        val variable = VarDecl.createAuto(string)
        return replaceWithIdentifier(variable, scope, string.parent)
    }

    private fun replaceWithIdentifier(variable: VarDecl, scope: INameScope, parent: Node): IdentifierReference {
        val variable1 = addVarDecl(scope, variable)
        // replace the reference literal by a identifier reference
        val identifier = IdentifierReference(listOf(variable1.name), variable1.position)
        identifier.parent = parent
        return identifier
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
            expr.left is StringLiteralValue ->
                processBinaryExprWithString(expr.left as StringLiteralValue, expr.right, expr)
            expr.right is StringLiteralValue ->
                processBinaryExprWithString(expr.right as StringLiteralValue, expr.left, expr)
            else -> super.visit(expr)
        }
    }

    private fun processBinaryExprWithString(string: StringLiteralValue, operand: Expression, expr: BinaryExpression): Expression {
        val constvalue = operand.constValue(program)
        if(constvalue!=null) {
            if (expr.operator == "*") {
                // repeat a string a number of times
                val idt = string.inferType(program)
                return StringLiteralValue(idt.typeOrElse(DataType.STR),
                        string.value.repeat(constvalue.number.toInt()), null, expr.position)
            }
        }
        if(expr.operator == "+" && operand is StringLiteralValue) {
            // concatenate two strings
            val idt = string.inferType(program)
            return StringLiteralValue(idt.typeOrElse(DataType.STR),
                    "${string.value}${operand.value}", null, expr.position)
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

internal fun fixupArrayDatatype(array: ArrayLiteralValue, program: Program): ArrayLiteralValue {
    val dts = array.value.map {it.inferType(program).typeOrElse(DataType.STRUCT)}.toSet()
    if(dts.any { it !in NumericDatatypes }) {
        return array
    }
    val dt = when {
        DataType.FLOAT in dts -> DataType.ARRAY_F
        DataType.WORD in dts -> DataType.ARRAY_W
        DataType.UWORD in dts -> DataType.ARRAY_UW
        DataType.BYTE in dts -> DataType.ARRAY_B
        else -> DataType.ARRAY_UB
    }
    if(dt==array.type)
        return array

    // convert values and array type
    val elementType = ArrayElementTypes.getValue(dt)
    val values = array.value.map { (it as NumericLiteralValue).cast(elementType) as Expression}.toTypedArray()
    val array2 = ArrayLiteralValue(dt, values, array.heapId, array.position)
    array2.linkParents(array.parent)
    return array2
}

internal fun fixupArrayDatatype(array: ArrayLiteralValue, vardecl: VarDecl, program: Program): ArrayLiteralValue {
    if(array.heapId!=null) {
        val arrayDt = array.type
        if(arrayDt!=vardecl.datatype) {
            // fix the datatype of the array (also on the heap) to match the vardecl
            val litval2 =
                    try {
                        val result = array.cast(vardecl.datatype)
                        if(result==null) {
                            val constElements = array.value.mapNotNull { it.constValue(program) }
                            val elementDts = constElements.map { it.type }
                            if(DataType.FLOAT in elementDts) {
                                array.cast(DataType.ARRAY_F) ?: ArrayLiteralValue(DataType.ARRAY_F, array.value, array.heapId, array.position)
                            } else {
                                val numbers = constElements.map { it.number.toInt() }
                                val minValue = numbers.min()!!
                                val maxValue = numbers.max()!!
                                if (minValue >= 0) {
                                    // only positive values, so uword or ubyte
                                    val dt = if(maxValue<256) DataType.ARRAY_UB else DataType.ARRAY_UW
                                    array.cast(dt) ?: ArrayLiteralValue(dt, array.value, array.heapId, array.position)
                                } else {
                                    // negative value present, so word or byte
                                    val dt = if(minValue >= -128 && maxValue<=127) DataType.ARRAY_B else DataType.ARRAY_W
                                    array.cast(dt) ?: ArrayLiteralValue(dt, array.value, array.heapId, array.position)
                                }
                            }
                        }
                        else result
                    } catch(x: ExpressionError) {
                        // couldn't cast permanently.
                        // instead, simply adjust the array type and trust the AstChecker to report the exact error
                        ArrayLiteralValue(vardecl.datatype, array.value, array.heapId, array.position)
                    }
            vardecl.value = litval2
            litval2.linkParents(vardecl)
            litval2.addToHeap(program.heap)
            return litval2
        }
    }
    return array
}
