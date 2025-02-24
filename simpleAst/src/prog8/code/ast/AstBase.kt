package prog8.code.ast

import prog8.code.core.IMemSizer
import prog8.code.core.IStringEncoding
import prog8.code.core.Position
import prog8.code.source.SourceCode
import java.nio.file.Path

// New simplified AST for the code generator.


sealed class PtNode(val position: Position) {

    val children = mutableListOf<PtNode>()
    lateinit var parent: PtNode

    fun add(child: PtNode) {
        children.add(child)
        child.parent = this
    }

    fun add(index: Int, child: PtNode) {
        children.add(index, child)
        child.parent = this
    }

    fun definingBlock() = findParentNode<PtBlock>(this)
    fun definingSub() = findParentNode<PtSub>(this)
    fun definingAsmSub() = findParentNode<PtAsmSub>(this)
    fun definingISub() = findParentNode<IPtSubroutine>(this)
}


sealed interface IPtStatementContainer


class PtNodeGroup : PtNode(Position.DUMMY), IPtStatementContainer


sealed class PtNamedNode(var name: String, position: Position): PtNode(position) {
    // Note that as an exception, the 'name' is not read-only
    // but a var. This is to allow for cheap node renames.
    val scopedName: String
        get() {
            var namedParent: PtNode = this.parent
            return if(namedParent is PtProgram)
                name
            else {
                while (namedParent !is PtNamedNode)
                    namedParent = namedParent.parent
                namedParent.scopedName + "." + name
            }
        }
}


class PtProgram(
    val name: String,
    val memsizer: IMemSizer,
    val encoding: IStringEncoding
) : PtNode(Position.DUMMY) {

//    fun allModuleDirectives(): Sequence<PtDirective> =
//        children.asSequence().flatMap { it.children }.filterIsInstance<PtDirective>().distinct()

    fun allBlocks(): Sequence<PtBlock> =
        children.asSequence().filterIsInstance<PtBlock>()

    fun entrypoint(): PtSub? =
        // returns the main.start subroutine if it exists
        allBlocks().firstOrNull { it.name == "main" || it.name=="p8b_main" }
            ?.children
            ?.firstOrNull { it is PtSub && (it.name == "start" || it.name=="main.start" || it.name=="p8s_start" || it.name=="p8b_main.p8s_start") } as PtSub?
}


class PtBlock(name: String,
              val library: Boolean,
              val source: SourceCode,       // taken from the module the block is defined in.
              val options: Options,
              position: Position
) : PtNamedNode(name, position), IPtStatementContainer {
    class Options(val address: UInt? = null,
                  val forceOutput: Boolean = false,
                  val noSymbolPrefixing: Boolean = false,
                  val veraFxMuls: Boolean = false,
                  val ignoreUnused: Boolean = false)
}


class PtInlineAssembly(val assembly: String, val isIR: Boolean, position: Position) : PtNode(position) {
    init {
        require(!assembly.startsWith('\n') && !assembly.startsWith('\r')) { "inline assembly should be trimmed" }
        require(!assembly.endsWith('\n') && !assembly.endsWith('\r')) { "inline assembly should be trimmed" }
    }
}


class PtLabel(name: String, position: Position) : PtNamedNode(name, position)


class PtBreakpoint(position: Position): PtNode(position)


class PtAlign(val align: UInt, position: Position): PtNode(position)


class PtIncludeBinary(val file: Path, val offset: UInt?, val length: UInt?, position: Position) : PtNode(position)


class PtNop(position: Position): PtNode(position)


// find the parent node of a specific type or interface
// (useful to figure out in what namespace/block something is defined, etc.)
inline fun <reified T> findParentNode(node: PtNode): T? {
    var candidate = node.parent
    while(candidate !is T && candidate !is PtProgram)
        candidate = candidate.parent
    return if(candidate is PtProgram)
        null
    else
        candidate as T
}