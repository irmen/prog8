/*
 * LOAD and STORE IR instruction translations for the new6502gen code generator.
 *
 * Translates all variants of LOAD/STORE opcodes into 6502/65C02 assembly.
 * Key variants:
 *   LOAD     - immediate value -> virtual register
 *   LOADM    - memory address -> virtual register
 *   LOADR    - virtual register -> virtual register (copy)
 *   LOADX    - memory[address + index_reg] -> virtual register (indexed load)
 *   LOADHR   - physical CPU register (slot 0-5) -> virtual register
 *   LOADI    - memory[base_reg + offset] -> virtual register (indirect load)
 *   STOREM   - virtual register -> memory address
 *   STOREX   - exchange virtual register with memory
 *   STOREZM  - zero memory at address
 *   STOREZI  - zero memory at [base_reg + offset]
 *   STOREZX  - zero memory at [address + index_reg]
 *   STOREHR  - virtual register -> physical CPU register (slot 0-5)
 *   STOREI   - virtual register -> memory[base_reg + offset] (indirect store)
 *
 * Slot mapping (for LOADHR/STOREHR):
 *   0 = A (byte), 1 = X (byte), 2 = Y (byte),
 *   3 = AX (word), 4 = AY (word), 5 = XY (word)
 *
 * Zero page temporary location $22-$23 is used for address computation.
 */

package codegen

import prog8.code.core.*
import prog8.intermediate.*

fun CodeGenerator.translateLoadStore(insn: IRInstruction) {
    val type = insn.type ?: IRDataType.BYTE
    val r1 = insn.reg1          // nullable - STOREZM and float ops have no reg1
    val r2 = insn.reg2
    val imm = insn.immediate
    val addr = insn.address
    val label = insn.labelSymbol
    val offset = insn.labelSymbolOffset

    when (insn.opcode) {
        Opcode.LOAD -> {
            val value = insn.immediate
            val sym = insn.labelSymbol
            when {
                value != null -> loadImmediate(r1 ?: error("LOAD needs reg1"), value, type)
                sym != null -> loadSymbolAddress(r1 ?: error("LOAD needs reg1"), sym, insn.labelSymbolOffset, type)
                else -> error("LOAD needs immediate or labelSymbol")
            }
        }

        Opcode.LOADM -> {
            val target = resolveAddress(addr, label, offset)
            loadFromMemory(r1 ?: error("LOADM needs reg1"), target, type)
        }

        Opcode.LOADR -> {
            val src = r2 ?: error("LOADR needs reg2")
            copyRegister(r1 ?: error("LOADR needs reg1"), src, type)
        }

        Opcode.LOADX -> {
            val idxReg = r2 ?: error("LOADX needs reg2")
            val base = resolveAddress(addr, label, offset)
            indexedLoad(r1 ?: error("LOADX needs reg1"), idxReg, base, type)
        }

        Opcode.LOADHR -> {
            loadFromHardwareReg(r1 ?: error("LOADHR needs reg1"), imm ?: error("LOADHR needs slot"), type)
        }

        Opcode.LOADI -> {
            val baseReg = r2 ?: error("LOADI needs reg2")
            val offset = insn.immediate ?: 0
            indirectLoad(r1 ?: error("LOADI needs reg1"), baseReg, offset, type)
        }

        Opcode.STOREM -> {
            val target = resolveAddress(addr, label, offset)
            storeToMemory(r1 ?: error("STOREM needs reg1"), target, type)
        }

        Opcode.STOREX -> {
            val r2val = r2 ?: error("STOREX needs reg2")
            val target = resolveAddress(addr, label, offset)
            storeExchange(r1 ?: error("STOREX needs reg1"), r2val, target, type)
        }

        Opcode.STOREZM -> {
            val target = resolveAddress(addr, label, offset)
            zeroMemory(target, type)
        }

        Opcode.STOREZI -> {
            val offset = insn.immediate ?: 0
            zeroIndexed(r1 ?: error("STOREZI needs reg1"), offset, type)
        }

        Opcode.STOREZX -> {
            val target = resolveAddress(addr, label, offset)
            zeroMemoryIndexed(r1 ?: error("STOREZX needs reg1"), target, type)
        }

        Opcode.STOREHR -> {
            storeToHardwareReg(r1 ?: error("STOREHR needs reg1"), imm ?: error("STOREHR needs slot"), type)
        }

        Opcode.STOREI -> {
            val baseReg = r2 ?: error("STOREI needs reg2")
            val offset = insn.immediate ?: 0
            indirectStore(r1 ?: error("STOREI needs reg1"), baseReg, offset, type)
        }

        Opcode.LOADHFACZERO -> TODO("LOADHFACZERO (float)")
        Opcode.LOADHFACONE -> TODO("LOADHFACONE (float)")
        Opcode.STOREHFACZERO -> TODO("STOREHFACZERO (float)")
        Opcode.STOREHFACONE -> TODO("STOREHFACONE (float)")

        else -> error("Unknown load/store opcode: ${insn.opcode}")
    }
}

// === Hardware register (slot) operations ===

private fun CodeGenerator.loadFromHardwareReg(virtualReg: Int, slot: Int, type: IRDataType) {
    // LOADHR: move value FROM physical CPU register (slot) INTO virtual register file
    when (slot) {
        0 -> {
            // slot s0 = A (byte)
            emitLine("sta  ${regAddrLo(virtualReg)}")
            if (type == IRDataType.WORD) {
                emitLine("stx  ${regAddrHi(virtualReg)}")
            }
        }
        1 -> {
            // slot s1 = X (byte)
            emitLine("stx  ${regAddrLo(virtualReg)}")
        }
        2 -> {
            // slot s2 = Y (byte)
            emitLine("sty  ${regAddrLo(virtualReg)}")
        }
        3 -> {
            // slot s3 = AX (word)
            emitLine("sta  ${regAddrLo(virtualReg)}")
            emitLine("stx  ${regAddrHi(virtualReg)}")
        }
        4 -> {
            // slot s4 = AY (word)
            emitLine("sta  ${regAddrLo(virtualReg)}")
            emitLine("sty  ${regAddrHi(virtualReg)}")
        }
        5 -> {
            // slot s5 = XY (word)
            emitLine("stx  ${regAddrLo(virtualReg)}")
            emitLine("sty  ${regAddrHi(virtualReg)}")
        }
        else -> emitLine("; LOADHR: unknown slot $slot")
    }
}

private fun CodeGenerator.storeToHardwareReg(virtualReg: Int, slot: Int, type: IRDataType) {
    // STOREHR: move value FROM virtual register file INTO physical CPU register (slot)
    when (slot) {
        0 -> {
            // slot s0 = A (byte)
            emitLine("lda  ${regAddrLo(virtualReg)}")
        }
        1 -> {
            // slot s1 = X (byte)
            emitLine("ldx  ${regAddrLo(virtualReg)}")
        }
        2 -> {
            // slot s2 = Y (byte)
            emitLine("ldy  ${regAddrLo(virtualReg)}")
        }
        3 -> {
            // slot s3 = AX (word)
            emitLine("lda  ${regAddrLo(virtualReg)}")
            emitLine("ldx  ${regAddrHi(virtualReg)}")
        }
        4 -> {
            // slot s4 = AY (word)
            emitLine("lda  ${regAddrLo(virtualReg)}")
            emitLine("ldy  ${regAddrHi(virtualReg)}")
        }
        5 -> {
            // slot s5 = XY (word)
            emitLine("ldx  ${regAddrLo(virtualReg)}")
            emitLine("ldy  ${regAddrHi(virtualReg)}")
        }
        else -> emitLine("; STOREHR: unknown slot $slot")
    }
}

// === helper implementations ===

private fun CodeGenerator.loadSymbolAddress(reg: Int, sym: String, offset: Int?, type: IRDataType) {
    val resolved = resolveSymbolRef(sym)
    val symWithOffset = if (offset != null) "$resolved+$offset" else resolved
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  #<${symWithOffset}")
            emitLine("sta  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  #<${symWithOffset}")
            emitLine("sta  ${regAddrLo(reg)}")
            emitLine("lda  #>${symWithOffset}")
            emitLine("sta  ${regAddrHi(reg)}")
        }
        IRDataType.LONG -> {
            emitLine("lda  #<${symWithOffset}")
            emitLine("sta  ${regAddrLo(reg)}")
            emitLine("lda  #>${symWithOffset}")
            emitLine("sta  ${regAddrHi(reg)}")
            emitLine("lda  #0")
            emitLine("sta  ${regAddrByte(reg, 2)}")
            emitLine("sta  ${regAddrByte(reg, 3)}")
        }
        else -> TODO("SYMLOAD r$reg, $symWithOffset ${type.name}")
    }
}

private fun CodeGenerator.loadImmediate(reg: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  #${value and 0xff}")
            emitLine("sta  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  #<${value and 0xffff}")
            emitLine("sta  ${regAddrLo(reg)}")
            emitLine("lda  #>${value and 0xffff}")
            emitLine("sta  ${regAddrHi(reg)}")
        }
        IRDataType.LONG -> {
            emitLine("lda  #<${value and 0xffff}")
            emitLine("sta  ${regAddrLo(reg)}")
            emitLine("lda  #>${value and 0xffff}")
            emitLine("sta  ${regAddrHi(reg)}")
            emitLine("lda  #${(value ushr 16) and 0xff}")
            emitLine("sta  ${regAddrByte(reg, 2)}")
            emitLine("lda  #${(value ushr 24) and 0xff}")
            emitLine("sta  ${regAddrByte(reg, 3)}")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT LOAD immediate r$reg = $value")
        }
    }
}

private fun CodeGenerator.loadFromMemory(reg: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  $source")
            emitLine("sta  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  $source")
            emitLine("sta  ${regAddrLo(reg)}")
            emitLine("lda  $source+1")
            emitLine("sta  ${regAddrHi(reg)}")
        }
        IRDataType.LONG -> {
            emitLine("lda  $source")
            emitLine("sta  ${regAddrLo(reg)}")
            emitLine("lda  $source+1")
            emitLine("sta  ${regAddrHi(reg)}")
            emitLine("lda  $source+2")
            emitLine("sta  ${regAddrByte(reg, 2)}")
            emitLine("lda  $source+3")
            emitLine("sta  ${regAddrByte(reg, 3)}")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT LOADM r$reg from $source")
        }
    }
}

private fun CodeGenerator.copyRegister(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(src)}")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> {
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(src)}")
            emitLine("sta  ${regAddrHi(dst)}")
            emitLine("lda  ${regAddrByte(src, 2)}")
            emitLine("sta  ${regAddrLo(dst) + 2}")
            emitLine("lda  ${regAddrHi(src) + 2}")
            emitLine("sta  ${regAddrLo(dst) + 3}")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT LOADR r$dst = r$src")
        }
    }
}

private fun CodeGenerator.storeToMemory(reg: Int, target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("sta  $target")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("sta  $target")
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("sta  $target+1")
        }
        IRDataType.LONG -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("sta  $target")
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("sta  $target+1")
            emitLine("lda  ${regAddrByte(reg, 2)}")
            emitLine("sta  $target+2")
            emitLine("lda  ${regAddrByte(reg, 3)}")
            emitLine("sta  $target+3")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT STOREM r$reg -> $target")
        }
    }
}

private fun CodeGenerator.indexedLoad(dst: Int, idxReg: Int, base: String, type: IRDataType) {
    val ptr = ZP_TEMP
    emitLine("lda  #<$base")
    emitLine("sta  $ptr")
    emitLine("lda  #>$base")
    emitLine("sta  ${ptr}+1")
    emitLine("lda  ${regAddrLo(idxReg)}")
    emitLine("clc")
    emitLine("adc  $ptr")
    emitLine("sta  $ptr")
    emitLine("lda  #0")
    emitLine("adc  ${ptr}+1")
    emitLine("sta  ${ptr}+1")
    when (type) {
        IRDataType.BYTE -> {
            emitLine("ldy  #0")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("ldy  #0")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("ldy  #1")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> {
            emitLine("ldy  #0")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("ldy  #1")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrHi(dst)}")
            emitLine("ldy  #2")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrLo(dst) + 2}")
            emitLine("ldy  #3")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrLo(dst) + 3}")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT LOADX r$dst")
        }
    }
}

private fun CodeGenerator.indirectLoad(dst: Int, baseReg: Int, offset: Int, type: IRDataType) {
    val ptr = ZP_TEMP
    emitLine("lda  ${regAddrLo(baseReg)}")
    emitLine("clc")
    emitLine("adc  #<${offset and 0xffff}")
    emitLine("sta  $ptr")
    emitLine("lda  ${regAddrHi(baseReg)}")
    emitLine("adc  #>${offset and 0xffff}")
    emitLine("sta  ${ptr}+1")
    when (type) {
        IRDataType.BYTE -> {
            emitLine("ldy  #0")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("ldy  #0")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("ldy  #1")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> {
            emitLine("ldy  #0")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("ldy  #1")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrHi(dst)}")
            emitLine("ldy  #2")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrLo(dst) + 2}")
            emitLine("ldy  #3")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrLo(dst) + 3}")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT LOADI r$dst")
        }
    }
}

private fun CodeGenerator.storeExchange(reg: Int, reg2: Int, target: String, type: IRDataType) {
    // STOREX: indexed store -- mem[target + reg2] = reg1
    when (type) {
        IRDataType.BYTE -> {
            emitLine("ldx  ${regAddrLo(reg2)}")
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("sta  $target,x")
        }
        IRDataType.WORD -> {
            emitLine("ldx  ${regAddrLo(reg2)}")
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("sta  $target,x")
            emitLine("ldx  ${regAddrHi(reg2)}")
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("sta  ${target}+1,x")
        }
        IRDataType.LONG -> {
            emitLine("ldx  ${regAddrLo(reg2)}")
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("sta  $target,x")
            emitLine("ldx  ${regAddrHi(reg2)}")
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("sta  ${target}+1,x")
            emitLine("ldx  ${regAddrLo(reg2) + 2}")
            emitLine("lda  ${regAddrByte(reg, 2)}")
            emitLine("sta  ${target}+2,x")
            emitLine("ldx  ${regAddrLo(reg2) + 3}")
            emitLine("lda  ${regAddrByte(reg, 3)}")
            emitLine("sta  ${target}+3,x")
        }
        else -> {
            TODO("STOREX r$reg, r$reg2, $target ${type.name}")
        }
    }
}

private fun CodeGenerator.zeroMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitStoreZero(target)
        }
        IRDataType.WORD -> {
            emitStoreZero(target)
            emitStoreZero("$target+1")
        }
        IRDataType.LONG -> {
            emitStoreZero(target)
            emitStoreZero("$target+1")
            emitStoreZero("$target+2")
            emitStoreZero("$target+3")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT STOREZM $target")
        }
    }
}

private fun CodeGenerator.zeroIndexed(baseReg: Int, offset: Int, type: IRDataType) {
    val ptr = ZP_TEMP
    emitLine("lda  ${regAddrLo(baseReg)}")
    emitLine("clc")
    emitLine("adc  #<${offset and 0xffff}")
    emitLine("sta  $ptr")
    emitLine("lda  ${regAddrHi(baseReg)}")
    emitLine("adc  #>${offset and 0xffff}")
    emitLine("sta  ${ptr}+1")
    when (type) {
        IRDataType.BYTE -> {
            emitLine("ldy  #0")
            if (is65C02())
                emitLine("stz  ($ptr),y")
            else {
                emitLine("lda  #0")
                emitLine("sta  ($ptr),y")
            }
        }
        IRDataType.WORD -> {
            emitLine("ldy  #0")
            if (is65C02())
                emitLine("stz  ($ptr),y")
            else {
                emitLine("lda  #0")
                emitLine("sta  ($ptr),y")
            }
            emitLine("ldy  #1")
            if (is65C02())
                emitLine("stz  ($ptr),y")
            else {
                emitLine("lda  #0")
                emitLine("sta  ($ptr),y")
            }
        }
        IRDataType.LONG -> {
            emitLine("ldy  #0")
            if (is65C02())
                emitLine("stz  ($ptr),y")
            else {
                emitLine("lda  #0")
                emitLine("sta  ($ptr),y")
            }
            emitLine("ldy  #1")
            if (is65C02())
                emitLine("stz  ($ptr),y")
            else {
                emitLine("lda  #0")
                emitLine("sta  ($ptr),y")
            }
            emitLine("ldy  #2")
            if (is65C02())
                emitLine("stz  ($ptr),y")
            else {
                emitLine("lda  #0")
                emitLine("sta  ($ptr),y")
            }
            emitLine("ldy  #3")
            if (is65C02())
                emitLine("stz  ($ptr),y")
            else {
                emitLine("lda  #0")
                emitLine("sta  ($ptr),y")
            }
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT STOREZI r$baseReg + $offset")
        }
    }
}

private fun CodeGenerator.zeroMemoryIndexed(reg: Int, base: String, type: IRDataType) {
    emitLine("ldx  ${regAddrLo(reg)}")
    when (type) {
        IRDataType.BYTE -> {
            if (is65C02())
                emitLine("stz  $base,x")
            else {
                emitLine("lda  #0")
                emitLine("sta  $base,x")
            }
        }
        IRDataType.WORD -> {
            if (is65C02())
                emitLine("stz  $base,x")
            else {
                emitLine("lda  #0")
                emitLine("sta  $base,x")
            }
            emitLine("ldx  ${regAddrHi(reg)}")
            if (is65C02())
                emitLine("stz  ${base}+1,x")
            else {
                emitLine("lda  #0")
                emitLine("sta  ${base}+1,x")
            }
        }
        IRDataType.LONG -> {
            emitLine("; STOREZX LONG not fully implemented")
            if (is65C02())
                emitLine("stz  $base,x")
            else {
                emitLine("lda  #0")
                emitLine("sta  $base,x")
            }
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT STOREZX")
        }
    }
}

private fun CodeGenerator.indirectStore(reg: Int, baseReg: Int, offset: Int, type: IRDataType) {
    val ptr = ZP_TEMP
    emitLine("lda  ${regAddrLo(baseReg)}")
    emitLine("clc")
    emitLine("adc  #<${offset and 0xffff}")
    emitLine("sta  $ptr")
    emitLine("lda  ${regAddrHi(baseReg)}")
    emitLine("adc  #>${offset and 0xffff}")
    emitLine("sta  ${ptr}+1")
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("ldy  #0")
            emitLine("sta  ($ptr),y")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("ldy  #0")
            emitLine("sta  ($ptr),y")
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("ldy  #1")
            emitLine("sta  ($ptr),y")
        }
        IRDataType.LONG -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("ldy  #0")
            emitLine("sta  ($ptr),y")
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("ldy  #1")
            emitLine("sta  ($ptr),y")
            emitLine("lda  ${regAddrByte(reg, 2)}")
            emitLine("ldy  #2")
            emitLine("sta  ($ptr),y")
            emitLine("lda  ${regAddrByte(reg, 3)}")
            emitLine("ldy  #3")
            emitLine("sta  ($ptr),y")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT STOREI r$reg")
        }
    }
}

internal fun CodeGenerator.resolveAddress(addr: MemoryAddress?, label: String?, offset: Int? = null): String {
    return when {
        label != null -> {
            val resolved = resolveSymbolRef(label)
            if (offset != null && offset != 0) "$resolved+$offset" else resolved
        }
        addr != null -> addr.value.toHex()
        else -> "0"
    }
}
