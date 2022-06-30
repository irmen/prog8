package prog8tests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.C64Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText


class TestTypecasts: FunSpec({

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
                }
            }
        """
        val result = compileText(C64Target(), false, text, writeAssembly = true)!!
        result.program.entrypoint.statements.size shouldBe 13
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
        statements.size shouldBe 27
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
