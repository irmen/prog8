package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.StStructInstance
import prog8.code.core.ICompilationTarget
import prog8.code.target.Amiga500Target
import prog8.code.target.C64Target
import prog8.code.target.C128Target
import prog8.code.target.Cx16Target
import prog8.code.target.PETTarget
import prog8.code.target.VMTarget
import prog8.intermediate.IRFileReader
import prog8.vm.VirtualMachine
import prog8.vm.VmRunner
import prog8.vm.VmVariableAllocator
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import prog8tests.helpers.simulate
import razorvine.ksim65.testing.TestMachine
import kotlin.io.path.readText

class TestStructInstances : FunSpec({

    val outputDir = tempdir().toPath()

    fun runVm(src: String, optimize: Boolean = false, check: (VirtualMachine, Map<String, UInt>) -> Unit) {
        val result = compileText(VMTarget(), optimize, src, outputDir, writeAssembly = true)!!
        val irContent = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir").readText()
        val irProgram = IRFileReader().read(irContent)
        irProgram.st.stripAllPrefixes()
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget).allocations
        VmRunner().runAndTestProgram(irContent) { vm -> check(vm, allocations) }
    }

    fun run6502(target: ICompilationTarget, src: String, newCodegen: Boolean = false): TestMachine {
        val errors = ErrorReporterForTests()
        val result = compileText(target, false, src, outputDir, errors = errors,
            writeAssembly = true, newCodegen = newCodegen)
        withClue("compilation failed for ${target.name} (newCodegen=$newCodegen): ${errors.errors}") {
            result shouldNotBe null
        }
        return result!!.simulate(maxCycles = 100000)
    }

    fun assertWord(machine: TestMachine, address: Int, value: Int) {
        machine.assertMemory(address, value and 0xff)
        machine.assertMemory(address + 1, (value shr 8) and 0xff)
    }

    // PET needs %zeropage basicsafe, else the launcher's reset-vector return address prevents
    // the simulator from ever reaching a BRK at program exit
    fun copyTestSource(zeropageSafe: Boolean = false): String = $$"""
%option no_sysinit
%launcher none
%address $1000
$${if (zeropageSafe) "%zeropage basicsafe" else ""}
main {
    struct Point { ubyte x  uword y }
    Point source = [11, 2222]
    Point target
    &ubyte @shared r1 = $0340
    &uword @shared r2 = $0342
    &uword @shared r3 = $0344
    sub start() {
        target = source
        target.x = 99
        r1 = source.x + target.x
        r2 = source.y
        r3 = target.y
    }
}
"""

    fun assertCopyTestResult(machine: TestMachine) {
        machine.assertMemory(0x0340, 110)   // 11 + 99
        assertWord(machine, 0x0342, 2222)
        assertWord(machine, 0x0344, 2222)
    }

    test("local and global struct instance variables, field reads and writes") {
        val src = """
%option no_sysinit
main {
    struct Point { ubyte x  uword y }
    Point global_point = [7, 1000]
    uword @shared r1, r2, r3
    sub start() {
        Point local_point = [3, 3000]
        local_point.x = 9
        global_point.y = 66
        r1 = local_point.x
        r2 = local_point.y
        r3 = global_point.y
    }
}
"""
        runVm(src) { vm, allocations ->
            vm.memory.getUB(allocations.getValue("main.r1")) shouldBe 9u
            vm.memory.getUW(allocations.getValue("main.r2")) shouldBe 3000u
            vm.memory.getUW(allocations.getValue("main.r3")) shouldBe 66u
        }
    }

    test("struct instance pointer field chain") {
        val src = """
%option no_sysinit
main {
    struct Node { ^^Node next  uword value }
    Node first
    Node second
    uword @shared r1, r2
    sub start() {
        first.next = &second
        second.value = 42
        r1 = first.next.value
        first.next.value = 77
        r2 = second.value
    }
}
"""
        runVm(src) { vm, allocations ->
            vm.memory.getUW(allocations.getValue("main.r1")) shouldBe 42u
            vm.memory.getUW(allocations.getValue("main.r2")) shouldBe 77u
        }
    }

    test("whole struct assignment copies by value") {
        val src = """
%option no_sysinit
main {
    struct Point { uword x  uword y }
    Point source = [111, 222]
    Point target
    uword @shared r1, r2, r3
    sub start() {
        target = source
        target.x = 999
        r1 = source.x
        r2 = target.x
        r3 = target.y
    }
}
"""
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = true)!!
        val ir = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir").readText()
        ir shouldContain "sys.memcopy"
        runVm(src) { vm, allocations ->
            vm.memory.getUW(allocations.getValue("main.r1")) shouldBe 111u
            vm.memory.getUW(allocations.getValue("main.r2")) shouldBe 999u
            vm.memory.getUW(allocations.getValue("main.r3")) shouldBe 222u
        }
    }

    test("direct struct variable initializer forms") {
        val src = """
%option no_sysinit
main {
    struct Node { ubyte id  uword value }
    Node g1 = [1, 100]
    Node g2 = Node:[2, 200]
    uword @shared r1, r2, r3
    sub start() {
        Node local = Node:[id=3, value=300]
        r1 = g1.value
        r2 = g2.value
        r3 = local.value
    }
}
"""
        runVm(src) { vm, allocations ->
            vm.memory.getUW(allocations.getValue("main.r1")) shouldBe 100u
            vm.memory.getUW(allocations.getValue("main.r2")) shouldBe 200u
            vm.memory.getUW(allocations.getValue("main.r3")) shouldBe 300u
        }
    }

    test("m68k codegen accepts direct struct instance variables") {
        val src = """
%option no_sysinit
main {
    struct Point { uword x  uword y }
    Point p = [1, 2]
    sub start() {
        Point q
        q = p
        q.x = 3
    }
}
"""
        val result = compileText(Amiga500Target(), false, src, outputDir,
            writeAssembly = true, assemble = false)!!
        val ir = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir").readText()
        ir shouldContain "<STRUCTINSTANCES>"
        ir shouldContain "sys.memcopy"
    }

    test("sizeof on a direct struct variable is the struct type size") {
        val src = """
%option no_sysinit
main {
    struct Point { uword x  uword y }
    Point p
    ubyte @shared sz
    sub start() {
        sz = sizeof(p)
    }
}
"""
        runVm(src) { vm, allocations ->
            vm.memory.getUB(allocations.getValue("main.sz")) shouldBe 4u
        }
    }

    test("6502: local and global struct fields with initializers (C64, old codegen)") {
        val src = $$"""
%option no_sysinit
%launcher none
%address $1000
main {
    struct Point { ubyte x  uword y }
    Point global = [7, 1000]
    &ubyte @shared r1 = $0340
    &uword @shared r2 = $0342
    sub start() {
        Point local = [3, 3000]
        local.x = 9
        global.y = 66
        r1 = local.x + global.x
        r2 = local.y + global.y
    }
}
"""
        val machine = run6502(C64Target(), src)
        machine.assertMemory(0x0340, 16)          // 9 + 7
        assertWord(machine, 0x0342, 3066)         // 3000 + 66
    }

    test("6502: whole struct assignment copies by value (C64, old codegen)") {
        val machine = run6502(C64Target(), copyTestSource())
        assertCopyTestResult(machine)
    }

    test("6502: struct copy and field access with the new codegen (C64)") {
        val machine = run6502(C64Target(), copyTestSource(), newCodegen = true)
        assertCopyTestResult(machine)
    }

    test("6502: struct copy and field access on CX16, both codegens") {
        withClue("old codegen") {
            assertCopyTestResult(run6502(Cx16Target(), copyTestSource()))
        }
        withClue("new codegen") {
            assertCopyTestResult(run6502(Cx16Target(), copyTestSource(), newCodegen = true))
        }
    }

    test("6502: struct copy and field access on C128 and PET") {
        withClue("C128") {
            assertCopyTestResult(run6502(C128Target(), copyTestSource()))
        }
        withClue("PET") {
            assertCopyTestResult(run6502(PETTarget(), copyTestSource(zeropageSafe = true)))
        }
    }

    test("6502: address of a direct struct instance in static initializer data compiles") {
        // no code execution needed: the crash occurs while generating the static data itself
        val src1 = $$"""
%option no_sysinit
main {
    struct Point { uword x  uword y }
    Point p1
    Point p2
    ^^Point[2] ptrs = [&p1, &p2]
    sub start() {
        ptrs[0].x = 5
    }
}
"""
        val src2 = $$"""
%option no_sysinit
main {
    struct Point { uword x  uword y }
    struct Holder { ^^Point pt  uword extra }
    Point p1
    Holder h = [&p1, 99]
    sub start() {
        h.pt.y = 7
    }
}
"""
        // the typed address-of form must work as well
        val src3 = src1.replace("&p1", "&&p1").replace("&p2", "&&p2")
        val src4 = src2.replace("&p1", "&&p1")
        listOf(src1, src2, src3, src4).forEachIndexed { index, src ->
            listOf(false, true).forEach { newCodegen ->
                withClue("src $index, newCodegen=$newCodegen") {
                    val result = compileText(C64Target(), false, src, outputDir,
                        errors = ErrorReporterForTests(), writeAssembly = true, assemble = false,
                        newCodegen = newCodegen)
                    result shouldNotBe null
                }
            }
        }
    }

    test("virtual: typed address of a direct struct instance in static initializer data") {
        val src = $$"""
%option no_sysinit
main {
    struct Point { uword x  uword y }
    struct Holder { ^^Point pt  uword extra }
    Point p1
    Point p2
    ^^Point[2] ptrs = [&&p1, &&p2]
    Holder h = [&&p2, 99]
    uword @shared r1, r2, r3, r4, r5
    sub start() {
        p1.x = 111
        p2.y = 222
        r1 = ptrs[0].x
        r2 = ptrs[1].y
        r3 = h.pt.y
        r4 = h.extra
        h.pt.x = 5
        r5 = p2.x
    }
}
"""
        runVm(src, optimize = true) { vm, allocations ->
            vm.memory.getUW(allocations.getValue("main.r1")) shouldBe 111u
            vm.memory.getUW(allocations.getValue("main.r2")) shouldBe 222u
            vm.memory.getUW(allocations.getValue("main.r3")) shouldBe 222u
            vm.memory.getUW(allocations.getValue("main.r4")) shouldBe 99u
            vm.memory.getUW(allocations.getValue("main.r5")) shouldBe 5u
        }
    }

    test("m68k: typed address of a direct struct instance in static initializer data") {
        val src = $$"""
%option no_sysinit
main {
    struct Point { uword x  uword y }
    struct Holder { ^^Point pt  uword extra }
    Point p1
    Point p2
    ^^Point[2] ptrs = [&&p1, &&p2]
    Holder h = [&&p2, 99]
    sub start() {
        ptrs[0].x = 1
        h.pt.y = 2
    }
}
"""
        val result = compileText(Amiga500Target(), false, src, outputDir,
            errors = ErrorReporterForTests(), writeAssembly = true, assemble = false)!!
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        val lines = asm.lines().map { it.trim() }
        // the static data must reference the struct instance storage, not the variable declaration
        lines.any { it.startsWith("dc.l") && it.contains("p8i_p1") } shouldBe true
        lines.any { it.startsWith("dc.l") && it.contains("p8i_p2") } shouldBe true
        val instanceLabels = lines.filter { it.endsWith("p8i_p1:") || it.endsWith("p8i_p2:") }
        instanceLabels.size shouldBe 2
    }

    test("6502: sizeof on a direct struct variable is the struct type size") {
        val src = $$"""
%option no_sysinit
%launcher none
%address $1000
main {
    struct Point { uword x  uword y }
    Point p
    &ubyte @shared sz = $0340
    sub start() {
        sz = sizeof(p)
    }
}
"""
        run6502(C64Target(), src).assertMemory(0x0340, 4)
    }

    test("6502: struct instance pointer field chain") {
        val src = $$"""
%option no_sysinit
%launcher none
%address $1000
main {
    struct Node { ^^Node next  uword value }
    Node first
    Node second
    &uword @shared r1 = $0340
    &uword @shared r2 = $0342
    sub start() {
        first.next = &second
        second.value = 42
        r1 = first.next.value
        first.next.value = 77
        r2 = second.value
    }
}
"""
        val machine = run6502(C64Target(), src)
        assertWord(machine, 0x0340, 42)
        assertWord(machine, 0x0342, 77)
    }

    test("symbol table keeps struct instance type for direct variables") {
        val src = """
%option no_sysinit
main {
    struct Point { uword x  uword y }
    Point p
    sub start() { p.x = 1 }
}
"""
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = true, assemble = true)!!
        val st = result.codegenSymboltable!!
        val instance = st.lookup("main.p") as? StStructInstance
        instance shouldNotBe null
        instance!!.structName shouldContain "Point"
    }
})
