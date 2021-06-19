package prog8tests

import org.junit.jupiter.api.Test
import prog8.ast.statements.Block
import prog8.parser.ParseError
import prog8.parser.Prog8Parser
import prog8.parser.Prog8Parser.parseModule
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile
import kotlin.test.*

class TestProg8Parser {

    @Test
    fun testModuleSourceNeedNotEndWithNewline() {
        val nl = "\n" // say, Unix-style (different flavours tested elsewhere)
        val srcText = "foo {" + nl + "}"   // source ends with '}' (= NO newline, issue #40)

        // #45: Prog8ANTLRParser would report (throw) "missing <EOL> at '<EOF>'"
        val module = parseModule(srcText)
        assertEquals(1, module.statements.size)
    }

    @Test
    fun testModuleSourceMayEndWithNewline() {
        val nl = "\n" // say, Unix-style (different flavours tested elsewhere)
        val srcText = "foo {" + nl + "}" + nl  // source does end with a newline (issue #40)
        val module = parseModule(srcText)
        assertEquals(1, module.statements.size)
    }

    @Test
    fun testAllBlocksButLastMustEndWithNewline() {
        val nl = "\n" // say, Unix-style (different flavours tested elsewhere)

        // BAD: 2nd block `bar` does NOT start on new line; however, there's is a nl at the very end
        val srcBad = "foo {" + nl + "}" + " bar {" + nl + "}" + nl

        // GOOD: 2nd block `bar` does start on a new line; however, a nl at the very end ain't needed
        val srcGood = "foo {" + nl + "}" + nl + "bar {" + nl + "}"

        assertFailsWith<ParseError> { parseModule(srcBad) }
        val module = parseModule(srcGood)
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

        val module = parseModule(srcText)
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
        val module = parseModule(srcText)
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
        val module = parseModule(srcText)
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
        val module = parseModule(srcText)
        assertEquals(1, module.statements.size)
    }

    @Test
    fun testNewlineBetweenTwoBlocksOrDirectivesStillRequired() {
        // issue: #47

        // block and block
        assertFailsWith<ParseError>{ parseModule("""
            blockA {
            } blockB {            
            }            
        """) }

        // block and directive
        assertFailsWith<ParseError>{ parseModule("""
            blockB {            
            } %import textio            
        """) }

        // The following two are bogus due to directive *args* expected to follow the directive name.
        // Leaving them in anyways.

        // dir and block
        assertFailsWith<ParseError>{ parseModule("""
            %import textio blockB {            
            }            
        """) }

        assertFailsWith<ParseError>{ parseModule("""
            %import textio %import syslib            
        """) }
    }

    @Test
    fun testParseModuleWithDirectoryPath() {
        val srcPath = Path.of("test", "fixtures")
        assertTrue(srcPath.isDirectory(), "sanity check: should be a directory")
        assertFailsWith<java.nio.file.AccessDeniedException> { Prog8Parser.parseModule(srcPath) }
    }

    @Test
    fun testParseModuleWithNonExistingPath() {
        val srcPath = Path.of("test", "fixtures", "i_do_not_exist")
        assertFalse(srcPath.exists(), "sanity check: file should not exist")
        assertFailsWith<java.nio.file.NoSuchFileException> { Prog8Parser.parseModule(srcPath) }
    }

    @Test
    fun testParseModuleWithPathMissingExtension_p8() {
        val srcPathWithoutExt = Path.of("test", "fixtures", "file_with_syntax_error")
        val srcPathWithExt = Path.of(srcPathWithoutExt.toString() + ".p8")
        assertTrue(srcPathWithExt.isRegularFile(), "sanity check: should be normal file")
        assertTrue(srcPathWithExt.isReadable(), "sanity check: should be readable")
        assertFailsWith<java.nio.file.NoSuchFileException> { Prog8Parser.parseModule(srcPathWithoutExt) }
    }

    @Test
    fun testParseModuleWithStringShouldNotLookAtImports() {
        val srcText = "%import i_do_not_exist"
        val module = Prog8Parser.parseModule(srcText)
        assertEquals(1, module.statements.size)
    }

    @Test
    fun testParseModuleWithPathShouldNotLookAtImports() {
        val srcPath = Path.of("test", "fixtures", "import_nonexisting.p8")
        val module = Prog8Parser.parseModule(srcPath)
        assertEquals(1, module.statements.size)
    }

    @Test
    fun testErrorLocationForSourceFromString() {
        val srcText = "bad * { }\n"

        assertFailsWith<ParseError> { parseModule(srcText) }
        try {
            parseModule(srcText)
        } catch (e: ParseError) {
            // Note: assertContains expects *actual* value first
            assertContains(e.position.file, Regex("^<String@[0-9a-f]+>$"))
            assertEquals(1, e.position.line, "line; should be 1-based")
            assertEquals(4, e.position.startCol, "startCol; should be 0-based" )
            assertEquals(4, e.position.endCol, "endCol; should be 0-based")
    }
    }

    @Test
    fun testErrorLocationForSourceFromPath() {
        val filename = "file_with_syntax_error.p8"
        val path = Path.of("test", "fixtures", filename)

        assertFailsWith<ParseError> { parseModule(path) }
        try {
            parseModule(path)
        } catch (e: ParseError) {
            assertEquals(filename, e.position.file, "provenance; should be the path's filename, incl. extension '.p8'")
            assertEquals(2, e.position.line, "line; should be 1-based")
            assertEquals(6, e.position.startCol, "startCol; should be 0-based" )
            assertEquals(6, e.position.endCol, "endCol; should be 0-based")
        }
    }

    @Test
    fun testProg8Ast() {
        val module = parseModule("""
main {
    sub start() {
        return
    }
}
""")
        assertIs<Block>(module.statements.first())
        assertEquals((module.statements.first() as Block).name, "main")
    }
}
