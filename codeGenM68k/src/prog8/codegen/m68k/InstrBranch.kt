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
    val target = insn.requireTarget()
    val label: String = when (target) {
        is CodeReference.Label -> fixNameSymbols(target.name)
        is CodeReference.Absolute -> target.address.toHex()
        is CodeReference.Indirect -> error("branch needs a static target")
    }
    val leavesSubroutine = when (target) {
        is CodeReference.Label -> labelIsOutsideCurrentSub(target.name)
        is CodeReference.Absolute -> true
        is CodeReference.Indirect -> false
    }

    when (insn.opcode) {
        Opcode.BSTCC -> emitBranch("bcc", label, leavesSubroutine)
        Opcode.BSTCS -> emitBranch("bcs", label, leavesSubroutine)
        Opcode.BSTEQ -> emitBranch("beq", label, leavesSubroutine)
        Opcode.BSTNE -> emitBranch("bne", label, leavesSubroutine)
        Opcode.BSTNEG -> emitBranch("bmi", label, leavesSubroutine)
        Opcode.BSTPOS -> emitBranch("bpl", label, leavesSubroutine)
        Opcode.BSTVC -> emitBranch("bvc", label, leavesSubroutine)
        Opcode.BSTVS -> emitBranch("bvs", label, leavesSubroutine)

        // Unsigned integer comparison branches
        Opcode.BGT -> cmpBranchUnsignedImm(insn, label, "bhi", leavesSubroutine)
        Opcode.BGE -> cmpBranchUnsignedImm(insn, label, "bhs", leavesSubroutine)
        Opcode.BLT -> cmpBranchUnsignedImm(insn, label, "blo", leavesSubroutine)
        Opcode.BLE -> cmpBranchUnsignedImm(insn, label, "bls", leavesSubroutine)

        Opcode.BGTR -> cmpBranchUnsignedReg(insn, label, "bhi", leavesSubroutine)
        Opcode.BGER -> cmpBranchUnsignedReg(insn, label, "bhs", leavesSubroutine)
        // BLTR doesn't exist in IR — uses BGTR with swapped operands

        // Signed integer comparison branches
        Opcode.BGTS -> cmpBranchSignedImm(insn, label, "bgt", leavesSubroutine)
        Opcode.BGES -> cmpBranchSignedImm(insn, label, "bge", leavesSubroutine)
        Opcode.BLTS -> cmpBranchSignedImm(insn, label, "blt", leavesSubroutine)
        Opcode.BLES -> cmpBranchSignedImm(insn, label, "ble", leavesSubroutine)

        Opcode.BGTSR -> cmpBranchSignedReg(insn, label, "bgt", leavesSubroutine)
        Opcode.BGESR -> cmpBranchSignedReg(insn, label, "bge", leavesSubroutine)
        // BLTSR doesn't exist in IR — uses BGTSR with swapped operands

        else -> error("Unknown branch opcode: ${insn.opcode}")
    }
}

// === Unsigned comparisons: register vs immediate ===

private fun AsmGen.cmpBranchUnsignedImm(insn: IRInstruction, label: String, branchOp: String, leavesSubroutine: Boolean) {
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
    emitBranch(branchOp, label, leavesSubroutine)
}

// === Unsigned comparisons: register vs register ===

private fun AsmGen.cmpBranchUnsignedReg(insn: IRInstruction, label: String, branchOp: String, leavesSubroutine: Boolean) {
    val type = insn.type ?: IRDataType.BYTE
    val left = insn.requireSrcA().intNumber
    val right = insn.requireSrcB().intNumber
    val s = dtSuffix(type)
    emitLoadD0(left, type)
    emitLine("cmp$s  ${regAddr(right)}, d0")
    emitBranch(branchOp, label, leavesSubroutine)
}

// === Signed comparisons: register vs immediate ===

private fun AsmGen.cmpBranchSignedImm(insn: IRInstruction, label: String, branchOp: String, leavesSubroutine: Boolean) {
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
    emitBranch(branchOp, label, leavesSubroutine)
}

// === Signed comparisons: register vs register ===

private fun AsmGen.cmpBranchSignedReg(insn: IRInstruction, label: String, branchOp: String, leavesSubroutine: Boolean) {
    val type = insn.type ?: IRDataType.BYTE
    val left = insn.requireSrcA().intNumber
    val right = insn.requireSrcB().intNumber
    val s = dtSuffix(type)
    emitLoadD0(left, type)
    emitLine("cmp$s  ${regAddr(right)}, d0")
    emitBranch(branchOp, label, leavesSubroutine)
}

private val inverseBranchOps = mapOf(
    "bcc" to "bcs", "bcs" to "bcc",
    "beq" to "bne", "bne" to "beq",
    "bmi" to "bpl", "bpl" to "bmi",
    "bvc" to "bvs", "bvs" to "bvc",
    "bhi" to "bls", "bls" to "bhi",
    "bhs" to "blo", "blo" to "bhs",
    "bgt" to "ble", "ble" to "bgt",
    "bge" to "blt", "blt" to "bge")

private fun AsmGen.emitBranch(branchOp: String, label: String, leavesSubroutine: Boolean) {
    invalidateD0Cache()
    if (!leavesSubroutine || !frameActive) {
        emitLine("$branchOp  $label")
        return
    }

    val skipLabel = makeLabel("branch_keep_frame")
    emitLine("${inverseBranchOps.getValue(branchOp)}  $skipLabel")
    emitFrameUnlk()
    emitLine("jmp  $label")
    emitLabel(skipLabel)
}
