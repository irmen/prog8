package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
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
import prog8.code.ast.PtAsmSub
import prog8.code.ast.PtSub
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.target.C64Target
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText


class TestTypecasts: FunSpec({

    val outputDir = tempdir().toPath()

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
        val result = compileText(C64Target(), false, text, outputDir, writeAssembly = false, errors=errors)
        result shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "type mismatch, was: float expected one of: [UWORD, WORD, LONG]"
    }

    test("not casting bool operands to logical operators") {
        val text="""
            main {
                sub start() {
                    bool @shared bb2=true
                    bool @shared bb3=false
                    bool @shared bb = bb2 and bb3
                }
            }"""
        val result = compileText(C64Target(), false, text, outputDir, writeAssembly = false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 6
        val expr = (stmts[5] as Assignment).value as BinaryExpression
        expr.operator shouldBe "and"
        (expr.left as IdentifierReference).nameInSource shouldBe listOf("bb2")  // no cast
        (expr.right as IdentifierReference).nameInSource shouldBe listOf("bb3")  // no cast

        val result2 = compileText(C64Target(), true, text, outputDir, writeAssembly = true)!!
        val stmts2 = result2.compilerAst.entrypoint.statements
        stmts2.size shouldBe 7
        val expr2 = (stmts2[5] as Assignment).value as BinaryExpression
        expr2.operator shouldBe "and"
        (expr2.left as IdentifierReference).nameInSource shouldBe listOf("bb2")  // no cast
        (expr2.right as IdentifierReference).nameInSource shouldBe listOf("bb3")  // no cast
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
        val result = compileText(C64Target(), true, text, outputDir, writeAssembly = true)!!
        val stmts = result.compilerAst.entrypoint.statements
        printProgram(result.compilerAst)
        /*
        bool @shared ub1
        ub1 = true
        bool @shared ub2
        ub2 = true
        bool @shared ub3
        ub3 = true
        bool @shared bvalue
        bvalue =  not ((ub1 xor ub2) xor ub3)
        bvalue = (((ub1 xor ub2) xor ub3) xor (ftrue(99)!=0))
        bvalue = ((ub1 and ub2) and (ftrue(99)!=0))
        bvalue = (((ub1 xor ub2) xor ub3) xor btrue(99))
        bvalue = ((ub1 and ub2) and btrue(99))
        return
         */
        stmts.size shouldBe 13
        val assignValue1 = (stmts[7] as Assignment).value as PrefixExpression
        val assignValue2 = (stmts[8] as Assignment).value as BinaryExpression
        val assignValue3 = (stmts[9] as Assignment).value as BinaryExpression
        val assignValue4 = (stmts[10] as Assignment).value as BinaryExpression
        val assignValue5 = (stmts[11] as Assignment).value as BinaryExpression
        assignValue1.operator shouldBe "not"
        assignValue2.operator shouldBe "xor"
        assignValue3.operator shouldBe "and"
        assignValue4.operator shouldBe "xor"
        assignValue5.operator shouldBe "and"
        val right2 = assignValue2.right as BinaryExpression
        val right3 = assignValue3.right as BinaryExpression
        right2.operator shouldBe "!="
        right3.operator shouldBe "!="
        right2.left shouldBe instanceOf<IFunctionCall>()
        right2.right shouldBe NumericLiteral(BaseDataType.UBYTE, 0.0, Position.DUMMY)
        right3.left shouldBe instanceOf<IFunctionCall>()
        right3.right shouldBe NumericLiteral(BaseDataType.UBYTE, 0.0, Position.DUMMY)
        assignValue4.right shouldBe instanceOf<IFunctionCall>()
        assignValue5.right shouldBe instanceOf<IFunctionCall>()
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
            
                    boolvalue1 = thing(true, false)
                    boolvalue2 = thing(false, true)
            
                    if boolvalue1 and boolvalue2
                        boolvalue1=false
                 }
            }"""
        val result = compileText(C64Target(), false, text, outputDir, writeAssembly = false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 7
        val fcall1 = ((stmts[4] as Assignment).value as IFunctionCall)
        fcall1.args[0] shouldBe NumericLiteral(BaseDataType.BOOL, 1.0, Position.DUMMY)
        fcall1.args[1] shouldBe NumericLiteral(BaseDataType.BOOL, 0.0, Position.DUMMY)
        val fcall2 = ((stmts[5] as Assignment).value as IFunctionCall)
        fcall2.args[0] shouldBe NumericLiteral(BaseDataType.BOOL, 0.0, Position.DUMMY)
        fcall2.args[1] shouldBe NumericLiteral(BaseDataType.BOOL, 1.0, Position.DUMMY)
        val ifCond = (stmts[6] as IfElse).condition as BinaryExpression
        ifCond.operator shouldBe "and" // no asm writing so logical expressions haven't been replaced with bitwise equivalents yet
        (ifCond.left as IdentifierReference).nameInSource shouldBe listOf("boolvalue1")
        (ifCond.right as IdentifierReference).nameInSource shouldBe listOf("boolvalue2")
    }

    test("correct evaluation of words in boolean expressions") {
        val text= """
            main {
                sub start() {
                    uword camg
                    bool @shared interlaced
                    interlaced = (camg & $0004) != 0
                    cx16.r0L++
                    interlaced = ($0004 & camg) != 0
                    cx16.r0L++
                    uword @shared ww
                    ww = (camg & $0004)
                    ww++
                    ww = ($0004 & camg)
                    ubyte @shared value
                    bool @shared collected = (value >= $33) or (value >= $66) or (value >= $99) or (value >= ${'$'}CC)
                }
            }"""
        val result = compileText(C64Target(), false, text, outputDir, writeAssembly = true)!!
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
        val result = compileText(C64Target(), false, text, outputDir, writeAssembly = false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 3
    }

    test("ubyte to word casts") {
        val src="""
main {
    sub start() {
        ubyte @shared bb = 255
        cx16.r0s = (bb as byte) as word     ; should result in -1 word value
        cx16.r1s = (bb as word)             ; should result in 255 word value
    }
}"""

        val result = compileText(C64Target(), true, src, outputDir, writeAssembly = false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 4
        val assign1tc = (stmts[2] as Assignment).value as TypecastExpression
        val assign2tc = (stmts[3] as Assignment).value as TypecastExpression
        assign1tc.type shouldBe BaseDataType.WORD
        assign2tc.type shouldBe BaseDataType.WORD
        assign2tc.expression shouldBe instanceOf<IdentifierReference>()
        val assign1subtc = (assign1tc.expression as TypecastExpression)
        assign1subtc.type shouldBe BaseDataType.BYTE
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
        val result = compileText(C64Target(), false, text, outputDir, writeAssembly = false)!!
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
        val result = compileText(C64Target(), false, text, outputDir, writeAssembly = true)!!
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
        compileText(C64Target(), false, text, outputDir, writeAssembly = true, errors=errors) shouldBe null
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
        compileText(C64Target(), false, text, outputDir, errors=errors) shouldBe null
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
        compileText(C64Target(), false, text, outputDir, errors=errors) shouldBe null
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
        compileText(C64Target(), false, text, outputDir, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "refused"
        errors.errors[1] shouldContain "refused"
    }

    test("correct implicit casts of signed number comparison and logical expressions") {
        val text = """
            %import floats
            
            main {
                sub start() {
                    byte @shared bb = -10
                    word @shared ww = -1000
                    
                    if bb>0 {
                        bb++
                    }
                    if bb < 0 {
                        bb ++
                    }
                    if bb & 1 !=0 {
                        bb++
                    }
                    if bb & 128 !=0 {
                        bb++
                    }
                    if bb & 255 !=0 {
                        bb++
                    }

                    if ww>0 {
                        ww++
                    }
                    if ww < 0 {
                        ww ++
                    }
                    if ww & 1 !=0 {
                        ww++
                    }
                    if ww & 32768 != 0 {
                        ww++
                    }
                    if ww & 65535 != 0 {
                        ww++
                    }
                }
            }
        """
        val result = compileText(C64Target(), false, text, outputDir, writeAssembly = true)!!
        val statements = result.compilerAst.entrypoint.statements
        statements.size shouldBeGreaterThan 10
    }

    test("cast to unsigned in conditional") {
        val text = """
            main {
                sub start() {
                    byte @shared bb
                    word @shared ww
            
                    bool @shared iteration_in_progress
                    uword @shared num_bytes

                    if not iteration_in_progress or num_bytes==0 {
                        num_bytes++
                    }
        
                    if bb as ubyte !=0  {
                        bb++
                    }
                    if ww as uword !=0 {
                        ww++
                    }
                }
            }"""
        val result = compileText(C64Target(), false, text, outputDir, writeAssembly = true)!!
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
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("(u)byte extend to word parameters") {
        val text = """
            main {
                sub start() {
                    byte @shared ub1 = -50
                    byte @shared ub2 = -51
                    byte @shared ub3 = -52
                    byte @shared ub4 = 100
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
        compileText(C64Target(), true, text, outputDir, writeAssembly = true) shouldNotBe null
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
        compileText(C64Target(), true, text, outputDir, writeAssembly = true) shouldNotBe null
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
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
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
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(C64Target(), true, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), true, text, outputDir, writeAssembly = true) shouldNotBe null
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
        compileText(C64Target(), false, text, outputDir, writeAssembly = false) shouldNotBe null
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
        compileText(C64Target(), false, text, outputDir, writeAssembly = false) shouldNotBe null
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
        compileText(C64Target(), false, text, outputDir, writeAssembly = false, errors=errors) shouldBe null
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
        thing(320*HEIGHT)       ; overflow
        large = 12345678        ; overflow
    }

    sub thing(uword value) {
        value++
    }
}"""
        val errors=ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 3
        errors.errors[0] shouldContain ":9:"
        errors.errors[0] shouldContain "no cast"
        errors.errors[1] shouldContain ":10:"
        errors.errors[1] shouldContain "out of range"
        errors.errors[2] shouldContain ":10:"
        errors.errors[2] shouldContain "doesn't match"
    }

    test("various bool typecasts and type mismatches") {
        val src="""
%option enable_floats

main {
    sub start() {

        float @shared fl
        ubyte @shared flags
        byte @shared flagss
        uword @shared flagsw
        word @shared flagssw
        bool @shared bflags = 123
        cx16.r0++
        bflags = 123
        cx16.r0++
        bflags = 123 as bool

        flags = bflags
        flagss = bflags
        flagsw = bflags
        flagssw = bflags
        fl = bflags
        bflags = flags
        bflags = flagss
        bflags = flagsw
        bflags = flagssw
        bflags = fl

        flags = bflags as ubyte
        flagss = bflags as byte
        flagsw = bflags as uword
        flagssw = bflags as word
        fl = bflags as float
        bflags = flags as bool
        bflags = flagss as bool
        bflags = flagsw as bool
        bflags = flagssw as bool
        bflags = fl as bool
    }
}"""
        val errors=ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 12
        errors.errors.all { "type of value" in it } shouldBe true
        errors.errors.all { "doesn't match" in it } shouldBe true
    }

    test("bool to byte cast in expression is not allowed") {
        val text="""
main {
    sub start() {
        ubyte[3] values
        func1(22 in values)
        func2(22 in values)
        ubyte @shared qq = 22 in values
        byte @shared ww = 22 in values
    }
    sub func1(ubyte arg) {
        arg++
    }
    sub func2(byte arg) {
        arg++
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, outputDir, writeAssembly = true, errors = errors) shouldBe null
        errors.errors.size shouldBe 4
        errors.errors[0] shouldContain("argument 1 type mismatch")
        errors.errors[1] shouldContain("argument 1 type mismatch")
        errors.errors[2] shouldContain("type of value bool doesn't match target")
        errors.errors[3] shouldContain("type of value bool doesn't match target")
    }

    test("bool function parameters correct typing") {
        val src = """
main {
    sub start() {
        bool bb = func(true)
        void func(true)
        ; all these should fail:
        void func(0)
        void func(1)
        void func(42)
        void func(65535)
        void func(655.444)
        cx16.r0L = func(true)
    }

    sub func(bool draw) -> bool {
        cx16.r0++
        return true
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 6
        errors.errors[0] shouldContain("type mismatch")
        errors.errors[1] shouldContain("type mismatch")
        errors.errors[2] shouldContain("type mismatch")
        errors.errors[3] shouldContain("type mismatch")
        errors.errors[4] shouldContain("type mismatch")
        errors.errors[5] shouldContain("type of value bool doesn't match target")
    }

    test("no implicit bool-to-int cast") {
        val src="""
main {
    sub start() {
        func(true)
        func(true as ubyte)
        cx16.r0L = true
        cx16.r0L = true as ubyte
    }

    sub func(bool b) {
        cx16.r0++
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain(":5:14: argument 1 type mismatch")
        errors.errors[1] shouldContain(":6:20: type of value bool doesn't match target")
    }

    test("no implicit int-to-bool cast") {
        val src="""
main {
    sub start() {
        func1(true)
        func2(true)
        func1(true as ubyte)
        func2(true as uword)
        bool @shared bb1 = 1
        bool @shared bb2 = 12345
        bool @shared bb3 = 1 as bool
        bool @shared bb4 = 12345 as bool
    }

    sub func1(ubyte ub) {
        cx16.r0++
    }

    sub func2(uword uw) {
        cx16.r0++
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 4
        errors.errors[0] shouldContain(":4:15: no implicit cast")
        errors.errors[1] shouldContain(":5:15: no implicit cast")
        errors.errors[2] shouldContain(":8:28: type of value ubyte doesn't match target")
        errors.errors[3] shouldContain(":9:28: type of value uword doesn't match target")
    }

    test("str replaced with uword in subroutine params and return types") {
        val src="""
main {
    sub start() {
        derp("hello")
        mult3("hello")
    }

    sub derp(str arg) -> str {
        cx16.r0++
        return arg
    }

    asmsub mult3(str input @XY) -> str @XY {
        %asm {{
            ldx  #100
            ldy  #101
            rts
        }}
    }
}"""
        compileText(C64Target(), true, src, outputDir, writeAssembly = true) shouldNotBe null
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val main = result.codegenAst!!.allBlocks().first()
        val derp = main.children.single { it is PtSub && it.name=="main.derp"} as PtSub
        derp.returns shouldBe listOf(DataType.forDt(BaseDataType.UWORD))
        derp.parameters.single().type shouldBe DataType.forDt(BaseDataType.UWORD)
        val mult3 = main.children.single { it is PtAsmSub && it.name=="main.mult3"} as PtAsmSub
        mult3.parameters.single().second.type shouldBe DataType.forDt(BaseDataType.UWORD)
        mult3.returns.single().second shouldBe DataType.forDt(BaseDataType.UWORD)
    }

    test("return 0 for str converted to uword") {
        val src="""
main {
    sub start() {
        cx16.r0 = test()
    }

    sub test() -> str {
        cx16.r0++
        if cx16.r0L==255
            return 0

        return 42
    }
}"""
        compileText(C64Target(), true, src, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("bool to word cast") {
        val src="""
main {
    sub start() {
        bool @shared flag, flag2
        cx16.r0L = (flag and flag2) as ubyte
        cx16.r0 = (flag and flag2) as uword
    }
}"""

        compileText(VMTarget(), false, src, outputDir, writeAssembly = true) shouldNotBe null
        compileText(C64Target(), false, src, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("undefined symbol error instead of type cast error") {
        val src="""
main {
    const ubyte foo = 0
    ubyte bar = 0
    sub start() {
        when foo {
            notdefined -> bar = 1
            else -> bar = 2
        }
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[1] shouldContain "undefined symbol"
    }

    test("return unsigned values for signed results ok if value fits") {
        val src = """
main {
    sub start() {
        void foo()
        void bar()
        void overflow1()
        void overflow2()
    }

    sub foo() -> byte {
        return 42
    }
    sub bar() -> word {
        return 12345
    }
    sub overflow1() -> byte {
        return 200
    }
    sub overflow2() -> word {
        return 44444
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "17:16: return value's type ubyte doesn't match subroutine's return type byte"
        errors.errors[1] shouldContain "20:16: return value's type uword doesn't match subroutine's return type word"
    }

    test("if-expression adjusts different value types to common type") {
        val src="""
main {
    sub start() {
        cx16.r0sL = if cx16.r0L < cx16.r1L -1 else 1
    }
}"""

        val result = compileText(C64Target(), false, src, outputDir, writeAssembly = false)!!
        val program = result.compilerAst
        val st = program.entrypoint.statements
        st.size shouldBe 1
        val assign = st[0] as Assignment
        assign.target.inferType(program).getOrUndef().base shouldBe BaseDataType.BYTE
        val ifexpr = assign.value as IfExpression
        ifexpr.truevalue.inferType(program).getOrUndef().base shouldBe BaseDataType.BYTE
        ifexpr.falsevalue.inferType(program).getOrUndef().base shouldBe BaseDataType.BYTE
        ifexpr.truevalue shouldBe instanceOf<NumericLiteral>()
        ifexpr.falsevalue shouldBe instanceOf<NumericLiteral>()
    }

    test("correct data types of numeric literals in word/byte scenario") {
        val src = """
main {
    sub start() {
        const uword WIDTH = 40
        const uword WIDER = 400
        cx16.r0 = cx16.r0-1+WIDTH
        cx16.r0 = cx16.r0-1+WIDER 
        cx16.r0 = cx16.r0L * 5               ; byte multiplication
        cx16.r0 = cx16.r0L * $0005      ; word multiplication
    }
}"""
        val result = compileText(C64Target(), false, src, outputDir, writeAssembly = false)!!
        val program = result.compilerAst
        val st = program.entrypoint.statements
        st.size shouldBe 6
        val v1 = (st[2] as Assignment).value as BinaryExpression
        v1.operator shouldBe "+"
        (v1.left as IdentifierReference).nameInSource shouldBe listOf("cx16","r0")
        (v1.right as NumericLiteral).type shouldBe BaseDataType.UWORD
        (v1.right as NumericLiteral).number shouldBe 39

        val v2 = (st[3] as Assignment).value as BinaryExpression
        v2.operator shouldBe "+"
        (v2.left as IdentifierReference).nameInSource shouldBe listOf("cx16","r0")
        (v2.right as NumericLiteral).type shouldBe BaseDataType.UWORD
        (v2.right as NumericLiteral).number shouldBe 399

        val v3 = (st[4] as Assignment).value as TypecastExpression
        v3.type shouldBe BaseDataType.UWORD
        val v3e = v3.expression as BinaryExpression
        v3e.operator shouldBe "*"
        (v3e.left as IdentifierReference).nameInSource shouldBe listOf("cx16","r0L")
        (v3e.right as NumericLiteral).type shouldBe BaseDataType.UBYTE
        (v3e.right as NumericLiteral).number shouldBe 5

        val v4 = (st[5] as Assignment).value as BinaryExpression
        v4.operator shouldBe "*"
        val v4t = v4.left as TypecastExpression
        v4t.type shouldBe BaseDataType.UWORD
        (v4t.expression as IdentifierReference).nameInSource shouldBe listOf("cx16","r0L")
        (v4.right as NumericLiteral).type shouldBe BaseDataType.UWORD
        (v4.right as NumericLiteral).number shouldBe 5
    }

    test("allow comparisons against constant values with different type") {
        val src = """
main {
    sub start() {
        const uword MAX_CAVE_WIDTH = 440             ; word here to avoid having to cast to word all the time

        if cx16.r0L > MAX_CAVE_WIDTH
            return
    }
}"""
        compileText(C64Target(), false, src, outputDir, writeAssembly = false) shouldNotBe null
    }
})
