package prog8.code.optimize

import prog8.code.ast.*
import prog8.code.core.BaseDataType
import prog8.code.core.DataType

/**
 * Memory and pointer optimizations.
 * Handles address-of/dereference cancellation and struct field access.
 */
internal object MemoryOptimizers {

    /**
     * Optimizes address-of followed by dereference: @(&x) -> x (for byte types only)
     */
    fun optimizeAddressOfDereference(program: PtProgram): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            if (node is PtMemoryByte) {
                // @(&x) -> x  (only if x is a byte type)
                val addressOf = node.address as? PtAddressOf
                if (addressOf != null && addressOf.identifier != null) {
                    val identifier = addressOf.identifier!!
                    if (identifier.type.isByteOrBool) {
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = identifier
                        changes++
                    }
                }
            }
            true
        }
        return changes
    }

    /**
     * Optimizes lsb()/msb() builtin calls on struct field dereferences.
     * msb(struct.field) --> @(&struct.field+1)
     * lsb(struct.field) --> @(&struct.field)
     */
    fun optimizeLsbMsbOnStructfields(program: PtProgram): Int {
        var changes = 0
        walkAst(program) { node: PtNode, depth: Int ->
            if (node is PtFunctionCall && node.builtin && (node.name=="msb" || node.name=="lsb")) {
                if(node.args[0] is PtPointerDeref) {
                    if(!node.args[0].type.isByteOrBool) {
                        // msb(struct.field) -->  @(&struct.field+1)
                        // lsb(struct.field) -->  @(&struct.field)
                        val addressOfDeref = PtAddressOf(DataType.UWORD, false, node.args[0].position)
                        addressOfDeref.add(node.args[0])
                        val address: PtExpression
                        if(node.name=="msb") {
                            address = PtBinaryExpression("+", addressOfDeref.type, addressOfDeref.position)
                            address.add(addressOfDeref)
                            address.add(PtNumber(BaseDataType.UWORD, 1.0, addressOfDeref.position))
                        } else {
                            address = addressOfDeref
                        }
                        val memread = PtMemoryByte(address.position)
                        memread.add(address)
                        memread.parent = node.parent
                        val index = node.parent.children.indexOf(node)
                        node.parent.children[index] = memread
                        changes++
                    }
                }
            }
            true
        }

        return changes
    }
}
