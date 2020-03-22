package prog8.compiler.target

import prog8.ast.Program
import prog8.ast.statements.Block
import prog8.ast.statements.VarDecl
import prog8.compiler.CompilationOptions
import prog8.compiler.Zeropage
import java.nio.file.Path


internal interface CompilationTarget {
    companion object {
        lateinit var name: String
        lateinit var machine: IMachineDefinition
        lateinit var encodeString: (str: String, altEncoding: Boolean) -> List<Short>
        lateinit var decodeString: (bytes: List<Short>, altEncoding: Boolean) -> String
        lateinit var asmGenerator: (Program, Zeropage, CompilationOptions, Path) -> IAssemblyGenerator
    }
}
