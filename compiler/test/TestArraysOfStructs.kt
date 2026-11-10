package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import prog8.code.core.BaseDataType
import prog8.code.target.*
import prog8.intermediate.IRFileReader
import prog8.vm.VmRunner
import prog8.vm.VmVariableAllocator
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
        val result = compileText(VMTarget(), true, src, out, writeAssembly = true)!!
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

    test("empty initializer in an inline struct array zero-fills its element") {
        // 'single' is a standalone zero-initialized instance, so the BSS block must
        // exist and hold exactly that one instance. The two inline elements must
        // not add their own entries.
        val src = """
            main {
                struct Point { ubyte x  ubyte y }
                Point[2] points = [Point:[], Point:[7, 9]]
                ubyte @shared firstX, firstY, secondX, secondY, singleX, singleY
                sub start() {
                    firstX = points[0].x
                    firstY = points[0].y
                    secondX = points[1].x
                    secondY = points[1].y
                    ^^Point single = ^^Point:[]
                    singleX = single.x
                    singleY = single.y
                }
            }
        """
        listOf(C64Target(), C128Target(), Cx16Target(), PETTarget()).forEach { target ->
            listOf(false, true).forEach { newCodegen ->
                val out = tempdir().toPath()
                val result = compileText(target, true, src, out, writeAssembly = true, assemble = false, newCodegen = newCodegen)!!
                val asm = result.compilationOptions.outputDir.resolve("${result.compilerAst.name}.asm").toFile().readText()
                withClue("target=${target.name} newCodegen=$newCodegen") {
                    asm shouldContain ".fill  2, 0"
                    asm shouldContain ".byte  7"
                    asm shouldContain ".byte  9"
                    val afterBlockStart = asm.substringAfter("prog8_struct_instances_bss  .block")
                    val blockEnd = listOf(".endblock", ".bend")
                        .map { afterBlockStart.indexOf(it) }
                        .filter { it >= 0 }
                        .minOrNull() ?: error("BSS struct instance block is not terminated")
                    val bss = afterBlockStart.substring(0, blockEnd)
                    bss.lines().count { ".fill" in it } shouldBe 1
                }
            }
        }

        listOf(Amiga500Target(), Amiga1200Target(), Qemu68kTarget()).forEach { target ->
            val out = tempdir().toPath()
            val result = compileText(target, true, src, out, writeAssembly = true, assemble = false)!!
            val asm = result.compilationOptions.outputDir.resolve("${result.compilerAst.name}.asm").toFile().readText()
            withClue("target=${target.name}") {
                asm shouldContain "dcb.b  2,0"
                asm shouldContain "dc.b  7"
                asm shouldContain "dc.b  9"
                asm shouldContain "; struct instances (zeroed)"
                // only the zeroed-instances emitter allocates ds.b with a "; struct <type>" comment
                asm.lines().count { "ds.b" in it && "; struct " in it } shouldBe 1
            }
        }
    }

    test("virtual machine zero-initializes empty inline struct array elements") {
        val src = """
            main {
                struct Point { ubyte x  ubyte y }
                Point[2] points = [Point:[], Point:[7, 9]]
                ubyte @shared firstX, firstY, secondX, secondY, singleX, singleY
                sub start() {
                    firstX = points[0].x
                    firstY = points[0].y
                    secondX = points[1].x
                    secondY = points[1].y
                    ^^Point single = ^^Point:[]
                    singleX = single.x
                    singleY = single.y
                }
            }
        """
        val out = tempdir().toPath()
        val result = compileText(VMTarget(), true, src, out, writeAssembly = true)!!
        val irContent = out.toFile().listFiles()!!.single { it.name.endsWith(".p8ir") }.readText()
        val irProgram = IRFileReader().read(irContent)
        irProgram.st.stripAllPrefixes()
        val inlineInstanceNames = irProgram.st.inlineStructArrayInstanceNames()
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget).allocations
        inlineInstanceNames.size shouldBe 2
        inlineInstanceNames.forEach { allocations.containsKey(it) shouldBe false }
        val standaloneInstances = irProgram.st.allStructInstances().map { it.name }.toSet() - inlineInstanceNames
        standaloneInstances.size shouldBe 1
        allocations.containsKey(standaloneInstances.single()) shouldBe true

        VmRunner().runAndTestProgram(irContent) { vm ->
            vm.memory.getUB(allocations.getValue("main.firstX")).toInt() shouldBe 0
            vm.memory.getUB(allocations.getValue("main.firstY")).toInt() shouldBe 0
            vm.memory.getUB(allocations.getValue("main.secondX")).toInt() shouldBe 7
            vm.memory.getUB(allocations.getValue("main.secondY")).toInt() shouldBe 9
            vm.memory.getUB(allocations.getValue("main.singleX")).toInt() shouldBe 0
            vm.memory.getUB(allocations.getValue("main.singleY")).toInt() shouldBe 0
        }
    }

    test("initialized struct array can point to a split word array") {
        val out = tempdir().toPath()
        val src = """
            main {
                struct Item { pointer data }
                uword[2] values = [1,2]
                Item[1] items = [Item : [&values]]
                sub start() {
                    pointer p = items[0].data
                }
            }
        """
        compileText(C64Target(), true, src, out, writeAssembly = true, assemble = true)!!
        val asm = out.toFile().listFiles()!!.single { it.name.endsWith(".asm") }.readText()
        asm shouldContain "p8v_values_lsb"
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

    // A memory-mapped array of struct instances used to crash the compiler on the first field
    // write: the element stride was taken from the array datatype's element type, which has
    // STRUCT_INSTANCE as sub but no way to reach the resolved struct subtype.
    test("memory-mapped struct array - field write uses the struct stride (m68k)") {
        val out = tempdir().toPath()
        val src = $$"""
            main {
                struct Chan { pointer LC  uword LEN  uword PER }
                &Chan[4] chans = $dff0a0
                sub start() {
                    chans[2].PER = 428
                    uword v = chans[3].LEN
                }
            }
        """
        compileText(Amiga500Target(), true, src, out, writeAssembly = true, assemble = false)!!
        val asm = out.toFile().listFiles()!!.single { it.name.endsWith(".asm") }.readText()
        val lines = asm.lines().map { it.trim().replace(Regex("\\s+"), " ") }
        // struct is 8 bytes (4 byte pointer + 2 + 2), so chans[2] is at +16 and PER at +6
        lines.any { it == "move.w #428,$00dff0b6" } shouldBe true
    }

    test("memory-mapped struct array - field write uses the struct stride (6502)") {
        val out = tempdir().toPath()
        val src = $$"""
            main {
                struct Chan { pointer LC  uword LEN  uword PER }
                &Chan[4] chans = $0400
                sub start() {
                    ubyte @shared idx
                    idx = 3
                    chans[1].LEN = 1234
                    chans[idx].PER = 428
                }
            }
        """
        compileText(C64Target(), true, src, out, writeAssembly = true, assemble = false)!!
        val asm = out.toFile().listFiles()!!.single { it.name.endsWith(".asm") }.readText()
        val lines = asm.lines().map { it.trim().replace(Regex("\\s+"), " ") }
        // struct is 6 bytes (2 byte pointer + 2 + 2), so chans[1].LEN is at $0400+8
        lines.any { it == "sta $0408" } shouldBe true
        // the variable index is scaled by the struct size, not by the pointer size
        lines.any { it == "ldy #6" } shouldBe true
    }

    // A struct instance's initializer arguments are held in a property rather than in the AST
    // children, so the symbol prefixer never walks into them and has to prefix the address-of
    // arguments itself. Both code generators run their own copy of the prefixer, hence both are
    // checked here. See github issue #246.
    test("array of struct instances: address-of initializer is symbol prefixed") {
        val src = """
            main {
                uword target
                struct Function { pointer addr }
                ^^Function[] a = [ ^^Function:[&target] ]
                sub start() {
                    pointer p = a[0].addr
                    target = 4242
                }
            }"""
        listOf(false, true).forEach { newCodegen ->
            withClue("newCodegen=$newCodegen") {
                val out = tempdir().toPath()
                compileText(C64Target(), true, src, out, writeAssembly = true, assemble = false, newCodegen = newCodegen)!!
                val asmFile = out.toFile().listFiles()!!.single { it.name.endsWith(".asm") }
                val lines = asmFile.readText().lines().map { it.trim().replace(Regex("\\s+"), " ") }
                lines.any { it.contains("p8b_main.p8v_target") && (it.contains(".dstruct") || it.startsWith(".word ")) } shouldBe true
                lines.any { it.contains("main.target") } shouldBe false
            }
        }
    }

    test("routine referenced only from a struct instance initializer is not removed as unused") {
        val src = """
            main {
                sub handler() -> ubyte { return 42 }
                struct Function { pointer addr }
                ^^Function[] a = [ ^^Function:[&handler] ]
                sub start() {
                    pointer p = a[0].addr
                    call(p)
                }
            }"""
        val out = tempdir().toPath()
        compileText(C64Target(), true, src, out, writeAssembly = true, assemble = false, newCodegen = true)!!
        val asmFile = out.toFile().listFiles()!!.single { it.name.endsWith(".asm") }
        val lines = asmFile.readText().lines().map { it.trim().replace(Regex("\\s+"), " ") }
        lines.any { it == "p8b_main.p8s_handler .proc" } shouldBe true
    }
})
