package prog8tests.vm

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldBeInstanceOf
import prog8.ast.expressions.FunctionCallExpression
import prog8.ast.expressions.RangeExpression
import prog8.ast.statements.ForLoop
import prog8.code.ast.PtForLoop
import prog8.code.ast.PtRange
import prog8.code.ast.walkAst
import prog8.code.target.VMTarget
import prog8.intermediate.IRFileReader
import prog8.intermediate.Opcode
import prog8tests.helpers.compileText
import kotlin.io.path.readText

class TestVariableStepForLoopStructure: FunSpec({
    val outputDir = tempdir().toPath()

    val source = """
        %zeropage basicsafe
        %option no_sysinit
        main {
            sub unsigned_step() -> ubyte {
                return 3
            }

            sub signed_step() -> byte {
                return -2
            }

            sub start() {
                ubyte i
                for i in 0 to 10 step unsigned_step() {
                }
                byte j
                for j in 10 downto 0 step signed_step() {
                }
            }
        }
    """.trimIndent()

    test("dynamic step remains side-effecting in compiler and simple AST") {
        val result = compileText(VMTarget(), optimize = false, source, outputDir, writeAssembly = true)!!

        val compilerRange = result.compilerAst.entrypoint.statements
            .filterIsInstance<ForLoop>()
            .first()
            .iterable
            .shouldBeInstanceOf<RangeExpression>()
        compilerRange.step shouldBe instanceOf<FunctionCallExpression>()
        compilerRange.step.hasSideEffects(VMTarget()) shouldBe true

        val simpleRanges = mutableListOf<PtRange>()
        walkAst(result.codegenAst!!) { node, _ ->
            if (node is PtForLoop)
                simpleRanges += node.iterable.shouldBeInstanceOf<PtRange>()
            true
        }
        simpleRanges.size shouldBe 2
        simpleRanges.forEach { range ->
            range.step shouldNotBe null
            range.step.hasSideEffects(VMTarget()) shouldBe true
            range.isSimple() shouldBe false
        }
        simpleRanges[0].step shouldBe instanceOf<prog8.code.ast.PtFunctionCall>()
    }

    test("variable-step IR captures steps and emits direct comparisons with wrap guards") {
        val result = compileText(VMTarget(), optimize = false, source, outputDir, writeAssembly = true)!!
        val irPath = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val program = IRFileReader().read(irPath.readText())
        val instructions = program.allSubs()
            .flatMap { it.chunks }
            .flatMap { it.instructions }
            .toList()

        instructions.count { it.opcode == Opcode.CALL && it.labelSymbol?.substringAfterLast('.') == "unsigned_step" } shouldBe 1
        instructions.count { it.opcode == Opcode.CALL && it.labelSymbol?.substringAfterLast('.') == "signed_step" } shouldBe 1
        instructions.map { it.opcode } shouldContain Opcode.BGTR
        instructions.map { it.opcode } shouldContain Opcode.BGTSR
        instructions.count { it.opcode == Opcode.ADDR } shouldBe 2
        instructions.count { it.opcode == Opcode.BGTR } shouldBeGreaterThan 1
        instructions.count { it.opcode == Opcode.BGTSR } shouldBeGreaterThan 1
    }

    test("unsigned variable-step IR omits direction and descending tails") {
        val unsignedSource = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                sub unsigned_step() -> ubyte {
                    return 3
                }

                sub start() {
                    ubyte i
                    for i in 0 to 10 step unsigned_step() {
                    }
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), optimize = false, unsignedSource, outputDir, writeAssembly = true)!!
        val irPath = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val program = IRFileReader().read(irPath.readText())
        val chunks = program.allSubs().flatMap { it.chunks }.toList()
        val instructions = chunks.flatMap { it.instructions }

        chunks.any { it.label?.contains("for_desc") == true } shouldBe false
        instructions.any { it.opcode == Opcode.BSTNEG } shouldBe false
    }

    test("signed variable-step IR shares the store-next tail") {
        val signedSource = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                sub signed_step() -> byte {
                    return -2
                }

                sub start() {
                    byte i
                    for i in 10 downto 0 step signed_step() {
                    }
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), optimize = false, signedSource, outputDir, writeAssembly = true)!!
        val irPath = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val chunks = IRFileReader().read(irPath.readText()).allSubs().flatMap { it.chunks }.toList()
        val loopvarSymbol = chunks.flatMap { it.instructions }
            .first { it.opcode == Opcode.STOREM && it.labelSymbol?.substringAfterLast('.') == "i" }
            .labelSymbol
        val tailChunks = chunks.filter { chunk ->
            chunk.label != null &&
            chunk.instructions.count { it.opcode == Opcode.STOREM && it.labelSymbol == loopvarSymbol } == 1 &&
                chunk.instructions.count { it.opcode == Opcode.JUMP } == 1
        }

        tailChunks.size shouldBe 1
        val tailLabel = tailChunks.single().label
        tailLabel shouldNotBe null
        chunks.flatMap { it.instructions }.count { it.opcode == Opcode.JUMP && it.labelSymbol == tailLabel } shouldBe 2
    }
})
