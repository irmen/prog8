package prog8tests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.ast.IFunctionCall
import prog8.ast.expressions.*
import prog8.ast.statements.Assignment
import prog8.ast.statements.IfElse
import prog8.ast.statements.VarDecl
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.target.C64Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText


class TestTypecasts: FunSpec({

    test("integer args for builtin funcs") {
        val text="""
            %import floats
            main {
                sub start() {
                    float fl
                    floats.print_f(abs(fl))
                }
            }"""
        val errors = ErrorReporterForTests()
        val result = compileText(C64Target(), false, text, writeAssembly = false, errors=errors)
        result shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "type mismatch, was: FLOAT expected one of: [UBYTE, BYTE, UWORD, WORD]"
    }

    test("not casting bool operands to logical operators") {
        val text="""
            %import textio
            %zeropage basicsafe
            
            main {
                sub start() {
                    bool bb2=true
                    bool @shared bb = bb2 and true
                }
            }"""
        val result = compileText(C64Target(), false, text, writeAssembly = false)!!
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 4
        val expr = (stmts[3] as Assignment).value as BinaryExpression
        expr.operator shouldBe "and"
        expr.right shouldBe NumericLiteral(DataType.UBYTE, 1.0, Position.DUMMY)
        (expr.left as IdentifierReference).nameInSource shouldBe listOf("bb2")  // no cast

        val result2 = compileText(C64Target(), true, text, writeAssembly = true)!!
        val stmts2 = result2.program.entrypoint.statements
        stmts2.size shouldBe 6
        val expr2 = (stmts2[4] as Assignment).value as BinaryExpression
        expr2.operator shouldBe "&"
        expr2.right shouldBe NumericLiteral(DataType.UBYTE, 1.0, Position.DUMMY)
        (expr2.left as IdentifierReference).nameInSource shouldBe listOf("bb")
    }

    test("bool expressions with functioncalls") {
        val text="""
main {
    sub ftrue(ubyte arg) -> ubyte {
        arg++
        return 64
    }

    sub start() {
        bool ub1 = true
        bool ub2 = true
        bool ub3 = true
        bool ub4 = 0
        bool @shared bvalue

        bvalue = ub1 xor ub2 xor ub3 xor true
        bvalue = ub1 xor ub2 xor ub3 xor ftrue(99)
        bvalue = ub1 and ub2 and ftrue(99)
    }
}"""
        val result = compileText(C64Target(), true, text, writeAssembly = true)!!
        val stmts = result.program.entrypoint.statements
        /*
        ubyte ub1
        ub1 = 1
        ubyte ub2
        ub2 = 1
        ubyte ub3
        ub3 = 1
        ubyte @shared bvalue
        bvalue = ub1
        bvalue ^= ub2
        bvalue ^= ub3
        bvalue ^= 1
        bvalue = (((ub1^ub2)^ub3)^(ftrue(99)!=0))
        bvalue = ((ub1&ub2)&(ftrue(99)!=0))
        return
         */
        stmts.size shouldBe 14
        val assignValue1 = (stmts[7] as Assignment).value as IdentifierReference
        val assignValue2 = (stmts[11] as Assignment).value as BinaryExpression
        val assignValue3 = (stmts[12] as Assignment).value as BinaryExpression
        assignValue1.nameInSource shouldBe listOf("ub1")
        assignValue2.operator shouldBe "^"
        assignValue3.operator shouldBe "&"
        val right2 = assignValue2.right as BinaryExpression
        val right3 = assignValue3.right as BinaryExpression
        right2.operator shouldBe "!="
        right3.operator shouldBe "!="
        right2.left shouldBe instanceOf<IFunctionCall>()
        right2.right shouldBe NumericLiteral(DataType.UBYTE, 0.0, Position.DUMMY)
        right3.left shouldBe instanceOf<IFunctionCall>()
        right3.right shouldBe NumericLiteral(DataType.UBYTE, 0.0, Position.DUMMY)
    }

    test("logical with byte instead of bool") {
        val text="""
%import textio

main  {

    sub ftrue(ubyte arg) -> ubyte {
        arg++
        return 128
    }

    sub ffalse(ubyte arg) -> ubyte {
        arg++
        return 0
    }

    sub start() {
        ubyte ub1 = 2
        ubyte ub2 = 4
        ubyte ub3 = 8
        ubyte ub4 = 0
        ubyte bvalue

        txt.print("const not 0: ")
        txt.print_ub(not 129)
        txt.nl()
        txt.print("const not 1: ")
        txt.print_ub(not 0)
        txt.nl()
        txt.print("const inv 126: ")
        txt.print_ub(~ 129)
        txt.nl()
        txt.print("const inv 255: ")
        txt.print_ub(~ 0)
        txt.nl()
        bvalue = 129
        txt.print("bitwise inv 126: ")
        bvalue = ~ bvalue
        txt.print_ub(bvalue)
        txt.nl()
        bvalue = 0
        txt.print("bitwise inv 255: ")
        bvalue = ~ bvalue
        txt.print_ub(bvalue)
        txt.nl()

        txt.print("bitwise or  14: ")
        txt.print_ub(ub1 | ub2 | ub3 | ub4)
        txt.nl()
        txt.print("bitwise or 142: ")
        txt.print_ub(ub1 | ub2 | ub3 | ub4 | 128)
        txt.nl()
        txt.print("bitwise and  0: ")
        txt.print_ub(ub1 & ub2 & ub3 & ub4)
        txt.nl()
        txt.print("bitwise and  8: ")
        txt.print_ub(ub3 & ub3 & 127)
        txt.nl()
        txt.print("bitwise xor 14: ")
        txt.print_ub(ub1 ^ ub2 ^ ub3 ^ ub4)
        txt.nl()
        txt.print("bitwise xor  6: ")
        txt.print_ub(ub1 ^ ub2 ^ ub3 ^ 8)
        txt.nl()
        txt.print("bitwise not 247: ")
        txt.print_ub(~ub3)
        txt.nl()
        txt.print("bitwise not 255: ")
        txt.print_ub(~ub4)
        txt.nl()

        txt.print("not 0: ")
        bvalue = 3 * (ub4 | not (ub3 | ub3 | ub3))
        txt.print_ub(bvalue)
        if 3*(ub4 | not (ub1 | ub1 | ub1))
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()

        txt.print("not 0: ")
        bvalue = not ub3
        txt.print_ub(bvalue)
        if not ub1
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()

        txt.print("not 1: ")
        bvalue = not ub4
        txt.print_ub(bvalue)
        if not ub4
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()

        bvalue = bvalue and 128
        txt.print("bvl 1: ")
        txt.print_ub(bvalue)
        if bvalue and 128
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()

        txt.print("and 1: ")
        bvalue = ub1 and ub2 and ub3
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print("and 1: ")
        bvalue = ub1 and ub2 and ub3 and 64
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3 and 64
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print("and 1: ")
        bvalue = ub1 and ub2 and ub3 and ftrue(99)
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3 and ftrue(99)
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print("and 0: ")
        bvalue = ub1 and ub2 and ub3 and ub4
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3 and ub4
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()
        txt.print("and 0: ")
        bvalue = ub1 and ub2 and ub3 and ffalse(99)
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3 and ffalse(99)
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()

        txt.print(" or 1: ")
        bvalue = ub1 or ub2 or ub3 or ub4
        txt.print_ub(bvalue)
        if ub1 or ub2 or ub3 or ub4
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print(" or 1: ")
        bvalue = ub4 or ub4 or ub1
        txt.print_ub(bvalue)
        if ub4 or ub4 or ub1
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print(" or 1: ")
        bvalue = ub1 or ub2 or ub3 or ftrue(99)
        txt.print_ub(bvalue)
        if ub1 or ub2 or ub3 or ftrue(99)
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()

        txt.print("xor 1: ")
        bvalue = ub1 xor ub2 xor ub3 xor ub4
        txt.print_ub(bvalue)
        if ub1 xor ub2 xor ub3 xor ub4
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print("xor 1: ")
        bvalue = ub1 xor ub2 xor ub3 xor ffalse(99)
        txt.print_ub(bvalue)
        if ub1 xor ub2 xor ub3 xor ffalse(99)
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()

        txt.print("xor 0: ")
        bvalue = ub1 xor ub2 xor ub3 xor ub4 xor true
        txt.print_ub(bvalue)
        if ub1 xor ub2 xor ub3 xor ub4 xor true
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()
        txt.print("xor 0: ")
        bvalue = ub1 xor ub2 xor ub3 xor ftrue(99)
        txt.print_ub(bvalue)
        if ub1 xor ub2 xor ub3 xor ftrue(99)
            txt.print(" / fail")
        else
            txt.print(" / ok")
    }
}
        """
        val result = compileText(C64Target(), true, text, writeAssembly = true)!!
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBeGreaterThan 10
    }

    test("logical with bools") {
        val text="""
%import textio

main  {

    sub ftrue(ubyte arg) -> ubyte {
        arg++
        return 128
    }

    sub ffalse(ubyte arg) -> ubyte {
        arg++
        return 0
    }

    sub start() {
        bool ub1 = 2
        bool ub2 = 4
        bool ub3 = 8
        bool ub4 = 0
        bool bvalue

        txt.print("const not 0: ")
        txt.print_ub(not 129)
        txt.nl()
        txt.print("const not 1: ")
        txt.print_ub(not 0)
        txt.nl()
        txt.print("const inv 126: ")
        txt.print_ub(~ 129)
        txt.nl()
        txt.print("const inv 255: ")
        txt.print_ub(~ 0)
        txt.nl()
        bvalue = 129
        txt.print("bitwise inv 126: ")
        bvalue = ~ bvalue
        txt.print_ub(bvalue)
        txt.nl()
        bvalue = 0
        txt.print("bitwise inv 255: ")
        bvalue = ~ bvalue
        txt.print_ub(bvalue)
        txt.nl()

        txt.print("bitwise or  14: ")
        txt.print_ub(ub1 | ub2 | ub3 | ub4)
        txt.nl()
        txt.print("bitwise or 142: ")
        txt.print_ub(ub1 | ub2 | ub3 | ub4 | 128)
        txt.nl()
        txt.print("bitwise and  0: ")
        txt.print_ub(ub1 & ub2 & ub3 & ub4)
        txt.nl()
        txt.print("bitwise and  8: ")
        txt.print_ub(ub3 & ub3 & 127)
        txt.nl()
        txt.print("bitwise xor 14: ")
        txt.print_ub(ub1 ^ ub2 ^ ub3 ^ ub4)
        txt.nl()
        txt.print("bitwise xor  6: ")
        txt.print_ub(ub1 ^ ub2 ^ ub3 ^ 8)
        txt.nl()
        txt.print("bitwise not 247: ")
        txt.print_ub(~ub3)
        txt.nl()
        txt.print("bitwise not 255: ")
        txt.print_ub(~ub4)
        txt.nl()

        txt.print("not 0: ")
        bvalue = 3 * (ub4 | not (ub3 | ub3 | ub3))
        txt.print_ub(bvalue)
        if 3*(ub4 | not (ub1 | ub1 | ub1))
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()

        txt.print("not 0: ")
        bvalue = not ub3
        txt.print_ub(bvalue)
        if not ub1
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()

        txt.print("not 1: ")
        bvalue = not ub4
        txt.print_ub(bvalue)
        if not ub4
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()

        bvalue = bvalue and 128
        txt.print("bvl 1: ")
        txt.print_ub(bvalue)
        if bvalue and 128
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()

        txt.print("and 1: ")
        bvalue = ub1 and ub2 and ub3
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print("and 1: ")
        bvalue = ub1 and ub2 and ub3 and 64
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3 and 64
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print("and 1: ")
        bvalue = ub1 and ub2 and ub3 and ftrue(99)
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3 and ftrue(99)
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print("and 0: ")
        bvalue = ub1 and ub2 and ub3 and ub4
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3 and ub4
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()
        txt.print("and 0: ")
        bvalue = ub1 and ub2 and ub3 and ffalse(99)
        txt.print_ub(bvalue)
        if ub1 and ub2 and ub3 and ffalse(99)
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()

        txt.print(" or 1: ")
        bvalue = ub1 or ub2 or ub3 or ub4
        txt.print_ub(bvalue)
        if ub1 or ub2 or ub3 or ub4
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print(" or 1: ")
        bvalue = ub4 or ub4 or ub1
        txt.print_ub(bvalue)
        if ub4 or ub4 or ub1
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print(" or 1: ")
        bvalue = ub1 or ub2 or ub3 or ftrue(99)
        txt.print_ub(bvalue)
        if ub1 or ub2 or ub3 or ftrue(99)
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()

        txt.print("xor 1: ")
        bvalue = ub1 xor ub2 xor ub3 xor ub4
        txt.print_ub(bvalue)
        if ub1 xor ub2 xor ub3 xor ub4
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()
        txt.print("xor 1: ")
        bvalue = ub1 xor ub2 xor ub3 xor ffalse(99)
        txt.print_ub(bvalue)
        if ub1 xor ub2 xor ub3 xor ffalse(99)
            txt.print(" / ok")
        else
            txt.print(" / fail")
        txt.nl()

        txt.print("xor 0: ")
        bvalue = ub1 xor ub2 xor ub3 xor ub4 xor true
        txt.print_ub(bvalue)
        if ub1 xor ub2 xor ub3 xor ub4 xor true
            txt.print(" / fail")
        else
            txt.print(" / ok")
        txt.nl()
        txt.print("xor 0: ")
        bvalue = ub1 xor ub2 xor ub3 xor ftrue(99)
        txt.print_ub(bvalue)
        if ub1 xor ub2 xor ub3 xor ftrue(99)
            txt.print(" / fail")
        else
            txt.print(" / ok")
    }
}
        """
        val result = compileText(C64Target(), true, text, writeAssembly = true)!!
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBeGreaterThan 10
    }

    test("bool arrays") {
        val text="""
            main {
                sub start() {
                    bool[] barray = [true, false, 1, 0, 222]
                    bool bb
                    ubyte xx
            
                    for bb in barray {
                        if bb
                            xx++
                    }
                 }
             }"""
        val result = compileText(C64Target(), false, text, writeAssembly = false)!!
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 6
        val arraydecl = stmts[0] as VarDecl
        arraydecl.datatype shouldBe DataType.ARRAY_BOOL
        val values = (arraydecl.value as ArrayLiteral).value
        values.size shouldBe 5
        values[0] shouldBe NumericLiteral(DataType.UBYTE, 1.0, Position.DUMMY)
        values[1] shouldBe NumericLiteral(DataType.UBYTE, 0.0, Position.DUMMY)
        values[2] shouldBe NumericLiteral(DataType.UBYTE, 1.0, Position.DUMMY)
        values[3] shouldBe NumericLiteral(DataType.UBYTE, 0.0, Position.DUMMY)
        values[4] shouldBe NumericLiteral(DataType.UBYTE, 1.0, Position.DUMMY)
    }

    test("correct handling of bool parameters") {
        val text="""
            main  {
            
                sub thing(bool b1, bool b2) -> bool {
                    return (b1 and b2) or b1
                }
            
                sub start() {
                    bool boolvalue1 = true
                    bool boolvalue2 = false
                    uword xx
            
                    boolvalue1 = thing(true, false)
                    boolvalue2 = thing(xx, xx)
            
                    if boolvalue1 and boolvalue2
                        boolvalue1=false
                 }
            }"""
        val result = compileText(C64Target(), false, text, writeAssembly = false)!!
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 9
        val fcall1 = ((stmts[6] as Assignment).value as IFunctionCall)
        fcall1.args[0] shouldBe NumericLiteral(DataType.UBYTE, 1.0, Position.DUMMY)
        fcall1.args[1] shouldBe NumericLiteral(DataType.UBYTE, 0.0, Position.DUMMY)
        val fcall2 = ((stmts[7] as Assignment).value as IFunctionCall)
        (fcall2.args[0] as TypecastExpression).type shouldBe DataType.BOOL
        (fcall2.args[1] as TypecastExpression).type shouldBe DataType.BOOL
        val ifCond = (stmts[8] as IfElse).condition as BinaryExpression
        ifCond.operator shouldBe "and" // no asm writing so logical expressions haven't been replaced with bitwise equivalents yet
        (ifCond.left as IdentifierReference).nameInSource shouldBe listOf("boolvalue1")
        (ifCond.right as IdentifierReference).nameInSource shouldBe listOf("boolvalue2")
    }

    test("correct evaluation of words in boolean expressions") {
        val text="""
            main {
                sub start() {
                    uword camg
                    ubyte @shared interlaced
                    interlaced = (camg & ${'$'}0004) != 0
                    interlaced++
                    interlaced = (${'$'}0004 & camg) != 0
                    interlaced++
                    uword @shared ww
                    ww = (camg & ${'$'}0004)
                    ww++
                    ww = (${'$'}0004 & camg)
                    
                    ubyte @shared wordNr2 = (interlaced >= ${'$'}33) + (interlaced >= ${'$'}66) + (interlaced >= ${'$'}99) + (interlaced >= ${'$'}CC)
                }
            }"""
        val result = compileText(C64Target(), false, text, writeAssembly = true)!!
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBeGreaterThan 10
    }

    test("word to byte casts") {
        val text="""
            %import textio
            main {
                sub func(ubyte arg) -> word {
                    return arg-99
                }
            
                sub start() {
                    txt.print_ub(func(0) as ubyte)
                    txt.print_uw(func(0) as ubyte)
                    txt.print_w(func(0) as ubyte)
                }
            }"""
        val result = compileText(C64Target(), false, text, writeAssembly = false)!!
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 3
    }

    test("add missing & to function arguments") {
        val text="""
            main  {
            
                sub handler(uword fptr) {
                }
            
                sub start() {
                    uword variable
            
                    pushw(variable)
                    pushw(handler)
                    pushw(&handler)
                    handler(variable)
                    handler(handler)
                    handler(&handler)
                }
            }"""
        val result = compileText(C64Target(), false, text, writeAssembly = false)!!
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 8
        val arg1 = (stmts[2] as IFunctionCall).args.single()
        val arg2 = (stmts[3] as IFunctionCall).args.single()
        val arg3 = (stmts[4] as IFunctionCall).args.single()
        val arg4 = (stmts[5] as IFunctionCall).args.single()
        val arg5 = (stmts[6] as IFunctionCall).args.single()
        val arg6 = (stmts[7] as IFunctionCall).args.single()
        arg1 shouldBe instanceOf<IdentifierReference>()
        arg2 shouldBe instanceOf<AddressOf>()
        arg3 shouldBe instanceOf<AddressOf>()
        arg4 shouldBe instanceOf<IdentifierReference>()
        arg5 shouldBe instanceOf<AddressOf>()
        arg6 shouldBe instanceOf<AddressOf>()
    }

    test("correct typecasts") {
        val text = """
            %import floats
            
            main {
                sub start() {
                    float @shared fl = 3.456
                    uword @shared uw = 5555
                    byte @shared bb = -44

                    bb = uw as byte
                    uw = bb as uword
                    fl = uw as float
                    fl = bb as float
                    bb = fl as byte
                    uw = fl as uword
                    uw = 8888 + (bb as ubyte)
                }
            }
        """
        val result = compileText(C64Target(), false, text, writeAssembly = true)!!
        result.program.entrypoint.statements.size shouldBe 15
    }

    test("invalid typecasts of numbers") {
        val text = """
            %import floats
            
            main {
                sub start() {
                    ubyte @shared bb

                    bb = 5555 as ubyte
                    routine(5555 as ubyte)
                }
                
                sub routine(ubyte bb) {
                    bb++
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, writeAssembly = true, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "can't cast"
        errors.errors[1] shouldContain "can't cast"
    }

    test("refuse to round float literal 1") {
        val text = """
            %option enable_floats
            main {
                sub start() {
                    float @shared fl = 3.456 as uword
                    fl = 1.234 as uword
                }
            }"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "can't cast"
        errors.errors[1] shouldContain "can't cast"
    }

    test("refuse to round float literal 2") {
        val text = """
            %option enable_floats
            main {
                sub start() {
                    float @shared fl = 3.456
                    fl++
                    fl = fl as uword
                }
            }"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, errors=errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "in-place makes no sense"
    }

    test("refuse to round float literal 3") {
        val text = """
            %option enable_floats
            main {
                sub start() {
                    uword @shared ww = 3.456 as uword
                    ww++
                    ww = 3.456 as uword
                }
            }"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "can't cast"
        errors.errors[1] shouldContain "can't cast"
    }

    test("correct implicit casts of signed number comparison and logical expressions") {
        val text = """
            %import floats
            
            main {
                sub start() {
                    byte bb = -10
                    word ww = -1000
                    
                    if bb>0 {
                        bb++
                    }
                    if bb < 0 {
                        bb ++
                    }
                    if bb & 1 {
                        bb++
                    }
                    if bb & 128 {
                        bb++
                    }
                    if bb & 255 {
                        bb++
                    }

                    if ww>0 {
                        ww++
                    }
                    if ww < 0 {
                        ww ++
                    }
                    if ww & 1 {
                        ww++
                    }
                    if ww & 32768 {
                        ww++
                    }
                    if ww & 65535 {
                        ww++
                    }
                }
            }
        """
        val result = compileText(C64Target(), false, text, writeAssembly = true)!!
        val statements = result.program.entrypoint.statements
        statements.size shouldBeGreaterThan 10
    }

    test("cast to unsigned in conditional") {
        val text = """
            main {
                sub start() {
                    byte bb
                    word ww
            
                    ubyte iteration_in_progress
                    uword num_bytes

                    if not iteration_in_progress or not num_bytes {
                        num_bytes++
                    }
        
                    if bb as ubyte  {
                        bb++
                    }
                    if ww as uword  {
                        ww++
                    }
                }
            }"""
        val result = compileText(C64Target(), false, text, writeAssembly = true)!!
        val statements = result.program.entrypoint.statements
        statements.size shouldBeGreaterThan 10
    }

    test("no infinite typecast loop in assignment asmgen") {
        val text = """
            main {
                sub start() {
                    word @shared qq = calculate(33)
                }
            
                sub calculate(ubyte row) -> word {
                    return (8-(row as byte))
                }
            }
        """
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("(u)byte extend to word parameters") {
        val text = """
            main {
                sub start() {
                    byte ub1 = -50
                    byte ub2 = -51
                    byte ub3 = -52
                    byte ub4 = 100
                    word @shared ww = func(ub1, ub2, ub3, ub4)
                    ww = func(ub4, ub2, ub3, ub1)
                    ww=afunc(ub1, ub2, ub3, ub4)
                    ww=afunc(ub4, ub2, ub3, ub1)
                }
            
                sub func(word x1, word y1, word x2, word y2) -> word {
                    return x1
                }
            
                asmsub afunc(word x1 @R0, word y1 @R1, word x2 @R2, word y2 @R3) -> word @AY {
                    %asm {{
                        lda  cx16.r0
                        ldy  cx16.r0+1
                        rts
                    }}
                }
            }"""
        compileText(C64Target(), true, text, writeAssembly = true) shouldNotBe null
    }

    test("lsb msb used as args with word types") {
        val text = """
            main {
                sub start() {
                    uword xx=${'$'}ea31
                    uword @shared ww = plot(lsb(xx), msb(xx))
                }
            
                inline asmsub  plot(uword plotx @R0, uword ploty @R1) -> uword @AY{
                    %asm {{
                        lda  cx16.r0
                        ldy  cx16.r1
                        rts
                    }}
                }
            }"""
        compileText(C64Target(), true, text, writeAssembly = true) shouldNotBe null
    }
})
