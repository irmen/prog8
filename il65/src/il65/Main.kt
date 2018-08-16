package il65

import java.nio.file.Paths
import il65.ast.*
import il65.parser.*
import il65.optimizing.optimizeExpressions
import il65.optimizing.optimizeStatements


fun main(args: Array<String>) {
    try {
        println("\nIL65 compiler by Irmen de Jong (irmen@razorvine.net)")
        println("This software is licensed under the GNU GPL 3.0, see https://www.gnu.org/licenses/gpl.html\n")

        val filepath = Paths.get(args[0]).normalize()
        val moduleAst = importModule(filepath)
        moduleAst.linkParents()
        val globalNamespace = moduleAst.namespace()
        // globalNamespace.debugPrint()

        moduleAst.optimizeExpressions(globalNamespace)
        moduleAst.optimizeStatements(globalNamespace)
        moduleAst.checkValid(globalNamespace)      // check if final tree is valid

        // todo compile to asm...
        moduleAst.statements.forEach {
            println(it)
        }
    } catch (px: ParsingFailedError) {
        System.err.println(px.message)
    }
}


