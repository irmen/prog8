package prog8.compiler.target

import prog8.ast.Program
import prog8.ast.base.ErrorReporter
import prog8.compiler.CompilationOptions
import prog8.compiler.Zeropage
import prog8.compiler.target.c64.C64MachineDefinition
import prog8.compiler.target.c64.Petscii
import prog8.compiler.target.c64.codegen.AsmGen
import prog8.compiler.target.cx16.CX16MachineDefinition
import java.nio.file.Path


internal interface CompilationTarget {
    val name: String
    val machine: IMachineDefinition
    fun encodeString(str: String, altEncoding: Boolean): List<Short>
    fun decodeString(bytes: List<Short>, altEncoding: Boolean): String
    fun asmGenerator(program: Program, errors: ErrorReporter, zp: Zeropage, options: CompilationOptions, path: Path): IAssemblyGenerator

    companion object {
        lateinit var instance: CompilationTarget
    }
}


internal class C64Target: CompilationTarget {
    override val name = "c64"
    override val machine = C64MachineDefinition
    override fun encodeString(str: String, altEncoding: Boolean) =
            if(altEncoding) Petscii.encodeScreencode(str, true) else Petscii.encodePetscii(str, true)
    override fun decodeString(bytes: List<Short>, altEncoding: Boolean) =
            if(altEncoding) Petscii.decodeScreencode(bytes, true) else Petscii.decodePetscii(bytes, true)
    override fun asmGenerator(program: Program, errors: ErrorReporter, zp: Zeropage, options: CompilationOptions, path: Path) =
            AsmGen(program, errors, zp, options, path)
}

internal class Cx16Target: CompilationTarget {
    override val name = "cx16"
    override val machine = CX16MachineDefinition
    override fun encodeString(str: String, altEncoding: Boolean) =
            if(altEncoding) Petscii.encodeScreencode(str, true) else Petscii.encodePetscii(str, true)
    override fun decodeString(bytes: List<Short>, altEncoding: Boolean) =
            if(altEncoding) Petscii.decodeScreencode(bytes, true) else Petscii.decodePetscii(bytes, true)
    override fun asmGenerator(program: Program, errors: ErrorReporter, zp: Zeropage, options: CompilationOptions, path: Path) =
            AsmGen(program, errors, zp, options, path)
}
