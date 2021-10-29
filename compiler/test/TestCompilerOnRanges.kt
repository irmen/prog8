package prog8tests

import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import prog8.ast.base.DataType
import prog8.ast.expressions.*
import prog8.ast.statements.ForLoop
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl
import prog8.compilerinterface.size
import prog8.compiler.target.C64Target
import prog8.compiler.target.Cx16Target
import prog8.compilerinterface.toConstantIntegerRange
import prog8tests.helpers.*
import kotlin.test.assertContains
import kotlin.test.assertEquals


/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCompilerOnRanges {

    @Test
    fun testUByteArrayInitializerWithRange_char_to_char() {
        val platform = Cx16Target
        val result = compileText(platform, true, """
            main {
                sub start() {
                    ubyte[] cs = @'a' to 'z' ; values are computed at compile time 
                    cs[0] = 23 ; keep optimizer from removing it
                }
            }
        """).assertSuccess()

        val program = result.programAst
        val startSub = program.entrypoint
        val decl = startSub
            .statements.filterIsInstance<VarDecl>()[0]
        val rhsValues = (decl.value as ArrayLiteralValue)
            .value // Array<Expression>
            .map { (it as NumericLiteralValue).number.toInt() }
        val expectedStart = platform.encodeString("a", true)[0].toInt()
        val expectedEnd = platform.encodeString("z", false)[0].toInt()
        val expectedStr = "$expectedStart .. $expectedEnd"

        val actualStr = "${rhsValues.first()} .. ${rhsValues.last()}"
        assertEquals(expectedStr, actualStr,".first .. .last")
        assertEquals(expectedEnd - expectedStart + 1, rhsValues.last() - rhsValues.first() + 1, "rangeExpr.size()")
    }

    @Test
    fun testFloatArrayInitializerWithRange_char_to_char() {
        val platform = C64Target
        val result = compileText(platform, optimize = false, """
            %option enable_floats
            main {
                sub start() {
                    float[] cs = 'a' to 'z' ; values are computed at compile time 
                    cs[0] = 23 ; keep optimizer from removing it
                }
            }
        """).assertSuccess()

        val program = result.programAst
        val startSub = program.entrypoint
        val decl = startSub
            .statements.filterIsInstance<VarDecl>()[0]
        val rhsValues = (decl.value as ArrayLiteralValue)
            .value // Array<Expression>
            .map { (it as NumericLiteralValue).number.toInt() }
        val expectedStart = platform.encodeString("a", false)[0].toInt()
        val expectedEnd = platform.encodeString("z", false)[0].toInt()
        val expectedStr = "$expectedStart .. $expectedEnd"

        val actualStr = "${rhsValues.first()} .. ${rhsValues.last()}"
        assertEquals(expectedStr, actualStr,".first .. .last")
        assertEquals(expectedEnd - expectedStart + 1, rhsValues.size, "rangeExpr.size()")
    }

    fun Subroutine.decl(varName: String): VarDecl {
        return statements.filterIsInstance<VarDecl>()
            .first { it.name == varName }
    }
    inline fun <reified T : Expression> VarDecl.rhs() : T {
        return value as T
    }
    inline fun <reified T : Expression> ArrayLiteralValue.elements() : List<T> {
        return value.map { it as T }
    }

    fun <N : Number> assertEndpoints(expFirst: N, expLast: N, actual: Iterable<N>, msg: String = ".first .. .last") {
        val expectedStr = "$expFirst .. $expLast"
        val actualStr = "${actual.first()} .. ${actual.last()}"
        assertEquals(expectedStr, actualStr,".first .. .last")
    }


    @TestFactory
    fun floatArrayInitializerWithRange() = mapCombinations(
        dim1 = listOf("", "42", "41"),                 // sizeInDecl
        dim2 = listOf("%option enable_floats", ""),    // optEnableFloats
        dim3 = listOf(Cx16Target, C64Target),          // platform
        dim4 = listOf(false, true),                    // optimize
        combine4 = { sizeInDecl, optEnableFloats, platform, optimize ->
            val displayName =
                "test failed for: " +
                when (sizeInDecl) {
                    "" -> "no"
                    "42" -> "correct"
                    else -> "wrong"
                } + " array size given" +
                ", " + (if (optEnableFloats == "") "without" else "with") + " %option enable_floats" +
                ", ${platform.name}, optimize: $optimize"
            dynamicTest(displayName) {
                val result = compileText(platform, optimize, """
                    $optEnableFloats
                    main {
                        sub start() {
                            float[$sizeInDecl] cs = 1 to 42 ; values are computed at compile time 
                            cs[0] = 23 ; keep optimizer from removing it
                        }
                    }
                """)
                if (optEnableFloats != "" && (sizeInDecl=="" || sizeInDecl=="42"))
                    result.assertSuccess()
                else
                    result.assertFailure()
            }
        }
    )

    @Test
    fun testForLoopWithRange_char_to_char() {
        val platform = Cx16Target
        val result = compileText(platform, optimize = true, """
            main {
                sub start() {
                    ubyte i
                    for i in @'a' to 'f' {
                        i += i ; keep optimizer from removing it
                    }
                }
            }
        """).assertSuccess()

        val program = result.programAst
        val startSub = program.entrypoint
        val iterable = startSub
            .statements.filterIsInstance<ForLoop>()
            .map { it.iterable }[0]
        val rangeExpr = iterable as RangeExpr

        val expectedStart = platform.encodeString("a", true)[0].toInt()
        val expectedEnd = platform.encodeString("f", false)[0].toInt()
        val expectedStr = "$expectedStart .. $expectedEnd"

        val intProgression = rangeExpr.toConstantIntegerRange(platform)
        val actualStr = "${intProgression?.first} .. ${intProgression?.last}"
        assertEquals(expectedStr, actualStr,".first .. .last")
        assertEquals(expectedEnd - expectedStart + 1, rangeExpr.size(platform), "rangeExpr.size()")
    }

    @Test
    fun testForLoopWithRange_bool_to_bool() {
        val platform = Cx16Target
        val result = compileText(platform, optimize = true, """
            main {
                sub start() {
                    ubyte i
                    for i in false to true {
                        i += i ; keep optimizer from removing it
                    }
                }
            }
        """).assertSuccess()

        val program = result.programAst
        val startSub = program.entrypoint
        val rangeExpr = startSub
            .statements.filterIsInstance<ForLoop>()
            .map { it.iterable }
            .filterIsInstance<RangeExpr>()[0]

        assertEquals(2, rangeExpr.size(platform))
        val intProgression = rangeExpr.toConstantIntegerRange(platform)
        assertEquals(0, intProgression?.first)
        assertEquals(1, intProgression?.last)
    }

    @Test
    fun testForLoopWithRange_ubyte_to_ubyte() {
        val platform = Cx16Target
        val result = compileText(platform, optimize = true, """
            main {
                sub start() {
                    ubyte i
                    for i in 1 to 9 {
                        i += i ; keep optimizer from removing it
                    }
                }
            }
        """).assertSuccess()

        val program = result.programAst
        val startSub = program.entrypoint
        val rangeExpr = startSub
            .statements.filterIsInstance<ForLoop>()
            .map { it.iterable }
            .filterIsInstance<RangeExpr>()[0]

        assertEquals(9, rangeExpr.size(platform))
        val intProgression = rangeExpr.toConstantIntegerRange(platform)
        assertEquals(1, intProgression?.first)
        assertEquals(9, intProgression?.last)
    }

    @Test
    fun testForLoopWithRange_str_downto_str() {
        val errors = ErrorReporterForTests()
        compileText(Cx16Target, true, """
            main {
                sub start() {
                    ubyte i
                    for i in "start" downto "end" {
                        i += i ; keep optimizer from removing it
                    }
                }
            }
        """, errors, false).assertFailure()
        assertEquals(2, errors.errors.size)
        assertContains(errors.errors[0], ".p8:5:29: range expression from value must be integer")
        assertContains(errors.errors[1], ".p8:5:44: range expression to value must be integer")
    }

    @Test
    fun testForLoopWithIterable_str() {
        val result = compileText(Cx16Target, false, """
            main {
                sub start() {
                    ubyte i
                    for i in "something" {
                        i += i ; keep optimizer from removing it
                    }
                }
            }
        """).assertSuccess()

        val program = result.programAst
        val startSub = program.entrypoint
        val iterable = startSub
            .statements.filterIsInstance<ForLoop>()
            .map { it.iterable }
            .filterIsInstance<IdentifierReference>()[0]

        assertEquals(DataType.STR, iterable.inferType(program).getOr(DataType.UNDEFINED))
    }

}

