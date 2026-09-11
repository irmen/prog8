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

    test("unsigned constant step stops on near-wrap and evaluates dynamic bounds in order") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared count
                ubyte @shared order
                sub getFrom() -> ubyte {
                    order = 1
                    return 254
                }
                sub getTo() -> ubyte {
                    order = order * 10 + 2
                    return 255
                }
                sub start() {
                    ubyte i
                    for i in getFrom() to getTo() step 3 {
                        count++
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.count"]!!) shouldBe 1u
            memory.getUB(allocations["main.order"]!!) shouldBe 12u
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

    test("string literal descending") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                uword @shared result
                sub start() {
                    ubyte c
                    for c in "abcd" step -1 {
                        result = result * 10 + c - 96
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUW(allocations["main.result"]!!) shouldBe 4321u
        }
    }

    test("linked list descending") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            %import lists
            main {
                uword @shared result
                sub start() {
                    struct MyList {
                        ^^lists.FullNode Head
                        pointer Tail
                        ^^lists.FullNode TailPred
                    }
                    ^^MyList mylist = memory("mylist", sizeof(MyList), 0)
                    ^^lists.FullNode n1 = memory("n1", sizeof(lists.FullNode), 0)
                    ^^lists.FullNode n2 = memory("n2", sizeof(lists.FullNode), 0)
                    ^^lists.FullNode n3 = memory("n3", sizeof(lists.FullNode), 0)
                    n1.Pri = 1
                    n2.Pri = 2
                    n3.Pri = 3
                    lists.init(mylist)
                    lists.add_tail(mylist, n1)
                    lists.add_tail(mylist, n2)
                    lists.add_tail(mylist, n3)
                    for ^^lists.FullNode node in mylist step -1 {
                        uword digit = node.Pri as uword
                        result = result * 10 + digit
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUW(allocations["main.result"]!!) shouldBe 321u
        }
    }

    test("constant unsigned byte downto 0") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared count
                ubyte @shared result
                sub start() {
                    ubyte i
                    for i in 5 downto 0 {
                        count++
                        result += i
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.count"]!!) shouldBe 6u
            memory.getUB(allocations["main.result"]!!) shouldBe 15u
        }
    }

    test("constant unsigned byte 0 downto 0") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared count
                ubyte @shared result
                sub start() {
                    ubyte i
                    for i in 0 downto 0 {
                        count++
                        result += i
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.count"]!!) shouldBe 1u
            memory.getUB(allocations["main.result"]!!) shouldBe 0u
        }
    }

    test("constant signed byte downto 0") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                byte @shared count
                byte @shared result
                sub start() {
                    byte i
                    for i in 2 downto 0 {
                        count++
                        result += i
                    }
                }
            }
        """) { memory, allocations ->
            memory.getSB(allocations["main.count"]!!) shouldBe 3
            memory.getSB(allocations["main.result"]!!) shouldBe 3
        }
    }

    test("non-constant uword ascending boundary 65535") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                uword @shared count
                uword @shared first
                uword @shared last
                sub start() {
                    uword @shared a = 65534
                    uword @shared b = 65535
                    uword i
                    for i in a to b {
                        if count == 0
                            first = i
                        last = i
                        count++
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUW(allocations["main.count"]!!) shouldBe 2u
            memory.getUW(allocations["main.first"]!!) shouldBe 65534u
            memory.getUW(allocations["main.last"]!!) shouldBe 65535u
        }
    }

    test("non-constant uword descending boundary 0") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                uword @shared count
                uword @shared first
                uword @shared last
                sub start() {
                    uword @shared a = 1
                    uword @shared b = 0
                    uword i
                    for i in a downto b {
                        if count == 0
                            first = i
                        last = i
                        count++
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUW(allocations["main.count"]!!) shouldBe 2u
            memory.getUW(allocations["main.first"]!!) shouldBe 1u
            memory.getUW(allocations["main.last"]!!) shouldBe 0u
        }
    }

    test("non-constant word ascending signed") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                word @shared count
                word @shared sum
                sub start() {
                    word @shared a = -2
                    word @shared b = 2
                    word i
                    for i in a to b {
                        count++
                        sum += i
                    }
                }
            }
        """) { memory, allocations ->
            memory.getSW(allocations["main.count"]!!) shouldBe 5
            memory.getSW(allocations["main.sum"]!!) shouldBe 0
        }
    }

    test("non-constant word descending signed boundary") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                word @shared count
                word @shared first
                word @shared last
                sub start() {
                    word @shared a = -32767
                    word @shared b = -32768
                    word i
                    for i in a downto b {
                        if count == 0
                            first = i
                        last = i
                        count++
                    }
                }
            }
        """) { memory, allocations ->
            memory.getSW(allocations["main.count"]!!) shouldBe 2
            memory.getSW(allocations["main.first"]!!) shouldBe -32767
            memory.getSW(allocations["main.last"]!!) shouldBe -32768
        }
    }

    test("non-constant ubyte ascending boundary") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared count
                ubyte @shared first
                ubyte @shared last
                sub start() {
                    ubyte @shared a = 254
                    ubyte @shared b = 255
                    ubyte i
                    for i in a to b {
                        if count == 0
                            first = i
                        last = i
                        count++
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.count"]!!) shouldBe 2u
            memory.getUB(allocations["main.first"]!!) shouldBe 254u
            memory.getUB(allocations["main.last"]!!) shouldBe 255u
        }
    }

    test("non-constant ubyte descending boundary") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared count
                ubyte @shared first
                ubyte @shared last
                sub start() {
                    ubyte @shared a = 1
                    ubyte @shared b = 0
                    ubyte i
                    for i in a downto b {
                        if count == 0
                            first = i
                        last = i
                        count++
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.count"]!!) shouldBe 2u
            memory.getUB(allocations["main.first"]!!) shouldBe 1u
            memory.getUB(allocations["main.last"]!!) shouldBe 0u
        }
    }

    test("non-constant byte descending signed") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                byte @shared count
                byte @shared sum
                sub start() {
                    byte @shared a = 1
                    byte @shared b = -1
                    byte i
                    for i in a downto b {
                        count++
                        sum += i
                    }
                }
            }
        """) { memory, allocations ->
            memory.getSB(allocations["main.count"]!!) shouldBe 3
            memory.getSB(allocations["main.sum"]!!) shouldBe 0
        }
    }

    test("non-constant from equals to") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared count
                ubyte @shared value
                sub start() {
                    ubyte @shared a = 7
                    ubyte @shared b = 7
                    ubyte i
                    for i in a to b {
                        count++
                        value = i
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.count"]!!) shouldBe 1u
            memory.getUB(allocations["main.value"]!!) shouldBe 7u
        }
    }

    test("non-constant empty range ascending") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared count
                sub start() {
                    ubyte @shared a = 10
                    ubyte @shared b = 0
                    ubyte i
                    for i in a to b {
                        count++
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.count"]!!) shouldBe 0u
        }
    }

    test("non-constant empty range descending") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte @shared count
                sub start() {
                    ubyte @shared a = 0
                    ubyte @shared b = 10
                    ubyte i
                    for i in a downto b {
                        count++
                    }
                }
            }
        """) { memory, allocations ->
            memory.getUB(allocations["main.count"]!!) shouldBe 0u
        }
    }

    test("non-constant long ascending boundary") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                long @shared count
                long @shared first
                long @shared last
                sub start() {
                    long @shared a = 2147483646
                    long @shared b = 2147483647
                    long i
                    for i in a to b {
                        if count == 0
                            first = i
                        last = i
                        count++
                    }
                }
            }
        """) { memory, allocations ->
            memory.getSL(allocations["main.count"]!!) shouldBe 2
            memory.getSL(allocations["main.first"]!!) shouldBe 2147483646
            memory.getSL(allocations["main.last"]!!) shouldBe 2147483647
        }
    }

    test("non-constant long descending boundary") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                long @shared count
                long @shared first
                long @shared last
                sub start() {
                    long @shared a = -2147483647
                    long @shared b = -2147483648
                    long i
                    for i in a downto b {
                        if count == 0
                            first = i
                        last = i
                        count++
                    }
                }
            }
        """) { memory, allocations ->
            memory.getSL(allocations["main.count"]!!) shouldBe 2
            memory.getSL(allocations["main.first"]!!) shouldBe -2147483647
            memory.getSL(allocations["main.last"]!!) shouldBe -2147483648
        }
    }

    test("non-constant pointer step 1 keeps working") {
        runVm("""
            %zeropage basicsafe
            %option no_sysinit
            main {
                pointer @shared result
                sub start() {
                    pointer @shared a = 0
                    pointer @shared b = 4
                    pointer i
                    pointer sum = 0
                    for i in a to b {
                        sum += i
                    }
                    result = sum
                }
            }
        """) { memory, allocations ->
            memory.getUW(allocations["main.result"]!!) shouldBe 10u
        }
    }
})
