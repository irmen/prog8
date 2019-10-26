package prog8.compiler.target

import prog8.ast.Program
import prog8.compiler.CompilationOptions
import prog8.compiler.Zeropage
import java.nio.file.Path

internal interface CompilationTarget {
    companion object {
        lateinit var name: String
        lateinit var machine: IMachineDefinition
        lateinit var encodeString: (str: String) -> List<Short>
        lateinit var asmGenerator: (Program, Zeropage, CompilationOptions, Path) -> IAssemblyGenerator
    }
}
