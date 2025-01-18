package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import prog8.ast.Program
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.code.source.SourceCode
import prog8.code.target.C64Target
import prog8.code.target.VMTarget
import prog8.compiler.CallGraph
import prog8.parser.Prog8Parser.parseModule
import prog8.vm.VmRunner
import prog8tests.helpers.*
import kotlin.io.path.readText

class TestCallgraph: FunSpec({
    test("testGraphForEmptySubs") {
        val sourcecode = """
            %import conv
            main {
                sub start() {
                }
                sub empty() {
                }
            }
        """
        val result = compileText(C64Target(), false, sourcecode)!!
        val graph = CallGraph(result.compilerAst)

        graph.imports.size shouldBe 1
        graph.importedBy.size shouldBe 1
        val toplevelModule = result.compilerAst.toplevelModule
        val importedModule = graph.imports.getValue(toplevelModule).single()
        importedModule.name shouldBe "conv"
        val importedBy = graph.importedBy.getValue(importedModule).single()
        importedBy.name.startsWith("on_the_fly_test") shouldBe true

        graph.unused(toplevelModule) shouldBe false
        graph.unused(importedModule) shouldBe false

        val mainBlock = toplevelModule.statements.filterIsInstance<Block>().single{it.name=="main"}
        for(stmt in mainBlock.statements) {
            val sub = stmt as Subroutine
            graph.calls shouldNotContainKey sub
            graph.calledBy shouldNotContainKey sub

            if(sub === result.compilerAst.entrypoint)
                withClue("start() should always be marked as used to avoid having it removed") {
                    graph.unused(sub) shouldBe false
                }
            else
                graph.unused(sub) shouldBe true
        }
    }

    test("reference to empty sub") {
        val sourcecode = """
            %import conv
            main {
                sub start() {
                    uword xx = &empty
                    xx++
                }
                sub empty() {
                }
            }
        """
        val result = compileText(C64Target(), false, sourcecode)!!
        val graph = CallGraph(result.compilerAst)

        graph.imports.size shouldBe 1
        graph.importedBy.size shouldBe 1
        val toplevelModule = result.compilerAst.toplevelModule
        val importedModule = graph.imports.getValue(toplevelModule).single()
        importedModule.name shouldBe "conv"
        val importedBy = graph.importedBy.getValue(importedModule).single()
        importedBy.name.startsWith("on_the_fly_test") shouldBe true

        graph.unused(toplevelModule) shouldBe false
        graph.unused(importedModule) shouldBe false

        val mainBlock = toplevelModule.statements.filterIsInstance<Block>().single{it.name=="main"}
        val startSub = mainBlock.statements.filterIsInstance<Subroutine>().single{it.name=="start"}
        val emptySub = mainBlock.statements.filterIsInstance<Subroutine>().single{it.name=="empty"}

        graph.calls shouldNotContainKey startSub
        graph.calledBy shouldNotContainKey emptySub
        withClue("empty doesn't call anything") {
            graph.calls shouldNotContainKey emptySub
        }
        withClue( "start doesn't get called (except as entrypoint ofc.)") {
            graph.calledBy shouldNotContainKey startSub
        }
    }

    test("allIdentifiers separates for different positions of the IdentifierReferences") {
        val sourcecode = """
            main {
                sub start() {
                    uword x1 = &empty
                    uword x2 = &empty
                    empty()
                }
                sub empty() {
                    %asm {{
                        nop
                    }}
                }
            }
        """
        val result = compileText(C64Target(), false, sourcecode)!!
        val graph = CallGraph(result.compilerAst)
        graph.allIdentifiers.size shouldBeGreaterThanOrEqual 5
        val empties = graph.allIdentifiers.filter { it.first.nameInSource==listOf("empty") }
        println(graph.allIdentifiers)
        empties.size shouldBe 3
        empties[0].first.position.line shouldBe 4
        empties[1].first.position.line shouldBe 5
        empties[2].first.position.line shouldBe 6
    }

    test("checking block and subroutine names usage in assembly code") {
        val source = """
            main {
                sub start() {
                    %asm {{
                        lda  #<blockname
                        lda  #<blockname.subroutine
            correctlabel:
                        nop
                    }}
                }
            
            }
            
            blockname {
                sub subroutine() {
                    @(1000) = 0
                }
            
                sub correctlabel() {
                    @(1000) = 0
                }
            }
            
            ; all block and subroutines below should NOT be found in asm because they're only substrings of the names in there
            locknam {
                sub rout() {
                    @(1000) = 0
                }
            
                sub orrectlab() {
                    @(1000) = 0
                }
            }"""
        val module = parseModule(SourceCode.Text(source))
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        program.addModule(module)
        val callgraph = CallGraph(program)
        val blockMain = program.allBlocks.single { it.name=="main" }
        val blockBlockname = program.allBlocks.single { it.name=="blockname" }
        val blockLocknam = program.allBlocks.single { it.name=="locknam" }
        val subStart = blockMain.statements.filterIsInstance<Subroutine>().single { it.name == "start" }
        val subSubroutine = blockBlockname.statements.filterIsInstance<Subroutine>().single { it.name == "subroutine" }
        val subCorrectlabel = blockBlockname.statements.filterIsInstance<Subroutine>().single { it.name == "correctlabel" }
        val subRout = blockLocknam.statements.filterIsInstance<Subroutine>().single { it.name == "rout" }
        val subOrrectlab = blockLocknam.statements.filterIsInstance<Subroutine>().single { it.name == "orrectlab" }
        callgraph.unused(blockMain) shouldBe false
        callgraph.unused(blockBlockname) shouldBe false
        callgraph.unused(blockLocknam) shouldBe true
        callgraph.unused(subStart) shouldBe false
        callgraph.unused(subSubroutine) shouldBe false
        callgraph.unused(subCorrectlabel) shouldBe false
        callgraph.unused(subRout) shouldBe true
        callgraph.unused(subOrrectlab) shouldBe true
    }

    test("recursion detection") {
        val source="""
            main {
                sub start() {
                    recurse1()
                }
                sub recurse1() {
                    recurse2()
                }
                sub recurse2() {
                    start()
                }
            }"""
        val module = parseModule(SourceCode.Text(source))
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        program.addModule(module)
        val callgraph = CallGraph(program)
        val errors = ErrorReporterForTests()
        callgraph.checkRecursiveCalls(errors)
        errors.errors.size shouldBe 0
        errors.warnings.size shouldBe 4
        errors.warnings[0] shouldContain "contains recursive subroutines"
    }

    test("no recursion warning if reference isn't a call") {
        val source="""
            main {
                sub start() {
                    recurse1()
                }
                sub recurse1() {
                    recurse2()
                }
                sub recurse2() {
                    uword @shared address = &start
                }
            }"""
        val module = parseModule(SourceCode.Text(source))
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        program.addModule(module)
        val callgraph = CallGraph(program)
        val errors = ErrorReporterForTests()
        callgraph.checkRecursiveCalls(errors)
        errors.errors.size shouldBe 0
        errors.warnings.size shouldBe 0
    }

    test("subs that aren't called but only used as scope aren't unused (6502)") {
        val src="""
main {
    sub start() {
        cx16.r0L = main.scopesub.variable
        cx16.r1L = main.scopesub.array[1]
        cx16.r0++
    }

    sub scopesub() {
        ubyte variable
        ubyte[] array = [1,2,3]

        variable++
    }
}"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        val result = compileText(C64Target(), true, src, errors=errors)!!
        val callgraph = CallGraph(result.compilerAst)
        val scopeSub = result.compilerAst.entrypoint.lookup(listOf("main", "scopesub")) as Subroutine
        scopeSub.name shouldBe "scopesub"
        callgraph.notCalledButReferenced shouldContain scopeSub
        callgraph.unused(scopeSub) shouldBe false

        errors.warnings.any { "unused" in it } shouldBe false
        errors.infos.any { "unused" in it } shouldBe false
    }

    test("subs that aren't called but only used as scope aren't unused (IR/VM)") {
        val src="""
main {
    sub start() {
        cx16.r0L = main.scopesub.variable
        cx16.r1L = main.scopesub.array[1]
        cx16.r0++
    }

    sub scopesub() {
        ubyte variable
        ubyte[] array = [1,2,3]

        variable++
    }
}"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        val result = compileText(VMTarget(), true, src, errors=errors)!!
        val callgraph = CallGraph(result.compilerAst)
        val scopeSub = result.compilerAst.entrypoint.lookup(listOf("main", "scopesub")) as Subroutine
        scopeSub.name shouldBe "scopesub"
        callgraph.notCalledButReferenced shouldContain scopeSub
        callgraph.unused(scopeSub) shouldBe false

        errors.warnings.any { "unused" in it } shouldBe false
        errors.infos.any { "unused" in it } shouldBe false

        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText())
    }

    test("also remove subroutines with names matching IR asm instruction") {
        var src="""
main {
    sub start() {
    }
}

xyz {
    uword buffer_ptr = memory("buffers_stack", 8192, 0)

    sub pop() -> ubyte {            ; pop is also an IR instruction
        return buffer_ptr[2]
    }
}"""
        val result = compileText(VMTarget(), true, src, writeAssembly = true)!!
        val blocks = result.codegenAst!!.allBlocks().toList()
        blocks.any { it.name=="xyz" } shouldBe false
        val result2 = compileText(C64Target(), true, src, writeAssembly = true)!!
        val blocks2 = result2.codegenAst!!.allBlocks().toList()
        blocks2.any { it.name=="xyz" } shouldBe false
    }
})
