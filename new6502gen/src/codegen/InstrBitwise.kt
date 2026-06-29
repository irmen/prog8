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
        Opcode.ASRM -> TODO("ASRM")
        Opcode.LSR -> logicalShiftRight(r1 ?: error("LSR needs reg1"), 1, type)
        Opcode.LSRM -> TODO("LSRM")
        Opcode.LSL -> logicalShiftLeft(r1 ?: error("LSL needs reg1"), 1, type)
        Opcode.LSLM -> TODO("LSLM")

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
        IRDataType.LONG -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("and  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("and  ${regAddrHi(src)}")
            emitLine("sta  ${regAddrHi(dst)}")
            emitLine("lda  ${regAddrByte(dst, 2)}")
            emitLine("and  ${regAddrByte(src, 2)}")
            emitLine("sta  ${regAddrByte(dst, 2)}")
            emitLine("lda  ${regAddrByte(dst, 3)}")
            emitLine("and  ${regAddrByte(src, 3)}")
            emitLine("sta  ${regAddrByte(dst, 3)}")
        }
        IRDataType.FLOAT -> TODO("FLOAT ANDR r$dst, r$src")
    }
}

private fun CodeGenerator.andImmediate(dst: Int, value: Int, type: IRDataType) {
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
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("and  #<${value and 0xffff}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("and  #>${value and 0xffff}")
            emitLine("sta  ${regAddrHi(dst)}")
            emitLine("lda  ${regAddrByte(dst, 2)}")
            emitLine("and  #${(value ushr 16) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 2)}")
            emitLine("lda  ${regAddrByte(dst, 3)}")
            emitLine("and  #${(value ushr 24) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 3)}")
        }
        IRDataType.FLOAT -> TODO("FLOAT AND r$dst #$value")
    }
}

private fun CodeGenerator.andMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("and  $source")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("and  $source")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("and  $source+1")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        else -> TODO("ANDM r$dst, $source ${type.name}")
    }
}

// === OR ===

private fun CodeGenerator.orRegisters(dst: Int, src: Int, type: IRDataType) {
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
        else -> TODO("ORR r$dst, r$src ${type.name}")
    }
}

private fun CodeGenerator.orImmediate(dst: Int, value: Int, type: IRDataType) {
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
        else -> TODO("OR r$dst, #$value ${type.name}")
    }
}

private fun CodeGenerator.orMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ora  $source")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ora  $source")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("ora  $source+1")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        else -> TODO("ORM r$dst, $source ${type.name}")
    }
}

// === XOR ===

private fun CodeGenerator.xorRegisters(dst: Int, src: Int, type: IRDataType) {
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
        else -> TODO("XORR r$dst, r$src ${type.name}")
    }
}

private fun CodeGenerator.xorImmediate(dst: Int, value: Int, type: IRDataType) {
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
        else -> TODO("XOR r$dst, #$value ${type.name}")
    }
}

private fun CodeGenerator.xorMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("eor  $source")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("eor  $source")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("eor  $source+1")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        else -> TODO("XORM r$dst, $source ${type.name}")
    }
}

// === Invert ===

private fun CodeGenerator.invertRegister(reg: Int, type: IRDataType) {
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
        else -> TODO("INV r$reg ${type.name}")
    }
}

private fun CodeGenerator.invertMemory(target: String, type: IRDataType) {
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
        else -> TODO("INVM $target ${type.name}")
    }
}

// === Shifts ===

private fun CodeGenerator.logicalShiftLeft(reg: Int, count: Int, type: IRDataType) {
    for (i in 1..count) {
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
            IRDataType.FLOAT -> TODO("FLOAT LSLN r$reg, $count")
        }
    }
}

private fun CodeGenerator.logicalShiftLeftMemory(target: String, count: Int, type: IRDataType) {
    for (i in 1..count) {
        when (type) {
            IRDataType.BYTE -> emitLine("asl  $target")
            IRDataType.WORD -> {
                emitLine("asl  $target")
                emitLine("rol  $target+1")
            }
            else -> TODO("LSLNM $target, $count ${type.name}")
        }
    }
}

private fun CodeGenerator.logicalShiftRight(reg: Int, count: Int, type: IRDataType) {
    for (i in 1..count) {
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
            IRDataType.FLOAT -> TODO("FLOAT LSRN r$reg, $count")
        }
    }
}

private fun CodeGenerator.logicalShiftRightMemory(target: String, count: Int, type: IRDataType) {
    for (i in 1..count) {
        when (type) {
            IRDataType.BYTE -> emitLine("lsr  $target")
            IRDataType.WORD -> {
                emitLine("lsr  $target+1")
                emitLine("ror  $target")
            }
            else -> TODO("LSRNM $target, $count ${type.name}")
        }
    }
}

private fun CodeGenerator.arithmeticShiftRight(reg: Int, count: Int, type: IRDataType) {
    for (i in 1..count) {
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
            else -> TODO("ASRN r$reg, $count ${type.name}")
        }
    }
}

private fun CodeGenerator.arithmeticShiftRightMemory(target: String, count: Int, type: IRDataType) {
    TODO("ASRNM $target, $count")
}

private fun CodeGenerator.logicalShiftLeftVar(reg: Int, countReg: Int, type: IRDataType) {
    emitLine("ldx  ${regAddrLo(countReg)}")
    emitLine("beq  +")
    emitLabel("loop")
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
            emitLine("rol  ${regAddrLo(reg) + 1}")
            emitLine("rol  ${regAddrByte(reg, 2)}")
            emitLine("rol  ${regAddrByte(reg, 3)}")
        }
        else -> TODO("LSL r$reg, r$countReg ${type.name}")
    }
    emitLine("dex")
    emitLine("bne  loop")
    emitLabel("+")
}

private fun CodeGenerator.logicalShiftRightVar(reg: Int, countReg: Int, type: IRDataType) {
    emitLine("ldx  ${regAddrLo(countReg)}")
    emitLine("beq  +")
    emitLabel("loop")
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
            emitLine("ror  ${regAddrLo(reg) + 1}")
            emitLine("ror  ${regAddrLo(reg)}")
        }
        else -> TODO("LSR r$reg, r$countReg ${type.name}")
    }
    emitLine("dex")
    emitLine("bne  loop")
    emitLabel("+")
}

private fun CodeGenerator.arithmeticShiftRightVar(reg: Int, countReg: Int, type: IRDataType) {
    emitLine("ldx  ${regAddrLo(countReg)}")
    emitLine("beq  +")
    emitLabel("loop")
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
            emitLine("ror  ${regAddrLo(reg) + 1}")
            emitLine("ror  ${regAddrLo(reg)}")
        }
        else -> TODO("ASR r$reg, r$countReg ${type.name}")
    }
    emitLine("dex")
    emitLine("bne  loop")
    emitLabel("+")
}

// === Memory variable-count shifts ===

private fun CodeGenerator.shiftMemoryVar(target: String, countReg: Int, type: IRDataType, isArithmetic: Boolean) {
    emitLine("ldx  ${regAddrLo(countReg)}")
    emitLine("beq  +")
    emitLabel("shiftmem_loop")
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
    emitLine("bne  shiftmem_loop")
    emitLabel("+")
}

private fun CodeGenerator.shiftMemoryLeftVar(target: String, countReg: Int, type: IRDataType) {
    emitLine("ldx  ${regAddrLo(countReg)}")
    emitLine("beq  +")
    emitLabel("shiftmeml_loop")
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
    emitLine("bne  shiftmeml_loop")
    emitLabel("+")
}

// === Rotates ===

private fun CodeGenerator.rotateLeft(reg: Int, type: IRDataType) {
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
        else -> TODO("ROL r$reg ${type.name}")
    }
}

private fun CodeGenerator.rotateLeftMemory(target: String, type: IRDataType) {
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
        else -> TODO("ROLM $target ${type.name}")
    }
}

private fun CodeGenerator.rotateRight(reg: Int, type: IRDataType) {
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
        else -> TODO("ROR r$reg ${type.name}")
    }
}

private fun CodeGenerator.rotateRightMemory(target: String, type: IRDataType) {
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
        else -> TODO("RORM $target ${type.name}")
    }
}

private fun CodeGenerator.rotateLeftThroughCarry(reg: Int, type: IRDataType) {
    // rotate through carry (like the 6502 ROL instruction does)
    when (type) {
        IRDataType.BYTE -> {
            emitLine("rol  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("rol  ${regAddrLo(reg)}")
            emitLine("rol  ${regAddrHi(reg)}")
        }
        else -> TODO("ROXL r$reg ${type.name}")
    }
}

private fun CodeGenerator.rotateRightThroughCarry(reg: Int, type: IRDataType) {
    // rotate through carry (like the 6502 ROR instruction does)
    when (type) {
        IRDataType.BYTE -> {
            emitLine("ror  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("ror  ${regAddrHi(reg)}")
            emitLine("ror  ${regAddrLo(reg)}")
        }
        else -> TODO("ROXR r$reg ${type.name}")
    }
}

private fun CodeGenerator.rotateLeftThroughCarryMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("rol  $target")
        }
        IRDataType.WORD -> {
            emitLine("rol  $target")
            emitLine("rol  $target+1")
        }
        else -> TODO("ROXLM $target ${type.name}")
    }
}

private fun CodeGenerator.rotateRightThroughCarryMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("ror  $target")
        }
        IRDataType.WORD -> {
            emitLine("ror  $target+1")
            emitLine("ror  $target")
        }
        else -> TODO("ROXRM $target ${type.name}")
    }
}

// === Bit manipulation ===

private fun CodeGenerator.bitTest(reg: Int, bit: Int, type: IRDataType) {
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

private fun CodeGenerator.bitSet(reg: Int, bit: Int, type: IRDataType) {
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

private fun CodeGenerator.bitClear(reg: Int, bit: Int, type: IRDataType) {
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

private fun CodeGenerator.bitToggle(reg: Int, bit: Int, type: IRDataType) {
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
