package prog8tests.ast

import com.github.michaelbull.result.getOrElse
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.or
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.ast.IFunctionCall
import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.code.core.*
import prog8.code.source.ImportFileSystem
import prog8.code.source.SourceCode
import prog8.code.target.C64Target
import prog8.code.target.VMTarget
import prog8.code.target.encodings.PetsciiEncoding
import prog8.parser.ParseError
import prog8.parser.Prog8Parser.parseModule
import prog8tests.helpers.*
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension


class TestProg8Parser: FunSpec( {

    context("Newline at end") {
        test("is not required - #40, fixed by #45") {
            val nl = "\n" // say, Unix-style (different flavours tested elsewhere)
            val src = SourceCode.Text("foo {$nl}")   // source ends with '}' (= NO newline, issue #40)

            // #40: Prog8ANTLRParser would report (throw) "missing <EOL> at '<EOF>'"
            val module = parseModule(src)
            module.statements.size shouldBe 1
        }

        test("is still accepted - #40, fixed by #45") {
            val nl = "\n" // say, Unix-style (different flavours tested elsewhere)
            val srcText = "foo {$nl}$nl"  // source does end with a newline (issue #40)
            val module = parseModule(SourceCode.Text(srcText))
            module.statements.size shouldBe 1
        }
    }

    context("Newline") {
        test("is required after each block except the last") {
            val nl = "\n" // say, Unix-style (different flavours tested elsewhere)

            // BAD: 2nd block `bar` does NOT start on new line; however, there's is a nl at the very end
            val srcBad = "foo {$nl} bar {$nl}$nl"

            // GOOD: 2nd block `bar` does start on a new line; however, a nl at the very end ain't needed
            val srcGood = "foo {$nl}${nl}bar {$nl}"

            shouldThrow<ParseError> { parseModule(SourceCode.Text(srcBad)) }
            val module = parseModule(SourceCode.Text(srcGood))
            module.statements.size shouldBe 2
        }

        test("is required between two Blocks or Directives - #47") {
            // block and block
            shouldThrow<ParseError>{ parseModule(
                SourceCode.Text("""
                blockA {
                } blockB {            
                }            
            """)) }

            // block and directive
            shouldThrow<ParseError>{ parseModule(
                SourceCode.Text("""
                blockB {            
                } %import textio            
            """)) }

            // The following two are bogus due to directive *args* expected to follow the directive name.
            // Leaving them in anyways.

            // dir and block
            shouldThrow<ParseError>{ parseModule(
                SourceCode.Text("""
                %import textio blockB {            
                }            
            """)) }

            shouldThrow<ParseError>{ parseModule(
                SourceCode.Text("""
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
            val path = assumeReadableFile(fixturesDir, "ast_empty.p8")
            val module = parseModule(ImportFileSystem.getFile(path))
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
            module.name shouldContain Regex("^string:[0-9a-f\\-]+$")
        }

        test("parsed from a file") {
            val path = assumeReadableFile(fixturesDir, "ast_simple_main.p8")
            val module = parseModule(ImportFileSystem.getFile(path))
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
            assertPosition(e.position, Regex("^string:[0-9a-f\\-]+$"), 1, 4, 4)
        }

        test("in ParseError from bad file source code") {
            val path = assumeReadableFile(fixturesDir, "ast_file_with_syntax_error.p8")

            val e = shouldThrow<ParseError> { parseModule(ImportFileSystem.getFile(path)) }
            assertPosition(e.position, SourceCode.relative(path).toString(), 2, 4)
        }

        test("of Module parsed from a string") {
            val srcText = """
                main {
                }
            """
            val module = parseModule(SourceCode.Text(srcText))
            assertPositionOf(module, Regex("^string:[0-9a-f\\-]+$"), 1, 0)
        }

        test("of Module parsed from a file") {
            val path = assumeReadableFile(fixturesDir, "ast_simple_main.p8")
            val module = parseModule(ImportFileSystem.getFile(path))
            assertPositionOf(module, SourceCode.relative(path).toString(), 1, 0)
        }

        test("of non-root Nodes parsed from file") {
            val path = assumeReadableFile(fixturesDir, "ast_simple_main.p8")

            val module = parseModule(ImportFileSystem.getFile(path))
            val mpf = module.position.file
            assertPositionOf(module, SourceCode.relative(path).toString(), 1, 0)
            val mainBlock = module.statements.filterIsInstance<Block>()[0]
            assertPositionOf(mainBlock, mpf, 2, 1, 4)
            val startSub = mainBlock.statements.filterIsInstance<Subroutine>()[0]
            assertPositionOf(startSub, mpf, 3, 5, 7)
        }

        test("of non-root Nodes parsed from a string") {
            val srcText = """
                %zeropage basicsafe
                main {
                    sub start() {
                        ubyte foo = 42
                        ubyte bar
                        when foo {
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
            assertPositionOf(targetDirective, mpf, 1, 1, 9)
            val mainBlock = module.statements.filterIsInstance<Block>()[0]
            assertPositionOf(mainBlock, mpf, 2, 1, 4)
            val startSub = mainBlock.statements.filterIsInstance<Subroutine>()[0]
            assertPositionOf(startSub, mpf, 3, 5, 7)
            val declFoo = startSub.statements.filterIsInstance<VarDecl>()[0]
            assertPositionOf(declFoo, mpf, 4, 9, 13)
            val rhsFoo = declFoo.value!!
            assertPositionOf(rhsFoo, mpf, 4, 21, 22)
            val declBar = startSub.statements.filterIsInstance<VarDecl>()[1]
            assertPositionOf(declBar, mpf, 5, 9, 13)
            val whenStmt = startSub.statements.filterIsInstance<When>()[0]
            assertPositionOf(whenStmt, mpf, 6, 9, 12)
            assertPositionOf(whenStmt.choices[0], mpf, 7, 13, 14)
            assertPositionOf(whenStmt.choices[1], mpf, 8, 13, 14)
            assertPositionOf(whenStmt.choices[2], mpf, 9, 13, 16)
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
            val path = assumeReadableFile(fixturesDir, "ast_simple_main.p8")
            val module = parseModule(ImportFileSystem.getFile(path))
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
                SourceCode.isStringResource(it.position.file) shouldBe true
            }
        }

        test("is library prefixed path for resources")
        {
            val resource = ImportFileSystem.getResource("prog8lib/math.p8")
            val module = parseModule(resource)
            assertSomethingForAllNodes(module) {
                SourceCode.isLibraryResource(it.position.file) shouldBe true
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

        test("on rhs of block-level var decl, default encoding") {
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
            rhs.encoding shouldBe Encoding.DEFAULT
            rhs.value shouldBe 'x'
        }

        test("on rhs of block-level const decl, with screencode enc") {
            val src = SourceCode.Text("""
                main {
                    const ubyte c = sc:'x'
                }
            """)
            val module = parseModule(src)
            val decl = module
                .statements.filterIsInstance<Block>()[0]
                .statements.filterIsInstance<VarDecl>()[0]

            val rhs = decl.value as CharLiteral
            rhs.encoding shouldBe Encoding.SCREENCODES
            rhs.value shouldBe 'x'
        }

        test("on rhs of block-level const decl, with iso encoding") {
            val src = SourceCode.Text("""
                main {
                    const ubyte c = iso:'_'
                }
            """)
            val module = parseModule(src)
            val decl = module
                .statements.filterIsInstance<Block>()[0]
                .statements.filterIsInstance<VarDecl>()[0]

            val rhs = decl.value as CharLiteral
            rhs.encoding shouldBe Encoding.ISO
            rhs.value shouldBe '_'
        }


        test("on rhs of subroutine-level var decl, default encoding") {
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
            rhs.encoding shouldBe Encoding.DEFAULT
        }

        test("on rhs of subroutine-level const decl, screencode encoded") {
            val src = SourceCode.Text("""
                main {
                    sub start() {
                        const ubyte c = sc:'x'
                    }
                }
            """)
            val module = parseModule(src)
            val decl = module
                .statements.filterIsInstance<Block>()[0]
                .statements.filterIsInstance<Subroutine>()[0]
                .statements.filterIsInstance<VarDecl>()[0]

            val rhs = decl.value as CharLiteral
            rhs.encoding shouldBe Encoding.SCREENCODES
            rhs.value shouldBe 'x'
        }

        test("on rhs of subroutine-level const decl, iso encoding") {
            val src = SourceCode.Text("""
                main {
                    sub start() {
                        const ubyte c = iso:'_'
                    }
                }
            """)
            val module = parseModule(src)
            val decl = module
                .statements.filterIsInstance<Block>()[0]
                .statements.filterIsInstance<Subroutine>()[0]
                .statements.filterIsInstance<VarDecl>()[0]

            val rhs = decl.value as CharLiteral
            rhs.encoding shouldBe Encoding.ISO
            rhs.value shouldBe '_'
        }
    }

    context("Strings") {

        test("default encoding") {
            val source = """
                main {
                    str name = "name"
                }"""
            val module = parseModule(SourceCode.Text(source))
            val decl = module
                .statements.filterIsInstance<Block>()[0]
                .statements.filterIsInstance<VarDecl>()[0]
            val rhs = decl.value as StringLiteral
            rhs.encoding shouldBe Encoding.DEFAULT
            rhs.value shouldBe "name"
        }

        test("string encodings") {
            val source = """
                main {
                    str name1 = petscii:"Name"
                    str name2 = sc:"Name"
                    str name3 = iso:"Name"
                }"""
            val module = parseModule(SourceCode.Text(source))
            val (decl1, decl2, decl3) = module
                .statements.filterIsInstance<Block>()[0]
                .statements.filterIsInstance<VarDecl>()
            val rhs1 = decl1.value as StringLiteral
            val rhs2 = decl2.value as StringLiteral
            val rhs3 = decl3.value as StringLiteral
            rhs1.encoding shouldBe Encoding.PETSCII
            rhs1.value shouldBe "Name"
            rhs2.encoding shouldBe Encoding.SCREENCODES
            rhs2.value shouldBe "Name"
            rhs3.encoding shouldBe Encoding.ISO
            rhs3.value shouldBe "Name"
        }
    }

    context("Ranges") {

        test("in for-loops") {
            val module = parseModule(
                SourceCode.Text("""
                main {
                    sub start() {
                        ubyte ub
                        for ub in "start" downto "end" {    ; #0
                        }
                        for ub in "something" {             ; #1
                        }
                        for ub in sc:'a' to 'f' {           ; #2
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

            val it0 = iterables[0] as RangeExpression
            it0.from shouldBe instanceOf<StringLiteral>()
            it0.to shouldBe instanceOf<StringLiteral>()

            val it1 = iterables[1] as StringLiteral
            it1.value shouldBe "something"

            val it2 = iterables[2] as RangeExpression
            it2.from shouldBe instanceOf<CharLiteral>()
            it2.to shouldBe instanceOf<CharLiteral>()

            val it3 = iterables[3] as RangeExpression
            it3.from shouldBe instanceOf<NumericLiteral>()
            it3.to shouldBe instanceOf<NumericLiteral>()

            val it4 = iterables[4] as RangeExpression
            it4.from shouldBe instanceOf<NumericLiteral>()
            it4.to shouldBe instanceOf<NumericLiteral>()
        }
    }

    test("testCharLiteralConstValue") {
        val char1 = CharLiteral.create('A', Encoding.PETSCII, Position.DUMMY)
        val char2 = CharLiteral.create('z', Encoding.SCREENCODES, Position.DUMMY)
        val char3 = CharLiteral.create('_', Encoding.ISO, Position.DUMMY)

        val program = Program("test", DummyFunctions, DummyMemsizer, AsciiStringEncoder)
        char1.constValue(program).number.toInt() shouldBe 65
        char2.constValue(program).number.toInt() shouldBe 122
        char3.constValue(program).number.toInt() shouldBe 95
    }

    test("testLiteralValueComparisons") {
        val ten = NumericLiteral(BaseDataType.UWORD, 10.0, Position.DUMMY)
        val nine = NumericLiteral(BaseDataType.UBYTE, 9.0, Position.DUMMY)
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

        val abc = StringLiteral.create("abc", Encoding.PETSCII, Position.DUMMY)
        val abd = StringLiteral.create("abd", Encoding.PETSCII, Position.DUMMY)
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
                        rol(xx)
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
        val initvalue = (repeatbody.statements[0] as VarDecl).value as? NumericLiteral
        initvalue?.number?.toInt() shouldBe 99
        repeatbody.statements[1] shouldBe instanceOf<FunctionCallStatement>()
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

    test("inferred type correct for binaryexpression") {
        val src = SourceCode.Text("""
            main {
                ubyte bb
                uword ww
                bool bb2 = (3+bb) or (3333+ww)       ; expression combining ubyte and uword
            }
        """)
        val module = parseModule(src)
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        program.addModule(module)
        val bb2 = (module.statements.single() as Block).statements[2] as VarDecl
        val expr = bb2.value as BinaryExpression
        println(expr)
        expr.operator shouldBe "or"
        expr.left.inferType(program).getOrElse { fail("dt") } shouldBe DataType.forDt(BaseDataType.UBYTE)
        expr.right.inferType(program).getOrElse { fail("dt") } shouldBe DataType.forDt(BaseDataType.UWORD)
        expr.inferType(program).getOrElse { fail("dt") } shouldBe DataType.forDt(BaseDataType.BOOL)
    }

    test("inferred type for typecasted expressions with logical operators") {
        val src= SourceCode.Text("""
            main {
                ubyte bb
                uword ww
                uword qq = (not bb as uword)
                uword zz = not bb or not ww
                ubyte bb2 = not bb or not ww
                uword zz2 = (not bb as uword) or not ww
            }
        """)
        val module = parseModule(src)
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        program.addModule(module)
        val stmts = (module.statements.single() as Block).statements
        stmts.size shouldBe 6
        val qq = (stmts[2] as VarDecl).value as TypecastExpression
        val zz = (stmts[3] as VarDecl).value as BinaryExpression
        val bb2 = (stmts[4] as VarDecl).value as BinaryExpression
        val zz2 = (stmts[5] as VarDecl).value as BinaryExpression
        qq.inferType(program).getOrElse { fail("dt") } shouldBe DataType.forDt(BaseDataType.UWORD)
        zz.inferType(program).getOrElse { fail("dt") } shouldBe DataType.forDt(BaseDataType.BOOL)
        bb2.inferType(program).getOrElse { fail("dt") } shouldBe DataType.forDt(BaseDataType.BOOL)

        zz2.operator shouldBe "or"
        val left = zz2.left as TypecastExpression
        val right = zz2.right as PrefixExpression
        left.inferType(program).getOrElse { fail("dt") } shouldBe DataType.forDt(BaseDataType.UWORD)
        right.inferType(program).getOrElse { fail("dt") } shouldBe DataType.forDt(BaseDataType.BOOL)
        zz2.inferType(program).getOrElse { fail("dt") } shouldBe DataType.forDt(BaseDataType.BOOL)
    }

    test("type cast from byte to ubyte as desired target type") {
        val src = SourceCode.Text("""
            main {
                ubyte r
                ubyte ub = (cos8(r)/2 + 100) as ubyte
            }""")
        val module = parseModule(src)
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        program.addModule(module)
        val stmts = (module.statements.single() as Block).statements
        stmts.size shouldBe 2
        val ubexpr = (stmts[1] as VarDecl).value as TypecastExpression
        ubexpr.inferType(program).getOrElse { fail("dt") } shouldBe DataType.forDt(BaseDataType.UBYTE)
    }

    test("assignment isAugmented correctness") {
        val src = SourceCode.Text("""
            main {
                sub start() {
                    ubyte r
                    ubyte q
                    r = q*3     ; #1 no
                    r = r*3     ; #2 yes
                    r = 3*r     ; #3 yes
                    r = 3*q     ; #4 no
                    r = 5+r     ; #5 yes
                    r = 5-r     ; #6 no
                    r = r-5     ; #7 yes
                    r = not r   ; #8 yes
                    r = not q   ; #9 no
                    r = (q+r)+5 ; #10 yes
                    r = q+(r+5) ; #11 yes
                    r = (q+r)-5 ; #12 yes
                    r = q+(r-5) ; #13 yes
                }
            }""")

        val module = parseModule(src)
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        program.addModule(module)
        val stmts = program.entrypoint.statements
        val expectedResults = listOf(
            false, true, true,
            false, true, false,
            true, true, false,
            true, true, true,
            true
        )
        stmts.size shouldBe 15
        expectedResults.size shouldBe stmts.size-2
        for((idx, pp) in stmts.drop(2).zip(expectedResults).withIndex()) {
            val assign = pp.first as Assignment
            val expected = pp.second
            withClue("#${idx+1}: should${if(expected) "" else "n't"} be augmentable: $assign") {
                assign.isAugmentable shouldBe expected
                assign.value shouldBe (instanceOf<PrefixExpression>() or instanceOf<BinaryExpression>() or instanceOf<TypecastExpression>())
            }
        }
    }

    test("test string x and u escape sequences") {
        val text="""
            main {
                sub start() {
                    str string = "\x00\xff\u0041"
                    ubyte zero = '\x00'
                    ubyte ff = '\xff'
                    ubyte letter = '\u0041'
                }
            }
        """
        val result = compileText(C64Target(), false, text, writeAssembly = false)!!
        val start = result.compilerAst.entrypoint
        val string = (start.statements[0] as VarDecl).value as StringLiteral
        withClue("x-escapes are hacked to range 0x8000-0x80ff") {
            string.value[0].code shouldBe 0x8000
            string.value[1].code shouldBe 0x80ff
        }
        string.value[2].code shouldBe 65
        val zero = start.statements[2] as Assignment
        zero.value shouldBe NumericLiteral(BaseDataType.UBYTE, 0.0, Position.DUMMY)
        val ff = start.statements[4] as Assignment
        ff.value shouldBe NumericLiteral(BaseDataType.UBYTE, 255.0, Position.DUMMY)
        val letter = start.statements[6] as Assignment
        val encodedletter = PetsciiEncoding.encodePetscii("A", true).getOrElse { fail("petscii error") }.single()
        letter.value shouldBe NumericLiteral(BaseDataType.UBYTE, encodedletter.toDouble(), Position.DUMMY)
    }

    test("`in` containment checks") {
        val text="""
            main {
                sub start() {
                    str string = "hello"
                    ubyte[  ] array = [1,2,3,4]
                    
                    bool bb
                    ubyte cc
                    if cc in [' ', '@', 0] {
                        cx16.r0L++
                    }
                    
                    if cc in "email" {
                        cx16.r0L++
                    }
                    
                    bb = 99 in array
                    bb = '@' in string
                }
            }
        """
        val result = compileText(C64Target(), false, text, writeAssembly = false)!!
        val start = result.compilerAst.entrypoint
        val containmentChecks = start.statements.takeLast(4)
        (containmentChecks[0] as IfElse).condition shouldBe instanceOf<ContainmentCheck>()
        (containmentChecks[1] as IfElse).condition shouldBe instanceOf<ContainmentCheck>()
        (containmentChecks[2] as Assignment).value shouldBe instanceOf<ContainmentCheck>()
        (containmentChecks[3] as Assignment).value shouldBe instanceOf<ContainmentCheck>()
    }

    test("invalid `in` containment checks") {
        val text="""
            main {
                sub start() {
                    bool @shared cc
                    ubyte [  ] array = [1,2,3]
                    cc = 99 in 12345
                    cc = 9999 in array
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(),  false, text, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "iterable must be"
        errors.errors[1] shouldContain "datatype doesn't match"
    }

    test("vardecl options any order okay") {
        val text="""
            main {
                %option force_output
                sub start() {
                    ubyte @zp @shared @requirezp var1
                    ubyte @shared @zp var2
                    ubyte @zp var3
                    ubyte @shared var4
                    ubyte @requirezp var5
                    ubyte var6
                }
            }
        """
        val result = compileText(C64Target(),  false, text, writeAssembly = false)!!
        val stmt = result.compilerAst.entrypoint.statements
        stmt.size shouldBe 12
        val var1 = stmt[0] as VarDecl
        var1.sharedWithAsm shouldBe true
        var1.zeropage shouldBe ZeropageWish.REQUIRE_ZEROPAGE
        val var2 = stmt[2] as VarDecl
        var2.sharedWithAsm shouldBe true
        var2.zeropage shouldBe ZeropageWish.PREFER_ZEROPAGE
        val var3 = stmt[4] as VarDecl
        var3.sharedWithAsm shouldBe false
        var3.zeropage shouldBe ZeropageWish.PREFER_ZEROPAGE
        val var4 = stmt[6] as VarDecl
        var4.sharedWithAsm shouldBe true
        var4.zeropage shouldBe ZeropageWish.DONTCARE
        val var5 = stmt[8] as VarDecl
        var5.sharedWithAsm shouldBe false
        var5.zeropage shouldBe ZeropageWish.REQUIRE_ZEROPAGE
        val var6 = stmt[10] as VarDecl
        var6.sharedWithAsm shouldBe false
        var6.zeropage shouldBe ZeropageWish.DONTCARE
    }

    test("line comment in array literal is ok") {
        val src="""
%import textio
%zeropage basicsafe

main {
    sub start() {
        byte[] foo = [ 1, 2, ; this comment is ok

; line comment also ok

               3,
                4]

        foo[1] ++
    }
}"""

        compileText(C64Target(),  false, src, writeAssembly = false) shouldNotBe null
    }

    test("various alternative curly brace styles are ok") {
        val src="""
%zeropage dontuse

main {
    ; curly braces without newline
    sub start () { foo() derp() other() }
    sub foo() { cx16.r0++ }
    asmsub derp() { %asm {{ nop }} %ir {{ load.b r0,1 }} }

    ; curly braces on next line
    sub other()
    {
        cx16.r0++
        asmother()
        asmir()
    }

    asmsub asmother()
    {
        %asm
        {{
            txa
            tay
        }}
    }

    asmsub asmir()
    {
        %ir
        {{
            load.b r0,1
        }}
    }
}"""

        compileText(VMTarget(),  false, src, writeAssembly = false) shouldNotBe null
    }

    test("underscores for numeric groupings") {
        val src="""
%option enable_floats
main {
    sub start() {
        uword w1 = 000_1234_5__
        uword w2 = ${'$'}ff_ee
        uword w3 = %11_0000_111111__0000
        float fl = 3_000_001.141_592_654
    }
}"""
        val result = compileText(VMTarget(),  false, src, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 8
        val assigns = st.filterIsInstance<Assignment>()
        (assigns[0].value as NumericLiteral).number shouldBe 12345
        (assigns[1].value as NumericLiteral).number shouldBe 0xffee
        (assigns[2].value as NumericLiteral).number shouldBe 0b1100001111110000
        (assigns[3].value as NumericLiteral).number shouldBe 3000001.141592654
    }

    test("oneliner") {
        val src="""
            main { sub start() { cx16.r0++ cx16.r1++ } }
            other { asmsub thing() { %asm {{ inx }} } }
        """
        val result = compileText(VMTarget(),  false, src, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 2
    }

})
