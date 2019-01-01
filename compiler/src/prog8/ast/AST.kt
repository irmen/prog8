package prog8.ast

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import prog8.compiler.HeapValues
import prog8.compiler.intermediate.Value
import prog8.compiler.target.c64.Petscii
import prog8.functions.BuiltinFunctions
import prog8.functions.NotConstArgumentException
import prog8.functions.builtinFunctionReturnType
import prog8.parser.prog8Parser
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.math.floor


/**************************** AST Data classes ****************************/

enum class DataType {
    UBYTE,
    BYTE,
    UWORD,
    WORD,
    FLOAT,
    STR,
    STR_P,
    STR_S,
    STR_PS,
    ARRAY_UB,
    ARRAY_B,
    ARRAY_UW,
    ARRAY_W,
    ARRAY_F;

    fun assignableTo(targetType: DataType) =
            when(this) {
                UBYTE -> targetType == UBYTE || targetType == UWORD || targetType == FLOAT
                BYTE -> targetType == BYTE || targetType == WORD || targetType == FLOAT
                UWORD -> targetType == UWORD || targetType == FLOAT
                WORD -> targetType == WORD || targetType == FLOAT
                FLOAT -> targetType == FLOAT
                STR -> targetType == STR || targetType==STR_S || targetType == UWORD
                STR_P -> targetType == STR_P || targetType==STR_PS || targetType == UWORD
                STR_S -> targetType == STR || targetType==STR_S || targetType == UWORD
                STR_PS -> targetType == STR_P || targetType==STR_PS || targetType == UWORD
                ARRAY_UB -> targetType == UWORD
                ARRAY_B -> targetType == UWORD
                ARRAY_UW -> targetType == UWORD
                ARRAY_W -> targetType == UWORD
                ARRAY_F -> targetType == UWORD
            }
}

enum class Register {
    A,
    X,
    Y
}

enum class RegisterOrPair {
    A,
    X,
    Y,
    AX,
    AY,
    XY
}       // only used in parameter and return value specs in asm subroutines

enum class Statusflag {
    Pc,
    Pz,
    Pv,
    Pn
}

enum class BranchCondition {
    CS,
    CC,
    EQ,
    Z,
    NE,
    NZ,
    VS,
    VC,
    MI,
    NEG,
    PL,
    POS
}

val IterableDatatypes = setOf(
        DataType.STR, DataType.STR_S,
        DataType.STR_P, DataType.STR_PS,  // note: these are a bit weird they store their length as the first byte
        DataType.ARRAY_UB, DataType.ARRAY_B,
        DataType.ARRAY_UW, DataType.ARRAY_W,
        DataType.ARRAY_F)

val StringDatatypes = setOf(DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS)
val NumericDatatypes = setOf(DataType.UBYTE, DataType.BYTE, DataType.UWORD, DataType.WORD, DataType.FLOAT)
val IntegerDatatypes = setOf(DataType.UBYTE, DataType.BYTE, DataType.UWORD, DataType.WORD)
val ArrayDatatypes = setOf(DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W, DataType.ARRAY_F)


class FatalAstException (override var message: String) : Exception(message)

open class AstException (override var message: String) : Exception(message)

class SyntaxError(override var message: String, val position: Position) : AstException(message) {
    override fun toString() = "$position Syntax error: $message"
}

class NameError(override var message: String, val position: Position) : AstException(message) {
    override fun toString() = "$position Name error: $message"
}

open class ExpressionError(message: String, val position: Position) : AstException(message) {
    override fun toString() = "$position Error: $message"
}

class UndefinedSymbolError(symbol: IdentifierReference)
    : ExpressionError("undefined symbol: ${symbol.nameInSource.joinToString(".")}", symbol.position)


data class Position(val file: String, val line: Int, val startCol: Int, val endCol: Int) {
    override fun toString(): String = "[$file: line $line col ${startCol+1}-${endCol+1}]"
}


interface IAstProcessor {
    fun process(module: Module) {
        module.statements = module.statements.asSequence().map { it.process(this) }.toMutableList()
    }

    fun process(expr: PrefixExpression): IExpression {
        expr.expression = expr.expression.process(this)
        return expr
    }

    fun process(expr: BinaryExpression): IExpression {
        expr.left = expr.left.process(this)
        expr.right = expr.right.process(this)
        return expr
    }

    fun process(directive: Directive): IStatement {
        return directive
    }

    fun process(block: Block): IStatement {
        block.statements = block.statements.asSequence().map { it.process(this) }.toMutableList()
        return block
    }

    fun process(decl: VarDecl): IStatement {
        decl.value = decl.value?.process(this)
        decl.arrayspec?.process(this)
        return decl
    }

    fun process(subroutine: Subroutine): IStatement {
        subroutine.statements = subroutine.statements.asSequence().map { it.process(this) }.toMutableList()
        return subroutine
    }

    fun process(functionCall: FunctionCall): IExpression {
        functionCall.arglist = functionCall.arglist.map { it.process(this) }.toMutableList()
        return functionCall
    }

    fun process(functionCall: FunctionCallStatement): IStatement {
        functionCall.arglist = functionCall.arglist.map { it.process(this) }.toMutableList()
        return functionCall
    }

    fun process(identifier: IdentifierReference): IExpression {
        // note: this is an identifier that is used in an expression.
        // other identifiers are simply part of the other statements (such as jumps, subroutine defs etc)
        return identifier
    }

    fun process(jump: Jump): IStatement {
        return jump
    }

    fun process(ifStatement: IfStatement): IStatement {
        ifStatement.condition = ifStatement.condition.process(this)
        ifStatement.truepart = ifStatement.truepart.process(this)
        ifStatement.elsepart = ifStatement.elsepart.process(this)
        return ifStatement
    }

    fun process(branchStatement: BranchStatement): IStatement {
        branchStatement.truepart = branchStatement.truepart.process(this)
        branchStatement.elsepart = branchStatement.elsepart.process(this)
        return branchStatement
    }

    fun process(range: RangeExpr): IExpression {
        range.from = range.from.process(this)
        range.to = range.to.process(this)
        return range
    }

    fun process(label: Label): IStatement {
        return label
    }

    fun process(literalValue: LiteralValue): LiteralValue {
        return literalValue
    }

    fun process(assignment: Assignment): IStatement {
        assignment.targets = assignment.targets.map { it.process(this) }
        assignment.value = assignment.value.process(this)
        return assignment
    }

    fun process(postIncrDecr: PostIncrDecr): IStatement {
        postIncrDecr.target = postIncrDecr.target.process(this)
        return postIncrDecr
    }

    fun process(contStmt: Continue): IStatement {
        return contStmt
    }

    fun process(breakStmt: Break): IStatement {
        return breakStmt
    }

    fun process(forLoop: ForLoop): IStatement {
        forLoop.loopVar?.process(this)
        forLoop.iterable = forLoop.iterable.process(this)
        forLoop.body = forLoop.body.process(this)
        return forLoop
    }

    fun process(whileLoop: WhileLoop): IStatement {
        whileLoop.condition = whileLoop.condition.process(this)
        whileLoop.body = whileLoop.body.process(this)
        return whileLoop
    }

    fun process(repeatLoop: RepeatLoop): IStatement {
        repeatLoop.untilCondition = repeatLoop.untilCondition.process(this)
        repeatLoop.body = repeatLoop.body.process(this)
        return repeatLoop
    }

    fun process(returnStmt: Return): IStatement {
        returnStmt.values = returnStmt.values.map { it.process(this) }
        return returnStmt
    }

    fun process(arrayIndexedExpression: ArrayIndexedExpression): IExpression {
        arrayIndexedExpression.identifier?.process(this)
        arrayIndexedExpression.arrayspec.process(this)
        return arrayIndexedExpression
    }

    fun process(assignTarget: AssignTarget): AssignTarget {
        assignTarget.arrayindexed?.process(this)
        assignTarget.identifier?.process(this)
        assignTarget.memoryAddress?.process(this)
        return assignTarget
    }

    fun process(scope: AnonymousScope): AnonymousScope {
        scope.statements = scope.statements.asSequence().map { it.process(this) }.toMutableList()
        return scope
    }

    fun process(typecastExpression: TypecastExpression): IExpression {
        typecastExpression.expression = typecastExpression.expression.process(this)
        return typecastExpression
    }

    fun process(memread: DirectMemoryRead): IExpression {
        memread.addressExpression = memread.addressExpression.process(this)
        return memread
    }

    fun process(memwrite: DirectMemoryWrite): IExpression {
        memwrite.addressExpression = memwrite.addressExpression.process(this)
        return memwrite
    }
}


interface Node {
    val position: Position
    var parent: Node             // will be linked correctly later (late init)
    fun linkParents(parent: Node)
    fun definingScope(): INameScope {
        val scope = findParentNode<INameScope>(this)
        if(scope!=null) {
            return scope
        }
        if(this is Label && this.name.startsWith("builtin::")) {
            return BuiltinFunctionScopePlaceholder
        }
        throw FatalAstException("scope missing from $this")
    }
}


// find the parent node of a specific type or interface
// (useful to figure out in what namespace/block something is defined, etc)
inline fun <reified T> findParentNode(node: Node): T? {
    var candidate = node.parent
    while(candidate !is T && candidate !is ParentSentinel)
        candidate = candidate.parent
    return if(candidate is ParentSentinel)
        null
    else
        candidate as T
}


interface IStatement : Node {
    fun process(processor: IAstProcessor) : IStatement
    fun makeScopedName(name: String): List<String> {
        // this is usually cached in a lazy property on the statement object itself
        val scope = mutableListOf<String>()
        var statementScope = this.parent
        while(statementScope !is ParentSentinel && statementScope !is Module) {
            if(statementScope is INameScope) {
                scope.add(0, statementScope.name)
            }
            statementScope = statementScope.parent
        }
        scope.add(name)
        return scope
    }
}


interface IFunctionCall {
    var target: IdentifierReference
    var arglist: MutableList<IExpression>
}


interface INameScope {
    val name: String
    val position: Position
    var statements: MutableList<IStatement>
    val parent: Node

    fun linkParents(parent: Node)

    fun subScopes(): Map<String, INameScope> {
        val subscopes = mutableMapOf<String, INameScope>()
        for(stmt in statements) {
            when(stmt) {
                is INameScope -> subscopes[stmt.name] = stmt
                is ForLoop -> subscopes[stmt.body.name] = stmt.body
                is RepeatLoop -> subscopes[stmt.body.name] = stmt.body
                is WhileLoop -> subscopes[stmt.body.name] = stmt.body
                is BranchStatement -> {
                    subscopes[stmt.truepart.name] = stmt.truepart
                    if(stmt.elsepart.isNotEmpty())
                        subscopes[stmt.elsepart.name] = stmt.elsepart
                }
                is IfStatement -> {
                    subscopes[stmt.truepart.name] = stmt.truepart
                    if(stmt.elsepart.isNotEmpty())
                        subscopes[stmt.elsepart.name] = stmt.elsepart
                }
            }
        }
        return subscopes
    }

    fun getLabelOrVariable(name: String): IStatement? {
        for (stmt in statements) {
            if (stmt is Label && stmt.name==name) return stmt
            if (stmt is VarDecl && stmt.name==name) return stmt
        }
        return null
    }

    fun allLabelsAndVariables() = statements.asSequence().filter { it is Label || it is VarDecl }
            .associate {((it as? Label)?.name ?: (it as? VarDecl)?.name)!! to it }

    fun lookup(scopedName: List<String>, statement: Node) : IStatement? {
        if(scopedName.size>1) {
            // it's a qualified name, look it up from the namespace root
            var scope: INameScope? = this
            scopedName.dropLast(1).forEach {
                scope = scope?.subScopes()?.get(it)
                if(scope==null)
                    return null
            }
            val foundScope : INameScope = scope!!
            return foundScope.getLabelOrVariable(scopedName.last())
                    ?:
                    foundScope.subScopes()[scopedName.last()] as IStatement?
        } else {
            // unqualified name, find the scope the statement is in, look in that first
            var statementScope = statement
            while(statementScope !is ParentSentinel) {
                val localScope = statementScope.definingScope()
                val result = localScope.getLabelOrVariable(scopedName[0])
                if (result != null)
                    return result
                val subscope = localScope.subScopes()[scopedName[0]] as IStatement?
                if (subscope != null)
                    return subscope
                // not found in this scope, look one higher up
                statementScope = statementScope.parent
            }
            return null
        }
    }

    fun debugPrint() {
        fun printNames(indent: Int, namespace: INameScope) {
            println(" ".repeat(4*indent) + "${namespace.name}   ->  ${namespace::class.simpleName} at ${namespace.position}")
            namespace.allLabelsAndVariables().forEach {
                println(" ".repeat(4 * (1 + indent)) + "${it.key}   ->  ${it.value::class.simpleName} at ${it.value.position}")
            }
            namespace.statements.filterIsInstance<INameScope>().forEach {
                printNames(indent+1, it)
            }
        }
        printNames(0, this)
    }

    fun isEmpty() = statements.isEmpty()
    fun isNotEmpty() = statements.isNotEmpty()

    fun remove(stmt: IStatement) {
        val removed = statements.remove(stmt)
        if(!removed)
            throw FatalAstException("stmt to remove wasn't found in scope")
    }
}

private object ParentSentinel : Node {
    override val position = Position("<<sentinel>>", 0, 0, 0)
    override var parent: Node = this
    override fun linkParents(parent: Node) {}
}

object BuiltinFunctionScopePlaceholder : INameScope {
    override val name = "<<builtin-functions-scope-placeholder>>"
    override val position = Position("<<placeholder>>", 0, 0, 0)
    override var statements = mutableListOf<IStatement>()
    override var parent: Node = ParentSentinel
    override fun linkParents(parent: Node) {}
}

class BuiltinFunctionStatementPlaceholder(val name: String, override val position: Position) : IStatement {
    override var parent: Node = ParentSentinel
    override fun linkParents(parent: Node) {}
    override fun process(processor: IAstProcessor): IStatement = this
    override fun definingScope(): INameScope = BuiltinFunctionScopePlaceholder
}

class Module(override val name: String,
             override var statements: MutableList<IStatement>,
             override val position: Position) : Node, INameScope {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent=parent
    }

    var loadAddress: Int = 0        // can be set with the %address directive

    fun linkParents() {
        parent = ParentSentinel
        statements.forEach {it.linkParents(this)}
    }

    fun process(processor: IAstProcessor) {
        processor.process(this)
    }

    override fun definingScope(): INameScope = GlobalNamespace("<<<global>>>", statements, position)
}


private class GlobalNamespace(override val name: String,
                              override var statements: MutableList<IStatement>,
                              override val position: Position) : INameScope {
    override var parent = ParentSentinel
    override fun linkParents(parent: Node) {}

    override fun lookup(scopedName: List<String>, statement: Node): IStatement? {
        if(scopedName.size==1 && scopedName[0] in BuiltinFunctions) {
            // builtin functions always exist, return a dummy statement for them
            val builtinPlaceholder = Label("builtin::${scopedName.last()}", statement.position)
            builtinPlaceholder.parent = ParentSentinel
            return builtinPlaceholder
        }
        val stmt = super.lookup(scopedName, statement)
        return when (stmt) {
            is Label, is VarDecl, is Block, is Subroutine -> stmt
            null -> null
            else -> throw NameError("wrong identifier target: $stmt", stmt.position)
        }
    }
}


class Block(override val name: String,
            val address: Int?,
            override var statements: MutableList<IStatement>,
            override val position: Position) : IStatement, INameScope {
    override lateinit var parent: Node
    val scopedname: String by lazy { makeScopedName(name).joinToString(".") }


    override fun linkParents(parent: Node) {
        this.parent = parent
        statements.forEach {it.linkParents(this)}
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        return "Block(name=$name, address=$address, ${statements.size} statements)"
    }

    fun options() = statements.filter { it is Directive && it.directive == "%option" }.flatMap { (it as Directive).args }.map {it.name!!}.toSet()
}


data class Directive(val directive: String, val args: List<DirectiveArg>, override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        args.forEach{it.linkParents(this)}
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}


data class DirectiveArg(val str: String?, val name: String?, val int: Int?, override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
}


data class Label(val name: String, override val position: Position) : IStatement {
    override lateinit var parent: Node
    val scopedname: String by lazy { makeScopedName(name).joinToString(".") }

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        return "Label(name=$name, pos=$position)"
    }
}


open class Return(var values: List<IExpression>, override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        values.forEach {it.linkParents(this)}
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        return "Return(values: $values, pos=$position)"
    }

    fun definingSubroutine(): Subroutine? {
        var scope = definingScope()
        while(scope !is GlobalNamespace) {
            if(scope is Subroutine)
                return scope
            val parent = scope.parent
            if(parent is Subroutine)
                return parent
            scope = parent.definingScope()
        }
        return null
    }
}


class ReturnFromIrq(override val position: Position) : Return(emptyList(), position) {
    override fun process(processor: IAstProcessor) = this

    override fun toString(): String {
        return "ReturnFromIrq(pos=$position)"
    }
}


class Continue(override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent=parent
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}

class Break(override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent=parent
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}


class ArraySpec(var x: IExpression, override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        x.linkParents(this)
    }

    companion object {
        fun forArray(v: LiteralValue, heap: HeapValues): ArraySpec {
            val arraySize = v.arrayvalue?.size ?: heap.get(v.heapId!!).arraysize
            return ArraySpec(LiteralValue.optimalNumeric(arraySize, v.position), v.position)
        }
    }

    fun process(processor: IAstProcessor) {
        x = x.process(processor)
    }

    override fun toString(): String {
        return("ArraySpec($x, pos=$position)")
    }

    fun size() = (x as? LiteralValue)?.asIntegerValue
}


enum class VarDeclType {
    VAR,
    CONST,
    MEMORY
}

class VarDecl(val type: VarDeclType,
              private val declaredDatatype: DataType,
              val arrayspec: ArraySpec?,
              val name: String,
              var value: IExpression?,
              override val position: Position) : IStatement {
    override lateinit var parent: Node

    val datatypeErrors = mutableListOf<SyntaxError>()       // don't crash at init time, report them in the AstChecker
    val datatype =
            if (arrayspec == null) declaredDatatype
            else when (declaredDatatype) {
                DataType.UBYTE -> DataType.ARRAY_UB
                DataType.BYTE -> DataType.ARRAY_B
                DataType.UWORD -> DataType.ARRAY_UW
                DataType.WORD -> DataType.ARRAY_W
                DataType.FLOAT -> DataType.ARRAY_F
                else -> {
                    datatypeErrors.add(SyntaxError("array can only contain bytes/words/floats", position))
                    DataType.UBYTE
                }
            }

    override fun linkParents(parent: Node) {
        this.parent = parent
        arrayspec?.linkParents(this)
        value?.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    val scopedname: String by lazy { makeScopedName(name).joinToString(".") }

    override fun toString(): String {
        return "VarDecl(name=$name, vartype=$type, datatype=$datatype, value=$value, pos=$position)"
    }

    fun asDefaultValueDecl(): VarDecl {
        val constValue = when(declaredDatatype) {
            DataType.UBYTE -> LiteralValue(DataType.UBYTE, 0, position=position)
            DataType.BYTE -> LiteralValue(DataType.BYTE, 0, position=position)
            DataType.UWORD -> LiteralValue(DataType.UWORD, wordvalue=0, position=position)
            DataType.WORD -> LiteralValue(DataType.WORD, wordvalue=0, position=position)
            DataType.FLOAT -> LiteralValue(DataType.FLOAT, floatvalue=0.0, position=position)
            else -> throw FatalAstException("can only set a default value for a numeric type")
        }
        return VarDecl(type, declaredDatatype, arrayspec, name, constValue, position)
    }
}


open class Assignment(var targets: List<AssignTarget>, val aug_op : String?, var value: IExpression, override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        targets.forEach { it.linkParents(this) }
        value.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        return("Assignment(augop: $aug_op, targets: $targets, value: $value, pos=$position)")
    }

    val singleTarget: AssignTarget?
        get() {
            return targets.singleOrNull()  // common case
        }
}

// This is a special class so the compiler can see if the assignments are for initializing the vars in the scope,
// or just a regular assignment. It may optimize the initialization step from this.
class VariableInitializationAssignment(target: AssignTarget, aug_op: String?, value: IExpression, position: Position)
    : Assignment(listOf(target), aug_op, value, position)


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

    fun process(processor: IAstProcessor) = processor.process(this)

    companion object {
        fun fromExpr(expr: IExpression): AssignTarget {
            return when (expr) {
                is RegisterExpr -> AssignTarget(expr.register, null, null, null, expr.position)
                is IdentifierReference -> AssignTarget(null, expr, null, null, expr.position)
                is ArrayIndexedExpression -> AssignTarget(null, null, expr, null, expr.position)
                is DirectMemoryRead -> AssignTarget(null, null, null, DirectMemoryWrite(expr.addressExpression, expr.position), expr.position)
                is DirectMemoryWrite -> AssignTarget(null, null, null, expr, expr.position)
                else -> throw FatalAstException("invalid expression object $expr")
            }
        }
    }

    fun determineDatatype(namespace: INameScope, heap: HeapValues, stmt: IStatement): DataType? {
        if(register!=null)
            return DataType.UBYTE

        if(identifier!=null) {
            val symbol = namespace.lookup(identifier.nameInSource, stmt) ?: return null
            if (symbol is VarDecl) return symbol.datatype
        }

        if(arrayindexed!=null) {
            val dt = arrayindexed.resultingDatatype(namespace, heap)
            if(dt!=null)
                return dt
        }

        if(memoryAddress!=null)
            return DataType.UBYTE

        return null
    }

    fun shortString(): String {
        if(register!=null)
            return register.toString()
        if(identifier!=null)
            return identifier.nameInSource.last()
        if(arrayindexed!=null)
            return arrayindexed.identifier!!.nameInSource.last()
        val address = memoryAddress?.addressExpression
        if(address is LiteralValue)
            return address.asIntegerValue.toString()
        return "???"
    }
}


interface IExpression: Node {
    fun isIterable(namespace: INameScope, heap: HeapValues): Boolean
    fun constValue(namespace: INameScope, heap: HeapValues): LiteralValue?
    fun process(processor: IAstProcessor): IExpression
    fun referencesIdentifier(name: String): Boolean
    fun resultingDatatype(namespace: INameScope, heap: HeapValues): DataType?
}


// note: some expression elements are mutable, to be able to rewrite/process the expression tree

class PrefixExpression(val operator: String, var expression: IExpression, override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        expression.linkParents(this)
    }

    override fun constValue(namespace: INameScope, heap: HeapValues): LiteralValue? = null
    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String) = expression.referencesIdentifier(name)
    override fun resultingDatatype(namespace: INameScope, heap: HeapValues): DataType? = expression.resultingDatatype(namespace, heap)
    override fun isIterable(namespace: INameScope, heap: HeapValues) = false

    override fun toString(): String {
        return "Prefix($operator $expression)"
    }
}


class BinaryExpression(var left: IExpression, var operator: String, var right: IExpression, override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        left.linkParents(this)
        right.linkParents(this)
    }

    override fun toString(): String {
        return "[$left $operator $right]"
    }

    // binary expression should actually have been optimized away into a single value, before const value was requested...
    override fun constValue(namespace: INameScope, heap: HeapValues): LiteralValue? = null
    override fun isIterable(namespace: INameScope, heap: HeapValues) = false
    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String) = left.referencesIdentifier(name) || right.referencesIdentifier(name)
    override fun resultingDatatype(namespace: INameScope, heap: HeapValues): DataType? {
        val leftDt = left.resultingDatatype(namespace, heap)
        val rightDt = right.resultingDatatype(namespace, heap)
        return when(operator) {
            "+", "-", "*", "**", "%" -> if(leftDt==null || rightDt==null) null else {
                try {
                    arithmeticOpDt(leftDt, rightDt)
                } catch(x: FatalAstException) {
                    null
                }
            }
            "//" -> if(leftDt==null || rightDt==null) null else integerDivisionOpDt(leftDt, rightDt)
            "&" -> leftDt
            "|" -> leftDt
            "^" -> leftDt
            "and", "or", "xor",
            "<", ">",
            "<=", ">=",
            "==", "!=" -> DataType.UBYTE
            "/" -> {
                val rightNum = right.constValue(namespace, heap)?.asNumericValue?.toDouble()
                if(rightNum!=null) {
                    when(leftDt) {
                        DataType.UBYTE ->
                            when(rightDt) {
                                DataType.UBYTE -> DataType.UBYTE
                                DataType.BYTE -> DataType.BYTE
                                DataType.UWORD -> if(rightNum >= 256) DataType.UBYTE else DataType.UWORD
                                DataType.WORD -> {
                                    if(rightNum < 0)
                                        if(rightNum<-256) DataType.UBYTE else DataType.WORD
                                    else
                                        if(rightNum>256) DataType.UBYTE else DataType.UWORD
                                }
                                DataType.FLOAT -> if(rightNum <= -256 || rightNum >= 256) DataType.UBYTE else DataType.FLOAT
                                else -> throw FatalAstException("invalid rightDt $rightDt")
                            }
                        DataType.BYTE ->
                            when(rightDt) {
                                DataType.UBYTE, DataType.BYTE -> DataType.BYTE
                                DataType.UWORD, DataType.WORD -> if(rightNum <= -256 || rightNum >= 256) DataType.BYTE else DataType.WORD
                                DataType.FLOAT -> if(rightNum <= -256 || rightNum >= 256) DataType.BYTE else DataType.FLOAT
                                else -> throw FatalAstException("invalid rightDt $rightDt")
                            }
                        DataType.UWORD ->
                            when(rightDt) {
                                DataType.UBYTE, DataType.UWORD -> DataType.UWORD
                                DataType.BYTE, DataType.WORD -> DataType.WORD
                                DataType.FLOAT -> if(rightNum <= -65536 || rightNum >= 65536) DataType.UWORD else DataType.FLOAT
                                else -> throw FatalAstException("invalid rightDt $rightDt")
                            }
                        DataType.WORD ->
                            when(rightDt) {
                                DataType.UBYTE, DataType.UWORD, DataType.BYTE, DataType.WORD -> DataType.WORD
                                DataType.FLOAT -> if(rightNum <= -65536 || rightNum >= 65536) DataType.WORD else DataType.FLOAT
                                else -> throw FatalAstException("invalid rightDt $rightDt")
                            }
                        DataType.FLOAT -> DataType.FLOAT
                        null -> DataType.FLOAT
                        else -> throw FatalAstException("invalid leftDt $leftDt")
                    }
                } else if(leftDt==null || rightDt==null) null else arithmeticOpDt(leftDt, rightDt)
            }
            else -> throw FatalAstException("resulting datatype check for invalid operator $operator")
        }
    }

    private fun integerDivisionOpDt(leftDt: DataType, rightDt: DataType): DataType {
        return when(leftDt) {
            DataType.UBYTE -> when(rightDt) {
                DataType.UBYTE, DataType.UWORD -> DataType.UBYTE
                DataType.BYTE, DataType.WORD, DataType.FLOAT -> DataType.BYTE
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            DataType.BYTE -> when(rightDt) {
                in NumericDatatypes -> DataType.BYTE
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            DataType.UWORD -> when(rightDt) {
                DataType.UBYTE, DataType.UWORD -> DataType.UWORD
                DataType.BYTE, DataType.WORD, DataType.FLOAT -> DataType.WORD
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            DataType.WORD -> when(rightDt) {
                in NumericDatatypes -> DataType.WORD
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            DataType.FLOAT -> when(rightDt) {
                in NumericDatatypes -> DataType.FLOAT
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
        }
    }

    private fun arithmeticOpDt(leftDt: DataType, rightDt: DataType): DataType {
        return when(leftDt) {
            DataType.UBYTE -> when(rightDt) {
                DataType.UBYTE -> DataType.UBYTE
                DataType.BYTE -> DataType.BYTE
                DataType.UWORD -> DataType.UWORD
                DataType.WORD -> DataType.WORD
                DataType.FLOAT -> DataType.FLOAT
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            DataType.BYTE -> when(rightDt) {
                DataType.BYTE, DataType.UBYTE -> DataType.BYTE
                DataType.WORD, DataType.UWORD -> DataType.WORD
                DataType.FLOAT -> DataType.FLOAT
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            DataType.UWORD -> when(rightDt) {
                DataType.UBYTE, DataType.UWORD -> DataType.UWORD
                DataType.BYTE, DataType.WORD -> DataType.WORD
                DataType.FLOAT -> DataType.FLOAT
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            DataType.WORD -> when(rightDt) {
                DataType.BYTE, DataType.UBYTE, DataType.WORD, DataType.UWORD -> DataType.WORD
                DataType.FLOAT -> DataType.FLOAT
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            DataType.FLOAT -> when(rightDt) {
                in NumericDatatypes -> DataType.FLOAT
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
        }
    }
}

class ArrayIndexedExpression(val identifier: IdentifierReference?,
                             var arrayspec: ArraySpec,
                             override val position: Position) : IExpression {
    override lateinit var parent: Node
    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
        arrayspec.linkParents(this)
    }

    override fun isIterable(namespace: INameScope, heap: HeapValues) = false
    override fun constValue(namespace: INameScope, heap: HeapValues): LiteralValue? = null
    override fun process(processor: IAstProcessor): IExpression = processor.process(this)
    override fun referencesIdentifier(name: String) = identifier?.referencesIdentifier(name) ?: false

    override fun resultingDatatype(namespace: INameScope, heap: HeapValues): DataType? {
        val target = identifier?.targetStatement(namespace)
        if (target is VarDecl) {
            return when (target.datatype) {
                in NumericDatatypes -> null
                in StringDatatypes -> DataType.UBYTE
                DataType.ARRAY_UB -> DataType.UBYTE
                DataType.ARRAY_B -> DataType.BYTE
                DataType.ARRAY_UW -> DataType.UWORD
                DataType.ARRAY_W -> DataType.WORD
                DataType.ARRAY_F -> DataType.FLOAT
                else -> throw FatalAstException("invalid dt")
            }
        }
        return null
    }

    override fun toString(): String {
        return "ArrayIndexed(ident=$identifier, arrayspec=$arrayspec; pos=$position)"
    }
}


class TypecastExpression(var expression: IExpression, var type: DataType, override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        expression.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String) = expression.referencesIdentifier(name)
    override fun resultingDatatype(namespace: INameScope, heap: HeapValues): DataType? = type
    override fun isIterable(namespace: INameScope, heap: HeapValues) = type in IterableDatatypes
    override fun constValue(namespace: INameScope, heap: HeapValues): LiteralValue? {
        val cv = expression.constValue(namespace, heap) ?: return null
        val value = Value(cv.type, cv.asNumericValue!!).cast(type)
        return LiteralValue.fromNumber(value.numericValue(), value.type, position)
    }

    override fun toString(): String {
        return "Typecast($expression as $type)"
    }
}


class DirectMemoryRead(var addressExpression: IExpression, override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        this.addressExpression.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String) = false
    override fun resultingDatatype(namespace: INameScope, heap: HeapValues): DataType? = DataType.UBYTE
    override fun isIterable(namespace: INameScope, heap: HeapValues) = false
    override fun constValue(namespace: INameScope, heap: HeapValues) = null

    override fun toString(): String {
        return "DirectMemoryRead($addressExpression)"
    }
}


class DirectMemoryWrite(var addressExpression: IExpression, override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        this.addressExpression.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String) = false
    override fun resultingDatatype(namespace: INameScope, heap: HeapValues): DataType? = DataType.UBYTE
    override fun isIterable(namespace: INameScope, heap: HeapValues) = false
    override fun constValue(namespace: INameScope, heap: HeapValues) = null

    override fun toString(): String {
        return "DirectMemoryWrite($addressExpression)"
    }
}


private data class NumericLiteral(val number: Number, val datatype: DataType)


class LiteralValue(val type: DataType,
                   val bytevalue: Short? = null,
                   val wordvalue: Int? = null,
                   val floatvalue: Double? = null,
                   strvalue: String? = null,
                   val arrayvalue: Array<IExpression>? = null,
                   val heapId: Int? =null,
                   override val position: Position) : IExpression {
    override lateinit var parent: Node
    private val initialstrvalue = strvalue

    override fun referencesIdentifier(name: String) = arrayvalue?.any { it.referencesIdentifier(name) } ?: false

    val isString = type in StringDatatypes
    val isNumeric = type in NumericDatatypes
    val isArray = type in ArrayDatatypes

    companion object {
        fun fromBoolean(bool: Boolean, position: Position) =
                LiteralValue(DataType.UBYTE, bytevalue = if(bool) 1 else 0, position=position)

        fun fromNumber(value: Number, type: DataType, position: Position) : LiteralValue {
            return when(type) {
                DataType.UBYTE, DataType.BYTE -> LiteralValue(type, bytevalue = value.toShort(), position = position)
                DataType.UWORD, DataType.WORD -> LiteralValue(type, wordvalue = value.toInt(), position = position)
                DataType.FLOAT -> LiteralValue(type, floatvalue = value.toDouble(), position = position)
                else -> throw FatalAstException("non numeric datatype")
            }
        }

        fun optimalNumeric(value: Number, position: Position): LiteralValue {
            return if(value is Double) {
                LiteralValue(DataType.FLOAT, floatvalue = value, position = position)
            } else {
                val intval = value.toInt()
                when (intval) {
                    in 0..255 -> LiteralValue(DataType.UBYTE, bytevalue=intval.toShort(), position = position)
                    in -128..127 -> LiteralValue(DataType.BYTE, bytevalue=intval.toShort(), position = position)
                    in 0..65535 -> LiteralValue(DataType.UWORD, wordvalue = intval, position = position)
                    in -32768..32767 -> LiteralValue(DataType.WORD, wordvalue = intval, position = position)
                    else -> LiteralValue(DataType.FLOAT, floatvalue = intval.toDouble(), position = position)
                }
            }
        }

        fun optimalInteger(value: Number, position: Position): LiteralValue {
            return when (value) {
                in 0..255 -> LiteralValue(DataType.UBYTE, bytevalue=value.toShort(), position = position)
                in -128..127 -> LiteralValue(DataType.BYTE, bytevalue=value.toShort(), position = position)
                in 0..65535 -> LiteralValue(DataType.UWORD, wordvalue = value.toInt(), position = position)
                else -> throw FatalAstException("integer overflow: $value")
            }
        }
    }

    init {
        when(type){
            DataType.UBYTE, DataType.BYTE -> if(bytevalue==null) throw FatalAstException("literal value missing bytevalue")
            DataType.UWORD, DataType.WORD -> if(wordvalue==null) throw FatalAstException("literal value missing wordvalue")
            DataType.FLOAT -> if(floatvalue==null) throw FatalAstException("literal value missing floatvalue")
            in StringDatatypes ->
                if(initialstrvalue==null && heapId==null) throw FatalAstException("literal value missing strvalue/heapId")
            in ArrayDatatypes ->
                if(arrayvalue==null && heapId==null) throw FatalAstException("literal value missing arrayvalue/heapId")
            else -> throw FatalAstException("invalid type $type")
        }
        if(bytevalue==null && wordvalue==null && floatvalue==null && arrayvalue==null && initialstrvalue==null && heapId==null)
            throw FatalAstException("literal value without actual value")
    }

    val asNumericValue: Number? = when {
        bytevalue!=null -> bytevalue
        wordvalue!=null -> wordvalue
        floatvalue!=null -> floatvalue
        else -> null
    }

    val asIntegerValue: Int? = when {
        bytevalue!=null -> bytevalue.toInt()
        wordvalue!=null -> wordvalue
        // don't round a float value, otherwise code will not detect that it's not an integer
        else -> null
    }

    val asBooleanValue: Boolean =
            (floatvalue!=null && floatvalue != 0.0) ||
            (bytevalue!=null && bytevalue != 0.toShort()) ||
            (wordvalue!=null && wordvalue != 0) ||
            (initialstrvalue!=null && initialstrvalue.isNotEmpty()) ||
            (arrayvalue != null && arrayvalue.isNotEmpty())

    override fun linkParents(parent: Node) {
        this.parent = parent
        arrayvalue?.forEach {it.linkParents(this)}
    }

    override fun constValue(namespace: INameScope, heap: HeapValues): LiteralValue?  = this
    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        val vstr = when(type) {
            DataType.UBYTE -> "ubyte:$bytevalue"
            DataType.BYTE -> "byte:$bytevalue"
            DataType.UWORD -> "uword:$wordvalue"
            DataType.WORD -> "word:$wordvalue"
            DataType.FLOAT -> "float:$floatvalue"
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS-> {
                if(heapId!=null) "str:#$heapId"
                else "str:$initialstrvalue"
            }
            DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W, DataType.ARRAY_F -> {
                if(heapId!=null) "arrayspec:#$heapId"
                else "arrayspec:$arrayvalue"
            }
        }
        return "LiteralValue($vstr)"
    }

    override fun resultingDatatype(namespace: INameScope, heap: HeapValues) = type

    override fun isIterable(namespace: INameScope, heap: HeapValues): Boolean = type in IterableDatatypes

    override fun hashCode(): Int {
        val bh = bytevalue?.hashCode() ?: 0x10001234
        val wh = wordvalue?.hashCode() ?: 0x01002345
        val fh = floatvalue?.hashCode() ?: 0x00103456
        val sh = initialstrvalue?.hashCode() ?: 0x00014567
        val ah = arrayvalue?.hashCode() ?: 0x11119876
        var hash = bh * 31 xor wh
        hash = hash*31 xor fh
        hash = hash*31 xor sh
        hash = hash*31 xor ah
        hash = hash*31 xor type.hashCode()
        return hash
    }

    override fun equals(other: Any?): Boolean {
        if(other==null || other !is LiteralValue)
            return false
        if(isNumeric && other.isNumeric)
            return asNumericValue?.toDouble()==other.asNumericValue?.toDouble()
        if(isArray && other.isArray)
            return arrayvalue!!.contentEquals(other.arrayvalue!!) && heapId==other.heapId
        if(isString && other.isString)
            return initialstrvalue==other.initialstrvalue && heapId==other.heapId

        if(type!=other.type)
            return false

        return compareTo(other) == 0
    }

    operator fun compareTo(other: LiteralValue): Int {
        val numLeft = asNumericValue?.toDouble()
        val numRight = other.asNumericValue?.toDouble()
        if(numLeft!=null && numRight!=null)
            return numLeft.compareTo(numRight)

        if(initialstrvalue!=null && other.initialstrvalue!=null)
            return initialstrvalue.compareTo(other.initialstrvalue)

        throw ExpressionError("cannot order compare type $type with ${other.type}", other.position)
    }

    fun intoDatatype(targettype: DataType): LiteralValue? {
        if(type==targettype)
            return this
        when(type) {
            DataType.UBYTE -> {
                if(targettype==DataType.BYTE && bytevalue!! <= 127)
                    return LiteralValue(targettype, bytevalue=bytevalue, position=position)
                if(targettype==DataType.WORD || targettype==DataType.UWORD)
                    return LiteralValue(targettype, wordvalue=bytevalue!!.toInt(), position=position)
                if(targettype==DataType.FLOAT)
                    return LiteralValue(targettype, floatvalue=bytevalue!!.toDouble(), position=position)
            }
            DataType.BYTE -> {
                if(targettype==DataType.UBYTE && bytevalue!! >= 0)
                    return LiteralValue(targettype, bytevalue=bytevalue, position=position)
                if(targettype==DataType.UWORD && bytevalue!! >= 0)
                    return LiteralValue(targettype, wordvalue=bytevalue.toInt(), position=position)
                if(targettype==DataType.WORD)
                    return LiteralValue(targettype, wordvalue=bytevalue!!.toInt(), position=position)
                if(targettype==DataType.FLOAT)
                    return LiteralValue(targettype, floatvalue=bytevalue!!.toDouble(), position=position)
            }
            DataType.UWORD -> {
                if(targettype==DataType.BYTE && wordvalue!! <= 127)
                    return LiteralValue(targettype, bytevalue=wordvalue.toShort(), position=position)
                if(targettype==DataType.UBYTE && wordvalue!! <= 255)
                    return LiteralValue(targettype, bytevalue=wordvalue.toShort(), position=position)
                if(targettype==DataType.WORD && wordvalue!! <= 32767)
                    return LiteralValue(targettype, wordvalue=wordvalue, position=position)
                if(targettype==DataType.FLOAT)
                    return LiteralValue(targettype, floatvalue=wordvalue!!.toDouble(), position=position)
            }
            DataType.WORD -> {
                if(targettype==DataType.BYTE && wordvalue!! in -128..127)
                    return LiteralValue(targettype, bytevalue=wordvalue.toShort(), position=position)
                if(targettype==DataType.UBYTE && wordvalue!! in 0..255)
                    return LiteralValue(targettype, bytevalue=wordvalue.toShort(), position=position)
                if(targettype==DataType.UWORD && wordvalue!! >=0)
                    return LiteralValue(targettype, wordvalue=wordvalue, position=position)
                if(targettype==DataType.FLOAT)
                    return LiteralValue(targettype, floatvalue=wordvalue!!.toDouble(), position=position)
            }
            DataType.FLOAT -> {
                if(floor(floatvalue!!)==floatvalue) {
                    val value = floatvalue.toInt()
                    if (targettype == DataType.BYTE && value in -128..127)
                        return LiteralValue(targettype, bytevalue = value.toShort(), position = position)
                    if (targettype == DataType.UBYTE && value in 0..255)
                        return LiteralValue(targettype, bytevalue = value.toShort(), position = position)
                    if (targettype == DataType.WORD && value in -32768..32767)
                        return LiteralValue(targettype, wordvalue = value, position = position)
                    if (targettype == DataType.UWORD && value in 0..65535)
                        return LiteralValue(targettype, wordvalue = value, position = position)
                }
            }
            DataType.STR, DataType.STR_P,  DataType.STR_S, DataType.STR_PS -> {
                if(targettype in StringDatatypes)
                    return this
            }
            else -> {}
        }
        return null    // invalid type conversion from $this to $targettype
    }

    fun strvalue(heap: HeapValues): String {
        if(initialstrvalue!=null)
            return initialstrvalue
        return heap.get(heapId!!).str!!
    }
}


class RangeExpr(var from: IExpression,
                var to: IExpression,
                var step: IExpression,
                override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        from.linkParents(this)
        to.linkParents(this)
        step.linkParents(this)
    }

    override fun constValue(namespace: INameScope, heap: HeapValues): LiteralValue? = null
    override fun isIterable(namespace: INameScope, heap: HeapValues) = true
    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String): Boolean  = from.referencesIdentifier(name) || to.referencesIdentifier(name)
    override fun resultingDatatype(namespace: INameScope, heap: HeapValues): DataType? {
        val fromDt=from.resultingDatatype(namespace, heap)
        val toDt=to.resultingDatatype(namespace, heap)
        return when {
            fromDt==null || toDt==null -> null
            fromDt==DataType.UBYTE && toDt==DataType.UBYTE -> DataType.UBYTE
            fromDt==DataType.UWORD && toDt==DataType.UWORD -> DataType.UWORD
            fromDt==DataType.STR && toDt==DataType.STR -> DataType.STR
            fromDt==DataType.STR_P && toDt==DataType.STR_P -> DataType.STR_P
            fromDt==DataType.STR_S && toDt==DataType.STR_S -> DataType.STR_S
            fromDt==DataType.STR_PS && toDt==DataType.STR_PS -> DataType.STR_PS
            fromDt==DataType.WORD || toDt==DataType.WORD -> DataType.WORD
            fromDt==DataType.BYTE || toDt==DataType.BYTE -> DataType.UBYTE
            else -> DataType.UBYTE
        }
    }
    override fun toString(): String {
        return "RangeExpr(from $from, to $to, step $step, pos=$position)"
    }

    fun size(heap: HeapValues): Int? {
        val fromLv = (from as? LiteralValue)
        val toLv = (to as? LiteralValue)
        if(fromLv==null || toLv==null)
            return null
        return toConstantIntegerRange(heap)?.count()
    }

    fun toConstantIntegerRange(heap: HeapValues): IntProgression? {
        val fromLv = from as? LiteralValue
        val toLv = to as? LiteralValue
        if(fromLv==null || toLv==null)
            return null         // non-constant range
        val fromVal: Int
        val toVal: Int
        if(fromLv.isString && toLv.isString) {
            // string range -> int range over petscii values
            fromVal = Petscii.encodePetscii(fromLv.strvalue(heap), true)[0].toInt()
            toVal = Petscii.encodePetscii(toLv.strvalue(heap), true)[0].toInt()
        } else {
            // integer range
            fromVal = (from as LiteralValue).asIntegerValue!!
            toVal = (to as LiteralValue).asIntegerValue!!
        }
        val stepVal = (step as? LiteralValue)?.asIntegerValue ?: 1
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
}


class RegisterExpr(val register: Register, override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun constValue(namespace: INameScope, heap: HeapValues): LiteralValue? = null
    override fun process(processor: IAstProcessor) = this
    override fun referencesIdentifier(name: String): Boolean  = false
    override fun isIterable(namespace: INameScope, heap: HeapValues) = false
    override fun toString(): String {
        return "RegisterExpr(register=$register, pos=$position)"
    }

    override fun resultingDatatype(namespace: INameScope, heap: HeapValues) = DataType.UBYTE
}


data class IdentifierReference(val nameInSource: List<String>, override val position: Position) : IExpression {
    override lateinit var parent: Node

    fun targetStatement(namespace: INameScope) =
        if(nameInSource.size==1 && nameInSource[0] in BuiltinFunctions)
            BuiltinFunctionStatementPlaceholder(nameInSource[0], position)
        else
            namespace.lookup(nameInSource, this)

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun constValue(namespace: INameScope, heap: HeapValues): LiteralValue? {
        val node = namespace.lookup(nameInSource, this)
                ?: throw UndefinedSymbolError(this)
        val vardecl = node as? VarDecl
        if(vardecl==null) {
            throw ExpressionError("name must be a constant, instead of: ${node::class.simpleName}", position)
        } else if(vardecl.type!=VarDeclType.CONST) {
            return null
        }
        return vardecl.value?.constValue(namespace, heap)
    }

    override fun toString(): String {
        return "IdentifierRef($nameInSource)"
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String): Boolean = nameInSource.last() == name   // @todo is this correct all the time?

    override fun resultingDatatype(namespace: INameScope, heap: HeapValues): DataType? {
        val targetStmt = targetStatement(namespace)
        if(targetStmt is VarDecl) {
            return targetStmt.datatype
        } else {
            throw FatalAstException("cannot get datatype from identifier reference ${this}, pos=$position")
        }
    }

    override fun isIterable(namespace: INameScope, heap: HeapValues): Boolean  = resultingDatatype(namespace, heap) in IterableDatatypes
}


class PostIncrDecr(var target: AssignTarget, val operator: String, override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        return "PostIncrDecr(op: $operator, target: $target, pos=$position)"
    }
}


class Jump(val address: Int?,
           val identifier: IdentifierReference?,
           val generatedLabel: String?,             // used in code generation scenarios
           override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        return "Jump(addr: $address, identifier: $identifier, label: $generatedLabel;  pos=$position)"
    }
}


class FunctionCall(override var target: IdentifierReference,
                   override var arglist: MutableList<IExpression>,
                   override val position: Position) : IExpression, IFunctionCall {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        arglist.forEach { it.linkParents(this) }
    }

    override fun constValue(namespace: INameScope, heap: HeapValues) = constValue(namespace, heap, true)

    private fun constValue(namespace: INameScope, heap: HeapValues, withDatatypeCheck: Boolean): LiteralValue? {
        // if the function is a built-in function and the args are consts, should try to const-evaluate!
        // lenghts of arrays and strings are constants that are determined at compile time!
        if(target.nameInSource.size>1) return null
        try {
            var resultValue: LiteralValue? = null
            val func = BuiltinFunctions[target.nameInSource[0]]
            if(func!=null) {
                val exprfunc = func.constExpressionFunc
                if(exprfunc!=null)
                    resultValue = exprfunc(arglist, position, namespace, heap)
                else if(func.returntype==null)
                    throw ExpressionError("builtin function ${target.nameInSource[0]} can't be used here because it doesn't return a value", position)
            }

            if(withDatatypeCheck) {
                val resultDt = this.resultingDatatype(namespace, heap)
                if(resultValue==null || resultDt == resultValue.type)
                    return resultValue
                throw FatalAstException("evaluated const expression result value doesn't match expected datatype $resultDt, pos=$position")
            } else {
                return resultValue
            }
        }
        catch(x: NotConstArgumentException) {
            // const-evaluating the builtin function call failed.
            return null
        }
    }

    override fun toString(): String {
        return "FunctionCall(target=$target, pos=$position)"
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String): Boolean = target.referencesIdentifier(name) || arglist.any{it.referencesIdentifier(name)}

    override fun resultingDatatype(namespace: INameScope, heap: HeapValues): DataType? {
        val constVal = constValue(namespace, heap,false)
        if(constVal!=null)
            return constVal.type
        val stmt = target.targetStatement(namespace) ?: return null
        when (stmt) {
            is BuiltinFunctionStatementPlaceholder -> {
                if(target.nameInSource[0] == "set_carry" || target.nameInSource[0]=="set_irqd" ||
                        target.nameInSource[0] == "clear_carry" || target.nameInSource[0]=="clear_irqd") {
                    return null // these have no return value
                }
                return builtinFunctionReturnType(target.nameInSource[0], this.arglist, namespace, heap)
            }
            is Subroutine -> {
                if(stmt.returntypes.isEmpty())
                    return null     // no return value
                if(stmt.returntypes.size==1)
                    return stmt.returntypes[0]
                return null     // has multiple return types... so not a single resulting datatype possible
            }
            is Label -> return null
        }
        return null     // calling something we don't recognise...
    }

    override fun isIterable(namespace: INameScope, heap: HeapValues) = resultingDatatype(namespace, heap) in IterableDatatypes
}


class FunctionCallStatement(override var target: IdentifierReference,
                            override var arglist: MutableList<IExpression>,
                            override val position: Position) : IStatement, IFunctionCall {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        arglist.forEach { it.linkParents(this) }
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        return "FunctionCall(target=$target, pos=$position)"
    }
}


class InlineAssembly(val assembly: String, override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun process(processor: IAstProcessor) = this
}


data class RegisterOrStatusflag(val registerOrPair: RegisterOrPair?, val statusflag: Statusflag?)

class AnonymousScope(override var statements: MutableList<IStatement>,
                     override val position: Position) : INameScope, IStatement {
    override val name: String
    override lateinit var parent: Node

    init {
        name = "<<<anonymous-$sequenceNumber>>>"
        sequenceNumber++
    }

    companion object {
        private var sequenceNumber = 1
    }

    override fun linkParents(parent: Node) {
        this.parent = parent
        statements.forEach { it.linkParents(this) }
    }
    override fun process(processor: IAstProcessor) = processor.process(this)
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
                 override var statements: MutableList<IStatement>,
                 override val position: Position) : IStatement, INameScope {
    override lateinit var parent: Node
    val scopedname: String by lazy { makeScopedName(name).joinToString(".") }

    override fun linkParents(parent: Node) {
        this.parent = parent
        parameters.forEach { it.linkParents(this) }
        statements.forEach { it.linkParents(this) }
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        return "Subroutine(name=$name, parameters=$parameters, returntypes=$returntypes, ${statements.size} statements, address=$asmAddress)"
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

class IfStatement(var condition: IExpression,
                  var truepart: AnonymousScope,
                  var elsepart: AnonymousScope,
                  override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        condition.linkParents(this)
        truepart.linkParents(this)
        elsepart.linkParents(this)
    }

    override fun process(processor: IAstProcessor): IStatement = processor.process(this)
}


class BranchStatement(var condition: BranchCondition,
                      var truepart: AnonymousScope,
                      var elsepart: AnonymousScope,
                      override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        truepart.linkParents(this)
        elsepart.linkParents(this)
    }

    override fun process(processor: IAstProcessor): IStatement = processor.process(this)
}


class ForLoop(val loopRegister: Register?,
              val decltype: DataType?,
              val loopVar: IdentifierReference?,
              var iterable: IExpression,
              var body: AnonymousScope,
              override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent=parent
        loopVar?.linkParents(if(decltype==null) this else body)
        iterable.linkParents(this)
        body.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        return "ForLoop(loopVar: $loopVar, loopReg: $loopRegister, iterable: $iterable, pos=$position)"
    }

    companion object {
        const val iteratorLoopcounterVarname = "prog8forloopcounter"
    }
}


class WhileLoop(var condition: IExpression,
                var body: AnonymousScope,
                override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        condition.linkParents(this)
        body.linkParents(this)
    }

    override fun process(processor: IAstProcessor): IStatement = processor.process(this)
}


class RepeatLoop(var body: AnonymousScope,
                 var untilCondition: IExpression,
                 override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        untilCondition.linkParents(this)
        body.linkParents(this)
    }

    override fun process(processor: IAstProcessor): IStatement = processor.process(this)
}


/***************** Antlr Extension methods to create AST ****************/

fun prog8Parser.ModuleContext.toAst(name: String) : Module =
        Module(name, modulestatement().asSequence().map { it.toAst() }.toMutableList(), toPosition())


private fun ParserRuleContext.toPosition() : Position {
    val file = Paths.get(this.start.inputStream.sourceName).fileName.toString()
    // note: be ware of TAB characters in the source text, they count as 1 column...
    return Position(file, start.line, start.charPositionInLine, stop.charPositionInLine+stop.text.length)
}


private fun prog8Parser.ModulestatementContext.toAst() : IStatement {
    val directive = directive()?.toAst()
    if(directive!=null) return directive

    val block = block()?.toAst()
    if(block!=null) return block

    throw FatalAstException(text)
}


private fun prog8Parser.BlockContext.toAst() : IStatement =
        Block(identifier().text, integerliteral()?.toAst()?.number?.toInt(), statement_block().toAst(), toPosition())


private fun prog8Parser.Statement_blockContext.toAst(): MutableList<IStatement> =
        statement().asSequence().map { it.toAst() }.toMutableList()


private fun prog8Parser.StatementContext.toAst() : IStatement {
    vardecl()?.let {
        return VarDecl(VarDeclType.VAR,
                it.datatype().toAst(),
                it.arrayspec()?.toAst(),
                it.identifier().text,
                null,
                it.toPosition())
    }

    varinitializer()?.let {
        return VarDecl(VarDeclType.VAR,
                it.datatype().toAst(),
                it.arrayspec()?.toAst(),
                it.identifier().text,
                it.expression().toAst(),
                it.toPosition())
    }

    constdecl()?.let {
        val cvarinit = it.varinitializer()
        return VarDecl(VarDeclType.CONST,
                cvarinit.datatype().toAst(),
                cvarinit.arrayspec()?.toAst(),
                cvarinit.identifier().text,
                cvarinit.expression().toAst(),
                cvarinit.toPosition())
    }

    memoryvardecl()?.let {
        val mvarinit = it.varinitializer()
        return VarDecl(VarDeclType.MEMORY,
                mvarinit.datatype().toAst(),
                mvarinit.arrayspec()?.toAst(),
                mvarinit.identifier().text,
                mvarinit.expression().toAst(),
                mvarinit.toPosition())
    }

    assignment()?.let {
        return Assignment(it.assign_targets().toAst(), null, it.expression().toAst(), it.toPosition())
    }

    augassignment()?.let {
        return Assignment(listOf(it.assign_target().toAst()),
                it.operator.text,
                it.expression().toAst(),
                it.toPosition())
    }

    postincrdecr()?.let {
        return PostIncrDecr(it.assign_target().toAst(), it.operator.text, it.toPosition())
    }

    val directive = directive()?.toAst()
    if(directive!=null) return directive

    val label = labeldef()?.toAst()
    if(label!=null) return label

    val jump = unconditionaljump()?.toAst()
    if(jump!=null) return jump

    val fcall = functioncall_stmt()?.toAst()
    if(fcall!=null) return fcall

    val ifstmt = if_stmt()?.toAst()
    if(ifstmt!=null) return ifstmt

    val returnstmt = returnstmt()?.toAst()
    if(returnstmt!=null) return returnstmt

    val sub = subroutine()?.toAst()
    if(sub!=null) return sub

    val asm = inlineasm()?.toAst()
    if(asm!=null) return asm

    val branchstmt = branch_stmt()?.toAst()
    if(branchstmt!=null) return branchstmt

    val forloop = forloop()?.toAst()
    if(forloop!=null) return forloop

    val repeatloop = repeatloop()?.toAst()
    if(repeatloop!=null) return repeatloop

    val whileloop = whileloop()?.toAst()
    if(whileloop!=null) return whileloop

    val breakstmt = breakstmt()?.toAst()
    if(breakstmt!=null) return breakstmt

    val continuestmt = continuestmt()?.toAst()
    if(continuestmt!=null) return continuestmt

    val asmsubstmt = asmsubroutine()?.toAst()
    if(asmsubstmt!=null) return asmsubstmt

    throw FatalAstException("unprocessed source text (are we missing ast conversion rules for parser elements?): $text")
}

private fun prog8Parser.Assign_targetsContext.toAst(): List<AssignTarget> = assign_target().map { it.toAst() }


private fun prog8Parser.AsmsubroutineContext.toAst(): IStatement {
    val name = identifier().text
    val address = asmsub_address()?.address?.toAst()?.number?.toInt()
    val params = asmsub_params()?.toAst() ?: emptyList()
    val returns = asmsub_returns()?.toAst() ?: emptyList()
    val normalParameters = params.map { SubroutineParameter(it.name, it.type, it.position) }
    val normalReturnvalues = returns.map { it.type }
    val paramRegisters = params.map { RegisterOrStatusflag(it.registerOrPair, it.statusflag) }
    val returnRegisters = returns.map { RegisterOrStatusflag(it.registerOrPair, it.statusflag) }
    val clobbers = clobber()?.toAst() ?: emptySet()
    val statements = statement_block()?.toAst() ?: mutableListOf()
    return Subroutine(name, normalParameters, normalReturnvalues,
            paramRegisters, returnRegisters, clobbers, address, true, statements, toPosition())
}

private class AsmSubroutineParameter(name: String,
                             type: DataType,
                             val registerOrPair: RegisterOrPair?,
                             val statusflag: Statusflag?,
                             position: Position) : SubroutineParameter(name, type, position)

private class AsmSubroutineReturn(val type: DataType,
                          val registerOrPair: RegisterOrPair?,
                          val statusflag: Statusflag?,
                          val position: Position)

private fun prog8Parser.ClobberContext.toAst(): Set<Register>
        = this.register().asSequence().map { it.toAst() }.toSet()


private fun prog8Parser.Asmsub_returnsContext.toAst(): List<AsmSubroutineReturn>
        = asmsub_return().map { AsmSubroutineReturn(it.datatype().toAst(), it.registerorpair()?.toAst(), it.statusregister()?.toAst(), toPosition()) }


private fun prog8Parser.Asmsub_paramsContext.toAst(): List<AsmSubroutineParameter>
        = asmsub_param().map { AsmSubroutineParameter(it.vardecl().identifier().text, it.vardecl().datatype().toAst(), it.registerorpair()?.toAst(), it.statusregister()?.toAst(), toPosition()) }


private fun prog8Parser.StatusregisterContext.toAst() = Statusflag.valueOf(text)


private fun prog8Parser.Functioncall_stmtContext.toAst(): IStatement {
    val location =
            if(identifier()!=null) identifier()?.toAst()
            else scoped_identifier()?.toAst()
    return if(expression_list() ==null)
        FunctionCallStatement(location!!, mutableListOf(), toPosition())
    else
        FunctionCallStatement(location!!, expression_list().toAst().toMutableList(), toPosition())
}


private fun prog8Parser.FunctioncallContext.toAst(): FunctionCall {
    val location =
            if(identifier()!=null) identifier()?.toAst()
            else scoped_identifier()?.toAst()
    return if(expression_list() ==null)
        FunctionCall(location!!, mutableListOf(), toPosition())
    else
        FunctionCall(location!!, expression_list().toAst().toMutableList(), toPosition())
}


private fun prog8Parser.InlineasmContext.toAst() =
        InlineAssembly(INLINEASMBLOCK().text, toPosition())


private fun prog8Parser.ReturnstmtContext.toAst() : Return {
    val values = expression_list()
    return Return(values?.toAst() ?: emptyList(), toPosition())
}

private fun prog8Parser.UnconditionaljumpContext.toAst(): Jump {
    val address = integerliteral()?.toAst()?.number?.toInt()
    val identifier = identifier()?.toAst() ?: scoped_identifier()?.toAst()
    return Jump(address, identifier, null, toPosition())
}


private fun prog8Parser.LabeldefContext.toAst(): IStatement =
        Label(children[0].text, toPosition())


private fun prog8Parser.SubroutineContext.toAst() : Subroutine {
    return Subroutine(identifier().text,
            sub_params()?.toAst() ?: emptyList(),
            sub_return_part()?.toAst() ?: emptyList(),
            emptyList(),
            emptyList(),
            emptySet(),
            null,
            false,
            statement_block()?.toAst() ?: mutableListOf(),
            toPosition())
}

private fun prog8Parser.Sub_return_partContext.toAst(): List<DataType> {
    val returns = sub_returns() ?: return emptyList()
    return returns.datatype().map { it.toAst() }
}


private fun prog8Parser.Sub_paramsContext.toAst(): List<SubroutineParameter> =
        vardecl().map {
            SubroutineParameter(it.identifier().text, it.datatype().toAst(), it.toPosition())
        }


private fun prog8Parser.Assign_targetContext.toAst() : AssignTarget {
    val register = register()?.toAst()
    val identifier = identifier()
    return when {
        register!=null -> AssignTarget(register, null, null, null, toPosition())
        identifier!=null -> AssignTarget(null, identifier.toAst(), null, null, toPosition())
        arrayindexed()!=null -> AssignTarget(null, null, arrayindexed().toAst(), null, toPosition())
        directmemory()!=null -> AssignTarget(null, null, null, DirectMemoryWrite(directmemory().expression().toAst(), toPosition()), toPosition())
        else -> AssignTarget(null, scoped_identifier()?.toAst(), null, null, toPosition())
    }
}

private fun prog8Parser.RegisterContext.toAst() = Register.valueOf(text.toUpperCase())

private fun prog8Parser.DatatypeContext.toAst() = DataType.valueOf(text.toUpperCase())

private fun prog8Parser.RegisterorpairContext.toAst() = RegisterOrPair.valueOf(text.toUpperCase())


private fun prog8Parser.ArrayspecContext.toAst() : ArraySpec =
        ArraySpec(expression().toAst(), toPosition())


private fun prog8Parser.DirectiveContext.toAst() : Directive =
        Directive(directivename.text, directivearg().map { it.toAst() }, toPosition())


private fun prog8Parser.DirectiveargContext.toAst() : DirectiveArg =
        DirectiveArg(stringliteral()?.text, identifier()?.text, integerliteral()?.toAst()?.number?.toInt(), toPosition())


private fun prog8Parser.IntegerliteralContext.toAst(): NumericLiteral {
    fun makeLiteral(text: String, radix: Int, forceWord: Boolean): NumericLiteral {
        val integer: Int
        var datatype = DataType.UBYTE
        when (radix) {
            10 -> {
                integer = text.toInt()
                datatype = when(integer) {
                    in 0..255 -> DataType.UBYTE
                    in -128..127 -> DataType.BYTE
                    in 0..65535 -> DataType.UWORD
                    in -32768..32767 -> DataType.WORD
                    else -> DataType.FLOAT
                }
            }
            2 -> {
                if(text.length>8)
                    datatype = DataType.UWORD
                integer = text.toInt(2)
            }
            16 -> {
                if(text.length>2)
                    datatype = DataType.UWORD
                integer = text.toInt(16)
            }
            else -> throw FatalAstException("invalid radix")
        }
        return NumericLiteral(integer, if(forceWord) DataType.UWORD else datatype)
    }
    val terminal: TerminalNode = children[0] as TerminalNode
    val integerPart = this.intpart.text
    return when (terminal.symbol.type) {
        prog8Parser.DEC_INTEGER -> makeLiteral(integerPart, 10, wordsuffix()!=null)
        prog8Parser.HEX_INTEGER -> makeLiteral(integerPart.substring(1), 16, wordsuffix()!=null)
        prog8Parser.BIN_INTEGER -> makeLiteral(integerPart.substring(1), 2, wordsuffix()!=null)
        else -> throw FatalAstException(terminal.text)
    }
}


private fun prog8Parser.ExpressionContext.toAst() : IExpression {

    val litval = literalvalue()
    if(litval!=null) {
        val booleanlit = litval.booleanliteral()?.toAst()
        return if(booleanlit!=null) {
            LiteralValue.fromBoolean(booleanlit, litval.toPosition())
        }
        else {
            val intLit = litval.integerliteral()?.toAst()
            when {
                intLit!=null -> when(intLit.datatype) {
                    DataType.UBYTE -> LiteralValue(DataType.UBYTE, bytevalue = intLit.number.toShort(), position = litval.toPosition())
                    DataType.BYTE -> LiteralValue(DataType.BYTE, bytevalue = intLit.number.toShort(), position = litval.toPosition())
                    DataType.UWORD -> LiteralValue(DataType.UWORD, wordvalue = intLit.number.toInt(), position = litval.toPosition())
                    DataType.WORD -> LiteralValue(DataType.WORD, wordvalue = intLit.number.toInt(), position = litval.toPosition())
                    DataType.FLOAT -> LiteralValue(DataType.FLOAT, floatvalue= intLit.number.toDouble(), position = litval.toPosition())
                    else -> throw FatalAstException("invalid datatype for numeric literal")
                }
                litval.floatliteral()!=null -> LiteralValue(DataType.FLOAT, floatvalue = litval.floatliteral().toAst(), position = litval.toPosition())
                litval.stringliteral()!=null -> LiteralValue(DataType.STR, strvalue = unescape(litval.stringliteral().text, litval.toPosition()), position = litval.toPosition())
                litval.charliteral()!=null -> LiteralValue(DataType.UBYTE, bytevalue = Petscii.encodePetscii(unescape(litval.charliteral().text, litval.toPosition()), true)[0], position = litval.toPosition())
                litval.arrayliteral()!=null -> {
                    val array = litval.arrayliteral()?.toAst()
                    // the actual type of the arrayspec can not yet be determined here (missing namespace & heap)
                    // the ConstantFolder takes care of that and converts the type if needed.
                    LiteralValue(DataType.ARRAY_UB, arrayvalue = array, position = litval.toPosition())
                }
                else -> throw FatalAstException("invalid parsed literal")
            }
        }
    }

    if(register()!=null)
        return RegisterExpr(register().toAst(), register().toPosition())

    if(identifier()!=null)
        return identifier().toAst()

    if(scoped_identifier()!=null)
        return scoped_identifier().toAst()

    if(bop!=null)
        return BinaryExpression(left.toAst(), bop.text, right.toAst(), toPosition())

    if(prefix!=null)
        return PrefixExpression(prefix.text, expression(0).toAst(), toPosition())

    val funcall = functioncall()?.toAst()
    if(funcall!=null) return funcall

    if (rangefrom!=null && rangeto!=null) {
        val step = rangestep?.toAst() ?: LiteralValue(DataType.UBYTE, 1, position = toPosition())
        return RangeExpr(rangefrom.toAst(), rangeto.toAst(), step, toPosition())
    }

    if(childCount==3 && children[0].text=="(" && children[2].text==")")
        return expression(0).toAst()        // expression within ( )

    if(arrayindexed()!=null)
        return arrayindexed().toAst()

    if(typecast()!=null)
        return TypecastExpression(expression(0).toAst(), typecast().datatype().toAst(), toPosition())

    if(directmemory()!=null)
        return DirectMemoryRead(directmemory().expression().toAst(), toPosition())

    throw FatalAstException(text)
}


private fun prog8Parser.ArrayindexedContext.toAst(): ArrayIndexedExpression {
    return ArrayIndexedExpression(identifier()?.toAst() ?: scoped_identifier()?.toAst(),
            arrayspec().toAst(),
            toPosition())
}


private fun prog8Parser.Expression_listContext.toAst() = expression().map{ it.toAst() }


private fun prog8Parser.IdentifierContext.toAst() : IdentifierReference =
        IdentifierReference(listOf(text), toPosition())


private fun prog8Parser.Scoped_identifierContext.toAst() : IdentifierReference =
        IdentifierReference(NAME().map { it.text }, toPosition())


private fun prog8Parser.FloatliteralContext.toAst() = text.toDouble()


private fun prog8Parser.BooleanliteralContext.toAst() = when(text) {
    "true" -> true
    "false" -> false
    else -> throw FatalAstException(text)
}


private fun prog8Parser.ArrayliteralContext.toAst() : Array<IExpression> =
        expression().map { it.toAst() }.toTypedArray()


private fun prog8Parser.If_stmtContext.toAst(): IfStatement {
    val condition = expression().toAst()
    val trueStatements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val elseStatements = else_part()?.toAst() ?: mutableListOf()
    val trueScope = AnonymousScope(trueStatements, statement_block()?.toPosition() ?: statement().toPosition())
    val elseScope = AnonymousScope(elseStatements, else_part()?.toPosition() ?: toPosition())
    return IfStatement(condition, trueScope, elseScope, toPosition())
}

private fun prog8Parser.Else_partContext.toAst(): MutableList<IStatement> {
    return statement_block()?.toAst() ?: mutableListOf(statement().toAst())
}


private fun prog8Parser.Branch_stmtContext.toAst(): BranchStatement {
    val branchcondition = branchcondition().toAst()
    val trueStatements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val elseStatements = else_part()?.toAst() ?: mutableListOf()
    val trueScope = AnonymousScope(trueStatements, statement_block()?.toPosition() ?: statement().toPosition())
    val elseScope = AnonymousScope(elseStatements, else_part()?.toPosition() ?: toPosition())
    return BranchStatement(branchcondition, trueScope, elseScope, toPosition())
}

private fun prog8Parser.BranchconditionContext.toAst() = BranchCondition.valueOf(text.substringAfter('_').toUpperCase())


private fun prog8Parser.ForloopContext.toAst(): ForLoop {
    val loopregister = register()?.toAst()
    val datatype = datatype()?.toAst()
    val loopvar = identifier()?.toAst()
    val iterable = expression()!!.toAst()
    val scope = AnonymousScope(statement_block().toAst(), statement_block().toPosition())
    return ForLoop(loopregister, datatype, loopvar, iterable, scope, toPosition())
}


private fun prog8Parser.ContinuestmtContext.toAst() = Continue(toPosition())

private fun prog8Parser.BreakstmtContext.toAst() = Break(toPosition())


private fun prog8Parser.WhileloopContext.toAst(): WhileLoop {
    val condition = expression().toAst()
    val statements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val scope = AnonymousScope(statements, statement_block()?.toPosition() ?: statement().toPosition())
    return WhileLoop(condition, scope, toPosition())
}


private fun prog8Parser.RepeatloopContext.toAst(): RepeatLoop {
    val untilCondition = expression().toAst()
    val statements = statement_block()?.toAst() ?: mutableListOf(statement().toAst())
    val scope = AnonymousScope(statements, statement_block()?.toPosition() ?: statement().toPosition())
    return RepeatLoop(scope, untilCondition, toPosition())
}


internal fun registerSet(asmReturnvaluesRegisters: Iterable<RegisterOrStatusflag>): Set<Register> {
    val resultRegisters = mutableSetOf<Register>()
    for(x in asmReturnvaluesRegisters) {
        when(x.registerOrPair) {
            RegisterOrPair.A -> resultRegisters.add(Register.A)
            RegisterOrPair.X -> resultRegisters.add(Register.X)
            RegisterOrPair.Y -> resultRegisters.add(Register.Y)
            RegisterOrPair.AX -> {
                resultRegisters.add(Register.A)
                resultRegisters.add(Register.X)
            }
            RegisterOrPair.AY -> {
                resultRegisters.add(Register.A)
                resultRegisters.add(Register.Y)
            }
            RegisterOrPair.XY -> {
                resultRegisters.add(Register.X)
                resultRegisters.add(Register.Y)
            }
        }
    }
    return resultRegisters
}


internal fun escape(str: String) = str.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

internal fun unescape(str: String, position: Position): String {
    val result = mutableListOf<Char>()
    val iter = str.iterator()
    while(iter.hasNext()) {
        val c = iter.nextChar()
        if(c=='\\') {
            val ec = iter.nextChar()
            result.add(when(ec) {
                '\\' -> '\\'
                'n' -> '\n'
                'r' -> '\r'
                '"' -> '"'
                'u' -> {
                    "${iter.nextChar()}${iter.nextChar()}${iter.nextChar()}${iter.nextChar()}".toInt(16).toChar()
                }
                else -> throw AstException("$position invalid escape char in string: \\$ec")
            })
        } else {
            result.add(c)
        }
    }
    return result.joinToString("")
}
