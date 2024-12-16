package prog8.code.ast

import prog8.code.core.*
import java.util.*
import kotlin.math.abs
import kotlin.math.truncate


sealed class PtExpression(val type: DataType, position: Position) : PtNode(position) {

    init {
        if(type.isUndefined) {
            @Suppress("LeakingThis")
            when(this) {
                is PtBuiltinFunctionCall -> { /* void function call */ }
                is PtFunctionCall -> { /* void function call */ }
                is PtIdentifier -> { /* non-variable identifier */ }
                else -> throw IllegalArgumentException("type should be known @$position")
            }
        }
    }

    infix fun isSameAs(other: PtExpression): Boolean {
        return when(this) {
            is PtAddressOf -> {
                if(other !is PtAddressOf)
                    return false
                if (other.type!==type || !(other.identifier isSameAs identifier))
                    return false
                if(other.children.size!=children.size)
                    return false
                if(children.size==1)
                    return true
                return arrayIndexExpr!! isSameAs other.arrayIndexExpr!!
            }
            is PtArrayIndexer -> other is PtArrayIndexer && other.type==type && other.variable isSameAs variable && other.index isSameAs index && other.splitWords==splitWords
            is PtBinaryExpression -> {
                if(other !is PtBinaryExpression || other.operator!=operator)
                    false
                else if(operator in AssociativeOperators)
                    (other.left isSameAs left && other.right isSameAs right) || (other.left isSameAs right && other.right isSameAs left)
                else
                    other.left isSameAs left && other.right isSameAs right
            }
            is PtContainmentCheck -> {
                if(other !is PtContainmentCheck || other.type != type || !(other.needle isSameAs needle))
                    false
                else {
                    if(haystackHeapVar!=null)
                        other.haystackHeapVar!=null && other.haystackHeapVar!! isSameAs haystackHeapVar!!
                    else if(haystackValues!=null)
                        other.haystackValues!=null && other.haystackValues!! isSameAs haystackValues!!
                    else
                        false
                }
            }
            is PtIdentifier -> other is PtIdentifier && other.type==type && other.name==name
            is PtIrRegister -> other is PtIrRegister && other.type==type && other.register==register
            is PtMemoryByte -> other is PtMemoryByte && other.address isSameAs address
            is PtNumber -> other is PtNumber && other.type==type && other.number==number
            is PtBool -> other is PtBool && other.value==value
            is PtPrefix -> other is PtPrefix && other.type==type && other.operator==operator && other.value isSameAs value
            is PtRange -> other is PtRange && other.type==type && other.from==from && other.to==to && other.step==step
            is PtTypeCast -> other is PtTypeCast && other.type==type && other.value isSameAs value
            else -> false
        }
    }

    infix fun isSameAs(target: PtAssignTarget): Boolean = when {
        target.memory != null && this is PtMemoryByte -> {
            target.memory!!.address isSameAs this.address
        }
        target.identifier != null && this is PtIdentifier -> {
            this.name == target.identifier!!.name
        }
        target.array != null && this is PtArrayIndexer -> {
            this.variable.name == target.array!!.variable.name && this.index isSameAs target.array!!.index && this.splitWords==target.array!!.splitWords
        }
        else -> false
    }

    fun asConstInteger(): Int? = (this as? PtNumber)?.number?.toInt() ?: (this as? PtBool)?.asInt()
    fun asConstValue(): Double? = (this as? PtNumber)?.number ?: (this as? PtBool)?.asInt()?.toDouble()

    fun isSimple(): Boolean {
        return when(this) {
            is PtAddressOf -> this.arrayIndexExpr==null || this.arrayIndexExpr?.isSimple()==true
            is PtArray -> true
            is PtArrayIndexer -> index is PtNumber || index is PtIdentifier
            is PtBinaryExpression -> false
            is PtBuiltinFunctionCall -> {
                when (name) {
                    in arrayOf("msb", "lsb", "msw", "lsw", "mkword", "set_carry", "set_irqd", "clear_carry", "clear_irqd") -> this.args.all { it.isSimple() }
                    else -> false
                }
            }
            is PtContainmentCheck -> false
            is PtFunctionCall -> false
            is PtIdentifier -> true
            is PtIrRegister -> true
            is PtMemoryByte -> address is PtNumber || address is PtIdentifier
            is PtBool -> true
            is PtNumber -> true
            is PtPrefix -> value.isSimple()
            is PtRange -> true
            is PtString -> true
            is PtTypeCast -> value.isSimple()
            is PtIfExpression -> condition.isSimple() && truevalue.isSimple() && falsevalue.isSimple()
        }
    }

    /*
    fun clone(): PtExpression {
        fun withClonedChildrenFrom(orig: PtExpression, clone: PtExpression): PtExpression {
            orig.children.forEach { clone.add((it as PtExpression).clone()) }
            return clone
        }
        when(this) {
            is PtAddressOf -> return withClonedChildrenFrom(this, PtAddressOf(position))
            is PtArray -> return withClonedChildrenFrom(this, PtArray(type, position))
            is PtArrayIndexer -> return withClonedChildrenFrom(this, PtArrayIndexer(type, position))
            is PtBinaryExpression -> return withClonedChildrenFrom(this, PtBinaryExpression(operator, type, position))
            is PtBuiltinFunctionCall -> return withClonedChildrenFrom(this, PtBuiltinFunctionCall(name, void, hasNoSideEffects, type, position))
            is PtContainmentCheck -> return withClonedChildrenFrom(this, PtContainmentCheck(position))
            is PtFunctionCall -> return withClonedChildrenFrom(this, PtFunctionCall(name, void, type, position))
            is PtIdentifier -> return withClonedChildrenFrom(this, PtIdentifier(name, type, position))
            is PtMachineRegister -> return withClonedChildrenFrom(this, PtMachineRegister(register, type, position))
            is PtMemoryByte -> return withClonedChildrenFrom(this, PtMemoryByte(position))
            is PtNumber -> return withClonedChildrenFrom(this, PtNumber(type, number, position))
            is PtBool -> return withClonedChildrenFrom(this, PtBool(value, position))
            is PtPrefix -> return withClonedChildrenFrom(this, PtPrefix(operator, type, position))
            is PtRange -> return withClonedChildrenFrom(this, PtRange(type, position))
            is PtString -> return withClonedChildrenFrom(this, PtString(value, encoding, position))
            is PtTypeCast -> return withClonedChildrenFrom(this, PtTypeCast(type, position))
        }
    }
    */
}

class PtAddressOf(position: Position, val isMsbForSplitArray: Boolean=false) : PtExpression(DataType.forDt(BaseDataType.UWORD), position) {
    val identifier: PtIdentifier
        get() = children[0] as PtIdentifier
    val arrayIndexExpr: PtExpression?
        get() = if(children.size==2) children[1] as PtExpression else null

    val isFromArrayElement: Boolean
        get() = children.size==2
}


class PtArrayIndexer(elementType: DataType, position: Position): PtExpression(elementType, position) {
    val variable: PtIdentifier
        get() = children[0] as PtIdentifier
    val index: PtExpression
        get() = children[1] as PtExpression
    val splitWords: Boolean
        get() = variable.type.isSplitWordArray

    init {
        require(elementType.isNumericOrBool)
    }
}


class PtArray(type: DataType, position: Position): PtExpression(type, position) {
    // children are always one of 3 types: PtBool, PtNumber or PtAddressOf.
    override fun hashCode(): Int = Objects.hash(children, type)
    override fun equals(other: Any?): Boolean {
        if(other==null || other !is PtArray)
            return false
        return type==other.type && children == other.children
    }

    val size: Int
        get() = children.size
}


class PtBuiltinFunctionCall(val name: String,
                            val void: Boolean,
                            val hasNoSideEffects: Boolean,
                            type: DataType,
                            position: Position) : PtExpression(type, position) {
    init {
        if(!void)
            require(!type.isUndefined)
    }

    val args: List<PtExpression>
        get() = children.map { it as PtExpression }
}


class PtBinaryExpression(val operator: String, type: DataType, position: Position): PtExpression(type, position) {
    val left: PtExpression
        get() = children[0] as PtExpression
    val right: PtExpression
        get() = children[1] as PtExpression

    init {
        if(operator in ComparisonOperators + LogicalOperators)
            require(type.isBool)
        else
            require(!type.isBool) { "no bool allowed for this operator $operator"}
    }
}


class PtIfExpression(type: DataType, position: Position): PtExpression(type, position) {
    val condition: PtExpression
        get() = children[0] as PtExpression
    val truevalue: PtExpression
        get() = children[1] as PtExpression
    val falsevalue: PtExpression
        get() = children[2] as PtExpression
}


class PtContainmentCheck(position: Position): PtExpression(DataType.forDt(BaseDataType.BOOL), position) {
    val needle: PtExpression
        get() = children[0] as PtExpression
    val haystackHeapVar: PtIdentifier?
        get() = children[1] as? PtIdentifier
    val haystackValues: PtArray?
        get() = children[1] as? PtArray

    companion object {
        const val MAX_SIZE_FOR_INLINE_CHECKS_BYTE = 5
        const val MAX_SIZE_FOR_INLINE_CHECKS_WORD = 4
    }
}


class PtFunctionCall(val name: String,
                     val void: Boolean,
                     type: DataType,
                     position: Position) : PtExpression(type, position) {
    val args: List<PtExpression>
        get() = children.map { it as PtExpression }

    init {
        if(void) require(type.isUndefined) {
            "void fcall should have undefined datatype"
        }
        // note: non-void calls can have UNDEFINED type: is if they return more than 1 value
    }
}


class PtIdentifier(val name: String, type: DataType, position: Position) : PtExpression(type, position) {
    override fun toString(): String {
        return "[PtIdentifier:$name $type $position]"
    }

    fun copy() = PtIdentifier(name, type, position)
}


class PtMemoryByte(position: Position) : PtExpression(DataType.forDt(BaseDataType.UBYTE), position) {
    val address: PtExpression
        get() = children.single() as PtExpression
}


class PtBool(val value: Boolean, position: Position) : PtExpression(DataType.forDt(BaseDataType.BOOL), position) {
    override fun hashCode(): Int = Objects.hash(type, value)

    override fun equals(other: Any?): Boolean {
        if(other==null || other !is PtBool)
            return false
        return value==other.value
    }

    override fun toString() = "PtBool:$value"

    fun asInt(): Int = if(value) 1 else 0
}


class PtNumber(type: BaseDataType, val number: Double, position: Position) : PtExpression(DataType.forDt(type), position) {

    companion object {
        fun fromBoolean(bool: Boolean, position: Position): PtNumber =
            PtNumber(BaseDataType.UBYTE, if(bool) 1.0 else 0.0, position)
    }

    init {
        if(type==BaseDataType.BOOL)
            throw IllegalArgumentException("use PtBool instead")
        if(type!=BaseDataType.FLOAT) {
            val trunc = truncate(number)
            if (trunc != number)
                throw IllegalArgumentException("refused truncating of float to avoid loss of precision @$position")
        }
        when(type) {
            BaseDataType.UBYTE -> require(number in 0.0..255.0)
            BaseDataType.BYTE -> require(number in -128.0..127.0)
            BaseDataType.UWORD -> require(number in 0.0..65535.0)
            BaseDataType.WORD -> require(number in -32728.0..32767.0)
            BaseDataType.LONG -> require(number in -2147483647.0..2147483647.0)
            else ->  require(type.isNumeric) { "numeric literal type should be numeric: $type" }
        }
    }

    override fun hashCode(): Int = Objects.hash(type, number)

    override fun equals(other: Any?): Boolean {
        return if(other==null || other !is PtNumber)
            false
        else if(!type.isBool && !other.type.isBool)
            number==other.number
        else
            type==other.type && number==other.number
    }

    operator fun compareTo(other: PtNumber): Int = number.compareTo(other.number)

    override fun toString() = "PtNumber:$type:$number"
}


class PtPrefix(val operator: String, type: DataType, position: Position): PtExpression(type, position) {
    val value: PtExpression
        get() = children.single() as PtExpression

    init {
        require(operator in PrefixOperators) { "invalid prefix operator: $operator" }
    }
}


class PtRange(type: DataType, position: Position) : PtExpression(type, position) {
    val from: PtExpression
        get() = children[0] as PtExpression
    val to: PtExpression
        get() = children[1] as PtExpression
    val step: PtNumber
        get() = children[2] as PtNumber

    fun toConstantIntegerRange(): IntProgression? {
        fun makeRange(fromVal: Int, toVal: Int, stepVal: Int): IntProgression {
            return when {
                fromVal == toVal -> fromVal .. toVal
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

        val fromLv = from as? PtNumber
        val toLv = to as? PtNumber
        if(fromLv==null || toLv==null)
            return null
        val fromVal = fromLv.number.toInt()
        val toVal = toLv.number.toInt()
        val stepVal = step.number.toInt()
        return makeRange(fromVal, toVal, stepVal)
    }
}


class PtString(val value: String, val encoding: Encoding, position: Position) : PtExpression(DataType.forDt(BaseDataType.STR), position) {
    override fun hashCode(): Int = Objects.hash(value, encoding)
    override fun equals(other: Any?): Boolean {
        if(other==null || other !is PtString)
            return false
        return value==other.value && encoding == other.encoding
    }
}


class PtTypeCast(type: BaseDataType, position: Position) : PtExpression(DataType.forDt(type), position) {
    val value: PtExpression
        get() = children.single() as PtExpression
}


// special node that isn't created from compiling user code, but used internally in the Intermediate Code
class PtIrRegister(val register: Int, type: DataType, position: Position) : PtExpression(type, position)
