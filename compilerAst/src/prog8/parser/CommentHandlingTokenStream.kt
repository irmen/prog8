package prog8.parser

import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.Token

class CommentHandlingTokenStream(lexer: Lexer) : CommonTokenStream(lexer) {

    fun leadingBlockCommentsBefore(tokenIndex: Int): List<String> {
        fill()
        val comments = mutableListOf<String>()
        var index = tokenIndex - 1
        while(index >= 0) {
            val token = get(index)
            when {
                token.channel == Token.HIDDEN_CHANNEL && token.type == Prog8ANTLRLexer.BLOCK_COMMENT ->
                    comments.add(0, token.text)
                token.channel == Token.HIDDEN_CHANNEL || token.type == Prog8ANTLRLexer.EOL -> Unit
                else -> break
            }
            index--
        }
        return comments
    }
}
