package prog8.codegen.target

import com.github.michaelbull.result.fold
import prog8.ast.base.*
import prog8.ast.expressions.Expression
import prog8.ast.statements.RegisterOrStatusflag
import prog8.ast.statements.Subroutine
import prog8.codegen.target.c128.C128MachineDefinition
import prog8.codegen.target.cbm.Petscii
import prog8.codegen.target.cpu6502.codegen.asmsub6502ArgsEvalOrder
import prog8.codegen.target.cpu6502.codegen.asmsub6502ArgsHaveRegisterClobberRisk
import prog8.compilerinterface.Encoding
import prog8.compilerinterface.ICompilationTarget


object C128Target: ICompilationTarget {
    override val name = "c128"
    override val machine = C128MachineDefinition()
    override fun encodeString(str: String, encoding: Encoding): List<UByte> {           // TODO use Result
        val coded = when(encoding) {
            Encoding.PETSCII -> Petscii.encodePetscii(str, true)
            Encoding.SCREENCODES -> Petscii.encodeScreencode(str, true)
            else -> throw FatalAstException("unsupported encoding $encoding")
        }
        return coded.fold(
            failure = { throw it },
            success = { it }
        )
    }
    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String {         // TODO use Result
        val decoded = when(encoding) {
            Encoding.PETSCII -> Petscii.decodePetscii(bytes, true)
            Encoding.SCREENCODES -> Petscii.decodeScreencode(bytes, true)
            else -> throw FatalAstException("unsupported encoding $encoding")
        }
        return decoded.fold(
            failure = { throw it },
            success = { it }
        )
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
