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

import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction
import prog8.intermediate.Opcode

internal fun AsmGen.translateBitwise(insn: IRInstruction) {
    val type = insn.type ?: IRDataType.BYTE
    val r1 = insn.reg1          // nullable - memory-only ops (INVM, ASRM, etc.) have no reg1
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
            val source = resolveAddress(addr, label, offset)
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
            val source = resolveAddress(addr, label, offset)
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
            val source = resolveAddress(addr, label, offset)
            xorMemory(r1 ?: error("XORM needs reg1"), source, type)
        }

        Opcode.INV -> invertRegister(r1 ?: error("INV needs reg1"), type)
        Opcode.INVM -> invertMemory(resolveAddress(addr, label, offset), type)

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
        Opcode.ASRNM -> shiftMemoryVar(resolveAddress(addr, label, offset), r1 ?: error("ASRNM needs reg1"), type, isArithmetic = true)
        Opcode.LSRNM -> shiftMemoryVar(resolveAddress(addr, label, offset), r1 ?: error("LSRNM needs reg1"), type, isArithmetic = false)
        Opcode.LSLNM -> shiftMemoryLeftVar(resolveAddress(addr, label, offset), r1 ?: error("LSLNM needs reg1"), type)

        // Shift by 1 (ASR/LSR/LSL: single-bit shift, no count operand)
        Opcode.ASR -> arithmeticShiftRight(r1 ?: error("ASR needs reg1"), 1, type)
        Opcode.ASRM -> arithmeticShiftRightMemory(resolveAddress(addr, label, offset), 1, type)
        Opcode.LSR -> logicalShiftRight(r1 ?: error("LSR needs reg1"), 1, type)
        Opcode.LSRM -> logicalShiftRightMemory(resolveAddress(addr, label, offset), 1, type)
        Opcode.LSL -> logicalShiftLeft(r1 ?: error("LSL needs reg1"), 1, type)
        Opcode.LSLM -> logicalShiftLeftMemory(resolveAddress(addr, label, offset), 1, type)

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
            bitSet(r1 ?: error("BITSET needs reg1"), bit)
        }
        Opcode.BITCLR -> {
            val bit = imm ?: error("BITCLR needs bit number")
            bitClear(r1 ?: error("BITCLR needs reg1"), bit)
        }
        Opcode.BITTOG -> {
            val bit = imm ?: error("BITTOG needs bit number")
            bitToggle(r1 ?: error("BITTOG needs reg1"), bit)
        }

        else -> error("Unknown bitwise opcode: ${insn.opcode}")
    }
}

// === AND ===

private fun AsmGen.andRegisters(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("and  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("and  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("and  ${regAddrHi(src)}")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> byteLoop4(regAddrByte(dst, 0), regAddrByte(src, 0), "and")
        IRDataType.FLOAT -> error("bitwise operations are not supported on floats")
    }
}

private fun AsmGen.andImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("and  #${value and 0xff}")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("and  #<${value and 0xffff}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("and  #>${value and 0xffff}")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> {
            emitLine("lda  ${regAddrByte(dst, 0)}")
            emitLine("and  #${value and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 0)}")
            emitLine("lda  ${regAddrByte(dst, 1)}")
            emitLine("and  #${(value shr 8) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 1)}")
            emitLine("lda  ${regAddrByte(dst, 2)}")
            emitLine("and  #${(value shr 16) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 2)}")
            emitLine("lda  ${regAddrByte(dst, 3)}")
            emitLine("and  #${(value shr 24) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 3)}")
        }
        IRDataType.FLOAT -> error("bitwise operations are not supported on floats")
    }
}

private fun AsmGen.andMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  $source")
            emitLine("and  ${regAddrLo(dst)}")
            emitLine("sta  $source")
        }
        IRDataType.WORD -> {
            emitLine("lda  $source")
            emitLine("and  ${regAddrLo(dst)}")
            emitLine("sta  $source")
            emitLine("lda  $source+1")
            emitLine("and  ${regAddrHi(dst)}")
            emitLine("sta  $source+1")
        }
        IRDataType.LONG -> byteLoop4(source, regAddrByte(dst, 0), "and")
        else -> error("ANDM not supported on ${type.name}")
    }
}

// === OR ===

private fun AsmGen.orRegisters(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ora  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ora  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("ora  ${regAddrHi(src)}")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> byteLoop4(regAddrByte(dst, 0), regAddrByte(src, 0), "ora")
        else -> error("ORR not supported on ${type.name}")
    }
}

private fun AsmGen.orImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ora  #${value and 0xff}")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ora  #<${value and 0xffff}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("ora  #>${value and 0xffff}")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> {
            emitLine("lda  ${regAddrByte(dst, 0)}")
            emitLine("ora  #${value and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 0)}")
            emitLine("lda  ${regAddrByte(dst, 1)}")
            emitLine("ora  #${(value shr 8) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 1)}")
            emitLine("lda  ${regAddrByte(dst, 2)}")
            emitLine("ora  #${(value shr 16) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 2)}")
            emitLine("lda  ${regAddrByte(dst, 3)}")
            emitLine("ora  #${(value shr 24) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 3)}")
        }
        else -> error("OR not supported on ${type.name}")
    }
}

private fun AsmGen.orMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  $source")
            emitLine("ora  ${regAddrLo(dst)}")
            emitLine("sta  $source")
        }
        IRDataType.WORD -> {
            emitLine("lda  $source")
            emitLine("ora  ${regAddrLo(dst)}")
            emitLine("sta  $source")
            emitLine("lda  $source+1")
            emitLine("ora  ${regAddrHi(dst)}")
            emitLine("sta  $source+1")
        }
        IRDataType.LONG -> byteLoop4(source, regAddrByte(dst, 0), "ora")
        else -> error("ORM not supported on ${type.name}")
    }
}

// === XOR ===

private fun AsmGen.xorRegisters(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("eor  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("eor  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("eor  ${regAddrHi(src)}")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> byteLoop4(regAddrByte(dst, 0), regAddrByte(src, 0), "eor")
        else -> error("XORR not supported on ${type.name}")
    }
}

private fun AsmGen.xorImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("eor  #${value and 0xff}")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("eor  #<${value and 0xffff}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("eor  #>${value and 0xffff}")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> {
            emitLine("lda  ${regAddrByte(dst, 0)}")
            emitLine("eor  #${value and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 0)}")
            emitLine("lda  ${regAddrByte(dst, 1)}")
            emitLine("eor  #${(value shr 8) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 1)}")
            emitLine("lda  ${regAddrByte(dst, 2)}")
            emitLine("eor  #${(value shr 16) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 2)}")
            emitLine("lda  ${regAddrByte(dst, 3)}")
            emitLine("eor  #${(value shr 24) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 3)}")
        }
        else -> error("XOR not supported on ${type.name}")
    }
}

private fun AsmGen.xorMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  $source")
            emitLine("eor  ${regAddrLo(dst)}")
            emitLine("sta  $source")
        }
        IRDataType.WORD -> {
            emitLine("lda  $source")
            emitLine("eor  ${regAddrLo(dst)}")
            emitLine("sta  $source")
            emitLine("lda  $source+1")
            emitLine("eor  ${regAddrHi(dst)}")
            emitLine("sta  $source+1")
        }
        IRDataType.LONG -> byteLoop4(source, regAddrByte(dst, 0), "eor")
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
            IRDataType.WORD -> {
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
            IRDataType.WORD -> {
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
    val loopLabel = makeLabel("asr_var_loop")
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
    emitLine("bne  loop")
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
