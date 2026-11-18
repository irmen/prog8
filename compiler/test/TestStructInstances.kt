package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import prog8.code.StStructInstance
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
import kotlin.io.path.readText

class TestStructInstances : FunSpec({

    val outputDir = tempdir().toPath()

    fun runVm(src: String, check: (VirtualMachine, Map<String, UInt>) -> Unit) {
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = true)!!
        val irContent = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir").readText()
        val irProgram = IRFileReader().read(irContent)
        irProgram.st.stripAllPrefixes()
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget).allocations
        VmRunner().runAndTestProgram(irContent) { vm -> check(vm, allocations) }
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

    test("6502 targets reject direct struct instance variables cleanly") {
        val src = """
%option no_sysinit
main {
    struct Point { uword x }
    Point p
    sub start() { p.x = 1 }
}
"""
        listOf(C64Target(), C128Target(), Cx16Target(), PETTarget()).forEach { target ->
            val errors = ErrorReporterForTests()
            val result = compileText(target, false, src, outputDir, errors = errors,
                writeAssembly = false, assemble = false)
            result shouldBe null
            errors.errors.any { "direct struct instance variables are not supported" in it } shouldBe true
        }
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
