package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import prog8.code.target.Cx16Target
import prog8tests.helpers.compileText
import prog8tests.helpers.simulate
import java.nio.file.Files

/**
 * These tests execute compiled 6502 code in the ksim65 simulator to verify that
 * in-place modifications (augmented assignments) on struct fields reached via
 * a pointer are correct, especially when the pointer variable is *not* located
 * in the zeropage (so its effective address must live in the shared scratch
 * pointer that evaluation of the source expression may clobber).
 */
class TestPointerFieldAugmentedAssignment6502 : FunSpec({
    val outputDir = Files.createTempDirectory("prog8test")

    test("word field += via expression source, pointer not in zeropage") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000
            %zeropage dontuse

            main {
                struct Node {
                    uword size
                    ^^Node next
                }
                &ubyte poweroff = $f203
                &uword psize = $02
                &uword qsize = $04
                sub start() {
                    ^^Node p = ^^Node: [0, 0]
                    ^^Node q = ^^Node: [0, 0]
                    p.size = 100
                    p.next = q
                    q.size = 200
                    q.next = 0
                    p.size += p.next.size
                    psize = p.size
                    qsize = q.size
                    poweroff = 1
                }
            }
        """.trimIndent()

        val compileResult = compileText(Cx16Target(), false, src, outputDir)
        val machine = compileResult!!.simulate()
        machine.assertMemory(0x02, 0x2c)
        machine.assertMemory(0x03, 0x01)   // p.size = 300
        machine.assertMemory(0x04, 0xc8)
        machine.assertMemory(0x05, 0x00)   // q.size = 200 (unchanged)
    }

    test("multi-element pointer chain as target, pointer not in zeropage") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000
            %zeropage dontuse

            main {
                struct Node {
                    uword size
                    uword val
                    ^^Node next
                }
                &ubyte poweroff = $f203
                &uword qsize = $02
                sub start() {
                    ^^Node p = ^^Node: [0, 0, 0]
                    ^^Node q = ^^Node: [0, 0, 0]
                    p.size = 100
                    p.val = 25
                    p.next = q
                    q.size = 400
                    p.next.size += p.val
                    qsize = q.size
                    poweroff = 1
                }
            }
        """.trimIndent()

        val compileResult = compileText(Cx16Target(), false, src, outputDir)
        val machine = compileResult!!.simulate()
        machine.assertMemory(0x02, 0xa9)
        machine.assertMemory(0x03, 0x01)   // q.size = 425
    }

    test("byte field += via expression source, pointer not in zeropage") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000
            %zeropage dontuse

            main {
                struct Node {
                    ubyte b
                    ^^Node next
                }
                &ubyte poweroff = $f203
                &ubyte pb = $02
                &ubyte qb = $03
                sub start() {
                    ^^Node p = ^^Node: [0, 0]
                    ^^Node q = ^^Node: [0, 0]
                    p.b = 10
                    p.next = q
                    q.b = 15
                    p.b += q.b
                    pb = p.b
                    qb = q.b
                    poweroff = 1
                }
            }
        """.trimIndent()

        val compileResult = compileText(Cx16Target(), false, src, outputDir)
        val machine = compileResult!!.simulate()
        machine.assertMemory(0x02, 25)   // p.b = 25
        machine.assertMemory(0x03, 15)   // q.b = 15 (unchanged)
    }

    test("multi-element chain target with zeropage base pointer") {
        // regression: derefUsesScratchPointer returned false for chain.size>1 with a
        // zeropage base pointer, so the source expression could clobber the target address.
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                struct Node {
                    uword size
                    ^^Node next
                }
                &ubyte poweroff = $f203
                &uword qsize = $02
                ^^Node p = ^^Node: [0, 0]
                ^^Node q = ^^Node: [0, 0]
                ^^Node r = ^^Node: [0, 0]
                &^^Node rptr = $2000
                sub start() {
                    rptr = r
                    p.next = q
                    q.size = 200
                    r.size = 50
                    p.next.size += rptr.size
                    qsize = q.size
                    poweroff = 1
                }
            }
        """.trimIndent()

        val compileResult = compileText(Cx16Target(), false, src, outputDir)
        val machine = compileResult!!.simulate()
        machine.assertMemory(0x02, 0xfa)
        machine.assertMemory(0x03, 0x00)   // q.size = 250
    }

    test("word field /= and %= via expression source, pointer not in zeropage") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000
            %zeropage dontuse

            main {
                struct Node {
                    uword size
                    uword val
                    ^^Node next
                }
                &ubyte poweroff = $f203
                &uword psize = $02
                &uword pval = $04
                sub start() {
                    ^^Node p = ^^Node: [0, 0, 0]
                    ^^Node q = ^^Node: [0, 0, 0]
                    p.size = 795
                    p.val = 13
                    p.next = q
                    q.size = 200
                    p.size /= q.size
                    p.size += q.size
                    p.size %= p.val
                    psize = p.size
                    pval = p.val
                    poweroff = 1
                }
            }
        """.trimIndent()

        val compileResult = compileText(Cx16Target(), false, src, outputDir)
        val machine = compileResult!!.simulate()
        machine.assertMemory(0x02, 8)
        machine.assertMemory(0x03, 0)      // (795/200=3, +200=203, %13=8)
        machine.assertMemory(0x04, 13)
        machine.assertMemory(0x05, 0)      // p.val unchanged
    }
})