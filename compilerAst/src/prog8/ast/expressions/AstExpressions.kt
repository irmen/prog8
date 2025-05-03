package prog8.ast.expressions

import prog8.ast.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstVisitor
import prog8.code.INTERNED_STRINGS_MODULENAME
import prog8.code.core.*
import prog8.code.target.encodings.JapaneseCharacterConverter
import java.io.CharConversionException
import java.util.*
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
                (other is AddressOf && other.identifier?.nameInSource == identifier?.nameInSource && other.arrayIndex==arrayIndex && other.dereference==dereference)
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

    fun typecastTo(targetDt: BaseDataType, sourceDt: DataType, implicit: Boolean=false): Pair<Boolean, Expression> {
        require(!sourceDt.isUndefined && targetDt!=BaseDataType.UNDEFINED)
        if(sourceDt.base==targetDt && sourceDt.sub==null)
            return Pair(false, this)
        if(this is TypecastExpression) {
            this.type = DataType.forDt(targetDt)
            return Pair(false, this)
        }
        val typecast = TypecastExpression(this, DataType.forDt(targetDt), implicit, this.position)
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
            "-" -> when {
                constval.type.isInteger -> NumericLiteral.optimalInteger(-constval.number.toInt(), constval.position)
                constval.type == BaseDataType.FLOAT -> NumericLiteral(BaseDataType.FLOAT, -constval.number, constval.position)
                else -> throw ExpressionError("can only take negative of int or float", constval.position)
            }
            "~" -> when (constval.type) {
                BaseDataType.BYTE -> NumericLiteral(BaseDataType.BYTE, constval.number.toInt().inv().toDouble(), constval.position)
                BaseDataType.UBYTE -> NumericLiteral(BaseDataType.UBYTE, (constval.number.toInt().inv() and 255).toDouble(), constval.position)
                BaseDataType.WORD -> NumericLiteral(BaseDataType.WORD, constval.number.toInt().inv().toDouble(), constval.position)
                BaseDataType.UWORD -> NumericLiteral(BaseDataType.UWORD, (constval.number.toInt().inv() and 65535).toDouble(), constval.position)
                BaseDataType.LONG -> NumericLiteral(BaseDataType.LONG, constval.number.toInt().inv().toDouble(), constval.position)
                else -> throw ExpressionError("can only take bitwise inversion of int", constval.position)
            }
            "not" -> NumericLiteral.fromBoolean(constval.number==0.0, constval.position)
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
            "not" -> InferredTypes.knownFor(BaseDataType.BOOL)
            "~" -> {
                if(inferred.isBytes) InferredTypes.knownFor(BaseDataType.UBYTE)
                else if(inferred.isWords) InferredTypes.knownFor(BaseDataType.UWORD)
                else InferredTypes.unknown()
            }
            "-" -> {
                if(inferred.isBytes) InferredTypes.knownFor(BaseDataType.BYTE)
                else if(inferred.isWords) InferredTypes.knownFor(BaseDataType.WORD)
                else inferred
            }
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
                                if(dt istype DataType.BYTE)
                                    InferredTypes.knownFor(BaseDataType.UBYTE)
                                else if(dt istype DataType.WORD)
                                    InferredTypes.knownFor(BaseDataType.UWORD)
                                else
                                    dt
                            } else
                                dt
                        } else
                            dt
                    } catch (_: FatalAstException) {
                        InferredTypes.unknown()
                    }
                }
            }
            "&", "|", "^" -> when(leftDt.getOrUndef()) {
                DataType.BYTE -> InferredTypes.knownFor(BaseDataType.UBYTE)
                DataType.WORD -> InferredTypes.knownFor(BaseDataType.UWORD)
                DataType.BOOL -> InferredTypes.knownFor(BaseDataType.UBYTE)
                else -> leftDt
            }
            "and", "or", "xor", "not", "in", "not in",
            "<", ">",
            "<=", ">=",
            "==", "!=" -> InferredTypes.knownFor(BaseDataType.BOOL)
            "<<", ">>" -> leftDt
            "." -> {
                val leftExpr = left as? BinaryExpression
                val leftIdentfier = left as? IdentifierReference
                val leftIndexer = left as? ArrayIndexedExpression
                val rightIdentifier = right as? IdentifierReference
                val rightIndexer = right as? ArrayIndexedExpression
                if(rightIdentifier!=null) {
                    val struct: StructDecl? =
                        if (leftIdentfier != null) {
                            // PTR . FIELD
                            leftIdentfier.targetVarDecl()?.datatype?.subType as? StructDecl
                        } else if(leftIndexer!=null) {
                            // ARRAY[x].NAME --> maybe it's a pointer dereference
                            leftIndexer.arrayvar.targetVarDecl()?.datatype?.subType as? StructDecl
                        } else if(leftExpr!=null) {
                            // SOMEEXPRESSION . NAME
                            val leftDt = leftExpr.inferType(program)
                            if(leftDt.isPointer)
                                leftDt.getOrUndef().subType as StructDecl?
                            else
                                null
                        }
                        else null
                    if (struct == null)
                        InferredTypes.unknown()
                    else {
                        val fieldDt = if(rightIdentifier.nameInSource.size==1)
                                struct.getFieldType(rightIdentifier.nameInSource.single())
                            else
                                rightIdentifier.traverseDerefChain(struct)
                        if (fieldDt != null)
                            if(fieldDt.isUndefined) InferredTypes.unknown() else InferredTypes.knownFor(fieldDt)
                        else
                            InferredTypes.unknown()
                    }
                } else if(rightIndexer!=null) {
                    if(leftDt.isStructInstance) {
                        //  pointer[x].field[y] --> type is the dt of 'field'
                        var fieldDt = (leftDt.getOrUndef().subType as? StructDecl)?.getFieldType(rightIndexer.arrayvar.nameInSource.single())
                        if (fieldDt == null)
                            InferredTypes.unknown()
                        else {
                            val struct = fieldDt.subType as StructDecl
                            fieldDt = struct.getFieldType(rightIndexer.arrayvar.nameInSource.single())
                            if(fieldDt!=null)
                                if(fieldDt.isUndefined) InferredTypes.unknown() else InferredTypes.knownFor(fieldDt)
                            else
                                InferredTypes.unknown()
                        }
                    } else
                        InferredTypes.unknown() // TODO("something.field[x]  at ${right.position}")
                        // TODO I don't think we can evaluate this type because it could end up in as a struct instance, which we don't support yet... rewrite or just give an error?
                } else
                    InferredTypes.unknown()
            }
            else -> throw FatalAstException("resulting datatype check for invalid operator $operator")
        }
    }


    companion object {
        fun commonDatatype(leftDt: DataType, rightDt: DataType,
                           left: Expression?, right: Expression?): Pair<DataType, Expression?> {

            if(leftDt.isUndefined || rightDt.isUndefined)
                return DataType.UNDEFINED to null

            // byte + byte -> byte
            // byte + word -> word
            // word + byte -> word
            // word + word -> word
            // a combination with a float will be float (but give a warning about this!)

            // if left or right is a numeric literal, and its value fits in the type of the other operand, use the other's operand type
            // EXCEPTION: if the numeric value is a word and the other operand is a byte type (to allow   v * $0008  for example)
            if (left is NumericLiteral && rightDt.isNumericOrBool) {
                if(!(leftDt.isWord && rightDt.isByte)) {
                    val optimal = NumericLiteral.optimalNumeric(rightDt.base, null, left.number, left.position)
                    if (optimal.type != leftDt.base && DataType.forDt(optimal.type) isAssignableTo rightDt) {
                        return DataType.forDt(optimal.type) to left
                    }
                }
            }
            if (right is NumericLiteral && leftDt.isNumericOrBool) {
                if(!(rightDt.isWord && leftDt.isByte)) {
                    val optimal = NumericLiteral.optimalNumeric(leftDt.base, null, right.number, right.position)
                    if (optimal.type != rightDt.base && DataType.forDt(optimal.type) isAssignableTo leftDt) {
                        return DataType.forDt(optimal.type) to right
                    }
                }
            }

            return when (leftDt.base) {
                BaseDataType.BOOL -> {
                    return if(rightDt.isBool)
                        Pair(DataType.BOOL, null)
                    else
                        Pair(DataType.BOOL, right)
                }
                BaseDataType.UBYTE -> {
                    when (rightDt.base) {
                        BaseDataType.UBYTE -> Pair(DataType.UBYTE, null)
                        BaseDataType.BYTE -> Pair(DataType.BYTE, left)
                        BaseDataType.UWORD -> Pair(DataType.UWORD, left)
                        BaseDataType.WORD -> Pair(DataType.WORD, left)
                        BaseDataType.FLOAT -> Pair(DataType.FLOAT, left)
                        else -> Pair(leftDt, null)      // non-numeric datatype
                    }
                }
                BaseDataType.BYTE -> {
                    when (rightDt.base) {
                        BaseDataType.UBYTE -> Pair(DataType.BYTE, right)
                        BaseDataType.BYTE -> Pair(DataType.BYTE, null)
                        BaseDataType.UWORD -> Pair(DataType.WORD, left)
                        BaseDataType.WORD -> Pair(DataType.WORD, left)
                        BaseDataType.FLOAT -> Pair(DataType.FLOAT, left)
                        else -> Pair(leftDt, null)      // non-numeric datatype
                    }
                }
                BaseDataType.UWORD -> {
                    when (rightDt.base) {
                        BaseDataType.UBYTE -> Pair(DataType.UWORD, right)
                        BaseDataType.BYTE -> Pair(DataType.WORD, right)
                        BaseDataType.UWORD -> Pair(DataType.UWORD, null)
                        BaseDataType.WORD -> Pair(DataType.WORD, left)
                        BaseDataType.FLOAT -> Pair(DataType.FLOAT, left)
                        else -> Pair(leftDt, null)      // non-numeric datatype
                    }
                }
                BaseDataType.WORD -> {
                    when (rightDt.base) {
                        BaseDataType.UBYTE -> Pair(DataType.WORD, right)
                        BaseDataType.BYTE -> Pair(DataType.WORD, right)
                        BaseDataType.UWORD -> Pair(DataType.WORD, right)
                        BaseDataType.WORD -> Pair(DataType.WORD, null)
                        BaseDataType.FLOAT -> Pair(DataType.FLOAT, left)
                        else -> Pair(leftDt, null)      // non-numeric datatype
                    }
                }
                BaseDataType.FLOAT -> {
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
            return when {
                target.datatype.isString || target.datatype.isUnsignedWord -> InferredTypes.knownFor(BaseDataType.UBYTE)
                target.datatype.isArray -> InferredTypes.knownFor(target.datatype.elementType())
                target.datatype.isPointer -> {
                    if(target.datatype.subType!=null)
                        InferredTypes.knownFor(DataType.structInstance(target.datatype.subType))
                    else if(target.datatype.subTypeFromAntlr!=null)
                        InferredTypes.unknown()
                    else
                        InferredTypes.knownFor(target.datatype.sub!!)
                }
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
        if(!type.isBasic)
            return null
        val cv = expression.constValue(program) ?: return null
        cv.linkParents(parent)
        val cast = cv.cast(type.base, implicit)
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

data class AddressOf(var identifier: IdentifierReference?, var arrayIndex: ArrayIndex?, var dereference: PtrDereference?, val msb: Boolean, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
        arrayIndex?.linkParents(this)
        dereference?.linkParents(this)
    }

    override val isSimple = true

    override fun replaceChildNode(node: Node, replacement: Node) {
        when {
            node===identifier -> {
                if(replacement is IdentifierReference) {
                    identifier = replacement
                    arrayIndex = null
                    dereference = null
                } else {
                    dereference = replacement as PtrDereference
                    identifier = null
                    arrayIndex = null
                }
            }
            node===arrayIndex -> {
                require(replacement is ArrayIndex)
                arrayIndex = replacement
            }
            else -> {
                throw FatalAstException("invalid replace, no child node $node")
            }
        }
        replacement.parent = this
    }

    override fun copy() = AddressOf(identifier?.copy(), arrayIndex?.copy(), dereference?.copy(), msb, position)
    override fun constValue(program: Program): NumericLiteral? {
        if(msb)
            return null
        val target = this.identifier?.targetStatement(program)
        val targetVar = target as? VarDecl
        if(targetVar!=null) {
            if (targetVar.type == VarDeclType.MEMORY || targetVar.type == VarDeclType.CONST) {
                var address = targetVar.value?.constValue(program)?.number
                if (address != null) {
                    if (arrayIndex != null) {
                        val index = arrayIndex?.constIndex()
                        if (index != null) {
                            address += when {
                                target.datatype.isUnsignedWord -> index
                                target.datatype.isArray -> program.memsizer.memorySize(targetVar.datatype, index)
                                else -> throw FatalAstException("need array or uword ptr")
                            }
                        } else
                            return null
                    }
                    return NumericLiteral(BaseDataType.UWORD, address, position)
                }
            }
        }

        val targetAsmAddress = (target as? Subroutine)?.asmAddress
        if (targetAsmAddress != null) {
            val constAddress = targetAsmAddress.address.constValue(program)
            if (constAddress == null)
                return null
            return NumericLiteral(BaseDataType.UWORD, constAddress.number, position)
        }
        return null
    }
    override fun referencesIdentifier(nameInSource: List<String>) = identifier?.nameInSource==nameInSource || arrayIndex?.referencesIdentifier(nameInSource)==true || dereference?.referencesIdentifier(nameInSource)==true
    override fun inferType(program: Program) = InferredTypes.knownFor(BaseDataType.UWORD)
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
    override fun inferType(program: Program) = InferredTypes.knownFor(BaseDataType.UBYTE)
    override fun constValue(program: Program): NumericLiteral? = null

    override fun toString(): String {
        return "DirectMemoryRead($addressExpression)"
    }
}

class NumericLiteral(val type: BaseDataType,    // only numerical types allowed + bool (there is no separate BooleanLiteral node)
                     numbervalue: Double,    // can be byte, word or float depending on the type
                     override val position: Position) : Expression() {
    override lateinit var parent: Node
    val number: Double by lazy {
        if(type==BaseDataType.FLOAT)
            numbervalue
        else {
            val trunc = truncate(numbervalue)
            if(trunc != numbervalue)
                throw ExpressionError("refused truncating of float to avoid loss of precision", position)
            trunc
        }
    }

    init {
        when(type) {
            BaseDataType.UBYTE -> require(numbervalue in 0.0..255.0)
            BaseDataType.BYTE -> require(numbervalue in -128.0..127.0)
            BaseDataType.UWORD -> require(numbervalue in 0.0..65535.0)
            BaseDataType.WORD -> require(numbervalue in -32768.0..32767.0)
            BaseDataType.LONG -> require(numbervalue in -2147483647.0..2147483647.0)
            BaseDataType.BOOL -> require(numbervalue==0.0 || numbervalue==1.0)
            else ->  require(type.isNumericOrBool) { "numeric literal type should be numeric or bool: $type" }
        }
    }

    override val isSimple = true
    override fun copy() = NumericLiteral(type, number, position)

    companion object {
        fun fromBoolean(bool: Boolean, position: Position) =
                NumericLiteral(BaseDataType.BOOL, if(bool) 1.0 else 0.0, position)

        fun optimalNumeric(origType1: BaseDataType, origType2: BaseDataType?, value: Number, position: Position) : NumericLiteral =
            fromOptimal(optimalNumeric(value, position), origType1, origType2, position)

        fun optimalInteger(origType1: BaseDataType, origType2: BaseDataType?, value: Int, position: Position): NumericLiteral =
            fromOptimal(optimalInteger(value, position), origType1, origType2, position)

        fun optimalNumeric(value: Number, position: Position): NumericLiteral {
            val digits = floor(value.toDouble()) - value.toDouble()
            return if(value is Double && digits!=0.0) {
                NumericLiteral(BaseDataType.FLOAT, value, position)
            } else {
                val dvalue = value.toDouble()
                when (value.toInt()) {
                    in 0..255 -> NumericLiteral(BaseDataType.UBYTE, dvalue, position)
                    in -128..127 -> NumericLiteral(BaseDataType.BYTE, dvalue, position)
                    in 0..65535 -> NumericLiteral(BaseDataType.UWORD, dvalue, position)
                    in -32768..32767 -> NumericLiteral(BaseDataType.WORD, dvalue, position)
                    in -2147483647..2147483647 -> NumericLiteral(BaseDataType.LONG, dvalue, position)
                    else -> NumericLiteral(BaseDataType.FLOAT, dvalue, position)
                }
            }
        }

        fun optimalInteger(value: Int, position: Position): NumericLiteral {
            val dvalue = value.toDouble()
            return when (value) {
                in 0..255 -> NumericLiteral(BaseDataType.UBYTE, dvalue, position)
                in -128..127 -> NumericLiteral(BaseDataType.BYTE, dvalue, position)
                in 0..65535 -> NumericLiteral(BaseDataType.UWORD, dvalue, position)
                in -32768..32767 -> NumericLiteral(BaseDataType.WORD, dvalue, position)
                in -2147483647..2147483647 -> NumericLiteral(BaseDataType.LONG, dvalue, position)
                else -> throw FatalAstException("integer overflow: $dvalue")
            }
        }

        fun optimalInteger(value: UInt, position: Position): NumericLiteral {
            return when (value) {
                in 0u..255u -> NumericLiteral(BaseDataType.UBYTE, value.toDouble(), position)
                in 0u..65535u -> NumericLiteral(BaseDataType.UWORD, value.toDouble(), position)
                in 0u..2147483647u -> NumericLiteral(BaseDataType.LONG, value.toDouble(), position)
                else -> throw FatalAstException("unsigned integer overflow: $value")
            }
        }

        private fun fromOptimal(optimal: NumericLiteral, origType1: BaseDataType, origType2: BaseDataType?, position: Position): NumericLiteral {
            var largestOrig = if(origType2==null) origType1 else if(origType1.largerSizeThan(origType2)) origType1 else origType2
            return if(largestOrig.largerSizeThan(optimal.type)) {
                if(optimal.number<0 && !largestOrig.isSigned) {
                    when(largestOrig) {
                        BaseDataType.BOOL -> {}
                        BaseDataType.UBYTE -> largestOrig = BaseDataType.BYTE
                        BaseDataType.UWORD -> largestOrig = BaseDataType.WORD
                        else -> throw FatalAstException("invalid dt")
                    }
                }
                NumericLiteral(largestOrig, optimal.number, position)
            }
            else
                optimal
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

    override fun inferType(program: Program): InferredTypes.InferredType = when(type) {
        BaseDataType.UBYTE -> InferredTypes.knownFor(BaseDataType.UBYTE)
        BaseDataType.BYTE -> InferredTypes.knownFor(BaseDataType.BYTE)
        BaseDataType.UWORD -> InferredTypes.knownFor(BaseDataType.UWORD)
        BaseDataType.WORD -> InferredTypes.knownFor(BaseDataType.WORD)
        BaseDataType.LONG -> InferredTypes.knownFor(BaseDataType.LONG)
        BaseDataType.FLOAT -> InferredTypes.knownFor(BaseDataType.FLOAT)
        BaseDataType.BOOL -> InferredTypes.knownFor(BaseDataType.BOOL)
        BaseDataType.STR -> InferredTypes.knownFor(BaseDataType.STR)
        else -> throw IllegalArgumentException("invalid type for numeric literal: $type")
    }

    override fun hashCode(): Int = Objects.hash(type, number)

    override fun equals(other: Any?): Boolean {
        return if(other==null || other !is NumericLiteral)
            false
        else if(type!=BaseDataType.BOOL && other.type!=BaseDataType.BOOL)
            number==other.number
        else
            type==other.type && number==other.number
    }

    operator fun compareTo(other: NumericLiteral): Int = number.compareTo(other.number)

    class ValueAfterCast(val isValid: Boolean, val whyFailed: String?, private val value: NumericLiteral?) {
        fun valueOrZero() = if(isValid) value!! else NumericLiteral(BaseDataType.UBYTE, 0.0, Position.DUMMY)
        fun linkParent(parent: Node) {
            value?.linkParents(parent)
        }
    }

    fun cast(targettype: BaseDataType, implicit: Boolean): ValueAfterCast {
        val result = internalCast(targettype, implicit)
        result.linkParent(this.parent)
        return result
    }

    private fun internalCast(targettype: BaseDataType, implicit: Boolean): ValueAfterCast {

        // NOTE: this MAY convert a value into another when switching from singed to unsigned!!!

        if(type==targettype)
            return ValueAfterCast(true, null, this)
        if (implicit && targettype.isInteger && type== BaseDataType.BOOL)
            return ValueAfterCast(false, "no implicit cast from boolean to integer allowed", this)

        if(targettype == BaseDataType.BOOL) {
            return if(implicit)
                ValueAfterCast(false, "no implicit cast to boolean allowed", this)
            else if(type.isNumeric)
                ValueAfterCast(true, null, fromBoolean(number!=0.0, position))
            else
                ValueAfterCast(false, "no cast available from $type to BOOL", null)
        }


        when(type) {
            BaseDataType.UBYTE -> {
                if(targettype==BaseDataType.BYTE && (number in 0.0..127.0 || !implicit))
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number.toInt().toByte().toDouble(), position))
                if(targettype==BaseDataType.WORD || targettype==BaseDataType.UWORD)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==BaseDataType.FLOAT)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==BaseDataType.LONG)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype.isPointer)
                    return ValueAfterCast(true, null, NumericLiteral(BaseDataType.UWORD, number, position))
            }
            BaseDataType.BYTE -> {
                if(targettype==BaseDataType.UBYTE) {
                    if(number in -128.0..0.0 && !implicit)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number.toInt().toUByte().toDouble(), position))
                    else if(number in 0.0..255.0)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                }
                if(targettype==BaseDataType.UWORD) {
                    if(number in -32768.0..0.0 && !implicit)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number.toInt().toUShort().toDouble(), position))
                    else if(number in 0.0..65535.0)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                }
                if(targettype==BaseDataType.WORD)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==BaseDataType.FLOAT)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==BaseDataType.LONG)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
            }
            BaseDataType.UWORD -> {
                if(targettype==BaseDataType.BYTE && number <= 127)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==BaseDataType.UBYTE && number <= 255)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==BaseDataType.WORD && (number <= 32767 || !implicit))
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number.toInt().toShort().toDouble(), position))
                if(targettype==BaseDataType.FLOAT)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==BaseDataType.LONG)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
            }
            BaseDataType.WORD -> {
                if(targettype==BaseDataType.BYTE && number >= -128 && number <=127)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==BaseDataType.UBYTE) {
                    if(number in -128.0..0.0 && !implicit)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number.toInt().toUByte().toDouble(), position))
                    else if(number in 0.0..255.0)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                }
                if(targettype==BaseDataType.UWORD) {
                    if(number in -32768.0 .. 0.0 && !implicit)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number.toInt().toUShort().toDouble(), position))
                    else if(number in 0.0..65535.0)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                }
                if(targettype==BaseDataType.FLOAT)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                if(targettype==BaseDataType.LONG)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
            }
            BaseDataType.FLOAT -> {
                try {
                    if (targettype == BaseDataType.BYTE && number >= -128 && number <= 127)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if (targettype == BaseDataType.UBYTE && number >= 0 && number <= 255)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if (targettype == BaseDataType.WORD && number >= -32768 && number <= 32767)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if (targettype == BaseDataType.UWORD && number >= 0 && number <= 65535)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if(targettype==BaseDataType.LONG && number >=0 && number <= 2147483647)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                } catch (x: ExpressionError) {
                    return ValueAfterCast(false, x.message,null)
                }
            }
            BaseDataType.BOOL -> {
                if(implicit)
                    return ValueAfterCast(false, "no implicit cast from boolean to integer allowed", null)
                else if(targettype.isInteger)
                    return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
            }
            BaseDataType.LONG -> {
                try {
                    if (targettype == BaseDataType.BYTE && number >= -128 && number <= 127)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if (targettype == BaseDataType.UBYTE && number >= 0 && number <= 255)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if (targettype == BaseDataType.WORD && number >= -32768 && number <= 32767)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if (targettype == BaseDataType.UWORD && number >= 0 && number <= 65535)
                        return ValueAfterCast(true, null, NumericLiteral(targettype, number, position))
                    if(targettype==BaseDataType.FLOAT)
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

    fun convertTypeKeepValue(targetDt: BaseDataType): ValueAfterCast {
        if(type==targetDt)
            return ValueAfterCast(true, null, this)

        when(type) {
            BaseDataType.UBYTE -> {
                when(targetDt) {
                    BaseDataType.BYTE -> if(number<=127.0) return cast(targetDt, false)
                    BaseDataType.UWORD, BaseDataType.WORD, BaseDataType.LONG, BaseDataType.FLOAT -> return cast(targetDt, false)
                    else -> {}
                }
            }
            BaseDataType.BYTE -> {
                when(targetDt) {
                    BaseDataType.UBYTE, BaseDataType.UWORD -> if(number>=0.0) return cast(targetDt, false)
                    BaseDataType.WORD, BaseDataType.LONG, BaseDataType.FLOAT -> return cast(targetDt, false)
                    else -> {}
                }
            }
            BaseDataType.UWORD -> {
                when(targetDt) {
                    BaseDataType.UBYTE -> if(number<=255.0) return cast(targetDt, false)
                    BaseDataType.BYTE -> if(number<=127.0) return cast(targetDt, false)
                    BaseDataType.WORD -> if(number<=32767.0) return cast(targetDt, false)
                    BaseDataType.LONG, BaseDataType.FLOAT -> return cast(targetDt, false)
                    else -> {}
                }
            }
            BaseDataType.WORD -> {
                when(targetDt) {
                    BaseDataType.UBYTE -> if(number in 0.0..255.0) return cast(targetDt, false)
                    BaseDataType.BYTE -> if(number in -128.0..127.0) return cast(targetDt, false)
                    BaseDataType.UWORD -> if(number in 0.0..32767.0) return cast(targetDt, false)
                    BaseDataType.LONG, BaseDataType.FLOAT -> return cast(targetDt, false)
                    else -> {}
                }
            }
            BaseDataType.LONG, BaseDataType.FLOAT -> return cast(targetDt, false)
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
        return NumericLiteral(BaseDataType.UBYTE, bytevalue.toDouble(), position)
    }
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    override fun toString(): String = "'${value.escape()}'"
    override fun inferType(program: Program) = InferredTypes.knownFor(BaseDataType.UBYTE)
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
    override fun inferType(program: Program) = InferredTypes.knownFor(BaseDataType.STR)
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
                return if(!loopvarDt.isNumericOrBool)
                    InferredTypes.unknown()
                else
                    InferredTypes.InferredType.known(loopvarDt.getOrUndef().elementToArray())
            }
        }

        // otherwise, select the "biggest" datatype based on the elements in the array.
        require(value.isNotEmpty()) { "can't determine type of empty array" }
        val datatypesInArray = value.map { it.inferType(program) }
        if(datatypesInArray.any{ it.isUnknown })
            return InferredTypes.unknown()
        val dts = datatypesInArray.map { it.getOrUndef() }
        return when {
            dts.any { it.isFloat } -> InferredTypes.InferredType.known(DataType.arrayFor(BaseDataType.FLOAT))
            dts.any { it.isString } -> InferredTypes.InferredType.known(DataType.arrayFor(BaseDataType.UWORD))
            dts.any { it.isSignedWord } -> InferredTypes.InferredType.known(DataType.arrayFor(BaseDataType.WORD))
            dts.any { it.isUnsignedWord } -> InferredTypes.InferredType.known(DataType.arrayFor(BaseDataType.UWORD))
            dts.any { it.isSignedByte } -> InferredTypes.InferredType.known(DataType.arrayFor(BaseDataType.BYTE))
            dts.any { it.isBool } -> {
                if(dts.all { it.isBool})
                    InferredTypes.InferredType.known(DataType.arrayFor(BaseDataType.BOOL))
                else
                    InferredTypes.unknown()
            }
            dts.any { it.isUnsignedByte } -> InferredTypes.InferredType.known(DataType.arrayFor(BaseDataType.UBYTE))
            dts.any { it.isArray } -> InferredTypes.InferredType.known(DataType.arrayFor(BaseDataType.UWORD))
            else -> InferredTypes.unknown()
        }
    }

    fun cast(targettype: DataType): ArrayLiteral? {
        if(type istype targettype)
            return this
        if(targettype.isArray) {
            val elementType = targettype.elementType()

            // if all values are numeric literals, just do the cast.
            // if not:
            // if all values are numeric literals OR addressof OR identifiers, and the target type is WORD or UWORD,
            //    do the cast for the numeric literals and leave the rest.
            // otherwise: return null (cast cannot be done)

            if(value.all { it is NumericLiteral }) {
                val castArray = if(elementType.isBool) {
                    value.map {
                        if((it as NumericLiteral).type==BaseDataType.BOOL)
                            it
                        else
                            return null // abort
                    }
                } else {
                    if(!elementType.isNumericOrBool)
                        return null     // only a numeric or boolean array can be casted to another value
                    value.map {
                        val cast = (it as NumericLiteral).cast(elementType.base, true)
                        if(cast.isValid)
                            cast.valueOrZero()
                        else
                            return null // abort
                    }
                }
                return ArrayLiteral(InferredTypes.InferredType.known(targettype), castArray.toTypedArray(), position = position)
            }
            else if(elementType.isWord && value.all { it is NumericLiteral || it is AddressOf || it is IdentifierReference}) {
                val castArray = value.map {
                    when(it) {
                        is AddressOf -> it
                        is IdentifierReference -> it
                        is NumericLiteral -> {
                            val numcast = it.cast(elementType.base, true)
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
            fromDt istype DataType.UBYTE && toDt istype DataType.UBYTE -> InferredTypes.knownFor(DataType.arrayFor(BaseDataType.UBYTE))
            fromDt istype DataType.UWORD && toDt istype DataType.UWORD -> InferredTypes.knownFor(DataType.arrayFor(BaseDataType.UWORD))
            fromDt istype DataType.STR && toDt istype DataType.STR -> InferredTypes.knownFor(BaseDataType.STR)
            fromDt istype DataType.WORD || toDt istype DataType.WORD -> InferredTypes.knownFor(DataType.arrayFor(BaseDataType.WORD))
            fromDt istype DataType.BYTE || toDt istype DataType.BYTE -> InferredTypes.knownFor(DataType.arrayFor(BaseDataType.BYTE))
            else -> {
                val fdt = fromDt.getOrUndef()
                val tdt = toDt.getOrUndef()
                if(fdt.largerSizeThan(tdt))
                    InferredTypes.knownFor(fdt.elementToArray())
                else
                    InferredTypes.knownFor(tdt.elementToArray())
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

    fun targetStatement(program: Program?): Statement? =
        if(program!=null && nameInSource.singleOrNull() in program.builtinFunctions.names)
            BuiltinFunctionPlaceholder(nameInSource[0], position, parent)
        else
            definingScope.lookup(nameInSource)

    fun targetVarDecl(): VarDecl? = targetStatement(null) as? VarDecl
    fun targetSubroutine(): Subroutine? = targetStatement(null) as? Subroutine
    fun targetStructDecl(): StructDecl? = targetStatement(null) as? StructDecl

    fun targetNameAndType(program: Program): Pair<String, DataType> {
        val target = targetStatement(program) as? INamedStatement  ?: throw FatalAstException("can't find target for $nameInSource")
        val targetname: String = if(target.name in program.builtinFunctions.names)
            "<builtin>.${target.name}"
        else
            target.scopedName.joinToString(".")
        val type = inferType(program).getOrUndef()
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
        if(value==null || value.type==vardecl.datatype.base)
            return value
        val optimal = NumericLiteral.optimalNumeric(value.number, value.position)
        if(optimal.type==vardecl.datatype.base)
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
                if(targetStmt.datatype.isUndefined)
                    InferredTypes.unknown()
                else
                    InferredTypes.knownFor(targetStmt.datatype)
            }
            null -> {
                val fieldType = traverseDerefChain(null)
                if(fieldType.isUndefined)
                    InferredTypes.unknown()
                else
                    InferredTypes.knownFor(fieldType)
            }
            else -> InferredTypes.unknown()
        }
    }

    fun traverseDerefChain(startStruct: StructDecl?): DataType {
        var struct: StructDecl
        var fieldDt: DataType? = null
        if(startStruct!=null) {
            struct = startStruct
        }
        else {
            val vardecl = definingScope.lookup(nameInSource.take(1)) as? VarDecl
            if (vardecl?.datatype?.isPointer != true)
                return DataType.UNDEFINED
            struct = vardecl.datatype.subType as StructDecl
            fieldDt = vardecl.datatype
        }

        for((idx, field) in nameInSource.drop(1).withIndex()) {
            fieldDt = struct.getFieldType(field)
            if(fieldDt==null)
                return DataType.UNDEFINED
            if(idx==nameInSource.size-2) {
                // was last path element
                return fieldDt
            }
            struct = fieldDt.subType as? StructDecl ?: return DataType.UNDEFINED
        }
        return fieldDt ?: DataType.UNDEFINED
    }

    fun wasStringLiteral(): Boolean {
        val decl = targetVarDecl()
        if(decl == null || decl.origin!=VarDeclOrigin.STRINGLITERAL)
            return false

        val scope=decl.definingModule
        return scope.name==INTERNED_STRINGS_MODULENAME
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
            if(resultValue==null || resultDt istype DataType.forDt(resultValue.type))
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
            is StructDecl -> {
                return InferredTypes.knownFor(DataType.structInstance(stmt))
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
                    if(elementConst.type.isByte) {
                        val stringval = iterable as StringLiteral
                        val exists = program.encoding.encodeString(stringval.value, stringval.encoding).contains(elementConst.number.toInt().toUByte() )
                        return NumericLiteral.fromBoolean(exists, position)
                    }
                }
                is RangeExpression -> {
                    if(elementConst.type.isInteger) {
                        val intprogression = (iterable as RangeExpression).toConstantIntegerRange()
                        if (intprogression!=null) {
                            return NumericLiteral.fromBoolean(intprogression.contains(elementConst.number.toInt()), position)
                        }
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

    override fun inferType(program: Program) = InferredTypes.knownFor(BaseDataType.BOOL)

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
        return if(t1==t2) t1 else InferredTypes.unknown()
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
            throw FatalAstException("invalid replace")
        if(node===condition) condition=replacement
        else if(node===truevalue) truevalue=replacement
        else if(node===falsevalue) falsevalue=replacement
        else throw FatalAstException("invalid replace")
    }
}

class PtrIndexedDereference(val indexed: ArrayIndexedExpression, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        indexed.linkParents(this)
    }

    override val isSimple = false
    override fun copy() = PtrIndexedDereference(indexed.copy(), position)
    override fun constValue(program: Program): NumericLiteral? = null
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun inferType(program: Program): InferredTypes.InferredType {
        val parentExpr = parent as? BinaryExpression
        if(parentExpr?.operator==".") {
            TODO("cannot determine type of dereferenced indexed pointer(?) as part of a larger dereference expression")
        }
        val vardecl = indexed.arrayvar.targetVarDecl()
        if(vardecl!=null &&vardecl.datatype.isPointer) {
            if(vardecl.datatype.sub!=null)
                return InferredTypes.knownFor(vardecl.datatype.sub!!)
            TODO("cannot determine type of dereferenced indexed pointer(?) that is not a pointer to a basic type")
        }
        return InferredTypes.unknown()
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun referencesIdentifier(nameInSource: List<String>) = indexed.referencesIdentifier(nameInSource)
}

class PtrDereference(val identifier: IdentifierReference, val chain: List<String>, val field: String?, override val position: Position) : Expression() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier.linkParents(this)
    }

    override val isSimple = false
    override fun copy(): PtrDereference = PtrDereference(identifier.copy(), chain.toList(), field, position)
    override fun constValue(program: Program): NumericLiteral? = null
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun inferType(program: Program): InferredTypes.InferredType {
        val first = identifier.targetStatement(program)
        if(first==null)
            return InferredTypes.unknown()
        val vardecl = identifier.targetVarDecl()
        if(vardecl==null || vardecl.datatype.isUndefined || (!vardecl.datatype.isPointer && !vardecl.datatype.isStructInstance) )
            return InferredTypes.unknown()

        if(chain.isEmpty()) {
            return if(field==null) {
                require(vardecl.datatype.sub!=null) { "can only dereference a pointer to a simple datatype " }
                InferredTypes.knownFor(vardecl.datatype.sub!!)
            } else {
                // lookup struct field type
                val struct = vardecl.datatype.subType as StructDecl
                val fieldDt = struct.getFieldType(field)
                if (fieldDt == null)
                    InferredTypes.unknown()
                else
                    InferredTypes.knownFor(fieldDt)
            }
        } else {
            // lookup type of field at the end of a dereference chain
            var struct = vardecl.datatype.subType as StructDecl
            chain.forEach { fieldname ->
                val fieldDt = struct.getFieldType(fieldname)
                if(fieldDt==null)
                    return InferredTypes.unknown()
                if(!fieldDt.isPointer || fieldDt.subType==null)
                    return InferredTypes.unknown()
                struct = fieldDt.subType as StructDecl
            }
            if(field==null) {
                return InferredTypes.knownFor(DataType.pointerToType(struct))
            }
            val fieldDt = struct.getFieldType(field)
            return if(fieldDt==null)
                InferredTypes.unknown()
            else
                InferredTypes.knownFor(fieldDt)
        }
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun referencesIdentifier(nameInSource: List<String>) = identifier.referencesIdentifier(nameInSource)
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
        BinaryExpression(cond, "==", NumericLiteral(BaseDataType.UBYTE, 0.0, cond.position), cond.position)
}
