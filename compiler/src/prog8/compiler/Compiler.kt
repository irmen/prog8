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


fun String.unescape(): String {
    val result = mutableListOf<Char>()
    val iter = this.iterator()
    while(iter.hasNext()) {
        val c = iter.nextChar()
        if(c=='\\') {
            val ec = iter.nextChar()
            result.add(when(ec) {
                '\\' -> '\\'
                'b' -> '\b'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    "${iter.nextChar()}${iter.nextChar()}${iter.nextChar()}${iter.nextChar()}".toInt(16).toChar()
                }
                else -> throw VmExecutionException("invalid escape char: $ec")
            })
        } else {
            result.add(c)
        }
    }
    return result.joinToString("")
}


class HeapValues {
    data class HeapValue(val type: DataType, val str: String?, val array: IntArray?, val doubleArray: DoubleArray?) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as HeapValue
            return type==other.type && str==other.str && Arrays.equals(array, other.array) && Arrays.equals(doubleArray, other.doubleArray)
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + (str?.hashCode() ?: 0)
            result = 31 * result + (array?.let { Arrays.hashCode(it) } ?: 0)
            result = 31 * result + (doubleArray?.let { Arrays.hashCode(it) } ?: 0)
            return result
        }
    }

    private val heap = mutableListOf<HeapValue>()

    fun add(type: DataType, str: String): Int {
        if (str.isEmpty() || str.length > 255)
            throw IllegalArgumentException("string length must be 1-255")

        // strings are 'interned' and shared if they're the same
        val value = HeapValue(type, str, null, null)
        val existing = heap.indexOf(value)
        if(existing>=0)
            return existing
        heap.add(value)
        return heap.size-1
    }

    fun add(type: DataType, array: IntArray): Int {
        // arrays are never shared
        heap.add(HeapValue(type, null, array, null))
        return heap.size-1
    }

    fun add(type: DataType, darray: DoubleArray): Int {
        // arrays are never shared
        heap.add(HeapValue(type, null, null, darray))
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

    fun update(heapId: Int, heapval: HeapValue) {
        heap[heapId] = heapval
    }

    fun get(heapId: Int): HeapValue = heap[heapId]

    fun allStrings() = heap.asSequence().withIndex().filter { it.value.str!=null }.toList()
    fun allArrays() = heap.asSequence().withIndex().filter { it.value.array!=null }.toList()
    fun allDoubleArrays() = heap.asSequence().withIndex().filter { it.value.doubleArray!=null }.toList()
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
            DataType.ARRAY, DataType.ARRAY_W, DataType.ARRAY_F, DataType.MATRIX -> {
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
        if(subroutine.asmAddress==null) {
            stackvmProg.label(subroutine.scopedname)
            // note: the caller has already written the arguments into the subroutine's parameter variables.
            translate(subroutine.statements)
        } else {
            throw CompilerException("kernel subroutines (with memory address and no body) are not supported by StackVM: $subroutine")
        }
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

    private fun opcodePush(dt: DataType): Opcode {
        return when (dt) {
            DataType.BYTE -> Opcode.PUSH
            DataType.WORD -> Opcode.PUSH_W
            DataType.FLOAT -> Opcode.PUSH_F
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS,
            DataType.ARRAY, DataType.ARRAY_W, DataType.ARRAY_F, DataType.MATRIX -> Opcode.PUSH_W
        }
    }

    private fun opcodePushvar(dt: DataType): Opcode {
        return when (dt)  {
            DataType.BYTE -> Opcode.PUSH_VAR
            DataType.WORD -> Opcode.PUSH_VAR_W
            DataType.FLOAT -> Opcode.PUSH_VAR_F
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS,
            DataType.ARRAY, DataType.ARRAY_W, DataType.ARRAY_F, DataType.MATRIX -> Opcode.PUSH_VAR_W
        }
    }

    private fun opcodePushvar(reg: Register): Opcode {
        return when(reg) {
            Register.A, Register.X, Register.Y -> Opcode.PUSH_VAR
            Register.AX, Register.AY, Register.XY -> Opcode.PUSH_VAR_W
        }
    }

    private fun opcodePopvar(reg: Register): Opcode {
        return when(reg) {
            Register.A, Register.X, Register.Y -> Opcode.POP_VAR
            Register.AX, Register.AY, Register.XY -> Opcode.POP_VAR_W
        }
    }

    private fun opcodeReadindexedvar(dt: DataType): Opcode {
        return when (dt)  {
            DataType.ARRAY -> Opcode.READ_INDEXED_VAR
            DataType.ARRAY_W -> Opcode.READ_INDEXED_VAR_W
            DataType.ARRAY_F -> Opcode.READ_INDEXED_VAR_F
            else -> throw CompilerException("invalid dt for indexed $dt")
        }
    }

    private fun opcodeWriteindexedvar(dt: DataType): Opcode {
        return when (dt)  {
            DataType.ARRAY -> Opcode.WRITE_INDEXED_VAR
            DataType.ARRAY_W -> Opcode.WRITE_INDEXED_VAR_W
            DataType.ARRAY_F -> Opcode.WRITE_INDEXED_VAR_F
            else -> throw CompilerException("invalid dt for indexed $dt")
        }
    }

    private fun opcodeDiscard(dt: DataType): Opcode {
        return when(dt) {
            DataType.BYTE -> Opcode.DISCARD
            DataType.WORD -> Opcode.DISCARD_W
            DataType.FLOAT -> Opcode.DISCARD_F
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS,
            DataType.ARRAY, DataType.ARRAY_W, DataType.ARRAY_F, DataType.MATRIX -> Opcode.DISCARD_W
        }
    }

    private fun opcodePopvar(dt: DataType): Opcode {
        return when (dt) {
            DataType.BYTE -> Opcode.POP_VAR
            DataType.WORD -> Opcode.POP_VAR_W
            DataType.FLOAT -> Opcode.POP_VAR_F
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS,
            DataType.ARRAY, DataType.ARRAY_W, DataType.ARRAY_F, DataType.MATRIX -> Opcode.POP_VAR_W
        }
    }

    private fun opcodeDecvar(reg: Register): Opcode {
        return when(reg) {
            Register.A, Register.X, Register.Y -> Opcode.DEC_VAR
            Register.AX, Register.AY, Register.XY -> Opcode.DEC_VAR_W
        }
    }

    private fun opcodeIncvar(reg: Register): Opcode {
        return when(reg) {
            Register.A, Register.X, Register.Y -> Opcode.INC_VAR
            Register.AX, Register.AY, Register.XY -> Opcode.INC_VAR_W
        }
    }

    private fun opcodeDecvar(dt: DataType): Opcode {
        return when(dt) {
            DataType.BYTE -> Opcode.DEC_VAR
            DataType.WORD -> Opcode.DEC_VAR_W
            else -> throw CompilerException("can't dec type $dt")
        }
    }

    private fun opcodeIncvar(dt: DataType): Opcode {
        return when(dt) {
            DataType.BYTE -> Opcode.INC_VAR
            DataType.WORD -> Opcode.INC_VAR_W
            else -> throw CompilerException("can't inc type $dt")
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
                val opcode = opcodePushvar(expr.register)
                stackvmProg.instr(opcode, callLabel = expr.register.toString())
            }
            is PrefixExpression -> {
                translate(expr.expression)
                translatePrefixOperator(expr.operator, expr.expression.resultingDatatype(namespace, heap))
            }
            is BinaryExpression -> {
                checkForFloatPrecisionProblem(expr.left, expr.right)
                translate(expr.left)
                translate(expr.right)
                translateBinaryOperator(expr.operator, expr.left.resultingDatatype(namespace, heap), expr.right.resultingDatatype(namespace, heap))
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
                        is Subroutine -> translateSubroutineCall(target, expr.arglist, expr.position)
                        else -> TODO("non-builtin-function call to $target")
                    }
                }
            }
            is IdentifierReference -> translate(expr)
            is ArrayIndexedExpression -> translate(expr, false)
            is RangeExpr -> {
                TODO("TRANSLATE range $expr")
            }
            else -> {
                val lv = expr.constValue(namespace, heap) ?: throw CompilerException("constant expression required, not $expr")
                when(lv.type) {
                    DataType.BYTE -> stackvmProg.instr(Opcode.PUSH, Value(DataType.BYTE, lv.bytevalue!!))
                    DataType.WORD -> stackvmProg.instr(Opcode.PUSH_W, Value(DataType.WORD, lv.wordvalue!!))
                    DataType.FLOAT -> stackvmProg.instr(Opcode.PUSH_F, Value(DataType.FLOAT, lv.floatvalue!!))
                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                        if(lv.heapId==null)
                            throw CompilerException("string should have been moved into heap   ${lv.position}")
                        stackvmProg.instr(Opcode.PUSH_W, Value(lv.type, lv.heapId))
                    }
                    DataType.ARRAY, DataType.ARRAY_W, DataType.ARRAY_F, DataType.MATRIX -> {
                        if(lv.heapId==null)
                            throw CompilerException("array/matrix should have been moved into heap  ${lv.position}")
                        stackvmProg.instr(Opcode.PUSH_W, Value(lv.type, lv.heapId))
                    }
                }
            }
        }
    }

    private fun translate(identifierRef: IdentifierReference) {
        val target = identifierRef.targetStatement(namespace)
        when (target) {
            is VarDecl -> {
                when (target.type) {
                    VarDeclType.VAR -> {
                        val opcode = opcodePushvar(target.datatype)
                        stackvmProg.instr(opcode, callLabel = target.scopedname)
                    }
                    VarDeclType.CONST ->
                        throw CompilerException("const ref should have been const-folded away")
                    VarDeclType.MEMORY -> {
                        when (target.datatype) {
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
            is Subroutine -> {
                translateSubroutineCall(targetStmt, stmt.arglist, stmt.position)
                // make sure we clean up the unused result values from the stack.
                for(rv in targetStmt.returnvalues) {
                    val opcode=opcodeDiscard(rv)
                    stackvmProg.instr(opcode)
                }
            }
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
            "lsl" -> {
                val arg = args.single()
                when (arg.resultingDatatype(namespace, heap)) {
                    DataType.BYTE -> stackvmProg.instr(Opcode.SHL)
                    DataType.WORD -> stackvmProg.instr(Opcode.SHL_W)
                    else -> throw CompilerException("wrong datatype")
                }
            }
            "lsr" -> {
                val arg = args.single()
                when (arg.resultingDatatype(namespace, heap)) {
                    DataType.BYTE -> stackvmProg.instr(Opcode.SHR)
                    DataType.WORD -> stackvmProg.instr(Opcode.SHR_W)
                    else -> throw CompilerException("wrong datatype")
                }
            }
            "rol" -> {
                val arg = args.single()
                when (arg.resultingDatatype(namespace, heap)) {
                    DataType.BYTE -> stackvmProg.instr(Opcode.ROL)
                    DataType.WORD -> stackvmProg.instr(Opcode.ROL_W)
                    else -> throw CompilerException("wrong datatype")
                }
            }
            "ror" -> {
                val arg = args.single()
                when (arg.resultingDatatype(namespace, heap)) {
                    DataType.BYTE -> stackvmProg.instr(Opcode.ROR)
                    DataType.WORD -> stackvmProg.instr(Opcode.ROR_W)
                    else -> throw CompilerException("wrong datatype")
                }
            }
            "rol2" -> {
                val arg = args.single()
                when (arg.resultingDatatype(namespace, heap)) {
                    DataType.BYTE -> stackvmProg.instr(Opcode.ROL2)
                    DataType.WORD -> stackvmProg.instr(Opcode.ROL2_W)
                    else -> throw CompilerException("wrong datatype")
                }
            }
            "ror2" -> {
                val arg = args.single()
                when (arg.resultingDatatype(namespace, heap)) {
                    DataType.BYTE -> stackvmProg.instr(Opcode.ROR2)
                    DataType.WORD -> stackvmProg.instr(Opcode.ROR2_W)
                    else -> throw CompilerException("wrong datatype")
                }
            }
            "set_carry" -> stackvmProg.instr(Opcode.SEC)
            "clear_carry" -> stackvmProg.instr(Opcode.CLC)
            "set_irqd" -> stackvmProg.instr(Opcode.SEI)
            "clear_irqd" -> stackvmProg.instr(Opcode.CLI)
            else -> createSyscall(funcname)  // call builtin function
        }
    }

    private fun translateSubroutineCall(subroutine: Subroutine, arguments: List<IExpression>, callPosition: Position) {
        // evaluate the arguments and assign them into the subroutine's argument variables.
        stackvmProg.line(callPosition)
        for(arg in arguments.zip(subroutine.parameters)) {
            translate(arg.first)
            val opcode=opcodePopvar(arg.second.type)
            stackvmProg.instr(opcode, callLabel = subroutine.scopedname+"."+arg.second.name)
        }
        stackvmProg.instr(Opcode.CALL, callLabel=subroutine.scopedname)
    }

    private fun translateBinaryOperator(operator: String, leftDt: DataType?, rightDt: DataType?) {
        if(leftDt==null || rightDt==null)
            throw CompilerException("left and/or right operand datatype not known")
        val validDt = setOf(DataType.BYTE, DataType.WORD, DataType.FLOAT)
        if(leftDt !in validDt || rightDt !in validDt)
            throw CompilerException("invalid datatype(s) for operand(s)")
        if(leftDt!=rightDt)
            throw CompilerException("operands have different datatypes")
        val opcode = when(operator) {
            // todo variants depending on leftdt/rightdt (b/w/f)
            "+" -> Opcode.ADD
            "-" -> Opcode.SUB
            "*" -> Opcode.MUL
            "/" -> Opcode.DIV
            "//" -> Opcode.FLOORDIV
            "%" -> Opcode.REMAINDER
            "**" -> Opcode.POW
            "&" -> {
                when(leftDt) {
                    DataType.BYTE -> Opcode.BITAND
                    DataType.WORD -> Opcode.BITAND_W
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "|" -> {
                when(leftDt) {
                    DataType.BYTE -> Opcode.BITOR
                    DataType.WORD -> Opcode.BITOR_W
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "^" -> {
                when(leftDt) {
                    DataType.BYTE -> Opcode.BITXOR
                    DataType.WORD -> Opcode.BITXOR_W
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "and" -> {
                when(leftDt) {
                    DataType.BYTE -> Opcode.AND
                    DataType.WORD -> Opcode.AND_W
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "or" -> {
                when(leftDt) {
                    DataType.BYTE -> Opcode.OR
                    DataType.WORD -> Opcode.OR_W
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "xor" -> {
                when(leftDt) {
                    DataType.BYTE -> Opcode.XOR
                    DataType.WORD -> Opcode.XOR_W
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "<" -> {
                when(leftDt) {
                    DataType.BYTE -> Opcode.LESS
                    DataType.WORD -> Opcode.LESS_W
                    DataType.FLOAT -> Opcode.LESS_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            ">" -> {
                when(leftDt) {
                    DataType.BYTE -> Opcode.GREATER
                    DataType.WORD -> Opcode.GREATER_W
                    DataType.FLOAT -> Opcode.GREATER_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            "<=" -> {
                when(leftDt) {
                    DataType.BYTE -> Opcode.LESSEQ
                    DataType.WORD -> Opcode.LESSEQ_W
                    DataType.FLOAT -> Opcode.LESSEQ_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            ">=" -> {
                when(leftDt) {
                    DataType.BYTE -> Opcode.GREATEREQ
                    DataType.WORD -> Opcode.GREATEREQ_W
                    DataType.FLOAT -> Opcode.GREATEREQ_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            "==" -> {
                when (leftDt) {
                    DataType.BYTE -> Opcode.EQUAL
                    DataType.WORD -> Opcode.EQUAL_W
                    DataType.FLOAT -> Opcode.EQUAL_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            "!=" -> {
                when (leftDt) {
                    DataType.BYTE -> Opcode.NOTEQUAL
                    DataType.WORD -> Opcode.NOTEQUAL_W
                    DataType.FLOAT -> Opcode.NOTEQUAL_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            else -> throw FatalAstException("const evaluation for invalid operator $operator")
        }
        stackvmProg.instr(opcode)
    }

    private fun translatePrefixOperator(operator: String, operandDt: DataType?) {
        if(operandDt==null)
            throw CompilerException("operand datatype not known")
        val opcode = when(operator) {
            "+" -> Opcode.NOP
            "-" -> Opcode.NEG       // todo b/w/f
            "~" -> {
                when(operandDt) {
                    DataType.BYTE -> Opcode.INV
                    DataType.WORD -> Opcode.INV_W
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "not" -> {
                when(operandDt) {
                    DataType.BYTE -> Opcode.NOT
                    DataType.WORD -> Opcode.NOT_W
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            else -> throw FatalAstException("const evaluation for invalid prefix operator $operator")
        }
        stackvmProg.instr(opcode)
    }

    private fun translate(arrayindexed: ArrayIndexedExpression, write: Boolean) {
        val variable = arrayindexed.identifier?.targetStatement(namespace) as? VarDecl
        val variableName =
                if(arrayindexed.register!=null) {
                    val reg=arrayindexed.register
                    if(reg==Register.A || reg==Register.X || reg==Register.Y)
                        throw CompilerException("requires register pair")
                    if(arrayindexed.array.y!=null)
                        throw CompilerException("when using an address, can only use one index dimension")
                    reg.toString()
                } else {
                    variable!!.scopedname
                }
        translate(arrayindexed.array.x)
        val y = arrayindexed.array.y
        if(y!=null) {
            // calc matrix index  i=y*columns+x
            // (the const-folding will have removed this for us when both x and y are constants)
            translate(y)
            stackvmProg.instr(Opcode.PUSH, Value(DataType.BYTE, (variable!!.arrayspec!!.x as LiteralValue).asIntegerValue!!))
            stackvmProg.instr(Opcode.MUL)
            stackvmProg.instr(Opcode.ADD)
        }

        if(write)
            stackvmProg.instr(opcodeWriteindexedvar(variable!!.datatype), callLabel = variableName)
        else
            stackvmProg.instr(opcodeReadindexedvar(variable!!.datatype), callLabel = variableName)
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
        when {
            stmt.target.register!=null -> when(stmt.operator) {
                "++" -> stackvmProg.instr(opcodeIncvar(stmt.target.register!!), callLabel = stmt.target.register.toString())
                "--" -> stackvmProg.instr(opcodeDecvar(stmt.target.register!!), callLabel = stmt.target.register.toString())
            }
            stmt.target.identifier!=null -> {
                val targetStatement = stmt.target.identifier!!.targetStatement(namespace) as VarDecl
                when(stmt.operator) {
                    "++" -> stackvmProg.instr(opcodeIncvar(targetStatement.datatype), callLabel = targetStatement.scopedname)
                    "--" -> stackvmProg.instr(opcodeDecvar(targetStatement.datatype), callLabel = targetStatement.scopedname)
                }
            }
            stmt.target.arrayindexed!=null -> {
                // todo: generate more efficient bytecode for this?
                translate(stmt.target.arrayindexed!!, false)
                val one = Value(stmt.target.arrayindexed!!.resultingDatatype(namespace, heap)!!, 1)
                val opcode = opcodePush(one.type)
                stackvmProg.instr(opcode, one)
                when(stmt.operator) {
                    "++" -> stackvmProg.instr(Opcode.ADD)
                    "--" -> stackvmProg.instr(Opcode.SUB)
                }
                translate(stmt.target.arrayindexed!!, true)
            }
            else -> throw CompilerException("very strange postincrdecr")
        }
    }

    private fun translate(stmt: Assignment) {
        stackvmProg.line(stmt.position)
        translate(stmt.value)
        val valueDt = stmt.value.resultingDatatype(namespace, heap)
        val targetDt = stmt.target.determineDatatype(namespace, heap, stmt)
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
                DataType.ARRAY, DataType.ARRAY_W, DataType.ARRAY_F, DataType.MATRIX -> throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                // todo: maybe if you assign byte or word to array/matrix, clear it with that value?
            }
        }

        if(stmt.aug_op!=null) {
            // augmented assignment
            when {
                stmt.target.identifier!=null -> {
                    val target = stmt.target.identifier!!.targetStatement(namespace)!!
                    when(target) {
                        is VarDecl -> {
                            val opcode = opcodePushvar(stmt.target.determineDatatype(namespace, heap, stmt))
                            stackvmProg.instr(opcode, callLabel = target.scopedname)
                        }
                        else -> throw CompilerException("invalid assignment target type ${target::class}")
                    }
                }
                stmt.target.register!=null -> {
                    val opcode= opcodePushvar(stmt.target.register!!)
                    stackvmProg.instr(opcode, callLabel = stmt.target.register.toString())
                }
                stmt.target.arrayindexed!=null -> translate(stmt.target.arrayindexed!!, false)
            }

            translateAugAssignOperator(stmt.aug_op, stmt.value.resultingDatatype(namespace, heap))
        }

        // pop the result value back into the assignment target
        when {
            stmt.target.identifier!=null -> {
                val target = stmt.target.identifier!!.targetStatement(namespace)!!
                when(target) {
                    is VarDecl -> {
                        val opcode = opcodePopvar(stmt.target.determineDatatype(namespace, heap, stmt))
                        stackvmProg.instr(opcode, callLabel =  target.scopedname)
                    }
                    else -> throw CompilerException("invalid assignment target type ${target::class}")
                }
            }
            stmt.target.register!=null -> {
                val opcode=opcodePopvar(stmt.target.register!!)
                stackvmProg.instr(opcode, callLabel = stmt.target.register.toString())
            }
            stmt.target.arrayindexed!=null -> translate(stmt.target.arrayindexed!!, true)     // write value to it
        }
    }

    private fun translateAugAssignOperator(aug_op: String, valueDt: DataType?) {
        if(valueDt==null)
            throw CompilerException("value datatype not known")
        val validDt = setOf(DataType.BYTE, DataType.WORD, DataType.FLOAT)
        if(valueDt !in validDt)
            throw CompilerException("invalid datatype(s) for operand(s)")
        val opcode = when(aug_op) {
            "+=" -> Opcode.ADD
            "-=" -> Opcode.SUB
            "/=" -> Opcode.DIV
            "//=" -> Opcode.FLOORDIV
            "*=" -> Opcode.MUL
            "**=" -> Opcode.POW
            "&=" -> {
                when(valueDt) {
                    DataType.BYTE -> Opcode.BITAND
                    DataType.WORD -> Opcode.BITAND_W
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "|=" -> {
                when(valueDt) {
                    DataType.BYTE -> Opcode.BITOR
                    DataType.WORD -> Opcode.BITOR_W
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "^=" -> {
                when(valueDt) {
                    DataType.BYTE -> Opcode.BITXOR
                    DataType.WORD -> Opcode.BITXOR_W
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            else -> throw CompilerException("invalid aug assignment operator $aug_op")
        }
        stackvmProg.instr(opcode)
    }

    private fun translate(stmt: Return) {
        // put the return values on the stack, in reversed order. The caller will process them.
        for(value in stmt.values.reversed()) {
            translate(value)
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
            // ok, must be a literalvalue
            val iterableValue: LiteralValue
            when {
                loop.iterable is LiteralValue -> {
                    TODO("loop over literal value (move literal to auto-generated heap variable)")
                }
                loop.iterable is IdentifierReference -> {
                    val idRef = loop.iterable as IdentifierReference
                    val vardecl = (idRef.targetStatement(namespace) as VarDecl)
                    iterableValue = vardecl.value as LiteralValue
                    if(!iterableValue.isIterable(namespace, heap))
                        throw CompilerException("loop over something that isn't iterable ${loop.iterable}")
                }
                else -> throw CompilerException("loopvar is something strange ${loop.iterable}")
            }
            translateForOverIterableVar(loop, loopVarDt, iterableValue)
        }
    }

    private fun translateForOverIterableVar(loop: ForLoop, loopvarDt: DataType, iterableValue: LiteralValue) {
        if(loopvarDt==DataType.BYTE && iterableValue.type !in setOf(DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS, DataType.ARRAY, DataType.MATRIX))
            throw CompilerException("loop variable type doesn't match iterableValue type")
        else if(loopvarDt==DataType.WORD && iterableValue.type != DataType.ARRAY_W)
            throw CompilerException("loop variable type doesn't match iterableValue type")
        else if(loopvarDt==DataType.FLOAT && iterableValue.type != DataType.ARRAY_F)
            throw CompilerException("loop variable type doesn't match iterableValue type")
        val numElements: Int
        val indexVar: String
        when(iterableValue.type) {
            DataType.BYTE,
            DataType.WORD,
            DataType.FLOAT -> throw CompilerException("non-iterableValue type")
            DataType.STR,
            DataType.STR_P,
            DataType.STR_S,
            DataType.STR_PS -> {
                numElements = iterableValue.strvalue?.length ?: heap.get(iterableValue.heapId!!).str!!.length
                indexVar = if(numElements>255) "XY" else "X"
            }
            DataType.ARRAY,
            DataType.ARRAY_W,
            DataType.MATRIX -> {
                numElements = iterableValue.arrayvalue?.size ?: heap.get(iterableValue.heapId!!).array!!.size
                indexVar = if(numElements>255) "XY" else "X"
            }
            DataType.ARRAY_F -> {
                numElements = iterableValue.arrayvalue?.size ?: heap.get(iterableValue.heapId!!).doubleArray!!.size
                indexVar = if(numElements>255) "XY" else "X"
            }
        }

        if(indexVar=="X" && loop.loopRegister!=null && loop.loopRegister in setOf(Register.X, Register.AX, Register.XY))
            throw CompilerException("loopVar cannot use X register because it is needed as internal index")
        if(indexVar=="XY" && loop.loopRegister!=null && loop.loopRegister in setOf(Register.X, Register.AX, Register.Y, Register.AY, Register.XY))
            throw CompilerException("loopVar cannot use X and Y registers because they are needed as internal index")

        /**
         *      indexVar = 0
         * loop:
         *      LV = iterableValue[indexVar]
         *      ..body..
         *      ..break statement:  goto break
         *      ..continue statement: goto continue
         *      ..
         * continue:
         *      IV++
         *      if IV!=numElements goto loop
         * break:
         *      nop
         */
        val loopLabel = makeLabel("loop")
        val continueLabel = makeLabel("continue")
        val breakLabel = makeLabel("break")

        continueStmtLabelStack.push(continueLabel)
        breakStmtLabelStack.push(breakLabel)

        val zero = Value(if(numElements<=255) DataType.BYTE else DataType.WORD, 0)
        stackvmProg.instr(opcodePush(zero.type), zero)
        stackvmProg.instr(opcodePopvar(zero.type), callLabel = indexVar)
        stackvmProg.label(loopLabel)
        val assignTarget = if(loop.loopRegister!=null)
            AssignTarget(loop.loopRegister, null, null, loop.position)
        else
            AssignTarget(null, loop.loopVar, null, loop.position)
        val arrayspec = ArraySpec(RegisterExpr(Register.valueOf(indexVar), loop.position), null, loop.position)
        val assignLv = Assignment(assignTarget, null, ArrayIndexedExpression(loop.iterable as IdentifierReference, null, arrayspec, loop.position), loop.position)
        assignLv.linkParents(loop.parent)
        translate(assignLv)
        translate(loop.body)
        stackvmProg.label(continueLabel)
        stackvmProg.instr(opcodeIncvar(zero.type), callLabel = indexVar)

        // TODO: optimize edge cases if last value = 255 or 0 (for bytes) etc. to avoid  PUSH / SUB opcodes and make use of the wrapping around of the value.
        stackvmProg.instr(opcodePush(zero.type), Value(zero.type, numElements))
        stackvmProg.instr(opcodePushvar(zero.type), callLabel = indexVar)
        stackvmProg.instr(Opcode.SUB)
        stackvmProg.instr(Opcode.BNZ, callLabel = loopLabel)

        stackvmProg.label(breakLabel)
        stackvmProg.instr(Opcode.NOP)

        breakStmtLabelStack.pop()
        continueStmtLabelStack.pop()
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

        stackvmProg.instr(opcodePush(varDt), Value(varDt, range.first))
        stackvmProg.instr(opcodePopvar(varDt), callLabel = varname)
        stackvmProg.label(loopLabel)
        translate(body)
        stackvmProg.label(continueLabel)
        when {
            range.step==1 -> stackvmProg.instr(opcodeIncvar(varDt), callLabel = varname)
            range.step==-1 -> stackvmProg.instr(opcodeDecvar(varDt), callLabel = varname)
            range.step>1 -> {
                stackvmProg.instr(opcodePushvar(varDt), callLabel = varname)
                stackvmProg.instr(opcodePush(varDt), Value(varDt, range.step))
                stackvmProg.instr(Opcode.ADD)
                stackvmProg.instr(opcodePopvar(varDt), callLabel = varname)
            }
            range.step<1 -> {
                stackvmProg.instr(opcodePushvar(varDt), callLabel = varname)
                stackvmProg.instr(opcodePush(varDt), Value(varDt, abs(range.step)))
                stackvmProg.instr(Opcode.SUB)
                stackvmProg.instr(opcodePopvar(varDt), callLabel = varname)
            }
        }

        // TODO: optimize edge cases if last value = 255 or 0 (for bytes) etc. to avoid  PUSH / SUB opcodes and make use of the wrapping around of the value.
        stackvmProg.instr(opcodePush(varDt), Value(varDt, range.last+range.step))
        stackvmProg.instr(opcodePushvar(varDt), callLabel = varname)
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
                AssignTarget(null, IdentifierReference(varname, range.position), null, range.position)
            else
                AssignTarget(register, null, null, range.position)
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
                // todo deal with target.arrayindexed?


        when (literalStepValue) {
            1 -> {
                // LV++
                val postIncr = PostIncrDecr(lvTarget, "++", range.position)
                postIncr.linkParents(range.parent)
                translate(postIncr)
                if(lvTarget.register!=null)
                    stackvmProg.instr(opcodePushvar(lvTarget.register), callLabel =lvTarget.register.toString())
                else {
                    val opcode = opcodePushvar(targetStatement!!.datatype)
                    stackvmProg.instr(opcode, callLabel = targetStatement.scopedname)
                }
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
