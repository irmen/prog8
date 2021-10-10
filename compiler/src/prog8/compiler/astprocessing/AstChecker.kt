package prog8.compiler.astprocessing

import prog8.ast.INameScope
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.compiler.CompilationOptions
import prog8.compiler.IErrorReporter
import prog8.compiler.ZeropageType
import prog8.compiler.functions.BuiltinFunctions
import prog8.compiler.functions.builtinFunctionReturnType
import prog8.compiler.target.ICompilationTarget
import java.io.CharConversionException
import java.io.File
import java.util.*
import kotlin.io.path.*

internal class AstChecker(private val program: Program,
                          private val compilerOptions: CompilationOptions,
                          private val errors: IErrorReporter,
                          private val compTarget: ICompilationTarget
) : IAstVisitor {

    override fun visit(program: Program) {
        assert(program === this.program)
        // there must be a single 'main' block with a 'start' subroutine for the program entry point.
        val mainBlocks = program.modules.flatMap { it.statements }.filter { b -> b is Block && b.name=="main" }.map { it as Block }
        if(mainBlocks.size>1)
            errors.err("more than one 'main' block", mainBlocks[0].position)
        if(mainBlocks.isEmpty())
            errors.err("there is no 'main' block", program.modules.firstOrNull()?.position ?: program.position)

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
            if (compilerOptions.zeropage !in setOf(ZeropageType.FLOATSAFE, ZeropageType.BASICSAFE, ZeropageType.DONTUSE ))
                errors.err("when floats are enabled, zero page type should be 'floatsafe' or 'basicsafe' or 'dontuse'", program.mainModule.position)
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
                if (expectedReturnValues[0] != valueDt.typeOrElse(DataType.UNDEFINED))
                    errors.err("type $valueDt of return value doesn't match subroutine's return type ${expectedReturnValues[0]}", returnStmt.value!!.position)
            }
        }
        super.visit(returnStmt)
    }

    override fun visit(ifStatement: IfStatement) {
        if(!ifStatement.condition.inferType(program).isInteger)
            errors.err("condition value should be an integer type", ifStatement.condition.position)
        super.visit(ifStatement)
    }

    override fun visit(forLoop: ForLoop) {

        fun checkUnsignedLoopDownto0(range: RangeExpr?) {
            if(range==null)
                return
            val step = range.step.constValue(program)?.number?.toDouble() ?: 1.0
            if(step < -1.0) {
                val limit = range.to.constValue(program)?.number?.toDouble()
                if(limit==0.0 && range.from.constValue(program)==null)
                    errors.err("for unsigned loop variable it's not possible to count down with step != -1 from a non-const value to exactly zero due to value wrapping", forLoop.position)
            }
        }

        val iterableDt = forLoop.iterable.inferType(program).typeOrElse(DataType.BYTE)
        if(iterableDt !in IterableDatatypes && forLoop.iterable !is RangeExpr) {
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

                        checkUnsignedLoopDownto0(forLoop.iterable as? RangeExpr)
                    }
                    DataType.UWORD -> {
                        if(iterableDt!= DataType.UBYTE && iterableDt!= DataType.UWORD && iterableDt != DataType.STR &&
                                iterableDt != DataType.ARRAY_UB && iterableDt!= DataType.ARRAY_UW)
                            errors.err("uword loop variable can only loop over unsigned bytes, words or strings", forLoop.position)

                        checkUnsignedLoopDownto0(forLoop.iterable as? RangeExpr)
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
                        errors.err("for loop only supports integers", forLoop.position)
                    }
                    else -> errors.err("loop variable must be numeric type", forLoop.position)
                }
                if(errors.noErrors()) {
                    // check loop range values
                    val range = forLoop.iterable as? RangeExpr
                    if(range!=null) {
                        val from = range.from as? NumericLiteralValue
                        val to = range.to as? NumericLiteralValue
                        if(from != null)
                            checkValueTypeAndRange(loopvar.datatype, from)
                        else if(!range.from.inferType(program).istype(loopvar.datatype))
                            errors.err("range start value is incompatible with loop variable type", range.position)
                        if(to != null)
                            checkValueTypeAndRange(loopvar.datatype, to)
                        else if(!range.to.inferType(program).istype(loopvar.datatype))
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
                if(targetStatement is BuiltinFunctionStatementPlaceholder)
                    errors.err("can't jump to a builtin function", jump.position)
            }
        }

        val addr = jump.address
        if(addr!=null && (addr < 0 || addr > 65535))
            errors.err("jump address must be valid integer 0..\$ffff", jump.position)
        super.visit(jump)
    }

    override fun visit(block: Block) {
        val addr = block.address
        if(addr!=null && (addr<0 || addr>65535)) {
            errors.err("block memory address must be valid integer 0..\$ffff", block.position)
        }

        for (statement in block.statements) {
            val ok = when (statement) {
                is Block,
                is Directive,
                is Label,
                is VarDecl,
                is InlineAssembly,
                is INameScope,
                is NopStatement -> true
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

    private fun hasReturnOrJump(scope: INameScope): Boolean {
        class Searcher: IAstVisitor
        {
            var count=0

            override fun visit(returnStmt: Return) {
                count++
            }
            override fun visit(jump: Jump) {
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

        if(subroutine.parameters.size>16)
            err("subroutines are limited to 16 parameters")

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
        if(!hasReturnOrJump(subroutine)) {
            if (subroutine.amountOfRtsInAsm() == 0) {
                if (subroutine.returntypes.isNotEmpty()) {
                    // for asm subroutines with an address, no statement check is possible.
                    if (subroutine.asmAddress == null && !subroutine.inline)
                        err("non-inline subroutine has result value(s) and thus must have at least one 'return' or 'goto' in it (or rts/jmp/bra in case of %asm)")
                }
            }
        }

        if(subroutine.inline && !subroutine.isAsmSubroutine)
            err("subroutine inlining is currently only supported on asmsub routines")

        if(subroutine.parent !is Block && subroutine.parent !is Subroutine)
            err("subroutines can only be defined in the scope of a block or within another subroutine")

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
                            && param.first.type != DataType.STR && param.first.type !in ArrayDatatypes && param.first.type != DataType.FLOAT)
                        err("parameter '${param.first.name}' should be (u)word/address")
                }
                else if(param.second.statusflag!=null) {
                    if (param.first.type != DataType.UBYTE)
                        err("parameter '${param.first.name}' should be ubyte")
                }
            }
            subroutine.returntypes.zip(subroutine.asmReturnvaluesRegisters).forEachIndexed { index, pair ->
                if(pair.second.registerOrPair in setOf(RegisterOrPair.A, RegisterOrPair.X, RegisterOrPair.Y)) {
                    if (pair.first != DataType.UBYTE && pair.first != DataType.BYTE)
                        err("return value #${index + 1} should be (u)byte")
                }
                else if(pair.second.registerOrPair in setOf(RegisterOrPair.AX, RegisterOrPair.AY, RegisterOrPair.XY)) {
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

        } else {
            // Pass-by-reference datatypes can not occur as parameters to a subroutine directly
            // Instead, their reference (address) should be passed (as an UWORD).
            if(subroutine.parameters.any{it.type in PassByReferenceDatatypes }) {
                err("Pass-by-reference types (str, array) cannot occur as a parameter type directly. Instead, use an uword to receive their address, or access the variable from the outer scope directly.")
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
        if(assignment.value is FunctionCall) {
            val stmt = (assignment.value as FunctionCall).target.targetStatement(program)
            if (stmt is Subroutine) {
                val idt = assignment.target.inferType(program)
                if(!idt.isKnown) {
                     errors.err("return type mismatch", assignment.value.position)
                }
                if(stmt.returntypes.isEmpty() || (stmt.returntypes.size == 1 && stmt.returntypes.single() isNotAssignableTo idt.typeOrElse(DataType.BYTE))) {
                    errors.err("return type mismatch", assignment.value.position)
                }
            }
        }

        val targetDt = assignment.target.inferType(program)
        val valueDt = assignment.value.inferType(program)
        if(valueDt.isKnown && !(valueDt isAssignableTo targetDt)) {
            if(targetDt.isIterable)
                errors.err("cannot assign value to string or array", assignment.value.position)
            else if(!(valueDt.istype(DataType.STR) && targetDt.istype(DataType.UWORD)))
                errors.err("type of value doesn't match target", assignment.value.position)
        }

        if(assignment.value is TypecastExpression) {
            if(assignment.isAugmentable && targetDt.istype(DataType.FLOAT))
                errors.err("typecasting a float value in-place makes no sense", assignment.value.position)
        }

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
            when (val targetSymbol = program.namespace.lookup(targetName, assignment)) {
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

// target type check is already done at the Assignment:
//        val targetDt = assignTarget.inferType(program, assignment).typeOrElse(DataType.STR)
//        if(targetDt in IterableDatatypes)
//            errors.err("cannot assign to a string or array type", assignTarget.position)

        if (assignment is Assignment) {

            val targetDatatype = assignTarget.inferType(program)
            if (targetDatatype.isKnown) {
                val constVal = assignment.value.constValue(program)
                if (constVal != null) {
                    checkValueTypeAndRange(targetDatatype.typeOrElse(DataType.BYTE), constVal)
                } else {
                    val sourceDatatype = assignment.value.inferType(program)
                    if (sourceDatatype.isUnknown) {
                        if (assignment.value !is FunctionCall)
                            errors.err("assignment value is invalid or has no proper datatype", assignment.value.position)
                    } else {
                        checkAssignmentCompatible(targetDatatype.typeOrElse(DataType.BYTE), assignTarget,
                                sourceDatatype.typeOrElse(DataType.BYTE), assignment.value, assignment.position)
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
        fun err(msg: String, position: Position?=null) = errors.err(msg, position ?: decl.position)

        // the initializer value can't refer to the variable itself (recursive definition)
        if(decl.value?.referencesIdentifier(decl.name) == true || decl.arraysize?.indexExpr?.referencesIdentifier(decl.name) == true)
            err("recursive var declaration")

        // CONST can only occur on simple types (byte, word, float)
        if(decl.type== VarDeclType.CONST) {
            if (decl.datatype !in NumericDatatypes)
                err("const modifier can only be used on numeric types (byte, word, float)")
        }

        // FLOATS enabled?
        if(!compilerOptions.floats && decl.datatype in setOf(DataType.FLOAT, DataType.ARRAY_F) && decl.type!= VarDeclType.MEMORY)
            err("floating point used, but that is not enabled via options")

        if(decl.datatype == DataType.FLOAT && (decl.zeropage==ZeropageWish.REQUIRE_ZEROPAGE || decl.zeropage==ZeropageWish.PREFER_ZEROPAGE))
            errors.warn("floating point values won't be placed in Zeropage due to size constraints", decl.position)

        // ARRAY without size specifier MUST have an iterable initializer value
        if(decl.isArray && decl.arraysize==null) {
            if(decl.type== VarDeclType.MEMORY)
                err("memory mapped array must have a size specification")
            if(decl.value==null) {
                err("array variable is missing a size specification or an initialization value")
                return
            }
            if(decl.value is NumericLiteralValue) {
                err("unsized array declaration cannot use a single literal initialization value")
                return
            }
            if(decl.value is RangeExpr)
                throw FatalAstException("range expressions in vardecls should have been converted into array values during constFolding  $decl")
        }

        when(decl.type) {
            VarDeclType.VAR, VarDeclType.CONST -> {
                when(decl.value) {
                    null -> {
                        // a vardecl without an initial value, don't bother with it
                    }
                    is RangeExpr -> throw FatalAstException("range expression should have been converted to a true array value")
                    is StringLiteralValue -> {
                        checkValueTypeAndRangeString(decl.datatype, decl.value as StringLiteralValue)
                    }
                    is ArrayLiteralValue -> {
                        val arraySpec = decl.arraysize ?: ArrayIndex.forArray(decl.value as ArrayLiteralValue)
                        checkValueTypeAndRangeArray(decl.datatype, arraySpec, decl.value as ArrayLiteralValue)
                    }
                    is NumericLiteralValue -> {
                        checkValueTypeAndRange(decl.datatype, decl.value as NumericLiteralValue)
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
                val numvalue = decl.value as? NumericLiteralValue
                if(numvalue!=null) {
                    if (numvalue.type !in IntegerDatatypes || numvalue.number.toInt() < 0 || numvalue.number.toInt() > 65535) {
                        err("memory address must be valid integer 0..\$ffff", decl.value?.position)
                    }
                } else {
                    err("value of memory mapped variable can only be a fixed number, perhaps you meant to use an address pointer type instead?", decl.value?.position)
                }
            }
        }

        val declValue = decl.value
        if(declValue!=null && decl.type==VarDeclType.VAR) {
            if (!declValue.inferType(program).istype(decl.datatype)) {
                err("initialisation value has incompatible type (${declValue.inferType(program)}) for the variable (${decl.datatype})", declValue.position)
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

        // string assignment is not supported in a vard
        if(decl.datatype==DataType.STR) {
            if(decl.value==null)
                err("string var must be initialized with a string literal")
            else if (decl.type==VarDeclType.VAR && decl.value !is StringLiteralValue)
                err("string var can only be initialized with a string literal")
        }

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
                if(directive.args.size!=1 || directive.args[0].name != "raw" && directive.args[0].name != "prg")
                    err("invalid output directive type, expected raw or prg")
            }
            "%launcher" -> {
                if(directive.parent !is Module)
                    err("this directive may only occur at module level")
                if(directive.args.size!=1 || directive.args[0].name != "basic" && directive.args[0].name != "none")
                    err("invalid launcher directive type, expected basic or none")
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
                else if(directive.args.isNotEmpty() && directive.args[0].str==null) err(errormsg)
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
                else if(directive.args.map{it.name in setOf("enable_floats", "force_output", "no_sysinit", "align_word", "align_page")}.any { !it })
                    err("invalid option directive argument(s)")
            }
            else -> throw SyntaxError("invalid directive ${directive.directive}", directive.position)
        }
        super.visit(directive)
    }

    private fun checkFileExists(directive: Directive, filename: String) {
        if (File(filename).isFile)
            return

        var definingModule = directive.parent   // TODO: why not just use directive.definingModule() here?
        while (definingModule !is Module)
            definingModule = definingModule.parent
        if (definingModule.isLibrary)
            return

        val s = definingModule.source?.pathString()
        if (s != null) {
            val sourceFileCandidate = Path(s).resolveSibling(filename).toFile()
            if (sourceFileCandidate.isFile)
                return
        }

        errors.err("included file not found: $filename", directive.position)
    }

    override fun visit(array: ArrayLiteralValue) {
        if(array.type.isKnown) {
            if (!compilerOptions.floats && array.type.typeOrElse(DataType.UNDEFINED) in setOf(DataType.FLOAT, DataType.ARRAY_F)) {
                errors.err("floating point used, but that is not enabled via options", array.position)
            }
            val arrayspec = ArrayIndex.forArray(array)
            checkValueTypeAndRangeArray(array.type.typeOrElse(DataType.UNDEFINED), arrayspec, array)
        }

        fun isPassByReferenceElement(e: Expression): Boolean {
            if(e is IdentifierReference) {
                val decl = e.targetVarDecl(program)!!
                return decl.datatype in PassByReferenceDatatypes
            }
            return e is StringLiteralValue
        }

        if(array.parent is VarDecl) {
            if (!array.value.all { it is NumericLiteralValue || it is AddressOf || isPassByReferenceElement(it) })
                errors.err("array literal for variable initialization contains non-constant elements", array.position)
        } else if(array.parent is ForLoop) {
            if (!array.value.all { it.constValue(program) != null })
                errors.err("array literal for iteration must contain constants. Try using a separate array variable instead?", array.position)
        }

        super.visit(array)
    }

    override fun visit(char: CharLiteral) {
        try {  // just *try* if it can be encoded, don't actually do it
            compTarget.encodeString(char.value.toString(), char.altEncoding)
        } catch (cx: CharConversionException) {
            errors.err(cx.message ?: "can't encode character", char.position)
        }

        super.visit(char)
    }

    override fun visit(string: StringLiteralValue) {
        checkValueTypeAndRangeString(DataType.STR, string)

        try {  // just *try* if it can be encoded, don't actually do it
            compTarget.encodeString(string.value, string.altEncoding)
        } catch (cx: CharConversionException) {
            errors.err(cx.message ?: "can't encode string", string.position)
        }

        super.visit(string)
    }

    override fun visit(expr: PrefixExpression) {
        val idt = expr.inferType(program)
        if(!idt.isKnown)
            return  // any error should be reported elsewhere

        val dt = idt.typeOrElse(DataType.UNDEFINED)
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

        val leftDt = leftIDt.typeOrElse(DataType.UNDEFINED)
        val rightDt = rightIDt.typeOrElse(DataType.UNDEFINED)

        when(expr.operator){
            "/", "%" -> {
                val constvalRight = expr.right.constValue(program)
                val divisor = constvalRight?.number?.toDouble()
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
        }

        if(leftDt !in NumericDatatypes && leftDt != DataType.STR)
            errors.err("left operand is not numeric or str", expr.left.position)
        if(rightDt!in NumericDatatypes && rightDt != DataType.STR)
            errors.err("right operand is not numeric or str", expr.right.position)
        if(leftDt!=rightDt)
            errors.err("left and right operands aren't the same type", expr.left.position)
    }

    override fun visit(typecast: TypecastExpression) {
        if(typecast.type in IterableDatatypes)
            errors.err("cannot type cast to string or array type", typecast.position)

        if(!typecast.expression.inferType(program).isKnown)
            errors.err("this expression doesn't return a value", typecast.expression.position)

        super.visit(typecast)
    }

    override fun visit(range: RangeExpr) {
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

    override fun visit(functionCall: FunctionCall) {
        // this function call is (part of) an expression, which should be in a statement somewhere.
        val stmtOfExpression = findParentNode<Statement>(functionCall)
                ?: throw FatalAstException("cannot determine statement scope of function call expression at ${functionCall.position}")

        val targetStatement = checkFunctionOrLabelExists(functionCall.target, stmtOfExpression)
        if(targetStatement!=null)
            checkFunctionCall(targetStatement, functionCall.args, functionCall.position)

        // warn about sgn(unsigned) this is likely a mistake
        if(functionCall.target.nameInSource.last()=="sgn") {
            val sgnArgType = functionCall.args.first().inferType(program)
            if(sgnArgType.istype(DataType.UBYTE) || sgnArgType.istype(DataType.UWORD))
                errors.warn("sgn() of unsigned type is always 0 or 1, this is perhaps not what was intended", functionCall.args.first().position)
        }

        val error = VerifyFunctionArgTypes.checkTypes(functionCall, program)
        if(error!=null)
            errors.err(error, functionCall.position)

        // check the functions that return multiple returnvalues.
        val stmt = functionCall.target.targetStatement(program)
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
                val (returnRegisters, returnStatusflags) = stmt.asmReturnvaluesRegisters.partition { rr -> rr.registerOrPair != null }
                if (returnRegisters.isEmpty() || returnRegisters.size == 1) {
                    if (returnStatusflags.any())
                        errors.warn("this asmsub also has one or more return 'values' in one of the status flags", functionCall.position)
                } else {
                    errors.err("It's not possible to store the multiple result values of this asmsub call; you should use a small block of custom inline assembly for this.", functionCall.position)
                }
            }
        }

        // functions that don't return a value, can't be used in an expression or assignment
        if(targetStatement is Subroutine) {
            if(targetStatement.returntypes.isEmpty()) {
                if(functionCall.parent is Expression || functionCall.parent is Assignment)
                    errors.err("subroutine doesn't return a value", functionCall.position)
            }
        }
        else if(targetStatement is BuiltinFunctionStatementPlaceholder) {
            if(builtinFunctionReturnType(targetStatement.name, functionCall.args, program).isUnknown) {
                if(functionCall.parent is Expression || functionCall.parent is Assignment)
                    errors.err("function doesn't return a value", functionCall.position)
            }
        }

        super.visit(functionCall)
    }

    override fun visit(functionCallStatement: FunctionCallStatement) {
        val targetStatement = checkFunctionOrLabelExists(functionCallStatement.target, functionCallStatement)
        if(targetStatement!=null)
            checkFunctionCall(targetStatement, functionCallStatement.args, functionCallStatement.position)
        if (!functionCallStatement.void) {
            // check for unused return values
            if (targetStatement is Subroutine && targetStatement.returntypes.isNotEmpty()) {
                if(targetStatement.returntypes.size==1)
                    errors.warn("result value of subroutine call is discarded (use void?)", functionCallStatement.position)
                else
                    errors.warn("result values of subroutine call are discarded (use void?)", functionCallStatement.position)
            }
            else if(targetStatement is BuiltinFunctionStatementPlaceholder) {
                val rt = builtinFunctionReturnType(targetStatement.name, functionCallStatement.args, program)
                if(rt.isKnown)
                    errors.warn("result value of a function call is discarded (use void?)", functionCallStatement.position)
            }
        }

        if(functionCallStatement.target.nameInSource.last() == "sort") {
            // sort is not supported on float arrays
            val idref = functionCallStatement.args.singleOrNull() as? IdentifierReference
            if(idref!=null && idref.inferType(program).istype(DataType.ARRAY_F)) {
                errors.err("sorting a floating point array is not supported", functionCallStatement.args.first().position)
            }
        }

        if(functionCallStatement.target.nameInSource.last() in setOf("rol", "ror", "rol2", "ror2", "swap", "sort", "reverse")) {
            // in-place modification, can't be done on literals
            if(functionCallStatement.args.any { it !is IdentifierReference && it !is ArrayIndexedExpression && it !is DirectMemoryRead }) {
                errors.err("invalid argument to a in-place modifying function", functionCallStatement.args.first().position)
            }
        }

        val error =
            VerifyFunctionArgTypes.checkTypes(functionCallStatement, program)
        if(error!=null) {
            errors.err(error, functionCallStatement.args.firstOrNull()?.position ?: functionCallStatement.position)
        }

        super.visit(functionCallStatement)
    }

    private fun checkFunctionCall(target: Statement, args: List<Expression>, position: Position) {
        if(target is Label && args.isNotEmpty())
            errors.err("cannot use arguments when calling a label", position)

        if(target is BuiltinFunctionStatementPlaceholder) {
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
                if(args[0].inferType(program).typeOrElse(DataType.STR) == DataType.STR) {
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
                    else if(arg.value is FunctionCall) {
                        val fcall = arg.value as FunctionCall
                        if(fcall.target.nameInSource == listOf("lsb") || fcall.target.nameInSource == listOf("msb"))
                            ident = fcall.args[0] as? IdentifierReference
                    }
                    if(ident!=null && ident.nameInSource[0] == "cx16" && ident.nameInSource[1].startsWith("r")) {
                        val reg = RegisterOrPair.valueOf(ident.nameInSource[1].uppercase())
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

    override fun visit(postIncrDecr: PostIncrDecr) {
        if(postIncrDecr.target.identifier != null) {
            val targetName = postIncrDecr.target.identifier!!.nameInSource
            val target = program.namespace.lookup(targetName, postIncrDecr)
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
                if(target.value is StringLiteralValue) {
                    // check string lengths for non-memory mapped strings
                    val stringLen = (target.value as StringLiteralValue).value.length
                    val index = arrayIndexedExpression.indexer.constIndex()
                    if (index != null && (index < 0 || index >= stringLen))
                        errors.err("index out of bounds", arrayIndexedExpression.indexer.position)
                }
            }
        } else
            errors.err("indexing requires a variable to act upon", arrayIndexedExpression.position)

        // check index value 0..255
        val dtxNum = arrayIndexedExpression.indexer.indexExpr.inferType(program)
        if(!dtxNum.istype(DataType.UBYTE) && !dtxNum.istype(DataType.BYTE))
            errors.err("array indexing is limited to byte size 0..255", arrayIndexedExpression.position)

        super.visit(arrayIndexedExpression)
    }

    override fun visit(whenStatement: WhenStatement) {
        if(!whenStatement.condition.inferType(program).isInteger)
            errors.err("when condition must be an integer value", whenStatement.position)
        val tally = mutableSetOf<Int>()
        for((choices, choiceNode) in whenStatement.choiceValues(program)) {
            if(choices!=null) {
                for (c in choices) {
                    if(c in tally)
                        errors.err("choice value already occurs earlier", choiceNode.position)
                    else
                        tally.add(c)
                }
            }
        }

        if(whenStatement.choices.isEmpty())
            errors.err("empty when statement", whenStatement.position)

        super.visit(whenStatement)
    }

    override fun visit(whenChoice: WhenChoice) {
        val whenStmt = whenChoice.parent as WhenStatement
        if(whenChoice.values!=null) {
            val conditionType = whenStmt.condition.inferType(program)
            if(!conditionType.isKnown)
                throw FatalAstException("can't determine when choice datatype $whenChoice")
            val constvalues = whenChoice.values!!.map { it.constValue(program) }
            for(constvalue in constvalues) {
                when {
                    constvalue == null -> errors.err("choice value must be a constant", whenChoice.position)
                    constvalue.type !in IntegerDatatypes -> errors.err("choice value must be a byte or word", whenChoice.position)
                    constvalue.type != conditionType.typeOrElse(DataType.UNDEFINED) -> errors.err("choice value datatype differs from condition value", whenChoice.position)
                }
            }
        } else {
            if(whenChoice !== whenStmt.choices.last())
                errors.err("else choice must be the last one", whenChoice.position)
        }
        super.visit(whenChoice)
    }

    private fun checkFunctionOrLabelExists(target: IdentifierReference, statement: Statement): Statement? {
        when (val targetStatement = target.targetStatement(program)) {
            is Label, is Subroutine, is BuiltinFunctionStatementPlaceholder -> return targetStatement
            null -> errors.err("undefined function or subroutine: ${target.nameInSource.joinToString(".")}", statement.position)
            else -> errors.err("cannot call that: ${target.nameInSource.joinToString(".")}", statement.position)
        }
        return null
    }

    private fun checkValueTypeAndRangeString(targetDt: DataType, value: StringLiteralValue) : Boolean {
        return if (targetDt == DataType.STR) {
            if (value.value.length > 255) {
                errors.err("string length must be 0-255", value.position)
                false
            }
            else
                true
        }
        else false
    }

    private fun checkValueTypeAndRangeArray(targetDt: DataType, arrayspec: ArrayIndex, value: ArrayLiteralValue) : Boolean {
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
                if(value.type.istype(targetDt)) {
                    if(!checkArrayValues(value, targetDt))
                        return false
                    val arraySpecSize = arrayspec.constIndex()
                    val arraySize = value.value.size
                    if(arraySpecSize!=null && arraySpecSize>0) {
                        if(arraySpecSize<1 || arraySpecSize>256)
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
                if(value.type.istype(targetDt)) {
                    if(!checkArrayValues(value, targetDt))
                        return false
                    val arraySpecSize = arrayspec.constIndex()
                    val arraySize = value.value.size
                    if(arraySpecSize!=null && arraySpecSize>0) {
                        if(arraySpecSize<1 || arraySpecSize>128)
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
                if(value.type.istype(targetDt)) {
                    if(!checkArrayValues(value, targetDt))
                        return false
                    val arraySize = value.value.size
                    val arraySpecSize = arrayspec.constIndex()
                    if(arraySpecSize!=null && arraySpecSize>0) {
                        if(arraySpecSize < 1 || arraySpecSize>51)
                            return err("float array length must be 1-51")
                        val expectedSize = arrayspec.constIndex() ?: return err("array size specifier must be constant integer value")
                        if (arraySize != expectedSize)
                            return err("initializer array size mismatch (expecting $expectedSize, got $arraySize)")
                    } else
                        return err("invalid float array size, must be 1-51")

                    // check if the floating point values are all within range
                    val doubles = value.value.map {it.constValue(program)?.number!!.toDouble()}.toDoubleArray()
                    if(doubles.any { it < compTarget.machine.FLOAT_MAX_NEGATIVE || it > compTarget.machine.FLOAT_MAX_POSITIVE })
                        return err("floating point value overflow")
                    return true
                }
                return err("invalid float array initialization value ${value.type}, expected $targetDt")
            }
            else -> return false
        }
    }

    private fun checkValueTypeAndRange(targetDt: DataType, value: NumericLiteralValue) : Boolean {
        fun err(msg: String) : Boolean {
            errors.err(msg, value.position)
            return false
        }
        when (targetDt) {
            DataType.FLOAT -> {
                val number=value.number.toDouble()
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

    private fun checkArrayValues(value: ArrayLiteralValue, type: DataType): Boolean {
        val array = value.value.map {
            when (it) {
                is NumericLiteralValue -> it.number.toInt()
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
                                          target: AssignTarget,
                                          sourceDatatype: DataType,
                                          sourceValue: Expression,
                                          position: Position) : Boolean {

        if(sourceValue is RangeExpr)
            errors.err("can't assign a range value to something else", position)

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
                errors.err(
                    "cannot assign ${sourceDatatype.name.lowercase()} to ${
                        targetDatatype.name.lowercase(
                            Locale.getDefault()
                        )
                    }", position)
        }


        return false
    }
}
