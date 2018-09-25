package prog8.ast

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import prog8.compiler.target.c64.Petscii
import prog8.functions.*
import prog8.parser.prog8Parser
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.math.floor


/**************************** AST Data classes ****************************/

enum class DataType {
    BYTE,
    WORD,
    FLOAT,
    STR,
    STR_P,
    STR_S,
    STR_PS,
    ARRAY,
    ARRAY_W,
    MATRIX
}

enum class Register {
    A,
    X,
    Y,
    AX,
    AY,
    XY
}

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
        functionCall.arglist = functionCall.arglist.map { it.process(this) }
        return functionCall
    }

    fun process(functionCall: FunctionCallStatement): IStatement {
        functionCall.arglist = functionCall.arglist.map { it.process(this) }
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
        ifStatement.statements = ifStatement.statements.map { it.process(this) }
        ifStatement.elsepart = ifStatement.elsepart.map { it.process(this) }
        return ifStatement
    }

    fun process(branchStatement: BranchStatement): IStatement {
        branchStatement.statements = branchStatement.statements.map { it.process(this) }
        branchStatement.elsepart = branchStatement.elsepart.map { it.process(this) }
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
        assignment.target = assignment.target.process(this)
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
        forLoop.body = forLoop.body.asSequence().map {it.process(this)}.toMutableList()
        return forLoop
    }

    fun process(whileLoop: WhileLoop): IStatement {
        whileLoop.condition = whileLoop.condition.process(this)
        whileLoop.statements = whileLoop.statements.map { it.process(this) }
        return whileLoop
    }

    fun process(repeatLoop: RepeatLoop): IStatement {
        repeatLoop.untilCondition = repeatLoop.untilCondition.process(this)
        repeatLoop.statements = repeatLoop.statements.map { it.process(this) }
        return repeatLoop
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
    var arglist: List<IExpression>
}


interface INameScope {
    val name: String
    val position: Position
    var statements: MutableList<IStatement>

    fun usedNames(): Set<String>

    fun registerUsedName(name: String)

    fun subScopes() = statements.asSequence().filter { it is INameScope }.map { it as INameScope }.associate { it.name to it }

    fun labelsAndVariables() = statements.asSequence().filter { it is Label || it is VarDecl }
            .associate {((it as? Label)?.name ?: (it as? VarDecl)?.name) to it }

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
            return foundScope.labelsAndVariables()[scopedName.last()]
                    ?:
                    foundScope.subScopes()[scopedName.last()] as IStatement?
        } else {
            // unqualified name, find the scope the statement is in, look in that first
            var statementScope = statement
            while(statementScope !is ParentSentinel) {
                val localScope = statementScope.definingScope()
                val result = localScope.labelsAndVariables()[scopedName[0]]
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
            namespace.labelsAndVariables().forEach {
                println(" ".repeat(4 * (1 + indent)) + "${it.key}   ->  ${it.value::class.simpleName} at ${it.value.position}")
            }
            namespace.statements.filter { it is INameScope }.forEach {
                printNames(indent+1, it as INameScope)
            }
        }
        printNames(0, this)
    }

    fun removeStatement(statement: IStatement) {
        // remove a statement (most likely because it is never referenced such as a subroutine)
        val removed = statements.remove(statement)
        if(!removed) throw AstException("node to remove wasn't found")
    }
}


/**
 * Inserted into the Ast in place of modified nodes (not inserted directly as a parser result)
 * It can hold zero or more replacement statements that have to be inserted at that point.
 */
class AnonymousStatementList(override var parent: Node,
                             var statements: List<IStatement>,
                             override val position: Position) : IStatement {

    init {
        linkParents(parent)
    }

    override fun linkParents(parent: Node) {
        this.parent = parent
        statements.forEach { it.linkParents(this) }
    }

    override fun process(processor: IAstProcessor): IStatement {
        statements = statements.map { it.process(processor) }
        return this
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
    override fun usedNames(): Set<String> = throw NotImplementedError("not implemented on sub-scopes")
    override fun registerUsedName(name: String) = throw NotImplementedError("not implemented on sub-scopes")
}

object BuiltinFunctionStatementPlaceholder : IStatement {
    override val position = Position("<<placeholder>>", 0, 0, 0)
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

    fun linkParents() {
        parent = ParentSentinel
        statements.forEach {it.linkParents(this)}
    }

    fun process(processor: IAstProcessor) {
        processor.process(this)
    }

    override fun definingScope(): INameScope = GlobalNamespace("<<<global>>>", statements, position)
    override fun usedNames(): Set<String> = throw NotImplementedError("not implemented on sub-scopes")
    override fun registerUsedName(name: String) = throw NotImplementedError("not implemented on sub-scopes")


}


private class GlobalNamespace(override val name: String,
                              override var statements: MutableList<IStatement>,
                              override val position: Position) : INameScope {

    private val scopedNamesUsed: MutableSet<String> = mutableSetOf("main", "main.start")      // main and main.start are always used

    override fun usedNames(): Set<String>  = scopedNamesUsed

    override fun lookup(scopedName: List<String>, statement: Node): IStatement? {
        if(BuiltinFunctionNames.contains(scopedName.last())) {
            // builtin functions always exist, return a dummy statement for them
            val builtinPlaceholder = Label("builtin::${scopedName.last()}", statement.position)
            builtinPlaceholder.parent = ParentSentinel
            return builtinPlaceholder
        }
        val stmt = super.lookup(scopedName, statement)
        if(stmt!=null) {
            val targetScopedName = when(stmt) {
                is Label -> stmt.scopedname
                is VarDecl -> stmt.scopedname
                is Block -> stmt.scopedname
                is Subroutine -> stmt.scopedname
                else -> throw NameError("wrong identifier target: $stmt", stmt.position)
            }
            registerUsedName(targetScopedName)
        }
        return stmt
    }

    override fun registerUsedName(name: String) {
        // make sure to also register each scope separately
        scopedNamesUsed.add(name)
        if(name.contains('.'))
            registerUsedName(name.substringBeforeLast('.'))
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

    override fun usedNames(): Set<String> = throw NotImplementedError("not implemented on sub-scopes")
    override fun registerUsedName(name: String) = throw NotImplementedError("not implemented on sub-scopes")
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


class Return(var values: List<IExpression>, override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        values.forEach {it.linkParents(this)}
    }

    override fun process(processor: IAstProcessor): IStatement {
        values = values.map { it.process(processor) }
        return this
    }

    override fun toString(): String {
        return "Return(values: $values, pos=$position)"
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


class ArraySpec(var x: IExpression, var y: IExpression?, override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        x.linkParents(this)
        y?.linkParents(this)
    }

    fun process(processor: IAstProcessor) {
        x = x.process(processor)
        y = y?.process(processor)
    }

    override fun toString(): String {
        return("ArraySpec(x: $x, y: $y, pos=$position)")
    }

    fun size() : Int? {
        if(y==null) {
            return (x as? LiteralValue)?.asIntegerValue
        } else {
            val sizeX = (x as? LiteralValue)?.asIntegerValue ?: return null
            val sizeY = (y as? LiteralValue)?.asIntegerValue ?: return null
            return sizeX * sizeY
        }
    }
}


enum class VarDeclType {
    VAR,
    CONST,
    MEMORY
}

class VarDecl(val type: VarDeclType,
              declaredDatatype: DataType,
              val arrayspec: ArraySpec?,
              val name: String,
              var value: IExpression?,
              override val position: Position) : IStatement {
    override lateinit var parent: Node

    val datatypeErrors = mutableListOf<SyntaxError>()       // don't crash at init time, report them in the AstChecker
    val datatype = when {
        arrayspec == null -> declaredDatatype
        arrayspec.y != null -> when (declaredDatatype) {
            DataType.BYTE -> DataType.MATRIX
            else -> {
                datatypeErrors.add(SyntaxError("matrix can only contain bytes", position))
                DataType.BYTE
            }
        }
        else -> when (declaredDatatype) {
            DataType.BYTE -> DataType.ARRAY
            DataType.WORD -> DataType.ARRAY_W
            else -> {
                datatypeErrors.add(SyntaxError("array can only contain bytes or words", position))
                DataType.BYTE
            }
        }
    }


    override fun linkParents(parent: Node) {
        this.parent = parent
        arrayspec?.linkParents(this)
        value?.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    val scopedname: String by lazy { makeScopedName(name).joinToString(".") }
    val memorySize: Int
        get() = when(datatype) {
            DataType.BYTE -> 1
            DataType.WORD -> 2
            DataType.FLOAT -> 5   // MFLPT5
            DataType.STR,
            DataType.STR_P,
            DataType.STR_S,
            DataType.STR_PS -> {
                val lv = value as? LiteralValue ?: throw ExpressionError("need constant initializer value expression", position)
                lv.strvalue!!.length + 1
            }
            DataType.ARRAY -> {
                val aX = arrayspec?.x as? LiteralValue ?: throw ExpressionError("need constant value expression for arrayspec", position)
                aX.asIntegerValue!!
            }
            DataType.ARRAY_W -> {
                val aX = arrayspec?.x as? LiteralValue ?: throw ExpressionError("need constant value expression for arrayspec", position)
                2*aX.asIntegerValue!!
            }
            DataType.MATRIX -> {
                val aX = arrayspec?.x as? LiteralValue ?: throw ExpressionError("need constant value expression for arrayspec", position)
                val aY = arrayspec.y as? LiteralValue ?: throw ExpressionError("need constant value expression for arrayspec", position)
                aX.asIntegerValue!! * aY.asIntegerValue!!
            }
        }

    override fun toString(): String {
        return "VarDecl(name=$name, vartype=$type, datatype=$datatype, value=$value, pos=$position)"
    }
}


class Assignment(var target: AssignTarget, val aug_op : String?, var value: IExpression, override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        value.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        return("Assignment(augop: $aug_op, target: $target, value: $value, pos=$position)")
    }
}

data class AssignTarget(val register: Register?, val identifier: IdentifierReference?, override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
    }

    fun process(processor: IAstProcessor) = this

    fun determineDatatype(namespace: INameScope, stmt: IStatement): DataType {
        if(register!=null)
            return when(register){
                Register.A, Register.X, Register.Y -> DataType.BYTE
                Register.AX, Register.AY, Register.XY -> DataType.WORD
            }

        val symbol = namespace.lookup(identifier!!.nameInSource, stmt) ?: throw FatalAstException("symbol lookup failed: ${identifier.nameInSource}")
        if(symbol is VarDecl) return symbol.datatype
        throw FatalAstException("cannot determine datatype of assignment target $this")
    }
}


interface IExpression: Node {
    val isIterable: Boolean
    fun constValue(namespace: INameScope): LiteralValue?
    fun process(processor: IAstProcessor): IExpression
    fun referencesIdentifier(name: String): Boolean
    fun resultingDatatype(namespace: INameScope): DataType?
}


// note: some expression elements are mutable, to be able to rewrite/process the expression tree

class PrefixExpression(val operator: String, var expression: IExpression, override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        expression.linkParents(this)
    }

    override fun constValue(namespace: INameScope): LiteralValue? = null
    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String) = expression.referencesIdentifier(name)
    override fun resultingDatatype(namespace: INameScope): DataType? = expression.resultingDatatype(namespace)
    override val isIterable = false
}


class BinaryExpression(var left: IExpression, val operator: String, var right: IExpression, override val position: Position) : IExpression {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        left.linkParents(this)
        right.linkParents(this)
    }

    // binary expression should actually have been optimized away into a single value, before const value was requested...
    override fun constValue(namespace: INameScope): LiteralValue? = null

    override val isIterable = false

    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String) = left.referencesIdentifier(name) || right.referencesIdentifier(name)
    override fun resultingDatatype(namespace: INameScope): DataType? {
        val leftDt = left.resultingDatatype(namespace)
        val rightDt = right.resultingDatatype(namespace)
        return when(operator) {
            "+", "-", "*", "**", "/", "%" -> if(leftDt==null || rightDt==null) null else arithmeticOpDt(leftDt, rightDt)
            "//" -> if(leftDt==null || rightDt==null) null else integerDivisionOpDt(leftDt, rightDt)
            "&" -> leftDt
            "|" -> leftDt
            "^" -> leftDt
            "and", "or", "xor",
            "<", ">",
            "<=", ">=",
            "==", "!=" -> DataType.BYTE
            else -> throw FatalAstException("resulting datatype check for invalid operator $operator")
        }
    }

    private fun integerDivisionOpDt(leftDt: DataType, rightDt: DataType): DataType {
        return when(leftDt) {
            DataType.BYTE -> when(rightDt) {
                DataType.BYTE, DataType.WORD, DataType.FLOAT -> DataType.BYTE
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            DataType.WORD -> when(rightDt) {
                DataType.BYTE, DataType.WORD, DataType.FLOAT -> DataType.WORD
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            DataType.FLOAT -> when(rightDt) {
                DataType.BYTE, DataType.WORD, DataType.FLOAT -> DataType.WORD
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
        }
    }

    private fun arithmeticOpDt(leftDt: DataType, rightDt: DataType): DataType {
        return when(leftDt) {
            DataType.BYTE -> when(rightDt) {
                DataType.BYTE -> DataType.BYTE
                DataType.WORD -> DataType.WORD
                DataType.FLOAT -> DataType.FLOAT
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            DataType.WORD -> when(rightDt) {
                DataType.BYTE, DataType.WORD -> DataType.WORD
                DataType.FLOAT -> DataType.FLOAT
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            DataType.FLOAT -> when(rightDt) {
                DataType.BYTE -> DataType.FLOAT
                DataType.WORD -> DataType.FLOAT
                DataType.FLOAT -> DataType.FLOAT
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
        }
    }
}

private data class ByteOrWordLiteral(val intvalue: Int, val datatype: DataType) {
    fun asWord() = ByteOrWordLiteral(intvalue, DataType.WORD)
    fun asByte() = ByteOrWordLiteral(intvalue, DataType.BYTE)
}

class LiteralValue(val type: DataType,
                   val bytevalue: Short? = null,
                   val wordvalue: Int? = null,
                   val floatvalue: Double? = null,
                   val strvalue: String? = null,
                   val arrayvalue: Array<IExpression>? = null,
                   override val position: Position) : IExpression {
    override lateinit var parent: Node
    override fun referencesIdentifier(name: String) = arrayvalue?.any { it.referencesIdentifier(name) } ?: false

    val isString = type==DataType.STR || type==DataType.STR_P || type==DataType.STR_S || type==DataType.STR_PS
    val isNumeric = type==DataType.BYTE || type==DataType.WORD || type==DataType.FLOAT
    val isArray = type==DataType.ARRAY || type==DataType.ARRAY_W || type==DataType.MATRIX

    companion object {
        fun fromBoolean(bool: Boolean, position: Position) =
                LiteralValue(DataType.BYTE, bytevalue = if(bool) 1 else 0, position=position)

        fun optimalNumeric(value: Number, position: Position): LiteralValue {
            val floatval = value.toDouble()
            return if(floatval == floor(floatval)  && floatval in -32768..65535) {
                // the floating point value is actually an integer.
                when (floatval) {
                    // note: we cheat a little here and allow negative integers during expression evaluations
                    in -128..255 -> LiteralValue(DataType.BYTE, bytevalue = floatval.toShort(), position = position)
                    in -32768..65535 -> LiteralValue(DataType.WORD, wordvalue = floatval.toInt(), position = position)
                    else -> LiteralValue(DataType.FLOAT, floatvalue = floatval, position = position)
                }
            } else {
                LiteralValue(DataType.FLOAT, floatvalue = floatval, position = position)
            }
        }

        fun optimalInteger(value: Number, position: Position): LiteralValue {
            return when (value) {
                // note: we cheat a little here and allow negative integers during expression evaluations
                in -128..255 -> LiteralValue(DataType.BYTE, bytevalue = value.toShort(), position = position)
                else -> LiteralValue(DataType.WORD, wordvalue = value.toInt(), position = position)
            }
        }
    }

    init {
        when(type){
            DataType.BYTE -> if(bytevalue==null) throw FatalAstException("literal value missing bytevalue")
            DataType.WORD -> if(wordvalue==null) throw FatalAstException("literal value missing wordvalue")
            DataType.FLOAT -> if(floatvalue==null) throw FatalAstException("literal value missing floatvalue")
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                if(strvalue==null) throw FatalAstException("literal value missing strvalue")
            }
            DataType.ARRAY, DataType.ARRAY_W -> if(arrayvalue==null) throw FatalAstException("literal value missing arrayvalue")
            DataType.MATRIX -> TODO("matrix literalvalue? for now, arrays are good enough for this")
        }
        if(bytevalue==null && wordvalue==null && floatvalue==null && arrayvalue==null && strvalue==null)
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
        else -> null
    }

    val asBooleanValue: Boolean =
            (floatvalue!=null && floatvalue != 0.0) ||
            (bytevalue!=null && bytevalue != 0.toShort()) ||
            (wordvalue!=null && wordvalue != 0) ||
            (strvalue!=null && strvalue.isNotEmpty()) ||
            (arrayvalue != null && arrayvalue.isNotEmpty())

    override fun linkParents(parent: Node) {
        this.parent = parent
        arrayvalue?.forEach {it.linkParents(this)}
    }

    override fun constValue(namespace: INameScope): LiteralValue?  = this
    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        return "LiteralValue(byte=$bytevalue, word=$wordvalue, float=$floatvalue, str=$strvalue, array=$arrayvalue pos=$position)"
    }

    override fun resultingDatatype(namespace: INameScope) = type
    override val isIterable = when(type) {
        DataType.BYTE, DataType.WORD, DataType.FLOAT -> false
        DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> true
        DataType.ARRAY, DataType.ARRAY_W, DataType.MATRIX -> true
    }

    override fun hashCode(): Int {
        val bh = bytevalue?.hashCode() ?: 0x10001234
        val wh = wordvalue?.hashCode() ?: 0x01002345
        val fh = floatvalue?.hashCode() ?: 0x00103456
        val sh = strvalue?.hashCode() ?: 0x00014567
        val ah = arrayvalue?.hashCode() ?: 0x11119876
        return bh xor wh xor fh xor sh xor ah xor type.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if(other==null || other !is LiteralValue)
            return false
        return compareTo(other)==0
    }

    operator fun compareTo(other: LiteralValue): Int {
        val numLeft = asNumericValue?.toDouble()
        val numRight = other.asNumericValue?.toDouble()
        if(numLeft!=null && numRight!=null)
            return numLeft.compareTo(numRight)

        if(strvalue!=null && other.strvalue!=null)
            return strvalue.compareTo(other.strvalue)

        throw ExpressionError("cannot compare type $type with ${other.type}", other.position)
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

    override fun constValue(namespace: INameScope): LiteralValue? = null
    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String): Boolean  = from.referencesIdentifier(name) || to.referencesIdentifier(name)
    override fun resultingDatatype(namespace: INameScope): DataType? {
        val fromDt=from.resultingDatatype(namespace)
        val toDt=to.resultingDatatype(namespace)
        return when {
            fromDt==null || toDt==null -> null
            fromDt==DataType.WORD || toDt==DataType.WORD -> DataType.WORD
            fromDt==DataType.STR || toDt==DataType.STR -> DataType.STR
            fromDt==DataType.STR_P || toDt==DataType.STR_P -> DataType.STR_P
            fromDt==DataType.STR_S || toDt==DataType.STR_S -> DataType.STR_S
            fromDt==DataType.STR_PS || toDt==DataType.STR_PS -> DataType.STR_PS
            else -> DataType.BYTE
        }
    }

    override val isIterable = true

    override fun toString(): String {
        return "RangeExpr(from $from, to $to, step $step, pos=$position)"
    }

    fun size(): Int? {
        val fromLv = (from as? LiteralValue)
        val toLv = (to as? LiteralValue)
        if(fromLv==null || toLv==null)
            return null
        return toConstantIntegerRange()?.count()
    }

    fun toConstantIntegerRange(): IntProgression? {
        val fromLv = from as? LiteralValue
        val toLv = to as? LiteralValue
        if(fromLv==null || toLv==null)
            return null         // non-constant range
        val fromVal: Int
        val toVal: Int
        if(fromLv.isString && toLv.isString) {
            // string range -> int range over petscii values
            fromVal = Petscii.encodePetscii(fromLv.strvalue!!, true)[0].toInt()
            toVal = Petscii.encodePetscii(toLv.strvalue!!, true)[0].toInt()
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

    override fun constValue(namespace: INameScope): LiteralValue? = null
    override fun process(processor: IAstProcessor) = this
    override fun referencesIdentifier(name: String): Boolean  = false
    override val isIterable = false

    override fun toString(): String {
        return "RegisterExpr(register=$register, pos=$position)"
    }

    override fun resultingDatatype(namespace: INameScope): DataType? {
        return when(register){
            Register.A, Register.X, Register.Y -> DataType.BYTE
            Register.AX, Register.AY, Register.XY -> DataType.WORD
        }
    }
}


data class IdentifierReference(val nameInSource: List<String>, override val position: Position) : IExpression {
    override lateinit var parent: Node

    fun targetStatement(namespace: INameScope) =
        if(nameInSource.size==1 && BuiltinFunctionNames.contains(nameInSource[0]))
            BuiltinFunctionStatementPlaceholder
        else
            namespace.lookup(nameInSource, this)

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun constValue(namespace: INameScope): LiteralValue? {
        val node = namespace.lookup(nameInSource, this)
                ?: throw UndefinedSymbolError(this)
        val vardecl = node as? VarDecl
        if(vardecl==null) {
            throw ExpressionError("name should be a constant, instead of: ${node::class.simpleName}", position)
        } else if(vardecl.type!=VarDeclType.CONST) {
            return null
        }
        return vardecl.value?.constValue(namespace)
    }

    override fun toString(): String {
        return "IdentifierRef($nameInSource)"
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String): Boolean  = nameInSource.last() == name   // @todo is this correct all the time?

    override fun resultingDatatype(namespace: INameScope): DataType? {
        val targetStmt = targetStatement(namespace)
        if(targetStmt is VarDecl) {
            return targetStmt.datatype
        } else {
            throw FatalAstException("cannot get datatype from identifier reference ${this}, pos=$position")
        }
    }

    override val isIterable: Boolean = true     // should be checked by caller by actually looking up the symbol
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
           val generatedLabel: String?,
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
                   override var arglist: List<IExpression>,
                   override val position: Position) : IExpression, IFunctionCall {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        arglist.forEach { it.linkParents(this) }
    }

    override fun constValue(namespace: INameScope) = constValue(namespace, true)

    private fun constValue(namespace: INameScope, withDatatypeCheck: Boolean): LiteralValue? {
        // if the function is a built-in function and the args are consts, should try to const-evaluate!
        if(target.nameInSource.size>1) return null
        try {
            val resultValue = when (target.nameInSource[0]) {
                "sin" -> builtinSin(arglist, position, namespace)
                "cos" -> builtinCos(arglist, position, namespace)
                "abs" -> builtinAbs(arglist, position, namespace)
                "acos" -> builtinAcos(arglist, position, namespace)
                "asin" -> builtinAsin(arglist, position, namespace)
                "tan" -> builtinTan(arglist, position, namespace)
                "atan" -> builtinAtan(arglist, position, namespace)
                "ln" -> builtinLn(arglist, position, namespace)
                "log2" -> builtinLog2(arglist, position, namespace)
                "log10" -> builtinLog10(arglist, position, namespace)
                "sqrt" -> builtinSqrt(arglist, position, namespace)
                "max" -> builtinMax(arglist, position, namespace)
                "min" -> builtinMin(arglist, position, namespace)
                "round" -> builtinRound(arglist, position, namespace)
                "rad" -> builtinRad(arglist, position, namespace)
                "deg" -> builtinDeg(arglist, position, namespace)
                "sum" -> builtinSum(arglist, position, namespace)
                "avg" -> builtinAvg(arglist, position, namespace)
                "len" -> builtinLen(arglist, position, namespace)
                "lsb" -> builtinLsb(arglist, position, namespace)
                "msb" -> builtinMsb(arglist, position, namespace)
                "flt" -> builtinFlt(arglist, position, namespace)
                "any" -> builtinAny(arglist, position, namespace)
                "all" -> builtinAll(arglist, position, namespace)
                "floor" -> builtinFloor(arglist, position, namespace)
                "ceil" -> builtinCeil(arglist, position, namespace)
                "lsl", "lsr", "rol", "rol2", "ror", "ror2", "set_carry", "clear_carry", "set_irqd", "clear_irqd" ->
                    throw ExpressionError("builtin function ${target.nameInSource[0]} can't be used in expressions because it doesn't return a value", position)
                else -> null
            }
            if(withDatatypeCheck) {
                val resultDt = this.resultingDatatype(namespace)
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

    override fun resultingDatatype(namespace: INameScope): DataType? {
        val constVal = constValue(namespace, false)
        if(constVal!=null)
            return constVal.resultingDatatype(namespace)
        val stmt = target.targetStatement(namespace) ?: return null
        if(stmt is BuiltinFunctionStatementPlaceholder) {
            if(target.nameInSource[0] == "set_carry" || target.nameInSource[0]=="set_irqd" ||
                    target.nameInSource[0] == "clear_carry" || target.nameInSource[0]=="clear_irqd") {
                return null // these have no return value
            }
            return builtinFunctionReturnType(target.nameInSource[0], this.arglist, namespace)
        }
        else if(stmt is Subroutine) {
            if(stmt.returnvalues.isEmpty()) {
                return null     // no return value
            }
            if(stmt.returnvalues.size==1) {
                if(stmt.returnvalues[0].register!=null) {
                    return when(stmt.returnvalues[0].register!!) {
                        Register.A, Register.X, Register.Y -> DataType.BYTE
                        Register.AX, Register.AY, Register.XY -> DataType.WORD
                    }
                } else if(stmt.returnvalues[0].statusflag!=null) {
                    val flag = stmt.returnvalues[0].statusflag!!
                    TODO("return value in status flag $flag")
                }
            }
            TODO("return type for subroutine with multiple return values $stmt")
        }
        TODO("datatype of functioncall to $stmt")
    }

    override val isIterable: Boolean
            get() {
                TODO("isIterable of function call result")
            }
}


class FunctionCallStatement(override var target: IdentifierReference,
                            override var arglist: List<IExpression>,
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


class Subroutine(override val name: String,
                 val parameters: List<SubroutineParameter>,
                 val returnvalues: List<SubroutineReturnvalue>,
                 val address: Int?,
                 override var statements: MutableList<IStatement>,
                 override val position: Position) : IStatement, INameScope {
    override lateinit var parent: Node
    val scopedname: String by lazy { makeScopedName(name).joinToString(".") }


    override fun linkParents(parent: Node) {
        this.parent = parent
        parameters.forEach { it.linkParents(this) }
        returnvalues.forEach { it.linkParents(this) }
        statements.forEach { it.linkParents(this) }
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        return "Subroutine(name=$name, address=$address, parameters=$parameters, returnvalues=$returnvalues, ${statements.size} statements)"
    }

    override fun usedNames(): Set<String> = throw NotImplementedError("not implemented on sub-scopes")
    override fun registerUsedName(name: String) = throw NotImplementedError("not implemented on sub-scopes")
}


data class SubroutineParameter(val name: String,
                               val register: Register?,
                               val statusflag: Statusflag?,
                               override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
}


data class SubroutineReturnvalue(val register: Register?,
                                 val statusflag: Statusflag?,
                                 val clobbered: Boolean,
                                 override val position: Position) : Node {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
}


class IfStatement(var condition: IExpression,
                  var statements: List<IStatement>,
                  var elsepart: List<IStatement>,
                  override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        condition.linkParents(this)
        statements.forEach { it.linkParents(this) }
        elsepart.forEach { it.linkParents(this) }
    }

    override fun process(processor: IAstProcessor): IStatement = processor.process(this)
}


class BranchStatement(var condition: BranchCondition,
                      var statements: List<IStatement>,
                      var elsepart: List<IStatement>,
                      override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        statements.forEach { it.linkParents(this) }
        elsepart.forEach { it.linkParents(this) }
    }

    override fun process(processor: IAstProcessor): IStatement = processor.process(this)

    override fun toString(): String {
        return "Branch(cond: $condition, ${statements.size} stmts, ${elsepart.size} else-stmts, pos=$position)"
    }
}


class ForLoop(val loopRegister: Register?,
              val loopVar: IdentifierReference?,
              var iterable: IExpression,
              var body: MutableList<IStatement>,
              override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent=parent
        loopVar?.linkParents(this)
        iterable.linkParents(this)
        body.forEach { it.linkParents(this) }
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        return "ForLoop(loopVar: $loopVar, loopReg: $loopRegister, iterable: $iterable, pos=$position)"
    }
}


class WhileLoop(var condition: IExpression,
                var statements: List<IStatement>,
                override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        condition.linkParents(this)
        statements.forEach { it.linkParents(this) }
    }

    override fun process(processor: IAstProcessor): IStatement = processor.process(this)
}


class RepeatLoop(var statements: List<IStatement>,
                 var untilCondition: IExpression,
                 override val position: Position) : IStatement {
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        untilCondition.linkParents(this)
        statements.forEach { it.linkParents(this) }
    }

    override fun process(processor: IAstProcessor): IStatement = processor.process(this)
}


/***************** Antlr Extension methods to create AST ****************/

fun prog8Parser.ModuleContext.toAst(name: String) : Module =
        Module(name, modulestatement().asSequence().map { it.toAst() }.toMutableList(), toPosition())


/************** Helper extension methods (private) ************/

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
        Block(identifier().text, integerliteral()?.toAst()?.intvalue, statement_block().toAst(), toPosition())


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
        return Assignment(it.assign_target().toAst(),null, it.expression().toAst(), it.toPosition())
    }

    augassignment()?.let {
        return Assignment(it.assign_target().toAst(),
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

    throw FatalAstException("unprocessed source text (are we missing ast conversion rules for parser elements?): $text")
}


private fun prog8Parser.Functioncall_stmtContext.toAst(): IStatement {
    val location =
            if(identifier()!=null) identifier()?.toAst()
            else scoped_identifier()?.toAst()
    return if(expression_list() ==null)
        FunctionCallStatement(location!!, emptyList(), toPosition())
    else
        FunctionCallStatement(location!!, expression_list().toAst(), toPosition())
}


private fun prog8Parser.FunctioncallContext.toAst(): FunctionCall {
    val location =
            if(identifier()!=null) identifier()?.toAst()
            else scoped_identifier()?.toAst()
    return if(expression_list() ==null)
        FunctionCall(location!!, emptyList(), toPosition())
    else
        FunctionCall(location!!, expression_list().toAst(), toPosition())
}


private fun prog8Parser.InlineasmContext.toAst(): IStatement =
        InlineAssembly(INLINEASMBLOCK().text, toPosition())


private fun prog8Parser.ReturnstmtContext.toAst() : IStatement {
    val values = expression_list()
    return Return(values?.toAst() ?: emptyList(), toPosition())
}

private fun prog8Parser.UnconditionaljumpContext.toAst(): IStatement {

    val address = integerliteral()?.toAst()?.intvalue
    val identifier =
            if(identifier()!=null) identifier()?.toAst()
            else scoped_identifier()?.toAst()

    return Jump(address, identifier, null, toPosition())
}


private fun prog8Parser.LabeldefContext.toAst(): IStatement =
        Label(children[0].text, toPosition())


private fun prog8Parser.SubroutineContext.toAst() : Subroutine {
    return Subroutine(identifier().text,
            if(sub_params() ==null) emptyList() else sub_params().toAst(),
            if(sub_returns() ==null) emptyList() else sub_returns().toAst(),
            sub_address()?.integerliteral()?.toAst()?.intvalue,
            if(statement_block() ==null) mutableListOf() else statement_block().toAst(),
            toPosition())
}


private fun prog8Parser.Sub_paramsContext.toAst(): List<SubroutineParameter> =
        sub_param().map {
            SubroutineParameter(it.identifier().text, it.register()?.toAst(), it.statusflag()?.toAst(), it.toPosition())
        }


private fun prog8Parser.Sub_returnsContext.toAst(): List<SubroutineReturnvalue> =
        sub_return().map {
            val isClobber = it.childCount==2 && it.children[1].text == "?"
            SubroutineReturnvalue(it.register()?.toAst(), it.statusflag()?.toAst(), isClobber, it.toPosition())
        }


private fun prog8Parser.Assign_targetContext.toAst() : AssignTarget {
    val register = register()?.toAst()
    val identifier = identifier()
    return if(identifier!=null)
        AssignTarget(register, identifier.toAst(), toPosition())
    else
        AssignTarget(register, scoped_identifier()?.toAst(), toPosition())
}


private fun prog8Parser.RegisterContext.toAst() = Register.valueOf(text.toUpperCase())

private fun prog8Parser.StatusflagContext.toAst() = Statusflag.valueOf(text)

private fun prog8Parser.DatatypeContext.toAst() = DataType.valueOf(text.toUpperCase())


private fun prog8Parser.ArrayspecContext.toAst() : ArraySpec =
        ArraySpec(expression(0).toAst(), if (expression().size > 1) expression(1).toAst() else null, toPosition())


private fun prog8Parser.DirectiveContext.toAst() : Directive =
        Directive(directivename.text, directivearg().map { it.toAst() }, toPosition())


private fun prog8Parser.DirectiveargContext.toAst() : DirectiveArg =
        DirectiveArg(stringliteral()?.text, identifier()?.text, integerliteral()?.toAst()?.intvalue, toPosition())


private fun prog8Parser.IntegerliteralContext.toAst(): ByteOrWordLiteral {
    fun makeLiteral(text: String, radix: Int, forceWord: Boolean): ByteOrWordLiteral {
        val integer: Int
        var datatype = DataType.BYTE
        if(radix==10) {
            integer = text.toInt()
            if(integer in 256..65535)
                datatype = DataType.WORD
        } else if(radix==2) {
            if(text.length>8)
                datatype = DataType.WORD
            integer = text.toInt(2)
        } else if(radix==16) {
            if(text.length>2)
                datatype = DataType.WORD
            integer = text.toInt(16)
        } else {
            throw FatalAstException("invalid radix")
        }
        return ByteOrWordLiteral(integer, if(forceWord) DataType.WORD else datatype)
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
                    DataType.BYTE -> LiteralValue(DataType.BYTE, bytevalue = intLit.intvalue.toShort(), position = litval.toPosition())
                    DataType.WORD -> LiteralValue(DataType.WORD, wordvalue = intLit.intvalue, position = litval.toPosition())
                    else -> throw FatalAstException("invalid datatype for integer literal")
                }
                litval.floatliteral()!=null -> LiteralValue(DataType.FLOAT, floatvalue = litval.floatliteral().toAst(), position = litval.toPosition())
                litval.stringliteral()!=null -> LiteralValue(DataType.STR, strvalue = litval.stringliteral().text, position = litval.toPosition())
                litval.arrayliteral()!=null -> {
                    val array = litval.arrayliteral()?.toAst()
                    // byte/word array type difference is not determined here.
                    // the ConstantFolder takes care of that and converts the type if needed.
                    LiteralValue(DataType.ARRAY, arrayvalue = array, position = litval.toPosition())
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
        val step = rangestep?.toAst() ?: LiteralValue(DataType.BYTE, 1, position = toPosition())
        return RangeExpr(rangefrom.toAst(), rangeto.toAst(), step, toPosition())
    }

    if(childCount==3 && children[0].text=="(" && children[2].text==")")
        return expression(0).toAst()        // expression within ( )

    throw FatalAstException(text)
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
    val statements = statement_block()?.toAst() ?: listOf(statement().toAst())
    val elsepart = else_part()?.toAst() ?: emptyList()
    return IfStatement(condition, statements, elsepart, toPosition())
}

private fun prog8Parser.Else_partContext.toAst(): List<IStatement> {
    return statement_block()?.toAst() ?: listOf(statement().toAst())
}


private fun prog8Parser.Branch_stmtContext.toAst(): BranchStatement {
    val branchcondition = branchcondition().toAst()
    val statements = statement_block()?.toAst() ?: listOf(statement().toAst())
    val elsepart = else_part()?.toAst() ?: emptyList()
    return BranchStatement(branchcondition, statements, elsepart, toPosition())
}

private fun prog8Parser.BranchconditionContext.toAst() = BranchCondition.valueOf(text.substringAfter('_').toUpperCase())


private fun prog8Parser.ForloopContext.toAst(): ForLoop {
    val loopregister = register()?.toAst()
    val loopvar = identifier()?.toAst()
    val iterable = expression()!!.toAst()
    val body = statement_block().toAst()
    return ForLoop(loopregister, loopvar, iterable, body, toPosition())
}


private fun prog8Parser.ContinuestmtContext.toAst() = Continue(toPosition())

private fun prog8Parser.BreakstmtContext.toAst() = Break(toPosition())


private fun prog8Parser.WhileloopContext.toAst(): WhileLoop {
    val condition = expression().toAst()
    val statements = statement_block()?.toAst() ?: listOf(statement().toAst())
    return WhileLoop(condition, statements, toPosition())
}


private fun prog8Parser.RepeatloopContext.toAst(): RepeatLoop {
    val untilCondition = expression().toAst()
    val statements = statement_block()?.toAst() ?: listOf(statement().toAst())
    return RepeatLoop(statements, untilCondition, toPosition())
}
