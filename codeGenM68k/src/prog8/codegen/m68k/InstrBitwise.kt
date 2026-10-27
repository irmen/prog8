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
import prog8.intermediate.*

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
    directDataPair(dstReg, srcReg, type)?.let { (d, src) ->
        emitLine("and$s  $src, $d")
    } ?: run {
        emitLoadD0(srcReg, type)
        emitLine("and$s  $SCRATCH_D0, ${operand(dstReg)}")
    }
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
    emitLine("andi$s  #$mask, ${operand(dstReg)}")
    invalidateD0CacheForSlot(dstReg)
}

private fun AsmGen.andMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    val s = dtSuffix(type)
    directDataSrc(dstReg, type)?.let { src ->
        emitLine("and$s  $src, $sourceAddress")
    } ?: run {
        emitLoadD0(dstReg, type)
        emitLine("and$s  $SCRATCH_D0, $sourceAddress")
    }
    invalidateD0CacheForAddress(sourceAddress)
}

// === OR ===

private fun AsmGen.orRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    directDataPair(dstReg, srcReg, type)?.let { (d, src) ->
        emitLine("or$s  $src, $d")
    } ?: run {
        emitLoadD0(srcReg, type)
        emitLine("or$s  $SCRATCH_D0, ${operand(dstReg)}")
    }
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
    emitLine("ori$s  #$mask, ${operand(dstReg)}")
    invalidateD0CacheForSlot(dstReg)
}

private fun AsmGen.orMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    val s = dtSuffix(type)
    directDataSrc(dstReg, type)?.let { src ->
        emitLine("or$s  $src, $sourceAddress")
    } ?: run {
        emitLoadD0(dstReg, type)
        emitLine("or$s  $SCRATCH_D0, $sourceAddress")
    }
    invalidateD0CacheForAddress(sourceAddress)
}

// === XOR ===

private fun AsmGen.xorRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    directDataPair(dstReg, srcReg, type)?.let { (d, src) ->
        emitLine("eor$s  $src, $d")
    } ?: run {
        emitLoadD0(srcReg, type)
        emitLine("eor$s  $SCRATCH_D0, ${operand(dstReg)}")
    }
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
    emitLine("eori$s  #$mask, ${operand(dstReg)}")
    invalidateD0CacheForSlot(dstReg)
}

private fun AsmGen.xorMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    val s = dtSuffix(type)
    directDataSrc(dstReg, type)?.let { src ->
        emitLine("eor$s  $src, $sourceAddress")
    } ?: run {
        emitLoadD0(dstReg, type)
        emitLine("eor$s  $SCRATCH_D0, $sourceAddress")
    }
    invalidateD0CacheForAddress(sourceAddress)
}

// === Invert ===

private fun AsmGen.invertRegister(reg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("not$s  ${operand(reg)}")
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

// Emit a .w count=1 memory-form shift/rotate. QEMU's 68020 emulation sign-extends word-sized
// memory right shifts (asr.w and lsr.w, in both addressing forms), so those take the register
// form there; left shifts and rotates are unaffected and keep the compact memory form.
private fun AsmGen.emitMemoryWordShiftOrRotate(op: String, address: String) {
    when (target.name) {
        Qemu68kTarget.NAME if (op == "asr" || op == "lsr") -> {
            emitLine("move.w  $address, $SCRATCH_D0")
            emitLine("$op.w  #1, $SCRATCH_D0")
            emitLine("move.w  $SCRATCH_D0, $address")
        }
        Qemu68kTarget.NAME -> {
            emitLine("lea  $address, $SCRATCH_A0")
            emitLine("$op.w  ($SCRATCH_A0)")
        }
        else -> {
            emitLine("$op.w  $address")
        }
    }
}

private fun AsmGen.shiftRegister(reg: Int, count: Int, type: IRDataType, isArithmetic: Boolean, isLeft: Boolean) {
    val s = dtSuffix(type)
    val op = shiftOpcode(isLeft, isArithmetic)
    directDataDst(reg, type)?.let { d ->
        if (isLeft && type == IRDataType.LONG && count == 16) {
            emitLine("swap  $d")
            emitLine("clr.w  $d")
        } else if (count in 1..8) {
            emitLine("$op$s  #$count, $d")
        } else {
            // m68k immediate-shift range is 1..8; larger counts need the count in a data register.
            emitLine("move.w  #$count, $SCRATCH_D1")
            emitLine("$op$s  $SCRATCH_D1, $d")
        }
        invalidateD0CacheForSlot(reg)
        return
    }
    if (isLeft && type == IRDataType.LONG && count == 16) {
        // x << 16 for long: swap; clr.w (the sign bit lives in the top half, which the bottom half replaces).
        emitLoadD0(reg, IRDataType.LONG)
        emitLine("swap  $SCRATCH_D0")
        emitLine("clr.w  $SCRATCH_D0")
        emitStoreD0(reg, IRDataType.LONG)
        return
    }
    if (type == IRDataType.WORD && count == 1 && VirtualRegister.int(reg) !in regAllocation) {
        // collapse the d0 round-trip into a .w count=1 memory form; only while in memory, not when register-allocated.
        emitMemoryWordShiftOrRotate(op, operand(reg))
        invalidateD0CacheForSlot(reg)
        return
    }
    if (count in 1..8) {
        emitLoadD0(reg, type)
        emitLine("$op$s  #$count, $SCRATCH_D0")
        emitStoreD0(reg, type)
    } else {
        // m68k immediate-shift range is 1..8; for larger counts the count
        // has to go into a data register first. Use moveq for the common
        // positive counts that fit its signed 8-bit immediate; move.w is
        // still required for counts above 127.
        if (count in 1..127)
            emitLine("moveq  #$count, $SCRATCH_D1")
        else
            emitLine("move.w  #$count, $SCRATCH_D1")
        emitLoadD0(reg, type)
        emitLine("$op$s  $SCRATCH_D1, $SCRATCH_D0")
        emitStoreD0(reg, type)
    }
}

private fun AsmGen.memoryShiftRotate(target: String, count: Int, type: IRDataType, isArithmetic: Boolean, isLeft: Boolean, isRotate: Boolean, throughCarry: Boolean) {
    // For logical rotates (ROL/ROR), clear X before roxl/roxr so the injected bit is 0, not the carry.
    if (isRotate && !throughCarry) {
        emitLine($$"andi  #$ef, ccr")
    }
    // On 68k, shift/rotate can operate directly on memory (.w size, count must be 1)
    if (type == IRDataType.WORD && count == 1) {
        val op = shiftOpcode(isLeft, isArithmetic, isRotate)
        emitMemoryWordShiftOrRotate(op, target)
        invalidateD0CacheForAddress(target)
        return
    }
    val s = dtSuffix(type)
    val op = shiftOpcode(isLeft, isArithmetic, isRotate)
    emitLoadD0FromAddress(target, type)
    emitLine("$op$s  #$count, $SCRATCH_D0")
    emitStoreD0ToAddress(target, type)
}

// === Variable-count shifts ===

private fun AsmGen.logicalShiftLeftVar(reg: Int, countReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    directDataDst(reg, type)?.let { d ->
        emitLine("move.b  ${operand(countReg)}, $SCRATCH_D1")
        emitLine("lsl$s  $SCRATCH_D1, $d")
        invalidateD0CacheForSlot(reg)
        return
    }
    emitLine("move.b  ${operand(countReg)}, $SCRATCH_D1")
    emitLoadD0(reg, type)
    emitLine("lsl$s  $SCRATCH_D1, $SCRATCH_D0")
    emitStoreD0(reg, type)
}

private fun AsmGen.logicalShiftRightVar(reg: Int, countReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    directDataDst(reg, type)?.let { d ->
        emitLine("move.b  ${operand(countReg)}, $SCRATCH_D1")
        emitLine("lsr$s  $SCRATCH_D1, $d")
        invalidateD0CacheForSlot(reg)
        return
    }
    emitLine("move.b  ${operand(countReg)}, $SCRATCH_D1")
    emitLoadD0(reg, type)
    emitLine("lsr$s  $SCRATCH_D1, $SCRATCH_D0")
    emitStoreD0(reg, type)
}

private fun AsmGen.arithmeticShiftRightVar(reg: Int, countReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    directDataDst(reg, type)?.let { d ->
        emitLine("move.b  ${operand(countReg)}, $SCRATCH_D1")
        emitLine("asr$s  $SCRATCH_D1, $d")
        invalidateD0CacheForSlot(reg)
        return
    }
    emitLine("move.b  ${operand(countReg)}, $SCRATCH_D1")
    emitLoadD0(reg, type)
    emitLine("asr$s  $SCRATCH_D1, $SCRATCH_D0")
    emitStoreD0(reg, type)
}

// === Memory variable-count shifts ===

private fun AsmGen.shiftMemoryVar(target: String, countReg: Int, type: IRDataType, isArithmetic: Boolean) {
    val s = dtSuffix(type)
    val op = if (isArithmetic) "asr" else "lsr"
    emitLine("move.b  ${operand(countReg)}, $SCRATCH_D1")
    emitLoadD0FromAddress(target, type)
    emitLine("$op$s  $SCRATCH_D1, $SCRATCH_D0")
    emitStoreD0ToAddress(target, type)
}

private fun AsmGen.shiftMemoryLeftVar(target: String, countReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move.b  ${operand(countReg)}, $SCRATCH_D1")
    emitLoadD0FromAddress(target, type)
    emitLine("lsl$s  $SCRATCH_D1, $SCRATCH_D0")
    emitStoreD0ToAddress(target, type)
}

// === Rotates ===
// On M68k, X (extend/CCR bit 4) serves as the "carry for rotates" while C is used for comparisons:
// ROL/ROR (logical) = clear X + roxl/roxr, ROXL/ROXR = roxl/roxr directly, and CLC/SEC manage
// both C+X to keep them in sync.

private fun AsmGen.rotateLeft(reg: Int, type: IRDataType) {
    // IR ROL: logical rotate left (inject 0 into LSB)
    directDataDst(reg, type)?.let { d ->
        emitLine($$"andi  #$ef, ccr")       // clear X (and leave C alone)
        emitLine("roxl${dtSuffix(type)}  #1, $d")
        invalidateD0CacheForSlot(reg)
        return
    }
    if (type == IRDataType.WORD) {
        emitLine($$"andi  #$ef, ccr")       // clear X (and leave C alone)
        emitMemoryWordShiftOrRotate("roxl", operand(reg))
        invalidateD0CacheForSlot(reg)
        return
    }
    val s = dtSuffix(type)
    emitLoadD0(reg, type)
    emitLine($$"andi  #$ef, ccr")       // clear X (and leave C alone)
    emitLine("roxl$s  #1, $SCRATCH_D0")           // rotate through X: X=0 -> bit 0 gets 0
    emitStoreD0(reg, type)
}

private fun AsmGen.rotateRight(reg: Int, type: IRDataType) {
    // IR ROR: logical rotate right (inject 0 into MSB)
    directDataDst(reg, type)?.let { d ->
        emitLine($$"andi  #$ef, ccr")       // clear X (and leave C alone)
        emitLine("roxr${dtSuffix(type)}  #1, $d")
        invalidateD0CacheForSlot(reg)
        return
    }
    if (type == IRDataType.WORD) {
        emitLine($$"andi  #$ef, ccr")       // clear X (and leave C alone)
        emitMemoryWordShiftOrRotate("roxr", operand(reg))
        invalidateD0CacheForSlot(reg)
        return
    }
    val s = dtSuffix(type)
    emitLoadD0(reg, type)
    emitLine($$"andi  #$ef, ccr")       // clear X (and leave C alone)
    emitLine("roxr$s  #1, $SCRATCH_D0")           // rotate through X: X=0 -> MSB gets 0
    emitStoreD0(reg, type)
}

// === Rotates through carry (extend) ===

private fun AsmGen.rotateLeftThroughCarry(reg: Int, type: IRDataType) {
    // IR ROXL: rotate left through carry (on M68k, X serves as rotate-carry)
    directDataDst(reg, type)?.let { d ->
        emitLine("roxl${dtSuffix(type)}  #1, $d")
        invalidateD0CacheForSlot(reg)
        return
    }
    if (type == IRDataType.WORD) {
        emitMemoryWordShiftOrRotate("roxl", operand(reg))
        invalidateD0CacheForSlot(reg)
        return
    }
    val s = dtSuffix(type)
    emitLoadD0(reg, type)
    emitLine("roxl$s  #1, $SCRATCH_D0")
    emitStoreD0(reg, type)
}

private fun AsmGen.rotateRightThroughCarry(reg: Int, type: IRDataType) {
    // IR ROXR: rotate right through carry (on M68k, X serves as rotate-carry)
    directDataDst(reg, type)?.let { d ->
        emitLine("roxr${dtSuffix(type)}  #1, $d")
        invalidateD0CacheForSlot(reg)
        return
    }
    if (type == IRDataType.WORD) {
        emitMemoryWordShiftOrRotate("roxr", operand(reg))
        invalidateD0CacheForSlot(reg)
        return
    }
    val s = dtSuffix(type)
    emitLoadD0(reg, type)
    emitLine("roxr$s  #1, $SCRATCH_D0")
    emitStoreD0(reg, type)
}

// === Bit manipulation ===

private fun AsmGen.bitOpMem(op: String, reg: Int, bit: Int, type: IRDataType) {
    // Big-endian slot: target bit is in byte (size-1-bit/8) at bit (bit % 8), so a byte op needs no d0 trip.
    val size = when(type) {
        IRDataType.BYTE -> 1
        IRDataType.WORD -> 2
        IRDataType.LONG -> 4
        else -> error("bit op on unsupported type $type")
    }
    val byteOffset = size - 1 - bit / 8
    val bitInByte = bit % 8
    val physical = regAllocation[VirtualRegister.int(reg)]
    if (physical != null && physical.startsWith("d")) {
        emitLine("$op  #$bit, ${operand(reg)}")
        if (op != "btst") invalidateD0CacheForSlot(reg)
    } else if (physical != null) {
        // Bit ops accept Dn or memory, never An: stage through d0 at full width, then invalidate the cache.
        invalidateD0Cache()
        emitLine("move.l  $physical, $SCRATCH_D0")
        emitLine("$op  #$bit, $SCRATCH_D0")
        emitLine("move.l  $SCRATCH_D0, $physical")
        invalidateD0Cache()
    } else {
        val address = regAddrByte(reg, byteOffset)
        emitLine("$op  #$bitInByte, $address")
        if (op != "btst") invalidateD0CacheForSlot(reg)
    }
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
