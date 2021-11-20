package prog8.compiler.target

import com.github.michaelbull.result.fold
import prog8.ast.base.ByteDatatypes
import prog8.ast.base.DataType
import prog8.ast.base.PassByReferenceDatatypes
import prog8.ast.base.WordDatatypes
import prog8.compiler.target.c64.C64MachineDefinition
import prog8.compiler.target.cbm.Petscii
import prog8.compilerinterface.ICompilationTarget


object C64Target: ICompilationTarget {
    override val name = "c64"
    override val machine = C64MachineDefinition
    override fun encodeString(str: String, altEncoding: Boolean): List<UByte> {
        val coded = if (altEncoding) Petscii.encodeScreencode(str, true) else Petscii.encodePetscii(str, true)
        return coded.fold(
            failure = { throw it },
            success = { it }
        )
    }
    override fun decodeString(bytes: List<UByte>, altEncoding: Boolean) =
        if (altEncoding) Petscii.decodeScreencode(bytes, true) else Petscii.decodePetscii(bytes, true)

    override fun memorySize(dt: DataType): Int {
        return when(dt) {
            in ByteDatatypes -> 1
            in WordDatatypes -> 2
            DataType.FLOAT -> machine.FLOAT_MEM_SIZE
            in PassByReferenceDatatypes -> machine.POINTER_MEM_SIZE
            else -> -9999999
        }
    }
}
