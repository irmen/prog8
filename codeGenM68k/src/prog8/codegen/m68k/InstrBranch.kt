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

import prog8.intermediate.CodeReference
import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction
import prog8.intermediate.Opcode
import prog8.intermediate.intNumber
import prog8.intermediate.requireImmediateInt
import prog8.intermediate.requireSrcA
import prog8.intermediate.requireSrcB
import prog8.intermediate.requireTarget

internal fun AsmGen.translateBranch(insn: IRInstruction) {
    val label: String = when (val target = insn.requireTarget()) {
        is CodeReference.Label -> fixNameSymbols(target.name)
        is CodeReference.Absolute -> target.address.toHex()
        is CodeReference.Indirect -> error("branch needs a static target")
    }

    when (insn.opcode) {
        Opcode.BSTCC -> emitBranch("bcc", label)
        Opcode.BSTCS -> emitBranch("bcs", label)
        Opcode.BSTEQ -> emitBranch("beq", label)
        Opcode.BSTNE -> emitBranch("bne", label)
        Opcode.BSTNEG -> emitBranch("bmi", label)
        Opcode.BSTPOS -> emitBranch("bpl", label)
        Opcode.BSTVC -> emitBranch("bvc", label)
        Opcode.BSTVS -> emitBranch("bvs", label)

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
    val reg = insn.requireSrcA().intNumber
    val imm = insn.requireImmediateInt()
    val s = dtSuffix(type)
    // cmpi.x #imm, <ea> and tst.x <ea> accept memory operands, so the
    // register-file load into d0 is redundant
    if(imm==0)
        emitLine("tst$s  ${regAddr(reg)}")
    else
        emitLine("cmpi$s  #$imm, ${regAddr(reg)}")
    emitBranch(branchOp, label)
}

// === Unsigned comparisons: register vs register ===

private fun AsmGen.cmpBranchUnsignedReg(insn: IRInstruction, label: String, branchOp: String) {
    val type = insn.type ?: IRDataType.BYTE
    val left = insn.requireSrcA().intNumber
    val right = insn.requireSrcB().intNumber
    val s = dtSuffix(type)
    emitLoadD0(left, type)
    emitLine("cmp$s  ${regAddr(right)}, d0")
    emitBranch(branchOp, label)
}

// === Signed comparisons: register vs immediate ===

private fun AsmGen.cmpBranchSignedImm(insn: IRInstruction, label: String, branchOp: String) {
    val type = insn.type ?: IRDataType.BYTE
    val reg = insn.requireSrcA().intNumber
    val imm = insn.requireImmediateInt()
    val s = dtSuffix(type)
    // cmpi.x #imm, <ea> and tst.x <ea> accept memory operands, so the
    // register-file load into d0 is redundant
    if(imm==0)
        emitLine("tst$s  ${regAddr(reg)}")
    else
        emitLine("cmpi$s  #$imm, ${regAddr(reg)}")
    emitBranch(branchOp, label)
}

// === Signed comparisons: register vs register ===

private fun AsmGen.cmpBranchSignedReg(insn: IRInstruction, label: String, branchOp: String) {
    val type = insn.type ?: IRDataType.BYTE
    val left = insn.requireSrcA().intNumber
    val right = insn.requireSrcB().intNumber
    val s = dtSuffix(type)
    emitLoadD0(left, type)
    emitLine("cmp$s  ${regAddr(right)}, d0")
    emitBranch(branchOp, label)
}

private fun AsmGen.emitBranch(branchOp: String, label: String) {
    invalidateD0Cache()
    emitLine("$branchOp  $label")
}
