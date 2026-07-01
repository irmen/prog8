package codegen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.*
import prog8.code.target.Cx16Target
import prog8.intermediate.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Regression tests for the BSS section / regfile label placement in the 6502 code generator.
 *
 * The bug being guarded against: a label emitted IMMEDIATELY BEFORE a `.section` directive
 * in 64tass does NOT resolve to the start of the section data - it resolves to the current
 * "phantom" address in the main flow. The data that actually gets placed into the section
 * starts at a different address (after whatever other content is in the section).
 *
 * In the variable-size regfile layout, the `p8_regfile:` label was emitted before the
 * `.section BSS_NOCLEAR` directive, while dirty variables in the SAME section preceded the
 * `.fill` data. This caused `p8_regfile+0` to alias the first dirty variable
 * (`_orig_irqvec` LSB), corrupting the IRQ vector at runtime.
 *
 * The fix: the regfile label and `.fill` data must be inside the same `.section` block
 * as (and after) the dirty variables.
 */
class TestRegfileSection : FunSpec({

    fun buildTestProgram(
        vars: List<IRStStaticVariable>,
        instructions: List<IRInstruction>
    ): Pair<IRProgram, ICompilationTarget> {
        val target = Cx16Target()
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.FLOATSAFE)
            .floats(false)
            .compilerVersion("test")
            .memtopAddress(0xffffu)
            .build()

        val st = IRSymbolTable()
        for (v in vars) {
            st.add(v)
        }

        val program = IRProgram("test", st, options, DummyStringEncoder)

        if (instructions.isNotEmpty()) {
            val chunk = IRCodeChunk(null, null)
            chunk.instructions.addAll(instructions)
            val sub = IRSubroutine("test.start", emptyList(), emptyList(), Position.DUMMY)
            sub.chunks.add(chunk)
            val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
            block.children.add(sub)
            program.blocks.add(block)
        }

        return Pair(program, target)
    }

    fun makeDirtyVar(name: String, dt: DataType): IRStStaticVariable =
        IRStStaticVariable(name, dt, null, null, ZeropageWish.DONTCARE, 0u, dirty = true, inBss = true)

    fun generateAsm(outputDir: Path, program: IRProgram, target: ICompilationTarget): String {
        val codegen = CodeGenerator(program, target)
        // generate() also runs 64tass; the .asm file is written regardless of
        // whether the assembler succeeds, so we always read it back.
        codegen.generate()
        val asmFile = outputDir.resolve("${program.name}.asm")
        check(asmFile.exists()) { "Assembly file not written: $asmFile" }
        return asmFile.readText()
    }

    test("p8_regfile label is INSIDE the BSS_NOCLEAR section, not before it") {
        val (program, target) = buildTestProgram(
            vars = listOf(
                makeDirtyVar("main.dirtyvar1", DataType.UBYTE),
                makeDirtyVar("main.dirtyvar2", DataType.UWORD)
            ),
            instructions = listOf(
                // Use register r1, which lives at offset 0 in the variable-size
                // regfile layout. If the label is misplaced this access overlaps
                // the dirty vars that share the same BSS_NOCLEAR section.
                IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = 1, immediate = 0)
            )
        )
        val outputDir = Path("/tmp/test-regfile-section-1")
        outputDir.toFile().deleteRecursively()
        outputDir.toFile().mkdirs()
        program.options.outputDir = outputDir

        val asm = generateAsm(outputDir, program, target)

        // Find the line containing p8_regfile:
        val lines = asm.lines()
        val regfileLabelLine = lines.indexOfFirst { it.trim() == "p8_regfile:" }
        check(regfileLabelLine >= 0) { "p8_regfile: label not found in generated assembly" }

        // Walk backwards from the label: the most recent .section directive
        // (any section, not just BSS_NOCLEAR) must appear BEFORE the label.
        // We tolerate other lines like blank lines or comments between them.
        val precedingSectionLine = (regfileLabelLine - 1 downTo 0)
            .firstOrNull { lines[it].trim().matches(Regex("""\.section\s+\S+""")) }
        check(precedingSectionLine != null) {
            "p8_regfile: must be preceded by a .section directive (so the label resolves " +
            "to the start of the regfile data, not the start of the BSS_NOCLEAR section)"
        }

        // Walk forward: there must be a .send directive matching the section,
        // confirming the label is actually inside the section's content block.
        val followingSendLine = (regfileLabelLine + 1 until lines.size)
            .firstOrNull { lines[it].trim().matches(Regex("""\.send\s+\S+""")) }
        check(followingSendLine != null) {
            "p8_regfile: must be followed by a .send directive"
        }

        // And the section name on the .send line must match the section name
        // on the preceding .section line, with no other .section in between.
        val sectionName = lines[precedingSectionLine].trim().substringAfter(".section").trim()
        val sendName = lines[followingSendLine].trim().substringAfter(".send").trim()
        sectionName shouldBe sendName

        // And the regfile data (.fill) must be between the label and the .send.
        val hasFillBeforeSend = (regfileLabelLine + 1 until followingSendLine)
            .any { lines[it].trim().matches(Regex("""\.fill\s+\d+""")) }
        check(hasFillBeforeSend) {
            "Expected a .fill directive for the regfile data between the p8_regfile: label " +
            "and the .send directive"
        }
    }

    test("no label is emitted immediately before a .section directive anywhere in the assembly") {
        // This is the general invariant that, if violated, causes the same class of
        // bug as the regfile issue. A label before a .section resolves to the
        // "phantom" current address in the main flow, NOT to the address where
        // the section's content will be placed.
        //
        // Allowed: anonymous forward/backward labels (`+`, `-`) - these are 64tass
        // branch targets and behave correctly.
        // Allowed: directives like `.proc`, `.block`, `.bend`, `.pend` which are
        // scope markers, not section markers.
        val (program, target) = buildTestProgram(
            vars = listOf(
                makeDirtyVar("main.dirtyvar1", DataType.UBYTE)
            ),
            instructions = listOf(
                IRInstruction(Opcode.LOAD, IRDataType.BYTE, reg1 = 1, immediate = 0),
                IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1 = 2, immediate = 0)
            )
        )
        val outputDir = Path("/tmp/test-regfile-section-2")
        outputDir.toFile().deleteRecursively()
        outputDir.toFile().mkdirs()
        program.options.outputDir = outputDir

        val asm = generateAsm(outputDir, program, target)
        val lines = asm.lines()

        val labelPattern = Regex("""^([A-Za-z_][\w.]*):\s*$""")
        val sectionPattern = Regex("""^\s*\.section\s+\S+""")

        // Skip the regfile section since it specifically combines a label with
        // .section content. We're checking the OTHER sections (BSS, BSS_SLABS,
        // STRUCTINSTANCES) where a label-before-section would be a bug.
        val offenders = mutableListOf<Pair<Int, String>>()

        var inSectionDepth = 0
        for ((i, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (sectionPattern.matches(trimmed)) {
                inSectionDepth++
            } else if (trimmed.matches(Regex("""^\s*\.send\s+\S+"""))) {
                inSectionDepth--
            } else if (inSectionDepth == 0) {
                // We're outside any .section block. A label here is a real label.
                if (labelPattern.matches(trimmed)) {
                    // Check if the next non-blank/non-comment line is a .section
                    val nextLine = (i + 1 until lines.size)
                        .firstOrNull { j ->
                            val t = lines[j].trim()
                            t.isNotEmpty() && !t.startsWith(";")
                        }
                    if (nextLine != null && sectionPattern.matches(lines[nextLine].trim())) {
                        offenders.add(i to trimmed)
                    }
                }
            }
        }

        if (offenders.isNotEmpty()) {
            val details = offenders.joinToString("\n") { (i, l) -> "  line ${i + 1}: $l" }
            throw AssertionError(
                "Found label(s) emitted immediately before a .section directive. " +
                "This causes the label to resolve to the wrong address in 64tass:\n$details"
            )
        }
    }
})
