package prog8tests.vm

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.target.*
import prog8.vm.VmRunner
import prog8tests.helpers.CapturingSerialIO
import prog8tests.helpers.compileText
import prog8tests.helpers.simulate
import java.io.ByteArrayOutputStream
import java.io.PrintStream


class TestRng: FunSpec({

    val src = $$"""
%import textio

galaxy {
    const uword base0 = $5A4A
    const uword base1 = $0248
    const uword base2 = $B753
    uword[3] seed

    sub tweakseed() {
        uword temp = seed[0] + seed[1] + seed[2]
        seed[0] = seed[1]
        seed[1] = seed[2]
        seed[2] = temp
    }

    sub twist(uword x) -> uword {
        ubyte xh = msb(x)
        ubyte xl = lsb(x)
        xh <<= 1
        rol(xl)
        return mkword(xh, xl)
    }
}

main {
    sub start() {
        txt.lowercase()
        txt.print("twist() rng test\n")

        uword[3] test_seeds = [$5A4A, $0248, $B753]
        ubyte si
        for si in 0 to 2 {
            txt.print("seed ")
            txt.print_uw(test_seeds[si])
            txt.print(":")
            uword x = test_seeds[si]
            ubyte i
            for i in 0 to 5 {
                txt.spc()
                txt.print_uwhex(galaxy.twist(x), true)
                x = galaxy.twist(x)
            }
            txt.nl()
        }

        txt.print("\ntweakseed test\n")
        galaxy.seed[0] = $5A4A
        galaxy.seed[1] = $0248
        galaxy.seed[2] = $B753
        ubyte j
        for j in 0 to 9 {
            txt.print_uwhex(galaxy.seed[0], true)
            txt.spc()
            txt.print_uwhex(galaxy.seed[1], true)
            txt.spc()
            txt.print_uwhex(galaxy.seed[2], true)
            txt.nl()
            galaxy.tweakseed()
        }
    }
}
"""

    // Expected seed values: seeds for twist (3 seeds, 6 iterations each) and tweakseed (10 iterations, 3 values each)
    val expectedOutput =
        "twist() rng test\n" +
        "seed 23114: \$b494 \$6829 \$d052 \$a0a5 \$404b \$8096\n" +
        "seed 584: \$0490 \$0820 \$1040 \$2080 \$4000 \$8000\n" +
        "seed 46931: \$6ea7 \$dc4e \$b89d \$703b \$e076 \$c0ed\n" +
        "\ntweakseed test\n" +
        "\$5a4a \$0248 \$b753\n" +
        "\$0248 \$b753 \$13e5\n" +
        "\$b753 \$13e5 \$cd80\n" +
        "\$13e5 \$cd80 \$98b8\n" +
        "\$cd80 \$98b8 \$7a1d\n" +
        "\$98b8 \$7a1d \$e055\n" +
        "\$7a1d \$e055 \$f32a\n" +
        "\$e055 \$f32a \$4d9c\n" +
        "\$f32a \$4d9c \$211b\n" +
        "\$4d9c \$211b \$61e1\n"

    test("rngtest runs on cx16 simulator and produces correct output") {
        val cx16src = $$"""
%option no_sysinit
%launcher none
%address $1000
%encoding iso

galaxy {
    uword[3] seed

    sub tweakseed() {
        uword temp = seed[0] + seed[1] + seed[2]
        seed[0] = seed[1]
        seed[1] = seed[2]
        seed[2] = temp
    }

    sub twist(uword x) -> uword {
        ubyte xh = msb(x)
        ubyte xl = lsb(x)
        xh <<= 1
        rol(xl)
        return mkword(xh, xl)
    }
}

main {
    &ubyte serial_out = $f201
    &ubyte poweroff = $f203

    sub print(str s) {
        repeat {
            ubyte ch = @(s)
            if ch==0
                return
            serial_out = ch
            s++
        }
    }

    sub print_uw(uword value) {
        ubyte[5] digits
        ubyte count = 0
        repeat {
            ubyte digit = lsb(value % 10)
            digits[count] = digit
            count++
            value /= 10
            if value==0
                break
        }
        repeat count {
            count--
            serial_out = digits[count] + '0'
        }
    }

    sub print_uwhex(uword value, bool prefix) {
        if prefix {
            serial_out = '$'
        }
        ubyte nibble
        nibble = msb(value) >> 4 & 15
        serial_out = nibble + '0' + 39 * ((nibble > 9) as ubyte)
        nibble = msb(value) & 15
        serial_out = nibble + '0' + 39 * ((nibble > 9) as ubyte)
        nibble = lsb(value) >> 4 & 15
        serial_out = nibble + '0' + 39 * ((nibble > 9) as ubyte)
        nibble = lsb(value) & 15
        serial_out = nibble + '0' + 39 * ((nibble > 9) as ubyte)
    }

    sub nl() { serial_out = 10 }
    sub spc() { serial_out = ' ' }

    sub start() {
        print("twist() rng test")
        nl()

        uword[3] test_seeds = [$5A4A, $0248, $B753]
        ubyte si
        for si in 0 to 2 {
            print("seed ")
            print_uw(test_seeds[si])
            print(":")
            uword x = test_seeds[si]
            ubyte i
            for i in 0 to 5 {
                spc()
                print_uwhex(galaxy.twist(x), true)
                x = galaxy.twist(x)
            }
            nl()
        }

        nl()
        print("tweakseed test")
        nl()
        galaxy.seed[0] = $5A4A
        galaxy.seed[1] = $0248
        galaxy.seed[2] = $B753
        ubyte j
        for j in 0 to 9 {
            print_uwhex(galaxy.seed[0], true)
            spc()
            print_uwhex(galaxy.seed[1], true)
            spc()
            print_uwhex(galaxy.seed[2], true)
            nl()
            galaxy.tweakseed()
        }
        poweroff = 1
    }
}
"""
        val outputDir = tempdir().toPath()
        val result = compileText(Cx16Target(), false, cx16src, outputDir)!!
        val serialIO = CapturingSerialIO()
        result.simulate(maxCycles = 10_000_000, serialAndPower = serialIO)
        serialIO.assertOutput(expectedOutput)
    }

    test("rngtest compiles for qemu68k") {
        val outputDir = tempdir().toPath()
        val result = compileText(Qemu68kTarget(), true, src, outputDir, writeAssembly=false)
        result shouldNotBe null
        
        // TODO somehow simulate this program too to check m68k runtime correctness
    }

    test("rngtest runs on virtual and produces correct output") {
        val outputDir = tempdir().toPath()
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly=true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir").toFile()

        val stdout = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(stdout))
        try {
            VmRunner().runProgram(virtfile.readText(), false)
        } finally {
            System.setOut(originalOut)
        }

        val output = stdout.toString().replace("\r\n", "\n")
        val relevant = output.substringBefore("\nProgram exit!")
        relevant shouldBe expectedOutput
    }
})
