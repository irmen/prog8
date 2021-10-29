package prog8.ast

import prog8.ast.base.*
import prog8.ast.expressions.Expression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.StringLiteralValue
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstVisitor
import prog8.compiler.IMemSizer
import prog8.parser.SourceCode

const val internedStringsModuleName = "prog8_interned_strings"


interface IAssignable {
    // just a tag for now
}

interface IFunctionCall {
    var target: IdentifierReference
    var args: MutableList<Expression>
}

interface IStatementContainer {
    val position: Position
    val parent: Node
    val statements: MutableList<Statement>
    fun linkParents(parent: Node)

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
                    is IfStatement -> {
                        find(it.truepart)
                        find(it.elsepart)
                    }
                    is UntilLoop -> find(it.body)
                    is RepeatLoop -> find(it.body)
                    is WhileLoop -> find(it.body)
                    is WhenStatement -> it.choices.forEach { choice->find(choice.statements) }
                    else -> { /* do nothing */ }
                }
            }
        }

        find(this)
        return result
    }

    fun nextSibling(stmt: Statement): Statement? {
        val nextIdx = statements.indexOfFirst { it===stmt } + 1
        return if(nextIdx < statements.size)
            statements[nextIdx]
        else
            null
    }

    fun previousSibling(stmt: Statement): Statement? {
        val previousIdx = statements.indexOfFirst { it===stmt } - 1
        return if(previousIdx>=0)
            statements[previousIdx]
        else
            null
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
//                is INamedStatement -> {
//                    if(stmt.name==name) return stmt
//                }
                is VarDecl -> {
                    if(stmt.name==name) return stmt
                }
                is Label -> {
                    if(stmt.name==name) return stmt
                }
                is Subroutine -> {
                    if(stmt.name==name)
                        return stmt
                }
                is AnonymousScope -> {
                    val found = stmt.searchSymbol(name)
                    if(found!=null)
                        return found
                }
                is IfStatement -> {
                    val found = stmt.truepart.searchSymbol(name) ?: stmt.elsepart.searchSymbol(name)
                    if(found!=null)
                        return found
                }
                is BranchStatement -> {
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
                is WhenStatement -> {
                    stmt.choices.forEach {
                        val found = it.statements.searchSymbol(name)
                        if(found!=null)
                            return found
                    }
                }
                else -> {
                    // NOTE: when other nodes containing AnonymousScope are introduced,
                    //       these should be added here as well to look into!
                }
            }
        }
        return null
    }

    val allDefinedSymbols: List<Pair<String, Statement>>
        get() {
            return statements.filterIsInstance<INamedStatement>().map { Pair(it.name, it as Statement) }
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
        // a scoped name refers to a name in another namespace.
        // look "up" from our current scope to search for the correct one.
        val localScope = this.subScope(scopedName[0])
        if(localScope!=null)
            return resolveLocally(localScope, scopedName.drop(1))

        var statementScope = this
        while(statementScope !is GlobalNamespace) {
            if(statementScope !is Module && statementScope.name==scopedName[0]) {
                return resolveLocally(statementScope, scopedName.drop(1))
            } else {
                statementScope = (statementScope as Node).definingScope
            }
        }

        // not found, try again but now assume it's a globally scoped name starting with the name of a block
        for(module in (this as Node).definingModule.program.modules) {
            module.statements.forEach {
                if(it is Block && it.name==scopedName[0])
                    return it.lookup(scopedName)
            }
        }
        return null
    }

    private fun resolveLocally(startScope: INameScope, name: List<String>): Statement? {
        var scope: INameScope? = startScope
        for(part in name.dropLast(1)) {
            scope = scope!!.subScope(part)
            if(scope==null)
                return null
        }
        return scope!!.searchSymbol(name.last())
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
        // if it's not found there, jump up one higher in the namespaces and try again.
        var statementScope = this
        while(statementScope !is GlobalNamespace) {
            val symbol = statementScope.searchSymbol(name)
            if(symbol!=null)
                return symbol
            else
                statementScope = (statementScope as Node).definingScope
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
}


/*********** Everything starts from here, the Program; zero or more modules *************/

class Program(val name: String,
              val builtinFunctions: IBuiltinFunctions,
              val memsizer: IMemSizer
): Node {
    private val _modules = mutableListOf<Module>()

    val modules: List<Module> = _modules
    val namespace: GlobalNamespace = GlobalNamespace(modules)

    init {
        // insert a container module for all interned strings later
        val internedStringsModule = Module(mutableListOf(), Position.DUMMY, SourceCode.Generated(internedStringsModuleName))
        val block = Block(internedStringsModuleName, null, mutableListOf(), true, Position.DUMMY)
        internedStringsModule.statements.add(block)

        _modules.add(0, internedStringsModule)
        internedStringsModule.linkParents(namespace)
        internedStringsModule.program = this
    }

    fun addModule(module: Module): Program {
        require(null == _modules.firstOrNull { it.name == module.name })
            { "module '${module.name}' already present" }
        _modules.add(module)
        module.linkIntoProgram(this)
        return this
    }

    fun removeModule(module: Module) = _modules.remove(module)

    fun moveModuleToFront(module: Module): Program {
        require(_modules.contains(module))
            { "Not a module of this program: '${module.name}'"}
        _modules.remove(module)
        _modules.add(0, module)
        return this
    }

    val allBlocks: List<Block>
        get() = modules.flatMap { it.statements.filterIsInstance<Block>() }

    val entrypoint: Subroutine
        get() {
            val mainBlocks = allBlocks.filter { it.name == "main" }
            return when (mainBlocks.size) {
                0 -> throw FatalAstException("no 'main' block")
                1 -> mainBlocks[0].subScope("start") as Subroutine
                else -> throw FatalAstException("more than one 'main' block")
            }
        }

    val toplevelModule: Module
        get() = modules.first { it.name!=internedStringsModuleName }

    val definedLoadAddress: Int
        get() = toplevelModule.loadAddress

    var actualLoadAddress: Int = 0
    private val internedStringsUnique = mutableMapOf<Pair<String, Boolean>, List<String>>()

    fun internString(string: StringLiteralValue): List<String> {
        // Move a string literal into the internal, deduplicated, string pool
        // replace it with a variable declaration that points to the entry in the pool.

        if(string.parent is VarDecl) {
            // deduplication can only be performed safely for known-const strings (=string literals OUTSIDE OF A VARDECL)!
            throw FatalAstException("cannot intern a string literal that's part of a vardecl")
        }

        fun getScopedName(string: StringLiteralValue): List<String> {
            val internedStringsBlock = modules
                .first { it.name == internedStringsModuleName }.statements
                .first { it is Block && it.name == internedStringsModuleName } as Block
            val varName = "string_${internedStringsBlock.statements.size}"
            val decl = VarDecl(
                VarDeclType.VAR, DataType.STR, ZeropageWish.NOT_IN_ZEROPAGE, null, varName, string,
                isArray = false, autogeneratedDontRemove = true, sharedWithAsm = false, position = string.position
            )
            internedStringsBlock.statements.add(decl)
            decl.linkParents(internedStringsBlock)
            return listOf(internedStringsModuleName, decl.name)
        }

        val key = Pair(string.value, string.altEncoding)
        val existing = internedStringsUnique[key]
        if (existing != null)
            return existing

        val scopedName = getScopedName(string)
        internedStringsUnique[key] = scopedName
        return scopedName
    }

    override val position: Position = Position.DUMMY
    override var parent: Node
        get() = throw FatalAstException("program has no parent")
        set(_) = throw FatalAstException("can't set parent of program")

    override fun linkParents(parent: Node) {
        modules.forEach {
            it.linkParents(namespace)
        }
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(node is Module && replacement is Module)
        val idx = _modules.indexOfFirst { it===node }
        _modules[idx] = replacement
        replacement.linkIntoProgram(this)
    }
}

open class Module(final override var statements: MutableList<Statement>,
             final override val position: Position,
             val source: SourceCode) : Node, INameScope {

    override lateinit var parent: Node
    lateinit var program: Program

    override val name = source.origin
            .substringBeforeLast(".")
            .substringAfterLast("/")
            .substringAfterLast("\\")

    val loadAddress: Int by lazy {
        val address = (statements.singleOrNull { it is Directive && it.directive == "%address" } as? Directive)?.args?.single()?.int ?: 0
        address
    }

    override fun linkParents(parent: Node) {
        require(parent is GlobalNamespace)
        this.parent = parent
        statements.forEach {it.linkParents(this)}
    }

    fun linkIntoProgram(program: Program) {
        this.program = program
        linkParents(program.namespace)
    }

    override val definingScope: INameScope
        get() = program.namespace
    override fun replaceChildNode(node: Node, replacement: Node) {
        require(node is Statement && replacement is Statement)
        val idx = statements.indexOfFirst { it===node }
        statements[idx] = replacement
        replacement.parent = this
    }

    override fun toString() = "Module(name=$name, pos=$position, lib=${isLibrary})"

    fun accept(visitor: IAstVisitor) = visitor.visit(this)
    fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)

    val isLibrary get() = source.isFromResources
}


class GlobalNamespace(val modules: Iterable<Module>): Node, INameScope {
    override val name = "<<<global>>>"
    override val position = Position("<<<global>>>", 0, 0, 0)
    override val statements = mutableListOf<Statement>()        // not used
    override var parent: Node = ParentSentinel

    override fun lookup(scopedName: List<String>): Statement? {
        throw NotImplementedError("use lookup on actual ast node instead")
    }

    override fun linkParents(parent: Node) {
        modules.forEach { it.linkParents(this) }
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        throw FatalAstException("cannot replace anything in the namespace")
    }
}

object BuiltinFunctionScopePlaceholder : INameScope {
    override val name = "<<builtin-functions-scope-placeholder>>"
    override val position = Position("<<placeholder>>", 0, 0, 0)
    override var statements = mutableListOf<Statement>()
    override var parent: Node = ParentSentinel
    override fun linkParents(parent: Node) {}
}


