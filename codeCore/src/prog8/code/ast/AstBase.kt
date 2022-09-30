package prog8.code.ast

import prog8.code.core.*
import java.nio.file.Path

// New simplified AST for the code generator.


sealed class PtNode(val position: Position) {

    val children = mutableListOf<PtNode>()
    lateinit var parent: PtNode

    fun printIndented(indent: Int) {
        print("    ".repeat(indent))
        print("${this.javaClass.simpleName}  ")
        printProperties()
        println()
        children.forEach { it.printIndented(indent+1) }
    }

    abstract fun printProperties()

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
}


class PtNodeGroup : PtNode(Position.DUMMY) {
    override fun printProperties() {}
}


abstract class PtNamedNode(val name: String, position: Position): PtNode(position) {
    val scopedName: List<String> by lazy {
        var namedParent: PtNode = this.parent
        if(namedParent is PtProgram)
            listOf(name)
        else {
            while (namedParent !is PtNamedNode)
                namedParent = namedParent.parent
            namedParent.scopedName + name
        }
    }
}


class PtProgram(
    val name: String,
    val memsizer: IMemSizer,
    val encoding: IStringEncoding
) : PtNode(Position.DUMMY) {
    fun print() = printIndented(0)
    override fun printProperties() {
        print("'$name'")
    }

//    fun allModuleDirectives(): Sequence<PtDirective> =
//        children.asSequence().flatMap { it.children }.filterIsInstance<PtDirective>().distinct()

    fun allBlocks(): Sequence<PtBlock> =
        children.asSequence().filterIsInstance<PtBlock>()

    fun entrypoint(): PtSub? =
        allBlocks().firstOrNull { it.name == "main" }?.children?.firstOrNull { it is PtSub && it.name == "start" } as PtSub?
}


class PtBlock(name: String,
              val address: UInt?,
              val library: Boolean,
              val forceOutput: Boolean,
              val alignment: BlockAlignment,
              position: Position
) : PtNamedNode(name, position) {
    override fun printProperties() {
        print("$name  addr=$address  library=$library  forceOutput=$forceOutput  alignment=$alignment")
    }

    enum class BlockAlignment {
        NONE,
        WORD,
        PAGE
    }
}


class PtInlineAssembly(val assembly: String, val isIR: Boolean, position: Position) : PtNode(position) {
    override fun printProperties() {}

    init {
        require(!assembly.startsWith('\n') && !assembly.startsWith('\r')) { "inline assembly should be trimmed" }
        require(!assembly.endsWith('\n') && !assembly.endsWith('\r')) { "inline assembly should be trimmed" }
    }
}


class PtLabel(name: String, position: Position) : PtNamedNode(name, position) {
    override fun printProperties() {
        print(name)
    }
}


class PtBreakpoint(position: Position): PtNode(position) {
    override fun printProperties() {}
}


class PtIncludeBinary(val file: Path, val offset: UInt?, val length: UInt?, position: Position) : PtNode(position) {
    override fun printProperties() {
        print("filename=$file  offset=$offset  length=$length")
    }
}


class PtNop(position: Position): PtNode(position) {
    override fun printProperties() {}
}


class PtScopeVarsDecls(position: Position): PtNode(position) {
    override fun printProperties() {}
}



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