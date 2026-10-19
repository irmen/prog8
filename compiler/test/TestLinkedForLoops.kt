package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.ast.Node
import prog8.ast.ParentSentinel
import prog8.ast.expressions.IdentifierReference
import prog8.ast.statements.AnonymousScope
import prog8.ast.statements.Block
import prog8.ast.statements.ForLoop
import prog8.ast.statements.Subroutine
import prog8.ast.walk.AstModification
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstVisitor
import prog8.code.core.Position
import prog8.code.source.SourceCode
import prog8.code.target.VMTarget
import prog8.parser.Prog8Parser.parseModule
import prog8.vm.VmRunner
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import kotlin.io.path.readText


class TestLinkedForLoops: FunSpec({

    val outputDir = tempdir().toPath()

    test("parser keeps step on linked loops with via and until") {
        val src = """
            main {
                sub start() {
                    for n in head via .next step 1 {
                    }
                    for n2 in head via .next until sentinel step 2 {
                    }
                }
            }
        """

        val module = parseModule(SourceCode.Text(src), VMTarget())
        val start = module.statements.filterIsInstance<Block>().single { it.name == "main" }
            .statements.filterIsInstance<Subroutine>().single { it.name == "start" }
        val loops = start.statements.filterIsInstance<ForLoop>()

        loops.size shouldBe 2
        loops[0].linkedVia shouldBe "next"
        loops[0].linkedUntil shouldBe null
        loops[0].step shouldNotBe null
        loops[1].linkedVia shouldBe "next"
        loops[1].linkedUntil shouldNotBe null
        loops[1].step shouldNotBe null
    }

    test("linked for loop with via cannot use step") {
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, """
            main {
                struct Node {
                    ^^Node next
                    ubyte value
                }
                sub start() {
                    ^^Node head = memory("head", sizeof(Node), 0)
                    for ^^Node n in head via .next step 1 {
                        n.value = 1
                    }
                }
            }
        """, outputDir, errors, writeAssembly = false) shouldBe null
        errors.errors.any { it.contains("linked-list for loop cannot use 'step'") } shouldBe true
    }

    test("pointer-linked for loop requires an explicit via field") {
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, """
            main {
                struct Node {
                    ^^Node next
                    ubyte value
                }
                sub start() {
                    ^^Node head = memory("head", sizeof(Node), 0)
                    for ^^Node n in head {
                        n.value = 1
                    }
                }
            }
        """, outputDir, errors, writeAssembly = false) shouldBe null
        errors.errors.any { it.contains("pointer-linked for loop requires an explicit 'via .field'") } shouldBe true
    }

    test("linkedUntil is traversed by IAstVisitor and AstWalker") {
        val pos = Position.DUMMY
        val loop = ForLoop(
            IdentifierReference(listOf("node"), pos),
            IdentifierReference(listOf("head"), pos),
            AnonymousScope.empty(),
            null,
            null,
            pos,
            linkedVia = "next",
            linkedUntil = IdentifierReference(listOf("sentinel"), pos)
        )

        val seenByVisitor = mutableListOf<String>()
        val visitor = object : IAstVisitor {
            override fun visit(identifier: IdentifierReference) {
                seenByVisitor += identifier.nameInSource.single()
            }
        }
        loop.accept(visitor)
        seenByVisitor shouldContain "sentinel"

        val seenByWalker = mutableListOf<String>()
        val walker = object : AstWalker() {
            override fun before(identifier: IdentifierReference, parent: Node): Iterable<AstModification> {
                seenByWalker += identifier.nameInSource.single()
                return noModifications
            }
        }
        loop.linkParents(ParentSentinel)
        loop.accept(walker, ParentSentinel)
        seenByWalker shouldContain "sentinel"
    }

    test("explicit custom sentinel identifier terminates traversal") {
        val src = $$"""
            %zeropage basicsafe
            main {
                struct Node {
                    ^^Node next
                    ubyte value
                }
                sub start() {
                    ^^Node n1 = memory("n1", sizeof(Node), 0)
                    ^^Node n2 = memory("n2", sizeof(Node), 0)
                    ^^Node sentinel = memory("sentinel", sizeof(Node), 0)
                    n1.value = 11
                    n2.value = 22
                    sentinel.value = 99
                    n1.next = n2
                    n2.next = sentinel
                    ubyte @shared sum = 0
                    for ^^Node n in n1 via .next until sentinel {
                        sum += n.value
                    }
                    if sum != 33 { sys.exit(7) }
                    pokew($4400, $c0ba)
                    sys.exit(0)
                }
            }
        """
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.readText(), true) { vm ->
            vm.memory.getUW(0x4400u).toInt() shouldBe 0xc0ba
        }
    }

    test("explicit header traversal uses tail sentinel") {
        val src = $$"""
            %zeropage basicsafe
            main {
                struct Node {
                    ^^Node next
                    ubyte value
                }
                struct List {
                    ^^Node head
                    ^^Node last
                }
                sub start() {
                    ^^Node n1 = memory("n1", sizeof(Node), 0)
                    ^^Node n2 = memory("n2", sizeof(Node), 0)
                    ^^Node sentinel = memory("sentinel", sizeof(Node), 0)
                    ^^List list = memory("list", sizeof(List), 0)
                    n1.value = 11
                    n2.value = 22
                    sentinel.value = 99
                    n1.next = n2
                    n2.next = sentinel
                    list.head = n1
                    list.last = sentinel
                    ubyte @shared sum = 0
                    for ^^Node n in list.head via .next until list.last {
                        sum += n.value
                    }
                    if sum != 33 { sys.exit(7) }
                    pokew($4400, $c0ba)
                    sys.exit(0)
                }
            }
        """
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.readText(), true) { vm ->
            vm.memory.getUW(0x4400u).toInt() shouldBe 0xc0ba
        }
    }

    test("explicit node traversal terminates at zero by default") {
        val src = $$"""
            %zeropage basicsafe
            main {
                struct Node {
                    ^^Node next
                    ubyte value
                }
                sub start() {
                    ^^Node n1 = memory("n1", sizeof(Node), 0)
                    ^^Node n2 = memory("n2", sizeof(Node), 0)
                    n1.value = 11
                    n2.value = 22
                    n1.next = n2
                    n2.next = 0
                    ^^Node @shared head = n1
                    ubyte @shared sum = 0
                    for ^^Node n in head via .next {
                        sum += n.value
                    }
                    if sum != 33 { sys.exit(7) }
                    pokew($4400, $c0ba)
                    sys.exit(0)
                }
            }
        """
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.readText(), true) { vm ->
            vm.memory.getUW(0x4400u).toInt() shouldBe 0xc0ba
        }
    }

    test("ordinary array, string and range for loops still compile") {
        val result = compileText(VMTarget(), false, """
            main {
                sub start() {
                    ubyte[] arr = [1,2,3,4]
                    ubyte x
                    for x in arr {
                        x++
                    }
                    for x in arr step -1 {
                        x++
                    }
                    str s = "abcd"
                    ubyte c
                    for c in s {
                        c++
                    }
                    for c in s step -1 {
                        c++
                    }
                    for x in 0 to 3 {
                        x++
                    }
                }
            }
        """, outputDir, writeAssembly = false)
        result shouldNotBe null
    }

    test("until in ordinary for loop is rejected") {
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, """
            main {
                sub start() {
                    ubyte[] arr = [1,2,3]
                    ubyte x
                    for x in arr until 2 {
                        x++
                    }
                }
            }
        """, outputDir, errors, writeAssembly = false) shouldBe null
        errors.errors.any { it.contains("'until' can only be used in linked-list for loops") } shouldBe true
    }

    test("explicit linked traversal requires a pointer link field") {
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, """
            main {
                struct Node {
                    ubyte value
                }
                sub start() {
                    ^^Node head = memory("head", sizeof(Node), 0)
                    for n in head via .value {
                        n.value = 1
                    }
                }
            }
        """, outputDir, errors, writeAssembly = false) shouldBe null
        errors.errors.any { it.contains("linked-list for loop 'via' field must be a pointer to the same node type") } shouldBe true
    }

    test("explicit until accepts address-of header field") {
        val src = $$"""
            %zeropage basicsafe
            main {
                struct Node {
                    ^^Node next
                    ubyte value
                }
                struct List {
                    ^^Node head
                    ^^Node tail
                }
                sub start() {
                    ^^Node n1 = memory("n1", sizeof(Node), 0)
                    ^^Node n2 = memory("n2", sizeof(Node), 0)
                    ^^List list = memory("list", sizeof(List), 0)
                    n1.value = 11
                    n2.value = 22
                    n1.next = n2
                    n2.next = &list.tail
                    list.head = n1
                    list.tail = n2
                    ubyte @shared sum = 0
                    for ^^Node n in list.head via .next until &list.tail {
                        sum += n.value
                    }
                    if sum != 33 { sys.exit(7) }
                    pokew($4400, $c0ba)
                    sys.exit(0)
                }
            }
        """
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.readText(), true) { vm ->
            vm.memory.getUW(0x4400u).toInt() shouldBe 0xc0ba
        }
    }

    test("explicit until accepts address-of untyped pointer header field") {
        val src = $$"""
            %zeropage basicsafe
            main {
                struct Node {
                    ^^Node next
                    ubyte value
                }
                struct List {
                    ^^Node head
                    pointer tail
                }
                sub start() {
                    ^^Node n1 = memory("n1", sizeof(Node), 0)
                    ^^Node n2 = memory("n2", sizeof(Node), 0)
                    ^^List list = memory("list", sizeof(List), 0)
                    n1.value = 11
                    n2.value = 22
                    n1.next = n2
                    n2.next = &list.tail
                    list.head = n1
                    list.tail = n2
                    ubyte @shared sum = 0
                    for ^^Node n in list.head via .next until &list.tail {
                        sum += n.value
                    }
                    if sum != 33 { sys.exit(7) }
                    pokew($4400, $c0ba)
                    sys.exit(0)
                }
            }
        """
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.readText(), true) { vm ->
            vm.memory.getUW(0x4400u).toInt() shouldBe 0xc0ba
        }
    }
})
