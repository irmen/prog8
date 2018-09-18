package prog8.compiler

import prog8.ast.*
import prog8.stackvm.*
import java.io.PrintStream
import java.util.*
import kotlin.math.abs


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

    val breakStmtLabelStack : Stack<String> = Stack()
    val continueStmtLabelStack : Stack<String> = Stack()

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
        if(continueStmtLabelStack.empty())
            throw CompilerException("continue outside of loop statement block")
        val label = continueStmtLabelStack.peek()
        stackvmProg.instr(Opcode.JUMP, null, label)
    }

    private fun translate(stmt: Break) {
        stackvmProg.line(stmt.position)
        if(breakStmtLabelStack.empty())
            throw CompilerException("break outside of loop statement block")
        val label = breakStmtLabelStack.peek()
        stackvmProg.instr(Opcode.JUMP, null, label)
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
        if(loop.body.isEmpty()) return
        stackvmProg.line(loop.position)
        val loopVarName: String
        val loopVarDt: DataType

        if(loop.loopRegister!=null) {
            val reg = loop.loopRegister
            loopVarName = reg.toString()
            loopVarDt = when (reg) {
                Register.A, Register.X, Register.Y -> DataType.BYTE
                Register.AX, Register.AY, Register.XY -> DataType.WORD
            }
        } else {
            val loopvar = (loop.loopVar!!.targetStatement(namespace) as VarDecl)
            loopVarName = loopvar.scopedname
            loopVarDt = loopvar.datatype
        }

        if(loop.iterable is RangeExpr) {
            val range = (loop.iterable as RangeExpr).toConstantIntegerRange()
            when {
                range!=null -> {
                    if (range.isEmpty())
                        throw CompilerException("loop over empty range")
                    when (loopVarDt) {
                        DataType.BYTE -> {
                            if (range.first < 0 || range.first > 255 || range.last < 0 || range.last > 255)
                                throw CompilerException("range out of bounds for byte")
                        }
                        DataType.WORD -> {
                            if (range.first < 0 || range.first > 65535 || range.last < 0 || range.last > 65535)
                                throw CompilerException("range out of bounds for word")
                        }
                        else -> throw CompilerException("range must be byte or word")
                    }
                    translateForOverConstantRange(loopVarName, loopVarDt, range, loop.body)
                }
                loop.loopRegister!=null ->
                    translateForOverVariableRange(null, loop.loopRegister, loopVarDt, loop.iterable as RangeExpr, loop.body)
                else ->
                    translateForOverVariableRange(loop.loopVar!!.nameInSource, null, loopVarDt, loop.iterable as RangeExpr, loop.body)
            }
        } else {
            val litVal = loop.iterable as? LiteralValue
            val ident = loop.iterable as? IdentifierReference
            when {
                litVal?.strvalue != null -> {
                    TODO("loop over string $litVal")
                }
                ident!=null -> {
                    val symbol = ident.targetStatement(namespace)
                    TODO("loop over symbol: ${ident.nameInSource} -> $symbol")
                }
                else -> throw CompilerException("loopvar is something strange ${loop.iterable}")
            }
        }
    }

    private fun translateForOverConstantRange(varname: String, varDt: DataType, range: IntProgression, body: MutableList<IStatement>) {
        /**
         * for LV in start..last { body }
         * (and we already know that the range is not empty)
         * (also we know that the range's last value is really the exact last occurring value of the range)
         * (and finally, start and last are constant integer values)
         *   ->
         *      LV = start
         * loop:
         *      ..body..
         *      ..break statement:  goto break
         *      ..continue statement: goto continue
         *      ..
         * continue:
         *      LV++  (if step=1)  or LV+=step  (if step > 1)
         *      LV--  (if step=-11)  or LV-=abs(step)  (if step < 1)
         *      if LV!=last goto loop   ; if last > 0
         *      if LV>=0 goto loop      ; if last==0
         * break:
         *
         */
        val loopLabel = makeLabel("loop")
        val continueLabel = makeLabel("continue")
        val breakLabel = makeLabel("break")
        continueStmtLabelStack.push(continueLabel)
        breakStmtLabelStack.push(breakLabel)

        val varValue = Value(DataType.STR, null, varname)
        stackvmProg.instr(Opcode.PUSH, Value(varDt, range.first))
        stackvmProg.instr(Opcode.POP_VAR, varValue)
        stackvmProg.label(loopLabel)
        translate(body)
        stackvmProg.label(continueLabel)
        when {
            range.step==1 -> stackvmProg.instr(Opcode.INC_VAR, varValue)
            range.step==-1 -> stackvmProg.instr(Opcode.DEC_VAR, varValue)
            range.step>1 -> {
                stackvmProg.instr(Opcode.PUSH_VAR, varValue)
                stackvmProg.instr(Opcode.PUSH, Value(varDt, range.step))
                stackvmProg.instr(Opcode.ADD)
                stackvmProg.instr(Opcode.POP_VAR, varValue)
            }
            range.step<1 -> {
                stackvmProg.instr(Opcode.PUSH_VAR, varValue)
                stackvmProg.instr(Opcode.PUSH, Value(varDt, abs(range.step)))
                stackvmProg.instr(Opcode.SUB)
                stackvmProg.instr(Opcode.POP_VAR, varValue)
            }
        }
        if(range.last>0) {
            stackvmProg.instr(Opcode.PUSH, Value(varDt, range.last))
            stackvmProg.instr(Opcode.PUSH_VAR, varValue)
            stackvmProg.instr(Opcode.SUB)
            stackvmProg.instr(Opcode.BNE, callLabel = loopLabel)
        } else {
            stackvmProg.instr(Opcode.PUSH_VAR, varValue)
            stackvmProg.instr(Opcode.BNE, callLabel = loopLabel)
        }
        stackvmProg.label(breakLabel)
        stackvmProg.instr(Opcode.NOP)

        breakStmtLabelStack.pop()
        continueStmtLabelStack.pop()
    }

    private fun translateForOverVariableRange(varname: List<String>?, register: Register?, varDt: DataType, range: RangeExpr, body: MutableList<IStatement>) {
        /**
         * for LV in start..last { body }
         * (and we already know that the range is not empty)
         * (also we know that the range's last value is really the exact last occurring value of the range)
         * (and finally, start and last are constant integer values)
         *   ->
         *      LV = start
         * loop:
         *      ..body..
         *      ..break statement:  goto break
         *      ..continue statement: goto continue
         *      ..
         * continue:
         *      LV++  (if step is not given, and is therefore 1)
         *      LV += step (if step is given)
         *      if LV<=last goto loop
         * break:
         *
         */
        fun makeAssignmentTarget(): AssignTarget {
            return if(varname!=null)
                AssignTarget(null, IdentifierReference(varname, range.position), range.position)
            else
                AssignTarget(register, null, range.position)
        }

        var assignmentTarget = makeAssignmentTarget()
        val startAssignment = Assignment(assignmentTarget, null, range.from, range.position)
        startAssignment.linkParents(range.parent)

        var stepIncrement: PostIncrDecr? = null
        var stepAddition: Assignment? = null
        if(range.step==null) {
            assignmentTarget = makeAssignmentTarget()
            stepIncrement = PostIncrDecr(assignmentTarget, "++", range.position)
            stepIncrement.linkParents(range.parent)
        }
        else {
            assignmentTarget = makeAssignmentTarget()
            stepAddition = Assignment(
                    assignmentTarget,
                    "+=",
                    range.step ?: LiteralValue(DataType.BYTE, 1, position = range.position),
                    range.position
            )
            stepAddition.linkParents(range.parent)
        }
        translate(startAssignment)

        val loopLabel = makeLabel("loop")
        val continueLabel = makeLabel("continue")
        val breakLabel = makeLabel("break")
        continueStmtLabelStack.push(continueLabel)
        breakStmtLabelStack.push(breakLabel)

        stackvmProg.label(loopLabel)
        translate(body)
        stackvmProg.label(continueLabel)
        if(stepAddition!=null)
            translate(stepAddition)
        if(stepIncrement!=null)
            translate(stepIncrement)

        val comparison = BinaryExpression(
                if(varname!=null)
                    IdentifierReference(varname, range.position)
                else
                    RegisterExpr(register!!, range.position)
                ,"<=", range.to, range.position)
        comparison.linkParents(range.parent)
        translate(comparison)
        stackvmProg.instr(Opcode.BNE, callLabel = loopLabel)
        stackvmProg.label(breakLabel)
        stackvmProg.instr(Opcode.NOP)

        breakStmtLabelStack.pop()
        continueStmtLabelStack.pop()
    }
}
