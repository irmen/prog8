package prog8tests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.ast.IFunctionCall
import prog8.ast.expressions.*
import prog8.ast.printProgram
import prog8.ast.statements.Assignment
import prog8.ast.statements.IfElse
import prog8.ast.statements.VarDecl
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.target.C64Target
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText


class TestTypecasts: FunSpec({

    test("integer args for builtin funcs") {
        val text="""
            %import floats
            main {
                sub start() {
                    float fl
                    floats.print(lsb(fl))
                }
            }"""
        val errors = ErrorReporterForTests()
        val result = compileText(C64Target(), false, text, writeAssembly = false, errors=errors)
        result shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "type mismatch, was: FLOAT expected one of: [UWORD, WORD]"
    }

    test("not casting bool operands to logical operators") {
        val text="""
            %import textio
            %zeropage basicsafe
            
            main {
                sub start() {
                    bool @shared bb2=true
                    bool @shared bb = bb2 and true
                }
            }"""
        val result = compileText(C64Target(), false, text, writeAssembly = false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 4
        val expr = (stmts[3] as Assignment).value as BinaryExpression
        expr.operator shouldBe "and"
        expr.right shouldBe NumericLiteral(DataType.BOOL, 1.0, Position.DUMMY)
        (expr.left as IdentifierReference).nameInSource shouldBe listOf("bb2")  // no cast

        val result2 = compileText(C64Target(), true, text, writeAssembly = true)!!
        val stmts2 = result2.compilerAst.entrypoint.statements
        stmts2.size shouldBe 5
        val expr2 = (stmts2[3] as Assignment).value as BinaryExpression
        expr2.operator shouldBe "and"
        expr2.right shouldBe NumericLiteral(DataType.BOOL, 1.0, Position.DUMMY)
    }

    test("bool expressions with functioncalls") {
        val text="""
main {
    sub ftrue(ubyte arg) -> ubyte {
        arg++
        return 42
    }
    
    sub btrue(ubyte arg) -> bool {
        arg++
        return true
    }

    sub start() {
        bool @shared ub1 = true
        bool @shared ub2 = true
        bool @shared ub3 = true
        bool @shared bvalue

        bvalue = ub1 xor ub2 xor ub3 xor true
        bvalue = ub1 xor ub2 xor ub3 xor ftrue(99)!=0
        bvalue = ub1 and ub2 and ftrue(99)!=0
        bvalue = ub1 xor ub2 xor ub3 xor btrue(99)
        bvalue = ub1 and ub2 and btrue(99)        
    }
}"""
        val result = compileText(C64Target(), true, text, writeAssembly = true)!!
        printProgram(result.compilerAst)
        val stmts = result.compilerAst.entrypoint.statements
        /*
        ubyte @shared ub1
        ub1 = 1
        ubyte @shared ub2
        ub2 = 1
        ubyte @shared ub3
        ub3 = 1
        ubyte @shared bvalue
        bvalue = (((ub1 xor ub2) xor ub3) xor 1)
        bvalue = (((ub1 xor ub2) xor ub3) xor (ftrue(99)!=0))
        bvalue = ((ub1 and ub2) and (ftrue(99)!=0))
        bvalue = (((ub1 xor ub2) xor ub3) xor btrue(99))
        bvalue = ((ub1 and ub2) and btrue(99))
        return
         */
        stmts.size shouldBe 13
        val assignValue1 = (stmts[7] as Assignment).value as BinaryExpression
        val assignValue2 = (stmts[8] as Assignment).value as BinaryExpression
        val assignValue3 = (stmts[9] as Assignment).value as BinaryExpression
        val assignValue4 = (stmts[10] as Assignment).value as BinaryExpression
        val assignValue5 = (stmts[11] as Assignment).value as BinaryExpression
        assignValue1.operator shouldBe "xor"
        assignValue2.operator shouldBe "xor"
        assignValue3.operator shouldBe "and"
        assignValue4.operator shouldBe "xor"
        assignValue5.operator shouldBe "and"
        val right2 = assignValue2.right as BinaryExpression
        val right3 = assignValue3.right as BinaryExpression
        right2.operator shouldBe "!="
        right3.operator shouldBe "!="
        right2.left shouldBe instanceOf<IFunctionCall>()
        right2.right shouldBe NumericLiteral(DataType.UBYTE, 0.0, Position.DUMMY)
        right3.left shouldBe instanceOf<IFunctionCall>()
        right3.right shouldBe NumericLiteral(DataType.UBYTE, 0.0, Position.DUMMY)
        assignValue4.right shouldBe instanceOf<IFunctionCall>()
        assignValue5.right shouldBe instanceOf<IFunctionCall>()
    }

    test("simple logical with byte instead of bool ok with typecasting") {
        val text="""
main  {
    ubyte ubb

    sub start() {
        ubb = ubb and 123
    }
}"""
        val result = compileText(C64Target(), true, text, writeAssembly = true)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 2
        val assignValue = (stmts[0] as Assignment).value as TypecastExpression
        assignValue.type shouldBe DataType.UBYTE
        assignValue.expression.inferType(result.compilerAst).isBool shouldBe true
        (assignValue.expression as BinaryExpression).operator shouldBe "and"
        (assignValue.expression as BinaryExpression).right shouldBe NumericLiteral(DataType.BOOL, 1.0, Position.DUMMY)
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
        val stmts = result.compilerAst.entrypoint.statements
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
        val stmts = result.compilerAst.entrypoint.statements
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
                    
                    bool @shared collected = (interlaced >= ${'$'}33) or (interlaced >= ${'$'}66) or (interlaced >= ${'$'}99) or (interlaced >= ${'$'}CC)
                }
            }"""
        val result = compileText(C64Target(), false, text, writeAssembly = true)!!
        val stmts = result.compilerAst.entrypoint.statements
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
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 3
    }

    test("ubyte to word casts") {
        var src="""
main {
    sub start() {
        ubyte @shared bb = 255
        cx16.r0s = (bb as byte) as word     ; should result in -1 word value
        cx16.r1s = (bb as word)             ; should result in 255 word value
    }
}"""

        val result = compileText(C64Target(), true, src, writeAssembly = false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 4
        val assign1tc = (stmts[2] as Assignment).value as TypecastExpression
        val assign2tc = (stmts[3] as Assignment).value as TypecastExpression
        assign1tc.type shouldBe DataType.WORD
        assign2tc.type shouldBe DataType.WORD
        assign2tc.expression shouldBe instanceOf<IdentifierReference>()
        val assign1subtc = (assign1tc.expression as TypecastExpression)
        assign1subtc.type shouldBe DataType.BYTE
        assign1subtc.expression shouldBe instanceOf<IdentifierReference>()
    }

    test("add missing & to function arguments") {
        val text="""
            main  {
            
                sub handler(uword fptr) {
                }
            
                sub start() {
                    uword variable
            
                    sys.pushw(variable)
                    sys.pushw(handler)
                    sys.pushw(&handler)
                    handler(variable)
                    handler(handler)
                    handler(&handler)
                }
            }"""
        val result = compileText(C64Target(), false, text, writeAssembly = false)!!
        val stmts = result.compilerAst.entrypoint.statements
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
        result.compilerAst.entrypoint.statements.size shouldBe 14
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
        errors.errors[0] shouldContain "no cast"
        errors.errors[1] shouldContain "no cast"
    }

    test("refuse to truncate float literal 1") {
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
        errors.errors[0] shouldContain "refused"
        errors.errors[1] shouldContain "refused"
    }

    test("refuse to truncate float literal 2") {
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

    test("refuse to truncate float literal 3") {
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
        errors.errors[0] shouldContain "refused"
        errors.errors[1] shouldContain "refused"
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
        val statements = result.compilerAst.entrypoint.statements
        statements.size shouldBeGreaterThan 10
    }

    test("cast to unsigned in conditional") {
        val text = """
            main {
                sub start() {
                    byte bb
                    word ww
            
                    bool iteration_in_progress
                    uword num_bytes

                    if not iteration_in_progress or num_bytes==0 {
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
        val statements = result.compilerAst.entrypoint.statements
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

    test("memory reads byte into word variable") {
        val text = """
            main {
                sub start() {
                    uword @shared ww
                    uword address = $1000
                    ww = @(address+100)
                    ww = @(address+1000)
                    cx16.r0 = @(address+100)
                    cx16.r0 = @(address+1000)
                }
            }"""
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
    }

    test("various floating point casts don't crash the compiler") {
        val text="""
            %import floats
            
            main {
                sub score() -> ubyte {
                    cx16.r15++
                    return 5
                }
            
                sub start() {
                    float @shared total = 0
                    ubyte bb = 5
            
                    cx16.r0 = 5
                    total += cx16.r0 as float
                    total += score() as float
                    uword ww = 5
                    total += ww as float
                    total += bb as float
                    float result = score() as float
                    total += result
                }
            }"""
        compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
        compileText(C64Target(), true, text, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), true, text, writeAssembly = true) shouldNotBe null
    }

    test("byte when choices silently converted to word for convenience") {
        val text="""
main {
  sub start() {
    uword z = 3
    when z {
        1-> z++
        2-> z++
        else -> z++
    }
  }
}"""
        compileText(C64Target(), false, text, writeAssembly = false) shouldNotBe null
    }

    test("returning smaller dt than returndt is ok") {
        val text="""
main {
    sub start() {
        void test()
    }
    
    sub test() -> uword {
        return cx16.r0L
    }
}"""
        compileText(C64Target(), false, text, writeAssembly = false) shouldNotBe null
    }

    test("returning bigger dt than returndt is not ok") {
        val text="""
main {
    sub start() {
        void test()
    }
    
    sub test() -> ubyte {
        return cx16.r0
    }
}"""
        val errors=ErrorReporterForTests()
        compileText(C64Target(), false, text, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.single() shouldContain "doesn't match"
    }

    test("long type okay in const expr but otherwise overflow") {
        val src="""
main {
    sub start() {
        const ubyte HEIGHT=240
        uword large = 320*240/8/8
        thing(large)
        thing(320*240/8/8)
        thing(320*HEIGHT/8/8)
        thing(320*HEIGHT)        ; overflow
        large = 12345678         ; overflow
    }

    sub thing(uword value) {
        value++
    }
}"""
        val errors=ErrorReporterForTests()
        compileText(C64Target(), false, src, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 4
        errors.errors[0] shouldContain "no cast"
        errors.errors[1] shouldContain "overflow"
        errors.errors[2] shouldContain "out of range"
        errors.errors[3] shouldContain "overflow"
    }

})
