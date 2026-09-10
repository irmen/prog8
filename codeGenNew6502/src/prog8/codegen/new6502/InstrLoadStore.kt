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

package prog8.codegen.new6502

import prog8.code.core.toHex
import prog8.intermediate.*

internal fun AsmGen.translateLoadStore(insn: IRInstruction) {
    val type = insn.type ?: IRDataType.BYTE

    // FLOAT operations use the float register file instead of the integer one
    if (type == IRDataType.FLOAT) {
        translateFloatLoadStore(insn)
        return
    }

    when (insn.opcode) {
        Opcode.LOAD -> {
            val dst = insn.requireIntDest().intNumber
            when (val value = insn.requireImmediate()) {
                is ImmediateOperand.Integer -> loadImmediate(dst, value.value, type)
                is ImmediateOperand.SymbolAddress -> loadSymbolAddress(dst, value.symbol, value.offset.takeIf { it != 0 }, type)
                is ImmediateOperand.FloatValue -> error("LOAD.$type has a float immediate")
            }
        }

        Opcode.LOADM -> {
            val dst = insn.requireIntDest().intNumber
            val target = resolveAddress(insn.requireMemory())
            loadFromMemory(dst, target, type)
        }

        Opcode.LOADR -> {
            val dst = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            copyRegister(dst, srcReg, type)
        }

        Opcode.LOADX -> {
            val dst = insn.requireIntDest().intNumber
            val mem = insn.requireMemory() as MemoryReference.Indexed
            val idxReg = mem.index.intNumber
            val baseAddress = resolveAddress(mem.base, mem.displacement)
            scaleIndexRegIfNeeded(idxReg, mem.scale)
            indexedLoad(dst, idxReg, baseAddress, type)
        }

        Opcode.LOADHR -> {
            val dst = insn.requireIntDest().intNumber
            val slot = insn.requireHardwareSlot().slot.value
            loadFromHardwareReg(dst, slot, type)
        }

        Opcode.LOADI -> {
            val dst = insn.requireIntDest().intNumber
            val mem = insn.requireMemory() as MemoryReference.Indirect
            indirectLoad(dst, mem.pointer.intNumber, mem.displacement, type)
        }

        Opcode.STOREM -> {
            val src = insn.requireIntSourceA().intNumber
            val target = resolveAddress(insn.requireMemory())
            storeToMemory(src, target, type)
        }

        Opcode.STOREX -> {
            val src = insn.requireIntSourceA().intNumber
            val mem = insn.requireMemory() as MemoryReference.Indexed
            val idxReg = mem.index.intNumber
            val target = resolveAddress(mem.base, mem.displacement)
            scaleIndexRegIfNeeded(idxReg, mem.scale)
            storeExchange(src, idxReg, target, type)
        }

        Opcode.STOREZM -> {
            val target = resolveAddress(insn.requireMemory())
            zeroMemory(target, type)
        }

        Opcode.STOREIM -> {
            val target = resolveAddress(insn.requireMemory())
            val value = insn.requireImmediateInt()
            storeImmediateToMemory(value, target, type)
        }

        Opcode.STOREZI -> {
            val mem = insn.requireMemory() as MemoryReference.Indirect
            zeroIndexed(mem.pointer.intNumber, mem.displacement, type)
        }

        Opcode.STOREZX -> {
            val mem = insn.requireMemory() as MemoryReference.Indexed
            val idxReg = mem.index.intNumber
            val target = resolveAddress(mem.base, mem.displacement)
            scaleIndexRegIfNeeded(idxReg, mem.scale)
            zeroMemoryIndexed(idxReg, target, type)
        }

        Opcode.STOREHR -> {
            val src = insn.requireIntSourceA().intNumber
            val slot = insn.requireHardwareSlot().slot.value
            storeToHardwareReg(src, slot)
        }

        Opcode.STOREI -> {
            val src = insn.requireIntSourceA().intNumber
            val mem = insn.requireMemory() as MemoryReference.Indirect
            indirectStore(src, mem.pointer.intNumber, mem.displacement, type)
        }

        Opcode.LOADP_INC -> {
            val dst = insn.requireIntDest().intNumber
            val ptrVar = resolveAddress(insn.requireMemory())
            // r1 = *ptrVar; ptrVar += sizeof(type)  (post-inc)
            // Use $22/$23 as temp pointer
            emitLine("lda  $ptrVar")
            emitLine("sta  $$22")
            emitLine("lda  ${ptrVar}+1")
            emitLine("sta  $$23")
            emitLine("ldy  #0")
            when (type) {
                IRDataType.BYTE -> {
                    emitLine("lda  ($$22),y")
                    emitLine("sta  ${regAddr(dst)}")
                    emitLine("inc  $ptrVar")
                    emitLine("bne  +")
                    emitLine("inc  ${ptrVar}+1")
                    emitLine("+")
                }
                IRDataType.WORD, IRDataType.POINTER -> {
                    emitLine("lda  ($$22),y")
                    emitLine("sta  ${regAddr(dst)}")
                    emitLine("iny")
                    emitLine("lda  ($$22),y")
                    emitLine("sta  ${regAddr(dst)}+1")
                    emitLine("lda  $ptrVar")
                    emitLine("clc")
                    emitLine("adc  #2")
                    emitLine("sta  $ptrVar")
                    emitLine("bcc  +")
                    emitLine("inc  ${ptrVar}+1")
                    emitLine("+")
                }
                IRDataType.LONG -> {
                    // long not used on 6502 for pointer post-inc, fallback to 4-byte inc
                    emitLine("lda  ($$22),y")
                    emitLine("sta  ${regAddr(dst)}")
                    emitLine("iny")
                    emitLine("lda  ($$22),y")
                    emitLine("sta  ${regAddr(dst)}+1")
                    emitLine("iny")
                    emitLine("lda  ($$22),y")
                    emitLine("sta  ${regAddr(dst)}+2")
                    emitLine("iny")
                    emitLine("lda  ($$22),y")
                    emitLine("sta  ${regAddr(dst)}+3")
                    emitLine("lda  $ptrVar")
                    emitLine("clc")
                    emitLine("adc  #4")
                    emitLine("sta  $ptrVar")
                    emitLine("bcc  +")
                    emitLine("inc  ${ptrVar}+1")
                    emitLine("+")
                }
            }
        }

        Opcode.STOREP_INC -> {
            val src = insn.requireIntSourceA().intNumber
            val ptrVar = resolveAddress(insn.requireMemory())
            // *ptrVar = r1; ptrVar += sizeof(type)
            emitLine("lda  $ptrVar")
            emitLine("sta  $$22")
            emitLine("lda  ${ptrVar}+1")
            emitLine("sta  $$23")
            emitLine("ldy  #0")
            when (type) {
                IRDataType.BYTE -> {
                    emitLine("lda  ${regAddr(src)}")
                    emitLine("sta  ($$22),y")
                    emitLine("inc  $ptrVar")
                    emitLine("bne  +")
                    emitLine("inc  ${ptrVar}+1")
                    emitLine("+")
                }
                IRDataType.WORD, IRDataType.POINTER -> {
                    emitLine("lda  ${regAddr(src)}")
                    emitLine("sta  ($$22),y")
                    emitLine("iny")
                    emitLine("lda  ${regAddr(src)}+1")
                    emitLine("sta  ($$22),y")
                    emitLine("lda  $ptrVar")
                    emitLine("clc")
                    emitLine("adc  #2")
                    emitLine("sta  $ptrVar")
                    emitLine("bcc  +")
                    emitLine("inc  ${ptrVar}+1")
                    emitLine("+")
                }
                IRDataType.LONG -> {
                    emitLine("lda  ${regAddr(src)}")
                    emitLine("sta  ($$22),y")
                    emitLine("iny")
                    emitLine("lda  ${regAddr(src)}+1")
                    emitLine("sta  ($$22),y")
                    emitLine("iny")
                    emitLine("lda  ${regAddr(src)}+2")
                    emitLine("sta  ($$22),y")
                    emitLine("iny")
                    emitLine("lda  ${regAddr(src)}+3")
                    emitLine("sta  ($$22),y")
                    emitLine("lda  $ptrVar")
                    emitLine("clc")
                    emitLine("adc  #4")
                    emitLine("sta  $ptrVar")
                    emitLine("bcc  +")
                    emitLine("inc  ${ptrVar}+1")
                    emitLine("+")
                }
            }
        }

        Opcode.LOADHFACZERO -> TODO("LOADHFACZERO (float)")
        Opcode.LOADHFACONE -> TODO("LOADHFACONE (float)")
        Opcode.STOREHFACZERO -> TODO("STOREHFACZERO (float)")
        Opcode.STOREHFACONE -> TODO("STOREHFACONE (float)")

        else -> error("Unknown load/store opcode: ${insn.opcode}")
    }
}

private fun AsmGen.scaleIndexRegIfNeeded(idxReg: Int, scale: Int) {
    if(scale==1) return
    when(scale) {
        2 -> {
            emitLine("asl  ${regAddrLo(idxReg)}")
        }
        4 -> {
            emitLine("asl  ${regAddrLo(idxReg)}")
            emitLine("asl  ${regAddrLo(idxReg)}")
        }
        else -> {
            // generic scale fallback: use the existing 8-bit multiply helper (A * Y -> A)
            emitLine("lda  ${regAddrLo(idxReg)}")
            emitLine("ldy  #$scale")
            emitLine("jsr  prog8_lib.multiply_bytes")
            emitLine("sta  ${regAddrLo(idxReg)}")
        }
    }
}

private fun AsmGen.emitFloatIndexScaled(idxReg: Int, scale: Int, ptr: String) {
    // index is element index; scale by element size (float = 5 or 8, etc.) then add to ptr
    if(scale==1) {
        emitLine("lda  ${regAddrLo(idxReg)}")
        emitLine("clc")
        emitLine("adc  $ptr")
        emitLine("sta  $ptr")
        emitLine("bcc  +")
        emitLine("inc  ${ptr}+1")
        emitLabel("+")
        return
    }
    // generic scale: idx*scale -> A, then add to ptr
    // use simple multiply for small scales; for float Mflpt 5 we do *5 = *4+*1
    when(scale) {
        2 -> {
            emitLine("lda  ${regAddrLo(idxReg)}")
            emitLine("asl  a")
            emitLine("clc")
            emitLine("adc  $ptr")
            emitLine("sta  $ptr")
            emitLine("bcc  +")
            emitLine("inc  ${ptr}+1")
            emitLabel("+")
        }
        4 -> {
            emitLine("lda  ${regAddrLo(idxReg)}")
            emitLine("asl  a")
            emitLine("asl  a")
            emitLine("clc")
            emitLine("adc  $ptr")
            emitLine("sta  $ptr")
            emitLine("bcc  +")
            emitLine("inc  ${ptr}+1")
            emitLabel("+")
        }
        5 -> {
            emitLine("lda  ${regAddrLo(idxReg)}")
            emitLine("sta  P8ZP_SCRATCH_REG") // temp
            emitLine("asl  a")
            emitLine("asl  a") // *4
            emitLine("clc")
            emitLine("adc  P8ZP_SCRATCH_REG") // *5
            emitLine("clc")
            emitLine("adc  $ptr")
            emitLine("sta  $ptr")
            emitLine("bcc  +")
            emitLine("inc  ${ptr}+1")
            emitLabel("+")
        }
        8 -> {
            emitLine("lda  ${regAddrLo(idxReg)}")
            emitLine("asl  a")
            emitLine("asl  a")
            emitLine("asl  a")
            emitLine("clc")
            emitLine("adc  $ptr")
            emitLine("sta  $ptr")
            emitLine("bcc  +")
            emitLine("inc  ${ptr}+1")
            emitLabel("+")
        }
        else -> {
            emitLine("lda  ${regAddrLo(idxReg)}")
            emitLine("ldy  #$scale")
            emitLine("jsr  prog8_lib.multiply_bytes")
            emitLine("clc")
            emitLine("adc  $ptr")
            emitLine("sta  $ptr")
            emitLine("bcc  +")
            emitLine("inc  ${ptr}+1")
            emitLabel("+")
        }
    }
}

private fun AsmGen.translateFloatLoadStore(insn: IRInstruction) {
    when (insn.opcode) {
        Opcode.LOAD -> {
            val fpReg = insn.requireFloatDest().floatNumber
            when (val value = insn.requireImmediate()) {
                is ImmediateOperand.FloatValue -> {
                    val constLabel = getFloatConstLabel(value.value)
                    emitLine("lda  #<$constLabel")
                    emitLine("ldy  #>$constLabel")
                    emitLine("jsr  floats.MOVFM")
                    emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
                    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
                    emitLine("jsr  floats.MOVMF")
                }
                is ImmediateOperand.SymbolAddress -> {
                    val resolved = resolveSymbolRef(value.symbol)
                    val symWithOffset = if (value.offset != 0) "$resolved+${value.offset}" else resolved
                    emitLine("lda  #<$symWithOffset")
                    emitLine("ldy  #>$symWithOffset")
                    emitLine("jsr  floats.MOVFM")
                    emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
                    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
                    emitLine("jsr  floats.MOVMF")
                }
                is ImmediateOperand.Integer -> error("FLOAT LOAD has an integer immediate")
            }
        }

        Opcode.LOADM -> {
            val target = resolveAddress(insn.requireMemory())
            val fpReg = insn.requireFloatDest().floatNumber
            emitLine("lda  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVFM")
            emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.LOADR -> {
            val srcReg = insn.requireFloatSourceA().floatNumber
            val dstReg = insn.requireFloatDest().floatNumber
            emitLine("lda  #<${fpRegAddr(srcReg.value)}")
            emitLine("ldy  #>${fpRegAddr(srcReg.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("ldx  #<${fpRegAddr(dstReg.value)}")
            emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.STOREM -> {
            val target = resolveAddress(insn.requireMemory())
            val fpReg = insn.requireFloatSourceA().floatNumber
            emitLine("lda  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("ldx  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.STOREIM -> {
            val target = resolveAddress(insn.requireMemory())
            val value = insn.requireImmediateFloat()
            val constLabel = getFloatConstLabel(value)
            emitLine("lda  #<$constLabel")
            emitLine("ldy  #>$constLabel")
            emitLine("jsr  floats.MOVFM")
            emitLine("ldx  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.LOADHR -> {
            val slot = insn.requireHardwareSlot().slot.value
            val fpReg = insn.requireFloatDest().floatNumber
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
            val slot = insn.requireHardwareSlot().slot.value
            val fpReg = insn.requireFloatSourceA().floatNumber
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
            val mem = insn.requireMemory() as MemoryReference.Indexed
            val idxReg = mem.index.intNumber
            val baseAddress = resolveAddress(mem.base, mem.displacement)
            val fpReg = insn.requireFloatDest().floatNumber
            val scale = mem.scale
            val ptr = ZP_TEMP
            emitLine("lda  #<$baseAddress")
            emitLine("sta  $ptr")
            emitLine("lda  #>$baseAddress")
            emitLine("sta  ${ptr}+1")
            // index is element index; scale by element size via helper
            emitFloatIndexScaled(idxReg, scale, ptr)
            emitLine("lda  $ptr")
            emitLine("ldy  ${ptr}+1")
            emitLine("jsr  floats.MOVFM")
            emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.LOADI -> {
            val mem = insn.requireMemory() as MemoryReference.Indirect
            val baseReg = mem.pointer.intNumber
            val offsetVal = mem.displacement
            val fpReg = insn.requireFloatDest().floatNumber
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
            val mem = insn.requireMemory() as MemoryReference.Indirect
            val baseReg = mem.pointer.intNumber
            val offsetVal = mem.displacement
            val fpReg = insn.requireFloatSourceA().floatNumber
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
            emitLine("ldx  $ptr")
            emitLine("ldy  ${ptr}+1")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.STOREX -> {
            val mem = insn.requireMemory() as MemoryReference.Indexed
            val idxReg = mem.index.intNumber
            val baseAddress = resolveAddress(mem.base, mem.displacement)
            val fpReg = insn.requireFloatSourceA().floatNumber
            val scale = mem.scale
            val ptr = ZP_TEMP
            emitLine("lda  #<$baseAddress")
            emitLine("sta  $ptr")
            emitLine("lda  #>$baseAddress")
            emitLine("sta  ${ptr}+1")
            emitFloatIndexScaled(idxReg, scale, ptr)
            emitLine("lda  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("ldx  $ptr")
            emitLine("ldy  ${ptr}+1")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.STOREZM -> {
            val memTarget = resolveAddress(insn.requireMemory())
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
            val mem = insn.requireMemory() as MemoryReference.Indirect
            val baseReg = mem.pointer.intNumber
            val offsetVal = mem.displacement
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
            val mem = insn.requireMemory() as MemoryReference.Indexed
            val idxReg = mem.index.intNumber
            val baseAddress = resolveAddress(mem.base, mem.displacement)
            val scale = mem.scale
            val ptr = ZP_TEMP
            emitLine("lda  #<$baseAddress")
            emitLine("sta  $ptr")
            emitLine("lda  #>$baseAddress")
            emitLine("sta  ${ptr}+1")
            emitFloatIndexScaled(idxReg, scale, ptr)
            val n = floatMemSize - 1
            emitLine("ldy  #$n")
            emitLine("lda  #0")
            emitLine("-  sta  ($ptr),y")
            emitLine("dey")
            emitLine("bpl  -")
        }

        Opcode.LOADHFACZERO -> {
            // Load hardware FAC1 (main accumulator) into fp reg
            val fpReg = insn.requireFloatDest().floatNumber
            emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.LOADHFACONE -> {
            // Load hardware FAC2 (argument register) into fp reg
            // First copy FAC2 to FAC1, then store
            val fpReg = insn.requireFloatDest().floatNumber
            emitLine("jsr  floats.MOVFA")
            emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.STOREHFACZERO -> {
            // Store fp reg into hardware FAC1 (main accumulator)
            val fpReg = insn.requireFloatSourceA().floatNumber
            emitLine("lda  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVFM")
        }

        Opcode.STOREHFACONE -> {
            // Store fp reg into hardware FAC2 (argument register)
            val fpReg = insn.requireFloatSourceA().floatNumber
            emitLine("lda  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.MOVAF")
        }

        else -> error("Unknown float load/store opcode: ${insn.opcode}")
    }
}

// === Hardware register (slot) operations ===

internal fun AsmGen.loadFromHardwareReg(virtualReg: Int, slot: Int, type: IRDataType) {
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

internal fun AsmGen.storeToHardwareReg(virtualReg: Int, slot: Int) {
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

internal fun AsmGen.loadSymbolAddress(reg: Int, sym: String, offset: Int?, type: IRDataType) {
    val resolved = resolveSymbolRef(sym)
    val symWithOffset = if (offset != null) "$resolved+$offset" else resolved
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  #<${symWithOffset}")
            emitLine("sta  ${regAddrLo(reg)}")
        }
        IRDataType.WORD, IRDataType.POINTER -> {
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
            emitStoreZero(regAddrByte(reg, 2))
            emitStoreZero(regAddrByte(reg, 3))
        }
        else -> TODO("SYMLOAD r$reg, $symWithOffset ${type.name}")
    }
}

internal fun AsmGen.loadImmediate(reg: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  #${value and 0xff}")
            emitLine("sta  ${regAddrLo(reg)}")
        }
        IRDataType.WORD, IRDataType.POINTER -> {
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

internal fun AsmGen.loadFromMemory(reg: Int, sourceAddress: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  $sourceAddress")
            emitLine("sta  ${regAddrLo(reg)}")
        }
        IRDataType.WORD, IRDataType.POINTER -> {
            emitLine("lda  $sourceAddress")
            emitLine("sta  ${regAddrLo(reg)}")
            emitLine("lda  $sourceAddress+1")
            emitLine("sta  ${regAddrHi(reg)}")
        }
        IRDataType.LONG -> {
            val baseAddress = regAddrByte(reg, 0)
            emitLine("ldy  #3")
            emitLine("-  lda  $sourceAddress,y")
            emitLine("sta  $baseAddress,y")
            emitLine("dey")
            emitLine("bpl  -")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT LOADM r$reg from $sourceAddress")
        }
    }
}

internal fun AsmGen.copyRegister(dstReg: Int, srcReg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(srcReg)}")
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD, IRDataType.POINTER -> {
            emitLine("lda  ${regAddrLo(srcReg)}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrHi(srcReg)}")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        IRDataType.LONG -> copyRegisterLong(dstReg, srcReg)
        IRDataType.FLOAT -> {
            TODO("FLOAT LOADR r$dstReg = r$srcReg")
        }
    }
}

internal fun AsmGen.storeImmediateToMemory(value: Int, target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  #${value and 0xff}")
            emitLine("sta  $target")
        }
        IRDataType.WORD, IRDataType.POINTER -> {
            emitLine("lda  #<${value and 0xffff}")
            emitLine("sta  $target")
            emitLine("lda  #>${value and 0xffff}")
            emitLine("sta  $target+1")
        }
        IRDataType.LONG -> {
            emitLine("lda  #<${value and 0xffff}")
            emitLine("sta  $target")
            emitLine("lda  #>${value and 0xffff}")
            emitLine("sta  $target+1")
            emitLine("lda  #${(value ushr 16) and 0xff}")
            emitLine("sta  $target+2")
            emitLine("lda  #${(value ushr 24) and 0xff}")
            emitLine("sta  $target+3")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT STOREIM $value -> $target")
        }
    }
}

internal fun AsmGen.storeToMemory(reg: Int, target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("sta  $target")
        }
        IRDataType.WORD, IRDataType.POINTER -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("sta  $target")
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("sta  $target+1")
        }
        IRDataType.LONG -> {
            val baseAddress = regAddrByte(reg, 0)
            emitLine("ldy  #3")
            emitLine("-  lda  $baseAddress,y")
            emitLine("sta  $target,y")
            emitLine("dey")
            emitLine("bpl  -")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT STOREM r$reg -> $target")
        }
    }
}

internal fun AsmGen.indexedLoad(dstReg: Int, idxReg: Int, baseAddress: String, type: IRDataType) {
    val ptr = ZP_TEMP
    emitLine("lda  #<$baseAddress")
    emitLine("sta  $ptr")
    emitLine("lda  #>$baseAddress")
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
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD, IRDataType.POINTER -> {
            emitLine("ldy  #0")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("ldy  #1")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        IRDataType.LONG -> {
            val baseAddress = regAddrByte(dstReg, 0)
            emitLine("ldy  #3")
            emitLine("-  lda  ($ptr),y")
            emitLine("sta  $baseAddress,y")
            emitLine("dey")
            emitLine("bpl  -")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT LOADX r$dstReg")
        }
    }
}

internal fun AsmGen.indirectLoad(dstReg: Int, baseReg: Int, offset: Int, type: IRDataType) {
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
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD, IRDataType.POINTER -> {
            emitLine("ldy  #0")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("ldy  #1")
            emitLine("lda  ($ptr),y")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        IRDataType.LONG -> {
            val baseAddress = regAddrByte(dstReg, 0)
            emitLine("ldy  #3")
            emitLine("-  lda  ($ptr),y")
            emitLine("sta  $baseAddress,y")
            emitLine("dey")
            emitLine("bpl  -")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT LOADI r$dstReg")
        }
    }
}

internal fun AsmGen.storeExchange(valueReg: Int, indexReg: Int, target: String, type: IRDataType) {
    // STOREX: indexed store -- mem[target + index] = value
    // TODO can this be simplified?
    when (type) {
        IRDataType.BYTE -> {
            emitLine("ldx  ${regAddrLo(indexReg)}")
            emitLine("lda  ${regAddrLo(valueReg)}")
            emitLine("sta  $target,x")
        }
        IRDataType.WORD -> {
            // Index register is a BYTE (pre-multiplied by 2). Keep X once for both bytes.
            emitLine("ldx  ${regAddrLo(indexReg)}")
            emitLine("lda  ${regAddrLo(valueReg)}")
            emitLine("sta  $target,x")
            emitLine("lda  ${regAddrHi(valueReg)}")
            emitLine("sta  ${target}+1,x")
        }
        IRDataType.LONG -> {
            // Index register is a BYTE (pre-multiplied by 4). Keep X once for all 4 bytes.
            emitLine("ldx  ${regAddrLo(indexReg)}")
            emitLine("lda  ${regAddrLo(valueReg)}")
            emitLine("sta  $target,x")
            emitLine("lda  ${regAddrHi(valueReg)}")
            emitLine("sta  ${target}+1,x")
            emitLine("lda  ${regAddrByte(valueReg, 2)}")
            emitLine("sta  ${target}+2,x")
            emitLine("lda  ${regAddrByte(valueReg, 3)}")
            emitLine("sta  ${target}+3,x")
        }
        else -> {
            TODO("STOREX r$valueReg, r$indexReg, $target ${type.name}")
        }
    }
}

internal fun AsmGen.zeroMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitStoreZero(target)
        }
        IRDataType.WORD, IRDataType.POINTER -> {
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

internal fun AsmGen.zeroIndexed(baseReg: Int, offset: Int, type: IRDataType) {
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
        IRDataType.WORD, IRDataType.POINTER -> {
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

internal fun AsmGen.zeroMemoryIndexed(reg: Int, baseAddress: String, type: IRDataType) {
    emitLine("ldx  ${regAddrLo(reg)}")
    when (type) {
        IRDataType.BYTE -> {
            emitStoreZero("$baseAddress,x")
        }
        IRDataType.WORD, IRDataType.POINTER -> {
            emitStoreZero("$baseAddress,x")
            emitStoreZero("${baseAddress}+1,x")
        }
        IRDataType.LONG -> {
            emitStoreZero("$baseAddress,x")
            emitStoreZero("${baseAddress}+1,x")
            emitStoreZero("${baseAddress}+2,x")
            emitStoreZero("${baseAddress}+3,x")
        }
        IRDataType.FLOAT -> {
            TODO("FLOAT STOREZX")
        }
    }
}

internal fun AsmGen.indirectStore(reg: Int, baseReg: Int, offset: Int, type: IRDataType) {
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
        IRDataType.WORD, IRDataType.POINTER -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("ldy  #0")
            emitLine("sta  ($ptr),y")
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("ldy  #1")
            emitLine("sta  ($ptr),y")
        }
        IRDataType.LONG -> {
            val baseAddress = regAddrByte(reg, 0)
            emitLine("ldy  #3")
            emitLine("-  lda  $baseAddress,y")
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

internal fun AsmGen.copyRegisterLong(dstReg: Int, srcReg: Int) {
    val srcBase = regAddrByte(srcReg, 0)
    val dstBase = regAddrByte(dstReg, 0)
    emitLine("ldy  #3")
    emitLine("-  lda  $srcBase,y")
    emitLine("sta  $dstBase,y")
    emitLine("dey")
    emitLine("bpl  -")
}

/** the address of a [base] (+ [displacement], for a Symbol base only - matches the previous behavior
 *  of ignoring any offset that came together with a fixed numeric address) */
internal fun AsmGen.resolveAddress(base: AddressBase, displacement: Int = 0): String {
    return when (base) {
        is AddressBase.Symbol -> {
            val resolved = resolveSymbolRef(base.name)
            if (displacement != 0) "$resolved+$displacement" else resolved
        }
        is AddressBase.Absolute -> base.address.value.toHex()
    }
}

/** the address of a DIRECT memory reference (LOADM/STOREM/STOREZM/STOREIM/memory-op opcodes) */
internal fun AsmGen.resolveAddress(memory: MemoryReference): String {
    val direct = memory as? MemoryReference.Direct ?: error("expected a direct memory reference: $memory")
    return resolveAddress(direct.base, direct.displacement)
}
