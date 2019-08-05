package prog8.repl

import prog8.compiler.compileProgram
import prog8.compiler.printAst
import java.io.File
import java.nio.file.Path

class Repl() {


    fun loop() {
        println("~~totally unfinished experimental REPL~~\n")
        while(true) {
            print("> ")
            val input = readLine() ?: break
            val replmodule = createReplModule(input)
            val compilationResult = compileProgram(replmodule, false, false, false)
            printAst(compilationResult.programAst)
            println("")
        }
    }

    private fun createReplModule(input: String): Path {
        val replmodule = File("replmodule.p8")
        replmodule.writeText("""
%option enable_floats
main {
    sub start() {
        ${input.trim()}        
    }
}
""")
        return replmodule.toPath()
    }

}
