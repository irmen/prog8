package prog8tests.ast

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.AstToSourceTextConverter
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.internedStringsModuleName
import prog8.parser.ParseError
import prog8.parser.Prog8Parser.parseModule
import prog8.parser.SourceCode
import prog8tests.ast.helpers.DummyFunctions
import prog8tests.ast.helpers.DummyMemsizer
import kotlin.test.assertContains


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestAstToSourceText {

    private fun generateP8(module: Module) : String {
        val program = Program("test", DummyFunctions, DummyMemsizer)
            .addModule(module)

        var generatedText = ""
        val it = AstToSourceTextConverter({ str -> generatedText += str }, program)
        it.visit(program)

        return generatedText
    }

    private fun roundTrip(module: Module): Pair<String, Module> {
        val generatedText = generateP8(module)
        try {
            val parsedAgain = parseModule(SourceCode.Text(generatedText))
            return Pair(generatedText, parsedAgain)
        } catch (e: ParseError) {
            assert(false) { "should produce valid Prog8 but threw $e" }
            throw e
        }
    }

    @Test
    fun testMentionsInternedStringsModule() {
        val orig = SourceCode.Text("\n")
        val (txt, _) = roundTrip(parseModule(orig))
        // assertContains has *actual* first!
        assertContains(txt, Regex(";.*$internedStringsModuleName"))
    }

    @Test
    fun testImportDirectiveWithLib() {
        val orig = SourceCode.Text("%import textio\n")
        val (txt, _) = roundTrip(parseModule(orig))
        // assertContains has *actual* first!
        assertContains(txt, Regex("%import +textio"))
    }

    @Test
    fun testImportDirectiveWithUserModule() {
        val orig = SourceCode.Text("%import my_own_stuff\n")
        val (txt, _) = roundTrip(parseModule(orig))
        // assertContains has *actual* first!
        assertContains(txt, Regex("%import +my_own_stuff"))
    }


    @Test
    fun testStringLiteral_noAlt() {
        val orig = SourceCode.Text("""
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
        val orig = SourceCode.Text("""
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
        val orig = SourceCode.Text("""
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
        val orig = SourceCode.Text("""
            main {
                ubyte cAlt = @'x'
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        // assertContains has *actual* first!
        assertContains(txt, Regex("ubyte +cAlt += +@'x'"), "alt char literal")
    }

}
