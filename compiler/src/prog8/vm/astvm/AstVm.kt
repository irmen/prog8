package prog8.vm.astvm

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.base.initvarsSubName
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.*
import prog8.compiler.target.c64.MachineDefinition
import prog8.vm.RuntimeValue
import prog8.vm.RuntimeValueRange
import prog8.compiler.target.c64.Petscii
import java.awt.EventQueue
import java.io.CharConversionException
import java.util.*
import kotlin.NoSuchElementException
import kotlin.concurrent.fixedRateTimer
import kotlin.math.*
import kotlin.random.Random


class VmExecutionException(msg: String?) : Exception(msg)

class VmTerminationException(msg: String?) : Exception(msg)

class VmBreakpointException : Exception("breakpoint")


class StatusFlags {
    var carry: Boolean = false
    var zero: Boolean = true
    var negative: Boolean = false
    var irqd: Boolean = false

    private fun setFlags(value: NumericLiteralValue?) {
        if (value != null) {
            when (value.type) {
                DataType.UBYTE -> {
                    val v = value.number.toInt()
                    negative = v > 127
                    zero = v == 0
                }
                DataType.BYTE -> {
                    val v = value.number.toInt()
                    negative = v < 0
                    zero = v == 0
                }
                DataType.UWORD -> {
                    val v = value.number.toInt()
                    negative = v > 32767
                    zero = v == 0
                }
                DataType.WORD -> {
                    val v = value.number.toInt()
                    negative = v < 0
                    zero = v == 0
                }
                DataType.FLOAT -> {
                    val flt = value.number.toDouble()
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
        return where[name] ?: throw NoSuchElementException("no such runtime variable: ${scope.name}.$name")
    }

    fun getMemoryAddress(scope: INameScope, name: String): Int {
        val where = memvars.getValue(scope)
        return where[name] ?: throw NoSuchElementException("no such runtime memory-variable: ${scope.name}.$name")
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

    private val rnd = Random(0)
    private val statusFlagsSave = Stack<StatusFlags>()
    private val registerXsave = Stack<RuntimeValue>()
    private val registerYsave = Stack<RuntimeValue>()
    private val registerAsave = Stack<RuntimeValue>()


    init {
        // observe the jiffyclock and screen matrix
        mem.observe(0xa0, 0xa1, 0xa2)
        for(i in 1024..2023)
            mem.observe(i)

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
        if(address in 1024..2023) {
            // write to the screen matrix
            val scraddr = address-1024
            dialog.canvas.setChar(scraddr % 40, scraddr / 40, value, 1)
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
            dialog.canvas.printText("\n<program ended>", true)
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

        var jiffies = (timeStamp-rtcOffset)*60/1000
        if(jiffies>24*3600*60-1) {
            jiffies = 0
            rtcOffset = timeStamp
        }
        // update the C-64 60hz jiffy clock in the ZP addresses:
        mem.setUByte_DMA(0x00a0, (jiffies ushr 16).toShort())
        mem.setUByte_DMA(0x00a1, (jiffies ushr 8 and 255).toShort())
        mem.setUByte_DMA(0x00a2, (jiffies and 255).toShort())
    }

    private val runtimeVariables = RuntimeVariables()
    private val evalCtx = EvalContext(program, mem, statusflags, runtimeVariables, ::performBuiltinFunction, ::executeSubroutine)

    class LoopControlBreak : Exception()
    class LoopControlContinue : Exception()
    class LoopControlReturn(val returnvalue: RuntimeValue?) : Exception()
    class LoopControlJump(val identifier: IdentifierReference?, val address: Int?, val generatedLabel: String?) : Exception()


    internal fun executeSubroutine(sub: Subroutine, arguments: List<RuntimeValue>, startAtLabel: Label?=null): RuntimeValue? {
        if(sub.isAsmSubroutine) {
            return performSyscall(sub, arguments)
        }

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
        if(startAtLabel!=null) {
            do {
                val stmt = statements.next()
            } while(stmt!==startAtLabel)
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
            return r.returnvalue
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
                            performBuiltinFunction(target.name, args, statusflags)
                        }
                    }
                    else -> {
                        TODO("weird call $target")
                    }
                }
            }
            is Return -> {
                val value =
                        if(stmt.value==null)
                            null
                        else
                            evaluate(stmt.value!!, evalCtx)
                throw LoopControlReturn(value)
            }
            is Continue -> throw LoopControlContinue()
            is Break -> throw LoopControlBreak()
            is Assignment -> {
                if (stmt.aug_op != null)
                    throw VmExecutionException("augmented assignment should have been converted into regular one $stmt")
                val value = evaluate(stmt.value, evalCtx)
                performAssignment(stmt.target, value, stmt, evalCtx)
            }
            is PostIncrDecr -> {
                when {
                    stmt.target.identifier != null -> {
                        val ident = stmt.definingScope().lookup(stmt.target.identifier!!.nameInSource, stmt) as VarDecl
                        val identScope = ident.definingScope()
                        when(ident.type){
                            VarDeclType.VAR -> {
                                var value = runtimeVariables.get(identScope, ident.name)
                                value = when {
                                    stmt.operator == "++" -> value.add(RuntimeValue(value.type, 1))
                                    stmt.operator == "--" -> value.sub(RuntimeValue(value.type, 1))
                                    else -> throw VmExecutionException("strange postincdec operator $stmt")
                                }
                                runtimeVariables.set(identScope, ident.name, value)
                            }
                            VarDeclType.MEMORY -> {
                                val addr=ident.value!!.constValue(program)!!.number.toInt()
                                val newval = when {
                                    stmt.operator == "++" -> mem.getUByte(addr)+1 and 255
                                    stmt.operator == "--" -> mem.getUByte(addr)-1 and 255
                                    else -> throw VmExecutionException("strange postincdec operator $stmt")
                                }
                                mem.setUByte(addr,newval.toShort())
                            }
                            VarDeclType.CONST -> throw VmExecutionException("can't be const")
                        }
                    }
                    stmt.target.memoryAddress != null -> {
                        val addr = evaluate(stmt.target.memoryAddress!!.addressExpression, evalCtx).integerValue()
                        val newval = when {
                            stmt.operator == "++" -> mem.getUByte(addr)+1 and 255
                            stmt.operator == "--" -> mem.getUByte(addr)-1 and 255
                            else -> throw VmExecutionException("strange postincdec operator $stmt")
                        }
                        mem.setUByte(addr,newval.toShort())
                    }
                    stmt.target.arrayindexed != null -> {
                        val arrayvar = stmt.target.arrayindexed!!.identifier.targetVarDecl(program.namespace)!!
                        val arrayvalue = runtimeVariables.get(arrayvar.definingScope(), arrayvar.name)
                        val elementType = stmt.target.arrayindexed!!.inferType(program)!!
                        val index = evaluate(stmt.target.arrayindexed!!.arrayspec.index, evalCtx).integerValue()
                        var value = RuntimeValue(elementType, arrayvalue.array!![index].toInt())
                        when {
                            stmt.operator == "++" -> value=value.inc()
                            stmt.operator == "--" -> value=value.dec()
                            else -> throw VmExecutionException("strange postincdec operator $stmt")
                        }
                        arrayvalue.array[index] = value.numericValue()
                    }
                    stmt.target.register != null -> {
                        var value = runtimeVariables.get(program.namespace, stmt.target.register!!.name)
                        value = when {
                            stmt.operator == "++" -> value.add(RuntimeValue(value.type, 1))
                            stmt.operator == "--" -> value.sub(RuntimeValue(value.type, 1))
                            else -> throw VmExecutionException("strange postincdec operator $stmt")
                        }
                        runtimeVariables.set(program.namespace, stmt.target.register!!.name, value)
                    }
                    else -> throw VmExecutionException("empty postincrdecr? $stmt")
                }
            }
            is Jump -> throw LoopControlJump(stmt.identifier, stmt.address, stmt.generatedLabel)
            is InlineAssembly -> {
                if (sub is Subroutine) {
                    val args = sub.parameters.map { runtimeVariables.get(sub, it.name) }
                    performSyscall(sub, args)
                    throw LoopControlReturn(null)
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
                        val rtval = RuntimeValue.fromLv(value)
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
                        else -> throw VmExecutionException("weird memaddress type $decl")
                    }
                } else
                    runtimeVariables.set(decl.definingScope(), decl.name, value)
            }
            target.memoryAddress != null -> {
                val address = evaluate(target.memoryAddress!!.addressExpression, evalCtx).wordval!!
                evalCtx.mem.setUByte(address, value.byteval!!)
            }
            target.arrayindexed != null -> {
                val vardecl = target.arrayindexed.identifier.targetVarDecl(program.namespace)!!
                if(vardecl.type==VarDeclType.VAR) {
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
                else {
                    val address = (vardecl.value as NumericLiteralValue).number.toInt()
                    val index = evaluate(target.arrayindexed.arrayspec.index, evalCtx).integerValue()
                    val elementType = target.arrayindexed.inferType(program)!!
                    when(elementType) {
                        DataType.UBYTE -> mem.setUByte(address+index, value.byteval!!)
                        DataType.BYTE -> mem.setSByte(address+index, value.byteval!!)
                        DataType.UWORD -> mem.setUWord(address+index*2, value.wordval!!)
                        DataType.WORD -> mem.setSWord(address+index*2, value.wordval!!)
                        DataType.FLOAT -> mem.setFloat(address+index* MachineDefinition.Mflpt5.MemorySize, value.floatval!!)
                        else -> throw VmExecutionException("strange array elt type $elementType")
                    }
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

    private fun performSyscall(sub: Subroutine, args: List<RuntimeValue>): RuntimeValue? {
        var result: RuntimeValue? = null
        when (sub.scopedname) {
            "c64scr.print" -> {
                // if the argument is an UWORD, consider it to be the "address" of the string (=heapId)
                if (args[0].wordval != null) {
                    val str = program.heap.get(args[0].wordval!!).str!!
                    dialog.canvas.printText(str, true)
                } else
                    dialog.canvas.printText(args[0].str!!, true)
            }
            "c64scr.print_ub" -> {
                dialog.canvas.printText(args[0].byteval!!.toString(), true)
            }
            "c64scr.print_ub0" -> {
                dialog.canvas.printText("%03d".format(args[0].byteval!!), true)
            }
            "c64scr.print_b" -> {
                dialog.canvas.printText(args[0].byteval!!.toString(), true)
            }
            "c64scr.print_uw" -> {
                dialog.canvas.printText(args[0].wordval!!.toString(), true)
            }
            "c64scr.print_uw0" -> {
                dialog.canvas.printText("%05d".format(args[0].wordval!!), true)
            }
            "c64scr.print_w" -> {
                dialog.canvas.printText(args[0].wordval!!.toString(), true)
            }
            "c64scr.print_ubhex" -> {
                val prefix = if (args[0].asBoolean) "$" else ""
                val number = args[1].byteval!!
                dialog.canvas.printText("$prefix${number.toString(16).padStart(2, '0')}", true)
            }
            "c64scr.print_uwhex" -> {
                val prefix = if (args[0].asBoolean) "$" else ""
                val number = args[1].wordval!!
                dialog.canvas.printText("$prefix${number.toString(16).padStart(4, '0')}", true)
            }
            "c64scr.print_uwbin" -> {
                val prefix = if (args[0].asBoolean) "%" else ""
                val number = args[1].wordval!!
                dialog.canvas.printText("$prefix${number.toString(2).padStart(16, '0')}", true)
            }
            "c64scr.print_ubbin" -> {
                val prefix = if (args[0].asBoolean) "%" else ""
                val number = args[1].byteval!!
                dialog.canvas.printText("$prefix${number.toString(2).padStart(8, '0')}", true)
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
            "c64scr.input_chars" -> {
                val input=mutableListOf<Char>()
                for(i in 0 until 80) {
                    while(dialog.keyboardBuffer.isEmpty()) {
                        Thread.sleep(10)
                    }
                    val char=dialog.keyboardBuffer.pop()
                    if(char=='\n')
                        break
                    else {
                        input.add(char)
                        val printChar = try {
                            Petscii.encodePetscii("" + char, true).first()
                        } catch (cv: CharConversionException) {
                            0x3f.toShort()
                        }
                        dialog.canvas.printPetscii(printChar)
                    }
                }
                val inputStr = input.joinToString("")
                val heapId = args[0].wordval!!
                val origStr = program.heap.get(heapId).str!!
                val paddedStr=inputStr.padEnd(origStr.length+1, '\u0000').substring(0, origStr.length)
                program.heap.update(heapId, paddedStr)
                result = RuntimeValue(DataType.UBYTE, paddedStr.indexOf('\u0000'))
            }
            "c64flt.print_f" -> {
                dialog.canvas.printText(args[0].floatval.toString(), true)
            }
            "c64.CHROUT" -> {
                dialog.canvas.printPetscii(args[0].byteval!!)
            }
            "c64.CLEARSCR" -> {
                dialog.canvas.clearScreen(6)
            }
            "c64.CHRIN" -> {
                while(dialog.keyboardBuffer.isEmpty()) {
                    Thread.sleep(10)
                }
                val char=dialog.keyboardBuffer.pop()
                result = RuntimeValue(DataType.UBYTE, char.toShort())
            }
            "c64utils.str2uword" -> {
                val heapId = args[0].wordval!!
                val argString = program.heap.get(heapId).str!!
                val numericpart = argString.takeWhile { it.isDigit() }
                result = RuntimeValue(DataType.UWORD, numericpart.toInt() and 65535)
            }
            else -> TODO("syscall  ${sub.scopedname} $sub")
        }

        return result
    }

    private fun performBuiltinFunction(name: String, args: List<RuntimeValue>, statusflags: StatusFlags): RuntimeValue? {
        return when (name) {
            "rnd" -> RuntimeValue(DataType.UBYTE, rnd.nextInt() and 255)
            "rndw" -> RuntimeValue(DataType.UWORD, rnd.nextInt() and 65535)
            "rndf" -> RuntimeValue(DataType.FLOAT, rnd.nextDouble())
            "lsb" -> RuntimeValue(DataType.UBYTE, args[0].integerValue() and 255)
            "msb" -> RuntimeValue(DataType.UBYTE, (args[0].integerValue() ushr 8) and 255)
            "sin" -> RuntimeValue(DataType.FLOAT, sin(args[0].numericValue().toDouble()))
            "sin8" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.BYTE, (127.0 * sin(rad)).toShort())
            }
            "sin8u" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.UBYTE, (128.0 + 127.5 * sin(rad)).toShort())
            }
            "sin16" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.BYTE, (32767.0 * sin(rad)).toShort())
            }
            "sin16u" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.UBYTE, (32768.0 + 32767.5 * sin(rad)).toShort())
            }
            "cos" -> RuntimeValue(DataType.FLOAT, cos(args[0].numericValue().toDouble()))
            "cos8" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.BYTE, (127.0 * cos(rad)).toShort())
            }
            "cos8u" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.UBYTE, (128.0 + 127.5 * cos(rad)).toShort())
            }
            "cos16" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.BYTE, (32767.0 * cos(rad)).toShort())
            }
            "cos16u" -> {
                val rad = args[0].numericValue().toDouble() / 256.0 * 2.0 * PI
                RuntimeValue(DataType.UBYTE, (32768.0 + 32767.5 * cos(rad)).toShort())
            }
            "tan" -> RuntimeValue(DataType.FLOAT, tan(args[0].numericValue().toDouble()))
            "atan" -> RuntimeValue(DataType.FLOAT, atan(args[0].numericValue().toDouble()))
            "ln" -> RuntimeValue(DataType.FLOAT, ln(args[0].numericValue().toDouble()))
            "log2" -> RuntimeValue(DataType.FLOAT, log2(args[0].numericValue().toDouble()))
            "sqrt" -> RuntimeValue(DataType.FLOAT, sqrt(args[0].numericValue().toDouble()))
            "sqrt16" -> RuntimeValue(DataType.UBYTE, sqrt(args[0].wordval!!.toDouble()).toInt())
            "rad" -> RuntimeValue(DataType.FLOAT, Math.toRadians(args[0].numericValue().toDouble()))
            "deg" -> RuntimeValue(DataType.FLOAT, Math.toDegrees(args[0].numericValue().toDouble()))
            "round" -> RuntimeValue(DataType.FLOAT, round(args[0].numericValue().toDouble()))
            "floor" -> RuntimeValue(DataType.FLOAT, floor(args[0].numericValue().toDouble()))
            "ceil" -> RuntimeValue(DataType.FLOAT, ceil(args[0].numericValue().toDouble()))
            "rol" -> {
                val (result, newCarry) = args[0].rol(statusflags.carry)
                statusflags.carry = newCarry
                return result
            }
            "rol2" -> args[0].rol2()
            "ror" -> {
                val (result, newCarry) = args[0].ror(statusflags.carry)
                statusflags.carry = newCarry
                return result
            }
            "ror2" -> args[0].ror2()
            "lsl" -> args[0].shl()
            "lsr" -> args[0].shr()
            "abs" -> {
                when (args[0].type) {
                    DataType.UBYTE -> args[0]
                    DataType.BYTE -> RuntimeValue(DataType.UBYTE, abs(args[0].numericValue().toDouble()))
                    DataType.UWORD -> args[0]
                    DataType.WORD -> RuntimeValue(DataType.UWORD, abs(args[0].numericValue().toDouble()))
                    DataType.FLOAT -> RuntimeValue(DataType.FLOAT, abs(args[0].numericValue().toDouble()))
                    else -> throw VmExecutionException("strange abs type ${args[0]}")
                }
            }
            "max" -> {
                val numbers = args.single().array!!.map { it.toDouble() }
                RuntimeValue(ArrayElementTypes.getValue(args[0].type), numbers.max())
            }
            "min" -> {
                val numbers = args.single().array!!.map { it.toDouble() }
                RuntimeValue(ArrayElementTypes.getValue(args[0].type), numbers.min())
            }
            "avg" -> {
                val numbers = args.single().array!!.map { it.toDouble() }
                RuntimeValue(DataType.FLOAT, numbers.average())
            }
            "sum" -> {
                val sum = args.single().array!!.map { it.toDouble() }.sum()
                when (args[0].type) {
                    DataType.ARRAY_UB -> RuntimeValue(DataType.UWORD, sum)
                    DataType.ARRAY_B -> RuntimeValue(DataType.WORD, sum)
                    DataType.ARRAY_UW -> RuntimeValue(DataType.UWORD, sum)
                    DataType.ARRAY_W -> RuntimeValue(DataType.WORD, sum)
                    DataType.ARRAY_F -> RuntimeValue(DataType.FLOAT, sum)
                    else -> throw VmExecutionException("weird sum type ${args[0]}")
                }
            }
            "any" -> {
                val numbers = args.single().array!!.map { it.toDouble() }
                RuntimeValue(DataType.UBYTE, if (numbers.any { it != 0.0 }) 1 else 0)
            }
            "all" -> {
                val numbers = args.single().array!!.map { it.toDouble() }
                RuntimeValue(DataType.UBYTE, if (numbers.all { it != 0.0 }) 1 else 0)
            }
            "swap" ->
                throw VmExecutionException("swap() cannot be implemented as a function")
            "strlen" -> {
                val zeroIndex = args[0].str!!.indexOf(0.toChar())
                if (zeroIndex >= 0)
                    RuntimeValue(DataType.UBYTE, zeroIndex)
                else
                    RuntimeValue(DataType.UBYTE, args[0].str!!.length)
            }
            "memset" -> {
                val target = args[0].array!!
                val amount = args[1].integerValue()
                val value = args[2].integerValue()
                for (i in 0 until amount) {
                    target[i] = value
                }
                null
            }
            "memsetw" -> {
                val target = args[0].array!!
                val amount = args[1].integerValue()
                val value = args[2].integerValue()
                for (i in 0 until amount step 2) {
                    target[i * 2] = value and 255
                    target[i * 2 + 1] = value ushr 8
                }
                null
            }
            "memcopy" -> {
                val source = args[0].array!!
                val dest = args[1].array!!
                val amount = args[2].integerValue()
                for(i in 0 until amount) {
                    dest[i] = source[i]
                }
                null
            }
            "mkword" -> {
                val result = (args[1].integerValue() shl 8) or args[0].integerValue()
                RuntimeValue(DataType.UWORD, result)
            }
            "set_carry" -> {
                statusflags.carry=true
                null
            }
            "clear_carry" -> {
                statusflags.carry=false
                null
            }
            "set_irqd" -> {
                statusflags.irqd=true
                null
            }
            "clear_irqd" -> {
                statusflags.irqd=false
                null
            }
            "read_flags" -> {
                val carry = if(statusflags.carry) 1 else 0
                val zero = if(statusflags.zero) 2 else 0
                val irqd = if(statusflags.irqd) 4 else 0
                val negative = if(statusflags.negative) 128 else 0
                RuntimeValue(DataType.UBYTE, carry or zero or irqd or negative)
            }
            "rsave" -> {
                statusFlagsSave.push(statusflags)
                registerAsave.push(runtimeVariables.get(program.namespace, Register.A.name))
                registerXsave.push(runtimeVariables.get(program.namespace, Register.X.name))
                registerYsave.push(runtimeVariables.get(program.namespace, Register.Y.name))
                null
            }
            "rrestore" -> {
                val flags = statusFlagsSave.pop()
                statusflags.carry = flags.carry
                statusflags.negative = flags.negative
                statusflags.zero = flags.zero
                statusflags.irqd = flags.irqd
                runtimeVariables.set(program.namespace, Register.A.name, registerAsave.pop())
                runtimeVariables.set(program.namespace, Register.X.name, registerXsave.pop())
                runtimeVariables.set(program.namespace, Register.Y.name, registerYsave.pop())
                null
            }
            else -> TODO("builtin function $name")
        }
    }

}

