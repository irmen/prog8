/*
 * Branch/compare IR instruction translations for the new6502gen code generator.
 *
 * The comparison branch instructions in the IR (BGTR, BGT, BGER, BGE, BLT, BLE,
 * and their signed variants) are self-contained - they carry the operands to compare
 * (either two registers, or a register and an immediate). The codegen must emit a CMP
 * between these operands before the flag-checking branch pattern.
 *
 * Two categories of branches:
 *
 * 1. Simple status flag branches (BSTCC/BSTCS/BSTEQ/BSTNE/BSTNEG/BSTPOS/BSTVC/BSTVS):
 *    These map directly to 6502 branch instructions (bcc/bcs/beq/bne/bmi/bpl/bvc/bvs)
 *    and branch based on flags set by the most recent comparison or arithmetic operation.
 *
 * 2. Comparison branches (BGTR/BGT/BGER/BGE/BLT/BLE, BGTSR/BGTS/BGESR/BGES/BLTS/BLES):
 *    These carry their own operands. The codegen emits a CMP between the operands first,
 *    then checks the resulting flags.
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

import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction
import prog8.intermediate.Opcode

fun CodeGenerator.translateBranch(insn: IRInstruction) {
    val target = insn.branchTarget
    val ls = insn.labelSymbol

    val label: String = when {
        target != null && target.label != null -> target.label!!
        ls != null -> ls
        else -> "unknown_target"
    }

    when (insn.opcode) {
        Opcode.BSTCC -> emitLine("bcc  $label")
        Opcode.BSTCS -> emitLine("bcs  $label")
        Opcode.BSTEQ -> emitLine("beq  $label")
        Opcode.BSTNE -> emitLine("bne  $label")
        Opcode.BSTNEG -> emitLine("bmi  $label")
        Opcode.BSTPOS -> emitLine("bpl  $label")
        Opcode.BSTVC -> emitLine("bvc  $label")
        Opcode.BSTVS -> emitLine("bvs  $label")

        // Unsigned comparison branches: emit CMP then check flags
        Opcode.BGT -> {
            emitCmpForBranch(insn)
            emitLine("beq  +")           // equal -> skip (NOT gt)
            emitLine("bcs  $label")      // unsigned above -> branch
            emitLabel("+")
        }
        Opcode.BGTR -> {
            emitCmpForBranch(insn)
            emitLine("beq  +")           // equal -> skip (NOT gt)
            emitLine("bcs  $label")      // unsigned above -> branch
            emitLabel("+")
        }
        Opcode.BGE -> {
            emitCmpForBranch(insn)
            emitLine("bcs  $label")
        }
        Opcode.BGER -> {
            emitCmpForBranch(insn)
            emitLine("bcs  $label")
        }
        Opcode.BLT -> {
            emitCmpForBranch(insn)
            emitLine("bcc  $label")
        }
        Opcode.BLE -> {
            emitCmpForBranch(insn)
            emitLine("beq  $label")
            emitLine("bcc  $label")
        }

        // Signed comparison branches: emit CMP then check N/V flags
        Opcode.BGTSR -> {
            emitCmpForBranch(insn)
            emitSignedBranch("gt", label)
        }
        Opcode.BGTS -> {
            emitCmpForBranch(insn)
            emitSignedBranch("gt", label)
        }
        Opcode.BGESR -> {
            emitCmpForBranch(insn)
            emitSignedBranch("ge", label)
        }
        Opcode.BGES -> {
            emitCmpForBranch(insn)
            emitSignedBranch("ge", label)
        }
        Opcode.BLTS -> {
            emitCmpForBranch(insn)
            emitSignedBranch("lt", label)
        }
        Opcode.BLES -> {
            emitCmpForBranch(insn)
            emitSignedBranch("le", label)
        }

        else -> error("Unknown branch opcode: ${insn.opcode}")
    }
}

private fun CodeGenerator.emitCmpForBranch(insn: IRInstruction) {
    // Emit a CMP between the branch's two operands.
    // The IR branch instructions are self-contained: they carry the operands to compare.
    val r1 = insn.reg1 ?: error("Branch ${insn.opcode} needs reg1")
    val type = insn.type ?: IRDataType.BYTE
    val immediate = insn.immediate

    if(immediate != null) {
        // Register vs immediate comparison
        when (type) {
            IRDataType.BYTE -> {
                emitLine("lda  ${regAddrLo(r1)}")
                emitLine("cmp  #${immediate and 0xff}")
            }
            IRDataType.WORD -> {
                emitLine("lda  ${regAddrLo(r1)}")
                emitLine("cmp  #${immediate and 0xff}")
                emitLine("lda  ${regAddrHi(r1)}")
                emitLine("sbc  #${(immediate shr 8) and 0xff}")
            }
            IRDataType.LONG -> {
                emitLine("lda  ${regAddrLo(r1)}")
                emitLine("cmp  #${immediate and 0xff}")
                emitLine("lda  ${regAddrHi(r1)}")
                emitLine("sbc  #${(immediate shr 8) and 0xff}")
                emitLine("lda  ${regAddrByte(r1, 2)}")
                emitLine("sbc  #${(immediate shr 16) and 0xff}")
                emitLine("lda  ${regAddrByte(r1, 3)}")
                emitLine("sbc  #${(immediate shr 24) and 0xff}")
            }
            else -> throw IllegalArgumentException("no cmp on type $type") 
        }
    } else {
        // Register vs register comparison
        val r2 = insn.reg2 ?: error("Branch ${insn.opcode} needs reg2")
        when (type) {
            IRDataType.BYTE -> {
                emitLine("lda  ${regAddrLo(r1)}")
                emitLine("cmp  ${regAddrLo(r2)}")
            }
            IRDataType.WORD -> {
                emitLine("lda  ${regAddrLo(r1)}")
                emitLine("cmp  ${regAddrLo(r2)}")
                emitLine("lda  ${regAddrHi(r1)}")
                emitLine("sbc  ${regAddrHi(r2)}")
            }
            IRDataType.LONG -> {
                emitLine("lda  ${regAddrLo(r1)}")
                emitLine("cmp  ${regAddrLo(r2)}")
                emitLine("lda  ${regAddrHi(r1)}")
                emitLine("sbc  ${regAddrHi(r2)}")
                emitLine("lda  ${regAddrByte(r1, 2)}")
                emitLine("sbc  ${regAddrByte(r2, 2)}")
                emitLine("lda  ${regAddrByte(r1, 3)}")
                emitLine("sbc  ${regAddrByte(r2, 3)}")
            }
            else -> throw IllegalArgumentException("no cmp on type $type")
        }
    }
}

private fun CodeGenerator.emitSignedBranch(cond: String, label: String) {
    // Standard 6502 signed comparison patterns after CMP.
    // Flags: N = sign of result, V = overflow, Z = zero.
    // Branch on (N == V) for signed >= and >, (N != V) for < and <=.
    // 64tass local labels: + = forward, - = backward, ++ = next-forward, etc.
    when (cond) {
        "gt" -> {
            emitLine("beq  ++")          // equal -> skip (NOT gt)
            emitLine("bvc  +")           // V=0: N is sign
            emitLine("bmi  $label")      // V=1,N=1: inverted -> positive (gt)
            emitLine("jmp  ++")          // V=1,N=0: not gt
            emitLabel("+")              // V=0 landing: check N flag
            emitLine("bpl  $label")      // V=0,N=0: positive -> gt
            emitLabel("+")              // skip point
        }
        "ge" -> {
            emitLine("bvc  +")           // V=0: N is sign
            emitLine("bmi  $label")      // V=1,N=1: inverted -> positive
            emitLine("jmp  ++")          // V=1,N=0: not ge
            emitLabel("+")              // first + label
            emitLine("bpl  $label")      // V=0,N=0: positive -> ge
            emitLabel("+")              // second + label
        }
        "lt" -> {
            emitLine("bvc  +")           // V=0: N is sign
            emitLine("bpl  $label")      // V=1,N=0: inverted -> negative
            emitLine("jmp  ++")          // V=1,N=1: not lt
            emitLabel("+")              // first + label
            emitLine("bmi  $label")      // V=0,N=1: negative -> lt
            emitLabel("+")              // second + label
        }
        "le" -> {
            emitLine("beq  $label")      // equal -> branch
            emitLine("bvc  +")           // V=0: N is sign
            emitLine("bpl  $label")      // V=1,N=0: inverted -> negative
            emitLine("jmp  ++")          // V=1,N=1: not lt
            emitLabel("+")              // first + label
            emitLine("bmi  $label")      // V=0,N=1: negative -> lt
            emitLabel("+")              // second + label
        }
    }
}
