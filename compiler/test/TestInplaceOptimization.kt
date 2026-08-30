package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.string.shouldContain
import prog8.code.target.VMTarget
import prog8tests.helpers.compileText
import kotlin.io.path.readText

class TestInplaceOptimization: FunSpec({
    val outputDir = tempdir().toPath()

    test("array shift right in-place with variable index") {
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte[] arr = [8, 16, 32, 64]
                ubyte @shared result
                sub start() {
                    ubyte @shared idx = 2
                    ubyte @shared sh = 1
                    arr[idx] >>= sh
                    result = arr[idx]
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val ir = outputDir.resolve(result.compilerAst.name + ".p8ir").readText()
        ir shouldContain "loadx.b"
        ir shouldContain "lsrn.b"
        ir shouldContain "storex.b"
    }

    test("array shift left in-place with variable index") {
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte[] arr = [1, 2, 4, 8]
                ubyte @shared result
                sub start() {
                    ubyte @shared idx = 1
                    arr[idx] <<= 2
                    result = arr[idx]
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val ir = outputDir.resolve(result.compilerAst.name + ".p8ir").readText()
        ir shouldContain "loadx.b"
        ir shouldContain "lsln.b"
        ir shouldContain "storex.b"
    }

    test("array multiply in-place with variable index") {
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte[] arr = [1, 2, 4, 8]
                ubyte @shared result
                sub start() {
                    ubyte @shared idx = 2
                    arr[idx] *= 3
                    result = arr[idx]
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val ir = outputDir.resolve(result.compilerAst.name + ".p8ir").readText()
        ir shouldContain "loadx.b"
        ir shouldContain "mul.b"
        ir shouldContain "storex.b"
    }

    test("array divide in-place with variable index") {
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte[] arr = [8, 16, 32, 64]
                ubyte @shared result
                sub start() {
                    ubyte @shared idx = 2
                    arr[idx] /= 3
                    result = arr[idx]
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val ir = outputDir.resolve(result.compilerAst.name + ".p8ir").readText()
        ir shouldContain "loadx.b"
        ir shouldContain "div.b"
        ir shouldContain "storex.b"
    }

    test("array modulo in-place with variable index") {
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte[] arr = [8, 16, 32, 64]
                ubyte @shared result
                sub start() {
                    ubyte @shared idx = 2
                    arr[idx] %= 6
                    result = arr[idx]
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val ir = outputDir.resolve(result.compilerAst.name + ".p8ir").readText()
        ir shouldContain "loadx.b"
        ir shouldContain "mod.b"
        ir shouldContain "storex.b"
    }

    test("memory multiply in-place with variable address") {
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                uword @shared ptr
                ubyte @shared result
                sub start() {
                    ptr = ${'$'}c000
                    @(ptr) = 5
                    @(ptr) *= 3
                    result = @(ptr)
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val ir = outputDir.resolve(result.compilerAst.name + ".p8ir").readText()
        ir shouldContain "loadi.b"
        ir shouldContain "mul.b"
        ir shouldContain "storei.b"
    }

    test("memory divide in-place with variable address") {
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                uword @shared ptr
                ubyte @shared result
                sub start() {
                    ptr = ${'$'}c000
                    @(ptr) = 20
                    @(ptr) /= 3
                    result = @(ptr)
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val ir = outputDir.resolve(result.compilerAst.name + ".p8ir").readText()
        ir shouldContain "loadi.b"
        ir shouldContain "div.b"
        ir shouldContain "storei.b"
    }

    test("memory modulo in-place with variable address") {
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                uword @shared ptr
                ubyte @shared result
                sub start() {
                    ptr = ${'$'}c000
                    @(ptr) = 20
                    @(ptr) %= 6
                    result = @(ptr)
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val ir = outputDir.resolve(result.compilerAst.name + ".p8ir").readText()
        ir shouldContain "loadi.b"
        ir shouldContain "mod.b"
        ir shouldContain "storei.b"
    }

    test("memory shift right in-place with variable address") {
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                uword @shared ptr
                ubyte @shared result
                sub start() {
                    ptr = ${'$'}c000
                    @(ptr) = 16
                    @(ptr) >>= 1
                    result = @(ptr)
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val ir = outputDir.resolve(result.compilerAst.name + ".p8ir").readText()
        ir shouldContain "loadi.b"
        ir shouldContain "lsr.b"
        ir shouldContain "storei.b"
    }

    test("memory shift left in-place with variable address") {
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                uword @shared ptr
                ubyte @shared result
                sub start() {
                    ptr = ${'$'}c000
                    @(ptr) = 4
                    @(ptr) <<= 1
                    result = @(ptr)
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val ir = outputDir.resolve(result.compilerAst.name + ".p8ir").readText()
        ir shouldContain "loadi.b"
        ir shouldContain "lsl.b"
        ir shouldContain "storei.b"
    }
})
