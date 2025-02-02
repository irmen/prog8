package prog8.ast

import prog8.ast.expressions.Expression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstVisitor
import prog8.code.core.*
import prog8.code.source.SourceCode


object ParentSentinel : Node {
    override val position = Position("<<sentinel>>", 0, 0, 0)
    override var parent: Node = this
    override fun linkParents(parent: Node) {}
    override fun replaceChildNode(node: Node, replacement: Node) {
        replacement.parent = this
    }

    override fun copy(): Node = throw FatalAstException("should never duplicate a ParentSentinel")
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = false
}


interface IFunctionCall {
    var target: IdentifierReference
    val args: MutableList<Expression>
    val position: Position
    var parent: Node             // will be linked correctly later (late init)
}

interface IStatementContainer {
    val statements: MutableList<Statement>

    fun remove(stmt: Statement) {
        if(!statements.remove(stmt))
            throw FatalAstException("stmt to remove wasn't found in scope")
    }

    fun getAllLabels(label: String): List<Label> {
        val result = mutableListOf<Label>()

        fun find(scope: IStatementContainer) {
            scope.statements.forEach {
                when(it) {
                    is Label -> result.add(it)
                    is IStatementContainer -> find(it)
                    is IfElse -> {
                        find(it.truepart)
                        find(it.elsepart)
                    }
                    is UntilLoop -> find(it.body)
                    is RepeatLoop -> find(it.body)
                    is WhileLoop -> find(it.body)
                    is When -> it.choices.forEach { choice->find(choice.statements) }
                    else -> { /* do nothing */ }
                }
            }
        }

        find(this)
        return result
    }

    fun indexOfChild(stmt: Statement): Int {
        val idx = statements.indexOfFirst { it===stmt }
        if(idx>=0)
            return idx
        else
            throw FatalAstException("attempt to find a non-child")
    }

    fun isEmpty(): Boolean = statements.isEmpty()
    fun isNotEmpty(): Boolean = statements.isNotEmpty()

    fun searchSymbol(name: String): Statement? {
        // this is called quite a lot and could perhaps be optimized a bit more,
        // but adding a memoization cache didn't make much of a practical runtime difference...
        for (stmt in statements) {
            when(stmt) {
                is VarDecl -> if(stmt.name==name || stmt.names.contains(name)) return stmt
                is INamedStatement -> if(stmt.name==name) return stmt
                is AnonymousScope -> {
                    val found = stmt.searchSymbol(name)
                    if(found!=null)
                        return found
                }
                is IfElse -> {
                    val found = stmt.truepart.searchSymbol(name) ?: stmt.elsepart.searchSymbol(name)
                    if(found!=null)
                        return found
                }
                is ConditionalBranch -> {
                    val found = stmt.truepart.searchSymbol(name) ?: stmt.elsepart.searchSymbol(name)
                    if(found!=null)
                        return found
                }
                is ForLoop -> {
                    val found = stmt.body.searchSymbol(name)
                    if(found!=null)
                        return found
                }
                is WhileLoop -> {
                    val found = stmt.body.searchSymbol(name)
                    if(found!=null)
                        return found
                }
                is RepeatLoop -> {
                    val found = stmt.body.searchSymbol(name)
                    if(found!=null)
                        return found
                }
                is UntilLoop -> {
                    val found = stmt.body.searchSymbol(name)
                    if(found!=null)
                        return found
                }
                is When -> {
                    stmt.choices.forEach {
                        val found = it.statements.searchSymbol(name)
                        if(found!=null)
                            return found
                    }
                }
                is Alias -> if(stmt.alias==name) return stmt
                else -> {
                    // NOTE: when other nodes containing AnonymousScope are introduced,
                    //       these should be added here as well to look into!
                }
            }
        }

        return null
    }

    val allDefinedSymbols: Sequence<Pair<String, Statement>>
        get() {
            return statements.asSequence().filterIsInstance<INamedStatement>().map { Pair(it.name, it as Statement) }
        }
}

interface INameScope: IStatementContainer, INamedStatement {
    fun subScope(name: String): INameScope?  = statements.firstOrNull { it is INameScope && it.name==name } as? INameScope

    fun lookup(scopedName: List<String>) : Statement? {
        return if(scopedName.size>1)
            lookupQualified(scopedName)
        else {
            lookupUnqualified(scopedName[0])
        }
    }

    private fun lookupQualified(scopedName: List<String>): Statement? {
        // a scoped name refers to a name in another namespace, and stars from the root.

// experimental code to be able to alias blocks too:
//        val stmt = this.lookup(listOf(scopedName[0])) ?: return null
//        if(stmt is Alias) {
//            val block = this.lookup(stmt.target.nameInSource) ?: return null
//            var statement = block as Statement?
//            for(name in scopedName.drop(1)) {
//                statement = (statement as? IStatementContainer)?.searchSymbol(name)
//                if(statement==null)
//                    return null
//            }
//            return statement
//        }
        for(module in (this as Node).definingModule.program.modules) {
            val block = module.searchSymbol(scopedName[0])
            if(block!=null) {
                var statement = block
                for(name in scopedName.drop(1)) {
                    statement = (statement as? IStatementContainer)?.searchSymbol(name)
                    if(statement==null)
                        return null
                }
                return statement
            }
        }
        return null
    }

    private fun lookupUnqualified(name: String): Statement? {
        val builtinFunctionsNames = (this as Node).definingModule.program.builtinFunctions.names
        if(name in builtinFunctionsNames) {
            // builtin functions always exist, return a dummy placeholder for them
            val builtinPlaceholder = Label("builtin::$name", this.position)
            builtinPlaceholder.parent = ParentSentinel
            return builtinPlaceholder
        }

        // search for the unqualified name in the current scope (and possibly in any anonymousscopes it may contain)
        var statementScope = this
        while(statementScope !is GlobalNamespace) {
            val symbol = statementScope.searchSymbol(name)
            if(symbol!=null)
                return symbol
            else
                statementScope = (statementScope as Node).definingScope
        }

        // still not found, maybe it is a symbol in another module
        for(module in (this as Node).definingModule.program.modules) {
            val stmt = module.searchSymbol(name)
            if(stmt!=null)
                return stmt
        }

        return null
    }

//    private fun getNamedSymbol(name: String): Statement? =
//        statements.singleOrNull { it is INamedStatement && it.name==name }

    val containsCodeOrVars get() = statements.any { it !is Directive || it.directive == "%asminclude" || it.directive == "%asm" }
    val containsNoCodeNorVars get() = !containsCodeOrVars
}


interface Node {
    val position: Position
    var parent: Node             // will be linked correctly later (late init)
    fun linkParents(parent: Node)

    val definingModule: Module
        get() {
            if (this is Module)
                return this
            return findParentNode<Module>(this)!!
        }

    val definingSubroutine: Subroutine? get() = findParentNode<Subroutine>(this)

    val definingScope: INameScope
        get() {
            val scope = findParentNode<INameScope>(this)
            if (scope != null) {
                return scope
            }
            if (this is Label && this.name.startsWith("builtin::")) {
                return BuiltinFunctionScopePlaceholder
            }
            if (this is GlobalNamespace)
                return this
            throw FatalAstException("scope missing from $this")
        }

    val definingBlock: Block
        get() {
            if (this is Block)
                return this
            return findParentNode<Block>(this)!!
        }

    val containingStatement: Statement
        get() {
            if (this is Statement)
                return this
            return findParentNode<Statement>(this)!!
        }

    fun replaceChildNode(node: Node, replacement: Node)
    fun copy(): Node
    fun referencesIdentifier(nameInSource: List<String>): Boolean
}


// find the parent node of a specific type or interface
// (useful to figure out in what namespace/block something is defined, etc.)
inline fun <reified T> findParentNode(node: Node): T? {
    var candidate = node.parent
    while(candidate !is T && candidate !is ParentSentinel)
        candidate = candidate.parent
    return if(candidate is ParentSentinel)
        null
    else
        candidate as T
}


open class Module(final override val statements: MutableList<Statement>,
                  final override val position: Position,
                  val source: SourceCode
) : Node, INameScope {

    override lateinit var parent: Node
    lateinit var program: Program

    override val name = source.origin
            .substringBeforeLast(".")
            .substringAfterLast("/")
            .substringAfterLast("\\")

    val loadAddress: Pair<UInt, Position>? by lazy {
        val address = (statements.singleOrNull { it is Directive && it.directive == "%address" } as? Directive)
        if(address==null || address.args.single().int==null)
            null
        else
            Pair(address.args.single().int!!, address.position)
    }

    val memtopAddress: Pair<UInt, Position>? by lazy {
        val address = (statements.singleOrNull { it is Directive && it.directive == "%memtop" } as? Directive)
        if(address==null || address.args.single().int==null)
            null
        else
            Pair(address.args.single().int!!, address.position)
    }

    override fun linkParents(parent: Node) {
        require(parent is GlobalNamespace)
        this.parent = parent
        statements.forEach {it.linkParents(this)}
    }

    override val definingScope: INameScope
        get() = program.namespace
    override fun replaceChildNode(node: Node, replacement: Node) {
        require(node is Statement && replacement is Statement)
        val idx = statements.indexOfFirst { it===node }
        statements[idx] = replacement
        replacement.parent = this
    }

    override fun copy(): Node = throw NotImplementedError("no support for duplicating a Module")

    override fun toString() = "Module(name=$name, pos=$position, lib=${isLibrary})"
    override fun referencesIdentifier(nameInSource: List<String>): Boolean = statements.any { it.referencesIdentifier(nameInSource) }
    fun options() = statements.filter { it is Directive && it.directive == "%option" }.flatMap { (it as Directive).args }.map {it.string!!}.toSet()

    fun accept(visitor: IAstVisitor) = visitor.visit(this)
    fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    val textEncoding: Encoding by lazy {
        val encoding = (statements.singleOrNull { it is Directive && it.directive == "%encoding" } as? Directive)
        if(encoding!=null)
            Encoding.entries.first { it.prefix==encoding.args[0].string }
        else
            program.encoding.defaultEncoding
    }

    val isLibrary get() = source.isFromLibrary
}


class GlobalNamespace(val modules: MutableList<Module>): Node, INameScope {
    override val name = "<<<global>>>"
    override val position = Position("<<<global>>>", 0, 0, 0)
    override val statements = mutableListOf<Statement>()        // not used
    override var parent: Node = ParentSentinel

    override fun copy(): Node = throw NotImplementedError("no support for duplicating a GlobalNamespace")

    override fun lookup(scopedName: List<String>): Statement? {
        throw NotImplementedError("use lookup on actual ast node instead")
    }

    override fun linkParents(parent: Node) {
        modules.forEach { it.linkParents(this) }
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        throw FatalAstException("cannot replace anything in the namespace")
    }

    override fun referencesIdentifier(nameInSource: List<String>): Boolean = modules.any { it.referencesIdentifier(nameInSource) }
}

internal object BuiltinFunctionScopePlaceholder : INameScope {
    override val name = "<<builtin-functions-scope-placeholder>>"
    override val statements = mutableListOf<Statement>()
}


fun defaultZero(dt: BaseDataType, position: Position) = when(dt) {
    BaseDataType.BOOL -> NumericLiteral(BaseDataType.BOOL, 0.0,  position)
    BaseDataType.UBYTE -> NumericLiteral(BaseDataType.UBYTE, 0.0,  position)
    BaseDataType.BYTE -> NumericLiteral(BaseDataType.BYTE, 0.0,  position)
    BaseDataType.UWORD, BaseDataType.STR -> NumericLiteral(BaseDataType.UWORD, 0.0, position)
    BaseDataType.WORD -> NumericLiteral(BaseDataType.WORD, 0.0, position)
    BaseDataType.FLOAT -> NumericLiteral(BaseDataType.FLOAT, 0.0, position)
    else -> throw FatalAstException("can only determine default zero value for a numeric type")
}

fun defaultZero(idt: InferredTypes.InferredType, position: Position) = defaultZero(idt.getOrUndef().base, position)