package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import prog8.code.target.VMTarget
import prog8.intermediate.IRFileReader
import prog8.vm.VmRunner
import prog8.vm.VmVariableAllocator
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import kotlin.io.path.readText


class TestNamedStructInitializers: FunSpec({

    val outputDir = tempdir().toPath()

    fun runVm(src: String, test: (prog8.vm.VirtualMachine, Map<String, UInt>) -> Unit) {
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irContent = virtfile.readText()
        val irProgram = IRFileReader().read(irContent)
        irProgram.st.stripAllPrefixes()
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget).allocations
        VmRunner().runAndTestProgram(irContent) { vm ->
            test(vm, allocations)
        }
    }

    test("named struct initializer sets fields and zeroes omitted fields") {
        val src = """
main {
    struct Enemy {
        ubyte hp
        ubyte x
        ubyte y
        ubyte frame
        ubyte bank
    }

    sub start() {
        ^^Enemy e = ^^Enemy:[hp=100, x=10]
        main.hp_result = e.hp
        main.x_result = e.x
        main.y_result = e.y
        main.frame_result = e.frame
        main.bank_result = e.bank
    }

    ubyte @shared hp_result
    ubyte @shared x_result
    ubyte @shared y_result
    ubyte @shared frame_result
    ubyte @shared bank_result
}"""
        runVm(src) { vm, allocations ->
            vm.memory.getUB(allocations["main.hp_result"]!!).toInt() shouldBe 100
            vm.memory.getUB(allocations["main.x_result"]!!).toInt() shouldBe 10
            vm.memory.getUB(allocations["main.y_result"]!!).toInt() shouldBe 0
            vm.memory.getUB(allocations["main.frame_result"]!!).toInt() shouldBe 0
            vm.memory.getUB(allocations["main.bank_result"]!!).toInt() shouldBe 0
        }
    }

    test("named fields can be provided in any order") {
        val src = """
main {
    struct Enemy {
        ubyte hp
        ubyte x
    }

    sub start() {
        ^^Enemy e = ^^Enemy:[x=20, hp=200]
        main.hp_result = e.hp
        main.x_result = e.x
    }

    ubyte @shared hp_result
    ubyte @shared x_result
}"""
        runVm(src) { vm, allocations ->
            vm.memory.getUB(allocations["main.hp_result"]!!).toInt() shouldBe 200
            vm.memory.getUB(allocations["main.x_result"]!!).toInt() shouldBe 20
        }
    }

    test("omitted array field is zero-initialized") {
        val src = """
main {
    struct Buffer {
        ubyte[4] data
        ubyte flag
    }

    sub start() {
        ^^Buffer b = ^^Buffer:[flag=42]
        main.r0 = b.data[0]
        main.r1 = b.data[1]
        main.r2 = b.data[2]
        main.r3 = b.data[3]
        main.flag_result = b.flag
    }

    ubyte @shared r0
    ubyte @shared r1
    ubyte @shared r2
    ubyte @shared r3
    ubyte @shared flag_result
}"""
        runVm(src) { vm, allocations ->
            vm.memory.getUB(allocations["main.r0"]!!).toInt() shouldBe 0
            vm.memory.getUB(allocations["main.r1"]!!).toInt() shouldBe 0
            vm.memory.getUB(allocations["main.r2"]!!).toInt() shouldBe 0
            vm.memory.getUB(allocations["main.r3"]!!).toInt() shouldBe 0
            vm.memory.getUB(allocations["main.flag_result"]!!).toInt() shouldBe 42
        }
    }

    test("named initializer works inside array of struct pointers") {
        val src = """
main {
    struct Enemy {
        ubyte hp
        ubyte x
    }

    sub start() {
        ^^Enemy[] @shared enemies = [ ^^Enemy:[hp=11], ^^Enemy:[x=22] ]
        main.hp_result = enemies[0].hp
        main.x_result = enemies[0].x
        main.hp_result2 = enemies[1].hp
        main.x_result2 = enemies[1].x
    }

    ubyte @shared hp_result
    ubyte @shared x_result
    ubyte @shared hp_result2
    ubyte @shared x_result2
}"""
        runVm(src) { vm, allocations ->
            vm.memory.getUB(allocations["main.hp_result"]!!).toInt() shouldBe 11
            vm.memory.getUB(allocations["main.x_result"]!!).toInt() shouldBe 0
            vm.memory.getUB(allocations["main.hp_result2"]!!).toInt() shouldBe 0
            vm.memory.getUB(allocations["main.x_result2"]!!).toInt() shouldBe 22
        }
    }

    test("duplicate field name in named struct initializer is rejected") {
        val src = """
main {
    struct Enemy {
        ubyte hp
        ubyte x
    }

    sub start() {
        ^^Enemy e = ^^Enemy:[hp=1, hp=2]
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors) shouldBe null
        errors.errors.size shouldBeGreaterThan 0
        errors.errors.any { it.contains("duplicate field name 'hp'") } shouldBe true
    }

    test("unknown field name in named struct initializer is rejected") {
        val src = """
main {
    struct Enemy {
        ubyte hp
        ubyte x
    }

    sub start() {
        ^^Enemy e = ^^Enemy:[hp=1, foo=2]
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors) shouldBe null
        errors.errors.size shouldBeGreaterThan 0
        errors.errors.any { it.contains("unknown field name 'foo'") } shouldBe true
    }

    test("mixed positional and named struct initializer is rejected") {
        val src = """
main {
    struct Enemy {
        ubyte hp
        ubyte x
    }

    sub start() {
        ^^Enemy e = ^^Enemy:[100, x=2]
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors) shouldBe null
        (errors.errors.size + errors.printedErrors.size) shouldBeGreaterThan 0
    }
})
