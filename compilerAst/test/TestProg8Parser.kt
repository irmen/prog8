package prog8tests

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.base.Position
import prog8.ast.expressions.CharLiteral
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.RangeExpr
import prog8.ast.expressions.StringLiteralValue
import prog8.ast.statements.*
import prog8.parser.ParseError
import prog8.parser.Prog8Parser.parseModule
import prog8.parser.SourceCode
import prog8tests.helpers.assumeNotExists
import prog8tests.helpers.assumeReadableFile
import prog8tests.helpers.fixturesDir
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestProg8Parser {

    @Nested
    inner class Newline {

        @Nested
        inner class AtEnd {

            @Test
            fun `is not required - #40, fixed by #45`() {
                val nl = "\n" // say, Unix-style (different flavours tested elsewhere)
                val src = SourceCode.Text("foo {" + nl + "}")   // source ends with '}' (= NO newline, issue #40)

                // #40: Prog8ANTLRParser would report (throw) "missing <EOL> at '<EOF>'"
                val module = parseModule(src)
                assertEquals(1, module.statements.size)
            }

            @Test
            fun `is still accepted - #40, fixed by #45`() {
                val nl = "\n" // say, Unix-style (different flavours tested elsewhere)
                val srcText = "foo {" + nl + "}" + nl  // source does end with a newline (issue #40)
                val module = parseModule(SourceCode.Text(srcText))
                assertEquals(1, module.statements.size)
            }
        }

        @Test
        fun `is required after each block except the last`() {
            val nl = "\n" // say, Unix-style (different flavours tested elsewhere)

            // BAD: 2nd block `bar` does NOT start on new line; however, there's is a nl at the very end
            val srcBad = "foo {" + nl + "}" + " bar {" + nl + "}" + nl

            // GOOD: 2nd block `bar` does start on a new line; however, a nl at the very end ain't needed
            val srcGood = "foo {" + nl + "}" + nl + "bar {" + nl + "}"

            assertFailsWith<ParseError> { parseModule(SourceCode.Text(srcBad)) }
            val module = parseModule(SourceCode.Text(srcGood))
            assertEquals(2, module.statements.size)
        }

        @Test
        fun `is required between two Blocks or Directives - #47`() {
            // block and block
            assertFailsWith<ParseError>{ parseModule(SourceCode.Text("""
                blockA {
                } blockB {            
                }            
            """)) }

            // block and directive
            assertFailsWith<ParseError>{ parseModule(SourceCode.Text("""
                blockB {            
                } %import textio            
            """)) }

            // The following two are bogus due to directive *args* expected to follow the directive name.
            // Leaving them in anyways.

            // dir and block
            assertFailsWith<ParseError>{ parseModule(SourceCode.Text("""
                %import textio blockB {            
                }            
            """)) }

            assertFailsWith<ParseError>{ parseModule(SourceCode.Text("""
                %import textio %import syslib            
            """)) }
        }

        @Test
        fun `can be Win, Unix or mixed, even mixed`() {
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

            val module = parseModule(SourceCode.Text(srcText))
            assertEquals(2, module.statements.size)
        }
    }

    @Nested
    inner class EOLsInterleavedWithComments {

        @Test
        fun `are ok before first block - #47`() {
            // issue: #47
            val srcText = """
                ; comment
                
                ; comment
                
                blockA {            
                }
            """
            val module = parseModule(SourceCode.Text(srcText))
            assertEquals(1, module.statements.size)
        }

        @Test
        fun `are ok between blocks - #47`() {
            // issue: #47
            val srcText = """
                blockA {
                }
                ; comment
                
                ; comment
                
                blockB {            
                }
            """
            val module = parseModule(SourceCode.Text(srcText))
            assertEquals(2, module.statements.size)
        }

        @Test
        fun `are ok after last block - #47`() {
            // issue: #47
            val srcText = """
                blockA {            
                }
                ; comment
                
                ; comment
                
            """
            val module = parseModule(SourceCode.Text(srcText))
            assertEquals(1, module.statements.size)
        }
    }


    @Nested
    inner class ImportDirectives {
        @Test
        fun `should not be looked into by the parser`() {
            val importedNoExt = assumeNotExists(fixturesDir, "i_do_not_exist")
            assumeNotExists(fixturesDir, "i_do_not_exist.p8")
            val text = "%import ${importedNoExt.name}"
            val module = parseModule(SourceCode.Text(text))

            assertEquals(1, module.statements.size)
        }
    }


    @Nested
    inner class EmptySourcecode {
        @Test
        fun `from an empty string should result in empty Module`() {
            val module = parseModule(SourceCode.Text(""))
            assertEquals(0, module.statements.size)
        }

        @Test
        fun `from an empty file should result in empty Module`() {
            val path = assumeReadableFile(fixturesDir, "empty.p8")
            val module = parseModule(SourceCode.File(path))
            assertEquals(0, module.statements.size)
        }
    }

    @Nested
    inner class NameOfModule {
        @Test
        fun `parsed from a string`() {
            val srcText = """
                main {
                }
            """.trimIndent()
            val module = parseModule(SourceCode.Text(srcText))

            // Note: assertContains has *actual* as first param
            assertContains(module.name, Regex("^anonymous_[0-9a-f]+$"))
        }

        @Test
        fun `parsed from a file`() {
            val path = assumeReadableFile(fixturesDir, "simple_main.p8")
            val module = parseModule(SourceCode.File(path))
            assertEquals(path.nameWithoutExtension, module.name)
        }
    }

    @Nested
    inner class PositionOfAstNodesAndParseErrors {

        private fun assertPosition(
            actual: Position,
            expFile: String? = null,
            expLine: Int? = null,
            expStartCol: Int? = null,
            expEndCol: Int? = null
        ) {
            require(!listOf(expLine, expStartCol, expEndCol).all { it == null })
            if (expLine != null) assertEquals(expLine, actual.line, ".position.line (1-based)")
            if (expStartCol != null) assertEquals(expStartCol, actual.startCol, ".position.startCol (0-based)")
            if (expEndCol != null) assertEquals(expEndCol, actual.endCol, ".position.endCol (0-based)")
            if (expFile != null) assertEquals(expFile, actual.file, ".position.file")
        }

        private fun assertPosition(
            actual: Position,
            expFile: Regex? = null,
            expLine: Int? = null,
            expStartCol: Int? = null,
            expEndCol: Int? = null
        ) {
            require(!listOf(expLine, expStartCol, expEndCol).all { it == null })
            if (expLine != null) assertEquals(expLine, actual.line, ".position.line (1-based)")
            if (expStartCol != null) assertEquals(expStartCol, actual.startCol, ".position.startCol (0-based)")
            if (expEndCol != null) assertEquals(expEndCol, actual.endCol, ".position.endCol (0-based)")
            // Note: assertContains expects *actual* value first
            if (expFile != null) assertContains(actual.file, expFile, ".position.file")
        }

        private fun assertPositionOf(
            actual: Node,
            expFile: String? = null,
            expLine: Int? = null,
            expStartCol: Int? = null,
            expEndCol: Int? = null
        ) =
            assertPosition(actual.position, expFile, expLine, expStartCol, expEndCol)

        private fun assertPositionOf(
            actual: Node,
            expFile: Regex? = null,
            expLine: Int? = null,
            expStartCol: Int? = null,
            expEndCol: Int? = null
        ) =
            assertPosition(actual.position, expFile, expLine, expStartCol, expEndCol)


        @Test
        fun `in ParseError from bad string source code`() {
            val srcText = "bad * { }\n"

            assertFailsWith<ParseError> { parseModule(SourceCode.Text(srcText)) }
            try {
                parseModule(SourceCode.Text(srcText))
            } catch (e: ParseError) {
                assertPosition(e.position, Regex("^<String@[0-9a-f]+>$"), 1, 4, 4)
            }
        }

        @Test
        fun `in ParseError from bad file source code`() {
            val path = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

            assertFailsWith<ParseError> { parseModule(SourceCode.File(path)) }
            try {
                parseModule(SourceCode.File(path))
            } catch (e: ParseError) {
                assertPosition(e.position, SourceCode.relative(path).toString(), 2, 6) // TODO: endCol wrong
            }
        }

        @Test
        fun  `of Module parsed from a string`() {
            val srcText = """
                main {
                }
            """.trimIndent()
            val module = parseModule(SourceCode.Text(srcText))
            assertPositionOf(module, Regex("^String@[0-9a-f]+$"), 1, 0) // TODO: endCol wrong
        }

        @Test
        fun  `of Module parsed from a file`() {
            val path = assumeReadableFile(fixturesDir, "simple_main.p8")
            val module = parseModule(SourceCode.File(path))
            assertPositionOf(module, SourceCode.relative(path).toString(), 1, 0) // TODO: endCol wrong
        }

        @Test
        fun `of non-root Nodes parsed from file`() {
            val path = assumeReadableFile(fixturesDir, "simple_main.p8")

            val module = parseModule(SourceCode.File(path))
            val mpf = module.position.file
            assertPositionOf(module, SourceCode.relative(path).toString(), 1, 0) // TODO: endCol wrong
            val mainBlock = module.statements.filterIsInstance<Block>()[0]
            assertPositionOf(mainBlock, mpf, 1, 0)  // TODO: endCol wrong!
            val startSub = mainBlock.statements.filterIsInstance<Subroutine>()[0]
            assertPositionOf(startSub, mpf, 2, 4)  // TODO: endCol wrong!
        }


        /**
         * TODO: this test is testing way too much at once
         */
        @Test
        fun `of non-root Nodes parsed from a string`() {
            val srcText = """
                %zeropage basicsafe ; DirectiveArg directly inherits from Node - neither an Expression nor a Statement..?
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
            val module = parseModule(SourceCode.Text(srcText))
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

    @Nested
    inner class CharLiterals {

        @Test
        fun `in argument position, no altEnc`() {
            val src = SourceCode.Text("""
                 main {
                    sub start() {
                        chrout('\n')
                    }
                }
            """)
            val module = parseModule(src)

            val startSub = module
                .statements.filterIsInstance<Block>()[0]
                .statements.filterIsInstance<Subroutine>()[0]
            val funCall = startSub.statements.filterIsInstance<IFunctionCall>().first()

            assertIs<CharLiteral>(funCall.args[0])
            val char = funCall.args[0] as CharLiteral
            assertEquals('\n', char.value)
        }

        @Test
        fun `on rhs of block-level var decl, no AltEnc`() {
            val src = SourceCode.Text("""
                main {
                    ubyte c = 'x'
                }
            """)
            val module = parseModule(src)
            val decl = module
                .statements.filterIsInstance<Block>()[0]
                .statements.filterIsInstance<VarDecl>()[0]

            val rhs = decl.value as CharLiteral
            assertEquals('x', rhs.value, "char literal's .value")
            assertEquals(false, rhs.altEncoding, "char literal's .altEncoding")
        }

        @Test
        fun `on rhs of block-level const decl, with AltEnc`() {
            val src = SourceCode.Text("""
                main {
                    const ubyte c = @'x'
                }
            """)
            val module = parseModule(src)
            val decl = module
                .statements.filterIsInstance<Block>()[0]
                .statements.filterIsInstance<VarDecl>()[0]

            val rhs = decl.value as CharLiteral
            assertEquals('x', rhs.value, "char literal's .value")
            assertEquals(true, rhs.altEncoding, "char literal's .altEncoding")
        }

        @Test
        fun `on rhs of subroutine-level var decl, no AltEnc`() {
            val src = SourceCode.Text("""
                main {
                    sub start() {
                        ubyte c = 'x'
                    }
                }
            """)
            val module = parseModule(src)
            val decl = module
                .statements.filterIsInstance<Block>()[0]
                .statements.filterIsInstance<Subroutine>()[0]
                .statements.filterIsInstance<VarDecl>()[0]

            val rhs = decl.value as CharLiteral
            assertEquals('x', rhs.value, "char literal's .value")
            assertEquals(false, rhs.altEncoding, "char literal's .altEncoding")
        }

        @Test
        fun `on rhs of subroutine-level const decl, with AltEnc`() {
            val src = SourceCode.Text("""
                main {
                    sub start() {
                        const ubyte c = @'x'
                    }
                }
            """)
            val module = parseModule(src)
            val decl = module
                .statements.filterIsInstance<Block>()[0]
                .statements.filterIsInstance<Subroutine>()[0]
                .statements.filterIsInstance<VarDecl>()[0]

            val rhs = decl.value as CharLiteral
            assertEquals('x', rhs.value, "char literal's .value")
            assertEquals(true, rhs.altEncoding, "char literal's .altEncoding")
        }
    }

    @Nested
    inner class Ranges {

        @Test
        fun `in for-loops`() {
            val module = parseModule(SourceCode.Text("""
                main {
                    sub start() {
                        ubyte ub
                        for ub in "start" downto "end" {    ; #0
                        }
                        for ub in "something" {             ; #1
                        }
                        for ub in @'a' to 'f' {             ; #2
                        }
                        for ub in false to true {           ; #3
                        }
                        for ub in 9 to 1 {                  ; #4 - yes, *parser* should NOT check!
                        }
                    }
                }
            """))
            val iterables = module
                .statements.filterIsInstance<Block>()[0]
                .statements.filterIsInstance<Subroutine>()[0]
                .statements.filterIsInstance<ForLoop>()
                .map { it.iterable }

            assertEquals(5, iterables.size)

            val it0 = iterables[0] as RangeExpr
            assertIs<StringLiteralValue>(it0.from, "parser should leave it as is")
            assertIs<StringLiteralValue>(it0.to, "parser should leave it as is")

            val it1 = iterables[1] as StringLiteralValue
            assertEquals("something", it1.value, "parser should leave it as is")

            val it2 = iterables[2] as RangeExpr
            assertIs<CharLiteral>(it2.from, "parser should leave it as is")
            assertIs<CharLiteral>(it2.to, "parser should leave it as is")

            val it3 = iterables[3] as RangeExpr
            // TODO: intro BoolLiteral
            assertIs<NumericLiteralValue>(it3.from, "parser should leave it as is")
            assertIs<NumericLiteralValue>(it3.to, "parser should leave it as is")

            val it4 = iterables[4] as RangeExpr
            assertIs<NumericLiteralValue>(it4.from, "parser should leave it as is")
            assertIs<NumericLiteralValue>(it4.to, "parser should leave it as is")
        }
    }

}
