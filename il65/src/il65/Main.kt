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
                    "     ; full position comment\n" +
                    " %asm {{\n" +
                    "  position 1\n"+
                    "\t\tposition 2\n"+
                    "  }}\n" +
                    " X=Y    ; comment hooo\n"+
                    "}\n"
    )
    val lexer = il65Lexer(input)
    val tokens = CommonTokenStream(lexer)
    val parser = il65Parser(tokens)


    val module = parser.module()
    val program = module.toAst(true)

    println(tokens.size())
    val commentTokenChannel = il65Lexer.channelNames.indexOf("HIDDEN")
    tokens.get(0, tokens.size()).filter{it.channel==commentTokenChannel}.map{
        val comment = it.text.substringAfter(';').trim()
        when(lexer.vocabulary.getSymbolicName(it.type)) {
            "COMMENT" -> println("comment at ${it.line}: >>$comment<<")
            "LINECOMMENT" -> println("position comment at ${it.line}: >>$comment<<")
            else -> throw UnsupportedOperationException("comment token type ${it.type}")
        }
    }

    program.lines.map {
        println(it)
    }
}

