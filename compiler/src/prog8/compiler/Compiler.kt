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


class HeapValues {
    data class HeapValue(val type: DataType, val str: String?, val array: IntArray?) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as HeapValue
            return type==other.type && str==other.str && Arrays.equals(array, other.array)
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + (str?.hashCode() ?: 0)
            result = 31 * result + (array?.let { Arrays.hashCode(it) } ?: 0)
            return result
        }
    }

    private val heap = mutableListOf<HeapValue>()

    fun add(type: DataType, str: String): Int {
        if (str.isEmpty() || str.length > 255)
            throw IllegalArgumentException("string length must be 1-255")

        // strings are 'interned' and shared if they're the same
        val value = HeapValue(type, str, null)
        val existing = heap.indexOf(value)
        if(existing>=0)
            return existing
        heap.add(value)
        return heap.size-1
    }

    fun add(type: DataType, array: IntArray): Int {
        // arrays are never shared
        heap.add(HeapValue(type, null, array))
        return heap.size-1
    }

    fun update(heapId: Int, str: String) {
        when(heap[heapId].type){
            DataType.STR,
            DataType.STR_P,
            DataType.STR_S,
            DataType.STR_PS -> {
                if(heap[heapId].str!!.length!=str.length)
                    throw IllegalArgumentException("heap string length mismatch")
                heap[heapId] = heap[heapId].copy(str=str)
            }
            else-> throw IllegalArgumentException("heap data type mismatch")
        }
    }

    fun update(heapId: Int, array: IntArray) {
        when(heap[heapId].type){
            DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX -> {
                if(heap[heapId].array!!.size != array.size)
                    throw IllegalArgumentException("heap array length mismatch")
                heap[heapId] = heap[heapId].copy(array=array)
            }
            else-> throw IllegalArgumentException("heap data type mismatch")
        }
    }

    fun get(heapId: Int): HeapValue = heap[heapId]

    fun allStrings() = heap.asSequence().withIndex().filter { it.value.str!=null }.toList()
    fun allArrays() = heap.asSequence().withIndex().filter { it.value.array!=null }.toList()
}


class StackVmProgram(val name: String, val heap: HeapValues) {
    private val instructions = mutableListOf<Instruction>()
    private val variables = mutableMapOf<String, MutableMap<String, Value>>()
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
            DataType.BYTE, DataType.WORD, DataType.FLOAT -> Value(decl.datatype, (decl.value as LiteralValue).asNumericValue!!)
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                val litval = (decl.value as LiteralValue)
                if(litval.heapId==null)
                    throw CompilerException("string should already be in the heap")
                Value(decl.datatype, litval.heapId)
            }
            DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX -> {
                val litval = (decl.value as LiteralValue)
                if(litval.heapId==null)
                    throw CompilerException("array/matrix should already be in the heap")
                Value(decl.datatype, litval.heapId)
            }
        }

        // We keep the block structure intact: vars are stored per block. This is needed eventually for the actual 6502 code generation later...
        val blockname = scopedname.substringBefore('.')
        val blockvars = variables[blockname] ?: mutableMapOf()
        variables[blockname] = blockvars
        blockvars[scopedname] = value
    }

    fun writeAsText(out: PrintStream) {
        Program(name, instructions, labels, variables, memory, heap).print(out)
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
        instructions.add(Instruction(Opcode.LINE, callLabel = "${position.line} ${position.file}"))
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
    fun compile(module: Module, heap: HeapValues) : StackVmProgram {
        println("\nCreating stackVM code...")

        val namespace = module.definingScope()
        val intermediate = StackVmProgram(module.name, heap)

        // create the heap of all variables used in all blocks and scopes
        val varGather = VarGatherer(intermediate)
        varGather.process(module)
        println(" ${intermediate.numVariables} allocated variables and constants")

        val translator = StatementTranslator(intermediate, namespace, heap)
        translator.process(module)
        println(" ${translator.stmtUniqueSequenceNr} source statements,  ${intermediate.numInstructions} resulting instructions")

        return intermediate
    }


    class VarGatherer(private val stackvmProg: StackVmProgram): IAstProcessor {
        // collect all the VarDecls to make them into one global list
        override fun process(decl: VarDecl): IStatement {
            if (decl.type == VarDeclType.VAR)
                stackvmProg.blockvar(decl.scopedname, decl)
            // MEMORY variables are memory mapped and thus need no storage at all
            return super.process(decl)
        }
    }

}

private class StatementTranslator(private val stackvmProg: StackVmProgram,
                                  private val namespace: INameScope,
                                  private val heap: HeapValues): IAstProcessor {
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
                is WhileLoop -> translate(stmt)
                is RepeatLoop -> translate(stmt)
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
         *      JUMP _stmt_999_end
         * _stmt_999_else:
         *      other_stuff     ;; optional
         * _stmt_999_end:
         *      nop
         *
         * @todo generate more efficient bytecode for the form with just jumps: if_xx goto .. [else goto ..] ?
         *  -> this should translate into just a single branch opcode per goto
         */
        stackvmProg.line(branch.position)
        val labelElse = makeLabel("else")
        val labelEnd = makeLabel("end")
        val opcode = when(branch.condition) {
            BranchCondition.CS -> Opcode.BCC
            BranchCondition.CC -> Opcode.BCS
            BranchCondition.EQ, BranchCondition.Z -> Opcode.BNZ
            BranchCondition.NE, BranchCondition.NZ -> Opcode.BZ
            BranchCondition.VS -> TODO("Opcode.BVC")
            BranchCondition.VC -> TODO("Opcode.BVS")
            BranchCondition.MI, BranchCondition.NEG -> Opcode.BPOS
            BranchCondition.PL, BranchCondition.POS -> Opcode.BNEG
        }
        if(branch.elsepart.isEmpty()) {
            stackvmProg.instr(opcode, callLabel = labelEnd)
            translate(branch.statements)
            stackvmProg.label(labelEnd)
        } else {
            stackvmProg.instr(opcode, callLabel = labelElse)
            translate(branch.statements)
            stackvmProg.instr(Opcode.JUMP, callLabel = labelEnd)
            stackvmProg.label(labelElse)
            translate(branch.elsepart)
            stackvmProg.label(labelEnd)
        }
        stackvmProg.instr(Opcode.NOP)
    }

    private fun makeLabel(postfix: String): String = "_prog8stmt_${stmtUniqueSequenceNr}_$postfix"

    private fun translate(stmt: IfStatement) {
        /*
         * An IF statement: IF (condition-expression) { stuff } else { other_stuff }
         * Which is translated into:
         *      <condition-expression evaluation>
         *      BZ _stmt_999_else
         *      stuff
         *      JUMP _stmt_999_end
         * _stmt_999_else:
         *      other_stuff     ;; optional
         * _stmt_999_end:
         *      nop
         *
         *  or when there is no else block:
         *      <condition-expression evaluation>
         *      BZ _stmt_999_end
         *      stuff
         * _stmt_999_end:
         *      nop
         *
         * @todo generate more efficient bytecode for the form with just jumps: if(..) goto .. [else goto ..]
         */
        stackvmProg.line(stmt.position)
        translate(stmt.condition)
        val labelEnd = makeLabel("end")
        if(stmt.elsepart.isEmpty()) {
            stackvmProg.instr(Opcode.BZ, callLabel = labelEnd)
            translate(stmt.statements)
            stackvmProg.label(labelEnd)
        } else {
            val labelElse = makeLabel("else")
            stackvmProg.instr(Opcode.BZ, callLabel = labelElse)
            translate(stmt.statements)
            stackvmProg.instr(Opcode.JUMP, callLabel = labelEnd)
            stackvmProg.label(labelElse)
            translate(stmt.elsepart)
            stackvmProg.label(labelEnd)
        }
        stackvmProg.instr(Opcode.NOP)
    }

    private fun checkForFloatPrecisionProblem(left: IExpression, right: IExpression) {
        val leftDt = left.resultingDatatype(namespace, heap)
        val rightDt = right.resultingDatatype(namespace, heap)
        if (leftDt == DataType.BYTE || leftDt == DataType.WORD) {
            if(rightDt==DataType.FLOAT)
                printWarning("byte or word value implicitly converted to float. Suggestion: use explicit flt() conversion or revert to byte/word arithmetic", left.position)
        }
    }

    private fun translate(expr: IExpression) {
        when(expr) {
            is RegisterExpr -> {
                stackvmProg.instr(Opcode.PUSH_VAR, callLabel = expr.register.toString())
            }
            is PrefixExpression -> {
                translate(expr.expression)
                translatePrefixOperator(expr.operator)
            }
            is BinaryExpression -> {
                checkForFloatPrecisionProblem(expr.left, expr.right)
                translate(expr.left)
                translate(expr.right)
                translateBinaryOperator(expr.operator)
            }
            is FunctionCall -> {
                val target = expr.target.targetStatement(namespace)
                if(target is BuiltinFunctionStatementPlaceholder) {
                    // call to a builtin function (some will just be an opcode!)
                    expr.arglist.forEach { translate(it) }
                    val funcname = expr.target.nameInSource[0]
                    translateFunctionCall(funcname, expr.arglist)
                } else {
                    when(target) {
                        is Subroutine -> translateSubroutineCall(target, expr.arglist, expr.parent)
                        else -> TODO("non-builtin-function call to $target")
                    }
                }
            }
            is IdentifierReference -> {
                val target = expr.targetStatement(namespace)
                when(target) {
                    is VarDecl -> {
                        when(target.type) {
                            VarDeclType.VAR ->
                                stackvmProg.instr(Opcode.PUSH_VAR, callLabel = target.scopedname)
                            VarDeclType.CONST ->
                                throw CompilerException("const ref should have been const-folded away")
                            VarDeclType.MEMORY -> {
                                when(target.datatype){
                                    DataType.BYTE -> stackvmProg.instr(Opcode.PUSH_MEM, Value(DataType.WORD, (target.value as LiteralValue).asNumericValue!!))
                                    DataType.WORD -> stackvmProg.instr(Opcode.PUSH_MEM_W, Value(DataType.WORD, (target.value as LiteralValue).asNumericValue!!))
                                    DataType.FLOAT -> stackvmProg.instr(Opcode.PUSH_MEM_F, Value(DataType.WORD, (target.value as LiteralValue).asNumericValue!!))
                                    else -> TODO("invalid datatype for memory variable expression: $target")
                                }
                            }
                        }

                    }
                    else -> throw CompilerException("expression identifierref should be a vardef, not $target")
                }
            }
            is RangeExpr -> {
                TODO("TRANSLATE range $expr")
            }
            else -> {
                val lv = expr.constValue(namespace, heap) ?: throw CompilerException("constant expression required, not $expr")
                when(lv.type) {
                    DataType.BYTE -> stackvmProg.instr(Opcode.PUSH, Value(DataType.BYTE, lv.bytevalue!!))
                    DataType.WORD -> stackvmProg.instr(Opcode.PUSH, Value(DataType.WORD, lv.wordvalue!!))
                    DataType.FLOAT -> stackvmProg.instr(Opcode.PUSH, Value(DataType.FLOAT, lv.floatvalue!!))
                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                        if(lv.heapId==null)
                            throw CompilerException("string should have been moved into heap   ${lv.position}")
                        stackvmProg.instr(Opcode.PUSH, Value(lv.type, lv.heapId))
                    }
                    DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX -> {
                        if(lv.heapId==null)
                            throw CompilerException("array/matrix should have been moved into heap  ${lv.position}")
                        stackvmProg.instr(Opcode.PUSH, Value(lv.type, lv.heapId))
                    }
                }
            }
        }
    }

    private fun translate(stmt: FunctionCallStatement) {
        stackvmProg.line(stmt.position)
        val targetStmt = stmt.target.targetStatement(namespace)!!
        if(targetStmt is BuiltinFunctionStatementPlaceholder) {
            stmt.arglist.forEach { translate(it) }
            val funcname = stmt.target.nameInSource[0]
            translateFunctionCall(funcname, stmt.arglist)
            return
        }

        when(targetStmt) {
            is Label ->
                stackvmProg.instr(Opcode.CALL, callLabel = targetStmt.scopedname)
            is Subroutine ->
                translateSubroutineCall(targetStmt, stmt.arglist, stmt)
            else ->
                throw AstException("invalid call target node type: ${targetStmt::class}")
        }
    }

    private fun translateFunctionCall(funcname: String, args: List<IExpression>) {
        // some functions are implemented as vm opcodes
        when (funcname) {
            "flt" -> {
                // 1 argument, type determines the exact opcode to use
                val arg = args.single()
                when (arg.resultingDatatype(namespace, heap)) {
                    DataType.BYTE -> stackvmProg.instr(Opcode.B2FLOAT)
                    DataType.WORD -> stackvmProg.instr(Opcode.W2FLOAT)
                    DataType.FLOAT -> stackvmProg.instr(Opcode.NOP)
                    else -> throw CompilerException("wrong datatype for flt()")
                }
            }
            "msb" -> stackvmProg.instr(Opcode.MSB)
            "lsb" -> stackvmProg.instr(Opcode.LSB)
            "lsl" -> stackvmProg.instr(Opcode.SHL)
            "lsr" -> stackvmProg.instr(Opcode.SHR)
            "rol" -> stackvmProg.instr(Opcode.ROL)
            "ror" -> stackvmProg.instr(Opcode.ROR)
            "rol2" -> stackvmProg.instr(Opcode.ROL2)
            "ror2" -> stackvmProg.instr(Opcode.ROR2)
            "set_carry" -> stackvmProg.instr(Opcode.SEC)
            "clear_carry" -> stackvmProg.instr(Opcode.CLC)
            "set_irqd" -> stackvmProg.instr(Opcode.SEI)
            "clear_irqd" -> stackvmProg.instr(Opcode.CLI)
            else -> createSyscall(funcname)  // call builtin function
        }
    }

    fun translateSubroutineCall(subroutine: Subroutine, arguments: List<IExpression>, parent: Node) {
        // setup the arguments: simply put them into the register vars
        // @todo support other types of parameters beside just registers
        for(param in arguments.zip(subroutine.parameters)) {
            val assign = Assignment(
                    AssignTarget(param.second.register, null, param.first.position),
                    null,
                    param.first,
                    param.first.position
            )
            assign.linkParents(parent)
            translate(assign)
        }
        stackvmProg.instr(Opcode.CALL, callLabel=subroutine.scopedname)
    }

    private fun translateBinaryOperator(operator: String) {
        val opcode = when(operator) {
            "+" -> Opcode.ADD
            "-" -> Opcode.SUB
            "*" -> Opcode.MUL
            "/" -> Opcode.DIV
            "//" -> Opcode.FLOORDIV
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

    private fun translatePrefixOperator(operator: String) {
        val opcode = when(operator) {
            "+" -> Opcode.NOP
            "-" -> Opcode.NEG
            "~" -> Opcode.INV
            "not" -> Opcode.NOT
            else -> throw FatalAstException("const evaluation for invalid prefix operator $operator")
        }
        stackvmProg.instr(opcode)
    }

    private fun createSyscall(funcname: String) {
        val function = (
                if (funcname.startsWith("_vm_"))
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

        when {
            stmt.generatedLabel!=null -> jumpLabel = stmt.generatedLabel
            stmt.address!=null -> jumpAddress = Value(DataType.WORD, stmt.address)
            else -> {
                val target = stmt.identifier!!.targetStatement(namespace)!!
                jumpLabel = when(target) {
                    is Label -> target.scopedname
                    is Subroutine -> target.scopedname
                    else -> throw CompilerException("invalid jump target type ${target::class}")
                }
            }
        }
        stackvmProg.line(stmt.position)
        stackvmProg.instr(Opcode.JUMP, jumpAddress, jumpLabel)
    }

    private fun translate(stmt: PostIncrDecr) {
        stackvmProg.line(stmt.position)
        if(stmt.target.register!=null) {
            when(stmt.operator) {
                "++" -> stackvmProg.instr(Opcode.INC_VAR, callLabel = stmt.target.register.toString())
                "--" -> stackvmProg.instr(Opcode.DEC_VAR, callLabel = stmt.target.register.toString())
            }
        } else {
            val targetStatement = stmt.target.identifier!!.targetStatement(namespace) as VarDecl
            when(stmt.operator) {
                "++" -> stackvmProg.instr(Opcode.INC_VAR, callLabel = targetStatement.scopedname)
                "--" -> stackvmProg.instr(Opcode.DEC_VAR, callLabel = targetStatement.scopedname)
            }
        }
    }

    private fun translate(stmt: Assignment) {
        stackvmProg.line(stmt.position)
        translate(stmt.value)
        val valueDt = stmt.value.resultingDatatype(namespace, heap)
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
                    is VarDecl -> stackvmProg.instr(Opcode.PUSH_VAR, callLabel = target.scopedname)
                    else -> throw CompilerException("invalid assignment target type ${target::class}")
                }
            } else if(stmt.target.register!=null) {
                stackvmProg.instr(Opcode.PUSH_VAR, callLabel = stmt.target.register.toString())
            }
            translateAugAssignOperator(stmt.aug_op)
        }

        // pop the result value back into the assignment target
        if(stmt.target.identifier!=null) {
            val target = stmt.target.identifier!!.targetStatement(namespace)!!
            when(target) {
                is VarDecl -> stackvmProg.instr(Opcode.POP_VAR, callLabel =  target.scopedname)
                else -> throw CompilerException("invalid assignment target type ${target::class}")
            }
        } else if(stmt.target.register!=null) {
            stackvmProg.instr(Opcode.POP_VAR, callLabel = stmt.target.register.toString())
        }
    }

    private fun translateAugAssignOperator(aug_op: String) {
        val opcode = when(aug_op) {
            "+=" -> Opcode.ADD
            "-=" -> Opcode.SUB
            "/=" -> Opcode.DIV
            "//=" -> Opcode.FLOORDIV
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
        val returnvalues = (stmt.definingScope() as? Subroutine)?.returnvalues ?: emptyList()
        for(value in stmt.values.zip(returnvalues)) {
            // assign the return values to the proper result registers
            // @todo support other things than just result registers
            val assign = Assignment(
                    AssignTarget(value.second.register, null, stmt.position),
                    null,
                    value.first,
                    stmt.position
            )
            assign.linkParents(stmt.parent)
            translate(assign)
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
            if(range!=null) {
                // loop over a range with constant start, last and step values
                if (range.isEmpty())
                    throw CompilerException("loop over empty range should have been optimized away")
                else if (range.count()==1)
                    throw CompilerException("loop over just 1 value should have been optimized away")
                if((range.last-range.first) % range.step != 0)
                    throw CompilerException("range first and last must be exactly inclusive")
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
            } else {
                // loop over a range where one or more of the start, last or step values is not a constant
                if(loop.loopRegister!=null) {
                    translateForOverVariableRange(null, loop.loopRegister, loopVarDt, loop.iterable as RangeExpr, loop.body)
                }
                else {
                    translateForOverVariableRange(loop.loopVar!!.nameInSource, null, loopVarDt, loop.iterable as RangeExpr, loop.body)
                }
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
         * (and we already know that the range is not empty, and first and last are exactly inclusive.)
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
         *      LV++  (if step=1)   /   LV += step  (if step > 1)
         *      LV--  (if step=-1)  /   LV -= abs(step)  (if step < 1)
         *      if LV!=(last+step) goto loop
         * break:
         *      nop
         */
        val loopLabel = makeLabel("loop")
        val continueLabel = makeLabel("continue")
        val breakLabel = makeLabel("break")

        continueStmtLabelStack.push(continueLabel)
        breakStmtLabelStack.push(breakLabel)

        stackvmProg.instr(Opcode.PUSH, Value(varDt, range.first))
        stackvmProg.instr(Opcode.POP_VAR, callLabel = varname)
        stackvmProg.label(loopLabel)
        translate(body)
        stackvmProg.label(continueLabel)
        when {
            range.step==1 -> stackvmProg.instr(Opcode.INC_VAR, callLabel = varname)
            range.step==-1 -> stackvmProg.instr(Opcode.DEC_VAR, callLabel = varname)
            range.step>1 -> {
                stackvmProg.instr(Opcode.PUSH_VAR, callLabel = varname)
                stackvmProg.instr(Opcode.PUSH, Value(varDt, range.step))
                stackvmProg.instr(Opcode.ADD)
                stackvmProg.instr(Opcode.POP_VAR, callLabel = varname)
            }
            range.step<1 -> {
                stackvmProg.instr(Opcode.PUSH_VAR, callLabel = varname)
                stackvmProg.instr(Opcode.PUSH, Value(varDt, abs(range.step)))
                stackvmProg.instr(Opcode.SUB)
                stackvmProg.instr(Opcode.POP_VAR, callLabel = varname)
            }
        }

        // TODO: optimize edge cases if last value = 255 or 0 (for bytes) etc. to avoid  PUSH / SUB opcodes and make use of the wrapping around of the value.
        stackvmProg.instr(Opcode.PUSH, Value(varDt, range.last+range.step))
        stackvmProg.instr(Opcode.PUSH_VAR, callLabel = varname)
        stackvmProg.instr(Opcode.SUB)
        stackvmProg.instr(Opcode.BNZ, callLabel = loopLabel)

        stackvmProg.label(breakLabel)
        stackvmProg.instr(Opcode.NOP)
        // note: ending value of loop register / variable is *undefined* after this point!

        breakStmtLabelStack.pop()
        continueStmtLabelStack.pop()
    }

    private fun translateForOverVariableRange(varname: List<String>?, register: Register?, varDt: DataType, range: RangeExpr, body: MutableList<IStatement>) {
        /*
         * for LV in start..last { body }
         * (where at least one of the start, last, step values is not a constant)
         * (so we can't make any static assumptions about them)
         *   ->
         *      LV = start
         * loop:
         *      if (step > 0) {
         *          if(LV>last) goto break
         *      } else {
         *          if(LV<last) goto break
         *      }
         *      ..body..
         *      ..break statement:  goto break
         *      ..continue statement: goto continue
         *      ..
         * continue:
         *
         *      (if we know step is a constant:)
         *      step == 1 ->
         *          LV++
         *          if_nz goto loop     ;; acts as overflow check
         *      step == -1 ->
         *          LV--
         *          @todo some condition to check for not overflow , jump to loop
         *      (not constant or other step:
         *          LV += step      ; @todo implement overflow on the appropriate arithmetic operations
         *          if_vc goto loop    ;; not overflowed
         * break:
         *      nop
         */
        fun makeAssignmentTarget(): AssignTarget {
            return if(varname!=null)
                AssignTarget(null, IdentifierReference(varname, range.position), range.position)
            else
                AssignTarget(register, null, range.position)
        }

        val startAssignment = Assignment(makeAssignmentTarget(), null, range.from, range.position)
        startAssignment.linkParents(range.parent)
        translate(startAssignment)

        val loopLabel = makeLabel("loop")
        val continueLabel = makeLabel("continue")
        val breakLabel = makeLabel("break")
        val literalStepValue = (range.step as? LiteralValue)?.asNumericValue?.toInt()

        continueStmtLabelStack.push(continueLabel)
        breakStmtLabelStack.push(breakLabel)

        stackvmProg.label(loopLabel)
        if(literalStepValue!=null) {
            // Step is a constant. We can optimize some stuff!
            val loopVar =
                    if(varname!=null)
                        IdentifierReference(varname, range.position)
                    else
                        RegisterExpr(register!!, range.position)

            val condition =
                    if(literalStepValue > 0) {
                        // if LV > last  goto break
                        BinaryExpression(loopVar,">", range.to, range.position)
                    } else {
                        // if LV < last  goto break
                        BinaryExpression(loopVar,"<", range.to, range.position)
                    }
            val ifstmt = IfStatement(condition,
                    listOf(Jump(null, null, breakLabel, range.position)),
                    emptyList(),
                    range.position)
            ifstmt.linkParents(range.parent)
            translate(ifstmt)
        } else {
            // Step is a variable. We can't optimize anything...
            TODO("for loop with non-constant step comparison of LV")
        }

        translate(body)
        stackvmProg.label(continueLabel)
        val lvTarget = makeAssignmentTarget()
        lvTarget.linkParents(range.parent)
        val targetStatement: VarDecl? =
                if(lvTarget.identifier!=null) {
                    lvTarget.identifier.targetStatement(namespace) as VarDecl
                } else {
                    null
                }

        when (literalStepValue) {
            1 -> {
                // LV++
                val postIncr = PostIncrDecr(lvTarget, "++", range.position)
                postIncr.linkParents(range.parent)
                translate(postIncr)
                if(lvTarget.register!=null)
                    stackvmProg.instr(Opcode.PUSH_VAR, callLabel =lvTarget.register.toString())
                else
                    stackvmProg.instr(Opcode.PUSH_VAR, callLabel =targetStatement!!.scopedname)
                val branch = BranchStatement(
                        BranchCondition.NZ,
                        listOf(Jump(null, null, loopLabel, range.position)),
                        emptyList(), range.position)
                branch.linkParents(range.parent)
                translate(branch)
            }
            -1 -> {
                // LV--
                val postIncr = PostIncrDecr(makeAssignmentTarget(), "--", range.position)
                postIncr.linkParents(range.parent)
                translate(postIncr)
                TODO("signed numbers and/or special condition are needed for decreasing for loop. Try an increasing loop and/or constant loop values instead? At: ${range.position}")
            }
            else -> {
                TODO("non-literal-const or other-than-one step increment code At: ${range.position}")
            }
        }

        stackvmProg.label(breakLabel)
        stackvmProg.instr(Opcode.NOP)
        // note: ending value of loop register / variable is *undefined* after this point!

        breakStmtLabelStack.pop()
        continueStmtLabelStack.pop()
    }

    private fun translate(stmt: WhileLoop)
    {
        /*
         *  while condition { statements... }  ->
         *
         *      goto continue
         *  loop:
         *      statements
         *      break -> goto break
         *      continue -> goto condition
         *  continue:
         *      <evaluate condition>
         *      bnz loop
         *  break:
         *      nop
         */
        val loopLabel = makeLabel("loop")
        val breakLabel = makeLabel("break")
        val continueLabel = makeLabel("continue")
        stackvmProg.line(stmt.position)
        breakStmtLabelStack.push(breakLabel)
        continueStmtLabelStack.push(continueLabel)
        stackvmProg.instr(Opcode.JUMP, callLabel = continueLabel)
        stackvmProg.label(loopLabel)
        translate(stmt.statements)
        stackvmProg.label(continueLabel)
        translate(stmt.condition)
        stackvmProg.instr(Opcode.BNZ, callLabel = loopLabel)
        stackvmProg.label(breakLabel)
        stackvmProg.instr(Opcode.NOP)
        breakStmtLabelStack.pop()
        continueStmtLabelStack.pop()

    }

    private fun translate(stmt: RepeatLoop)
    {
        /*
         *  repeat { statements... }  until condition  ->
         *
         *  loop:
         *      statements
         *      break -> goto break
         *      continue -> goto condition
         *  condition:
         *      <evaluate untilCondition>
         *      bz goto loop
         *  break:
         *      nop
         */
        val loopLabel = makeLabel("loop")
        val continueLabel = makeLabel("continue")
        val breakLabel = makeLabel("break")
        stackvmProg.line(stmt.position)
        breakStmtLabelStack.push(breakLabel)
        continueStmtLabelStack.push(continueLabel)
        stackvmProg.label(loopLabel)
        translate(stmt.statements)
        stackvmProg.label(continueLabel)
        translate(stmt.untilCondition)
        stackvmProg.instr(Opcode.BZ, callLabel = loopLabel)
        stackvmProg.label(breakLabel)
        stackvmProg.instr(Opcode.NOP)
        breakStmtLabelStack.pop()
        continueStmtLabelStack.pop()
    }
}
