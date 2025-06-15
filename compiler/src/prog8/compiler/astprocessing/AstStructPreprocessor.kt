package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.statements.StructDecl
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.code.core.BaseDataType
import prog8.code.core.DataType

// TODO integrate back into AstPreprocessor?

class AstStructPreprocessor(val program: Program) : AstWalker() {

    override fun after(struct: StructDecl, parent: Node): Iterable<IAstModification> {

        // convert str fields to ^^ubyte
        val convertedFields = struct.fields.map {
            if(it.first.isString)
                DataType.pointer(BaseDataType.UBYTE) to it.second       // replace str field with ^^ubyte field
            else
                it.first to it.second
        }.toTypedArray()

        if(!convertedFields.contentEquals(struct.fields))
            convertedFields.copyInto(struct.fields)

        return noModifications
    }

}
