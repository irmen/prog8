package prog8.compiler.target

import com.github.michaelbull.result.fold
import prog8.ast.base.ByteDatatypes
import prog8.ast.base.DataType
import prog8.ast.base.PassByReferenceDatatypes
import prog8.ast.base.WordDatatypes
import prog8.ast.expressions.Expression
import prog8.ast.statements.Subroutine
import prog8.compiler.target.cbm.Petscii
import prog8.compiler.target.cpu6502.codegen.asmsub6502ArgsEvalOrder
import prog8.compiler.target.cpu6502.codegen.asmsub6502ArgsHaveRegisterClobberRisk
import prog8.compiler.target.cx16.CX16MachineDefinition
import prog8.compilerinterface.ICompilationTarget


object Cx16Target: ICompilationTarget {
    override val name = "cx16"
    override val machine = CX16MachineDefinition
    override fun encodeString(str: String, altEncoding: Boolean): List<UByte> {
        val coded= if (altEncoding) Petscii.encodeScreencode(str, true) else Petscii.encodePetscii(str, true)
        return coded.fold(
            failure = { throw it },
            success = { it }
        )
    }
    override fun decodeString(bytes: List<UByte>, altEncoding: Boolean) =
        if (altEncoding) Petscii.decodeScreencode(bytes, true) else Petscii.decodePetscii(bytes, true)

    override fun asmsubArgsEvalOrder(sub: Subroutine): List<Int> = asmsub6502ArgsEvalOrder(sub)
    override fun asmsubArgsHaveRegisterClobberRisk(args: List<Expression>) = asmsub6502ArgsHaveRegisterClobberRisk(args)

    override fun memorySize(dt: DataType): Int {
        return when(dt) {
            in ByteDatatypes -> 1
            in WordDatatypes -> 2
            DataType.FLOAT -> machine.FLOAT_MEM_SIZE
            in PassByReferenceDatatypes -> machine.POINTER_MEM_SIZE
            else -> Int.MIN_VALUE
        }
    }
}

