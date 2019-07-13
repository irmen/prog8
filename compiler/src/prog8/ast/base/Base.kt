package prog8.ast.base

import prog8.ast.Node

/**************************** AST Data classes ****************************/

enum class DataType {
    UBYTE,              // pass by value
    BYTE,               // pass by value
    UWORD,              // pass by value
    WORD,               // pass by value
    FLOAT,              // pass by value
    STR,                // pass by reference
    STR_S,              // pass by reference
    ARRAY_UB,           // pass by reference
    ARRAY_B,            // pass by reference
    ARRAY_UW,           // pass by reference
    ARRAY_W,            // pass by reference
    ARRAY_F,            // pass by reference
    STRUCT;             // pass by reference

    /**
     * is the type assignable to the given other type?
     */
    infix fun isAssignableTo(targetType: DataType) =
            // what types are assignable to others without loss of precision?
            when(this) {
                UBYTE -> targetType in setOf(UBYTE, UWORD, WORD, FLOAT)
                BYTE -> targetType in setOf(BYTE, UBYTE, UWORD, WORD, FLOAT)
                UWORD -> targetType in setOf(UWORD, FLOAT)
                WORD -> targetType in setOf(WORD, UWORD, FLOAT)
                FLOAT -> targetType == FLOAT
                STR -> targetType == STR || targetType==STR_S
                STR_S -> targetType == STR || targetType==STR_S
                in ArrayDatatypes -> targetType == this
                else -> false
            }


    infix fun isAssignableTo(targetTypes: Set<DataType>) = targetTypes.any { this isAssignableTo it }

    infix fun largerThan(other: DataType) =
            when(this) {
                in ByteDatatypes -> false
                in WordDatatypes -> other in ByteDatatypes
                else -> true
            }

    infix fun equalsSize(other: DataType) =
            when(this) {
                in ByteDatatypes -> other in ByteDatatypes
                in WordDatatypes -> other in WordDatatypes
                else -> false
            }
}

enum class Register {
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
    XY
}       // only used in parameter and return value specs in asm subroutines

enum class Statusflag {
    Pc,
    Pz,
    Pv,
    Pn
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
val StringDatatypes = setOf(DataType.STR, DataType.STR_S)
val ArrayDatatypes = setOf(DataType.ARRAY_UB, DataType.ARRAY_B, DataType.ARRAY_UW, DataType.ARRAY_W, DataType.ARRAY_F)
val IterableDatatypes = setOf(
        DataType.STR, DataType.STR_S,
        DataType.ARRAY_UB, DataType.ARRAY_B,
        DataType.ARRAY_UW, DataType.ARRAY_W,
        DataType.ARRAY_F)
val PassByValueDatatypes = NumericDatatypes
val PassByReferenceDatatypes = IterableDatatypes.plus(DataType.STRUCT)
val ArrayElementTypes = mapOf(
        DataType.ARRAY_B to DataType.BYTE,
        DataType.ARRAY_UB to DataType.UBYTE,
        DataType.ARRAY_W to DataType.WORD,
        DataType.ARRAY_UW to DataType.UWORD,
        DataType.ARRAY_F to DataType.FLOAT)

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
}

data class Position(val file: String, val line: Int, val startCol: Int, val endCol: Int) {
    override fun toString(): String = "[$file: line $line col ${startCol+1}-${endCol+1}]"
}
