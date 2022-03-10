package prog8.codegen.target.cbm

import prog8.ast.base.ByteDatatypes
import prog8.ast.base.DataType
import prog8.ast.base.PassByReferenceDatatypes
import prog8.ast.base.WordDatatypes
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
}