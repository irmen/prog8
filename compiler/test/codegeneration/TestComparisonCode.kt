package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import prog8.code.target.C64Target
import prog8.intermediate.IRFileReader
import prog8.intermediate.Opcode
import prog8tests.helpers.compileText
import prog8tests.helpers.simulate
import java.nio.file.Files


class TestComparisonCode : FunSpec({
    val outputDir = Files.createTempDirectory("prog8test")

    fun compileAndGetOpcodes(src: String): Pair<prog8.compiler.CompilationResult, List<Opcode>> {
        val compileResult = compileText(C64Target(), false, src, outputDir, newCodegen = true)!!
        val irFile = compileResult.compilationOptions.outputDir.resolve(compileResult.compilerAst.name + ".p8ir")
        val irProgram = IRFileReader().read(irFile)
        val startSub = irProgram.allSubs().first { it.label.endsWith("start") }
        val opcodes = startSub.chunks.flatMap { it.instructions }.map { it.opcode }
        return compileResult to opcodes
    }

    // In if/else, the IR codegen inverts the operator because the branch
    // targets the else-label (condition false path). So:
    //   source >=  emitted as <  (BLT/BGTR/BLTS/BGTSR)
    //   source >   emitted as <= (BLE/BGER/BLES/BGESR)
    //   source <   emitted as >= (BGE/BGTR/BGES/BGTSR)
    //   source <=  emitted as >  (BGT/BGER/BGTS/BGESR)

    // --- unsigned byte, immediate ---

    test("c64 newcodegen BGE (ubyte < immediate)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                &ubyte result = $02
                sub start() {
                    ubyte @shared a = 10
                    if a < 20 {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        val (compileResult, opcodes) = compileAndGetOpcodes(src)
        val machine = compileResult.simulate()
        machine.assertMemory(0x02, 1)
        opcodes shouldContain Opcode.BGE
    }

    test("c64 newcodegen BGT (ubyte <= immediate)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                &ubyte result = $02
                sub start() {
                    ubyte @shared a = 10
                    if a <= 20 {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        val (compileResult, opcodes) = compileAndGetOpcodes(src)
        val machine = compileResult.simulate()
        machine.assertMemory(0x02, 1)
        opcodes shouldContain Opcode.BGT
    }

    test("c64 newcodegen BLT (ubyte >= immediate)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                &ubyte result = $02
                sub start() {
                    ubyte @shared a = 10
                    if a >= 20 {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        val (compileResult, opcodes) = compileAndGetOpcodes(src)
        val machine = compileResult.simulate()
        machine.assertMemory(0x02, 2)
        opcodes shouldContain Opcode.BLT
    }

    test("c64 newcodegen BLE (ubyte > immediate)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                &ubyte result = $02
                sub start() {
                    ubyte @shared a = 10
                    if a > 20 {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        val (compileResult, opcodes) = compileAndGetOpcodes(src)
        val machine = compileResult.simulate()
        machine.assertMemory(0x02, 2)
        opcodes shouldContain Opcode.BLE
    }

    // --- unsigned byte, register vs register ---

    test("c64 newcodegen BGTR (ubyte < ubyte)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                &ubyte result = $02
                sub start() {
                    ubyte @shared a = 10
                    ubyte @shared b = 20
                    if a < b {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        val (compileResult, opcodes) = compileAndGetOpcodes(src)
        val machine = compileResult.simulate()
        machine.assertMemory(0x02, 1)
        opcodes shouldContain Opcode.BGTR
    }

    test("c64 newcodegen BGER (ubyte > ubyte)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                &ubyte result = $02
                sub start() {
                    ubyte @shared a = 10
                    ubyte @shared b = 20
                    if a > b {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        val (compileResult, opcodes) = compileAndGetOpcodes(src)
        val machine = compileResult.simulate()
        machine.assertMemory(0x02, 2)
        opcodes shouldContain Opcode.BGER
    }

    // --- signed byte, immediate ---

    test("c64 newcodegen BGES (byte < immediate)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                &ubyte result = $02
                sub start() {
                    byte @shared a = -3
                    if a < -5 {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        val (compileResult, opcodes) = compileAndGetOpcodes(src)
        val machine = compileResult.simulate()
        machine.assertMemory(0x02, 2)
        opcodes shouldContain Opcode.BGES
    }

    test("c64 newcodegen BGTS (byte <= immediate)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                &ubyte result = $02
                sub start() {
                    byte @shared a = -3
                    if a <= -5 {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        val (compileResult, opcodes) = compileAndGetOpcodes(src)
        val machine = compileResult.simulate()
        machine.assertMemory(0x02, 2)
        opcodes shouldContain Opcode.BGTS
    }

    test("c64 newcodegen BLTS (byte >= immediate)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                &ubyte result = $02
                sub start() {
                    byte @shared a = -3
                    if a >= -5 {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        val (compileResult, opcodes) = compileAndGetOpcodes(src)
        val machine = compileResult.simulate()
        machine.assertMemory(0x02, 1)
        opcodes shouldContain Opcode.BLTS
    }

    test("c64 newcodegen BLES (byte > immediate)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                &ubyte result = $02
                sub start() {
                    byte @shared a = -3
                    if a > -5 {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        val (compileResult, opcodes) = compileAndGetOpcodes(src)
        val machine = compileResult.simulate()
        machine.assertMemory(0x02, 1)
        opcodes shouldContain Opcode.BLES
    }

    // --- signed byte, register vs register ---

    test("c64 newcodegen BGTSR (byte < byte)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                &ubyte result = $02
                sub start() {
                    byte @shared a = -3
                    byte @shared b = -10
                    if a < b {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        val (compileResult, opcodes) = compileAndGetOpcodes(src)
        val machine = compileResult.simulate()
        machine.assertMemory(0x02, 2)
        opcodes shouldContain Opcode.BGTSR
    }

    test("c64 newcodegen BGESR (byte > byte)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            main {
                &ubyte result = $02
                sub start() {
                    byte @shared a = -3
                    byte @shared b = -10
                    if a > b {
                        result = 1
                    } else {
                        result = 2
                    }
                }
            }
        """.trimIndent()
        val (compileResult, opcodes) = compileAndGetOpcodes(src)
        val machine = compileResult.simulate()
        machine.assertMemory(0x02, 1)
        opcodes shouldContain Opcode.BGESR
    }
})
