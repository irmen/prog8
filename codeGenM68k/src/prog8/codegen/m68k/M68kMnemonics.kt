package prog8.codegen.m68k

// Central mnemonic classification for the M68k assembly-level peephole optimizer.
// Single source of truth: every pass queries these sets instead of declaring its own
// copy, so a missing mnemonic only has to be added in one place.

/** Unconditional transfers and every instruction that may fault or leave the trace. */
internal val unconditionalTransfers = setOf(
    "bra", "bsr", "jmp", "jsr", "rts", "rtr", "rte", "trap", "trapv", "chk", "illegal"
)

/** All conditional branch instructions (flags-based). */
internal val conditionBranches = setOf(
    "bcc", "bcs", "bvs", "bvc", "bmi", "bpl", "bne", "beq", "bhi", "bls",
    "bhs", "blo", "bgt", "ble", "bge", "blt"
)

/** All DBcc style dispatch instructions. */
internal val dbccBranches = setOf(
    "dbcc", "dbcs", "dbhi", "dbhs", "dblo", "dbls", "dbne", "dbnz", "dbeq", "dbz",
    "dbmi", "dbpl", "dbge", "dblt", "dbgt", "dble", "dbvc", "dbvs", "dbtst", "dbset",
    "dbclr", "dbchg", "dbf", "dbra"
)

/** Everything that does not preserve the fall-through register state. */
internal val controlTransfers: Set<String> = unconditionalTransfers + conditionBranches + dbccBranches

/** Control transfers that end a tracked value even on the fall-through path: everything
 *  except conditional branches, which resume at their (tracked) label instead. */
internal val controlTransfersNoConditionals: Set<String> = unconditionalTransfers + dbccBranches

/** Instruction mnemonics that unconditionally write the N flag (and Z/C), so the earlier state is gone. */
internal val nFlagOverwriters = setOf(
    "move", "moveq", "clr", "tst", "cmp", "cmpa", "cmpi", "cmpm",
    "add", "sub", "addi", "subi", "addq", "subq", "addx", "subx",
    "and", "andi", "or", "ori", "eor", "eori",
    "asl", "asr", "lsl", "lsr", "rol", "ror", "roxl", "roxr",
    "neg", "negx", "not", "ext", "swap", "tas",
    "mulu", "muls", "mulsl", "divu", "divs", "divsl", "chk"
)

/** Instruction mnemonics that define all four condition flags (N, Z, V, C) from their own result
 *  on every CPU and do not read any of them, so an earlier flag write cannot be observed. `mulu`,
 *  `muls`, `divu`, `divs` and `chk` are excluded: N/Z are undefined for the multiplies on 68000.
 *  `add`/`sub`/`addq`/`subq` are excluded because their ADDA/SUBA forms (address-register
 *  destination) leave the CCR untouched; their data-register forms are handled explicitly in
 *  the callers that need operand-aware classification. */
internal val allFlagOverwriters = setOf(
    "move", "moveq", "clr", "tst", "cmp", "cmpa", "cmpi", "cmpm",
    "addi", "subi", "addx", "subx",
    "and", "andi", "or", "ori", "eor", "eori",
    "asl", "asr", "lsl", "lsr", "rol", "ror", "roxl", "roxr",
    "neg", "negx", "not", "ext", "swap", "tas"
)

/** Instruction mnemonics that leave the N flag untouched and do not read it: the fall-through
 *  path still sees the rewritten flags (which for Z/C are identical to before, and FPU ops only
 *  touch FPSR), so scanning may continue past them. */
internal val nFlagOpaque = setOf(
    "lea", "movea", "adda", "suba", "exg", "link", "unlk", "nop", "movem",
    "btst", "bset", "bclr", "bchg", "st", "sf",
    "scc", "scs", "seq", "sne", "svc", "svs", "shi", "sls", "shs", "slo",
    "fmove", "fadd", "fsub", "fmul", "fdiv", "ftst", "fcmp",
    "fsqrt", "fabs", "fneg", "fintrz", "fgetexp", "fgetman",
    "dc", "ds", "dcb", "even", "cnop", "align", "org", "section", "xdef", "xref", "public", "global", "offset"
)

/** True when an instruction line's mnemonic base transfers control away from the fall-through path. */
internal fun String.isControlTransfer(): Boolean =
    instructionPart().substringBefore(' ').substringBefore('\t').substringBefore('.').trim().lowercase() in controlTransfers

/** Mnemonic without a size suffix, e.g. "move" for "move.l". */
internal fun String.mnemonic(): String =
    instructionPart().substringBefore(' ').substringBefore('\t').substringBefore('.').let { if (it.lastOrNull() == '.') it.dropLast(1) else it }.trim().lowercase()

/** Full mnemonic including size suffix, e.g. "move.l". */
internal fun String.instructionName(): String =
    instructionPart().substringBefore(' ').substringBefore('\t').trim().lowercase()
