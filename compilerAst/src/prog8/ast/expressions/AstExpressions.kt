package prog8.ast.expressions

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.ExpressionError
import prog8.ast.base.FatalAstException
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstVisitor
import prog8.code.core.*
import prog8.code.target.encodings.JapaneseCharacterConverter
import java.io.CharConversionException
import java.util.Objects
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.truncate


sealed class Expression: Node {
    abstract override fun copy(): Expression
    abstract fun constValue(program: Program): NumericLiteral?
    abstract fun accept(visitor: IAstVisitor)
    abstract fun accept(visitor: AstWalker, parent: Node)
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
            is BinaryExpression -> {
                if(other !is BinaryExpression || other.operator!=operator)
                    false
                else if(operator in AssociativeOperators)
                    (other.left isSameAs left && other.right isSameAs right) || (other.left isSameAs right && other.right isSameAs left)
                else
                    other.left isSameAs left && other.right isSameAs right
            }
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
                (other is AddressOf && other.identifier.nameInSource == identifier.nameInSource && other.arrayIndex==arrayIndex)
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
        if(operator=="^") {
            val valueDt = expression.inferType(program)
            if(valueDt.isBytes || valueDt.isWords)
                return NumericLiteral(DataType.UBYTE, 0.0, expression.position)
        }
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
            "not" -> NumericLiteral.fromBoolean(constval.number==0.0, constval.position)
            "^" -> NumericLiteral(DataType.UBYTE, (constval.number.toInt() ushr 16 and 255).toDouble(), constval.position)  // bank
            "<<" -> NumericLiteral(DataType.UWORD, (constval.number.toInt() and 65535).toDouble(), constval.position)       // address
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
            "not" -> InferredTypes.knownFor(DataType.BOOL)
            "~" -> {
                if(inferred.isBytes) InferredTypes.knownFor(DataType.UBYTE)
                else if(inferred.isWords) InferredTypes.knownFor(DataType.UWORD)
                else InferredTypes.InferredType.unknown()
            }
            "-" -> {
                if(inferred.isBytes) InferredTypes.knownFor(DataType.BYTE)
                else if(inferred.isWords) InferredTypes.knownFor(DataType.WORD)
                else inferred
            }
            "^" -> InferredTypes.knownFor(DataType.UBYTE)
            "<<" -> InferredTypes.knownFor(DataType.UWORD)
            else -> throw FatalAstException("weird prefix expression operator")
        }
    }

    override val isSimple = expression.isSimple

    override fun toString(): String {
        return "Prefix($operator $expression)"
    }
}

class BinaryExpression(
    var left: Expression,
    var operator: String,
    var right: Expression,
    override val position: Position,
    private val insideParentheses: Boolean = false
) : Expression() {
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

    override fun copy() = BinaryExpression(left.copy(), operator, right.copy(), position, insideParentheses)
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
            "+", "-", "*", "%", "/" -> {
                if (!leftDt.isKnown || !rightDt.isKnown)
                    InferredTypes.unknown()
                else {
                    try {
                        val dt = InferredTypes.knownFor(
                            commonDatatype(
                                leftDt.getOr(DataType.BYTE),
                                rightDt.getOr(DataType.BYTE),
                                null, null
                            ).first
                        )
                        if(operator=="*") {
                            // if both operands are the same, X*X is always positive.
                            if(left isSameAs right) {
                                if(dt.istype(DataType.BYTE))
                                    InferredTypes.knownFor(DataType.UBYTE)
                                else if(dt.istype(DataType.WORD))
                                    InferredTypes.knownFor(DataType.UWORD)
                                else
                                    dt
                            } else
                                dt
                        } else
                            dt
                    } catch (x: FatalAstException) {
                        InferredTypes.unknown()
                    }
                }
            }
            "&", "|", "^" -> when(leftDt.getOr(DataType.UNDEFINED)) {
                DataType.BYTE -> InferredTypes.knownFor(DataType.UBYTE)
                DataType.WORD -> InferredTypes.knownFor(DataType.UWORD)
                DataType.BOOL -> InferredTypes.knownFor(DataType.UBYTE)
                else -> leftDt
            }
            "and", "or", "xor", "not", "in", "not in",
            "<", ">",
            "<=", ">=",
            "==", "!=" -> InferredTypes.knownFor(DataType.BOOL)
            "<<", ">>" -> leftDt
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
                DataType.BOOL -> {
                    return if(rightDt==DataType.BOOL)
                        Pair(DataType.BOOL, null)
                    else
                        Pair(DataType.BOOL, right)
                }
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

    override fun referencesIdentifier(nameInSource: List<String>) = arrayvar.referencesIdentifier(nameInSource) || indexer.referencesIdentifier(nameInSource)

    override fun inferType(program: Program): InferredTypes.InferredType {
        val target = arrayvar.targetStatement(program)
        if (target is VarDecl) {
            return when (target.datatype) {
                DataType.STR, DataType.UWORD -> InferredTypes.knownFor(DataType.UBYTE)
                in ArrayDatatypes -> InferredTypes.knownFor(ArrayToElementTypes.getValue(target.datatype))
                else -> InferredTypes.knownFor(target.datatype)
            }
        }
        return InferredTypes.unknown()
    }

    override fun toString(): String {
        return "ArrayIndexed(ident=$arrayvar, idx=$indexer; pos=$position)"
    }

    override fun copy() = ArrayIndexedExpression(arrayvar.copy(), indexer.copy(), position)
}

class TypecastExpression(var expression: Expression, var type: DataType, val implicit: Boolean, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        expression.linkParents(this)
    }

    init {
        if(type==DataType.BOOL) require(!implicit) {"no implicit cast to boolean allowed"}
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
        cv.linkParents(parent)
        val cast = cv.cast(type, implicit)
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

data class AddressOf(var identifier: IdentifierReference, var arrayIndex: ArrayIndex?, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier.linkParents(this)
        arrayIndex?.linkParents(this)
    }

    override val isSimple = true

    override fun replaceChildNode(node: Node, replacement: Node) {
        if(node===identifier) {
            require(replacement is IdentifierReference)
            identifier = replacement
            replacement.parent = this
        } else if(node===arrayIndex) {
            require(replacement is ArrayIndex)
            arrayIndex = replacement
            replacement.parent = this
        } else {
            throw FatalAstException("invalid replace, no child node $node")
        }
    }

    override fun copy() = AddressOf(identifier.copy(), arrayIndex?.copy(), position)
    override fun constValue(program: Program): NumericLiteral? {
        val target = this.identifier.targetStatement(program)
        val targetVar = target as? VarDecl
        if(targetVar!=null) {
            if (targetVar.type == VarDeclType.MEMORY || targetVar.type == VarDeclType.CONST) {
                var address = targetVar.value?.constValue(program)?.number
                if (address != null) {
                    if (arrayIndex != null) {
                        val index = arrayIndex?.constIndex()
                        if (index != null) {
                            address += when (targetVar.datatype) {
                                DataType.UWORD -> index
                                in ArrayDatatypes -> program.memsizer.memorySize(targetVar.datatype, index)
                                else -> throw FatalAstException("need array or uword ptr")
                            }
                        } else
                            return null
                    }
                    return NumericLiteral(DataType.UWORD, address, position)
                }
            }
        }
        val targetAsmAddress = (target as? Subroutine)?.asmAddress
        if(targetAsmAddress!=null) {
            val constAddress = targetAsmAddress.address.constValue(program)
            if(constAddress==null)
                return null
            return NumericLiteral(DataType.UWORD, constAddress.number, position)
        }
        return null
    }
    override fun referencesIdentifier(nameInSource: List<String>) = identifier.nameInSource==nameInSource || arrayIndex?.referencesIdentifier(nameInSource)==true
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
            val trunc = truncate(numbervalue)
            if(trunc != numbervalue)
                throw ExpressionError("refused truncating of float to avoid loss of precision", position)
            trunc
        }
    }

    override val isSimple = true
    override fun copy() = NumericLiteral(type, number, position)

    companion object {
        fun fromBoolean(bool: Boolean, position: Position) =
                NumericLiteral(DataType.BOOL, if(bool) 1.0 else 0.0, position)

        fun optimalNumeric(origType1: DataType, origType2: DataType?, value: Number, position: Position) : NumericLiteral {
            val optimal = optimalNumeric(value, position)
            val largestOrig = if(origType2==null) origType1 else if(origType1.largerThan(origType2)) origType1 else origType2
            return if(largestOrig.largerThan(optimal.type))
                NumericLiteral(largestOrig, optimal.number, position)
            else
                optimal
        }

        fun optimalInteger(origType1: DataType, origType2: DataType?, value: Int, position: Position): NumericLiteral {
            val optimal = optimalInteger(value, position)
            val largestOrig = if(origType2==null) origType1 else if(origType1.largerThan(origType2)) origType1 else origType2
            return if(largestOrig.largerThan(optimal.type))
                NumericLiteral(largestOrig, optimal.number, position)
            else
                optimal
        }

        fun optimalNumeric(value: Number, position: Position): NumericLiteral {
            val digits = floor(value.toDouble()) - value.toDouble()
            return if(value is Double && digits!=0.0) {
                NumericLiteral(DataType.FLOAT, value, position)
            } else {
                val dvalue = value.toDouble()
                when (value.toInt()) {
                    in 0..255 -> NumericLiteral(DataType.UBYTE, dvalue, position)
                    in -128..127 -> NumericLiteral(DataType.BYTE, dvalue, position)
                    in 0..65535 -> NumericLiteral(DataType.UWORD, dvalue, position)
                    in -32768..32767 -> NumericLiteral(DataType.WORD, dvalue, position)
                    in -2147483647..2147483647 -> NumericLiteral(DataType.LONG, dvalue, position)
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
                in -2147483647..2147483647 -> NumericLiteral(DataType.LONG, dvalue, position)
                else -> throw FatalAstException("integer overflow: $dvalue")
            }
        }

        fun optimalInteger(value: UInt, position: Position): NumericLiteral {
            return when (value) {
                in 0u..255u -> NumericLiteral(DataType.UBYTE, value.toDouble(), position)
                in 0u..65535u -> NumericLiteral(DataType.UWORD, value.toDouble(), position)
                in 0u..2147483647u -> NumericLiteral(DataType.LONG, value.toDouble(), position)
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
        return if(other==null || other !is NumericLiteral)
            false
        else if(type!=DataType.BOOL && other.type!=DataType.BOOL)
            number==other.number
        else
            type==other.type && number==other.number
    }

    operator fun compareTo(other: NumericLiteral): Int = number.compareTo(other.number)

    class ValueAfterCast(val isValid: Boolean, val whyFailed: String?, private val value: NumericLiteral?) {
        fun valueOrZero() = if(isValid) value!! else NumericLiteral(DataType.UBYTE, 0.0, Position.DUMMY)
        fun linkParent(parent: Node) {
            value?.linkParents(parent)
        }
    }

    fun cast(targettype: DataType, implicit: Boolean): ValueAfterCast {
        val result = internalCast(targettype, implicit)
        result.linkParent(this.parent)
        return result
    }

    private fun internalCast(targettype: DataType, implicit: Boolean): ValueAfterCast {

        // NOTE: this MAY convert a value into another when switching from singed to unsigned!!!

        if(type==targettype)
            return ValueAfterCast(true, null, this)
        if (implicit && targettype in IntegerDatatypes && type==DataType.BOOL)
            return ValueAfterCast(false, "no implicit cast from boolean to integer allowed", this)

        if(targettype == DataType.BOOL) {
            return if(implicit)
                ValueAfterCast(false, "no implicit cast to boolean allowed", this)
            else if(type in NumericDatatypes)
                ValueAfterCast(true, null, fromBoolean(number!=0.0, position))
            else
                ValueAfterCast(false, "no cast available from $type to BOOL", null)
        }


        when(type) {
            DataType.UBYTE -> {
                if(targettype==DataType.BYTE && (number in 0.0..127.0 || !implicit))
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number.toInt().toByte().toDouble(), position))
                if(targettype==DataType.WORD || targettype==DataType.UWORD)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==DataType.FLOAT)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==DataType.LONG)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
            }
            DataType.BYTE -> {
                if(targettype==DataType.UBYTE) {
                    if(number in -128.0..0.0)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number.toInt().toUByte().toDouble(), position))
                    else if(number in 0.0..255.0)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                }
                if(targettype==DataType.UWORD) {
                    if(number in -32768.0..0.0)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number.toInt().toUShort().toDouble(), position))
                    else if(number in 0.0..65535.0)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                }
                if(targettype==DataType.WORD)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==DataType.FLOAT)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==DataType.LONG)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
            }
            DataType.UWORD -> {
                if(targettype==DataType.BYTE && number <= 127)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==DataType.UBYTE && number <= 255)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==DataType.WORD && (number <= 32767 || !implicit))
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number.toInt().toShort().toDouble(), position))
                if(targettype==DataType.FLOAT)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==DataType.LONG)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
            }
            DataType.WORD -> {
                if(targettype==DataType.BYTE && number >= -128 && number <=127)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==DataType.UBYTE) {
                    if(number in -128.0..0.0)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number.toInt().toUByte().toDouble(), position))
                    else if(number in 0.0..255.0)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                }
                if(targettype==DataType.UWORD) {
                    if(number in -32768.0 .. 0.0)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number.toInt().toUShort().toDouble(), position))
                    else if(number in 0.0..65535.0)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                }
                if(targettype==DataType.FLOAT)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==DataType.LONG)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
            }
            DataType.FLOAT -> {
                try {
                    if (targettype == DataType.BYTE && number >= -128 && number <= 127)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if (targettype == DataType.UBYTE && number >= 0 && number <= 255)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if (targettype == DataType.WORD && number >= -32768 && number <= 32767)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if (targettype == DataType.UWORD && number >= 0 && number <= 65535)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if(targettype==DataType.LONG && number >=0 && number <= 2147483647)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                } catch (x: ExpressionError) {
                    return ValueAfterCast(false, x.message,null)
                }
            }
            DataType.BOOL -> {
                if(implicit)
                    return ValueAfterCast(false, "no implicit cast from boolean to integer allowed", null)
                else if(targettype in IntegerDatatypes)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
            }
            DataType.LONG -> {
                try {
                    if (targettype == DataType.BYTE && number >= -128 && number <= 127)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if (targettype == DataType.UBYTE && number >= 0 && number <= 255)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if (targettype == DataType.WORD && number >= -32768 && number <= 32767)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if (targettype == DataType.UWORD && number >= 0 && number <= 65535)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if(targettype==DataType.FLOAT)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                } catch (x: ExpressionError) {
                    return ValueAfterCast(false, x.message, null)
                }
            }
            else -> {
                throw FatalAstException("type cast of weird type $type")
            }
        }
        return ValueAfterCast(false, "no cast available from $type to $targettype number=$number", null)
    }

    fun convertTypeKeepValue(targetDt: DataType): ValueAfterCast {
        if(type==targetDt)
            return ValueAfterCast(true, null, this)

        when(type) {
            DataType.UBYTE -> {
                when(targetDt) {
                    DataType.BYTE -> if(number<=127.0) return cast(targetDt, false)
                    DataType.UWORD, DataType.WORD, DataType.LONG, DataType.FLOAT -> return cast(targetDt, false)
                    else -> {}
                }
            }
            DataType.BYTE -> {
                when(targetDt) {
                    DataType.UBYTE, DataType.UWORD -> if(number>=0.0) return cast(targetDt, false)
                    DataType.WORD, DataType.LONG, DataType.FLOAT -> return cast(targetDt, false)
                    else -> {}
                }
            }
            DataType.UWORD -> {
                when(targetDt) {
                    DataType.UBYTE -> if(number<=255.0) return cast(targetDt, false)
                    DataType.BYTE -> if(number<=127.0) return cast(targetDt, false)
                    DataType.WORD -> if(number<=32767.0) return cast(targetDt, false)
                    DataType.LONG, DataType.FLOAT -> return cast(targetDt, false)
                    else -> {}
                }
            }
            DataType.WORD -> {
                when(targetDt) {
                    DataType.UBYTE -> if(number in 0.0..255.0) return cast(targetDt, false)
                    DataType.BYTE -> if(number in -128.0..127.0) return cast(targetDt, false)
                    DataType.UWORD -> if(number in 0.0..32767.0) return cast(targetDt, false)
                    DataType.LONG, DataType.FLOAT -> return cast(targetDt, false)
                    else -> {}
                }
            }
            DataType.LONG, DataType.FLOAT -> return cast(targetDt, false)
            else -> {}
        }
        return ValueAfterCast(false, "no type conversion possible from $type to $targetDt", null)
    }
}

class CharLiteral private constructor(val value: Char,
                  var encoding: Encoding,
                  override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    companion object {
        fun create(character: Char, encoding: Encoding, position: Position): CharLiteral {
            return if(encoding==Encoding.KATAKANA) {
                val processed = JapaneseCharacterConverter.zenkakuKatakanaToHankakuKatakana(character.toString())
                if(processed.length==1)
                    CharLiteral(processed[0], encoding, position)
                else
                    throw CharConversionException("character literal encodes into multiple bytes at $position")
            } else
                CharLiteral(character, encoding, position)
        }

        fun fromEscaped(raw: String, encoding: Encoding, position: Position): CharLiteral {
            val unescaped = raw.unescape()
            return create(unescaped[0], encoding, position)
        }
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

    override fun toString(): String = "'${value.escape()}'"
    override fun inferType(program: Program) = InferredTypes.knownFor(DataType.UBYTE)
    operator fun compareTo(other: CharLiteral): Int = value.compareTo(other.value)
    override fun hashCode(): Int = Objects.hash(value, encoding)
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is CharLiteral)
            return false
        return value == other.value && encoding == other.encoding
    }
}

class StringLiteral private constructor(val value: String,
                    var encoding: Encoding,
                    override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    companion object {
        fun create(str: String, encoding: Encoding, position: Position): StringLiteral {
            if (encoding == Encoding.KATAKANA) {
                val processed = JapaneseCharacterConverter.zenkakuKatakanaToHankakuKatakana(str)
                return StringLiteral(processed, encoding, position)
            } else
                return StringLiteral(str, encoding, position)
        }

        fun fromEscaped(raw: String, encoding: Encoding, position: Position): StringLiteral = create(raw.unescape(), encoding, position)
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

    override fun toString(): String = "'${value.escape()}'"
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

    override fun copy(): ArrayLiteral = ArrayLiteral(type, value.map { it.copy() }.toTypedArray(), position)
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

        // otherwise, select the "biggest" datatype based on the elements in the array.
        require(value.isNotEmpty()) { "can't determine type of empty array" }
        val datatypesInArray = value.map { it.inferType(program) }
        if(datatypesInArray.any{ it.isUnknown })
            return InferredTypes.InferredType.unknown()
        val dts = datatypesInArray.map { it.getOr(DataType.UNDEFINED) }
        return when {
            DataType.FLOAT in dts -> InferredTypes.InferredType.known(DataType.ARRAY_F)
            DataType.STR in dts -> InferredTypes.InferredType.known(DataType.ARRAY_UW)
            DataType.WORD in dts -> InferredTypes.InferredType.known(DataType.ARRAY_W)
            DataType.UWORD in dts -> InferredTypes.InferredType.known(DataType.ARRAY_UW)
            DataType.BYTE in dts -> InferredTypes.InferredType.known(DataType.ARRAY_B)
            DataType.BOOL in dts -> {
                if(dts.all { it==DataType.BOOL})
                    InferredTypes.InferredType.known(DataType.ARRAY_BOOL)
                else
                    InferredTypes.InferredType.unknown()
            }
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

            // if all values are numeric literals, just do the cast.
            // if not:
            // if all values are numeric literals OR addressof OR identifiers, and the target type is WORD or UWORD,
            //    do the cast for the numeric literals and leave the rest.
            // otherwise: return null (cast cannot be done)

            if(value.all { it is NumericLiteral }) {
                val castArray = if(elementType==DataType.BOOL) {
                    value.map {
                        if((it as NumericLiteral).type==DataType.BOOL)
                            it
                        else
                            return null // abort
                    }
                } else {
                    value.map {
                        val cast = (it as NumericLiteral).cast(elementType, true)
                        if(cast.isValid)
                            cast.valueOrZero()
                        else
                            return null // abort
                    }
                }
                return ArrayLiteral(InferredTypes.InferredType.known(targettype), castArray.toTypedArray(), position = position)
            }
            else if(elementType in WordDatatypes && value.all { it is NumericLiteral || it is AddressOf || it is IdentifierReference}) {
                val castArray = value.map {
                    when(it) {
                        is AddressOf -> it
                        is IdentifierReference -> it
                        is NumericLiteral -> {
                            val numcast = it.cast(elementType, true)
                            if(numcast.isValid)
                                numcast.valueOrZero()
                            else
                                return null     // abort
                        }
                        else -> return null     // abort
                    }
                }.toTypedArray()
                return ArrayLiteral(InferredTypes.InferredType.known(targettype), castArray, position = position)
            }
            else
                return null
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

    override fun referencesIdentifier(nameInSource: List<String>): Boolean  = from.referencesIdentifier(nameInSource) || to.referencesIdentifier(nameInSource) || step.referencesIdentifier(nameInSource)
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
        if(nameInSource.singleOrNull() in program.builtinFunctions.names)
            BuiltinFunctionPlaceholder(nameInSource[0], position, parent)
        else
            definingScope.lookup(nameInSource)

    fun targetVarDecl(program: Program): VarDecl? = targetStatement(program) as? VarDecl
    fun targetSubroutine(program: Program): Subroutine? = targetStatement(program) as? Subroutine

    fun targetNameAndType(program: Program): Pair<String, DataType> {
        val target = targetStatement(program) as? INamedStatement  ?: throw FatalAstException("can't find target for $nameInSource")
        val targetname: String = if(target.name in program.builtinFunctions.names)
            "<builtin>.${target.name}"
        else
            target.scopedName.joinToString(".")
        val type = inferType(program).getOr(DataType.UNDEFINED)
        return Pair(targetname, type)
    }

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        throw FatalAstException("can't replace here")
    }

    override fun copy() = IdentifierReference(nameInSource, position)
    override fun constValue(program: Program): NumericLiteral? {
        val node = definingScope.lookup(nameInSource)
        if(node==null) {
            // maybe not a statement but perhaps a subroutine parameter?
            (definingScope as? Subroutine)?.let { sub ->
                if(sub.parameters.any { it.name==nameInSource.last() })
                    return null
            }
            return null
        }
        val vardecl = node as? VarDecl
        if(vardecl==null) {
            return null
        } else if(vardecl.type!= VarDeclType.CONST) {
            return null
        }

        // the value of a variable can (temporarily) be a different type as the vardecl itself.
        // don't return the value if the types don't match yet!
        val value = vardecl.value?.constValue(program)
        if(value==null || value.type==vardecl.datatype)
            return value
        val optimal = NumericLiteral.optimalNumeric(value.number, value.position)
        if(optimal.type==vardecl.datatype)
            return optimal
        return null
    }

    override fun toString(): String {
        return "IdentifierRef($nameInSource)"
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(nameInSource: List<String>): Boolean = this.nameInSource==nameInSource

    override fun inferType(program: Program): InferredTypes.InferredType {
        return when (val targetStmt = targetStatement(program)) {
            is VarDecl -> {
                if(targetStmt.datatype==DataType.UNDEFINED)
                    InferredTypes.unknown()
                else
                    InferredTypes.knownFor(targetStmt.datatype)
            }
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
                             override val args: MutableList<Expression>,
                             override val position: Position) : Expression(), IFunctionCall {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        args.forEach { it.linkParents(this) }
    }

    override fun copy() = FunctionCallExpression(target.copy(), args.map { it.copy() }.toMutableList(), position)
    override val isSimple = when (target.nameInSource.singleOrNull()) {
        in arrayOf("msb", "lsb", "mkword", "set_carry", "set_irqd", "clear_carry", "clear_irqd") -> this.args.all { it.isSimple }
        else -> false
    }
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
                return program.builtinFunctions.returnType(target.nameInSource[0])
            }
            is Subroutine -> {
                if(stmt.returntypes.isEmpty())
                    return InferredTypes.void()     // no return value
                if(stmt.returntypes.size==1)
                    return InferredTypes.knownFor(stmt.returntypes[0])

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
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = element.referencesIdentifier(nameInSource) || iterable.referencesIdentifier(nameInSource)

    override fun inferType(program: Program) = InferredTypes.knownFor(DataType.BOOL)

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

class IfExpression(var condition: Expression, var truevalue: Expression, var falsevalue: Expression, override val position: Position) : Expression() {

    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        condition.linkParents(this)
        truevalue.linkParents(this)
        falsevalue.linkParents(this)
    }

    override val isSimple: Boolean = condition.isSimple && truevalue.isSimple && falsevalue.isSimple

    override fun toString() = "IfExpr(cond=$condition, true=$truevalue, false=$falsevalue, pos=$position)"
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = condition.referencesIdentifier(nameInSource) || truevalue.referencesIdentifier(nameInSource) || falsevalue.referencesIdentifier(nameInSource)
    override fun inferType(program: Program): InferredTypes.InferredType {
        val t1 = truevalue.inferType(program)
        val t2 = falsevalue.inferType(program)
        return if(t1==t2) t1 else InferredTypes.InferredType.unknown()
    }

    override fun copy(): Expression = IfExpression(condition.copy(), truevalue.copy(), falsevalue.copy(), position)

    override fun constValue(program: Program): NumericLiteral? {
        val cond = condition.constValue(program)
        if(cond!=null) {
            return if (cond.asBooleanValue) truevalue.constValue(program) else falsevalue.constValue(program)
        }
        return null
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        if(replacement !is Expression)
            throw throw FatalAstException("invalid replace")
        if(node===condition) condition=replacement
        else if(node===truevalue) truevalue=replacement
        else if(node===falsevalue) falsevalue=replacement
        else throw FatalAstException("invalid replace")
    }
}

fun invertCondition(cond: Expression, program: Program): Expression {
    if(cond is BinaryExpression) {
        val invertedOperator = invertedComparisonOperator(cond.operator)
        if (invertedOperator != null)
            return BinaryExpression(cond.left, invertedOperator, cond.right, cond.position)
    }

    return if(cond.inferType(program).isBool)
        PrefixExpression("not", cond, cond.position)
    else
        BinaryExpression(cond, "==", NumericLiteral(DataType.UBYTE, 0.0, cond.position), cond.position)
}
