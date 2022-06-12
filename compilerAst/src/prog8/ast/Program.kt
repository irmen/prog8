package prog8.ast

import prog8.ast.base.FatalAstException
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.StringLiteral
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.code.core.*

/*********** Everything starts from here, the Program; zero or more modules *************/

class Program(val name: String,
              val builtinFunctions: IBuiltinFunctions,
              val memsizer: IMemSizer,
              val encoding: IStringEncoding
) {
    private val _modules = mutableListOf<Module>()

    val modules: List<Module> = _modules
    val namespace: GlobalNamespace = GlobalNamespace(modules)

    init {
        // insert a container module for all interned strings later
        val internedStringsModule =
            Module(mutableListOf(), Position.DUMMY, SourceCode.Generated(internedStringsModuleName))
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
        module.program = this
        module.linkParents(namespace)
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
        get() = modules.flatMap { it.statements.asSequence().filterIsInstance<Block>() }

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
        get() = modules.first { it.name!= internedStringsModuleName }

    private val internedStringsReferenceCounts = mutableMapOf<VarDecl, Int>()

    fun internString(string: StringLiteral): List<String> {
        // Move a string literal into the internal, deduplicated, string pool
        // replace it with a variable declaration that points to the entry in the pool.

        if(string.parent is VarDecl) {
            // deduplication can only be performed safely for known-const strings (=string literals OUTSIDE OF A VARDECL)!
            throw FatalAstException("cannot intern a string literal that's part of a vardecl")
        }

        val internedStringsBlock = modules
            .first { it.name == internedStringsModuleName }.statements
            .first { it is Block && it.name == internedStringsModuleName } as Block

        fun addNewInternedStringvar(string: StringLiteral): Pair<List<String>, VarDecl> {
            val varName = "string_${internedStringsBlock.statements.size}"
            val decl = VarDecl(
                VarDeclType.VAR, VarDeclOrigin.STRINGLITERAL, DataType.STR, ZeropageWish.NOT_IN_ZEROPAGE, null, varName, string,
                isArray = false, sharedWithAsm = false, subroutineParameter = null, position = string.position
            )
            internedStringsBlock.statements.add(decl)
            decl.linkParents(internedStringsBlock)
            return Pair(listOf(internedStringsModuleName, decl.name), decl)
        }

        val existingDecl = internedStringsBlock.statements.singleOrNull {
            val declString = (it as VarDecl).value as StringLiteral
            declString.encoding == string.encoding && declString.value == string.value
        }
        return if (existingDecl != null) {
            existingDecl as VarDecl
            internedStringsReferenceCounts[existingDecl] = internedStringsReferenceCounts.getValue(existingDecl)+1
            existingDecl.scopedName
        }
        else {
            val (newName, newDecl) = addNewInternedStringvar(string)
            internedStringsReferenceCounts[newDecl] = 1
            newName
        }
    }

    fun removeInternedStringsFromRemovedSubroutine(sub: Subroutine) {
        val s = StringSearch(this)
        sub.accept(s)
        s.removeStrings(modules)
    }

    fun removeInternedStringsFromRemovedBlock(block: Block) {
        val s = StringSearch(this)
        block.accept(s)
        s.removeStrings(modules)
    }

    private class StringSearch(val program: Program): IAstVisitor {
        val removals = mutableListOf<List<String>>()
        override fun visit(identifier: IdentifierReference) {
            if(identifier.wasStringLiteral(program))
                removals.add(identifier.nameInSource)
        }

        fun removeStrings(modules: List<Module>) {
            if(removals.isNotEmpty()) {
                val internedStringsBlock = modules
                    .first { it.name == internedStringsModuleName }.statements
                    .first { it is Block && it.name == internedStringsModuleName } as Block
                removals.forEach { scopedname ->
                    val decl = internedStringsBlock.statements.single { decl -> (decl as VarDecl).scopedName == scopedname } as VarDecl
                    val numRefs = program.internedStringsReferenceCounts.getValue(decl) - 1
                    program.internedStringsReferenceCounts[decl] = numRefs
                    if(numRefs==0)
                        internedStringsBlock.statements.remove(decl)
                }
            }
        }
    }

}
