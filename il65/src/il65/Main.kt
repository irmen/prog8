package il65

import il65.ast.Label
import il65.ast.LiteralValue
import il65.ast.PrefixExpression
import il65.ast.toAst
import il65.parser.il65Lexer
import il65.parser.il65Parser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.misc.IntegerList


class MyTokenStream(lexer: Lexer) : CommonTokenStream(lexer) {

    data class Comment(val type: String, val line: Int, val comment: String)

    fun commentTokens() : List<Comment> {
        // extract the comments
        val commentTokenChannel = il65Lexer.channelNames.indexOf("HIDDEN")
        val theLexer = tokenSource as Lexer
        return  get(0, size())
                .filter { it.channel == commentTokenChannel }
                .map {
                    Comment(theLexer.vocabulary.getSymbolicName(it.type),
                            it.line, it.text.substringAfter(';').trim())
                }
    }
}



fun main(args: Array<String>) {
    // println("Reading source file: ${args[0]}")

    val input = CharStreams.fromString(
            "%zp clobber,derp,33,de\n" +
                    "~ main \$c000  { \n" +
                    " A=4 + 99\n" +
                    "     ; full position comment\n" +
                    " %asm {{\n" +
                    "  position 1\n"+
                    "\t\tposition 2\n"+
                    "  }}\n" +
                    " X=Y    ; comment hooo\n"+
                    "}\n"
    )
    val lexer = il65Lexer(input)
    val tokens = MyTokenStream(lexer)
    val parser = il65Parser(tokens)
    val module = parser.module()
    val program = module.toAst(true)

    // the comments:
    tokens.commentTokens().forEach { println(it) }

    program.lines.map {
        println(it)
    }
}


