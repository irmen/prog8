package prog8.ast.base

import prog8.ast.Node
import prog8.compiler.target.CompilationTarget


/**************************** AST Data classes ****************************/

enum class DataType {
    UBYTE,              // pass by value
    BYTE,               // pass by value
    UWORD,              // pass by value
    WORD,               // pass by value
    FLOAT,              // pass by value
    STR,                // pass by reference
    ARRAY_UB,           // pass by reference
    ARRAY_B,            // pass by reference
    ARRAY_UW,           // pass by reference
    ARRAY_W,            // pass by reference
    ARRAY_F,            // pass by reference
    STRUCT;             // pass by reference

    /**
     * is the type assignable to the given other type (perhaps via a typecast) without loss of precision?
     */
    infix fun isAssignableTo(targetType: DataType) =
            when(this) {
                UBYTE -> targetType in setOf(UBYTE, WORD, UWORD, FLOAT)
                BYTE -> targetType in setOf(BYTE, WORD, FLOAT)
                UWORD -> targetType in setOf(UWORD, FLOAT)
                WORD -> targetType in setOf(WORD, FLOAT)
                FLOAT -> targetType == FLOAT
                STR -> targetType == STR
                in ArrayDatatypes -> targetType == this
                else -> false
            }

    infix fun isAssignableTo(targetTypes: Set<DataType>) = targetTypes.any { this isAssignableTo it }
    infix fun isNotAssignableTo(targetType: DataType) = !this.isAssignableTo(targetType)
    infix fun isNotAssignableTo(targetTypes: Set<DataType>) = !this.isAssignableTo(targetTypes)

    infix fun largerThan(other: DataType) =
            when {
                this == other -> false
                this in ByteDatatypes -> false
                this in WordDatatypes -> other in ByteDatatypes
                this==STR && other==UWORD || this==UWORD && other==STR -> false
                else -> true
            }

    infix fun equalsSize(other: DataType) =
            when {
                this == other -> true
                this in ByteDatatypes -> other in ByteDatatypes
                this in WordDatatypes -> other in WordDatatypes
                this==STR && other==UWORD || this==UWORD && other==STR -> true
                else -> false
            }

    fun memorySize(): Int {
        return when(this) {
            in ByteDatatypes -> 1
            in WordDatatypes -> 2
            FLOAT -> CompilationTarget.instance.machine.FLOAT_MEM_SIZE
            in PassByReferenceDatatypes -> CompilationTarget.instance.machine.POINTER_MEM_SIZE
            else -> -9999999
        }
    }
}

enum class CpuRegister {
    A,
    X,
    Y
}

enum class RegisterOrPair {
    A,
    X,
    Y,
    AX,
    AY,
    XY,
    FAC1,
    FAC2,
    // cx16 virtual registers:
    R0, R1, R2, R3, R4, R5, R6, R7,
    R8, R9, R10, R11, R12, R13, R14, R15;

    companion object {
        val names by lazy { values().map { it.toString()} }
    }

}       // only used in parameter and return value specs in asm subroutines

enum class Statusflag {
    Pc,
    Pz,
    Pv,
    Pn;

    companion object {
        val names by lazy { values().map { it.toString()} }
    }
}

enum class BranchCondition {
    CS,
    CC,
    EQ,
    Z,
    NE,
    NZ,
    VS,
    VC,
    MI,
    NEG,
    PL,
    POS
}

enum class VarDeclType {
    VAR,
    CONST,
    MEMORY
}

val ByteDatatypes = setOf(DataType.UBYTE, DataType.BYTE)
val WordDatatypes = setOf(DataType.UWORD, DataType.WORD)
val IntegerDatatypes = setOf(DataType.UBYTE, DataType.BYTE, DataType.UWORD, DataType.WORD)
val NumericDatatypes = setOf(DataType.UBYTE, DataType.BYTE, DataType.UWORD, DataType.WORD, DataType.FLOAT)
val ArrayDatatypes = setOf(DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W, DataType.ARRAY_F)
val IterableDatatypes = setOf(
        DataType.STR,
        DataType.ARRAY_UB, DataType.ARRAY_B,
        DataType.ARRAY_UW, DataType.ARRAY_W,
        DataType.ARRAY_F)
val PassByValueDatatypes = NumericDatatypes
val PassByReferenceDatatypes = IterableDatatypes.plus(DataType.STRUCT)
val ArrayElementTypes = mapOf(
        DataType.STR to DataType.UBYTE,
        DataType.ARRAY_B to DataType.BYTE,
        DataType.ARRAY_UB to DataType.UBYTE,
        DataType.ARRAY_W to DataType.WORD,
        DataType.ARRAY_UW to DataType.UWORD,
        DataType.ARRAY_F to DataType.FLOAT)
val ElementArrayTypes = mapOf(
        DataType.BYTE to DataType.ARRAY_B,
        DataType.UBYTE to DataType.ARRAY_UB,
        DataType.WORD to DataType.ARRAY_W,
        DataType.UWORD to DataType.ARRAY_UW,
        DataType.FLOAT to DataType.ARRAY_F
)

// find the parent node of a specific type or interface
// (useful to figure out in what namespace/block something is defined, etc)
inline fun <reified T> findParentNode(node: Node): T? {
    var candidate = node.parent
    while(candidate !is T && candidate !is ParentSentinel)
        candidate = candidate.parent
    return if(candidate is ParentSentinel)
        null
    else
        candidate as T
}

object ParentSentinel : Node {
    override val position = Position("<<sentinel>>", 0, 0, 0)
    override var parent: Node = this
    override fun linkParents(parent: Node) {}
    override fun replaceChildNode(node: Node, replacement: Node) {
        replacement.parent = this
    }
}

data class Position(val file: String, val line: Int, val startCol: Int, val endCol: Int) {
    override fun toString(): String = "[$file: line $line col ${startCol+1}-${endCol+1}]"
    fun toClickableStr(): String = "$file:$line:$startCol:"

    companion object {
        val DUMMY = Position("<dummy>", 0, 0, 0)
    }
}
