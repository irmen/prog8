package prog8.ast.expressions

import prog8.ast.IFunctionCall
import prog8.ast.INameScope
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.antlr.escape
import prog8.ast.base.*
import prog8.ast.processing.IAstModifyingVisitor
import prog8.ast.processing.IAstVisitor
import prog8.ast.statements.ArrayIndex
import prog8.ast.statements.BuiltinFunctionStatementPlaceholder
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl
import prog8.compiler.HeapValues
import prog8.compiler.IntegerOrAddressOf
import prog8.compiler.target.c64.Petscii
import prog8.functions.BuiltinFunctions
import prog8.functions.NotConstArgumentException
import prog8.functions.builtinFunctionReturnType
import kotlin.math.abs


val associativeOperators = setOf("+", "*", "&", "|", "^", "or", "and", "xor", "==", "!=")


sealed class Expression: Node {
    abstract fun constValue(program: Program): NumericLiteralValue?
    abstract fun accept(visitor: IAstModifyingVisitor): Expression
    abstract fun accept(visitor: IAstVisitor)
    abstract fun referencesIdentifiers(vararg name: String): Boolean     // todo: remove this here and move it into CallGraph instead
    abstract fun inferType(program: Program): DataType?

    infix fun isSameAs(other: Expression): Boolean {
        if(this===other)
            return true
        when(this) {
            is RegisterExpr ->
                return (other is RegisterExpr && other.register==register)
            is IdentifierReference ->
                return (other is IdentifierReference && other.nameInSource==nameInSource)
            is PrefixExpression ->
                return (other is PrefixExpression && other.operator==operator && other.expression isSameAs expression)
            is BinaryExpression ->
                return (other is BinaryExpression && other.operator==operator
                        && other.left isSameAs left
                        && other.right isSameAs right)
            is ArrayIndexedExpression -> {
                return (other is ArrayIndexedExpression && other.identifier.nameInSource == identifier.nameInSource
                        && other.arrayspec.index isSameAs arrayspec.index)
            }
            else -> return other==this
        }
    }
}


class PrefixExpression(val operator: String, var expression: Expression, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        expression.linkParents(this)
    }

    override fun constValue(program: Program): NumericLiteralValue? = null
    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun referencesIdentifiers(vararg name: String) = expression.referencesIdentifiers(*name)
    override fun inferType(program: Program): DataType? = expression.inferType(program)

    override fun toString(): String {
        return "Prefix($operator $expression)"
    }
}

class BinaryExpression(var left: Expression, var operator: String, var right: Expression, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        left.linkParents(this)
        right.linkParents(this)
    }

    override fun toString(): String {
        return "[$left $operator $right]"
    }

    // binary expression should actually have been optimized away into a single value, before const value was requested...
    override fun constValue(program: Program): NumericLiteralValue? = null

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun referencesIdentifiers(vararg name: String) = left.referencesIdentifiers(*name) || right.referencesIdentifiers(*name)
    override fun inferType(program: Program): DataType? {
        val leftDt = left.inferType(program)
        val rightDt = right.inferType(program)
        return when (operator) {
            "+", "-", "*", "**", "%" -> if (leftDt == null || rightDt == null) null else {
                try {
                    arithmeticOpDt(leftDt, rightDt)
                } catch (x: FatalAstException) {
                    null
                }
            }
            "/" -> if (leftDt == null || rightDt == null) null else divisionOpDt(leftDt, rightDt)
            "&" -> leftDt
            "|" -> leftDt
            "^" -> leftDt
            "and", "or", "xor",
            "<", ">",
            "<=", ">=",
            "==", "!=" -> DataType.UBYTE
            "<<", ">>" -> leftDt
            else -> throw FatalAstException("resulting datatype check for invalid operator $operator")
        }
    }

    companion object {
        fun divisionOpDt(leftDt: DataType, rightDt: DataType): DataType {
            return when (leftDt) {
                DataType.UBYTE -> when (rightDt) {
                    DataType.UBYTE, DataType.UWORD -> DataType.UBYTE
                    DataType.BYTE, DataType.WORD -> DataType.WORD
                    DataType.FLOAT -> DataType.BYTE
                    else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
                }
                DataType.BYTE -> when (rightDt) {
                    in NumericDatatypes -> DataType.BYTE
                    else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
                }
                DataType.UWORD -> when (rightDt) {
                    DataType.UBYTE, DataType.UWORD -> DataType.UWORD
                    DataType.BYTE, DataType.WORD -> DataType.WORD
                    DataType.FLOAT -> DataType.FLOAT
                    else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
                }
                DataType.WORD -> when (rightDt) {
                    in NumericDatatypes -> DataType.WORD
                    else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
                }
                DataType.FLOAT -> when (rightDt) {
                    in NumericDatatypes -> DataType.FLOAT
                    else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
                }
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
        }

        fun arithmeticOpDt(leftDt: DataType, rightDt: DataType): DataType {
            return when (leftDt) {
                DataType.UBYTE -> when (rightDt) {
                    DataType.UBYTE -> DataType.UBYTE
                    DataType.BYTE -> DataType.BYTE
                    DataType.UWORD -> DataType.UWORD
                    DataType.WORD -> DataType.WORD
                    DataType.FLOAT -> DataType.FLOAT
                    else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
                }
                DataType.BYTE -> when (rightDt) {
                    in ByteDatatypes -> DataType.BYTE
                    in WordDatatypes -> DataType.WORD
                    DataType.FLOAT -> DataType.FLOAT
                    else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
                }
                DataType.UWORD -> when (rightDt) {
                    DataType.UBYTE, DataType.UWORD -> DataType.UWORD
                    DataType.BYTE, DataType.WORD -> DataType.WORD
                    DataType.FLOAT -> DataType.FLOAT
                    else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
                }
                DataType.WORD -> when (rightDt) {
                    in IntegerDatatypes -> DataType.WORD
                    DataType.FLOAT -> DataType.FLOAT
                    else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
                }
                DataType.FLOAT -> when (rightDt) {
                    in NumericDatatypes -> DataType.FLOAT
                    else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
                }
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
        }
    }

    fun commonDatatype(leftDt: DataType, rightDt: DataType,
                       left: Expression, right: Expression): Pair<DataType, Expression?> {
        // byte + byte -> byte
        // byte + word -> word
        // word + byte -> word
        // word + word -> word
        // a combination with a float will be float (but give a warning about this!)

        if(this.operator=="/") {
            // division is a bit weird, don't cast the operands
            val commondt = divisionOpDt(leftDt, rightDt)
            return Pair(commondt, null)
        }

        return when (leftDt) {
            DataType.UBYTE -> {
                when (rightDt) {
                    DataType.UBYTE -> Pair(DataType.UBYTE, null)
                    DataType.BYTE -> Pair(DataType.BYTE, left)
                    DataType.UWORD -> Pair(DataType.UWORD, left)
                    DataType.WORD -> Pair(DataType.WORD, left)
                    DataType.FLOAT -> Pair(DataType.FLOAT, left)
                    else -> Pair(leftDt, null)      // non-numeric datatype
                }
            }
            DataType.BYTE -> {
                when (rightDt) {
                    DataType.UBYTE -> Pair(DataType.BYTE, right)
                    DataType.BYTE -> Pair(DataType.BYTE, null)
                    DataType.UWORD -> Pair(DataType.WORD, left)
                    DataType.WORD -> Pair(DataType.WORD, left)
                    DataType.FLOAT -> Pair(DataType.FLOAT, left)
                    else -> Pair(leftDt, null)      // non-numeric datatype
                }
            }
            DataType.UWORD -> {
                when (rightDt) {
                    DataType.UBYTE -> Pair(DataType.UWORD, right)
                    DataType.BYTE -> Pair(DataType.UWORD, right)
                    DataType.UWORD -> Pair(DataType.UWORD, null)
                    DataType.WORD -> Pair(DataType.WORD, left)
                    DataType.FLOAT -> Pair(DataType.FLOAT, left)
                    else -> Pair(leftDt, null)      // non-numeric datatype
                }
            }
            DataType.WORD -> {
                when (rightDt) {
                    DataType.UBYTE -> Pair(DataType.WORD, right)
                    DataType.BYTE -> Pair(DataType.WORD, right)
                    DataType.UWORD -> Pair(DataType.WORD, right)
                    DataType.WORD -> Pair(DataType.WORD, null)
                    DataType.FLOAT -> Pair(DataType.FLOAT, left)
                    else -> Pair(leftDt, null)      // non-numeric datatype
                }
            }
            DataType.FLOAT -> {
                Pair(DataType.FLOAT, right)
            }
            else -> Pair(leftDt, null)      // non-numeric datatype
        }
    }
}

class ArrayIndexedExpression(val identifier: IdentifierReference,
                             var arrayspec: ArrayIndex,
                             override val position: Position) : Expression() {
    override lateinit var parent: Node
    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier.linkParents(this)
        arrayspec.linkParents(this)
    }

    override fun constValue(program: Program): NumericLiteralValue? = null
    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun referencesIdentifiers(vararg name: String) = identifier.referencesIdentifiers(*name)

    override fun inferType(program: Program): DataType? {
        val target = identifier.targetStatement(program.namespace)
        if (target is VarDecl) {
            return when (target.datatype) {
                in NumericDatatypes -> null
                in StringDatatypes -> DataType.UBYTE
                in ArrayDatatypes -> ArrayElementTypes[target.datatype]
                else -> throw FatalAstException("invalid dt")
            }
        }
        return null
    }

    override fun toString(): String {
        return "ArrayIndexed(ident=$identifier, arraysize=$arrayspec; pos=$position)"
    }
}

class TypecastExpression(var expression: Expression, var type: DataType, val implicit: Boolean, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        expression.linkParents(this)
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun referencesIdentifiers(vararg name: String) = expression.referencesIdentifiers(*name)
    override fun inferType(program: Program): DataType? = type
    override fun constValue(program: Program): NumericLiteralValue? {
        val cv = expression.constValue(program) ?: return null
        return cv.cast(type)
        // val value = RuntimeValue(cv.type, cv.asNumericValue!!).cast(type)
        // return LiteralValue.fromNumber(value.numericValue(), value.type, position).cast(type)
    }

    override fun toString(): String {
        return "Typecast($expression as $type)"
    }
}

data class AddressOf(val identifier: IdentifierReference, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier.parent=this
    }

    var scopedname: String? = null     // will be set in a later state by the compiler
    override fun constValue(program: Program): NumericLiteralValue? = null
    override fun referencesIdentifiers(vararg name: String) = false
    override fun inferType(program: Program) = DataType.UWORD
    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
}

class DirectMemoryRead(var addressExpression: Expression, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        this.addressExpression.linkParents(this)
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun referencesIdentifiers(vararg name: String) = false
    override fun inferType(program: Program): DataType? = DataType.UBYTE
    override fun constValue(program: Program): NumericLiteralValue? = null

    override fun toString(): String {
        return "DirectMemoryRead($addressExpression)"
    }
}

class NumericLiteralValue(val type: DataType,    // only numerical types allowed
                          val number: Number,    // can be byte, word or float depending on the type
                          override val position: Position) : Expression() {
    override lateinit var parent: Node

    companion object {
        fun fromBoolean(bool: Boolean, position: Position) =
                NumericLiteralValue(DataType.UBYTE, if (bool) 1 else 0, position)

        fun optimalNumeric(value: Number, position: Position): NumericLiteralValue {
            return if(value is Double) {
                NumericLiteralValue(DataType.FLOAT, value, position)
            } else {
                when (val intval = value.toInt()) {
                    in 0..255 -> NumericLiteralValue(DataType.UBYTE, intval, position)
                    in -128..127 -> NumericLiteralValue(DataType.BYTE, intval, position)
                    in 0..65535 -> NumericLiteralValue(DataType.UWORD, intval, position)
                    in -32768..32767 -> NumericLiteralValue(DataType.WORD, intval, position)
                    else -> NumericLiteralValue(DataType.FLOAT, intval.toDouble(), position)
                }
            }
        }

        fun optimalInteger(value: Int, position: Position): NumericLiteralValue {
            return when (value) {
                in 0..255 -> NumericLiteralValue(DataType.UBYTE, value, position)
                in -128..127 -> NumericLiteralValue(DataType.BYTE, value, position)
                in 0..65535 -> NumericLiteralValue(DataType.UWORD, value, position)
                else -> throw FatalAstException("integer overflow: $value")
            }
        }
    }

    val asBooleanValue: Boolean = number!=0

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun referencesIdentifiers(vararg name: String) = false
    override fun constValue(program: Program) = this

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    override fun toString(): String = "NumericLiteral(${type.name}:$number)"

    override fun inferType(program: Program) = type

    override fun hashCode(): Int  = type.hashCode() * 31 xor number.hashCode()

    override fun equals(other: Any?): Boolean {
        if(other==null || other !is NumericLiteralValue)
            return false
        return number.toDouble()==other.number.toDouble()
    }

    operator fun compareTo(other: NumericLiteralValue): Int = number.toDouble().compareTo(other.number.toDouble())

    fun cast(targettype: DataType): NumericLiteralValue? {
        if(type==targettype)
            return this
        val numval = number.toDouble()
        when(type) {
            DataType.UBYTE -> {
                if(targettype== DataType.BYTE && numval <= 127)
                    return NumericLiteralValue(targettype, number.toShort(), position)
                if(targettype== DataType.WORD || targettype== DataType.UWORD)
                    return NumericLiteralValue(targettype, number.toInt(), position)
                if(targettype== DataType.FLOAT)
                    return NumericLiteralValue(targettype, number.toDouble(), position)
            }
            DataType.BYTE -> {
                if(targettype== DataType.UBYTE && numval >= 0)
                    return NumericLiteralValue(targettype, number.toShort(), position)
                if(targettype== DataType.UWORD && numval >= 0)
                    return NumericLiteralValue(targettype, number.toInt(), position)
                if(targettype== DataType.WORD)
                    return NumericLiteralValue(targettype, number.toInt(), position)
                if(targettype== DataType.FLOAT)
                    return NumericLiteralValue(targettype, number.toDouble(), position)
            }
            DataType.UWORD -> {
                if(targettype== DataType.BYTE && numval <= 127)
                    return NumericLiteralValue(targettype, number.toShort(), position)
                if(targettype== DataType.UBYTE && numval <= 255)
                    return NumericLiteralValue(targettype, number.toShort(), position)
                if(targettype== DataType.WORD && numval <= 32767)
                    return NumericLiteralValue(targettype, number.toInt(), position)
                if(targettype== DataType.FLOAT)
                    return NumericLiteralValue(targettype, number.toDouble(), position)
            }
            DataType.WORD -> {
                if(targettype== DataType.BYTE && numval >= -128 && numval <=127)
                    return NumericLiteralValue(targettype, number.toShort(), position)
                if(targettype== DataType.UBYTE && numval >= 0 && numval <= 255)
                    return NumericLiteralValue(targettype, number.toShort(), position)
                if(targettype== DataType.UWORD && numval >=0)
                    return NumericLiteralValue(targettype, number.toInt(), position)
                if(targettype== DataType.FLOAT)
                    return NumericLiteralValue(targettype, number.toDouble(), position)
            }
            DataType.FLOAT -> {
                if (targettype == DataType.BYTE && numval >= -128 && numval <=127)
                    return NumericLiteralValue(targettype, number.toShort(), position)
                if (targettype == DataType.UBYTE && numval >=0 && numval <= 255)
                    return NumericLiteralValue(targettype, number.toShort(), position)
                if (targettype == DataType.WORD && numval >= -32768 && numval <= 32767)
                    return NumericLiteralValue(targettype, number.toInt(), position)
                if (targettype == DataType.UWORD && numval >=0 && numval <= 65535)
                    return NumericLiteralValue(targettype, number.toInt(), position)
            }
            else -> {}
        }
        return null    // invalid type conversion from $this to $targettype
    }
}

class StructLiteralValue(var values: List<Expression>,
                         override val position: Position): Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent=parent
        values.forEach { it.linkParents(this) }
    }

    override fun constValue(program: Program): NumericLiteralValue?  = null
    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun referencesIdentifiers(vararg name: String) = values.any { it.referencesIdentifiers(*name) }
    override fun inferType(program: Program) = DataType.STRUCT

    override fun toString(): String {
        return "struct{ ${values.joinToString(", ")} }"
    }
}

class ReferenceLiteralValue(val type: DataType,     // only reference types allowed here
                            val str: String? = null,
                            val array: Array<Expression>? = null,
                            // actually, at the moment, we don't have struct literals in the language
                            initHeapId: Int? =null,
                            override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun referencesIdentifiers(vararg name: String) = array?.any { it.referencesIdentifiers(*name) } ?: false

    val isString = type in StringDatatypes
    val isArray = type in ArrayDatatypes
    var heapId = initHeapId
        private set

    init {
        when(type){
            in StringDatatypes ->
                if(str==null && heapId==null) throw FatalAstException("literal value missing strvalue/heapId")
            in ArrayDatatypes ->
                if(array==null && heapId==null) throw FatalAstException("literal value missing arrayvalue/heapId")
            else -> throw FatalAstException("invalid type $type")
        }
        if(array==null && str==null && heapId==null)
            throw FatalAstException("literal ref value without actual value")
    }

    override fun linkParents(parent: Node) {
        this.parent = parent
        array?.forEach {it.linkParents(this)}
    }

    override fun constValue(program: Program): NumericLiteralValue? {
        // note that we can't handle arrays that only contain constant numbers here anymore
        // so they're not treated as constants anymore
        return null
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    override fun toString(): String {
        val valueStr = when(type) {
            in StringDatatypes -> "'${escape(str!!)}'"
            in ArrayDatatypes -> "$array"
            else -> throw FatalAstException("weird ref type")
        }
        return "ReferenceValueLiteral($type, $valueStr)"
    }

    override fun inferType(program: Program) = type

    override fun hashCode(): Int {
        val sh = str?.hashCode() ?: 0x00014567
        val ah = array?.hashCode() ?: 0x11119876
        return sh * 31 xor ah xor type.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if(other==null || other !is ReferenceLiteralValue)
            return false
        if(isArray && other.isArray)
            return array!!.contentEquals(other.array!!) && heapId==other.heapId
        if(isString && other.isString)
            return str==other.str && heapId==other.heapId

        if(type!=other.type)
            return false

        return compareTo(other) == 0
    }

    operator fun compareTo(other: ReferenceLiteralValue): Int {
        throw ExpressionError("cannot order compare type $type with ${other.type}", other.position)
    }

    fun cast(targettype: DataType): ReferenceLiteralValue? {
        if(type==targettype)
            return this
        when(type) {
            in StringDatatypes -> {
                if(targettype in StringDatatypes)
                    return ReferenceLiteralValue(targettype, str, initHeapId = heapId, position = position)
            }
            else -> {}
        }
        return null    // invalid type conversion from $this to $targettype
    }

    fun addToHeap(heap: HeapValues) {
        if (heapId != null) return
        if (str != null) {
            heapId = heap.addString(type, str)
        }
        else if (array!=null) {
            if(array.any {it is AddressOf }) {
                val intArrayWithAddressOfs = array.map {
                    when (it) {
                        is AddressOf -> IntegerOrAddressOf(null, it)
                        is NumericLiteralValue -> IntegerOrAddressOf(it.number.toInt(), null)
                        else -> throw FatalAstException("invalid datatype in array")
                    }
                }
                heapId = heap.addIntegerArray(type, intArrayWithAddressOfs.toTypedArray())
            } else {
                val valuesInArray = array.map { (it as NumericLiteralValue).number }
                heapId = if(type== DataType.ARRAY_F) {
                    val doubleArray = valuesInArray.map { it.toDouble() }.toDoubleArray()
                    heap.addDoublesArray(doubleArray)
                } else {
                    val integerArray = valuesInArray.map { it.toInt() }
                    heap.addIntegerArray(type, integerArray.map { IntegerOrAddressOf(it, null) }.toTypedArray())
                }
            }
        }
    }
}

class RangeExpr(var from: Expression,
                var to: Expression,
                var step: Expression,
                override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        from.linkParents(this)
        to.linkParents(this)
        step.linkParents(this)
    }

    override fun constValue(program: Program): NumericLiteralValue? = null
    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun referencesIdentifiers(vararg name: String): Boolean  = from.referencesIdentifiers(*name) || to.referencesIdentifiers(*name)
    override fun inferType(program: Program): DataType? {
        val fromDt=from.inferType(program)
        val toDt=to.inferType(program)
        return when {
            fromDt==null || toDt==null -> null
            fromDt== DataType.UBYTE && toDt== DataType.UBYTE -> DataType.UBYTE
            fromDt== DataType.UWORD && toDt== DataType.UWORD -> DataType.UWORD
            fromDt== DataType.STR && toDt== DataType.STR -> DataType.STR
            fromDt== DataType.STR_S && toDt== DataType.STR_S -> DataType.STR_S
            fromDt== DataType.WORD || toDt== DataType.WORD -> DataType.WORD
            fromDt== DataType.BYTE || toDt== DataType.BYTE -> DataType.BYTE
            else -> DataType.UBYTE
        }
    }
    override fun toString(): String {
        return "RangeExpr(from $from, to $to, step $step, pos=$position)"
    }

    fun size(): Int? {
        val fromLv = (from as? NumericLiteralValue)
        val toLv = (to as? NumericLiteralValue)
        if(fromLv==null || toLv==null)
            return null
        return toConstantIntegerRange()?.count()
    }

    fun toConstantIntegerRange(): IntProgression? {
        val fromVal: Int
        val toVal: Int
        val fromRlv = from as? ReferenceLiteralValue
        val toRlv = to as? ReferenceLiteralValue
        if(fromRlv!=null && fromRlv.isString && toRlv!=null && toRlv.isString) {
            // string range -> int range over petscii values
            fromVal = Petscii.encodePetscii(fromRlv.str!!, true)[0].toInt()
            toVal = Petscii.encodePetscii(toRlv.str!!, true)[0].toInt()
        } else {
            val fromLv = from as? NumericLiteralValue
            val toLv = to as? NumericLiteralValue
            if(fromLv==null || toLv==null)
                return null         // non-constant range
            // integer range
            fromVal = fromLv.number.toInt()
            toVal = toLv.number.toInt()
        }
        val stepVal = (step as? NumericLiteralValue)?.number?.toInt() ?: 1
        return when {
            fromVal <= toVal -> when {
                stepVal <= 0 -> IntRange.EMPTY
                stepVal == 1 -> fromVal..toVal
                else -> fromVal..toVal step stepVal
            }
            else -> when {
                stepVal >= 0 -> IntRange.EMPTY
                stepVal == -1 -> fromVal downTo toVal
                else -> fromVal downTo toVal step abs(stepVal)
            }
        }
    }
}

class RegisterExpr(val register: Register, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun constValue(program: Program): NumericLiteralValue? = null
    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun referencesIdentifiers(vararg name: String): Boolean = register.name in name
    override fun toString(): String {
        return "RegisterExpr(register=$register, pos=$position)"
    }

    override fun inferType(program: Program) = DataType.UBYTE
}

data class IdentifierReference(val nameInSource: List<String>, override val position: Position) : Expression() {
    override lateinit var parent: Node

    fun targetStatement(namespace: INameScope) =
        if(nameInSource.size==1 && nameInSource[0] in BuiltinFunctions)
            BuiltinFunctionStatementPlaceholder(nameInSource[0], position)
        else
            namespace.lookup(nameInSource, this)

    fun targetVarDecl(namespace: INameScope): VarDecl? = targetStatement(namespace) as? VarDecl
    fun targetSubroutine(namespace: INameScope): Subroutine? = targetStatement(namespace) as? Subroutine

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun constValue(program: Program): NumericLiteralValue? {
        val node = program.namespace.lookup(nameInSource, this)
                ?: throw UndefinedSymbolError(this)
        val vardecl = node as? VarDecl
        if(vardecl==null) {
            return null
        } else if(vardecl.type!= VarDeclType.CONST) {
            return null
        }
        return vardecl.value?.constValue(program)
    }

    override fun toString(): String {
        return "IdentifierRef($nameInSource)"
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun referencesIdentifiers(vararg name: String): Boolean = nameInSource.last() in name   // @todo is this correct all the time?

    override fun inferType(program: Program): DataType? {
        val targetStmt = targetStatement(program.namespace)
        if(targetStmt is VarDecl) {
            return targetStmt.datatype
        } else {
            throw FatalAstException("cannot get datatype from identifier reference ${this}, pos=$position")
        }
    }

    fun memberOfStruct(namespace: INameScope) = this.targetVarDecl(namespace)?.struct

    fun heapId(namespace: INameScope): Int {
        val node = namespace.lookup(nameInSource, this) ?: throw UndefinedSymbolError(this)
        val value = (node as? VarDecl)?.value ?: throw FatalAstException("requires a reference value")
        return when (value) {
            is IdentifierReference -> value.heapId(namespace)
            is ReferenceLiteralValue -> value.heapId ?: throw FatalAstException("refLv is not on the heap: $value")
            else -> throw FatalAstException("requires a reference value")
        }
    }
}

class FunctionCall(override var target: IdentifierReference,
                   override var arglist: MutableList<Expression>,
                   override val position: Position) : Expression(), IFunctionCall {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        arglist.forEach { it.linkParents(this) }
    }

    override fun constValue(program: Program) = constValue(program, true)

    private fun constValue(program: Program, withDatatypeCheck: Boolean): NumericLiteralValue? {
        // if the function is a built-in function and the args are consts, should try to const-evaluate!
        // lenghts of arrays and strings are constants that are determined at compile time!
        if(target.nameInSource.size>1) return null
        try {
            var resultValue: NumericLiteralValue? = null
            val func = BuiltinFunctions[target.nameInSource[0]]
            if(func!=null) {
                val exprfunc = func.constExpressionFunc
                if(exprfunc!=null)
                    resultValue = exprfunc(arglist, position, program)
                else if(func.returntype==null)
                    throw ExpressionError("builtin function ${target.nameInSource[0]} can't be used here because it doesn't return a value", position)
            }

            if(withDatatypeCheck) {
                val resultDt = this.inferType(program)
                if(resultValue==null || resultDt == resultValue.type)
                    return resultValue
                throw FatalAstException("evaluated const expression result value doesn't match expected datatype $resultDt, pos=$position")
            } else {
                return resultValue
            }
        }
        catch(x: NotConstArgumentException) {
            // const-evaluating the builtin function call failed.
            return null
        }
    }

    override fun toString(): String {
        return "FunctionCall(target=$target, pos=$position)"
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun referencesIdentifiers(vararg name: String): Boolean = target.referencesIdentifiers(*name) || arglist.any{it.referencesIdentifiers(*name)}

    override fun inferType(program: Program): DataType? {
        val constVal = constValue(program ,false)
        if(constVal!=null)
            return constVal.type
        val stmt = target.targetStatement(program.namespace) ?: return null
        when (stmt) {
            is BuiltinFunctionStatementPlaceholder -> {
                if(target.nameInSource[0] == "set_carry" || target.nameInSource[0]=="set_irqd" ||
                        target.nameInSource[0] == "clear_carry" || target.nameInSource[0]=="clear_irqd") {
                    return null // these have no return value
                }
                return builtinFunctionReturnType(target.nameInSource[0], this.arglist, program)
            }
            is Subroutine -> {
                if(stmt.returntypes.isEmpty())
                    return null     // no return value
                if(stmt.returntypes.size==1)
                    return stmt.returntypes[0]
                return null     // has multiple return types... so not a single resulting datatype possible
            }
            else -> return null
        }
    }
}
