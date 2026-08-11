package prog8tests.vm

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import prog8.code.target.VMTarget
import prog8.intermediate.IRFileReader
import prog8.vm.Memory
import prog8.vm.VmRunner
import prog8.vm.VmVariableAllocator
import prog8tests.helpers.compileText
import kotlin.io.path.readText

class TestVariableStepForLoops: FunSpec({
    val outputDir = tempdir().toPath()

    fun runVm(source: String, check: (Memory, Map<String, UInt>) -> Unit) {
        val result = compileText(VMTarget(), true, source.trimIndent(), outputDir, writeAssembly = true)!!
        val irFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irSource = irFile.readText()
        val irProgram = IRFileReader().read(irSource)
        irProgram.st.stripAllPrefixes()
        val allocations = VmVariableAllocator(
            irProgram.st,
            irProgram.encoding,
            irProgram.options.compTarget
        ).allocations

        VmRunner().runAndTestProgram(irSource) { vm ->
            check(vm.memory, allocations)
        }
    }

    test("unsigned byte ascending") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared result
                sub start() {
                    ubyte @shared step = 3
                    ubyte i
                    ubyte sum = 0
                    for i in 0 to 10 step step {
                        sum += i
                    }
                    result = sum
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.result"]!!) shouldBe 18u
        }
    }

    test("signed negative step with unsigned byte descending") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared result
                sub start() {
                    byte @shared step = -2
                    ubyte i
                    ubyte sum = 0
                    for i in 10 downto 0 step step {
                        sum += i
                    }
                    result = sum
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.result"]!!) shouldBe 30u
        }
    }

    test("unsigned word ascending") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                uword @shared result
                sub start() {
                    uword @shared step = 100
                    uword i
                    uword sum = 0
                    for i in 0 to 500 step step {
                        sum += i
                    }
                    result = sum
                }
            }
        """) { memory, allocations ->
            memory.getUW(allocations["main.result"]!!) shouldBe 1500u
        }
    }

    test("signed word ascending") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                word @shared count
                word @shared result
                sub start() {
                    word @shared step = 4
                    word i
                    word sum = 0
                    for i in -10 to 10 step step {
                        count += 1
                        sum += i
                    }
                    result = sum
                }
            }
        """) { memory, allocations ->
            memory.getSW(allocations["main.count"]!!) shouldBe 6
            memory.getSW(allocations["main.result"]!!) shouldBe 0
        }
    }

    test("zero step") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared result
                sub start() {
                    ubyte @shared step = 0
                    ubyte i
                    for i in 0 to 10 step step {
                        result++
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.result"]!!) shouldBe 0u
        }
    }

    test("wrong direction positive and negative") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared positive
                ubyte @shared negative
                sub start() {
                    ubyte @shared positiveStep = 1
                    byte @shared negativeStep = -1
                    ubyte i
                    for i in 10 to 0 step positiveStep {
                        positive++
                    }
                    for i in 0 to 10 step negativeStep {
                        negative++
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.positive"]!!) shouldBe 0u
            memory.getUB(allocations["main.negative"]!!) shouldBe 0u
        }
    }

    test("from equals to") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared result
                sub start() {
                    ubyte @shared step = 5
                    ubyte i
                    for i in 7 to 7 step step {
                        result++
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.result"]!!) shouldBe 1u
        }
    }

    test("body mutation of step does not change the loop increment") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared result
                ubyte @shared step = 2
                sub start() {
                    ubyte i
                    for i in 0 to 10 step step {
                        result += i
                        step = 99
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.result"]!!) shouldBe 30u
        }
    }

    test("fixed width ubyte wrap stops at the bound") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared result
                sub start() {
                    ubyte @shared step = 3
                    ubyte i
                    for i in 254 to 255 step step {
                        result++
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.result"]!!) shouldBe 1u
        }
    }

    test("nested break exits only the inner loop") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared result
                sub start() {
                    ubyte @shared step = 1
                    ubyte i
                    ubyte j
                    for i in 0 to 5 step step {
                        for j in 0 to 5 step step {
                            result++
                            if j == 2
                                break
                        }
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.result"]!!) shouldBe 18u
        }
    }

    test("non-constant bounds") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared result
                ubyte @shared a = 1
                ubyte @shared b = 9
                sub start() {
                    ubyte @shared step = 2
                    ubyte i
                    for i in a to b step step {
                        result += i
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.result"]!!) shouldBe 25u
        }
    }

    test("pointer ascending") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                pointer @shared result
                sub start() {
                    pointer @shared step = 2
                    pointer i
                    pointer sum = 0
                    for i in 0 to 6 step step {
                        sum += i
                    }
                    result = sum
                }
            }
        """) { memory, allocations ->
            memory.getUW(allocations["main.result"]!!) shouldBe 12u
        }
    }

    test("for bounds and step are evaluated once in source order") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared order
                ubyte @shared result

                sub side_from() -> ubyte {
                    order = 1
                    return 1
                }

                sub side_to() -> ubyte {
                    order = order * 10 + 2
                    return 5
                }

                sub side_step() -> byte {
                    order = order * 10 + 3
                    return 2
                }

                sub start() {
                    ubyte i
                    order = 0
                    for i in side_from() to side_to() step side_step() {
                        result += i
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.order"]!!) shouldBe 123u
            memory.getUB(allocations["main.result"]!!) shouldBe 9u
        }
    }

    test("continue still performs the next step") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared count
                ubyte @shared result
                sub start() {
                    ubyte @shared step = 1
                    ubyte i
                    for i in 0 to 5 step step {
                        if i == 2
                            continue
                        count++
                        result += i
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.count"]!!) shouldBe 5u
            memory.getUB(allocations["main.result"]!!) shouldBe 13u
        }
    }

    test("long ascending") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                long @shared result
                sub start() {
                    long @shared step = 10000
                    long i
                    long sum = 0
                    for i in 0 to 50000 step step {
                        sum += i
                    }
                    result = sum
                }
            }
        """) { memory, allocations ->
            memory.getSL(allocations["main.result"]!!) shouldBe 150000
        }
    }
})
