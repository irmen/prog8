package prog8.codegen.m68k

import prog8.code.core.CpuType
import java.util.Collections
import kotlin.math.abs

internal data class MulRecipeTerm(val shift: Int, val sign: Int)

internal data class MulRecipe(
    val factor: Int,           // original positive constant
    val terms: List<MulRecipeTerm>,
    val overallShift: Int      // factor = sum(terms) * 2^overallShift
)

/**
 * Find a shift-add-sub recipe for multiplying by a positive integer constant.
 * Returns null if no profitable recipe exists for the target CPU.
 */
internal fun findMultiplyRecipe(constant: Int, cpu: CpuType): MulRecipe? {
    if (constant <= 0)
        return null
    if (Integer.bitCount(constant) == 1)
        return null // power of two: handled by the IR-level shift pass

    val maxTerms = when (cpu) {
        CpuType.M68000 -> 3
        CpuType.M68020 -> 2
        else -> return null
    }

    // Factor out powers of two; they become a final left shift.
    val overallShift = Integer.numberOfTrailingZeros(constant)
    val oddFactor = constant shr overallShift

    val terms = findOddRecipe(oddFactor, maxTerms) ?: return null
    return MulRecipe(constant, terms, overallShift)
}

private val recipeCache = Collections.synchronizedMap(mutableMapOf<Pair<Int, Int>, List<MulRecipeTerm>?>())

private fun findOddRecipe(odd: Int, maxTerms: Int): List<MulRecipeTerm>? {
    require(odd > 0 && odd % 2 == 1)
    return recipeCache.getOrPut(odd to maxTerms) {
        searchRecipe(odd, maxTerms, emptySet())
    }
}

private fun searchRecipe(target: Int, maxTerms: Int, visited: Set<Int>): List<MulRecipeTerm>? {
    if (target == 0)
        return emptyList()
    if (maxTerms == 0)
        return null
    if (target in visited)
        return null

    // Limit shifts to a sensible range around the magnitude of the target.
    val maxShift = minOf(30, 31 - Integer.numberOfLeadingZeros(maxOf(abs(target), 1)) + 1)

    var best: List<MulRecipeTerm>? = null

    for (shift in 0..maxShift) {
        val value = 1 shl shift

        // +value
        val sub1 = searchRecipe(target - value, maxTerms - 1, visited + target)
        if (sub1 != null) {
            val candidate = listOf(MulRecipeTerm(shift, +1)) + sub1
            if (best == null || candidate.size < best.size) {
                best = candidate
                if (best.size == 1) return best
            }
        }

        // -value
        val sub2 = searchRecipe(target + value, maxTerms - 1, visited + target)
        if (sub2 != null) {
            val candidate = listOf(MulRecipeTerm(shift, -1)) + sub2
            if (best == null || candidate.size < best.size) {
                best = candidate
                if (best.size == 1) return best
            }
        }
    }
    return best
}
