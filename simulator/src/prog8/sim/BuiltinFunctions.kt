package prog8.sim

import prog8.ast.base.DataType
import prog8.ast.statements.VarDecl
import prog8.compilerinterface.Encoding
import prog8.compilerinterface.IMemSizer
import prog8.compilerinterface.IStringEncoding

internal object MemSizer: IMemSizer {
    override fun memorySize(dt: DataType): Int {
        TODO("Not yet implemented")
    }

    override fun memorySize(decl: VarDecl): Int {
        TODO("Not yet implemented")
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