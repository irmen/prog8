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

    test("string literal initializes ubyte array field with C-style semantics") {
        val src = $$"""
            %encoding iso
            main {
                struct Node {
                    ubyte id
                    ubyte[4] name
                    bool flag
                }

                sub start() {
                    ; C semantics: shorter than array -> pad with zeros including implicit terminator
                    ; "abc" -> 'a','b','c','\0'  (97, 98, 99, 0)
                    ^^Node n = [1, "abc", false]
                    n.id = 7
                    n.flag = true
                    ^^Node m = $4000
                    m^^ = n^^
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
            vm.memory.getUB(0x4000u).toInt() shouldBe 7
            vm.memory.getUB(0x4001u).toInt() shouldBe 'a'.code
            vm.memory.getUB(0x4002u).toInt() shouldBe 'b'.code
            vm.memory.getUB(0x4003u).toInt() shouldBe 'c'.code
            vm.memory.getUB(0x4004u).toInt() shouldBe 0
            vm.memory.getUB(0x4005u).toInt() shouldBe 1   // flag=true
        }
    }

    test("string literal exactly fills ubyte array field (no implicit terminator)") {
        val src = $$"""
            %encoding iso
            main {
                sub start() {
                    struct Node {
                        ubyte[4] name
                    }
                    ; "abcd" (4 bytes) fills the array exactly; no room for the 0 terminator
                    ^^Node n = ["abcd"]
                    ^^Node m = $4000
                    m^^ = n^^
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
            vm.memory.getUB(0x4000u).toInt() shouldBe 'a'.code
            vm.memory.getUB(0x4001u).toInt() shouldBe 'b'.code
            vm.memory.getUB(0x4002u).toInt() shouldBe 'c'.code
            vm.memory.getUB(0x4003u).toInt() shouldBe 'd'.code
        }
    }

    test("string literal too long for ubyte array field errors") {
        val src = $$"""
            %encoding iso
            main {
                sub start() {
                    struct Node {
                        ubyte[3] name
                    }
                    ; "abcd" (4 bytes) is too long for an array of size 3
                    ^^Node n = ["abcd"]
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        val result = compileText(VMTarget(), true, src, outputDir, errors = errors)
        result shouldBe null
        errors.errors.any { it.contains("does not fit in ubyte array field 'name'") } shouldBe true
    }

    test("string literal pads with zeros when shorter than ubyte array field") {
        val src = $$"""
            %encoding iso
            main {
                sub start() {
                    struct Node {
                        ubyte[5] name
                    }
                    ; "ab" -> 'a','b','\0','\0','\0'
                    ^^Node n = ["ab"]
                    ^^Node m = $4000
                    m^^ = n^^
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
            vm.memory.getUB(0x4000u).toInt() shouldBe 'a'.code
            vm.memory.getUB(0x4001u).toInt() shouldBe 'b'.code
            vm.memory.getUB(0x4002u).toInt() shouldBe 0
            vm.memory.getUB(0x4003u).toInt() shouldBe 0
            vm.memory.getUB(0x4004u).toInt() shouldBe 0
        }
    }

    test("string literal initializes signed byte array field") {
        val src = $$"""
            %encoding iso
            main {
                struct Node {
                    byte id
                    byte[4] name
                    bool flag
                }

                sub start() {
                    ^^Node n = [1, "abc", false]
                    ^^Node m = $4000
                    m^^ = n^^
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
            vm.memory.getSB(0x4000u).toInt() shouldBe 1
            vm.memory.getSB(0x4001u).toInt() shouldBe 'a'.code
            vm.memory.getSB(0x4002u).toInt() shouldBe 'b'.code
            vm.memory.getSB(0x4003u).toInt() shouldBe 'c'.code
            vm.memory.getSB(0x4004u).toInt() shouldBe 0
        }
    }

    test("string literal with high bytes errors on signed byte array field") {
        val src = $$"""
            %encoding iso
            main {
                struct Node {
                    byte[3] name
                }

                sub start() {
                    ; 'ÿ' is 0xFF which doesn't fit in signed byte
                    ^^Node n = ["\u00ff"]
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        val result = compileText(VMTarget(), true, src, outputDir, errors = errors)
        result shouldBe null
        errors.errors.any { it.contains("do not fit in the signed byte array") } shouldBe true
    }

    test("string literal initializes ubyte array field via typed struct initializer") {
        val src = $$"""
            %encoding iso
            main {
                struct Node {
                    ubyte id
                    ubyte[4] name
                    bool flag
                }

                sub start() {
                    ^^Node n = ^^Node : [1, "abc", false]
                    n.id = 7
                    n.flag = true
                    ^^Node m = $4000
                    m^^ = n^^
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
            vm.memory.getUB(0x4000u).toInt() shouldBe 7
            vm.memory.getUB(0x4001u).toInt() shouldBe 'a'.code
            vm.memory.getUB(0x4002u).toInt() shouldBe 'b'.code
            vm.memory.getUB(0x4003u).toInt() shouldBe 'c'.code
            vm.memory.getUB(0x4004u).toInt() shouldBe 0
            vm.memory.getUB(0x4005u).toInt() shouldBe 1   // flag=true
        }
    }

    test("string literal too long for signed byte array field errors") {
        val src = $$"""
            %encoding iso
            main {
                sub start() {
                    struct Node {
                        byte[3] name
                    }
                    ; "abcd" (4 bytes) is too long for an array of size 3
                    ^^Node n = ["abcd"]
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        val result = compileText(VMTarget(), true, src, outputDir, errors = errors)
        result shouldBe null
        errors.errors.any { it.contains("does not fit in byte array field 'name'") } shouldBe true
    }
})
