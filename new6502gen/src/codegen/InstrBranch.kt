/*
 * Branch/compare IR instruction translations for the new6502gen code generator.
 *
 * Two categories of branches:
 *
 * 1. Simple status flag branches (BSTCC/BSTCS/BSTEQ/BSTNE/BSTNEG/BSTPOS/BSTVC/BSTVS):
 *    These map directly to 6502 branch instructions (bcc/bcs/beq/bne/bmi/bpl/bvc/bvs)
 *    and branch based on flags set by the most recent comparison or arithmetic operation.
 *
 * 2. Comparison branches (BGT/BGE/BLT/BLE, BGTS/BGES/BLTS/BLES):
 *    These expect the flags to have been set by a preceding CMP instruction.
 *    Unsigned variants use simple bcc/bcs patterns.
 *    Signed variants need to check both N and V flags.
 *
 * BGT, BLE need two branches (e.g. bne skip + bcs target) because the 6502
 * doesn't have a single "branch if greater than" instruction.
 *
 * Signed branch logic (N flag vs V flag):
 *   - gt:  Z=0 and (N==V)
 *   - ge:  N==V
 *   - lt:  N!=V
 *   - le:  Z=1 or (N!=V)
 */

package codegen

import prog8.intermediate.*

fun CodeGenerator.translateBranch(insn: IRInstruction) {
    val target = insn.branchTarget
    val ls = insn.labelSymbol

    val label: String = when {
        target != null && target.label != null -> target.label!!
        ls != null -> ls
        else -> "unknown_target"
    }

    when (insn.opcode) {
        Opcode.BSTCC -> emitLine("bcc $label")
        Opcode.BSTCS -> emitLine("bcs $label")
        Opcode.BSTEQ -> emitLine("beq $label")
        Opcode.BSTNE -> emitLine("bne $label")
        Opcode.BSTNEG -> emitLine("bmi $label")
        Opcode.BSTPOS -> emitLine("bpl $label")
        Opcode.BSTVC -> emitLine("bvc $label")
        Opcode.BSTVS -> emitLine("bvs $label")

        Opcode.BGTR -> {
            emitLine("beq +")           // equal → skip
            emitLine("bcs $label")      // unsigned above → branch
            emitLabel("+")
        }
        Opcode.BGE -> emitLine("bcs $label")
        Opcode.BLT -> emitLine("bcc $label")
        Opcode.BLE -> {
            emitLine("beq $label")
            emitLine("bcc $label")
        }

        Opcode.BGTSR -> emitSignedBranch("gt", label)
        Opcode.BGTS -> emitSignedBranch("gt", label)
        Opcode.BGER -> emitSignedBranch("ge", label)
        Opcode.BGESR -> emitSignedBranch("ge", label)
        Opcode.BGES -> emitSignedBranch("ge", label)
        Opcode.BLTS -> emitSignedBranch("lt", label)
        Opcode.BLES -> emitSignedBranch("le", label)

        else -> error("Unknown branch opcode: ${insn.opcode}")
    }
}

private fun CodeGenerator.emitSignedBranch(cond: String, label: String) {
    // Standard 6502 signed comparison patterns after CMP.
    // Flags: N = sign of result, V = overflow, Z = zero.
    // Branch on (N == V) for signed >= and >, (N != V) for < and <=.
    // 64tass local labels: + = forward, - = backward, ++ = next-forward, etc.
    when (cond) {
        "gt" -> {
            emitLine("beq ++")          // equal → skip (NOT gt)
            emitLine("bvc +")           // V=0: N is sign
            emitLine("bmi $label")      // V=1,N=1: inverted → positive (gt)
            emitLine("jmp ++")          // V=1,N=0: not gt
            emitLabel("+")              // V=0 landing: check N flag
            emitLine("bpl $label")      // V=0,N=0: positive → gt
            emitLabel("+")              // skip point
        }
        "ge" -> {
            emitLine("bvc +")           // V=0: N is sign
            emitLine("bmi $label")      // V=1,N=1: inverted → positive
            emitLine("jmp ++")          // V=1,N=0: not ge
            emitLabel("+")              // first + label
            emitLine("bpl $label")      // V=0,N=0: positive → ge
            emitLabel("+")              // second + label
        }
        "lt" -> {
            emitLine("bvc +")           // V=0: N is sign
            emitLine("bpl $label")      // V=1,N=0: inverted → negative
            emitLine("jmp ++")          // V=1,N=1: not lt
            emitLabel("+")              // first + label
            emitLine("bmi $label")      // V=0,N=1: negative → lt
            emitLabel("+")              // second + label
        }
        "le" -> {
            emitLine("beq $label")      // equal → branch
            emitLine("bvc +")           // V=0: N is sign
            emitLine("bpl $label")      // V=1,N=0: inverted → negative
            emitLine("jmp ++")          // V=1,N=1: not lt
            emitLabel("+")              // first + label
            emitLine("bmi $label")      // V=0,N=1: negative → lt
            emitLabel("+")              // second + label
        }
    }
}
