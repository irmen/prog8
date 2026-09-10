/*
 * Bitwise and logical IR instruction translations for the M68k code generator.
 *
 * Handles: AND, OR, XOR (register/imm/memory), INV (bitwise not),
 * shifts (LSL, LSR, ASR) by constant or variable count,
 * rotates (ROL, ROR, ROXL, ROXR) and bit manipulation (BITTST, BITSET, BITCLR, BITTOG).
 *
 * M68k has native .B/.W/.L support for all logical operations,
 * including immediate and register-count shifts.
 * Rotates ROL/ROR are true rotates (bit wraps around, no carry involvement for entry).
 * ROXL/ROXR rotate through the extend (X/C) bit.
 */

package prog8.codegen.m68k

import prog8.code.target.Qemu68kTarget
import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction
import prog8.intermediate.Opcode
import prog8.intermediate.intNumber
import prog8.intermediate.requireImmediateInt
import prog8.intermediate.requireIntDest
import prog8.intermediate.requireIntSourceA
import prog8.intermediate.requireMemory

internal fun AsmGen.translateBitwise(insn: IRInstruction) {
    val type = insn.type ?: IRDataType.BYTE

    when (insn.opcode) {
        Opcode.ANDR -> andRegisters(insn.requireIntDest().intNumber, insn.requireIntSourceA().intNumber, type)
        Opcode.AND -> andImmediate(insn.requireIntDest().intNumber, insn.requireImmediateInt(), type)
        Opcode.ANDM -> andMemory(insn.requireIntSourceA().intNumber, resolveMemory(insn.requireMemory()), type)

        Opcode.ORR -> orRegisters(insn.requireIntDest().intNumber, insn.requireIntSourceA().intNumber, type)
        Opcode.OR -> orImmediate(insn.requireIntDest().intNumber, insn.requireImmediateInt(), type)
        Opcode.ORM -> orMemory(insn.requireIntSourceA().intNumber, resolveMemory(insn.requireMemory()), type)

        Opcode.XORR -> xorRegisters(insn.requireIntDest().intNumber, insn.requireIntSourceA().intNumber, type)
        Opcode.XOR -> xorImmediate(insn.requireIntDest().intNumber, insn.requireImmediateInt(), type)
        Opcode.XORM -> xorMemory(insn.requireIntSourceA().intNumber, resolveMemory(insn.requireMemory()), type)

        Opcode.INV -> invertRegister(insn.requireIntDest().intNumber, type)
        Opcode.INVM -> invertMemory(resolveMemory(insn.requireMemory()), type)

        Opcode.ASRN -> arithmeticShiftRightVar(insn.requireIntDest().intNumber, insn.requireIntSourceA().intNumber, type)
        Opcode.LSRN -> logicalShiftRightVar(insn.requireIntDest().intNumber, insn.requireIntSourceA().intNumber, type)
        Opcode.LSLN -> logicalShiftLeftVar(insn.requireIntDest().intNumber, insn.requireIntSourceA().intNumber, type)

        Opcode.ASRNM -> shiftMemoryVar(resolveMemory(insn.requireMemory()), insn.requireIntSourceA().intNumber, type, isArithmetic = true)
        Opcode.LSRNM -> shiftMemoryVar(resolveMemory(insn.requireMemory()), insn.requireIntSourceA().intNumber, type, isArithmetic = false)
        Opcode.LSLNM -> shiftMemoryLeftVar(resolveMemory(insn.requireMemory()), insn.requireIntSourceA().intNumber, type)

        // Immediate-count shifts (LSLI/LSRI/ASRI): the count is a literal in the immediate operand.
        Opcode.ASRI -> shiftRegister(insn.requireIntDest().intNumber, insn.requireImmediateInt(), type, isArithmetic = true, isLeft = false)
        Opcode.LSRI -> shiftRegister(insn.requireIntDest().intNumber, insn.requireImmediateInt(), type, isArithmetic = false, isLeft = false)
        Opcode.LSLI -> shiftRegister(insn.requireIntDest().intNumber, insn.requireImmediateInt(), type, isArithmetic = false, isLeft = true)

        Opcode.ASR -> shiftRegister(insn.requireIntDest().intNumber, 1, type, isArithmetic = true, isLeft = false)
        Opcode.ASRM -> memoryShiftRotate(resolveMemory(insn.requireMemory()), 1, type, isArithmetic = true, isLeft = false, isRotate = false, throughCarry = false)
        Opcode.LSR -> shiftRegister(insn.requireIntDest().intNumber, 1, type, isArithmetic = false, isLeft = false)
        Opcode.LSRM -> memoryShiftRotate(resolveMemory(insn.requireMemory()), 1, type, isArithmetic = false, isLeft = false, isRotate = false, throughCarry = false)
        Opcode.LSL -> shiftRegister(insn.requireIntDest().intNumber, 1, type, isArithmetic = false, isLeft = true)
        Opcode.LSLM -> memoryShiftRotate(resolveMemory(insn.requireMemory()), 1, type, isArithmetic = false, isLeft = true, isRotate = false, throughCarry = false)

        Opcode.ROR -> rotateRight(insn.requireIntDest().intNumber, type)
        Opcode.RORM -> memoryShiftRotate(resolveMemory(insn.requireMemory()), 1, type, isArithmetic = false, isLeft = false, isRotate = true, throughCarry = false)
        Opcode.ROL -> rotateLeft(insn.requireIntDest().intNumber, type)
        Opcode.ROLM -> memoryShiftRotate(resolveMemory(insn.requireMemory()), 1, type, isArithmetic = false, isLeft = true, isRotate = true, throughCarry = false)
        Opcode.ROXR -> rotateRightThroughCarry(insn.requireIntDest().intNumber, type)
        Opcode.ROXRM -> memoryShiftRotate(resolveMemory(insn.requireMemory()), 1, type, isArithmetic = false, isLeft = false, isRotate = true, throughCarry = true)
        Opcode.ROXL -> rotateLeftThroughCarry(insn.requireIntDest().intNumber, type)
        Opcode.ROXLM -> memoryShiftRotate(resolveMemory(insn.requireMemory()), 1, type, isArithmetic = false, isLeft = true, isRotate = true, throughCarry = true)

        Opcode.BITTST -> bitTest(insn.requireIntSourceA().intNumber, insn.requireImmediateInt(), type)
        Opcode.BITSET -> bitSet(insn.requireIntDest().intNumber, insn.requireImmediateInt(), type)
        Opcode.BITCLR -> bitClear(insn.requireIntDest().intNumber, insn.requireImmediateInt(), type)
        Opcode.BITTOG -> bitToggle(insn.requireIntDest().intNumber, insn.requireImmediateInt(), type)

        else -> error("Unknown bitwise opcode: ${insn.opcode}")
    }
}

// === AND ===

private fun AsmGen.andRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLoadD0(srcReg, type)
    emitLine("and$s  d0, ${regAddr(dstReg)}")
    invalidateD0CacheForSlot(dstReg)
}

private fun AsmGen.andImmediate(dstReg: Int, value: Int, type: IRDataType) {
    val s = dtSuffix(type)
    val mask = when (type) {
        IRDataType.BYTE -> value and 0xff
        IRDataType.WORD -> value and 0xffff
        IRDataType.LONG -> value
        else -> error("unsupported type for AND immediate")
    }
    emitLine("andi$s  #$mask, ${regAddr(dstReg)}")
    invalidateD0CacheForSlot(dstReg)
}

private fun AsmGen.andMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    val s = dtSuffix(type)
    emitLoadD0(dstReg, type)
    emitLine("and$s  d0, $sourceAddress")
    invalidateD0CacheForAddress(sourceAddress)
}

// === OR ===

private fun AsmGen.orRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLoadD0(srcReg, type)
    emitLine("or$s  d0, ${regAddr(dstReg)}")
    invalidateD0CacheForSlot(dstReg)
}

private fun AsmGen.orImmediate(dstReg: Int, value: Int, type: IRDataType) {
    val s = dtSuffix(type)
    val mask = when (type) {
        IRDataType.BYTE -> value and 0xff
        IRDataType.WORD -> value and 0xffff
        IRDataType.LONG -> value
        else -> error("unsupported type for OR immediate")
    }
    emitLine("ori$s  #$mask, ${regAddr(dstReg)}")
    invalidateD0CacheForSlot(dstReg)
}

private fun AsmGen.orMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    val s = dtSuffix(type)
    emitLoadD0(dstReg, type)
    emitLine("or$s  d0, $sourceAddress")
    invalidateD0CacheForAddress(sourceAddress)
}

// === XOR ===

private fun AsmGen.xorRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLoadD0(srcReg, type)
    emitLine("eor$s  d0, ${regAddr(dstReg)}")
    invalidateD0CacheForSlot(dstReg)
}

private fun AsmGen.xorImmediate(dstReg: Int, value: Int, type: IRDataType) {
    val s = dtSuffix(type)
    val mask = when (type) {
        IRDataType.BYTE -> value and 0xff
        IRDataType.WORD -> value and 0xffff
        IRDataType.LONG -> value
        else -> error("unsupported type for XOR immediate")
    }
    emitLine("eori$s  #$mask, ${regAddr(dstReg)}")
    invalidateD0CacheForSlot(dstReg)
}

private fun AsmGen.xorMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    val s = dtSuffix(type)
    emitLoadD0(dstReg, type)
    emitLine("eor$s  d0, $sourceAddress")
    invalidateD0CacheForAddress(sourceAddress)
}

// === Invert ===

private fun AsmGen.invertRegister(reg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("not$s  ${regAddr(reg)}")
    invalidateD0CacheForSlot(reg)
}

private fun AsmGen.invertMemory(target: String, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("not$s  $target")
    invalidateD0CacheForAddress(target)
}

// === Shift/rotate size helpers ===

private fun AsmGen.shiftOpcode(isLeft: Boolean, isArithmetic: Boolean, isRotate: Boolean = false): String {
    if (isRotate) {
        // Always use roxl/roxr (never rol/ror). X is managed as rotate-carry.
        return if (isLeft) "roxl" else "roxr"
    }
    return if (isLeft) {
        "lsl"
    } else {
        if (isArithmetic) "asr" else "lsr"
    }
}

// === Shifts by 1 (constant count 1) ===

// Emit a .w count=1 memory-form shift/rotate. The absolute form (`op.w addr`)
// is one instruction and optimal, but the QEMU 68020 emulation has a bug where
// `asr.w` with absolute addressing zero-extends the 16-bit operand before the
// shift instead of sign-extending it, producing a logical shift result for
// negative values. The 68000 (vamos) and the `(a0)` / register forms are
// unaffected. Workaround for the qemu68k target only: load the address into
// a0 and use `(a0)` addressing. A0 is a scratch address register in the m68k
// codegen, so this is safe.
private fun AsmGen.emitMemoryWordShiftOrRotate(op: String, address: String) {
    if (target.name == Qemu68kTarget.NAME) {
        emitLine("lea  $address, a0")
        emitLine("$op.w  (a0)")
    } else {
        emitLine("$op.w  $address")
    }
}

private fun AsmGen.shiftRegister(reg: Int, count: Int, type: IRDataType, isArithmetic: Boolean, isLeft: Boolean) {
    val s = dtSuffix(type)
    val op = shiftOpcode(isLeft, isArithmetic)
    if (isLeft && type == IRDataType.LONG && count == 16) {
        // x << 16 for long: `swap; clr.w`. Left shift by 16 has the same
        // result for signed and unsigned long: the sign bit lives in the
        // top half, which is replaced by the bottom half; the bottom
        // half is always cleared.
        emitLoadD0(reg, IRDataType.LONG)
        emitLine("swap  d0")
        emitLine("clr.w  d0")
        emitStoreD0(reg, IRDataType.LONG)
        return
    }
    if (type == IRDataType.WORD && count == 1) {
        // m68k memory shift/rotate is supported for .w count=1; collapse the
        // d0 round-trip into a single memory form
        emitMemoryWordShiftOrRotate(op, regAddr(reg))
        invalidateD0CacheForSlot(reg)
        return
    }
    if (count in 1..8) {
        emitLoadD0(reg, type)
        emitLine("$op$s  #$count, d0")
        emitStoreD0(reg, type)
    } else {
        // m68k immediate-shift range is 1..8; for larger counts the count
        // has to go into a data register first. `move.w` preserves the
        // count up to 32767 (Prog8 shift counts are 0..255, so this is
        // always safe).
        emitLine("move.w  #$count, d1")
        emitLoadD0(reg, type)
        emitLine("$op$s  d1, d0")
        emitStoreD0(reg, type)
    }
}

private fun AsmGen.memoryShiftRotate(target: String, count: Int, type: IRDataType, isArithmetic: Boolean, isLeft: Boolean, isRotate: Boolean, throughCarry: Boolean) {
    // For logical rotates (ROL/ROR), clear X before roxl/roxr
    // so the injected bit is 0 (logical rotate, not through-carry).
    if (isRotate && !throughCarry) {
        emitLine($$"andi  #$ef, ccr")
    }
    // On 68k, shift/rotate instructions can operate directly on memory operands
    // (only for .w size, and shift/rotate count must be 1)
    if (type == IRDataType.WORD && count == 1) {
        val op = shiftOpcode(isLeft, isArithmetic, isRotate)
        emitMemoryWordShiftOrRotate(op, target)
        invalidateD0CacheForAddress(target)
        return
    }
    val s = dtSuffix(type)
    val op = shiftOpcode(isLeft, isArithmetic, isRotate)
    emitLoadD0FromAddress(target, type)
    emitLine("$op$s  #$count, d0")
    emitStoreD0ToAddress(target, type)
}

// === Variable-count shifts ===

private fun AsmGen.logicalShiftLeftVar(reg: Int, countReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move.b  ${regAddr(countReg)}, d1")
    emitLoadD0(reg, type)
    emitLine("lsl$s  d1, d0")
    emitStoreD0(reg, type)
}

private fun AsmGen.logicalShiftRightVar(reg: Int, countReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move.b  ${regAddr(countReg)}, d1")
    emitLoadD0(reg, type)
    emitLine("lsr$s  d1, d0")
    emitStoreD0(reg, type)
}

private fun AsmGen.arithmeticShiftRightVar(reg: Int, countReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move.b  ${regAddr(countReg)}, d1")
    emitLoadD0(reg, type)
    emitLine("asr$s  d1, d0")
    emitStoreD0(reg, type)
}

// === Memory variable-count shifts ===

private fun AsmGen.shiftMemoryVar(target: String, countReg: Int, type: IRDataType, isArithmetic: Boolean) {
    val s = dtSuffix(type)
    val op = if (isArithmetic) "asr" else "lsr"
    emitLine("move.b  ${regAddr(countReg)}, d1")
    emitLoadD0FromAddress(target, type)
    emitLine("$op$s  d1, d0")
    emitStoreD0ToAddress(target, type)
}

private fun AsmGen.shiftMemoryLeftVar(target: String, countReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move.b  ${regAddr(countReg)}, d1")
    emitLoadD0FromAddress(target, type)
    emitLine("lsl$s  d1, d0")
    emitStoreD0ToAddress(target, type)
}

// === Rotates ===
// On M68k, the X (extend/CCR bit 4) bit serves as the "carry for rotates",
// while C (carry/CCR bit 0) is used for comparisons.
// This maps naturally: ROL/ROR IR (logical rotate) = clear X + roxl/roxr,
// ROXL/ROXR IR (rotate through carry) = roxl/roxr directly.
// CLC/SEC manage both C+X to keep them in sync.

private fun AsmGen.rotateLeft(reg: Int, type: IRDataType) {
    // IR ROL: logical rotate left (inject 0 into LSB)
    if (type == IRDataType.WORD) {
        emitLine($$"andi  #$ef, ccr")       // clear X (and leave C alone)
        emitMemoryWordShiftOrRotate("roxl", regAddr(reg))
        invalidateD0CacheForSlot(reg)
        return
    }
    val s = dtSuffix(type)
    emitLoadD0(reg, type)
    emitLine($$"andi  #$ef, ccr")       // clear X (and leave C alone)
    emitLine("roxl$s  #1, d0")           // rotate through X: X=0 -> bit 0 gets 0
    emitStoreD0(reg, type)
}

private fun AsmGen.rotateRight(reg: Int, type: IRDataType) {
    // IR ROR: logical rotate right (inject 0 into MSB)
    if (type == IRDataType.WORD) {
        emitLine($$"andi  #$ef, ccr")       // clear X (and leave C alone)
        emitMemoryWordShiftOrRotate("roxr", regAddr(reg))
        invalidateD0CacheForSlot(reg)
        return
    }
    val s = dtSuffix(type)
    emitLoadD0(reg, type)
    emitLine($$"andi  #$ef, ccr")       // clear X (and leave C alone)
    emitLine("roxr$s  #1, d0")           // rotate through X: X=0 -> MSB gets 0
    emitStoreD0(reg, type)
}

// === Rotates through carry (extend) ===

private fun AsmGen.rotateLeftThroughCarry(reg: Int, type: IRDataType) {
    // IR ROXL: rotate left through carry (on M68k, X serves as rotate-carry)
    if (type == IRDataType.WORD) {
        emitMemoryWordShiftOrRotate("roxl", regAddr(reg))
        invalidateD0CacheForSlot(reg)
        return
    }
    val s = dtSuffix(type)
    emitLoadD0(reg, type)
    emitLine("roxl$s  #1, d0")
    emitStoreD0(reg, type)
}

private fun AsmGen.rotateRightThroughCarry(reg: Int, type: IRDataType) {
    // IR ROXR: rotate right through carry (on M68k, X serves as rotate-carry)
    if (type == IRDataType.WORD) {
        emitMemoryWordShiftOrRotate("roxr", regAddr(reg))
        invalidateD0CacheForSlot(reg)
        return
    }
    val s = dtSuffix(type)
    emitLoadD0(reg, type)
    emitLine("roxr$s  #1, d0")
    emitStoreD0(reg, type)
}

// === Bit manipulation ===

private fun AsmGen.bitOpMem(op: String, reg: Int, bit: Int, type: IRDataType) {
    // The register slot is big-endian in memory, so the target bit lives in
    // byte (size-1-bit/8) at bit position (bit % 8). A byte-sized bit op with
    // an immediate bit number operates on that byte directly, no d0 round-trip.
    val size = when(type) {
        IRDataType.BYTE -> 1
        IRDataType.WORD -> 2
        IRDataType.LONG -> 4
        else -> error("bit op on unsupported type $type")
    }
    val byteOffset = size - 1 - bit / 8
    val bitInByte = bit % 8
    emitLine("$op  #$bitInByte, ${regAddrByte(reg, byteOffset)}")
}

private fun AsmGen.bitTest(reg: Int, bit: Int, type: IRDataType) {
    bitOpMem("btst", reg, bit, type)
}

private fun AsmGen.bitSet(reg: Int, bit: Int, type: IRDataType) {
    bitOpMem("bset", reg, bit, type)
}

private fun AsmGen.bitClear(reg: Int, bit: Int, type: IRDataType) {
    bitOpMem("bclr", reg, bit, type)
}

private fun AsmGen.bitToggle(reg: Int, bit: Int, type: IRDataType) {
    bitOpMem("bchg", reg, bit, type)
}
