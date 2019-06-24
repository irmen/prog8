package prog8.astvm

import prog8.ast.*
import prog8.compiler.RuntimeValue
import java.awt.EventQueue


class VmExecutionException(msg: String?) : Exception(msg)

class VmTerminationException(msg: String?) : Exception(msg)

class VmBreakpointException : Exception("breakpoint")


class RuntimeVariables {
    fun define(scope: INameScope, name: String, initialValue: RuntimeValue) {
        val where = vars.getValue(scope)
        where[name] = initialValue
        vars[scope] = where
        println("DEFINE RUNTIMEVAR:  ${scope.name}.$name = $initialValue")    // TODO
    }

    fun set(scope: INameScope, name: String, value: RuntimeValue) {
        val where = vars.getValue(scope)
        val existing = where[name] ?: throw NoSuchElementException("no such runtime variable: ${scope.name}.$name")
        if(existing.type!=value.type)
            throw VmExecutionException("new value is of different datatype ${value.type} expected ${existing.type} for $name")
        where[name] = value
        vars[scope] = where
        println("SET RUNTIMEVAR:  ${scope.name}.$name = $value")    // TODO
    }

    fun get(scope: INameScope, name: String): RuntimeValue {
        val where = vars.getValue(scope)
        val value = where[name]
        if(value!=null)
            return value
        throw NoSuchElementException("no such runtime variable: ${scope.name}.$name")
    }

    private val vars = mutableMapOf<INameScope, MutableMap<String, RuntimeValue>>().withDefault { mutableMapOf() }
}


class AstVm(val program: Program) {
    val mem = Memory()
    var P_carry: Boolean = false
        private set
    var P_zero: Boolean = true
        private set
    var P_negative: Boolean = false
        private set
    var P_irqd: Boolean = false
        private set
    private var dialog = ScreenDialog()
    var instructionCounter = 0

    init {
        dialog.requestFocusInWindow()

        EventQueue.invokeLater {
            dialog.pack()
            dialog.isVisible = true
            dialog.start()
        }
    }

    fun run() {
        try {
            val init = VariablesCreator(runtimeVariables, program.heap)
            init.process(program)

            // initialize all global variables
            for(m in program.modules) {
                for (b in m.statements.filterIsInstance<Block>()) {
                    for (s in b.statements.filterIsInstance<Subroutine>()) {
                        if (s.name == initvarsSubName) {
                            try {
                                executeSubroutine(s, emptyList())
                            } catch (x: LoopControlReturn) {
                                // regular return
                            }
                        }
                    }
                }
            }

            val entrypoint = program.entrypoint() ?: throw VmTerminationException("no valid entrypoint found")
            try {
                executeSubroutine(entrypoint, emptyList())
            } catch (x: LoopControlReturn) {
                // regular return
            }
            println("PROGRAM EXITED!")
            dialog.title = "PROGRAM EXITED"
        } catch(bp: VmBreakpointException) {
            println("Breakpoint: execution halted. Press enter to resume.")
            readLine()
        } catch (tx: VmTerminationException) {
            println("Execution halted: ${tx.message}")
        } catch (xx: VmExecutionException) {
            println("Execution error: ${xx.message}")
            throw xx
        }
    }

    private val runtimeVariables = RuntimeVariables()

    class LoopControlBreak: Exception()
    class LoopControlContinue: Exception()
    class LoopControlReturn(val returnvalues: List<RuntimeValue>): Exception()

    internal fun executeSubroutine(sub: INameScope, arguments: List<RuntimeValue>): List<RuntimeValue> {
        if (sub.statements.isEmpty())
            if(sub !is AnonymousScope)
                throw VmTerminationException("scope contains no statements: $sub")
        if(sub is Subroutine) {
            assert(!sub.isAsmSubroutine)
            // TODO process arguments if it's a subroutine
        }
        try {
            for (s in sub.statements) {
                executeStatement(sub, s)
            }
        } catch (r: LoopControlReturn) {
            return r.returnvalues
        }
        if(sub !is AnonymousScope)
            throw VmTerminationException("instruction pointer overflow, is a return missing? $sub")
        return emptyList()
    }

    private fun executeStatement(sub: INameScope, stmt: IStatement) {
        instructionCounter++
        if(instructionCounter % 10 == 0)
            Thread.sleep(1)
        when (stmt) {
            is NopStatement, is Label, is Subroutine -> {
                // do nothing, skip this instruction
            }
            is Directive -> {
                if(stmt.directive=="%breakpoint")
                    throw VmBreakpointException()
                else if(stmt.directive=="%asm")
                    throw VmExecutionException("can't execute assembly code")
            }
            is VarDecl -> {
                // should have been defined already when the program started
            }
            is FunctionCallStatement -> {
                val target = stmt.target.targetStatement(program.namespace)
                when(target) {
                    is Subroutine -> {
                        val args = evaluate(stmt.arglist)
                        if(target.isAsmSubroutine) {
                            performSyscall(target, args)
                        } else {
                            val results = executeSubroutine(target, args)
                            // TODO process result values
                        }
                    }
                    is BuiltinFunctionStatementPlaceholder -> {
                        val args = evaluate(stmt.arglist)
                        performBuiltinFunction(target.name, args)
                    }
                    else -> {
                        TODO("CALL $target")
                    }
                }
            }
            is BuiltinFunctionStatementPlaceholder -> {
                TODO("$stmt")
            }
            is Return -> throw LoopControlReturn(stmt.values.map { evaluate(it, program, runtimeVariables, ::executeSubroutine) })
            is Continue -> throw LoopControlContinue()
            is Break -> throw LoopControlBreak()
            is Assignment -> {
                if(stmt.aug_op!=null)
                    throw VmExecutionException("augmented assignment should have been converted into regular one $stmt")
                val target = stmt.singleTarget
                if(target!=null) {
                    when {
                        target.identifier!=null -> {
                            val ident = stmt.definingScope().lookup(target.identifier.nameInSource, stmt) as VarDecl
                            val value = evaluate(stmt.value, program, runtimeVariables, ::executeSubroutine)
                            val identScope = ident.definingScope()
                            runtimeVariables.set(identScope, ident.name, value)
                        }
                        target.memoryAddress!=null -> {
                            TODO("$stmt")
                        }
                        target.arrayindexed!=null -> {
                            val array = evaluate(target.arrayindexed.identifier, program, runtimeVariables, ::executeSubroutine)
                            val index = evaluate(target.arrayindexed.arrayspec.index, program, runtimeVariables, ::executeSubroutine)
                            val value = evaluate(stmt.value, program, runtimeVariables, ::executeSubroutine)
                            when(array.type) {
                                DataType.ARRAY_UB -> {
                                    if(value.type!=DataType.UBYTE)
                                        throw VmExecutionException("new value is of different datatype ${value.type} for $array")
                                }
                                DataType.ARRAY_B -> {
                                    if(value.type!=DataType.BYTE)
                                        throw VmExecutionException("new value is of different datatype ${value.type} for $array")
                                }
                                DataType.ARRAY_UW -> {
                                    if(value.type!=DataType.UWORD)
                                        throw VmExecutionException("new value is of different datatype ${value.type} for $array")
                                }
                                DataType.ARRAY_W -> {
                                    if(value.type!=DataType.WORD)
                                        throw VmExecutionException("new value is of different datatype ${value.type} for $array")
                                }
                                DataType.ARRAY_F -> {
                                    if(value.type!=DataType.FLOAT)
                                        throw VmExecutionException("new value is of different datatype ${value.type} for $array")
                                }
                                else -> throw VmExecutionException("strange array type ${array.type}")
                            }
                            array.array!![index.integerValue()] = value.numericValue()
                        }
                    }
                }
                else TODO("$stmt")
            }
            is PostIncrDecr -> {
                when {
                    stmt.target.identifier!=null -> {
                        val ident = stmt.definingScope().lookup(stmt.target.identifier!!.nameInSource, stmt) as VarDecl
                        val identScope = ident.definingScope()
                        var value = runtimeVariables.get(identScope, ident.name)
                        value = when {
                            stmt.operator=="++" -> value.add(RuntimeValue(value.type, 1))
                            stmt.operator=="--" -> value.sub(RuntimeValue(value.type, 1))
                            else -> throw VmExecutionException("strange postincdec operator $stmt")
                        }
                        runtimeVariables.set(identScope, ident.name, value)
                    }
                    stmt.target.memoryAddress!=null -> {
                        TODO("$stmt")
                    }
                    stmt.target.arrayindexed!=null -> {
                        TODO("$stmt")
                    }
                }
            }
            is Jump -> {
                TODO("$stmt")
            }
            is InlineAssembly -> {
                throw VmExecutionException("can't execute inline assembly in $sub")
            }
            is AnonymousScope -> {
                throw VmExecutionException("anonymous scopes should have been flattened")
            }
            is IfStatement -> {
                val condition = evaluate(stmt.condition, program, runtimeVariables, ::executeSubroutine)
                if(condition.asBoolean)
                    executeSubroutine(stmt.truepart, emptyList())
                else
                    executeSubroutine(stmt.elsepart, emptyList())
            }
            is BranchStatement -> {
                TODO("$stmt")
            }
            is ForLoop -> {
                TODO("$stmt")
//                try {
//
//                } catch(b: LoopControlBreak) {
//                    break
//                } catch(c: LoopControlContinue){
//                    continue
//                }
            }
            is WhileLoop -> {
                var condition = evaluate(stmt.condition, program, runtimeVariables, ::executeSubroutine)
                while (condition.asBoolean) {
                    try {
                        println("STILL IN WHILE LOOP ${stmt.position}")
                        executeSubroutine(stmt.body, emptyList())
                        condition = evaluate(stmt.condition, program, runtimeVariables, ::executeSubroutine)
                    } catch(b: LoopControlBreak) {
                        break
                    } catch(c: LoopControlContinue){
                        continue
                    }
                }
                println(">>>>WHILE LOOP EXITED")
            }
            is RepeatLoop -> {
                do {
                    val condition = evaluate(stmt.untilCondition, program, runtimeVariables, ::executeSubroutine)
                    try {
                        executeSubroutine(stmt.body, emptyList())
                    } catch(b: LoopControlBreak) {
                        break
                    } catch(c: LoopControlContinue){
                        continue
                    }
                } while(!condition.asBoolean)
            }
            else -> {
                TODO("implement $stmt")
            }
        }
    }


    private fun evaluate(args: List<IExpression>): List<RuntimeValue>  = args.map { evaluate(it, program, runtimeVariables, ::executeSubroutine) }

    private fun performBuiltinFunction(name: String, args: List<RuntimeValue>) {
        when(name) {
            "memset" -> {
                val target = args[0].array!!
                val amount = args[1].integerValue()
                val value = args[2].integerValue()
                for(i in 0 until amount) {
                    target[i] = value
                }
            }
            else -> TODO("builtin function $name")
        }
    }

    private fun performSyscall(sub: Subroutine, args: List<RuntimeValue>) {
        assert(sub.isAsmSubroutine)
        when(sub.scopedname) {
            "c64scr.print" -> {
                // if the argument is an UWORD, consider it to be the "address" of the string (=heapId)
                if(args[0].wordval!=null) {
                    val str = program.heap.get(args[0].wordval!!).str!!
                    dialog.canvas.printText(str, 1, true)
                }
                else
                    dialog.canvas.printText(args[0].str!!, 1, true)
            }
            "c64scr.print_ub" -> {
                dialog.canvas.printText(args[0].byteval!!.toString(), 1, true)
            }
            "c64scr.print_b" -> {
                dialog.canvas.printText(args[0].byteval!!.toString(), 1, true)
            }
            "c64scr.print_ubhex" -> {
                val prefix = if(args[0].asBoolean) "$" else ""
                val number = args[1].byteval!!
                dialog.canvas.printText("$prefix${number.toString(16).padStart(2, '0')}", 1, true)
            }
            "c64scr.print_uw" -> {
                dialog.canvas.printText(args[0].wordval!!.toString(), 1, true)
            }
            "c64scr.print_w" -> {
                dialog.canvas.printText(args[0].wordval!!.toString(), 1, true)
            }
            "c64scr.print_uwhex" -> {
                val prefix = if(args[0].asBoolean) "$" else ""
                val number = args[1].wordval!!
                dialog.canvas.printText("$prefix${number.toString(16).padStart(4, '0')}", 1, true)
            }
            "c64.CHROUT" -> {
                dialog.canvas.printChar(args[0].byteval!!)
            }
            else -> TODO("syscall $sub")
        }
    }


    private fun setFlags(value: LiteralValue?) {
        if(value!=null) {
            when(value.type) {
                DataType.UBYTE -> {
                    val v = value.bytevalue!!.toInt()
                    P_negative = v>127
                    P_zero = v==0
                }
                DataType.BYTE -> {
                    val v = value.bytevalue!!.toInt()
                    P_negative = v<0
                    P_zero = v==0
                }
                DataType.UWORD -> {
                    val v = value.wordvalue!!
                    P_negative = v>32767
                    P_zero = v==0
                }
                DataType.WORD -> {
                    val v = value.wordvalue!!
                    P_negative = v<0
                    P_zero = v==0
                }
                DataType.FLOAT -> {
                    val flt = value.floatvalue!!
                    P_negative = flt < 0.0
                    P_zero = flt==0.0
                }
                else -> {
                    // no flags for non-numeric type
                }
            }
        }
    }
}
