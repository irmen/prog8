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

import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction
import prog8.intermediate.Opcode

internal fun AsmGen.translateBitwise(insn: IRInstruction) {
    val type = insn.type ?: IRDataType.BYTE
    val r1 = insn.reg1
    val r2 = insn.reg2
    val imm = insn.immediate
    val addr = insn.address
    val label = insn.labelSymbol
    val offset = insn.labelSymbolOffset

    when (insn.opcode) {
        Opcode.ANDR -> {
            val r2val = r2 ?: error("ANDR needs reg2")
            andRegisters(r1 ?: error("ANDR needs reg1"), r2val, type)
        }
        Opcode.AND -> {
            val value = imm ?: error("AND needs immediate")
            andImmediate(r1 ?: error("AND needs reg1"), value, type)
        }
        Opcode.ANDM -> {
            val sourceAddress = resolveAddress(addr, label, offset)
            andMemory(r1 ?: error("ANDM needs reg1"), sourceAddress, type)
        }

        Opcode.ORR -> {
            val r2val = r2 ?: error("ORR needs reg2")
            orRegisters(r1 ?: error("ORR needs reg1"), r2val, type)
        }
        Opcode.OR -> {
            val value = imm ?: error("OR needs immediate")
            orImmediate(r1 ?: error("OR needs reg1"), value, type)
        }
        Opcode.ORM -> {
            val sourceAddress = resolveAddress(addr, label, offset)
            orMemory(r1 ?: error("ORM needs reg1"), sourceAddress, type)
        }

        Opcode.XORR -> {
            val r2val = r2 ?: error("XORR needs reg2")
            xorRegisters(r1 ?: error("XORR needs reg1"), r2val, type)
        }
        Opcode.XOR -> {
            val value = imm ?: error("XOR needs immediate")
            xorImmediate(r1 ?: error("XOR needs reg1"), value, type)
        }
        Opcode.XORM -> {
            val sourceAddress = resolveAddress(addr, label, offset)
            xorMemory(r1 ?: error("XORM needs reg1"), sourceAddress, type)
        }

        Opcode.INV -> invertRegister(r1 ?: error("INV needs reg1"), type)
        Opcode.INVM -> invertMemory(resolveAddress(addr, label, offset), type)

        Opcode.ASRN -> {
            val countReg = r2 ?: error("ASRN needs reg2 for shift count")
            arithmeticShiftRightVar(r1 ?: error("ASRN needs reg1"), countReg, type)
        }
        Opcode.LSRN -> {
            val countReg = r2 ?: error("LSRN needs reg2 for shift count")
            logicalShiftRightVar(r1 ?: error("LSRN needs reg1"), countReg, type)
        }
        Opcode.LSLN -> {
            val countReg = r2 ?: error("LSLN needs reg2 for shift count")
            logicalShiftLeftVar(r1 ?: error("LSLN needs reg1"), countReg, type)
        }

        Opcode.ASRNM -> shiftMemoryVar(resolveAddress(addr, label, offset), r1 ?: error("ASRNM needs reg1"), type, isArithmetic = true)
        Opcode.LSRNM -> shiftMemoryVar(resolveAddress(addr, label, offset), r1 ?: error("LSRNM needs reg1"), type, isArithmetic = false)
        Opcode.LSLNM -> shiftMemoryLeftVar(resolveAddress(addr, label, offset), r1 ?: error("LSLNM needs reg1"), type)

        Opcode.ASR -> shiftRegister(r1 ?: error("ASR needs reg1"), 1, type, isArithmetic = true, isLeft = false)
        Opcode.ASRM -> shiftMemory(resolveAddress(addr, label, offset), 1, type, isArithmetic = true, isLeft = false)
        Opcode.LSR -> shiftRegister(r1 ?: error("LSR needs reg1"), 1, type, isArithmetic = false, isLeft = false)
        Opcode.LSRM -> shiftMemory(resolveAddress(addr, label, offset), 1, type, isArithmetic = false, isLeft = false)
        Opcode.LSL -> shiftRegister(r1 ?: error("LSL needs reg1"), 1, type, isArithmetic = false, isLeft = true)
        Opcode.LSLM -> shiftMemory(resolveAddress(addr, label, offset), 1, type, isArithmetic = false, isLeft = true)

        Opcode.ROR -> rotateRight(r1 ?: error("ROR needs reg1"), type)
        Opcode.RORM -> rotateRightMemory(resolveAddress(addr, label, offset), type)
        Opcode.ROL -> rotateLeft(r1 ?: error("ROL needs reg1"), type)
        Opcode.ROLM -> rotateLeftMemory(resolveAddress(addr, label, offset), type)
        Opcode.ROXR -> rotateRightThroughCarry(r1 ?: error("ROXR needs reg1"), type)
        Opcode.ROXRM -> rotateRightThroughCarryMemory(resolveAddress(addr, label, offset), type)
        Opcode.ROXL -> rotateLeftThroughCarry(r1 ?: error("ROXL needs reg1"), type)
        Opcode.ROXLM -> rotateLeftThroughCarryMemory(resolveAddress(addr, label, offset), type)

        Opcode.BITTST -> {
            val bit = imm ?: error("BITTST needs bit number")
            bitTest(r1 ?: error("BITTST needs reg1"), bit)
        }
        Opcode.BITSET -> {
            val bit = imm ?: error("BITSET needs bit number")
            bitSet(r1 ?: error("BITSET needs reg1"), bit, type)
        }
        Opcode.BITCLR -> {
            val bit = imm ?: error("BITCLR needs bit number")
            bitClear(r1 ?: error("BITCLR needs reg1"), bit, type)
        }
        Opcode.BITTOG -> {
            val bit = imm ?: error("BITTOG needs bit number")
            bitToggle(r1 ?: error("BITTOG needs reg1"), bit, type)
        }

        else -> error("Unknown bitwise opcode: ${insn.opcode}")
    }
}

// === AND ===

private fun AsmGen.andRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(srcReg)}, d0")
    emitLine("and$s  d0, ${regAddr(dstReg)}")
}

private fun AsmGen.andImmediate(dstReg: Int, value: Int, type: IRDataType) {
    val s = dtSuffix(type)
    val mask = when (type) {
        IRDataType.BYTE -> value and 0xff
        IRDataType.WORD -> value and 0xffff
        IRDataType.LONG -> value
        else -> error("unsupported type for AND immediate")
    }
    emitLine("move$s  ${regAddr(dstReg)}, d0")
    emitLine("and$s  #$mask, d0")
    emitLine("move$s  d0, ${regAddr(dstReg)}")
}

private fun AsmGen.andMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  $sourceAddress, d0")
    emitLine("and$s  ${regAddr(dstReg)}, d0")
    emitLine("move$s  d0, $sourceAddress")
}

// === OR ===

private fun AsmGen.orRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(srcReg)}, d0")
    emitLine("or$s  d0, ${regAddr(dstReg)}")
}

private fun AsmGen.orImmediate(dstReg: Int, value: Int, type: IRDataType) {
    val s = dtSuffix(type)
    val mask = when (type) {
        IRDataType.BYTE -> value and 0xff
        IRDataType.WORD -> value and 0xffff
        IRDataType.LONG -> value
        else -> error("unsupported type for OR immediate")
    }
    emitLine("move$s  ${regAddr(dstReg)}, d0")
    emitLine("or$s  #$mask, d0")
    emitLine("move$s  d0, ${regAddr(dstReg)}")
}

private fun AsmGen.orMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  $sourceAddress, d0")
    emitLine("or$s  ${regAddr(dstReg)}, d0")
    emitLine("move$s  d0, $sourceAddress")
}

// === XOR ===

private fun AsmGen.xorRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(srcReg)}, d0")
    emitLine("eor$s  d0, ${regAddr(dstReg)}")
}

private fun AsmGen.xorImmediate(dstReg: Int, value: Int, type: IRDataType) {
    val s = dtSuffix(type)
    val mask = when (type) {
        IRDataType.BYTE -> value and 0xff
        IRDataType.WORD -> value and 0xffff
        IRDataType.LONG -> value
        else -> error("unsupported type for XOR immediate")
    }
    emitLine("move$s  ${regAddr(dstReg)}, d0")
    emitLine("eor$s  #$mask, d0")
    emitLine("move$s  d0, ${regAddr(dstReg)}")
}

private fun AsmGen.xorMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  $sourceAddress, d0")
    emitLine("eor$s  ${regAddr(dstReg)}, d0")
    emitLine("move$s  d0, $sourceAddress")
}

// === Invert ===

private fun AsmGen.invertRegister(reg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(reg)}, d0")
    emitLine("not$s  d0")
    emitLine("move$s  d0, ${regAddr(reg)}")
}

private fun AsmGen.invertMemory(target: String, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  $target, d0")
    emitLine("not$s  d0")
    emitLine("move$s  d0, $target")
}

// === Shift/rotate size helpers ===

private fun AsmGen.shiftOpcode(isLeft: Boolean, isArithmetic: Boolean, isRotate: Boolean = false, throughCarry: Boolean = false): String {
    if (isRotate) {
        return if (throughCarry) {
            if (isLeft) "roxl" else "roxr"
        } else {
            if (isLeft) "rol" else "ror"
        }
    }
    return if (isLeft) {
        "lsl"
    } else {
        if (isArithmetic) "asr" else "lsr"
    }
}

// === Shifts by 1 (constant count 1) ===

private fun AsmGen.shiftRegister(reg: Int, count: Int, type: IRDataType, isArithmetic: Boolean, isLeft: Boolean) {
    val s = dtSuffix(type)
    val op = shiftOpcode(isLeft, isArithmetic)
    emitLine("move$s  ${regAddr(reg)}, d0")
    emitLine("$op$s  #$count, d0")
    emitLine("move$s  d0, ${regAddr(reg)}")
}

private fun AsmGen.shiftMemory(target: String, count: Int, type: IRDataType, isArithmetic: Boolean, isLeft: Boolean) {
    val s = dtSuffix(type)
    val op = shiftOpcode(isLeft, isArithmetic)
    emitLine("move$s  $target, d0")
    emitLine("$op$s  #$count, d0")
    emitLine("move$s  d0, $target")
}

// === Variable-count shifts ===

private fun AsmGen.logicalShiftLeftVar(reg: Int, countReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move.b  ${regAddr(countReg)}, d1")
    emitLine("move$s  ${regAddr(reg)}, d0")
    emitLine("lsl$s  d1, d0")
    emitLine("move$s  d0, ${regAddr(reg)}")
}

private fun AsmGen.logicalShiftRightVar(reg: Int, countReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move.b  ${regAddr(countReg)}, d1")
    emitLine("move$s  ${regAddr(reg)}, d0")
    emitLine("lsr$s  d1, d0")
    emitLine("move$s  d0, ${regAddr(reg)}")
}

private fun AsmGen.arithmeticShiftRightVar(reg: Int, countReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move.b  ${regAddr(countReg)}, d1")
    emitLine("move$s  ${regAddr(reg)}, d0")
    emitLine("asr$s  d1, d0")
    emitLine("move$s  d0, ${regAddr(reg)}")
}

// === Memory variable-count shifts ===

private fun AsmGen.shiftMemoryVar(target: String, countReg: Int, type: IRDataType, isArithmetic: Boolean) {
    val s = dtSuffix(type)
    val op = if (isArithmetic) "asr" else "lsr"
    emitLine("move.b  ${regAddr(countReg)}, d1")
    emitLine("move$s  $target, d0")
    emitLine("$op$s  d1, d0")
    emitLine("move$s  d0, $target")
}

private fun AsmGen.shiftMemoryLeftVar(target: String, countReg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move.b  ${regAddr(countReg)}, d1")
    emitLine("move$s  $target, d0")
    emitLine("lsl$s  d1, d0")
    emitLine("move$s  d0, $target")
}

// === Rotates (no carry involvement — true rotates) ===

private fun AsmGen.rotateLeft(reg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(reg)}, d0")
    emitLine("rol$s  #1, d0")
    emitLine("move$s  d0, ${regAddr(reg)}")
}

private fun AsmGen.rotateLeftMemory(target: String, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  $target, d0")
    emitLine("rol$s  #1, d0")
    emitLine("move$s  d0, $target")
}

private fun AsmGen.rotateRight(reg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(reg)}, d0")
    emitLine("ror$s  #1, d0")
    emitLine("move$s  d0, ${regAddr(reg)}")
}

private fun AsmGen.rotateRightMemory(target: String, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  $target, d0")
    emitLine("ror$s  #1, d0")
    emitLine("move$s  d0, $target")
}

// === Rotates through carry (extend) ===

private fun AsmGen.rotateLeftThroughCarry(reg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(reg)}, d0")
    emitLine("roxl$s  #1, d0")
    emitLine("move$s  d0, ${regAddr(reg)}")
}

private fun AsmGen.rotateLeftThroughCarryMemory(target: String, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  $target, d0")
    emitLine("roxl$s  #1, d0")
    emitLine("move$s  d0, $target")
}

private fun AsmGen.rotateRightThroughCarry(reg: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(reg)}, d0")
    emitLine("roxr$s  #1, d0")
    emitLine("move$s  d0, ${regAddr(reg)}")
}

private fun AsmGen.rotateRightThroughCarryMemory(target: String, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  $target, d0")
    emitLine("roxr$s  #1, d0")
    emitLine("move$s  d0, $target")
}

// === Bit manipulation ===

private fun AsmGen.bitTest(reg: Int, bit: Int) {
    emitLine("move.l  ${regAddr(reg)}, d0")
    emitLine("btst.l  #$bit, d0")
}

private fun AsmGen.bitSet(reg: Int, bit: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(reg)}, d0")
    emitLine("bset.l  #$bit, d0")
    emitLine("move$s  d0, ${regAddr(reg)}")
}

private fun AsmGen.bitClear(reg: Int, bit: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(reg)}, d0")
    emitLine("bclr.l  #$bit, d0")
    emitLine("move$s  d0, ${regAddr(reg)}")
}

private fun AsmGen.bitToggle(reg: Int, bit: Int, type: IRDataType) {
    val s = dtSuffix(type)
    emitLine("move$s  ${regAddr(reg)}, d0")
    emitLine("bchg.l  #$bit, d0")
    emitLine("move$s  d0, ${regAddr(reg)}")
}
