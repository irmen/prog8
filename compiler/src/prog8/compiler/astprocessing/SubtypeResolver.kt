package prog8.compiler.astprocessing

import prog8.ast.FatalAstException
import prog8.code.StStruct
import prog8.code.SymbolTable
import prog8.code.ast.*
import prog8.code.core.DataType
import prog8.code.core.ISubType

/**
 * Resolves subtype references in the simplified AST.
 * 
 * After parsing, struct type references may be stored as generic ISubType instances.
 * This postprocessing pass converts them to proper StStruct references from the
 * symbol table, which is required for later code generation phases.
 */
internal object SubtypeResolver {

    /**
     * Process the entire AST, resolving all subtype references.
     */
    fun resolve(program: PtProgram, st: SymbolTable) {
        walkAst(program) { node, _ ->
            fixSubtypes(node, st)
            true  // Continue traversal
        }
    }

    private fun getStStruct(subType: ISubType, st: SymbolTable): StStruct {
        if(subType is StStruct)
            return subType
        val stNode = st.lookup(subType.scopedNameString) as? StStruct
        if(stNode != null)
            return stNode
        else
            throw FatalAstException("cannot find in ST: ${subType.scopedNameString} $subType")
    }

    private fun fixSubtypeIntoStType(type: DataType, st: SymbolTable) {
        if(type.subType!=null && type.subType !is StStruct) {
            type.subType = getStStruct(type.subType!!, st)
        }
    }

    private fun fixSubtypes(node: PtNode, st: SymbolTable) {
        when(node) {
            is IPtVariable -> {
                fixSubtypeIntoStType(node.type, st)
                // if it's an array, fix the subtypes of its elements as well
                if(node.type.isArray && node is PtVariable) {
                    (node.value as? PtArray)?.let {array ->
                        array.children.forEach { fixSubtypes(it, st) }
                    }
                }
            }
            is PtPointerDeref -> fixSubtypeIntoStType(node.type, st)
            is PtStructDecl -> node.fields.forEach { fixSubtypeIntoStType(it.first, st) }
            is PtAsmSub -> node.returns.forEach { fixSubtypeIntoStType(it.second, st) }
            is PtExpression -> {
                fixSubtypeIntoStType(node.type, st)
                if(node is PtTypeCast) {
                    if(node.type.isUnsignedWord && node.value.type.isPointerToByte) {
                        // casting a pointer to a byte , to uword, is not required because pointer arithmetic on either of those will be identical
                        val idx = node.parent.children.indexOf(node)
                        node.parent.children[idx] = node.value
                        node.value.parent = node.parent
                    }
                }
            }
            is PtSubSignature -> node.returns.forEach { fixSubtypeIntoStType(it, st) }
            is PtSubroutineParameter -> fixSubtypeIntoStType(node.type, st)
            else -> { /* has no datatype */ }
        }
    }
}
