package prog8tests

import org.junit.jupiter.api.*
import kotlin.test.*
import prog8tests.helpers.*
import kotlin.io.path.*

import prog8.parser.ParseError
import prog8.parser.Prog8Parser.parseModule
import prog8.parser.SourceCode
import prog8.ast.Node
import prog8.ast.base.Position
import prog8.ast.statements.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestProg8Parser {

    @BeforeAll
    fun setUp() {
        sanityCheckDirectories("compilerAst")
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
        val importedNoExt = fixturesDir.resolve("i_do_not_exist")
        val importedWithExt = fixturesDir.resolve("i_do_not_exist.p8")
        assumeNotExists(importedNoExt)
        assumeNotExists(importedWithExt)

        val text = "%import ${importedNoExt.name}"
        val module = parseModule(SourceCode.of(text))

        assertEquals(1, module.statements.size)
    }


    @Test
    fun testParseModuleWithEmptyString() {
        val module = parseModule(SourceCode.of(""))
        assertEquals(0, module.statements.size)
    }

    @Test
    fun testParseModuleWithEmptyFile() {
        val path = fixturesDir.resolve("empty.p8")
        assumeReadableFile(path)

        val module = parseModule(SourceCode.fromPath(path))
        assertEquals(0, module.statements.size)
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


    fun assertPosition(actual: Position, expFile: String? = null, expLine: Int? = null, expStartCol: Int? = null, expEndCol: Int? = null) {
        require(!listOf(expLine, expStartCol, expEndCol).all { it == null })
        if (expLine != null) assertEquals(expLine, actual.line, ".position.line (1-based)")
        if (expStartCol != null) assertEquals(expStartCol, actual.startCol, ".position.startCol (0-based)" )
        if (expEndCol != null) assertEquals(expEndCol, actual.endCol, ".position.endCol (0-based)")
        if (expFile != null) assertEquals(expFile, actual.file, ".position.file")
    }

    fun assertPosition(actual: Position, expFile: Regex? = null, expLine: Int? = null, expStartCol: Int? = null, expEndCol: Int? = null) {
        require(!listOf(expLine, expStartCol, expEndCol).all { it == null })
        if (expLine != null) assertEquals(expLine, actual.line, ".position.line (1-based)")
        if (expStartCol != null) assertEquals(expStartCol, actual.startCol, ".position.startCol (0-based)" )
        if (expEndCol != null) assertEquals(expEndCol, actual.endCol, ".position.endCol (0-based)")
        // Note: assertContains expects *actual* value first
        if (expFile != null) assertContains(actual.file, expFile, ".position.file")
    }

    fun assertPositionOf(actual: Node, expFile: String? = null, expLine: Int? = null, expStartCol: Int? = null, expEndCol: Int? = null) =
        assertPosition(actual.position, expFile, expLine, expStartCol, expEndCol)

    fun assertPositionOf(actual: Node, expFile: Regex? = null, expLine: Int? = null, expStartCol: Int? = null, expEndCol: Int? = null) =
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
            assertPosition(e.position, path.absolutePathString(), 2, 6) // TODO: endCol wrong
        }
    }

    @Test
    fun testModulePositionForSourceFromString() {
        val srcText = """
            main {
            }
        """.trimIndent()
        val module = parseModule(SourceCode.of(srcText))
        assertPositionOf(module, Regex("^<String@[0-9a-f]+>$"), 1, 0) // TODO: endCol wrong
    }

    @Test
    fun testModulePositionForSourceFromPath() {
        val path = fixturesDir.resolve("simple_main.p8")

        val module = parseModule(SourceCode.fromPath(path))
        assertPositionOf(module, path.absolutePathString(), 1, 0) // TODO: endCol wrong
    }

    @Test
    fun testInnerNodePositionsForSourceFromPath() {
        val path = fixturesDir.resolve("simple_main.p8")

        val module = parseModule(SourceCode.fromPath(path))
        val mpf = module.position.file

        assertPositionOf(module, path.absolutePathString(), 1, 0) // TODO: endCol wrong
        val mainBlock = module.statements.filterIsInstance<Block>()[0]
        assertPositionOf(mainBlock, mpf, 1, 0)  // TODO: endCol wrong!
        val startSub = mainBlock.statements.filterIsInstance<Subroutine>()[0]
        assertPositionOf(startSub, mpf, 2, 4)  // TODO: endCol wrong!
    }

    /**
     * TODO: this test is testing way too much at once
     */
    @Test
    @Disabled
    fun testInnerNodePositionsForSourceFromString() {
        val srcText = """
            %target 16, "abc" ; DirectiveArg directly inherits from Node - neither an Expression nor a Statement..?
            main {
                sub start() {
                    ubyte foo = 42
                    ubyte bar
                    when (foo) {
                        23 -> bar = 'x' ; WhenChoice, also directly inheriting Node
                        42 -> bar = 'y'
                        else -> bar = 'z'
                    }
                }
            }
        """.trimIndent()
        val module = parseModule(SourceCode.of(srcText))
        val mpf = module.position.file

        val targetDirective = module.statements.filterIsInstance<Directive>()[0]
        assertPositionOf(targetDirective, mpf, 1, 0)  // TODO: endCol wrong!
        val mainBlock = module.statements.filterIsInstance<Block>()[0]
        assertPositionOf(mainBlock, mpf, 2, 0)  // TODO: endCol wrong!
        val startSub = mainBlock.statements.filterIsInstance<Subroutine>()[0]
        assertPositionOf(startSub, mpf, 3, 4)  // TODO: endCol wrong!
        val declFoo = startSub.statements.filterIsInstance<VarDecl>()[0]
        assertPositionOf(declFoo, mpf, 4, 8)  // TODO: endCol wrong!
        val rhsFoo = declFoo.value!!
        assertPositionOf(rhsFoo, mpf, 4, 20)  // TODO: endCol wrong!
        val declBar = startSub.statements.filterIsInstance<VarDecl>()[1]
        assertPositionOf(declBar, mpf, 5, 8)  // TODO: endCol wrong!
        val whenStmt = startSub.statements.filterIsInstance<WhenStatement>()[0]
        assertPositionOf(whenStmt, mpf, 6, 8)  // TODO: endCol wrong!
        assertPositionOf(whenStmt.choices[0], mpf, 7, 12)  // TODO: endCol wrong!
        assertPositionOf(whenStmt.choices[1], mpf, 8, 12)  // TODO: endCol wrong!
        assertPositionOf(whenStmt.choices[2], mpf, 9, 12)  // TODO: endCol wrong!
    }

}
