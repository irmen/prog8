/*
 * Arithmetic IR instruction translations for the new6502gen code generator.
 *
 * Handles: INC/DEC, NEG, ADD/SUB, MUL/DIV/MOD (signed and unsigned),
 * DIVMOD (combined division and modulo), and CMP/CMPI.
 *
 * All operations work on virtual registers (r0-r199 in the register file).
 * BYTE and WORD types are fully implemented; LONG and FLOAT are partial/TODO.
 *
 *  Multiply/divide/modulo operations use helper subroutines provided by
 *  the prog8 standard library (prog8_math module), such as
 *  prog8_math.multiply_bytes, prog8_math.multiply_words,
 *  prog8_math.divmod_ub_asm, prog8_math.divmod_uw_asm, etc.
 *
 * Important notes:
 *   - 6502 flag discipline: ADC needs CLC before, SBC needs SEC before
 *   - 16-bit addition/subtraction is done low byte first with carry propagation
 *   - Signed multiply/divide currently fall through to unsigned versions
 */

package prog8.codegen.new6502

import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction
import prog8.intermediate.Opcode

internal fun AsmGen.translateArithmetic(insn: IRInstruction) {
    val type = insn.type ?: IRDataType.BYTE
    val r1 = insn.reg1          // nullable - INCM/DECM/NEGM have no reg1
    val r2 = insn.reg2
    val imm = insn.immediate
    val addr = insn.address
    val label = insn.labelSymbol
    val offset = insn.labelSymbolOffset

    // FLOAT operations use fpReg1/fpReg2 instead of reg1/reg2
    if (type == IRDataType.FLOAT) {
        translateFloatArithmetic(insn)
        return
    }

    when (insn.opcode) {
        Opcode.INC -> incrementRegister(r1 ?: error("INC needs reg1"), type)
        Opcode.INCM -> incrementMemory(resolveAddress(addr, label, offset), type)
        Opcode.DEC -> decrementRegister(r1 ?: error("DEC needs reg1"), type)
        Opcode.DECM -> decrementMemory(resolveAddress(addr, label, offset), type)
        Opcode.NEG -> negateRegister(r1 ?: error("NEG needs reg1"), type)
        Opcode.NEGM -> negateMemory(resolveAddress(addr, label, offset), type)

        Opcode.ADDR, Opcode.PTRADD -> {
            val r2val = r2 ?: error("ADDR/PTRADD needs reg2")
            addRegisters(r1 ?: error("ADDR/PTRADD needs reg1"), r2val, type)
        }
        Opcode.ADD -> {
            val value = imm ?: error("ADD needs immediate")
            addImmediate(r1 ?: error("ADD needs reg1"), value, type)
        }
        Opcode.ADDM -> {
            val target = resolveAddress(addr, label, offset)
            addMemory(r1 ?: error("ADDM needs reg1"), target, type)
        }

        Opcode.SUBR -> {
            val r2val = r2 ?: error("SUBR needs reg2")
            subRegisters(r1 ?: error("SUBR needs reg1"), r2val, type)
        }
        Opcode.SUB -> {
            val value = imm ?: error("SUB needs immediate")
            subImmediate(r1 ?: error("SUB needs reg1"), value, type)
        }
        Opcode.SUBM -> {
            val target = resolveAddress(addr, label, offset)
            subMemory(r1 ?: error("SUBM needs reg1"), target, type)
        }

        Opcode.MULR -> {
            val r2val = r2 ?: error("MULR needs reg2")
            mulRegisters(r1 ?: error("MULR needs reg1"), r2val, type)
        }
        Opcode.MUL -> {
            val value = imm ?: error("MUL needs immediate")
            mulImmediate(r1 ?: error("MUL needs reg1"), value, type)
        }
        Opcode.MULM -> {
            val target = resolveAddress(addr, label, offset)
            mulMemory(r1 ?: error("MULM needs reg1"), target, type)
        }
        Opcode.MULSR -> {
            val r2val = r2 ?: error("MULSR needs reg2")
            mulSignedRegisters(r1 ?: error("MULSR needs reg1"), r2val, type)
        }
        Opcode.MULS -> {
            val value = imm ?: error("MULS needs immediate")
            mulSignedImmediate(r1 ?: error("MULS needs reg1"), value, type)
        }
        Opcode.MULSM -> {
            val target = resolveAddress(addr, label, offset)
            mulSignedMemory(r1 ?: error("MULSM needs reg1"), target, type)
        }

        Opcode.DIVR -> {
            val r2val = r2 ?: error("DIVR needs reg2")
            divRegisters(r1 ?: error("DIVR needs reg1"), r2val, type)
        }
        Opcode.DIV -> {
            val value = imm ?: error("DIV needs immediate")
            divImmediate(r1 ?: error("DIV needs reg1"), value, type)
        }
        Opcode.DIVM -> {
            val target = resolveAddress(addr, label, offset)
            divMemory(r1 ?: error("DIVM needs reg1"), target, type)
        }
        Opcode.DIVSR -> {
            val r2val = r2 ?: error("DIVSR needs reg2")
            divSignedRegisters(r1 ?: error("DIVSR needs reg1"), r2val, type)
        }
        Opcode.DIVS -> {
            val value = imm ?: error("DIVS needs immediate")
            divSignedImmediate(r1 ?: error("DIVS needs reg1"), value, type)
        }
        Opcode.DIVSM -> {
            val target = resolveAddress(addr, label, offset)
            divSignedMemory(r1 ?: error("DIVSM needs reg1"), target, type)
        }

        Opcode.MODR -> {
            val r2val = r2 ?: error("MODR needs reg2")
            modRegisters(r1 ?: error("MODR needs reg1"), r2val, type)
        }
        Opcode.MOD -> {
            val value = imm ?: error("MOD needs immediate")
            modImmediate(r1 ?: error("MOD needs reg1"), value, type)
        }
        Opcode.MODSR -> {
            val r2val = r2 ?: error("MODSR needs reg2")
            modSignedRegisters(r1 ?: error("MODSR needs reg1"), r2val, type)
        }
        Opcode.MODS -> {
            val value = imm ?: error("MODS needs immediate")
            modSignedImmediate(r1 ?: error("MODS needs reg1"), value, type)
        }

        Opcode.DIVMODR -> {
            val r2val = r2 ?: error("DIVMODR needs reg2")
            divModRegisters(r1 ?: error("DIVMODR needs reg1"), r2val, type)
        }
        Opcode.DIVMOD -> {
            val value = imm ?: error("DIVMOD needs immediate")
            divModImmediate(r1 ?: error("DIVMOD needs reg1"), value, type)
        }
        Opcode.SDIVMODR -> {
            val r2val = r2 ?: error("SDIVMODR needs reg2")
            sdivModRegisters(r1 ?: error("SDIVMODR needs reg1"), r2val, type)
        }
        Opcode.SDIVMOD -> {
            val value = imm ?: error("SDIVMOD needs immediate")
            sdivModImmediate(r1 ?: error("SDIVMOD needs reg1"), value, type)
        }

        Opcode.CMP -> {
            val r2val = r2 ?: error("CMP needs reg2")
            compareRegisters(r1 ?: error("CMP needs reg1"), r2val, type)
        }
        Opcode.CMPI -> {
            val value = imm ?: error("CMPI needs immediate")
            compareImmediate(r1 ?: error("CMPI needs reg1"), value, type)
        }

        else -> error("Unknown arithmetic opcode: ${insn.opcode}")
    }
}

// === Increment/Decrement ===

private fun AsmGen.incrementRegister(reg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("inc  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("inc  ${regAddrLo(reg)}")
            emitLine("bne  +")
            emitLine("inc  ${regAddrHi(reg)}")
            emitLabel("+")
        }
        IRDataType.LONG -> {
            emitLine("inc  ${regAddrLo(reg)}")
            emitLine("bne  +")
            emitLine("inc  ${regAddrHi(reg)}")
            emitLine("bne  +")
            emitLine("inc  ${regAddrByte(reg, 2)}")
            emitLine("bne  +")
            emitLine("inc  ${regAddrByte(reg, 3)}")
            emitLabel("+")
        }
        IRDataType.FLOAT -> TODO("FLOAT INC r$reg")
    }
}

private fun AsmGen.decrementRegister(reg: Int, type: IRDataType) {
    // Note: under the strict status-bits contract (CpuType.statusBitsOnMultiByteOps=false),
    // the IR generator always emits an explicit CMPI before any branch that depends on the
    // result, so the multi-byte op doesn't need to set Z correctly. We still emit the
    // `ora` postamble as a defensive measure (it sets Z=1 iff the full value is 0), but
    // it's wasted work. The explicit CMPI that follows is what the branch actually uses.
    when (type) {
        IRDataType.BYTE -> {
            emitLine("dec  ${regAddrLo(reg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("bne  +")
            emitLine("dec  ${regAddrHi(reg)}")
            emitLabel("+")
            emitLine("dec  ${regAddrLo(reg)}")
            emitLine("ora  ${regAddrHi(reg)}")
        }
        IRDataType.LONG -> {
            val label1 = makeLabel("p8_decl_skip_lo")
            val label2 = makeLabel("p8_decl_skip_hi")
            val label3 = makeLabel("p8_decl_skip_b2")
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("bne  $label1")
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("bne  $label2")
            emitLine("lda  ${regAddrByte(reg, 2)}")
            emitLine("bne  $label3")
            emitLine("dec  ${regAddrByte(reg, 3)}")
            emitLabel(label3)
            emitLine("dec  ${regAddrByte(reg, 2)}")
            emitLabel(label2)
            emitLine("dec  ${regAddrHi(reg)}")
            emitLabel(label1)
            emitLine("dec  ${regAddrLo(reg)}")
            emitLine("ora  ${regAddrHi(reg)}")
            emitLine("ora  ${regAddrByte(reg, 2)}")
            emitLine("ora  ${regAddrByte(reg, 3)}")
        }
        IRDataType.FLOAT -> TODO("FLOAT DEC r$reg")
    }
}

private fun AsmGen.incrementMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> emitLine("inc  $target")
        IRDataType.WORD -> {
            emitLine("inc  $target")
            emitLine("bne  +")
            emitLine("inc  $target+1")
            emitLabel("+")
        }
        IRDataType.LONG -> {
            emitLine("inc  $target")
            emitLine("bne  +")
            emitLine("inc  $target+1")
            emitLine("bne  +")
            emitLine("inc  $target+2")
            emitLine("bne  +")
            emitLine("inc  $target+3")
            emitLabel("+")
        }
        IRDataType.FLOAT -> TODO("FLOAT INCM $target")
    }
}

internal fun AsmGen.decrementMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> emitLine("dec  $target")
        IRDataType.WORD -> {
            emitLine("lda  $target")
            emitLine("bne  +")
            emitLine("dec  $target+1")
            emitLabel("+")
            emitLine("dec  $target")
            emitLine("ora  $target+1")
        }
        IRDataType.LONG -> {
            emitLine("sec")
            emitLine("lda  $target")
            emitLine("sbc  #1")
            emitLine("sta  $target")
            emitLine("lda  $target+1")
            emitLine("sbc  #0")
            emitLine("sta  $target+1")
            emitLine("lda  $target+2")
            emitLine("sbc  #0")
            emitLine("sta  $target+2")
            emitLine("lda  $target+3")
            emitLine("sbc  #0")
            emitLine("sta  $target+3")
        }
        IRDataType.FLOAT -> TODO("FLOAT DECM $target")
    }
}

internal fun AsmGen.negateRegister(reg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            if (is65C02()) {
                emitLine("lda  ${regAddrLo(reg)}")
                emitLine("eor  #255")
                emitIncrementA()
                emitLine("sta  ${regAddrLo(reg)}")
            } else {
                emitLine("sec")
                emitLine("lda  #0")
                emitLine("sbc  ${regAddrLo(reg)}")
                emitLine("sta  ${regAddrLo(reg)}")
            }
        }
        IRDataType.WORD -> {
            emitLine("sec")
            emitLine("lda  #0")
            emitLine("sbc  ${regAddrLo(reg)}")
            emitLine("sta  ${regAddrLo(reg)}")
            emitLine("lda  #0")
            emitLine("sbc  ${regAddrHi(reg)}")
            emitLine("sta  ${regAddrHi(reg)}")
        }
        IRDataType.LONG -> {
            emitLine("sec")
            emitLine("lda  #0")
            emitLine("sbc  ${regAddrLo(reg)}")
            emitLine("sta  ${regAddrLo(reg)}")
            emitLine("lda  #0")
            emitLine("sbc  ${regAddrByte(reg, 1)}")
            emitLine("sta  ${regAddrByte(reg, 1)}")
            emitLine("lda  #0")
            emitLine("sbc  ${regAddrByte(reg, 2)}")
            emitLine("sta  ${regAddrByte(reg, 2)}")
            emitLine("lda  #0")
            emitLine("sbc  ${regAddrByte(reg, 3)}")
            emitLine("sta  ${regAddrByte(reg, 3)}")
        }
        IRDataType.FLOAT -> TODO("FLOAT NEG r$reg")
    }
}

internal fun AsmGen.negateMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            if (is65C02()) {
                emitLine("lda  $target")
                emitLine("eor  #255")
                emitIncrementA()
                emitLine("sta  $target")
            } else {
                emitLine("sec")
                emitLine("lda  #0")
                emitLine("sbc  $target")
                emitLine("sta  $target")
            }
        }
        IRDataType.WORD -> {
            emitLine("sec")
            emitLine("lda  #0")
            emitLine("sbc  $target")
            emitLine("sta  $target")
            emitLine("lda  #0")
            emitLine("sbc  $target+1")
            emitLine("sta  $target+1")
        }
        IRDataType.LONG -> {
            emitLine("sec")
            emitLine("lda  #0")
            emitLine("sbc  $target")
            emitLine("sta  $target")
            emitLine("lda  #0")
            emitLine("sbc  $target+1")
            emitLine("sta  $target+1")
            emitLine("lda  #0")
            emitLine("sbc  $target+2")
            emitLine("sta  $target+2")
            emitLine("lda  #0")
            emitLine("sbc  $target+3")
            emitLine("sta  $target+3")
        }
        IRDataType.FLOAT -> TODO("FLOAT NEGM $target")
    }
}

// === Addition ===

internal fun AsmGen.addRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("clc")
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("adc  ${regAddrLo(srcReg)}")
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("adc  ${regAddrLo(srcReg)}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("adc  ${regAddrHi(srcReg)}")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        IRDataType.LONG -> {
            emitLine("clc")
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("adc  ${regAddrLo(srcReg)}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("adc  ${regAddrHi(srcReg)}")
            emitLine("sta  ${regAddrHi(dstReg)}")
            emitLine("lda  ${regAddrByte(dstReg, 2)}")
            emitLine("adc  ${regAddrByte(srcReg, 2)}")
            emitLine("sta  ${regAddrByte(dstReg, 2)}")
            emitLine("lda  ${regAddrByte(dstReg, 3)}")
            emitLine("adc  ${regAddrByte(srcReg, 3)}")
            emitLine("sta  ${regAddrByte(dstReg, 3)}")
        }
        IRDataType.FLOAT -> TODO("FLOAT ADDR r$dstReg, r$srcReg")
    }
}

internal fun AsmGen.addImmediate(dstReg: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            if (is65C02() && value == 1) {
                emitIncrementA()
            } else {
                emitLine("clc")
                emitLine("adc  #${value and 0xff}")
            }
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("adc  #<${value and 0xffff}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("adc  #>${value and 0xffff}")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        IRDataType.LONG -> {
            emitLine("clc")
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("adc  #${value and 0xff}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrByte(dstReg, 1)}")
            emitLine("adc  #${(value shr 8) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 1)}")
            emitLine("lda  ${regAddrByte(dstReg, 2)}")
            emitLine("adc  #${(value shr 16) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 2)}")
            emitLine("lda  ${regAddrByte(dstReg, 3)}")
            emitLine("adc  #${(value shr 24) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 3)}")
        }
        IRDataType.FLOAT -> TODO("FLOAT ADD r$dstReg, #$value")
    }
}

internal fun AsmGen.addMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("clc")
            emitLine("lda  $sourceAddress")
            emitLine("adc  ${regAddrLo(dstReg)}")
            emitLine("sta  $sourceAddress")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("lda  $sourceAddress")
            emitLine("adc  ${regAddrLo(dstReg)}")
            emitLine("sta  $sourceAddress")
            emitLine("lda  $sourceAddress+1")
            emitLine("adc  ${regAddrHi(dstReg)}")
            emitLine("sta  $sourceAddress+1")
        }
        IRDataType.LONG -> {
            emitLine("clc")
            emitLine("lda  $sourceAddress")
            emitLine("adc  ${regAddrLo(dstReg)}")
            emitLine("sta  $sourceAddress")
            emitLine("lda  $sourceAddress+1")
            emitLine("adc  ${regAddrByte(dstReg, 1)}")
            emitLine("sta  $sourceAddress+1")
            emitLine("lda  $sourceAddress+2")
            emitLine("adc  ${regAddrByte(dstReg, 2)}")
            emitLine("sta  $sourceAddress+2")
            emitLine("lda  $sourceAddress+3")
            emitLine("adc  ${regAddrByte(dstReg, 3)}")
            emitLine("sta  $sourceAddress+3")
        }
        IRDataType.FLOAT -> TODO("FLOAT ADDM r$dstReg, $sourceAddress")
    }
}

// === Subtraction ===

internal fun AsmGen.subRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("sec")
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sbc  ${regAddrLo(srcReg)}")
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("sec")
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sbc  ${regAddrLo(srcReg)}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("sbc  ${regAddrHi(srcReg)}")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        IRDataType.LONG -> {
            emitLine("sec")
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sbc  ${regAddrLo(srcReg)}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("sbc  ${regAddrHi(srcReg)}")
            emitLine("sta  ${regAddrHi(dstReg)}")
            emitLine("lda  ${regAddrByte(dstReg, 2)}")
            emitLine("sbc  ${regAddrByte(srcReg, 2)}")
            emitLine("sta  ${regAddrByte(dstReg, 2)}")
            emitLine("lda  ${regAddrByte(dstReg, 3)}")
            emitLine("sbc  ${regAddrByte(srcReg, 3)}")
            emitLine("sta  ${regAddrByte(dstReg, 3)}")
        }
        IRDataType.FLOAT -> TODO("FLOAT SUBR r$dstReg, r$srcReg")
    }
}

internal fun AsmGen.subImmediate(dstReg: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            if (is65C02() && value == 1) {
                emitDecrementA()
            } else {
                emitLine("sec")
                emitLine("sbc  #${value and 0xff}")
            }
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("sec")
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sbc  #<${value and 0xffff}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("sbc  #>${value and 0xffff}")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        IRDataType.LONG -> {
            emitLine("sec")
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sbc  #${value and 0xff}")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  ${regAddrByte(dstReg, 1)}")
            emitLine("sbc  #${(value shr 8) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 1)}")
            emitLine("lda  ${regAddrByte(dstReg, 2)}")
            emitLine("sbc  #${(value shr 16) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 2)}")
            emitLine("lda  ${regAddrByte(dstReg, 3)}")
            emitLine("sbc  #${(value shr 24) and 0xff}")
            emitLine("sta  ${regAddrByte(dstReg, 3)}")
        }
        IRDataType.FLOAT -> TODO("FLOAT SUB r$dstReg, #$value")
    }
}

internal fun AsmGen.subMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("sec")
            emitLine("lda  $sourceAddress")
            emitLine("sbc  ${regAddrLo(dstReg)}")
            emitLine("sta  $sourceAddress")
        }
        IRDataType.WORD -> {
            emitLine("sec")
            emitLine("lda  $sourceAddress")
            emitLine("sbc  ${regAddrLo(dstReg)}")
            emitLine("sta  $sourceAddress")
            emitLine("lda  $sourceAddress+1")
            emitLine("sbc  ${regAddrHi(dstReg)}")
            emitLine("sta  $sourceAddress+1")
        }
        IRDataType.LONG -> {
            emitLine("sec")
            emitLine("lda  $sourceAddress")
            emitLine("sbc  ${regAddrLo(dstReg)}")
            emitLine("sta  $sourceAddress")
            emitLine("lda  $sourceAddress+1")
            emitLine("sbc  ${regAddrByte(dstReg, 1)}")
            emitLine("sta  $sourceAddress+1")
            emitLine("lda  $sourceAddress+2")
            emitLine("sbc  ${regAddrByte(dstReg, 2)}")
            emitLine("sta  $sourceAddress+2")
            emitLine("lda  $sourceAddress+3")
            emitLine("sbc  ${regAddrByte(dstReg, 3)}")
            emitLine("sta  $sourceAddress+3")
        }
        IRDataType.FLOAT -> TODO("FLOAT SUBM r$dstReg, $sourceAddress")
    }
}

// === Multiplication ===

internal fun AsmGen.mulRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ldy  ${regAddrLo(srcReg)}")
            emitLine("jsr  prog8_math.multiply_bytes")
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(srcReg)}")
            emitLine("sta  prog8_math.multiply_words.multiplier")
            emitLine("lda  ${regAddrHi(srcReg)}")
            emitLine("sta  prog8_math.multiply_words.multiplier+1")
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ldy  ${regAddrHi(dstReg)}")
            emitLine("jsr  prog8_math.multiply_words")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("sty  ${regAddrHi(dstReg)}")
        }
        IRDataType.LONG -> TODO("MULR LONG r$dstReg, r$srcReg")
        IRDataType.FLOAT -> TODO("MULR FLOAT r$dstReg, r$srcReg")
    }
}

internal fun AsmGen.mulImmediate(dstReg: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ldy  #${value and 0xff}")
            emitLine("jsr  prog8_math.multiply_bytes")
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  #<${value and 0xffff}")
            emitLine("sta  prog8_math.multiply_words.multiplier")
            emitLine("lda  #>${value and 0xffff}")
            emitLine("sta  prog8_math.multiply_words.multiplier+1")
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ldy  ${regAddrHi(dstReg)}")
            emitLine("jsr  prog8_math.multiply_words")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("sty  ${regAddrHi(dstReg)}")
        }
        else -> TODO("MUL r$dstReg, #$value ${type.name}")
    }
}

internal fun AsmGen.mulMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  $sourceAddress")
            emitLine("ldy  ${regAddrLo(dstReg)}")
            emitLine("jsr  prog8_math.multiply_bytes")
            emitLine("sta  $sourceAddress")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sta  prog8_math.multiply_words.multiplier")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("sta  prog8_math.multiply_words.multiplier+1")
            emitLine("lda  $sourceAddress")
            emitLine("ldy  $sourceAddress+1")
            emitLine("jsr  prog8_math.multiply_words")
            emitLine("sta  $sourceAddress")
            emitLine("sty  $sourceAddress+1")
        }
        else -> TODO("MULM r$dstReg, $sourceAddress ${type.name}")
    }
}

internal fun AsmGen.mulSignedRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    emitLine("; MULSR r$dstReg, r$srcReg (signed) - using unsigned for now")
    mulRegisters(dstReg, srcReg, type)
}

internal fun AsmGen.mulSignedImmediate(dstReg: Int, value: Int, type: IRDataType) {
    emitLine("; MULS r$dstReg, #$value (signed) - using unsigned for now")
    mulImmediate(dstReg, value, type)
}

internal fun AsmGen.mulSignedMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    emitLine("; MULSM r$dstReg, $sourceAddress (signed) - using unsigned for now")
    mulMemory(dstReg, sourceAddress, type)
}

// === Division ===

internal fun AsmGen.divRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ldy  ${regAddrLo(srcReg)}")
            emitLine("jsr  prog8_math.divmod_ub_asm")
            emitLine("sty  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  ${regAddrLo(srcReg)}")
            emitLine("ldy  ${regAddrHi(srcReg)}")
            emitLine("jsr  prog8_math.divmod_uw_asm")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("sty  ${regAddrHi(dstReg)}")
        }
        else -> TODO("DIVR r$dstReg, r$srcReg ${type.name}")
    }
}

internal fun AsmGen.divImmediate(dstReg: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ldy  #${value and 0xff}")
            emitLine("jsr  prog8_math.divmod_ub_asm")
            emitLine("sty  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  #<${value and 0xffff}")
            emitLine("ldy  #>${value and 0xffff}")
            emitLine("jsr  prog8_math.divmod_uw_asm")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("sty  ${regAddrHi(dstReg)}")
        }
        else -> TODO("DIV r$dstReg, $value ${type.name}")
    }
}

internal fun AsmGen.divMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  $sourceAddress")
            emitLine("ldy  ${regAddrLo(dstReg)}")
            emitLine("jsr  prog8_math.divmod_ub_asm")
            emitLine("sty  $sourceAddress")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  $sourceAddress")
            emitLine("ldy  $sourceAddress+1")
            emitLine("jsr  prog8_math.divmod_uw_asm")
            emitLine("sta  $sourceAddress")
            emitLine("sty  $sourceAddress+1")
        }
        else -> TODO("DIVM r$dstReg, $sourceAddress ${type.name}")
    }
}

internal fun AsmGen.divSignedRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ldy  ${regAddrLo(srcReg)}")
            emitLine("jsr  prog8_math.divmod_b_asm")
            emitLine("sty  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  ${regAddrLo(srcReg)}")
            emitLine("ldy  ${regAddrHi(srcReg)}")
            emitLine("jsr  prog8_math.divmod_w_asm")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("sty  ${regAddrHi(dstReg)}")
        }
        else -> TODO("DIVSR r$dstReg, r$srcReg ${type.name}")
    }
}

internal fun AsmGen.divSignedImmediate(dstReg: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ldy  #${value and 0xff}")
            emitLine("jsr  prog8_math.divmod_b_asm")
            emitLine("sty  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  #<${value and 0xffff}")
            emitLine("ldy  #>${value and 0xffff}")
            emitLine("jsr  prog8_math.divmod_w_asm")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("sty  ${regAddrHi(dstReg)}")
        }
        else -> TODO("DIVS r$dstReg, $value ${type.name}")
    }
}

internal fun AsmGen.divSignedMemory(dstReg: Int, sourceAddress: String, type: IRDataType) {
    TODO("DIVSM r$dstReg, $sourceAddress (signed)")
}

// === Modulo ===

internal fun AsmGen.modRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ldy  ${regAddrLo(srcReg)}")
            emitLine("jsr  prog8_math.remainder_ub_asm")
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  ${regAddrLo(srcReg)}")
            emitLine("ldy  ${regAddrHi(srcReg)}")
            emitLine("jsr  prog8_math.divmod_uw_asm")
            emitLine("lda  P8ZP_SCRATCH_W2")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  P8ZP_SCRATCH_W2+1")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        else -> TODO("MODR r$dstReg, r$srcReg ${type.name}")
    }
}

internal fun AsmGen.modImmediate(dstReg: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ldy  #${value and 0xff}")
            emitLine("jsr  prog8_math.remainder_ub_asm")
            emitLine("sta  ${regAddrLo(dstReg)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  #<${value and 0xffff}")
            emitLine("ldy  #>${value and 0xffff}")
            emitLine("jsr  prog8_math.divmod_uw_asm")
            emitLine("lda  P8ZP_SCRATCH_W2")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("lda  P8ZP_SCRATCH_W2+1")
            emitLine("sta  ${regAddrHi(dstReg)}")
        }
        else -> TODO("MOD r$dstReg, $value ${type.name}")
    }
}

internal fun AsmGen.modSignedRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    TODO("MODSR r$dstReg, r$srcReg (signed)")
}

internal fun AsmGen.modSignedImmediate(dstReg: Int, value: Int, type: IRDataType) {
    emitLine("; MODS r$dstReg, $value (signed, using unsigned)")
    modImmediate(dstReg, value, type)
}

// === DivMod ===

internal fun AsmGen.divModRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    // division and modulo combined: dstReg = dstReg/srcReg, dstReg+1 = dstReg%srcReg
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ldy  ${regAddrLo(srcReg)}")
            emitLine("jsr  prog8_math.divmod_ub_asm")
            emitLine("sty  ${regAddrLo(dstReg)}")
            emitLine("sta  ${regAddrByte(dstReg, 1)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  ${regAddrLo(srcReg)}")
            emitLine("ldy  ${regAddrHi(srcReg)}")
            emitLine("jsr  prog8_math.divmod_uw_asm")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("sty  ${regAddrHi(dstReg)}")
            emitLine("lda  P8ZP_SCRATCH_W2")
            emitLine("sta  ${regAddrByte(dstReg, 2)}")
            emitLine("lda  P8ZP_SCRATCH_W2+1")
            emitLine("sta  ${regAddrByte(dstReg, 3)}")
        }
        else -> TODO("DIVMODR r$dstReg, r$srcReg ${type.name}")
    }
}

internal fun AsmGen.sdivModRegisters(dstReg: Int, srcReg: Int, type: IRDataType) {
    TODO("SDIVMODR r$dstReg, r$srcReg (signed)")
}

internal fun AsmGen.divModImmediate(dstReg: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("ldy  #${value and 0xff}")
            emitLine("jsr  prog8_math.divmod_ub_asm")
            emitLine("sty  ${regAddrLo(dstReg)}")
            emitLine("sta  ${regAddrByte(dstReg, 1)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dstReg)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  #<${value and 0xffff}")
            emitLine("ldy  #>${value and 0xffff}")
            emitLine("jsr  prog8_math.divmod_uw_asm")
            emitLine("sta  ${regAddrLo(dstReg)}")
            emitLine("sty  ${regAddrHi(dstReg)}")
            emitLine("lda  P8ZP_SCRATCH_W2")
            emitLine("sta  ${regAddrByte(dstReg, 2)}")
            emitLine("lda  P8ZP_SCRATCH_W2+1")
            emitLine("sta  ${regAddrByte(dstReg, 3)}")
        }
        else -> TODO("DIVMOD r$dstReg, $value ${type.name}")
    }
}

internal fun AsmGen.sdivModImmediate(dstReg: Int, value: Int, type: IRDataType) {
    emitLine("; SDIVMOD r$dstReg, $value (signed, using unsigned)")
    divModImmediate(dstReg, value, type)
}

// === Compare ===

internal fun AsmGen.compareRegisters(r1: Int, r2: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(r1)}")
            emitLine("cmp  ${regAddrLo(r2)}")
        }
        IRDataType.WORD -> {
            val skip = makeLabel("cmpskip")
            emitLine("lda  ${regAddrLo(r1)}")
            emitLine("cmp  ${regAddrLo(r2)}")
            emitLine("bne  $skip")
            emitLine("lda  ${regAddrHi(r1)}")
            emitLine("cmp  ${regAddrHi(r2)}")
            emitLabel(skip)
        }
        IRDataType.LONG -> {
            val skip = makeLabel("cmpskip")
            emitLine("lda  ${regAddrLo(r1)}")
            emitLine("cmp  ${regAddrLo(r2)}")
            emitLine("bne  $skip")
            emitLine("lda  ${regAddrHi(r1)}")
            emitLine("cmp  ${regAddrHi(r2)}")
            emitLine("bne  $skip")
            emitLine("lda  ${regAddrByte(r1, 2)}")
            emitLine("cmp  ${regAddrByte(r2, 2)}")
            emitLine("bne  $skip")
            emitLine("lda  ${regAddrByte(r1, 3)}")
            emitLine("cmp  ${regAddrByte(r2, 3)}")
            emitLabel(skip)
        }
        IRDataType.FLOAT -> TODO("FLOAT CMP r$r1, r$r2")
    }
}

internal fun AsmGen.compareImmediate(r1: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(r1)}")
            emitLine("cmp  #${value and 0xff}")
        }
        IRDataType.WORD -> {
            // Special case: comparing with 0 must use a different pattern than the
            // standard CMP/SBC cascade, because the cascade's final Z flag only
            // reflects the high byte (the low byte's Z is clobbered by the LDA hi).
            // The ORA pattern correctly sets Z=1 iff BOTH bytes are zero.
            if (value == 0) {
                emitLine("lda  ${regAddrLo(r1)}")
                emitLine("ora  ${regAddrHi(r1)}")
            } else {
                val skip = makeLabel("cmpskip")
                emitLine("lda  ${regAddrLo(r1)}")
                emitLine("cmp  #<${value and 0xffff}")
                emitLine("bne  $skip")
                emitLine("lda  ${regAddrHi(r1)}")
                emitLine("cmp  #>${value and 0xffff}")
                emitLabel(skip)
            }
        }
        IRDataType.LONG -> {
            // Same special case for LONG: the cascade only reflects the highest byte.
            if (value == 0) {
                emitLine("lda  ${regAddrLo(r1)}")
                emitLine("ora  ${regAddrHi(r1)}")
                emitLine("ora  ${regAddrByte(r1, 2)}")
                emitLine("ora  ${regAddrByte(r1, 3)}")
            } else {
                val skip = makeLabel("cmpskip")
                emitLine("lda  ${regAddrLo(r1)}")
                emitLine("cmp  #${value and 0xff}")
                emitLine("bne  $skip")
                emitLine("lda  ${regAddrHi(r1)}")
                emitLine("cmp  #${(value shr 8) and 0xff}")
                emitLine("bne  $skip")
                emitLine("lda  ${regAddrByte(r1, 2)}")
                emitLine("cmp  #${(value shr 16) and 0xff}")
                emitLine("bne  $skip")
                emitLine("lda  ${regAddrByte(r1, 3)}")
                emitLine("cmp  #${(value shr 24) and 0xff}")
                emitLabel(skip)
            }
        }
        else -> TODO("CMPI r$r1, #$value ${type.name}")
    }
}

// === Float arithmetic ===

internal fun AsmGen.translateFloatArithmetic(insn: IRInstruction) {
    val fr1 = insn.fpReg1
    val fr2 = insn.fpReg2
    val immFp = insn.immediateFp
    val addr = insn.address
    val label = insn.labelSymbol
    val offset = insn.labelSymbolOffset

    when (insn.opcode) {
        Opcode.INC -> {
            // fr1 += 1.0
            val fpReg = fr1 ?: error("INC.f needs fpReg1")
            emitLine("lda  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVFM")
            val oneConst = getFloatConstLabel(1.0)
            emitLine("lda  #<$oneConst")
            emitLine("ldy  #>$oneConst")
            emitLine("jsr  floats.FADD")
            emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.DEC -> {
            // fr1 -= 1.0
            // NOTE: FSUB does FAC1 = memory - FAC1, so we must use push/pop + FSUBT instead
            val fpReg = fr1 ?: error("DEC.f needs fpReg1")
            emitLine("lda  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.pushFAC1")
            val oneConst = getFloatConstLabel(1.0)
            emitLine("lda  #<$oneConst")
            emitLine("ldy  #>$oneConst")
            emitLine("jsr  floats.MOVFM")
            emitLine("sec")
            emitLine("jsr  floats.popFAC")
            emitLine("jsr  floats.FSUBT")
            emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.NEG -> {
            // fr1 = -fr1
            val fpReg = fr1 ?: error("NEG.f needs fpReg1")
            emitLine("lda  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.NEGOP")
            emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.ADDR -> {
            // fr1 += fr2
            val dstReg = fr1 ?: error("ADDR.f needs fpReg1")
            val srcReg = fr2 ?: error("ADDR.f needs fpReg2")
            if (useC64PushPopOperands) {
                // On C64/PET32, FAC2 must be loaded last via CONUPK for correct arisgn setup
                emitLine("lda  #<${fpRegAddr(srcReg.value)}")
                emitLine("ldy  #>${fpRegAddr(srcReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("jsr  floats.pushFAC1")
                emitLine("lda  #<${fpRegAddr(dstReg.value)}")
                emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("sec")
                emitLine("jsr  floats.popFAC")
            } else {
                emitLoadFAC2FromFpReg(srcReg.value)
                emitLine("lda  #<${fpRegAddr(dstReg.value)}")
                emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
                emitLine("jsr  floats.MOVFM")
            }
            emitLine("jsr  floats.FADDT${floatTMathSuffix}")
            emitLine("ldx  #<${fpRegAddr(dstReg.value)}")
            emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.SUBR -> {
            // fr1 -= fr2  =>  FSUBT = FAC2 - FAC1, need FAC2=fr1, FAC1=fr2
            val dstReg = fr1 ?: error("SUBR.f needs fpReg1")
            val srcReg = fr2 ?: error("SUBR.f needs fpReg2")
            if (useC64PushPopOperands) {
                emitLine("lda  #<${fpRegAddr(dstReg.value)}")
                emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("jsr  floats.pushFAC1")
                emitLine("lda  #<${fpRegAddr(srcReg.value)}")
                emitLine("ldy  #>${fpRegAddr(srcReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("sec")
                emitLine("jsr  floats.popFAC")
            } else {
                emitLoadFAC2FromFpReg(dstReg.value)
                emitLine("lda  #<${fpRegAddr(srcReg.value)}")
                emitLine("ldy  #>${fpRegAddr(srcReg.value)}")
                emitLine("jsr  floats.MOVFM")
            }
            emitLine("jsr  floats.FSUBT")
            emitLine("ldx  #<${fpRegAddr(dstReg.value)}")
            emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.MULR -> {
            // fr1 *= fr2
            val dstReg = fr1 ?: error("MULR.f needs fpReg1")
            val srcReg = fr2 ?: error("MULR.f needs fpReg2")
            if (useC64PushPopOperands) {
                emitLine("lda  #<${fpRegAddr(srcReg.value)}")
                emitLine("ldy  #>${fpRegAddr(srcReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("jsr  floats.pushFAC1")
                emitLine("lda  #<${fpRegAddr(dstReg.value)}")
                emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("sec")
                emitLine("jsr  floats.popFAC")
            } else {
                emitLoadFAC2FromFpReg(srcReg.value)
                emitLine("lda  #<${fpRegAddr(dstReg.value)}")
                emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
                emitLine("jsr  floats.MOVFM")
            }
            emitLine("jsr  floats.FMULTT${floatTMathSuffix}")
            emitLine("ldx  #<${fpRegAddr(dstReg.value)}")
            emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.DIVR, Opcode.DIVSR -> {
            // fr1 /= fr2  =>  FDIVT = FAC2 / FAC1, need FAC2=fr1, FAC1=fr2
            val dstReg = fr1 ?: error("DIVR.f needs fpReg1")
            val srcReg = fr2 ?: error("DIVR.f needs fpReg2")
            if (useC64PushPopOperands) {
                emitLine("lda  #<${fpRegAddr(dstReg.value)}")
                emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("jsr  floats.pushFAC1")
                emitLine("lda  #<${fpRegAddr(srcReg.value)}")
                emitLine("ldy  #>${fpRegAddr(srcReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("sec")
                emitLine("jsr  floats.popFAC")
            } else {
                emitLoadFAC2FromFpReg(dstReg.value)
                emitLine("lda  #<${fpRegAddr(srcReg.value)}")
                emitLine("ldy  #>${fpRegAddr(srcReg.value)}")
                emitLine("jsr  floats.MOVFM")
            }
            emitLine("jsr  floats.FDIVT${floatTMathSuffix}")
            emitLine("ldx  #<${fpRegAddr(dstReg.value)}")
            emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.ADD, Opcode.MUL, Opcode.MULS -> {
            val dstReg = fr1 ?: error("${insn.opcode}.f needs fpReg1")
            val value = immFp ?: error("${insn.opcode}.f needs immediateFp")
            val constLabel = getFloatConstLabel(value)
            emitLine("lda  #<${fpRegAddr(dstReg.value)}")
            emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("lda  #<$constLabel")
            emitLine("ldy  #>$constLabel")
            val mathRoutine = when (insn.opcode) {
                Opcode.ADD -> "floats.FADD"
                Opcode.MUL, Opcode.MULS -> "floats.FMULT"
                else -> error("unreachable")
            }
            emitLine("jsr  $mathRoutine")
            emitLine("ldx  #<${fpRegAddr(dstReg.value)}")
            emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.SUB -> {
            // NOTE: FSUB does FAC1 = memory - FAC1, so we must use push/pop + FSUBT instead
            val dstReg = fr1 ?: error("SUB.f needs fpReg1")
            val value = immFp ?: error("SUB.f needs immediateFp")
            val constLabel = getFloatConstLabel(value)
            emitLine("lda  #<${fpRegAddr(dstReg.value)}")
            emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.pushFAC1")
            emitLine("lda  #<$constLabel")
            emitLine("ldy  #>$constLabel")
            emitLine("jsr  floats.MOVFM")
            emitLine("sec")
            emitLine("jsr  floats.popFAC")
            emitLine("jsr  floats.FSUBT")
            emitLine("ldx  #<${fpRegAddr(dstReg.value)}")
            emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.DIV, Opcode.DIVS -> {
            // NOTE: FDIV does FAC1 = memory / FAC1, so we must use push/pop + FDIVT instead
            val dstReg = fr1 ?: error("${insn.opcode}.f needs fpReg1")
            val value = immFp ?: error("${insn.opcode}.f needs immediateFp")
            val constLabel = getFloatConstLabel(value)
            emitLine("lda  #<${fpRegAddr(dstReg.value)}")
            emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.pushFAC1")
            emitLine("lda  #<$constLabel")
            emitLine("ldy  #>$constLabel")
            emitLine("jsr  floats.MOVFM")
            emitLine("sec")
            emitLine("jsr  floats.popFAC")
            emitLine("jsr  floats.FDIVT${floatTMathSuffix}")
            emitLine("ldx  #<${fpRegAddr(dstReg.value)}")
            emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.ADDM -> {
            val srcReg = fr1 ?: error("ADDM.f needs fpReg1")
            val target = resolveAddress(addr, label, offset)
            if (useC64PushPopOperands) {
                emitLine("lda  #<$target")
                emitLine("ldy  #>$target")
                emitLine("jsr  floats.MOVFM")
                emitLine("jsr  floats.pushFAC1")
                emitLine("lda  #<${fpRegAddr(srcReg.value)}")
                emitLine("ldy  #>${fpRegAddr(srcReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("sec")
                emitLine("jsr  floats.popFAC")
            } else {
                emitLoadFAC2FromFpReg(srcReg.value)
                emitLine("lda  #<$target")
                emitLine("ldy  #>$target")
                emitLine("jsr  floats.MOVFM")
            }
            emitLine("jsr  floats.FADDT${floatTMathSuffix}")
            emitLine("ldx  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.SUBM -> {
            val srcReg = fr1 ?: error("SUBM.f needs fpReg1")
            val target = resolveAddress(addr, label, offset)
            if (useC64PushPopOperands) {
                emitLine("lda  #<$target")
                emitLine("ldy  #>$target")
                emitLine("jsr  floats.MOVFM")
                emitLine("jsr  floats.pushFAC1")
                emitLine("lda  #<${fpRegAddr(srcReg.value)}")
                emitLine("ldy  #>${fpRegAddr(srcReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("sec")
                emitLine("jsr  floats.popFAC")
            } else {
                emitLoadFAC2FromFpReg(srcReg.value)
                emitLine("lda  #<$target")
                emitLine("ldy  #>$target")
                emitLine("jsr  floats.MOVFM")
            }
            emitLine("jsr  floats.FSUBT")
            emitLine("ldx  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.INCM -> {
            val target = resolveAddress(addr, label, offset)
            emitLine("lda  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVFM")
            val oneConst = getFloatConstLabel(1.0)
            emitLine("lda  #<$oneConst")
            emitLine("ldy  #>$oneConst")
            emitLine("jsr  floats.FADD")
            emitLine("ldx  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.DECM -> {
            val target = resolveAddress(addr, label, offset)
            emitLine("lda  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.pushFAC1")
            val oneConst = getFloatConstLabel(1.0)
            emitLine("lda  #<$oneConst")
            emitLine("ldy  #>$oneConst")
            emitLine("jsr  floats.MOVFM")
            emitLine("sec")
            emitLine("jsr  floats.popFAC")
            emitLine("jsr  floats.FSUBT")
            emitLine("ldx  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.NEGM -> {
            val target = resolveAddress(addr, label, offset)
            emitLine("lda  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.NEGOP")
            emitLine("ldx  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.MULM, Opcode.MULSM -> {
            val srcReg = fr1 ?: error("${insn.opcode}.f needs fpReg1")
            val target = resolveAddress(addr, label, offset)
            if (useC64PushPopOperands) {
                emitLine("lda  #<$target")
                emitLine("ldy  #>$target")
                emitLine("jsr  floats.MOVFM")
                emitLine("jsr  floats.pushFAC1")
                emitLine("lda  #<${fpRegAddr(srcReg.value)}")
                emitLine("ldy  #>${fpRegAddr(srcReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("sec")
                emitLine("jsr  floats.popFAC")
            } else {
                emitLoadFAC2FromFpReg(srcReg.value)
                emitLine("lda  #<$target")
                emitLine("ldy  #>$target")
                emitLine("jsr  floats.MOVFM")
            }
            emitLine("jsr  floats.FMULTT${floatTMathSuffix}")
            emitLine("ldx  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.DIVM, Opcode.DIVSM -> {
            val srcReg = fr1 ?: error("${insn.opcode}.f needs fpReg1")
            val target = resolveAddress(addr, label, offset)
            if (useC64PushPopOperands) {
                emitLine("lda  #<$target")
                emitLine("ldy  #>$target")
                emitLine("jsr  floats.MOVFM")
                emitLine("jsr  floats.pushFAC1")
                emitLine("lda  #<${fpRegAddr(srcReg.value)}")
                emitLine("ldy  #>${fpRegAddr(srcReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("sec")
                emitLine("jsr  floats.popFAC")
            } else {
                emitLine("lda  #<$target")
                emitLine("ldy  #>$target")
                emitLine("jsr  floats.MOVFM")
                emitLine("jsr  floats.MOVAF")
                emitLine("lda  #<${fpRegAddr(srcReg.value)}")
                emitLine("ldy  #>${fpRegAddr(srcReg.value)}")
                emitLine("jsr  floats.MOVFM")
            }
            emitLine("jsr  floats.FDIVT${floatTMathSuffix}")
            emitLine("ldx  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.MULSR -> {
            // fr1 *= fr2  (signed - same as unsigned for floats)
            val dstReg = fr1 ?: error("MULSR.f needs fpReg1")
            val srcReg = fr2 ?: error("MULSR.f needs fpReg2")
            if (useC64PushPopOperands) {
                emitLine("lda  #<${fpRegAddr(srcReg.value)}")
                emitLine("ldy  #>${fpRegAddr(srcReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("jsr  floats.pushFAC1")
                emitLine("lda  #<${fpRegAddr(dstReg.value)}")
                emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("sec")
                emitLine("jsr  floats.popFAC")
            } else {
                emitLoadFAC2FromFpReg(srcReg.value)
                emitLine("lda  #<${fpRegAddr(dstReg.value)}")
                emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
                emitLine("jsr  floats.MOVFM")
            }
            emitLine("jsr  floats.FMULTT${floatTMathSuffix}")
            emitLine("ldx  #<${fpRegAddr(dstReg.value)}")
            emitLine("ldy  #>${fpRegAddr(dstReg.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        else -> TODO("Unsupported float arithmetic opcode: ${insn.opcode}")
    }
}
