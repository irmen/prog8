package il65

import il65.ast.toAst
import il65.parser.il65Lexer
import il65.parser.il65Parser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream


fun main(args: Array<String>) {
    // println("Reading source file: ${args[0]}")

    val input = CharStreams.fromString(
            "%zp clobber,derp,33,de\n" +
                    "~ main \$c000  { \n" +
                    " A=4 to 99\n" +
                    " %asm {{\n" +
                    "  line 1\n"+
                    "\t\tline 2\n"+
                    "  }}\n" +
                    "}\n"
    )
    val lexer = il65Lexer(input)
    val tokens = CommonTokenStream(lexer)
    val parser = il65Parser(tokens)

    val module = parser.module()
    val program = module.toAst()

    program.lines.map {
        println(it)
    }
}

