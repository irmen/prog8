package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import prog8.code.target.Cx16Target
import prog8tests.helpers.compileText
import prog8tests.helpers.simulate

class TestArena6502 : FunSpec({
    val outputDir = tempdir().toPath()

    fun run(source: String) = compileText(Cx16Target(), false, source.trimIndent(), outputDir)!!.simulate()

    test("basic alloc and reset") {
        val machine = run($$"""
            %import arena
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_diff_p2_p1 = $0200
                &uword result_diff_p3_p1 = $0202
                sub start() {
                    arena.init(memory("testarena", 256, 0), 256)
                    pointer p1 = arena.alloc(10)
                    pointer p2 = arena.alloc(20)
                    result_diff_p2_p1 = (p2 as uword) - (p1 as uword)
                    arena.reset()
                    pointer p3 = arena.alloc(30)
                    result_diff_p3_p1 = (p3 as uword) - (p1 as uword)
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x0200, 10)
        machine.assertMemory(0x0201, 0)
        machine.assertMemory(0x0202, 0)
        machine.assertMemory(0x0203, 0)
    }

    test("odd size is not aligned on 6502") {
        val machine = run($$"""
            %import arena
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_diff = $0200
                sub start() {
                    arena.init(memory("testarena", 256, 0), 256)
                    pointer p1 = arena.alloc(5)
                    pointer p2 = arena.alloc(1)
                    result_diff = (p2 as uword) - (p1 as uword)
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x0200, 5)
        machine.assertMemory(0x0201, 0)
    }

    test("overflow returns zero") {
        val machine = run($$"""
            %import arena
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_ok = $0200
                &uword result_overflow = $0202
                sub start() {
                    arena.init(memory("testarena", 32, 0), 32)
                    pointer ok = arena.alloc(16)
                    pointer overflow = arena.alloc(20)
                    result_ok = ok as uword
                    result_overflow = overflow as uword
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x0202, 0)
        machine.assertMemory(0x0203, 0)
    }

    test("zero size alloc returns zero") {
        val machine = run($$"""
            %import arena
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_zero = $0200
                sub start() {
                    arena.init(memory("testarena", 32, 0), 32)
                    pointer zero = arena.alloc(0)
                    result_zero = zero as uword
                    poweroff = 1
                }
            }
        """)
        machine.assertMemory(0x0200, 0)
        machine.assertMemory(0x0201, 0)
    }
})
