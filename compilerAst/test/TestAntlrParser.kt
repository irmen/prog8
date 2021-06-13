package prog8tests

import org.antlr.v4.runtime.*
import org.junit.jupiter.api.Test
import prog8.ast.IStringEncoding
import prog8.ast.antlr.toAst
import prog8.ast.statements.Block
import prog8.parser.*
import java.nio.file.Path
import kotlin.test.*

class TestAntlrParser {

    class MyErrorListener: ConsoleErrorListener() {
        override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int, charPositionInLine: Int, msg: String, e: RecognitionException?) {
            throw ParsingFailedError(msg)
        }
    }

    object TestStringEncoding: IStringEncoding {
        override fun encodeString(str: String, altEncoding: Boolean): List<Short> {
            TODO("Not yet implemented")
        }

        override fun decodeString(bytes: List<Short>, altEncoding: Boolean): String {
            TODO("Not yet implemented")
        }
    }

    @Test
    fun testAntlrTree() {
        // can create charstreams from many other sources as well;
        val charstream = CharStreams.fromString("""
main {
    sub start() {
        return
    }
}
""")
        val lexer = prog8Lexer(charstream)
        val tokens = CommonTokenStream(lexer)
        val parser = prog8Parser(tokens)
        parser.errorHandler = BailErrorStrategy()
//        parser.removeErrorListeners()
//        parser.addErrorListener(MyErrorListener())
        val nodes = parser.module()
        val blockName = nodes.block(0).identifier().NAME().text
        assertEquals(blockName, "main")
    }

    @Test
    fun testProg8Ast() {
        // can create charstreams from many other sources as well;
        val charstream = CharStreams.fromString("""
main {
    sub start() {
        return
    }
}
""")
        val lexer = prog8Lexer(charstream)
        val tokens = CommonTokenStream(lexer)
        val parser = prog8Parser(tokens)
        parser.errorHandler = BailErrorStrategy()
//        parser.removeErrorListeners()
//        parser.addErrorListener(MyErrorListener())

        val ast = parser.module().toAst("test", false, Path.of(""), TestStringEncoding)
        assertIs<Block>(ast.statements.first())
        assertEquals((ast.statements.first() as Block).name, "main")
    }
}
