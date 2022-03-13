package prog8.code.ast

import prog8.code.core.*
import java.nio.file.Path

// New (work-in-progress) simplified AST for the code generator.


sealed class PtNode(val position: Position, val children: MutableList<PtNode> = mutableListOf()) {

    lateinit var parent: PtNode

    protected fun printIndented(indent: Int) {
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
}


class PtNodeGroup: PtNode(Position.DUMMY) {
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


// TODO remove duplicates that are also in CompilationOptions (that is already passed into the AsmGen already)
// TODO make sure loadaddress is always set to a sensible value (not 0)  see determineProgramLoadAddress()
class ProgramOptions(
    val output: OutputType,
    val launcher: CbmPrgLauncherType,
    val zeropage: ZeropageType,
    val zpReserved: Collection<UIntRange>,
    val loadAddress: UInt,
    val floatsEnabled: Boolean,
    val noSysInit: Boolean,
    val dontReinitGlobals: Boolean,
    val optimize: Boolean,
    val target: String
)


class PtProgram(
    val name: String,
    val options: ProgramOptions,
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
              position: Position
) : PtNamedNode(name, position) {
    override fun printProperties() {
        print("$name  addr=$address  library=$library")
    }
}


class PtInlineAssembly(val assembly: String, position: Position) : PtNode(position) {
    override fun printProperties() {}
}


class PtLabel(name: String, position: Position) : PtNamedNode(name, position) {
    override fun printProperties() {
        print(name)
    }
}


class PtBreakpoint(position: Position): PtNode(position) {
    override fun printProperties() {}
}


class PtInlineBinary(val file: Path, val offset: UInt?, val length: UInt?, position: Position) : PtNode(position) {
    override fun printProperties() {
        print("filename=$file  offset=$offset  length=$length")
    }
}


class PtNop(position: Position): PtNode(position) {
    override fun printProperties() {}
}