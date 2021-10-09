package prog8.parser

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Lexer

internal class CommentHandlingTokenStream(lexer: Lexer) : CommonTokenStream(lexer) {

    data class Comment(val type: String, val line: Int, val comment: String)

    fun commentTokens() : List<Comment> {
        // extract the comments
        val commentTokenChannel = Prog8ANTLRLexer.channelNames.indexOf("HIDDEN")
        val theLexer = tokenSource as Lexer
        return get(0, size())
                .asSequence()
                .filter { it.channel == commentTokenChannel }
                .map {
                    Comment(theLexer.vocabulary.getSymbolicName(it.type),
                            it.line, it.text.substringAfter(';').trim())
                }
                .toList()
    }
}
