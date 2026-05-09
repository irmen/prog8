package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.Assignment
import prog8.code.target.Cx16Target
import prog8tests.helpers.compileText
import prog8tests.helpers.simulate
import java.nio.file.Files

class TestPointerArithmeticScaling : FunSpec({
    val outputDir = Files.createTempDirectory("prog8test_ptr_scaling")

    test("const pointer arithmetic scaling - ubyte (scale 1)") {
        val src = $$"""
            %option no_sysinit
            main {
                const ^^ubyte p = $1000
                sub start() {
                    cx16.r0 = p + 5     ; should be $1005
                    cx16.r1 = p - 2     ; should be $1000 - 2 = $0ffe
                    cx16.r2 = &p[10]    ; should be $100a
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), true, src, outputDir)!!
        val startSub = result.compilerAst.entrypoint
        val assignments = startSub.statements.filterIsInstance<Assignment>()
        
        (assignments[0].value as NumericLiteral).number.toInt() shouldBe 0x1005
        (assignments[1].value as NumericLiteral).number.toInt() shouldBe 0x0ffe
        (assignments[2].value as NumericLiteral).number.toInt() shouldBe 0x100a
    }

    test("non-const pointer arithmetic scaling - ubyte (scale 1)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000
            main {
                &ubyte poweroff = $f203
                ^^ubyte @shared p = $2000
                uword @shared i = 5
                
                &ubyte res1 = $02
                &ubyte res2 = $03
                
                sub start() {
                    @(p + i) = 42
                    res1 = p[i]
                    res2 = @(p + i)
                    
                    poweroff = 1
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), true, src, outputDir)!!
        val machine = result.simulate()
        machine.assertMemory(0x02, 42)
        machine.assertMemory(0x03, 42)
        machine.assertMemory(0x2005, 42)
    }

    test("non-const pointer arithmetic scaling - uword (scale 2)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000
            main {
                &ubyte poweroff = $f203
                ^^uword @shared p = $2000
                uword @shared i = 5
                
                &uword res1 = $02
                &uword res2 = $04
                &uword res3 = $06
                
                sub start() {
                    res1 = p + i        ; 2000 + 5*2 = 200a
                    res2 = p - 2        ; 2000 - 2*2 = 1ffc
                    res3 = &p[i]        ; 2000 + 5*2 = 200a
                    
                    poweroff = 1
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), true, src, outputDir)!!
        val machine = result.simulate()
        machine.assertMemory(0x02, 0x0a)
        machine.assertMemory(0x03, 0x20)
        machine.assertMemory(0x04, 0xfc)
        machine.assertMemory(0x05, 0x1f)
        machine.assertMemory(0x06, 0x0a)
        machine.assertMemory(0x07, 0x20)
    }

    test("non-const pointer arithmetic scaling - float (scale 5)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000
            main {
                &ubyte poweroff = $f203
                ^^float @shared p = $2000
                uword @shared i = 3
                
                &uword res1 = $02
                &uword res2 = $04
                &uword res3 = $06
                
                sub start() {
                    res1 = p + i        ; 2000 + 3*5 = 200f
                    res2 = p - 1        ; 2000 - 1*5 = 1ffb
                    res3 = &p[i]        ; 2000 + 3*5 = 200f
                    
                    poweroff = 1
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), true, src, outputDir)!!
        val machine = result.simulate()
        machine.assertMemory(0x02, 0x0f)
        machine.assertMemory(0x03, 0x20)
        machine.assertMemory(0x04, 0xfb)
        machine.assertMemory(0x05, 0x1f)
        machine.assertMemory(0x06, 0x0f)
        machine.assertMemory(0x07, 0x20)
    }
})
