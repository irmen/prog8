package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.target.Amiga500Target
import prog8.code.target.C64Target
import prog8.code.target.Qemu68kTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import kotlin.io.path.readText


class TestAmigaChipramOption: FunSpec({

    test("amiga_chipram option on amiga500 block sets AMIGACHIPRAM in codegen AST") {
        val src = """
chipper {
    %option amiga_chipram

    ubyte @shared init_var = 42
    ubyte @shared uninit_var
    long @shared slab_ptr = memory("chiper_slab", 16, 4)

    sub chipram_sub() {
        init_var++
    }
}

main {
    sub start() {
        chipper.chipram_sub()
    }
}
"""
        val result = compileText(Amiga500Target(), optimize = false, src, tempdir().toPath(), writeAssembly = true)
        result shouldNotBe null
        val blocks = result!!.codegenAst!!.allBlocks().toList()
        val chipramBlock = blocks.singleOrNull { it.options.amigaChipram }
        chipramBlock shouldNotBe null
        // the non-chipram blocks must not have the option set
        blocks.filter { it !== chipramBlock }.forEach {
            it.options.amigaChipram shouldBe false
        }
    }

    test("amiga_chipram option on amiga500 emits AMIGACHIPRAM attribute in .p8ir file") {
        val src = """
chipper {
    %option amiga_chipram

    ubyte @shared init_var = 42
    ubyte @shared uninit_var
    long @shared slab_ptr = memory("chiper_slab", 16, 4)

    sub chipram_sub() {
        init_var++
    }
}

main {
    sub start() {
        chipper.chipram_sub()
    }
}
"""
        val result = compileText(Amiga500Target(), optimize = false, src, tempdir().toPath(), writeAssembly = true)
        result shouldNotBe null
        val irFile = result!!.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irText = irFile.readText()
        irText shouldContain "AMIGACHIPRAM=\"true\""
    }

    test("amiga_chipram option on amiga500 produces chip ram section directives in assembly") {
        val src = """
chipper {
    %option amiga_chipram

    ubyte @shared init_var = 42
    ubyte @shared uninit_var
    long @shared slab_ptr = memory("chiper_slab", 16, 4)

    sub chipram_sub() {
        init_var++
    }
}

main {
    sub start() {
        chipper.chipram_sub()
    }
}
"""
        val result = compileText(Amiga500Target(), optimize = false, src, tempdir().toPath(), writeAssembly = true)
        result shouldNotBe null
        val asmFile = result!!.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm")
        val lines = asmFile.readText().lines().map { it.trim() }
        lines.any { it.contains("SECTION code_c", ignoreCase = true) } shouldBe true
        lines.any { it.contains("SECTION data_c", ignoreCase = true) } shouldBe true
        lines.any { it.contains("SECTION bss_c", ignoreCase = true) } shouldBe true
    }

    test("amiga_chipram option at module level is rejected") {
        val src = """
%option amiga_chipram

main {
    sub start() {
        ; nothing
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Amiga500Target(), optimize = false, src, tempdir().toPath(), writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "option that is not valid for modules"
    }

    test("amiga_chipram option on c64 target is rejected") {
        val src = """
chipper {
    %option amiga_chipram

    sub chipram_sub() {
        cx16.r0++
    }
}

main {
    sub start() {
        chipper.chipram_sub()
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), optimize = false, src, tempdir().toPath(), writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "amiga_chipram option is only valid on amiga500 target"
    }

    test("amiga_chipram option on qemu68k target is rejected") {
        val src = """
chipper {
    %option amiga_chipram

    sub chipram_sub() {
        ; nothing
    }
}

main {
    sub start() {
        chipper.chipram_sub()
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(Qemu68kTarget(), optimize = false, src, tempdir().toPath(), writeAssembly = false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "amiga_chipram option is only valid on amiga500 target"
    }
})
