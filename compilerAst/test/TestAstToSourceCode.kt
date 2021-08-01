package prog8tests

import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import kotlin.test.*
import prog8tests.helpers.*

import prog8.ast.*
import prog8.parser.Prog8Parser.parseModule
import prog8.parser.SourceCode

import prog8.ast.AstToSourceCode
import prog8.parser.ParseError


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestAstToSourceCode {

    private fun generateP8(module: Module) : String {
        val program = Program("test", DummyFunctions, DummyMemsizer)
            .addModule(module)

        var generatedText = ""
        val it = AstToSourceCode({ str -> generatedText += str }, program)
        it.visit(program)

        return generatedText
    }

    private fun roundTrip(module: Module): Pair<String, Module> {
        val generatedText = generateP8(module)
        try {
            val parsedAgain = parseModule(SourceCode.of(generatedText))
            return Pair(generatedText, parsedAgain)
        } catch (e: ParseError) {
            assert(false) { "should produce valid Prog8 but threw $e" }
            throw e
        }
    }

    @Test
    fun testMentionsInternedStringsModule() {
        val orig = SourceCode.of("\n")
        val (txt, _) = roundTrip(parseModule(orig))
        // assertContains has *actual* first!
        assertContains(txt, Regex(";.*$internedStringsModuleName"))
    }

    @Test
    fun testTargetDirectiveAndComment() {
        val orig = SourceCode.of("%target 42  ; invalid arg - shouldn't matter\n")
        val (txt, _) = roundTrip(parseModule(orig))
        // assertContains has *actual* first!
        assertContains(txt, Regex("%target +42"))
    }

    @Test
    fun testImportDirectiveWithLib() {
        val orig = SourceCode.of("%import textio\n")
        val (txt, _) = roundTrip(parseModule(orig))
        // assertContains has *actual* first!
        assertContains(txt, Regex("%import +textio"))
    }

    @Test
    fun testImportDirectiveWithUserModule() {
        val orig = SourceCode.of("%import my_own_stuff\n")
        val (txt, _) = roundTrip(parseModule(orig))
        // assertContains has *actual* first!
        assertContains(txt, Regex("%import +my_own_stuff"))
    }


    @Test
    fun testStringLiteral_noAlt() {
        val orig = SourceCode.of("""
            main {
                str s = "fooBar\n"
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        // assertContains has *actual* first!
        assertContains(txt, Regex("str +s += +\"fooBar\\\\n\""))
    }

    @Test
    fun testStringLiteral_withAlt() {
        val orig = SourceCode.of("""
            main {
                str sAlt = @"fooBar\n"
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        // assertContains has *actual* first!
        assertContains(txt, Regex("str +sAlt += +@\"fooBar\\\\n\""))
    }

    @Test
    fun testCharLiteral_noAlt() {
        val orig = SourceCode.of("""
            main {
                ubyte c = 'x'
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        // assertContains has *actual* first!
        assertContains(txt, Regex("ubyte +c += +'x'"), "char literal")
    }

    @Test
    fun testCharLiteral_withAlt() {
        val orig = SourceCode.of("""
            main {
                ubyte cAlt = @'x'
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        // assertContains has *actual* first!
        assertContains(txt, Regex("ubyte +cAlt += +@'x'"), "alt char literal")
    }

}
