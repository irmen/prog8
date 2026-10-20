package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.core.OutputFiles
import prog8.code.target.Amiga1200Target
import prog8.code.target.Amiga500Target
import prog8.code.target.Qemu68kTarget
import prog8tests.helpers.assumeDirectory
import prog8tests.helpers.compileFile
import prog8tests.helpers.workingDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Golden (byte-identical) assembly harness for the m68k codegen backend.
 *
 * With an empty regAllocation map (Stage 1 checkpoint) the generated .asm must
 * match the checked-in golden files byte-for-byte. Once Stage 2 populates the
 * map, any diff here is a regression signal.
 *
 * Regenerate goldens with:  -Dm68kGoldenUpdate=true  (or env UPDATE_M68K_GOLDEN=1)
 */
class TestM68kGoldenAsm : FunSpec({

    val updateGolden = System.getProperty("m68kGoldenUpdate") == "true" ||
        System.getenv("UPDATE_M68K_GOLDEN") == "1"

    val examplesDir = assumeDirectory(workingDir, "../examples")

    fun goldenPath(target: String, name: String, optimize: Boolean): Path {
        val dir = workingDir.resolve("test/golden/m68k/$target")
        dir.createDirectories()
        val suffix = if (optimize) "opt" else "noopt"
        return dir.resolve("$name-$suffix.asm")
    }

    fun checkExample(targetName: String, target: prog8.code.core.ICompilationTarget,
                     exampleRelDir: String, name: String, optimize: Boolean) {
        val srcDir = assumeDirectory(examplesDir, exampleRelDir)
        val outDir = tempdir().toPath()
        val result = compileFile(target, optimize, srcDir, "$name.p8", outDir,
            writeAssembly = true, assemble = false)
        result shouldNotBe null
        val asmFile = OutputFiles.of(result!!.compilationOptions, result.compilerAst.name).asm()
        val asm = asmFile.readText()
        val gp = goldenPath(targetName, name, optimize)
        if (updateGolden) {
            gp.writeText(asm)
        } else {
            gp.exists() shouldBe true
            val golden = gp.readText()
            if (asm != golden) {
                val actual = gp.resolveSibling("$name-${if (optimize) "opt" else "noopt"}.actual.asm")
                actual.writeText(asm)
            }
            asm shouldBe golden
        }
    }

    // --- qemu68k examples (68020+) ---
    listOf("maze", "textelite", "qemubootinfo").forEach { name ->
        test("qemu68k example $name (optimized)") {
            checkExample("qemu68k", Qemu68kTarget(), "qemu68k", name, true)
        }
        test("qemu68k example $name (unoptimized)") {
            checkExample("qemu68k", Qemu68kTarget(), "qemu68k", name, false)
        }
    }

    // --- amiga examples (curated) for both amiga500 and amiga1200 ---
    val amigaExamples = listOf("3d", "bdmusic", "circles", "fractal-tree", "gfxdemo",
        "iff", "modplay", "rasters", "tictactoe", "window", "window3d", "screen", "lowlevel-timer")
    amigaExamples.forEach { name ->
        listOf(Amiga500Target() to "amiga500", Amiga1200Target() to "amiga1200").forEach { (tgt, tname) ->
            test("$tname example $name (optimized)") {
                checkExample(tname, tgt, "amiga", name, true)
            }
            test("$tname example $name (unoptimized)") {
                checkExample(tname, tgt, "amiga", name, false)
            }
        }
    }

    // --- top-level examples that compile on m68k targets ---
    listOf("fibonacci", "numbergame", "primes", "screencodes").forEach { name ->
        listOf(Qemu68kTarget() to "qemu68k",
               Amiga500Target() to "amiga500",
               Amiga1200Target() to "amiga1200").forEach { (tgt, tname) ->
            test("$tname toplevel $name (optimized)") {
                checkExample(tname, tgt, "", name, true)
            }
        }
    }
})
