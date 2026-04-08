package prog8.code.ast

import prog8.code.INTERNED_STRINGS_MODULENAME
import prog8.code.StStaticVariable
import prog8.code.SymbolTable
import prog8.code.core.*
import prog8.code.source.SourceCode
import java.nio.file.Path

// New simplified AST for the code generator.


sealed class PtNode(val position: Position) {

    private val _children = mutableListOf<PtNode>()
    val children: List<PtNode> get() = _children  // Read-only view
    lateinit var parent: PtNode

    fun parentHasBeenSet() = ::parent.isInitialized

    override fun toString(): String = "${super.toString()} at $position"

    fun add(child: PtNode) {
        _children.add(child)
        child.parent = this
    }

    fun add(index: Int, child: PtNode) {
        _children.add(index, child)
        child.parent = this
    }

    /**
     * Replace child at given index with a new child.
     * Updates the new child's parent pointer.
     */
    fun setChild(index: Int, child: PtNode) {
        _children[index] = child
        child.parent = this
    }

    /**
     * Remove a child node.
     * Does NOT clear the removed child's parent pointer (caller should do that if needed).
     */
    fun removeChild(child: PtNode): Boolean {
        return _children.remove(child)
    }

    /**
     * Remove child at given index.
     * Does NOT clear the removed child's parent pointer (caller should do that if needed).
     */
    fun removeChildAt(index: Int): PtNode {
        return _children.removeAt(index)
    }

    /**
     * Add multiple children at once.
     */
    fun addAll(children: Collection<PtNode>) {
        children.forEach { add(it) }
    }

    /**
     * Remove all children.
     * Does NOT clear the removed children's parent pointers (caller should do that if needed).
     */
    fun clearChildren() {
        _children.clear()
    }

    /**
     * Create a shallow copy of this node (without children).
     * Subclasses that need to support copying must override this.
     * The new node's children list is empty - caller must populate it.
     * 
     * Default implementation throws UnsupportedOperationException.
     */
    open fun copy(): PtNode = throw UnsupportedOperationException("copy() not implemented for ${this::class.simpleName}")

    fun definingBlock() = findParentNode<PtBlock>(this)
    fun definingSub() = findParentNode<PtSub>(this)
    fun definingAsmSub() = findParentNode<PtAsmSub>(this)
    fun definingISub(): IPtSubroutine? {
        // Find parent that implements IPtSubroutine
        var candidate: PtNode? = parent
        while (candidate != null) {
            if (candidate is IPtSubroutine) return candidate
            if (candidate is PtProgram) return null
            candidate = candidate.parent
        }
        return null
    }
}


class PtNodeGroup : PtNode(Position.DUMMY) {
    override fun copy(): PtNode = PtNodeGroup()
}


sealed class PtNamedNode(initialName: String, position: Position): PtNode(position) {
    private var nodeName = initialName
    private var cachedScopedName: String? = null

    var name
        get() = nodeName
        set(value) {
            nodeName = value
            cachedScopedName = null
        }

    val scopedName: String
        get() {
            return if(cachedScopedName!=null)
                cachedScopedName!!
            else {
                var namedParent: PtNode = this.parent
                cachedScopedName = if (namedParent is PtProgram)
                    nodeName
                else {
                    while (namedParent !is PtNamedNode)
                        namedParent = namedParent.parent
                    namedParent.scopedName + "." + nodeName
                }
                cachedScopedName!!
            }
        }
}


class PtProgram(
    val name: String,
    val memsizer: IMemSizer,
    val encoding: IStringEncoding
) : PtNode(Position.DUMMY) {
    // Root node is never transformed - return self
    override fun copy(): PtNode = this

//    fun allModuleDirectives(): Sequence<PtDirective> =
//        children.asSequence().flatMap { it.children }.filterIsInstance<PtDirective>().distinct()

    fun allBlocks(): Sequence<PtBlock> =
        children.asSequence().filterIsInstance<PtBlock>()

    fun entrypoint(): PtSub? =
        // returns the main.start subroutine if it exists
        allBlocks().firstOrNull { it.name == "main" || it.name=="p8b_main" }
            ?.children
            ?.firstOrNull { it is PtSub && (it.name == "start" || it.name=="main.start" || it.name=="p8s_start" || it.name=="p8b_main.p8s_start") } as PtSub?

    fun internString(string: PtString, st: SymbolTable): String {
        val internedStringsBlock = children.first { it is PtBlock && it.name == INTERNED_STRINGS_MODULENAME }
        val varname = "ptstring_${internedStringsBlock.children.size}"
        val internedString = PtVariable(varname, DataType.STR, ZeropageWish.NOT_IN_ZEROPAGE, 0u, false, string, null, string.position)
        internedStringsBlock.add(internedString)
        val stEntry = StStaticVariable(internedString.scopedName, DataType.STR, string.value to string.encoding, null, string.value.length.toUInt()+1u,
            ZeropageWish.NOT_IN_ZEROPAGE, 0u, false, astNode=internedString)
        st.add(stEntry)
        return internedString.scopedName
    }
}


class PtBlock(name: String,
              val library: Boolean,
              val source: SourceCode,       // taken from the module the block is defined in.
              val options: Options,
              position: Position
) : PtNamedNode(name, position) {
    class Options(val address: UInt? = null,
                  val forceOutput: Boolean = false,
                  val noSymbolPrefixing: Boolean = false,
                  val veraFxMuls: Boolean = false,
                  val ignoreUnused: Boolean = false)
    
    override fun copy(): PtNode = PtBlock(name, library, source, options, position)
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
inline fun <reified T : PtNode> findParentNode(node: PtNode): T? {
    var candidate: PtNode? = node.parent
    while (candidate != null) {
        if (candidate is T) return candidate
        if (candidate is PtProgram) return null
        candidate = candidate.parent
    }
    return null
}
