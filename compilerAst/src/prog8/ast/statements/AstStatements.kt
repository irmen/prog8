package prog8.ast.statements

import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstVisitor
import prog8.code.core.*
import java.util.*


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


// this class is only created as temporary result from looking up the target for a builtin function call.
// this node is never actually part of the Ast.
class BuiltinFunctionPlaceholder(override val name: String, override val position: Position, override var parent: Node) : Statement(), INamedStatement {
    override fun linkParents(parent: Node) {}
    override fun accept(visitor: IAstVisitor) = throw FatalAstException("should not iterate over this node")
    override fun accept(visitor: AstWalker, parent: Node) = throw FatalAstException("should not iterate over this node")
    override val definingScope: INameScope
        get() = BuiltinFunctionScopePlaceholder
    override fun replaceChildNode(node: Node, replacement: Node) {
        replacement.parent = this
    }

    override fun copy() = throw NotImplementedError("no support for duplicating a BuiltinFunctionStatementPlaceholder")
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = nameInSource.size==1 && name==nameInSource[0]
}

// note: a Block is not strictly a Statement (but a Module Element rather)
//       however by making it a statement we can reuse the name lookup logic for them (a module *is* name scope that has to do lookups)
class Block(override val name: String,
            val address: UInt?,
            override val statements: MutableList<Statement>,
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
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = statements.any { it.referencesIdentifier(nameInSource) }

    fun options() = statements.filter { it is Directive && it.directive == "%option" }.flatMap { (it as Directive).args }.map {it.string!!}.toSet()
}

// note: a Directive is not strictly always Statement (in module scope, it's a Module Element rather)
//       however by making it a statement we can reuse the name lookup logic for them (a module *is* name scope that has to do lookups)
data class Directive(val directive: String, val args: List<DirectiveArg>, override val position: Position) : Statement() {
    override lateinit var parent: Node

//    init {
//        require(directive in arrayOf(
//            "%address",
//            "%memtop",
//            "%asmbinary",
//            "%asminclude",
//            "%breakpoint",
//            "%encoding",
//            "%import",
//            "%jmptable",
//            "%launcher",
//            "%option",
//            "%output",
//            "%zeropage",
//            "%zpallowed",
//            "%zpreserved",
//        )) { "invalid directive" }
//    }

    override fun linkParents(parent: Node) {
        this.parent = parent
        args.forEach{it.linkParents(this)}
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun copy() = Directive(directive, args.map { it.copy() }, position)
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = false
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

data class DirectiveArg(val string: String?, val int: UInt?, override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun copy() = DirectiveArg(string, int, position)
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = false
}

data class Alias(val alias: String, val target: IdentifierReference, override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.parent = this
    }

    override fun copy(): Statement = Alias(alias, target.copy(), position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = (nameInSource.size==1 && nameInSource[0]==alias) || target.referencesIdentifier(nameInSource)
}


data class Label(override val name: String, override val position: Position) : Statement(), INamedStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun copy() = Label(name, position)
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = nameInSource.size==1 && nameInSource[0]==name
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString()= "Label(name=$name, pos=$position)"
}

class Return(val values: Array<Expression>, override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        values.forEach { it.linkParents(this) }
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        val index = values.indexOf(node)
        if(replacement is Expression && index>=0) {
            values[index] = replacement
        } else throw FatalAstException("invalid replace")
    }

    override fun copy() = Return(values.map { it.copy() }.toTypedArray(), position)
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = values.any{ it.referencesIdentifier(nameInSource) }
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString() = "Return($values, pos=$position)"
}

class Break(override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent=parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = false
    override fun copy() = Break(position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

class Continue(override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent=parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = false
    override fun copy() = Break(position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}


enum class VarDeclOrigin {
    USERCODE,
    SUBROUTINEPARAM,
    STRINGLITERAL,
    ARRAYLITERAL
}

enum class VarDeclType {
    VAR,
    CONST,
    MEMORY
}

class VarDecl(val type: VarDeclType,
              val origin: VarDeclOrigin,
              val datatype: DataType,
              val zeropage: ZeropageWish,
              val splitwordarray: SplitWish,
              var arraysize: ArrayIndex?,
              override val name: String,
              val names: List<String>,
              var value: Expression?,
              val sharedWithAsm: Boolean,
              val alignment: UInt,
              val dirty: Boolean,
              override val position: Position) : Statement(), INamedStatement {
    override lateinit var parent: Node
    var allowInitializeWithZero = true

    companion object {
        private var autoHeapValueSequenceNumber = 0

        fun fromParameter(param: SubroutineParameter): VarDecl {
            val decltype: VarDeclType
            val value: Expression?
            if(param.registerOrPair==null) {
                // regular parameter variable
                decltype = VarDeclType.VAR
                value = null
            } else {
                // parameter variable memory mapped to a R0-R15 virtual register
                val regname = param.registerOrPair.asScopedNameVirtualReg(param.type)
                decltype = VarDeclType.MEMORY
                value = AddressOf(IdentifierReference(regname, param.position), null, false, param.position)
            }
            val dt = if(param.type.isArray) DataType.UWORD else param.type
            return VarDecl(decltype, VarDeclOrigin.SUBROUTINEPARAM, dt, param.zp, SplitWish.DONTCARE, null, param.name, emptyList(), value,
                sharedWithAsm = false,
                alignment = 0u,
                dirty = false,
                position = param.position
            )
        }

        fun createAuto(array: ArrayLiteral): VarDecl {
            val autoVarName = "auto_heap_value_${++autoHeapValueSequenceNumber}"
            var arrayDt = array.type.getOrElse { throw FatalAstException("unknown dt") }
            if(arrayDt.isSplitWordArray) {
                // autovars for array literals are NOT stored as a split word array!
                when(arrayDt.sub) {
                    BaseDataType.WORD -> arrayDt = DataType.arrayFor(BaseDataType.WORD, false)
                    BaseDataType.UWORD -> arrayDt = DataType.arrayFor(BaseDataType.UWORD, false)
                    else -> { }
                }
            }
            val arraysize = ArrayIndex.forArray(array)
            return VarDecl(VarDeclType.VAR, VarDeclOrigin.ARRAYLITERAL, arrayDt, ZeropageWish.NOT_IN_ZEROPAGE,
                SplitWish.NOSPLIT, arraysize, autoVarName, emptyList(), array,
                    sharedWithAsm = false, alignment = 0u, dirty = false, position = array.position)
        }
    }

    val isArray: Boolean
        get() = datatype.isArray

    override fun linkParents(parent: Node) {
        this.parent = parent
        arraysize?.linkParents(this)
        value?.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Expression && (value==null || node===value))
        value = replacement     // note: any datatype differences between the value and the decl itself, will be fixed by a separate ast walker step
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString() =
        "VarDecl(name=$name, vartype=$type, datatype=$datatype, value=$value, pos=$position)"

    fun zeroElementValue(): NumericLiteral {
        if(allowInitializeWithZero) {
            return if(datatype.isArray) defaultZero(datatype.sub!!, position)
                else defaultZero(datatype.base, position)
        }
        else
            throw IllegalArgumentException("attempt to get zero value for vardecl that shouldn't get it")
    }

    override fun copy(): VarDecl = copy(datatype)

    fun copy(newDatatype: DataType): VarDecl {
        if(names.size>1)
            throw FatalAstException("should not copy a vardecl that still has multiple names")
        val copy = VarDecl(type, origin, newDatatype, zeropage, splitwordarray, arraysize?.copy(), name, names, value?.copy(),
            sharedWithAsm, alignment, dirty, position)
        copy.allowInitializeWithZero = this.allowInitializeWithZero
        return copy
    }

    override fun referencesIdentifier(nameInSource: List<String>): Boolean =
        value?.referencesIdentifier(nameInSource)==true ||
                this.arraysize?.referencesIdentifier(nameInSource)==true

    fun desugarMultiDecl(): List<VarDecl> {
        require(alignment==0u)
        if(value==null || value?.isSimple==true) {
            // just copy the initialization value to a separate vardecl for each component
            return names.map {
                val copy = VarDecl(type, origin, datatype, zeropage, splitwordarray, arraysize?.copy(), it, emptyList(), value?.copy(),
                    sharedWithAsm, alignment, dirty, position)
                copy.allowInitializeWithZero = this.allowInitializeWithZero
                copy
            }
        } else {
            // evaluate the value once in the vardecl for the first component, and set the other components to the first
            val first = VarDecl(type, origin, datatype, zeropage, splitwordarray, arraysize?.copy(), names[0], emptyList(), value?.copy(),
                sharedWithAsm, alignment, dirty, position)
            first.allowInitializeWithZero = this.allowInitializeWithZero
            val firstVar = firstVarAsValue(first)
            return listOf(first) + names.drop(1 ).map {
                val copy = VarDecl(type, origin, datatype, zeropage, splitwordarray, arraysize?.copy(), it, emptyList(), firstVar.copy(),
                    sharedWithAsm, alignment, dirty, position)
                copy.allowInitializeWithZero = this.allowInitializeWithZero
                copy
            }
        }
    }

    private fun firstVarAsValue(first: VarDecl): Expression {
        if(first.isArray || first.type!=VarDeclType.VAR)
            throw FatalAstException("invalid multi decl type $first")
        return IdentifierReference(listOf(first.name), first.position)
    }
}

class StructDecl(override val name: String, val members: List<Pair<DataType, String>>, override val position: Position) : Statement(), INamedStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun referencesIdentifier(nameInSource: List<String>) = false
    override fun copy() = StructDecl(name, members.toList(), position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    fun memsize(sizer: IMemSizer): Int = members.sumOf { sizer.memorySize(it.first, 1) }
    fun getField(name: String): Pair<DataType, String>? = members.firstOrNull { it.second==name }
}

class StructFieldRef(val pointer: IdentifierReference, val struct: StructDecl, val type: DataType, override val name: String, override val position: Position): Statement(), INamedStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        // pointer and struct are not our property!
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")

    override fun referencesIdentifier(nameInSource: List<String>) = pointer.referencesIdentifier(nameInSource) || struct.referencesIdentifier(nameInSource)

    override fun copy(): StructFieldRef = StructFieldRef(pointer.copy(), struct.copy(), type, name, position)

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

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
        fun forArray(v: ArrayLiteral): ArrayIndex {
            val indexnum = NumericLiteral.optimalNumeric(v.value.size, v.position)
            return ArrayIndex(indexnum, v.position)
        }
    }

    fun accept(visitor: IAstVisitor) = indexExpr.accept(visitor)
    fun accept(visitor: AstWalker)  = indexExpr.accept(visitor, this)
    override fun toString() = "ArrayIndex($indexExpr, pos=$position)"

    fun constIndex() = (indexExpr as? NumericLiteral)?.number?.toInt()

    infix fun isSameAs(other: ArrayIndex): Boolean = indexExpr isSameAs other.indexExpr
    override fun copy() = ArrayIndex(indexExpr.copy(), position)
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = indexExpr.referencesIdentifier(nameInSource)
}

enum class AssignmentOrigin {
    USERCODE,
    VARINIT,
    OPTIMIZER
}


class ChainedAssignment(var target: AssignTarget, var nested: Statement, override val position: Position): Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        nested.linkParents(this)
    }

    override fun copy() = throw FatalAstException("you're not supposed to copy a ChainedAssignment")

    override fun replaceChildNode(node: Node, replacement: Node) {
        when {
            node===target -> target = replacement as AssignTarget
            node===nested -> nested = replacement as Statement
            else -> throw FatalAstException("invalid replace")
        }
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString() = "ChainedAssignment(target: $target, nested: $nested, pos=$position)"
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = target.referencesIdentifier(nameInSource) || nested.referencesIdentifier(nameInSource)
}

class Assignment(var target: AssignTarget, var value: Expression, var origin: AssignmentOrigin, override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
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
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = target.referencesIdentifier(nameInSource) || value.referencesIdentifier(nameInSource)

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
}

data class AssignTarget(var identifier: IdentifierReference?,
                        var arrayindexed: ArrayIndexedExpression?,
                        val memoryAddress: DirectMemoryWrite?,
                        val multi: List<AssignTarget>?,
                        val void: Boolean,
                        override val position: Position,    // TODO move to end of param list
                        val pointerDereference: IdentifierReference? = null) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
        arrayindexed?.linkParents(this)
        memoryAddress?.linkParents(this)
        pointerDereference?.linkParents(this)
        multi?.forEach { it.linkParents(this) }
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        when {
            node === identifier -> {
                require(replacement is IdentifierReference)
                identifier = replacement
                arrayindexed = null
            }
            node === arrayindexed -> {
                arrayindexed = replacement as ArrayIndexedExpression
                identifier = null
            }
            node === multi -> throw FatalAstException("can't replace multi assign targets")
            else -> throw FatalAstException("invalid replace")
        }
        replacement.parent = this
    }

    fun accept(visitor: IAstVisitor) = visitor.visit(this)
    fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun copy() = AssignTarget(identifier?.copy(), arrayindexed?.copy(), memoryAddress?.copy(), multi?.toList(), void, position, pointerDereference?.copy())
    override fun referencesIdentifier(nameInSource: List<String>): Boolean =
        identifier?.referencesIdentifier(nameInSource)==true ||
                arrayindexed?.referencesIdentifier(nameInSource)==true ||
                memoryAddress?.referencesIdentifier(nameInSource)==true ||
                multi?.any { it.referencesIdentifier(nameInSource)}==true

    fun inferType(program: Program): InferredTypes.InferredType {
        if (identifier != null) {
            val symbol = definingScope.lookup(identifier!!.nameInSource) ?: return InferredTypes.unknown()
            if (symbol is VarDecl) return InferredTypes.knownFor(symbol.datatype)
        }
        return when {
            arrayindexed != null -> arrayindexed!!.inferType(program)
            memoryAddress != null -> InferredTypes.knownFor(BaseDataType.UBYTE)
            pointerDereference != null -> pointerDereference.inferType(program)
            else -> InferredTypes.unknown()   // a multi-target has no 1 particular type
        }
    }

    fun toExpression(): Expression {
        // return a copy of the assignment target but as a source expression.
        return when {
            identifier != null -> identifier!!.copy()
            arrayindexed != null -> arrayindexed!!.copy()
            memoryAddress != null -> DirectMemoryRead(memoryAddress.addressExpression.copy(), memoryAddress.position)
            multi != null -> throw FatalAstException("cannot turn a multi-assign into a single source expression")
            pointerDereference != null -> PtrDereference(pointerDereference.copy(), position)
            else -> throw FatalAstException("invalid assignment target")
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
            multi != null -> false
            pointerDereference!=null -> value is IdentifierReference && value.nameInSource == pointerDereference.nameInSource
            else -> false
        }
    }

    fun isSameAs(other: AssignTarget, program: Program): Boolean {
        when {
            this === other -> return true
            void && other.void -> return true
            this.identifier != null && other.identifier != null -> return this.identifier!!.nameInSource == other.identifier!!.nameInSource
            this.memoryAddress != null && other.memoryAddress != null -> {
                val addr1 = this.memoryAddress.addressExpression.constValue(program)
                val addr2 = other.memoryAddress.addressExpression.constValue(program)
                return addr1 != null && addr2 != null && addr1 == addr2
            }
            this.arrayindexed != null && other.arrayindexed != null -> {
                if (this.arrayindexed!!.arrayvar.nameInSource == other.arrayindexed!!.arrayvar.nameInSource) {
                    val x1 = this.arrayindexed!!.indexer.constIndex()
                    val x2 = other.arrayindexed!!.indexer.constIndex()
                    return x1 != null && x2 != null && x1 == x2
                }
                else
                    return false
            }
            this.pointerDereference!=null && other.pointerDereference!=null -> {
                return this.pointerDereference.nameInSource == other.pointerDereference.nameInSource
            }
            this.multi != null && other.multi != null -> return this.multi == other.multi
            else -> return false
        }
    }

    fun isIOAddress(target: ICompilationTarget): Boolean {
        val memAddr = memoryAddress
        val arrayIdx = arrayindexed
        val ident = identifier
        when {
            memAddr != null -> {
                val addr = memAddr.addressExpression.constValue(definingModule.program)
                if(addr!=null)
                    return target.isIOAddress(addr.number.toUInt())
                return when (memAddr.addressExpression) {
                    is IdentifierReference -> {
                        val decl = (memAddr.addressExpression as IdentifierReference).targetVarDecl()
                        val result = if ((decl?.type == VarDeclType.MEMORY || decl?.type == VarDeclType.CONST) && decl.value is NumericLiteral)
                            target.isIOAddress((decl.value as NumericLiteral).number.toUInt())
                        else
                            false
                        result
                    }
                    else -> false
                }
            }
            arrayIdx != null -> {
                val targetStmt = arrayIdx.arrayvar.targetVarDecl()
                return if (targetStmt?.type == VarDeclType.MEMORY) {
                    val addr = targetStmt.value as? NumericLiteral
                    if (addr != null)
                        target.isIOAddress(addr.number.toUInt())
                    else
                        false
                } else false
            }
            ident != null -> {
                val decl = ident.targetVarDecl() ?: throw FatalAstException("invalid identifier ${ident.nameInSource}")
                return if (decl.type == VarDeclType.MEMORY && decl.value is NumericLiteral)
                    target.isIOAddress((decl.value as NumericLiteral).number.toUInt())
                else
                    false
            }
            multi != null -> {
                return multi.any { it.isIOAddress(target) }
            }
            else -> return false
        }
    }

    fun targetIdentifiers(): List<IdentifierReference> {
        val result = mutableListOf<IdentifierReference>()
        multi?.mapNotNullTo(result) { it.identifier }
        if(identifier!=null)
            result += identifier!!
        return result
    }
}

class Jump(var target: Expression, override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        if(node===target && replacement is Expression) {
            target = replacement
        }
        else
            throw FatalAstException("can't replace $node")
    }
    override fun copy() = Jump(target.copy(), position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = target.referencesIdentifier(nameInSource)

    override fun toString() =
        "Jump($target, pos=$position)"
}

class FunctionCallStatement(override var target: IdentifierReference,
                            override val args: MutableList<Expression>,
                            val void: Boolean,
                            override val position: Position) : Statement(), IFunctionCall {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        args.forEach { it.linkParents(this) }
    }

    override fun copy(): FunctionCallStatement {
        val argsCopies = args.map { it.copy() }
        return FunctionCallStatement(target.copy(), argsCopies.toMutableList(), void, position)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        if(node===target)
            target = replacement as IdentifierReference
        else {
            val idx = args.indexOfFirst { it===node }
            args[idx] = replacement as Expression
        }
        replacement.parent = this
    }

    override fun referencesIdentifier(nameInSource: List<String>): Boolean = target.referencesIdentifier(nameInSource) || args.any { it.referencesIdentifier(nameInSource) }
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString() = "FunctionCallStatement(target=$target, pos=$position)"
}

class InlineAssembly(val assembly: String, val isIR: Boolean, override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun copy() = InlineAssembly(assembly, isIR, position)

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = false

    fun hasReturnOrRts(): Boolean {
        return if(isIR) {
            " return" in assembly || "\treturn" in assembly || " jump" in assembly || "\tjump" in assembly || " jumpa" in assembly || "\tjumpa" in assembly
        } else {
            " rti" in assembly || "\trti" in assembly || " rts" in assembly || "\trts" in assembly ||
            " jmp" in assembly || "\tjmp" in assembly || " bra" in assembly || "\tbra" in assembly
        }
    }


    val names: Set<String> by lazy {
        // A cache of all the words (identifiers) present in this block of assembly code
        // this is used when checking if prog8 names are referenced from assembly code
        val wordPattern = if(isIR)
                Regex("""\b([_a-zA-Z]\w+?)(?!.[a-zA-Z]\b)\b""")     // words not ending with a dot and a single letter (like "pop.b")
            else
                Regex("""\b([_a-zA-Z]\w+?)\b""")
        assembly.splitToSequence('\n')
            .map {
                val everythingBeforeComment = it.substringBefore(';')
                wordPattern.findAll(everythingBeforeComment)
            }
            .flatMap { it.map { mr -> mr.value } }
            .toSet()
    }
}

class Defer(val scope: AnonymousScope, override val position: Position): Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        scope.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = scope.referencesIdentifier(nameInSource)
    override fun copy() = Defer(scope.copy(), position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

class AnonymousScope(override val statements: MutableList<Statement>,
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

    override fun referencesIdentifier(nameInSource: List<String>): Boolean = statements.any { it.referencesIdentifier(nameInSource) }
    override fun copy() = AnonymousScope(statements.map { it.copy() }.toMutableList(), position)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

// the subroutine class covers both the normal user-defined subroutines,
// and also the predefined/ROM/register-based subroutines.
// (multiple return types can only occur for the latter type)
class Subroutine(override val name: String,
                 val parameters: MutableList<SubroutineParameter>,
                 val returntypes: MutableList<DataType>,
                 val asmParameterRegisters: List<RegisterOrStatusflag>,
                 val asmReturnvaluesRegisters: List<RegisterOrStatusflag>,
                 val asmClobbers: Set<CpuRegister>,
                 val asmAddress: Address?,
                 val isAsmSubroutine: Boolean,
                 var inline: Boolean,
                 var hasBeenInlined: Boolean=false,
                 override val statements: MutableList<Statement>,
                 override val position: Position) : Statement(), INameScope {

    override lateinit var parent: Node
    override fun copy() = throw NotImplementedError("no support for duplicating a Subroutine")

    override fun linkParents(parent: Node) {
        this.parent = parent
        this.asmAddress?.varbank?.linkParents(this)
        this.asmAddress?.address?.linkParents(this)
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
            is NumericLiteral -> {
                if(node===asmAddress?.address) {
                    asmAddress.address = replacement
                } else if(node===asmAddress?.varbank) {
                    asmAddress.constbank = replacement.number.toInt().toUByte()
                    asmAddress.varbank = null
                } else throw FatalAstException("can't replace")
            }
            else -> throw FatalAstException("can't replace")
        }
    }

    override fun referencesIdentifier(nameInSource: List<String>): Boolean =
        asmAddress?.varbank?.referencesIdentifier(nameInSource)==true ||
        statements.any { it.referencesIdentifier(nameInSource) } ||
                parameters.any { it.referencesIdentifier(nameInSource) }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun toString() =
        "Subroutine(name=$name, parameters=$parameters, returntypes=$returntypes, ${statements.size} statements, address=$asmAddress)"

    class Address(var constbank: UByte?, var varbank: IdentifierReference?, var address: Expression) {
        override fun toString(): String {
            val addrString = (address as? NumericLiteral)?.number?.toHex() ?: "<non-const-address>"
            return if(constbank!=null)
                "$constbank:$addrString"
            else if(varbank!=null)
                "${varbank?.nameInSource?.joinToString(".")}:$addrString"
            else
                addrString
        }
    }
}

open class SubroutineParameter(val name: String,
                               val type: DataType,
                               val zp: ZeropageWish,
                               val registerOrPair: RegisterOrPair?,
                               final override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        throw FatalAstException("can't replace anything in a subroutineparameter node")
    }

    override fun copy() = SubroutineParameter(name, type, zp, registerOrPair, position)
    override fun toString() = "Param($type:$name)"
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = nameInSource.size==1 && name==nameInSource[0]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SubroutineParameter) return false
        return name == other.name && type == other.type && zp == other.zp && registerOrPair == other.registerOrPair
    }

    override fun hashCode(): Int = Objects.hash(name, type, zp)


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
    override fun referencesIdentifier(nameInSource: List<String>): Boolean =
        condition.referencesIdentifier(nameInSource) ||
                truepart.referencesIdentifier(nameInSource) ||
                elsepart.referencesIdentifier(nameInSource)
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
    override fun referencesIdentifier(nameInSource: List<String>): Boolean =
        truepart.referencesIdentifier(nameInSource) || elsepart.referencesIdentifier(nameInSource)
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
    override fun referencesIdentifier(nameInSource: List<String>): Boolean =
        loopVar.referencesIdentifier(nameInSource) ||
                iterable.referencesIdentifier(nameInSource) ||
                body.referencesIdentifier(nameInSource)

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
    override fun referencesIdentifier(nameInSource: List<String>): Boolean =
        condition.referencesIdentifier(nameInSource) || body.referencesIdentifier(nameInSource)
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
    override fun referencesIdentifier(nameInSource: List<String>): Boolean =
        iterations?.referencesIdentifier(nameInSource)==true || body.referencesIdentifier(nameInSource)
}

class UnrollLoop(var iterations: Expression, var body: AnonymousScope, override val position: Position) : Statement() {
    // note: the iterations needs to evaluate to a constant number once parsed.
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        iterations.linkParents(this)
        body.linkParents(this)
    }

    override fun copy() = throw NotImplementedError("no support for duplicating an UnrollLoop")

    override fun replaceChildNode(node: Node, replacement: Node) {
        if (node===body) body = replacement as AnonymousScope
        else if (node===iterations) iterations = replacement as Expression
        else throw FatalAstException("invalid replace")
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = iterations.referencesIdentifier(nameInSource) || body.referencesIdentifier(nameInSource)
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
    override fun referencesIdentifier(nameInSource: List<String>): Boolean =
        condition.referencesIdentifier(nameInSource) || body.referencesIdentifier(nameInSource)
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
    override fun referencesIdentifier(nameInSource: List<String>): Boolean =
        condition.referencesIdentifier(nameInSource) || choices.any { it.referencesIdentifier(nameInSource) }
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
    override fun referencesIdentifier(nameInSource: List<String>): Boolean =
        values?.any { it.referencesIdentifier(nameInSource) }==true || statements.referencesIdentifier(nameInSource)
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
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = addressExpression.referencesIdentifier(nameInSource)
}
