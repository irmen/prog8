package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import prog8.code.target.Cx16Target
import prog8tests.helpers.compileText
import prog8tests.helpers.simulate
import razorvine.ksim65.testing.TestMachine

class TestConstStepForLoops6502 : FunSpec({
    val outputDir = tempdir().toPath()

    fun run(source: String) = compileText(Cx16Target(), false, source.trimIndent(), outputDir)!!.simulate()

    fun assertWord(machine: TestMachine, address: Int, value: Int) {
        machine.assertMemory(address, value and 0xff)
        machine.assertMemory(address + 1, (value shr 8) and 0xff)
    }

    fun assertLong(machine: TestMachine, address: Int, value: Long) {
        machine.assertMemory(address, (value and 0xffL).toInt())
        machine.assertMemory(address + 1, ((value shr 8) and 0xffL).toInt())
        machine.assertMemory(address + 2, ((value shr 16) and 0xffL).toInt())
        machine.assertMemory(address + 3, ((value shr 24) and 0xffL).toInt())
    }

    test("word single iteration ascending") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                &word result_sum = $0202
                sub start() {
                    word i
                    uword count = 0
                    word sum = 0
                    for i in 42 to 42 {
                        count++
                        sum += i
                    }
                    result_count = count
                    result_sum = sum
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 1)
        assertWord(machine, 0x0202, 42)
    }

    test("word single iteration descending") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                &word result_sum = $0202
                sub start() {
                    word i
                    uword count = 0
                    word sum = 0
                    for i in 42 downto 42 {
                        count++
                        sum += i
                    }
                    result_count = count
                    result_sum = sum
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 1)
        assertWord(machine, 0x0202, 42)
    }

    test("uword single iteration ascending") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                &uword result_sum = $0202
                sub start() {
                    uword i
                    uword count = 0
                    uword sum = 0
                    for i in 1000 to 1000 {
                        count++
                        sum += i
                    }
                    result_count = count
                    result_sum = sum
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 1)
        assertWord(machine, 0x0202, 1000)
    }

    test("uword ascending low byte wrap") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                &uword result_sum = $0202
                sub start() {
                    uword i
                    uword count = 0
                    uword sum = 0
                    for i in 250 to 260 {
                        count++
                        sum += i
                    }
                    result_count = count
                    result_sum = sum
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 11)
        assertWord(machine, 0x0202, 2805)
    }

    test("uword ascending to 65535") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                sub start() {
                    uword i
                    uword count = 0
                    for i in 65530 to 65535 {
                        count++
                    }
                    result_count = count
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 6)
    }

    test("uword descending to 0") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                sub start() {
                    uword i
                    uword count = 0
                    for i in 5 downto 0 {
                        count++
                    }
                    result_count = count
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 6)
    }

    test("uword descending last not zero") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                sub start() {
                    uword i
                    uword count = 0
                    for i in 65535 downto 65530 {
                        count++
                    }
                    result_count = count
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 6)
    }

    test("word ascending to 32767") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                sub start() {
                    word i
                    uword count = 0
                    for i in 32765 to 32767 {
                        count++
                    }
                    result_count = count
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 3)
    }

    test("word descending to -32768") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                sub start() {
                    word i
                    uword count = 0
                    for i in -32766 downto -32768 {
                        count++
                    }
                    result_count = count
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 3)
    }

    test("word descending last not zero") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                sub start() {
                    word i
                    uword count = 0
                    for i in 10 downto 1 {
                        count++
                    }
                    result_count = count
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 10)
    }

    test("signed word crossing zero ascending") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                &word result_sum = $0202
                sub start() {
                    word i
                    uword count = 0
                    word sum = 0
                    for i in -2 to 2 {
                        count++
                        sum += i
                    }
                    result_count = count
                    result_sum = sum
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 5)
        assertWord(machine, 0x0202, 0)
    }

    test("signed word crossing zero descending") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                &word result_sum = $0202
                sub start() {
                    word i
                    uword count = 0
                    word sum = 0
                    for i in 2 downto -2 {
                        count++
                        sum += i
                    }
                    result_count = count
                    result_sum = sum
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 5)
        assertWord(machine, 0x0202, 0)
    }

    test("long single iteration ascending") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                &long result_sum = $0202
                sub start() {
                    long i
                    uword count = 0
                    long sum = 0
                    for i in 12345 to 12345 {
                        count++
                        sum += i
                    }
                    result_count = count
                    result_sum = sum
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 1)
        assertLong(machine, 0x0202, 12345L)
    }

    test("long ascending to 2147483647") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                sub start() {
                    long i
                    uword count = 0
                    for i in 2147483645 to 2147483647 {
                        count++
                    }
                    result_count = count
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 3)
    }

    test("long descending to -2147483648") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                sub start() {
                    long i
                    uword count = 0
                    for i in -2147483646 downto -2147483648 {
                        count++
                    }
                    result_count = count
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 3)
    }

    test("long descending to 0") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                sub start() {
                    long i
                    uword count = 0
                    for i in 5 downto 0 {
                        count++
                    }
                    result_count = count
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 6)
    }

    test("long low byte wrap") {
        val machine = run($$"""
            %option no_sysinit
            %launcher none
            %address $1000

            main {
                &ubyte poweroff = $f203
                &uword result_count = $0200
                sub start() {
                    long i
                    uword count = 0
                    for i in $123455fe to $12345602 {
                        count++
                    }
                    result_count = count
                    poweroff = 1
                }
            }
        """)
        assertWord(machine, 0x0200, 5)
    }
})
