package prog8.ast.processing

import prog8.ast.*
import prog8.ast.base.DataType
import prog8.ast.base.FatalAstException
import prog8.ast.base.initvarsSubName
import prog8.ast.base.printWarning
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.functions.BuiltinFunctions


fun flattenStructAssignmentFromIdentifier(structAssignment: Assignment, program: Program): List<Assignment> {
    val identifier = structAssignment.target.identifier!!
    val identifierName = identifier.nameInSource.single()
    val targetVar = identifier.targetVarDecl(program.namespace)!!
    val struct = targetVar.struct!!
    when {
        structAssignment.value is IdentifierReference -> {
            val sourceVar = (structAssignment.value as IdentifierReference).targetVarDecl(program.namespace)!!
            if (sourceVar.struct == null)
                throw FatalAstException("can only assign arrays or structs to structs")
            // struct memberwise copy
            val sourceStruct = sourceVar.struct!!
            if(sourceStruct!==targetVar.struct) {
                // structs are not the same in assignment
                return listOf()     // error will be printed elsewhere
            }
            return struct.statements.zip(sourceStruct.statements).map { member ->
                val targetDecl = member.first as VarDecl
                val sourceDecl = member.second as VarDecl
                if(targetDecl.name != sourceDecl.name)
                    throw FatalAstException("struct member mismatch")
                val mangled = mangledStructMemberName(identifierName, targetDecl.name)
                val idref = IdentifierReference(listOf(mangled), structAssignment.position)
                val sourcemangled = mangledStructMemberName(sourceVar.name, sourceDecl.name)
                val sourceIdref = IdentifierReference(listOf(sourcemangled), structAssignment.position)
                val assign = Assignment(AssignTarget(null, idref, null, null, structAssignment.position),
                        null, sourceIdref, member.second.position)
                assign.linkParents(structAssignment)
                assign
            }
        }
        structAssignment.value is StructLiteralValue -> {
            throw IllegalArgumentException("not going to flatten a structLv assignment here")
        }
        else -> throw FatalAstException("strange struct value")
    }
}


internal class StatementReorderer(private val program: Program): IAstModifyingVisitor {
    // Reorders the statements in a way the compiler needs.
    // - 'main' block must be the very first statement UNLESS it has an address set.
    // - blocks are ordered by address, where blocks without address are put at the end.
    // - in every scope:
    //      -- the directives '%output', '%launcher', '%zeropage', '%zpreserved', '%address' and '%option' will come first.
    //      -- all vardecls then follow.
    //      -- the remaining statements then follow in their original order.
    //
    // - the 'start' subroutine in the 'main' block will be moved to the top immediately following the directives.
    // - all other subroutines will be moved to the end of their block.
    // - sorts the choices in when statement.
    //
    // Also, makes sure any value assignments get the proper type casts if needed to cast them into the target variable's type.
    // (this includes function call arguments)

    private val directivesToMove = setOf("%output", "%launcher", "%zeropage", "%zpreserved", "%address", "%option")

    override fun visit(module: Module) {
        super.visit(module)

        val (blocks, other) = module.statements.partition { it is Block }
        module.statements = other.asSequence().plus(blocks.sortedBy { (it as Block).address ?: Int.MAX_VALUE }).toMutableList()

        // make sure user-defined blocks come BEFORE library blocks, and move the "main" block to the top of everything
        val nonLibraryBlocks = module.statements.withIndex()
                .filter { it.value is Block && !(it.value as Block).isInLibrary }
                .map { it.index to it.value }
                .reversed()
        for(nonLibBlock in nonLibraryBlocks)
            module.statements.removeAt(nonLibBlock.first)
        for(nonLibBlock in nonLibraryBlocks)
            module.statements.add(0, nonLibBlock.second)
        val mainBlock = module.statements.singleOrNull { it is Block && it.name=="main" }
        if(mainBlock!=null && (mainBlock as Block).address==null) {
            module.remove(mainBlock)
            module.statements.add(0, mainBlock)
        }

        val varDecls = module.statements.filterIsInstance<VarDecl>()
        module.statements.removeAll(varDecls)
        module.statements.addAll(0, varDecls)

        val directives = module.statements.filter {it is Directive && it.directive in directivesToMove}
        module.statements.removeAll(directives)
        module.statements.addAll(0, directives)
    }

    override fun visit(block: Block): Statement {

        val subroutines = block.statements.filterIsInstance<Subroutine>()
        var numSubroutinesAtEnd = 0
        // move all subroutines to the end of the block
        for (subroutine in subroutines) {
            if(subroutine.name!="start" || block.name!="main") {
                block.remove(subroutine)
                block.statements.add(subroutine)
            }
            numSubroutinesAtEnd++
        }
        // move the "start" subroutine to the top
        if(block.name=="main") {
            block.statements.singleOrNull { it is Subroutine && it.name == "start" } ?.let {
                block.remove(it)
                block.statements.add(0, it)
                numSubroutinesAtEnd--
            }
        }

        // make sure there is a 'return' in front of the first subroutine
        // (if it isn't the first statement in the block itself, and isn't the program's entrypoint)
        if(numSubroutinesAtEnd>0 && block.statements.size > (numSubroutinesAtEnd+1)) {
            val firstSub = block.statements[block.statements.size - numSubroutinesAtEnd] as Subroutine
            if(firstSub.name != "start" && block.name != "main") {
                val stmtBeforeFirstSub = block.statements[block.statements.size - numSubroutinesAtEnd - 1]
                if (stmtBeforeFirstSub !is Return
                        && stmtBeforeFirstSub !is Jump
                        && stmtBeforeFirstSub !is Subroutine
                        && stmtBeforeFirstSub !is BuiltinFunctionStatementPlaceholder) {
                    val ret = Return(null, stmtBeforeFirstSub.position)
                    ret.linkParents(block)
                    block.statements.add(block.statements.size - numSubroutinesAtEnd, ret)
                }
            }
        }

        val varDecls = block.statements.filterIsInstance<VarDecl>()
        block.statements.removeAll(varDecls)
        block.statements.addAll(0, varDecls)
        val directives = block.statements.filter {it is Directive && it.directive in directivesToMove}
        block.statements.removeAll(directives)
        block.statements.addAll(0, directives)
        block.linkParents(block.parent)

        // create subroutine that initializes the block's variables (if any)
        val varInits = block.statements.withIndex().filter { it.value is VariableInitializationAssignment }
        if(varInits.isNotEmpty()) {
            val statements = varInits.map{it.value}.toMutableList()
            val varInitSub = Subroutine(initvarsSubName, emptyList(), emptyList(), emptyList(), emptyList(),
                    emptySet(), null, false, statements, block.position)
            varInitSub.keepAlways = true
            varInitSub.linkParents(block)
            block.statements.add(varInitSub)

            // remove the varinits from the block's statements
            for(index in varInits.map{it.index}.reversed())
                block.statements.removeAt(index)
        }

        return super.visit(block)
    }

    override fun visit(subroutine: Subroutine): Statement {
        super.visit(subroutine)

        val varDecls = subroutine.statements.filterIsInstance<VarDecl>()
        subroutine.statements.removeAll(varDecls)
        subroutine.statements.addAll(0, varDecls)
        val directives = subroutine.statements.filter {it is Directive && it.directive in directivesToMove}
        subroutine.statements.removeAll(directives)
        subroutine.statements.addAll(0, directives)

        if(subroutine.returntypes.isEmpty()) {
            // add the implicit return statement at the end (if it's not there yet), but only if it's not a kernel routine.
            // and if an assembly block doesn't contain a rts/rti
            if(subroutine.asmAddress==null && subroutine.amountOfRtsInAsm()==0) {
                if (subroutine.statements.lastOrNull {it !is VarDecl } !is Return) {
                    val returnStmt = Return(null, subroutine.position)
                    returnStmt.linkParents(subroutine)
                    subroutine.statements.add(returnStmt)
                }
            }
        }

        return subroutine
    }

    override fun visit(expr: BinaryExpression): Expression {
        val leftDt = expr.left.inferType(program)
        val rightDt = expr.right.inferType(program)
        if(leftDt!=null && rightDt!=null && leftDt!=rightDt) {
            // determine common datatype and add typecast as required to make left and right equal types
            val (commonDt, toFix) = expr.commonDatatype(leftDt, rightDt, expr.left, expr.right)
            if(toFix!=null) {
                when {
                    toFix===expr.left -> {
                        expr.left = TypecastExpression(expr.left, commonDt, true, expr.left.position)
                        expr.left.linkParents(expr)
                    }
                    toFix===expr.right -> {
                        expr.right = TypecastExpression(expr.right, commonDt, true, expr.right.position)
                        expr.right.linkParents(expr)
                    }
                    else -> throw FatalAstException("confused binary expression side")
                }
            }
        }
        return super.visit(expr)
    }

    override fun visit(assignment: Assignment): Statement {
        val assg = super.visit(assignment)
        if(assg !is Assignment)
            return assg

        // see if a typecast is needed to convert the value's type into the proper target type
        val valuetype = assg.value.inferType(program)
        val targettype = assg.target.inferType(program, assg)
        if(targettype!=null && valuetype!=null) {
            if(valuetype!=targettype) {
                if (valuetype isAssignableTo targettype) {
                    assg.value = TypecastExpression(assg.value, targettype, true, assg.value.position)
                    assg.value.linkParents(assg)
                }
                // if they're not assignable, we'll get a proper error later from the AstChecker
            }
        }

        // struct assignments will be flattened (if it's not a struct literal)
        if(valuetype==DataType.STRUCT && targettype==DataType.STRUCT) {
            if(assg.value is StructLiteralValue)
                return assg  // do NOT flatten it at this point!! (the compiler will take care if it, later, if needed)

            val assignments = flattenStructAssignmentFromIdentifier(assg, program)    //   'structvar1 = structvar2'
            return if(assignments.isEmpty()) {
                // something went wrong (probably incompatible struct types)
                // we'll get an error later from the AstChecker
                assg
            } else {
                val scope = AnonymousScope(assignments.toMutableList(), assg.position)
                scope.linkParents(assg.parent)
                scope
            }
        }

        if(assg.aug_op!=null) {
            // transform augmented assg into normal assg so we have one case less to deal with later
            val newTarget: Expression =
                    when {
                        assg.target.register != null -> RegisterExpr(assg.target.register!!, assg.target.position)
                        assg.target.identifier != null -> assg.target.identifier!!
                        assg.target.arrayindexed != null -> assg.target.arrayindexed!!
                        assg.target.memoryAddress != null -> DirectMemoryRead(assg.target.memoryAddress!!.addressExpression, assg.value.position)
                        else -> throw FatalAstException("strange assg")
                    }

            val expression = BinaryExpression(newTarget, assg.aug_op.substringBeforeLast('='), assg.value, assg.position)
            expression.linkParents(assg.parent)
            val convertedAssignment = Assignment(assg.target, null, expression, assg.position)
            convertedAssignment.linkParents(assg.parent)
            return super.visit(convertedAssignment)
        }

        return assg
    }

    override fun visit(functionCallStatement: FunctionCallStatement): Statement {
        checkFunctionCallArguments(functionCallStatement, functionCallStatement.definingScope())
        return super.visit(functionCallStatement)
    }

    override fun visit(functionCall: FunctionCall): Expression {
        checkFunctionCallArguments(functionCall, functionCall.definingScope())
        return super.visit(functionCall)
    }

    private fun checkFunctionCallArguments(call: IFunctionCall, scope: INameScope) {
        // see if a typecast is needed to convert the arguments into the required parameter's type
        when(val sub = call.target.targetStatement(scope)) {
            is Subroutine -> {
                for(arg in sub.parameters.zip(call.arglist.withIndex())) {
                    val argtype = arg.second.value.inferType(program)
                    if(argtype!=null) {
                        val requiredType = arg.first.type
                        if (requiredType != argtype) {
                            if (argtype isAssignableTo requiredType) {
                                val typecasted = TypecastExpression(arg.second.value, requiredType, true, arg.second.value.position)
                                typecasted.linkParents(arg.second.value.parent)
                                call.arglist[arg.second.index] = typecasted
                            }
                            // if they're not assignable, we'll get a proper error later from the AstChecker
                        }
                    }
                }
            }
            is BuiltinFunctionStatementPlaceholder -> {
                // if(sub.name in setOf("lsl", "lsr", "rol", "ror", "rol2", "ror2", "memset", "memcopy", "memsetw", "swap"))
                val func = BuiltinFunctions.getValue(sub.name)
                if(func.pure) {
                    // non-pure functions don't get automatic typecasts because sometimes they act directly on their parameters
                    for (arg in func.parameters.zip(call.arglist.withIndex())) {
                        val argtype = arg.second.value.inferType(program)
                        if (argtype != null) {
                            if (arg.first.possibleDatatypes.any { argtype == it })
                                continue
                            for (possibleType in arg.first.possibleDatatypes) {
                                if (argtype isAssignableTo possibleType) {
                                    val typecasted = TypecastExpression(arg.second.value, possibleType, true, arg.second.value.position)
                                    typecasted.linkParents(arg.second.value.parent)
                                    call.arglist[arg.second.index] = typecasted
                                    break
                                }
                            }
                        }
                    }
                }
            }
            null -> {}
            else -> TODO("call to something weird $sub   ${call.target}")
        }
    }

    override fun visit(typecast: TypecastExpression): Expression {
        // warn about any implicit type casts to Float, because that may not be intended
        if(typecast.implicit && typecast.type in setOf(DataType.FLOAT, DataType.ARRAY_F)) {
            printWarning("byte or word value implicitly converted to float. Suggestion: use explicit cast as float, a float number, or revert to integer arithmetic", typecast.position)
        }
        return super.visit(typecast)
    }

    override fun visit(memread: DirectMemoryRead): Expression {
        // make sure the memory address is an uword
        val dt = memread.addressExpression.inferType(program)
        if(dt!=DataType.UWORD) {
            val literaladdr = memread.addressExpression as? NumericLiteralValue
            if(literaladdr!=null) {
                memread.addressExpression = literaladdr.cast(DataType.UWORD)!!
            } else {
                memread.addressExpression = TypecastExpression(memread.addressExpression, DataType.UWORD, true, memread.addressExpression.position)
                memread.addressExpression.parent = memread
            }
        }
        return super.visit(memread)
    }

    override fun visit(memwrite: DirectMemoryWrite) {
        val dt = memwrite.addressExpression.inferType(program)
        if(dt!=DataType.UWORD) {
            val literaladdr = memwrite.addressExpression as? NumericLiteralValue
            if(literaladdr!=null) {
                memwrite.addressExpression = literaladdr.cast(DataType.UWORD)!!
            } else {
                memwrite.addressExpression = TypecastExpression(memwrite.addressExpression, DataType.UWORD, true, memwrite.addressExpression.position)
                memwrite.addressExpression.parent = memwrite
            }
        }
        super.visit(memwrite)
    }

    override fun visit(structLv: StructLiteralValue): Expression {
        val litval = super.visit(structLv)
        if(litval !is StructLiteralValue)
            return litval

        val decl = litval.parent as? VarDecl
        if(decl != null) {
            val struct = decl.struct
            if(struct != null) {
                addTypecastsIfNeeded(litval, struct)
            }
        } else {
            val assign = litval.parent as? Assignment
            if (assign != null) {
                val decl2 = assign.target.identifier?.targetVarDecl(program.namespace)
                if(decl2 != null) {
                    val struct = decl2.struct
                    if(struct != null) {
                        addTypecastsIfNeeded(litval, struct)
                    }
                }
            }
        }

        return litval
    }

    private fun addTypecastsIfNeeded(structLv: StructLiteralValue, struct: StructDecl) {
        structLv.values = struct.statements.zip(structLv.values).map {
            val memberDt = (it.first as VarDecl).datatype
            val valueDt = it.second.inferType(program)
            if (valueDt != memberDt)
                TypecastExpression(it.second, memberDt, true, it.second.position)
            else
                it.second
        }
    }
}
