package prog8.ast.statements

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstVisitor


interface INamedStatement {
    val name: String

    val scopedName: List<String>
        get() {
            val scopedName = mutableListOf(name)
            var node: Node = this as Node
            while (node !is Block) {
                node = node.parent
                if(node is INameScope) {
                    scopedName.add(0, node.name)
                }
            }
            return scopedName
        }
}

sealed class Statement : Node {
    abstract override fun copy(): Statement
    abstract fun accept(visitor: IAstVisitor)
    abstract fun accept(visitor: AstWalker, parent: Node)

    fun nextSibling(): Statement? {
        val statements = (parent as? IStatementContainer)?.statements ?: return null
        val nextIdx = statements.indexOfFirst { it===this } + 1
        return if(nextIdx < statements.size)
            statements[nextIdx]
        else
            null
    }

    fun previousSibling(): Statement? {
        val statements = (parent as? IStatementContainer)?.statements ?: return null
        val previousIdx = statements.indexOfFirst { it===this } - 1
        return if(previousIdx >= 0)
            statements[previousIdx]
        else
            null
    }
}


class BuiltinFunctionPlaceholder(val name: String, override val position: Position, override var parent: Node) : Statement() {
    override fun linkParents(parent: Node) {}
    override fun accept(visitor: IAstVisitor) = throw FatalAstException("should not iterate over this node")
    override fun accept(visitor: AstWalker, parent: Node) = throw FatalAstException("should not iterate over this node")
    override val definingScope: INameScope
        get() = BuiltinFunctionScopePlaceholder
    override fun replaceChildNode(node: Node, replacement: Node) {
        replacement.parent = this
    }

    override fun copy() = throw NotImplementedError("no support for duplicating a BuiltinFunctionStatementPlaceholder")
}

data class RegisterOrStatusflag(val registerOrPair: RegisterOrPair?, val statusflag: Statusflag?)

class Block(override val name: String,
            val address: UInt?,
            override var statements: MutableList<Statement>,
            val isInLibrary: Boolean,
            override val position: Position) : Statement(), INameScope {
    override lateinit var parent: Node

    override fun copy() = throw NotImplementedError("no support for duplicating a Block")

    override fun linkParents(parent: Node) {
        this.parent = parent
        statements.forEach {it.linkParents(this)}
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Statement)
        val idx = statements.indexOfFirst { it ===node }
        statements[idx] = replacement
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString() = "Block(name=$name, address=$address, ${statements.size} statements)"

    fun options() = statements.filter { it is Directive && it.directive == "%option" }.flatMap { (it as Directive).args }.map {it.name!!}.toSet()
}

data class Directive(val directive: String, val args: List<DirectiveArg>, override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        args.forEach{it.linkParents(this)}
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun copy() = Directive(directive, args.map { it.copy() }, position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

data class DirectiveArg(val str: String?, val name: String?, val int: UInt?, override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun copy() = DirectiveArg(str, name, int, position)
}

data class Label(override val name: String, override val position: Position) : Statement(), INamedStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun copy() = Label(name, position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString()= "Label(name=$name, pos=$position)"
}

class Return(var value: Expression?, override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        value?.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Expression)
        value = replacement
        replacement.parent = this
    }

    override fun copy() = Return(value?.copy(), position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString() = "Return($value, pos=$position)"
}

class Break(override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent=parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun copy() = Break(position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}


enum class ZeropageWish {
    REQUIRE_ZEROPAGE,
    PREFER_ZEROPAGE,
    DONTCARE,
    NOT_IN_ZEROPAGE
}

enum class VarDeclOrigin {
    USERCODE,
    SUBROUTINEPARAM,
    STRINGLITERAL,
    ARRAYLITERAL
}


class VarDecl(val type: VarDeclType,
              val origin: VarDeclOrigin,
              private val declaredDatatype: DataType,
              var zeropage: ZeropageWish,
              var arraysize: ArrayIndex?,
              override val name: String,
              var value: Expression?,
              val isArray: Boolean,
              val sharedWithAsm: Boolean,
              val subroutineParameter: SubroutineParameter?,
              override val position: Position) : Statement(), INamedStatement {
    override lateinit var parent: Node
    var allowInitializeWithZero = true

    // prefix for literal values that are turned into a variable on the heap

    companion object {
        private var autoHeapValueSequenceNumber = 0

        fun fromParameter(param: SubroutineParameter): VarDecl {
            return VarDecl(VarDeclType.VAR, VarDeclOrigin.SUBROUTINEPARAM, param.type, ZeropageWish.DONTCARE, null, param.name, null,
                isArray = false,
                sharedWithAsm = false,
                subroutineParameter = param,
                position = param.position
            )
        }

        fun createAuto(array: ArrayLiteralValue): VarDecl {
            val autoVarName = "auto_heap_value_${++autoHeapValueSequenceNumber}"
            val arrayDt = array.type.getOrElse { throw FatalAstException("unknown dt") }
            val declaredType = ArrayToElementTypes.getValue(arrayDt)
            val arraysize = ArrayIndex.forArray(array)
            return VarDecl(VarDeclType.VAR, VarDeclOrigin.ARRAYLITERAL, declaredType, ZeropageWish.NOT_IN_ZEROPAGE, arraysize, autoVarName, array,
                    isArray = true, sharedWithAsm = false, subroutineParameter = null, position = array.position)
        }

        fun defaultZero(dt: DataType, position: Position) = when(dt) {
            DataType.UBYTE -> NumericLiteralValue(DataType.UBYTE, 0.0,  position)
            DataType.BYTE -> NumericLiteralValue(DataType.BYTE, 0.0,  position)
            DataType.UWORD, DataType.STR -> NumericLiteralValue(DataType.UWORD, 0.0, position)
            DataType.WORD -> NumericLiteralValue(DataType.WORD, 0.0, position)
            DataType.FLOAT -> NumericLiteralValue(DataType.FLOAT, 0.0, position)
            else -> throw FatalAstException("can only determine default zero value for a numeric type")
        }
    }

    val datatypeErrors = mutableListOf<SyntaxError>()       // don't crash at init time, report them in the AstChecker
    val datatype =
            if (!isArray) declaredDatatype
            else when (declaredDatatype) {
                DataType.UBYTE -> DataType.ARRAY_UB
                DataType.BYTE -> DataType.ARRAY_B
                DataType.UWORD -> DataType.ARRAY_UW
                DataType.WORD -> DataType.ARRAY_W
                DataType.FLOAT -> DataType.ARRAY_F
                DataType.STR -> DataType.ARRAY_UW       // use memory address of the string instead
                else -> {
                    datatypeErrors.add(SyntaxError("array can only contain bytes/words/floats/strings(ptrs)", position))
                    DataType.ARRAY_UB
                }
            }

    override fun linkParents(parent: Node) {
        this.parent = parent
        arraysize?.linkParents(this)
        value?.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Expression && (value==null || node===value))
        value = replacement
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString() =
        "VarDecl(name=$name, vartype=$type, datatype=$datatype, value=$value, pos=$position)"

    fun zeroElementValue(): NumericLiteralValue {
        if(allowInitializeWithZero)
            return defaultZero(declaredDatatype, position)
        else
            throw IllegalArgumentException("attempt to get zero value for vardecl that shouldn't get it")
    }

    override fun copy(): VarDecl {
        val copy = VarDecl(type, origin, declaredDatatype, zeropage, arraysize?.copy(), name, value?.copy(),
            isArray, sharedWithAsm, subroutineParameter, position)
        copy.allowInitializeWithZero = this.allowInitializeWithZero
        return copy
    }

    fun findInitializer(program: Program): Assignment? =
        (parent as IStatementContainer).statements
        .asSequence()
        .filterIsInstance<Assignment>()
        .singleOrNull { it.origin==AssignmentOrigin.VARINIT && it.target.identifier?.targetVarDecl(program) === this }
}

class ArrayIndex(var indexExpr: Expression,
                 override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        indexExpr.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Expression)
        if (node===indexExpr) indexExpr = replacement
        else throw FatalAstException("invalid replace")
    }

    companion object {
        fun forArray(v: ArrayLiteralValue): ArrayIndex {
            val indexnum = NumericLiteralValue.optimalNumeric(v.value.size, v.position)
            return ArrayIndex(indexnum, v.position)
        }
    }

    fun accept(visitor: IAstVisitor) = indexExpr.accept(visitor)
    fun accept(visitor: AstWalker)  = indexExpr.accept(visitor, this)
    override fun toString() = "ArrayIndex($indexExpr, pos=$position)"

    fun constIndex() = (indexExpr as? NumericLiteralValue)?.number?.toInt()

    infix fun isSameAs(other: ArrayIndex): Boolean = indexExpr isSameAs other.indexExpr
    override fun copy() = ArrayIndex(indexExpr.copy(), position)
}

enum class AssignmentOrigin {
    USERCODE,
    VARINIT,
    PARAMETERASSIGN,
    OPTIMIZER,
    BEFOREASMGEN,
    ASMGEN,
}

class Assignment(var target: AssignTarget, var value: Expression, var origin: AssignmentOrigin, override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        this.target.linkParents(this)
        value.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        when {
            node===target -> target = replacement as AssignTarget
            node===value -> value = replacement as Expression
            else -> throw FatalAstException("invalid replace")
        }
        replacement.parent = this
    }

    override fun copy()= Assignment(target.copy(), value.copy(), origin, position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString() = "Assignment(target: $target, value: $value, pos=$position)"

    /**
     * Is the assigment value an expression that references the assignment target itself?
     * The expression can be a BinaryExpression, PrefixExpression or TypecastExpression (possibly with one sub-cast).
     */
    val isAugmentable: Boolean
        get() {
            val binExpr = value as? BinaryExpression
            if(binExpr!=null) {
                if(binExpr.left isSameAs target)
                    return true  // A = A <operator> Something

                if(binExpr.operator in "+-") {
                    val leftBinExpr = binExpr.left as? BinaryExpression
                    val rightBinExpr = binExpr.right as? BinaryExpression
                    if(rightBinExpr==null && leftBinExpr!=null && leftBinExpr.operator in "+-") {
                        // A = (A +- x) +- y
                        if(leftBinExpr.left isSameAs target || leftBinExpr.right isSameAs target || binExpr.right isSameAs target)
                            return true
                    }
                    if(leftBinExpr==null && rightBinExpr!=null && rightBinExpr.operator in "+-") {
                        // A = y +- (A +- x)
                        if(rightBinExpr.left isSameAs target || rightBinExpr.right isSameAs target || binExpr.left isSameAs target)
                            return true
                    }
                }

                if(binExpr.operator in AssociativeOperators) {
                    if (binExpr.left !is BinaryExpression && binExpr.right isSameAs target)
                        return true  // A = v <associative-operator> A

                    val leftBinExpr = binExpr.left as? BinaryExpression
                    val rightBinExpr = binExpr.right as? BinaryExpression
                    if(leftBinExpr?.operator == binExpr.operator && rightBinExpr==null) {
                        // one of these?
                        // A = (A <associative-operator> x) <same-operator> y
                        // A = (x <associative-operator> A) <same-operator> y
                        // A = (x <associative-operator> y) <same-operator> A
                        return leftBinExpr.left isSameAs target || leftBinExpr.right isSameAs target || binExpr.right isSameAs target
                    }
                    if(rightBinExpr?.operator == binExpr.operator && leftBinExpr==null) {
                        // one of these?
                        // A = y <associative-operator> (A <same-operator> x)
                        // A = y <associative-operator> (x <same-operator> y)
                        // A = A <associative-operator> (x <same-operator> y)
                        return rightBinExpr.left isSameAs target || rightBinExpr.right isSameAs target || binExpr.left isSameAs target
                    }
                }
            }

            val prefixExpr = value as? PrefixExpression
            if(prefixExpr!=null)
                return prefixExpr.expression isSameAs target

            val castExpr = value as? TypecastExpression
            if(castExpr!=null) {
                val subCast = castExpr.expression as? TypecastExpression
                return if(subCast!=null) subCast.expression isSameAs target else castExpr.expression isSameAs target
            }

            return false
        }

    fun initializerFor(program: Program) =
            if(origin==AssignmentOrigin.VARINIT)
                target.identifier!!.targetVarDecl(program)
            else
                null
}

data class AssignTarget(var identifier: IdentifierReference?,
                        var arrayindexed: ArrayIndexedExpression?,
                        val memoryAddress: DirectMemoryWrite?,
                        override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
        arrayindexed?.linkParents(this)
        memoryAddress?.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        when {
            node === identifier -> identifier = replacement as IdentifierReference
            node === arrayindexed -> arrayindexed = replacement as ArrayIndexedExpression
            else -> throw FatalAstException("invalid replace")
        }
        replacement.parent = this
    }

    fun accept(visitor: IAstVisitor) = visitor.visit(this)
    fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    fun inferType(program: Program): InferredTypes.InferredType {
        if (identifier != null) {
            val symbol = definingScope.lookup(identifier!!.nameInSource) ?: return InferredTypes.unknown()
            if (symbol is VarDecl) return InferredTypes.knownFor(symbol.datatype)
        }

        if (arrayindexed != null) {
            return arrayindexed!!.inferType(program)
        }

        if (memoryAddress != null)
            return InferredTypes.knownFor(DataType.UBYTE)

        return InferredTypes.unknown()
    }

    fun toExpression(): Expression {
        // return a copy of the assignment target but as a source expression.
        return when {
            identifier != null -> identifier!!.copy()
            arrayindexed != null -> arrayindexed!!.copy()
            memoryAddress != null -> DirectMemoryRead(memoryAddress.addressExpression.copy(), memoryAddress.position)
            else -> throw FatalAstException("invalid assignmenttarget $this")
        }
    }

    infix fun isSameAs(value: Expression): Boolean {
        return when {
            memoryAddress != null -> {
                // if the target is a memory write, and the value is a memory read, they're the same if the address matches
                if (value is DirectMemoryRead)
                    this.memoryAddress.addressExpression isSameAs value.addressExpression
                else
                    false
            }
            identifier != null -> value is IdentifierReference && value.nameInSource == identifier!!.nameInSource
            arrayindexed != null -> {
                if(value is ArrayIndexedExpression && value.arrayvar.nameInSource == arrayindexed!!.arrayvar.nameInSource)
                    arrayindexed!!.indexer isSameAs value.indexer
                else
                    false
            }
            else -> false
        }
    }

    fun isSameAs(other: AssignTarget, program: Program): Boolean {
        if (this === other)
            return true
        if (this.identifier != null && other.identifier != null)
            return this.identifier!!.nameInSource == other.identifier!!.nameInSource
        if (this.memoryAddress != null && other.memoryAddress != null) {
            val addr1 = this.memoryAddress.addressExpression.constValue(program)
            val addr2 = other.memoryAddress.addressExpression.constValue(program)
            return addr1 != null && addr2 != null && addr1 == addr2
        }
        if (this.arrayindexed != null && other.arrayindexed != null) {
            if (this.arrayindexed!!.arrayvar.nameInSource == other.arrayindexed!!.arrayvar.nameInSource) {
                val x1 = this.arrayindexed!!.indexer.constIndex()
                val x2 = other.arrayindexed!!.indexer.constIndex()
                return x1 != null && x2 != null && x1 == x2
            }
        }
        return false
    }

    override fun copy() = AssignTarget(identifier?.copy(), arrayindexed?.copy(), memoryAddress?.copy(), position)
}

class PostIncrDecr(var target: AssignTarget, val operator: String, override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is AssignTarget && node===target)
        target = replacement
        replacement.parent = this
    }

    override fun copy() = PostIncrDecr(target.copy(), operator, position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString() = "PostIncrDecr(op: $operator, target: $target, pos=$position)"
}

class Jump(var address: UInt?,
           val identifier: IdentifierReference?,
           val generatedLabel: String?,             // can be used in code generation scenarios
           override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        if(node===identifier && replacement is NumericLiteralValue) {
            address = replacement.number.toUInt()
        }
        else
            throw FatalAstException("can't replace $node")
    }
    override fun copy() = Jump(address, identifier?.copy(), generatedLabel, position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    override fun toString() =
        "Jump(addr: $address, identifier: $identifier, label: $generatedLabel;  pos=$position)"
}

// a GoSub is ONLY created internally for calling subroutines
class GoSub(val address: UInt?,
            val identifier: IdentifierReference?,
            val generatedLabel: String?,             // can be used in code generation scenarios
            override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun copy() = GoSub(address, identifier?.copy(), generatedLabel, position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    override fun toString() =
        "GoSub(addr: $address, identifier: $identifier, label: $generatedLabel;  pos=$position)"
}

class FunctionCallStatement(override var target: IdentifierReference,
                            override var args: MutableList<Expression>,
                            val void: Boolean,
                            override val position: Position) : Statement(), IFunctionCall {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        args.forEach { it.linkParents(this) }
    }

    override fun copy() = throw NotImplementedError("no support for duplicating a FunctionCallStatement")

    override fun replaceChildNode(node: Node, replacement: Node) {
        if(node===target)
            target = replacement as IdentifierReference
        else {
            val idx = args.indexOfFirst { it===node }
            args[idx] = replacement as Expression
        }
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString() = "FunctionCallStatement(target=$target, pos=$position)"
}

class InlineAssembly(val assembly: String, override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun copy() = throw NotImplementedError("no support for duplicating a InlineAssembly")

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    val names: Set<String> by lazy {
        // A cache of all the words (identifiers) present in this block of assembly code
        // this is used when checking if prog8 names are referenced from assembly code
        // TODO: smarter pattern; don't include words in comments
        val wordPattern = Regex("""\b([_a-zA-Z][_a-zA-Z0-9]+?)\b""", RegexOption.MULTILINE)
        wordPattern.findAll(assembly).map { it.value }.toSet()
    }
}

class AnonymousScope(override var statements: MutableList<Statement>,
                     override val position: Position) : IStatementContainer, Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        statements.forEach { it.linkParents(this) }
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Statement)
        val idx = statements.indexOfFirst { it===node }
        statements[idx] = replacement
        replacement.parent = this
    }

    override fun copy() = AnonymousScope(statements.map { it.copy() }.toMutableList(), position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

class AsmGenInfo {
    // This class contains various attributes that influence the assembly code generator.
    // Conceptually it should be part of any INameScope.
    // But because the resulting code only creates "real" scopes on a subroutine level,
    // it's more consistent to only define these attributes on a Subroutine node.
    var usedRegsaveA = false
    var usedRegsaveX = false
    var usedRegsaveY = false
    var usedFloatEvalResultVar1 = false
    var usedFloatEvalResultVar2 = false

    val extraVars = mutableListOf<Triple<DataType, String, UInt?>>()
}

// the subroutine class covers both the normal user-defined subroutines,
// and also the predefined/ROM/register-based subroutines.
// (multiple return types can only occur for the latter type)
class Subroutine(override val name: String,
                 val parameters: MutableList<SubroutineParameter>,
                 val returntypes: List<DataType>,
                 val asmParameterRegisters: List<RegisterOrStatusflag>,
                 val asmReturnvaluesRegisters: List<RegisterOrStatusflag>,
                 val asmClobbers: Set<CpuRegister>,
                 val asmAddress: UInt?,
                 val isAsmSubroutine: Boolean,
                 val inline: Boolean,
                 override var statements: MutableList<Statement>,
                 override val position: Position) : Statement(), INameScope {

    constructor(name: String, parameters: MutableList<SubroutineParameter>, returntypes: List<DataType>, statements: MutableList<Statement>, inline: Boolean, position: Position)
            : this(name, parameters, returntypes, emptyList(), determineReturnRegisters(returntypes), emptySet(), null, false, inline, statements, position)

    companion object {
        private fun determineReturnRegisters(returntypes: List<DataType>): List<RegisterOrStatusflag> {
            // for non-asm subroutines, determine the return registers based on the type of the return value
            return when(returntypes.singleOrNull()) {
                in ByteDatatypes -> listOf(RegisterOrStatusflag(RegisterOrPair.A, null))
                in WordDatatypes -> listOf(RegisterOrStatusflag(RegisterOrPair.AY, null))
                DataType.FLOAT -> listOf(RegisterOrStatusflag(RegisterOrPair.FAC1, null))
                null -> emptyList()
                else -> listOf(RegisterOrStatusflag(RegisterOrPair.AY, null))
            }
        }
    }

    override lateinit var parent: Node
    val asmGenInfo = AsmGenInfo()

    override fun copy() = throw NotImplementedError("no support for duplicating a Subroutine")

    override fun linkParents(parent: Node) {
        this.parent = parent
        parameters.forEach { it.linkParents(this) }
        statements.forEach { it.linkParents(this) }
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        when(replacement) {
            is SubroutineParameter -> {
                val idx = parameters.indexOf(node)
                parameters[idx] = replacement
                replacement.parent = this
            }
            is Statement -> {
                val idx = statements.indexOfFirst { it===node }
                statements[idx] = replacement
                replacement.parent = this
            }
            else -> throw FatalAstException("can't replace")
        }
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString() =
        "Subroutine(name=$name, parameters=$parameters, returntypes=$returntypes, ${statements.size} statements, address=$asmAddress)"

    fun regXasResult() = asmReturnvaluesRegisters.any { it.registerOrPair in arrayOf(RegisterOrPair.X, RegisterOrPair.AX, RegisterOrPair.XY) }
    fun regXasParam() = asmParameterRegisters.any { it.registerOrPair in arrayOf(RegisterOrPair.X, RegisterOrPair.AX, RegisterOrPair.XY) }
    fun shouldSaveX() = CpuRegister.X in asmClobbers || regXasResult() || regXasParam()

    class KeepAresult(val saveOnEntry: Boolean, val saveOnReturn: Boolean)

    fun shouldKeepA(): KeepAresult {
        // determine if A's value should be kept when preparing for calling the subroutine, and when returning from it
        if(!isAsmSubroutine)
            return KeepAresult(saveOnEntry = false, saveOnReturn = false)

        // it seems that we never have to save A when calling? will be loaded correctly after setup.
        // but on return it depends on wether the routine returns something in A.
        val saveAonReturn = asmReturnvaluesRegisters.any { it.registerOrPair==RegisterOrPair.A || it.registerOrPair==RegisterOrPair.AY || it.registerOrPair==RegisterOrPair.AX }
        return KeepAresult(false, saveAonReturn)
    }

    fun amountOfRtsInAsm(): Int = statements
            .asSequence()
            .filter { it is InlineAssembly }
            .map { (it as InlineAssembly).assembly }
            .count { " rti" in it || "\trti" in it || " rts" in it || "\trts" in it || " jmp" in it || "\tjmp" in it || " bra" in it || "\tbra" in it }


    // code to provide the ability to reference asmsub parameters via qualified name:
    private val asmParamsDecls = mutableMapOf<String, VarDecl>()

    fun searchAsmParameter(name: String): VarDecl? {
        require(isAsmSubroutine)

        val existingDecl = asmParamsDecls[name]
        if(existingDecl!=null)
            return existingDecl

        val param = parameters.firstOrNull {it.name==name} ?: return null
        val decl = VarDecl.fromParameter(param)
        decl.linkParents(this)
        asmParamsDecls[name] = decl
        return decl
    }
}

open class SubroutineParameter(val name: String,
                               val type: DataType,
                               final override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        throw FatalAstException("can't replace anything in a subroutineparameter node")
    }

    override fun copy() = SubroutineParameter(name, type, position)
    override fun toString() = "Param($type:$name)"
}

class IfElse(var condition: Expression,
             var truepart: AnonymousScope,
             var elsepart: AnonymousScope,
             override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        condition.linkParents(this)
        truepart.linkParents(this)
        elsepart.linkParents(this)
    }

    override fun copy() = throw NotImplementedError("no support for duplicating a IfStatement")

    override fun replaceChildNode(node: Node, replacement: Node) {
        when {
            node===condition -> condition = replacement as Expression
            node===truepart -> truepart = replacement as AnonymousScope
            node===elsepart -> elsepart = replacement as AnonymousScope
            else -> throw FatalAstException("invalid replace")
        }
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

}

class ConditionalBranch(var condition: BranchCondition,
                        var truepart: AnonymousScope,
                        var elsepart: AnonymousScope,
                        override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        truepart.linkParents(this)
        elsepart.linkParents(this)
    }

    override fun copy() = throw NotImplementedError("no support for duplicating a BranchStatement")

    override fun replaceChildNode(node: Node, replacement: Node) {
        when {
            node===truepart -> truepart = replacement as AnonymousScope
            node===elsepart -> elsepart = replacement as AnonymousScope
            else -> throw FatalAstException("invalid replace")
        }
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

}

class ForLoop(var loopVar: IdentifierReference,
              var iterable: Expression,
              var body: AnonymousScope,
              override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent=parent
        loopVar.linkParents(this)
        iterable.linkParents(this)
        body.linkParents(this)
    }

    override fun copy() = throw NotImplementedError("no support for duplicating a ForLoop")

    override fun replaceChildNode(node: Node, replacement: Node) {
        when {
            node===loopVar -> loopVar = replacement as IdentifierReference
            node===iterable -> iterable = replacement as Expression
            node===body -> body = replacement as AnonymousScope
            else -> throw FatalAstException("invalid replace")
        }
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString() = "ForLoop(loopVar: $loopVar, iterable: $iterable, pos=$position)"

    fun loopVarDt(program: Program) = loopVar.inferType(program)
}

class WhileLoop(var condition: Expression,
                var body: AnonymousScope,
                override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        condition.linkParents(this)
        body.linkParents(this)
    }

    override fun copy() = throw NotImplementedError("no support for duplicating a WhileLoop")

    override fun replaceChildNode(node: Node, replacement: Node) {
        when {
            node===condition -> condition = replacement as Expression
            node===body -> body = replacement as AnonymousScope
            else -> throw FatalAstException("invalid replace")
        }
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

class RepeatLoop(var iterations: Expression?, var body: AnonymousScope, override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        iterations?.linkParents(this)
        body.linkParents(this)
    }

    override fun copy() = throw NotImplementedError("no support for duplicating a RepeatLoop")

    override fun replaceChildNode(node: Node, replacement: Node) {
        when {
            node===iterations -> iterations = replacement as Expression
            node===body -> body = replacement as AnonymousScope
            else -> throw FatalAstException("invalid replace")
        }
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

class UntilLoop(var body: AnonymousScope,
                var condition: Expression,
                override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        condition.linkParents(this)
        body.linkParents(this)
    }
    override fun copy() = throw NotImplementedError("no support for duplicating a UntilLoop")

    override fun replaceChildNode(node: Node, replacement: Node) {
        when {
            node===condition -> condition = replacement as Expression
            node===body -> body = replacement as AnonymousScope
            else -> throw FatalAstException("invalid replace")
        }
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

class When(var condition: Expression,
           var choices: MutableList<WhenChoice>,
           override val position: Position): Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        condition.linkParents(this)
        choices.forEach { it.linkParents(this) }
    }

    override fun copy() = throw NotImplementedError("no support for duplicating a WhenStatement")

    override fun replaceChildNode(node: Node, replacement: Node) {
        if(node===condition)
            condition = replacement as Expression
        else {
            val idx = choices.withIndex().find { it.value===node }!!.index
            choices[idx] = replacement as WhenChoice
        }
        replacement.parent = this
    }

    fun choiceValues(program: Program): List<Pair<List<Int>?, WhenChoice>> {
        // only gives sensible results when the choices are all valid (constant integers)
        val result = mutableListOf<Pair<List<Int>?, WhenChoice>>()
        for(choice in choices) {
            if(choice.values==null)
                result.add(null to choice)
            else {
                val values = choice.values!!.map {
                    val cv = it.constValue(program)
                    cv?.number?.toInt() ?: it.hashCode()       // the hashcode is a nonsensical number, but it avoids weird AST validation errors later
                }
                result.add(values to choice)
            }
        }
        return result
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

class WhenChoice(var values: MutableList<Expression>?,           // if null,  this is the 'else' part
                 var statements: AnonymousScope,
                 override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        values?.forEach { it.linkParents(this) }
        statements.linkParents(this)
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        val choiceValues = values
        if(replacement is AnonymousScope && node===statements) {
            statements = replacement
            replacement.parent = this
        } else if(choiceValues!=null && node in choiceValues) {
            val idx = choiceValues.indexOf(node)
            choiceValues[idx] = replacement as Expression
            replacement.parent = this
        } else {
            throw FatalAstException("invalid replacement")
        }
    }

    override fun copy() = WhenChoice(values?.map{ it.copy() }?.toMutableList(), statements.copy(), position)
    override fun toString() = "Choice($values at $position)"

    fun accept(visitor: IAstVisitor) = visitor.visit(this)
    fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

class DirectMemoryWrite(var addressExpression: Expression, override val position: Position) : Node {
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

    override fun toString() = "DirectMemoryWrite($addressExpression)"
    fun accept(visitor: IAstVisitor) = visitor.visit(this)
    fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun copy() = DirectMemoryWrite(addressExpression.copy(), position)
}


class Pipe(val expressions: MutableList<Expression>, override val position: Position): Statement() {
    override lateinit var parent: Node

    constructor(source: Expression, target: Expression, position: Position) : this(mutableListOf(), position) {
        if(source is PipeExpression)
            expressions.addAll(source.expressions)
        else
            expressions.add(source)

        if(target is PipeExpression)
            expressions.addAll(target.expressions)
        else
            expressions.add(target)
    }

    override fun linkParents(parent: Node) {
        this.parent = parent
        expressions.forEach { it.linkParents(this) }
    }

    override fun copy() = Pipe(expressions.map { it.copy() }.toMutableList(), position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node)  = visitor.visit(this, parent)

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(node is Expression)
        require(replacement is Expression)
        val idx = expressions.indexOf(node)
        expressions[idx] = replacement
    }
}