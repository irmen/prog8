package il65.ast

import il65.functions.*
import il65.parser.il65Parser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import java.nio.file.Paths
import javax.xml.crypto.Data
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
    NE,
    VS,
    VC,
    MI,
    PL
}


class FatalAstException (override var message: String) : Exception(message)

open class AstException (override var message: String) : Exception(message)

class SyntaxError(override var message: String, val position: Position?) : AstException(message) {
    override fun toString(): String {
        val location = position?.toString() ?: ""
        return "$location Syntax error: $message"
    }
}

class NameError(override var message: String, val position: Position?) : AstException(message) {
    override fun toString(): String {
        val location = position?.toString() ?: ""
        return "$location Name error: $message"
    }
}

open class ExpressionError(message: String, val position: Position?) : AstException(message) {
    override fun toString(): String {
        val location = position?.toString() ?: ""
        return "$location Error: $message"
    }
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
        subroutine.statements = subroutine.statements.map { it.process(this) }.toMutableList()
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
}


interface Node {
    var position: Position?      // optional for the sake of easy unit testing
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
    val position: Position?
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
class AnonymousStatementList(override var parent: Node, var statements: List<IStatement>) : IStatement {
    override var position: Position? = null

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
    override var position: Position? = null
    override var parent: Node = this
    override fun linkParents(parent: Node) {}
}

object BuiltinFunctionScopePlaceholder : INameScope {
    override val name = "<<builtin-functions-scope-placeholder>>"
    override val position: Position? = null
    override var statements = mutableListOf<IStatement>()
    override fun usedNames(): Set<String> = throw NotImplementedError("not implemented on sub-scopes")
    override fun registerUsedName(name: String) = throw NotImplementedError("not implemented on sub-scopes")
}

object BuiltinFunctionStatementPlaceholder : IStatement {
    override var position: Position? = null
    override var parent: Node = ParentSentinel
    override fun linkParents(parent: Node) {}
    override fun process(processor: IAstProcessor): IStatement = this
    override fun definingScope(): INameScope = BuiltinFunctionScopePlaceholder
}

class Module(override val name: String,
             override var statements: MutableList<IStatement>) : Node, INameScope {
    override var position: Position? = null
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
                              override val position: Position?) : INameScope {

    private val scopedNamesUsed: MutableSet<String> = mutableSetOf("main", "main.start")      // main and main.start are always used

    override fun usedNames(): Set<String>  = scopedNamesUsed

    override fun lookup(scopedName: List<String>, statement: Node): IStatement? {
        if(BuiltinFunctionNames.contains(scopedName.last())) {
            // builtin functions always exist, return a dummy statement for them
            val builtinPlaceholder = Label("builtin::${scopedName.last()}")
            builtinPlaceholder.position = statement.position
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
                 override var statements: MutableList<IStatement>) : IStatement, INameScope {
    override var position: Position? = null
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


data class Directive(val directive: String, val args: List<DirectiveArg>) : IStatement {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        args.forEach{it.linkParents(this)}
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}


data class DirectiveArg(val str: String?, val name: String?, val int: Int?) : Node {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
}


data class Label(val name: String) : IStatement {
    override var position: Position? = null
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


class Return(var values: List<IExpression>) : IStatement {
    override var position: Position? = null
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


class ArraySpec(var x: IExpression, var y: IExpression?) : Node {
    override var position: Position? = null
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
                   var value: IExpression?) : IStatement {
    override var position: Position? = null
    override lateinit var parent: Node

    // note: the actual datatype will be determined somewhat later (in the ast checker phase)
    // (we don't do it at init time, because we have to have a way to create a proper syntax error instead of a crash)
    lateinit var datatype: DataType

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

    fun setDatatype() {
        datatype =
                when {
                    arrayspec == null -> declaredDatatype
                    arrayspec.y!=null -> when (declaredDatatype) {
                        DataType.BYTE -> DataType.MATRIX
                        else -> throw SyntaxError("matrix can only contain bytes", position)
                    }
                    else -> when (declaredDatatype) {
                        DataType.BYTE -> DataType.ARRAY
                        DataType.WORD -> DataType.ARRAY_W
                        else -> throw SyntaxError("array can only contain bytes or words", position)
                    }
                }
    }
}


class Assignment(var target: AssignTarget, val aug_op : String?, var value: IExpression) : IStatement {
    override var position: Position? = null
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

data class AssignTarget(val register: Register?, val identifier: IdentifierReference?) : Node {
    override var position: Position? = null
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

        val symbol = namespace.lookup(identifier!!.nameInSource, stmt)
        if(symbol is VarDecl) return symbol.datatype
        throw FatalAstException("cannot determine datatype of assignment target $this")
    }
}


interface IExpression: Node {
    fun constValue(namespace: INameScope): LiteralValue?
    fun process(processor: IAstProcessor): IExpression
    fun referencesIdentifier(name: String): Boolean
    fun resultingDatatype(namespace: INameScope): DataType?
}


// note: some expression elements are mutable, to be able to rewrite/process the expression tree

class PrefixExpression(val operator: String, var expression: IExpression) : IExpression {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        expression.linkParents(this)
    }

    override fun constValue(namespace: INameScope): LiteralValue? = null
    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String) = expression.referencesIdentifier(name)
    override fun resultingDatatype(namespace: INameScope): DataType? = expression.resultingDatatype(namespace)
}


class BinaryExpression(var left: IExpression, val operator: String, var right: IExpression) : IExpression {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        left.linkParents(this)
        right.linkParents(this)
    }

    // binary expression should actually have been optimized away into a single value, before const value was requested...
    override fun constValue(namespace: INameScope): LiteralValue? = null

    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String) = left.referencesIdentifier(name) || right.referencesIdentifier(name)
    override fun resultingDatatype(namespace: INameScope): DataType? {
        val leftDt = left.resultingDatatype(namespace)
        val rightDt = right.resultingDatatype(namespace)
        return when(operator) {
            "+", "-", "*", "/", "**" -> if(leftDt==null || rightDt==null) null else arithmeticOpDt(leftDt, rightDt)
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

    private fun arithmeticOpDt(leftDt: DataType, rightDt: DataType): DataType {
        return when(leftDt) {
            DataType.BYTE -> when(rightDt) {
                DataType.BYTE -> DataType.BYTE
                DataType.WORD -> DataType.WORD
                DataType.FLOAT -> DataType.FLOAT
                else -> throw FatalAstException("arithmetic operation on incompatible datatypes: $leftDt and $rightDt")
            }
            DataType.WORD -> when(rightDt) {
                DataType.BYTE -> DataType.BYTE
                DataType.WORD -> DataType.WORD
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

data class LiteralValue(val bytevalue: Short? = null,
                        val wordvalue: Int? = null,
                        val floatvalue: Double? = null,
                        val strvalue: String? = null,
                        val arrayvalue: MutableList<IExpression>? = null) : IExpression {
    override var position: Position? = null
    override lateinit var parent: Node
    override fun referencesIdentifier(name: String) = arrayvalue?.any { it.referencesIdentifier(name) } ?: false

    companion object {
        fun optimalNumeric(value: Number): LiteralValue {
            val floatval = value.toDouble()
            return if(floatval == floor(floatval)  && floatval in -32768..65535) {
                // the floating point value is actually an integer.
                when (floatval) {
                    // note: we cheat a little here and allow negative integers during expression evaluations
                    in -128..255 -> LiteralValue(bytevalue = floatval.toShort())
                    in -32768..65535 -> LiteralValue(wordvalue = floatval.toInt())
                    else -> throw FatalAstException("integer overflow: $floatval")
                }
            } else {
                LiteralValue(floatvalue = floatval)
            }
        }
    }

    val isByte = bytevalue!=null
    val isWord = wordvalue!=null
    val isInteger = isByte or isWord
    val isFloat = floatvalue!=null
    val isArray = arrayvalue!=null      // @todo: array / word-array / matrix ?
    val isString = strvalue!=null

    init {
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

    override fun resultingDatatype(namespace: INameScope): DataType? {
        return when {
            isByte -> DataType.BYTE
            isWord -> DataType.WORD
            isFloat -> DataType.FLOAT
            isString -> DataType.STR        // @todo which string?
            isArray -> DataType.ARRAY       // @todo byte/word array?
            else -> throw FatalAstException("literalvalue has no value")
        }
    }
}


class RangeExpr(var from: IExpression, var to: IExpression) : IExpression {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        from.linkParents(this)
        to.linkParents(this)
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
}


class RegisterExpr(val register: Register) : IExpression {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun constValue(namespace: INameScope): LiteralValue? = null
    override fun process(processor: IAstProcessor) = this
    override fun referencesIdentifier(name: String): Boolean  = false

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


data class IdentifierReference(val nameInSource: List<String>) : IExpression {
    override var position: Position? = null
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
}


class PostIncrDecr(var target: AssignTarget, val operator: String) : IStatement {
    override var position: Position? = null
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


class Jump(val address: Int?, val identifier: IdentifierReference?) : IStatement {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    override fun toString(): String {
        return "Jump(addr: $address, identifier: $identifier, target:  pos=$position)"
    }
}


class FunctionCall(override var target: IdentifierReference, override var arglist: List<IExpression>) : IExpression, IFunctionCall {
    override var position: Position? = null
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
                "log" -> builtinLog(arglist, position, namespace)
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
                "any" -> builtinAny(arglist, position, namespace)
                "all" -> builtinAll(arglist, position, namespace)
                "floor" -> builtinFloor(arglist, position, namespace)
                "ceil" -> builtinCeil(arglist, position, namespace)
                "lsl" -> builtinLsl(arglist, position, namespace)
                "lsr" -> builtinLsr(arglist, position, namespace)
                "rol" -> throw ExpressionError("builtin function rol can't be used in expressions because it doesn't return a value", position)
                "rol2" -> throw ExpressionError("builtin function rol2 can't be used in expressions because it doesn't return a value", position)
                "ror" -> throw ExpressionError("builtin function ror can't be used in expressions because it doesn't return a value", position)
                "ror2" -> throw ExpressionError("builtin function ror2 can't be used in expressions because it doesn't return a value", position)
                "P_carry" -> throw ExpressionError("builtin function P_carry can't be used in expressions because it doesn't return a value", position)
                "P_irqd" -> throw ExpressionError("builtin function P_irqd can't be used in expressions because it doesn't return a value", position)
                else -> null
            }
            if(withDatatypeCheck) {
                val resultDt = this.resultingDatatype(namespace)
                if(resultValue==null)
                    return resultValue
                when(resultDt) {
                    DataType.BYTE -> if(resultValue.isByte) return resultValue
                    DataType.WORD -> if(resultValue.isWord) return resultValue
                    DataType.FLOAT -> if(resultValue.isFloat) return resultValue
                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> if(resultValue.isString) return resultValue
                    DataType.ARRAY -> if(resultValue.isArray) return resultValue
                    DataType.ARRAY_W -> if(resultValue.isArray) return resultValue
                    DataType.MATRIX -> TODO("expected matrix as constvalue this is not yet supported")
                }
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
        val stmt = target.targetStatement(namespace)
        if(stmt is BuiltinFunctionStatementPlaceholder) {
            if(target.nameInSource[0] == "P_carry" || target.nameInSource[0]=="P_irqd") {
                return null // these have no return value
            }
            return DataType.BYTE        // @todo table lookup to determine result type of builtin function call
        }
        else if(stmt is Subroutine) {
            if(stmt.returnvalues.isEmpty()) {
                return null     // no return value
            }
            if(stmt.returnvalues.size==1) {
                return when(stmt.returnvalues[0].register) {
                    Register.A, Register.X, Register.Y -> DataType.BYTE
                    Register.AX, Register.AY, Register.XY -> DataType.WORD
                    else -> TODO("return type for non-register result from subroutine $stmt")
                }
            }
            TODO("return type for subroutine with multiple return values $stmt")
        }
        TODO("datatype of functioncall to $stmt")
    }

}


class FunctionCallStatement(override var target: IdentifierReference, override var arglist: List<IExpression>) : IStatement, IFunctionCall {
    override var position: Position? = null
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


class InlineAssembly(val assembly: String) : IStatement {
    override var position: Position? = null
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
                      override var statements: MutableList<IStatement>) : IStatement, INameScope {
    override var position: Position? = null
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


data class SubroutineParameter(val name: String, val register: Register?, val statusflag: Statusflag?) : Node {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
}


data class SubroutineReturnvalue(val register: Register?, val statusflag: Statusflag?, val clobbered: Boolean) : Node {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
}


class IfStatement(var condition: IExpression,
                       var statements: List<IStatement>, var
                       elsepart: List<IStatement>) : IStatement {
    override var position: Position? = null
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
                       var statements: List<IStatement>, var
                       elsepart: List<IStatement>) : IStatement {
    override var position: Position? = null
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


/***************** Antlr Extension methods to create AST ****************/

fun il65Parser.ModuleContext.toAst(name: String, withPosition: Boolean) : Module {
    val module = Module(name, modulestatement().asSequence().map { it.toAst(withPosition) }.toMutableList())
    module.position = toPosition(withPosition)
    return module
}


/************** Helper extension methods (private) ************/

private fun ParserRuleContext.toPosition(withPosition: Boolean) : Position? {
    val file = Paths.get(this.start.inputStream.sourceName).fileName.toString()
    return if (withPosition)
        // note: be ware of TAB characters in the source text, they count as 1 column...
        Position(file, start.line, start.charPositionInLine, stop.charPositionInLine+stop.text.length)
    else
        null
}


private fun il65Parser.ModulestatementContext.toAst(withPosition: Boolean) : IStatement {
    val directive = directive()?.toAst(withPosition)
    if(directive!=null) return directive

    val block = block()?.toAst(withPosition)
    if(block!=null) return block

    throw FatalAstException(text)
}


private fun il65Parser.BlockContext.toAst(withPosition: Boolean) : IStatement {
    val block= Block(identifier().text,
            integerliteral()?.toAst()?.intvalue,
            statement_block().toAst(withPosition))
    block.position = toPosition(withPosition)
    return block
}


private fun il65Parser.Statement_blockContext.toAst(withPosition: Boolean): MutableList<IStatement>
        = statement().asSequence().map { it.toAst(withPosition) }.toMutableList()


private fun il65Parser.StatementContext.toAst(withPosition: Boolean) : IStatement {
    vardecl()?.let {
        val decl= VarDecl(VarDeclType.VAR,
                it.datatype().toAst(),
                it.arrayspec()?.toAst(withPosition),
                it.identifier().text,
                null)
        decl.position = it.toPosition(withPosition)
        return decl
    }

    varinitializer()?.let {
        val decl= VarDecl(VarDeclType.VAR,
                it.datatype().toAst(),
                it.arrayspec()?.toAst(withPosition),
                it.identifier().text,
                it.expression().toAst(withPosition))
        decl.position = it.toPosition(withPosition)
        return decl
    }

    constdecl()?.let {
        val cvarinit = it.varinitializer()
        val decl = VarDecl(VarDeclType.CONST,
                cvarinit.datatype().toAst(),
                cvarinit.arrayspec()?.toAst(withPosition),
                cvarinit.identifier().text,
                cvarinit.expression().toAst(withPosition))
        decl.position = cvarinit.toPosition(withPosition)
        return decl
    }

    memoryvardecl()?.let {
        val mvarinit = it.varinitializer()
        val decl = VarDecl(VarDeclType.MEMORY,
                mvarinit.datatype().toAst(),
                mvarinit.arrayspec()?.toAst(withPosition),
                mvarinit.identifier().text,
                mvarinit.expression().toAst(withPosition))
        decl.position = mvarinit.toPosition(withPosition)
        return decl
    }

    assignment()?.let {
        val ast =Assignment(it.assign_target().toAst(withPosition),
                null, it.expression().toAst(withPosition))
        ast.position = it.toPosition(withPosition)
        return ast
    }

    augassignment()?.let {
        val aug= Assignment(it.assign_target().toAst(withPosition),
                it.operator.text,
                it.expression().toAst(withPosition))
        aug.position = it.toPosition(withPosition)
        return aug
    }

    postincrdecr()?.let {
        val ast = PostIncrDecr(it.assign_target().toAst(withPosition), it.operator.text)
        ast.position = it.toPosition(withPosition)
        return ast
    }

    val directive = directive()?.toAst(withPosition)
    if(directive!=null) return directive

    val label = labeldef()?.toAst(withPosition)
    if(label!=null) return label

    val jump = unconditionaljump()?.toAst(withPosition)
    if(jump!=null) return jump

    val fcall = functioncall_stmt()?.toAst(withPosition)
    if(fcall!=null) return fcall

    val ifstmt = if_stmt()?.toAst(withPosition)
    if(ifstmt!=null) return ifstmt

    val returnstmt = returnstmt()?.toAst(withPosition)
    if(returnstmt!=null) return returnstmt

    val sub = subroutine()?.toAst(withPosition)
    if(sub!=null) return sub

    val asm = inlineasm()?.toAst(withPosition)
    if(asm!=null) return asm

    val branchstmt = branch_stmt()?.toAst(withPosition)
    if(branchstmt!=null) return branchstmt

    throw FatalAstException("unprocessed source text: $text")
}

private fun il65Parser.Functioncall_stmtContext.toAst(withPosition: Boolean): IStatement {
    val location =
            if(identifier()!=null) identifier()?.toAst(withPosition)
            else scoped_identifier()?.toAst(withPosition)
    val fcall = if(expression_list()==null)
        FunctionCallStatement(location!!, emptyList())
    else
        FunctionCallStatement(location!!, expression_list().toAst(withPosition))
    fcall.position = toPosition(withPosition)
    return fcall
}


private fun il65Parser.FunctioncallContext.toAst(withPosition: Boolean): FunctionCall {
    val location =
            if(identifier()!=null) identifier()?.toAst(withPosition)
            else scoped_identifier()?.toAst(withPosition)
    val fcall = if(expression_list()==null)
        FunctionCall(location!!, emptyList())
    else
        FunctionCall(location!!, expression_list().toAst(withPosition))
    fcall.position = toPosition(withPosition)
    return fcall
}


private fun il65Parser.InlineasmContext.toAst(withPosition: Boolean): IStatement {
    val asm = InlineAssembly(INLINEASMBLOCK().text)
    asm.position = toPosition(withPosition)
    return asm
}


private fun il65Parser.ReturnstmtContext.toAst(withPosition: Boolean) : IStatement {
    val values = expression_list()
    return Return(values?.toAst(withPosition) ?: emptyList())
}

private fun il65Parser.UnconditionaljumpContext.toAst(withPosition: Boolean): IStatement {

    val address = integerliteral()?.toAst()?.intvalue
    val identifier =
            if(identifier()!=null) identifier()?.toAst(withPosition)
            else scoped_identifier()?.toAst(withPosition)

    val jump = Jump(address, identifier)
    jump.position = toPosition(withPosition)
    return jump
}


private fun il65Parser.LabeldefContext.toAst(withPosition: Boolean): IStatement {
    val lbl = Label(this.children[0].text)
    lbl.position = toPosition(withPosition)
    return lbl
}


private fun il65Parser.SubroutineContext.toAst(withPosition: Boolean) : Subroutine {
    val sub = Subroutine(identifier().text,
            if(sub_params()==null) emptyList() else sub_params().toAst(),
            if(sub_returns()==null) emptyList() else sub_returns().toAst(),
            sub_address()?.integerliteral()?.toAst()?.intvalue,
            if(statement_block()==null) mutableListOf() else statement_block().toAst(withPosition))
    sub.position = toPosition(withPosition)
    return sub
}


private fun il65Parser.Sub_paramsContext.toAst(): List<SubroutineParameter> =
        sub_param().map {
            SubroutineParameter(it.identifier().text, it.register()?.toAst(), it.statusflag()?.toAst())
        }


private fun il65Parser.Sub_returnsContext.toAst(): List<SubroutineReturnvalue> =
        sub_return().map {
            val isClobber = it.childCount==2 && it.children[1].text == "?"
            SubroutineReturnvalue(it.register()?.toAst(), it.statusflag()?.toAst(), isClobber)
        }


private fun il65Parser.Assign_targetContext.toAst(withPosition: Boolean) : AssignTarget {
    val register = register()?.toAst()
    val identifier = identifier()
    val result = if(identifier!=null)
        AssignTarget(register, identifier.toAst(withPosition))
    else
        AssignTarget(register, scoped_identifier()?.toAst(withPosition))
    result.position = toPosition(withPosition)
    return result
}


private fun il65Parser.RegisterContext.toAst() = Register.valueOf(text.toUpperCase())

private fun il65Parser.StatusflagContext.toAst() = Statusflag.valueOf(text)

private fun il65Parser.DatatypeContext.toAst() = DataType.valueOf(text.toUpperCase())


private fun il65Parser.ArrayspecContext.toAst(withPosition: Boolean) : ArraySpec {
    val spec = ArraySpec(
            expression(0).toAst(withPosition),
            if (expression().size > 1) expression(1).toAst(withPosition) else null)
    spec.position = toPosition(withPosition)
    return spec
}


private fun il65Parser.DirectiveContext.toAst(withPosition: Boolean) : Directive {
    val dir = Directive(directivename.text, directivearg().map { it.toAst(withPosition) })
    dir.position = toPosition(withPosition)
    return dir
}


private fun il65Parser.DirectiveargContext.toAst(withPosition: Boolean) : DirectiveArg {
    val darg = DirectiveArg(stringliteral()?.text,
            identifier()?.text,
            integerliteral()?.toAst()?.intvalue)
    darg.position = toPosition(withPosition)
    return darg
}


private fun il65Parser.IntegerliteralContext.toAst(): ByteOrWordLiteral {
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
        il65Parser.DEC_INTEGER -> makeLiteral(integerPart, 10, wordsuffix()!=null)
        il65Parser.HEX_INTEGER -> makeLiteral(integerPart.substring(1), 16, wordsuffix()!=null)
        il65Parser.BIN_INTEGER -> makeLiteral(integerPart.substring(1), 2, wordsuffix()!=null)
        else -> throw FatalAstException(terminal.text)
    }
}


private fun il65Parser.ExpressionContext.toAst(withPosition: Boolean) : IExpression {

    val litval = literalvalue()
    if(litval!=null) {
        val booleanlit = litval.booleanliteral()?.toAst()
        val value =
                if(booleanlit!=null)
                    LiteralValue(bytevalue = if(booleanlit) 1 else 0)
                else {
                    val intLit = litval.integerliteral()?.toAst()
                    if(intLit!=null) {
                        when(intLit.datatype) {
                            DataType.BYTE -> LiteralValue(bytevalue = intLit.intvalue.toShort())
                            DataType.WORD -> LiteralValue(wordvalue = intLit.intvalue)
                            else -> throw FatalAstException("invalid datatype for integer literal")
                        }
                    } else {
                        LiteralValue(null, null,
                                litval.floatliteral()?.toAst(),
                                litval.stringliteral()?.text,
                                litval.arrayliteral()?.toAst(withPosition))
                    }
                }
        value.position = litval.toPosition(withPosition)
        return value
    }

    if(register()!=null) {
        val reg = RegisterExpr(register().toAst())
        reg.position = register().toPosition(withPosition)
        return reg
    }

    if(identifier()!=null)
        return identifier().toAst(withPosition)

    if(scoped_identifier()!=null)
        return scoped_identifier().toAst(withPosition)

    if(bop!=null) {
        val expr = BinaryExpression(left.toAst(withPosition),
                bop.text,
                right.toAst(withPosition))
        expr.position = toPosition(withPosition)
        return expr
    }

    if(prefix!=null) {
        val expr = PrefixExpression(prefix.text,
                expression(0).toAst(withPosition))
        expr.position = toPosition(withPosition)
        return expr
    }

    val funcall = functioncall()?.toAst(withPosition)
    if(funcall!=null) return funcall

    if (rangefrom!=null && rangeto!=null) {
        val rexp = RangeExpr(rangefrom.toAst(withPosition), rangeto.toAst(withPosition))
        rexp.position = toPosition(withPosition)
        return rexp
    }

    if(childCount==3 && children[0].text=="(" && children[2].text==")")
        return expression(0).toAst(withPosition)        // expression within ( )

    throw FatalAstException(text)
}


private fun il65Parser.Expression_listContext.toAst(withPosition: Boolean) = expression().map{ it.toAst(withPosition) }


private fun il65Parser.IdentifierContext.toAst(withPosition: Boolean) : IdentifierReference {
    val ident = IdentifierReference(listOf(text))
    ident.position = toPosition(withPosition)
    return ident
}


private fun il65Parser.Scoped_identifierContext.toAst(withPosition: Boolean) : IdentifierReference {
    val ident = IdentifierReference(NAME().map { it.text })
    ident.position = toPosition(withPosition)
    return ident
}


private fun il65Parser.FloatliteralContext.toAst() = text.toDouble()


private fun il65Parser.BooleanliteralContext.toAst() = when(text) {
    "true" -> true
    "false" -> false
    else -> throw FatalAstException(text)
}


private fun il65Parser.ArrayliteralContext.toAst(withPosition: Boolean) =
        expression().asSequence().map { it.toAst(withPosition) }.toMutableList()


private fun il65Parser.If_stmtContext.toAst(withPosition: Boolean): IfStatement {
    val condition = expression().toAst(withPosition)
    val statements = statement_block()?.toAst(withPosition) ?: listOf(statement().toAst(withPosition))
    val elsepart = else_part()?.toAst(withPosition) ?: emptyList()
    val result = IfStatement(condition, statements, elsepart)
    result.position = toPosition(withPosition)
    return result
}

private fun il65Parser.Else_partContext.toAst(withPosition: Boolean): List<IStatement> {
    return statement_block()?.toAst(withPosition) ?: listOf(statement().toAst(withPosition))
}


private fun il65Parser.Branch_stmtContext.toAst(withPosition: Boolean): IStatement {
    val branchcondition = branchcondition().toAst(withPosition)
    val statements = statement_block()?.toAst(withPosition) ?: listOf(statement().toAst(withPosition))
    val elsepart = else_part()?.toAst(withPosition) ?: emptyList()
    val result = BranchStatement(branchcondition, statements, elsepart)
    result.position = toPosition(withPosition)
    return result
}

private fun il65Parser.BranchconditionContext.toAst(withPosition: Boolean) = BranchCondition.valueOf(text.substringAfter('_').toUpperCase())
