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
 *
 * The signed comparison technique uses overflow correction:
 *   sec / sbc value / bvc + / eor #$80 / +
 * After this, N=0 means the result is >= 0 (left >= right for subtraction left-right).
 */

package prog8.codegen.new6502

import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction
import prog8.intermediate.Opcode

internal fun AsmGen.translateBranch(insn: IRInstruction) {
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

        // Unsigned integer comparison branches: emit CMP then check flags.
//        never emitted here: Opcode.BGT -> translateCmpBranchUnsigned(insn, label, greaterThan = true)
//        never emitted here> Opcode.BGTR -> translateCmpBranchRegUnsigned(insn, label, greaterThan = true)
        Opcode.BGE -> translateCmpBranchUnsigned(insn, label, greaterOrEqual = true)
        Opcode.BGER -> translateCmpBranchRegUnsigned(insn, label, greaterOrEqual = true)
        Opcode.BLT -> translateCmpBranchUnsigned(insn, label, lessThan = true)
//        never emitted here: Opcode.BLE -> translateCmpBranchUnsigned(insn, label, lessOrEqual = true)
//
//        // Signed integer comparison branches: emit CMP then check N/V flags with overflow correction.
        Opcode.BGTS -> translateCmpBranchSigned(insn, label, greaterThan = true)
        Opcode.BGTSR -> translateCmpBranchRegSigned(insn, label, greaterThan = true)
        Opcode.BGES -> translateCmpBranchSigned(insn, label, greaterOrEqual = true)
        Opcode.BGESR -> translateCmpBranchRegSigned(insn, label, greaterOrEqual = true)
        Opcode.BLTS -> translateCmpBranchSigned(insn, label, lessThan = true)
//        never emitted here> Opcode.BLES -> translateCmpBranchSigned(insn, label, lessOrEqual = true)

        else -> TODO("Unknown branch opcode: ${insn.opcode}")
    }
}

private fun AsmGen.translateCmpBranchUnsigned(insn: IRInstruction, label: String,
                                              greaterThan: Boolean = false,
                                              greaterOrEqual: Boolean = false,
                                              lessThan: Boolean = false,
                                              lessOrEqual: Boolean = false) {
    val type = insn.type ?: IRDataType.BYTE
    val reg = insn.reg1 ?: error("branch needs reg1")
    val imm = insn.immediate ?: error("unsigned branch needs immediate")

    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("cmp  #${imm and 0xff}")
            when {
                greaterThan -> {
                    // A > imm: use bcc (A < imm) + beq (A == imm) to skip
                    val skip = makeLabel("bskip")
                    emitLine("bcc  $skip")
                    emitLine("beq  $skip")
                    emitLine("jmp  $label")
                    emitLabel(skip)
                }
                greaterOrEqual -> {
                    // A >= imm: bcs label (branch if carry set, i.e., A >= imm unsigned)
                    emitLine("bcs  $label")
                }
                lessThan -> {
                    // A < imm: bcc label (branch if C=0, i.e., A < imm)
                    emitLine("bcc  $label")
                }
                lessOrEqual -> {
                    // A <= imm: bcc (A < imm) or beq (A == imm)
                    emitLine("bcc  $label")
                    emitLine("beq  $label")
                }
            }
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("cmp  #${imm and 0xff}")
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("sbc  #${(imm shr 8) and 0xff}")
            when {
                greaterThan -> {
                    val skip = makeLabel("bskip")
                    emitLine("bcc  $skip")
                    emitLine("beq  $skip")
                    emitLine("jmp  $label")
                    emitLabel(skip)
                }
                greaterOrEqual -> {
                    emitLine("bcs  $label")
                }
                lessThan -> {
                    emitLine("bcc  $label")
                }
                lessOrEqual -> {
                    emitLine("bcc  $label")
                    emitLine("beq  $label")
                }
            }
        }
        IRDataType.LONG -> translateCmpBranchLongUnsigned(insn, label, greaterThan, greaterOrEqual, lessThan, lessOrEqual)
        IRDataType.FLOAT -> error("float branch not supported")
    }
}

private fun AsmGen.translateCmpBranchLongUnsigned(insn: IRInstruction, label: String,
                                                  greaterThan: Boolean,
                                                  greaterOrEqual: Boolean,
                                                  lessThan: Boolean,
                                                  lessOrEqual: Boolean) {
    val reg = insn.reg1 ?: error("branch needs reg1")
    val imm = insn.immediate ?: error("branch needs immediate")
    for (byteIdx in 0..3) {
        val regPart = if (byteIdx < 2) regAddrByte(reg, byteIdx) else regAddrByte(reg, byteIdx)
        val immPart = (imm shr (byteIdx * 8)) and 0xff
        if (byteIdx == 0) {
            emitLine("lda  $regPart")
            emitLine("cmp  #$immPart")
        } else {
            emitLine("lda  $regPart")
            emitLine("sbc  #$immPart")
        }
    }
    when {
        greaterThan -> {
            val skip = makeLabel("bskip")
            emitLine("bcc  $skip")
            emitLine("beq  $skip")
            emitLine("jmp  $label")
            emitLabel(skip)
        }
        greaterOrEqual -> emitLine("bcs  $label")
        lessThan -> emitLine("bcc  $label")
        lessOrEqual -> {
            emitLine("bcc  $label")
            emitLine("beq  $label")
        }
    }
}

private fun AsmGen.translateCmpBranchRegUnsigned(insn: IRInstruction, label: String,
                                                 greaterThan: Boolean = false,
                                                 greaterOrEqual: Boolean = false) {
    val type = insn.type ?: IRDataType.BYTE
    val reg1 = insn.reg1 ?: error("branch needs reg1")
    val reg2 = insn.reg2 ?: error("reg branch needs reg2")

    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(reg1)}")
            emitLine("cmp  ${regAddrLo(reg2)}")
            if (greaterThan) {
                val skip = makeLabel("bskip")
                emitLine("bcc  $skip")
                emitLine("beq  $skip")
                emitLine("jmp  $label")
                emitLabel(skip)
            } else if (greaterOrEqual) {
                emitLine("bcs  $label")
            }
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(reg1)}")
            emitLine("cmp  ${regAddrLo(reg2)}")
            emitLine("lda  ${regAddrHi(reg1)}")
            emitLine("sbc  ${regAddrHi(reg2)}")
            if (greaterThan) {
                val skip = makeLabel("bskip")
                emitLine("bcc  $skip")
                emitLine("beq  $skip")
                emitLine("jmp  $label")
                emitLabel(skip)
            } else if (greaterOrEqual) {
                emitLine("bcs  $label")
            }
        }
        IRDataType.LONG -> {
            for (byteIdx in 0..3) {
                if (byteIdx == 0) {
                    emitLine("lda  ${regAddrByte(reg1, byteIdx)}")
                    emitLine("cmp  ${regAddrByte(reg2, byteIdx)}")
                } else {
                    emitLine("lda  ${regAddrByte(reg1, byteIdx)}")
                    emitLine("sbc  ${regAddrByte(reg2, byteIdx)}")
                }
            }
            if (greaterThan) {
                val skip = makeLabel("bskip")
                emitLine("bcc  $skip")
                emitLine("beq  $skip")
                emitLine("jmp  $label")
                emitLabel(skip)
            } else if (greaterOrEqual) {
                emitLine("bcs  $label")
            }
        }
        IRDataType.FLOAT -> error("float branch not supported")
    }
}

/**
 * Signed comparison branch with immediate value.
 * Uses the overflow correction technique: sec / sbc / bvc+ / eor #$80 /
 * After correction, N=0 means left >= right (for subtraction left-right).
 */
private fun AsmGen.translateCmpBranchSigned(insn: IRInstruction, label: String,
                                            greaterThan: Boolean = false,
                                            greaterOrEqual: Boolean = false,
                                            lessThan: Boolean = false,
                                            lessOrEqual: Boolean = false) {
    val type = insn.type ?: IRDataType.BYTE
    val reg = insn.reg1 ?: error("branch needs reg1")
    val imm = insn.immediate ?: error("signed branch needs immediate")

    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("sec")
            emitLine("sbc  #${imm and 0xff}")
            when {
                greaterThan -> {
                    // left > right (signed): Z=0 after subtraction, then N=0 after correction
                    val skip = makeLabel("bskip")
                    emitLine("beq  $skip")       // equal: not greater
                    emitLine("bvc  +")
                    emitLine("eor  #${0x80}")    // overflow: correct N flag
                    emitLine("+")
                    emitLine("bpl  $label")      // N=0 after correction: left > right
                    emitLabel(skip)
                }
                greaterOrEqual -> {
                    // left >= right (signed): N=0 after correction
                    emitLine("bvc  +")
                    emitLine("eor  #${0x80}")
                    emitLine("+")
                    emitLine("bpl  $label")
                }
                lessThan -> {
                    // left < right (signed): N=1 after correction
                    emitLine("bvc  +")
                    emitLine("eor  #${0x80}")
                    emitLine("+")
                    emitLine("bmi  $label")
                }
                lessOrEqual -> {
                    // left <= right (signed): Z=1 or N=1 after correction
                    val skip = makeLabel("bskip")
                    emitLine("beq  $label")      // equal: is less-or-equal
                    emitLine("bvc  +")
                    emitLine("eor  #${0x80}")
                    emitLine("+")
                    emitLine("bmi  $label")      // negative: left < right
                    emitLabel(skip)
                }
            }
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("cmp  #${imm and 0xff}")
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("sbc  #${(imm shr 8) and 0xff}")
            when {
                greaterThan -> {
                    val skip = makeLabel("bskip")
                    emitLine("beq  $skip")
                    emitLine("bvc  +")
                    emitLine("eor  #${0x80}")
                    emitLine("+")
                    emitLine("bpl  $label")
                    emitLabel(skip)
                }
                greaterOrEqual -> {
                    emitLine("bvc  +")
                    emitLine("eor  #${0x80}")
                    emitLine("+")
                    emitLine("bpl  $label")
                }
                lessThan -> {
                    emitLine("bvc  +")
                    emitLine("eor  #${0x80}")
                    emitLine("+")
                    emitLine("bmi  $label")
                }
                lessOrEqual -> {
                    val skip = makeLabel("bskip")
                    emitLine("beq  $label")
                    emitLine("bvc  +")
                    emitLine("eor  #${0x80}")
                    emitLine("+")
                    emitLine("bmi  $label")
                    emitLabel(skip)
                }
            }
        }
        IRDataType.LONG -> {
            val skipLabel = makeLabel("bskip")
            // Multi-byte subtraction with overflow correction on MSB only
            for (byteIdx in 0..3) {
                val regPart = regAddrByte(reg, byteIdx)
                val immPart = (imm shr (byteIdx * 8)) and 0xff
                if (byteIdx == 0) {
                    emitLine("lda  $regPart")
                    emitLine("cmp  #$immPart")
                } else if (byteIdx < 3) {
                    emitLine("lda  $regPart")
                    emitLine("sbc  #$immPart")
                } else {
                    emitLine("lda  $regPart")
                    emitLine("sbc  #$immPart")
                    emitLine("bvc  +")
                    emitLine("eor  #${0x80}")
                    emitLine("+")
                }
            }
            when {
                greaterThan -> {
                    emitLine("beq  $skipLabel")
                    emitLine("bpl  $label")
                    emitLabel(skipLabel)
                }
                greaterOrEqual -> emitLine("bpl  $label")
                lessThan -> emitLine("bmi  $label")
                lessOrEqual -> {
                    emitLine("beq  $label")
                    emitLine("bmi  $label")
                }
            }
        }
        IRDataType.FLOAT -> error("float branch not supported")
    }
}

/**
 * Signed comparison branch with register operand.
 * Uses the overflow correction technique on the subtracted result.
 */
private fun AsmGen.translateCmpBranchRegSigned(insn: IRInstruction, label: String,
                                               greaterThan: Boolean = false,
                                               greaterOrEqual: Boolean = false) {
    val type = insn.type ?: IRDataType.BYTE
    val reg1 = insn.reg1 ?: error("branch needs reg1")
    val reg2 = insn.reg2 ?: error("reg branch needs reg2")

    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(reg1)}")
            emitLine("sec")
            emitLine("sbc  ${regAddrLo(reg2)}")
            emitLine("bvc  +")
            emitLine("eor  #${0x80}")
            emitLine("+")
            if (greaterThan) {
                // left > right: Z=0 AND N=0 after correction
                val skip = makeLabel("bskip")
                emitLine("beq  $skip")
                emitLine("bpl  $label")
                emitLabel(skip)
            } else if (greaterOrEqual) {
                emitLine("bpl  $label")
            }
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(reg1)}")
            emitLine("cmp  ${regAddrLo(reg2)}")
            emitLine("lda  ${regAddrHi(reg1)}")
            emitLine("sbc  ${regAddrHi(reg2)}")
            emitLine("bvc  +")
            emitLine("eor  #${0x80}")
            emitLine("+")
            if (greaterThan) {
                val skip = makeLabel("bskip")
                emitLine("beq  $skip")
                emitLine("bpl  $label")
                emitLabel(skip)
            } else if (greaterOrEqual) {
                emitLine("bpl  $label")
            }
        }
        IRDataType.LONG -> {
            for (byteIdx in 0..3) {
                if (byteIdx == 0) {
                    emitLine("lda  ${regAddrByte(reg1, byteIdx)}")
                    emitLine("cmp  ${regAddrByte(reg2, byteIdx)}")
                } else if (byteIdx < 3) {
                    emitLine("lda  ${regAddrByte(reg1, byteIdx)}")
                    emitLine("sbc  ${regAddrByte(reg2, byteIdx)}")
                } else {
                    emitLine("lda  ${regAddrByte(reg1, byteIdx)}")
                    emitLine("sbc  ${regAddrByte(reg2, byteIdx)}")
                    emitLine("bvc  +")
                    emitLine("eor  #${0x80}")
                    emitLine("+")
                }
            }
            if (greaterThan) {
                val skip = makeLabel("bskip")
                emitLine("beq  $skip")
                emitLine("bpl  $label")
                emitLabel(skip)
            } else if (greaterOrEqual) {
                emitLine("bpl  $label")
            }
        }
        IRDataType.FLOAT -> error("float branch not supported")
    }
}
