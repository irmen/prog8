package prog8.ast

import prog8.ast.base.*
import prog8.ast.statements.Block
import prog8.ast.statements.Label
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl
import prog8.compiler.HeapValues
import prog8.functions.BuiltinFunctions
import java.nio.file.Path


/*********** Everything starts from here, the Program; zero or more modules *************/

class Program(val name: String, val modules: MutableList<Module>) {
    val namespace = GlobalNamespace(modules)
    val heap = HeapValues()

    val loadAddress: Int
        get() = modules.first().loadAddress

    fun entrypoint(): Subroutine? {
        val mainBlocks = modules.flatMap { it.statements }.filter { b -> b is Block && b.name=="main" }.map { it as Block }
        if(mainBlocks.size > 1)
            throw FatalAstException("more than one 'main' block")
        return if(mainBlocks.isEmpty()) {
            null
        } else {
            mainBlocks[0].subScopes()["start"] as Subroutine?
        }
    }
}

class Module(override val name: String,
             override var statements: MutableList<IStatement>,
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

    override fun toString() = "Module(name=$name, pos=$position, lib=$isLibraryModule)"
}

class GlobalNamespace(val modules: List<Module>): Node, INameScope {
    override val name = "<<<global>>>"
    override val position = Position("<<<global>>>", 0, 0, 0)
    override val statements = mutableListOf<IStatement>()
    override var parent: Node = ParentSentinel

    override fun linkParents(parent: Node) {
        modules.forEach { it.linkParents(this) }
    }

    override fun lookup(scopedName: List<String>, localContext: Node): IStatement? {
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
                    val mangledVar = thing.definingScope().getLabelOrVariable(mangled)
                    return mangledVar
                }
            }
        }

        val stmt = localContext.definingModule().lookup(scopedName, localContext)
        return when (stmt) {
            is Label, is VarDecl, is Block, is Subroutine -> stmt
            null -> null
            else -> throw NameError("wrong identifier target: $stmt", stmt.position)
        }
    }
}

object BuiltinFunctionScopePlaceholder : INameScope {
    override val name = "<<builtin-functions-scope-placeholder>>"
    override val position = Position("<<placeholder>>", 0, 0, 0)
    override var statements = mutableListOf<IStatement>()
    override var parent: Node = ParentSentinel
    override fun linkParents(parent: Node) {}
}


// prefix for struct member variables
internal fun mangledStructMemberName(varName: String, memberName: String) = "prog8struct_${varName}_$memberName"
