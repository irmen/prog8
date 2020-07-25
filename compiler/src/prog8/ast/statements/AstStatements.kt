package prog8.ast.statements

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstVisitor


sealed class Statement : Node {
    abstract fun accept(visitor: IAstVisitor)
    abstract fun accept(visitor: AstWalker, parent: Node)

    fun makeScopedName(name: String): String {
        // easy way out is to always return the full scoped name.
        // it would be nicer to find only the minimal prefixed scoped name, but that's too much hassle for now.
        // and like this, we can cache the name even,
        // like in a lazy property on the statement object itself (label, subroutine, vardecl)
        val scope = mutableListOf<String>()
        var statementScope = this.parent
        while(statementScope !is ParentSentinel && statementScope !is Module) {
            if(statementScope is INameScope) {
                scope.add(0, statementScope.name)
            }
            statementScope = statementScope.parent
        }
        if(name.isNotEmpty())
            scope.add(name)
        return scope.joinToString(".")
    }

    fun definingBlock(): Block {
        if(this is Block)
            return this
        return findParentNode<Block>(this)!!
    }
}


class BuiltinFunctionStatementPlaceholder(val name: String, override val position: Position) : Statement() {
    override var parent: Node = ParentSentinel
    override fun linkParents(parent: Node) {}
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
    override fun definingScope(): INameScope = BuiltinFunctionScopePlaceholder
    override fun replaceChildNode(node: Node, replacement: Node) {
        replacement.parent = this
    }
}

data class RegisterOrStatusflag(val registerOrPair: RegisterOrPair?, val statusflag: Statusflag?, val stack: Boolean)

class Block(override val name: String,
            val address: Int?,
            override var statements: MutableList<Statement>,
            val isInLibrary: Boolean,
            override val position: Position) : Statement(), INameScope {
    override lateinit var parent: Node

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

    override fun toString(): String {
        return "Block(name=$name, address=$address, ${statements.size} statements)"
    }

    fun options() = statements.filter { it is Directive && it.directive == "%option" }.flatMap { (it as Directive).args }.map {it.name!!}.toSet()
}

data class Directive(val directive: String, val args: List<DirectiveArg>, override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        args.forEach{it.linkParents(this)}
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

data class DirectiveArg(val str: String?, val name: String?, val int: Int?, override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
}

data class Label(val name: String, override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    override fun toString(): String {
        return "Label(name=$name, pos=$position)"
    }
}

open class Return(var value: Expression?, override val position: Position) : Statement() {
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

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    override fun toString(): String {
        return "Return($value, pos=$position)"
    }
}

class ReturnFromIrq(override val position: Position) : Return(null, position) {
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    override fun toString(): String {
        return "ReturnFromIrq(pos=$position)"
    }
    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
}

class Continue(override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent=parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

class Break(override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent=parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}


enum class ZeropageWish {
    REQUIRE_ZEROPAGE,
    PREFER_ZEROPAGE,
    DONTCARE,
    NOT_IN_ZEROPAGE
}


open class VarDecl(val type: VarDeclType,
              private val declaredDatatype: DataType,
              val zeropage: ZeropageWish,
              var arraysize: ArrayIndex?,
              val name: String,
              private val structName: String?,
              var value: Expression?,
              val isArray: Boolean,
              val autogeneratedDontRemove: Boolean,
              override val position: Position) : Statement() {
    override lateinit var parent: Node
    var struct: StructDecl? = null        // set later (because at parse time, we only know the name)
        private set
    var structHasBeenFlattened = false      // set later
        private set

    // prefix for literal values that are turned into a variable on the heap

    companion object {
        private var autoHeapValueSequenceNumber = 0

        fun createAuto(string: StringLiteralValue): VarDecl {
            val autoVarName = "auto_heap_value_${++autoHeapValueSequenceNumber}"
            return VarDecl(VarDeclType.VAR, DataType.STR, ZeropageWish.NOT_IN_ZEROPAGE, null, autoVarName, null, string,
                        isArray = false, autogeneratedDontRemove = true, position = string.position)
        }

        fun createAuto(array: ArrayLiteralValue): VarDecl {
            val autoVarName = "auto_heap_value_${++autoHeapValueSequenceNumber}"
            val declaredType = ArrayElementTypes.getValue(array.type.typeOrElse(DataType.STRUCT))
            val arraysize = ArrayIndex.forArray(array)
            return VarDecl(VarDeclType.VAR, declaredType, ZeropageWish.NOT_IN_ZEROPAGE, arraysize, autoVarName, null, array,
                    isArray = true, autogeneratedDontRemove = true, position = array.position)
        }

        fun defaultZero(dt: DataType, position: Position) = when(dt) {
            DataType.UBYTE -> NumericLiteralValue(DataType.UBYTE, 0,  position)
            DataType.BYTE -> NumericLiteralValue(DataType.BYTE, 0,  position)
            DataType.UWORD -> NumericLiteralValue(DataType.UWORD, 0, position)
            DataType.WORD -> NumericLiteralValue(DataType.WORD, 0, position)
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
                else -> {
                    datatypeErrors.add(SyntaxError("array can only contain bytes/words/floats", position))
                    DataType.ARRAY_UB
                }
            }

    override fun linkParents(parent: Node) {
        this.parent = parent
        arraysize?.linkParents(this)
        value?.linkParents(this)
        if(structName!=null) {
            val structStmt = definingScope().lookup(listOf(structName), this)
            if(structStmt!=null)
                struct = definingScope().lookup(listOf(structName), this) as StructDecl
        }
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Expression && node===value)
        value = replacement
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    override fun toString(): String {
        return "VarDecl(name=$name, vartype=$type, datatype=$datatype, struct=$structName, value=$value, pos=$position)"
    }

    fun zeroElementValue() = defaultZero(declaredDatatype, position)

    fun flattenStructMembers(): MutableList<Statement> {
        val result = struct!!.statements.withIndex().map {
            val member = it.value as VarDecl
            val initvalue = if(value!=null) (value as ArrayLiteralValue).value[it.index] else null
            VarDecl(
                    VarDeclType.VAR,
                    member.datatype,
                    ZeropageWish.NOT_IN_ZEROPAGE,
                    member.arraysize,
                    mangledStructMemberName(name, member.name),
                    struct!!.name,
                    initvalue,
                    member.isArray,
                    true,
                    member.position
            ) as Statement
        }.toMutableList()
        structHasBeenFlattened = true
        return result
    }
}

// a vardecl used only for subroutine parameters
class ParameterVarDecl(name: String, declaredDatatype: DataType, position: Position)
    : VarDecl(VarDeclType.VAR, declaredDatatype, ZeropageWish.NOT_IN_ZEROPAGE, null, name, null, null, false, true, position)


class ArrayIndex(var index: Expression, override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        index.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Expression && node===index)
        index = replacement
        replacement.parent = this
    }

    companion object {
        fun forArray(v: ArrayLiteralValue): ArrayIndex {
            return ArrayIndex(NumericLiteralValue.optimalNumeric(v.value.size, v.position), v.position)
        }
    }

    fun accept(visitor: IAstVisitor) = index.accept(visitor)
    fun accept(visitor: AstWalker, parent: Node) = index.accept(visitor, this)

    override fun toString(): String {
        return("ArrayIndex($index, pos=$position)")
    }

    fun size() = (index as? NumericLiteralValue)?.number?.toInt()
}

open class Assignment(var target: AssignTarget, var aug_op : String?, var value: Expression, override val position: Position) : Statement() {
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

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    override fun toString(): String {
        return("Assignment(augop: $aug_op, target: $target, value: $value, pos=$position)")
    }

    fun asDesugaredNonaugmented(): Assignment {
        val augmented = aug_op ?: return this

        val leftOperand: Expression =
                when {
                    target.identifier != null -> target.identifier!!
                    target.arrayindexed != null -> target.arrayindexed!!
                    target.memoryAddress != null -> DirectMemoryRead(target.memoryAddress!!.addressExpression, value.position)
                    else -> throw FatalAstException("strange this")
                }

        val assignment =
            if(augmented=="setvalue") {
                Assignment(target, null, value, position)
            } else {
                val expression = BinaryExpression(leftOperand, augmented.substringBeforeLast('='), value, position)
                Assignment(target, null, expression, position)
            }
        assignment.linkParents(parent)

        return assignment
    }
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
            node===identifier -> identifier = replacement as IdentifierReference
            node===arrayindexed -> arrayindexed = replacement as ArrayIndexedExpression
            else -> throw FatalAstException("invalid replace")
        }
        replacement.parent = this
    }

    fun accept(visitor: IAstVisitor) = visitor.visit(this)
    fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    companion object {
        fun fromExpr(expr: Expression): AssignTarget {
            return when (expr) {
                is IdentifierReference -> AssignTarget(expr, null, null, expr.position)
                is ArrayIndexedExpression -> AssignTarget(null, expr, null, expr.position)
                is DirectMemoryRead -> AssignTarget(null, null, DirectMemoryWrite(expr.addressExpression, expr.position), expr.position)
                else -> throw FatalAstException("invalid expression object $expr")
            }
        }
    }

    fun inferType(program: Program, stmt: Statement): InferredTypes.InferredType {
        if(identifier!=null) {
            val symbol = program.namespace.lookup(identifier!!.nameInSource, stmt) ?: return InferredTypes.unknown()
            if (symbol is VarDecl) return InferredTypes.knownFor(symbol.datatype)
        }

        if(arrayindexed!=null) {
            return arrayindexed!!.inferType(program)
        }

        if(memoryAddress!=null)
            return InferredTypes.knownFor(DataType.UBYTE)

        return InferredTypes.unknown()
    }

    infix fun isSameAs(value: Expression): Boolean {
        return when {
            this.memoryAddress!=null -> {
                // if the target is a memory write, and the value is a memory read, they're the same if the address matches
                if(value is DirectMemoryRead)
                    this.memoryAddress.addressExpression isSameAs value.addressExpression
                else
                    false
            }
            this.identifier!=null -> value is IdentifierReference && value.nameInSource==identifier!!.nameInSource
            this.arrayindexed!=null -> value is ArrayIndexedExpression &&
                    value.identifier.nameInSource==arrayindexed!!.identifier.nameInSource &&
                    value.arrayspec.size()!=null &&
                    arrayindexed!!.arrayspec.size()!=null &&
                    value.arrayspec.size()==arrayindexed!!.arrayspec.size()
            else -> false
        }
    }

    fun isSameAs(other: AssignTarget, program: Program): Boolean {
        if(this===other)
            return true
        if(this.identifier!=null && other.identifier!=null)
            return this.identifier!!.nameInSource==other.identifier!!.nameInSource
        if(this.memoryAddress!=null && other.memoryAddress!=null) {
            val addr1 = this.memoryAddress.addressExpression.constValue(program)
            val addr2 = other.memoryAddress.addressExpression.constValue(program)
            return addr1!=null && addr2!=null && addr1==addr2
        }
        if(this.arrayindexed!=null && other.arrayindexed!=null) {
            if(this.arrayindexed!!.identifier.nameInSource == other.arrayindexed!!.identifier.nameInSource) {
                val x1 = this.arrayindexed!!.arrayspec.index.constValue(program)
                val x2 = other.arrayindexed!!.arrayspec.index.constValue(program)
                return x1!=null && x2!=null && x1==x2
            }
        }
        return false
    }

    fun isNotMemory(namespace: INameScope): Boolean {
        if(this.memoryAddress!=null)
            return false
        if(this.arrayindexed!=null) {
            val targetStmt = this.arrayindexed!!.identifier.targetVarDecl(namespace)
            if(targetStmt!=null)
                return targetStmt.type!= VarDeclType.MEMORY
        }
        if(this.identifier!=null) {
            val targetStmt = this.identifier!!.targetVarDecl(namespace)
            if(targetStmt!=null)
                return targetStmt.type!= VarDeclType.MEMORY
        }
        return false
    }
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

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    override fun toString(): String {
        return "PostIncrDecr(op: $operator, target: $target, pos=$position)"
    }
}

class Jump(val address: Int?,
           val identifier: IdentifierReference?,
           val generatedLabel: String?,             // used in code generation scenarios
           override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    override fun toString(): String {
        return "Jump(addr: $address, identifier: $identifier, label: $generatedLabel;  pos=$position)"
    }
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

    override fun toString(): String {
        return "FunctionCallStatement(target=$target, pos=$position)"
    }
}

class InlineAssembly(val assembly: String, override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

class AnonymousScope(override var statements: MutableList<Statement>,
                     override val position: Position) : INameScope, Statement() {
    override val name: String
    override lateinit var parent: Node

    companion object {
        private var sequenceNumber = 1
    }

    init {
        name = "<anon-$sequenceNumber>"     // make sure it's an invalid soruce code identifier so user source code can never produce it
        sequenceNumber++
    }

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

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

class NopStatement(override val position: Position): Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) = throw FatalAstException("can't replace here")
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

// the subroutine class covers both the normal user-defined subroutines,
// and also the predefined/ROM/register-based subroutines.
// (multiple return types can only occur for the latter type)
class Subroutine(override val name: String,
                 val parameters: List<SubroutineParameter>,
                 val returntypes: List<DataType>,
                 val asmParameterRegisters: List<RegisterOrStatusflag>,
                 val asmReturnvaluesRegisters: List<RegisterOrStatusflag>,
                 val asmClobbers: Set<CpuRegister>,
                 val asmAddress: Int?,
                 val isAsmSubroutine: Boolean,
                 override var statements: MutableList<Statement>,
                 override val position: Position) : Statement(), INameScope {

    var keepAlways: Boolean = false
    override lateinit var parent: Node
    val scopedname: String by lazy { makeScopedName(name) }

    override fun linkParents(parent: Node) {
        this.parent = parent
        parameters.forEach { it.linkParents(this) }
        statements.forEach { it.linkParents(this) }
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Statement)
        val idx = statements.indexOfFirst { it===node }
        statements[idx] = replacement
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    override fun toString(): String {
        return "Subroutine(name=$name, parameters=$parameters, returntypes=$returntypes, ${statements.size} statements, address=$asmAddress)"
    }

    fun regXasResult() = asmReturnvaluesRegisters.any { it.registerOrPair in setOf(RegisterOrPair.X, RegisterOrPair.AX, RegisterOrPair.XY) }

    fun amountOfRtsInAsm(): Int = statements
            .asSequence()
            .filter { it is InlineAssembly }
            .map { (it as InlineAssembly).assembly }
            .count { " rti" in it || "\trti" in it || " rts" in it || "\trts" in it || " jmp" in it || "\tjmp" in it }
}


open class SubroutineParameter(val name: String,
                               val type: DataType,
                               override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        throw FatalAstException("can't replace anything in a subroutineparameter node")
    }
}

class IfStatement(var condition: Expression,
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

class BranchStatement(var condition: BranchCondition,
                      var truepart: AnonymousScope,
                      var elsepart: AnonymousScope,
                      override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        truepart.linkParents(this)
        elsepart.linkParents(this)
    }

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

    override fun toString(): String {
        return "ForLoop(loopVar: $loopVar, iterable: $iterable, pos=$position)"
    }

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
                var untilCondition: Expression,
                override val position: Position) : Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        untilCondition.linkParents(this)
        body.linkParents(this)
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        when {
            node===untilCondition -> untilCondition = replacement as Expression
            node===body -> body = replacement as AnonymousScope
            else -> throw FatalAstException("invalid replace")
        }
        replacement.parent = this
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

class WhenStatement(var condition: Expression,
                    var choices: MutableList<WhenChoice>,
                    override val position: Position): Statement() {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        condition.linkParents(this)
        choices.forEach { it.linkParents(this) }
    }

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
                val values = choice.values!!.map { it.constValue(program)?.number?.toInt() }
                if(values.contains(null))
                    result.add(null to choice)
                else
                    result.add(values.filterNotNull() to choice)
            }
        }
        return result
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}

class WhenChoice(var values: List<Expression>?,           // if null,  this is the 'else' part
                 var statements: AnonymousScope,
                 override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        values?.forEach { it.linkParents(this) }
        statements.linkParents(this)
        this.parent = parent
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is AnonymousScope && node===statements)
        statements = replacement
        replacement.parent = this
    }

    override fun toString(): String {
        return "Choice($values at $position)"
    }

    fun accept(visitor: IAstVisitor) = visitor.visit(this)
    fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}


class StructDecl(override val name: String,
                 override var statements: MutableList<Statement>,      // actually, only vardecls here
                 override val position: Position): Statement(), INameScope {

    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        this.statements.forEach { it.linkParents(this) }
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(replacement is Statement)
        val idx = statements.indexOfFirst { it===node }
        statements[idx] = replacement
        replacement.parent = this
    }

    val numberOfElements: Int
        get() = this.statements.size

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    fun nameOfFirstMember() = (statements.first() as VarDecl).name
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

    override fun toString(): String {
        return "DirectMemoryWrite($addressExpression)"
    }

    fun accept(visitor: IAstVisitor) = visitor.visit(this)
    fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}
