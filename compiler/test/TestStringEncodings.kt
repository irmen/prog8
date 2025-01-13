package prog8tests.compiler

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.expectError
import com.github.michaelbull.result.getOrElse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.ast.expressions.CharLiteral
import prog8.ast.expressions.NumericLiteral
import prog8.ast.expressions.StringLiteral
import prog8.ast.statements.Assignment
import prog8.ast.statements.VarDecl
import prog8.code.core.Encoding
import prog8.code.core.Position
import prog8.code.core.unescape
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.encodings.Encoder
import prog8.code.target.encodings.AtasciiEncoding
import prog8.code.target.encodings.IsoEncoding
import prog8.code.target.encodings.PetsciiEncoding
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import java.io.CharConversionException


class TestStringEncodings: FunSpec({

    context("petscii") {
        test("testZero") {
            PetsciiEncoding.encodePetscii("\u0000", true) shouldBe Ok(listOf<UByte>(0u))
            PetsciiEncoding.encodePetscii("\u0000", false) shouldBe Ok(listOf<UByte>(0u))
            PetsciiEncoding.decodePetscii(listOf(0u), true) shouldBe Ok("\u0000")
            PetsciiEncoding.decodePetscii(listOf(0u), false) shouldBe Ok("\u0000")
        }

        test("testLowercase") {
            PetsciiEncoding.encodePetscii("hello WORLD 123 @!£", true) shouldBe
                    Ok(listOf<UByte>(72u, 69u, 76u, 76u, 79u, 32u, 0xd7u, 0xcfu, 0xd2u, 0xccu, 0xc4u, 32u, 49u, 50u, 51u, 32u, 64u, 33u, 0x5cu))
            PetsciiEncoding.encodePetscii("\uf11a", true) shouldBe Ok(listOf<UByte>(0x12u))   // reverse vid
            PetsciiEncoding.encodePetscii("✓", true) shouldBe Ok(listOf<UByte>(0xfau))
            withClue("expect lowercase error fallback") {
                PetsciiEncoding.encodePetscii("π", true) shouldBe Ok(listOf<UByte>(255u))
                PetsciiEncoding.encodePetscii("♥", true) shouldBe Ok(listOf<UByte>(0xd3u))
            }

            PetsciiEncoding.decodePetscii(listOf(72u, 0xd7u, 0x5cu, 0xfau, 0x12u), true) shouldBe Ok("hW£✓\uF11A")
        }

        test("testUppercase") {
            PetsciiEncoding.encodePetscii("HELLO 123 @!£") shouldBe
                    Ok(listOf<UByte>(72u, 69u, 76u, 76u, 79u, 32u, 49u, 50u, 51u, 32u, 64u, 33u, 0x5cu))
            PetsciiEncoding.encodePetscii("\uf11a") shouldBe Ok(listOf<UByte>(0x12u))   // reverse vid
            PetsciiEncoding.encodePetscii("♥") shouldBe Ok(listOf<UByte>(0xd3u))
            PetsciiEncoding.encodePetscii("π") shouldBe Ok(listOf<UByte>(0xffu))
            withClue("expecting fallback") {
                PetsciiEncoding.encodePetscii("✓") shouldBe Ok(listOf<UByte>(250u))
            }

            PetsciiEncoding.decodePetscii(listOf(72u, 0x5cu, 0xd3u, 0xffu)) shouldBe Ok("H£♥π")
        }

        test("testScreencodeLowercase") {
            PetsciiEncoding.encodeScreencode("hello WORLD 123 @!£", true) shouldBe
                    Ok(listOf<UByte>(0x08u, 0x05u, 0x0cu, 0x0cu, 0x0fu, 0x20u, 0x57u, 0x4fu, 0x52u, 0x4cu, 0x44u, 0x20u, 0x31u, 0x32u, 0x33u, 0x20u, 0x00u, 0x21u, 0x1cu))
            PetsciiEncoding.encodeScreencode("✓", true) shouldBe Ok(listOf<UByte>(0x7au))
            withClue("expect fallback") {
                PetsciiEncoding.encodeScreencode("♥", true) shouldBe Ok(listOf<UByte>(83u))
                PetsciiEncoding.encodeScreencode("π", true) shouldBe Ok(listOf<UByte>(94u))
            }

            PetsciiEncoding.decodeScreencode(listOf(0x08u, 0x57u, 0x1cu, 0x7au), true) shouldBe Ok("hW£✓")
        }

        test("testScreencodeUppercase") {
            PetsciiEncoding.encodeScreencode("WORLD 123 @!£") shouldBe
                    Ok(listOf<UByte>(0x17u, 0x0fu, 0x12u, 0x0cu, 0x04u, 0x20u, 0x31u, 0x32u, 0x33u, 0x20u, 0x00u, 0x21u, 0x1cu))
            PetsciiEncoding.encodeScreencode("♥") shouldBe Ok(listOf<UByte>(0x53u))
            PetsciiEncoding.encodeScreencode("π") shouldBe Ok(listOf<UByte>(0x5eu))
            PetsciiEncoding.encodeScreencode("HELLO") shouldBe Ok(listOf<UByte>(8u, 5u, 12u, 12u, 15u))
            withClue("expecting fallback") {
                PetsciiEncoding.encodeScreencode("hello") shouldBe Ok(listOf<UByte>(8u, 5u, 12u, 12u, 15u))
                PetsciiEncoding.encodeScreencode("✓") shouldBe Ok(listOf<UByte>(122u))
            }

            PetsciiEncoding.decodeScreencode(listOf(0x17u, 0x1cu, 0x53u, 0x5eu)) shouldBe Ok("W£♥π")
        }

        test("testErrorCases") {
            PetsciiEncoding.encodePetscii("~", true).expectError { "shouldn't be able to encode tilde" }
            PetsciiEncoding.encodePetscii("~", false).expectError { "shouldn't be able to encode tilde" }
            PetsciiEncoding.encodeScreencode("~", true).expectError { "shouldn't be able to encode tilde" }
            PetsciiEncoding.encodeScreencode("~", false).expectError { "shouldn't be able to encode tilde" }
        }

        test("testReturn") {
            PetsciiEncoding.encodePetscii("\r", true) shouldBe Ok(listOf<UByte>(13u))
            PetsciiEncoding.encodePetscii("\r", false) shouldBe Ok(listOf<UByte>(13u))
            PetsciiEncoding.encodePetscii("\n", true) shouldBe Ok(listOf<UByte>(13u))
            PetsciiEncoding.encodePetscii("\n", false) shouldBe Ok(listOf<UByte>(13u))
            PetsciiEncoding.decodePetscii(listOf(13u), false) shouldBe Ok("\n")
            PetsciiEncoding.decodePetscii(listOf(13u), true) shouldBe Ok("\n")
            PetsciiEncoding.decodePetscii(listOf(0x8du), false) shouldBe Ok("\r")
            PetsciiEncoding.decodePetscii(listOf(0x8du), true) shouldBe Ok("\r")
        }

        test("testSpecialReplacements") {
            fun encodeP(c: Char, lower: Boolean) = PetsciiEncoding.encodePetscii(c.toString(), lower).getOrElse { throw it }.single()
            fun encodeS(c: Char, lower: Boolean) = PetsciiEncoding.encodeScreencode(c.toString(), lower).getOrElse { throw it }.single()

            PetsciiEncoding.encodePetscii("`", false).expectError { "shouldn't have translation for backtick" }
            PetsciiEncoding.encodePetscii("`", true).expectError { "shouldn't have translation for backtick" }
            PetsciiEncoding.encodePetscii("~", false).expectError { "shouldn't have translation for tilde" }
            PetsciiEncoding.encodePetscii("~", true).expectError { "shouldn't have translation for tilde" }

            encodeP('^', false) shouldBe 94u
            encodeP('^', true) shouldBe 94u
            encodeS('^', false) shouldBe 30u
            encodeS('^', true) shouldBe 30u
            encodeP('_', false) shouldBe 228u
            encodeP('_', true) shouldBe 228u
            encodeS('_', false) shouldBe 100u
            encodeS('_', true) shouldBe 100u
            encodeP('{', false) shouldBe 243u
            encodeP('{', true) shouldBe 243u
            encodeS('{', false) shouldBe 115u
            encodeS('{', true) shouldBe 115u
            encodeP('}', false) shouldBe 235u
            encodeP('}', true) shouldBe 235u
            encodeS('}', false) shouldBe 107u
            encodeS('}', true) shouldBe 107u
            encodeP('|', false) shouldBe 221u
            encodeP('|', true) shouldBe 221u
            encodeS('|', false) shouldBe 93u
            encodeS('|', true) shouldBe 93u
            encodeP('\\', false) shouldBe 205u
            encodeP('\\', true) shouldBe 205u
            encodeS('\\', false) shouldBe 77u
            encodeS('\\', true) shouldBe 77u
            encodeP('\r', true) shouldBe 13u
            encodeP('\r', false) shouldBe 13u
            encodeP('\n', true) shouldBe 13u
            encodeP('\n', false) shouldBe 13u
        }

        test("testBoxDrawingCharsEncoding") {
            fun encodeP(c: Char, lower: Boolean) = PetsciiEncoding.encodePetscii(c.toString(), lower).getOrElse { throw it }.single()
            fun encodeS(c: Char, lower: Boolean) = PetsciiEncoding.encodeScreencode(c.toString(), lower).getOrElse { throw it }.single()

            // pipe char
            encodeP('|', false) shouldBe 221u
            encodeP('|', true) shouldBe 221u
            encodeS('|', false) shouldBe 93u
            encodeS('|', true) shouldBe 93u
            // ... same as '│', 0x7D -> BOX DRAWINGS LIGHT VERTICAL
            encodeP('│', false) shouldBe 221u
            encodeP('│', true) shouldBe 221u
            encodeS('│', false) shouldBe 93u
            encodeS('│', true) shouldBe 93u

            // underscore
            encodeP('_', false) shouldBe 228u
            encodeP('_', true) shouldBe 228u
            encodeS('_', false) shouldBe 100u
            encodeS('_', true) shouldBe 100u
            // ... same as '▁',  0xE4 LOWER ONE EIGHTH BLOCK
            encodeP('▁', false) shouldBe 228u
            encodeP('▁', true) shouldBe 228u
            encodeS('▁', false) shouldBe 100u
            encodeS('▁', true) shouldBe 100u

            // ─    0xC0 -> BOX DRAWINGS LIGHT HORIZONTAL
            encodeP('─', false) shouldBe 192u
            encodeP('─', true) shouldBe 192u
            encodeS('─', false) shouldBe 64u
            encodeS('─', true) shouldBe 64u
            // │    0x62 -> BOX DRAWINGS LIGHT VERTICAL
            encodeP('│', false) shouldBe 221u
            encodeP('│', true) shouldBe 221u
            encodeS('│', false) shouldBe 93u
            encodeS('│', true) shouldBe 93u
        }

        test("testBoxDrawingCharsDecoding") {
            // ─    0xC0 -> BOX DRAWINGS LIGHT HORIZONTAL
            PetsciiEncoding.decodePetscii(listOf(195u), false).getOrElse { throw it }.single() shouldBe '\uf13b' //"BOX DRAWINGS LIGHT HORIZONTAL ONE EIGHTH UP (CUS)"
            PetsciiEncoding.decodePetscii(listOf(195u), true).getOrElse { throw it }.single() shouldBe 'C'
            PetsciiEncoding.decodePetscii(listOf(192u), false).getOrElse { throw it }.single() shouldBe '─'
            PetsciiEncoding.decodePetscii(listOf(192u), true).getOrElse { throw it }.single() shouldBe '─'
            PetsciiEncoding.decodeScreencode(listOf(67u), false).getOrElse { throw it }.single() shouldBe '\uf13b' //"BOX DRAWINGS LIGHT HORIZONTAL ONE EIGHTH UP (CUS)"
            PetsciiEncoding.decodeScreencode(listOf(67u), true).getOrElse { throw it }.single() shouldBe 'C'
            PetsciiEncoding.decodeScreencode(listOf(64u), false).getOrElse { throw it }.single() shouldBe '─'
            PetsciiEncoding.decodeScreencode(listOf(64u), true).getOrElse { throw it }.single() shouldBe '─'

            // │    0x62 -> BOX DRAWINGS LIGHT VERTICAL
            PetsciiEncoding.decodePetscii(listOf(125u), false).getOrElse { throw it }.single() shouldBe '│'
            PetsciiEncoding.decodePetscii(listOf(125u), true).getOrElse { throw it }.single() shouldBe '│'
            PetsciiEncoding.decodePetscii(listOf(221u), false).getOrElse { throw it }.single() shouldBe '│'
            PetsciiEncoding.decodePetscii(listOf(221u), true).getOrElse { throw it }.single() shouldBe '│'
            PetsciiEncoding.decodeScreencode(listOf(93u), false).getOrElse { throw it }.single() shouldBe '│'
            PetsciiEncoding.decodeScreencode(listOf(93u), true).getOrElse { throw it }.single() shouldBe '│'
            PetsciiEncoding.decodeScreencode(listOf(66u), false).getOrElse { throw it }.single() shouldBe '\uf13c' // "BOX DRAWINGS LIGHT VERTICAL ONE EIGHTH LEFT (CUS)"
            PetsciiEncoding.decodeScreencode(listOf(66u), true).getOrElse { throw it }.single() shouldBe 'B'
        }
    }

    context("atari") {
        test("atascii encoding") {
            AtasciiEncoding.encode("a") shouldBe Ok(listOf<UByte>(97u))
            AtasciiEncoding.encode("A") shouldBe Ok(listOf<UByte>(65u))
            AtasciiEncoding.encode("\n") shouldBe Ok(listOf<UByte>(155u))
        }

        test("atascii decoding") {
            AtasciiEncoding.decode(listOf<UByte>(97u)) shouldBe Ok("a")
            AtasciiEncoding.decode(listOf<UByte>(65u)) shouldBe Ok("A")
            AtasciiEncoding.decode(listOf<UByte>(155u)) shouldBe Ok("\n")
        }
    }

    context("iso") {
        test("iso accepts iso-characters") {
            val result = IsoEncoding.encode("a_~ëç")
            result.getOrElse { throw it }.map {it.toInt()} shouldBe listOf(97, 95, 126, 235, 231)
        }

        test("non-iso doesn't accept iso-characters") {
            var result = PetsciiEncoding.encodePetscii("a_~ë")
            result.expectError { "should not encode" }
            result = PetsciiEncoding.encodeScreencode("a_~ë")
            result.expectError { "should not encode" }
        }
    }

    context("kata") {
        test("kata translation to half width glyphs") {
            val orig = "ｶ が ガ"
            orig.length shouldBe 5
            val str = StringLiteral.create(orig, Encoding.KATAKANA, Position.DUMMY)
            str.value.length shouldBe 7

            val character = CharLiteral.create('ｶ', Encoding.KATAKANA, Position.DUMMY)
            character.value shouldBe 'ｶ'

            shouldThrow<CharConversionException> {
                CharLiteral.create('ガ', Encoding.KATAKANA, Position.DUMMY)
            }
        }
    }

    test("special pass-through") {
        val passthroughEscaped= """\x00\x1b\x99\xff"""
        val passthrough = passthroughEscaped.unescape()
        passthrough.length shouldBe 4
        passthrough[0] shouldBe '\u8000'
        passthrough[1] shouldBe '\u801b'
        passthrough[2] shouldBe '\u8099'
        passthrough[3] shouldBe '\u80ff'
        var encoded = Encoder.encodeString(passthrough, Encoding.PETSCII)
        encoded shouldBe listOf<UByte>(0u, 0x1bu, 0x99u, 0xffu)
        encoded = Encoder.encodeString(passthrough, Encoding.ATASCII)
        encoded shouldBe listOf<UByte>(0u, 0x1bu, 0x99u, 0xffu)
        encoded = Encoder.encodeString(passthrough, Encoding.SCREENCODES)
        encoded shouldBe listOf<UByte>(0u, 0x1bu, 0x99u, 0xffu)
        encoded = Encoder.encodeString(passthrough, Encoding.ISO)
        encoded shouldBe listOf<UByte>(0u, 0x1bu, 0x99u, 0xffu)
    }

    test("invalid encoding immediately errors the parser") {
        val source="""
            main {
                str string5 = unicorns:"wrong"
                ubyte char5 = unicorns:'?'
            
                sub start() {
                }
            }"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, source, errors, false) shouldBe null
        errors.errors.size shouldBe 0
    }

    test("iso string encoding also on C64 compilationtarget") {
        val source="""
            main {
                str string1 = "default"
                str string2 = sc:"screencodes"
                str string3 = iso:"iso"
                str string4 = petscii:"petscii"
                sub start() {
                }
            }"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, source, errors, writeAssembly = false) shouldNotBe null
        errors.errors.size shouldBe 0
    }

    test("iso char encoding also on C64 compilationtarget") {
        val source="""
            main {
                ubyte char1 = 'd'
                ubyte char2 = sc:'s'
                ubyte char3 = iso:'i'
                ubyte char4 = petscii:'p'
                sub start() {
                }
            }"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, source, errors, writeAssembly = false) shouldNotBe null
        errors.errors.size shouldBe 0
    }

    test("all encodings supported for Cx16 target") {
        val source="""
            main {
                str string1 = "default"
                str string2 = sc:"screencodes"
                str string3 = iso:"iso"
                str string4 = iso5:"Хозяин и Работник"
                str string5 = iso16:"zażółć gęślą jaźń"
                str string6 = cp437:"≈ IBM Pc ≈ ♂♀♪☺¶"
                str string7 = petscii:"petscii"
                str string8 = atascii:"atascii"
                str string9 = kata:"ｸｼﾞﾝ｡ # が # ガ"
            
                ubyte char1 = 'd'
                ubyte char2 = sc:'s'
                ubyte char3 = iso:'i'
                ubyte char4 = iso5:'и'
                ubyte char5 = iso16:'ł'
                ubyte char6 = cp437:'☺'
                ubyte char7 = petscii:'p'
                ubyte char8 = atascii:'p'
                ubyte char9 = kata:'ｶ'
            
                sub start() {
                }
            }"""
        compileText(Cx16Target(), false, source, writeAssembly = false) shouldNotBe null
    }

    test("module level no default encoding thus petscii") {
        val source="""
            main {
                str string1 = "default"
                str string2 = petscii:"petscii"
            
                ubyte char1 = 'd'
                ubyte char2 = petscii:'p'
            
                sub start() {
                }
            }"""
        val result = compileText(Cx16Target(), false, source, writeAssembly = false)!!
        val main = result.compilerAst.entrypoint.definingBlock
        main.statements.size shouldBe 7
        val string1 = (main.statements[0] as VarDecl).value as StringLiteral
        string1.encoding shouldBe Encoding.PETSCII
        string1.value shouldBe "default"
        val string2 = (main.statements[1] as VarDecl).value as StringLiteral
        string2.encoding shouldBe Encoding.PETSCII
        string2.value shouldBe "petscii"
        val char1 = (main.statements[3] as Assignment).value as NumericLiteral
        char1.number shouldBe 68.0
        val char2 = (main.statements[5] as Assignment).value as NumericLiteral
        char2.number shouldBe 80.0
    }

    test("module level default encoding iso") {
        val source="""
            %encoding iso
            main {
                str string1 = "default"
                str string2 = petscii:"petscii"
            
                ubyte char1 = 'd'
                ubyte char2 = petscii:'p'
            
                sub start() {
                }
            }"""
        val result = compileText(Cx16Target(), false, source, writeAssembly = false)!!
        val main = result.compilerAst.entrypoint.definingBlock
        main.statements.size shouldBe 7
        val string1 = (main.statements[0] as VarDecl).value as StringLiteral
        string1.encoding shouldBe Encoding.ISO
        string1.value shouldBe "default"
        val string2 = (main.statements[1] as VarDecl).value as StringLiteral
        string2.encoding shouldBe Encoding.PETSCII
        string2.value shouldBe "petscii"
        val char1 = (main.statements[3] as Assignment).value as NumericLiteral
        char1.number shouldBe 100.0
        val char2 = (main.statements[5] as Assignment).value as NumericLiteral
        char2.number shouldBe 80.0
    }
})

