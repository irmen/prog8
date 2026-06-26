/*
 * Bitwise and logical IR instruction translations for the new6502gen code generator.
 *
 * Handles: AND, OR, XOR (register/imm/memory), INV (bitwise not),
 * shifts (LSL, LSR, ASR) by constant or variable count,
 * rotates (ROL, ROR, ROXL, ROXR) and bit manipulation (BITTST, BITSET, BITCLR, BITTOG).
 *
 * Constant-count shifts are unrolled (for loop in the generator).
 * Variable-count shifts use a loop with X as the counter.
 * Arithmetic shifts use CMP #128 + ROR to preserve the sign bit.
 *
 * Note: ROL/ROR without carry prefix (clc) are rotate-through-carry,
 * while ROXL/ROXR explicitly use carry for multi-byte rotates.
 */

package codegen

import prog8.intermediate.*

fun CodeGenerator.translateBitwise(insn: IRInstruction) {
    val type = insn.type ?: IRDataType.BYTE
    val r1 = insn.reg1          // nullable - memory-only ops (INVM, ASRM, etc.) have no reg1
    val r2 = insn.reg2
    val imm = insn.immediate
    val addr = insn.address
    val label = insn.labelSymbol

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
            val source = resolveAddress(addr, label)
            andMemory(r1 ?: error("ANDM needs reg1"), source, type)
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
            val source = resolveAddress(addr, label)
            orMemory(r1 ?: error("ORM needs reg1"), source, type)
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
            val source = resolveAddress(addr, label)
            xorMemory(r1 ?: error("XORM needs reg1"), source, type)
        }

        Opcode.INV -> invertRegister(r1 ?: error("INV needs reg1"), type)
        Opcode.INVM -> invertMemory(resolveAddress(addr, label), type)

        // Shift by register count (ASRN/LSRN/LSLN: r2 = shift count register)
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

        // Shift memory by register count (ASRNM/LSRNM/LSLNM: r1 = count reg, addr = target)
        Opcode.ASRNM -> emitLine("; ASRNM (not implemented)")
        Opcode.LSRNM -> emitLine("; LSRNM (not implemented)")
        Opcode.LSLNM -> emitLine("; LSLNM (not implemented)")

        // Shift by 1 (ASR/LSR/LSL: single-bit shift, no count operand)
        Opcode.ASR -> arithmeticShiftRight(r1 ?: error("ASR needs reg1"), 1, type)
        Opcode.ASRM -> emitLine("; ASRM (not implemented)")
        Opcode.LSR -> logicalShiftRight(r1 ?: error("LSR needs reg1"), 1, type)
        Opcode.LSRM -> emitLine("; LSRM (not implemented)")
        Opcode.LSL -> logicalShiftLeft(r1 ?: error("LSL needs reg1"), 1, type)
        Opcode.LSLM -> emitLine("; LSLM (not implemented)")

        Opcode.ROR -> rotateRight(r1 ?: error("ROR needs reg1"), type)
        Opcode.RORM -> rotateRightMemory(resolveAddress(addr, label), type)
        Opcode.ROL -> rotateLeft(r1 ?: error("ROL needs reg1"), type)
        Opcode.ROLM -> rotateLeftMemory(resolveAddress(addr, label), type)
        Opcode.ROXR -> rotateRightThroughCarry(r1 ?: error("ROXR needs reg1"), type)
        Opcode.ROXRM -> emitLine("; ROXRM (not implemented)")
        Opcode.ROXL -> rotateLeftThroughCarry(r1 ?: error("ROXL needs reg1"), type)
        Opcode.ROXLM -> emitLine("; ROXLM (not implemented)")

        Opcode.BITTST -> {
            val bit = imm ?: error("BITTST needs bit number")
            bitTest(r1 ?: error("BITTST needs reg1"), bit, type)
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

private fun CodeGenerator.andRegisters(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("and ${regAddrLo(src)}")
            emitLine("sta ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("and ${regAddrLo(src)}")
            emitLine("sta ${regAddrLo(dst)}")
            emitLine("lda ${regAddrHi(dst)}")
            emitLine("and ${regAddrHi(src)}")
            emitLine("sta ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("and ${regAddrLo(src)}")
            emitLine("sta ${regAddrLo(dst)}")
            emitLine("lda ${regAddrHi(dst)}")
            emitLine("and ${regAddrHi(src)}")
            emitLine("sta ${regAddrHi(dst)}")
            emitLine("lda ${regAddrLo(dst) + 2}")
            emitLine("and ${regAddrLo(src) + 2}")
            emitLine("sta ${regAddrLo(dst) + 2}")
            emitLine("lda ${regAddrLo(dst) + 3}")
            emitLine("and ${regAddrLo(src) + 3}")
            emitLine("sta ${regAddrLo(dst) + 3}")
        }
        IRDataType.FLOAT -> emitLine("; FLOAT ANDR r$dst, r$src (not implemented)")
    }
}

private fun CodeGenerator.andImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("and #${value and 0xff}")
            emitLine("sta ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("and #<${value and 0xffff}")
            emitLine("sta ${regAddrLo(dst)}")
            emitLine("lda ${regAddrHi(dst)}")
            emitLine("and #>${value and 0xffff}")
            emitLine("sta ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("and #<${value and 0xffff}")
            emitLine("sta ${regAddrLo(dst)}")
            emitLine("lda ${regAddrHi(dst)}")
            emitLine("and #>${value and 0xffff}")
            emitLine("sta ${regAddrHi(dst)}")
            emitLine("lda ${regAddrLo(dst) + 2}")
            emitLine("and #${(value ushr 16) and 0xff}")
            emitLine("sta ${regAddrLo(dst) + 2}")
            emitLine("lda ${regAddrLo(dst) + 3}")
            emitLine("and #${(value ushr 24) and 0xff}")
            emitLine("sta ${regAddrLo(dst) + 3}")
        }
        IRDataType.FLOAT -> emitLine("; FLOAT AND r$dst #$value (not implemented)")
    }
}

private fun CodeGenerator.andMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("and $source")
            emitLine("sta ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("and $source")
            emitLine("sta ${regAddrLo(dst)}")
            emitLine("lda ${regAddrHi(dst)}")
            emitLine("and $source+1")
            emitLine("sta ${regAddrHi(dst)}")
        }
        else -> emitLine("; ANDM r$dst, $source ${type.name} (not implemented)")
    }
}

// === OR ===

private fun CodeGenerator.orRegisters(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("ora ${regAddrLo(src)}")
            emitLine("sta ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("ora ${regAddrLo(src)}")
            emitLine("sta ${regAddrLo(dst)}")
            emitLine("lda ${regAddrHi(dst)}")
            emitLine("ora ${regAddrHi(src)}")
            emitLine("sta ${regAddrHi(dst)}")
        }
        else -> emitLine("; ORR r$dst, r$src ${type.name} (not implemented)")
    }
}

private fun CodeGenerator.orImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("ora #${value and 0xff}")
            emitLine("sta ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("ora #<${value and 0xffff}")
            emitLine("sta ${regAddrLo(dst)}")
            emitLine("lda ${regAddrHi(dst)}")
            emitLine("ora #>${value and 0xffff}")
            emitLine("sta ${regAddrHi(dst)}")
        }
        else -> emitLine("; OR r$dst, #$value ${type.name} (not implemented)")
    }
}

private fun CodeGenerator.orMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("ora $source")
            emitLine("sta ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("ora $source")
            emitLine("sta ${regAddrLo(dst)}")
            emitLine("lda ${regAddrHi(dst)}")
            emitLine("ora $source+1")
            emitLine("sta ${regAddrHi(dst)}")
        }
        else -> emitLine("; ORM r$dst, $source ${type.name} (not implemented)")
    }
}

// === XOR ===

private fun CodeGenerator.xorRegisters(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("eor ${regAddrLo(src)}")
            emitLine("sta ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("eor ${regAddrLo(src)}")
            emitLine("sta ${regAddrLo(dst)}")
            emitLine("lda ${regAddrHi(dst)}")
            emitLine("eor ${regAddrHi(src)}")
            emitLine("sta ${regAddrHi(dst)}")
        }
        else -> emitLine("; XORR r$dst, r$src ${type.name} (not implemented)")
    }
}

private fun CodeGenerator.xorImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("eor #${value and 0xff}")
            emitLine("sta ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("eor #<${value and 0xffff}")
            emitLine("sta ${regAddrLo(dst)}")
            emitLine("lda ${regAddrHi(dst)}")
            emitLine("eor #>${value and 0xffff}")
            emitLine("sta ${regAddrHi(dst)}")
        }
        else -> emitLine("; XOR r$dst, #$value ${type.name} (not implemented)")
    }
}

private fun CodeGenerator.xorMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("eor $source")
            emitLine("sta ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda ${regAddrLo(dst)}")
            emitLine("eor $source")
            emitLine("sta ${regAddrLo(dst)}")
            emitLine("lda ${regAddrHi(dst)}")
            emitLine("eor $source+1")
            emitLine("sta ${regAddrHi(dst)}")
        }
        else -> emitLine("; XORM r$dst, $source ${type.name} (not implemented)")
    }
}

// === Invert ===

private fun CodeGenerator.invertRegister(reg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda ${regAddrLo(reg)}")
            emitLine("eor #255")
            emitLine("sta ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda ${regAddrLo(reg)}")
            emitLine("eor #255")
            emitLine("sta ${regAddrLo(reg)}")
            emitLine("lda ${regAddrHi(reg)}")
            emitLine("eor #255")
            emitLine("sta ${regAddrHi(reg)}")
        }
        else -> emitLine("; INV r$reg ${type.name} (not implemented)")
    }
}

private fun CodeGenerator.invertMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda $target")
            emitLine("eor #255")
            emitLine("sta $target")
        }
        IRDataType.WORD -> {
            emitLine("lda $target")
            emitLine("eor #255")
            emitLine("sta $target")
            emitLine("lda $target+1")
            emitLine("eor #255")
            emitLine("sta $target+1")
        }
        else -> emitLine("; INVM $target ${type.name} (not implemented)")
    }
}

// === Shifts ===

private fun CodeGenerator.logicalShiftLeft(reg: Int, count: Int, type: IRDataType) {
    for (i in 1..count) {
        when (type) {
            IRDataType.BYTE -> {
                emitLine("asl ${regAddrLo(reg)}")
            }
            IRDataType.WORD -> {
                emitLine("asl ${regAddrLo(reg)}")
                emitLine("rol ${regAddrHi(reg)}")
            }
            IRDataType.LONG -> {
                emitLine("asl ${regAddrLo(reg)}")
                emitLine("rol ${regAddrHi(reg)}")
                emitLine("rol ${regAddrLo(reg) + 2}")
                emitLine("rol ${regAddrLo(reg) + 3}")
            }
            IRDataType.FLOAT -> emitLine("; FLOAT LSLN r$reg, $count (not implemented)")
        }
    }
}

private fun CodeGenerator.logicalShiftLeftMemory(target: String, count: Int, type: IRDataType) {
    for (i in 1..count) {
        when (type) {
            IRDataType.BYTE -> emitLine("asl $target")
            IRDataType.WORD -> {
                emitLine("asl $target")
                emitLine("rol $target+1")
            }
            else -> emitLine("; LSLNM $target, $count ${type.name} (not implemented)")
        }
    }
}

private fun CodeGenerator.logicalShiftRight(reg: Int, count: Int, type: IRDataType) {
    for (i in 1..count) {
        when (type) {
            IRDataType.BYTE -> {
                emitLine("lsr ${regAddrLo(reg)}")
            }
            IRDataType.WORD -> {
                emitLine("lsr ${regAddrHi(reg)}")
                emitLine("ror ${regAddrLo(reg)}")
            }
            IRDataType.LONG -> {
                emitLine("lsr ${regAddrLo(reg) + 3}")
                emitLine("ror ${regAddrLo(reg) + 2}")
                emitLine("ror ${regAddrHi(reg)}")
                emitLine("ror ${regAddrLo(reg)}")
            }
            IRDataType.FLOAT -> emitLine("; FLOAT LSRN r$reg, $count (not implemented)")
        }
    }
}

private fun CodeGenerator.logicalShiftRightMemory(target: String, count: Int, type: IRDataType) {
    for (i in 1..count) {
        when (type) {
            IRDataType.BYTE -> emitLine("lsr $target")
            IRDataType.WORD -> {
                emitLine("lsr $target+1")
                emitLine("ror $target")
            }
            else -> emitLine("; LSRNM $target, $count ${type.name} (not implemented)")
        }
    }
}

private fun CodeGenerator.arithmeticShiftRight(reg: Int, count: Int, type: IRDataType) {
    for (i in 1..count) {
        when (type) {
            IRDataType.BYTE -> {
                emitLine("lda ${regAddrLo(reg)}")
                emitLine("cmp #128", "sign extend for ASR")
                emitLine("ror ${regAddrLo(reg)}")
            }
            IRDataType.WORD -> {
                emitLine("lda ${regAddrHi(reg)}")
                emitLine("cmp #128")
                emitLine("ror ${regAddrHi(reg)}")
                emitLine("ror ${regAddrLo(reg)}")
            }
            else -> emitLine("; ASRN r$reg, $count ${type.name} (not implemented)")
        }
    }
}

private fun CodeGenerator.arithmeticShiftRightMemory(target: String, count: Int, type: IRDataType) {
    for (i in 1..count) {
        emitLine("; ASRNM $target, $count (not implemented)")
    }
}

private fun CodeGenerator.logicalShiftLeftVar(reg: Int, countReg: Int, type: IRDataType) {
    emitLine("ldx ${regAddrLo(countReg)}")
    emitLine("beq +")
    emitLabel("loop")
    when (type) {
        IRDataType.BYTE -> {
            emitLine("asl ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("asl ${regAddrLo(reg)}")
            emitLine("rol ${regAddrHi(reg)}")
        }
        else -> emitLine("; LSL r$reg, r$countReg ${type.name} (not implemented)")
    }
    emitLine("dex")
    emitLine("bne loop")
    emitLabel("+")
}

private fun CodeGenerator.logicalShiftRightVar(reg: Int, countReg: Int, type: IRDataType) {
    emitLine("ldx ${regAddrLo(countReg)}")
    emitLine("beq +")
    emitLabel("loop")
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lsr ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("lsr ${regAddrHi(reg)}")
            emitLine("ror ${regAddrLo(reg)}")
        }
        else -> emitLine("; LSR r$reg, r$countReg ${type.name} (not implemented)")
    }
    emitLine("dex")
    emitLine("bne loop")
    emitLabel("+")
}

private fun CodeGenerator.arithmeticShiftRightVar(reg: Int, countReg: Int, type: IRDataType) {
    emitLine("ldx ${regAddrLo(countReg)}")
    emitLine("beq +")
    emitLabel("loop")
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda ${regAddrLo(reg)}")
            emitLine("cmp #128")
            emitLine("ror ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda ${regAddrHi(reg)}")
            emitLine("cmp #128")
            emitLine("ror ${regAddrHi(reg)}")
            emitLine("ror ${regAddrLo(reg)}")
        }
        else -> emitLine("; ASR r$reg, r$countReg ${type.name} (not implemented)")
    }
    emitLine("dex")
    emitLine("bne loop")
    emitLabel("+")
}

// === Rotates ===

private fun CodeGenerator.rotateLeft(reg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("clc", "rotate left by 1 bit (no carry)")
            emitLine("rol ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("rol ${regAddrLo(reg)}")
            emitLine("rol ${regAddrHi(reg)}")
        }
        else -> emitLine("; ROL r$reg ${type.name} (not implemented)")
    }
}

private fun CodeGenerator.rotateLeftMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("clc")
            emitLine("rol $target")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("rol $target")
            emitLine("rol $target+1")
        }
        else -> emitLine("; ROLM $target ${type.name} (not implemented)")
    }
}

private fun CodeGenerator.rotateRight(reg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("clc", "rotate right by 1 bit (no carry)")
            emitLine("ror ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("ror ${regAddrHi(reg)}")
            emitLine("ror ${regAddrLo(reg)}")
        }
        else -> emitLine("; ROR r$reg ${type.name} (not implemented)")
    }
}

private fun CodeGenerator.rotateRightMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("clc")
            emitLine("ror $target")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("ror $target+1")
            emitLine("ror $target")
        }
        else -> emitLine("; RORM $target ${type.name} (not implemented)")
    }
}

private fun CodeGenerator.rotateLeftThroughCarry(reg: Int, type: IRDataType) {
    // rotate through carry (like the 6502 ROL instruction does)
    when (type) {
        IRDataType.BYTE -> {
            emitLine("rol ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("rol ${regAddrLo(reg)}")
            emitLine("rol ${regAddrHi(reg)}")
        }
        else -> emitLine("; ROXL r$reg ${type.name} (not implemented)")
    }
}

private fun CodeGenerator.rotateRightThroughCarry(reg: Int, type: IRDataType) {
    // rotate through carry (like the 6502 ROR instruction does)
    when (type) {
        IRDataType.BYTE -> {
            emitLine("ror ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("ror ${regAddrHi(reg)}")
            emitLine("ror ${regAddrLo(reg)}")
        }
        else -> emitLine("; ROXR r$reg ${type.name} (not implemented)")
    }
}

// === Bit manipulation ===

private fun CodeGenerator.bitTest(reg: Int, bit: Int, type: IRDataType) {
    val mask = 1 shl bit
    emitLine("lda ${regAddrLo(reg)}")
    if (is65C02()) {
        emitLine("bit #$mask", "test bit $bit of r$reg")
    } else {
        // 6502: bit only works with memory; use and to test
        emitLine("and #$mask")
        // Z flag set if result is zero (bit not set)
    }
}

private fun CodeGenerator.bitSet(reg: Int, bit: Int, type: IRDataType) {
    val mask = 1 shl bit
    if (mask <= 0xff) {
        emitLine("lda ${regAddrLo(reg)}")
        emitLine("ora #$mask")
        emitLine("sta ${regAddrLo(reg)}")
    } else {
        // high byte bit
        val hbit = bit - 8
        emitLine("lda ${regAddrHi(reg)}")
        emitLine("ora #${1 shl hbit}")
        emitLine("sta ${regAddrHi(reg)}")
    }
}

private fun CodeGenerator.bitClear(reg: Int, bit: Int, type: IRDataType) {
    val mask = 1 shl bit
    if (mask <= 0xff) {
        emitLine("lda ${regAddrLo(reg)}")
        emitLine("and #${0xff xor mask}")
        emitLine("sta ${regAddrLo(reg)}")
    } else {
        val hbit = bit - 8
        emitLine("lda ${regAddrHi(reg)}")
        emitLine("and #${0xff xor (1 shl hbit)}")
        emitLine("sta ${regAddrHi(reg)}")
    }
}

private fun CodeGenerator.bitToggle(reg: Int, bit: Int, type: IRDataType) {
    val mask = 1 shl bit
    if (mask <= 0xff) {
        emitLine("lda ${regAddrLo(reg)}")
        emitLine("eor #$mask")
        emitLine("sta ${regAddrLo(reg)}")
    } else {
        val hbit = bit - 8
        emitLine("lda ${regAddrHi(reg)}")
        emitLine("eor #${1 shl hbit}")
        emitLine("sta ${regAddrHi(reg)}")
    }
}
