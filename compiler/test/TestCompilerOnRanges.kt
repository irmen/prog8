package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.ast.expressions.ArrayLiteral
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteral
import prog8.ast.expressions.RangeExpression
import prog8.ast.statements.ForLoop
import prog8.ast.statements.VarDecl
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.Encoding
import prog8.code.core.Position
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.cartesianProduct
import prog8tests.helpers.compileText


/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 */
class TestCompilerOnRanges: FunSpec({

    test("testUByteArrayInitializerWithRange_char_to_char") {
        val platform = Cx16Target()
        val result = compileText(platform, false, """
            main {
                sub start() {
                    ubyte[] @shared cs = sc:'a' to sc:'z' ; values are computed at compile time 
                }
            }
        """)!!

        val program = result.compilerAst
        val startSub = program.entrypoint
        val decl = startSub
            .statements.filterIsInstance<VarDecl>()[0]
        val rhsValues = (decl.value as ArrayLiteral)
            .value // Array<Expression>
            .map { (it as NumericLiteral).number.toInt() }
        val expectedStart = platform.encodeString("a", Encoding.SCREENCODES)[0].toInt()
        val expectedEnd = platform.encodeString("z", Encoding.SCREENCODES)[0].toInt()
        val expectedStr = "$expectedStart .. $expectedEnd"

        val actualStr = "${rhsValues.first()} .. ${rhsValues.last()}"
        withClue(".first .. .last") {
            actualStr shouldBe expectedStr
        }
        withClue("rangeExpr.size()") {
            (rhsValues.last() - rhsValues.first() + 1) shouldBe (expectedEnd - expectedStart + 1)
        }
    }

    test("testFloatArrayInitializerWithRange_char_to_char") {
        val platform = C64Target()
        val result = compileText(platform, optimize = false, """
            %import floats
            main {
                sub start() {
                    float[] @shared cs = 'a' to 'z' ; values are computed at compile time 
                }
            }
        """)!!

        val program = result.compilerAst
        val startSub = program.entrypoint
        val decl = startSub
            .statements.filterIsInstance<VarDecl>()[0]
        val rhsValues = (decl.value as ArrayLiteral)
            .value // Array<Expression>
            .map { (it as NumericLiteral).number.toInt() }
        val expectedStart = platform.encodeString("a", Encoding.PETSCII)[0].toInt()
        val expectedEnd = platform.encodeString("z", Encoding.PETSCII)[0].toInt()
        val expectedStr = "$expectedStart .. $expectedEnd"

        val actualStr = "${rhsValues.first()} .. ${rhsValues.last()}"
        withClue(".first .. .last") {
            actualStr shouldBe expectedStr
        }
        withClue("rangeExpr.size()") {
            rhsValues.size shouldBe (expectedEnd - expectedStart + 1)
        }
    }

    context("floatArrayInitializerWithRange") {
        withData(
            nameFn = {
                when (it.first) {
                    "" -> "no"
                    "42" -> "correct"
                    else -> "wrong"
                } + " array size given" +
                        ", " + (if (it.second == "") "without" else "with") + " %option enable_floats" +
                        ", ${it.third.name}, optimize: ${it.fourth}"
            },
            cartesianProduct(
                listOf("", "42", "41"),                 // sizeInDecl
                listOf("%import floats", ""),           // optEnableFloats
                listOf(Cx16Target(), C64Target()),          // platform
                listOf(false, true)                     // optimize
            )
        ) { seq ->
            val (sizeInDecl, optEnableFloats, platform, optimize) = seq

            val result = compileText(platform, optimize, """
                    $optEnableFloats
                    main {
                        sub start() {
                            float[$sizeInDecl] @shared cs = 1 to 42 ; values are computed at compile time
                        }
                    }
                """)
            if (optEnableFloats != "" && (sizeInDecl=="" || sizeInDecl=="42"))
                result shouldNotBe null
            else
                result shouldBe null
        }
    }

    test("testForLoopWithRange_char_to_char") {
        val platform = Cx16Target()
        val result = compileText(platform, optimize = true, """
            main {
                sub start() {
                    ubyte i
                    for i in sc:'a' to 'f' {
                        i += i ; keep optimizer from removing it
                    }
                }
            }
        """)!!

        val program = result.compilerAst
        val startSub = program.entrypoint
        val iterable = startSub
            .statements.filterIsInstance<ForLoop>()
            .map { it.iterable }[0]
        val rangeExpr = iterable as RangeExpression

        val expectedStart = platform.encodeString("a", Encoding.SCREENCODES)[0].toInt()
        val expectedEnd = platform.encodeString("f", Encoding.PETSCII)[0].toInt()
        val expectedStr = "$expectedStart .. $expectedEnd"

        val intProgression = rangeExpr.toConstantIntegerRange()
        val actualStr = "${intProgression?.first} .. ${intProgression?.last}"
        withClue(".first .. .last") {
            actualStr shouldBe expectedStr
        }
        withClue("rangeExpr.size()") {
            rangeExpr.size() shouldBe (expectedEnd - expectedStart + 1)
        }
    }

    test("testForLoopWithRange_ubyte_to_ubyte") {
        val platform = Cx16Target()
        val result = compileText(platform, optimize = true, """
            main {
                sub start() {
                    ubyte i
                    for i in 1 to 9 {
                        i += i ; keep optimizer from removing it
                    }
                }
            }
        """)!!

        val program = result.compilerAst
        val startSub = program.entrypoint
        val rangeExpr = startSub
            .statements.filterIsInstance<ForLoop>()
            .map { it.iterable }
            .filterIsInstance<RangeExpression>()[0]

        rangeExpr.size() shouldBe 9
        val intProgression = rangeExpr.toConstantIntegerRange()
        intProgression?.first shouldBe 1
        intProgression?.last shouldBe 9
    }

    test("testForLoopWithRange_str_downto_str") {
        val errors = ErrorReporterForTests()
        compileText(
            Cx16Target(), true, """
            main {
                sub start() {
                    ubyte i
                    for i in "start" downto "end" {
                        i += i ; keep optimizer from removing it
                    }
                }
            }
        """, errors, false) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain ".p8:5:30: range expression from value must be integer"
        errors.errors[1] shouldContain ".p8:5:45: range expression to value must be integer"
    }

    test("testForLoopWithIterable_str") {
        val result = compileText(
            Cx16Target(), false, """
            main {
                sub start() {
                    ubyte i
                    for i in "something" {
                        i += i ; keep optimizer from removing it
                    }
                }
            }
        """)!!

        val program = result.compilerAst
        val startSub = program.entrypoint
        val iterable = startSub
            .statements.filterIsInstance<ForLoop>()
            .map { it.iterable }
            .filterIsInstance<IdentifierReference>()[0]

        iterable.inferType(program).getOrUndef() shouldBe DataType.forDt(BaseDataType.STR)
    }

    test("testRangeExprNumericSize") {
        val expr = RangeExpression(
            NumericLiteral.optimalInteger(10, Position.DUMMY),
            NumericLiteral.optimalInteger(20, Position.DUMMY),
            NumericLiteral.optimalInteger(2, Position.DUMMY),
            Position.DUMMY)
        expr.size() shouldBe 6
        expr.toConstantIntegerRange() shouldBe (10..20 step 2)

        val expr2 = RangeExpression(
            NumericLiteral.optimalInteger(20, Position.DUMMY),
            NumericLiteral.optimalInteger(10, Position.DUMMY),
            NumericLiteral.optimalInteger(-3, Position.DUMMY),
            Position.DUMMY)
        expr2.size() shouldBe 4
        expr2.toConstantIntegerRange() shouldBe (20 downTo 10 step 3)
    }

    test("range with negative step should be constvalue") {
        val result = compileText(
            C64Target(), false, """
            main {
                sub start() {
                    ubyte[] array = 100 to 50 step -2
                    ubyte xx
                    for xx in 100 to 50 step -2 {
                    }
                }
            }
        """)!!
        val statements = result.compilerAst.entrypoint.statements
        val array = (statements[0] as VarDecl).value
        array shouldBe instanceOf<ArrayLiteral>()
        (array as ArrayLiteral).value.size shouldBe 26
        val forloop = (statements.dropLast(1).last() as ForLoop)
        forloop.iterable shouldBe instanceOf<RangeExpression>()
        (forloop.iterable as RangeExpression).step shouldBe NumericLiteral(BaseDataType.BYTE, -2.0, Position.DUMMY)
    }

    test("range with start/end variables should be ok") {
        val result = compileText(
            C64Target(), false, """
            main {
                sub start() {
                    byte from = 100
                    byte end = 50
                    byte xx
                    for xx in from to end step -2 {
                    }
                }
            }
        """)!!
        val statements = result.compilerAst.entrypoint.statements
        val forloop = (statements.dropLast(1).last() as ForLoop)
        forloop.iterable shouldBe instanceOf<RangeExpression>()
        (forloop.iterable as RangeExpression).step shouldBe NumericLiteral(BaseDataType.BYTE, -2.0, Position.DUMMY)
    }


    test("for statement on all possible iterable expressions") {
        compileText(
            C64Target(), false, """
            main {
                sub start() {
                    ubyte xx
                    uword ww
                    str name = "irmen"
                    ubyte[] values = [1,2,3,4,5,6,7]
                    uword[] wvalues = [1000,2000,3000]
            
                    for xx in name {
                        xx++
                    }
            
                    for xx in values {
                        xx++
                    }
            
                    for xx in 10 to 20 step 3 {
                        xx++
                    }
            
                    for xx in "abcdef" {
                        xx++
                    }
            
                    for xx in [2,4,6,8] {
                        xx++
                    }
                    
                    for ww in [9999,8888,7777] {
                        xx++
                    }

                    for ww in wvalues {
                        xx++
                    }
                }
            }""", writeAssembly = true) shouldNotBe null
    }

    test("if containment check on all possible iterable expressions") {
        compileText(
            C64Target(), false, """
            main {
                sub start() {
                    ubyte xx
                    uword ww
                    str name = "irmen"
                    ubyte[] values = [1,2,3,4,5,6,7]
                    uword[] wvalues = [1000,2000,3000]
                    uword[] @nosplit wnsvalues = [1000,2000,3000]
            
                    if 'm' in name {
                        xx++
                    }
            
                    if 5 in values {
                        xx++
                    }
            
                    if 'b' in "abcdef" {
                        xx++
                    }
            
                    if xx in name {
                        xx++
                    }
            
                    if xx in values {
                        xx++
                    }
            
                    if xx in "abcdef" {
                        xx++
                    }
            
                    if xx in [2,4,6,8] {
                        xx++
                    }
                    
                    if ww in [9999,8888,7777] {
                        xx++
                    }

                    if ww in wvalues {
                        xx++
                    }                    
                    
                    if ww in wnsvalues {
                        xx++
                    }                    

                    if xx in 10 to 20 {
                        xx++
                    }
                    
                    if ww in 1000 to 2000 {
                        xx++
                    }
                }
            }""", writeAssembly = true) shouldNotBe null
    }

    test("containment check expressions") {
        compileText(
            C64Target(), false, """
            main {
                sub start() {
                    bool xx
                    uword ww
                    str name = "irmen"
                    ubyte[] values = [1,2,3,4,5,6,7]
                    uword[] wvalues = [1000,2000,3000]
                    uword[] @nosplit wnsvalues = [1000,2000,3000]
            
                    xx = 'm' in name
                    xx = 5 in values
                    xx = 'b' in "abcdef"
                    xx = 8 in [2,4,6,8]
                    xx = xx in name
                    xx = xx in values
                    xx = xx in "abcdef"
                    xx = xx in [2,4,6,8]
                    xx = ww in [9000,8000,7000]
                    xx = ww in wvalues
                    xx = ww in wnsvalues
                }
            }""", writeAssembly = true) shouldNotBe null
    }

    test("ranges with byte and word boundary") {
        val src="""
main{
    sub start() {
        cx16.r0 = 500
        if cx16.r0 in 127 to 5555
            cx16.r0++

        cx16.r0 = 50
        if cx16.r0 in 5555 downto 127
            cx16.r0++
    }
}           
        """
        compileText(Cx16Target(), true, src, writeAssembly = true) shouldNotBe null
    }

    test("range types changed from byte to words if needed by outer containment check") {
        val src= """
main {
    sub start() {
        bool @shared z1 = cx16.r0 in 1 to 135
        bool @shared z2 = cx16.r0 in $0001 to 135
        bool @shared z4 = cx16.r0s in 1 to 135
        bool @shared z6 = cx16.r0s in 1 to (135 as word)
        bool @shared z3 = cx16.r0 in 1 to (135 as word)
        bool @shared z5 = cx16.r0s in $0001 to 135
    }
}"""
        compileText(Cx16Target(), true, src) shouldNotBe null
    }
})
