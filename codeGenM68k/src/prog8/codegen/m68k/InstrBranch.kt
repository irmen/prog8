/*
 * Branch/compare IR instruction translations for the M68k code generator.
 *
 * Status bit branches (BSTCC/BSTCS/etc.) map directly to M68k conditional
 * branch instructions (BCC/BCS/BEQ/BNE/BMI/BPL/BVC/BVS).
 *
 * Comparison branches (BGT/BGE/BLT/BLE and signed variants) first emit
 * a CMP or CMPI between the operands, then branch using the appropriate
 * M68k condition code:
 *
 *   Unsigned: BHI (C=0 & Z=0), BHS/BCC (C=0), BLO/BCS (C=1), BLS (C=1 | Z=1)
 *   Signed:   BGT (N=V & Z=0), BGE (N=V), BLT (N!=V), BLE (Z=1 | N!=V)
 *
 * M68k CMP/CMPI sets all flags correctly for .B, .W, and .L — no cascading
 * or overflow correction needed (unlike 6502).
 */

package prog8.codegen.m68k

import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction
import prog8.intermediate.Opcode

internal fun AsmGen.translateBranch(insn: IRInstruction) {
    val target = insn.branchTarget
    val ls = insn.labelSymbol

    val label: String = when {
        target != null && target.label != null -> fixNameSymbols(target.label!!)
        ls != null -> fixNameSymbols(ls)
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

        // Unsigned integer comparison branches
        Opcode.BGT -> cmpBranchUnsignedImm(insn, label, "bhi")
        Opcode.BGE -> cmpBranchUnsignedImm(insn, label, "bhs")
        Opcode.BLT -> cmpBranchUnsignedImm(insn, label, "blo")
        Opcode.BLE -> cmpBranchUnsignedImm(insn, label, "bls")

        Opcode.BGTR -> cmpBranchUnsignedReg(insn, label, "bhi")
        Opcode.BGER -> cmpBranchUnsignedReg(insn, label, "bhs")
        // BLTR doesn't exist in IR — uses BGTR with swapped operands

        // Signed integer comparison branches
        Opcode.BGTS -> cmpBranchSignedImm(insn, label, "bgt")
        Opcode.BGES -> cmpBranchSignedImm(insn, label, "bge")
        Opcode.BLTS -> cmpBranchSignedImm(insn, label, "blt")
        Opcode.BLES -> cmpBranchSignedImm(insn, label, "ble")

        Opcode.BGTSR -> cmpBranchSignedReg(insn, label, "bgt")
        Opcode.BGESR -> cmpBranchSignedReg(insn, label, "bge")
        // BLTSR doesn't exist in IR — uses BGTSR with swapped operands

        else -> error("Unknown branch opcode: ${insn.opcode}")
    }
}

// === Unsigned comparisons: register vs immediate ===

private fun AsmGen.cmpBranchUnsignedImm(insn: IRInstruction, label: String, branchOp: String) {
    val type = insn.type ?: IRDataType.BYTE
    val reg = insn.reg1 ?: error("branch needs reg1")
    val imm = insn.immediate ?: error("unsigned branch needs immediate")
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(reg)}, d0")
    emitLine("cmpi$s  #$imm, d0")
    emitLine("$branchOp  $label")
}

// === Unsigned comparisons: register vs register ===

private fun AsmGen.cmpBranchUnsignedReg(insn: IRInstruction, label: String, branchOp: String) {
    val type = insn.type ?: IRDataType.BYTE
    val reg1 = insn.reg1 ?: error("branch needs reg1")
    val reg2 = insn.reg2 ?: error("reg branch needs reg2")
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(reg1)}, d0")
    emitLine("cmp$s  ${regAddr(reg2)}, d0")
    emitLine("$branchOp  $label")
}

// === Signed comparisons: register vs immediate ===

private fun AsmGen.cmpBranchSignedImm(insn: IRInstruction, label: String, branchOp: String) {
    val type = insn.type ?: IRDataType.BYTE
    val reg = insn.reg1 ?: error("branch needs reg1")
    val imm = insn.immediate ?: error("signed branch needs immediate")
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(reg)}, d0")
    emitLine("cmpi$s  #$imm, d0")
    emitLine("$branchOp  $label")
}

// === Signed comparisons: register vs register ===

private fun AsmGen.cmpBranchSignedReg(insn: IRInstruction, label: String, branchOp: String) {
    val type = insn.type ?: IRDataType.BYTE
    val reg1 = insn.reg1 ?: error("branch needs reg1")
    val reg2 = insn.reg2 ?: error("reg branch needs reg2")
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(reg1)}, d0")
    emitLine("cmp$s  ${regAddr(reg2)}, d0")
    emitLine("$branchOp  $label")
}
