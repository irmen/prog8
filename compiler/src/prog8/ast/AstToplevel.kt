package prog8.ast

import prog8.ast.base.*
import prog8.ast.expressions.Expression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.processing.AstWalker
import prog8.ast.processing.IAstVisitor
import prog8.ast.statements.*
import prog8.functions.BuiltinFunctions
import java.nio.file.Path


interface Node {
    val position: Position
    var parent: Node             // will be linked correctly later (late init)
    fun linkParents(parent: Node)

    fun definingModule(): Module {
        if(this is Module)
            return this
        return findParentNode<Module>(this)!!
    }

    fun definingSubroutine(): Subroutine?  = findParentNode<Subroutine>(this)

    fun definingScope(): INameScope {
        val scope = findParentNode<INameScope>(this)
        if(scope!=null) {
            return scope
        }
        if(this is Label && this.name.startsWith("builtin::")) {
            return BuiltinFunctionScopePlaceholder
        }
        if(this is GlobalNamespace)
            return this
        throw FatalAstException("scope missing from $this")
    }

    fun replaceChildNode(node: Node, replacement: Node)
}

interface IFunctionCall {
    var target: IdentifierReference
    var args: MutableList<Expression>
}

interface INameScope {
    val name: String
    val position: Position
    val statements: MutableList<Statement>
    val parent: Node

    fun linkParents(parent: Node)

    fun subScope(name: String): INameScope? {
        for(stmt in statements) {
            when(stmt) {
                // NOTE: if other nodes are introduced that are a scope, or contain subscopes, they must be added here!
                is ForLoop -> if(stmt.body.name==name) return stmt.body
                is RepeatLoop -> if(stmt.body.name==name) return stmt.body
                is WhileLoop -> if(stmt.body.name==name) return stmt.body
                is BranchStatement -> {
                    if(stmt.truepart.name==name) return stmt.truepart
                    if(stmt.elsepart.containsCodeOrVars() && stmt.elsepart.name==name) return stmt.elsepart
                }
                is IfStatement -> {
                    if(stmt.truepart.name==name) return stmt.truepart
                    if(stmt.elsepart.containsCodeOrVars() && stmt.elsepart.name==name) return stmt.elsepart
                }
                is WhenStatement -> {
                    val scope = stmt.choices.firstOrNull { it.statements.name==name }
                    if(scope!=null)
                        return scope.statements
                }
                is INameScope -> if(stmt.name==name) return stmt
                else -> {}
            }
        }
        return null
    }

    fun getLabelOrVariable(name: String): Statement? {
        // this is called A LOT and could perhaps be optimized a bit more,
        // but adding a memoization cache didn't make much of a practical runtime difference
        for (stmt in statements) {
            if (stmt is VarDecl && stmt.name==name) return stmt
            if (stmt is Label && stmt.name==name) return stmt
            if (stmt is AnonymousScope) {
                val sub = stmt.getLabelOrVariable(name)
                if(sub!=null)
                    return sub
            }
        }
        return null
    }

    fun allDefinedSymbols(): List<Pair<String, Statement>>  {
        return statements.mapNotNull {
            when (it) {
                is Label -> it.name to it
                is VarDecl -> it.name to it
                is Subroutine -> it.name to it
                is Block -> it.name to it
                else -> null
            }
        }
    }

    fun lookup(scopedName: List<String>, localContext: Node) : Statement? {
        if(scopedName.size>1) {
            // a scoped name can a) refer to a member of a struct, or b) refer to a name in another module.
            // try the struct first.
            val thing = lookup(scopedName.dropLast(1), localContext) as? VarDecl
            val struct = thing?.struct
            if (struct != null) {
                if(struct.statements.any { (it as VarDecl).name == scopedName.last()}) {
                    // return ref to the mangled name variable
                    val mangled = mangledStructMemberName(thing.name, scopedName.last())
                    return thing.definingScope().getLabelOrVariable(mangled)
                }
            }

            // it's a qualified name, look it up from the root of the module's namespace (consider all modules in the program)
            for(module in localContext.definingModule().program.modules) {
                var scope: INameScope? = module
                for(name in scopedName.dropLast(1)) {
                    scope = scope?.subScope(name)
                    if(scope==null)
                        break
                }
                if(scope!=null) {
                    val result = scope.getLabelOrVariable(scopedName.last())
                    if(result!=null)
                        return result
                    return scope.subScope(scopedName.last()) as Statement?
                }
            }
            return null
        } else {
            // unqualified name, find the scope the localContext is in, look in that first
            var statementScope = localContext
            while(statementScope !is ParentSentinel) {
                val localScope = statementScope.definingScope()
                val result = localScope.getLabelOrVariable(scopedName[0])
                if (result != null)
                    return result
                val subscope = localScope.subScope(scopedName[0]) as Statement?
                if (subscope != null)
                    return subscope
                // not found in this scope, look one higher up
                statementScope = statementScope.parent
            }
            return null
        }
    }

    fun containsCodeOrVars() = statements.any { it !is Directive || it.directive == "%asminclude" || it.directive == "%asm"}
    fun containsNoVars() = statements.all { it !is VarDecl }
    fun containsNoCodeNorVars() = !containsCodeOrVars()

    fun remove(stmt: Statement) {
        if(!statements.remove(stmt))
            throw FatalAstException("stmt to remove wasn't found in scope")
    }

    fun getAllLabels(label: String): List<Label> {
        val result = mutableListOf<Label>()

        fun find(scope: INameScope) {
            scope.statements.forEach {
                when(it) {
                    is Label -> result.add(it)
                    is INameScope -> find(it)
                    is IfStatement -> {
                        find(it.truepart)
                        find(it.elsepart)
                    }
                    is RepeatLoop -> find(it.body)
                    is ForeverLoop -> find(it.body)
                    is WhileLoop -> find(it.body)
                    is WhenStatement -> it.choices.forEach { choice->find(choice.statements) }
                    else -> { /* do nothing */ }
                }
            }
        }

        find(this)
        return result
    }
}

interface IAssignable {
    // just a tag for now
}


/*********** Everything starts from here, the Program; zero or more modules *************/

class Program(val name: String, val modules: MutableList<Module>): Node {
    val namespace = GlobalNamespace(modules)

    val definedLoadAddress: Int
        get() = modules.first().loadAddress

    var actualLoadAddress: Int = 0

    fun entrypoint(): Subroutine? {
        val mainBlocks = allBlocks().filter { it.name=="main" }
        if(mainBlocks.size > 1)
            throw FatalAstException("more than one 'main' block")
        return if(mainBlocks.isEmpty()) {
            null
        } else {
            mainBlocks[0].subScope("start") as Subroutine?
        }
    }

    fun allBlocks(): List<Block> = modules.flatMap { it.statements.filterIsInstance<Block>() }

    override val position: Position = Position.DUMMY
    override var parent: Node
        get() = throw FatalAstException("program has no parent")
        set(value) = throw FatalAstException("can't set parent of program")

    override fun linkParents(parent: Node) {
        modules.forEach {
            it.linkParents(this)
        }
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        require(node is Module && replacement is Module)
        val idx = modules.indexOf(node)
        modules[idx] = replacement
        replacement.parent = this
    }
}

class Module(override val name: String,
             override var statements: MutableList<Statement>,
             override val position: Position,
             val isLibraryModule: Boolean,
             val source: Path) : Node, INameScope {

    override lateinit var parent: Node
    lateinit var program: Program
    val importedBy = mutableListOf<Module>()
    val imports = mutableSetOf<Module>()

    var loadAddress: Int = 0        // can be set with the %address directive

    override fun linkParents(parent: Node) {
        this.parent = parent
        statements.forEach {it.linkParents(this)}
    }

    override fun definingScope(): INameScope = program.namespace
    override fun replaceChildNode(node: Node, replacement: Node) {
        require(node is Statement && replacement is Statement)
        val idx = statements.indexOf(node)
        statements[idx] = replacement
        replacement.parent = this
    }

    override fun toString() = "Module(name=$name, pos=$position, lib=$isLibraryModule)"

    fun accept(visitor: IAstVisitor) = visitor.visit(this)
    fun accept(visitor: AstWalker, parent: Node) = visitor.visit(this, parent)
}


class GlobalNamespace(val modules: List<Module>): Node, INameScope {
    override val name = "<<<global>>>"
    override val position = Position("<<<global>>>", 0, 0, 0)
    override val statements = mutableListOf<Statement>()
    override var parent: Node = ParentSentinel

    override fun linkParents(parent: Node) {
        modules.forEach { it.linkParents(this) }
    }

    override fun replaceChildNode(node: Node, replacement: Node) {
        throw FatalAstException("cannot replace anything in the namespace")
    }

    override fun lookup(scopedName: List<String>, localContext: Node): Statement? {
        if (scopedName.size == 1 && scopedName[0] in BuiltinFunctions) {
            // builtin functions always exist, return a dummy localContext for them
            val builtinPlaceholder = Label("builtin::${scopedName.last()}", localContext.position)
            builtinPlaceholder.parent = ParentSentinel
            return builtinPlaceholder
        }

        if(scopedName.size>1) {
            // a scoped name can a) refer to a member of a struct, or b) refer to a name in another module.
            // try the struct first.
            val thing = lookup(scopedName.dropLast(1), localContext) as? VarDecl
            val struct = thing?.struct
            if (struct != null) {
                if(struct.statements.any { (it as VarDecl).name == scopedName.last()}) {
                    // return ref to the mangled name variable
                    val mangled = mangledStructMemberName(thing.name, scopedName.last())
                    return thing.definingScope().getLabelOrVariable(mangled)
                }
            }
        }
        // lookup something from the module.
        return when (val stmt = localContext.definingModule().lookup(scopedName, localContext)) {
            is Label, is VarDecl, is Block, is Subroutine -> stmt
            null -> null
            else -> throw SyntaxError("wrong identifier target for $scopedName: $stmt", stmt.position)
        }
    }
}

object BuiltinFunctionScopePlaceholder : INameScope {
    override val name = "<<builtin-functions-scope-placeholder>>"
    override val position = Position("<<placeholder>>", 0, 0, 0)
    override var statements = mutableListOf<Statement>()
    override var parent: Node = ParentSentinel
    override fun linkParents(parent: Node) {}
}


// prefix for struct member variables
internal fun mangledStructMemberName(varName: String, memberName: String) = "prog8struct_${varName}_$memberName"
