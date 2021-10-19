package prog8.compiler.target

import com.github.michaelbull.result.fold
import prog8.ast.IMemSizer
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.AssignTarget
import prog8.compiler.CompilationOptions
import prog8.compiler.IErrorReporter
import prog8.compiler.IStringEncoding
import prog8.compiler.Zeropage
import prog8.compiler.target.c64.C64MachineDefinition
import prog8.compiler.target.cbm.Petscii
import prog8.compiler.target.cpu6502.codegen.AsmGen
import prog8.compiler.target.cx16.CX16MachineDefinition
import java.io.CharConversionException
import java.nio.file.Path


interface ICompilationTarget: IStringEncoding, IMemSizer {
    val name: String
    val machine: IMachineDefinition
    override fun encodeString(str: String, altEncoding: Boolean): List<Short>
    override fun decodeString(bytes: List<Short>, altEncoding: Boolean): String
}


internal object C64Target: ICompilationTarget {
    override val name = "c64"
    override val machine = C64MachineDefinition
    override fun encodeString(str: String, altEncoding: Boolean): List<Short> {
        val coded = if (altEncoding) Petscii.encodeScreencode(str, true) else Petscii.encodePetscii(str, true)
        return coded.fold(
            failure = { throw it },
            success = { it }
        )
    }
    override fun decodeString(bytes: List<Short>, altEncoding: Boolean) =
        try {
            if (altEncoding) Petscii.decodeScreencode(bytes, true) else Petscii.decodePetscii(bytes, true)
        } catch (x: CharConversionException) {
            throw CharConversionException("can't decode string: ${x.message}")
        }

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

internal object Cx16Target: ICompilationTarget {
    override val name = "cx16"
    override val machine = CX16MachineDefinition
    override fun encodeString(str: String, altEncoding: Boolean): List<Short> {
        val coded= if (altEncoding) Petscii.encodeScreencode(str, true) else Petscii.encodePetscii(str, true)
        return coded.fold(
            failure = { throw it },
            success = { it }
        )
    }
    override fun decodeString(bytes: List<Short>, altEncoding: Boolean) =
        try {
            if (altEncoding) Petscii.decodeScreencode(bytes, true) else Petscii.decodePetscii(bytes, true)
        } catch (x: CharConversionException) {
            throw CharConversionException("can't decode string: ${x.message}")
        }

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


internal fun asmGeneratorFor(
    compTarget: ICompilationTarget,
    program: Program,
    errors: IErrorReporter,
    zp: Zeropage,
    options: CompilationOptions,
    outputDir: Path
): IAssemblyGenerator
{
    // at the moment we only have one code generation backend (for 6502 and 65c02)
    return AsmGen(program, errors, zp, options, compTarget, outputDir)
}
