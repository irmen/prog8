package prog8.ast.expressions

import prog8.ast.*
import prog8.ast.antlr.escape
import prog8.ast.base.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstVisitor
import prog8.compilerinterface.Encoding
import java.util.*
import kotlin.math.abs
import kotlin.math.round

val AssociativeOperators = setOf("+", "*", "&", "|", "^", "or", "and", "xor", "==", "!=")
val ComparisonOperators = setOf("==", "!=", "<", ">", "<=", ">=")
val AugmentAssignmentOperators = setOf("+", "-", "/", "*", "**", "&", "|", "^", "<<", ">>", "%", "and", "or", "xor")
val LogicalOperators = setOf("and", "or", "xor", "not")
val BitwiseOperators = setOf("&", "|", "^")

fun invertedComparisonOperator(operator: String) =
    when (operator) {
        "==" -> "!="
        "!=" -> "=="
        "<" -> ">="
        ">" -> "<="
        "<=" -> ">"
        ">=" -> "<"
        else -> null
    }


sealed class Expression: Node {
    abstract override fun copy(): Expression
    abstract fun constValue(program: Program): NumericLiteral?
    abstract fun accept(visitor: IAstVisitor)
    abstract fun accept(visitor: AstWalker, parent: Node)
    abstract fun referencesIdentifier(nameInSource: List<String>): Boolean
    abstract fun inferType(program: Program): InferredTypes.InferredType
    abstract val isSimple: Boolean

    infix fun isSameAs(assigntarget: AssignTarget) = assigntarget.isSameAs(this)

    infix fun isSameAs(other: Expression): Boolean {
        if(this===other)
            return true
        return when(this) {
            is IdentifierReference ->
                (other is IdentifierReference && other.nameInSource==nameInSource)
            is PrefixExpression ->
                (other is PrefixExpression && other.operator==operator && other.expression isSameAs expression)
            is BinaryExpression ->
                (other is BinaryExpression && other.operator==operator
                        && other.left isSameAs left
                        && other.right isSameAs right)
            is ArrayIndexedExpression -> {
                (other is ArrayIndexedExpression && other.arrayvar.nameInSource == arrayvar.nameInSource
                        && other.indexer isSameAs indexer)
            }
            is DirectMemoryRead -> {
                (other is DirectMemoryRead && other.addressExpression isSameAs addressExpression)
            }
            is TypecastExpression -> {
                (other is TypecastExpression && other.implicit==implicit && other.type==type && other.expression isSameAs expression)
            }
            is AddressOf -> {
                (other is AddressOf && other.identifier.nameInSource == identifier.nameInSource)
            }
            is RangeExpression -> {
                (other is RangeExpression && other.from==from && other.to==to && other.step==step)
            }
            is FunctionCallExpression -> {
                (other is FunctionCallExpression && other.target.nameInSource == target.nameInSource
                        && other.args.size == args.size
                        && other.args.zip(args).all { it.first isSameAs it.second } )
            }
            else -> other==this
        }
    }

    fun typecastTo(targetDt: DataType, sourceDt: DataType, implicit: Boolean=false): Pair<Boolean, Expression> {
        require(sourceDt!=DataType.UNDEFINED && targetDt!=DataType.UNDEFINED)
        if(sourceDt==targetDt)
            return Pair(false, this)
        if(this is TypecastExpression) {
            this.type = targetDt
            return Pair(false, this)
        }
        val typecast = TypecastExpression(this, targetDt, implicit, this.position)
        return Pair(true, typecast)
    }
}


class PrefixExpression(val operator: String, var expression: Expression, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        expression.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(node === expression && replacement is Expression)
        expression = replacement
        replacement.parent = this
    }

    override fun copy() = PrefixExpression(operator, expression.copy(), position)
    override fun constValue(program: Program): NumericLiteral? {
        val constval = expression.constValue(program) ?: return null
        val converted = when(operator) {
            "+" -> constval
            "-" -> when (constval.type) {
                in IntegerDatatypes -> NumericLiteral.optimalInteger(-constval.number.toInt(), constval.position)
                DataType.FLOAT -> NumericLiteral(DataType.FLOAT, -constval.number, constval.position)
                else -> throw ExpressionError("can only take negative of int or float", constval.position)
            }
            "~" -> when (constval.type) {
                DataType.BYTE -> NumericLiteral(DataType.BYTE, constval.number.toInt().inv().toDouble(), constval.position)
                DataType.UBYTE -> NumericLiteral(DataType.UBYTE, (constval.number.toInt().inv() and 255).toDouble(), constval.position)
                DataType.WORD -> NumericLiteral(DataType.WORD, constval.number.toInt().inv().toDouble(), constval.position)
                DataType.UWORD -> NumericLiteral(DataType.UWORD, (constval.number.toInt().inv() and 65535).toDouble(), constval.position)
                else -> throw ExpressionError("can only take bitwise inversion of int", constval.position)
            }
            "not" -> NumericLiteral.fromBoolean(constval.number == 0.0, constval.position)
            else -> throw FatalAstException("invalid operator")
        }
        converted.linkParents(this.parent)
        return converted
    }
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(nameInSource: List<String>) = expression.referencesIdentifier(nameInSource)
    override fun inferType(program: Program): InferredTypes.InferredType {
        val inferred = expression.inferType(program)
        return when(operator) {
            "+" -> inferred
            "~", "not" -> {
                when(inferred.getOr(DataType.UNDEFINED)) {
                    in ByteDatatypes -> InferredTypes.knownFor(DataType.UBYTE)
                    in WordDatatypes -> InferredTypes.knownFor(DataType.UWORD)
                    else -> inferred
                }
            }
            "-" -> {
                when(inferred.getOr(DataType.UNDEFINED)) {
                    in ByteDatatypes -> InferredTypes.knownFor(DataType.BYTE)
                    in WordDatatypes -> InferredTypes.knownFor(DataType.WORD)
                    else -> inferred
                }
            }
            else -> throw FatalAstException("weird prefix expression operator")
        }
    }

    override val isSimple = true

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

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Expression)
        when {
            node===left -> left = replacement
            node===right -> right = replacement
            else -> throw FatalAstException("invalid replace, no child $node")
        }
        replacement.parent = this
    }

    override fun copy() = BinaryExpression(left.copy(), operator, right.copy(), position)
    override fun toString() = "[$left $operator $right]"

    override val isSimple = false

    // binary expression should actually have been optimized away into a single value, before const value was requested...
    override fun constValue(program: Program): NumericLiteral? = null

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(nameInSource: List<String>) = left.referencesIdentifier(nameInSource) || right.referencesIdentifier(nameInSource)
    override fun inferType(program: Program): InferredTypes.InferredType {
        val leftDt = left.inferType(program)
        val rightDt = right.inferType(program)
        return when (operator) {
            "+", "-", "*", "**", "%", "/" -> {
                if (!leftDt.isKnown || !rightDt.isKnown)
                    InferredTypes.unknown()
                else {
                    try {
                        InferredTypes.knownFor(
                            commonDatatype(
                                leftDt.getOr(DataType.BYTE),
                                rightDt.getOr(DataType.BYTE),
                                null, null
                            ).first
                        )
                    } catch (x: FatalAstException) {
                        InferredTypes.unknown()
                    }
                }
            }
            "&" -> leftDt
            "|" -> leftDt
            "^" -> leftDt
            "and", "or", "xor",
            "<", ">",
            "<=", ">=",
            "==", "!=" -> InferredTypes.knownFor(DataType.UBYTE)
            "<<", ">>" -> leftDt
            "in" -> InferredTypes.knownFor(DataType.UBYTE)
            else -> throw FatalAstException("resulting datatype check for invalid operator $operator")
        }
    }

    companion object {
        fun commonDatatype(leftDt: DataType, rightDt: DataType,
                           left: Expression?, right: Expression?): Pair<DataType, Expression?> {
            // byte + byte -> byte
            // byte + word -> word
            // word + byte -> word
            // word + word -> word
            // a combination with a float will be float (but give a warning about this!)

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
                        DataType.BYTE -> Pair(DataType.WORD, right)
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
}

class ArrayIndexedExpression(var arrayvar: IdentifierReference,
                             val indexer: ArrayIndex,
                             override val position: Position) : Expression() {
    override lateinit var parent: Node
    override fun linkParents(parent: Node) {
        this.parent = parent
        arrayvar.linkParents(this)
        indexer.linkParents(this)
    }

    override val isSimple = indexer.indexExpr is NumericLiteral || indexer.indexExpr is IdentifierReference

    override fun replaceChildNode(node: Node, replacement: Node) {
        when {
            node===arrayvar -> arrayvar = replacement as IdentifierReference
            else -> throw FatalAstException("invalid replace")
        }
        replacement.parent = this
    }

    override fun constValue(program: Program): NumericLiteral? = null
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(nameInSource: List<String>) = arrayvar.referencesIdentifier(nameInSource)

    override fun inferType(program: Program): InferredTypes.InferredType {
        val target = arrayvar.targetStatement(program)
        if (target is VarDecl) {
            return when (target.datatype) {
                DataType.STR, DataType.UWORD -> InferredTypes.knownFor(DataType.UBYTE)
                in ArrayDatatypes -> InferredTypes.knownFor(ArrayToElementTypes.getValue(target.datatype))
                else -> InferredTypes.unknown()
            }
        }
        return InferredTypes.unknown()
    }

    override fun toString(): String {
        return "ArrayIndexed(ident=$arrayvar, arraysize=$indexer; pos=$position)"
    }

    override fun copy() = ArrayIndexedExpression(arrayvar.copy(), indexer.copy(), position)
}

class TypecastExpression(var expression: Expression, var type: DataType, val implicit: Boolean, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        expression.linkParents(this)
    }

    override val isSimple = expression.isSimple

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Expression && node===expression)
        expression = replacement
        replacement.parent = this
    }

    override fun copy() = TypecastExpression(expression.copy(), type, implicit, position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(nameInSource: List<String>) = expression.referencesIdentifier(nameInSource)
    override fun inferType(program: Program) = InferredTypes.knownFor(type)
    override fun constValue(program: Program): NumericLiteral? {
        val cv = expression.constValue(program) ?: return null
        val cast = cv.cast(type)
        return if(cast.isValid) {
            val newval = cast.valueOrZero()
            newval.linkParents(parent)
            return newval
        }
        else
            null
    }

    override fun toString(): String {
        return "Typecast($expression as $type)"
    }
}

data class AddressOf(var identifier: IdentifierReference, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier.parent=this
    }

    override val isSimple = true

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is IdentifierReference && node===identifier)
        identifier = replacement
        replacement.parent = this
    }

    override fun copy() = AddressOf(identifier.copy(), position)
    override fun constValue(program: Program): NumericLiteral? = null
    override fun referencesIdentifier(nameInSource: List<String>) = identifier.nameInSource==nameInSource
    override fun inferType(program: Program) = InferredTypes.knownFor(DataType.UWORD)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)
}

class DirectMemoryRead(var addressExpression: Expression, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        this.addressExpression.linkParents(this)
    }

    override val isSimple = addressExpression is NumericLiteral || addressExpression is IdentifierReference

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Expression && node===addressExpression)
        addressExpression = replacement
        replacement.parent = this
    }

    override fun copy() = DirectMemoryRead(addressExpression.copy(), position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(nameInSource: List<String>) = addressExpression.referencesIdentifier(nameInSource)
    override fun inferType(program: Program) = InferredTypes.knownFor(DataType.UBYTE)
    override fun constValue(program: Program): NumericLiteral? = null

    override fun toString(): String {
        return "DirectMemoryRead($addressExpression)"
    }
}

class NumericLiteral(val type: DataType,    // only numerical types allowed
                     numbervalue: Double,    // can be byte, word or float depending on the type
                     override val position: Position) : Expression() {
    override lateinit var parent: Node
    val number: Double by lazy {
        if(type==DataType.FLOAT)
            numbervalue
        else {
            val rounded = round(numbervalue)
            if(rounded != numbervalue)
                throw ExpressionError("refused rounding of float to avoid loss of precision", position)
            rounded
        }
    }

    override val isSimple = true
    override fun copy() = NumericLiteral(type, number, position)

    companion object {
        fun fromBoolean(bool: Boolean, position: Position) =
                NumericLiteral(DataType.UBYTE, if (bool) 1.0 else 0.0, position)

        fun optimalNumeric(value: Number, position: Position): NumericLiteral {
            return if(value is Double) {
                NumericLiteral(DataType.FLOAT, value, position)
            } else {
                val dvalue = value.toDouble()
                when (value.toInt()) {
                    in 0..255 -> NumericLiteral(DataType.UBYTE, dvalue, position)
                    in -128..127 -> NumericLiteral(DataType.BYTE, dvalue, position)
                    in 0..65535 -> NumericLiteral(DataType.UWORD, dvalue, position)
                    in -32768..32767 -> NumericLiteral(DataType.WORD, dvalue, position)
                    else -> NumericLiteral(DataType.FLOAT, dvalue, position)
                }
            }
        }

        fun optimalInteger(value: Int, position: Position): NumericLiteral {
            val dvalue = value.toDouble()
            return when (value) {
                in 0..255 -> NumericLiteral(DataType.UBYTE, dvalue, position)
                in -128..127 -> NumericLiteral(DataType.BYTE, dvalue, position)
                in 0..65535 -> NumericLiteral(DataType.UWORD, dvalue, position)
                in -32768..32767 -> NumericLiteral(DataType.WORD, dvalue, position)
                else -> throw FatalAstException("integer overflow: $dvalue")
            }
        }

        fun optimalInteger(value: UInt, position: Position): NumericLiteral {
            return when (value) {
                in 0u..255u -> NumericLiteral(DataType.UBYTE, value.toDouble(), position)
                in 0u..65535u -> NumericLiteral(DataType.UWORD, value.toDouble(), position)
                else -> throw FatalAstException("unsigned integer overflow: $value")
            }
        }
    }

    val asBooleanValue: Boolean = number != 0.0

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        throw FatalAstException("can't replace here")
    }

    override fun referencesIdentifier(nameInSource: List<String>) = false
    override fun constValue(program: Program): NumericLiteral {
        return copy().also {
            if(::parent.isInitialized)
                it.parent = parent
        }
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun toString(): String = "NumericLiteral(${type.name}:$number)"

    override fun inferType(program: Program) = InferredTypes.knownFor(type)

    override fun hashCode(): Int = Objects.hash(type, number)

    override fun equals(other: Any?): Boolean {
        if(other==null || other !is NumericLiteral)
            return false
        return number==other.number
    }

    operator fun compareTo(other: NumericLiteral): Int = number.compareTo(other.number)

    class CastValue(val isValid: Boolean, private val value: NumericLiteral?) {
        fun valueOrZero() = if(isValid) value!! else NumericLiteral(DataType.UBYTE, 0.0, Position.DUMMY)
        fun linkParent(parent: Node) {
            value?.linkParents(parent)
        }
    }

    fun cast(targettype: DataType): CastValue {
        val result = internalCast(targettype)
        result.linkParent(this.parent)
        return result
    }

    private fun internalCast(targettype: DataType): CastValue {
        if(type==targettype)
            return CastValue(true, this)
        when(type) {
            DataType.UBYTE -> {
                if(targettype== DataType.BYTE && number <= 127)
                    return CastValue(true, NumericLiteral(targettype, number, position))
                if(targettype== DataType.WORD || targettype== DataType.UWORD)
                    return CastValue(true, NumericLiteral(targettype, number, position))
                if(targettype== DataType.FLOAT)
                    return CastValue(true, NumericLiteral(targettype, number, position))
            }
            DataType.BYTE -> {
                if(targettype== DataType.UBYTE && number >= 0)
                    return CastValue(true, NumericLiteral(targettype, number, position))
                if(targettype== DataType.UWORD && number >= 0)
                    return CastValue(true, NumericLiteral(targettype, number, position))
                if(targettype== DataType.WORD)
                    return CastValue(true, NumericLiteral(targettype, number, position))
                if(targettype== DataType.FLOAT)
                    return CastValue(true, NumericLiteral(targettype, number, position))
            }
            DataType.UWORD -> {
                if(targettype== DataType.BYTE && number <= 127)
                    return CastValue(true, NumericLiteral(targettype, number, position))
                if(targettype== DataType.UBYTE && number <= 255)
                    return CastValue(true, NumericLiteral(targettype, number, position))
                if(targettype== DataType.WORD && number <= 32767)
                    return CastValue(true, NumericLiteral(targettype, number, position))
                if(targettype== DataType.FLOAT)
                    return CastValue(true, NumericLiteral(targettype, number, position))
            }
            DataType.WORD -> {
                if(targettype== DataType.BYTE && number >= -128 && number <=127)
                    return CastValue(true, NumericLiteral(targettype, number, position))
                if(targettype== DataType.UBYTE && number >= 0 && number <= 255)
                    return CastValue(true, NumericLiteral(targettype, number, position))
                if(targettype== DataType.UWORD && number >=0)
                    return CastValue(true, NumericLiteral(targettype, number, position))
                if(targettype== DataType.FLOAT)
                    return CastValue(true, NumericLiteral(targettype, number, position))
            }
            DataType.FLOAT -> {
                try {
                    if (targettype == DataType.BYTE && number >= -128 && number <= 127)
                        return CastValue(true, NumericLiteral(targettype, number, position))
                    if (targettype == DataType.UBYTE && number >= 0 && number <= 255)
                        return CastValue(true, NumericLiteral(targettype, number, position))
                    if (targettype == DataType.WORD && number >= -32768 && number <= 32767)
                        return CastValue(true, NumericLiteral(targettype, number, position))
                    if (targettype == DataType.UWORD && number >= 0 && number <= 65535)
                        return CastValue(true, NumericLiteral(targettype, number, position))
                } catch (x: ExpressionError) {
                    return CastValue(false, null)
                }
            }
            else -> {}
        }
        return CastValue(false, null)
    }
}

class CharLiteral(val value: Char,
                  var encoding: Encoding,
                  override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override val isSimple = true

    override fun replaceChildNode(node: Node, replacement: Node) {
        throw FatalAstException("can't replace here")
    }

    override fun copy() =
        CharLiteral(value, encoding, position)
    override fun referencesIdentifier(nameInSource: List<String>) = false
    override fun constValue(program: Program): NumericLiteral {
        val bytevalue = program.encoding.encodeString(value.toString(), encoding).single()
        return NumericLiteral(DataType.UBYTE, bytevalue.toDouble(), position)
    }
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    override fun toString(): String = "'${escape(value.toString())}'"
    override fun inferType(program: Program) = InferredTypes.knownFor(DataType.UBYTE)
    operator fun compareTo(other: CharLiteral): Int = value.compareTo(other.value)
    override fun hashCode(): Int = Objects.hash(value, encoding)
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is CharLiteral)
            return false
        return value == other.value && encoding == other.encoding
    }
}

class StringLiteral(val value: String,
                    var encoding: Encoding,
                    override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override val isSimple = true
    override fun copy() = StringLiteral(value, encoding, position)

    override fun replaceChildNode(node: Node, replacement: Node) {
        throw FatalAstException("can't replace here")
    }

    override fun referencesIdentifier(nameInSource: List<String>) = false
    override fun constValue(program: Program): NumericLiteral? = null
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun toString(): String = "'${escape(value)}'"
    override fun inferType(program: Program) = InferredTypes.knownFor(DataType.STR)
    operator fun compareTo(other: StringLiteral): Int = value.compareTo(other.value)
    override fun hashCode(): Int = Objects.hash(value, encoding)
    override fun equals(other: Any?): Boolean {
        if(other==null || other !is StringLiteral)
            return false
        return value==other.value && encoding == other.encoding
    }
}

class ArrayLiteral(val type: InferredTypes.InferredType,     // inferred because not all array literals hava a known type yet
                   val value: Array<Expression>,
                   override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        value.forEach {it.linkParents(this)}
    }

    override fun copy() = throw NotImplementedError("no support for duplicating a ArrayLiteralValue")
    override val isSimple = true

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Expression)
        val idx = value.indexOfFirst { it===node }
        value[idx] = replacement
        replacement.parent = this
    }

    override fun referencesIdentifier(nameInSource: List<String>) = value.any { it.referencesIdentifier(nameInSource) }
    override fun constValue(program: Program): NumericLiteral? = null
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun toString(): String = "$value"
    override fun inferType(program: Program): InferredTypes.InferredType = if(type.isKnown) type else guessDatatype(program)

    operator fun compareTo(other: ArrayLiteral): Int = throw ExpressionError("cannot order compare arrays", position)
    override fun hashCode(): Int = Objects.hash(value, type)
    override fun equals(other: Any?): Boolean {
        if(other==null || other !is ArrayLiteral)
            return false
        return type==other.type && value.contentEquals(other.value)
    }

    fun guessDatatype(program: Program): InferredTypes.InferredType {
        // Educated guess of the desired array literal's datatype.
        // If it's inside a for loop, assume the data type of the loop variable is what we want.
        val forloop = parent as? ForLoop
        if(forloop != null)  {
            val loopvarDt = forloop.loopVarDt(program)
            if(loopvarDt.isKnown) {
                return if(!loopvarDt.isArrayElement)
                    InferredTypes.InferredType.unknown()
                else
                    InferredTypes.InferredType.known(ElementToArrayTypes.getValue(loopvarDt.getOr(DataType.UNDEFINED)))
            }
        }

        // otherwise, select the "biggegst" datatype based on the elements in the array.
        val datatypesInArray = value.map { it.inferType(program) }
        require(datatypesInArray.isNotEmpty() && datatypesInArray.all { it.isKnown }) { "can't determine type of empty array" }
        val dts = datatypesInArray.map { it.getOr(DataType.UNDEFINED) }
        return when {
            DataType.FLOAT in dts -> InferredTypes.InferredType.known(DataType.ARRAY_F)
            DataType.STR in dts -> InferredTypes.InferredType.known(DataType.ARRAY_UW)
            DataType.WORD in dts -> InferredTypes.InferredType.known(DataType.ARRAY_W)
            DataType.UWORD in dts -> InferredTypes.InferredType.known(DataType.ARRAY_UW)
            DataType.BYTE in dts -> InferredTypes.InferredType.known(DataType.ARRAY_B)
            DataType.UBYTE in dts -> InferredTypes.InferredType.known(DataType.ARRAY_UB)
            DataType.ARRAY_UW in dts ||
                DataType.ARRAY_W in dts ||
                DataType.ARRAY_UB in dts ||
                DataType.ARRAY_B in dts ||
                DataType.ARRAY_F in dts -> InferredTypes.InferredType.known(DataType.ARRAY_UW)
            else -> InferredTypes.InferredType.unknown()
        }
    }

    fun cast(targettype: DataType): ArrayLiteral? {
        if(type istype targettype)
            return this
        if(targettype in ArrayDatatypes) {
            val elementType = ArrayToElementTypes.getValue(targettype)
            val castArray = value.map{
                val num = it as? NumericLiteral
                if(num==null) {
                    // an array of UWORDs could possibly also contain AddressOfs, other stuff can't be typecasted
                    if (elementType != DataType.UWORD || it !is AddressOf)
                        return null  // can't cast a value of  the array, abort
                    it
                } else {
                    val cast = num.cast(elementType)
                    if(cast.isValid)
                        cast.valueOrZero()
                    else
                        return null // can't cast a value of the array, abort
                }
            }.toTypedArray()
            return ArrayLiteral(InferredTypes.InferredType.known(targettype), castArray, position = position)
        }
        return null    // invalid type conversion from $this to $targettype
    }
}

class RangeExpression(var from: Expression,
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

    override val isSimple = true

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Expression)
        when {
            from===node -> from=replacement
            to===node -> to=replacement
            step===node -> step=replacement
            else -> throw FatalAstException("invalid replacement")
        }
        replacement.parent = this
    }

    override fun copy() = RangeExpression(from.copy(), to.copy(), step.copy(), position)
    override fun constValue(program: Program): NumericLiteral? = null
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(nameInSource: List<String>): Boolean  = from.referencesIdentifier(nameInSource) || to.referencesIdentifier(nameInSource)
    override fun inferType(program: Program): InferredTypes.InferredType {
        val fromDt=from.inferType(program)
        val toDt=to.inferType(program)
        return when {
            !fromDt.isKnown || !toDt.isKnown -> InferredTypes.unknown()
            fromDt istype DataType.UBYTE && toDt istype DataType.UBYTE -> InferredTypes.knownFor(DataType.ARRAY_UB)
            fromDt istype DataType.UWORD && toDt istype DataType.UWORD -> InferredTypes.knownFor(DataType.ARRAY_UW)
            fromDt istype DataType.STR && toDt istype DataType.STR -> InferredTypes.knownFor(DataType.STR)
            fromDt istype DataType.WORD || toDt istype DataType.WORD -> InferredTypes.knownFor(DataType.ARRAY_W)
            fromDt istype DataType.BYTE || toDt istype DataType.BYTE -> InferredTypes.knownFor(DataType.ARRAY_B)
            else -> {
                val fdt = fromDt.getOr(DataType.UNDEFINED)
                val tdt = toDt.getOr(DataType.UNDEFINED)
                if(fdt largerThan tdt)
                    InferredTypes.knownFor(ElementToArrayTypes.getValue(fdt))
                else
                    InferredTypes.knownFor(ElementToArrayTypes.getValue(tdt))
            }
        }
    }
    override fun toString(): String {
        return "RangeExpr(from $from, to $to, step $step, pos=$position)"
    }

    fun toConstantIntegerRange(): IntProgression? {

        fun makeRange(fromVal: Int, toVal: Int, stepVal: Int): IntProgression {
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

        val fromLv = from as? NumericLiteral
        val toLv = to as? NumericLiteral
        val stepLv = step as? NumericLiteral
        if(fromLv==null || toLv==null || stepLv==null)
            return null
        val fromVal = fromLv.number.toInt()
        val toVal = toLv.number.toInt()
        val stepVal = stepLv.number.toInt()
        return makeRange(fromVal, toVal, stepVal)
    }


    fun size(): Int? {
        val fromLv = (from as? NumericLiteral)
        val toLv = (to as? NumericLiteral)
        if(fromLv==null || toLv==null)
            return null
        return toConstantIntegerRange()?.count()
    }
}


data class IdentifierReference(val nameInSource: List<String>, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override val isSimple = true

    fun targetStatement(program: Program) =
        if(nameInSource.size==1 && nameInSource[0] in program.builtinFunctions.names)
            BuiltinFunctionPlaceholder(nameInSource[0], position, parent)
        else
            definingScope.lookup(nameInSource)

    fun targetVarDecl(program: Program): VarDecl? = targetStatement(program) as? VarDecl
    fun targetSubroutine(program: Program): Subroutine? = targetStatement(program) as? Subroutine

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        throw FatalAstException("can't replace here")
    }

    override fun copy() = IdentifierReference(nameInSource, position)
    override fun constValue(program: Program): NumericLiteral? {
        val node = definingScope.lookup(nameInSource) ?: throw UndefinedSymbolError(this)
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

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(nameInSource: List<String>): Boolean = this.nameInSource==nameInSource

    override fun inferType(program: Program): InferredTypes.InferredType {
        return when (val targetStmt = targetStatement(program)) {
            is VarDecl -> InferredTypes.knownFor(targetStmt.datatype)
            else -> InferredTypes.InferredType.unknown()
        }
    }

    fun wasStringLiteral(program: Program): Boolean {
        val decl = targetVarDecl(program)
        if(decl == null || decl.origin!=VarDeclOrigin.STRINGLITERAL)
            return false

        val scope=decl.definingModule
        return scope.name==internedStringsModuleName
    }
}

class FunctionCallExpression(override var target: IdentifierReference,
                             override var args: MutableList<Expression>,
                             override val position: Position) : Expression(), IFunctionCall {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        args.forEach { it.linkParents(this) }
    }

    override fun copy() = FunctionCallExpression(target.copy(), args.map { it.copy() }.toMutableList(), position)
    override val isSimple = target.nameInSource.size==1 && (target.nameInSource[0] in arrayOf("msb", "lsb", "peek", "peekw"))

    override fun replaceChildNode(node: Node, replacement: Node) {
        if(node===target)
            target=replacement as IdentifierReference
        else {
            val idx = args.indexOfFirst { it===node }
            args[idx] = replacement as Expression
        }
        replacement.parent = this
    }

    override fun constValue(program: Program) = constValue(program, true)

    private fun constValue(program: Program, withDatatypeCheck: Boolean): NumericLiteral? {
        // if the function is a built-in function and the args are consts, should try to const-evaluate!
        // lenghts of arrays and strings are constants that are determined at compile time!
        if(target.nameInSource.size>1)
            return null
        val resultValue: NumericLiteral? = program.builtinFunctions.constValue(target.nameInSource[0], args, position)
        if(withDatatypeCheck) {
            val resultDt = this.inferType(program)
            if(resultValue==null || resultDt istype resultValue.type)
                return resultValue
            throw FatalAstException("evaluated const expression result value doesn't match expected datatype $resultDt, pos=$position")
        } else {
            return resultValue
        }
    }

    override fun toString(): String {
        return "FunctionCall(target=$target, pos=$position)"
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(nameInSource: List<String>): Boolean = target.referencesIdentifier(nameInSource) || args.any{it.referencesIdentifier(nameInSource)}

    override fun inferType(program: Program): InferredTypes.InferredType {
        val constVal = constValue(program ,false)
        if(constVal!=null)
            return InferredTypes.knownFor(constVal.type)
        val stmt = target.targetStatement(program) ?: return InferredTypes.unknown()
        when (stmt) {
            is BuiltinFunctionPlaceholder -> {
                return program.builtinFunctions.returnType(target.nameInSource[0], this.args)
            }
            is Subroutine -> {
                if(stmt.returntypes.isEmpty())
                    return InferredTypes.void()     // no return value
                if(stmt.returntypes.size==1)
                    return InferredTypes.knownFor(stmt.returntypes[0])

                // multiple return values. Can occur for asmsub routines. If there is exactly one register return value, take that.
                val numRegisterReturns = stmt.asmReturnvaluesRegisters.count { it.registerOrPair!=null }
                if(numRegisterReturns==1)
                    return InferredTypes.InferredType.known(DataType.UBYTE)

                return InferredTypes.unknown()     // has multiple return types... so not a single resulting datatype possible
            }
            else -> return InferredTypes.unknown()
        }
    }
}


class ContainmentCheck(var element: Expression,
                       var iterable: Expression,
                       override val position: Position): Expression() {

    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        element.parent = this
        iterable.linkParents(this)
    }

    companion object {
        const val max_inlined_string_length = 16
    }

    override val isSimple: Boolean = false
    override fun copy() = ContainmentCheck(element.copy(), iterable.copy(), position)
    override fun constValue(program: Program): NumericLiteral? {
        val elementConst = element.constValue(program)
        if(elementConst!=null) {
            when(iterable){
                is ArrayLiteral -> {
                    val exists = (iterable as ArrayLiteral).value.any { it.constValue(program)==elementConst }
                    return NumericLiteral.fromBoolean(exists, position)
                }
                is RangeExpression -> {
                    val intRange = (iterable as RangeExpression).toConstantIntegerRange()
                    if(intRange!=null && elementConst.type in IntegerDatatypes) {
                        val exists = elementConst.number.toInt() in intRange
                        return NumericLiteral.fromBoolean(exists, position)
                    }
                }
                is StringLiteral -> {
                    if(elementConst.type in ByteDatatypes) {
                        val stringval = iterable as StringLiteral
                        val exists = program.encoding.encodeString(stringval.value, stringval.encoding).contains(elementConst.number.toInt().toUByte() )
                        return NumericLiteral.fromBoolean(exists, position)
                    }
                }
                else -> {}
            }
        }

        when(iterable){
            is ArrayLiteral -> {
                val array= iterable as ArrayLiteral
                if(array.value.isEmpty())
                    return NumericLiteral.fromBoolean(false, position)
            }
            is RangeExpression -> {
                val size = (iterable as RangeExpression).size()
                if(size!=null && size==0)
                    return NumericLiteral.fromBoolean(false, position)
            }
            is StringLiteral -> {
                if((iterable as StringLiteral).value.isEmpty())
                    return NumericLiteral.fromBoolean(false, position)
            }
            else -> {}
        }

        return null
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun referencesIdentifier(nameInSource: List<String>): Boolean {
        if(element is IdentifierReference)
            return element.referencesIdentifier(nameInSource)
        return iterable.referencesIdentifier(nameInSource)
    }

    override fun inferType(program: Program) = InferredTypes.knownFor(DataType.UBYTE)

    override fun replaceChildNode(node: Node, replacement: Node) {
        if(replacement !is Expression)
            throw FatalAstException("invalid replace")
        if(node===element)
            element=replacement
        else if(node===iterable)
            iterable=replacement
        else
            throw FatalAstException("invalid replace")
    }
}

class PipeExpression(override var source: Expression,
                     override val segments: MutableList<FunctionCallExpression>,
                     override val position: Position): Expression(), IPipe {
    override lateinit var parent: Node

    override val isSimple = false
    override fun linkParents(parent: Node) {
        this.parent=parent
        source.linkParents(this)
        segments.forEach { it.linkParents(this) }
    }
    override fun copy(): PipeExpression = PipeExpression(source.copy(), segments.map {it.copy()}.toMutableList(), position)
    override fun constValue(program: Program): NumericLiteral? = null
    override fun referencesIdentifier(nameInSource: List<String>) =
        source.referencesIdentifier(nameInSource) || segments.any { it.referencesIdentifier(nameInSource) }
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun inferType(program: Program) = segments.last().inferType(program)

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(node is Expression)
        require(replacement is Expression)
        if(node===source) {
            source = replacement
        } else {
            val idx = segments.indexOf(node)
            segments[idx] = replacement as FunctionCallExpression
        }
    }
}


fun invertCondition(cond: Expression): BinaryExpression? {
    if(cond is BinaryExpression) {
        val invertedOperator = invertedComparisonOperator(cond.operator)
        if (invertedOperator != null)
            return BinaryExpression(cond.left, invertedOperator, cond.right, cond.position)
    }
    return null
}


class BuiltinFunctionCall(override var target: IdentifierReference,
                          override var args: MutableList<Expression>,
                          override val position: Position) : Expression(), IFunctionCall {

    val name = target.nameInSource.single()

    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        args.forEach { it.linkParents(this) }
    }

    override fun copy() = BuiltinFunctionCall(target.copy(), args.map { it.copy() }.toMutableList(), position)
    override val isSimple = name in arrayOf("msb", "lsb", "peek", "peekw", "set_carry", "set_irqd", "clear_carry", "clear_irqd")

    override fun replaceChildNode(node: Node, replacement: Node) {
        if(node===target)
            target=replacement as IdentifierReference
        else {
            val idx = args.indexOfFirst { it===node }
            args[idx] = replacement as Expression
        }
        replacement.parent = this
    }

    override fun constValue(program: Program): NumericLiteral? = null
    override fun toString() = "BuiltinFunctionCall(name=$name, pos=$position)"
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = target.referencesIdentifier(nameInSource) || args.any{it.referencesIdentifier(nameInSource)}
    override fun inferType(program: Program) = program.builtinFunctions.returnType(name, this.args)
}
