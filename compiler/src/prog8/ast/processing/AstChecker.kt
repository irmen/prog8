package prog8.ast.processing

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.base.printWarning
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.CompilationOptions
import prog8.compiler.HeapValues
import prog8.compiler.target.c64.FLOAT_MAX_NEGATIVE
import prog8.compiler.target.c64.FLOAT_MAX_POSITIVE
import prog8.functions.BuiltinFunctions
import java.io.File

internal class AstChecker(private val program: Program,
                         private val compilerOptions: CompilationOptions) : IAstModifyingVisitor {
    private val checkResult: MutableList<AstException> = mutableListOf()
    private val heapStringSentinel: Int
    init {
        val stringSentinel = program.heap.allEntries().firstOrNull {it.value.str==""}
        heapStringSentinel = stringSentinel?.key ?: program.heap.addString(DataType.STR, "")
    }

    fun result(): List<AstException> {
        return checkResult
    }

    override fun visit(program: Program) {
        assert(program === this.program)
        // there must be a single 'main' block with a 'start' subroutine for the program entry point.
        val mainBlocks = program.modules.flatMap { it.statements }.filter { b -> b is Block && b.name=="main" }.map { it as Block }
        if(mainBlocks.size>1)
            checkResult.add(SyntaxError("more than one 'main' block", mainBlocks[0].position))

        for(mainBlock in mainBlocks) {
            val startSub = mainBlock.subScopes()["start"] as? Subroutine
            if (startSub == null) {
                checkResult.add(SyntaxError("missing program entrypoint ('start' subroutine in 'main' block)", mainBlock.position))
            } else {
                if (startSub.parameters.isNotEmpty() || startSub.returntypes.isNotEmpty())
                    checkResult.add(SyntaxError("program entrypoint subroutine can't have parameters and/or return values", startSub.position))
            }

            // the main module cannot contain 'regular' statements (they will never be executed!)
            for (statement in mainBlock.statements) {
                val ok = when (statement) {
                    is Block -> true
                    is Directive -> true
                    is Label -> true
                    is VarDecl -> true
                    is InlineAssembly -> true
                    is INameScope -> true
                    is VariableInitializationAssignment -> true
                    is NopStatement -> true
                    else -> false
                }
                if (!ok) {
                    checkResult.add(SyntaxError("main block contains regular statements, this is not allowed (they'll never get executed). Use subroutines.", statement.position))
                    break
                }
            }
        }

        // there can be an optional single 'irq' block with a 'irq' subroutine in it,
        // which will be used as the 60hz irq routine in the vm if it's present
        val irqBlocks = program.modules.flatMap { it.statements }.filter { it is Block && it.name=="irq" }.map { it as Block }
        if(irqBlocks.size>1)
            checkResult.add(SyntaxError("more than one 'irq' block", irqBlocks[0].position))
        for(irqBlock in irqBlocks) {
            val irqSub = irqBlock.subScopes()["irq"] as? Subroutine
            if (irqSub != null) {
                if (irqSub.parameters.isNotEmpty() || irqSub.returntypes.isNotEmpty())
                    checkResult.add(SyntaxError("irq entrypoint subroutine can't have parameters and/or return values", irqSub.position))
            }
        }

        super.visit(program)
    }

    override fun visit(module: Module) {
        super.visit(module)
        val directives = module.statements.filterIsInstance<Directive>().groupBy { it.directive }
        directives.filter { it.value.size > 1 }.forEach{ entry ->
            when(entry.key) {
                "%output", "%launcher", "%zeropage", "%address" ->
                    entry.value.mapTo(checkResult) { SyntaxError("directive can just occur once", it.position) }
            }
        }
    }

    override fun visit(returnStmt: Return): IStatement {
        val expectedReturnValues = returnStmt.definingSubroutine()?.returntypes ?: emptyList()
        if(expectedReturnValues.size != returnStmt.values.size) {
            // if the return value is a function call, check the result of that call instead
            if(returnStmt.values.size==1 && returnStmt.values[0] is FunctionCall) {
                val dt = (returnStmt.values[0] as FunctionCall).inferType(program)
                if(dt!=null && expectedReturnValues.isEmpty())
                    checkResult.add(SyntaxError("invalid number of return values", returnStmt.position))
            } else
                checkResult.add(SyntaxError("invalid number of return values", returnStmt.position))
        }

        for (rv in expectedReturnValues.withIndex().zip(returnStmt.values)) {
            val valueDt=rv.second.inferType(program)
            if(rv.first.value!=valueDt)
                checkResult.add(ExpressionError("type $valueDt of return value #${rv.first.index + 1} doesn't match subroutine return type ${rv.first.value}", rv.second.position))
        }
        return super.visit(returnStmt)
    }

    override fun visit(forLoop: ForLoop): IStatement {
        if(forLoop.body.containsNoCodeNorVars())
            printWarning("for loop body is empty", forLoop.position)

        val iterableDt = forLoop.iterable.inferType(program)
        if(iterableDt !in IterableDatatypes && forLoop.iterable !is RangeExpr) {
            checkResult.add(ExpressionError("can only loop over an iterable type", forLoop.position))
        } else {
            if (forLoop.loopRegister != null) {
                printWarning("using a register as loop variable is risky (it could get clobbered in the body)", forLoop.position)
                // loop register
                if (iterableDt != DataType.UBYTE && iterableDt!= DataType.ARRAY_UB && iterableDt !in StringDatatypes)
                    checkResult.add(ExpressionError("register can only loop over bytes", forLoop.position))
            } else {
                // loop variable
                val loopvar = forLoop.loopVar!!.targetVarDecl(program.namespace)
                if(loopvar==null || loopvar.type== VarDeclType.CONST) {
                    checkResult.add(SyntaxError("for loop requires a variable to loop with", forLoop.position))

                } else {
                    when (loopvar.datatype) {
                        DataType.UBYTE -> {
                            if(iterableDt!= DataType.UBYTE && iterableDt!= DataType.ARRAY_UB && iterableDt !in StringDatatypes)
                                checkResult.add(ExpressionError("ubyte loop variable can only loop over unsigned bytes or strings", forLoop.position))
                        }
                        DataType.UWORD -> {
                            if(iterableDt!= DataType.UBYTE && iterableDt!= DataType.UWORD && iterableDt !in StringDatatypes &&
                                    iterableDt != DataType.ARRAY_UB && iterableDt!= DataType.ARRAY_UW)
                                checkResult.add(ExpressionError("uword loop variable can only loop over unsigned bytes, words or strings", forLoop.position))
                        }
                        DataType.BYTE -> {
                            if(iterableDt!= DataType.BYTE && iterableDt!= DataType.ARRAY_B)
                                checkResult.add(ExpressionError("byte loop variable can only loop over bytes", forLoop.position))
                        }
                        DataType.WORD -> {
                            if(iterableDt!= DataType.BYTE && iterableDt!= DataType.WORD &&
                                    iterableDt != DataType.ARRAY_B && iterableDt!= DataType.ARRAY_W)
                                checkResult.add(ExpressionError("word loop variable can only loop over bytes or words", forLoop.position))
                        }
                        DataType.FLOAT -> {
                            if(iterableDt!= DataType.FLOAT && iterableDt != DataType.ARRAY_F)
                                checkResult.add(ExpressionError("float loop variable can only loop over floats", forLoop.position))
                        }
                        else -> checkResult.add(ExpressionError("loop variable must be numeric type", forLoop.position))
                    }
                }
            }
        }
        return super.visit(forLoop)
    }

    override fun visit(jump: Jump): IStatement {
        if(jump.identifier!=null) {
            val targetStatement = checkFunctionOrLabelExists(jump.identifier, jump)
            if(targetStatement!=null) {
                if(targetStatement is BuiltinFunctionStatementPlaceholder)
                    checkResult.add(SyntaxError("can't jump to a builtin function", jump.position))
            }
        }

        if(jump.address!=null && (jump.address < 0 || jump.address > 65535))
            checkResult.add(SyntaxError("jump address must be valid integer 0..\$ffff", jump.position))
        return super.visit(jump)
    }

    override fun visit(block: Block): IStatement {
        if(block.address!=null && (block.address<0 || block.address>65535)) {
            checkResult.add(SyntaxError("block memory address must be valid integer 0..\$ffff", block.position))
        }

        return super.visit(block)
    }

    override fun visit(label: Label): IStatement {
        // scope check
        if(label.parent !is Block && label.parent !is Subroutine && label.parent !is AnonymousScope) {
            checkResult.add(SyntaxError("Labels can only be defined in the scope of a block, a loop body, or within another subroutine", label.position))
        }
        return super.visit(label)
    }

    /**
     * Check subroutine definition
     */
    override fun visit(subroutine: Subroutine): IStatement {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, subroutine.position))
        }

        if(subroutine.name in BuiltinFunctions)
            err("cannot redefine a built-in function")

        val uniqueNames = subroutine.parameters.asSequence().map { it.name }.toSet()
        if(uniqueNames.size!=subroutine.parameters.size)
            err("parameter names must be unique")

        super.visit(subroutine)

        // user-defined subroutines can only have zero or one return type
        // (multiple return values are only allowed for asm subs)
        if(!subroutine.isAsmSubroutine && subroutine.returntypes.size>1)
            err("subroutines can only have one return value")

        // subroutine must contain at least one 'return' or 'goto'
        // (or if it has an asm block, that must contain a 'rts' or 'jmp')
        if(subroutine.statements.count { it is Return || it is Jump } == 0) {
            if (subroutine.amountOfRtsInAsm() == 0) {
                if (subroutine.returntypes.isNotEmpty()) {
                    // for asm subroutines with an address, no statement check is possible.
                    if (subroutine.asmAddress == null)
                        err("subroutine has result value(s) and thus must have at least one 'return' or 'goto' in it (or 'rts' / 'jmp' in case of %asm)")
                }
            }
        }

        // scope check
        if(subroutine.parent !is Block && subroutine.parent !is Subroutine) {
            err("subroutines can only be defined in the scope of a block or within another subroutine")
        }

        if(subroutine.isAsmSubroutine) {
            if(subroutine.asmParameterRegisters.size != subroutine.parameters.size)
                err("number of asm parameter registers is not the isSameAs as number of parameters")
            if(subroutine.asmReturnvaluesRegisters.size != subroutine.returntypes.size)
                err("number of return registers is not the isSameAs as number of return values")
            for(param in subroutine.parameters.zip(subroutine.asmParameterRegisters)) {
                if(param.second.registerOrPair in setOf(RegisterOrPair.A, RegisterOrPair.X, RegisterOrPair.Y)) {
                    if (param.first.type != DataType.UBYTE && param.first.type != DataType.BYTE)
                        err("parameter '${param.first.name}' should be (u)byte")
                }
                else if(param.second.registerOrPair in setOf(RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY)) {
                    if (param.first.type != DataType.UWORD && param.first.type != DataType.WORD
                            && param.first.type !in StringDatatypes && param.first.type !in ArrayDatatypes && param.first.type != DataType.FLOAT)
                        err("parameter '${param.first.name}' should be (u)word/address")
                }
                else if(param.second.statusflag!=null) {
                    if (param.first.type != DataType.UBYTE)
                        err("parameter '${param.first.name}' should be ubyte")
                }
            }
            for(ret in subroutine.returntypes.withIndex().zip(subroutine.asmReturnvaluesRegisters)) {
                if(ret.second.registerOrPair in setOf(RegisterOrPair.A, RegisterOrPair.X, RegisterOrPair.Y)) {
                    if (ret.first.value != DataType.UBYTE && ret.first.value != DataType.BYTE)
                        err("return value #${ret.first.index + 1} should be (u)byte")
                }
                else if(ret.second.registerOrPair in setOf(RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY)) {
                    if (ret.first.value != DataType.UWORD && ret.first.value != DataType.WORD
                            && ret.first.value !in StringDatatypes && ret.first.value !in ArrayDatatypes && ret.first.value != DataType.FLOAT)
                        err("return value #${ret.first.index + 1} should be (u)word/address")
                }
                else if(ret.second.statusflag!=null) {
                    if (ret.first.value != DataType.UBYTE)
                        err("return value #${ret.first.index + 1} should be ubyte")
                }
            }

            val regCounts = mutableMapOf<Register, Int>().withDefault { 0 }
            val statusflagCounts = mutableMapOf<Statusflag, Int>().withDefault { 0 }
            fun countRegisters(from: Iterable<RegisterOrStatusflag>) {
                regCounts.clear()
                statusflagCounts.clear()
                for(p in from) {
                    when(p.registerOrPair) {
                        RegisterOrPair.A -> regCounts[Register.A]=regCounts.getValue(Register.A)+1
                        RegisterOrPair.X -> regCounts[Register.X]=regCounts.getValue(Register.X)+1
                        RegisterOrPair.Y -> regCounts[Register.Y]=regCounts.getValue(Register.Y)+1
                        RegisterOrPair.AX -> {
                            regCounts[Register.A]=regCounts.getValue(Register.A)+1
                            regCounts[Register.X]=regCounts.getValue(Register.X)+1
                        }
                        RegisterOrPair.AY -> {
                            regCounts[Register.A]=regCounts.getValue(Register.A)+1
                            regCounts[Register.Y]=regCounts.getValue(Register.Y)+1
                        }
                        RegisterOrPair.XY -> {
                            regCounts[Register.X]=regCounts.getValue(Register.X)+1
                            regCounts[Register.Y]=regCounts.getValue(Register.Y)+1
                        }
                        null ->
                            if(p.statusflag!=null)
                                statusflagCounts[p.statusflag] = statusflagCounts.getValue(p.statusflag) + 1
                    }
                }
            }
            countRegisters(subroutine.asmParameterRegisters)
            if(regCounts.any{it.value>1})
                err("a register is used multiple times in the parameters")
            if(statusflagCounts.any{it.value>1})
                err("a status flag is used multiple times in the parameters")
            countRegisters(subroutine.asmReturnvaluesRegisters)
            if(regCounts.any{it.value>1})
                err("a register is used multiple times in the return values")
            if(statusflagCounts.any{it.value>1})
                err("a status flag is used multiple times in the return values")

            if(subroutine.asmClobbers.intersect(regCounts.keys).isNotEmpty())
                err("a return register is also in the clobber list")
        } else {
            // TODO: non-asm subroutines can only take numeric arguments for now. (not strings and arrays) Maybe this can be improved now that we have '&' ?
            // the way string params are treated is almost okay (their address is passed) but the receiving subroutine treats it as an integer rather than referring back to the original string.
            // the way array params are treated is buggy; it thinks the subroutine needs a byte parameter in place of a byte[] ...
            // This is not easy to fix because strings and arrays are treated a bit simplistic (a "virtual" pointer to the value on the heap)
            // while passing them as subroutine parameters would require a "real" pointer OR copying the VALUE to the subroutine's parameter variable (which is very inefficient).
            // For now, don't pass strings and arrays as parameters and instead create the workaround as suggested in the error message below.
            if(!subroutine.parameters.all{it.type in NumericDatatypes }) {
                err("Non-asm subroutine can only take numerical parameters (no str/array types) for now. Workaround (for nested subroutine): access the variable from the outer scope directly.")
            }
        }
        return subroutine
    }

    /**
     * Assignment target must be register, or a variable name
     * Also check data type compatibility and number of values
     */
    override fun visit(assignment: Assignment): IStatement {

        // assigning from a functioncall COULD return multiple values (from an asm subroutine)
        if(assignment.value is FunctionCall) {
            val stmt = (assignment.value as FunctionCall).target.targetStatement(program.namespace)
            if (stmt is Subroutine) {
                if (stmt.isAsmSubroutine) {
                    if (stmt.returntypes.size != assignment.targets.size)
                        checkResult.add(ExpressionError("number of return values doesn't match number of assignment targets", assignment.value.position))
                    else {
                        for (thing in stmt.returntypes.zip(assignment.targets)) {
                            if (thing.second.inferType(program, assignment) != thing.first)
                                checkResult.add(ExpressionError("return type mismatch for target ${thing.second.shortString()}", assignment.value.position))
                        }
                    }
                } else if(assignment.targets.size>1)
                    checkResult.add(ExpressionError("only asmsub subroutines can return multiple values", assignment.value.position))
            }
        }

        var resultingAssignment = assignment
        for (target in assignment.targets) {
            resultingAssignment = processAssignmentTarget(resultingAssignment, target)
        }
        return super.visit(resultingAssignment)
    }

    private fun processAssignmentTarget(assignment: Assignment, target: AssignTarget): Assignment {
        val memAddr = target.memoryAddress?.addressExpression?.constValue(program)?.asIntegerValue
        if(memAddr!=null) {
            if(memAddr<0 || memAddr>=65536)
                checkResult.add(ExpressionError("address out of range", target.position))
        }

        if(target.identifier!=null) {
            val targetName = target.identifier.nameInSource
            val targetSymbol = program.namespace.lookup(targetName, assignment)
            when (targetSymbol) {
                null -> {
                    checkResult.add(ExpressionError("undefined symbol: ${targetName.joinToString(".")}", assignment.position))
                    return assignment
                }
                !is VarDecl -> {
                    checkResult.add(SyntaxError("assignment LHS must be register or variable", assignment.position))
                    return assignment
                }
                else -> {
                    if(targetSymbol.type == VarDeclType.CONST) {
                        checkResult.add(ExpressionError("cannot assign new value to a constant", assignment.position))
                        return assignment
                    }
                }
            }
        }

        if(assignment.aug_op!=null) {
            // check augmented assignment (and convert it into a normal assignment!)
            // A /= 3  -> check as if it was A = A / 3
            val newTarget: IExpression =
                    when {
                        target.register != null -> RegisterExpr(target.register, target.position)
                        target.identifier != null -> target.identifier
                        target.arrayindexed != null -> target.arrayindexed
                        target.memoryAddress != null -> DirectMemoryRead(target.memoryAddress!!.addressExpression, assignment.value.position)
                        else -> throw FatalAstException("strange assignment")
                    }

            val expression = BinaryExpression(newTarget, assignment.aug_op.substringBeforeLast('='), assignment.value, assignment.position)
            expression.linkParents(assignment.parent)
            val assignment2 = Assignment(listOf(target), null, expression, assignment.position)
            assignment2.linkParents(assignment.parent)
            return assignment2
        }

        val targetDatatype = target.inferType(program, assignment)
        if(targetDatatype!=null) {
            val constVal = assignment.value.constValue(program)
            if(constVal!=null) {
                val arrayspec = if(target.identifier!=null) {
                    val targetVar = program.namespace.lookup(target.identifier.nameInSource, assignment) as? VarDecl
                    targetVar?.arraysize
                } else null
                checkValueTypeAndRange(targetDatatype,
                        arrayspec ?: ArrayIndex(LiteralValue.optimalInteger(-1, assignment.position), assignment.position),
                        constVal, program.heap)
            } else {
                val sourceDatatype: DataType? = assignment.value.inferType(program)
                if(sourceDatatype==null) {
                    if(assignment.targets.size<=1) {
                        if (assignment.value is FunctionCall) {
                            val targetStmt = (assignment.value as FunctionCall).target.targetStatement(program.namespace)
                            if(targetStmt!=null)
                                checkResult.add(ExpressionError("function call doesn't return a suitable value to use in assignment", assignment.value.position))
                        }
                        else
                            checkResult.add(ExpressionError("assignment value is invalid or has no proper datatype", assignment.value.position))
                    }
                }
                else
                    checkAssignmentCompatible(targetDatatype, sourceDatatype, assignment.value, assignment.targets, assignment.position)
            }
        }
        return assignment
    }

    override fun visit(addressOf: AddressOf): IExpression {
        val variable=addressOf.identifier.targetVarDecl(program.namespace)
        if(variable==null)
            checkResult.add(ExpressionError("pointer-of operand must be the name of a heap variable", addressOf.position))
        else {
            if(variable.datatype !in ArrayDatatypes && variable.datatype !in StringDatatypes)
                checkResult.add(ExpressionError("pointer-of operand must be the name of a string or array heap variable", addressOf.position))
        }
        if(addressOf.scopedname==null)
            throw FatalAstException("the scopedname of AddressOf should have been set by now  $addressOf")
        return super.visit(addressOf)
    }

    /**
     * Check the variable declarations (values within range etc)
     */
    override fun visit(decl: VarDecl): IStatement {
        fun err(msg: String, position: Position?=null) {
            checkResult.add(SyntaxError(msg, position ?: decl.position))
        }

        // the initializer value can't refer to the variable itself (recursive definition)
        if(decl.value?.referencesIdentifier(decl.name) == true || decl.arraysize?.index?.referencesIdentifier(decl.name) == true) {
            err("recursive var declaration")
        }

        // CONST can only occur on simple types (byte, word, float)
        if(decl.type== VarDeclType.CONST) {
            if (decl.datatype !in NumericDatatypes)
                err("const modifier can only be used on numeric types (byte, word, float)")
        }

        // FLOATS
        if(!compilerOptions.floats && decl.datatype in setOf(DataType.FLOAT, DataType.ARRAY_F) && decl.type!= VarDeclType.MEMORY) {
            checkResult.add(SyntaxError("floating point used, but that is not enabled via options", decl.position))
        }

        // ARRAY without size specifier MUST have an iterable initializer value
        if(decl.isArray && decl.arraysize==null) {
            if(decl.type== VarDeclType.MEMORY)
                checkResult.add(SyntaxError("memory mapped array must have a size specification", decl.position))
            if(decl.value==null) {
                checkResult.add(SyntaxError("array variable is missing a size specification or an initialization value", decl.position))
                return decl
            }
            if(decl.value is LiteralValue && !(decl.value as LiteralValue).isArray) {
                checkResult.add(SyntaxError("unsized array declaration cannot use a single literal initialization value", decl.position))
                return decl
            }
            if(decl.value is RangeExpr)
                throw FatalAstException("range expressions in vardecls should have been converted into array values during constFolding  $decl")
        }

        when(decl.type) {
            VarDeclType.VAR, VarDeclType.CONST -> {
                if (decl.value == null) {
                    when {
                        decl.datatype in NumericDatatypes -> {
                            // initialize numeric var with value zero by default.
                            val litVal =
                                    when {
                                        decl.datatype in ByteDatatypes -> LiteralValue(decl.datatype, bytevalue = 0, position = decl.position)
                                        decl.datatype in WordDatatypes -> LiteralValue(decl.datatype, wordvalue = 0, position = decl.position)
                                        else -> LiteralValue(decl.datatype, floatvalue = 0.0, position = decl.position)
                                    }
                            litVal.parent = decl
                            decl.value = litVal
                        }
                        decl.type== VarDeclType.VAR -> {
                            val litVal = LiteralValue(decl.datatype, initHeapId = heapStringSentinel, position = decl.position)    // point to the sentinel heap value instead
                            litVal.parent=decl
                            decl.value = litVal
                        }
                        else -> err("var/const declaration needs a compile-time constant initializer value for type ${decl.datatype}")
                        // const fold should have provided it!
                    }
                    return super.visit(decl)
                }
                when {
                    decl.value is RangeExpr -> {
                        if(decl.arraysize!=null)
                            checkValueTypeAndRange(decl.datatype, decl.arraysize!!, decl.value as RangeExpr)
                    }
                    decl.value is LiteralValue -> {
                        val arraySpec = decl.arraysize ?: (
                                if((decl.value as LiteralValue).isArray)
                                    ArrayIndex.forArray(decl.value as LiteralValue, program.heap)
                                else
                                    ArrayIndex(LiteralValue.optimalInteger(-2, decl.position), decl.position)
                                )
                        checkValueTypeAndRange(decl.datatype, arraySpec, decl.value as LiteralValue, program.heap)
                    }
                    else -> {
                        err("var/const declaration needs a compile-time constant initializer value, or range, instead found: ${decl.value!!::class.simpleName}")
                        return super.visit(decl)
                    }
                }
            }
            VarDeclType.MEMORY -> {
                if(decl.arraysize!=null) {
                    val arraySize = decl.arraysize!!.size() ?: 1
                    when(decl.datatype) {
                        DataType.ARRAY_B, DataType.ARRAY_UB ->
                            if(arraySize > 256)
                                err("byte array length must be 1-256")
                        DataType.ARRAY_W, DataType.ARRAY_UW ->
                            if(arraySize > 128)
                                err("word array length must be 1-128")
                        DataType.ARRAY_F ->
                            if(arraySize > 51)
                                err("float array length must be 1-51")
                        else -> {}
                    }
                }

                if(decl.value !is LiteralValue) {
                    err("value of memory var decl is not a literal (it is a ${decl.value!!::class.simpleName}).", decl.value?.position)
                } else {
                    val value = decl.value as LiteralValue
                    if (value.asIntegerValue == null || value.asIntegerValue< 0 || value.asIntegerValue > 65535) {
                        err("memory address must be valid integer 0..\$ffff")
                    }
                }
            }
        }

        return super.visit(decl)
    }

    /**
     * check the arguments of the directive
     */
    override fun visit(directive: Directive): IStatement {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, directive.position))
        }
        when(directive.directive) {
            "%output" -> {
                if(directive.parent !is Module) err("this directive may only occur at module level")
                if(directive.args.size!=1 || directive.args[0].name != "raw" && directive.args[0].name != "prg")
                    err("invalid output directive type, expected raw or prg")
            }
            "%launcher" -> {
                if(directive.parent !is Module) err("this directive may only occur at module level")
                if(directive.args.size!=1 || directive.args[0].name != "basic" && directive.args[0].name != "none")
                    err("invalid launcher directive type, expected basic or none")
            }
            "%zeropage" -> {
                if(directive.parent !is Module) err("this directive may only occur at module level")
                if(directive.args.size!=1 ||
                        directive.args[0].name != "basicsafe" &&
                        directive.args[0].name != "floatsafe" &&
                        directive.args[0].name != "kernalsafe" &&
                        directive.args[0].name != "full")
                    err("invalid zp type, expected basicsafe, floatsafe, kernalsafe, or full")
            }
            "%zpreserved" -> {
                if(directive.parent !is Module) err("this directive may only occur at module level")
                if(directive.args.size!=2 || directive.args[0].int==null || directive.args[1].int==null)
                    err("requires two addresses (start, end)")
            }
            "%address" -> {
                if(directive.parent !is Module) err("this directive may only occur at module level")
                if(directive.args.size!=1 || directive.args[0].int == null)
                    err("invalid address directive, expected numeric address argument")
            }
            "%import" -> {
                if(directive.parent !is Module) err("this directive may only occur at module level")
                if(directive.args.size!=1 || directive.args[0].name==null)
                    err("invalid import directive, expected module name argument")
                if(directive.args[0].name == (directive.parent as? Module)?.name)
                    err("invalid import directive, cannot import itself")
            }
            "%breakpoint" -> {
                if(directive.parent !is INameScope || directive.parent is Module) err("this directive may only occur in a block")
                if(directive.args.isNotEmpty())
                    err("invalid breakpoint directive, expected no arguments")
            }
            "%asminclude" -> {
                if(directive.parent !is INameScope || directive.parent is Module) err("this directive may only occur in a block")
                if(directive.args.size!=2 || directive.args[0].str==null || directive.args[1].str==null)
                    err("invalid asminclude directive, expected arguments: \"filename\", \"scopelabel\"")
                checkFileExists(directive, directive.args[0].str!!)
            }
            "%asmbinary" -> {
                if(directive.parent !is INameScope || directive.parent is Module) err("this directive may only occur in a block")
                val errormsg = "invalid asmbinary directive, expected arguments: \"filename\" [, offset [, length ] ]"
                if(directive.args.isEmpty()) err(errormsg)
                if(directive.args.isNotEmpty() && directive.args[0].str==null) err(errormsg)
                if(directive.args.size>=2 && directive.args[1].int==null) err(errormsg)
                if(directive.args.size==3 && directive.args[2].int==null) err(errormsg)
                if(directive.args.size>3) err(errormsg)
                checkFileExists(directive, directive.args[0].str!!)
            }
            "%option" -> {
                if(directive.parent !is Block && directive.parent !is Module) err("this directive may only occur in a block or at module level")
                if(directive.args.isEmpty())
                    err("missing option directive argument(s)")
                else if(directive.args.map{it.name in setOf("enable_floats", "force_output")}.any { !it })
                    err("invalid option directive argument(s)")
            }
            else -> throw SyntaxError("invalid directive ${directive.directive}", directive.position)
        }
        return super.visit(directive)
    }

    private fun checkFileExists(directive: Directive, filename: String) {
        var definingModule = directive.parent
        while (definingModule !is Module)
            definingModule = definingModule.parent
        if (!(filename.startsWith("library:") || definingModule.source.resolveSibling(filename).toFile().isFile || File(filename).isFile))
            checkResult.add(NameError("included file not found: $filename", directive.position))
    }

    override fun visit(literalValue: LiteralValue): LiteralValue {
        if(!compilerOptions.floats && literalValue.type in setOf(DataType.FLOAT, DataType.ARRAY_F)) {
            checkResult.add(SyntaxError("floating point used, but that is not enabled via options", literalValue.position))
        }
        val arrayspec =
                if(literalValue.isArray)
                    ArrayIndex.forArray(literalValue, program.heap)
                else
                    ArrayIndex(LiteralValue.optimalInteger(-3, literalValue.position), literalValue.position)
        checkValueTypeAndRange(literalValue.type, arrayspec, literalValue, program.heap)

        val lv = super.visit(literalValue)
        when(lv.type) {
            in StringDatatypes -> {
                if(lv.heapId==null)
                    throw FatalAstException("string should have been moved to heap at ${lv.position}")
            }
            in ArrayDatatypes -> {
                if(lv.heapId==null)
                    throw FatalAstException("array should have been moved to heap at ${lv.position}")
            }
            else -> {}
        }
        return lv
    }

    override fun visit(expr: PrefixExpression): IExpression {
        if(expr.operator=="-") {
            val dt = expr.inferType(program)
            if (dt != DataType.BYTE && dt != DataType.WORD && dt != DataType.FLOAT) {
                checkResult.add(ExpressionError("can only take negative of a signed number type", expr.position))
            }
        }
        return super.visit(expr)
    }

    override fun visit(expr: BinaryExpression): IExpression {
        val leftDt = expr.left.inferType(program)
        val rightDt = expr.right.inferType(program)

        when(expr.operator){
            "/", "%" -> {
                val constvalRight = expr.right.constValue(program)
                val divisor = constvalRight?.asNumericValue?.toDouble()
                if(divisor==0.0)
                    checkResult.add(ExpressionError("division by zero", expr.right.position))
                if(expr.operator=="%") {
                    if ((rightDt != DataType.UBYTE && rightDt != DataType.UWORD) || (leftDt!= DataType.UBYTE && leftDt!= DataType.UWORD))
                        checkResult.add(ExpressionError("remainder can only be used on unsigned integer operands", expr.right.position))
                }
            }
            "**" -> {
                if(leftDt in IntegerDatatypes)
                    checkResult.add(ExpressionError("power operator requires floating point", expr.position))
            }
            "and", "or", "xor" -> {
                // only integer numeric operands accepted, and if literal constants, only boolean values accepted (0 or 1)
                if(leftDt !in IntegerDatatypes || rightDt !in IntegerDatatypes)
                    checkResult.add(ExpressionError("logical operator can only be used on boolean operands", expr.right.position))
                val constLeft = expr.left.constValue(program)
                val constRight = expr.right.constValue(program)
                if(constLeft!=null && constLeft.asIntegerValue !in 0..1 || constRight!=null && constRight.asIntegerValue !in 0..1)
                    checkResult.add(ExpressionError("const literal argument of logical operator must be boolean (0 or 1)", expr.position))
            }
            "&", "|", "^" -> {
                // only integer numeric operands accepted
                if(leftDt !in IntegerDatatypes || rightDt !in IntegerDatatypes)
                    checkResult.add(ExpressionError("bitwise operator can only be used on integer operands", expr.right.position))
            }
        }

        if(leftDt !in NumericDatatypes)
            checkResult.add(ExpressionError("left operand is not numeric", expr.left.position))
        if(rightDt!in NumericDatatypes)
            checkResult.add(ExpressionError("right operand is not numeric", expr.right.position))
        return super.visit(expr)
    }

    override fun visit(typecast: TypecastExpression): IExpression {
        if(typecast.type in IterableDatatypes)
            checkResult.add(ExpressionError("cannot type cast to string or array type", typecast.position))
        return super.visit(typecast)
    }

    override fun visit(range: RangeExpr): IExpression {
        fun err(msg: String) {
            checkResult.add(SyntaxError(msg, range.position))
        }
        super.visit(range)
        val from = range.from.constValue(program)
        val to = range.to.constValue(program)
        val stepLv = range.step.constValue(program) ?: LiteralValue(DataType.UBYTE, 1, position = range.position)
        if (stepLv.asIntegerValue == null || stepLv.asIntegerValue == 0) {
            err("range step must be an integer != 0")
            return range
        }
        val step = stepLv.asIntegerValue
        if(from!=null && to != null) {
            when {
                from.asIntegerValue!=null && to.asIntegerValue!=null -> {
                    if(from.asIntegerValue == to.asIntegerValue)
                        printWarning("range is just a single value, don't use a loop here", range.position)
                    else if(from.asIntegerValue < to.asIntegerValue && step<=0)
                        err("ascending range requires step > 0")
                    else if(from.asIntegerValue > to.asIntegerValue && step>=0)
                        err("descending range requires step < 0")
                }
                from.isString && to.isString -> {
                    val fromString = from.strvalue!!
                    val toString = to.strvalue!!
                    if(fromString.length!=1 || toString.length!=1)
                        err("range from and to must be a single character")
                    if(fromString[0] == toString[0])
                        printWarning("range contains just a single character", range.position)
                    else if(fromString[0] < toString[0] && step<=0)
                        err("ascending range requires step > 0")
                    else if(fromString[0] > toString[0] && step>=0)
                        err("descending range requires step < 0")
                }
                else -> err("range expression must be over integers or over characters")
            }
        }
        return range
    }

    override fun visit(functionCall: FunctionCall): IExpression {
        // this function call is (part of) an expression, which should be in a statement somewhere.
        val stmtOfExpression = findParentNode<IStatement>(functionCall)
                ?: throw FatalAstException("cannot determine statement scope of function call expression at ${functionCall.position}")

        val targetStatement = checkFunctionOrLabelExists(functionCall.target, stmtOfExpression)
        if(targetStatement!=null)
            checkFunctionCall(targetStatement, functionCall.arglist, functionCall.position)
        return super.visit(functionCall)
    }

    override fun visit(functionCallStatement: FunctionCallStatement): IStatement {
        val targetStatement = checkFunctionOrLabelExists(functionCallStatement.target, functionCallStatement)
        if(targetStatement!=null)
            checkFunctionCall(targetStatement, functionCallStatement.arglist, functionCallStatement.position)
        if(targetStatement is Subroutine && targetStatement.returntypes.isNotEmpty())
            printWarning("result value of subroutine call is discarded", functionCallStatement.position)
        return super.visit(functionCallStatement)
    }

    private fun checkFunctionCall(target: IStatement, args: List<IExpression>, position: Position) {
        if(target is Label && args.isNotEmpty())
            checkResult.add(SyntaxError("cannot use arguments when calling a label", position))

        if(target is BuiltinFunctionStatementPlaceholder) {
            // it's a call to a builtin function.
            val func = BuiltinFunctions.getValue(target.name)
            if(args.size!=func.parameters.size)
                checkResult.add(SyntaxError("invalid number of arguments", position))
            else {
                for (arg in args.withIndex().zip(func.parameters)) {
                    val argDt=arg.first.value.inferType(program)
                    if(argDt!=null && !(argDt isAssignableTo arg.second.possibleDatatypes)) {
                        checkResult.add(ExpressionError("builtin function '${target.name}' argument ${arg.first.index + 1} has invalid type $argDt, expected ${arg.second.possibleDatatypes}", position))
                    }
                }
                if(target.name=="swap") {
                    // swap() is a bit weird because this one is translated into a sequence of bytecodes, instead of being an actual function call
                    val dt1 = args[0].inferType(program)!!
                    val dt2 = args[1].inferType(program)!!
                    if (dt1 != dt2)
                        checkResult.add(ExpressionError("swap requires 2 args of identical type", position))
                    else if (args[0].constValue(program) != null || args[1].constValue(program) != null)
                        checkResult.add(ExpressionError("swap requires 2 variables, not constant value(s)", position))
                    else if(args[0] isSameAs args[1])
                        checkResult.add(ExpressionError("swap should have 2 different args", position))
                    else if(dt1 !in NumericDatatypes)
                        checkResult.add(ExpressionError("swap requires args of numerical type", position))
                }
            }
        } else if(target is Subroutine) {
            if(args.size!=target.parameters.size)
                checkResult.add(SyntaxError("invalid number of arguments", position))
            else {
                for (arg in args.withIndex().zip(target.parameters)) {
                    val argDt = arg.first.value.inferType(program)
                    if(argDt!=null && !(argDt isAssignableTo arg.second.type)) {
                        // for asm subroutines having STR param it's okay to provide a UWORD too (pointer value)
                        if(!(target.isAsmSubroutine && arg.second.type in StringDatatypes && argDt== DataType.UWORD))
                            checkResult.add(ExpressionError("subroutine '${target.name}' argument ${arg.first.index + 1} has invalid type $argDt, expected ${arg.second.type}", position))
                    }

                    if(target.isAsmSubroutine) {
                        if (target.asmParameterRegisters[arg.first.index].registerOrPair in setOf(RegisterOrPair.AX, RegisterOrPair.XY, RegisterOrPair.X)) {
                            if (arg.first.value !is LiteralValue && arg.first.value !is IdentifierReference)
                                printWarning("calling a subroutine that expects X as a parameter is problematic, more so when providing complex arguments. If you see a compiler error/crash about this later, try to simplify this call", position)
                        }

                        // check if the argument types match the register(pairs)
                        val asmParamReg = target.asmParameterRegisters[arg.first.index]
                        if(asmParamReg.statusflag!=null) {
                            if(argDt !in ByteDatatypes)
                                checkResult.add(ExpressionError("subroutine '${target.name}' argument ${arg.first.index + 1} must be byte type for statusflag", position))
                        } else if(asmParamReg.registerOrPair in setOf(RegisterOrPair.A, RegisterOrPair.X, RegisterOrPair.Y)) {
                            if(argDt !in ByteDatatypes)
                                checkResult.add(ExpressionError("subroutine '${target.name}' argument ${arg.first.index + 1} must be byte type for single register", position))
                        } else if(asmParamReg.registerOrPair in setOf(RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY)) {
                            if(argDt !in WordDatatypes + IterableDatatypes)
                                checkResult.add(ExpressionError("subroutine '${target.name}' argument ${arg.first.index + 1} must be word type for register pair", position))
                        }
                    }
                }
            }
        }
    }

    override fun visit(postIncrDecr: PostIncrDecr): IStatement {
        if(postIncrDecr.target.identifier != null) {
            val targetName = postIncrDecr.target.identifier!!.nameInSource
            val target = program.namespace.lookup(targetName, postIncrDecr)
            if(target==null) {
                checkResult.add(SyntaxError("undefined symbol: ${targetName.joinToString(".")}", postIncrDecr.position))
            } else {
                if(target !is VarDecl || target.type== VarDeclType.CONST) {
                    checkResult.add(SyntaxError("can only increment or decrement a variable", postIncrDecr.position))
                } else if(target.datatype !in NumericDatatypes) {
                    checkResult.add(SyntaxError("can only increment or decrement a byte/float/word variable", postIncrDecr.position))
                }
            }
        } else if(postIncrDecr.target.arrayindexed != null) {
            val target = postIncrDecr.target.arrayindexed?.identifier?.targetStatement(program.namespace)
            if(target==null) {
                checkResult.add(SyntaxError("undefined symbol", postIncrDecr.position))
            }
            else {
                val dt = (target as VarDecl).datatype
                if(dt !in NumericDatatypes && dt !in ArrayDatatypes)
                    checkResult.add(SyntaxError("can only increment or decrement a byte/float/word", postIncrDecr.position))
            }
        } else if(postIncrDecr.target.memoryAddress != null) {
            // a memory location can always be ++/--
        }
        return super.visit(postIncrDecr)
    }

    override fun visit(arrayIndexedExpression: ArrayIndexedExpression): IExpression {
        val target = arrayIndexedExpression.identifier.targetStatement(program.namespace)
        if(target is VarDecl) {
            if(target.datatype !in IterableDatatypes)
                checkResult.add(SyntaxError("indexing requires an iterable variable", arrayIndexedExpression.position))
            val arraysize = target.arraysize?.size()
            if(arraysize!=null) {
                // check out of bounds
                val index = (arrayIndexedExpression.arrayspec.index as? LiteralValue)?.asIntegerValue
                if(index!=null && (index<0 || index>=arraysize))
                    checkResult.add(ExpressionError("array index out of bounds", arrayIndexedExpression.arrayspec.position))
            } else if(target.datatype in StringDatatypes) {
                // check string lengths
                val heapId = (target.value as LiteralValue).heapId!!
                val stringLen = program.heap.get(heapId).str!!.length
                val index = (arrayIndexedExpression.arrayspec.index as? LiteralValue)?.asIntegerValue
                if(index!=null && (index<0 || index>=stringLen))
                    checkResult.add(ExpressionError("index out of bounds", arrayIndexedExpression.arrayspec.position))
            }
        } else
            checkResult.add(SyntaxError("indexing requires a variable to act upon", arrayIndexedExpression.position))

        // check index value 0..255
        val dtx = arrayIndexedExpression.arrayspec.index.inferType(program)
        if(dtx!= DataType.UBYTE && dtx!= DataType.BYTE)
            checkResult.add(SyntaxError("array indexing is limited to byte size 0..255", arrayIndexedExpression.position))

        return super.visit(arrayIndexedExpression)
    }

    override fun visit(whenStatement: WhenStatement): IStatement {
        val conditionType = whenStatement.condition.inferType(program)
        if(conditionType !in IntegerDatatypes)
            checkResult.add(SyntaxError("when condition must be an integer value", whenStatement.position))
        val choiceValues = whenStatement.choiceValues(program)
        val occurringValues = choiceValues.map {it.first}
        val tally = choiceValues.associate { it.second to occurringValues.count { ov->it.first==ov} }
        tally.filter { it.value>1 }.forEach {
            checkResult.add(SyntaxError("choice value occurs multiple times", it.key.position))
        }
        return super.visit(whenStatement)
    }

    override fun visit(whenChoice: WhenChoice) {
        if(whenChoice.value!=null) {
            val constvalue = whenChoice.value.constValue(program)
            if (constvalue == null)
                checkResult.add(SyntaxError("value of a when choice must be a constant", whenChoice.position))
            else if (constvalue.type !in IntegerDatatypes)
                checkResult.add(SyntaxError("value of a when choice must be a byte or word", whenChoice.position))
        } else {
            if(whenChoice !== (whenChoice.parent as WhenStatement).choices.last())
                checkResult.add(SyntaxError("else choice must be the last one", whenChoice.position))
        }
        super.visit(whenChoice)
    }

    private fun checkFunctionOrLabelExists(target: IdentifierReference, statement: IStatement): IStatement? {
        val targetStatement = target.targetStatement(program.namespace)
        if(targetStatement is Label || targetStatement is Subroutine || targetStatement is BuiltinFunctionStatementPlaceholder)
            return targetStatement
        checkResult.add(NameError("undefined function or subroutine: ${target.nameInSource.joinToString(".")}", statement.position))
        return null
    }

    private fun checkValueTypeAndRange(targetDt: DataType, arrayspec: ArrayIndex, range: RangeExpr) : Boolean {
        val from = range.from.constValue(program)
        val to = range.to.constValue(program)
        if(from==null || to==null) {
            checkResult.add(SyntaxError("range from and to values must be constants", range.position))
            return false
        }

        when(targetDt) {
            in NumericDatatypes -> {
                checkResult.add(SyntaxError("can't assign a range to a scalar type", range.position))
                return false
            }
            in StringDatatypes -> {
                // range check bytes (chars)
                if(!from.isString || !to.isString) {
                    checkResult.add(ExpressionError("range for string must have single characters from and to values", range.position))
                    return false
                }
                val rangeSize=range.size()
                if(rangeSize!=null && (rangeSize<0 || rangeSize>255)) {
                    checkResult.add(ExpressionError("size of range for string must be 0..255, instead of $rangeSize", range.position))
                    return false
                }
                return true
            }
            in ArrayDatatypes -> {
                // range and length check bytes
                val expectedSize = arrayspec.size()
                val rangeSize=range.size()
                if(rangeSize!=null && rangeSize != expectedSize) {
                    checkResult.add(ExpressionError("range size doesn't match array size, expected $expectedSize found $rangeSize", range.position))
                    return false
                }
                return true
            }
            else -> throw FatalAstException("invalid targetDt")
        }
    }

    private fun checkValueTypeAndRange(targetDt: DataType, arrayspec: ArrayIndex, value: LiteralValue, heap: HeapValues) : Boolean {
        fun err(msg: String) : Boolean {
            checkResult.add(ExpressionError(msg, value.position))
            return false
        }
        when (targetDt) {
            DataType.FLOAT -> {
                val number = when(value.type) {
                    in ByteDatatypes -> value.bytevalue!!.toDouble()
                    in WordDatatypes -> value.wordvalue!!.toDouble()
                    DataType.FLOAT -> value.floatvalue!!
                    else -> return err("numeric value expected")
                }
                if (number > 1.7014118345e+38 || number < -1.7014118345e+38)
                    return err("value '$number' out of range for MFLPT format")
            }
            DataType.UBYTE -> {
                val number = value.asIntegerValue ?: return if (value.floatvalue!=null)
                    err("unsigned byte value expected instead of float; possible loss of precision")
                else
                    err("unsigned byte value expected")
                if (number < 0 || number > 255)
                    return err("value '$number' out of range for unsigned byte")
            }
            DataType.BYTE -> {
                val number = value.asIntegerValue ?: return if (value.floatvalue!=null)
                    err("byte value expected instead of float; possible loss of precision")
                else
                    err("byte value expected")
                if (number < -128 || number > 127)
                    return err("value '$number' out of range for byte")
            }
            DataType.UWORD -> {
                val number = value.asIntegerValue ?: return if (value.floatvalue!=null)
                    err("unsigned word value expected instead of float; possible loss of precision")
                else
                    err("unsigned word value or address expected")
                if (number < 0 || number > 65535)
                    return err("value '$number' out of range for unsigned word")
            }
            DataType.WORD -> {
                val number = value.asIntegerValue ?: return if (value.floatvalue!=null)
                    err("word value expected instead of float; possible loss of precision")
                else
                    err("word value expected")
                if (number < -32768 || number > 32767)
                    return err("value '$number' out of range for word")
            }
            DataType.STR, DataType.STR_S -> {
                if(!value.isString)
                    return err("string value expected")
                if (value.strvalue!!.length > 255)
                    return err("string length must be 0-255")
            }
            DataType.ARRAY_UB, DataType.ARRAY_B -> {
                // value may be either a single byte, or a byte arraysize (of all constant values), or a range
                if(value.type==targetDt) {
                    if(!checkArrayValues(value, targetDt))
                        return false
                    val arraySpecSize = arrayspec.size()
                    val arraySize = value.arrayvalue?.size ?: heap.get(value.heapId!!).arraysize
                    if(arraySpecSize!=null && arraySpecSize>0) {
                        if(arraySpecSize<1 || arraySpecSize>256)
                            return err("byte array length must be 1-256")
                        val constX = arrayspec.index.constValue(program)
                        if(constX?.asIntegerValue==null)
                            return err("array size specifier must be constant integer value")
                        val expectedSize = constX.asIntegerValue
                        if (arraySize != expectedSize)
                            return err("initializer array size mismatch (expecting $expectedSize, got $arraySize)")
                        return true
                    }
                    return err("invalid byte array size, must be 1-256")
                }
                return err("invalid byte array initialization value ${value.type}, expected $targetDt")
            }
            DataType.ARRAY_UW, DataType.ARRAY_W -> {
                // value may be either a single word, or a word arraysize, or a range
                if(value.type==targetDt) {
                    if(!checkArrayValues(value, targetDt))
                        return false
                    val arraySpecSize = arrayspec.size()
                    val arraySize = value.arrayvalue?.size ?: heap.get(value.heapId!!).arraysize
                    if(arraySpecSize!=null && arraySpecSize>0) {
                        if(arraySpecSize<1 || arraySpecSize>128)
                            return err("word array length must be 1-128")
                        val constX = arrayspec.index.constValue(program)
                        if(constX?.asIntegerValue==null)
                            return err("array size specifier must be constant integer value")
                        val expectedSize = constX.asIntegerValue
                        if (arraySize != expectedSize)
                            return err("initializer array size mismatch (expecting $expectedSize, got $arraySize)")
                        return true
                    }
                    return err("invalid word array size, must be 1-128")
                }
                return err("invalid word array initialization value ${value.type}, expected $targetDt")
            }
            DataType.ARRAY_F -> {
                // value may be either a single float, or a float arraysize
                if(value.type==targetDt) {
                    if(!checkArrayValues(value, targetDt))
                        return false
                    val arraySize = value.arrayvalue?.size ?: heap.get(value.heapId!!).doubleArray!!.size
                    val arraySpecSize = arrayspec.size()
                    if(arraySpecSize!=null && arraySpecSize>0) {
                        if(arraySpecSize < 1 || arraySpecSize>51)
                            return err("float array length must be 1-51")
                        val constX = arrayspec.index.constValue(program)
                        if(constX?.asIntegerValue==null)
                            return err("array size specifier must be constant integer value")
                        val expectedSize = constX.asIntegerValue
                        if (arraySize != expectedSize)
                            return err("initializer array size mismatch (expecting $expectedSize, got $arraySize)")
                    } else
                        return err("invalid float array size, must be 1-51")

                    // check if the floating point values are all within range
                    val doubles = if(value.arrayvalue!=null)
                        value.arrayvalue.map {it.constValue(program)?.asNumericValue!!.toDouble()}.toDoubleArray()
                    else
                        heap.get(value.heapId!!).doubleArray!!
                    if(doubles.any { it < FLOAT_MAX_NEGATIVE || it> FLOAT_MAX_POSITIVE })
                        return err("floating point value overflow")
                    return true
                }
                return err("invalid float array initialization value ${value.type}, expected $targetDt")
            }
        }
        return true
    }

    private fun checkArrayValues(value: LiteralValue, type: DataType): Boolean {
        if(value.isArray && value.heapId==null) {
            // TODO weird, array literal that hasn't been moved to the heap yet?
            val array = value.arrayvalue!!.map { it.constValue(program)!! }
            val correct: Boolean
            when(type) {
                DataType.ARRAY_UB -> {
                    correct=array.all { it.bytevalue!=null && it.bytevalue in 0..255 }
                }
                DataType.ARRAY_B -> {
                    correct=array.all { it.bytevalue!=null && it.bytevalue in -128..127 }
                }
                DataType.ARRAY_UW -> {
                    correct=array.all { it.wordvalue!=null && it.wordvalue in 0..65535 }
                }
                DataType.ARRAY_W -> {
                    correct=array.all { it.wordvalue!=null && it.wordvalue in -32768..32767}
                }
                DataType.ARRAY_F -> correct = true
                else -> throw AstException("invalid array type $type")
            }
            if(!correct)
                checkResult.add(ExpressionError("array value out of range for type $type", value.position))
            return correct
        }

        val array = program.heap.get(value.heapId!!)
        val correct: Boolean
        when(type) {
            DataType.ARRAY_UB -> {
                correct=array.array!=null && array.array.all { it.integer!=null && it.integer in 0..255 }
            }
            DataType.ARRAY_B -> {
                correct=array.array!=null && array.array.all { it.integer!=null && it.integer in -128..127 }
            }
            DataType.ARRAY_UW -> {
                correct=array.array!=null && array.array.all { (it.integer!=null && it.integer in 0..65535)  || it.addressOf!=null}
            }
            DataType.ARRAY_W -> {
                correct=array.array!=null && array.array.all { it.integer!=null && it.integer in -32768..32767 }
            }
            DataType.ARRAY_F -> correct = array.doubleArray!=null
            else -> throw AstException("invalid array type $type")
        }
        if(!correct)
            checkResult.add(ExpressionError("array value out of range for type $type", value.position))
        return correct
    }

    private fun checkAssignmentCompatible(targetDatatype: DataType,
                                          sourceDatatype: DataType,
                                          sourceValue: IExpression,
                                          assignTargets: List<AssignTarget>,
                                          position: Position) : Boolean {

        if(sourceValue is RangeExpr)
            checkResult.add(SyntaxError("can't assign a range value", position))

        val result =  when(targetDatatype) {
            DataType.BYTE -> sourceDatatype== DataType.BYTE
            DataType.UBYTE -> sourceDatatype== DataType.UBYTE
            DataType.WORD -> sourceDatatype== DataType.BYTE || sourceDatatype== DataType.UBYTE || sourceDatatype== DataType.WORD
            DataType.UWORD -> sourceDatatype== DataType.UBYTE || sourceDatatype== DataType.UWORD
            DataType.FLOAT -> sourceDatatype in NumericDatatypes
            DataType.STR -> sourceDatatype== DataType.STR
            DataType.STR_S -> sourceDatatype== DataType.STR_S
            else -> checkResult.add(SyntaxError("cannot assign new value to variable of type $targetDatatype", position))
        }

        if(result)
            return true

        if((sourceDatatype== DataType.UWORD || sourceDatatype== DataType.WORD) && (targetDatatype== DataType.UBYTE || targetDatatype== DataType.BYTE)) {
            if(assignTargets.size==2 && assignTargets[0].register!=null && assignTargets[1].register!=null)
                return true // for asm subroutine calls that return a (U)WORD that's going to be stored into two BYTES (registers), we make an exception.
            else
                checkResult.add(ExpressionError("cannot assign word to byte, use msb() or lsb()?", position))
        }
        else if(sourceDatatype== DataType.FLOAT && targetDatatype in IntegerDatatypes)
            checkResult.add(ExpressionError("cannot assign float to ${targetDatatype.name.toLowerCase()}; possible loss of precision. Suggestion: round the value or revert to integer arithmetic", position))
        else
            checkResult.add(ExpressionError("cannot assign ${sourceDatatype.name.toLowerCase()} to ${targetDatatype.name.toLowerCase()}", position))

        return false
    }
}
