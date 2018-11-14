package prog8.compiler

import prog8.ast.*
import prog8.compiler.intermediate.IntermediateProgram
import prog8.compiler.intermediate.Opcode
import prog8.compiler.intermediate.Value
import prog8.stackvm.Syscall
import prog8.stackvm.VmExecutionException
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

    fun allStrings() = heap.asSequence().withIndex().filter { it.value.str!=null }.toList()
    fun allArrays() = heap.asSequence().withIndex().filter { it.value.array!=null }.toList()
    fun allDoubleArrays() = heap.asSequence().withIndex().filter { it.value.doubleArray!=null }.toList()
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
        prog.newBlock(block.scopedname, block.name, block.address)
        processVariables(block)         // @todo optimize initializations with same value: load the value only once
        prog.label(block.scopedname)
        prog.line(block.position)
        translate(block.statements)
        return super.process(block)
    }

    private fun processVariables(scope: INameScope) {
        for(variable in scope.statements.asSequence().filter {it is VarDecl }.map { it as VarDecl })
            prog.variable(variable.scopedname, variable)
        for(subscope in scope.subScopes())
            processVariables(subscope.value)
    }

    override fun process(subroutine: Subroutine): IStatement {
        if(subroutine.asmAddress==null) {
            prog.label(subroutine.scopedname)
            prog.line(subroutine.position)
            // note: the caller has already written the arguments into the subroutine's parameter variables.
            translate(subroutine.statements)
        } else {
            // asmsub
            if(subroutine.isNotEmpty())
                throw CompilerException("kernel subroutines (with memory address) can't have a body: $subroutine")

            prog.symbolDef(subroutine.scopedname, subroutine.asmAddress)
        }
        return super.process(subroutine)
    }

    override fun process(directive: Directive): IStatement {
        when(directive.directive) {
            "%asminclude" -> throw CompilerException("can't use %asminclude in stackvm")
            "%asmbinary" -> throw CompilerException("can't use %asmbinary in stackvm")
            "%breakpoint" -> {
                prog.line(directive.position)
                prog.instr(Opcode.BREAKPOINT)
            }
        }
        return super.process(directive)
    }

    private fun translate(statements: List<IStatement>) {
        for (stmt: IStatement in statements) {
            generatedLabelSequenceNumber++
            when (stmt) {
                is Label -> translate(stmt)
                is Return -> translate(stmt)
                is VariableInitializationAssignment -> translate(stmt)        // for initializing vars in a scope
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
                is AnonymousScope -> translate(stmt)
                is Directive, is VarDecl, is Subroutine -> {}   // skip this, already processed these.
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
            DataType.ARRAY_B, DataType.ARRAY_W -> Opcode.PUSH_VAR_WORD
        }
    }

    private fun opcodeReadindexedvar(dt: DataType): Opcode {
        return when (dt)  {
            DataType.ARRAY_UB, DataType.ARRAY_B -> Opcode.READ_INDEXED_VAR_BYTE
            DataType.ARRAY_UW, DataType.ARRAY_W -> Opcode.READ_INDEXED_VAR_WORD
            DataType.ARRAY_F -> Opcode.READ_INDEXED_VAR_FLOAT
            DataType.STR, DataType.STR_S -> Opcode.READ_INDEXED_VAR_BYTE
            DataType.STR_P, DataType.STR_PS -> throw CompilerException("cannot access pascal-string type $dt with index")
            else -> throw CompilerException("invalid dt for indexed access $dt")
        }
    }

    private fun opcodeWriteindexedvar(dt: DataType): Opcode {
        return when (dt)  {
            DataType.ARRAY_UB, DataType.ARRAY_B -> Opcode.WRITE_INDEXED_VAR_BYTE
            DataType.ARRAY_UW, DataType.ARRAY_W -> Opcode.WRITE_INDEXED_VAR_WORD
            DataType.ARRAY_F -> Opcode.WRITE_INDEXED_VAR_FLOAT
            DataType.STR, DataType.STR_S -> Opcode.WRITE_INDEXED_VAR_BYTE
            DataType.STR_P, DataType.STR_PS -> throw CompilerException("cannot access pascal-string type $dt with index")
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
         * @todo generate more efficient bytecode for the form with just jumps: if_xx goto .. [else goto ..] ?
         *  -> this should translate into just a single branch opcode per goto
         */
        prog.line(branch.position)
        val labelElse = makeLabel("else")
        val labelEnd = makeLabel("end")
        val opcode = when(branch.condition) {
            BranchCondition.CS -> Opcode.BCC
            BranchCondition.CC -> Opcode.BCS
            BranchCondition.EQ, BranchCondition.Z -> Opcode.BNZ
            BranchCondition.NE, BranchCondition.NZ -> Opcode.BZ
            BranchCondition.VS -> Opcode.BVC
            BranchCondition.VC -> Opcode.BVS
            BranchCondition.MI, BranchCondition.NEG -> Opcode.BPOS
            BranchCondition.PL, BranchCondition.POS -> Opcode.BNEG
        }
        if(branch.elsepart.isEmpty()) {
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

    private fun makeLabel(postfix: String): String {
        generatedLabelSequenceNumber++
        return "_prog8stmt_${generatedLabelSequenceNumber}_$postfix"
    }

    private fun translate(stmt: IfStatement) {
        /*
         * An IF statement: IF (condition-expression) { stuff } else { other_stuff }
         * Which is translated into:
         *      <condition-expression evaluation>
         *      TEST
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
         *      TEST
         *      BZ _stmt_999_end
         *      stuff
         * _stmt_999_end:
         *      nop
         *
         * @todo generate more efficient bytecode for the form with just jumps: if(..) goto .. [else goto ..]
         */
        prog.line(stmt.position)
        translate(stmt.condition)
        val labelEnd = makeLabel("end")
        if(stmt.elsepart.isEmpty()) {
            prog.instr(Opcode.TEST)
            prog.instr(Opcode.BZ, callLabel = labelEnd)
            translate(stmt.truepart)
            prog.label(labelEnd)
        } else {
            val labelElse = makeLabel("else")
            prog.instr(Opcode.TEST)
            prog.instr(Opcode.BZ, callLabel = labelElse)
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

        val floatWarning = "byte or word value implicitly converted to float. Suggestion: use explicit flt() conversion, a float number, or revert to integer arithmetic"

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
                val commonDt = commonDatatype(leftDt, rightDt, expr.left.position, expr.right.position)
                translate(expr.left)
                if(leftDt!=commonDt)
                    convertType(leftDt, commonDt)
                translate(expr.right)
                if(rightDt!=commonDt)
                    convertType(rightDt, commonDt)
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
            is RangeExpr -> {
                TODO("TRANSLATE range $expr")
            }
            else -> {
                val lv = expr.constValue(namespace, heap) ?: throw CompilerException("constant expression required, not $expr")
                when(lv.type) {
                    DataType.UBYTE, DataType.BYTE -> prog.instr(Opcode.PUSH_BYTE, Value(lv.type, lv.bytevalue!!))
                    DataType.UWORD, DataType.WORD -> prog.instr(Opcode.PUSH_WORD, Value(lv.type, lv.wordvalue!!))
                    DataType.FLOAT -> prog.instr(Opcode.PUSH_FLOAT, Value(lv.type, lv.floatvalue!!))
                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                        if(lv.heapId==null)
                            throw CompilerException("string should have been moved into heap   ${lv.position}")
                        prog.instr(Opcode.PUSH_WORD, Value(lv.type, lv.heapId))
                    }
                    DataType.ARRAY_UB, DataType.ARRAY_UW, DataType.ARRAY_F,
                    DataType.ARRAY_B, DataType.ARRAY_W -> {
                        if(lv.heapId==null)
                            throw CompilerException("array should have been moved into heap  ${lv.position}")
                        prog.instr(Opcode.PUSH_WORD, Value(lv.type, lv.heapId))
                    }
                }
            }
        }
    }

    private fun convertType(givenDt: DataType, targetDt: DataType) {
        // only WIDENS a type, never NARROWS
        if(givenDt==targetDt)
            return
        if(givenDt !in NumericDatatypes)
            throw CompilerException("converting non-numeric $givenDt")
        if(targetDt !in NumericDatatypes)
            throw CompilerException("converting $givenDt to non-numeric $targetDt")
        when(givenDt) {
            DataType.UBYTE -> when(targetDt) {
                DataType.UWORD, DataType.WORD -> prog.instr(Opcode.UB2UWORD)
                DataType.FLOAT -> prog.instr(Opcode.UB2FLOAT)
                else -> {}
            }
            DataType.BYTE -> when(targetDt) {
                DataType.UWORD, DataType.WORD -> prog.instr(Opcode.B2WORD)
                DataType.FLOAT -> prog.instr(Opcode.B2FLOAT)
                else -> {}
            }
            DataType.UWORD -> when(targetDt) {
                DataType.UBYTE, DataType.BYTE -> throw CompilerException("narrowing type")
                DataType.FLOAT -> prog.instr(Opcode.UW2FLOAT)
                else -> {}
            }
            DataType.WORD -> when(targetDt) {
                DataType.UBYTE, DataType.BYTE -> throw CompilerException("narrowing type")
                DataType.FLOAT -> prog.instr(Opcode.W2FLOAT)
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
                            else -> TODO("invalid datatype for memory variable expression: $target")
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
                // make sure we clean up the unused result values from the stack.
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
            "flt" -> {
                // 1 argument, type determines the exact opcode to use
                val arg = args.single()
                when (arg.resultingDatatype(namespace, heap)) {
                    DataType.UBYTE -> prog.instr(Opcode.UB2FLOAT)
                    DataType.BYTE -> prog.instr(Opcode.B2FLOAT)
                    DataType.UWORD -> prog.instr(Opcode.UW2FLOAT)
                    DataType.WORD -> prog.instr(Opcode.W2FLOAT)
                    DataType.FLOAT -> prog.instr(Opcode.NOP)
                    else -> throw CompilerException("wrong datatype for flt()")
                }
            }
            "msb" -> prog.instr(Opcode.MSB)
            "lsb" -> prog.instr(Opcode.LSB)
            "b2ub" -> prog.instr(Opcode.B2UB)
            "ub2b" -> prog.instr(Opcode.UB2B)
            "lsl" -> {
                val arg = args.single()
                val dt = arg.resultingDatatype(namespace, heap)
                when (dt) {
                    DataType.UBYTE -> prog.instr(Opcode.SHL_BYTE)
                    DataType.UWORD -> prog.instr(Opcode.SHL_WORD)
                    else -> throw CompilerException("wrong datatype")
                }
                // this function doesn't return a value on the stack so we pop it directly into the argument register/variable again
                popValueIntoTarget(AssignTarget.fromExpr(arg), dt)
            }
            "lsr" -> {
                val arg = args.single()
                val dt = arg.resultingDatatype(namespace, heap)
                when (dt) {
                    DataType.UBYTE -> prog.instr(Opcode.SHR_BYTE)
                    DataType.UWORD -> prog.instr(Opcode.SHR_WORD)
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
        prog.line(callPosition)
        for(arg in arguments.zip(subroutine.parameters)) {
            translate(arg.first)
            val opcode=opcodePopvar(arg.second.type)
            prog.instr(opcode, callLabel = subroutine.scopedname+"."+arg.second.name)
        }
        prog.instr(Opcode.CALL, callLabel=subroutine.scopedname)
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
                when(dt) {
                    DataType.UBYTE -> Opcode.DIV_UB
                    DataType.BYTE -> Opcode.DIV_B
                    DataType.UWORD -> Opcode.DIV_UW
                    DataType.WORD -> Opcode.DIV_W
                    DataType.FLOAT -> Opcode.DIV_F
                    else -> throw CompilerException("only byte/word/float possible")
                }
            }
            "//" -> {
                when(dt) {
                    DataType.UBYTE -> Opcode.FLOORDIV_UB
                    DataType.BYTE -> Opcode.FLOORDIV_B
                    DataType.UWORD -> Opcode.FLOORDIV_UW
                    DataType.WORD -> Opcode.FLOORDIV_W
                    DataType.FLOAT -> Opcode.FLOORDIV_F
                    else -> throw CompilerException("only byte/word/float possible")
                }
            }
            "%" -> {
                when(dt) {
                    DataType.UBYTE -> Opcode.REMAINDER_UB
                    DataType.BYTE -> Opcode.REMAINDER_B
                    DataType.UWORD -> Opcode.REMAINDER_UW
                    DataType.WORD -> Opcode.REMAINDER_W
                    DataType.FLOAT -> Opcode.REMAINDER_F
                    else -> throw CompilerException("only byte/word/float possible")
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
        val variable = arrayindexed.identifier?.targetStatement(namespace) as VarDecl
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

    private fun translate(stmt: Jump) {
        var jumpAddress: Value? = null
        var jumpLabel: String? = null

        when {
            stmt.generatedLabel!=null -> jumpLabel = stmt.generatedLabel
            stmt.address!=null -> jumpAddress = Value(DataType.UWORD, stmt.address)
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
        prog.instr(Opcode.JUMP, jumpAddress, jumpLabel)
    }

    private fun translate(stmt: PostIncrDecr) {
        prog.line(stmt.position)
        when {
            stmt.target.register!=null -> when(stmt.operator) {
                "++" -> prog.instr(Opcode.INC_VAR_UB, callLabel = stmt.target.register.toString())
                "--" -> prog.instr(Opcode.DEC_VAR_UB, callLabel = stmt.target.register.toString())
            }
            stmt.target.identifier!=null -> {
                val targetStatement = stmt.target.identifier!!.targetStatement(namespace) as VarDecl
                when(stmt.operator) {
                    "++" -> prog.instr(opcodeIncvar(targetStatement.datatype), callLabel = targetStatement.scopedname)
                    "--" -> prog.instr(opcodeDecvar(targetStatement.datatype), callLabel = targetStatement.scopedname)
                }
            }
            stmt.target.arrayindexed!=null -> {
                // todo: generate more efficient bytecode for this?
                translate(stmt.target.arrayindexed!!, false)
                val one = Value(stmt.target.arrayindexed!!.resultingDatatype(namespace, heap)!!, 1)
                val opcode = opcodePush(one.type)
                prog.instr(opcode, one)
                when(stmt.operator) {
                    "++" -> prog.instr(opcodeAdd(one.type))
                    "--" -> prog.instr(opcodeSub(one.type))
                }
                translate(stmt.target.arrayindexed!!, true)
            }
            else -> throw CompilerException("very strange postincrdecr")
        }
    }


    private fun translate(stmt: VariableInitializationAssignment) {
        // this is an assignment to initialize a variable's value in the scope.
        // the compiler can perhaps optimize this phase.
        // todo: optimize variable init by keeping track of the block of init values so it can be copied as a whole
        translate(stmt as Assignment)
    }

    private fun translate(stmt: Assignment) {
        val assignTarget= stmt.singleTarget ?: throw CompilerException("cannot use assignment to multiple assignment targets ${stmt.position}")
        prog.line(stmt.position)
        translate(stmt.value)
        val valueDt = stmt.value.resultingDatatype(namespace, heap)
        val targetDt = assignTarget.determineDatatype(namespace, heap, stmt)
        if(valueDt!=targetDt) {
            // convert value to target datatype if possible
            when(targetDt) {
                DataType.UBYTE, DataType.BYTE ->
                    throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                DataType.UWORD, DataType.WORD -> {
                    when (valueDt) {
                        DataType.UBYTE -> prog.instr(Opcode.UB2UWORD)
                        DataType.BYTE -> prog.instr(Opcode.B2WORD)
                        else -> throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                    }
                }
                DataType.FLOAT -> {
                    when (valueDt) {
                        DataType.UBYTE -> prog.instr(Opcode.UB2FLOAT)
                        DataType.BYTE -> prog.instr(Opcode.B2FLOAT)
                        DataType.UWORD -> prog.instr(Opcode.UW2FLOAT)
                        DataType.WORD -> prog.instr(Opcode.W2FLOAT)
                        else -> throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                    }
                }
                // todo: maybe if you assign byte or word to arrayspec, clear it with that value?
                DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W, DataType.ARRAY_F -> throw CompilerException("incompatible data types valueDt=$valueDt  targetDt=$targetDt  at $stmt")
                null -> throw CompilerException("could not determine targetdt")
            }
        }

        if(stmt.aug_op!=null) {
            // augmented assignment
            when {
                assignTarget.identifier!=null -> {
                    val target = assignTarget.identifier.targetStatement(namespace)!!
                    when(target) {
                        is VarDecl -> {
                            val opcode = opcodePushvar(assignTarget.determineDatatype(namespace, heap, stmt)!!)
                            prog.instr(opcode, callLabel = target.scopedname)
                        }
                        else -> throw CompilerException("invalid assignment target type ${target::class}")
                    }
                }
                assignTarget.register!=null -> prog.instr(Opcode.PUSH_VAR_BYTE, callLabel = assignTarget.register.toString())
                assignTarget.arrayindexed!=null -> translate(assignTarget.arrayindexed, false)
            }

            translateAugAssignOperator(stmt.aug_op, stmt.value.resultingDatatype(namespace, heap))
        }

        // pop the result value back into the assignment target
        val datatype = assignTarget.determineDatatype(namespace, heap, stmt)!!
        popValueIntoTarget(assignTarget, datatype)
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
        }
    }

    private fun translateAugAssignOperator(aug_op: String, valueDt: DataType?) {
        if(valueDt==null)
            throw CompilerException("value datatype not known")
        val validDt = setOf(DataType.UBYTE, DataType.UWORD, DataType.FLOAT)
        if(valueDt !in validDt)
            throw CompilerException("invalid datatype(s) for operand(s)")
        val opcode = when(aug_op) {
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
            "/=" -> {
                when (valueDt) {
                    DataType.UBYTE -> Opcode.DIV_UB
                    DataType.UWORD -> Opcode.DIV_UW
                    DataType.FLOAT -> Opcode.DIV_F
                    else -> throw CompilerException("only byte/word/lfoat possible")
                }
            }
            "//=" -> {
                when (valueDt) {
                    DataType.UBYTE -> Opcode.FLOORDIV_UB
                    DataType.UWORD -> Opcode.FLOORDIV_UW
                    DataType.FLOAT -> Opcode.FLOORDIV_F
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
            DataType.STR,
            DataType.STR_P,
            DataType.STR_S,
            DataType.STR_PS -> {
                numElements = iterableValue.strvalue?.length ?: heap.get(iterableValue.heapId!!).str!!.length
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
            throw CompilerException("loopVar cannot use X register because it is needed as internal index")

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

        val zero = Value(if (numElements <= 255) DataType.UBYTE else DataType.UWORD, 0)
        prog.instr(opcodePush(zero.type), zero)
        prog.instr(opcodePopvar(zero.type), callLabel = "X")
        prog.label(loopLabel)
        val assignTarget = if(loop.loopRegister!=null)
            AssignTarget(loop.loopRegister, null, null, loop.position)
        else
            AssignTarget(null, loop.loopVar!!.copy(), null, loop.position)
        val arrayspec = ArraySpec(RegisterExpr(Register.X, loop.position), loop.position)
        val assignLv = Assignment(listOf(assignTarget), null, ArrayIndexedExpression((loop.iterable as IdentifierReference).copy(), arrayspec, loop.position), loop.position)
        assignLv.linkParents(loop.body)
        translate(assignLv)
        translate(loop.body)
        prog.label(continueLabel)
        prog.instr(opcodeIncvar(zero.type), callLabel = "X")

        // TODO: optimize edge cases if last value = 255 or 0 (for bytes) etc. to avoid  PUSH_BYTE / SUB opcodes and make use of the wrapping around of the value.
        prog.instr(opcodePush(zero.type), Value(zero.type, numElements))
        prog.instr(opcodePushvar(zero.type), callLabel = "X")
        prog.instr(opcodeSub(zero.type))
        prog.instr(Opcode.TEST)
        prog.instr(Opcode.BNZ, callLabel = loopLabel)

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

        prog.instr(opcodePush(varDt), Value(varDt, range.first))
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
                prog.instr(opcodeSub(varDt))
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
        prog.instr(opcodePush(varDt), Value(varDt, range.last + range.step))
        prog.instr(opcodePushvar(varDt), callLabel = varname)
        prog.instr(opcodeSub(varDt))
        prog.instr(Opcode.TEST)
        prog.instr(Opcode.BNZ, callLabel = loopLabel)

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
                AssignTarget(null, IdentifierReference(varname, range.position), null, range.position)
            else
                AssignTarget(register, null, null, range.position)
        }

        val startAssignment = Assignment(listOf(makeAssignmentTarget()), null, range.from, range.position)
        startAssignment.linkParents(range.parent)
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
            ifstmt.linkParents(range.parent)
            translate(ifstmt)
        } else {
            // Step is a variable. We can't optimize anything...
            TODO("for loop with non-constant step comparison of LV, at: ${range.position}")
        }

        translate(body)
        prog.label(continueLabel)
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
         *      test
         *      bnz loop
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
        prog.instr(Opcode.TEST)
        prog.instr(Opcode.BNZ, callLabel = loopLabel)
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
         *      test
         *      bz goto loop
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
        prog.instr(Opcode.TEST)
        prog.instr(Opcode.BZ, callLabel = loopLabel)
        prog.label(breakLabel)
        prog.instr(Opcode.NOP)
        breakStmtLabelStack.pop()
        continueStmtLabelStack.pop()
    }
}
