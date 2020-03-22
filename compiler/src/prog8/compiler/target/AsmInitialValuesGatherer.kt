package prog8.compiler.target

import prog8.ast.Program
import prog8.ast.base.NumericDatatypes
import prog8.ast.base.VarDeclType
import prog8.ast.processing.IAstVisitor
import prog8.ast.statements.Block
import prog8.ast.statements.VarDecl

internal class AsmInitialValuesGatherer(val program: Program): IAstVisitor {
    val initialValues = mutableMapOf<Block, MutableMap<String, VarDecl>>()

    override fun visit(decl: VarDecl) {
        // collect all variables that have an initialisation value
        super.visit(decl)
        val declValue = decl.value
        if(declValue!=null
                && decl.type== VarDeclType.VAR
                && decl.datatype in NumericDatatypes
                && declValue.constValue(program)!=null) {

            val block = decl.definingBlock()
            var blockInits = initialValues[block]
            if(blockInits==null) {
                blockInits = mutableMapOf()
                initialValues[block] = blockInits
            }
            blockInits[decl.makeScopedName(decl.name)] = decl
        }
    }
}
