package prog8tests

import org.junit.jupiter.api.Test
import prog8.ast.Node
import prog8.ast.base.Position
import kotlin.test.*
import java.nio.file.Path   // TODO: use kotlin.io.path.Path instead
import kotlin.io.path.*
import prog8.ast.statements.Block
import prog8.ast.statements.Directive
import prog8.parser.ParseError
import prog8.parser.Prog8Parser.parseModule
import prog8.parser.SourceCode


class TestProg8Parser {
    val workingDir = Path("").absolute()    // Note: Path(".") does NOT work..!
    val fixturesDir = workingDir.resolve("test/fixtures")

    @Test
    fun testDirectoriesSanityCheck() {
        assertEquals("compilerAst", workingDir.fileName.toString())
        assertTrue(fixturesDir.isDirectory(), "sanity check; should be directory: $fixturesDir")
    }

    @Test
    fun testModuleSourceNeedNotEndWithNewline() {
        val nl = "\n" // say, Unix-style (different flavours tested elsewhere)
        val src = SourceCode.of("foo {" + nl + "}")   // source ends with '}' (= NO newline, issue #40)

        // #45: Prog8ANTLRParser would report (throw) "missing <EOL> at '<EOF>'"
        val module = parseModule(src)
        assertEquals(1, module.statements.size)
    }

    @Test
    fun testModuleSourceMayEndWithNewline() {
        val nl = "\n" // say, Unix-style (different flavours tested elsewhere)
        val srcText = "foo {" + nl + "}" + nl  // source does end with a newline (issue #40)
        val module = parseModule(SourceCode.of(srcText))
        assertEquals(1, module.statements.size)
    }

    @Test
    fun testAllBlocksButLastMustEndWithNewline() {
        val nl = "\n" // say, Unix-style (different flavours tested elsewhere)

        // BAD: 2nd block `bar` does NOT start on new line; however, there's is a nl at the very end
        val srcBad = "foo {" + nl + "}" + " bar {" + nl + "}" + nl

        // GOOD: 2nd block `bar` does start on a new line; however, a nl at the very end ain't needed
        val srcGood = "foo {" + nl + "}" + nl + "bar {" + nl + "}"

        assertFailsWith<ParseError> { parseModule(SourceCode.of(srcBad)) }
        val module = parseModule(SourceCode.of(srcGood))
        assertEquals(2, module.statements.size)
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

        val module = parseModule(SourceCode.of(srcText))
        assertEquals(2, module.statements.size)
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
        val module = parseModule(SourceCode.of(srcText))
        assertEquals(1, module.statements.size)
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
        val module = parseModule(SourceCode.of(srcText))
        assertEquals(2, module.statements.size)
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
        val module = parseModule(SourceCode.of(srcText))
        assertEquals(1, module.statements.size)
    }

    @Test
    fun testNewlineBetweenTwoBlocksOrDirectivesStillRequired() {
        // issue: #47

        // block and block
        assertFailsWith<ParseError>{ parseModule(SourceCode.of("""
            blockA {
            } blockB {            
            }            
        """)) }

        // block and directive
        assertFailsWith<ParseError>{ parseModule(SourceCode.of("""
            blockB {            
            } %import textio            
        """)) }

        // The following two are bogus due to directive *args* expected to follow the directive name.
        // Leaving them in anyways.

        // dir and block
        assertFailsWith<ParseError>{ parseModule(SourceCode.of("""
            %import textio blockB {            
            }            
        """)) }

        assertFailsWith<ParseError>{ parseModule(SourceCode.of("""
            %import textio %import syslib            
        """)) }
    }

    @Test
    fun parseModuleShouldNotLookAtImports() {
        val imported = "i_do_not_exist"
        val pathNoExt = Path.of(imported).absolute()
        val pathWithExt = Path.of("${pathNoExt}.p8")
        val text = "%import $imported"

        assertFalse(pathNoExt.exists(), "sanity check: file should not exist: $pathNoExt")
        assertFalse(pathWithExt.exists(), "sanity check: file should not exist: $pathWithExt")

        val module = parseModule(SourceCode.of(text))
        assertEquals(1, module.statements.size)
    }


    @Test
    fun testModuleNameForSourceFromString() {
        val srcText = """
            main {
            }
        """.trimIndent()
        val module = parseModule(SourceCode.of(srcText))

        // Note: assertContains has *actual* as first param
        assertContains(module.name, Regex("^anonymous_[0-9a-f]+$"))
    }

    @Test
    fun testModuleNameForSourceFromPath() {
        val path = fixturesDir.resolve("simple_main.p8")

        val module = parseModule(SourceCode.fromPath(path))

        assertEquals(path.nameWithoutExtension, module.name)
    }


    fun assertPosition(actual: Position, expFile: String, expLine: Int, expStartCol: Int, expEndCol: Int) {
        assertEquals(expLine, actual.line, ".position.line (1-based)")
        assertEquals(expStartCol, actual.startCol, ".position.startCol (0-based)" )
        assertEquals(expEndCol, actual.endCol, ".position.endCol (0-based)")
        assertEquals(expFile, actual.file, ".position.file")
    }

    fun assertPosition(actual: Position, expFile: Regex, expLine: Int, expStartCol: Int, expEndCol: Int) {
        assertEquals(expLine, actual.line, ".position.line (1-based)")
        assertEquals(expStartCol, actual.startCol, ".position.startCol (0-based)" )
        assertEquals(expEndCol, actual.endCol, ".position.endCol (0-based)")
        // Note: assertContains expects *actual* value first
        assertContains(actual.file, expFile, ".position.file")
    }

    fun assertPositionOf(actual: Node, expFile: String, expLine: Int, expStartCol: Int, expEndCol: Int) =
        assertPosition(actual.position, expFile, expLine, expStartCol, expEndCol)

    fun assertPositionOf(actual: Node, expFile: Regex, expLine: Int, expStartCol: Int, expEndCol: Int) =
        assertPosition(actual.position, expFile, expLine, expStartCol, expEndCol)


    @Test
    fun testErrorLocationForSourceFromString() {
        val srcText = "bad * { }\n"

        assertFailsWith<ParseError> { parseModule(SourceCode.of(srcText)) }
        try {
            parseModule(SourceCode.of(srcText))
        } catch (e: ParseError) {
            assertPosition(e.position, Regex("^<String@[0-9a-f]+>$"), 1, 4, 4)
    }
    }

    @Test
    fun testErrorLocationForSourceFromPath() {
        val path = fixturesDir.resolve("file_with_syntax_error.p8")

        assertFailsWith<ParseError> { parseModule(SourceCode.fromPath(path)) }
        try {
            parseModule(SourceCode.fromPath(path))
        } catch (e: ParseError) {
            assertPosition(e.position, path.absolutePathString(), 2, 6, 6)
        }
    }

    @Test
    fun testModulePositionForSourceFromString() {
        val srcText = """
            main {
            }
        """.trimIndent()
        val module = parseModule(SourceCode.of(srcText))
        assertPositionOf(module, Regex("^<String@[0-9a-f]+>$"), 1, 0, 0)
    }

    @Test
    fun testModulePositionForSourceFromPath() {
        val path = fixturesDir.resolve("simple_main.p8")

        val module = parseModule(SourceCode.fromPath(path))
        assertPositionOf(module, path.absolutePathString(), 1, 0, 0)
    }

    
    @Test
    fun testProg8Ast() {
        val module = parseModule(SourceCode.of("""
        main {
            sub start() {
                return
            }
        }
        """))
        assertIs<Block>(module.statements.first())
        assertEquals((module.statements.first() as Block).name, "main")
    }
}
