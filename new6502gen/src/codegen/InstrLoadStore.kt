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

import prog8.code.core.toHex
import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction
import prog8.intermediate.MemoryAddress
import prog8.intermediate.Opcode

fun CodeGenerator.translateLoadStore(insn: IRInstruction) {
    val type = insn.type ?: IRDataType.BYTE
    val r1 = insn.reg1          // nullable - STOREZM and float ops have no reg1
    val r2 = insn.reg2
    val imm = insn.immediate
    val addr = insn.address
    val label = insn.labelSymbol
    val offset = insn.labelSymbolOffset

    // FLOAT operations use fpReg1/fpReg2 instead of reg1/reg2
    if (type == IRDataType.FLOAT) {
        translateFloatLoadStore(insn)
        return
    }

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
            storeToHardwareReg(r1 ?: error("STOREHR needs reg1"), imm ?: error("STOREHR needs slot"))
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

private fun CodeGenerator.translateFloatLoadStore(insn: IRInstruction) {
    val fpReg1 = insn.fpReg1
    val fpReg2 = insn.fpReg2
    val imm = insn.immediate
    val addr = insn.address
    val label = insn.labelSymbol
    val offset = insn.labelSymbolOffset
    val r1 = insn.reg1          // index register for LOADX/LOADI

    when (insn.opcode) {
        Opcode.LOAD -> {
            val value = insn.immediateFp
            val sym = insn.labelSymbol
            when {
                value != null -> {
                    val fpReg = fpReg1 ?: error("FLOAT LOAD needs fpReg1")
                    val constLabel = getFloatConstLabel(value)
                    emitLine("lda  #<$constLabel")
                    emitLine("ldy  #>$constLabel")
                    emitLine("jsr  floats.MOVFM")
                    emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
                    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
                    emitLine("jsr  floats.MOVMF")
                }
                sym != null -> {
                    val fpReg = fpReg1 ?: error("FLOAT LOAD needs fpReg1")
                    val resolved = resolveSymbolRef(sym)
                    val symWithOffset = if (offset != null) "$resolved+$offset" else resolved
                    emitLine("lda  #<$symWithOffset")
                    emitLine("ldy  #>$symWithOffset")
                    emitLine("jsr  floats.MOVFM")
                    emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
                    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
                    emitLine("jsr  floats.MOVMF")
                }
                else -> error("FLOAT LOAD needs immediate or labelSymbol")
            }
        }

        Opcode.LOADM -> {
            val target = resolveAddress(addr, label, offset)
            val fpReg = fpReg1 ?: error("LOADM.f needs fpReg1")
            emitLine("lda  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVFM")
            emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.LOADR -> {
            val src = fpReg2 ?: error("LOADR.f needs fpReg2")
            val dst = fpReg1 ?: error("LOADR.f needs fpReg1")
            emitLine("lda  #<${fpRegAddr(src.value)}")
            emitLine("ldy  #>${fpRegAddr(src.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("ldx  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.STOREM -> {
            val target = resolveAddress(addr, label, offset)
            val fpReg = fpReg1 ?: error("STOREM.f needs fpReg1")
            emitLine("lda  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("ldx  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.LOADHR -> {
            val slot = imm ?: error("LOADHR.f needs slot")
            val fpReg = fpReg1 ?: error("LOADHR.f needs fpReg1")
            when (slot) {
                6 -> {
                    emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
                    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
                    emitLine("jsr  floats.MOVMF")
                }
                7 -> {
                    emitLine("jsr  floats.MOVFA")
                    emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
                    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
                    emitLine("jsr  floats.MOVMF")
                }
                else -> emitLine("; LOADHR.f: unknown slot $slot")
            }
        }

        Opcode.STOREHR -> {
            val slot = imm ?: error("STOREHR.f needs slot")
            val fpReg = fpReg1 ?: error("STOREHR.f needs fpReg1")
            when (slot) {
                6 -> {
                    emitLine("lda  #<${fpRegAddr(fpReg.value)}")
                    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
                    emitLine("jsr  floats.MOVFM")
                }
                7 -> {
                    emitLine("lda  #<${fpRegAddr(fpReg.value)}")
                    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
                    emitLine("jsr  floats.MOVFM")
                    emitLine("jsr  floats.MOVAF")
                }
                else -> emitLine("; STOREHR.f: unknown slot $slot")
            }
        }

        Opcode.LOADX -> {
            val idxReg = r1 ?: error("LOADX.f needs reg1 (index)")
            val base = resolveAddress(addr, label, offset)
            val fpReg = fpReg1 ?: error("LOADX.f needs fpReg1")
            val ptr = ZP_TEMP
            emitLine("lda  #<$base")
            emitLine("sta  $ptr")
            emitLine("lda  #>$base")
            emitLine("sta  ${ptr}+1")
            emitLine("lda  ${regAddrLo(idxReg)}")
            emitLine("asl  a")
            emitLine("asl  a")
            emitLine("clc")
            emitLine("adc  ${regAddrLo(idxReg)}")
            emitLine("clc")
            emitLine("adc  $ptr")
            emitLine("sta  $ptr")
            emitLine("bcc  +")
            emitLine("inc  ${ptr}+1")
            emitLabel("+")
            emitLine("lda  $ptr")
            emitLine("ldy  ${ptr}+1")
            emitLine("jsr  floats.MOVFM")
            emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.LOADI -> {
            val baseReg = r1 ?: error("LOADI.f needs reg1 (base ptr)")
            val offsetVal = insn.immediate ?: 0
            val fpReg = fpReg1 ?: error("LOADI.f needs fpReg1")
            val ptr = ZP_TEMP
            emitLine("lda  ${regAddrLo(baseReg)}")
            emitLine("clc")
            emitLine("adc  #<${offsetVal and 0xffff}")
            emitLine("sta  $ptr")
            emitLine("lda  ${regAddrHi(baseReg)}")
            emitLine("adc  #>${offsetVal and 0xffff}")
            emitLine("sta  ${ptr}+1")
            emitLine("lda  $ptr")
            emitLine("ldy  ${ptr}+1")
            emitLine("jsr  floats.MOVFM")
            emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.STOREI -> {
            val baseReg = r1 ?: error("STOREI.f needs reg1 (base ptr)")
            val offsetVal = insn.immediate ?: 0
            val fpReg = fpReg1 ?: error("STOREI.f needs fpReg1")
            val ptr = ZP_TEMP
            emitLine("lda  ${regAddrLo(baseReg)}")
            emitLine("clc")
            emitLine("adc  #<${offsetVal and 0xffff}")
            emitLine("sta  $ptr")
            emitLine("lda  ${regAddrHi(baseReg)}")
            emitLine("adc  #>${offsetVal and 0xffff}")
            emitLine("sta  ${ptr}+1")
            emitLine("lda  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("lda  $ptr")
            emitLine("ldy  ${ptr}+1")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.STOREX -> {
            val idxReg = r1 ?: error("STOREX.f needs reg1 (index)")
            val base = resolveAddress(addr, label, offset)
            val fpReg = fpReg1 ?: error("STOREX.f needs fpReg1")
            val ptr = ZP_TEMP
            emitLine("lda  #<$base")
            emitLine("sta  $ptr")
            emitLine("lda  #>$base")
            emitLine("sta  ${ptr}+1")
            emitLine("lda  ${regAddrLo(idxReg)}")
            emitLine("asl  a")
            emitLine("asl  a")
            emitLine("clc")
            emitLine("adc  ${regAddrLo(idxReg)}")
            emitLine("clc")
            emitLine("adc  $ptr")
            emitLine("sta  $ptr")
            emitLine("bcc  +")
            emitLine("inc  ${ptr}+1")
            emitLabel("+")
            emitLine("lda  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("lda  $ptr")
            emitLine("ldy  ${ptr}+1")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.STOREZM -> {
            val memTarget = resolveAddress(addr, label, offset)
            val n = floatMemSize - 1
            // Note: the 65C02 STZ instruction does NOT support Y-indexed addressing
            // (only zp, zp,X, abs, abs,X). We use lda #0 / sta for both 6502 and 65C02
            // here because the target size (floatMemSize) is small (5) and the
            // code size is the same.
            emitLine("ldy  #$n")
            emitLine("lda  #0")
            emitLine("-  sta  $memTarget,y")
            emitLine("dey")
            emitLine("bpl  -")
        }

        Opcode.STOREZI -> {
            val baseReg = r1 ?: error("STOREZI.f needs reg1 (base ptr)")
            val offsetVal = insn.immediate ?: 0
            val ptr = ZP_TEMP
            emitLine("lda  ${regAddrLo(baseReg)}")
            emitLine("clc")
            emitLine("adc  #<${offsetVal and 0xffff}")
            emitLine("sta  $ptr")
            emitLine("lda  ${regAddrHi(baseReg)}")
            emitLine("adc  #>${offsetVal and 0xffff}")
            emitLine("sta  ${ptr}+1")
            val n = floatMemSize - 1
            emitLine("ldy  #$n")
            emitLine("lda  #0")
            emitLine("-  sta  ($ptr),y")
            emitLine("dey")
            emitLine("bpl  -")
        }

        Opcode.STOREZX -> {
            val idxReg = r1 ?: error("STOREZX.f needs reg1 (index)")
            val base = resolveAddress(addr, label, offset)
            val ptr = ZP_TEMP
            emitLine("lda  #<$base")
            emitLine("sta  $ptr")
            emitLine("lda  #>$base")
            emitLine("sta  ${ptr}+1")
            emitLine("lda  ${regAddrLo(idxReg)}")
            emitLine("asl  a")
            emitLine("asl  a")
            emitLine("clc")
            emitLine("adc  ${regAddrLo(idxReg)}")
            emitLine("clc")
            emitLine("adc  $ptr")
            emitLine("sta  $ptr")
            emitLine("bcc  +")
            emitLine("inc  ${ptr}+1")
            emitLabel("+")
            val n = floatMemSize - 1
            emitLine("ldy  #$n")
            emitLine("lda  #0")
            emitLine("-  sta  ($ptr),y")
            emitLine("dey")
            emitLine("bpl  -")
        }

        Opcode.LOADHFACZERO -> {
            // Load hardware FAC1 (main accumulator) into fp reg
            val fpReg = fpReg1 ?: error("LOADHFACZERO needs fpReg1")
            emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.LOADHFACONE -> {
            // Load hardware FAC2 (argument register) into fp reg
            // First copy FAC2 to FAC1, then store
            val fpReg = fpReg1 ?: error("LOADHFACONE needs fpReg1")
            emitLine("jsr  floats.MOVFA")
            emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.STOREHFACZERO -> {
            // Store fp reg into hardware FAC1 (main accumulator)
            val fpReg = fpReg1 ?: error("STOREHFACZERO needs fpReg1")
            emitLine("lda  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVFM")
        }

        Opcode.STOREHFACONE -> {
            // Store fp reg into hardware FAC2 (argument register)
            val fpReg = fpReg1 ?: error("STOREHFACONE needs fpReg1")
            emitLine("lda  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.MOVAF")
        }

        else -> error("Unknown float load/store opcode: ${insn.opcode}")
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

private fun CodeGenerator.storeToHardwareReg(virtualReg: Int, slot: Int) {
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
        IRDataType.LONG -> loadFromMemoryLong(reg, source)
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
        IRDataType.LONG -> copyRegisterLong(dst, src)
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
        IRDataType.LONG -> storeToMemoryLong(reg, target)
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
            val base = regAddrByte(dst, 0)
            emitLine("ldy  #3")
            emitLine("-  lda  ($ptr),y")
            emitLine("sta  $base,y")
            emitLine("dey")
            emitLine("bpl  -")
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
            val base = regAddrByte(dst, 0)
            emitLine("ldy  #3")
            emitLine("-  lda  ($ptr),y")
            emitLine("sta  $base,y")
            emitLine("dey")
            emitLine("bpl  -")
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
            emitLine("ldy  #3")
            emitLine("lda  #0")
            emitLine("-  sta  $target,y")
            emitLine("dey")
            emitLine("bpl  -")
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
            emitLine("lda  #0")
            emitLine("sta  ($ptr),y")
        }
        IRDataType.WORD -> {
            emitLine("ldy  #0")
            emitLine("lda  #0")
            emitLine("sta  ($ptr),y")
            emitLine("ldy  #1")
            emitLine("lda  #0")
            emitLine("sta  ($ptr),y")
        }
        IRDataType.LONG -> {
            emitLine("ldy  #3")
            emitLine("lda  #0")
            emitLine("-  sta  ($ptr),y")
            emitLine("dey")
            emitLine("bpl  -")
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
            val base = regAddrByte(reg, 0)
            emitLine("ldy  #3")
            emitLine("-  lda  $base,y")
            emitLine("sta  ($ptr),y")
            emitLine("dey")
            emitLine("bpl  -")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT STOREI r$reg")
        }
    }
}

// === LONG byte loop for non-carry-dependent operations ===

private fun CodeGenerator.loadFromMemoryLong(reg: Int, source: String) {
    val base = regAddrByte(reg, 0)
    emitLine("ldy  #3")
    emitLine("-  lda  $source,y")
    emitLine("sta  $base,y")
    emitLine("dey")
    emitLine("bpl  -")
}

private fun CodeGenerator.storeToMemoryLong(reg: Int, target: String) {
    val base = regAddrByte(reg, 0)
    emitLine("ldy  #3")
    emitLine("-  lda  $base,y")
    emitLine("sta  $target,y")
    emitLine("dey")
    emitLine("bpl  -")
}

private fun CodeGenerator.copyRegisterLong(dst: Int, src: Int) {
    val srcBase = regAddrByte(src, 0)
    val dstBase = regAddrByte(dst, 0)
    emitLine("ldy  #3")
    emitLine("-  lda  $srcBase,y")
    emitLine("sta  $dstBase,y")
    emitLine("dey")
    emitLine("bpl  -")
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
