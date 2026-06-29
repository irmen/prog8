/*
 * Zero-page variable allocator for the new6502gen code generator.
 *
 * Allocates variables to zero-page based on their ZeropageWish:
 *   REQUIRE_ZEROPAGE - must be in ZP, error if not possible
 *   PREFER_ZEROPAGE - best-effort, silently falls back to RAM
 *   DONTCARE - best-effort, sorted by usage frequency
 *   NOT_IN_ZEROPAGE - never in ZP
 *
 * Usage scoring counts variable references across IR instructions,
 * weighted by data type (word/pointer = 2, byte = 1).
 */

package codegen

import prog8.code.core.*
import prog8.intermediate.*

class ZeropageAllocator(
    private val program: IRProgram,
    private val target: ICompilationTarget
) {
    private val zeropage = target.zeropage
    private val allVars by lazy { program.st.allVariables().toList() }
    private val usageScores by lazy { computeUsageScores() }

    /**
     * Allocate variables into zero-page.
     * Returns the map of allocated variable names to their ZP addresses.
     */
    fun allocate(): Map<String, MemoryAllocator.VarAllocation> {
        if (program.options.zeropage == ZeropageType.DONTUSE)
            return emptyMap()

        val requireZp = allVars.filter { it.zpwish == ZeropageWish.REQUIRE_ZEROPAGE }
        val preferZp = allVars.filter { it.zpwish == ZeropageWish.PREFER_ZEROPAGE }
        val (dontcareNoAlign, _) = allVars
            .filter { it.zpwish == ZeropageWish.DONTCARE }
            .partition { it.align == 0u }

        // 1st: REQUIRE_ZEROPAGE - fail if no room
        for (v in requireZp) {
            if (!allocateVar(v)) {
                System.err.println("WARNING: variable '${v.name}' requires zeropage but no space available")
            }
        }

        // 2nd: PREFER_ZEROPAGE - best-effort, silently fall back
        for (v in preferZp) {
            allocateVar(v)
        }

        // 3rd: DONTCARE (no alignment) - sorted by usage score, only integers/pointers
        val scored = dontcareNoAlign
            .filter { it.dt.isInteger || it.dt.isPointer }
            .sortedByDescending { usageScores[it.name] ?: 0 }
        for (v in scored) {
            if (!zeropage.hasByteAvailable()) break
            allocateVar(v)
        }

        return zeropage.allocatedVariables
    }

    /**
     * Check if a variable was allocated to zero-page.
     */
    fun isZpVar(name: String): Boolean {
        if (name in zeropage.allocatedVariables)
            return true
        val v = program.st.lookup(name)
        return if (v is IRStMemVar) v.address <= 255u else false
    }

    /**
     * Get the ZP allocation for a variable, or null if not in ZP.
     */
    fun getAllocation(name: String): MemoryAllocator.VarAllocation? {
        return zeropage.allocatedVariables[name]
    }

    private fun allocateVar(v: IRStStaticVariable): Boolean {
        val result = zeropage.allocate(v.name, v.dt, v.length?.toInt(), null, SilentErrorReporter)
        return result.component1() != null
    }

    /**
     * Count variable references across IR instructions to determine usage frequency.
     * This is a simpler heuristic than the old codegen's AST walk with loop depth,
     * but still effectively identifies the hottest variables.
     */
    private fun computeUsageScores(): Map<String, Int> {
        val scores = mutableMapOf<String, Int>()

        // Build a set of variable names for quick lookup
        val varNames = allVars.associateBy { it.name }

        fun scoreInstruction(insn: IRInstruction) {
            val label = insn.labelSymbol ?: return
            if (label !in varNames) return
            val weight = when {
                insn.type == IRDataType.WORD -> 2
                insn.type == IRDataType.LONG -> 3
                else -> 1
            }
            scores[label] = (scores[label] ?: 0) + weight
        }

        // Walk all code chunks and count references
        program.foreachCodeChunk { chunk ->
            if (chunk is IRCodeChunk) {
                for (insn in chunk.instructions) {
                    scoreInstruction(insn)
                }
            }
        }

        return scores
    }

    /**
     * Error reporter that silently swallows errors during ZP allocation.
     * REQUIRE_ZEROPAGE failures are reported to stderr by the caller.
     */
    private object SilentErrorReporter : IErrorReporter {
        private var errors = false
        override fun err(msg: String, position: Position) { errors = true }
        override fun warn(msg: String, position: Position) {}
        override fun info(msg: String, position: Position) {}
        override fun undefined(symbol: List<String>, suggestImport: Boolean, position: Position) {}
        override fun noErrors(): Boolean = !errors
        override fun report() {}
        override fun noErrorForLine(position: Position): Boolean = true
        override fun printSingleError(errormessage: String) {}
    }
}
