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

package prog8.codegen.new6502

import prog8.intermediate.*

internal fun AsmGen.translateBitwise(insn: IRInstruction) {
    val type = insn.type ?: IRDataType.BYTE

    when (insn.opcode) {
        Opcode.ANDR -> {
            val dst = insn.requireIntDest().intNumber
            val src = insn.requireIntSourceA().intNumber
            andRegisters(dst, src, type)
        }
        Opcode.AND -> {
            val dst = insn.requireIntDest().intNumber
            val value = insn.requireImmediateInt()
            andImmediate(dst, value, type)
        }
        Opcode.ANDM -> {
            val src = insn.requireIntSourceA().intNumber
            val sourceAddress = resolveAddress(insn.requireMemory())
            andMemory(src, sourceAddress, type)
        }

        Opcode.ORR -> {
            val dst = insn.requireIntDest().intNumber
            val src = insn.requireIntSourceA().intNumber
            orRegisters(dst, src, type)
        }
        Opcode.OR -> {
            val dst = insn.requireIntDest().intNumber
            val value = insn.requireImmediateInt()
            orImmediate(dst, value, type)
        }
        Opcode.ORM -> {
            val src = insn.requireIntSourceA().intNumber
            val sourceAddress = resolveAddress(insn.requireMemory())
            orMemory(src, sourceAddress, type)
        }

        Opcode.XORR -> {
            val dst = insn.requireIntDest().intNumber
            val src = insn.requireIntSourceA().intNumber
            xorRegisters(dst, src, type)
        }
        Opcode.XOR -> {
            val dst = insn.requireIntDest().intNumber
            val value = insn.requireImmediateInt()
            xorImmediate(dst, value, type)
        }
        Opcode.XORM -> {
            val src = insn.requireIntSourceA().intNumber
            val sourceAddress = resolveAddress(insn.requireMemory())
            xorMemory(src, sourceAddress, type)
        }

        Opcode.INV -> invertRegister(insn.requireIntDest().intNumber, type)
        Opcode.INVM -> invertMemory(resolveAddress(insn.requireMemory()), type)

        // Shift by register count (ASRN/LSRN/LSLN: srcA = shift count register)
        Opcode.ASRN -> {
            val dst = insn.requireIntDest().intNumber
            val countReg = insn.requireIntSourceA().intNumber
            arithmeticShiftRightVar(dst, countReg, type)
        }
        Opcode.LSRN -> {
            val dst = insn.requireIntDest().intNumber
            val countReg = insn.requireIntSourceA().intNumber
            logicalShiftRightVar(dst, countReg, type)
        }
        Opcode.LSLN -> {
            val dst = insn.requireIntDest().intNumber
            val countReg = insn.requireIntSourceA().intNumber
            logicalShiftLeftVar(dst, countReg, type)
        }

        // Shift memory by register count (ASRNM/LSRNM/LSLNM: srcA = count reg, memory = target)
        Opcode.ASRNM -> shiftMemoryVar(resolveAddress(insn.requireMemory()), insn.requireIntSourceA().intNumber, type, isArithmetic = true)
        Opcode.LSRNM -> shiftMemoryVar(resolveAddress(insn.requireMemory()), insn.requireIntSourceA().intNumber, type, isArithmetic = false)
        Opcode.LSLNM -> shiftMemoryLeftVar(resolveAddress(insn.requireMemory()), insn.requireIntSourceA().intNumber, type)

        // Shift by 1 (ASR/LSR/LSL: single-bit shift, no count operand)
        Opcode.ASR -> arithmeticShiftRight(insn.requireIntDest().intNumber, 1, type)
        Opcode.ASRM -> arithmeticShiftRightMemory(resolveAddress(insn.requireMemory()), 1, type)
        Opcode.LSR -> logicalShiftRight(insn.requireIntDest().intNumber, 1, type)
        Opcode.LSRM -> logicalShiftRightMemory(resolveAddress(insn.requireMemory()), 1, type)
        Opcode.LSL -> logicalShiftLeft(insn.requireIntDest().intNumber, 1, type)
        Opcode.LSLM -> logicalShiftLeftMemory(resolveAddress(insn.requireMemory()), 1, type)

        // Immediate-count shifts (LSLI/LSRI/ASRI): unroll the count into N 1-bit shifts
        Opcode.ASRI -> arithmeticShiftRight(insn.requireIntDest().intNumber, insn.requireImmediateInt(), type)
        Opcode.LSRI -> logicalShiftRight(insn.requireIntDest().intNumber, insn.requireImmediateInt(), type)
        Opcode.LSLI -> logicalShiftLeft(insn.requireIntDest().intNumber, insn.requireImmediateInt(), type)

        Opcode.ROR -> rotateRight(insn.requireIntDest().intNumber, type)
        Opcode.RORM -> rotateRightMemory(resolveAddress(insn.requireMemory()), type)
        Opcode.ROL -> rotateLeft(insn.requireIntDest().intNumber, type)
        Opcode.ROLM -> rotateLeftMemory(resolveAddress(insn.requireMemory()), type)
        Opcode.ROXR -> rotateRightThroughCarry(insn.requireIntDest().intNumber, type)
        Opcode.ROXRM -> rotateRightThroughCarryMemory(resolveAddress(insn.requireMemory()), type)
        Opcode.ROXL -> rotateLeftThroughCarry(insn.requireIntDest().intNumber, type)
        Opcode.ROXLM -> rotateLeftThroughCarryMemory(resolveAddress(insn.requireMemory()), type)

        Opcode.BITTST -> bitTest(insn.requireIntSourceA().intNumber, insn.requireImmediateInt())
        Opcode.BITSET -> bitSet(insn.requireIntDest().intNumber, insn.requireImmediateInt())
        Opcode.BITCLR -> bitClear(insn.requireIntDest().intNumber, insn.requireImmediateInt())
        Opcode.BITTOG -> bitToggle(insn.requireIntDest().intNumber, insn.requireImmediateInt())

        else -> error("Unknown bitwise opcode: ${insn.opcode}")
    }
}

// === AND ===

private fun AsmGen.andRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("and  ${regAddrLo(srcReg)}")
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD, IRDataType.POINTER -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("and  ${regAddrLo(srcReg)}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("and  ${regAddrHi(srcReg)}")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        IRDataType.LONG -> byteLoop4(regAddrByte(dstReg, 0), regAddrByte(srcReg, 0), "and")
        IRDataType.FLOAT -> error("bitwise operations are not supported on floats")
    }
}

private fun AsmGen.andImmediate(dstReg: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("and  #${value and 0xff}")
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD, IRDataType.POINTER -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("and  #<${value and 0xffff}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("and  #>${value and 0xffff}")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        IRDataType.LONG -> {
            emitLine("lda  ${regAddrByte(dstReg, 0)}")
            emitLine("and  #${value and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 0)}")
            emitLine("lda  ${regAddrByte(dstReg, 1)}")
            emitLine("and  #${(value shr 8) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 1)}")
            emitLine("lda  ${regAddrByte(dstReg, 2)}")
            emitLine("and  #${(value shr 16) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 2)}")
            emitLine("lda  ${regAddrByte(dstReg, 3)}")
            emitLine("and  #${(value shr 24) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 3)}")
        }
        IRDataType.FLOAT -> error("bitwise operations are not supported on floats")
    }
}

private fun AsmGen.andMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  $sourceAddress")
            emitLine("and  ${regAddrLo(dstReg)}")
            emitLine("sta  $sourceAddress")
        }
        IRDataType.WORD -> {
            emitLine("lda  $sourceAddress")
            emitLine("and  ${regAddrLo(dstReg)}")
            emitLine("sta  $sourceAddress")
            emitLine("lda  $sourceAddress+1")
            emitLine("and  ${regAddrHi(dstReg)}")
            emitLine("sta  $sourceAddress+1")
        }
        IRDataType.LONG -> byteLoop4(sourceAddress, regAddrByte(dstReg, 0), "and")
        else -> error("ANDM not supported on ${type.name}")
    }
}

// === OR ===

private fun AsmGen.orRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ora  ${regAddrLo(srcReg)}")
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ora  ${regAddrLo(srcReg)}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("ora  ${regAddrHi(srcReg)}")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        IRDataType.LONG -> byteLoop4(regAddrByte(dstReg, 0), regAddrByte(srcReg, 0), "ora")
        else -> error("ORR not supported on ${type.name}")
    }
}

private fun AsmGen.orImmediate(dstReg: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ora  #${value and 0xff}")
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ora  #<${value and 0xffff}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("ora  #>${value and 0xffff}")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        IRDataType.LONG -> {
            emitLine("lda  ${regAddrByte(dstReg, 0)}")
            emitLine("ora  #${value and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 0)}")
            emitLine("lda  ${regAddrByte(dstReg, 1)}")
            emitLine("ora  #${(value shr 8) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 1)}")
            emitLine("lda  ${regAddrByte(dstReg, 2)}")
            emitLine("ora  #${(value shr 16) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 2)}")
            emitLine("lda  ${regAddrByte(dstReg, 3)}")
            emitLine("ora  #${(value shr 24) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 3)}")
        }
        else -> error("OR not supported on ${type.name}")
    }
}

private fun AsmGen.orMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  $sourceAddress")
            emitLine("ora  ${regAddrLo(dstReg)}")
            emitLine("sta  $sourceAddress")
        }
        IRDataType.WORD -> {
            emitLine("lda  $sourceAddress")
            emitLine("ora  ${regAddrLo(dstReg)}")
            emitLine("sta  $sourceAddress")
            emitLine("lda  $sourceAddress+1")
            emitLine("ora  ${regAddrHi(dstReg)}")
            emitLine("sta  $sourceAddress+1")
        }
        IRDataType.LONG -> byteLoop4(sourceAddress, regAddrByte(dstReg, 0), "ora")
        else -> error("ORM not supported on ${type.name}")
    }
}

// === XOR ===

private fun AsmGen.xorRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("eor  ${regAddrLo(srcReg)}")
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("eor  ${regAddrLo(srcReg)}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("eor  ${regAddrHi(srcReg)}")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        IRDataType.LONG -> byteLoop4(regAddrByte(dstReg, 0), regAddrByte(srcReg, 0), "eor")
        else -> error("XORR not supported on ${type.name}")
    }
}

private fun AsmGen.xorImmediate(dstReg: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("eor  #${value and 0xff}")
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("eor  #<${value and 0xffff}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("eor  #>${value and 0xffff}")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        IRDataType.LONG -> {
            emitLine("lda  ${regAddrByte(dstReg, 0)}")
            emitLine("eor  #${value and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 0)}")
            emitLine("lda  ${regAddrByte(dstReg, 1)}")
            emitLine("eor  #${(value shr 8) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 1)}")
            emitLine("lda  ${regAddrByte(dstReg, 2)}")
            emitLine("eor  #${(value shr 16) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 2)}")
            emitLine("lda  ${regAddrByte(dstReg, 3)}")
            emitLine("eor  #${(value shr 24) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 3)}")
        }
        else -> error("XOR not supported on ${type.name}")
    }
}

private fun AsmGen.xorMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  $sourceAddress")
            emitLine("eor  ${regAddrLo(dstReg)}")
            emitLine("sta  $sourceAddress")
        }
        IRDataType.WORD -> {
            emitLine("lda  $sourceAddress")
            emitLine("eor  ${regAddrLo(dstReg)}")
            emitLine("sta  $sourceAddress")
            emitLine("lda  $sourceAddress+1")
            emitLine("eor  ${regAddrHi(dstReg)}")
            emitLine("sta  $sourceAddress+1")
        }
        IRDataType.LONG -> byteLoop4(sourceAddress, regAddrByte(dstReg, 0), "eor")
        else -> error("XORM not supported on ${type.name}")
    }
}

// === Invert ===

private fun AsmGen.invertRegister(reg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("eor  #255")
            emitLine("sta  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("eor  #255")
            emitLine("sta  ${regAddrLo(reg)}")
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("eor  #255")
            emitLine("sta  ${regAddrHi(reg)}")
        }
        IRDataType.LONG -> byteLoop4Unary(regAddrByte(reg, 0), "eor  #255")
        else -> error("INV not supported on ${type.name}")
    }
}

private fun AsmGen.invertMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  $target")
            emitLine("eor  #255")
            emitLine("sta  $target")
        }
        IRDataType.WORD -> {
            emitLine("lda  $target")
            emitLine("eor  #255")
            emitLine("sta  $target")
            emitLine("lda  $target+1")
            emitLine("eor  #255")
            emitLine("sta  $target+1")
        }
        IRDataType.LONG -> byteLoop4Unary(target, "eor  #255")
        else -> error("INVM not supported on ${type.name}")
    }
}

// === Shifts ===

private fun AsmGen.logicalShiftLeft(reg: Int, count: Int, type: IRDataType) {
    repeat(count) {
        when (type) {
            IRDataType.BYTE -> {
                emitLine("asl  ${regAddrLo(reg)}")
            }
            IRDataType.WORD, IRDataType.POINTER -> {
                emitLine("asl  ${regAddrLo(reg)}")
                emitLine("rol  ${regAddrHi(reg)}")
            }
            IRDataType.LONG -> {
                emitLine("asl  ${regAddrLo(reg)}")
                emitLine("rol  ${regAddrHi(reg)}")
                emitLine("rol  ${regAddrByte(reg, 2)}")
                emitLine("rol  ${regAddrByte(reg, 3)}")
            }
            IRDataType.FLOAT -> error("bitwise operations are not supported on floats")
        }
    }
}

private fun AsmGen.logicalShiftLeftMemory(target: String, count: Int, type: IRDataType) {
    repeat(count) {
        when (type) {
            IRDataType.BYTE -> emitLine("asl  $target")
            IRDataType.WORD -> {
                emitLine("asl  $target")
                emitLine("rol  $target+1")
            }
            IRDataType.LONG -> {
                emitLine("asl  $target")
                emitLine("rol  $target+1")
                emitLine("rol  $target+2")
                emitLine("rol  $target+3")
            }
            else -> error("LSLM not supported on ${type.name}")
        }
    }
}

private fun AsmGen.logicalShiftRight(reg: Int, count: Int, type: IRDataType) {
    repeat(count) {
        when (type) {
            IRDataType.BYTE -> {
                emitLine("lsr  ${regAddrLo(reg)}")
            }
            IRDataType.WORD, IRDataType.POINTER -> {
                emitLine("lsr  ${regAddrHi(reg)}")
                emitLine("ror  ${regAddrLo(reg)}")
            }
            IRDataType.LONG -> {
                emitLine("lsr  ${regAddrByte(reg, 3)}")
                emitLine("ror  ${regAddrByte(reg, 2)}")
                emitLine("ror  ${regAddrHi(reg)}")
                emitLine("ror  ${regAddrLo(reg)}")
            }
            IRDataType.FLOAT -> error("bitwise operations are not supported on floats")
        }
    }
}

private fun AsmGen.logicalShiftRightMemory(target: String, count: Int, type: IRDataType) {
    repeat(count) {
        when (type) {
            IRDataType.BYTE -> emitLine("lsr  $target")
            IRDataType.WORD -> {
                emitLine("lsr  $target+1")
                emitLine("ror  $target")
            }
            IRDataType.LONG -> {
                emitLine("lsr  $target+3")
                emitLine("ror  $target+2")
                emitLine("ror  $target+1")
                emitLine("ror  $target")
            }
            else -> error("LSRM not supported on ${type.name}")
        }
    }
}

private fun AsmGen.arithmeticShiftRight(reg: Int, count: Int, type: IRDataType) {
    repeat(count) {
        when (type) {
            IRDataType.BYTE -> {
                emitLine("lda  ${regAddrLo(reg)}")
                emitLine("cmp  #128")
                emitLine("ror  ${regAddrLo(reg)}")
            }
            IRDataType.WORD -> {
                emitLine("lda  ${regAddrHi(reg)}")
                emitLine("cmp  #128")
                emitLine("ror  ${regAddrHi(reg)}")
                emitLine("ror  ${regAddrLo(reg)}")
            }
            IRDataType.LONG -> {
                emitLine("lda  ${regAddrByte(reg, 3)}")
                emitLine("cmp  #128")
                emitLine("ror  ${regAddrByte(reg, 3)}")
                emitLine("ror  ${regAddrByte(reg, 2)}")
                emitLine("ror  ${regAddrHi(reg)}")
                emitLine("ror  ${regAddrLo(reg)}")
            }
            else -> TODO("ASRN r$reg, $count ${type.name}")
        }
    }
}

private fun AsmGen.arithmeticShiftRightMemory(target: String, count: Int, type: IRDataType) {
    repeat(count) {
        when (type) {
            IRDataType.BYTE -> {
                emitLine("lda  $target")
                emitLine("cmp  #128")
                emitLine("ror  $target")
            }
            IRDataType.WORD -> {
                emitLine("lda  $target+1")
                emitLine("cmp  #128")
                emitLine("ror  $target+1")
                emitLine("ror  $target")
            }
            IRDataType.LONG -> {
                emitLine("lda  $target+3")
                emitLine("cmp  #128")
                emitLine("ror  $target+3")
                emitLine("ror  $target+2")
                emitLine("ror  $target+1")
                emitLine("ror  $target")
            }
            else -> TODO("ASRNM $target, $count ${type.name}")
        }
    }
}

private fun AsmGen.logicalShiftLeftVar(reg: Int, countReg: Int, type: IRDataType) {
    val loopLabel = makeLabel("lsl_var_loop")
    emitLine("ldx  ${regAddrLo(countReg)}")
    emitLine("beq  +")
    emitLabel(loopLabel)
    when (type) {
        IRDataType.BYTE -> {
            emitLine("asl  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("asl  ${regAddrLo(reg)}")
            emitLine("rol  ${regAddrHi(reg)}")
        }
        IRDataType.LONG -> {
            emitLine("asl  ${regAddrLo(reg)}")
            emitLine("rol  ${regAddrByte(reg, 1)}")
            emitLine("rol  ${regAddrByte(reg, 2)}")
            emitLine("rol  ${regAddrByte(reg, 3)}")
        }
        else -> TODO("LSL r$reg, r$countReg ${type.name}")
    }
    emitLine("dex")
    emitLine("bne  $loopLabel")
    emitLabel("+")
}

private fun AsmGen.logicalShiftRightVar(reg: Int, countReg: Int, type: IRDataType) {
    val loopLabel = makeLabel("lsr_var_loop")
    emitLine("ldx  ${regAddrLo(countReg)}")
    emitLine("beq  +")
    emitLabel(loopLabel)
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lsr  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("lsr  ${regAddrHi(reg)}")
            emitLine("ror  ${regAddrLo(reg)}")
        }
        IRDataType.LONG -> {
            emitLine("lsr  ${regAddrByte(reg, 3)}")
            emitLine("ror  ${regAddrByte(reg, 2)}")
            emitLine("ror  ${regAddrByte(reg, 1)}")
            emitLine("ror  ${regAddrLo(reg)}")
        }
        else -> TODO("LSR r$reg, r$countReg ${type.name}")
    }
    emitLine("dex")
    emitLine("bne  $loopLabel")
    emitLabel("+")
}

private fun AsmGen.arithmeticShiftRightVar(reg: Int, countReg: Int, type: IRDataType) {
    val loopLabel = makeLabel("asr_var_loop")       // TODO can be anonymous label ?  (also at other places?)
    emitLine("ldx  ${regAddrLo(countReg)}")
    emitLine("beq  +")
    emitLabel(loopLabel)
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("cmp  #128")
            emitLine("ror  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("cmp  #128")
            emitLine("ror  ${regAddrHi(reg)}")
            emitLine("ror  ${regAddrLo(reg)}")
        }
        IRDataType.LONG -> {
            emitLine("lda  ${regAddrByte(reg, 3)}")
            emitLine("cmp  #128")
            emitLine("ror  ${regAddrByte(reg, 3)}")
            emitLine("ror  ${regAddrByte(reg, 2)}")
            emitLine("ror  ${regAddrByte(reg, 1)}")
            emitLine("ror  ${regAddrLo(reg)}")
        }
        else -> TODO("ASR r$reg, r$countReg ${type.name}")
    }
    emitLine("dex")
    emitLine("bne  $loopLabel")
    emitLabel("+")
}

// === Memory variable-count shifts ===

private fun AsmGen.shiftMemoryVar(target: String, countReg: Int, type: IRDataType, isArithmetic: Boolean) {
    val loopLabel = makeLabel("shiftmem_loop")
    emitLine("ldx  ${regAddrLo(countReg)}")
    emitLine("beq  +")
    emitLabel(loopLabel)
    when (type) {
        IRDataType.BYTE -> {
            if (isArithmetic) {
                emitLine("lda  $target")
                emitLine("cmp  #128")
                emitLine("ror  $target")
            } else {
                emitLine("lsr  $target")
            }
        }
        IRDataType.WORD -> {
            if (isArithmetic) {
                emitLine("lda  $target+1")
                emitLine("cmp  #128")
                emitLine("ror  $target+1")
                emitLine("ror  $target")
            } else {
                emitLine("lsr  $target+1")
                emitLine("ror  $target")
            }
        }
        IRDataType.LONG -> {
            if (isArithmetic) {
                emitLine("lda  $target+3")
                emitLine("cmp  #128")
                emitLine("ror  $target+3")
                emitLine("ror  $target+2")
                emitLine("ror  $target+1")
                emitLine("ror  $target")
            } else {
                emitLine("lsr  $target+3")
                emitLine("ror  $target+2")
                emitLine("ror  $target+1")
                emitLine("ror  $target")
            }
        }
        else -> TODO("shiftmem ${if(isArithmetic) "ASR" else "LSR"} $target ${type.name}")
    }
    emitLine("dex")
    emitLine("bne  $loopLabel")
    emitLabel("+")
}

private fun AsmGen.shiftMemoryLeftVar(target: String, countReg: Int, type: IRDataType) {
    val loopLabel = makeLabel("shiftmeml_loop")
    emitLine("ldx  ${regAddrLo(countReg)}")
    emitLine("beq  +")
    emitLabel(loopLabel)
    when (type) {
        IRDataType.BYTE -> {
            emitLine("asl  $target")
        }
        IRDataType.WORD -> {
            emitLine("asl  $target")
            emitLine("rol  $target+1")
        }
        IRDataType.LONG -> {
            emitLine("asl  $target")
            emitLine("rol  $target+1")
            emitLine("rol  $target+2")
            emitLine("rol  $target+3")
        }
        else -> TODO("LSLNM $target ${type.name}")
    }
    emitLine("dex")
    emitLine("bne  $loopLabel")
    emitLabel("+")
}

// === Rotates ===

private fun AsmGen.rotateLeft(reg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("clc")
            emitLine("rol  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("rol  ${regAddrLo(reg)}")
            emitLine("rol  ${regAddrHi(reg)}")
        }
        IRDataType.LONG -> {
            emitLine("clc")
            emitLine("rol  ${regAddrLo(reg)}")
            emitLine("rol  ${regAddrHi(reg)}")
            emitLine("rol  ${regAddrByte(reg, 2)}")
            emitLine("rol  ${regAddrByte(reg, 3)}")
        }
        else -> error("ROL not supported on ${type.name}")
    }
}

private fun AsmGen.rotateLeftMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("clc")
            emitLine("rol  $target")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("rol  $target")
            emitLine("rol  $target+1")
        }
        IRDataType.LONG -> {
            emitLine("clc")
            emitLine("rol  $target")
            emitLine("rol  $target+1")
            emitLine("rol  $target+2")
            emitLine("rol  $target+3")
        }
        else -> error("ROLM not supported on ${type.name}")
    }
}

private fun AsmGen.rotateRight(reg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("clc")
            emitLine("ror  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("ror  ${regAddrHi(reg)}")
            emitLine("ror  ${regAddrLo(reg)}")
        }
        IRDataType.LONG -> {
            emitLine("clc")
            emitLine("ror  ${regAddrByte(reg, 3)}")
            emitLine("ror  ${regAddrByte(reg, 2)}")
            emitLine("ror  ${regAddrHi(reg)}")
            emitLine("ror  ${regAddrLo(reg)}")
        }
        else -> error("ROR not supported on ${type.name}")
    }
}

private fun AsmGen.rotateRightMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("clc")
            emitLine("ror  $target")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("ror  $target+1")
            emitLine("ror  $target")
        }
        IRDataType.LONG -> {
            emitLine("clc")
            emitLine("ror  $target+3")
            emitLine("ror  $target+2")
            emitLine("ror  $target+1")
            emitLine("ror  $target")
        }
        else -> error("RORM not supported on ${type.name}")
    }
}

private fun AsmGen.rotateLeftThroughCarry(reg: Int, type: IRDataType) {
    // rotate through carry (like the 6502 ROL instruction does)
    when (type) {
        IRDataType.BYTE -> {
            emitLine("rol  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("rol  ${regAddrLo(reg)}")
            emitLine("rol  ${regAddrHi(reg)}")
        }
        IRDataType.LONG -> {
            emitLine("rol  ${regAddrLo(reg)}")
            emitLine("rol  ${regAddrHi(reg)}")
            emitLine("rol  ${regAddrByte(reg, 2)}")
            emitLine("rol  ${regAddrByte(reg, 3)}")
        }
        else -> error("ROXL not supported on ${type.name}")
    }
}

private fun AsmGen.rotateRightThroughCarry(reg: Int, type: IRDataType) {
    // rotate through carry (like the 6502 ROR instruction does)
    when (type) {
        IRDataType.BYTE -> {
            emitLine("ror  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("ror  ${regAddrHi(reg)}")
            emitLine("ror  ${regAddrLo(reg)}")
        }
        IRDataType.LONG -> {
            emitLine("ror  ${regAddrByte(reg, 3)}")
            emitLine("ror  ${regAddrByte(reg, 2)}")
            emitLine("ror  ${regAddrHi(reg)}")
            emitLine("ror  ${regAddrLo(reg)}")
        }
        else -> error("ROXR not supported on ${type.name}")
    }
}

private fun AsmGen.rotateLeftThroughCarryMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("rol  $target")
        }
        IRDataType.WORD -> {
            emitLine("rol  $target")
            emitLine("rol  $target+1")
        }
        IRDataType.LONG -> {
            emitLine("rol  $target")
            emitLine("rol  $target+1")
            emitLine("rol  $target+2")
            emitLine("rol  $target+3")
        }
        else -> TODO("ROXLM $target ${type.name}")
    }
}

private fun AsmGen.rotateRightThroughCarryMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("ror  $target")
        }
        IRDataType.WORD -> {
            emitLine("ror  $target+1")
            emitLine("ror  $target")
        }
        IRDataType.LONG -> {
            emitLine("ror  $target+3")
            emitLine("ror  $target+2")
            emitLine("ror  $target+1")
            emitLine("ror  $target")
        }
        else -> TODO("ROXRM $target ${type.name}")
    }
}

// === Bit manipulation ===

private fun AsmGen.bitTest(reg: Int, bit: Int) {
    val mask = 1 shl bit
    emitLine("lda  ${regAddrLo(reg)}")
    if (is65C02()) {
        emitLine("bit  #$mask")
    } else {
        // 6502: bit only works with memory; use and to test
        emitLine("and  #$mask")
        // Z flag set if result is zero (bit not set)
    }
}

private fun AsmGen.bitSet(reg: Int, bit: Int) {
    val mask = 1 shl bit
    if (mask <= 0xff) {
        emitLine("lda  ${regAddrLo(reg)}")
        emitLine("ora  #$mask")
        emitLine("sta  ${regAddrLo(reg)}")
    } else {
        // high byte bit
        val hbit = bit - 8
        emitLine("lda  ${regAddrHi(reg)}")
        emitLine("ora  #${1 shl hbit}")
        emitLine("sta  ${regAddrHi(reg)}")
    }
}

private fun AsmGen.bitClear(reg: Int, bit: Int) {
    val mask = 1 shl bit
    if (mask <= 0xff) {
        emitLine("lda  ${regAddrLo(reg)}")
        emitLine("and  #${0xff xor mask}")
        emitLine("sta  ${regAddrLo(reg)}")
    } else {
        val hbit = bit - 8
        emitLine("lda  ${regAddrHi(reg)}")
        emitLine("and  #${0xff xor (1 shl hbit)}")
        emitLine("sta  ${regAddrHi(reg)}")
    }
}

private fun AsmGen.bitToggle(reg: Int, bit: Int) {
    val mask = 1 shl bit
    if (mask <= 0xff) {
        emitLine("lda  ${regAddrLo(reg)}")
        emitLine("eor  #$mask")
        emitLine("sta  ${regAddrLo(reg)}")
    } else {
        val hbit = bit - 8
        emitLine("lda  ${regAddrHi(reg)}")
        emitLine("eor  #${1 shl hbit}")
        emitLine("sta  ${regAddrHi(reg)}")
    }
}

// === LONG byte loop helpers ===
// These operations have no carry dependency, so a simple Y-loop works.
// Keep carry-dependent ops (shifts, rotates) unrolled.

private fun AsmGen.byteLoop4(targetBase: String, srcBase: String, op: String) {
    emitLine("ldy  #3")
    emitLine("-  lda  $targetBase,y")
    emitLine("$op  $srcBase,y")
    emitLine("sta  $targetBase,y")
    emitLine("dey")
    emitLine("bpl  -")
}

private fun AsmGen.byteLoop4Unary(targetBase: String, op: String) {
    emitLine("ldy  #3")
    emitLine("-  lda  $targetBase,y")
    emitLine(op)
    emitLine("sta  $targetBase,y")
    emitLine("dey")
    emitLine("bpl  -")
}
