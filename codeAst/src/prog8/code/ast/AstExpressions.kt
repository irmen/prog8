package prog8.code.ast

import prog8.code.core.DataType
import prog8.code.core.Encoding
import prog8.code.core.Position
import java.util.*
import kotlin.math.round


sealed class PtExpression(val type: DataType, position: Position) : PtNode(position) {
    override fun printProperties() {
        print(type)
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


class PtBuiltinFunctionCall(val name: String, val void: Boolean, type: DataType, position: Position) : PtExpression(type, position) {
    init {
        if(!void)
            require(type!=DataType.UNDEFINED)
    }

    val args: List<PtExpression>
        get() = children.map { it as PtExpression }
    override fun printProperties() {
        print("$name void=$void")
    }
}


class PtBinaryExpression(val operator: String, type: DataType, position: Position): PtExpression(type, position) {
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
        if(type!=DataType.FLOAT) {
            val rounded = round(number)
            if (rounded != number)
                throw IllegalArgumentException("refused rounding of float to avoid loss of precision")
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


class PtPipe(type: DataType, val void: Boolean, position: Position) : PtExpression(type, position) {
    init {
        if(!void)
            require(type!=DataType.UNDEFINED)
    }

    val segments: List<PtExpression>
        get() = children.map { it as PtExpression }

    override fun printProperties() {}
}


class PtPrefix(val operator: String, type: DataType, position: Position): PtExpression(type, position) {
    val value: PtExpression
        get() = children.single() as PtExpression

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


fun constValue(expr: PtExpression): Double? = if(expr is PtNumber) expr.number else null
fun constIntValue(expr: PtExpression): Int? = if(expr is PtNumber) expr.number.toInt() else null
