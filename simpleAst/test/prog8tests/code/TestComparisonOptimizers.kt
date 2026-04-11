package prog8tests.code

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.ast.*
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.optimize.ComparisonOptimizers
import prog8tests.helpers.DummyMemsizer
import prog8tests.helpers.DummyStringEncoder

/**
 * Tests for ComparisonOptimizers, particularly the optimizeComparisonIdentities function.
 * Verifies that integer/byte comparisons are optimized but float comparisons are NOT
 * (because NaN comparisons don't follow the same rules).
 */
class TestComparisonOptimizers: FunSpec({

    // Helper to create a simple program with a binary expression in a sub
    fun createProgramWithComparison(operator: String, leftType: DataType): PtProgram {
        val program = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val block = PtBlock("main", false, prog8.code.source.SourceCode.Generated("main"), PtBlock.Options(), Position.DUMMY)
        program.add(block)
        
        val sub = PtSub("start", Position.DUMMY)
        block.add(sub)
        
        val left = PtIdentifier("x", leftType, Position.DUMMY)
        val right = PtIdentifier("x", leftType, Position.DUMMY)
        val comparison = PtBinaryExpression(operator, DataType.BOOL, Position.DUMMY)
        comparison.add(left)
        comparison.add(right)
        sub.add(comparison)
        
        return program
    }

    test("optimizeComparisonIdentities: integer x==x should be optimized to true") {
        val program = createProgramWithComparison("==", DataType.UWORD)
        val changes = ComparisonOptimizers.optimizeComparisonIdentities(program)
        
        changes shouldBe 1
        // The comparison should be replaced by PtBool(true)
        val sub = program.children.filterIsInstance<PtBlock>().first()
            .children.filterIsInstance<PtSub>().first()
        sub.children.filterIsInstance<PtBool>().size shouldBe 1
        sub.children.filterIsInstance<PtBool>().first().value shouldBe true
    }

    test("optimizeComparisonIdentities: byte x<x should be optimized to false") {
        val program = createProgramWithComparison("<", DataType.UBYTE)
        val changes = ComparisonOptimizers.optimizeComparisonIdentities(program)
        
        changes shouldBe 1
        val sub = program.children.filterIsInstance<PtBlock>().first()
            .children.filterIsInstance<PtSub>().first()
        sub.children.filterIsInstance<PtBool>().first().value shouldBe false
    }

    test("optimizeComparisonIdentities: word x<=x should be optimized to true") {
        val program = createProgramWithComparison("<=", DataType.WORD)
        val changes = ComparisonOptimizers.optimizeComparisonIdentities(program)
        
        changes shouldBe 1
        val sub = program.children.filterIsInstance<PtBlock>().first()
            .children.filterIsInstance<PtSub>().first()
        sub.children.filterIsInstance<PtBool>().first().value shouldBe true
    }

    test("optimizeComparisonIdentities: float x==x should NOT be optimized (NaN)") {
        val program = createProgramWithComparison("==", DataType.FLOAT)
        val changes = ComparisonOptimizers.optimizeComparisonIdentities(program)
        
        // NaN == NaN is false, so x==x should NOT be optimized to true for floats
        changes shouldBe 0
        val sub = program.children.filterIsInstance<PtBlock>().first()
            .children.filterIsInstance<PtSub>().first()
        // Should still be a PtBinaryExpression, not a PtBool
        sub.children.filterIsInstance<PtBinaryExpression>().size shouldBe 1
    }

    test("optimizeComparisonIdentities: float x<=x should NOT be optimized (NaN)") {
        val program = createProgramWithComparison("<=", DataType.FLOAT)
        val changes = ComparisonOptimizers.optimizeComparisonIdentities(program)
        
        // NaN <= NaN is false, so x<=x should NOT be optimized to true for floats
        changes shouldBe 0
        val sub = program.children.filterIsInstance<PtBlock>().first()
            .children.filterIsInstance<PtSub>().first()
        sub.children.filterIsInstance<PtBinaryExpression>().size shouldBe 1
    }

    test("optimizeComparisonIdentities: float x>=x should NOT be optimized (NaN)") {
        val program = createProgramWithComparison(">=", DataType.FLOAT)
        val changes = ComparisonOptimizers.optimizeComparisonIdentities(program)
        
        // NaN >= NaN is false, so x>=x should NOT be optimized to true for floats
        changes shouldBe 0
        val sub = program.children.filterIsInstance<PtBlock>().first()
            .children.filterIsInstance<PtSub>().first()
        sub.children.filterIsInstance<PtBinaryExpression>().size shouldBe 1
    }

    test("optimizeComparisonIdentities: float x!=x should NOT be optimized (NaN)") {
        val program = createProgramWithComparison("!=", DataType.FLOAT)
        val changes = ComparisonOptimizers.optimizeComparisonIdentities(program)
        
        // NaN != NaN is true, but we should not optimize this as it's NaN-specific behavior
        changes shouldBe 0
        val sub = program.children.filterIsInstance<PtBlock>().first()
            .children.filterIsInstance<PtSub>().first()
        sub.children.filterIsInstance<PtBinaryExpression>().size shouldBe 1
    }
})
