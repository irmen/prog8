package prog8.compiler

import prog8.ast.*
import prog8.stackvm.*
import java.io.PrintStream


class CompilerException(message: String?) : Exception(message)


fun Number.toHex(): String {
    //  0..15 -> "0".."15"
    //  16..255 -> "$10".."$ff"
    //  256..65536 -> "$0100".."$ffff"
    val integer = this.toInt()
    return when (integer) {
        in 0 until 16 -> integer.toString()
        in 0 until 0x100 -> "$"+integer.toString(16).padStart(2,'0')
        in 0 until 0x10000 -> "$"+integer.toString(16).padStart(4,'0')
        else -> throw CompilerException("number too large for 16 bits $this")
    }
}



class StackVmProgram(val name: String) {
    private val instructions = mutableListOf<Instruction>()
    private val variables = mutableMapOf<String, Value>()
    private val memory = mutableMapOf<Int, List<Value>>()
    private val labels = mutableMapOf<String, Instruction>()
    val numVariables: Int
        get() {return variables.size}
    val numInstructions: Int
        get() {return instructions.size}

    fun optimize() {
        println("\nOptimizing stackVM code...")

        this.instructions.removeIf { it.opcode==Opcode.NOP && it !is LabelInstr }     // remove nops (that are not a label)
        // todo optimize stackvm code
    }

    fun blockvar(scopedname: String, decl: VarDecl) {
        val value = when(decl.datatype) {
            DataType.BYTE, DataType.WORD, DataType.FLOAT -> Value(decl.datatype, (decl.value as LiteralValue).asNumericValue)
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> Value(decl.datatype, null, (decl.value as LiteralValue).strvalue)
            DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX -> TODO("array/matrix variable values")
        }
        variables[scopedname] = value
    }

    fun writeAsText(out: PrintStream) {
        Program(name, instructions, labels, variables, memory).print(out)
    }

    fun instr(opcode: Opcode, arg: Value? = null, callLabel: String? = null) {
        instructions.add(Instruction(opcode, arg, callLabel))
    }

    fun label(labelname: String) {
        val instr = LabelInstr(labelname)
        instructions.add(instr)
        labels[labelname] = instr
    }

    fun line(position: Position) {
        instructions.add(Instruction(Opcode.LINE, Value(DataType.STR, null, "${position.line} ${position.file}")))
    }
}

enum class OutputType {
    RAW,
    PRG
}

enum class LauncherType {
    BASIC,
    NONE
}

enum class ZeropageType {
    BASICSAFE,
    KERNALSAFE,
    FULL
}


data class CompilationOptions(val output: OutputType,
                              val launcher: LauncherType,
                              val zeropage: ZeropageType,
                              val floats: Boolean)


class Compiler(private val options: CompilationOptions) {
    fun compile(module: Module) : StackVmProgram {
        println("\nCreating stackVM code...")

        val namespace = module.definingScope()
        val intermediate = StackVmProgram(module.name)

        // create the pool of all variables used in all blocks and scopes
        val varGather = VarGatherer(intermediate)
        varGather.process(module)
        println("Number of allocated variables and constants: ${intermediate.numVariables}")

        val translator = StatementTranslator(intermediate, namespace)
        translator.process(module)
        println("Number of source statements: ${translator.stmtUniqueSequenceNr}")
        println("Number of vm instructions: ${intermediate.numInstructions}")

        return intermediate
    }


    class VarGatherer(private val stackvmProg: StackVmProgram): IAstProcessor {
        // collect all the VarDecls to make them into one global list
        override fun process(decl: VarDecl): IStatement {
            if(decl.type == VarDeclType.MEMORY)
                TODO("stackVm doesn't support memory vars for now")

            if (decl.type == VarDeclType.VAR) {
                stackvmProg.blockvar(decl.scopedname, decl)
            }
            return super.process(decl)
        }
    }

}

private class StatementTranslator(private val stackvmProg: StackVmProgram, private val namespace: INameScope): IAstProcessor {
    var stmtUniqueSequenceNr = 0
        private set

    override fun process(subroutine: Subroutine): IStatement {
        stackvmProg.label(subroutine.scopedname)
        translate(subroutine.statements)
        return super.process(subroutine)
    }

    override fun process(block: Block): IStatement {
        stackvmProg.label(block.scopedname)
        translate(block.statements)
        return super.process(block)
    }

    override fun process(directive: Directive): IStatement {
        when(directive.directive) {
            "%asminclude" -> throw CompilerException("can't use %asminclude in stackvm")
            "%asmbinary" -> throw CompilerException("can't use %asmbinary in stackvm")
            "%breakpoint" -> {
                stackvmProg.line(directive.position)
                stackvmProg.instr(Opcode.BREAKPOINT)
            }
        }
        return super.process(directive)
    }

    private fun translate(statements: List<IStatement>) {
        for (stmt: IStatement in statements) {
            stmtUniqueSequenceNr++
            when (stmt) {
                is AnonymousStatementList -> translate(stmt.statements)
                is Label -> translate(stmt)
                is Return -> translate(stmt)
                is Assignment -> translate(stmt)        // normal and augmented assignments
                is PostIncrDecr -> translate(stmt)
                is Jump -> translate(stmt)
                is FunctionCallStatement -> translate(stmt)
                is IfStatement -> translate(stmt)
                is BranchStatement -> translate(stmt)
                is Break -> translate(stmt)
                is Continue -> translate(stmt)
                is ForLoop -> translate(stmt)
                is Directive, is VarDecl, is Subroutine -> {}   // skip this, already processed these.
                is InlineAssembly -> throw CompilerException("inline assembly is not supported by the StackVM")
                else -> TODO("translate statement $stmt to stackvm")
            }
        }
    }


    private fun translate(stmt: Continue) {
        stackvmProg.line(stmt.position)
        TODO("translate CONTINUE")
    }

    private fun translate(stmt: Break) {
        stackvmProg.line(stmt.position)
        TODO("translate BREAK")
    }

    private fun translate(branch: BranchStatement) {
        /*
         * A branch: IF_CC { stuff } else { other_stuff }
         * Which is translated into:
         *      BCS _stmt_999_else
         *      stuff
         *      JUMP _stmt_999_continue
         * _stmt_999_else:
         *      other_stuff     ;; optional
         * _stmt_999_continue:
         *      ...
         */
        stackvmProg.line(branch.position)
        val labelElse = makeLabel("else")
        val labelContinue = makeLabel("continue")
        val opcode = when(branch.condition) {
            BranchCondition.CS -> Opcode.BCC
            BranchCondition.CC -> Opcode.BCS
            BranchCondition.EQ -> Opcode.BNE
            BranchCondition.NE -> Opcode.BEQ
            BranchCondition.VS -> TODO("Opcode.BVC")
            BranchCondition.VC -> TODO("Opcode.BVS")
            BranchCondition.MI -> Opcode.BPL
            BranchCondition.PL -> Opcode.BMI
        }
        if(branch.elsepart.isEmpty()) {
            stackvmProg.instr(opcode, callLabel = labelContinue)
            translate(branch.statements)
            stackvmProg.label(labelContinue)
        } else {
            stackvmProg.instr(opcode, callLabel = labelElse)
            translate(branch.statements)
            stackvmProg.instr(Opcode.JUMP, callLabel = labelContinue)
            stackvmProg.label(labelElse)
            translate(branch.elsepart)
            stackvmProg.label(labelContinue)
        }
    }

    private fun makeLabel(postfix: String): String = "_prog8stmt_${stmtUniqueSequenceNr}_$postfix"

    private fun translate(stmt: IfStatement) {
        /*
         * An IF statement: IF (condition-expression) { stuff } else { other_stuff }
         * Which is translated into:
         *      <condition-expression evaluation>
         *      BEQ _stmt_999_else
         *      stuff
         *      JUMP _stmt_999_continue
         * _stmt_999_else:
         *      other_stuff     ;; optional
         * _stmt_999_continue:
         *      ...
         */
        stackvmProg.line(stmt.position)
        translate(stmt.condition)
        val labelElse = makeLabel("else")
        val labelContinue = makeLabel("continue")
        if(stmt.elsepart.isEmpty()) {
            stackvmProg.instr(Opcode.BEQ, callLabel = labelContinue)
            translate(stmt.statements)
            stackvmProg.label(labelContinue)
        } else {
            stackvmProg.instr(Opcode.BEQ, callLabel = labelElse)
            translate(stmt.statements)
            stackvmProg.instr(Opcode.JUMP, callLabel = labelContinue)
            stackvmProg.label(labelElse)
            translate(stmt.elsepart)
            stackvmProg.label(labelContinue)
        }
    }

    private fun translate(expr: IExpression) {
        when(expr) {
            is RegisterExpr -> {
                stackvmProg.instr(Opcode.PUSH_VAR, Value(DataType.STR, null, expr.register.toString()))
            }
            is BinaryExpression -> {
                translate(expr.left)
                translate(expr.right)
                translateBinaryOperator(expr.operator)
            }
            is FunctionCall -> {
                expr.arglist.forEach { translate(it) }
                val target = expr.target.targetStatement(namespace)
                if(target is BuiltinFunctionStatementPlaceholder) {
                    // call to a builtin function
                    val funcname = expr.target.nameInSource[0].toUpperCase()
                    createFunctionCall(funcname)  // call builtin function
                } else {
                    when(target) {
                        is Subroutine -> {
                            stackvmProg.instr(Opcode.CALL, callLabel = target.scopedname)
                        }
                        else -> TODO("non-builtin-function call to $target")
                    }
                }
            }
            is IdentifierReference -> {
                val target = expr.targetStatement(namespace)
                when(target) {
                    is VarDecl -> {
                        stackvmProg.instr(Opcode.PUSH_VAR, Value(DataType.STR, null, target.scopedname))
                    }
                    else -> throw CompilerException("expression identifierref should be a vardef, not $target")
                }
            }
            is RangeExpr -> {
                TODO("TRANSLATE range $expr")      // todo
            }
            else -> {
                val lv = expr.constValue(namespace) ?: throw CompilerException("constant expression required, not $expr")
                when(lv.type) {
                    DataType.BYTE -> stackvmProg.instr(Opcode.PUSH, Value(DataType.BYTE, lv.bytevalue))
                    DataType.WORD -> stackvmProg.instr(Opcode.PUSH, Value(DataType.WORD, lv.wordvalue))
                    DataType.FLOAT -> stackvmProg.instr(Opcode.PUSH, Value(DataType.FLOAT, lv.floatvalue))
                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> stackvmProg.instr(Opcode.PUSH, Value(DataType.STR,null, lv.strvalue))
                    DataType.ARRAY, DataType.ARRAY_W -> {
                        lv.arrayvalue?.forEach { translate(it) }
                        stackvmProg.instr(Opcode.ARRAY, Value(DataType.WORD, lv.arrayvalue!!.size))
                    }
                    DataType.MATRIX -> TODO("matrix type")
                }
            }
        }
    }

    private fun translateBinaryOperator(operator: String) {
        val opcode = when(operator) {
            "+" -> Opcode.ADD
            "-" -> Opcode.SUB
            "*" -> Opcode.MUL
            "/" -> Opcode.DIV
            "%" -> Opcode.REMAINDER
            "**" -> Opcode.POW
            "&" -> Opcode.BITAND
            "|" -> Opcode.BITOR
            "^" -> Opcode.BITXOR
            "and" -> Opcode.AND
            "or" -> Opcode.OR
            "xor" -> Opcode.XOR
            "<" -> Opcode.LESS
            ">" -> Opcode.GREATER
            "<=" -> Opcode.LESSEQ
            ">=" -> Opcode.GREATEREQ
            "==" -> Opcode.EQUAL
            "!=" -> Opcode.NOTEQUAL
            else -> throw FatalAstException("const evaluation for invalid operator $operator")
        }
        stackvmProg.instr(opcode)
    }

    private fun translate(stmt: FunctionCallStatement) {
        stackvmProg.line(stmt.position)
        val targetStmt = stmt.target.targetStatement(namespace)!!
        if(targetStmt is BuiltinFunctionStatementPlaceholder) {
            stmt.arglist.forEach { translate(it) }
            val funcname = stmt.target.nameInSource[0].toUpperCase()
            createFunctionCall(funcname)  // call builtin function
            return
        }

        val targetname = when(targetStmt) {
            is Label -> targetStmt.scopedname
            is Subroutine -> targetStmt.scopedname
            else -> throw AstException("invalid call target node type: ${targetStmt::class}")
        }
        stmt.arglist.forEach { translate(it) }
        stackvmProg.instr(Opcode.CALL, callLabel = targetname)
    }

    private fun createFunctionCall(funcname: String) {
        val function = (
                if (funcname.startsWith("_VM_"))
                    funcname.substring(4)
                else
                    "FUNC_$funcname"
                ).toUpperCase()
        val callNr = Syscall.valueOf(function).callNr
        stackvmProg.instr(Opcode.SYSCALL, Value(DataType.BYTE, callNr))
    }

    private fun translate(stmt: Jump) {
        var jumpAddress: Value? = null
        var jumpLabel: String? = null

        if(stmt.address!=null) {
            jumpAddress = Value(DataType.WORD, stmt.address)
        } else {
            val target = stmt.identifier!!.targetStatement(namespace)!!
            jumpLabel = when(target) {
                is Label -> target.scopedname
                is Subroutine -> target.scopedname
                else -> throw CompilerException("invalid jump target type ${target::class}")
            }
        }
        stackvmProg.line(stmt.position)
        stackvmProg.instr(Opcode.JUMP, jumpAddress, jumpLabel)
    }

    private fun translate(stmt: PostIncrDecr) {
        stackvmProg.line(stmt.position)
        if(stmt.target.register!=null) {
            when(stmt.operator) {
                "++" -> stackvmProg.instr(Opcode.INC_VAR, Value(DataType.STR, null, stmt.target.register.toString()))
                "--" -> stackvmProg.instr(Opcode.DEC_VAR, Value(DataType.STR, null, stmt.target.register.toString()))
            }
        } else {
            val targetStatement = stmt.target.identifier!!.targetStatement(namespace) as VarDecl
            when(stmt.operator) {
                "++" -> stackvmProg.instr(Opcode.INC_VAR, Value(DataType.STR, null, targetStatement.scopedname))
                "--" -> stackvmProg.instr(Opcode.DEC_VAR, Value(DataType.STR, null, targetStatement.scopedname))
            }
        }
    }

    private fun translate(stmt: Assignment) {
        stackvmProg.line(stmt.position)
        translate(stmt.value)
        val valueDt = stmt.value.resultingDatatype(namespace)
        val targetDt = stmt.target.determineDatatype(namespace, stmt)
        if(valueDt!=targetDt) {
            // convert value to target datatype if possible
            when(targetDt) {
                DataType.BYTE -> throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                DataType.WORD -> {
                    if(valueDt==DataType.BYTE)
                        stackvmProg.instr(Opcode.B2WORD)
                    else
                        throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                }
                DataType.FLOAT -> {
                    when (valueDt) {
                        DataType.BYTE -> stackvmProg.instr(Opcode.B2FLOAT)
                        DataType.WORD -> stackvmProg.instr(Opcode.W2FLOAT)
                        else -> throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                    }
                }
                DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX -> throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                // todo: maybe if you assign byte or word to array/matrix, clear it with that value?
            }
        }

        if(stmt.aug_op!=null) {
            // augmented assignment
            if(stmt.target.identifier!=null) {
                val target = stmt.target.identifier!!.targetStatement(namespace)!!
                when(target) {
                    is VarDecl -> stackvmProg.instr(Opcode.PUSH_VAR, Value(DataType.STR, null, target.scopedname))
                    else -> throw CompilerException("invalid assignment target type ${target::class}")
                }
            } else if(stmt.target.register!=null) {
                stackvmProg.instr(Opcode.PUSH_VAR, Value(DataType.STR, null, stmt.target.register.toString()))
            }
            translateAugAssignOperator(stmt.aug_op)
        }

        // pop the result value back into the assignment target
        if(stmt.target.identifier!=null) {
            val target = stmt.target.identifier!!.targetStatement(namespace)!!
            when(target) {
                is VarDecl -> stackvmProg.instr(Opcode.POP_VAR, Value(DataType.STR, null, target.scopedname))
                else -> throw CompilerException("invalid assignment target type ${target::class}")
            }
        } else if(stmt.target.register!=null) {
            stackvmProg.instr(Opcode.POP_VAR, Value(DataType.STR, null, stmt.target.register.toString()))
        }
    }

    private fun translateAugAssignOperator(aug_op: String) {
        val opcode = when(aug_op) {
            "+=" -> Opcode.ADD
            "-=" -> Opcode.SUB
            "/=" -> Opcode.DIV
            "*=" -> Opcode.MUL
            "**=" -> Opcode.POW
            "&=" -> Opcode.BITAND
            "|=" -> Opcode.BITOR
            "^=" -> Opcode.BITXOR
            else -> throw CompilerException("invalid aug assignment operator $aug_op")
        }
        stackvmProg.instr(opcode)
    }

    private fun translate(stmt: Return) {
        if(stmt.values.isNotEmpty()) {
            TODO("return with value(s) not yet supported: $stmt")
        }
        stackvmProg.line(stmt.position)
        stackvmProg.instr(Opcode.RETURN)
    }

    private fun translate(stmt: Label) {
        stackvmProg.label(stmt.scopedname)
    }

    private fun translate(loop: ForLoop) {
        stackvmProg.line(loop.position)
        if(loop.loopRegister!=null) {
            val reg = loop.loopRegister
            if(loop.iterable is RangeExpr) {
                val range = (loop.iterable as RangeExpr).toKotlinRange()
                if(range.isEmpty())
                    throw CompilerException("loop over empty range")
                if(range.step==1) {
                    println("@todo for loop, register, range, step 1")      // todo
                } else {
                    TODO("loop over range with step != 1")
                }
            } else {
                TODO("loop over something else as a Range: ${loop.iterable}")
            }
        } else {
            val loopvar = (loop.loopVar!!.targetStatement(namespace) as VarDecl)
            println("@TODO translate for loop (variable $loopvar)") // TODO
        }
    }
}
