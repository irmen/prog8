package il65.ast

import il65.functions.*
import il65.parser.il65Parser
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode
import java.nio.file.Paths


/**************************** AST Data classes ****************************/

enum class DataType {
    BYTE,
    WORD,
    FLOAT,
    STR,
    STR_P,
    STR_S,
    STR_PS
}

enum class Register {
    A,
    X,
    Y,
    AX,
    AY,
    XY,
    PC,
    PI,
    PZ,
    PN,
    PV
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

class ExpressionException(message: String, val position: Position?) : AstException(message) {
    override fun toString(): String {
        val location = position?.toString() ?: ""
        return "$location Error: $message"
    }
}


data class Position(val file: String, val line: Int, val startCol: Int, val endCol: Int) {
    override fun toString(): String = "[$file: line $line col ${startCol+1}-${endCol+1}]"
}


interface IAstProcessor {
    fun process(module: Module) {
        module.statements = module.statements.map { it.process(this) }.toMutableList()
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
        block.statements = block.statements.map { it.process(this) }.toMutableList()
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

    fun process(range: RangeExpr): IExpression {
        range.from = range.from.process(this)
        range.to = range.to.process(this)
        return range
    }

    fun process(label: Label): IStatement {
        return label
    }
}


interface Node {
    var position: Position?      // optional for the sake of easy unit testing
    var parent: Node             // will be linked correctly later (late init)
    fun linkParents(parent: Node)
}


interface IStatement : Node {
    fun process(processor: IAstProcessor) : IStatement
    fun makeScopedName(name: String): List<String> {
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

    fun subScopes() = statements.filter { it is INameScope } .map { it as INameScope }.associate { it.name to it }

    fun definedNames() = statements.filter { it is Label || it is VarDecl }
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
            return foundScope.definedNames()[scopedName.last()]
                    ?:
                    foundScope.subScopes()[scopedName.last()] as IStatement?
        } else {
            // unqualified name, find the scope the statement is in, look in that first
            var statementScope: Node = statement
            while(true) {
                while (statementScope !is INameScope && statementScope !is ParentSentinel)
                    statementScope = statementScope.parent
                if (statementScope is ParentSentinel)
                    return null
                val localScope = statementScope as INameScope
                val result = localScope.definedNames()[scopedName[0]]
                if (result != null)
                    return result
                val subscope = localScope.subScopes()[scopedName[0]] as IStatement?
                if (subscope != null)
                    return subscope
                // not found in this scope, look one higher up
                statementScope = statementScope.parent
            }
        }
    }

    fun debugPrint() {
        fun printNames(indent: Int, namespace: INameScope) {
            println(" ".repeat(4*indent) + "${namespace.name}   ->  ${namespace::class.simpleName} at ${namespace.position}")
            namespace.definedNames().forEach {
                println(" ".repeat(4 * (1 + indent)) + "${it.key}   ->  ${it.value::class.simpleName} at ${it.value.position}")
            }
            namespace.subScopes().forEach {
                printNames(indent+1, it.value)
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
data class AnonymousStatementList(override var parent: Node, var statements: List<IStatement>) : IStatement {
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


object ParentSentinel : Node {
    override var position: Position? = null
    override var parent: Node = this
    override fun linkParents(parent: Node) {}
}


data class Module(override val name: String,
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

    fun namespace(): INameScope {
        class GlobalNamespace(override val name: String,
                              override var statements: MutableList<IStatement>,
                              override val position: Position?) : INameScope {

            private val scopedNamesUsed: MutableSet<String> = mutableSetOf("main")      // main is always used

            override fun usedNames(): Set<String>  = scopedNamesUsed

            override fun lookup(scopedName: List<String>, statement: Node): IStatement? {
                if(BuiltinFunctionNames.contains(scopedName.last())) {
                    // pseudo functions always exist, return a dummy statement for them
                    val pseudo = Label("pseudo::${scopedName.last()}")
                    pseudo.position = statement.position
                    pseudo.parent = ParentSentinel
                    return pseudo
                }
                val stmt = super.lookup(scopedName, statement)
                if(stmt!=null) {
                    val targetScopedName = when(stmt) {
                        is Label -> stmt.makeScopedName(stmt.name)
                        is VarDecl -> stmt.makeScopedName(stmt.name)
                        is Block -> stmt.makeScopedName(stmt.name)
                        is Subroutine -> stmt.makeScopedName(stmt.name)
                        else -> throw NameError("wrong identifier target: $stmt", stmt.position)
                    }
                    registerUsedName(targetScopedName.joinToString("."))
                }
                return stmt
            }

            override fun registerUsedName(name: String) {
                scopedNamesUsed.add(name)
            }
        }

        return GlobalNamespace("<<<global>>>", statements, position)
    }

    override fun usedNames(): Set<String> = throw NotImplementedError("not implemented on sub-scopes")
    override fun registerUsedName(name: String) = throw NotImplementedError("not implemented on sub-scopes")
}


data class Block(override val name: String,
                 val address: Int?,
                 override var statements: MutableList<IStatement>) : IStatement, INameScope {
    override var position: Position? = null
    override lateinit var parent: Node

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

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}


data class Return(var values: List<IExpression>) : IStatement {
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
}


data class ArraySpec(var x: IExpression, var y: IExpression?) : Node {
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

data class VarDecl(val type: VarDeclType,
                   val datatype: DataType,
                   val arrayspec: ArraySpec?,
                   val name: String,
                   var value: IExpression?) : IStatement {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        arrayspec?.linkParents(this)
        value?.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)

    val isScalar = arrayspec==null
    val isArray = arrayspec!=null && arrayspec.y==null
    val isMatrix = arrayspec?.y != null
    fun arraySizeX(namespace: INameScope) : Int? {
        return arrayspec?.x?.constValue(namespace)?.intvalue
    }
    fun arraySizeY(namespace: INameScope) : Int? {
        return arrayspec?.y?.constValue(namespace)?.intvalue
    }
}


data class Assignment(var target: AssignTarget, val aug_op : String?, var value: IExpression) : IStatement {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        value.linkParents(this)
    }

    override fun process(processor: IAstProcessor): IStatement {
        target = target.process(processor)
        value = value.process(processor)
        return this
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
}


interface IExpression: Node {
    fun constValue(namespace: INameScope): LiteralValue?
    fun process(processor: IAstProcessor): IExpression
    fun referencesIdentifier(name: String): Boolean
}


// note: some expression elements are mutable, to be able to rewrite/process the expression tree

data class PrefixExpression(val operator: String, var expression: IExpression) : IExpression {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        expression.linkParents(this)
    }

    override fun constValue(namespace: INameScope): LiteralValue? = null
    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String) = expression.referencesIdentifier(name)
}


data class BinaryExpression(var left: IExpression, val operator: String, var right: IExpression) : IExpression {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        left.linkParents(this)
        right.linkParents(this)
    }

    override fun constValue(namespace: INameScope): LiteralValue? {
        throw ExpressionException("expression should have been optimized away into a single value, before const value was requested (this error is often caused by another)", position)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String) = left.referencesIdentifier(name) || right.referencesIdentifier(name)
}

data class LiteralValue(val intvalue: Int? = null,
                        val floatvalue: Double? = null,
                        val strvalue: String? = null,
                        val arrayvalue: List<IExpression>? = null) : IExpression {
    override var position: Position? = null
    override lateinit var parent: Node
    override fun referencesIdentifier(name: String) = arrayvalue?.any { it.referencesIdentifier(name) } ?: false

    fun asInt(errorIfNotNumeric: Boolean=true): Int? {
        return when {
            intvalue!=null -> intvalue
            floatvalue!=null -> floatvalue.toInt()
            else -> {
                if((strvalue!=null || arrayvalue!=null) && errorIfNotNumeric)
                    throw AstException("attempt to get int value from non-integer $this")
                else null
            }
        }
    }

    fun asFloat(errorIfNotNumeric: Boolean=true): Double? {
        return when {
            floatvalue!=null -> floatvalue
            intvalue!=null -> intvalue.toDouble()
            else -> {
                if((strvalue!=null || arrayvalue!=null) && errorIfNotNumeric)
                    throw AstException("attempt to get float value from non-integer $this")
                else null
            }
        }
    }

    fun asBoolean(): Boolean =
            (floatvalue!=null && floatvalue != 0.0) ||
            (intvalue!=null && intvalue != 0) ||
            (strvalue!=null && strvalue.isNotEmpty()) ||
            (arrayvalue != null && arrayvalue.isNotEmpty())

    override fun linkParents(parent: Node) {
        this.parent = parent
        arrayvalue?.forEach {it.linkParents(this)}
    }

    override fun constValue(namespace: INameScope): LiteralValue?  = this
    override fun process(processor: IAstProcessor) = this
}


data class RangeExpr(var from: IExpression, var to: IExpression) : IExpression {
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
}


data class RegisterExpr(val register: Register) : IExpression {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun constValue(namespace: INameScope): LiteralValue? = null
    override fun process(processor: IAstProcessor) = this
    override fun referencesIdentifier(name: String): Boolean  = false
}


data class IdentifierReference(val nameInSource: List<String>) : IExpression {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun constValue(namespace: INameScope): LiteralValue? {
        val node = namespace.lookup(nameInSource, this)
                ?:
                throw ExpressionException("undefined symbol: ${nameInSource.joinToString(".")}", position)
        val vardecl = node as? VarDecl
        if(vardecl==null) {
            throw ExpressionException("name should be a constant, instead of: ${node::class.simpleName}", position)
        } else if(vardecl.type!=VarDeclType.CONST) {
            return null
        }
        return vardecl.value?.constValue(namespace)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String): Boolean  = nameInSource.last() == name   // @todo is this correct all the time?
}


data class PostIncrDecr(var target: AssignTarget, val operator: String) : IStatement {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
    }

    override fun process(processor: IAstProcessor): IStatement {
        target = target.process(processor)
        return this
    }
}


data class Jump(val address: Int?, val identifier: IdentifierReference?) : IStatement {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        identifier?.linkParents(this)
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}


data class FunctionCall(override var target: IdentifierReference, override var arglist: List<IExpression>) : IExpression, IFunctionCall {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        arglist.forEach { it.linkParents(this) }
    }

    override fun constValue(namespace: INameScope): LiteralValue? {
        // if the function is a built-in function and the args are consts, should try to const-evaluate!
        if(target.nameInSource.size>1) return null
        try {
            return when (target.nameInSource[0]) {
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
                "_lsl" -> builtinLsl(arglist, position, namespace)
                "_lsr" -> builtinLsr(arglist, position, namespace)
                "_rol" -> throw ExpressionException("builtin function _rol can't be used in expressions because it doesn't return a value", position)
                "_rol2" -> throw ExpressionException("builtin function _rol2 can't be used in expressions because it doesn't return a value", position)
                "_ror" -> throw ExpressionException("builtin function _ror can't be used in expressions because it doesn't return a value", position)
                "_ror2" -> throw ExpressionException("builtin function _ror2 can't be used in expressions because it doesn't return a value", position)
                "_P_carry" -> throw ExpressionException("builtin function _P_carry can't be used in expressions because it doesn't return a value", position)
                "_P_irqd" -> throw ExpressionException("builtin function _P_irqd can't be used in expressions because it doesn't return a value", position)
                else -> null
            }
        }
        catch(x: NotConstArgumentException) {
            // const-evaluating the builtin function call failed.
            return null
        }
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
    override fun referencesIdentifier(name: String): Boolean = target.referencesIdentifier(name) || arglist.any{it.referencesIdentifier(name)}
}


data class FunctionCallStatement(override var target: IdentifierReference, override var arglist: List<IExpression>) : IStatement, IFunctionCall {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
        target.linkParents(this)
        arglist.forEach { it.linkParents(this) }
    }

    override fun process(processor: IAstProcessor) = processor.process(this)
}


data class InlineAssembly(val assembly: String) : IStatement {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }

    override fun process(processor: IAstProcessor) = this
}


data class Subroutine(override val name: String,
                      val parameters: List<SubroutineParameter>,
                      val returnvalues: List<SubroutineReturnvalue>,
                      val address: Int?,
                      override var statements: MutableList<IStatement>) : IStatement, INameScope {
    override var position: Position? = null
    override lateinit var parent: Node

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


data class SubroutineParameter(val name: String, val register: Register) : Node {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
}


data class SubroutineReturnvalue(val register: Register, val clobbered: Boolean) : Node {
    override var position: Position? = null
    override lateinit var parent: Node

    override fun linkParents(parent: Node) {
        this.parent = parent
    }
}


data class IfStatement(var condition: IExpression,
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


/***************** Antlr Extension methods to create AST ****************/

fun il65Parser.ModuleContext.toAst(name: String, withPosition: Boolean) : Module {
    val module = Module(name, modulestatement().map { it.toAst(withPosition) }.toMutableList())
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
            integerliteral()?.toAst(),
            statement_block().toAst(withPosition))
    block.position = toPosition(withPosition)
    return block
}


private fun il65Parser.Statement_blockContext.toAst(withPosition: Boolean): MutableList<IStatement>
        = statement().map { it.toAst(withPosition) }.toMutableList()


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

    throw FatalAstException(text)
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

    val address = integerliteral()?.toAst()
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
            sub_address()?.integerliteral()?.toAst(),
            if(statement_block()==null) mutableListOf() else statement_block().toAst(withPosition))
    sub.position = toPosition(withPosition)
    return sub
}


private fun il65Parser.Sub_paramsContext.toAst(): List<SubroutineParameter> =
        sub_param().map { SubroutineParameter(it.identifier().text, it.register().toAst()) }


private fun il65Parser.Sub_returnsContext.toAst(): List<SubroutineReturnvalue> =
        sub_return().map {
            val isClobber = it.childCount==2 && it.children[1].text == "?"
            SubroutineReturnvalue(it.register().toAst(), isClobber)
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
            integerliteral()?.toAst())
    darg.position = toPosition(withPosition)
    return darg
}


private fun il65Parser.IntegerliteralContext.toAst(): Int {
    val terminal: TerminalNode = children[0] as TerminalNode
    return when (terminal.symbol.type) {
        il65Parser.DEC_INTEGER -> text.toInt()
        il65Parser.HEX_INTEGER -> text.substring(1).toInt(16)
        il65Parser.BIN_INTEGER -> text.substring(1).toInt(2)
        else -> throw FatalAstException(terminal.text)
    }
}


private fun il65Parser.ExpressionContext.toAst(withPosition: Boolean) : IExpression {

    val litval = literalvalue()
    if(litval!=null) {
        val booleanlit = litval.booleanliteral()?.toAst()
        val value =
                if(booleanlit!=null)
                    LiteralValue(intvalue = if(booleanlit) 1 else 0)
                else
                    LiteralValue(litval.integerliteral()?.toAst(),
                            litval.floatliteral()?.toAst(),
                            litval.stringliteral()?.text,
                            litval.arrayliteral()?.toAst(withPosition))
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
        expression().map { it.toAst(withPosition) }


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
