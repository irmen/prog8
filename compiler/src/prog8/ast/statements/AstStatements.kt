package prog8.ast.statements

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.processing.IAstModifyingVisitor
import prog8.ast.processing.IAstVisitor
import prog8.compiler.HeapValues


sealed class Statement : Node {
    abstract fun accept(visitor: IAstModifyingVisitor) : Statement
    abstract fun accept(visitor: IAstVisitor)
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

    abstract val expensiveToInline: Boolean

    fun definingBlock(): Block {
        if(this is Block)
            return this
        return findParentNode<Block>(this)!!
    }
}


class BuiltinFunctionStatementPlaceholder(val name: String, override val position: Position) : Statement() {
    override var parent: Node = ParentSentinel
    override fun linkParents(parent: Node) {}
    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun definingScope(): INameScope = BuiltinFunctionScopePlaceholder
    override val expensiveToInline = false
}


data class RegisterOrStatusflag(val registerOrPair: RegisterOrPair?, val statusflag: Statusflag?, val stack: Boolean?)


class Block(override val name: String,
            val address: Int?,
            override var statements: MutableList<Statement>,
            val isInLibrary: Boolean,
            override val position: Position) : Statement(), INameScope {
    override lateinit var parent: Node
    override val expensiveToInline
        get() = statements.any { it.expensiveToInline }

    override fun linkParents(parent: Node) {
        this.parent = parent
        statements.forEach {it.linkParents(this)}
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    override fun toString(): String {
        return "Block(name=$name, address=$address, ${statements.size} statements)"
    }

    fun options() = statements.filter { it is Directive && it.directive == "%option" }.flatMap { (it as Directive).args }.map {it.name!!}.toSet()
}

data class Directive(val directive: String, val args: List<DirectiveArg>, override val position: Position) : Statement() {
    override lateinit var parent: Node
    override val expensiveToInline = false

    override fun linkParents(parent: Node) {
        this.parent = parent
        args.forEach{it.linkParents(this)}
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
}

data class DirectiveArg(val str: String?, val name: String?, val int: Int?, override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
}

data class Label(val name: String, override val position: Position) : Statement() {
    override lateinit var parent: Node
    override val expensiveToInline = false

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    override fun toString(): String {
        return "Label(name=$name, pos=$position)"
    }

    val scopedname: String by lazy { makeScopedName(name) }
}

open class Return(var value: Expression?, override val position: Position) : Statement() {
    override lateinit var parent: Node
    override val expensiveToInline = value!=null && value !is NumericLiteralValue

    override fun linkParents(parent: Node) {
        this.parent = parent
        value?.linkParents(this)
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    override fun toString(): String {
        return "Return($value, pos=$position)"
    }
}

class ReturnFromIrq(override val position: Position) : Return(null, position) {
    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    override fun toString(): String {
        return "ReturnFromIrq(pos=$position)"
    }
}

class Continue(override val position: Position) : Statement() {
    override lateinit var parent: Node
    override val expensiveToInline = false

    override fun linkParents(parent: Node) {
        this.parent=parent
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
}

class Break(override val position: Position) : Statement() {
    override lateinit var parent: Node
    override val expensiveToInline = false

    override fun linkParents(parent: Node) {
        this.parent=parent
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
}


enum class ZeropageWish {
    REQUIRE_ZEROPAGE,
    PREFER_ZEROPAGE,
    DONTCARE,
    NOT_IN_ZEROPAGE
}

class VarDecl(val type: VarDeclType,
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

    override val expensiveToInline
            get() = value!=null && value !is NumericLiteralValue

    companion object {
        fun createAuto(refLv: ReferenceLiteralValue, heap: HeapValues): VarDecl {
            if(refLv.heapId==null)
                throw FatalAstException("can only create autovar for a ref lv that has a heapid  $refLv")

            val autoVarName = "$autoHeapValuePrefix${refLv.heapId}"

            return if(refLv.isArray) {
                val declaredType = ArrayElementTypes.getValue(refLv.type)
                val arraysize = ArrayIndex.forArray(refLv, heap)
                VarDecl(VarDeclType.VAR, declaredType, ZeropageWish.NOT_IN_ZEROPAGE, arraysize, autoVarName, null, refLv,
                        isArray = true, autogeneratedDontRemove = true, position = refLv.position)
            } else {
                VarDecl(VarDeclType.VAR, refLv.type, ZeropageWish.NOT_IN_ZEROPAGE, null, autoVarName, null, refLv,
                        isArray = false, autogeneratedDontRemove = true, position = refLv.position)
            }
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

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    val scopedname: String by lazy { makeScopedName(name) }

    override fun toString(): String {
        return "VarDecl(name=$name, vartype=$type, datatype=$datatype, struct=$structName, value=$value, pos=$position)"
    }

    fun asDefaultValueDecl(parent: Node?): VarDecl {
        val constValue = when(declaredDatatype) {
            DataType.UBYTE -> NumericLiteralValue(DataType.UBYTE, 0,  position)
            DataType.BYTE -> NumericLiteralValue(DataType.BYTE, 0,  position)
            DataType.UWORD -> NumericLiteralValue(DataType.UWORD, 0, position)
            DataType.WORD -> NumericLiteralValue(DataType.WORD, 0, position)
            DataType.FLOAT -> NumericLiteralValue(DataType.FLOAT, 0.0, position)
            else -> throw FatalAstException("can only set a default value for a numeric type")
        }
        val decl = VarDecl(type, declaredDatatype, zeropage, arraysize, name, structName, constValue, isArray, false, position)
        if(parent!=null)
            decl.linkParents(parent)
        return decl
    }

    fun flattenStructMembers(): MutableList<Statement> {
        val result = struct!!.statements.withIndex().map {
            val member = it.value as VarDecl
            val initvalue = if(value!=null) (value as StructLiteralValue).values[it.index] else null
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

class ArrayIndex(var index: Expression, override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        index.linkParents(this)
    }

    companion object {
        fun forArray(v: ReferenceLiteralValue, heap: HeapValues): ArrayIndex {
            val arraySize = v.array?.size ?: heap.get(v.heapId!!).arraysize
            return ArrayIndex(NumericLiteralValue.optimalNumeric(arraySize, v.position), v.position)
        }
    }

    fun accept(visitor: IAstModifyingVisitor) {
        index = index.accept(visitor)
    }
    fun accept(visitor: IAstVisitor) {
        index.accept(visitor)
    }

    override fun toString(): String {
        return("ArrayIndex($index, pos=$position)")
    }

    fun size() = (index as? NumericLiteralValue)?.number?.toInt()
}

open class Assignment(var target: AssignTarget, val aug_op : String?, var value: Expression, override val position: Position) : Statement() {
    override lateinit var parent: Node
    override val expensiveToInline
            get() = value !is NumericLiteralValue

    override fun linkParents(parent: Node) {
        this.parent = parent
        this.target.linkParents(this)
        value.linkParents(this)
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    override fun toString(): String {
        return("Assignment(augop: $aug_op, target: $target, value: $value, pos=$position)")
    }
}

// This is a special class so the compiler can see if the assignments are for initializing the vars in the scope,
// or just a regular assignment. It may optimize the initialization step from this.
class VariableInitializationAssignment(target: AssignTarget, aug_op: String?, value: Expression, position: Position)
    : Assignment(target, aug_op, value, position)

data class AssignTarget(val register: Register?,
                        val identifier: IdentifierReference?,
                        val arrayindexed: ArrayIndexedExpression?,
                        var memoryAddress: DirectMemoryWrite?,
                        override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
        arrayindexed?.linkParents(this)
        memoryAddress?.linkParents(this)
    }

    fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    fun accept(visitor: IAstVisitor) = visitor.visit(this)

    companion object {
        fun fromExpr(expr: Expression): AssignTarget {
            return when (expr) {
                is RegisterExpr -> AssignTarget(expr.register, null, null, null, expr.position)
                is IdentifierReference -> AssignTarget(null, expr, null, null, expr.position)
                is ArrayIndexedExpression -> AssignTarget(null, null, expr, null, expr.position)
                is DirectMemoryRead -> AssignTarget(null, null, null, DirectMemoryWrite(expr.addressExpression, expr.position), expr.position)
                else -> throw FatalAstException("invalid expression object $expr")
            }
        }
    }

    fun inferType(program: Program, stmt: Statement): DataType? {
        if(register!=null)
            return DataType.UBYTE

        if(identifier!=null) {
            val symbol = program.namespace.lookup(identifier.nameInSource, stmt) ?: return null
            if (symbol is VarDecl) return symbol.datatype
        }

        if(arrayindexed!=null) {
            val dt = arrayindexed.inferType(program)
            if(dt!=null)
                return dt
        }

        if(memoryAddress!=null)
            return DataType.UBYTE

        return null
    }

    infix fun isSameAs(value: Expression): Boolean {
        return when {
            this.memoryAddress!=null -> false
            this.register!=null -> value is RegisterExpr && value.register==register
            this.identifier!=null -> value is IdentifierReference && value.nameInSource==identifier.nameInSource
            this.arrayindexed!=null -> value is ArrayIndexedExpression &&
                    value.identifier.nameInSource==arrayindexed.identifier.nameInSource &&
                    value.arrayspec.size()!=null &&
                    arrayindexed.arrayspec.size()!=null &&
                    value.arrayspec.size()==arrayindexed.arrayspec.size()
            else -> false
        }
    }

    fun isSameAs(other: AssignTarget, program: Program): Boolean {
        if(this===other)
            return true
        if(this.register!=null && other.register!=null)
            return this.register==other.register
        if(this.identifier!=null && other.identifier!=null)
            return this.identifier.nameInSource==other.identifier.nameInSource
        if(this.memoryAddress!=null && other.memoryAddress!=null) {
            val addr1 = this.memoryAddress!!.addressExpression.constValue(program)
            val addr2 = other.memoryAddress!!.addressExpression.constValue(program)
            return addr1!=null && addr2!=null && addr1==addr2
        }
        if(this.arrayindexed!=null && other.arrayindexed!=null) {
            if(this.arrayindexed.identifier.nameInSource == other.arrayindexed.identifier.nameInSource) {
                val x1 = this.arrayindexed.arrayspec.index.constValue(program)
                val x2 = other.arrayindexed.arrayspec.index.constValue(program)
                return x1!=null && x2!=null && x1==x2
            }
        }
        return false
    }

    fun isNotMemory(namespace: INameScope): Boolean {
        if(this.register!=null)
            return true
        if(this.memoryAddress!=null)
            return false
        if(this.arrayindexed!=null) {
            val targetStmt = this.arrayindexed.identifier.targetVarDecl(namespace)
            if(targetStmt!=null)
                return targetStmt.type!= VarDeclType.MEMORY
        }
        if(this.identifier!=null) {
            val targetStmt = this.identifier.targetVarDecl(namespace)
            if(targetStmt!=null)
                return targetStmt.type!= VarDeclType.MEMORY
        }
        return false
    }
}

class PostIncrDecr(var target: AssignTarget, val operator: String, override val position: Position) : Statement() {
    override lateinit var parent: Node
    override val expensiveToInline = false

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    override fun toString(): String {
        return "PostIncrDecr(op: $operator, target: $target, pos=$position)"
    }
}

class Jump(val address: Int?,
           val identifier: IdentifierReference?,
           val generatedLabel: String?,             // used in code generation scenarios
           override val position: Position) : Statement() {
    override lateinit var parent: Node
    override val expensiveToInline = false

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    override fun toString(): String {
        return "Jump(addr: $address, identifier: $identifier, label: $generatedLabel;  pos=$position)"
    }
}

class FunctionCallStatement(override var target: IdentifierReference,
                            override var arglist: MutableList<Expression>,
                            override val position: Position) : Statement(), IFunctionCall {
    override lateinit var parent: Node
    override val expensiveToInline
            get() = arglist.any { it !is NumericLiteralValue }

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        arglist.forEach { it.linkParents(this) }
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    override fun toString(): String {
        return "FunctionCallStatement(target=$target, pos=$position)"
    }
}

class InlineAssembly(val assembly: String, override val position: Position) : Statement() {
    override lateinit var parent: Node
    override val expensiveToInline = true

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
}

class AnonymousScope(override var statements: MutableList<Statement>,
                     override val position: Position) : INameScope, Statement() {
    override val name: String
    override lateinit var parent: Node
    override val expensiveToInline
        get() = statements.any { it.expensiveToInline }

    init {
        name = "<anon-$sequenceNumber>"     // make sure it's an invalid soruce code identifier so user source code can never produce it
        sequenceNumber++
    }

    companion object {
        private var sequenceNumber = 1
    }

    override fun linkParents(parent: Node) {
        this.parent = parent
        statements.forEach { it.linkParents(this) }
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
}

class NopStatement(override val position: Position): Statement() {
    override lateinit var parent: Node
    override val expensiveToInline = false

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    companion object {
        fun insteadOf(stmt: Statement): NopStatement {
            val nop = NopStatement(stmt.position)
            nop.parent = stmt.parent
            return nop
        }
    }
}

// the subroutine class covers both the normal user-defined subroutines,
// and also the predefined/ROM/register-based subroutines.
// (multiple return types can only occur for the latter type)
class Subroutine(override val name: String,
                 val parameters: List<SubroutineParameter>,
                 val returntypes: List<DataType>,
                 val asmParameterRegisters: List<RegisterOrStatusflag>,
                 val asmReturnvaluesRegisters: List<RegisterOrStatusflag>,
                 val asmClobbers: Set<Register>,
                 val asmAddress: Int?,
                 val isAsmSubroutine: Boolean,
                 override var statements: MutableList<Statement>,
                 override val position: Position) : Statement(), INameScope {

    var keepAlways: Boolean = false
    override val expensiveToInline
            get() = statements.any { it.expensiveToInline }

    override lateinit var parent: Node
    val calledBy = mutableListOf<Node>()
    val calls = mutableSetOf<Subroutine>()

    val scopedname: String by lazy { makeScopedName(name) }

    override fun linkParents(parent: Node) {
        this.parent = parent
        parameters.forEach { it.linkParents(this) }
        statements.forEach { it.linkParents(this) }
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    override fun toString(): String {
        return "Subroutine(name=$name, parameters=$parameters, returntypes=$returntypes, ${statements.size} statements, address=$asmAddress)"
    }

    fun amountOfRtsInAsm(): Int = statements
            .asSequence()
            .filter { it is InlineAssembly }
            .map { (it as InlineAssembly).assembly }
            .count { " rti" in it || "\trti" in it || " rts" in it || "\trts" in it || " jmp" in it || "\tjmp" in it }

    val canBeAsmSubroutine =false // TODO disabled for now, see below about problem with converting to asm subroutine
//            !isAsmSubroutine
//                    && ((parameters.size == 1 && parameters[0].type in setOf(DataType.BYTE, DataType.UBYTE, DataType.WORD, DataType.UWORD))
//                    || (parameters.size == 2 && parameters.map { it.type }.all { it == DataType.BYTE || it == DataType.UBYTE }))

    fun intoAsmSubroutine(): Subroutine {
        // TODO turn subroutine into asm calling convention.   Requires rethinking of how parameters are handled (conflicts with local vardefs now, see AstIdentifierChecker...)
        return this // TODO

//        println("TO ASM   $this")  // TODO
//        val paramregs = if (parameters.size == 1 && parameters[0].type in setOf(DataType.BYTE, DataType.UBYTE))
//            listOf(RegisterOrStatusflag(RegisterOrPair.Y, null, null))
//        else if (parameters.size == 1 && parameters[0].type in setOf(DataType.WORD, DataType.UWORD))
//            listOf(RegisterOrStatusflag(RegisterOrPair.AY, null, null))
//        else if (parameters.size == 2 && parameters.map { it.type }.all { it == DataType.BYTE || it == DataType.UBYTE })
//            listOf(RegisterOrStatusflag(RegisterOrPair.A, null, null), RegisterOrStatusflag(RegisterOrPair.Y, null, null))
//        else throw FatalAstException("cannot convert subroutine to asm parameters")
//
//        val asmsub=Subroutine(
//                name,
//                parameters,
//                returntypes,
//                paramregs,
//                emptyList(),
//                emptySet(),
//                null,
//                true,
//                statements,
//                position
//        )
//        asmsub.linkParents(parent)
//        return asmsub
    }
}

open class SubroutineParameter(val name: String,
                               val type: DataType,
                               override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
}

class IfStatement(var condition: Expression,
                  var truepart: AnonymousScope,
                  var elsepart: AnonymousScope,
                  override val position: Position) : Statement() {
    override lateinit var parent: Node
    override val expensiveToInline: Boolean
        get() = truepart.expensiveToInline || elsepart.expensiveToInline

    override fun linkParents(parent: Node) {
        this.parent = parent
        condition.linkParents(this)
        truepart.linkParents(this)
        elsepart.linkParents(this)
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
}

class BranchStatement(var condition: BranchCondition,
                      var truepart: AnonymousScope,
                      var elsepart: AnonymousScope,
                      override val position: Position) : Statement() {
    override lateinit var parent: Node
    override val expensiveToInline: Boolean
        get() = truepart.expensiveToInline || elsepart.expensiveToInline

    override fun linkParents(parent: Node) {
        this.parent = parent
        truepart.linkParents(this)
        elsepart.linkParents(this)
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
}

class ForLoop(val loopRegister: Register?,
              val decltype: DataType?,
              val zeropage: ZeropageWish,
              val loopVar: IdentifierReference?,
              var iterable: Expression,
              var body: AnonymousScope,
              override val position: Position) : Statement() {
    override lateinit var parent: Node
    override val expensiveToInline = true

    override fun linkParents(parent: Node) {
        this.parent=parent
        loopVar?.linkParents(if(decltype==null) this else body)
        iterable.linkParents(this)
        body.linkParents(this)
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)

    override fun toString(): String {
        return "ForLoop(loopVar: $loopVar, loopReg: $loopRegister, iterable: $iterable, pos=$position)"
    }

    companion object {
        const val iteratorLoopcounterVarname = "prog8forloopcounter"
    }
}

class WhileLoop(var condition: Expression,
                var body: AnonymousScope,
                override val position: Position) : Statement() {
    override lateinit var parent: Node
    override val expensiveToInline = true

    override fun linkParents(parent: Node) {
        this.parent = parent
        condition.linkParents(this)
        body.linkParents(this)
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
}

class RepeatLoop(var body: AnonymousScope,
                 var untilCondition: Expression,
                 override val position: Position) : Statement() {
    override lateinit var parent: Node
    override val expensiveToInline = true

    override fun linkParents(parent: Node) {
        this.parent = parent
        untilCondition.linkParents(this)
        body.linkParents(this)
    }

    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
}

class WhenStatement(val condition: Expression,
                    val choices: MutableList<WhenChoice>,
                    override val position: Position): Statement() {
    override lateinit var parent: Node
    override val expensiveToInline: Boolean = true

    override fun linkParents(parent: Node) {
        this.parent = parent
        condition.linkParents(this)
        choices.forEach { it.linkParents(this) }
    }

    fun choiceValues(program: Program): List<Pair<List<Int>?, WhenChoice>> {
        // only gives sensible results when the choices are all valid (constant integers)
        val result = mutableListOf<Pair<List<Int>?, WhenChoice>>()
        for(choice in choices) {
            if(choice.values==null)
                result.add(null to choice)
            else {
                val values = choice.values.map { it.constValue(program)?.number?.toInt() }
                if(values.contains(null))
                    result.add(null to choice)
                else
                    result.add(values.filterNotNull() to choice)
            }
        }
        return result
    }

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
}

class WhenChoice(val values: List<Expression>?,           // if null,  this is the 'else' part
                 val statements: AnonymousScope,
                 override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        values?.forEach { it.linkParents(this) }
        statements.linkParents(this)
        this.parent = parent
    }

    override fun toString(): String {
        return "Choice($values at $position)"
    }

    fun accept(visitor: IAstVisitor) = visitor.visit(this)
    fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
}


class StructDecl(override val name: String,
                 override var statements: MutableList<Statement>,      // actually, only vardecls here
                 override val position: Position): Statement(), INameScope {

    override lateinit var parent: Node
    override val expensiveToInline: Boolean = true

    override fun linkParents(parent: Node) {
        this.parent = parent
        this.statements.forEach { it.linkParents(this) }
    }

    val numberOfElements: Int
        get() = this.statements.size

    override fun accept(visitor: IAstVisitor) = visitor.visit(this)
    override fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
}

class DirectMemoryWrite(var addressExpression: Expression, override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        this.addressExpression.linkParents(this)
    }

    override fun toString(): String {
        return "DirectMemoryWrite($addressExpression)"
    }

    fun accept(visitor: IAstVisitor) = visitor.visit(this)
    fun accept(visitor: IAstModifyingVisitor) = visitor.visit(this)
}

