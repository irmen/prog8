package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.instanceOf
import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.Assignment
import prog8.ast.statements.Return
import prog8.ast.statements.VarDecl
import prog8.ast.statements.VarDeclType
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8tests.helpers.compileText

class TestConst: FunSpec({

    test("const folding multiple scenarios +/-") {
        val source = """
            main {
                const ubyte boardHeightC = 20
                const ubyte boardOffsetC = 3

                sub start() {
                    uword @shared load_location = 12345
                    word @shared llw = 12345
                    cx16.r0 = load_location + 8000 + 1000 + 1000
                    cx16.r2 = 8000 + 1000 + 1000 + load_location
                    cx16.r4 = load_location + boardOffsetC + boardHeightC - 1
                    cx16.r5s = llw - 900 - 999
                    cx16.r7s = llw - 900 + 999
                }
            }"""
        val result = compileText(C64Target(), true, source, writeAssembly = false)!!
        // expected:
//        uword load_location
//        load_location = 12345
//        word llw
//        llw = 12345
//        cx16.r0 = load_location + 10000
//        cx16.r2 = load_location + 10000
//        cx16.r4 = load_location + 22
//        cx16.r5s = llw - 1899
//        cx16.r7s = llw + 99
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 9

        val addR0value = (stmts[4] as Assignment).value
        val binexpr0 = addR0value as BinaryExpression
        binexpr0.operator shouldBe "+"
        binexpr0.right shouldBe NumericLiteral(DataType.UWORD, 10000.0, Position.DUMMY)
        val addR2value = (stmts[5] as Assignment).value
        val binexpr2 = addR2value as BinaryExpression
        binexpr2.operator shouldBe "+"
        binexpr2.right shouldBe NumericLiteral(DataType.UWORD, 10000.0, Position.DUMMY)
        val addR4value = (stmts[6] as Assignment).value
        val binexpr4 = addR4value as BinaryExpression
        binexpr4.operator shouldBe "+"
        binexpr4.right shouldBe NumericLiteral(DataType.UWORD, 22.0, Position.DUMMY)
        val subR5value = (stmts[7] as Assignment).value
        val binexpr5 = subR5value as BinaryExpression
        binexpr5.operator shouldBe "-"
        binexpr5.right shouldBe NumericLiteral(DataType.UWORD, 1899.0, Position.DUMMY)
        val subR7value = (stmts[8] as Assignment).value
        val binexpr7 = subR7value as BinaryExpression
        binexpr7.operator shouldBe "+"
        binexpr7.right shouldBe NumericLiteral(DataType.UWORD, 99.0, Position.DUMMY)
    }

    test("const folding multiple scenarios * and / (floats)") {
        val source = """
            %option enable_floats
            main {
                sub start() {
                    float @shared llw = 300.0
                    float @shared result
                    result = 9 * 2 * 10 * llw
                    result++
                    result = llw * 9 * 2 * 10
                    result++
                    result = llw / 30 / 3
                    result++
                    result = llw / 2 * 10
                    result++
                    result = llw * 90 / 5
                }
            }"""
        val result = compileText(C64Target(), true, source, writeAssembly = false)!!
        // expected:
//        float llw
//        llw = 300.0
//        float result
//        result = llw * 180.0
//        result++
//        result = llw * 180.0
//        result++
//        result = llw / 90.0
//        result++
//        result = llw * 5.0
//        result++
//        result = llw * 18.0
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 12

        val mulR0Value = (stmts[3] as Assignment).value
        val binexpr0 = mulR0Value as BinaryExpression
        binexpr0.operator shouldBe "*"
        binexpr0.right shouldBe NumericLiteral(DataType.FLOAT, 180.0, Position.DUMMY)
        val mulR1Value = (stmts[5] as Assignment).value
        val binexpr1 = mulR1Value as BinaryExpression
        binexpr1.operator shouldBe "*"
        binexpr1.right shouldBe NumericLiteral(DataType.FLOAT, 180.0, Position.DUMMY)
        val divR2Value = (stmts[7] as Assignment).value
        val binexpr2 = divR2Value as BinaryExpression
        binexpr2.operator shouldBe "/"
        binexpr2.right shouldBe NumericLiteral(DataType.FLOAT, 90.0, Position.DUMMY)
        val mulR3Value = (stmts[9] as Assignment).value
        val binexpr3 = mulR3Value as BinaryExpression
        binexpr3.operator shouldBe "*"
        binexpr3.right shouldBe NumericLiteral(DataType.FLOAT, 5.0, Position.DUMMY)
        binexpr3.left shouldBe instanceOf<IdentifierReference>()
        val mulR4Value = (stmts[11] as Assignment).value
        val binexpr4 = mulR4Value as BinaryExpression
        binexpr4.operator shouldBe "*"
        binexpr4.right shouldBe NumericLiteral(DataType.FLOAT, 18.0, Position.DUMMY)
        binexpr4.left shouldBe instanceOf<IdentifierReference>()
    }

    test("const folding multiple scenarios * and / (integers)") {
        val source = """
            main {
                sub start() {
                    word @shared llw = 300
                    cx16.r0s = 9 * 2 * 10 * llw
                    cx16.r1s = llw * 9 * 2 * 10
                    cx16.r2s = llw / 30 / 3
                    cx16.r3s = llw / 2 * 10
                    cx16.r4s = llw * 90 / 5     ; not optimized because of loss of integer division precision
                }
            }"""
        val result = compileText(C64Target(), true, source, writeAssembly = false)!!
        // expected:
//        word llw
//        llw = 300
//        cx16.r0s = llw * 180
//        cx16.r1s = llw * 180
//        cx16.r2s = llw / 90
//        cx16.r3s = llw /2 *10
//        cx16.r4s = llw *90 /5
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 7

        val mulR0Value = (stmts[2] as Assignment).value
        val binexpr0 = mulR0Value as BinaryExpression
        binexpr0.operator shouldBe "*"
        binexpr0.right shouldBe NumericLiteral(DataType.UWORD, 180.0, Position.DUMMY)
        val mulR1Value = (stmts[3] as Assignment).value
        val binexpr1 = mulR1Value as BinaryExpression
        binexpr1.operator shouldBe "*"
        binexpr1.right shouldBe NumericLiteral(DataType.UWORD, 180.0, Position.DUMMY)
        val divR2Value = (stmts[4] as Assignment).value
        val binexpr2 = divR2Value as BinaryExpression
        binexpr2.operator shouldBe "/"
        binexpr2.right shouldBe NumericLiteral(DataType.UWORD, 90.0, Position.DUMMY)
        val mulR3Value = (stmts[5] as Assignment).value
        val binexpr3 = mulR3Value as BinaryExpression
        binexpr3.operator shouldBe "*"
        binexpr3.right shouldBe NumericLiteral(DataType.UWORD, 10.0, Position.DUMMY)
        binexpr3.left shouldBe instanceOf<BinaryExpression>()
        val mulR4Value = (stmts[6] as Assignment).value
        val binexpr4 = mulR4Value as BinaryExpression
        binexpr4.operator shouldBe "/"
        binexpr4.right shouldBe NumericLiteral(DataType.UWORD, 5.0, Position.DUMMY)
        binexpr4.left shouldBe instanceOf<BinaryExpression>()
    }

    test("const folding and silently typecasted for initializervalues") {
        val sourcecode = """
            main {
                sub start() {
                    const ubyte TEST = 10
                    byte @shared x1 = TEST as byte + 1
                    byte @shared x2 = 1 + TEST as byte
                    ubyte @shared y1 = TEST + 1 as byte
                    ubyte @shared y2 = 1 as byte + TEST
                }
            }
        """
        val result = compileText(C64Target(), false, sourcecode)!!
        val mainsub = result.compilerAst.entrypoint
        mainsub.statements.size shouldBe 10
        val declTest = mainsub.statements[0] as VarDecl
        val declX1 = mainsub.statements[1] as VarDecl
        val initX1 = mainsub.statements[2] as Assignment
        val declX2 = mainsub.statements[3] as VarDecl
        val initX2 = mainsub.statements[4] as Assignment
        val declY1 = mainsub.statements[5] as VarDecl
        val initY1 = mainsub.statements[6] as Assignment
        val declY2 = mainsub.statements[7] as VarDecl
        val initY2 = mainsub.statements[8] as Assignment
        mainsub.statements[9] shouldBe instanceOf<Return>()
        (declTest.value as NumericLiteral).number shouldBe 10.0
        declX1.value shouldBe null
        declX2.value shouldBe null
        declY1.value shouldBe null
        declY2.value shouldBe null
        (initX1.value as NumericLiteral).type shouldBe DataType.BYTE
        (initX1.value as NumericLiteral).number shouldBe 11.0
        (initX2.value as NumericLiteral).type shouldBe DataType.BYTE
        (initX2.value as NumericLiteral).number shouldBe 11.0
        (initY1.value as NumericLiteral).type shouldBe DataType.UBYTE
        (initY1.value as NumericLiteral).number shouldBe 11.0
        (initY2.value as NumericLiteral).type shouldBe DataType.UBYTE
        (initY2.value as NumericLiteral).number shouldBe 11.0
    }

    test("const eval of address-of a memory mapped variable") {
        val src = """
main {
    sub start() {
        &ubyte mappedvar = 1000
        cx16.r0 = &mappedvar
        &ubyte[8] array = &mappedvar
        cx16.r0 = &array
        
        const uword HIGH_MEMORY_START = 40960
        &uword[20] @shared wa = HIGH_MEMORY_START
    }
}"""
        compileText(VMTarget(), optimize=false, src, writeAssembly=false) shouldNotBe null
    }

    test("const pointer variable indexing works") {
        val src="""
main {
    sub start() {
        const uword pointer=$1000
        cx16.r0L = pointer[2]
        pointer[2] = cx16.r0L
    }
}
"""
        compileText(C64Target(), optimize=false, src, writeAssembly=false) shouldNotBe null
    }

    test("advanced const folding of known library functions") {
        val src="""
%import floats
%import math
%import string

main {
    sub start() {
        float fl = 1.2  ; no other assignments
        cx16.r0L = string.isdigit(math.diff(119, floats.floor(floats.deg(fl)) as ubyte))
        cx16.r1L = string.isletter(math.diff(119, floats.floor(floats.deg(1.2)) as ubyte))
    }
}"""
        val result = compileText(Cx16Target(), true, src, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 3
        (st[0] as VarDecl).type shouldBe VarDeclType.CONST
        val assignv1 = (st[1] as Assignment).value
        val assignv2 = (st[2] as Assignment).value
        (assignv1 as NumericLiteral).number shouldBe 1.0
        (assignv2 as NumericLiteral).number shouldBe 0.0
    }

    test("const address-of memory mapped arrays") {
        val src="""
main {
    sub start() {
        &uword[30] wb = ${'$'}2000
        &uword[100] array1 = ${'$'}9e00
        &uword[30] array2 = &array1[len(wb)]

        cx16.r0 = &array1           ; ${'$'}9e00
        cx16.r1 = &array1[len(wb)]  ; ${'$'}9e3c
        cx16.r2 = &array2           ; ${'$'}9e3c
    }
}"""
        val result = compileText(Cx16Target(), false, src, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 6
        ((st[0] as VarDecl).value as NumericLiteral).number shouldBe 0x2000
        ((st[1] as VarDecl).value as NumericLiteral).number shouldBe 0x9e00
        ((st[2] as VarDecl).value as NumericLiteral).number shouldBe 0x9e00+2*30
        ((st[3] as Assignment).value as NumericLiteral).number shouldBe 0x9e00
        ((st[4] as Assignment).value as NumericLiteral).number shouldBe 0x9e00+2*30
        ((st[5] as Assignment).value as NumericLiteral).number shouldBe 0x9e00+2*30
    }

    test("address of a const uword pointer array expression") {
        val src="""
main {
    sub start() {
        const uword buffer = ${'$'}2000
        uword addr = &buffer[2]
        
        const ubyte width = 100
        ubyte @shared i
        ubyte @shared j
        uword addr2 = &buffer[i * width + j]
    }
}"""
        val result = compileText(Cx16Target(), true, src, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 9999
    }
})
