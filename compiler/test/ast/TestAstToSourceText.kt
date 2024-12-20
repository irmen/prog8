package prog8tests.ast

import io.kotest.assertions.fail
import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.string.shouldContain
import prog8.ast.AstToSourceTextConverter
import prog8.ast.Module
import prog8.ast.Program
import prog8.code.source.SourceCode
import prog8.code.internedStringsModuleName
import prog8.parser.ParseError
import prog8.parser.Prog8Parser.parseModule
import prog8tests.helpers.DummyFunctions
import prog8tests.helpers.DummyMemsizer
import prog8tests.helpers.DummyStringEncoder


class TestAstToSourceText: AnnotationSpec() {

    private fun generateP8(module: Module) : String {
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
            .addModule(module)

        var generatedText = ""
        val it = AstToSourceTextConverter({ str -> generatedText += str }, program, true)
        it.visit(program)

        return generatedText
    }

    private fun roundTrip(module: Module): Pair<String, Module> {
        val generatedText = generateP8(module)
        try {
            val parsedAgain = parseModule(SourceCode.Text(generatedText))
            return Pair(generatedText, parsedAgain)
        } catch (e: ParseError) {
            fail("should produce valid Prog8 but threw $e")
        }
    }

    @Test
    fun testMentionsInternedStringsModule() {
        val orig = SourceCode.Text("\n")
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex(";.*$internedStringsModuleName")
    }

    @Test
    fun testImportDirectiveWithLib() {
        val orig = SourceCode.Text("%import textio\n")
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("%import +textio")
    }

    @Test
    fun testImportDirectiveWithUserModule() {
        val orig = SourceCode.Text("%import my_own_stuff\n")
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("%import +my_own_stuff")
    }


    @Test
    fun testStringLiteral_DefaultEnc() {
        val orig = SourceCode.Text("""
            main {
                str s = "fooBar\n"
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("str +s += +\"fooBar\\\\n\"")
    }

    @Test
    fun testStringLiteral_withSc() {
        val orig = SourceCode.Text("""
            main {
                str sAlt = sc:"fooBar\n"
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("str +sAlt += +sc:\"fooBar\\\\n\"")
    }

    @Test
    fun testStringLiteral_withIso() {
        val orig = SourceCode.Text("""
            main {
                str sAlt = iso:"fooBar\n"
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("str +sAlt += +iso:\"fooBar\\\\n\"")
    }

    @Test
    fun testCharLiteral_defaultEnc() {
        val orig = SourceCode.Text("""
            main {
                ubyte c = 'x'
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("ubyte +c += +'x'")
    }

    @Test
    fun testCharLiteral_Sc() {
        val orig = SourceCode.Text("""
            main {
                ubyte cAlt = sc:'x'
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("ubyte +cAlt += +sc:'x'")
    }

    @Test
    fun testVar_withZpAndShared() {
        val orig = SourceCode.Text("""
            main {
                ubyte @shared @zp qq
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("ubyte +@zp +@shared +qq")
    }

}
