package il65

import il65.ast.IStatement
import il65.ast.Module
import il65.ast.toAst
import il65.parser.il65Lexer
import il65.parser.il65Parser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Lexer


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

public inline fun <R> Module.map(transform: (IStatement) -> R): List<R> {
    val result = ArrayList<R>(lines.size)
    for (line in this.lines) {
        result.add(transform(line))
    }
    return result
}


fun main(args: Array<String>) {
    // println("Reading source file: ${args[0]}")

    val input = CharStreams.fromString(
                    "~ main \$c000  { \n" +
                            " const byte hopla=55-33\n"+
                            " const byte hopla2=55-hopla\n"+
                            " A = \"derp\" * (2-2) \n" +
                            "}\n")
    val lexer = il65Lexer(input)
    val tokens = MyTokenStream(lexer)
    val parser = il65Parser(tokens)
    var moduleAst = parser.module().toAst(true).optimized()

    // the comments:
    tokens.commentTokens().forEach { println(it) }

    moduleAst.lines.map {
        println(it)
    }
}


