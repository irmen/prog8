package prog8.code.ast

import prog8.code.core.DataType
import prog8.code.core.Encoding
import prog8.code.core.Position
import java.util.*
import kotlin.math.round


sealed class PtExpression(val type: DataType, position: Position) : PtNode(position) {

    init {
        if(type==DataType.BOOL)
            throw IllegalArgumentException("bool should have become ubyte @$position")
        if(type==DataType.UNDEFINED) {
            @Suppress("LeakingThis")
            when(this) {
                is PtBuiltinFunctionCall -> { /* void function call */ }
                is PtFunctionCall -> { /* void function call */ }
                is PtIdentifier -> { /* non-variable identifier */ }
                else -> throw IllegalArgumentException("type should be known @$position")
            }
        }
    }

    override fun printProperties() {
        print(type)
    }

    infix fun isSameAs(other: PtExpression): Boolean {
        return when(this) {
            is PtAddressOf -> other is PtAddressOf && other.type==type && other.identifier isSameAs identifier
            is PtArrayIndexer -> other is PtArrayIndexer && other.type==type && other.variable isSameAs variable && other.index isSameAs index
            is PtBinaryExpression -> other is PtBinaryExpression && other.left isSameAs left && other.right isSameAs right
            is PtContainmentCheck -> other is PtContainmentCheck && other.type==type && other.element isSameAs element && other.iterable isSameAs iterable
            is PtIdentifier -> other is PtIdentifier && other.type==type && other.targetName==targetName
            is PtMachineRegister -> other is PtMachineRegister && other.type==type && other.register==register
            is PtMemoryByte -> other is PtMemoryByte && other.address isSameAs address
            is PtNumber -> other is PtNumber && other.type==type && other.number==number
            is PtPrefix -> other is PtPrefix && other.type==type && other.operator==operator && other.value isSameAs value
            is PtRange -> other is PtRange && other.type==type && other.from==from && other.to==to && other.step==step
            is PtTypeCast -> other is PtTypeCast && other.type==type && other.value isSameAs value
            else -> false
        }
    }
}

class PtAddressOf(position: Position) : PtExpression(DataType.UWORD, position) {
    val identifier: PtIdentifier
        get() = children.single() as PtIdentifier
}


class PtArrayIndexer(type: DataType, position: Position): PtExpression(type, position) {
    val variable: PtIdentifier
        get() = children[0] as PtIdentifier
    val index: PtExpression
        get() = children[1] as PtExpression
}


class PtArray(type: DataType, position: Position): PtExpression(type, position) {
    override fun hashCode(): Int = Objects.hash(children, type)
    override fun equals(other: Any?): Boolean {
        if(other==null || other !is PtArray)
            return false
        return type==other.type && children == other.children
    }
}


class PtBuiltinFunctionCall(val name: String,
                            val void: Boolean,
                            val hasNoSideEffects: Boolean,
                            type: DataType,
                            position: Position) : PtExpression(type, position) {
    init {
        if(!void)
            require(type!=DataType.UNDEFINED)
    }

    val args: List<PtExpression>
        get() = children.map { it as PtExpression }
    override fun printProperties() {
        print("$name void=$void noSideFx=$hasNoSideEffects")
    }
}


class PtBinaryExpression(val operator: String, type: DataType, position: Position): PtExpression(type, position) {
    // note: "and", "or", "xor" do not occur anymore as operators. They've been replaced int the ast by their bitwise versions &, |, ^.
    val left: PtExpression
        get() = children[0] as PtExpression
    val right: PtExpression
        get() = children[1] as PtExpression

    override fun printProperties() {
        print("$operator -> $type")
    }
}


class PtContainmentCheck(position: Position): PtExpression(DataType.UBYTE, position) {
    val element: PtExpression
        get() = children[0] as PtExpression
    val iterable: PtIdentifier
        get() = children[1] as PtIdentifier
}


class PtFunctionCall(val functionName: List<String>,
                     val void: Boolean,
                     type: DataType,
                     position: Position) : PtExpression(type, position) {
    init {
        if(!void)
            require(type!=DataType.UNDEFINED)
    }

    val args: List<PtExpression>
        get() = children.map { it as PtExpression }
    override fun printProperties() {
        print("${functionName.joinToString(".")} void=$void")
    }
}


class PtIdentifier(val ref: List<String>, val targetName: List<String>, type: DataType, position: Position) : PtExpression(type, position) {
    override fun printProperties() {
        print("$ref --> $targetName  $type")
    }
}


class PtMemoryByte(position: Position) : PtExpression(DataType.UBYTE, position) {
    val address: PtExpression
        get() = children.single() as PtExpression
    override fun printProperties() {}
}


class PtNumber(type: DataType, val number: Double, position: Position) : PtExpression(type, position) {

    init {
        if(type==DataType.BOOL)
            throw IllegalArgumentException("bool should have become ubyte @$position")
        if(type!=DataType.FLOAT) {
            val rounded = round(number)
            if (rounded != number)
                throw IllegalArgumentException("refused rounding of float to avoid loss of precision @$position")
        }
    }

    override fun printProperties() {
        print("$number ($type)")
    }

    override fun hashCode(): Int = Objects.hash(type, number)

    override fun equals(other: Any?): Boolean {
        if(other==null || other !is PtNumber)
            return false
        return number==other.number
    }

    operator fun compareTo(other: PtNumber): Int = number.compareTo(other.number)
}


class PtPrefix(val operator: String, type: DataType, position: Position): PtExpression(type, position) {
    val value: PtExpression
        get() = children.single() as PtExpression

    init {
        // note: the "not" operator may no longer occur in the ast; not x should have been replaced with x==0
        require(operator in setOf("+", "-", "~")) { "invalid prefix operator: $operator" }
    }

    override fun printProperties() {
        print(operator)
    }
}


class PtRange(type: DataType, position: Position) : PtExpression(type, position) {
    val from: PtExpression
        get() = children[0] as PtExpression
    val to: PtExpression
        get() = children[1] as PtExpression
    val step: PtNumber
        get() = children[2] as PtNumber

    override fun printProperties() {}
}


class PtString(val value: String, val encoding: Encoding, position: Position) : PtExpression(DataType.STR, position) {
    override fun printProperties() {
        print("$encoding:\"$value\"")
    }

    override fun hashCode(): Int = Objects.hash(value, encoding)
    override fun equals(other: Any?): Boolean {
        if(other==null || other !is PtString)
            return false
        return value==other.value && encoding == other.encoding
    }
}


class PtTypeCast(type: DataType, position: Position) : PtExpression(type, position) {
    val value: PtExpression
        get() = children.single() as PtExpression
}


// special node that isn't created from compiling user code, but used internally
class PtMachineRegister(val register: Int, type: DataType, position: Position) : PtExpression(type, position) {
    override fun printProperties() {
        print("reg=$register  $type")
    }
}


fun constValue(expr: PtExpression): Double? = if(expr is PtNumber) expr.number else null
fun constIntValue(expr: PtExpression): Int? = if(expr is PtNumber) expr.number.toInt() else null
