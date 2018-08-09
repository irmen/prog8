package il65

import il65.ast.toAst
import il65.parser.il65Lexer
import il65.parser.il65Parser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream


fun main(args: Array<String>) {
    // println("Reading source file: ${args[0]}")

    val input = CharStreams.fromString(
            "AX //= (5+8)*77\n" +
            "X = -3.44e-99")
    val lexer = il65Lexer(input)
    val tokens = CommonTokenStream(lexer)
    val parser = il65Parser(tokens)

    val module = parser.module()
    val program = module.toAst()

    program.lines.map {
        println(it)
    }
}

