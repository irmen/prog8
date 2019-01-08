package prog8.compiler

import prog8.ast.*
import prog8.ast.RegisterOrPair.*
import prog8.compiler.intermediate.IntermediateProgram
import prog8.compiler.intermediate.Opcode
import prog8.compiler.intermediate.Value
import prog8.stackvm.Syscall
import java.util.*
import kotlin.math.abs


class CompilerException(message: String?) : Exception(message)


fun Number.toHex(): String {
    //  0..15 -> "0".."15"
    //  16..255 -> "$10".."$ff"
    //  256..65536 -> "$0100".."$ffff"
    // negative values are prefixed with '-'.
    val integer = this.toInt()
    if(integer<0)
        return '-' + abs(integer).toHex()
    return when (integer) {
        in 0 until 16 -> integer.toString()
        in 0 until 0x100 -> "$"+integer.toString(16).padStart(2,'0')
        in 0 until 0x10000 -> "$"+integer.toString(16).padStart(4,'0')
        else -> throw CompilerException("number too large for 16 bits $this")
    }
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

        val arraysize: Int = array?.size ?: doubleArray?.size ?: 0
    }

    private val heap = mutableListOf<HeapValue>()

    fun size(): Int = heap.size

    fun add(type: DataType, str: String): Int {
        if (str.length > 255)
            throw IllegalArgumentException("string length must be 0-255")

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

    fun allEntries() = heap.withIndex().toList()
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
                              val zpReserved: List<IntRange>,
                              val floats: Boolean)


class Compiler(private val options: CompilationOptions) {
    fun compile(module: Module, heap: HeapValues) : IntermediateProgram {
        println("\nCreating stackVM code...")

        val namespace = module.definingScope()
        val program = IntermediateProgram(module.name, module.loadAddress, heap)

        val translator = StatementTranslator(program, namespace, heap)
        translator.process(module)

        return program
    }
}

private class StatementTranslator(private val prog: IntermediateProgram,
                                  private val namespace: INameScope,
                                  private val heap: HeapValues): IAstProcessor {
    var generatedLabelSequenceNumber = 0
        private set

    val breakStmtLabelStack : Stack<String> = Stack()
    val continueStmtLabelStack : Stack<String> = Stack()

    override fun process(block: Block): IStatement {
        prog.newBlock(block.scopedname, block.name, block.address, block.options())
        processVariables(block)         // @todo optimize initializations with same value: load the value only once  (sort on initalization value, datatype   ?)
        prog.label("block."+block.scopedname, false)
        prog.line(block.position)
        translate(block.statements)
        return super.process(block)
    }

    private fun processVariables(scope: INameScope) {
        for(variable in scope.statements.filterIsInstance<VarDecl>())
            prog.variable(variable.scopedname, variable)
        for(subscope in scope.subScopes())
            processVariables(subscope.value)
    }

    override fun process(subroutine: Subroutine): IStatement {
        if(subroutine.asmAddress==null) {
            prog.label(subroutine.scopedname, true)
            prog.instr(Opcode.START_PROCDEF)
            prog.line(subroutine.position)
            // note: the caller has already written the arguments into the subroutine's parameter variables.
            translate(subroutine.statements)
            val r= super.process(subroutine)
            prog.instr(Opcode.END_PROCDEF)
            return r
        } else {
            // asmsub
            if(subroutine.isNotEmpty())
                throw CompilerException("kernel subroutines (with memory address) can't have a body: $subroutine")

            prog.memoryPointer(subroutine.scopedname, subroutine.asmAddress, DataType.UBYTE)        // the datatype is a bit of a dummy in this case
            return super.process(subroutine)
        }
    }

    private fun translate(statements: List<IStatement>) {
        for (stmt: IStatement in statements) {
            generatedLabelSequenceNumber++
            when (stmt) {
                is Label -> translate(stmt)
                is VariableInitializationAssignment -> translate(stmt)        // for initializing vars in a scope
                is Assignment -> translate(stmt)        // normal and augmented assignments
                is PostIncrDecr -> translate(stmt)
                is Jump -> translate(stmt, null)
                is FunctionCallStatement -> translate(stmt)
                is IfStatement -> translate(stmt)
                is BranchStatement -> translate(stmt)
                is Break -> translate(stmt)
                is Continue -> translate(stmt)
                is ForLoop -> translate(stmt)
                is WhileLoop -> translate(stmt)
                is RepeatLoop -> translate(stmt)
                is AnonymousScope -> translate(stmt)
                is ReturnFromIrq -> translate(stmt)
                is Return -> translate(stmt)
                is Directive -> {
                    when(stmt.directive) {
                        "%asminclude" -> throw CompilerException("can't use %asminclude in stackvm")
                        "%asmbinary" -> throw CompilerException("can't use %asmbinary in stackvm")
                        "%breakpoint" -> {
                            prog.line(stmt.position)
                            prog.instr(Opcode.BREAKPOINT)
                        }
                    }
                }
                is VarDecl, is Subroutine -> {}   // skip this, already processed these.
                is NopStatement -> {}
                is InlineAssembly -> translate(stmt)
                else -> TODO("translate statement $stmt to stackvm")
            }
        }
    }

    private fun opcodePush(dt: DataType): Opcode {
        return when (dt) {
            DataType.UBYTE, DataType.BYTE -> Opcode.PUSH_BYTE
            DataType.UWORD, DataType.WORD -> Opcode.PUSH_WORD
            DataType.FLOAT -> Opcode.PUSH_FLOAT
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS,
            DataType.ARRAY_UB, DataType.ARRAY_UW, DataType.ARRAY_F,
            DataType.ARRAY_B, DataType.ARRAY_W -> Opcode.PUSH_WORD
        }
    }

    private fun opcodeAdd(dt: DataType): Opcode {
        return when (dt) {
            DataType.UBYTE -> Opcode.ADD_UB
            DataType.BYTE -> Opcode.ADD_B
            DataType.UWORD -> Opcode.ADD_UW
            DataType.WORD -> Opcode.ADD_W
            DataType.FLOAT -> Opcode.ADD_F
            else -> throw CompilerException("invalid dt $dt")
        }
    }

    private fun opcodeSub(dt: DataType): Opcode {
        return when (dt) {
            DataType.UBYTE -> Opcode.SUB_UB
            DataType.BYTE -> Opcode.SUB_B
            DataType.UWORD -> Opcode.SUB_UW
            DataType.WORD -> Opcode.SUB_W
            DataType.FLOAT -> Opcode.SUB_F
            else -> throw CompilerException("invalid dt $dt")
        }
    }

    private fun opcodePushvar(dt: DataType): Opcode {
        return when (dt)  {
            DataType.UBYTE, DataType.BYTE -> Opcode.PUSH_VAR_BYTE
            DataType.UWORD, DataType.WORD -> Opcode.PUSH_VAR_WORD
            DataType.FLOAT -> Opcode.PUSH_VAR_FLOAT
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS,
            DataType.ARRAY_UB, DataType.ARRAY_UW, DataType.ARRAY_F,
            DataType.ARRAY_B, DataType.ARRAY_W -> Opcode.PUSH_ADDR_HEAPVAR
        }
    }

    private fun opcodeReadindexedvar(dt: DataType): Opcode {
        return when (dt)  {
            DataType.ARRAY_UB, DataType.ARRAY_B -> Opcode.READ_INDEXED_VAR_BYTE
            DataType.ARRAY_UW, DataType.ARRAY_W -> Opcode.READ_INDEXED_VAR_WORD
            DataType.ARRAY_F -> Opcode.READ_INDEXED_VAR_FLOAT
            DataType.STR, DataType.STR_S -> Opcode.READ_INDEXED_VAR_BYTE
            DataType.STR_P, DataType.STR_PS -> throw CompilerException("cannot index on type $dt - use regular 0-terminated str type")
            else -> throw CompilerException("invalid dt for indexed access $dt")
        }
    }

    private fun opcodeWriteindexedvar(dt: DataType): Opcode {
        return when (dt)  {
            DataType.ARRAY_UB, DataType.ARRAY_B -> Opcode.WRITE_INDEXED_VAR_BYTE
            DataType.ARRAY_UW, DataType.ARRAY_W -> Opcode.WRITE_INDEXED_VAR_WORD
            DataType.ARRAY_F -> Opcode.WRITE_INDEXED_VAR_FLOAT
            DataType.STR, DataType.STR_S -> Opcode.WRITE_INDEXED_VAR_BYTE
            DataType.STR_P, DataType.STR_PS -> throw CompilerException("cannot index on type $dt - use regular 0-terminated str type")
            else -> throw CompilerException("invalid dt for indexed access $dt")
        }
    }

    private fun opcodeDiscard(dt: DataType): Opcode {
        return when(dt) {
            DataType.UBYTE, DataType.BYTE -> Opcode.DISCARD_BYTE
            DataType.UWORD, DataType.WORD -> Opcode.DISCARD_WORD
            DataType.FLOAT -> Opcode.DISCARD_FLOAT
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS,
            DataType.ARRAY_UB, DataType.ARRAY_UW, DataType.ARRAY_F,
            DataType.ARRAY_B, DataType.ARRAY_W -> Opcode.DISCARD_WORD
        }
    }

    private fun opcodePopvar(dt: DataType): Opcode {
        return when (dt) {
            DataType.UBYTE, DataType.BYTE -> Opcode.POP_VAR_BYTE
            DataType.UWORD, DataType.WORD -> Opcode.POP_VAR_WORD
            DataType.FLOAT -> Opcode.POP_VAR_FLOAT
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS,
            DataType.ARRAY_UB, DataType.ARRAY_UW, DataType.ARRAY_F,
            DataType.ARRAY_B, DataType.ARRAY_W -> Opcode.POP_VAR_WORD
        }
    }

    private fun opcodePopmem(dt: DataType): Opcode {
        return when (dt) {
            DataType.UBYTE, DataType.BYTE -> Opcode.POP_MEM_BYTE
            DataType.UWORD, DataType.WORD -> Opcode.POP_MEM_WORD
            DataType.FLOAT -> Opcode.POP_MEM_FLOAT
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS,
            DataType.ARRAY_UB, DataType.ARRAY_UW, DataType.ARRAY_F,
            DataType.ARRAY_B, DataType.ARRAY_W -> Opcode.POP_MEM_WORD
        }
    }

    private fun opcodeDecvar(dt: DataType): Opcode {
        return when(dt) {
            DataType.UBYTE -> Opcode.DEC_VAR_UB
            DataType.BYTE -> Opcode.DEC_VAR_B
            DataType.UWORD -> Opcode.DEC_VAR_UW
            DataType.WORD -> Opcode.DEC_VAR_W
            DataType.FLOAT -> Opcode.DEC_VAR_F
            else -> throw CompilerException("can't dec type $dt")
        }
    }

    private fun opcodeIncvar(dt: DataType): Opcode {
        return when(dt) {
            DataType.UBYTE -> Opcode.INC_VAR_UB
            DataType.BYTE -> Opcode.INC_VAR_B
            DataType.UWORD -> Opcode.INC_VAR_UW
            DataType.WORD -> Opcode.INC_VAR_W
            DataType.FLOAT -> Opcode.INC_VAR_F
            else -> throw CompilerException("can't inc type $dt")
        }
    }

    private fun opcodeIncArrayindexedVar(dt: DataType): Opcode {
        return when(dt) {
            DataType.ARRAY_UB -> Opcode.INC_INDEXED_VAR_UB
            DataType.ARRAY_B -> Opcode.INC_INDEXED_VAR_B
            DataType.ARRAY_UW -> Opcode.INC_INDEXED_VAR_UW
            DataType.ARRAY_W -> Opcode.INC_INDEXED_VAR_W
            DataType.ARRAY_F -> Opcode.INC_INDEXED_VAR_FLOAT
            else -> throw CompilerException("can't inc type $dt")
        }
    }

    private fun opcodeDecArrayindexedVar(dt: DataType): Opcode {
        return when(dt) {
            DataType.ARRAY_UB -> Opcode.DEC_INDEXED_VAR_UB
            DataType.ARRAY_B -> Opcode.DEC_INDEXED_VAR_B
            DataType.ARRAY_UW -> Opcode.DEC_INDEXED_VAR_UW
            DataType.ARRAY_W -> Opcode.DEC_INDEXED_VAR_W
            DataType.ARRAY_F -> Opcode.DEC_INDEXED_VAR_FLOAT
            else -> throw CompilerException("can't dec type $dt")
        }
    }

    private fun translate(stmt: InlineAssembly) {
        prog.instr(Opcode.INLINE_ASSEMBLY, callLabel = stmt.assembly)
    }

    private fun translate(stmt: Continue) {
        prog.line(stmt.position)
        if(continueStmtLabelStack.empty())
            throw CompilerException("continue outside of loop statement block")
        val label = continueStmtLabelStack.peek()
        prog.instr(Opcode.JUMP, null, label)
    }

    private fun translate(stmt: Break) {
        prog.line(stmt.position)
        if(breakStmtLabelStack.empty())
            throw CompilerException("break outside of loop statement block")
        val label = breakStmtLabelStack.peek()
        prog.instr(Opcode.JUMP, null, label)
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
         * if the branch statement just contains jumps, more efficient code is generated.
         * (just the appropriate branching instruction is outputted!)
         */
        if(branch.elsepart.isEmpty() && branch.truepart.isEmpty())
            return

        fun branchOpcode(branch: BranchStatement, complement: Boolean) =
            if(complement) {
                when (branch.condition) {
                    BranchCondition.CS -> Opcode.BCC
                    BranchCondition.CC -> Opcode.BCS
                    BranchCondition.EQ, BranchCondition.Z -> Opcode.BNZ
                    BranchCondition.NE, BranchCondition.NZ -> Opcode.BZ
                    BranchCondition.VS -> Opcode.BVC
                    BranchCondition.VC -> Opcode.BVS
                    BranchCondition.MI, BranchCondition.NEG -> Opcode.BPOS
                    BranchCondition.PL, BranchCondition.POS -> Opcode.BNEG
                }
            } else {
                when (branch.condition) {
                    BranchCondition.CS -> Opcode.BCS
                    BranchCondition.CC -> Opcode.BCC
                    BranchCondition.EQ, BranchCondition.Z -> Opcode.BZ
                    BranchCondition.NE, BranchCondition.NZ -> Opcode.BNZ
                    BranchCondition.VS -> Opcode.BVS
                    BranchCondition.VC -> Opcode.BVC
                    BranchCondition.MI, BranchCondition.NEG -> Opcode.BNEG
                    BranchCondition.PL, BranchCondition.POS -> Opcode.BPOS
                }
            }

        prog.line(branch.position)
        val truejump = branch.truepart.statements.first()
        val elsejump = branch.elsepart.statements.firstOrNull()
        if(truejump is Jump && truejump.address==null && (elsejump ==null || (elsejump is Jump && elsejump.address==null))) {
            // optimized code for just conditional jumping
            val opcodeTrue = branchOpcode(branch, false)
            translate(truejump, opcodeTrue)
            if(elsejump is Jump) {
                val opcodeFalse = branchOpcode(branch, true)
                translate(elsejump, opcodeFalse)
            }
        } else {
            // regular if..else branching
            val labelElse = makeLabel("else")
            val labelEnd = makeLabel("end")
            val opcode = branchOpcode(branch, true)
            if (branch.elsepart.isEmpty()) {
                prog.instr(opcode, callLabel = labelEnd)
                translate(branch.truepart)
                prog.label(labelEnd)
            } else {
                prog.instr(opcode, callLabel = labelElse)
                translate(branch.truepart)
                prog.instr(Opcode.JUMP, callLabel = labelEnd)
                prog.label(labelElse)
                translate(branch.elsepart)
                prog.label(labelEnd)
            }
            prog.instr(Opcode.NOP)
        }
    }

    private fun makeLabel(postfix: String): String {
        generatedLabelSequenceNumber++
        return "_prog8stmt_${generatedLabelSequenceNumber}_$postfix"
    }

    private fun translate(stmt: IfStatement) {
        /*
         * An IF statement: IF (condition-expression) { stuff } else { other_stuff }
         * Which is translated into:
         *      <condition-expression evaluation>
         *      JZ/JZW _stmt_999_else
         *      stuff
         *      JUMP _stmt_999_end
         * _stmt_999_else:
         *      other_stuff     ;; optional
         * _stmt_999_end:
         *      nop
         *
         *  or when there is no else block:
         *      <condition-expression evaluation>
         *      JZ/JZW _stmt_999_end
         *      stuff
         * _stmt_999_end:
         *      nop
         *
         * @todo generate more efficient bytecode for the form with just jumps: if(..) goto .. [else goto ..]
         */
        prog.line(stmt.position)
        translate(stmt.condition)
        val conditionJumpOpcode = when(stmt.condition.resultingDatatype(namespace, heap)) {
            DataType.UBYTE, DataType.BYTE -> Opcode.JZ
            DataType.UWORD, DataType.WORD -> Opcode.JZW
            else -> throw CompilerException("invalid condition datatype (expected byte or word) $stmt")
        }
        val labelEnd = makeLabel("end")
        if(stmt.elsepart.isEmpty()) {
            prog.instr(conditionJumpOpcode, callLabel = labelEnd)
            translate(stmt.truepart)
            prog.label(labelEnd)
        } else {
            val labelElse = makeLabel("else")
            prog.instr(conditionJumpOpcode, callLabel = labelElse)
            translate(stmt.truepart)
            prog.instr(Opcode.JUMP, callLabel = labelEnd)
            prog.label(labelElse)
            translate(stmt.elsepart)
            prog.label(labelEnd)
        }
        prog.instr(Opcode.NOP)
    }

    private fun commonDatatype(leftDt: DataType, rightDt: DataType, leftpos: Position, rightpos: Position): DataType {
        // byte + byte -> byte
        // byte + word -> word
        // word + byte -> word
        // word + word -> word
        // a combination with a float will be float (but give a warning about this!)

        val floatWarning = "byte or word value implicitly converted to float. Suggestion: use explicit cast as float, a float number, or revert to integer arithmetic"

        return when(leftDt) {
            DataType.UBYTE -> {
                when(rightDt) {
                    DataType.UBYTE -> DataType.UBYTE
                    DataType.BYTE -> DataType.BYTE
                    DataType.UWORD -> DataType.UWORD
                    DataType.WORD -> DataType.WORD
                    DataType.FLOAT -> {
                        printWarning(floatWarning, leftpos)
                        DataType.FLOAT
                    }
                    else -> throw CompilerException("non-numeric datatype $rightDt")
                }
            }
            DataType.BYTE -> {
                when(rightDt) {
                    DataType.UBYTE, DataType.BYTE -> DataType.BYTE
                    DataType.UWORD, DataType.WORD -> DataType.WORD
                    DataType.FLOAT -> {
                        printWarning(floatWarning, leftpos)
                        DataType.FLOAT
                    }
                    else -> throw CompilerException("non-numeric datatype $rightDt")
                }
            }
            DataType.UWORD -> {
                when(rightDt) {
                    DataType.UBYTE, DataType.UWORD -> DataType.UWORD
                    DataType.BYTE, DataType.WORD -> DataType.WORD
                    DataType.FLOAT -> {
                        printWarning(floatWarning, leftpos)
                        DataType.FLOAT
                    }
                    else -> throw CompilerException("non-numeric datatype $rightDt")
                }
            }
            DataType.WORD -> {
                when(rightDt) {
                    DataType.UBYTE, DataType.UWORD, DataType.BYTE, DataType.WORD -> DataType.WORD
                    DataType.FLOAT -> {
                        printWarning(floatWarning, leftpos)
                        DataType.FLOAT
                    }
                    else -> throw CompilerException("non-numeric datatype $rightDt")
                }
            }
            DataType.FLOAT -> {
                if(rightDt!=DataType.FLOAT)
                    printWarning(floatWarning, rightpos)
                DataType.FLOAT
            }
            else -> throw CompilerException("non-numeric datatype $leftDt")
        }
    }

    private fun translate(expr: IExpression) {
        when(expr) {
            is RegisterExpr -> {
                prog.instr(Opcode.PUSH_VAR_BYTE, callLabel = expr.register.toString())
            }
            is PrefixExpression -> {
                translate(expr.expression)
                translatePrefixOperator(expr.operator, expr.expression.resultingDatatype(namespace, heap))
            }
            is BinaryExpression -> {
                val leftDt = expr.left.resultingDatatype(namespace, heap)!!
                val rightDt = expr.right.resultingDatatype(namespace, heap)!!
                val commonDt =
                        if(expr.operator=="/")
                            DataType.FLOAT   // result of division is always float
                        else
                            commonDatatype(leftDt, rightDt, expr.left.position, expr.right.position)
                translate(expr.left)
                if(leftDt!=commonDt)
                    convertType(leftDt, commonDt)
                translate(expr.right)
                if(rightDt!=commonDt)
                    convertType(rightDt, commonDt)
                if(expr.operator=="<<" || expr.operator==">>")
                    translateBitshiftedOperator(expr.operator, leftDt, expr.right.constValue(namespace, heap))
                else
                    translateBinaryOperator(expr.operator, commonDt)
            }
            is FunctionCall -> {
                val target = expr.target.targetStatement(namespace)
                if(target is BuiltinFunctionStatementPlaceholder) {
                    // call to a builtin function (some will just be an opcode!)
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
            is RangeExpr -> throw CompilerException("it's not possible to just have a range expression that has to be translated")
            is TypecastExpression -> translate(expr)
            is DirectMemoryRead -> translate(expr)
            is DirectMemoryWrite -> translate(expr)
            else -> {
                val lv = expr.constValue(namespace, heap) ?: throw CompilerException("constant expression required, not $expr")
                when(lv.type) {
                    DataType.UBYTE, DataType.BYTE -> prog.instr(Opcode.PUSH_BYTE, Value(lv.type, lv.bytevalue!!))
                    DataType.UWORD, DataType.WORD -> prog.instr(Opcode.PUSH_WORD, Value(lv.type, lv.wordvalue!!))
                    DataType.FLOAT -> prog.instr(Opcode.PUSH_FLOAT, Value(lv.type, lv.floatvalue!!))
                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                        if(lv.heapId==null)
                            throw CompilerException("string should have been moved into heap   ${lv.position}")
                        prog.instr(Opcode.PUSH_ADDR_HEAPVAR, callLabel = "@todo-string-varname?")    // XXX  push address of string
                    }
                    DataType.ARRAY_UB, DataType.ARRAY_UW, DataType.ARRAY_F,
                    DataType.ARRAY_B, DataType.ARRAY_W -> {
                        if(lv.heapId==null)
                            throw CompilerException("array should have been moved into heap  ${lv.position}")
                        prog.instr(Opcode.PUSH_WORD, Value(lv.type, lv.heapId))     // XXX  push address of array
                    }
                }
            }
        }
    }

    private fun convertType(givenDt: DataType, targetDt: DataType) {
        // only WIDENS a type, never NARROWS. To avoid loss of precision.
        if(givenDt==targetDt)
            return
        if(givenDt !in NumericDatatypes)
            throw CompilerException("converting non-numeric $givenDt")
        if(targetDt !in NumericDatatypes)
            throw CompilerException("converting $givenDt to non-numeric $targetDt")
        when(givenDt) {
            DataType.UBYTE -> when(targetDt) {
                DataType.UWORD -> prog.instr(Opcode.CAST_UB_TO_UW)
                DataType.WORD -> prog.instr(Opcode.CAST_UB_TO_W)
                DataType.FLOAT -> prog.instr(Opcode.CAST_UB_TO_F)
                else -> {}
            }
            DataType.BYTE -> when(targetDt) {
                DataType.UWORD -> prog.instr(Opcode.CAST_B_TO_UW)
                DataType.WORD -> prog.instr(Opcode.CAST_B_TO_W)
                DataType.FLOAT -> prog.instr(Opcode.CAST_B_TO_F)
                else -> {}
            }
            DataType.UWORD -> when(targetDt) {
                DataType.UBYTE, DataType.BYTE -> throw CompilerException("narrowing type")
                DataType.FLOAT -> prog.instr(Opcode.CAST_UW_TO_F)
                else -> {}
            }
            DataType.WORD -> when(targetDt) {
                DataType.UBYTE, DataType.BYTE -> throw CompilerException("narrowing type")
                DataType.FLOAT -> prog.instr(Opcode.CAST_W_TO_F)
                else -> {}
            }
            DataType.FLOAT -> if(targetDt in IntegerDatatypes) throw CompilerException("narrowing type")
            else -> {}
        }
    }

    private fun translate(identifierRef: IdentifierReference) {
        val target = identifierRef.targetStatement(namespace)
        when (target) {
            is VarDecl -> {
                when (target.type) {
                    VarDeclType.VAR -> {
                        val opcode = opcodePushvar(target.datatype)
                        prog.instr(opcode, callLabel = target.scopedname)
                    }
                    VarDeclType.CONST ->
                        throw CompilerException("const ref should have been const-folded away")
                    VarDeclType.MEMORY -> {
                        when (target.datatype) {
                            DataType.UBYTE -> prog.instr(Opcode.PUSH_MEM_UB, Value(DataType.UWORD, (target.value as LiteralValue).asNumericValue!!))
                            DataType.BYTE-> prog.instr(Opcode.PUSH_MEM_B, Value(DataType.UWORD, (target.value as LiteralValue).asNumericValue!!))
                            DataType.UWORD -> prog.instr(Opcode.PUSH_MEM_UW, Value(DataType.UWORD, (target.value as LiteralValue).asNumericValue!!))
                            DataType.WORD -> prog.instr(Opcode.PUSH_MEM_W, Value(DataType.UWORD, (target.value as LiteralValue).asNumericValue!!))
                            DataType.FLOAT -> prog.instr(Opcode.PUSH_MEM_FLOAT, Value(DataType.UWORD, (target.value as LiteralValue).asNumericValue!!))
                            else -> throw CompilerException("invalid datatype for memory variable expression: $target")
                        }
                    }
                }

            }
            else -> throw CompilerException("expression identifierref should be a vardef, not $target")
        }
    }

    private fun translate(stmt: FunctionCallStatement) {
        prog.line(stmt.position)
        val targetStmt = stmt.target.targetStatement(namespace)!!
        if(targetStmt is BuiltinFunctionStatementPlaceholder) {
            val funcname = stmt.target.nameInSource[0]
            translateFunctionCall(funcname, stmt.arglist)
            return
        }

        when(targetStmt) {
            is Label ->
                prog.instr(Opcode.CALL, callLabel = targetStmt.scopedname)
            is Subroutine -> {
                translateSubroutineCall(targetStmt, stmt.arglist, stmt.position)
                // make sure we clean up the unused result values from the stack
                // only if they're non-register return values!
                if(targetStmt.asmReturnvaluesRegisters.isEmpty())
                    for(rv in targetStmt.returntypes) {
                        val opcode=opcodeDiscard(rv)
                        prog.instr(opcode)
                    }
            }
            else ->
                throw AstException("invalid call target node type: ${targetStmt::class}")
        }
    }

    private fun translateFunctionCall(funcname: String, args: List<IExpression>) {
        // some functions are implemented as vm opcodes
        args.forEach { translate(it) }  // place function argument(s) on the stack
        when (funcname) {
            "len" -> {
                // 1 argument, type determines the exact syscall to use
                val arg=args.single()
                when (arg.resultingDatatype(namespace, heap)) {
                    DataType.STR -> createSyscall("${funcname}_str")
                    DataType.STR_P -> createSyscall("${funcname}_strp")
                    DataType.STR_S -> createSyscall("${funcname}_str")
                    DataType.STR_PS -> createSyscall("${funcname}_strp")
                    else -> throw CompilerException("wrong datatype for len()")
                }
            }
            "any", "all" -> {
                // 1 array argument, type determines the exact syscall to use
                val arg=args.single() as IdentifierReference
                val target=arg.targetStatement(namespace) as VarDecl
                val length=Value(DataType.UBYTE, target.arrayspec!!.size()!!)
                prog.instr(Opcode.PUSH_BYTE, length)
                when (arg.resultingDatatype(namespace, heap)) {
                    DataType.ARRAY_B, DataType.ARRAY_UB -> createSyscall("${funcname}_b")
                    DataType.ARRAY_W, DataType.ARRAY_UW -> createSyscall("${funcname}_w")
                    DataType.ARRAY_F ->  createSyscall("${funcname}_f")
                    else -> throw CompilerException("wrong datatype for $funcname()")
                }
            }
            "avg" -> {
                // 1 array argument, type determines the exact syscall to use
                val arg=args.single() as IdentifierReference
                val target=arg.targetStatement(namespace) as VarDecl
                val length=Value(DataType.UBYTE, target.arrayspec!!.size()!!)
                val arrayDt=arg.resultingDatatype(namespace, heap)
                prog.instr(Opcode.PUSH_BYTE, length)
                when (arrayDt) {
                    DataType.ARRAY_UB -> {
                        createSyscall("sum_ub")
                        prog.instr(Opcode.CAST_UW_TO_F)     // result of sum(ubyte) is uword, so cast
                    }
                    DataType.ARRAY_B -> {
                        createSyscall("sum_b")
                        prog.instr(Opcode.CAST_W_TO_F)     // result of sum(byte) is word, so cast
                    }
                    DataType.ARRAY_UW -> {
                        createSyscall("sum_uw")
                        prog.instr(Opcode.CAST_UW_TO_F)     // result of sum(uword) is uword, so cast
                    }
                    DataType.ARRAY_W -> {
                        createSyscall("sum_w")
                        prog.instr(Opcode.CAST_W_TO_F)     // result of sum(word) is word, so cast
                    }
                    DataType.ARRAY_F -> createSyscall("sum_f")
                    else -> throw CompilerException("wrong datatype for avg")
                }
                // divide by the number of elements
                prog.instr(opcodePush(DataType.FLOAT), Value(DataType.FLOAT, length.numericValue()))
                prog.instr(Opcode.DIV_F)
            }
            "min", "max", "sum" -> {
                // 1 array argument, type determines the exact syscall to use
                val arg=args.single() as IdentifierReference
                val target=arg.targetStatement(namespace) as VarDecl
                val length=Value(DataType.UBYTE, target.arrayspec!!.size()!!)
                prog.instr(Opcode.PUSH_BYTE, length)
                when (arg.resultingDatatype(namespace, heap)) {
                    DataType.ARRAY_UB -> createSyscall("${funcname}_ub")
                    DataType.ARRAY_B -> createSyscall("${funcname}_b")
                    DataType.ARRAY_UW -> createSyscall("${funcname}_uw")
                    DataType.ARRAY_W -> createSyscall("${funcname}_w")
                    DataType.ARRAY_F -> createSyscall("${funcname}_f")
                    else -> throw CompilerException("wrong datatype for $funcname()")
                }
            }
            "abs" -> {
                // 1 argument, type determines the exact opcode to use
                val arg = args.single()
                when (arg.resultingDatatype(namespace, heap)) {
                    DataType.UBYTE, DataType.UWORD -> {}
                    DataType.BYTE -> prog.instr(Opcode.ABS_B)
                    DataType.WORD -> prog.instr(Opcode.ABS_W)
                    DataType.FLOAT -> prog.instr(Opcode.ABS_F)
                    else -> throw CompilerException("wrong datatype for $funcname()")
                }
            }
            "msb" -> prog.instr(Opcode.MSB)         // note: LSB is not a function, it's just an alias for the cast "... as ubyte"
            "lsl" -> {
                val arg = args.single()
                val dt = arg.resultingDatatype(namespace, heap)
                when (dt) {
                    DataType.UBYTE, DataType.BYTE -> prog.instr(Opcode.SHL_BYTE)
                    DataType.UWORD, DataType.WORD -> prog.instr(Opcode.SHL_WORD)
                    else -> throw CompilerException("wrong datatype")
                }
                // this function doesn't return a value on the stack so we pop it directly into the argument register/variable again
                popValueIntoTarget(AssignTarget.fromExpr(arg), dt)
            }
            "lsr" -> {
                val arg = args.single()
                val dt = arg.resultingDatatype(namespace, heap)
                when (dt) {
                    DataType.UBYTE -> prog.instr(Opcode.SHR_UBYTE)
                    DataType.BYTE -> prog.instr(Opcode.SHR_SBYTE)
                    DataType.UWORD -> prog.instr(Opcode.SHR_UWORD)
                    DataType.WORD -> prog.instr(Opcode.SHR_SWORD)
                    else -> throw CompilerException("wrong datatype")
                }
                // this function doesn't return a value on the stack so we pop it directly into the argument register/variable again
                popValueIntoTarget(AssignTarget.fromExpr(arg), dt)
            }
            "rol" -> {
                val arg = args.single()
                val dt = arg.resultingDatatype(namespace, heap)
                when (dt) {
                    DataType.UBYTE -> prog.instr(Opcode.ROL_BYTE)
                    DataType.UWORD -> prog.instr(Opcode.ROL_WORD)
                    else -> throw CompilerException("wrong datatype")
                }
                // this function doesn't return a value on the stack so we pop it directly into the argument register/variable again
                popValueIntoTarget(AssignTarget.fromExpr(arg), dt)
            }
            "ror" -> {
                val arg = args.single()
                val dt = arg.resultingDatatype(namespace, heap)
                when (dt) {
                    DataType.UBYTE, DataType.BYTE -> prog.instr(Opcode.ROR_BYTE)
                    DataType.UWORD, DataType.WORD -> prog.instr(Opcode.ROR_WORD)
                    else -> throw CompilerException("wrong datatype")
                }
                // this function doesn't return a value on the stack so we pop it directly into the argument register/variable again
                popValueIntoTarget(AssignTarget.fromExpr(arg), dt)
            }
            "rol2" -> {
                val arg = args.single()
                val dt = arg.resultingDatatype(namespace, heap)
                when (dt) {
                    DataType.UBYTE, DataType.BYTE -> prog.instr(Opcode.ROL2_BYTE)
                    DataType.UWORD, DataType.WORD -> prog.instr(Opcode.ROL2_WORD)
                    else -> throw CompilerException("wrong datatype")
                }
                // this function doesn't return a value on the stack so we pop it directly into the argument register/variable again
                popValueIntoTarget(AssignTarget.fromExpr(arg), dt)
            }
            "ror2" -> {
                val arg = args.single()
                val dt = arg.resultingDatatype(namespace, heap)
                when (dt) {
                    DataType.UBYTE, DataType.BYTE -> prog.instr(Opcode.ROR2_BYTE)
                    DataType.UWORD, DataType.WORD -> prog.instr(Opcode.ROR2_WORD)
                    else -> throw CompilerException("wrong datatype")
                }
                // this function doesn't return a value on the stack so we pop it directly into the argument register/variable again
                popValueIntoTarget(AssignTarget.fromExpr(arg), dt)
            }
            "set_carry" -> prog.instr(Opcode.SEC)
            "clear_carry" -> prog.instr(Opcode.CLC)
            "set_irqd" -> prog.instr(Opcode.SEI)
            "clear_irqd" -> prog.instr(Opcode.CLI)
            "rsave" -> prog.instr(Opcode.RSAVE)
            "rrestore" -> prog.instr(Opcode.RRESTORE)
            else -> createSyscall(funcname)  // call builtin function
        }
    }

    private fun translateSubroutineCall(subroutine: Subroutine, arguments: List<IExpression>, callPosition: Position) {
        // evaluate the arguments and assign them into the subroutine's argument variables.
        var restoreX = Register.X in subroutine.asmClobbers
        if(subroutine.isAsmSubroutine) {
            if(subroutine.parameters.size!=subroutine.asmParameterRegisters.size)
                throw CompilerException("no support for mix of register and non-register subroutine arguments")

            if(restoreX)
                prog.instr(Opcode.RSAVEX)

            // only register arguments (or status-flag bits)
            var carryParam: Boolean? = null
            for(arg in arguments.zip(subroutine.asmParameterRegisters)) {
                if(arg.second.statusflag!=null) {
                    if(arg.second.statusflag==Statusflag.Pc)
                        carryParam = arg.first.constValue(namespace, heap)!!.asBooleanValue
                    else
                        throw CompilerException("no support for status flag parameter: ${arg.second.statusflag}")
                } else {
                    when (arg.second.registerOrPair!!) {
                        A -> {
                            val assign = Assignment(listOf(AssignTarget(Register.A, null, null, null, callPosition)), null, arg.first, callPosition)
                            assign.linkParents(arguments[0].parent)
                            translate(assign)
                        }
                        X -> {
                            if(!restoreX) {
                                prog.instr(Opcode.RSAVEX)
                                restoreX = true
                            }
                            val assign = Assignment(listOf(AssignTarget(Register.X, null, null, null, callPosition)), null, arg.first, callPosition)
                            assign.linkParents(arguments[0].parent)
                            translate(assign)
                        }
                        Y -> {
                            val assign = Assignment(listOf(AssignTarget(Register.Y, null, null, null, callPosition)), null, arg.first, callPosition)
                            assign.linkParents(arguments[0].parent)
                            translate(assign)
                        }
                        AX -> {
                            if(!restoreX) {
                                prog.instr(Opcode.RSAVEX)
                                restoreX = true
                            }
                            val valueA: IExpression
                            val valueX: IExpression
                            val paramDt = arg.first.resultingDatatype(namespace, heap)
                            when (paramDt) {
                                DataType.UBYTE -> {
                                    valueA=arg.first
                                    valueX=LiteralValue.optimalInteger(0, callPosition)
                                    val assignA = Assignment(listOf(AssignTarget(Register.A, null, null, null, callPosition)), null, valueA, callPosition)
                                    val assignX = Assignment(listOf(AssignTarget(Register.X, null, null, null, callPosition)), null, valueX, callPosition)
                                    assignA.linkParents(arguments[0].parent)
                                    assignX.linkParents(arguments[0].parent)
                                    translate(assignA)
                                    translate(assignX)
                                }
                                DataType.UWORD -> {
                                    translate(arg.first)
                                    prog.instr(Opcode.POP_REGAX_WORD)
                                }
                                DataType.STR, DataType.STR_S -> {
                                    pushStringAddress(arg.first, false)     // TODO with or without remove last opcode??
                                    prog.instr(Opcode.POP_REGAX_WORD)
                                }
                                DataType.FLOAT -> {
                                    pushFloatAddress(arg.first)
                                    prog.instr(Opcode.POP_REGAX_WORD)
                                }
                                else -> TODO("pass parameter of type $paramDt in registers AX at $callPosition")
                            }
                        }
                        AY -> {
                            val valueA: IExpression
                            val valueY: IExpression
                            val paramDt = arg.first.resultingDatatype(namespace, heap)
                            when (paramDt) {
                                DataType.UBYTE -> {
                                    valueA=arg.first
                                    valueY=LiteralValue.optimalInteger(0, callPosition)
                                    val assignA = Assignment(listOf(AssignTarget(Register.A, null, null, null, callPosition)), null, valueA, callPosition)
                                    val assignY = Assignment(listOf(AssignTarget(Register.Y, null, null, null, callPosition)), null, valueY, callPosition)
                                    assignA.linkParents(arguments[0].parent)
                                    assignY.linkParents(arguments[0].parent)
                                    translate(assignA)
                                    translate(assignY)
                                }
                                DataType.UWORD, DataType.WORD -> {
                                    translate(arg.first)
                                    prog.instr(Opcode.POP_REGAY_WORD)
                                }
                                DataType.STR, DataType.STR_S -> {
                                    pushStringAddress(arg.first, false)     // TODO with or without remove last opcode??
                                    prog.instr(Opcode.POP_REGAY_WORD)
                                }
                                DataType.FLOAT -> {
                                    pushFloatAddress(arg.first)
                                    prog.instr(Opcode.POP_REGAY_WORD)
                                }
                                else -> TODO("pass parameter of type $paramDt in registers AY at $callPosition")
                            }
                        }
                        XY -> {
                            if(!restoreX) {
                                prog.instr(Opcode.RSAVEX)
                                restoreX = true
                            }
                            val valueX: IExpression
                            val valueY: IExpression
                            val paramDt = arg.first.resultingDatatype(namespace, heap)
                            when (paramDt) {
                                DataType.UBYTE -> {
                                    valueX=arg.first
                                    valueY=LiteralValue.optimalInteger(0, callPosition)
                                    val assignX = Assignment(listOf(AssignTarget(Register.X, null, null, null, callPosition)), null, valueX, callPosition)
                                    val assignY = Assignment(listOf(AssignTarget(Register.Y, null, null, null, callPosition)), null, valueY, callPosition)
                                    assignX.linkParents(arguments[0].parent)
                                    assignY.linkParents(arguments[0].parent)
                                    translate(assignX)
                                    translate(assignY)
                                }
                                DataType.UWORD -> {
                                    translate(arg.first)
                                    prog.instr(Opcode.POP_REGXY_WORD)
                                }
                                DataType.STR, DataType.STR_S -> {
                                    pushStringAddress(arg.first, false)     // TODO with or without remove last opcode??
                                    prog.instr(Opcode.POP_REGXY_WORD)
                                }
                                DataType.FLOAT -> {
                                    pushFloatAddress(arg.first)
                                    prog.instr(Opcode.POP_REGXY_WORD)
                                }
                                else -> TODO("pass parameter of type $paramDt in registers XY at $callPosition")
                            }
                        }
                    }
                }
            }

            // carry is set last, to avoid clobbering it when loading the other parameters
            when(carryParam) {
                true -> prog.instr(Opcode.SEC)
                false -> prog.instr(Opcode.CLC)
            }
        } else {
            // only regular (non-register) arguments
            // "assign" the arguments to the locally scoped parameter variables for this subroutine
            // (subroutine arguments are not passed via the stack!)
            for (arg in arguments.zip(subroutine.parameters)) {
                translate(arg.first)
                convertType(arg.first.resultingDatatype(namespace, heap)!!, arg.second.type) // convert types of arguments to required parameter type
                val opcode = opcodePopvar(arg.second.type)
                prog.instr(opcode, callLabel = subroutine.scopedname + "." + arg.second.name)
            }
        }
        prog.instr(Opcode.CALL, callLabel = subroutine.scopedname)
        if(restoreX)
            prog.instr(Opcode.RRESTOREX)
    }

    private fun translateBinaryOperator(operator: String, dt: DataType) {
        if(dt !in NumericDatatypes)
            throw CompilerException("non-numeric datatype for operator: $dt")
        val opcode = when(operator) {
            "+" -> {
                when(dt) {
                    DataType.UBYTE -> Opcode.ADD_UB
                    DataType.BYTE -> Opcode.ADD_B
                    DataType.UWORD -> Opcode.ADD_UW
                    DataType.WORD -> Opcode.ADD_W
                    DataType.FLOAT -> Opcode.ADD_F
                    else -> throw CompilerException("only byte/word/float possible")
                }
            }
            "-" -> {
                when(dt) {
                    DataType.UBYTE -> Opcode.SUB_UB
                    DataType.BYTE -> Opcode.SUB_B
                    DataType.UWORD -> Opcode.SUB_UW
                    DataType.WORD -> Opcode.SUB_W
                    DataType.FLOAT -> Opcode.SUB_F
                    else -> throw CompilerException("only byte/word/float possible")
                }
            }
            "*" -> {
                when(dt) {
                    DataType.UBYTE -> Opcode.MUL_UB
                    DataType.BYTE -> Opcode.MUL_B
                    DataType.UWORD -> Opcode.MUL_UW
                    DataType.WORD -> Opcode.MUL_W
                    DataType.FLOAT -> Opcode.MUL_F
                    else -> throw CompilerException("only byte/word/float possible")
                }
            }
            "/" -> {
                if(dt!=DataType.FLOAT) throw CompilerException("normal division only possible between floats")
                else Opcode.DIV_F
            }
            "//" -> {
                when(dt) {
                    DataType.UBYTE -> Opcode.IDIV_UB
                    DataType.BYTE -> Opcode.IDIV_B
                    DataType.UWORD -> Opcode.IDIV_UW
                    DataType.WORD -> Opcode.IDIV_W
                    DataType.FLOAT -> Opcode.FLOORDIV
                    else -> throw CompilerException("only byte/word/float possible")
                }
            }
            "%" -> {
                when(dt) {
                    DataType.UBYTE -> Opcode.REMAINDER_UB
                    DataType.UWORD -> Opcode.REMAINDER_UW
                    DataType.BYTE, DataType.WORD -> throw CompilerException("remainder of signed integers is not properly defined/implemented, use unsigned instead")
                    else -> throw CompilerException("only byte/word operands possible")
                }
            }
            "**" -> {
                when(dt) {
                    DataType.UBYTE -> Opcode.POW_UB
                    DataType.BYTE -> Opcode.POW_B
                    DataType.UWORD -> Opcode.POW_UW
                    DataType.WORD -> Opcode.POW_W
                    DataType.FLOAT -> Opcode.POW_F
                    else -> throw CompilerException("only byte/word/float possible")
                }
            }
            "&" -> {
                when(dt) {
                    DataType.UBYTE, DataType.BYTE -> Opcode.BITAND_BYTE
                    DataType.UWORD, DataType.WORD -> Opcode.BITAND_WORD
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "|" -> {
                when(dt) {
                    DataType.UBYTE, DataType.BYTE -> Opcode.BITOR_BYTE
                    DataType.UWORD, DataType.WORD -> Opcode.BITOR_WORD
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "^" -> {
                when(dt) {
                    DataType.UBYTE, DataType.BYTE -> Opcode.BITXOR_BYTE
                    DataType.UWORD, DataType.WORD -> Opcode.BITXOR_WORD
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "and" -> {
                when(dt) {
                    DataType.UBYTE, DataType.BYTE -> Opcode.AND_BYTE
                    DataType.UWORD, DataType.WORD -> Opcode.AND_WORD
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "or" -> {
                when(dt) {
                    DataType.UBYTE, DataType.BYTE -> Opcode.OR_BYTE
                    DataType.UWORD, DataType.WORD -> Opcode.OR_WORD
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "xor" -> {
                when(dt) {
                    DataType.UBYTE, DataType.BYTE -> Opcode.XOR_BYTE
                    DataType.UWORD, DataType.WORD -> Opcode.XOR_WORD
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "<" -> {
                when(dt) {
                    DataType.UBYTE -> Opcode.LESS_UB
                    DataType.BYTE -> Opcode.LESS_B
                    DataType.UWORD -> Opcode.LESS_UW
                    DataType.WORD -> Opcode.LESS_W
                    DataType.FLOAT -> Opcode.LESS_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            ">" -> {
                when(dt) {
                    DataType.UBYTE -> Opcode.GREATER_UB
                    DataType.BYTE -> Opcode.GREATER_B
                    DataType.UWORD -> Opcode.GREATER_UW
                    DataType.WORD -> Opcode.GREATER_W
                    DataType.FLOAT -> Opcode.GREATER_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            "<=" -> {
                when(dt) {
                    DataType.UBYTE -> Opcode.LESSEQ_UB
                    DataType.BYTE -> Opcode.LESSEQ_B
                    DataType.UWORD -> Opcode.LESSEQ_UW
                    DataType.WORD -> Opcode.LESSEQ_W
                    DataType.FLOAT -> Opcode.LESSEQ_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            ">=" -> {
                when(dt) {
                    DataType.UBYTE -> Opcode.GREATEREQ_UB
                    DataType.BYTE -> Opcode.GREATEREQ_B
                    DataType.UWORD -> Opcode.GREATEREQ_UW
                    DataType.WORD -> Opcode.GREATEREQ_W
                    DataType.FLOAT -> Opcode.GREATEREQ_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            "==" -> {
                when (dt) {
                    DataType.UBYTE, DataType.BYTE -> Opcode.EQUAL_BYTE
                    DataType.UWORD, DataType.WORD -> Opcode.EQUAL_WORD
                    DataType.FLOAT -> Opcode.EQUAL_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            "!=" -> {
                when (dt) {
                    DataType.UBYTE, DataType.BYTE -> Opcode.NOTEQUAL_BYTE
                    DataType.UWORD, DataType.WORD -> Opcode.NOTEQUAL_WORD
                    DataType.FLOAT -> Opcode.NOTEQUAL_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            else -> throw FatalAstException("const evaluation for invalid operator $operator")
        }
        prog.instr(opcode)
    }

    private fun translateBitshiftedOperator(operator: String, leftDt: DataType, amount: LiteralValue?) {
        if(amount?.asIntegerValue == null)
            throw FatalAstException("bitshift operators should only have constant integer value as right operand")
        var shifts=amount.asIntegerValue
        if(shifts<0)
            throw FatalAstException("bitshift value should be >= 0")

        prog.removeLastInstruction()        // the amount of shifts is not used as a stack value
        if(shifts==0)
            return
        while(shifts>0) {
            if(operator==">>") {
                when (leftDt) {
                    DataType.UBYTE -> prog.instr(Opcode.SHIFTEDR_UBYTE)
                    DataType.BYTE -> prog.instr(Opcode.SHIFTEDR_SBYTE)
                    DataType.UWORD -> prog.instr(Opcode.SHIFTEDR_UWORD)
                    DataType.WORD -> prog.instr(Opcode.SHIFTEDR_SWORD)
                    else -> throw CompilerException("wrong datatype")
                }
            } else if(operator=="<<") {
                when (leftDt) {
                    DataType.UBYTE, DataType.BYTE -> prog.instr(Opcode.SHIFTEDL_BYTE)
                    DataType.UWORD, DataType.WORD -> prog.instr(Opcode.SHIFTEDL_WORD)
                    else -> throw CompilerException("wrong datatype")
                }
            }
            shifts--
        }
    }

    private fun translatePrefixOperator(operator: String, operandDt: DataType?) {
        if(operandDt==null)
            throw CompilerException("operand datatype not known")
        val opcode = when(operator) {
            "+" -> Opcode.NOP
            "-" -> {
                when (operandDt) {
                    DataType.BYTE -> Opcode.NEG_B
                    DataType.WORD -> Opcode.NEG_W
                    DataType.FLOAT -> Opcode.NEG_F
                    else -> throw CompilerException("only byte/word/foat possible")
                }
            }
            "~" -> {
                when(operandDt) {
                    DataType.UBYTE, DataType.BYTE -> Opcode.INV_BYTE
                    DataType.UWORD, DataType.WORD -> Opcode.INV_WORD
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "not" -> {
                when(operandDt) {
                    DataType.UBYTE, DataType.BYTE -> Opcode.NOT_BYTE
                    DataType.UWORD, DataType.WORD -> Opcode.NOT_WORD
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            else -> throw FatalAstException("const evaluation for invalid prefix operator $operator")
        }
        prog.instr(opcode)
    }

    private fun translate(arrayindexed: ArrayIndexedExpression, write: Boolean) {
        val variable = arrayindexed.identifier.targetStatement(namespace) as VarDecl
        translate(arrayindexed.arrayspec.x)
        if (write)
            prog.instr(opcodeWriteindexedvar(variable.datatype), callLabel = variable.scopedname)
        else
            prog.instr(opcodeReadindexedvar(variable.datatype), callLabel = variable.scopedname)
    }

    private fun createSyscall(funcname: String) {
        val function = (
                if (funcname.startsWith("vm_"))
                    funcname
                else
                    "FUNC_$funcname"
                ).toUpperCase()
        val callNr = Syscall.valueOf(function).callNr
        prog.instr(Opcode.SYSCALL, Value(DataType.UBYTE, callNr))
    }

    private fun translate(stmt: Jump, branchOpcode: Opcode?) {
        var jumpAddress: Value? = null
        var jumpLabel: String? = null

        when {
            stmt.generatedLabel!=null -> jumpLabel = stmt.generatedLabel
            stmt.address!=null -> {
                if(branchOpcode!=null)
                    throw CompilerException("cannot branch to address, should use absolute jump instead")
                jumpAddress = Value(DataType.UWORD, stmt.address)
            }
            else -> {
                val target = stmt.identifier!!.targetStatement(namespace)!!
                jumpLabel = when(target) {
                    is Label -> target.scopedname
                    is Subroutine -> target.scopedname
                    else -> throw CompilerException("invalid jump target type ${target::class}")
                }
            }
        }
        prog.line(stmt.position)
        prog.instr(branchOpcode ?: Opcode.JUMP, jumpAddress, jumpLabel)
    }

    private fun translate(stmt: PostIncrDecr) {
        prog.line(stmt.position)
        when {
            stmt.target.register != null -> when(stmt.operator) {
                "++" -> prog.instr(Opcode.INC_VAR_UB, callLabel = stmt.target.register.toString())
                "--" -> prog.instr(Opcode.DEC_VAR_UB, callLabel = stmt.target.register.toString())
            }
            stmt.target.identifier != null -> {
                val targetStatement = stmt.target.identifier!!.targetStatement(namespace) as VarDecl
                when(stmt.operator) {
                    "++" -> prog.instr(opcodeIncvar(targetStatement.datatype), callLabel = targetStatement.scopedname)
                    "--" -> prog.instr(opcodeDecvar(targetStatement.datatype), callLabel = targetStatement.scopedname)
                }
            }
            stmt.target.arrayindexed != null -> {
                val variable = stmt.target.arrayindexed!!.identifier.targetStatement(namespace) as VarDecl
                translate(stmt.target.arrayindexed!!.arrayspec.x)
                when(stmt.operator) {
                    "++" -> prog.instr(opcodeIncArrayindexedVar(variable.datatype), callLabel = variable.scopedname)
                    "--" -> prog.instr(opcodeDecArrayindexedVar(variable.datatype), callLabel = variable.scopedname)
                }
            }
            stmt.target.memoryAddress != null -> {
                val address = stmt.target.memoryAddress?.addressExpression?.constValue(namespace, heap)?.asIntegerValue
                if(address!=null) {
                    when(stmt.operator) {
                        "++" -> prog.instr(Opcode.INC_MEMORY, Value(DataType.UWORD, address))
                        "--" -> prog.instr(Opcode.DEC_MEMORY, Value(DataType.UWORD, address))
                    }
                } else {
                    translate(stmt.target.memoryAddress!!.addressExpression)
                    when(stmt.operator) {
                        "++" -> prog.instr(Opcode.POP_INC_MEMORY)
                        "--" -> prog.instr(Opcode.POP_DEC_MEMORY)
                    }
                }
            }
            else -> throw CompilerException("very strange postincrdecr ${stmt.target}")
        }
    }

    private fun translate(stmt: VariableInitializationAssignment) {
        // this is an assignment to initialize a variable's value in the scope.
        // the compiler can perhaps optimize this phase.
        // todo: optimize variable init by keeping track of the block of init values so it can be copied as a whole
        translate(stmt as Assignment)
    }

    private fun translate(stmt: Assignment) {
        prog.line(stmt.position)
        translate(stmt.value)

        val assignTarget= stmt.singleTarget
        if(assignTarget==null) {
            // we're dealing with multiple return values
            translateMultiReturnAssignment(stmt)
            return
        }

        val valueDt = stmt.value.resultingDatatype(namespace, heap)
        val targetDt = assignTarget.determineDatatype(namespace, heap, stmt)
        if(valueDt!=targetDt) {
            // convert value to target datatype if possible
            // @todo use convertType()????
            when(targetDt) {
                DataType.UBYTE, DataType.BYTE ->
                    if(valueDt!=DataType.BYTE && valueDt!=DataType.UBYTE)
                        throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                DataType.WORD -> {
                    when (valueDt) {
                        DataType.UBYTE -> prog.instr(Opcode.CAST_UB_TO_W)
                        DataType.BYTE -> prog.instr(Opcode.CAST_B_TO_W)
                        else -> throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                    }
                }
                DataType.UWORD -> {
                    when (valueDt) {
                        DataType.UBYTE -> prog.instr(Opcode.CAST_UB_TO_UW)
                        DataType.BYTE -> prog.instr(Opcode.CAST_B_TO_UW)
                        DataType.STR, DataType.STR_S -> pushStringAddress(stmt.value, true)
                        DataType.ARRAY_B, DataType.ARRAY_UB, DataType.ARRAY_W, DataType.ARRAY_UW, DataType.ARRAY_F -> {
                            if (stmt.value is IdentifierReference) {
                                val vardecl = (stmt.value as IdentifierReference).targetStatement(namespace) as VarDecl
                                prog.removeLastInstruction()
                                prog.instr(Opcode.PUSH_ADDR_HEAPVAR, callLabel = vardecl.scopedname)
                            }
                            else
                                throw CompilerException("can only take address of a literal string value or a string/array variable")
                        }
                        else -> throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                    }
                }
                DataType.FLOAT -> {
                    when (valueDt) {
                        DataType.UBYTE -> prog.instr(Opcode.CAST_UB_TO_F)
                        DataType.BYTE -> prog.instr(Opcode.CAST_B_TO_F)
                        DataType.UWORD -> prog.instr(Opcode.CAST_UW_TO_F)
                        DataType.WORD -> prog.instr(Opcode.CAST_W_TO_F)
                        else -> throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                    }
                }
                DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W, DataType.ARRAY_F -> throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                null -> throw CompilerException("could not determine targetdt")
            }
        }

        if(stmt.aug_op!=null) {
            // augmented assignment
            when {
                assignTarget.identifier != null -> {
                    val target = assignTarget.identifier.targetStatement(namespace)!!
                    when(target) {
                        is VarDecl -> {
                            val opcode = opcodePushvar(assignTarget.determineDatatype(namespace, heap, stmt)!!)
                            prog.instr(opcode, callLabel = target.scopedname)
                        }
                        else -> throw CompilerException("invalid assignment target type ${target::class}")
                    }
                }
                assignTarget.register != null -> prog.instr(Opcode.PUSH_VAR_BYTE, callLabel = assignTarget.register.toString())
                assignTarget.arrayindexed != null -> translate(assignTarget.arrayindexed, false)
                assignTarget.memoryAddress != null -> {
                    TODO("translate aug assign on memory address $stmt")
                }
            }

            translateAugAssignOperator(stmt.aug_op, stmt.value.resultingDatatype(namespace, heap))
        }

        if(stmt.value is FunctionCall) {
            val sub = (stmt.value as FunctionCall).target.targetStatement(namespace)
            if(sub is Subroutine && sub.asmReturnvaluesRegisters.isNotEmpty()) {
                // the subroutine call returns its values in registers
                storeRegisterIntoTarget(sub.asmReturnvaluesRegisters.single(), assignTarget, stmt)
                return
            }
        }

        // pop the result value back into the assignment target
        val datatype = assignTarget.determineDatatype(namespace, heap, stmt)!!
        popValueIntoTarget(assignTarget, datatype)
    }

    private fun pushStringAddress(value: IExpression, removeLastOpcode: Boolean) {
        when (value) {
            is LiteralValue -> {
                if(removeLastOpcode) prog.removeLastInstruction()
                prog.instr(Opcode.PUSH_ADDR_STR, Value(value.type, value.heapId!!))
            }
            is IdentifierReference -> {
                val vardecl = value.targetStatement(namespace) as VarDecl
                if(removeLastOpcode) prog.removeLastInstruction()
                prog.instr(Opcode.PUSH_ADDR_HEAPVAR, callLabel = vardecl.scopedname)
            }
            else -> throw CompilerException("can only take address of a literal string value or a string/array variable")
        }
    }

    private fun pushFloatAddress(value: IExpression) {
        when (value) {
            is LiteralValue -> {
                prog.instr(Opcode.PUSH_ADDR_FLOAT, Value(value.type, value.floatvalue!!))
            }
            is IdentifierReference -> {
                val vardecl = value.targetStatement(namespace) as VarDecl
                prog.instr(Opcode.PUSH_ADDR_HEAPVAR, callLabel = vardecl.scopedname)
            }
            else -> throw CompilerException("literal float value or float variable expected")
        }
    }

    private fun translateMultiReturnAssignment(stmt: Assignment) {
        val targetStmt = (stmt.value as? FunctionCall)?.target?.targetStatement(namespace)
        if(targetStmt is Subroutine && targetStmt.isAsmSubroutine) {
            // we're dealing with the one case where multiple assignment targets are allowed: a call to an asmsub with multiple return values
            // for now, we only support multiple return values as long as they're returned in registers as well.
            if(targetStmt.asmReturnvaluesRegisters.isEmpty())
                throw CompilerException("we only support multiple return values / assignment when the asmsub returns values in registers")
            // if the result registers are not assigned in the exact same registers, or in variables, we need some code
            if(stmt.targets.all{it.register!=null}) {
                val resultRegisters = registerSet(targetStmt.asmReturnvaluesRegisters)
                if(stmt.targets.size!=resultRegisters.size)
                    throw CompilerException("asmsub number of return values doesn't match number of assignment targets ${stmt.position}")
                val targetRegs = stmt.targets.filter {it.register!=null}.map{it.register}.toSet()
                if(resultRegisters!=targetRegs)
                    throw CompilerException("asmsub return registers don't match assignment target registers ${stmt.position}")
                // output is in registers already, no need to emit any asm code
            } else {
                // output is in registers but has to be stored somewhere
                for(result in targetStmt.asmReturnvaluesRegisters.zip(stmt.targets))
                    storeRegisterIntoTarget(result.first, result.second, stmt)
            }
        } else throw CompilerException("can only use multiple assignment targets on an asmsub call")
    }

    private fun storeRegisterIntoTarget(registerOrStatus: RegisterOrStatusflag, target: AssignTarget, parent: IStatement) {
        if(registerOrStatus.statusflag!=null)
            return
        when(registerOrStatus.registerOrPair){
            A -> {
                val assignment = Assignment(listOf(target), null, RegisterExpr(Register.A, target.position), target.position)
                assignment.linkParents(parent)
                translate(assignment)
            }
            X -> {
                val assignment = Assignment(listOf(target), null, RegisterExpr(Register.X, target.position), target.position)
                assignment.linkParents(parent)
                translate(assignment)
            }
            Y -> {
                val assignment = Assignment(listOf(target), null, RegisterExpr(Register.Y, target.position), target.position)
                assignment.linkParents(parent)
                translate(assignment)
            }
            AX -> {
                // deal with register pair AX:  target = A + X*256
                val targetDt = target.determineDatatype(namespace, heap, parent)
                if(targetDt!=DataType.UWORD && targetDt!=DataType.WORD)
                    throw CompilerException("invalid target datatype for registerpair $targetDt")
                prog.instr(Opcode.PUSH_REGAX_WORD)
                popValueIntoTarget(target, targetDt)
            }
            AY -> {
                // deal with register pair AY:  target = A + Y*256
                val targetDt = target.determineDatatype(namespace, heap, parent)
                if(targetDt!=DataType.UWORD && targetDt!=DataType.WORD)
                    throw CompilerException("invalid target datatype for registerpair $targetDt")
                prog.instr(Opcode.PUSH_REGAY_WORD)
                popValueIntoTarget(target, targetDt)
            }
            XY -> {
                // deal with register pair XY:  target = X + Y*256
                val targetDt = target.determineDatatype(namespace, heap, parent)
                if(targetDt!=DataType.UWORD && targetDt!=DataType.WORD)
                    throw CompilerException("invalid target datatype for registerpair $targetDt")
                prog.instr(Opcode.PUSH_REGXY_WORD)
                popValueIntoTarget(target, targetDt)
            }
        }
    }

    private fun popValueIntoTarget(assignTarget: AssignTarget, datatype: DataType) {
        when {
            assignTarget.identifier != null -> {
                val target = assignTarget.identifier.targetStatement(namespace)!!
                if (target is VarDecl) {
                    when (target.type) {
                        VarDeclType.VAR -> {
                            val opcode = opcodePopvar(datatype)
                            prog.instr(opcode, callLabel = target.scopedname)
                        }
                        VarDeclType.MEMORY -> {
                            val opcode = opcodePopmem(datatype)
                            val address = target.value?.constValue(namespace, heap)!!.asIntegerValue!!
                            prog.instr(opcode, Value(DataType.UWORD, address))
                        }
                        VarDeclType.CONST -> throw CompilerException("cannot assign to const")
                    }
                } else throw CompilerException("invalid assignment target type ${target::class}")
            }
            assignTarget.register != null -> prog.instr(Opcode.POP_VAR_BYTE, callLabel = assignTarget.register.toString())
            assignTarget.arrayindexed != null -> translate(assignTarget.arrayindexed, true)     // write value to it
            assignTarget.memoryAddress != null -> {
                val address = assignTarget.memoryAddress?.addressExpression?.constValue(namespace, heap)?.asIntegerValue
                if(address!=null) {
                    // const integer address given
                    prog.instr(Opcode.POP_MEM_BYTE, arg=Value(DataType.UWORD, address))
                } else {
                    translate(assignTarget.memoryAddress!!)
                }
            }
            else -> throw CompilerException("corrupt assigntarget $assignTarget")
        }
    }

    private fun translateAugAssignOperator(aug_op: String, valueDt: DataType?) {        // @todo: not used in practice? (all augassigns are converted to normal assigns)
        if(valueDt==null)
            throw CompilerException("value datatype not known")
        val validDt = setOf(DataType.UBYTE, DataType.UWORD, DataType.FLOAT)
        if(valueDt !in validDt)
            throw CompilerException("invalid datatype(s) for operand(s)")
        val opcode = when(aug_op) {
            // @todo ... need more datatypes here?
            "+=" -> {
                when (valueDt) {
                    DataType.UBYTE -> Opcode.ADD_UB
                    DataType.UWORD -> Opcode.ADD_UW
                    DataType.FLOAT -> Opcode.ADD_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            "-=" -> {
                when (valueDt) {
                    DataType.UBYTE -> Opcode.SUB_UB
                    DataType.UWORD -> Opcode.SUB_UW
                    DataType.FLOAT -> Opcode.SUB_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            "/=" -> TODO("/=")
            "//=" -> {
                when (valueDt) {
                    DataType.UBYTE -> Opcode.IDIV_UB
                    DataType.BYTE -> Opcode.IDIV_B
                    DataType.UWORD -> Opcode.IDIV_UW
                    DataType.WORD -> Opcode.IDIV_W
                    DataType.FLOAT -> Opcode.FLOORDIV
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            "*=" -> {
                when (valueDt) {
                    DataType.UBYTE -> Opcode.MUL_UB
                    DataType.UWORD -> Opcode.MUL_UW
                    DataType.FLOAT -> Opcode.MUL_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            "**=" -> {
                when (valueDt) {
                    DataType.UBYTE -> Opcode.POW_UB
                    DataType.UWORD -> Opcode.POW_UW
                    DataType.FLOAT -> Opcode.POW_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            "&=" -> {
                when(valueDt) {
                    DataType.UBYTE, DataType.BYTE -> Opcode.BITAND_BYTE
                    DataType.UWORD, DataType.WORD -> Opcode.BITAND_WORD
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "|=" -> {
                when(valueDt) {
                    DataType.UBYTE, DataType.BYTE -> Opcode.BITOR_BYTE
                    DataType.UWORD, DataType.WORD -> Opcode.BITOR_WORD
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            "^=" -> {
                when(valueDt) {
                    DataType.UBYTE, DataType.BYTE -> Opcode.BITXOR_BYTE
                    DataType.UWORD, DataType.WORD -> Opcode.BITXOR_WORD
                    else -> throw CompilerException("only byte/word possible")
                }
            }
            else -> throw CompilerException("invalid aug assignment operator $aug_op")
        }
        prog.instr(opcode)
    }

    private fun translate(stmt: Return) {
        // put the return values on the stack, in reversed order. The caller will process them.
        for(value in stmt.values.reversed()) {
            translate(value)
        }
        prog.line(stmt.position)
        prog.instr(Opcode.RETURN)
    }

    private fun translate(stmt: Label) {
        prog.label(stmt.scopedname)
    }

    private fun translate(loop: ForLoop) {
        if(loop.body.isEmpty()) return
        prog.line(loop.position)
        val loopVarName: String
        val loopVarDt: DataType

        if(loop.loopRegister!=null) {
            val reg = loop.loopRegister
            loopVarName = reg.toString()
            loopVarDt = DataType.UBYTE
        } else {
            val loopvar = (loop.loopVar!!.targetStatement(namespace) as VarDecl)
            loopVarName = loopvar.scopedname
            loopVarDt = loopvar.datatype
        }

        if(loop.iterable is RangeExpr) {
            val range = (loop.iterable as RangeExpr).toConstantIntegerRange(heap)
            if(range!=null) {
                // loop over a range with constant start, last and step values
                if (range.isEmpty())
                    throw CompilerException("loop over empty range should have been optimized away")
                else if (range.count()==1)
                    throw CompilerException("loop over just 1 value should have been optimized away")
                if((range.last-range.first) % range.step != 0)
                    throw CompilerException("range first and last must be exactly inclusive")
                when (loopVarDt) {
                    DataType.UBYTE -> {
                        if (range.first < 0 || range.first > 255 || range.last < 0 || range.last > 255)
                            throw CompilerException("range out of bounds for byte")
                    }
                    DataType.UWORD -> {
                        if (range.first < 0 || range.first > 65535 || range.last < 0 || range.last > 65535)
                            throw CompilerException("range out of bounds for word")
                    }
                    else -> throw CompilerException("range must be byte or word")
                }
                translateForOverConstantRange(loopVarName, loopVarDt, range, loop.body)
            } else {
                // loop over a range where one or more of the start, last or step values is not a constant
                if(loop.loopRegister!=null) {
                    translateForOverVariableRange(null, loop.loopRegister, loop.iterable as RangeExpr, loop.body)
                }
                else {
                    translateForOverVariableRange(loop.loopVar!!.nameInSource, null, loop.iterable as RangeExpr, loop.body)
                }
            }
        } else {
            // ok, must be a literalvalue
            when {
                loop.iterable is IdentifierReference -> {
                    val idRef = loop.iterable as IdentifierReference
                    val vardecl = (idRef.targetStatement(namespace) as VarDecl)
                    val iterableValue = vardecl.value as LiteralValue
                    if(!iterableValue.isIterable(namespace, heap))
                        throw CompilerException("loop over something that isn't iterable ${loop.iterable}")
                    translateForOverIterableVar(loop, loopVarDt, iterableValue)
                }
                loop.iterable is LiteralValue -> throw CompilerException("literal value in loop must have been moved to heap already $loop")
                else -> throw CompilerException("loopvar is something strange ${loop.iterable}")
            }
        }
    }

    private fun translateForOverIterableVar(loop: ForLoop, loopvarDt: DataType, iterableValue: LiteralValue) {
        if(loopvarDt==DataType.UBYTE && iterableValue.type !in setOf(DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS, DataType.ARRAY_UB))
            throw CompilerException("loop variable type doesn't match iterableValue type")
        else if(loopvarDt==DataType.UWORD && iterableValue.type != DataType.ARRAY_UW)
            throw CompilerException("loop variable type doesn't match iterableValue type")
        else if(loopvarDt==DataType.FLOAT && iterableValue.type != DataType.ARRAY_F)
            throw CompilerException("loop variable type doesn't match iterableValue type")

        val numElements: Int
        when(iterableValue.type) {
            DataType.UBYTE, DataType.BYTE,
            DataType.UWORD, DataType.WORD,
            DataType.FLOAT -> throw CompilerException("non-iterableValue type")
            DataType.STR_P, DataType.STR_PS -> throw CompilerException("can't iterate string type ${iterableValue.type}")
            DataType.STR, DataType.STR_S -> {
                numElements = iterableValue.strvalue(heap).length
                if(numElements>255) throw CompilerException("string length > 255")
            }
            DataType.ARRAY_UB, DataType.ARRAY_B,
            DataType.ARRAY_UW, DataType.ARRAY_W -> {
                numElements = iterableValue.arrayvalue?.size ?: heap.get(iterableValue.heapId!!).arraysize
                if(numElements>255) throw CompilerException("string length > 255")
            }
            DataType.ARRAY_F -> {
                numElements = iterableValue.arrayvalue?.size ?: heap.get(iterableValue.heapId!!).arraysize
                if(numElements>255) throw CompilerException("string length > 255")
            }
        }

        if(loop.loopRegister!=null && loop.loopRegister==Register.X)
            throw CompilerException("loopVar cannot use X register because that is used as internal stack pointer")

        /**
         *      indexVar = 0
         * loop:
         *      LV = iterableValue[indexVar]
         *      ..body..
         *      ..break statement:  goto break
         *      ..continue statement: goto continue
         *      ..
         * continue:
         *      indexVar++
         *      if indexVar!=numElements goto loop
         * break:
         *      nop
         */
        val loopLabel = makeLabel("loop")
        val continueLabel = makeLabel("continue")
        val breakLabel = makeLabel("break")
        val indexVarType = if (numElements <= 255) DataType.UBYTE else DataType.UWORD
        val indexVar = loop.body.getLabelOrVariable(ForLoop.iteratorLoopcounterVarname) as VarDecl

        continueStmtLabelStack.push(continueLabel)
        breakStmtLabelStack.push(breakLabel)

        // set the index var to zero before the loop
        prog.instr(opcodePush(indexVarType), Value(indexVarType, 0))
        prog.instr(opcodePopvar(indexVarType), callLabel = indexVar.scopedname)

        // loop starts here
        prog.label(loopLabel)
        val assignTarget = if(loop.loopRegister!=null)
            AssignTarget(loop.loopRegister, null, null, null, loop.position)
        else
            AssignTarget(null, loop.loopVar!!.copy(), null, null, loop.position)
        val arrayspec = ArraySpec(IdentifierReference(listOf(ForLoop.iteratorLoopcounterVarname), loop.position), loop.position)
        val assignLv = Assignment(
                listOf(assignTarget), null,
                ArrayIndexedExpression((loop.iterable as IdentifierReference).copy(), arrayspec, loop.position),
                loop.position)
        assignLv.linkParents(loop.body)
        translate(assignLv)
        translate(loop.body)
        prog.label(continueLabel)

        prog.instr(opcodeIncvar(indexVarType), callLabel = indexVar.scopedname)

        // TODO: optimize edge cases if last value = 255 or 0 (for bytes) etc. to avoid  PUSH_BYTE / SUB opcodes and make use of the wrapping around of the value.
        prog.instr(opcodePush(indexVarType), Value(indexVarType, numElements))
        prog.instr(opcodePushvar(indexVarType), callLabel = indexVar.scopedname)
        prog.instr(opcodeSub(indexVarType))
        if(indexVarType==DataType.UWORD)
            prog.instr(Opcode.JNZW, callLabel = loopLabel)
        else
            prog.instr(Opcode.JNZ, callLabel = loopLabel)

        prog.label(breakLabel)
        prog.instr(Opcode.NOP)

        breakStmtLabelStack.pop()
        continueStmtLabelStack.pop()
    }

    private fun translateForOverConstantRange(varname: String, varDt: DataType, range: IntProgression, body: AnonymousScope) {
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

        prog.instr(opcodePush(varDt), Value(varDt, range.first))        // @todo use VariableInitializationStatement instead?? (like other for loop)
        prog.instr(opcodePopvar(varDt), callLabel = varname)
        prog.label(loopLabel)
        translate(body)
        prog.label(continueLabel)
        when {
            range.step==1 -> prog.instr(opcodeIncvar(varDt), callLabel = varname)
            range.step==-1 -> prog.instr(opcodeDecvar(varDt), callLabel = varname)
            range.step>1 -> {
                prog.instr(opcodePushvar(varDt), callLabel = varname)
                prog.instr(opcodePush(varDt), Value(varDt, range.step))
                prog.instr(opcodeAdd(varDt))
                prog.instr(opcodePopvar(varDt), callLabel = varname)
            }
            range.step<1 -> {
                prog.instr(opcodePushvar(varDt), callLabel = varname)
                prog.instr(opcodePush(varDt), Value(varDt, abs(range.step)))
                prog.instr(opcodeSub(varDt))
                prog.instr(opcodePopvar(varDt), callLabel = varname)
            }
        }

        // TODO: optimize edge cases if last value = 255 or 0 (for bytes) etc. to avoid  PUSH_BYTE / SUB opcodes and make use of the wrapping around of the value.
        // TODO: ubyte/uword can't count down to 0 with negative step because test value will be <0 which causes "value out of range" crash
        prog.instr(opcodePush(varDt), Value(varDt, range.last + range.step))
        prog.instr(opcodePushvar(varDt), callLabel = varname)
        prog.instr(opcodeSub(varDt))
        val loopvarJumpOpcode = when(varDt) {
            DataType.UBYTE, DataType.BYTE -> Opcode.JNZ
            DataType.UWORD, DataType.WORD -> Opcode.JNZW
            else -> throw CompilerException("invalid loop var datatype (expected byte or word) $varDt of var $varname")
        }
        prog.instr(loopvarJumpOpcode, callLabel = loopLabel)
        prog.label(breakLabel)
        prog.instr(Opcode.NOP)
        // note: ending value of loop register / variable is *undefined* after this point!

        breakStmtLabelStack.pop()
        continueStmtLabelStack.pop()
    }

    private fun translateForOverVariableRange(varname: List<String>?, register: Register?,
                                              range: RangeExpr, body: AnonymousScope) {
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
                AssignTarget(null, IdentifierReference(varname, range.position), null, null, range.position)
            else
                AssignTarget(register, null, null, null, range.position)
        }

        val startAssignment = Assignment(listOf(makeAssignmentTarget()), null, range.from, range.position)
        startAssignment.linkParents(body)
        translate(startAssignment)

        val loopLabel = makeLabel("loop")
        val continueLabel = makeLabel("continue")
        val breakLabel = makeLabel("break")
        val literalStepValue = (range.step as? LiteralValue)?.asNumericValue?.toInt()

        continueStmtLabelStack.push(continueLabel)
        breakStmtLabelStack.push(breakLabel)

        prog.label(loopLabel)
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
                    AnonymousScope(mutableListOf(Jump(null, null, breakLabel, range.position)), range.position),
                    AnonymousScope(mutableListOf(), range.position),
                    range.position)
            ifstmt.linkParents(body)
            translate(ifstmt)
        } else {
            // Step is a variable. We can't optimize anything...
            TODO("for loop with non-constant step comparison of LV, at: ${range.position}")
        }

        translate(body)
        prog.label(continueLabel)
        val lvTarget = makeAssignmentTarget()
        lvTarget.linkParents(body)
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
                postIncr.linkParents(body)
                translate(postIncr)
                if(lvTarget.register!=null)
                    prog.instr(Opcode.PUSH_VAR_BYTE, callLabel =lvTarget.register.toString())
                else {
                    val opcode = opcodePushvar(targetStatement!!.datatype)
                    prog.instr(opcode, callLabel = targetStatement.scopedname)
                }
                val branch = BranchStatement(
                        BranchCondition.NZ,
                        AnonymousScope(mutableListOf(Jump(null, null, loopLabel, range.position)), range.position),
                        AnonymousScope(mutableListOf(), range.position),
                        range.position)
                branch.linkParents(body)
                translate(branch)
            }
            -1 -> {
                // LV--
                val postIncr = PostIncrDecr(makeAssignmentTarget(), "--", range.position)
                postIncr.linkParents(body)
                translate(postIncr)
                TODO("signed numbers and/or special condition are needed for decreasing for loop. Try an increasing loop and/or constant loop values instead? At: ${range.position}")   // fix with signed numbers
            }
            else -> {
                TODO("non-literal-const or other-than-one step increment code At: ${range.position}")
            }
        }

        prog.label(breakLabel)
        prog.instr(Opcode.NOP)
        // note: ending value of loop register / variable is *undefined* after this point!

        breakStmtLabelStack.pop()
        continueStmtLabelStack.pop()
    }

    private fun translate(scope: AnonymousScope)  = translate(scope.statements)

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
         *      jnz/jnzw loop
         *  break:
         *      nop
         */
        val loopLabel = makeLabel("loop")
        val breakLabel = makeLabel("break")
        val continueLabel = makeLabel("continue")
        prog.line(stmt.position)
        breakStmtLabelStack.push(breakLabel)
        continueStmtLabelStack.push(continueLabel)
        prog.instr(Opcode.JUMP, callLabel = continueLabel)
        prog.label(loopLabel)
        translate(stmt.body)
        prog.label(continueLabel)
        translate(stmt.condition)
        val conditionJumpOpcode = when(stmt.condition.resultingDatatype(namespace, heap)) {
            DataType.UBYTE, DataType.BYTE -> Opcode.JNZ
            DataType.UWORD, DataType.WORD -> Opcode.JNZW
            else -> throw CompilerException("invalid condition datatype (expected byte or word) $stmt")
        }
        prog.instr(conditionJumpOpcode, callLabel = loopLabel)
        prog.label(breakLabel)
        prog.instr(Opcode.NOP)
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
         *      jz/jzw goto loop
         *  break:
         *      nop
         */
        val loopLabel = makeLabel("loop")
        val continueLabel = makeLabel("continue")
        val breakLabel = makeLabel("break")
        prog.line(stmt.position)
        breakStmtLabelStack.push(breakLabel)
        continueStmtLabelStack.push(continueLabel)
        prog.label(loopLabel)
        translate(stmt.body)
        prog.label(continueLabel)
        translate(stmt.untilCondition)
        val conditionJumpOpcode = when(stmt.untilCondition.resultingDatatype(namespace, heap)) {
            DataType.UBYTE, DataType.BYTE -> Opcode.JZ
            DataType.UWORD, DataType.WORD -> Opcode.JZW
            else -> throw CompilerException("invalid condition datatype (expected byte or word) $stmt")
        }
        prog.instr(conditionJumpOpcode, callLabel = loopLabel)
        prog.label(breakLabel)
        prog.instr(Opcode.NOP)
        breakStmtLabelStack.pop()
        continueStmtLabelStack.pop()
    }

    private fun translate(expr: TypecastExpression) {
        val funcTarget = (expr.expression as? IFunctionCall)?.target?.targetStatement(namespace)
        if(funcTarget is Subroutine &&
                funcTarget.asmReturnvaluesRegisters.isNotEmpty() &&
                funcTarget.asmReturnvaluesRegisters.all { it.stack!=true }) {
            throw CompilerException("cannot type cast a call to an asmsub that returns value in register - use a variable to store it first")
        }

        translate(expr.expression)
        val sourceDt = expr.expression.resultingDatatype(namespace, heap) ?: throw CompilerException("don't know what type to cast")
        if(sourceDt==expr.type)
            return

        when(expr.type) {
            DataType.UBYTE -> when(sourceDt) {
                DataType.UBYTE -> {}
                DataType.BYTE -> prog.instr(Opcode.CAST_B_TO_UB)
                DataType.UWORD-> prog.instr(Opcode.CAST_UW_TO_UB)
                DataType.WORD-> prog.instr(Opcode.CAST_W_TO_UB)
                DataType.FLOAT -> prog.instr(Opcode.CAST_F_TO_UB)
                else -> throw CompilerException("invalid cast type $sourceDt")
            }
            DataType.BYTE -> when(sourceDt) {
                DataType.UBYTE -> prog.instr(Opcode.CAST_UB_TO_B)
                DataType.BYTE -> {}
                DataType.UWORD -> prog.instr(Opcode.CAST_UW_TO_B)
                DataType.WORD -> prog.instr(Opcode.CAST_W_TO_B)
                DataType.FLOAT -> prog.instr(Opcode.CAST_F_TO_B)
                else -> throw CompilerException("invalid cast type $sourceDt")
            }
            DataType.UWORD -> when(sourceDt) {
                DataType.UBYTE -> prog.instr(Opcode.CAST_UB_TO_UW)
                DataType.BYTE -> prog.instr(Opcode.CAST_B_TO_UW)
                DataType.UWORD -> {}
                DataType.WORD -> prog.instr(Opcode.CAST_W_TO_UW)
                DataType.FLOAT -> prog.instr(Opcode.CAST_F_TO_UW)
                else -> throw CompilerException("invalid cast type $sourceDt")
            }
            DataType.WORD -> when(sourceDt) {
                DataType.UBYTE -> prog.instr(Opcode.CAST_UB_TO_W)
                DataType.BYTE -> prog.instr(Opcode.CAST_B_TO_W)
                DataType.UWORD -> prog.instr(Opcode.CAST_UW_TO_W)
                DataType.WORD -> {}
                DataType.FLOAT -> prog.instr(Opcode.CAST_F_TO_W)
                else -> throw CompilerException("invalid cast type $sourceDt")
            }
            DataType.FLOAT -> when(sourceDt) {
                DataType.UBYTE -> prog.instr(Opcode.CAST_UB_TO_F)
                DataType.BYTE -> prog.instr(Opcode.CAST_B_TO_F)
                DataType.UWORD -> prog.instr(Opcode.CAST_UW_TO_F)
                DataType.WORD -> prog.instr(Opcode.CAST_W_TO_F)
                DataType.FLOAT -> {}
                else -> throw CompilerException("invalid cast type $sourceDt")
            }
            else -> throw CompilerException("can't typecast to ${expr.type}")
        }
    }

    private fun translate(memread: DirectMemoryRead) {
        // for now, only a single memory location (ubyte) is read at a time.
        val address = memread.addressExpression.constValue(namespace, heap)?.asIntegerValue
        if(address!=null) {
            prog.instr(Opcode.PUSH_MEM_UB, arg = Value(DataType.UWORD, address))
        } else {
            translate(memread.addressExpression)
            prog.instr(Opcode.PUSH_MEMREAD)
        }
    }

    private fun translate(memwrite: DirectMemoryWrite) {
        // for now, only a single memory location (ubyte) is written at a time.
        val address = memwrite.addressExpression.constValue(namespace, heap)?.asIntegerValue
        if(address!=null) {
            prog.instr(Opcode.POP_MEM_BYTE, arg = Value(DataType.UWORD, address))
        } else {
            translate(memwrite.addressExpression)
            prog.instr(Opcode.POP_MEMWRITE)
        }
    }

}
