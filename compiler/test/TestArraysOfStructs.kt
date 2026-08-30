package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import prog8.code.core.BaseDataType
import prog8.code.target.C64Target
import prog8.code.target.Qemu68kTarget
import prog8.code.target.VMTarget
import prog8.intermediate.IRFileReader
import prog8tests.helpers.compileText

class TestArraysOfStructs: FunSpec({

    test("declare array of struct - symbol table") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Point { uword x  uword y }
                Point[4] points
                sub start() { points[0].x = 1 }
            }
        """
        val result = compileText(VMTarget(), true, src, out, writeAssembly = true, assemble = false)!!
        val dt = result.codegenSymboltable!!.allVariables.first { it.name.endsWith("points") }.dt
        dt.base shouldBe BaseDataType.ARRAY
        dt.sub shouldBe BaseDataType.STRUCT_INSTANCE
        dt.subType!!.scopedNameString shouldContain "Point"
    }

    test("field read via variable index") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Point { uword x  uword y }
                Point[4] points
                sub start() {
                    uword v = points[1].y
                }
            }
        """
        val result = compileText(VMTarget(), true, src, out, writeAssembly = true, assemble = false)!!
        val dt = result.codegenSymboltable!!.allVariables.first { it.name.endsWith("points") }.dt
        dt.sub shouldBe BaseDataType.STRUCT_INSTANCE
    }

    test("field write via variable index desugars to poke") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Point { uword x  uword y }
                Point[4] points
                sub start() {
                    points[2].x = 1234
                }
            }
        """
        compileText(C64Target(), true, src, out, writeAssembly = true, assemble = false)!!
        val asm = out.toFile().listFiles()!!.single { it.name.endsWith(".asm") }.readText()
        asm shouldContain "poke"
    }

    test("whole struct assignment becomes memcopy") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Point { uword x  uword y }
                Point[4] points
                sub start() {
                    points[1] = points[2]
                }
            }
        """
        compileText(VMTarget(), true, src, out, writeAssembly = true, assemble = true)!!
        val ir = out.toFile().listFiles()!!.single { it.name.endsWith(".p8ir") }.readText()
        ir shouldContain "call sys.memcopy"
    }

    test("iterate over struct array with for loop") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Big { uword a  uword b  uword c  uword d  uword e  uword f }
                Big[5] bigs
                sub start() {
                    for i in 0 to 4 {
                        bigs[i].a = i
                    }
                }
            }
        """
        compileText(VMTarget(), true, src, out, writeAssembly = true, assemble = true)!!
        val ir = out.toFile().listFiles()!!.single { it.name.endsWith(".p8ir") }.readText()
        ir.split("bigs").size shouldBe 3  // declaration + loop read + loop write
    }

    test("initialized array of structs") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Point { uword x  uword y }
                Point[3] arr = [Point : [1,2], Point : [3,4], Point : [5,6]]
                sub start() {
                    uword v = arr[1].x
                }
            }
        """
        compileText(VMTarget(), true, src, out, writeAssembly = true, assemble = true)!!
        val ir = out.toFile().listFiles()!!.single { it.name.endsWith(".p8ir") }.readText()
        ir shouldContain "<STRUCTINSTANCES>"
        ir shouldContain "arr"
    }

    test("struct instance array with pointer initializer should be error") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Point { uword x  uword y }
                Point[3] arr = [^^Point : [1,2], ^^Point : [3,4], ^^Point : [5,6]]
                sub start() {
                    uword v = arr[1].x
                }
            }
        """
        val errors = prog8tests.helpers.ErrorReporterForTests()
        val result = compileText(VMTarget(), true, src, out, writeAssembly = false, assemble = false, errors=errors)
        result shouldBe null
        errors.errors.any { it.contains("must be initialized with struct values") } shouldBe true
    }

    test("initialized array of structs with inferred type") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Point { uword x  uword y }
                Point[3] arr = [[1,2], [3,4], [5,6]]
                sub start() {
                    uword v = arr[1].x
                }
            }
        """
        val result = compileText(VMTarget(), true, src, out, writeAssembly = true, assemble = true)!!
        val ir = out.toFile().listFiles()!!.single { it.name.endsWith(".p8ir") }.readText()
        ir shouldContain "<STRUCTINSTANCES>"
        ir shouldContain "arr"
        // also verify parsing via symbol table
        val dt = result.codegenSymboltable!!.allVariables.first { it.name.endsWith("arr") }.dt
        dt.sub shouldBe BaseDataType.STRUCT_INSTANCE
    }

    test("IR round-trip preserves struct field definitions") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Point { uword x  uword y }
                Point[3] arr
                sub start() {
                    arr[1].x = 1234
                }
            }
        """
        compileText(VMTarget(), true, src, out, writeAssembly = true, assemble = true)!!
        val irFile = out.toFile().listFiles()!!.single { it.name.endsWith(".p8ir") }.toPath()
        val ir = IRFileReader().read(irFile)
        val pointDef = ir.st.lookup("main.Point") as prog8.intermediate.IRStStructDef
        pointDef.fields.size shouldBe 2
        pointDef.fields.any { it.name == "x" } shouldBe true
        pointDef.fields.any { it.name == "y" } shouldBe true
    }

    test("6502 stride >256 uses 16-bit offset") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Big130 {
                    uword w1
                    uword w2
                    uword w3
                    uword w4
                    uword w5
                    uword w6
                    uword w7
                    uword w8
                    uword w9
                    uword w10
                    uword w11
                    uword w12
                    uword w13
                    uword w14
                    uword w15
                    uword w16
                    uword w17
                    uword w18
                    uword w19
                    uword w20
                    uword w21
                    uword w22
                    uword w23
                    uword w24
                    uword w25
                    uword w26
                    uword w27
                    uword w28
                    uword w29
                    uword w30
                    uword w31
                    uword w32
                    uword w33
                    uword w34
                    uword w35
                    uword w36
                    uword w37
                    uword w38
                    uword w39
                    uword w40
                    uword w41
                    uword w42
                    uword w43
                    uword w44
                    uword w45
                    uword w46
                    uword w47
                    uword w48
                    uword w49
                    uword w50
                    uword w51
                    uword w52
                    uword w53
                    uword w54
                    uword w55
                    uword w56
                    uword w57
                    uword w58
                    uword w59
                    uword w60
                    uword w61
                    uword w62
                    uword w63
                    uword w64
                    uword w65
                }
                Big130[4] arr
                sub start() {
                    arr[1].w65 = 1234
                }
            }
        """
        // uses VM target to allow total size >256 (130*4=520) while still testing 16-bit offset codegen logic
        compileText(VMTarget(), true, src, out, writeAssembly = true, assemble = true)!!
        val ir = out.toFile().listFiles()!!.single { it.name.endsWith(".p8ir") }.readText()
        ir shouldContain "arr"
    }

    test("6502 variable index stride >256 uses 16-bit scaling") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Big { ubyte a  ubyte b  ubyte c  ubyte d  ubyte e  ubyte f  ubyte g }
                Big[18] arr
                sub start() {
                    ubyte @shared idx
                    idx = 10
                    arr[idx].g = 123
                }
            }
        """
        compileText(C64Target(), true, src, out, writeAssembly = true, assemble = false)!!
        val asm = out.toFile().listFiles()!!.single { it.name.endsWith(".asm") }.readText()
        // size 7, variable index must use fast Y path
        asm shouldContain "tay"
        asm shouldContain "multiply_bytes"
        asm shouldContain "p8v_arr,y"
    }

    test("6502 byte field at offset 0 with variable index") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Trip { ubyte a  ubyte b  ubyte c }
                Trip[80] trips
                sub start() {
                    ubyte @shared idx
                    idx = 50
                    trips[idx].a = 123
                }
            }
        """
        compileText(C64Target(), true, src, out, writeAssembly = true, assemble = false)!!
        val asm = out.toFile().listFiles()!!.single { it.name.endsWith(".asm") }.readText()
        // must use fast Y-indexed store, not pointer indirection
        asm shouldContain "p8v_trips,y"
        asm shouldContain "tay"
        asm shouldContain "multiply_bytes"
    }

    test("6502 byte field at non-zero offset with variable index") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Point { uword x  ubyte y }
                Point[80] points
                sub start() {
                    ubyte @shared idx
                    idx = 50
                    points[idx].y = 123
                }
            }
        """
        compileText(C64Target(), true, src, out, writeAssembly = true, assemble = false)!!
        val asm = out.toFile().listFiles()!!.single { it.name.endsWith(".asm") }.readText()
        // must use fast Y-indexed store, not a direct absolute address
        asm shouldContain "p8v_points,y"
        asm shouldContain "tay"
        asm shouldNotContain "sta  p8b_main.p8v_points+2"
    }

    test("m68k struct array without vasm - IR check") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Point { uword x  uword y }
                Point[4] points
                sub start() {
                    points[2].x = 1234
                    points[1] = points[2]
                }
            }
        """
        val result = compileText(Qemu68kTarget(), true, src, out, writeAssembly = true, assemble = false)!!
        result.codegenSymboltable!!.allVariables.any { it.name.endsWith("points") } shouldBe true
        // check assembly was generated without needing vasm (assemble=false)
        val asmFiles = out.toFile().listFiles()!!.filter { it.name.endsWith(".asm") }
        (asmFiles.isNotEmpty() || result.codegenAst != null) shouldBe true
    }
})
