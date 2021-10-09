package prog8tests

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.ParseCancellationException
import org.junit.jupiter.api.Test
import prog8.ast.IBuiltinFunctions
import prog8.ast.IMemSizer
import prog8.ast.IStringEncoding
import prog8.ast.antlr.toAst
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.Block
import prog8.parser.ParsingFailedError
import prog8.parser.prog8Lexer
import prog8.parser.prog8Parser
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TestAntlrParser {

    class MyErrorListener: ConsoleErrorListener() {
        override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int, charPositionInLine: Int, msg: String, e: RecognitionException?) {
            throw ParsingFailedError("line $line:$charPositionInLine $msg")
        }
    }

    class MyErrorStrategy: BailErrorStrategy() {
        override fun recover(recognizer: Parser?, e: RecognitionException?) {
            try {
                // let it fill in e in all the contexts
                super.recover(recognizer, e)
            } catch (pce: ParseCancellationException) {
                reportError(recognizer, e)
            }
        }

        override fun recoverInline(recognizer: Parser?): Token {
            throw InputMismatchException(recognizer)
        }
    }

    private fun parseModule(srcText: String): prog8Parser.ModuleContext {
        return parseModule(CharStreams.fromString(srcText))
    }

    private fun parseModule(srcFile: Path): prog8Parser.ModuleContext {
        return parseModule(CharStreams.fromPath(srcFile))
    }

    private fun parseModule(srcStream: CharStream): prog8Parser.ModuleContext {
        val errorListener = MyErrorListener()
        val lexer = prog8Lexer(srcStream)
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)
        val tokens = CommonTokenStream(lexer)
        val parser = prog8Parser(tokens)
        parser.errorHandler = MyErrorStrategy()
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)
        return parser.module()
    }

    object DummyEncoding: IStringEncoding {
        override fun encodeString(str: String, altEncoding: Boolean): List<Short> {
            TODO("Not yet implemented")
        }

        override fun decodeString(bytes: List<Short>, altEncoding: Boolean): String {
            TODO("Not yet implemented")
        }
    }

    object DummyFunctions: IBuiltinFunctions {
        override val names: Set<String> = emptySet()
        override val purefunctionNames: Set<String> = emptySet()
        override fun constValue(name: String, args: List<Expression>, position: Position, memsizer: IMemSizer): NumericLiteralValue? = null
        override fun returnType(name: String, args: MutableList<Expression>) = InferredTypes.InferredType.unknown()
    }

    object DummyMemsizer: IMemSizer {
        override fun memorySize(dt: DataType): Int = 0
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

        //parseModule(Paths.get("test", "fixtures", "mac_newlines.p8").toAbsolutePath())

        // a good mix of all kinds of newlines:
        val srcText =
            "foo {" +
            nlMac +
            nlWin +
            "}" +
            nlMac +     // <-- do test a single \r (!) where an EOL is expected
            "bar {" +
            nlUnix +
            "}" +
            nlUnix + nlMac   // both should be "eaten up" by just one EOL token
            "combi {" +
            nlMac + nlWin + nlUnix   // all three should be "eaten up" by just one EOL token
            "}" +
            nlUnix      // end with newline (see testModuleSourceNeedNotEndWithNewline)

        val parseTree = parseModule(srcText)
        assertEquals(parseTree.block().size, 2)
    }

    @Test
    fun testInterleavedEolAndCommentBeforeFirstBlock() {
        // issue: #47
        val srcText = """
            ; comment
            
            ; comment
            
            blockA {            
            }
"""
        val parseTree = parseModule(srcText)
        assertEquals(parseTree.block().size, 1)
    }

    @Test
    fun testInterleavedEolAndCommentBetweenBlocks() {
        // issue: #47
        val srcText = """
            blockA {
            }
            ; comment
            
            ; comment
            
            blockB {            
            }
"""
        val parseTree = parseModule(srcText)
        assertEquals(parseTree.block().size, 2)
    }

    @Test
    fun testInterleavedEolAndCommentAfterLastBlock() {
        // issue: #47
        val srcText = """
            blockA {            
            }
            ; comment
            
            ; comment
            
"""
        val parseTree = parseModule(srcText)
        assertEquals(parseTree.block().size, 1)
    }

    @Test
    fun testNewlineBetweenTwoBlocksOrDirectivesStillRequired() {
        // issue: #47

        // block and block
        assertFailsWith<ParsingFailedError>{ parseModule("""
            blockA {
            } blockB {            
            }            
        """) }

        // block and directive
        assertFailsWith<ParsingFailedError>{ parseModule("""
            blockB {            
            } %import textio            
        """) }

        // The following two are bogus due to directive *args* expected to follow the directive name.
        // Leaving them in anyways.

        // dir and block
        assertFailsWith<ParsingFailedError>{ parseModule("""
            %import textio blockB {            
            }            
        """) }

        assertFailsWith<ParsingFailedError>{ parseModule("""
            %import textio %import syslib            
        """) }
    }

    /*
    @Test
    fun testImportLibraryModule() {
        val program = Program("foo", mutableListOf(), DummyFunctions, DummyMemsizer)
        val importer = ModuleImporter(program, DummyEncoding, "blah", listOf("./test/fixtures"))

        //assertFailsWith<ParsingFailedError>(){ importer.importLibraryModule("import_file_with_syntax_error") }
    }
    */

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

        val ast = parser.module().toAst("test", Path.of(""), DummyEncoding)
        assertIs<Block>(ast.statements.first())
        assertEquals((ast.statements.first() as Block).name, "main")
    }
}
