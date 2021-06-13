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

    private fun parseModule(srcText: String): prog8Parser.ModuleContext {
        val lexer = prog8Lexer(CharStreams.fromString(srcText))
        val tokens = CommonTokenStream(lexer)
        val parser = prog8Parser(tokens)
        //parser.errorHandler = BailErrorStrategy()
        parser.addErrorListener(MyErrorListener())
        return parser.module()
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
    fun testModuleSourceNeedNotEndWithNewline() {
        val nl = "\n" // say, Unix-style (different flavours tested elsewhere)
        val srcText = "foo {" + nl + "}"   // source ends with '}' (= NO newline, issue #40)

        // before the fix, prog8Parser would have reported (thrown) "missing <EOL> at '<EOF>'"
        val parseTree = parseModule(srcText)
        assertEquals(parseTree.block().size, 1)
    }

    @Test
    fun testModuleSourceMayEndWithNewline() {
        val nl = "\n" // say, Unix-style (different flavours tested elsewhere)
        val srcText = "foo {" + nl + "}" + nl  // source does end with a newline (issue #40)
        val parseTree = parseModule(srcText)
        assertEquals(parseTree.block().size, 1)
    }

    @Test
    fun testAllBlocksButLastMustEndWithNewline() {
        val nl = "\n" // say, Unix-style (different flavours tested elsewhere)

        // BAD: 2nd block `bar` does NOT start on new line; however, there's is a nl at the very end
        val srcBad = "foo {" + nl + "}" + " bar {" + nl + "}" + nl

        // GOOD: 2nd block `bar` does start on a new line; however, a nl at the very end ain't needed
        val srcGood = "foo {" + nl + "}" + nl + "bar {" + nl + "}"

        assertFailsWith<ParsingFailedError> { parseModule(srcBad) }
        val parseTree = parseModule(srcGood)
        assertEquals(parseTree.block().size, 2)
    }

    @Test
    fun testWindowsAndMacNewlinesAreAlsoFine() {
        val nlWin = "\r\n"
        val nlUnix = "\n"
        val nlMac = "\r"

        // a good mix of all kinds of newlines:
        val srcText =
            "foo {" +
            nlWin +
            "}" +
            nlUnix +
            nlMac +     // both these newlines should be "eaten up" by just one EOL token
            "bar {" +
            nlMac +
            nlWin +
            nlUnix +   // all three should be "eaten up" by just one EOL token
            "}"

        val parseTree = parseModule(srcText)
        assertEquals(parseTree.block().size, 2)
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
