package prog8.compiler.astprocessing

// Checks the m68k register rule on hand-written bodies: every callee-saved or reserved register
// the body writes must be pushed before and popped after. Text-based and heuristic: writes made
// through anything except an AmigaOS vector call, or through aliases, are not seen.
// A jsr/jmp through (a6) is an AmigaOS library call: only D2-D5 and A2-A5 survive it, so D6 changes.
internal object M68kSaveRules {
    private val watched = (2..7).map { "d$it" } + (2..5).map { "a$it" } + (2..7).map { "fp$it" }
    private val registerName = Regex("^(d[0-7]|a[0-7]|fp[0-7])$")
    private val pushOperand = Regex("^-\\((sp|a7)\\)$")
    private val autoIncDec = Regex("^(?:\\((a[0-7])\\)\\+|-\\((a[0-7])\\))$")
    private val labelPrefix = Regex("^[^\\s]+:\\s*")
    private val noWrite = setOf("bra", "bsr", "bcc", "bcs", "beq", "bne", "bhi", "bls", "bpl", "bmi",
        "bge", "blt", "bgt", "ble", "bvc", "bvs", "blo", "bhs", "btst", "tst", "nop")
    private val linkUnlk = setOf("link", "unlk")
    private val directives = setOf("dc", "ds", "even", "align", "section", "include", "alias")

    // returns: result registers (lowercase names) that the body may write without saving.
    fun unsavedWrites(asm: String, returns: Set<String>): Set<String> {
        val saved = mutableSetOf<String>()
        val written = mutableSetOf<String>()
        for (raw in asm.lineSequence()) {
            val line = raw.substringBefore(';').trim().replace(labelPrefix, "")
            if (line.isEmpty()) continue
            val mnemonic = line.substringBefore(' ').substringBefore('\t').lowercase()
            val base = mnemonic.substringBefore('.')
            if (base in directives) continue
            val rest = line.substring(mnemonic.length).trim()
            val operands = if (rest.isEmpty()) emptyList() else rest.split(',').map { it.trim().lowercase() }
            if (operands.size == 2 && pushOperand.matches(operands[1])) {
                saved += expand(operands[0])
                continue
            }
            if ((base == "jsr" || base == "jmp") && operands.any { "(a6" in it }) {
                // AmigaOS library call through A6: D6 is volatile across it, so it counts as a write.
                written += "d6"
                continue
            }
            if (base in noWrite || base.startsWith("cmp") || base.startsWith("fcmp") || base.startsWith("ftst")) continue
            when {
                base in linkUnlk -> written += operands.filter { registerName.matches(it) }
                base == "movem" && operands.size == 2 && !operands[1].contains('(') -> written += expand(operands[1])
                base == "exg" -> written += operands
                base.startsWith("db") && operands.isNotEmpty() -> written += expand(operands[0])
                operands.isNotEmpty() -> writeTarget(operands.last(), written)
            }
        }
        return (written intersect watched.toSet()) - saved - returns
    }

    private fun writeTarget(operand: String, written: MutableSet<String>) {
        if (registerName.matches(operand)) {
            written += operand
            return
        }
        autoIncDec.find(operand)?.let { m -> written += (m.groupValues[1] + m.groupValues[2]) }
    }

    private fun expand(list: String): Set<String> = list.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        .flatMap { token ->
            val dash = token.indexOf('-')
            if (dash < 0) return@flatMap listOf(token)
            val lo = token.substring(0, dash)
            val hi = token.substring(dash + 1)
            val prefix = lo.takeWhile { !it.isDigit() }
            val from = lo.drop(prefix.length).toIntOrNull()
            val to = if (hi.startsWith(prefix)) hi.drop(prefix.length).toIntOrNull() else null
            if (from == null || to == null) listOf(token) else (from..to).map { "$prefix$it" }
        }
        .filter { registerName.matches(it) }
        .toSet()
}
