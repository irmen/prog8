package prog8.vm.astvm

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.base.initvarsSubName
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.LiteralValue
import prog8.ast.statements.*
import prog8.vm.RuntimeValue
import prog8.vm.RuntimeValueRange
import prog8.compiler.target.c64.Petscii
import java.awt.EventQueue
import kotlin.NoSuchElementException
import kotlin.concurrent.fixedRateTimer
import kotlin.math.min


class VmExecutionException(msg: String?) : Exception(msg)

class VmTerminationException(msg: String?) : Exception(msg)

class VmBreakpointException : Exception("breakpoint")


class StatusFlags {
    var carry: Boolean = false
    var zero: Boolean = true
    var negative: Boolean = false
    var irqd: Boolean = false

    private fun setFlags(value: LiteralValue?) {
        if (value != null) {
            when (value.type) {
                DataType.UBYTE -> {
                    val v = value.bytevalue!!.toInt()
                    negative = v > 127
                    zero = v == 0
                }
                DataType.BYTE -> {
                    val v = value.bytevalue!!.toInt()
                    negative = v < 0
                    zero = v == 0
                }
                DataType.UWORD -> {
                    val v = value.wordvalue!!
                    negative = v > 32767
                    zero = v == 0
                }
                DataType.WORD -> {
                    val v = value.wordvalue!!
                    negative = v < 0
                    zero = v == 0
                }
                DataType.FLOAT -> {
                    val flt = value.floatvalue!!
                    negative = flt < 0.0
                    zero = flt == 0.0
                }
                else -> {
                    // no flags for non-numeric type
                }
            }
        }
    }
}


class RuntimeVariables {
    fun define(scope: INameScope, name: String, initialValue: RuntimeValue) {
        val where = vars.getValue(scope)
        where[name] = initialValue
        vars[scope] = where
    }

    fun defineMemory(scope: INameScope, name: String, address: Int) {
        val where = memvars.getValue(scope)
        where[name] = address
        memvars[scope] = where
    }

    fun set(scope: INameScope, name: String, value: RuntimeValue) {
        val where = vars.getValue(scope)
        val existing = where[name]
        if(existing==null) {
            if(memvars.getValue(scope)[name]!=null)
                throw NoSuchElementException("this is a memory mapped var, not a normal var: ${scope.name}.$name")
            throw NoSuchElementException("no such runtime variable: ${scope.name}.$name")
        }
        if(existing.type!=value.type)
            throw VmExecutionException("new value is of different datatype ${value.type} expected ${existing.type} for $name")
        where[name] = value
        vars[scope] = where
    }

    fun get(scope: INameScope, name: String): RuntimeValue {
        val where = vars.getValue(scope)
        val value = where[name] ?: throw NoSuchElementException("no such runtime variable: ${scope.name}.$name")
        return value
    }

    fun getMemoryAddress(scope: INameScope, name: String): Int {
        val where = memvars.getValue(scope)
        val address = where[name] ?: throw NoSuchElementException("no such runtime memory-variable: ${scope.name}.$name")
        return address
    }

    fun swap(a1: VarDecl, a2: VarDecl) = swap(a1.definingScope(), a1.name, a2.definingScope(), a2.name)

    fun swap(scope1: INameScope, name1: String, scope2: INameScope, name2: String) {
        val v1 = get(scope1, name1)
        val v2 = get(scope2, name2)
        set(scope1, name1, v2)
        set(scope2, name2, v1)
    }

    private val vars = mutableMapOf<INameScope, MutableMap<String, RuntimeValue>>().withDefault { mutableMapOf() }
    private val memvars = mutableMapOf<INameScope, MutableMap<String, Int>>().withDefault { mutableMapOf() }
}


class AstVm(val program: Program) {

    val mem = Memory(::memread, ::memwrite)
    val statusflags = StatusFlags()

    private var dialog = ScreenDialog("AstVM")
    var instructionCounter = 0
    val bootTime = System.currentTimeMillis()
    var rtcOffset = bootTime

    init {
        // observe the jiffyclock
        mem.observe(0xa0, 0xa1, 0xa2)

        dialog.requestFocusInWindow()

        EventQueue.invokeLater {
            dialog.pack()
            dialog.isVisible = true
            dialog.start()
        }

        fixedRateTimer("60hz-irq", true, period=1000/60) {
            irq(this.scheduledExecutionTime())
        }
    }

    fun memread(address: Int, value: Short): Short {
        // println("MEM READ  $address  -> $value")
        return value
    }

    fun memwrite(address: Int, value: Short): Short {
        if(address==0xa0 || address==0xa1 || address==0xa2) {
            // a write to the jiffy clock, update the clock offset for the irq
            val time_hi = if(address==0xa0) value else mem.getUByte_DMA(0xa0)
            val time_mid = if(address==0xa1) value else mem.getUByte_DMA(0xa1)
            val time_lo = if(address==0xa2) value else mem.getUByte_DMA(0xa2)
            val jiffies = (time_hi.toInt() shl 16) + (time_mid.toInt() shl 8) + time_lo
            rtcOffset = bootTime - (jiffies*1000/60)
        }
        return value
    }

    fun run() {
        try {
            val init = VariablesCreator(runtimeVariables, program.heap)
            init.visit(program)

            // initialize all global variables
            for (m in program.modules) {
                for (b in m.statements.filterIsInstance<Block>()) {
                    for (s in b.statements.filterIsInstance<Subroutine>()) {
                        if (s.name == initvarsSubName) {
                            try {
                                executeSubroutine(s, emptyList(), null)
                            } catch (x: LoopControlReturn) {
                                // regular return
                            }
                        }
                    }
                }
            }

            var entrypoint: Subroutine? = program.entrypoint() ?: throw VmTerminationException("no valid entrypoint found")
            var startlabel: Label? = null

            while(entrypoint!=null) {
                try {
                    executeSubroutine(entrypoint, emptyList(), startlabel)
                    entrypoint = null
                } catch (rx: LoopControlReturn) {
                    // regular return
                } catch (jx: LoopControlJump) {
                    if (jx.address != null)
                        throw VmTerminationException("doesn't support jumping to machine address ${jx.address}")
                    when {
                        jx.generatedLabel != null -> {
                            val label = entrypoint.getLabelOrVariable(jx.generatedLabel) as Label
                            TODO("generatedlabel $label")
                        }
                        jx.identifier != null -> {
                            when (val jumptarget = entrypoint.lookup(jx.identifier.nameInSource, jx.identifier.parent)) {
                                is Label -> {
                                    startlabel = jumptarget
                                    entrypoint = jumptarget.definingSubroutine()
                                }
                                is Subroutine -> entrypoint = jumptarget
                                else -> throw VmTerminationException("weird jump target $jumptarget")
                            }
                        }
                        else -> throw VmTerminationException("unspecified jump target")
                    }
                }
            }
            println("PROGRAM EXITED!")
            dialog.title = "PROGRAM EXITED"
        } catch (tx: VmTerminationException) {
            println("Execution halted: ${tx.message}")
        } catch (xx: VmExecutionException) {
            println("Execution error: ${xx.message}")
            throw xx
        }
    }

    private fun irq(timeStamp: Long) {
        // 60hz IRQ handling
        if(statusflags.irqd)
            return      // interrupt is disabled

        val jiffies = min((timeStamp-rtcOffset)*60/1000, 24*3600*60-1)
        // update the C-64 60hz jiffy clock in the ZP addresses:
        mem.setUByte_DMA(0x00a0, (jiffies ushr 16).toShort())
        mem.setUByte_DMA(0x00a1, (jiffies ushr 8 and 255).toShort())
        mem.setUByte_DMA(0x00a2, (jiffies and 255).toShort())
    }

    private val runtimeVariables = RuntimeVariables()
    private val functions = BuiltinFunctions()
    private val evalCtx = EvalContext(program, mem, statusflags, runtimeVariables, functions, ::executeSubroutine)

    class LoopControlBreak : Exception()
    class LoopControlContinue : Exception()
    class LoopControlReturn(val returnvalues: List<RuntimeValue>) : Exception()
    class LoopControlJump(val identifier: IdentifierReference?, val address: Int?, val generatedLabel: String?) : Exception()


    internal fun executeSubroutine(sub: Subroutine, arguments: List<RuntimeValue>, startlabel: Label?=null): List<RuntimeValue> {
        assert(!sub.isAsmSubroutine)
        if (sub.statements.isEmpty())
            throw VmTerminationException("scope contains no statements: $sub")
        if (arguments.size != sub.parameters.size)
            throw VmTerminationException("number of args doesn't match number of required parameters")

        for (arg in sub.parameters.zip(arguments)) {
            val idref = IdentifierReference(listOf(arg.first.name), sub.position)
            performAssignment(AssignTarget(null, idref, null, null, idref.position),
                    arg.second, sub.statements.first(), evalCtx)
        }

        val statements = sub.statements.iterator()
        if(startlabel!=null) {
            do {
                val stmt = statements.next()
            } while(stmt!==startlabel)
        }

        try {
            while(statements.hasNext()) {
                val s = statements.next()
                try {
                    executeStatement(sub, s)
                }
                catch (b: VmBreakpointException) {
                    print("BREAKPOINT HIT at ${s.position} - Press enter to continue:")
                    readLine()
                }
            }
        } catch (r: LoopControlReturn) {
            return r.returnvalues
        }
        throw VmTerminationException("instruction pointer overflow, is a return missing? $sub")
    }

    internal fun executeAnonymousScope(scope: INameScope) {
        for (s in scope.statements) {
            executeStatement(scope, s)
        }
    }


    private fun executeStatement(sub: INameScope, stmt: IStatement) {
        instructionCounter++
        if (instructionCounter % 200 == 0)
            Thread.sleep(1)
        when (stmt) {
            is NopStatement, is Label, is Subroutine -> {
                // do nothing, skip this instruction
            }
            is Directive -> {
                if (stmt.directive == "%breakpoint")
                    throw VmBreakpointException()
                else if (stmt.directive == "%asm")
                    throw VmExecutionException("can't execute assembly code")
            }
            is VarDecl -> {
                // should have been defined already when the program started
            }
            is FunctionCallStatement -> {
                val target = stmt.target.targetStatement(program.namespace)
                when (target) {
                    is Subroutine -> {
                        val args = evaluate(stmt.arglist)
                        if (target.isAsmSubroutine) {
                            performSyscall(target, args)
                        } else {
                            executeSubroutine(target, args, null)
                            // any return value(s) are discarded
                        }
                    }
                    is BuiltinFunctionStatementPlaceholder -> {
                        if(target.name=="swap") {
                            // swap cannot be implemented as a function, so inline it here
                            executeSwap(stmt)
                        } else {
                            val args = evaluate(stmt.arglist)
                            functions.performBuiltinFunction(target.name, args, statusflags)
                        }
                    }
                    else -> {
                        TODO("weird call $target")
                    }
                }
            }
            is Return -> throw LoopControlReturn(stmt.values.map { evaluate(it, evalCtx) })
            is Continue -> throw LoopControlContinue()
            is Break -> throw LoopControlBreak()
            is Assignment -> {
                if (stmt.aug_op != null)
                    throw VmExecutionException("augmented assignment should have been converted into regular one $stmt")
                val target = stmt.singleTarget
                if (target != null) {
                    val value = evaluate(stmt.value, evalCtx)
                    performAssignment(target, value, stmt, evalCtx)
                } else TODO("assign multitarget $stmt")
            }
            is PostIncrDecr -> {
                when {
                    stmt.target.identifier != null -> {
                        val ident = stmt.definingScope().lookup(stmt.target.identifier!!.nameInSource, stmt) as VarDecl
                        val identScope = ident.definingScope()
                        var value = runtimeVariables.get(identScope, ident.name)
                        value = when {
                            stmt.operator == "++" -> value.add(RuntimeValue(value.type, 1))
                            stmt.operator == "--" -> value.sub(RuntimeValue(value.type, 1))
                            else -> throw VmExecutionException("strange postincdec operator $stmt")
                        }
                        runtimeVariables.set(identScope, ident.name, value)
                    }
                    stmt.target.memoryAddress != null -> {
                        TODO("postincrdecr memory $stmt")
                    }
                    stmt.target.arrayindexed != null -> {
                        TODO("postincrdecr array $stmt")
                    }
                }
            }
            is Jump -> throw LoopControlJump(stmt.identifier, stmt.address, stmt.generatedLabel)
            is InlineAssembly -> {
                if (sub is Subroutine) {
                    val args = sub.parameters.map { runtimeVariables.get(sub, it.name) }
                    performSyscall(sub, args)
                    throw LoopControlReturn(emptyList())
                }
                throw VmExecutionException("can't execute inline assembly in $sub")
            }
            is AnonymousScope -> executeAnonymousScope(stmt)
            is IfStatement -> {
                val condition = evaluate(stmt.condition, evalCtx)
                if (condition.asBoolean)
                    executeAnonymousScope(stmt.truepart)
                else
                    executeAnonymousScope(stmt.elsepart)
            }
            is BranchStatement -> {
                when(stmt.condition) {
                    BranchCondition.CS -> if(statusflags.carry) executeAnonymousScope(stmt.truepart) else executeAnonymousScope(stmt.elsepart)
                    BranchCondition.CC -> if(!statusflags.carry) executeAnonymousScope(stmt.truepart) else executeAnonymousScope(stmt.elsepart)
                    BranchCondition.EQ, BranchCondition.Z -> if(statusflags.zero) executeAnonymousScope(stmt.truepart) else executeAnonymousScope(stmt.elsepart)
                    BranchCondition.NE, BranchCondition.NZ -> if(statusflags.zero) executeAnonymousScope(stmt.truepart) else executeAnonymousScope(stmt.elsepart)
                    BranchCondition.MI, BranchCondition.NEG -> if(statusflags.negative) executeAnonymousScope(stmt.truepart) else executeAnonymousScope(stmt.elsepart)
                    BranchCondition.PL, BranchCondition.POS -> if(statusflags.negative) executeAnonymousScope(stmt.truepart) else executeAnonymousScope(stmt.elsepart)
                    BranchCondition.VS, BranchCondition.VC -> TODO("overflow status")
                }
            }
            is ForLoop -> {
                val iterable = evaluate(stmt.iterable, evalCtx)
                if (iterable.type !in IterableDatatypes && iterable !is RuntimeValueRange)
                    throw VmExecutionException("can only iterate over an iterable value:  $stmt")
                val loopvarDt: DataType
                val loopvar: IdentifierReference
                if (stmt.loopRegister != null) {
                    loopvarDt = DataType.UBYTE
                    loopvar = IdentifierReference(listOf(stmt.loopRegister.name), stmt.position)
                } else {
                    loopvarDt = stmt.loopVar!!.inferType(program)!!
                    loopvar = stmt.loopVar
                }
                val iterator = iterable.iterator()
                for (loopvalue in iterator) {
                    try {
                        oneForCycle(stmt, loopvarDt, loopvalue, loopvar)
                    } catch (b: LoopControlBreak) {
                        break
                    } catch (c: LoopControlContinue) {
                        continue
                    }
                }
            }
            is WhileLoop -> {
                var condition = evaluate(stmt.condition, evalCtx)
                while (condition.asBoolean) {
                    try {
                        executeAnonymousScope(stmt.body)
                        condition = evaluate(stmt.condition, evalCtx)
                    } catch (b: LoopControlBreak) {
                        break
                    } catch (c: LoopControlContinue) {
                        continue
                    }
                }
            }
            is RepeatLoop -> {
                do {
                    val condition = evaluate(stmt.untilCondition, evalCtx)
                    try {
                        executeAnonymousScope(stmt.body)
                    } catch (b: LoopControlBreak) {
                        break
                    } catch (c: LoopControlContinue) {
                        continue
                    }
                } while (!condition.asBoolean)
            }
            is WhenStatement -> {
                val condition=evaluate(stmt.condition, evalCtx)
                for(choice in stmt.choices) {
                    if(choice.values==null) {
                        // the 'else' choice
                        executeAnonymousScope(choice.statements)
                        break
                    } else {
                        val value = choice.values.single().constValue(evalCtx.program) ?: throw VmExecutionException("can only use const values in when choices ${choice.position}")
                        val rtval = RuntimeValue.from(value, evalCtx.program.heap)
                        if(condition==rtval) {
                            executeAnonymousScope(choice.statements)
                            break
                        }
                    }
                }
            }
            else -> {
                TODO("implement $stmt")
            }
        }
    }

    private fun executeSwap(swap: FunctionCallStatement) {
        val v1 = swap.arglist[0]
        val v2 = swap.arglist[1]
        val value1 = evaluate(v1, evalCtx)
        val value2 = evaluate(v2, evalCtx)
        val target1 = AssignTarget.fromExpr(v1)
        val target2 = AssignTarget.fromExpr(v2)
        performAssignment(target1, value2, swap, evalCtx)
        performAssignment(target2, value1, swap, evalCtx)
    }

    fun performAssignment(target: AssignTarget, value: RuntimeValue, contextStmt: IStatement, evalCtx: EvalContext) {
        when {
            target.identifier != null -> {
                val decl = contextStmt.definingScope().lookup(target.identifier.nameInSource, contextStmt) as? VarDecl
                        ?: throw VmExecutionException("can't find assignment target ${target.identifier}")
                if (decl.type == VarDeclType.MEMORY) {
                    val address = runtimeVariables.getMemoryAddress(decl.definingScope(), decl.name)
                    when (decl.datatype) {
                        DataType.UBYTE -> mem.setUByte(address, value.byteval!!)
                        DataType.BYTE -> mem.setSByte(address, value.byteval!!)
                        DataType.UWORD -> mem.setUWord(address, value.wordval!!)
                        DataType.WORD -> mem.setSWord(address, value.wordval!!)
                        DataType.FLOAT -> mem.setFloat(address, value.floatval!!)
                        DataType.STR -> mem.setString(address, value.str!!)
                        DataType.STR_S -> mem.setScreencodeString(address, value.str!!)
                        else -> TODO("set memvar $decl")
                    }
                } else
                    runtimeVariables.set(decl.definingScope(), decl.name, value)
            }
            target.memoryAddress != null -> {
                val address = evaluate(target.memoryAddress!!.addressExpression, evalCtx).wordval!!
                evalCtx.mem.setUByte(address, value.byteval!!)
            }
            target.arrayindexed != null -> {
                val array = evaluate(target.arrayindexed.identifier, evalCtx)
                val index = evaluate(target.arrayindexed.arrayspec.index, evalCtx)
                when (array.type) {
                    DataType.ARRAY_UB -> {
                        if (value.type != DataType.UBYTE)
                            throw VmExecutionException("new value is of different datatype ${value.type} for $array")
                    }
                    DataType.ARRAY_B -> {
                        if (value.type != DataType.BYTE)
                            throw VmExecutionException("new value is of different datatype ${value.type} for $array")
                    }
                    DataType.ARRAY_UW -> {
                        if (value.type != DataType.UWORD)
                            throw VmExecutionException("new value is of different datatype ${value.type} for $array")
                    }
                    DataType.ARRAY_W -> {
                        if (value.type != DataType.WORD)
                            throw VmExecutionException("new value is of different datatype ${value.type} for $array")
                    }
                    DataType.ARRAY_F -> {
                        if (value.type != DataType.FLOAT)
                            throw VmExecutionException("new value is of different datatype ${value.type} for $array")
                    }
                    DataType.STR, DataType.STR_S -> {
                        if (value.type !in ByteDatatypes)
                            throw VmExecutionException("new value is of different datatype ${value.type} for $array")
                    }
                    else -> throw VmExecutionException("strange array type ${array.type}")
                }
                if (array.type in ArrayDatatypes)
                    array.array!![index.integerValue()] = value.numericValue()
                else if (array.type in StringDatatypes) {
                    val indexInt = index.integerValue()
                    val newchr = Petscii.decodePetscii(listOf(value.numericValue().toShort()), true)
                    val newstr = array.str!!.replaceRange(indexInt, indexInt + 1, newchr)
                    val ident = contextStmt.definingScope().lookup(target.arrayindexed.identifier.nameInSource, contextStmt) as? VarDecl
                            ?: throw VmExecutionException("can't find assignment target ${target.identifier}")
                    val identScope = ident.definingScope()
                    program.heap.update(array.heapId!!, newstr)
                    runtimeVariables.set(identScope, ident.name, RuntimeValue(array.type, str = newstr, heapId = array.heapId))
                }
            }
            target.register != null -> {
                runtimeVariables.set(program.namespace, target.register.name, value)
            }
            else -> TODO("assign $target")
        }
    }

    private fun oneForCycle(stmt: ForLoop, loopvarDt: DataType, loopValue: Number, loopVar: IdentifierReference) {
        // assign the new loop value to the loopvar, and run the code
        performAssignment(AssignTarget(null, loopVar, null, null, loopVar.position),
                RuntimeValue(loopvarDt, loopValue), stmt.body.statements.first(), evalCtx)
        executeAnonymousScope(stmt.body)
    }

    private fun evaluate(args: List<IExpression>) = args.map { evaluate(it, evalCtx) }

    private fun performSyscall(sub: Subroutine, args: List<RuntimeValue>) {
        assert(sub.isAsmSubroutine)
        when (sub.scopedname) {
            "c64scr.print" -> {
                // if the argument is an UWORD, consider it to be the "address" of the string (=heapId)
                if (args[0].wordval != null) {
                    val str = program.heap.get(args[0].wordval!!).str!!
                    dialog.canvas.printText(str, 1, true)
                } else
                    dialog.canvas.printText(args[0].str!!, 1, true)
            }
            "c64scr.print_ub" -> {
                dialog.canvas.printText(args[0].byteval!!.toString(), 1, true)
            }
            "c64scr.print_ub0" -> {
                dialog.canvas.printText("%03d".format(args[0].byteval!!), 1, true)
            }
            "c64scr.print_b" -> {
                dialog.canvas.printText(args[0].byteval!!.toString(), 1, true)
            }
            "c64scr.print_uw" -> {
                dialog.canvas.printText(args[0].wordval!!.toString(), 1, true)
            }
            "c64scr.print_uw0" -> {
                dialog.canvas.printText("%05d".format(args[0].wordval!!), 1, true)
            }
            "c64scr.print_w" -> {
                dialog.canvas.printText(args[0].wordval!!.toString(), 1, true)
            }
            "c64scr.print_ubhex" -> {
                val prefix = if (args[0].asBoolean) "$" else ""
                val number = args[1].byteval!!
                dialog.canvas.printText("$prefix${number.toString(16).padStart(2, '0')}", 1, true)
            }
            "c64scr.print_uwhex" -> {
                val prefix = if (args[0].asBoolean) "$" else ""
                val number = args[1].wordval!!
                dialog.canvas.printText("$prefix${number.toString(16).padStart(4, '0')}", 1, true)
            }
            "c64scr.print_uwbin" -> {
                val prefix = if (args[0].asBoolean) "%" else ""
                val number = args[1].wordval!!
                dialog.canvas.printText("$prefix${number.toString(2).padStart(16, '0')}", 1, true)
            }
            "c64scr.print_ubbin" -> {
                val prefix = if (args[0].asBoolean) "%" else ""
                val number = args[1].byteval!!
                dialog.canvas.printText("$prefix${number.toString(2).padStart(8, '0')}", 1, true)
            }
            "c64scr.clear_screenchars" -> {
                dialog.canvas.clearScreen(6)
            }
            "c64scr.clear_screen" -> {
                dialog.canvas.clearScreen(args[0].integerValue().toShort())
            }
            "c64scr.setcc" -> {
                dialog.canvas.setChar(args[0].integerValue(), args[1].integerValue(), args[2].integerValue().toShort(), args[3].integerValue().toShort())
            }
            "c64scr.plot" -> {
                dialog.canvas.setCursorPos(args[0].integerValue(), args[1].integerValue())
            }
            "c64.CHROUT" -> {
                dialog.canvas.printChar(args[0].byteval!!)
            }
            "c64flt.print_f" -> {
                dialog.canvas.printText(args[0].floatval.toString(), 1, true)
            }
            else -> TODO("syscall  ${sub.scopedname} $sub")
        }
    }
}

