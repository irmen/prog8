package prog8.sim

import prog8.ast.base.*
import prog8.ast.expressions.StringLiteral
import prog8.ast.statements.VarDecl
import prog8.compilerinterface.Encoding
import prog8.compilerinterface.IMemSizer
import prog8.compilerinterface.IStringEncoding

internal object MemSizer: IMemSizer {
    override fun memorySize(dt: DataType): Int {
        return when(dt) {
            in ByteDatatypes -> 1
            in WordDatatypes, in PassByReferenceDatatypes -> 2
            DataType.FLOAT -> Double.SIZE_BYTES
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
                    DataType.STR -> (decl.value as StringLiteral).value.length + 1
                    else -> 0
                }
            }
        }
    }
}

internal object StringEncoding: IStringEncoding {
    override fun encodeString(str: String, encoding: Encoding): List<UByte> {
        TODO("Not yet implemented")
    }

    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String {
        TODO("Not yet implemented")
    }
}