package prog8.ast.expressions

import prog8.ast.*
import prog8.ast.antlr.escape
import prog8.ast.base.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstVisitor
import java.util.Objects
import kotlin.math.abs


val associativeOperators = setOf("+", "*", "&", "|", "^", "or", "and", "xor", "==", "!=")
val comparisonOperators = setOf("==", "!=", "<", ">", "<=", ">=")
val augmentAssignmentOperators = setOf("+", "-", "/", "*", "**", "&", "|", "^", "<<", ">>", "%", "and", "or", "xor")
val logicalOperators = setOf("and", "or", "xor", "not")


sealed class Expression: Node {
    abstract fun constValue(program: Program): NumericLiteralValue?
    abstract fun accept(visitor: IAstVisitor)
    abstract fun accept(visitor: AstWalker, parent: Node)
    abstract fun referencesIdentifier(vararg scopedName: String): Boolean
    abstract fun inferType(program: Program): InferredTypes.InferredType

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
            is RangeExpr -> {
                (other is RangeExpr && other.from==from && other.to==to && other.step==step)
            }
            is FunctionCall -> {
                (other is FunctionCall && other.target.nameInSource == target.nameInSource
                        && other.args.size == args.size
                        && other.args.zip(args).all { it.first isSameAs it.second } )
            }
            else -> other==this
        }
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

    override fun constValue(program: Program): NumericLiteralValue? = null
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(vararg scopedName: String) = expression.referencesIdentifier(*scopedName)
    override fun inferType(program: Program): InferredTypes.InferredType {
        val inferred = expression.inferType(program)
        return when(operator) {
            "+" -> inferred
            "~", "not" -> {
                when(inferred.typeOrElse(DataType.STRUCT)) {
                    in ByteDatatypes -> InferredTypes.knownFor(DataType.UBYTE)
                    in WordDatatypes -> InferredTypes.knownFor(DataType.UWORD)
                    else -> inferred
                }
            }
            "-" -> {
                when(inferred.typeOrElse(DataType.STRUCT)) {
                    in ByteDatatypes -> InferredTypes.knownFor(DataType.BYTE)
                    in WordDatatypes -> InferredTypes.knownFor(DataType.WORD)
                    else -> inferred
                }
            }
            else -> throw FatalAstException("weird prefix expression operator")
        }
    }

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

    override fun toString(): String {
        return "[$left $operator $right]"
    }

    // binary expression should actually have been optimized away into a single value, before const value was requested...
    override fun constValue(program: Program): NumericLiteralValue? = null

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(vararg scopedName: String) = left.referencesIdentifier(*scopedName) || right.referencesIdentifier(*scopedName)
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
                                leftDt.typeOrElse(DataType.BYTE),
                                rightDt.typeOrElse(DataType.BYTE),
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
                             override val position: Position) : Expression(), IAssignable {
    override lateinit var parent: Node
    override fun linkParents(parent: Node) {
        this.parent = parent
        arrayvar.linkParents(this)
        indexer.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        when {
            node===arrayvar -> arrayvar = replacement as IdentifierReference
            else -> throw FatalAstException("invalid replace")
        }
        replacement.parent = this
    }

    override fun constValue(program: Program): NumericLiteralValue? = null
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(vararg scopedName: String) = arrayvar.referencesIdentifier(*scopedName)

    override fun inferType(program: Program): InferredTypes.InferredType {
        val target = arrayvar.targetStatement(program)
        if (target is VarDecl) {
            return when (target.datatype) {
                DataType.STR -> InferredTypes.knownFor(DataType.UBYTE)
                in ArrayDatatypes -> InferredTypes.knownFor(ArrayElementTypes.getValue(target.datatype))
                else -> InferredTypes.unknown()
            }
        }
        return InferredTypes.unknown()
    }

    override fun toString(): String {
        return "ArrayIndexed(ident=$arrayvar, arraysize=$indexer; pos=$position)"
    }

    fun copy() = ArrayIndexedExpression(arrayvar.copy(), indexer.copy(), position)
}

class TypecastExpression(var expression: Expression, var type: DataType, val implicit: Boolean, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        expression.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Expression && node===expression)
        expression = replacement
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(vararg scopedName: String) = expression.referencesIdentifier(*scopedName)
    override fun inferType(program: Program): InferredTypes.InferredType = InferredTypes.knownFor(type)
    override fun constValue(program: Program): NumericLiteralValue? {
        val cv = expression.constValue(program) ?: return null
        val cast = cv.cast(type)
        return if(cast.isValid)
            cast.valueOrZero()
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

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is IdentifierReference && node===identifier)
        identifier = replacement
        replacement.parent = this
    }

    override fun constValue(program: Program): NumericLiteralValue? = null
    override fun referencesIdentifier(vararg scopedName: String) = false
    override fun inferType(program: Program): InferredTypes.InferredType = InferredTypes.knownFor(DataType.UWORD)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)
}

class DirectMemoryRead(var addressExpression: Expression, override val position: Position) : Expression(), IAssignable {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        this.addressExpression.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Expression && node===addressExpression)
        addressExpression = replacement
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(vararg scopedName: String) = false
    override fun inferType(program: Program): InferredTypes.InferredType = InferredTypes.knownFor(DataType.UBYTE)
    override fun constValue(program: Program): NumericLiteralValue? = null

    override fun toString(): String {
        return "DirectMemoryRead($addressExpression)"
    }

    fun copy() =  DirectMemoryRead(addressExpression, position)
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
                in -32768..32767 -> NumericLiteralValue(DataType.WORD, value, position)
                else -> throw FatalAstException("integer overflow: $value")
            }
        }
    }

    val asBooleanValue: Boolean = number.toDouble() != 0.0

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        throw FatalAstException("can't replace here")
    }

    override fun referencesIdentifier(vararg scopedName: String) = false
    override fun constValue(program: Program) = this

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun toString(): String = "NumericLiteral(${type.name}:$number)"

    override fun inferType(program: Program): InferredTypes.InferredType = InferredTypes.knownFor(type)

    override fun hashCode(): Int = Objects.hash(type, number)

    override fun equals(other: Any?): Boolean {
        if(other==null || other !is NumericLiteralValue)
            return false
        return number.toDouble()==other.number.toDouble()
    }

    operator fun compareTo(other: NumericLiteralValue): Int = number.toDouble().compareTo(other.number.toDouble())

    class CastValue(val isValid: Boolean, private val value: NumericLiteralValue?) {
        fun valueOrZero() = if(isValid) value!! else NumericLiteralValue(DataType.UBYTE, 0, Position.DUMMY)
    }

    fun cast(targettype: DataType): CastValue {
        if(type==targettype)
            return CastValue(true, this)
        val numval = number.toDouble()
        when(type) {
            DataType.UBYTE -> {
                if(targettype== DataType.BYTE && numval <= 127)
                    return CastValue(true, NumericLiteralValue(targettype, number.toShort(), position))
                if(targettype== DataType.WORD || targettype== DataType.UWORD)
                    return CastValue(true, NumericLiteralValue(targettype, number.toInt(), position))
                if(targettype== DataType.FLOAT)
                    return CastValue(true, NumericLiteralValue(targettype, number.toDouble(), position))
            }
            DataType.BYTE -> {
                if(targettype== DataType.UBYTE && numval >= 0)
                    return CastValue(true, NumericLiteralValue(targettype, number.toShort(), position))
                if(targettype== DataType.UWORD && numval >= 0)
                    return CastValue(true, NumericLiteralValue(targettype, number.toInt(), position))
                if(targettype== DataType.WORD)
                    return CastValue(true, NumericLiteralValue(targettype, number.toInt(), position))
                if(targettype== DataType.FLOAT)
                    return CastValue(true, NumericLiteralValue(targettype, number.toDouble(), position))
            }
            DataType.UWORD -> {
                if(targettype== DataType.BYTE && numval <= 127)
                    return CastValue(true, NumericLiteralValue(targettype, number.toShort(), position))
                if(targettype== DataType.UBYTE && numval <= 255)
                    return CastValue(true, NumericLiteralValue(targettype, number.toShort(), position))
                if(targettype== DataType.WORD && numval <= 32767)
                    return CastValue(true, NumericLiteralValue(targettype, number.toInt(), position))
                if(targettype== DataType.FLOAT)
                    return CastValue(true, NumericLiteralValue(targettype, number.toDouble(), position))
            }
            DataType.WORD -> {
                if(targettype== DataType.BYTE && numval >= -128 && numval <=127)
                    return CastValue(true, NumericLiteralValue(targettype, number.toShort(), position))
                if(targettype== DataType.UBYTE && numval >= 0 && numval <= 255)
                    return CastValue(true, NumericLiteralValue(targettype, number.toShort(), position))
                if(targettype== DataType.UWORD && numval >=0)
                    return CastValue(true, NumericLiteralValue(targettype, number.toInt(), position))
                if(targettype== DataType.FLOAT)
                    return CastValue(true, NumericLiteralValue(targettype, number.toDouble(), position))
            }
            DataType.FLOAT -> {
                if (targettype == DataType.BYTE && numval >= -128 && numval <=127)
                    return CastValue(true, NumericLiteralValue(targettype, number.toShort(), position))
                if (targettype == DataType.UBYTE && numval >=0 && numval <= 255)
                    return CastValue(true, NumericLiteralValue(targettype, number.toShort(), position))
                if (targettype == DataType.WORD && numval >= -32768 && numval <= 32767)
                    return CastValue(true, NumericLiteralValue(targettype, number.toInt(), position))
                if (targettype == DataType.UWORD && numval >=0 && numval <= 65535)
                    return CastValue(true, NumericLiteralValue(targettype, number.toInt(), position))
            }
            else -> {}
        }
        return CastValue(false, null)
    }
}

class StringLiteralValue(val value: String,
                         val altEncoding: Boolean,          // such as: screencodes instead of Petscii for the C64
                         override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        throw FatalAstException("can't replace here")
    }

    override fun referencesIdentifier(vararg scopedName: String) = false
    override fun constValue(program: Program): NumericLiteralValue? = null
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun toString(): String = "'${escape(value)}'"
    override fun inferType(program: Program): InferredTypes.InferredType = InferredTypes.knownFor(DataType.STR)
    operator fun compareTo(other: StringLiteralValue): Int = value.compareTo(other.value)
    override fun hashCode(): Int = Objects.hash(value, altEncoding)
    override fun equals(other: Any?): Boolean {
        if(other==null || other !is StringLiteralValue)
            return false
        return value==other.value && altEncoding == other.altEncoding
    }
}

class ArrayLiteralValue(val type: InferredTypes.InferredType,     // inferred because not all array literals hava a known type yet
                        val value: Array<Expression>,
                        override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        value.forEach {it.linkParents(this)}
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Expression)
        val idx = value.indexOfFirst { it===node }
        value[idx] = replacement
        replacement.parent = this
    }

    override fun referencesIdentifier(vararg scopedName: String) = value.any { it.referencesIdentifier(*scopedName) }
    override fun constValue(program: Program): NumericLiteralValue? = null
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun toString(): String = "$value"
    override fun inferType(program: Program): InferredTypes.InferredType = if(type.isKnown) type else guessDatatype(program)

    operator fun compareTo(other: ArrayLiteralValue): Int = throw ExpressionError("cannot order compare arrays", position)
    override fun hashCode(): Int = Objects.hash(value, type)
    override fun equals(other: Any?): Boolean {
        if(other==null || other !is ArrayLiteralValue)
            return false
        return type==other.type && value.contentEquals(other.value)
    }

    fun memsize(memsizer: IMemSizer): Int {
        if(type.isKnown) {
            val eltType = ArrayElementTypes.getValue(type.typeOrElse(DataType.STRUCT))
            return memsizer.memorySize(eltType) * value.size
        }
        else throw IllegalArgumentException("array datatype is not yet known")
    }

    fun guessDatatype(program: Program): InferredTypes.InferredType {
        // Educated guess of the desired array literal's datatype.
        // If it's inside a for loop, assume the data type of the loop variable is what we want.
        val forloop = parent as? ForLoop
        if(forloop != null)  {
            val loopvarDt = forloop.loopVarDt(program)
            if(loopvarDt.isKnown) {
                return if(loopvarDt.typeOrElse(DataType.STRUCT) !in ElementArrayTypes)
                    InferredTypes.InferredType.unknown()
                else
                    InferredTypes.InferredType.known(ElementArrayTypes.getValue(loopvarDt.typeOrElse(DataType.STRUCT)))
            }
        }

        // otherwise, select the "biggegst" datatype based on the elements in the array.
        val datatypesInArray = value.map { it.inferType(program) }
        require(datatypesInArray.isNotEmpty() && datatypesInArray.all { it.isKnown }) { "can't determine type of empty array" }
        val dts = datatypesInArray.map { it.typeOrElse(DataType.STRUCT) }
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
                DataType.ARRAY_F in dts ||
                DataType.STRUCT in dts -> InferredTypes.InferredType.known(DataType.ARRAY_UW)
            else -> InferredTypes.InferredType.unknown()
        }
    }

    fun cast(targettype: DataType): ArrayLiteralValue? {
        if(type.istype(targettype))
            return this
        if(targettype in ArrayDatatypes) {
            val elementType = ArrayElementTypes.getValue(targettype)
            val castArray = value.map{
                val num = it as? NumericLiteralValue
                if(num==null) {
                    // an array of UWORDs could possibly also contain AddressOfs, other stuff can't be casted
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
            return ArrayLiteralValue(InferredTypes.InferredType.known(targettype), castArray, position = position)
        }
        return null    // invalid type conversion from $this to $targettype
    }
}

class RangeExpr(var from: Expression,
                var to: Expression,
                var step: Expression,
                private val encoding: IStringEncoding,
                override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        from.linkParents(this)
        to.linkParents(this)
        step.linkParents(this)
    }

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

    override fun constValue(program: Program): NumericLiteralValue? = null
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(vararg scopedName: String): Boolean  = from.referencesIdentifier(*scopedName) || to.referencesIdentifier(*scopedName)
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
                val fdt = fromDt.typeOrElse(DataType.STRUCT)
                val tdt = toDt.typeOrElse(DataType.STRUCT)
                if(fdt largerThan tdt)
                    InferredTypes.knownFor(ElementArrayTypes.getValue(fdt))
                else
                    InferredTypes.knownFor(ElementArrayTypes.getValue(tdt))
            }
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
        val fromString = from as? StringLiteralValue
        val toString = to as? StringLiteralValue
        if(fromString!=null && toString!=null ) {
            // string range -> int range over character values
            fromVal = encoding.encodeString(fromString.value, fromString.altEncoding)[0].toInt()
            toVal = encoding.encodeString(toString.value, fromString.altEncoding)[0].toInt()
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
        return makeRange(fromVal, toVal, stepVal)
    }
}

internal fun makeRange(fromVal: Int, toVal: Int, stepVal: Int): IntProgression {
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

data class IdentifierReference(val nameInSource: List<String>, override val position: Position) : Expression(), IAssignable {
    override lateinit var parent: Node

    fun targetStatement(program: Program) =
        if(nameInSource.size==1 && nameInSource[0] in program.builtinFunctions.names)
            BuiltinFunctionStatementPlaceholder(nameInSource[0], position, parent)
        else
            program.namespace.lookup(nameInSource, this)

    fun targetVarDecl(program: Program): VarDecl? = targetStatement(program) as? VarDecl
    fun targetSubroutine(program: Program): Subroutine? = targetStatement(program) as? Subroutine

    override fun equals(other: Any?) = other is IdentifierReference && other.nameInSource==nameInSource     // NOTE: only compare by the name, not the position!
    override fun hashCode() = nameInSource.hashCode()

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        throw FatalAstException("can't replace here")
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

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)= visitor.visit(this, parent)

    override fun referencesIdentifier(vararg scopedName: String): Boolean =
            nameInSource.size==scopedName.size && nameInSource.toTypedArray().contentEquals(scopedName)

    override fun inferType(program: Program): InferredTypes.InferredType {
        return when (val targetStmt = targetStatement(program)) {
            is VarDecl -> InferredTypes.knownFor(targetStmt.datatype)
            is StructDecl -> InferredTypes.knownFor(DataType.STRUCT)
            else -> InferredTypes.InferredType.unknown()
        }
    }

    fun memberOfStruct(program: Program) = this.targetVarDecl(program)?.struct

    fun firstStructVarName(program: Program): String? {
        // take the name of the first struct member of the structvariable instead
        // if it's just a regular variable, return null.
        val struct = memberOfStruct(program) ?: return null
        val decl = targetVarDecl(program)!!
        if(decl.datatype!=DataType.STRUCT)
            return null

        val firstStructMember = struct.nameOfFirstMember()
        // find the flattened var that belongs to this first struct member
        val firstVarName = listOf(decl.name, firstStructMember)
        val firstVar = definingScope().lookup(firstVarName, this) as VarDecl
        return firstVar.name
    }
}

class FunctionCall(override var target: IdentifierReference,
                   override var args: MutableList<Expression>,
                   override val position: Position) : Expression(), IFunctionCall {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        args.forEach { it.linkParents(this) }
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

    private fun constValue(program: Program, withDatatypeCheck: Boolean): NumericLiteralValue? {
        // if the function is a built-in function and the args are consts, should try to const-evaluate!
        // lenghts of arrays and strings are constants that are determined at compile time!
        if(target.nameInSource.size>1)
            return null
        val resultValue: NumericLiteralValue? = program.builtinFunctions.constValue(target.nameInSource[0], args, position, program.memsizer)
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

    override fun referencesIdentifier(vararg scopedName: String): Boolean = target.referencesIdentifier(*scopedName) || args.any{it.referencesIdentifier(*scopedName)}

    override fun inferType(program: Program): InferredTypes.InferredType {
        val constVal = constValue(program ,false)
        if(constVal!=null)
            return InferredTypes.knownFor(constVal.type)
        val stmt = target.targetStatement(program) ?: return InferredTypes.unknown()
        when (stmt) {
            is BuiltinFunctionStatementPlaceholder -> {
                if(target.nameInSource[0] == "set_carry" || target.nameInSource[0]=="set_irqd" ||
                        target.nameInSource[0] == "clear_carry" || target.nameInSource[0]=="clear_irqd") {
                    return InferredTypes.void() // these have no return value
                }
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
