package prog8.codegen.target.cbm

import prog8.ast.base.*
import prog8.ast.expressions.StringLiteralValue
import prog8.ast.statements.VarDecl
import prog8.compilerinterface.IMemSizer

internal object CbmMemorySizer: IMemSizer {
    override fun memorySize(dt: DataType): Int {
        return when(dt) {
            in ByteDatatypes -> 1
            in WordDatatypes, in PassByReferenceDatatypes -> 2
            DataType.FLOAT -> Mflpt5.FLOAT_MEM_SIZE
            else -> Int.MIN_VALUE
        }
    }

    override fun memorySize(decl: VarDecl): Int {
        return when(decl.type) {
            VarDeclType.CONST -> 0
            VarDeclType.VAR, VarDeclType.MEMORY -> {
                when(val dt = decl.datatype) {
                    in NumericDatatypes -> return memorySize(dt)
                    in ArrayDatatypes -> decl.arraysize!!.constIndex()!! * memorySize(ArrayToElementTypes.getValue(dt))
                    DataType.STR -> (decl.value as StringLiteralValue).value.length + 1
                    else -> 0
                }
            }
        }
    }
}