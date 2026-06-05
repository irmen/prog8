package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.target.VMTarget
import prog8.vm.VmRunner
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import java.io.File

class TestStructsWithArrays : FunSpec({
    val outputDir = File("build/test/TestStructsWithArrays").toPath()
    
    beforeTest {
        if (outputDir.toFile().exists()) outputDir.toFile().deleteRecursively()
        outputDir.toFile().mkdirs()
    }

        test("array in struct access") {
        val text = """
            %encoding iso
            M {
                struct S {
                    ubyte[4] data
                }
            }
            main {
                sub start() {
                    ^^M.S s = ^^M.S : [ [1, 2, 3, 4] ]
                    s.data[0] = 10
                    if s.data[0] == 10 and s.data[1] == 2 and s.data[2] == 3 and s.data[3] == 4 {
                        ; pass
                    }
                }
            }
        """.trimIndent()
        
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        val result = compileText(VMTarget(), true, text, outputDir, errors = errors)
        System.err.println("Errors: ${errors.errors}")
        if (result == null) {
            throw Exception("Compilation failed")
        }
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runProgram(virtfile.toFile().readText(), false)
    }

    test("nested array in struct initialization") {
        val text = """
            %encoding iso
            M {
                struct S {
                    ubyte id
                    ubyte[4] data
                    word[2] scores
                }
            }
            main {
                sub start() {
                    ^^M.S s = ^^M.S : [1, [10, 20, 30, 40], [1000, 2000]]
                    if s.id == 1 and s.data[0] == 10 and s.data[1] == 20 and s.data[2] == 30 and s.data[3] == 40 and s.scores[0] == 1000 and s.scores[1] == 2000 {
                        ; pass
                    }
                }
            }
        """.trimIndent()
        
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        val result = compileText(VMTarget(), true, text, outputDir, errors = errors)
        if (result == null) {
            throw Exception("Compilation failed: ${errors.errors}")
        }
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runProgram(virtfile.toFile().readText(), false)
    }

    test("struct with inlined array memory layout") {
        val src = $$"""
            main {
                struct Node {
                    ubyte a
                    bool flag
                    ubyte[5] array
                    word number
                }

                sub start() {
                    ^^Node k2 = [1, false, [65,66,67,68,0], 9999]
                    ^^Node k3 = $4000
                    k3^^=k2^^
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        val result = compileText(VMTarget(), true, src, outputDir, errors = errors)
        if (result == null) {
            throw Exception("Compilation failed: ${errors.errors}")
        }
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.toFile().readText(), true) { vm ->
            vm.memory.getUB(0x4000u).toInt() shouldBe 1
            vm.memory.getUB(0x4001u).toInt() shouldBe 0
            vm.memory.getUB(0x4002u).toInt() shouldBe 65
            vm.memory.getUB(0x4003u).toInt() shouldBe 66
            vm.memory.getUB(0x4004u).toInt() shouldBe 67
            vm.memory.getUB(0x4005u).toInt() shouldBe 68
            vm.memory.getUB(0x4006u).toInt() shouldBe 0
            vm.memory.getUW(0x4007u).toInt() shouldBe 9999
        }
    }
})
