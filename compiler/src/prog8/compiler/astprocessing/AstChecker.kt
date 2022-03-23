package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.base.FatalAstException
import prog8.ast.base.SyntaxError
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.code.core.*
import prog8.code.target.VMTarget
import prog8.compiler.BuiltinFunctions
import prog8.compiler.builtinFunctionReturnType
import java.io.CharConversionException
import java.io.File
import kotlin.io.path.Path

/**
 * Semantic analysis.
 */
internal class AstChecker(private val program: Program,
                          private val errors: IErrorReporter,
                          private val compilerOptions: CompilationOptions
) : IAstVisitor {

    override fun visit(program: Program) {
        require(program === this.program)
        // there must be a single 'main' block with a 'start' subroutine for the program entry point.
        val mainBlocks = program.modules.flatMap { it.statements }.filter { b -> b is Block && b.name=="main" }.map { it as Block }
        if(mainBlocks.size>1)
            errors.err("more than one 'main' block", mainBlocks[0].position)
        if(mainBlocks.isEmpty())
            errors.err("there is no 'main' block", program.modules.firstOrNull()?.position ?: Position.DUMMY)

        for(mainBlock in mainBlocks) {
            val startSub = mainBlock.subScope("start") as? Subroutine
            if (startSub == null) {
                errors.err("missing program entrypoint ('start' subroutine in 'main' block)", mainBlock.position)
            } else {
                if (startSub.parameters.isNotEmpty() || startSub.returntypes.isNotEmpty())
                    errors.err("program entrypoint subroutine can't have parameters and/or return values", startSub.position)
            }
        }

        if(compilerOptions.floats) {
            if (compilerOptions.zeropage !in arrayOf(ZeropageType.FLOATSAFE, ZeropageType.BASICSAFE, ZeropageType.DONTUSE ))
                errors.err("when floats are enabled, zero page type should be 'floatsafe' or 'basicsafe' or 'dontuse'", program.toplevelModule.position)
        }

        super.visit(program)
    }

    override fun visit(module: Module) {
        super.visit(module)
        val directives = module.statements.filterIsInstance<Directive>().groupBy { it.directive }
        directives.filter { it.value.size > 1 }.forEach{ entry ->
            when(entry.key) {
                "%output", "%launcher", "%zeropage", "%address" ->
                    entry.value.forEach { errors.err("directive can just occur once", it.position) }
            }
        }
    }

    override fun visit(returnStmt: Return) {
        val expectedReturnValues = returnStmt.definingSubroutine?.returntypes ?: emptyList()
        if(expectedReturnValues.size>1) {
            throw FatalAstException("cannot use a return with one value in a subroutine that has multiple return values: $returnStmt")
        }

        if(expectedReturnValues.isEmpty() && returnStmt.value!=null) {
            errors.err("invalid number of return values", returnStmt.position)
        }
        if(expectedReturnValues.isNotEmpty() && returnStmt.value==null) {
            errors.err("invalid number of return values", returnStmt.position)
        }
        if(expectedReturnValues.size==1 && returnStmt.value!=null) {
            val valueDt = returnStmt.value!!.inferType(program)
            if(!valueDt.isKnown) {
                errors.err("return value type mismatch or unknown symbol", returnStmt.value!!.position)
            } else {
                if (expectedReturnValues[0] != valueDt.getOr(DataType.UNDEFINED))
                    errors.err("type $valueDt of return value doesn't match subroutine's return type ${expectedReturnValues[0]}", returnStmt.value!!.position)
            }
        }
        super.visit(returnStmt)
    }

    override fun visit(ifElse: IfElse) {
        if(!ifElse.condition.inferType(program).isInteger)
            errors.err("condition value should be an integer type", ifElse.condition.position)
        super.visit(ifElse)
    }

    override fun visit(forLoop: ForLoop) {

        fun checkUnsignedLoopDownto0(range: RangeExpression?) {
            if(range==null)
                return
            val step = range.step.constValue(program)?.number ?: 1.0
            if(step < -1.0) {
                val limit = range.to.constValue(program)?.number
                if(limit==0.0 && range.from.constValue(program)==null)
                    errors.err("for unsigned loop variable it's not possible to count down with step != -1 from a non-const value to exactly zero due to value wrapping", forLoop.position)
            }
        }

        val iterableDt = forLoop.iterable.inferType(program).getOr(DataType.BYTE)
        if(iterableDt !in IterableDatatypes && forLoop.iterable !is RangeExpression) {
            errors.err("can only loop over an iterable type", forLoop.position)
        } else {
            val loopvar = forLoop.loopVar.targetVarDecl(program)
            if(loopvar==null || loopvar.type== VarDeclType.CONST) {
                errors.err("for loop requires a variable to loop with", forLoop.position)
            } else {
                when (loopvar.datatype) {
                    DataType.UBYTE -> {
                        if(iterableDt!= DataType.UBYTE && iterableDt!= DataType.ARRAY_UB && iterableDt != DataType.STR)
                            errors.err("ubyte loop variable can only loop over unsigned bytes or strings", forLoop.position)

                        checkUnsignedLoopDownto0(forLoop.iterable as? RangeExpression)
                    }
                    DataType.UWORD -> {
                        if(iterableDt!= DataType.UBYTE && iterableDt!= DataType.UWORD && iterableDt != DataType.STR &&
                                iterableDt != DataType.ARRAY_UB && iterableDt!= DataType.ARRAY_UW)
                            errors.err("uword loop variable can only loop over unsigned bytes, words or strings", forLoop.position)

                        checkUnsignedLoopDownto0(forLoop.iterable as? RangeExpression)
                    }
                    DataType.BYTE -> {
                        if(iterableDt!= DataType.BYTE && iterableDt!= DataType.ARRAY_B)
                            errors.err("byte loop variable can only loop over bytes", forLoop.position)
                    }
                    DataType.WORD -> {
                        if(iterableDt!= DataType.BYTE && iterableDt!= DataType.WORD &&
                                iterableDt != DataType.ARRAY_B && iterableDt!= DataType.ARRAY_W)
                            errors.err("word loop variable can only loop over bytes or words", forLoop.position)
                    }
                    DataType.FLOAT -> {
                        // Looping over float variables is very inefficient because the loopvar is going to
                        // get copied over with new values all the time. We don't support this for now.
                        // Loop with an integer index variable if you really need to... or write different code.
                        errors.err("for loop only supports integers", forLoop.position)
                    }
                    else -> errors.err("loop variable must be numeric type", forLoop.position)
                }
                if(errors.noErrors()) {
                    // check loop range values
                    val range = forLoop.iterable as? RangeExpression
                    if(range!=null) {
                        val from = range.from as? NumericLiteral
                        val to = range.to as? NumericLiteral
                        if(from != null)
                            checkValueTypeAndRange(loopvar.datatype, from)
                        else if(range.from.inferType(program) isnot loopvar.datatype)
                            errors.err("range start value is incompatible with loop variable type", range.position)
                        if(to != null)
                            checkValueTypeAndRange(loopvar.datatype, to)
                        else if(range.to.inferType(program) isnot loopvar.datatype)
                            errors.err("range end value is incompatible with loop variable type", range.position)
                    }
                }
            }
        }

        super.visit(forLoop)
    }

    override fun visit(jump: Jump) {
        val ident = jump.identifier
        if(ident!=null) {
            val targetStatement = checkFunctionOrLabelExists(ident, jump)
            if(targetStatement!=null) {
                if(targetStatement is BuiltinFunctionPlaceholder)
                    errors.err("can't jump to a builtin function", jump.position)
            }
            if(targetStatement is Subroutine && targetStatement.parameters.any()) {
                errors.err("can't jump to a subroutine that takes parameters", jump.position)
            }
        }

        val addr = jump.address
        if(addr!=null && addr > 65535u)
            errors.err("jump address must be valid integer 0..\$ffff", jump.position)
        super.visit(jump)
    }

    override fun visit(gosub: GoSub) {
        val targetStatement = checkFunctionOrLabelExists(gosub.identifier, gosub)
        if(targetStatement!=null) {
            if(targetStatement is BuiltinFunctionPlaceholder)
                errors.err("can't gosub to a builtin function", gosub.position)
        }

        super.visit(gosub)
    }

    override fun visit(block: Block) {
        val addr = block.address
        if(addr!=null && addr>65535u) {
            errors.err("block memory address must be valid integer 0..\$ffff", block.position)
        }

        for (statement in block.statements) {
            val ok = when (statement) {
                is Block,
                is Directive,
                is Label,
                is VarDecl,
                is InlineAssembly,
                is IStatementContainer -> true
                is Assignment -> {
                    val target = statement.target.identifier!!.targetStatement(program)
                    target === statement.previousSibling()      // an initializer assignment is okay
                }
                else -> false
            }
            if (!ok) {
                errors.err("non-declarative statement occurs in block scope, where it will never be executed. Move it to a subroutine instead.", statement.position)
                break
            }
        }

        super.visit(block)
    }

    override fun visit(label: Label) {
        // scope check
        if(label.parent !is Block && label.parent !is Subroutine && label.parent !is AnonymousScope) {
            errors.err("Labels can only be defined in the scope of a block, a loop body, or within another subroutine", label.position)
        }
        super.visit(label)
    }

    private fun hasReturnOrJumpOrRts(scope: IStatementContainer): Boolean {
        class Searcher: IAstVisitor
        {
            var count=0

            override fun visit(returnStmt: Return) {
                count++
            }
            override fun visit(jump: Jump) {
                count++
            }

            override fun visit(inlineAssembly: InlineAssembly) {
                val assembly = inlineAssembly.assembly
                if(" rti" in assembly || "\trti" in assembly || " rts" in assembly || "\trts" in assembly ||
                    " jmp" in assembly || "\tjmp" in assembly || " bra" in assembly || "\tbra" in assembly )
                    count++
            }
        }

        val s=Searcher()
        for(stmt in scope.statements) {
            stmt.accept(s)
            if(s.count>0)
                return true
        }
        return s.count > 0
    }

    override fun visit(subroutine: Subroutine) {
        fun err(msg: String) = errors.err(msg, subroutine.position)

        if(subroutine.name in BuiltinFunctions)
            err("cannot redefine a built-in function")

        if(subroutine.parameters.size>6 && !subroutine.isAsmSubroutine)
            errors.warn("subroutine has a large number of parameters, this slows down code execution a lot", subroutine.position)

        val uniqueNames = subroutine.parameters.asSequence().map { it.name }.toSet()
        if(uniqueNames.size!=subroutine.parameters.size)
            err("parameter names must be unique")

        super.visit(subroutine)

        // user-defined subroutines can only have zero or one return type
        // (multiple return values are only allowed for asm subs)
        if(!subroutine.isAsmSubroutine && subroutine.returntypes.size>1)
            err("subroutines can only have one return value")

        // subroutine must contain at least one 'return' or 'goto'
        // (or if it has an asm block, that must contain a 'rts' or 'jmp' or 'bra')
        if(!hasReturnOrJumpOrRts(subroutine)) {
            if (subroutine.returntypes.isNotEmpty()) {
                // for asm subroutines with an address, no statement check is possible.
                if (subroutine.asmAddress == null && !subroutine.inline)
                    err("non-inline subroutine has result value(s) and thus must have at least one 'return' or 'goto' in it (or rts/jmp/bra in case of %asm)")
            }
        }

        if(compilerOptions.compTarget.name!=VMTarget.NAME && subroutine.inline && !subroutine.isAsmSubroutine)
            err("subroutine inlining is currently only supported on asmsub routines")

        if(subroutine.parent !is Block && subroutine.parent !is Subroutine)
            err("subroutines can only be defined in the scope of a block or within another subroutine")

        if(subroutine.isAsmSubroutine) {
            if(subroutine.asmParameterRegisters.size != subroutine.parameters.size)
                err("number of asm parameter registers is not the isSameAs as number of parameters")
            if(subroutine.asmReturnvaluesRegisters.size != subroutine.returntypes.size)
                err("number of return registers is not the isSameAs as number of return values")
            for(param in subroutine.parameters.zip(subroutine.asmParameterRegisters)) {
                if(param.second.registerOrPair in arrayOf(RegisterOrPair.A, RegisterOrPair.X, RegisterOrPair.Y)) {
                    if (param.first.type != DataType.UBYTE && param.first.type != DataType.BYTE)
                        err("parameter '${param.first.name}' should be (u)byte")
                }
                else if(param.second.registerOrPair in arrayOf(RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY)) {
                    if (param.first.type != DataType.UWORD && param.first.type != DataType.WORD
                            && param.first.type != DataType.STR && param.first.type !in ArrayDatatypes && param.first.type != DataType.FLOAT)
                        err("parameter '${param.first.name}' should be (u)word (an address) or str")
                }
                else if(param.second.statusflag!=null) {
                    if (param.first.type != DataType.UBYTE)
                        err("parameter '${param.first.name}' should be ubyte")
                }
            }
            subroutine.returntypes.zip(subroutine.asmReturnvaluesRegisters).forEachIndexed { index, pair ->
                if(pair.second.registerOrPair in arrayOf(RegisterOrPair.A, RegisterOrPair.X, RegisterOrPair.Y)) {
                    if (pair.first != DataType.UBYTE && pair.first != DataType.BYTE)
                        err("return value #${index + 1} should be (u)byte")
                }
                else if(pair.second.registerOrPair in arrayOf(RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY)) {
                    if (pair.first != DataType.UWORD && pair.first != DataType.WORD
                            && pair.first != DataType.STR && pair.first !in ArrayDatatypes && pair.first != DataType.FLOAT)
                        err("return value #${index + 1} should be (u)word/address")
                }
                else if(pair.second.statusflag!=null) {
                    if (pair.first != DataType.UBYTE)
                        err("return value #${index + 1} should be ubyte")
                }
            }

            val regCounts = mutableMapOf<CpuRegister, Int>().withDefault { 0 }
            val statusflagCounts = mutableMapOf<Statusflag, Int>().withDefault { 0 }
            fun countRegisters(from: Iterable<RegisterOrStatusflag>) {
                regCounts.clear()
                statusflagCounts.clear()
                for(p in from) {
                    when(p.registerOrPair) {
                        RegisterOrPair.A -> regCounts[CpuRegister.A]=regCounts.getValue(CpuRegister.A)+1
                        RegisterOrPair.X -> regCounts[CpuRegister.X]=regCounts.getValue(CpuRegister.X)+1
                        RegisterOrPair.Y -> regCounts[CpuRegister.Y]=regCounts.getValue(CpuRegister.Y)+1
                        RegisterOrPair.AX -> {
                            regCounts[CpuRegister.A]=regCounts.getValue(CpuRegister.A)+1
                            regCounts[CpuRegister.X]=regCounts.getValue(CpuRegister.X)+1
                        }
                        RegisterOrPair.AY -> {
                            regCounts[CpuRegister.A]=regCounts.getValue(CpuRegister.A)+1
                            regCounts[CpuRegister.Y]=regCounts.getValue(CpuRegister.Y)+1
                        }
                        RegisterOrPair.XY -> {
                            regCounts[CpuRegister.X]=regCounts.getValue(CpuRegister.X)+1
                            regCounts[CpuRegister.Y]=regCounts.getValue(CpuRegister.Y)+1
                        }
                        RegisterOrPair.FAC1, RegisterOrPair.FAC2 -> { /* no sensible way to count this */ }
                        RegisterOrPair.R0,
                        RegisterOrPair.R1,
                        RegisterOrPair.R2,
                        RegisterOrPair.R3,
                        RegisterOrPair.R4,
                        RegisterOrPair.R5,
                        RegisterOrPair.R6,
                        RegisterOrPair.R7,
                        RegisterOrPair.R8,
                        RegisterOrPair.R9,
                        RegisterOrPair.R10,
                        RegisterOrPair.R11,
                        RegisterOrPair.R12,
                        RegisterOrPair.R13,
                        RegisterOrPair.R14,
                        RegisterOrPair.R15 -> { /* no sensible way to count this */ }
                        null -> {
                            val statusf = p.statusflag
                            if (statusf != null)
                                statusflagCounts[statusf] = statusflagCounts.getValue(statusf) + 1
                        }
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

            if(subroutine.statements.any{it !is InlineAssembly})
                err("asmsub can only contain inline assembly (%asm)")

            val statusFlagsNoCarry = subroutine.asmParameterRegisters.mapNotNull { it.statusflag }.toSet() - Statusflag.Pc
            if(statusFlagsNoCarry.isNotEmpty())
                err("can only use Carry as status flag parameter")

        }

        // Non-string and non-ubytearray Pass-by-reference datatypes can not occur as parameters to a subroutine directly
        // Instead, their reference (address) should be passed (as an UWORD).
        for(p in subroutine.parameters) {
            if(p.type in PassByReferenceDatatypes && p.type !in listOf(DataType.STR, DataType.ARRAY_UB)) {
                errors.err("this pass-by-reference type can't be used as a parameter type. Instead, use an uword to receive the address, or access the variable from the outer scope directly.", p.position)
            }
        }
    }

    override fun visit(untilLoop: UntilLoop) {
        if(!untilLoop.condition.inferType(program).isInteger)
            errors.err("condition value should be an integer type", untilLoop.condition.position)
        super.visit(untilLoop)
    }

    override fun visit(whileLoop: WhileLoop) {
        if(!whileLoop.condition.inferType(program).isInteger)
            errors.err("condition value should be an integer type", whileLoop.condition.position)
        super.visit(whileLoop)
    }

    override fun visit(repeatLoop: RepeatLoop) {
        val iterations = repeatLoop.iterations?.constValue(program)
        if(iterations != null && iterations.number.toInt() > 65535)
            errors.err("repeat cannot go over 65535 iterations", iterations.position)
        super.visit(repeatLoop)
    }

    override fun visit(assignment: Assignment) {
        val targetDt = assignment.target.inferType(program)
        val valueDt = assignment.value.inferType(program)
        if(valueDt.isKnown && !(valueDt isAssignableTo targetDt)) {
            if(targetDt.isIterable)
                errors.err("cannot assign value to string or array", assignment.value.position)
            else if(!(valueDt istype DataType.STR && targetDt istype DataType.UWORD)) {
                if(targetDt.isUnknown) {
                    if(assignment.target.identifier?.targetStatement(program)!=null)
                        errors.err("target datatype is unknown", assignment.target.position)
                    // otherwise, another error about missing symbol is already reported.
                } else {
                    errors.err("type of value $valueDt doesn't match target $targetDt", assignment.value.position)
                }
            }
        }

        if(assignment.value is TypecastExpression) {
            if(assignment.isAugmentable && targetDt istype DataType.FLOAT)
                errors.err("typecasting a float value in-place makes no sense", assignment.value.position)
        }

        val numvalue = assignment.value.constValue(program)
        if(numvalue!=null && targetDt.isKnown)
            checkValueTypeAndRange(targetDt.getOr(DataType.UNDEFINED), numvalue)

        super.visit(assignment)
    }

    override fun visit(assignTarget: AssignTarget) {
        super.visit(assignTarget)

        val memAddr = assignTarget.memoryAddress?.addressExpression?.constValue(program)?.number?.toInt()
        if (memAddr != null) {
            if (memAddr < 0 || memAddr >= 65536)
                errors.err("address out of range", assignTarget.position)
        }

        val assignment = assignTarget.parent as Statement
        val targetIdentifier = assignTarget.identifier
        if (targetIdentifier != null) {
            val targetName = targetIdentifier.nameInSource
            when (val targetSymbol = assignment.definingScope.lookup(targetName)) {
                null -> {
                    errors.err("undefined symbol: ${targetIdentifier.nameInSource.joinToString(".")}", targetIdentifier.position)
                    return
                }
                !is VarDecl -> {
                    errors.err("assignment LHS must be register or variable", assignment.position)
                    return
                }
                else -> {
                    if (targetSymbol.type == VarDeclType.CONST) {
                        errors.err("cannot assign new value to a constant", assignment.position)
                        return
                    }
                }
            }
        }

        if (assignment is Assignment) {
            val targetDatatype = assignTarget.inferType(program)
            if (targetDatatype.isKnown) {
                val constVal = assignment.value.constValue(program)
                if(constVal==null) {
                    val sourceDatatype = assignment.value.inferType(program)
                    if (sourceDatatype.isUnknown) {
                        if (assignment.value !is FunctionCallExpression)
                            errors.err("assignment value is invalid or has no proper datatype, maybe forgot '&' (address-of)", assignment.value.position)
                    } else {
                        checkAssignmentCompatible(targetDatatype.getOr(DataType.UNDEFINED),
                                sourceDatatype.getOr(DataType.UNDEFINED), assignment.value)
                    }
                }
            }
        }
    }

    override fun visit(addressOf: AddressOf) {
        val variable=addressOf.identifier.targetVarDecl(program)
        if(variable!=null && variable.type==VarDeclType.CONST)
            errors.err("invalid pointer-of operand type", addressOf.position)
        super.visit(addressOf)
    }

    override fun visit(decl: VarDecl) {
        fun err(msg: String) = errors.err(msg, decl.position)

        // the initializer value can't refer to the variable itself (recursive definition)
        if(decl.value?.referencesIdentifier(listOf(decl.name)) == true || decl.arraysize?.indexExpr?.referencesIdentifier(listOf(decl.name)) == true)
            err("recursive var declaration")

        // CONST can only occur on simple types (byte, word, float)
        if(decl.type== VarDeclType.CONST) {
            if (decl.datatype !in NumericDatatypes)
                err("const modifier can only be used on numeric types (byte, word, float)")
        }

        // FLOATS enabled?
        if(!compilerOptions.floats && decl.datatype.oneOf(DataType.FLOAT, DataType.ARRAY_F) && decl.type!= VarDeclType.MEMORY)
            err("floating point used, but that is not enabled via options")

        // ARRAY without size specifier MUST have an iterable initializer value
        if(decl.isArray && decl.arraysize==null) {
            if(decl.type== VarDeclType.MEMORY)
                err("memory mapped array must have a size specification")
            if(decl.value==null) {
                err("array variable is missing a size specification or an initialization value")
                return
            }
            if(decl.value is NumericLiteral) {
                err("unsized array declaration cannot use a single literal initialization value")
                return
            }
            if(decl.value is RangeExpression)
                throw InternalCompilerException("range expressions in vardecls should have been converted into array values during constFolding  $decl")
        }

        when(decl.type) {
            VarDeclType.VAR, VarDeclType.CONST -> {
                when(decl.value) {
                    null -> {
                        // a vardecl without an initial value, don't bother with it
                    }
                    is RangeExpression -> throw InternalCompilerException("range expression should have been converted to a true array value")
                    is StringLiteral -> {
                        checkValueTypeAndRangeString(decl.datatype, decl.value as StringLiteral)
                    }
                    is ArrayLiteral -> {
                        val arraySpec = decl.arraysize ?: ArrayIndex.forArray(decl.value as ArrayLiteral)
                        checkValueTypeAndRangeArray(decl.datatype, arraySpec, decl.value as ArrayLiteral)
                    }
                    is NumericLiteral -> {
                        checkValueTypeAndRange(decl.datatype, decl.value as NumericLiteral)
                    }
                    else -> {
                        if(decl.type==VarDeclType.CONST) {
                            err("const declaration needs a compile-time constant initializer value, or range")
                            super.visit(decl)
                            return
                        }
                    }
                }
            }
            VarDeclType.MEMORY -> {
                val arraysize = decl.arraysize
                if(arraysize!=null) {
                    val arraySize = arraysize.constIndex() ?: 1
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
                val numvalue = decl.value as? NumericLiteral
                if(numvalue!=null) {
                    if (numvalue.type !in IntegerDatatypes || numvalue.number.toInt() < 0 || numvalue.number.toInt() > 65535) {
                        err("memory address must be valid integer 0..\$ffff")
                    }
                } else {
                    err("value of memory mapped variable can only be a fixed number, perhaps you meant to use an address pointer type instead?")
                }
            }
        }

        val declValue = decl.value
        if(declValue!=null && decl.type==VarDeclType.VAR) {
            if (declValue.inferType(program) isnot decl.datatype) {
                err("initialisation value has incompatible type (${declValue.inferType(program)}) for the variable (${decl.datatype})")
            }
        }

        // array length limits and constant lenghts
        if(decl.isArray) {
            val length = decl.arraysize?.constIndex()
            if(length==null)
                err("array length must be known at compile-time")
            else {
                when (decl.datatype) {
                    DataType.STR, DataType.ARRAY_UB, DataType.ARRAY_B -> {
                        if (length == 0 || length > 256)
                            err("string and byte array length must be 1-256")
                    }
                    DataType.ARRAY_UW, DataType.ARRAY_W -> {
                        if (length == 0 || length > 128)
                            err("word array length must be 1-128")
                    }
                    DataType.ARRAY_F -> {
                        if (length == 0 || length > 51)
                            err("float array length must be 1-51")
                    }
                    else -> {
                    }
                }
            }
        }

        if(decl.datatype==DataType.STR) {
            if(decl.value==null) {
                // complain about uninitialized str, but only if it's a regular variable
                val parameter = (decl.parent as? Subroutine)?.parameters?.singleOrNull{ it.name==decl.name }
                if(parameter==null)
                    err("string var must be initialized with a string literal")
            }
        }

        if(compilerOptions.zeropage==ZeropageType.DONTUSE && decl.zeropage == ZeropageWish.REQUIRE_ZEROPAGE)
            err("zeropage usage has been disabled by options")

        super.visit(decl)
    }

    override fun visit(directive: Directive) {
        fun err(msg: String) {
            errors.err(msg, directive.position)
        }
        when(directive.directive) {
            "%output" -> {
                if(directive.parent !is Module)
                    err("this directive may only occur at module level")
                if(directive.args.size!=1 || directive.args[0].name !in OutputType.values().map {it.name.lowercase()})
                    err("invalid output directive type")
            }
            "%launcher" -> {
                if(directive.parent !is Module)
                    err("this directive may only occur at module level")
                if(directive.args.size!=1 || directive.args[0].name !in CbmPrgLauncherType.values().map{it.name.lowercase()})
                    err("invalid launcher directive type")
            }
            "%zeropage" -> {
                if(directive.parent !is Module)
                    err("this directive may only occur at module level")
                if(directive.args.size!=1 ||
                        directive.args[0].name != "basicsafe" &&
                        directive.args[0].name != "floatsafe" &&
                        directive.args[0].name != "kernalsafe" &&
                        directive.args[0].name != "dontuse" &&
                        directive.args[0].name != "full")
                    err("invalid zp type, expected basicsafe, floatsafe, kernalsafe, dontuse, or full")
            }
            "%zpreserved" -> {
                if(directive.parent !is Module)
                    err("this directive may only occur at module level")
                if(directive.args.size!=2 || directive.args[0].int==null || directive.args[1].int==null)
                    err("requires two addresses (start, end)")
                if(directive.args[0].int!! > 255u || directive.args[1].int!! > 255u)
                    err("start and end addresss must be in Zeropage so 0..255")
            }
            "%address" -> {
                if(directive.parent !is Module)
                    err("this directive may only occur at module level")
                if(directive.args.size!=1 || directive.args[0].int == null)
                    err("invalid address directive, expected numeric address argument")
            }
            "%import" -> {
                if(directive.parent !is Module)
                    err("this directive may only occur at module level")
                if(directive.args.size!=1 || directive.args[0].name==null)
                    err("invalid import directive, expected module name argument")
                if(directive.args[0].name == (directive.parent as? Module)?.name)
                    err("invalid import directive, cannot import itself")
            }
            "%breakpoint" -> {
                if(directive.parent !is INameScope || directive.parent is Module)
                    err("this directive can't be used here")
                if(directive.args.isNotEmpty())
                    err("invalid breakpoint directive, expected no arguments")
            }
            "%asminclude" -> {
                if(directive.parent !is INameScope || directive.parent is Module)
                    err("this directive can't be used here")
                if(directive.args.size!=1 || directive.args[0].str==null)
                    err("invalid asminclude directive, expected argument: \"filename\"")
                checkFileExists(directive, directive.args[0].str!!)
            }
            "%asmbinary" -> {
                if(directive.parent !is INameScope || directive.parent is Module)
                    err("this directive can't be used here")
                val errormsg = "invalid asmbinary directive, expected arguments: \"filename\" [, offset [, length ] ]"
                if(directive.args.isEmpty()) err(errormsg)
                else if(directive.args[0].str==null) err(errormsg)
                else if(directive.args.size>=2 && directive.args[1].int==null) err(errormsg)
                else if(directive.args.size==3 && directive.args[2].int==null) err(errormsg)
                else if(directive.args.size>3) err(errormsg)
                else checkFileExists(directive, directive.args[0].str!!)
            }
            "%option" -> {
                if(directive.parent !is Block && directive.parent !is Module)
                    err("this directive may only occur in a block or at module level")
                if(directive.args.isEmpty())
                    err("missing option directive argument(s)")
                else if(directive.args.map{it.name in arrayOf("enable_floats", "force_output", "no_sysinit", "align_word", "align_page")}.any { !it })
                    err("invalid option directive argument(s)")
            }
            else -> throw SyntaxError("invalid directive ${directive.directive}", directive.position)
        }
        super.visit(directive)
    }

    private fun checkFileExists(directive: Directive, filename: String) {
        if (File(filename).isFile)
            return

        val definingModule = directive.definingModule
        if (definingModule.isLibrary || !definingModule.source.isFromFilesystem)
            return

        val s = definingModule.source.origin
        val sourceFileCandidate = Path(s).resolveSibling(filename).toFile()
        if (sourceFileCandidate.isFile)
            return
        else
            errors.err("included file not found: $filename", directive.position)
    }

    override fun visit(array: ArrayLiteral) {
        if(array.type.isKnown) {
            if (!compilerOptions.floats && array.type.oneOf(DataType.FLOAT, DataType.ARRAY_F)) {
                errors.err("floating point used, but that is not enabled via options", array.position)
            }
            val arrayspec = ArrayIndex.forArray(array)
            checkValueTypeAndRangeArray(array.type.getOr(DataType.UNDEFINED), arrayspec, array)
        }

        fun isPassByReferenceElement(e: Expression): Boolean {
            if(e is IdentifierReference) {
                val decl = e.targetVarDecl(program)!!
                return decl.datatype in PassByReferenceDatatypes
            }
            return e is StringLiteral
        }

        if(array.parent is VarDecl) {
            if (!array.value.all { it is NumericLiteral || it is AddressOf || isPassByReferenceElement(it) })
                errors.err("array literal for variable initialization contains non-constant elements", array.position)
        } else if(array.parent is ForLoop) {
            if (!array.value.all { it.constValue(program) != null })
                errors.err("array literal for iteration must contain constants. Try using a separate array variable instead?", array.position)
        }

        super.visit(array)
    }

    override fun visit(char: CharLiteral) {
        try {  // just *try* if it can be encoded, don't actually do it
            compilerOptions.compTarget.encodeString(char.value.toString(), char.encoding)
        } catch (cx: CharConversionException) {
            errors.err(cx.message ?: "can't encode character", char.position)
        }

        super.visit(char)
    }

    override fun visit(string: StringLiteral) {
        checkValueTypeAndRangeString(DataType.STR, string)

        try {  // just *try* if it can be encoded, don't actually do it
            val bytes = compilerOptions.compTarget.encodeString(string.value, string.encoding)
            if(0u in bytes)
                errors.warn("a character in the string encodes into the 0-byte, which will terminate the string prematurely", string.position)
        } catch (cx: CharConversionException) {
            errors.err(cx.message ?: "can't encode string", string.position)
        }

        super.visit(string)
    }

    override fun visit(expr: PrefixExpression) {
        val dt = expr.expression.inferType(program).getOr(DataType.UNDEFINED)
        if(dt==DataType.UNDEFINED)
            return  // any error should be reported elsewhere

        if(expr.operator=="-") {
            if (dt != DataType.BYTE && dt != DataType.WORD && dt != DataType.FLOAT) {
                errors.err("can only take negative of a signed number type", expr.position)
            }
        }
        else if(expr.operator == "not") {
            if(dt !in IntegerDatatypes)
                errors.err("can only use boolean not on integer types", expr.position)
        }
        else if(expr.operator == "~") {
            if(dt !in IntegerDatatypes)
                errors.err("can only use bitwise invert on integer types", expr.position)
        }
        super.visit(expr)
    }

    override fun visit(expr: BinaryExpression) {
        super.visit(expr)

        val leftIDt = expr.left.inferType(program)
        val rightIDt = expr.right.inferType(program)
        if(!leftIDt.isKnown || !rightIDt.isKnown)
            return     // hopefully this error will be detected elsewhere

        val leftDt = leftIDt.getOr(DataType.UNDEFINED)
        val rightDt = rightIDt.getOr(DataType.UNDEFINED)

        if(expr.operator=="+" || expr.operator=="-") {
            if(leftDt == DataType.STR || rightDt == DataType.STR || leftDt in ArrayDatatypes || rightDt in ArrayDatatypes) {
                errors.err("missing & (address-of) on the string operand", expr.position)
                return
            }
        }

        when(expr.operator){
            "/", "%" -> {
                val constvalRight = expr.right.constValue(program)
                val divisor = constvalRight?.number
                if(divisor==0.0)
                    errors.err("division by zero", expr.right.position)
                if(expr.operator=="%") {
                    if ((rightDt != DataType.UBYTE && rightDt != DataType.UWORD) || (leftDt!= DataType.UBYTE && leftDt!= DataType.UWORD))
                        errors.err("remainder can only be used on unsigned integer operands", expr.right.position)
                }
            }
            "**" -> {
                if(leftDt in IntegerDatatypes)
                    errors.err("power operator requires floating point operands", expr.position)
            }
            "and", "or", "xor" -> {
                // only integer numeric operands accepted, and if literal constants, only boolean values accepted (0 or 1)
                if(leftDt !in IntegerDatatypes || rightDt !in IntegerDatatypes)
                    errors.err("logical operator can only be used on boolean operands", expr.right.position)
                val constLeft = expr.left.constValue(program)
                val constRight = expr.right.constValue(program)
                if(constLeft!=null && constLeft.number.toInt() !in 0..1 || constRight!=null && constRight.number.toInt() !in 0..1)
                    errors.err("const literal argument of logical operator must be boolean (0 or 1)", expr.position)
            }
            "&", "|", "^" -> {
                // only integer numeric operands accepted
                if(leftDt !in IntegerDatatypes || rightDt !in IntegerDatatypes)
                    errors.err("bitwise operator can only be used on integer operands", expr.right.position)
            }
            "in" -> throw FatalAstException("in expression should have been replaced by containmentcheck")
        }

        if(leftDt !in NumericDatatypes && leftDt != DataType.STR)
            errors.err("left operand is not numeric or str", expr.left.position)
        if(rightDt!in NumericDatatypes && rightDt != DataType.STR)
            errors.err("right operand is not numeric or str", expr.right.position)
        if(leftDt!=rightDt) {
            if(leftDt==DataType.STR && rightDt in IntegerDatatypes) {
                // only exception allowed: str * constvalue
                if(expr.right.constValue(program)!=null)
                    errors.err("can only use string repeat with a constant number value", expr.left.position)
            } else {
                errors.err("left and right operands aren't the same type", expr.left.position)
            }
        }

        if(expr.operator !in ComparisonOperators) {
            if (leftDt == DataType.STR && rightDt == DataType.STR || leftDt in ArrayDatatypes && rightDt in ArrayDatatypes) {
                // str+str  and  str*number have already been const evaluated before we get here.
                errors.err("no computational expressions with strings or arrays are possible", expr.position)
            }
        }

    }

    override fun visit(typecast: TypecastExpression) {
        if(typecast.type in IterableDatatypes)
            errors.err("cannot type cast to string or array type", typecast.position)

        if(!typecast.expression.inferType(program).isKnown)
            errors.err("this expression doesn't return a value", typecast.expression.position)

        if(typecast.expression is NumericLiteral)
            errors.err("can't cast the value to the requested target type", typecast.expression.position)

        super.visit(typecast)
    }

    override fun visit(range: RangeExpression) {
        fun err(msg: String) {
            errors.err(msg, range.position)
        }
        super.visit(range)
        val from = range.from.constValue(program)
        val to = range.to.constValue(program)
        val stepLv = range.step.constValue(program)
        if(stepLv==null) {
            err("range step must be a constant integer")
            return
        } else if (stepLv.type !in IntegerDatatypes || stepLv.number.toInt() == 0) {
            err("range step must be an integer != 0")
            return
        }
        val step = stepLv.number.toInt()
        if(from!=null && to != null) {
            when {
                from.type in IntegerDatatypes && to.type in IntegerDatatypes -> {
                    val fromValue = from.number.toInt()
                    val toValue = to.number.toInt()
                    if(fromValue== toValue)
                        errors.warn("range is just a single value, don't use a loop here", range.position)
                    else if(fromValue < toValue && step<=0)
                        err("ascending range requires step > 0")
                    else if(fromValue > toValue && step>=0)
                        err("descending range requires step < 0")
                }
                else -> err("range expression must be over integers or over characters")
            }
        }
    }

    override fun visit(functionCallExpr: FunctionCallExpression) {
        // this function call is (part of) an expression, which should be in a statement somewhere.
        val stmtOfExpression = findParentNode<Statement>(functionCallExpr)
                ?: throw FatalAstException("cannot determine statement scope of function call expression at ${functionCallExpr.position}")

        val targetStatement = checkFunctionOrLabelExists(functionCallExpr.target, stmtOfExpression)
        if(targetStatement!=null)
            checkFunctionCall(targetStatement, functionCallExpr.args, functionCallExpr.position)

        // warn about sgn(unsigned) this is likely a mistake
        if(functionCallExpr.target.nameInSource.last()=="sgn") {
            val sgnArgType = functionCallExpr.args.first().inferType(program)
            if(sgnArgType istype DataType.UBYTE  || sgnArgType istype DataType.UWORD)
                errors.warn("sgn() of unsigned type is always 0 or 1, this is perhaps not what was intended", functionCallExpr.args.first().position)
        }

        val error = VerifyFunctionArgTypes.checkTypes(functionCallExpr, program)
        if(error!=null)
            errors.err(error.first, error.second)

        // check the functions that return multiple returnvalues.
        val stmt = functionCallExpr.target.targetStatement(program)
        if (stmt is Subroutine) {
            if (stmt.returntypes.size > 1) {
                // Currently, it's only possible to handle ONE (or zero) return values from a subroutine.
                // asmsub routines can have multiple return values, for instance in 2 different registers.
                // It's not (yet) possible to handle these multiple return values because assignments
                // are only to a single unique target at the same time.
                //   EXCEPTION:
                // if the asmsub returns multiple values and one of them is via a status register bit,
                // it *is* possible to handle them by just actually assigning the register value and
                // dealing with the status bit as just being that, the status bit after the call.
                val (returnRegisters, _) = stmt.asmReturnvaluesRegisters.partition { rr -> rr.registerOrPair != null }
                if (returnRegisters.size>1) {
                    errors.err("It's not possible to store the multiple result values of this asmsub call; you should use a small block of custom inline assembly for this.", functionCallExpr.position)
                }
            }
        }

        // functions that don't return a value, can't be used in an expression or assignment
        if(targetStatement is Subroutine) {
            if(targetStatement.returntypes.isEmpty()) {
                if(functionCallExpr.parent is Expression || functionCallExpr.parent is Assignment)
                    errors.err("subroutine doesn't return a value", functionCallExpr.position)
            }
        }
        else if(targetStatement is BuiltinFunctionPlaceholder) {
            val args = if(functionCallExpr.parent is IPipe) {
                // pipe segment, add implicit first argument
                val firstArgDt = BuiltinFunctions.getValue(targetStatement.name).parameters.first().possibleDatatypes.first()
                listOf(defaultZero(firstArgDt, functionCallExpr.position)) + functionCallExpr.args
            } else {
                functionCallExpr.args
            }
            if(builtinFunctionReturnType(targetStatement.name, args, program).isUnknown) {
                if(functionCallExpr.parent is Expression || functionCallExpr.parent is Assignment)
                    errors.err("function doesn't return a value", functionCallExpr.position)
            }
        }

        super.visit(functionCallExpr)
    }

    override fun visit(functionCallStatement: FunctionCallStatement) {
        val targetStatement = checkFunctionOrLabelExists(functionCallStatement.target, functionCallStatement)
        if(targetStatement!=null) {
            checkFunctionCall(targetStatement, functionCallStatement.args, functionCallStatement.position)
            checkUnusedReturnValues(functionCallStatement, targetStatement, program, errors)
        }

        val funcName = functionCallStatement.target.nameInSource

        if(funcName.size==1) {
            // check some builtin function calls
            if(funcName[0] == "sort") {
                // sort is not supported on float arrays
                val idref = functionCallStatement.args.singleOrNull() as? IdentifierReference
                if(idref!=null && idref.inferType(program) istype DataType.ARRAY_F) {
                    errors.err("sorting a floating point array is not supported", functionCallStatement.args.first().position)
                }
            }
            else if(funcName[0] in arrayOf("pop", "popw")) {
                // can only pop into a variable, that has to have the correct type
                val idref = functionCallStatement.args[0]
                if(idref !is IdentifierReference) {
                    if(idref is TypecastExpression) {
                        val passByRef = idref.expression.inferType(program).isPassByReference
                        if(idref.type!=DataType.UWORD || !passByRef)
                            errors.err("invalid argument to pop, must be a variable with the correct type: ${functionCallStatement.args.first()}", functionCallStatement.args.first().position)
                    } else {
                        errors.err("invalid argument to pop, must be a variable with the correct type: ${functionCallStatement.args.first()}", functionCallStatement.args.first().position)
                    }
                }
            }

            if(funcName[0] in arrayOf("rol", "ror", "rol2", "ror2", "swap", "sort", "reverse")) {
                // in-place modification, can't be done on literals
                if(functionCallStatement.args.any { it !is IdentifierReference && it !is ArrayIndexedExpression && it !is DirectMemoryRead }) {
                    errors.err("invalid argument to a in-place modifying function", functionCallStatement.args.first().position)
                }
            }

        }

        val error = VerifyFunctionArgTypes.checkTypes(functionCallStatement, program)
        if(error!=null)
            errors.err(error.first, error.second)

        super.visit(functionCallStatement)
    }

    private fun checkFunctionCall(target: Statement, args: List<Expression>, position: Position) {
        if(target is Label && args.isNotEmpty())
            errors.err("cannot use arguments when calling a label", position)

        if(target is BuiltinFunctionPlaceholder) {
            if(target.name=="swap") {
                // swap() is a bit weird because this one is translated into an operations directly, instead of being a function call
                val dt1 = args[0].inferType(program)
                val dt2 = args[1].inferType(program)
                if (dt1 != dt2)
                    errors.err("swap requires 2 args of identical type", position)
                else if (args[0].constValue(program) != null || args[1].constValue(program) != null)
                    errors.err("swap requires 2 variables, not constant value(s)", position)
                else if(args[0] isSameAs args[1])
                    errors.err("swap should have 2 different args", position)
                else if(!dt1.isNumeric)
                    errors.err("swap requires args of numerical type", position)
            }
            else if(target.name=="all" || target.name=="any") {
                if((args[0] as? AddressOf)?.identifier?.targetVarDecl(program)?.datatype == DataType.STR) {
                    errors.err("any/all on a string is useless (is always true unless the string is empty)", position)
                }
                if(args[0].inferType(program).getOr(DataType.STR) == DataType.STR) {
                    errors.err("any/all on a string is useless (is always true unless the string is empty)", position)
                }
            }
        } else if(target is Subroutine) {
            if(target.isAsmSubroutine) {
                for (arg in args.zip(target.parameters)) {
                    val argIDt = arg.first.inferType(program)
                    if (!argIDt.isKnown)
                        return
                }

                // check that cx16 virtual registers aren't used as arguments in a conflicting way
                val params = target.asmParameterRegisters.withIndex().toList()
                for(arg in args.withIndex()) {
                    var ident: IdentifierReference? = null
                    if(arg.value is IdentifierReference)
                        ident = arg.value as IdentifierReference
                    else if(arg.value is FunctionCallExpression) {
                        val fcall = arg.value as FunctionCallExpression
                        if(fcall.target.nameInSource == listOf("lsb") || fcall.target.nameInSource == listOf("msb"))
                            ident = fcall.args[0] as? IdentifierReference
                    }
                    if(ident!=null && ident.nameInSource[0] == "cx16" && ident.nameInSource[1].startsWith("r")) {
                        var regname = ident.nameInSource[1].uppercase()
                        if(regname.endsWith('L') || regname.endsWith('H') || regname.endsWith('s'))
                            regname=regname.substring(0, regname.length-1)
                        val reg = RegisterOrPair.valueOf(regname)
                        val same = params.filter { it.value.registerOrPair==reg }
                        for(s in same) {
                            if(s.index!=arg.index) {
                                errors.err("conflicting register $reg used as argument but is also a target register for another parameter", ident.position)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun visit(pipe: PipeExpression) = process(pipe)

    override fun visit(pipe: Pipe) = process(pipe)

    private fun process(pipe: IPipe) {
        if(pipe.source in pipe.segments)
            throw InternalCompilerException("pipe source and segments should all be different nodes")
        if (pipe.segments.isEmpty())
            throw FatalAstException("pipe is missing one or more expressions")
        if(pipe.segments.any { it !is IFunctionCall })
            throw FatalAstException("pipe segments can only be function calls")
    }

    override fun visit(postIncrDecr: PostIncrDecr) {
        if(postIncrDecr.target.identifier != null) {
            val targetName = postIncrDecr.target.identifier!!.nameInSource
            val target = postIncrDecr.definingScope.lookup(targetName)
            if(target==null) {
                val symbol = postIncrDecr.target.identifier!!
                errors.err("undefined symbol: ${symbol.nameInSource.joinToString(".")}", symbol.position)
            } else {
                if(target !is VarDecl || target.type== VarDeclType.CONST) {
                    errors.err("can only increment or decrement a variable", postIncrDecr.position)
                } else if(target.datatype !in NumericDatatypes) {
                    errors.err("can only increment or decrement a byte/float/word variable", postIncrDecr.position)
                }
            }
        } else if(postIncrDecr.target.arrayindexed != null) {
            val target = postIncrDecr.target.arrayindexed?.arrayvar?.targetStatement(program)
            if(target==null) {
                errors.err("undefined symbol", postIncrDecr.position)
            }
            else {
                val dt = (target as VarDecl).datatype
                if(dt !in NumericDatatypes && dt !in ArrayDatatypes)
                    errors.err("can only increment or decrement a byte/float/word", postIncrDecr.position)
            }
        }
        // else if(postIncrDecr.target.memoryAddress != null) { } // a memory location can always be ++/--
        super.visit(postIncrDecr)
    }

    override fun visit(arrayIndexedExpression: ArrayIndexedExpression) {
        val target = arrayIndexedExpression.arrayvar.targetStatement(program)
        if(target is VarDecl) {
            if(target.datatype !in IterableDatatypes)
                errors.err("indexing requires an iterable variable", arrayIndexedExpression.position)
            val arraysize = target.arraysize?.constIndex()
            if(arraysize!=null) {
                // check out of bounds
                val index = arrayIndexedExpression.indexer.constIndex()
                if(index!=null && (index<0 || index>=arraysize))
                    errors.err("array index out of bounds", arrayIndexedExpression.indexer.position)
            } else if(target.datatype == DataType.STR) {
                if(target.value is StringLiteral) {
                    // check string lengths for non-memory mapped strings
                    val stringLen = (target.value as StringLiteral).value.length
                    val index = arrayIndexedExpression.indexer.constIndex()
                    if (index != null && (index < 0 || index >= stringLen))
                        errors.err("index out of bounds", arrayIndexedExpression.indexer.position)
                }
            }
        } else
            errors.err("indexing requires a variable to act upon", arrayIndexedExpression.position)

        // check index value 0..255
        val dtxNum = arrayIndexedExpression.indexer.indexExpr.inferType(program)
        if(dtxNum isnot DataType.UBYTE && dtxNum isnot DataType.BYTE)
            errors.err("array indexing is limited to byte size 0..255", arrayIndexedExpression.position)

        super.visit(arrayIndexedExpression)
    }

    override fun visit(whenStmt: When) {
        if(!whenStmt.condition.inferType(program).isInteger)
            errors.err("when condition must be an integer value", whenStmt.position)
        val tally = mutableSetOf<Int>()
        for((choices, choiceNode) in whenStmt.choiceValues(program)) {
            if(choices!=null) {
                for (c in choices) {
                    if(c in tally)
                        errors.err("choice value already occurs earlier", choiceNode.position)
                    else
                        tally.add(c)
                }
            }
        }

        if(whenStmt.choices.isEmpty())
            errors.err("empty when statement", whenStmt.position)

        super.visit(whenStmt)
    }

    override fun visit(whenChoice: WhenChoice) {
        val whenStmt = whenChoice.parent as When
        if(whenChoice.values!=null) {
            val conditionType = whenStmt.condition.inferType(program)
            if(!conditionType.isKnown)
                throw FatalAstException("can't determine when choice datatype $whenChoice")
            val constvalues = whenChoice.values!!.map { it.constValue(program) }
            for(constvalue in constvalues) {
                when {
                    constvalue == null -> errors.err("choice value must be a constant", whenChoice.position)
                    constvalue.type !in IntegerDatatypes -> errors.err("choice value must be a byte or word", whenChoice.position)
                    conditionType isnot constvalue.type -> errors.err("choice value datatype differs from condition value", whenChoice.position)
                }
            }
        } else {
            if(whenChoice !== whenStmt.choices.last())
                errors.err("else choice must be the last one", whenChoice.position)
        }
        super.visit(whenChoice)
    }

    override fun visit(containment: ContainmentCheck) {
        val elementDt = containment.element.inferType(program)
        val iterableDt = containment.iterable.inferType(program)

        if(containment.parent is BinaryExpression)
            errors.err("containment check is currently not supported in complex expressions", containment.position)

        val range = containment.iterable as? RangeExpression
        if(range!=null && range.toConstantIntegerRange()==null)
            errors.err("containment check requires a constant integer range", range.position)

        if(iterableDt.isIterable) {
            val iterableEltDt = ArrayToElementTypes.getValue(iterableDt.getOr(DataType.UNDEFINED))
            val invalidDt = if (elementDt.isBytes) {
                iterableEltDt !in ByteDatatypes
            } else if (elementDt.isWords) {
                iterableEltDt !in WordDatatypes
            } else {
                false
            }
            if (invalidDt)
                errors.err("element datatype doesn't match iterable datatype", containment.position)
        } else {
            errors.err("value set for containment check must be an iterable type", containment.iterable.position)
        }

        super.visit(containment)
    }

    private fun checkFunctionOrLabelExists(target: IdentifierReference, statement: Statement): Statement? {
        when (val targetStatement = target.targetStatement(program)) {
            is Label, is Subroutine, is BuiltinFunctionPlaceholder -> return targetStatement
            is VarDecl -> {
                if(statement is Jump) {
                    if (targetStatement.datatype == DataType.UWORD)
                        return targetStatement
                    else
                        errors.err("wrong address variable datatype, expected uword", target.position)
                }
                else
                    errors.err("cannot call that: ${target.nameInSource.joinToString(".")}", target.position)
            }
            null -> errors.err("undefined function or subroutine: ${target.nameInSource.joinToString(".")}", target.position)
            else -> errors.err("cannot call that: ${target.nameInSource.joinToString(".")}", target.position)
        }
        return null
    }

    private fun checkValueTypeAndRangeString(targetDt: DataType, value: StringLiteral) : Boolean {
        return if (targetDt == DataType.STR) {
            when {
                value.value.length > 255 -> {
                    errors.err("string length must be 0-255", value.position)
                    false
                }
                value.value.isEmpty() -> {
                    val decl = value.parent as? VarDecl
                    if(decl!=null && (decl.zeropage==ZeropageWish.REQUIRE_ZEROPAGE || decl.zeropage==ZeropageWish.PREFER_ZEROPAGE)) {
                        errors.err("string in Zeropage must be non-empty", value.position)
                        false
                    }
                    else true
                }
                else -> true
            }
        }
        else false
    }

    private fun checkValueTypeAndRangeArray(targetDt: DataType, arrayspec: ArrayIndex, value: ArrayLiteral) : Boolean {
        fun err(msg: String) : Boolean {
            errors.err(msg, value.position)
            return false
        }

        if(value.type.isUnknown)
            return false

        when (targetDt) {
            DataType.STR -> return err("string value expected")
            DataType.ARRAY_UB, DataType.ARRAY_B -> {
                // value may be either a single byte, or a byte arraysize (of all constant values), or a range
                if(value.type istype targetDt) {
                    if(!checkArrayValues(value, targetDt))
                        return false
                    val arraySpecSize = arrayspec.constIndex()
                    val arraySize = value.value.size
                    if(arraySpecSize!=null && arraySpecSize>0) {
                        if(arraySpecSize>256)
                            return err("byte array length must be 1-256")
                        val expectedSize = arrayspec.constIndex() ?: return err("array size specifier must be constant integer value")
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
                if(value.type istype targetDt) {
                    if(!checkArrayValues(value, targetDt))
                        return false
                    val arraySpecSize = arrayspec.constIndex()
                    val arraySize = value.value.size
                    if(arraySpecSize!=null && arraySpecSize>0) {
                        if(arraySpecSize>128)
                            return err("word array length must be 1-128")
                        val expectedSize = arrayspec.constIndex() ?: return err("array size specifier must be constant integer value")
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
                if(value.type istype targetDt) {
                    if(!checkArrayValues(value, targetDt))
                        return false
                    val arraySize = value.value.size
                    val arraySpecSize = arrayspec.constIndex()
                    if(arraySpecSize!=null && arraySpecSize>0) {
                        if(arraySpecSize>51)
                            return err("float array length must be 1-51")
                        val expectedSize = arrayspec.constIndex() ?: return err("array size specifier must be constant integer value")
                        if (arraySize != expectedSize)
                            return err("initializer array size mismatch (expecting $expectedSize, got $arraySize)")
                    } else
                        return err("invalid float array size, must be 1-51")

                    // check if the floating point values are all within range
                    val doubles = value.value.map {it.constValue(program)?.number!!.toDouble()}.toDoubleArray()
                    if(doubles.any { it < compilerOptions.compTarget.machine.FLOAT_MAX_NEGATIVE || it > compilerOptions.compTarget.machine.FLOAT_MAX_POSITIVE })
                        return err("floating point value overflow")
                    return true
                }
                return err("invalid float array initialization value ${value.type}, expected $targetDt")
            }
            else -> return false
        }
    }

    private fun checkValueTypeAndRange(targetDt: DataType, value: NumericLiteral) : Boolean {
        fun err(msg: String) : Boolean {
            errors.err(msg, value.position)
            return false
        }
        when (targetDt) {
            DataType.FLOAT -> {
                val number=value.number
                if (number > 1.7014118345e+38 || number < -1.7014118345e+38)
                    return err("value '$number' out of range for MFLPT format")
            }
            DataType.UBYTE -> {
                if(value.type==DataType.FLOAT)
                    err("unsigned byte value expected instead of float; possible loss of precision")
                val number=value.number.toInt()
                if (number < 0 || number > 255)
                    return err("value '$number' out of range for unsigned byte")
            }
            DataType.BYTE -> {
                if(value.type==DataType.FLOAT)
                    err("byte value expected instead of float; possible loss of precision")
                val number=value.number.toInt()
                if (number < -128 || number > 127)
                    return err("value '$number' out of range for byte")
            }
            DataType.UWORD -> {
                if(value.type==DataType.FLOAT)
                    err("unsigned word value expected instead of float; possible loss of precision")
                val number=value.number.toInt()
                if (number < 0 || number > 65535)
                    return err("value '$number' out of range for unsigned word")
            }
            DataType.WORD -> {
                if(value.type==DataType.FLOAT)
                    err("word value expected instead of float; possible loss of precision")
                val number=value.number.toInt()
                if (number < -32768 || number > 32767)
                    return err("value '$number' out of range for word")
            }
            else -> return err("value of type ${value.type} not compatible with $targetDt")
        }
        return true
    }

    private fun checkArrayValues(value: ArrayLiteral, type: DataType): Boolean {
        val array = value.value.map {
            when (it) {
                is NumericLiteral -> it.number.toInt()
                is AddressOf -> it.identifier.hashCode() and 0xffff
                is TypecastExpression -> {
                    val constVal = it.expression.constValue(program)
                    val cast = constVal?.cast(it.type)
                    if(cast==null || !cast.isValid)
                        -9999999
                    else
                        cast.valueOrZero().number.toInt()
                }
                else -> -9999999
            }
        }
        val correct: Boolean
        when (type) {
            DataType.ARRAY_UB -> {
                correct = array.all { it in 0..255 }
            }
            DataType.ARRAY_B -> {
                correct = array.all { it in -128..127 }
            }
            DataType.ARRAY_UW -> {
                correct = array.all { (it in 0..65535) }
            }
            DataType.ARRAY_W -> {
                correct = array.all { it in -32768..32767 }
            }
            DataType.ARRAY_F -> correct = true
            else -> throw FatalAstException("invalid array type $type")
        }
        if (!correct)
            errors.err("array value out of range for type $type", value.position)
        return correct
    }

    private fun checkAssignmentCompatible(targetDatatype: DataType,
                                          sourceDatatype: DataType,
                                          sourceValue: Expression) : Boolean {
        val position = sourceValue.position

        if(sourceValue is RangeExpression) {
            errors.err("can't assign a range value to something else", position)
            return false
        }

        if(sourceDatatype==DataType.UNDEFINED) {
            errors.err("assignment right hand side doesn't result in a value", position)
            return false
        }

        val result =  when(targetDatatype) {
            DataType.BYTE -> sourceDatatype== DataType.BYTE
            DataType.UBYTE -> sourceDatatype== DataType.UBYTE
            DataType.WORD -> sourceDatatype== DataType.BYTE || sourceDatatype== DataType.UBYTE || sourceDatatype== DataType.WORD
            DataType.UWORD -> sourceDatatype== DataType.UBYTE || sourceDatatype== DataType.UWORD
            DataType.FLOAT -> sourceDatatype in NumericDatatypes
            DataType.STR -> sourceDatatype== DataType.STR
            else -> {
                errors.err("cannot assign new value to variable of type $targetDatatype", position)
                false
            }
        }

        if(result)
            return true

        if((sourceDatatype== DataType.UWORD || sourceDatatype== DataType.WORD) && (targetDatatype== DataType.UBYTE || targetDatatype== DataType.BYTE)) {
            errors.err("cannot assign word to byte, use msb() or lsb()?", position)
        }
        else if(sourceDatatype== DataType.FLOAT && targetDatatype in IntegerDatatypes)
            errors.err("cannot assign float to ${targetDatatype.name.lowercase()}; possible loss of precision. Suggestion: round the value or revert to integer arithmetic", position)
        else {
            if(targetDatatype!=DataType.UWORD && sourceDatatype !in PassByReferenceDatatypes)
                errors.err("type of value $sourceDatatype doesn't match target $targetDatatype", position)
        }

        return false
    }
}

internal fun checkUnusedReturnValues(call: FunctionCallStatement, target: Statement, program: Program, errors: IErrorReporter) {
    if (!call.void) {
        // check for unused return values
        if (target is Subroutine && target.returntypes.isNotEmpty()) {
            if (target.returntypes.size == 1)
                errors.warn("result value of subroutine call is discarded (use void?)", call.position)
            else
                errors.warn("result values of subroutine call are discarded (use void?)", call.position)
        } else if (target is BuiltinFunctionPlaceholder) {
            val rt = builtinFunctionReturnType(target.name, call.args, program)
            if (rt.isKnown)
                errors.warn("result value of a function call is discarded (use void?)", call.position)
        }
    }
}
