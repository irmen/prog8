package prog8.ast.expressions

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.processing.IAstModifyingVisitor
import prog8.ast.processing.IAstVisitor
import prog8.ast.statements.*
import prog8.compiler.HeapValues
import prog8.compiler.IntegerOrAddressOf
import prog8.compiler.target.c64.Petscii
import prog8.functions.BuiltinFunctions
import prog8.functions.NotConstArgumentException
import prog8.functions.builtinFunctionReturnType
import kotlin.math.abs
import kotlin.math.floor


val associativeOperators = setOf("+", "*", "&", "|", "^", "or", "and", "xor", "==", "!=")


class PrefixExpression(val operator: String, var expression: IExpression, override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        expression.linkParents(this)
    }

    override fun constValue(program: Program): LiteralValue? = null
    override fun accept(processor: IAstModifyingVisitor) = processor.visit(this)
    override fun accept(processor: IAstVisitor) = processor.visit(this)
    override fun referencesIdentifier(name: String) = expression.referencesIdentifier(name)
    override fun inferType(program: Program): DataType? = expression.inferType(program)

    override fun toString(): String {
        return "Prefix($operator $expression)"
    }
}

class BinaryExpression(var left: IExpression, var operator: String, var right: IExpression, override val position: Position) : IExpression {
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
    override fun constValue(program: Program): LiteralValue? = null

    override fun accept(processor: IAstModifyingVisitor) = processor.visit(this)
    override fun accept(processor: IAstVisitor) = processor.visit(this)
    override fun referencesIdentifier(name: String) = left.referencesIdentifier(name) || right.referencesIdentifier(name)
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
                       left: IExpression, right: IExpression): Pair<DataType, IExpression?> {
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
                    else -> throw FatalAstException("non-numeric datatype $rightDt")
                }
            }
            DataType.BYTE -> {
                when (rightDt) {
                    DataType.UBYTE -> Pair(DataType.BYTE, right)
                    DataType.BYTE -> Pair(DataType.BYTE, null)
                    DataType.UWORD -> Pair(DataType.WORD, left)
                    DataType.WORD -> Pair(DataType.WORD, left)
                    DataType.FLOAT -> Pair(DataType.FLOAT, left)
                    else -> throw FatalAstException("non-numeric datatype $rightDt")
                }
            }
            DataType.UWORD -> {
                when (rightDt) {
                    DataType.UBYTE -> Pair(DataType.UWORD, right)
                    DataType.BYTE -> Pair(DataType.UWORD, right)
                    DataType.UWORD -> Pair(DataType.UWORD, null)
                    DataType.WORD -> Pair(DataType.WORD, left)
                    DataType.FLOAT -> Pair(DataType.FLOAT, left)
                    else -> throw FatalAstException("non-numeric datatype $rightDt")
                }
            }
            DataType.WORD -> {
                when (rightDt) {
                    DataType.UBYTE -> Pair(DataType.WORD, right)
                    DataType.BYTE -> Pair(DataType.WORD, right)
                    DataType.UWORD -> Pair(DataType.WORD, right)
                    DataType.WORD -> Pair(DataType.WORD, null)
                    DataType.FLOAT -> Pair(DataType.FLOAT, left)
                    else -> throw FatalAstException("non-numeric datatype $rightDt")
                }
            }
            DataType.FLOAT -> {
                Pair(DataType.FLOAT, right)
            }
            else -> throw FatalAstException("non-numeric datatype $leftDt")
        }
    }
}

class ArrayIndexedExpression(val identifier: IdentifierReference,
                             var arrayspec: ArrayIndex,
                             override val position: Position) : IExpression {
    override lateinit var parent: Node
    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier.linkParents(this)
        arrayspec.linkParents(this)
    }

    override fun constValue(program: Program): LiteralValue? = null
    override fun accept(processor: IAstModifyingVisitor) = processor.visit(this)
    override fun accept(processor: IAstVisitor) = processor.visit(this)
    override fun referencesIdentifier(name: String) = identifier.referencesIdentifier(name)

    override fun inferType(program: Program): DataType? {
        val target = identifier.targetStatement(program.namespace)
        if (target is VarDecl) {
            return when (target.datatype) {
                in NumericDatatypes -> null
                in StringDatatypes -> DataType.UBYTE
                DataType.ARRAY_UB -> DataType.UBYTE
                DataType.ARRAY_B -> DataType.BYTE
                DataType.ARRAY_UW -> DataType.UWORD
                DataType.ARRAY_W -> DataType.WORD
                DataType.ARRAY_F -> DataType.FLOAT
                else -> throw FatalAstException("invalid dt")
            }
        }
        return null
    }

    override fun toString(): String {
        return "ArrayIndexed(ident=$identifier, arraysize=$arrayspec; pos=$position)"
    }
}

class TypecastExpression(var expression: IExpression, var type: DataType, val implicit: Boolean, override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        expression.linkParents(this)
    }

    override fun accept(processor: IAstModifyingVisitor) = processor.visit(this)
    override fun accept(processor: IAstVisitor) = processor.visit(this)
    override fun referencesIdentifier(name: String) = expression.referencesIdentifier(name)
    override fun inferType(program: Program): DataType? = type
    override fun constValue(program: Program): LiteralValue? {
        val cv = expression.constValue(program) ?: return null
        return cv.cast(type)
        // val value = RuntimeValue(cv.type, cv.asNumericValue!!).cast(type)
        // return LiteralValue.fromNumber(value.numericValue(), value.type, position).cast(type)
    }

    override fun toString(): String {
        return "Typecast($expression as $type)"
    }
}

data class AddressOf(val identifier: IdentifierReference, override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier.parent=this
    }

    var scopedname: String? = null     // will be set in a later state by the compiler
    override fun constValue(program: Program): LiteralValue? = null
    override fun referencesIdentifier(name: String) = false
    override fun inferType(program: Program) = DataType.UWORD
    override fun accept(processor: IAstModifyingVisitor) = processor.visit(this)
    override fun accept(processor: IAstVisitor) = processor.visit(this)
}

class DirectMemoryRead(var addressExpression: IExpression, override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        this.addressExpression.linkParents(this)
    }

    override fun accept(processor: IAstModifyingVisitor) = processor.visit(this)
    override fun accept(processor: IAstVisitor) = processor.visit(this)
    override fun referencesIdentifier(name: String) = false
    override fun inferType(program: Program): DataType? = DataType.UBYTE
    override fun constValue(program: Program): LiteralValue? = null

    override fun toString(): String {
        return "DirectMemoryRead($addressExpression)"
    }
}

open class LiteralValue(val type: DataType,
                        val bytevalue: Short? = null,
                        val wordvalue: Int? = null,
                        val floatvalue: Double? = null,
                        val strvalue: String? = null,
                        val arrayvalue: Array<IExpression>? = null,
                        initHeapId: Int? =null,
                        override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun referencesIdentifier(name: String) = arrayvalue?.any { it.referencesIdentifier(name) } ?: false

    val isString = type in StringDatatypes
    val isNumeric = type in NumericDatatypes
    val isArray = type in ArrayDatatypes
    var heapId = initHeapId
        private set

    companion object {
        fun fromBoolean(bool: Boolean, position: Position) =
                LiteralValue(DataType.UBYTE, bytevalue = if (bool) 1 else 0, position = position)

        fun fromNumber(value: Number, type: DataType, position: Position) : LiteralValue {
            return when(type) {
                in ByteDatatypes -> LiteralValue(type, bytevalue = value.toShort(), position = position)
                in WordDatatypes -> LiteralValue(type, wordvalue = value.toInt(), position = position)
                DataType.FLOAT -> LiteralValue(type, floatvalue = value.toDouble(), position = position)
                else -> throw FatalAstException("non numeric datatype")
            }
        }

        fun optimalNumeric(value: Number, position: Position): LiteralValue {
            return if(value is Double) {
                LiteralValue(DataType.FLOAT, floatvalue = value, position = position)
            } else {
                when (val intval = value.toInt()) {
                    in 0..255 -> LiteralValue(DataType.UBYTE, bytevalue = intval.toShort(), position = position)
                    in -128..127 -> LiteralValue(DataType.BYTE, bytevalue = intval.toShort(), position = position)
                    in 0..65535 -> LiteralValue(DataType.UWORD, wordvalue = intval, position = position)
                    in -32768..32767 -> LiteralValue(DataType.WORD, wordvalue = intval, position = position)
                    else -> LiteralValue(DataType.FLOAT, floatvalue = intval.toDouble(), position = position)
                }
            }
        }

        fun optimalInteger(value: Number, position: Position): LiteralValue {
            val intval = value.toInt()
            if(intval.toDouble() != value.toDouble())
                throw FatalAstException("value is not an integer: $value")
            return when (intval) {
                in 0..255 -> LiteralValue(DataType.UBYTE, bytevalue = value.toShort(), position = position)
                in -128..127 -> LiteralValue(DataType.BYTE, bytevalue = value.toShort(), position = position)
                in 0..65535 -> LiteralValue(DataType.UWORD, wordvalue = value.toInt(), position = position)
                else -> throw FatalAstException("integer overflow: $value")
            }
        }
    }

    init {
        when(type){
            in ByteDatatypes -> if(bytevalue==null) throw FatalAstException("literal value missing bytevalue")
            in WordDatatypes -> if(wordvalue==null) throw FatalAstException("literal value missing wordvalue")
            DataType.FLOAT -> if(floatvalue==null) throw FatalAstException("literal value missing floatvalue")
            in StringDatatypes ->
                if(strvalue==null && heapId==null) throw FatalAstException("literal value missing strvalue/heapId")
            in ArrayDatatypes ->
                if(arrayvalue==null && heapId==null) throw FatalAstException("literal value missing arrayvalue/heapId")
            else -> throw FatalAstException("invalid type $type")
        }
        if(bytevalue==null && wordvalue==null && floatvalue==null && arrayvalue==null && strvalue==null && heapId==null)
            throw FatalAstException("literal value without actual value")
    }

    val asNumericValue: Number? = when {
        bytevalue!=null -> bytevalue
        wordvalue!=null -> wordvalue
        floatvalue!=null -> floatvalue
        else -> null
    }

    val asIntegerValue: Int? = when {
        bytevalue!=null -> bytevalue.toInt()
        wordvalue!=null -> wordvalue
        // don't round a float value, otherwise code will not detect that it's not an integer
        else -> null
    }

    val asBooleanValue: Boolean =
            (floatvalue!=null && floatvalue != 0.0) ||
            (bytevalue!=null && bytevalue != 0.toShort()) ||
            (wordvalue!=null && wordvalue != 0) ||
            (strvalue!=null && strvalue.isNotEmpty()) ||
            (arrayvalue != null && arrayvalue.isNotEmpty())

    override fun linkParents(parent: Node) {
        this.parent = parent
        arrayvalue?.forEach {it.linkParents(this)}
    }

    override fun constValue(program: Program): LiteralValue? {
        if(arrayvalue!=null) {
            for(v in arrayvalue) {
                if(v.constValue(program)==null) return null
            }
        }
        return this
    }

    override fun accept(processor: IAstModifyingVisitor) = processor.visit(this)
    override fun accept(processor: IAstVisitor) = processor.visit(this)

    override fun toString(): String {
        val vstr = when(type) {
            DataType.UBYTE -> "ubyte:$bytevalue"
            DataType.BYTE -> "byte:$bytevalue"
            DataType.UWORD -> "uword:$wordvalue"
            DataType.WORD -> "word:$wordvalue"
            DataType.FLOAT -> "float:$floatvalue"
            in StringDatatypes -> "str:$strvalue"
            in ArrayDatatypes -> "array:$arrayvalue"
            else -> throw FatalAstException("weird datatype")
        }
        return "LiteralValue($vstr)"
    }

    override fun inferType(program: Program) = type

    override fun hashCode(): Int {
        val bh = bytevalue?.hashCode() ?: 0x10001234
        val wh = wordvalue?.hashCode() ?: 0x01002345
        val fh = floatvalue?.hashCode() ?: 0x00103456
        val sh = strvalue?.hashCode() ?: 0x00014567
        val ah = arrayvalue?.hashCode() ?: 0x11119876
        var hash = bh * 31 xor wh
        hash = hash*31 xor fh
        hash = hash*31 xor sh
        hash = hash*31 xor ah
        hash = hash*31 xor type.hashCode()
        return hash
    }

    override fun equals(other: Any?): Boolean {
        if(other==null || other !is LiteralValue)
            return false
        if(isNumeric && other.isNumeric)
            return asNumericValue?.toDouble()==other.asNumericValue?.toDouble()
        if(isArray && other.isArray)
            return arrayvalue!!.contentEquals(other.arrayvalue!!) && heapId==other.heapId
        if(isString && other.isString)
            return strvalue==other.strvalue && heapId==other.heapId

        if(type!=other.type)
            return false

        return compareTo(other) == 0
    }

    operator fun compareTo(other: LiteralValue): Int {
        val numLeft = asNumericValue?.toDouble()
        val numRight = other.asNumericValue?.toDouble()
        if(numLeft!=null && numRight!=null)
            return numLeft.compareTo(numRight)

        if(strvalue!=null && other.strvalue!=null)
            return strvalue.compareTo(other.strvalue)

        throw ExpressionError("cannot order compare type $type with ${other.type}", other.position)
    }

    fun cast(targettype: DataType): LiteralValue? {
        if(type==targettype)
            return this
        when(type) {
            DataType.UBYTE -> {
                if(targettype== DataType.BYTE && bytevalue!! <= 127)
                    return LiteralValue(targettype, bytevalue = bytevalue, position = position)
                if(targettype== DataType.WORD || targettype== DataType.UWORD)
                    return LiteralValue(targettype, wordvalue = bytevalue!!.toInt(), position = position)
                if(targettype== DataType.FLOAT)
                    return LiteralValue(targettype, floatvalue = bytevalue!!.toDouble(), position = position)
            }
            DataType.BYTE -> {
                if(targettype== DataType.UBYTE && bytevalue!! >= 0)
                    return LiteralValue(targettype, bytevalue = bytevalue, position = position)
                if(targettype== DataType.UWORD && bytevalue!! >= 0)
                    return LiteralValue(targettype, wordvalue = bytevalue.toInt(), position = position)
                if(targettype== DataType.WORD)
                    return LiteralValue(targettype, wordvalue = bytevalue!!.toInt(), position = position)
                if(targettype== DataType.FLOAT)
                    return LiteralValue(targettype, floatvalue = bytevalue!!.toDouble(), position = position)
            }
            DataType.UWORD -> {
                if(targettype== DataType.BYTE && wordvalue!! <= 127)
                    return LiteralValue(targettype, bytevalue = wordvalue.toShort(), position = position)
                if(targettype== DataType.UBYTE && wordvalue!! <= 255)
                    return LiteralValue(targettype, bytevalue = wordvalue.toShort(), position = position)
                if(targettype== DataType.WORD && wordvalue!! <= 32767)
                    return LiteralValue(targettype, wordvalue = wordvalue, position = position)
                if(targettype== DataType.FLOAT)
                    return LiteralValue(targettype, floatvalue = wordvalue!!.toDouble(), position = position)
            }
            DataType.WORD -> {
                if(targettype== DataType.BYTE && wordvalue!! in -128..127)
                    return LiteralValue(targettype, bytevalue = wordvalue.toShort(), position = position)
                if(targettype== DataType.UBYTE && wordvalue!! in 0..255)
                    return LiteralValue(targettype, bytevalue = wordvalue.toShort(), position = position)
                if(targettype== DataType.UWORD && wordvalue!! >=0)
                    return LiteralValue(targettype, wordvalue = wordvalue, position = position)
                if(targettype== DataType.FLOAT)
                    return LiteralValue(targettype, floatvalue = wordvalue!!.toDouble(), position = position)
            }
            DataType.FLOAT -> {
                if(floor(floatvalue!!) ==floatvalue) {
                    val value = floatvalue.toInt()
                    if (targettype == DataType.BYTE && value in -128..127)
                        return LiteralValue(targettype, bytevalue = value.toShort(), position = position)
                    if (targettype == DataType.UBYTE && value in 0..255)
                        return LiteralValue(targettype, bytevalue = value.toShort(), position = position)
                    if (targettype == DataType.WORD && value in -32768..32767)
                        return LiteralValue(targettype, wordvalue = value, position = position)
                    if (targettype == DataType.UWORD && value in 0..65535)
                        return LiteralValue(targettype, wordvalue = value, position = position)
                }
            }
            in StringDatatypes -> {
                if(targettype in StringDatatypes)
                    return this
            }
            else -> {}
        }
        return null    // invalid type conversion from $this to $targettype
    }

    fun addToHeap(heap: HeapValues) {
        if(heapId==null) {
            if (strvalue != null) {
                heapId = heap.addString(type, strvalue)
            }
            else if (arrayvalue!=null) {
                if(arrayvalue.any {it is AddressOf }) {
                    val intArrayWithAddressOfs = arrayvalue.map {
                        when (it) {
                            is AddressOf -> IntegerOrAddressOf(null, it)
                            is LiteralValue -> IntegerOrAddressOf(it.asIntegerValue, null)
                            else -> throw FatalAstException("invalid datatype in array")
                        }
                    }
                    heapId = heap.addIntegerArray(type, intArrayWithAddressOfs.toTypedArray())
                } else {
                    val valuesInArray = arrayvalue.map { (it as LiteralValue).asNumericValue!! }
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
}

class RangeExpr(var from: IExpression,
                var to: IExpression,
                var step: IExpression,
                override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        from.linkParents(this)
        to.linkParents(this)
        step.linkParents(this)
    }

    override fun constValue(program: Program): LiteralValue? = null
    override fun accept(processor: IAstModifyingVisitor) = processor.visit(this)
    override fun accept(processor: IAstVisitor) = processor.visit(this)
    override fun referencesIdentifier(name: String): Boolean  = from.referencesIdentifier(name) || to.referencesIdentifier(name)
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
        val fromLv = (from as? LiteralValue)
        val toLv = (to as? LiteralValue)
        if(fromLv==null || toLv==null)
            return null
        return toConstantIntegerRange()?.count()
    }

    fun toConstantIntegerRange(): IntProgression? {
        val fromLv = from as? LiteralValue
        val toLv = to as? LiteralValue
        if(fromLv==null || toLv==null)
            return null         // non-constant range
        val fromVal: Int
        val toVal: Int
        if(fromLv.isString && toLv.isString) {
            // string range -> int range over petscii values
            fromVal = Petscii.encodePetscii(fromLv.strvalue!!, true)[0].toInt()
            toVal = Petscii.encodePetscii(toLv.strvalue!!, true)[0].toInt()
        } else {
            // integer range
            fromVal = (from as LiteralValue).asIntegerValue!!
            toVal = (to as LiteralValue).asIntegerValue!!
        }
        val stepVal = (step as? LiteralValue)?.asIntegerValue ?: 1
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

class RegisterExpr(val register: Register, override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun constValue(program: Program): LiteralValue? = null
    override fun accept(processor: IAstModifyingVisitor) = this
    override fun accept(processor: IAstVisitor) {}
    override fun referencesIdentifier(name: String): Boolean  = false
    override fun toString(): String {
        return "RegisterExpr(register=$register, pos=$position)"
    }

    override fun inferType(program: Program) = DataType.UBYTE
}

data class IdentifierReference(val nameInSource: List<String>, override val position: Position) : IExpression {
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

    override fun constValue(program: Program): LiteralValue? {
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

    override fun accept(processor: IAstModifyingVisitor) = processor.visit(this)
    override fun accept(processor: IAstVisitor) = processor.visit(this)
    override fun referencesIdentifier(name: String): Boolean = nameInSource.last() == name   // @todo is this correct all the time?

    override fun inferType(program: Program): DataType? {
        val targetStmt = targetStatement(program.namespace)
        if(targetStmt is VarDecl) {
            return targetStmt.datatype
        } else {
            throw FatalAstException("cannot get datatype from identifier reference ${this}, pos=$position")
        }
    }

    fun heapId(namespace: INameScope): Int {
        val node = namespace.lookup(nameInSource, this) ?: throw UndefinedSymbolError(this)
        return ((node as? VarDecl)?.value as? LiteralValue)?.heapId ?: throw FatalAstException("identifier is not on the heap: $this")
    }
}

class FunctionCall(override var target: IdentifierReference,
                   override var arglist: MutableList<IExpression>,
                   override val position: Position) : IExpression, IFunctionCall {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        arglist.forEach { it.linkParents(this) }
    }

    override fun constValue(program: Program) = constValue(program, true)

    private fun constValue(program: Program, withDatatypeCheck: Boolean): LiteralValue? {
        // if the function is a built-in function and the args are consts, should try to const-evaluate!
        // lenghts of arrays and strings are constants that are determined at compile time!
        if(target.nameInSource.size>1) return null
        try {
            var resultValue: LiteralValue? = null
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

    override fun accept(processor: IAstModifyingVisitor) = processor.visit(this)
    override fun accept(processor: IAstVisitor) = processor.visit(this)
    override fun referencesIdentifier(name: String): Boolean = target.referencesIdentifier(name) || arglist.any{it.referencesIdentifier(name)}

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
            is Label -> return null
        }
        return null     // calling something we don't recognise...
    }
}
