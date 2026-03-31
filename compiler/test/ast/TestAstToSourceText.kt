package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import prog8.ast.AstToSourceTextConverter
import prog8.ast.Module
import prog8.ast.Program
import prog8.code.PROG8_CONTAINER_MODULES
import prog8.code.source.SourceCode
import prog8.parser.ParseError
import prog8.parser.Prog8Parser.parseModule
import prog8tests.helpers.DummyFunctions
import prog8tests.helpers.DummyMemsizer
import prog8tests.helpers.DummyStringEncoder


class TestAstToSourceText: FunSpec({

    // Helper function to generate Prog8 source from AST
    fun generateP8(module: Module): String {
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
            .addModule(module)

        var generatedText = ""
        val converter = AstToSourceTextConverter({ str -> generatedText += str }, program, true)
        converter.visit(program)

        return generatedText
    }

    // Helper function for round-trip testing (parse → generate → parse again)
    fun roundTrip(module: Module): Pair<String, Module> {
        val generatedText = generateP8(module)
        try {
            val parsedAgain = parseModule(SourceCode.Text(generatedText))
            return Pair(generatedText, parsedAgain)
        } catch (e: ParseError) {
            error("should produce valid Prog8 but threw $e")
        }
    }

    // ============================================================================
    // AstToSourceText Tests
    // ============================================================================

    test("testMentionsProg8ContainerModules") {
        val orig = SourceCode.Text("\n")
        val (txt, _) = roundTrip(parseModule(orig))
        PROG8_CONTAINER_MODULES.forEach {
            txt shouldContain Regex(";.*$it")
        }
    }

    test("testImportDirectiveWithLib") {
        val orig = SourceCode.Text("%import textio\n")
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("%import +textio")
    }

    test("testImportDirectiveWithUserModule") {
        val orig = SourceCode.Text("%import my_own_stuff\n")
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("%import +my_own_stuff")
    }


    test("testStringLiteral_DefaultEnc") {
        val orig = SourceCode.Text("""
            main {
                str s = "fooBar\n"
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("str +s += +\"fooBar\\\\n\"")
    }

    test("testStringLiteral_withSc") {
        val orig = SourceCode.Text("""
            main {
                str sAlt = sc:"fooBar\n"
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("str +sAlt += +sc:\"fooBar\\\\n\"")
    }

    test("testStringLiteral_withIso") {
        val orig = SourceCode.Text("""
            main {
                str sAlt = iso:"fooBar\n"
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("str +sAlt += +iso:\"fooBar\\\\n\"")
    }

    test("testCharLiteral_defaultEnc") {
        val orig = SourceCode.Text("""
            main {
                ubyte c = 'x'
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("ubyte +c += +'x'")
    }

    test("testCharLiteral_Sc") {
        val orig = SourceCode.Text("""
            main {
                ubyte cAlt = sc:'x'
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("ubyte +cAlt += +sc:'x'")
    }

    test("testVar_withZpAndShared") {
        val orig = SourceCode.Text("""
            main {
                ubyte @shared @zp qq
            }
        """)
        val (txt, _) = roundTrip(parseModule(orig))
        txt shouldContain Regex("ubyte +@zp +@shared +qq")
    }

})
