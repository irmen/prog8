package il65

import il65.ast.*
import net.razorvine.il65.parser.tinybasicLexer
import net.razorvine.il65.parser.tinybasicParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream


fun main(args: Array<String>) {
    println("Reading source file: ${args[0]}")
    val input = CharStreams.fromFileName(args[0])
    val lexer = tinybasicLexer(input)
    val tokens = CommonTokenStream(lexer)
    val parser = tinybasicParser(tokens)
    val parseTree: tinybasicParser.ProgramContext = parser.program()
    val program = parseTree.toAst()
    println(program)
}

