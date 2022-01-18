package prog8.codegen.target

import com.github.michaelbull.result.fold
import prog8.ast.base.*
import prog8.ast.expressions.Expression
import prog8.ast.statements.RegisterOrStatusflag
import prog8.ast.statements.Subroutine
import prog8.codegen.target.cbm.Petscii
import prog8.codegen.target.cpu6502.codegen.asmsub6502ArgsEvalOrder
import prog8.codegen.target.cpu6502.codegen.asmsub6502ArgsHaveRegisterClobberRisk
import prog8.codegen.target.cx16.CX16MachineDefinition
import prog8.compilerinterface.Encoding
import prog8.compilerinterface.ICompilationTarget


object Cx16Target: ICompilationTarget {
    override val name = "cx16"
    override val machine = CX16MachineDefinition()
    override fun encodeString(str: String, encoding: Encoding): List<UByte> {
        val coded = when(encoding) {
            Encoding.PETSCII -> Petscii.encodePetscii(str, true)
            Encoding.SCREENCODES -> Petscii.encodeScreencode(str, true)
            Encoding.ISO -> TODO("cx16 iso-encoding")
            else -> throw FatalAstException("unsupported encoding $encoding")
        }
        return coded.fold(
            failure = { throw it },
            success = { it }
        )
    }
    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String {
        return when(encoding) {
            Encoding.PETSCII -> Petscii.decodePetscii(bytes, true)
            Encoding.SCREENCODES -> Petscii.decodeScreencode(bytes, true)
            Encoding.ISO -> TODO("cx16 iso-encoding")
            else -> throw FatalAstException("unsupported encoding $encoding")
        }
    }


    override fun asmsubArgsEvalOrder(sub: Subroutine): List<Int> =
        asmsub6502ArgsEvalOrder(sub)
    override fun asmsubArgsHaveRegisterClobberRisk(args: List<Expression>, paramRegisters: List<RegisterOrStatusflag>) =
        asmsub6502ArgsHaveRegisterClobberRisk(args, paramRegisters)

    override fun memorySize(dt: DataType): Int {
        return when(dt) {
            in ByteDatatypes -> 1
            in WordDatatypes, in PassByReferenceDatatypes -> 2
            DataType.FLOAT -> machine.FLOAT_MEM_SIZE
            else -> Int.MIN_VALUE
        }
    }
}

