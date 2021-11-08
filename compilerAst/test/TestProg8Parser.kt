package prog8tests.ast

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.instanceOf
import prog8.ast.IFunctionCall
import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.parser.ParseError
import prog8.parser.Prog8Parser.parseModule
import prog8.parser.SourceCode
import prog8tests.ast.helpers.*
import prog8tests.ast.helpers.DummyFunctions
import prog8tests.ast.helpers.DummyMemsizer
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension


class TestProg8Parser: FunSpec( {

    context("Newline at end") {
        test("is not required - #40, fixed by #45") {
            val nl = "\n" // say, Unix-style (different flavours tested elsewhere)
            val src = SourceCode.Text("foo {" + nl + "}")   // source ends with '}' (= NO newline, issue #40)

            // #40: Prog8ANTLRParser would report (throw) "missing <EOL> at '<EOF>'"
            val module = parseModule(src)
            module.statements.size shouldBe 1
        }

        test("is still accepted - #40, fixed by #45") {
            val nl = "\n" // say, Unix-style (different flavours tested elsewhere)
            val srcText = "foo {" + nl + "}" + nl  // source does end with a newline (issue #40)
            val module = parseModule(SourceCode.Text(srcText))
            module.statements.size shouldBe 1
        }
    }

    context("Newline") {
        test("is required after each block except the last") {
            val nl = "\n" // say, Unix-style (different flavours tested elsewhere)

            // BAD: 2nd block `bar` does NOT start on new line; however, there's is a nl at the very end
            val srcBad = "foo {" + nl + "}" + " bar {" + nl + "}" + nl

            // GOOD: 2nd block `bar` does start on a new line; however, a nl at the very end ain't needed
            val srcGood = "foo {" + nl + "}" + nl + "bar {" + nl + "}"

            shouldThrow<ParseError> { parseModule(SourceCode.Text(srcBad)) }
            val module = parseModule(SourceCode.Text(srcGood))
            module.statements.size shouldBe 2
        }

        test("is required between two Blocks or Directives - #47") {
            // block and block
            shouldThrow<ParseError>{ parseModule(SourceCode.Text("""
                blockA {
                } blockB {            
                }            
            """)) }

            // block and directive
            shouldThrow<ParseError>{ parseModule(SourceCode.Text("""
                blockB {            
                } %import textio            
            """)) }

            // The following two are bogus due to directive *args* expected to follow the directive name.
            // Leaving them in anyways.

            // dir and block
            shouldThrow<ParseError>{ parseModule(SourceCode.Text("""
                %import textio blockB {            
                }            
            """)) }

            shouldThrow<ParseError>{ parseModule(SourceCode.Text("""
                %import textio %import syslib            
            """)) }
        }

        test("can be Win, Unix or mixed, even mixed") {
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
            module.statements.size shouldBe 2
        }
    }

    context("EOLsInterleavedWithComments") {

        test("are ok before first block - #47") {
            // issue: #47
            val srcText = """
                ; comment
                
                ; comment
                
                blockA {            
                }
            """
            val module = parseModule(SourceCode.Text(srcText))
            module.statements.size shouldBe 1
        }

        test("are ok between blocks - #47") {
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
            module.statements.size shouldBe 2
        }

        test("are ok after last block - #47") {
            // issue: #47
            val srcText = """
                blockA {            
                }
                ; comment
                
                ; comment
                
            """
            val module = parseModule(SourceCode.Text(srcText))
            module.statements.size shouldBe 1
        }
    }

    context("ImportDirectives") {
        test("should not be looked into by the parser") {
            val importedNoExt = assumeNotExists(fixturesDir, "i_do_not_exist")
            assumeNotExists(fixturesDir, "i_do_not_exist.p8")
            val text = "%import ${importedNoExt.name}"
            val module = parseModule(SourceCode.Text(text))

            module.statements.size shouldBe 1
        }
    }

    context("EmptySourcecode") {
        test("from an empty string should result in empty Module") {
            val module = parseModule(SourceCode.Text(""))
            module.statements.size shouldBe 0
        }

        test("from an empty file should result in empty Module") {
            val path = assumeReadableFile(fixturesDir, "empty.p8")
            val module = parseModule(SourceCode.File(path))
            module.statements.size shouldBe 0
        }
    }

    context("NameOfModule") {
        test("parsed from a string") {
            val srcText = """
                main {
                }
            """.trimIndent()
            val module = parseModule(SourceCode.Text(srcText))

            // Note: assertContains has *actual* as first param
            module.name shouldContain Regex("^<String@[0-9a-f\\-]+>$")
        }

        test("parsed from a file") {
            val path = assumeReadableFile(fixturesDir, "simple_main.p8")
            val module = parseModule(SourceCode.File(path))
            module.name shouldBe path.nameWithoutExtension
        }
    }

    context("PositionOfAstNodesAndParseErrors") {

        fun assertPosition(
            actual: Position,
            expFile: String? = null,
            expLine: Int? = null,
            expStartCol: Int? = null,
            expEndCol: Int? = null
        ) {
            require(!listOf(expLine, expStartCol, expEndCol).all { it == null })
            if (expLine != null) actual.line shouldBe expLine
            if (expStartCol != null) actual.startCol shouldBe expStartCol
            if (expEndCol != null) actual.endCol shouldBe expEndCol
            if (expFile != null) actual.file shouldBe expFile
        }

        fun assertPosition(
            actual: Position,
            expFile: Regex? = null,
            expLine: Int? = null,
            expStartCol: Int? = null,
            expEndCol: Int? = null
        ) {
            require(!listOf(expLine, expStartCol, expEndCol).all { it == null })
            if (expLine != null) actual.line shouldBe expLine
            if (expStartCol != null) actual.startCol shouldBe expStartCol
            if (expEndCol != null) actual.endCol shouldBe expEndCol
            if (expFile != null) actual.file shouldContain expFile
        }

        fun assertPositionOf(
            actual: Node,
            expFile: String? = null,
            expLine: Int? = null,
            expStartCol: Int? = null,
            expEndCol: Int? = null
        ) =
            assertPosition(actual.position, expFile, expLine, expStartCol, expEndCol)

        fun assertPositionOf(
            actual: Node,
            expFile: Regex? = null,
            expLine: Int? = null,
            expStartCol: Int? = null,
            expEndCol: Int? = null
        ) =
            assertPosition(actual.position, expFile, expLine, expStartCol, expEndCol)


        test("in ParseError from bad string source code") {
            val srcText = "bad * { }\n"

            val e = shouldThrow<ParseError> { parseModule(SourceCode.Text(srcText)) }
            assertPosition(e.position, Regex("^<String@[0-9a-f\\-]+>$"), 1, 4, 4)
        }

        test("in ParseError from bad file source code") {
            val path = assumeReadableFile(fixturesDir, "file_with_syntax_error.p8")

            val e = shouldThrow<ParseError> { parseModule(SourceCode.File(path)) }
            assertPosition(e.position, SourceCode.relative(path).toString(), 2, 6)
        }

        test("of Module parsed from a string") {
            val srcText = """
                main {
                }
            """
            val module = parseModule(SourceCode.Text(srcText))
            assertPositionOf(module, Regex("^<String@[0-9a-f\\-]+>$"), 1, 0)
        }

        test("of Module parsed from a file") {
            val path = assumeReadableFile(fixturesDir, "simple_main.p8")
            val module = parseModule(SourceCode.File(path))
            assertPositionOf(module, SourceCode.relative(path).toString(), 1, 0)
        }

        test("of non-root Nodes parsed from file") {
            val path = assumeReadableFile(fixturesDir, "simple_main.p8")

            val module = parseModule(SourceCode.File(path))
            val mpf = module.position.file
            assertPositionOf(module, SourceCode.relative(path).toString(), 1, 0)
            val mainBlock = module.statements.filterIsInstance<Block>()[0]
            assertPositionOf(mainBlock, mpf, 2, 0, 3)
            val startSub = mainBlock.statements.filterIsInstance<Subroutine>()[0]
            assertPositionOf(startSub, mpf, 3, 4, 6)
        }

        test("of non-root Nodes parsed from a string") {
            val srcText = """
                %zeropage basicsafe
                main {
                    sub start() {
                        ubyte foo = 42
                        ubyte bar
                        when (foo) {
                            23 -> bar = 'x'
                            42 -> bar = 'y'
                            else -> bar = 'z'
                        }
                    }
                }
            """.trimIndent()
            val module = parseModule(SourceCode.Text(srcText))
            val mpf = module.position.file

            val targetDirective = module.statements.filterIsInstance<Directive>()[0]
            assertPositionOf(targetDirective, mpf, 1, 0, 8)
            val mainBlock = module.statements.filterIsInstance<Block>()[0]
            assertPositionOf(mainBlock, mpf, 2, 0, 3)
            val startSub = mainBlock.statements.filterIsInstance<Subroutine>()[0]
            assertPositionOf(startSub, mpf, 3, 4, 6)
            val declFoo = startSub.statements.filterIsInstance<VarDecl>()[0]
            assertPositionOf(declFoo, mpf, 4, 8, 12)
            val rhsFoo = declFoo.value!!
            assertPositionOf(rhsFoo, mpf, 4, 20, 21)
            val declBar = startSub.statements.filterIsInstance<VarDecl>()[1]
            assertPositionOf(declBar, mpf, 5, 8, 12)
            val whenStmt = startSub.statements.filterIsInstance<WhenStatement>()[0]
            assertPositionOf(whenStmt, mpf, 6, 8, 11)
            assertPositionOf(whenStmt.choices[0], mpf, 7, 12, 13)
            assertPositionOf(whenStmt.choices[1], mpf, 8, 12, 13)
            assertPositionOf(whenStmt.choices[2], mpf, 9, 12, 15)
        }
    }

    context("PositionFile") {
        fun assertSomethingForAllNodes(module: Module, asserter: (Node) -> Unit) {
            asserter(module)
            module.statements.forEach(asserter)
            module.statements.filterIsInstance<Block>().forEach { b ->
                asserter(b)
                b.statements.forEach(asserter)
                b.statements.filterIsInstance<Subroutine>().forEach { s ->
                    asserter(s)
                    s.statements.forEach(asserter)
                }
            }
        }

        test("isn't absolute for filesystem paths") {
            val path = assumeReadableFile(fixturesDir, "simple_main.p8")
            val module = parseModule(SourceCode.File(path))
            assertSomethingForAllNodes(module) {
                Path(it.position.file).isAbsolute shouldBe false
                Path(it.position.file).isRegularFile() shouldBe true
            }
        }

        test("is mangled string id for string sources")
        {
            val srcText="""
                %zeropage basicsafe
                main {
                    sub start() {
                        ubyte aa=99
                        aa++
                    }
                }
            """.trimIndent()
            val module = parseModule(SourceCode.Text(srcText))
            assertSomethingForAllNodes(module) {
                it.position.file shouldStartWith SourceCode.stringSourcePrefix
            }
        }

        test("is library prefixed path for resources")
        {
            val resource = SourceCode.Resource("prog8lib/math.p8")
            val module = parseModule(resource)
            assertSomethingForAllNodes(module) {
                it.position.file shouldStartWith SourceCode.libraryFilePrefix
            }
        }
    }

    context("CharLiterals") {

        test("in argument position, no altEnc") {
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

            funCall.args[0] shouldBe(instanceOf<CharLiteral>())
            val char = funCall.args[0] as CharLiteral
            char.value shouldBe '\n'
        }

        test("on rhs of block-level var decl, no AltEnc") {
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
            rhs.value shouldBe 'x'
            rhs.altEncoding shouldBe false
        }

        test("on rhs of block-level const decl, with AltEnc") {
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
            rhs.value shouldBe 'x'
            rhs.altEncoding shouldBe true
        }

        test("on rhs of subroutine-level var decl, no AltEnc") {
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
            rhs.value shouldBe 'x'
            rhs.altEncoding shouldBe false
        }

        test("on rhs of subroutine-level const decl, with AltEnc") {
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
            rhs.value shouldBe 'x'
            rhs.altEncoding shouldBe true
        }
    }

    context("Ranges") {

        test("in for-loops") {
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

            iterables.size shouldBe 5

            val it0 = iterables[0] as RangeExpr
            it0.from shouldBe instanceOf<StringLiteralValue>()
            it0.to shouldBe instanceOf<StringLiteralValue>()

            val it1 = iterables[1] as StringLiteralValue
            it1.value shouldBe "something"

            val it2 = iterables[2] as RangeExpr
            it2.from shouldBe instanceOf<CharLiteral>()
            it2.to shouldBe instanceOf<CharLiteral>()

            val it3 = iterables[3] as RangeExpr
            it3.from shouldBe instanceOf<NumericLiteralValue>()
            it3.to shouldBe instanceOf<NumericLiteralValue>()

            val it4 = iterables[4] as RangeExpr
            it4.from shouldBe instanceOf<NumericLiteralValue>()
            it4.to shouldBe instanceOf<NumericLiteralValue>()
        }
    }

    test("testCharLiteralConstValue") {
        val char1 = CharLiteral('A', false, Position.DUMMY)
        val char2 = CharLiteral('z', true, Position.DUMMY)

        val program = Program("test", DummyFunctions, DummyMemsizer, AsciiStringEncoder)
        char1.constValue(program).number.toInt() shouldBe 65
        char2.constValue(program).number.toInt() shouldBe 122
    }

    test("testLiteralValueComparisons") {
        val ten = NumericLiteralValue(DataType.UWORD, 10, Position.DUMMY)
        val nine = NumericLiteralValue(DataType.UBYTE, 9, Position.DUMMY)
        ten shouldBe ten
        nine shouldNotBe ten
        (ten != ten) shouldBe false
        (ten != nine) shouldBe true

        (ten > nine) shouldBe true
        (ten >= nine) shouldBe true
        (ten >= ten) shouldBe true
        (ten > ten) shouldBe false

        (ten < nine) shouldBe false
        (ten <= nine) shouldBe false
        (ten <= ten) shouldBe true
        (ten < ten) shouldBe false

        val abc = StringLiteralValue("abc", false, Position.DUMMY)
        val abd = StringLiteralValue("abd", false, Position.DUMMY)
        abc shouldBe abc
        (abc!=abd) shouldBe true
        (abc!=abc) shouldBe false
    }

    test("testAnonScopeStillContainsVarsDirectlyAfterParse") {
        val src = SourceCode.Text("""
            main {
                sub start() {
                    repeat {
                        ubyte xx = 99
                        xx++
                    }
                }
            }
        """)
        val module = parseModule(src)
        val mainBlock = module.statements.single() as Block
        val start = mainBlock.statements.single() as Subroutine
        val repeatbody = (start.statements.single() as RepeatLoop).body
        withClue("no vars moved to main block") {
            mainBlock.statements.any { it is VarDecl } shouldBe false
        }
        withClue("no vars moved to start sub") {
            start.statements.any { it is VarDecl } shouldBe false
        }
        withClue("\"var is still in repeat block (anonymousscope") {
            repeatbody.statements[0] shouldBe instanceOf<VarDecl>()
        }
        val initvalue = (repeatbody.statements[0] as VarDecl).value as? NumericLiteralValue
        initvalue?.number?.toInt() shouldBe 99
        repeatbody.statements[1] shouldBe instanceOf<PostIncrDecr>()
        // the ast processing steps used in the compiler, will eventually move the var up to the containing scope (subroutine).
    }

    test("testLabelsWithAnonScopesParsesFine") {
        val src = SourceCode.Text("""
            main {
                sub start() {
                    goto mylabeloutside
        
                    if true {
                        if true {
                            goto labeloutside
                            goto iflabel
                        }
            iflabel:
                    }
        
                    repeat {
                        goto labelinside
            labelinside:
                    }
        
            labeloutside:
                }
            }
        """)
        val module = parseModule(src)
        val mainBlock = module.statements.single() as Block
        val start = mainBlock.statements.single() as Subroutine
        val labels = start.statements.filterIsInstance<Label>()
        labels.size shouldBe 1
    }

    test("logical operator 'not' priority") {
        val src = SourceCode.Text("""
            main {
                sub start() {
                    ubyte xx
                    xx = not 4 and not 5
                    xx = not 4 or not 5
                    xx = not 4 xor not 5
                }
            }
        """)
        val module = parseModule(src)
        val start = (module.statements.single() as Block).statements.single() as Subroutine
        val andAssignmentExpr = (start.statements[1] as Assignment).value
        val orAssignmentExpr = (start.statements[2] as Assignment).value
        val xorAssignmentExpr = (start.statements[3] as Assignment).value

        fun correctPrios(expr: Expression, operator: String) {
            withClue("not should have higher prio as the other logical operators") {
                expr shouldBe instanceOf<BinaryExpression>()
                val binExpr = expr as BinaryExpression
                binExpr.operator shouldBe operator
                (binExpr.left as PrefixExpression).operator shouldBe "not"
                (binExpr.right as PrefixExpression).operator shouldBe "not"
            }
        }

        correctPrios(andAssignmentExpr, "and")
        correctPrios(orAssignmentExpr, "or")
        correctPrios(xorAssignmentExpr, "xor")
    }
})
