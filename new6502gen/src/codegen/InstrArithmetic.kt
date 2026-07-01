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

package codegen

import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction
import prog8.intermediate.Opcode

fun CodeGenerator.translateArithmetic(insn: IRInstruction) {
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

        Opcode.ADDR -> {
            val r2val = r2 ?: error("ADDR needs reg2")
            addRegisters(r1 ?: error("ADDR needs reg1"), r2val, type)
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

private fun CodeGenerator.incrementRegister(reg: Int, type: IRDataType) {
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

private fun CodeGenerator.decrementRegister(reg: Int, type: IRDataType) {
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

private fun CodeGenerator.incrementMemory(target: String, type: IRDataType) {
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

private fun CodeGenerator.decrementMemory(target: String, type: IRDataType) {
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

private fun CodeGenerator.negateRegister(reg: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("sec")
            emitLine("lda  #0")
            emitLine("sbc  ${regAddrLo(reg)}")
            emitLine("sta  ${regAddrLo(reg)}")
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

private fun CodeGenerator.negateMemory(target: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("sec")
            emitLine("lda  #0")
            emitLine("sbc  $target")
            emitLine("sta  $target")
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

private fun CodeGenerator.addRegisters(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("clc")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("adc  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("adc  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("adc  ${regAddrHi(src)}")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> {
            emitLine("clc")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("adc  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("adc  ${regAddrHi(src)}")
            emitLine("sta  ${regAddrHi(dst)}")
            emitLine("lda  ${regAddrByte(dst, 2)}")
            emitLine("adc  ${regAddrByte(src, 2)}")
            emitLine("sta  ${regAddrByte(dst, 2)}")
            emitLine("lda  ${regAddrByte(dst, 3)}")
            emitLine("adc  ${regAddrByte(src, 3)}")
            emitLine("sta  ${regAddrByte(dst, 3)}")
        }
        IRDataType.FLOAT -> TODO("FLOAT ADDR r$dst, r$src")
    }
}

private fun CodeGenerator.addImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("clc")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("adc  #${value and 0xff}")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("adc  #<${value and 0xffff}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("adc  #>${value and 0xffff}")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> {
            emitLine("clc")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("adc  #${value and 0xff}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrByte(dst, 1)}")
            emitLine("adc  #${(value shr 8) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 1)}")
            emitLine("lda  ${regAddrByte(dst, 2)}")
            emitLine("adc  #${(value shr 16) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 2)}")
            emitLine("lda  ${regAddrByte(dst, 3)}")
            emitLine("adc  #${(value shr 24) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 3)}")
        }
        IRDataType.FLOAT -> TODO("FLOAT ADD r$dst, #$value")
    }
}

private fun CodeGenerator.addMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("clc")
            emitLine("lda  $source")
            emitLine("adc  ${regAddrLo(dst)}")
            emitLine("sta  $source")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("lda  $source")
            emitLine("adc  ${regAddrLo(dst)}")
            emitLine("sta  $source")
            emitLine("lda  $source+1")
            emitLine("adc  ${regAddrHi(dst)}")
            emitLine("sta  $source+1")
        }
        IRDataType.LONG -> {
            emitLine("clc")
            emitLine("lda  $source")
            emitLine("adc  ${regAddrLo(dst)}")
            emitLine("sta  $source")
            emitLine("lda  $source+1")
            emitLine("adc  ${regAddrByte(dst, 1)}")
            emitLine("sta  $source+1")
            emitLine("lda  $source+2")
            emitLine("adc  ${regAddrByte(dst, 2)}")
            emitLine("sta  $source+2")
            emitLine("lda  $source+3")
            emitLine("adc  ${regAddrByte(dst, 3)}")
            emitLine("sta  $source+3")
        }
        IRDataType.FLOAT -> TODO("FLOAT ADDM r$dst, $source")
    }
}

// === Subtraction ===

private fun CodeGenerator.subRegisters(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("sec")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sbc  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("sec")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sbc  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sbc  ${regAddrHi(src)}")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> {
            emitLine("sec")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sbc  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sbc  ${regAddrHi(src)}")
            emitLine("sta  ${regAddrHi(dst)}")
            emitLine("lda  ${regAddrByte(dst, 2)}")
            emitLine("sbc  ${regAddrByte(src, 2)}")
            emitLine("sta  ${regAddrByte(dst, 2)}")
            emitLine("lda  ${regAddrByte(dst, 3)}")
            emitLine("sbc  ${regAddrByte(src, 3)}")
            emitLine("sta  ${regAddrByte(dst, 3)}")
        }
        IRDataType.FLOAT -> TODO("FLOAT SUBR r$dst, r$src")
    }
}

private fun CodeGenerator.subImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("sec")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sbc  #${value and 0xff}")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("sec")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sbc  #<${value and 0xffff}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sbc  #>${value and 0xffff}")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> {
            emitLine("sec")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sbc  #${value and 0xff}")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrByte(dst, 1)}")
            emitLine("sbc  #${(value shr 8) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 1)}")
            emitLine("lda  ${regAddrByte(dst, 2)}")
            emitLine("sbc  #${(value shr 16) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 2)}")
            emitLine("lda  ${regAddrByte(dst, 3)}")
            emitLine("sbc  #${(value shr 24) and 0xff}")
            emitLine("sta  ${regAddrByte(dst, 3)}")
        }
        IRDataType.FLOAT -> TODO("FLOAT SUB r$dst, #$value")
    }
}

private fun CodeGenerator.subMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("sec")
            emitLine("lda  $source")
            emitLine("sbc  ${regAddrLo(dst)}")
            emitLine("sta  $source")
        }
        IRDataType.WORD -> {
            emitLine("sec")
            emitLine("lda  $source")
            emitLine("sbc  ${regAddrLo(dst)}")
            emitLine("sta  $source")
            emitLine("lda  $source+1")
            emitLine("sbc  ${regAddrHi(dst)}")
            emitLine("sta  $source+1")
        }
        IRDataType.LONG -> {
            emitLine("sec")
            emitLine("lda  $source")
            emitLine("sbc  ${regAddrLo(dst)}")
            emitLine("sta  $source")
            emitLine("lda  $source+1")
            emitLine("sbc  ${regAddrByte(dst, 1)}")
            emitLine("sta  $source+1")
            emitLine("lda  $source+2")
            emitLine("sbc  ${regAddrByte(dst, 2)}")
            emitLine("sta  $source+2")
            emitLine("lda  $source+3")
            emitLine("sbc  ${regAddrByte(dst, 3)}")
            emitLine("sta  $source+3")
        }
        IRDataType.FLOAT -> TODO("FLOAT SUBM r$dst, $source")
    }
}

// === Multiplication ===

private fun CodeGenerator.mulRegisters(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  ${regAddrLo(src)}")
            emitLine("jsr  prog8_math.multiply_bytes")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  prog8_math.multiply_words.multiplier")
            emitLine("lda  ${regAddrHi(src)}")
            emitLine("sta  prog8_math.multiply_words.multiplier+1")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  ${regAddrHi(dst)}")
            emitLine("jsr  prog8_math.multiply_words")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("sty  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> TODO("MULR LONG r$dst, r$src")
        IRDataType.FLOAT -> TODO("MULR FLOAT r$dst, r$src")
    }
}

private fun CodeGenerator.mulImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  #${value and 0xff}")
            emitLine("jsr  prog8_math.multiply_bytes")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  #<${value and 0xffff}")
            emitLine("sta  prog8_math.multiply_words.multiplier")
            emitLine("lda  #>${value and 0xffff}")
            emitLine("sta  prog8_math.multiply_words.multiplier+1")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  ${regAddrHi(dst)}")
            emitLine("jsr  prog8_math.multiply_words")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("sty  ${regAddrHi(dst)}")
        }
        else -> TODO("MUL r$dst, #$value ${type.name}")
    }
}

private fun CodeGenerator.mulMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  $source")
            emitLine("jsr  prog8_math.multiply_bytes")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  $source")
            emitLine("sta  prog8_math.multiply_words.multiplier")
            emitLine("lda  $source+1")
            emitLine("sta  prog8_math.multiply_words.multiplier+1")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  ${regAddrHi(dst)}")
            emitLine("jsr  prog8_math.multiply_words")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("sty  ${regAddrHi(dst)}")
        }
        else -> TODO("MULM r$dst, $source ${type.name}")
    }
}

private fun CodeGenerator.mulSignedRegisters(dst: Int, src: Int, type: IRDataType) {
    emitLine("; MULSR r$dst, r$src (signed) - using unsigned for now")
    mulRegisters(dst, src, type)
}

private fun CodeGenerator.mulSignedImmediate(dst: Int, value: Int, type: IRDataType) {
    emitLine("; MULS r$dst, #$value (signed) - using unsigned for now")
    mulImmediate(dst, value, type)
}

private fun CodeGenerator.mulSignedMemory(dst: Int, source: String, type: IRDataType) {
    emitLine("; MULSM r$dst, $source (signed) - using unsigned for now")
    mulMemory(dst, source, type)
}

// === Division ===

private fun CodeGenerator.divRegisters(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  ${regAddrLo(src)}")
            emitLine("jsr  prog8_math.divmod_ub_asm")
            emitLine("sty  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("ldy  ${regAddrHi(src)}")
            emitLine("jsr  prog8_math.divmod_uw_asm")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("sty  ${regAddrHi(dst)}")
        }
        else -> TODO("DIVR r$dst, r$src ${type.name}")
    }
}

private fun CodeGenerator.divImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  #${value and 0xff}")
            emitLine("jsr  prog8_math.divmod_ub_asm")
            emitLine("sty  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  #<${value and 0xffff}")
            emitLine("ldy  #>${value and 0xffff}")
            emitLine("jsr  prog8_math.divmod_uw_asm")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("sty  ${regAddrHi(dst)}")
        }
        else -> TODO("DIV r$dst, $value ${type.name}")
    }
}

private fun CodeGenerator.divMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  $source")
            emitLine("jsr  prog8_math.divmod_ub_asm")
            emitLine("sty  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  $source")
            emitLine("ldy  $source+1")
            emitLine("jsr  prog8_math.divmod_uw_asm")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("sty  ${regAddrHi(dst)}")
        }
        else -> TODO("DIVM r$dst, $source ${type.name}")
    }
}

private fun CodeGenerator.divSignedRegisters(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  ${regAddrLo(src)}")
            emitLine("jsr  prog8_math.divmod_b_asm")
            emitLine("sty  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("ldy  ${regAddrHi(src)}")
            emitLine("jsr  prog8_math.divmod_w_asm")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("sty  ${regAddrHi(dst)}")
        }
        else -> TODO("DIVSR r$dst, r$src ${type.name}")
    }
}

private fun CodeGenerator.divSignedImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  #${value and 0xff}")
            emitLine("jsr  prog8_math.divmod_b_asm")
            emitLine("sty  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  #<${value and 0xffff}")
            emitLine("ldy  #>${value and 0xffff}")
            emitLine("jsr  prog8_math.divmod_w_asm")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("sty  ${regAddrHi(dst)}")
        }
        else -> TODO("DIVS r$dst, $value ${type.name}")
    }
}

private fun CodeGenerator.divSignedMemory(dst: Int, source: String, type: IRDataType) {
    TODO("DIVSM r$dst, $source (signed)")
}

// === Modulo ===

private fun CodeGenerator.modRegisters(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  ${regAddrLo(src)}")
            emitLine("jsr  prog8_math.remainder_ub_asm")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("ldy  ${regAddrHi(src)}")
            emitLine("jsr  prog8_math.divmod_uw_asm")
            emitLine("lda  P8ZP_SCRATCH_W2")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  P8ZP_SCRATCH_W2+1")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        else -> TODO("MODR r$dst, r$src ${type.name}")
    }
}

private fun CodeGenerator.modImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  #${value and 0xff}")
            emitLine("jsr  prog8_math.remainder_ub_asm")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  #<${value and 0xffff}")
            emitLine("ldy  #>${value and 0xffff}")
            emitLine("jsr  prog8_math.divmod_uw_asm")
            emitLine("lda  P8ZP_SCRATCH_W2")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  P8ZP_SCRATCH_W2+1")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        else -> TODO("MOD r$dst, $value ${type.name}")
    }
}

private fun CodeGenerator.modSignedRegisters(dst: Int, src: Int, type: IRDataType) {
    TODO("MODSR r$dst, r$src (signed)")
}

private fun CodeGenerator.modSignedImmediate(dst: Int, value: Int, type: IRDataType) {
    emitLine("; MODS r$dst, $value (signed, using unsigned)")
    modImmediate(dst, value, type)
}

// === DivMod ===

private fun CodeGenerator.divModRegisters(dst: Int, src: Int, type: IRDataType) {
    // division and modulo combined: dst = dst/src, dst+1 = dst%src
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  ${regAddrLo(src)}")
            emitLine("jsr  prog8_math.divmod_ub_asm")
            emitLine("sty  ${regAddrLo(dst)}")
            emitLine("sta  ${regAddrByte(dst, 1)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("ldy  ${regAddrHi(src)}")
            emitLine("jsr  prog8_math.divmod_uw_asm")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("sty  ${regAddrHi(dst)}")
            emitLine("lda  P8ZP_SCRATCH_W2")
            emitLine("sta  ${regAddrByte(dst, 2)}")
            emitLine("lda  P8ZP_SCRATCH_W2+1")
            emitLine("sta  ${regAddrByte(dst, 3)}")
        }
        else -> TODO("DIVMODR r$dst, r$src ${type.name}")
    }
}

private fun CodeGenerator.sdivModRegisters(dst: Int, src: Int, type: IRDataType) {
    TODO("SDIVMODR r$dst, r$src (signed)")
}

private fun CodeGenerator.divModImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("ldy  #${value and 0xff}")
            emitLine("jsr  prog8_math.divmod_ub_asm")
            emitLine("sty  ${regAddrLo(dst)}")
            emitLine("sta  ${regAddrByte(dst, 1)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  P8ZP_SCRATCH_W1+1")
            emitLine("lda  #<${value and 0xffff}")
            emitLine("ldy  #>${value and 0xffff}")
            emitLine("jsr  prog8_math.divmod_uw_asm")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("sty  ${regAddrHi(dst)}")
            emitLine("lda  P8ZP_SCRATCH_W2")
            emitLine("sta  ${regAddrByte(dst, 2)}")
            emitLine("lda  P8ZP_SCRATCH_W2+1")
            emitLine("sta  ${regAddrByte(dst, 3)}")
        }
        else -> TODO("DIVMOD r$dst, $value ${type.name}")
    }
}

private fun CodeGenerator.sdivModImmediate(dst: Int, value: Int, type: IRDataType) {
    emitLine("; SDIVMOD r$dst, $value (signed, using unsigned)")
    divModImmediate(dst, value, type)
}

// === Compare ===

private fun CodeGenerator.compareRegisters(r1: Int, r2: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(r1)}")
            emitLine("cmp  ${regAddrLo(r2)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(r1)}")
            emitLine("cmp  ${regAddrLo(r2)}")
            emitLine("lda  ${regAddrHi(r1)}")
            emitLine("sbc  ${regAddrHi(r2)}")
        }
        IRDataType.LONG -> {
            emitLine("lda  ${regAddrLo(r1)}")
            emitLine("cmp  ${regAddrLo(r2)}")
            emitLine("lda  ${regAddrHi(r1)}")
            emitLine("sbc  ${regAddrHi(r2)}")
            emitLine("lda  ${regAddrByte(r1, 2)}")
            emitLine("sbc  ${regAddrByte(r2, 2)}")
            emitLine("lda  ${regAddrByte(r1, 3)}")
            emitLine("sbc  ${regAddrByte(r2, 3)}")
        }
        IRDataType.FLOAT -> TODO("FLOAT CMP r$r1, r$r2")
    }
}

private fun CodeGenerator.compareImmediate(r1: Int, value: Int, type: IRDataType) {
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
                emitLine("lda  ${regAddrLo(r1)}")
                emitLine("cmp  #<${value and 0xffff}")
                emitLine("lda  ${regAddrHi(r1)}")
                emitLine("sbc  #>${value and 0xffff}")
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
                emitLine("lda  ${regAddrLo(r1)}")
                emitLine("cmp  #${value and 0xff}")
                emitLine("lda  ${regAddrHi(r1)}")
                emitLine("sbc  #${(value shr 8) and 0xff}")
                emitLine("lda  ${regAddrByte(r1, 2)}")
                emitLine("sbc  #${(value shr 16) and 0xff}")
                emitLine("lda  ${regAddrByte(r1, 3)}")
                emitLine("sbc  #${(value shr 24) and 0xff}")
            }
        }
        else -> TODO("CMPI r$r1, #$value ${type.name}")
    }
}

// === Float arithmetic ===

private fun CodeGenerator.translateFloatArithmetic(insn: IRInstruction) {
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
            val fpReg = fr1 ?: error("DEC.f needs fpReg1")
            emitLine("lda  #<${fpRegAddr(fpReg.value)}")
            emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
            emitLine("jsr  floats.MOVFM")
            val oneConst = getFloatConstLabel(1.0)
            emitLine("lda  #<$oneConst")
            emitLine("ldy  #>$oneConst")
            emitLine("jsr  floats.FSUB")
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
            // Safe order: MOVFM src→FAC1, MOVAF→FAC2, MOVFM dst→FAC1, FADDT
            val dst = fr1 ?: error("ADDR.f needs fpReg1")
            val src = fr2 ?: error("ADDR.f needs fpReg2")
            emitLine("lda  #<${fpRegAddr(src.value)}")
            emitLine("ldy  #>${fpRegAddr(src.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.MOVAF")
            emitLine("lda  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.FADDT")
            emitLine("ldx  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.SUBR -> {
            // fr1 -= fr2  =>  FSUBT = FAC2 - FAC1
            // Need FAC2=fr1, FAC1=fr2 for result=fr1-fr2
            val dst = fr1 ?: error("SUBR.f needs fpReg1")
            val src = fr2 ?: error("SUBR.f needs fpReg2")
            emitLine("lda  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.MOVAF")
            emitLine("lda  #<${fpRegAddr(src.value)}")
            emitLine("ldy  #>${fpRegAddr(src.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.FSUBT")
            emitLine("ldx  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.MULR -> {
            // fr1 *= fr2
            val dst = fr1 ?: error("MULR.f needs fpReg1")
            val src = fr2 ?: error("MULR.f needs fpReg2")
            emitLine("lda  #<${fpRegAddr(src.value)}")
            emitLine("ldy  #>${fpRegAddr(src.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.MOVAF")
            emitLine("lda  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.FMULTT")
            emitLine("ldx  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.DIVR, Opcode.DIVSR -> {
            // fr1 /= fr2  =>  FDIVT = FAC2 / FAC1
            // Need FAC2=fr1, FAC1=fr2 for result=fr1/fr2
            val dst = fr1 ?: error("DIVR.f needs fpReg1")
            val src = fr2 ?: error("DIVR.f needs fpReg2")
            emitLine("lda  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.MOVAF")
            emitLine("lda  #<${fpRegAddr(src.value)}")
            emitLine("ldy  #>${fpRegAddr(src.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.FDIVT")
            emitLine("ldx  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.ADD, Opcode.MUL, Opcode.MULS -> {
            val dst = fr1 ?: error("${insn.opcode}.f needs fpReg1")
            val value = immFp ?: error("${insn.opcode}.f needs immediateFp")
            val constLabel = getFloatConstLabel(value)
            emitLine("lda  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("lda  #<$constLabel")
            emitLine("ldy  #>$constLabel")
            val mathRoutine = when (insn.opcode) {
                Opcode.ADD -> "floats.FADD"
                Opcode.MUL, Opcode.MULS -> "floats.FMULT"
                else -> error("unreachable")
            }
            emitLine("jsr  $mathRoutine")
            emitLine("ldx  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.SUB, Opcode.DIV, Opcode.DIVS -> {
            val dst = fr1 ?: error("${insn.opcode}.f needs fpReg1")
            val value = immFp ?: error("${insn.opcode}.f needs immediateFp")
            val constLabel = getFloatConstLabel(value)
            emitLine("lda  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("lda  #<$constLabel")
            emitLine("ldy  #>$constLabel")
            val mathRoutine = when (insn.opcode) {
                Opcode.SUB -> "floats.FSUB"
                Opcode.DIV, Opcode.DIVS -> "floats.FDIV"
                else -> error("unreachable")
            }
            emitLine("jsr  $mathRoutine")
            emitLine("ldx  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.ADDM -> {
            val src = fr1 ?: error("ADDM.f needs fpReg1")
            val target = resolveAddress(addr, label, offset)
            emitLine("lda  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.MOVAF")
            emitLine("lda  #<${fpRegAddr(src.value)}")
            emitLine("ldy  #>${fpRegAddr(src.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.FADDT")
            emitLine("ldx  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.SUBM -> {
            val src = fr1 ?: error("SUBM.f needs fpReg1")
            val target = resolveAddress(addr, label, offset)
            emitLine("lda  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.MOVAF")
            emitLine("lda  #<${fpRegAddr(src.value)}")
            emitLine("ldy  #>${fpRegAddr(src.value)}")
            emitLine("jsr  floats.MOVFM")
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
            val oneConst = getFloatConstLabel(1.0)
            emitLine("lda  #<$oneConst")
            emitLine("ldy  #>$oneConst")
            emitLine("jsr  floats.FSUB")
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
            val src = fr1 ?: error("${insn.opcode}.f needs fpReg1")
            val target = resolveAddress(addr, label, offset)
            emitLine("lda  #<${fpRegAddr(src.value)}")
            emitLine("ldy  #>${fpRegAddr(src.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.MOVAF")
            emitLine("lda  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.FMULTT")
            emitLine("ldx  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.DIVM, Opcode.DIVSM -> {
            val src = fr1 ?: error("${insn.opcode}.f needs fpReg1")
            val target = resolveAddress(addr, label, offset)
            emitLine("lda  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.MOVAF")
            emitLine("lda  #<${fpRegAddr(src.value)}")
            emitLine("ldy  #>${fpRegAddr(src.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.FDIVT")
            emitLine("ldx  #<$target")
            emitLine("ldy  #>$target")
            emitLine("jsr  floats.MOVMF")
        }

        Opcode.MULSR -> {
            // fr1 *= fr2  (signed - same as unsigned for floats)
            val dst = fr1 ?: error("MULSR.f needs fpReg1")
            val src = fr2 ?: error("MULSR.f needs fpReg2")
            emitLine("lda  #<${fpRegAddr(src.value)}")
            emitLine("ldy  #>${fpRegAddr(src.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.MOVAF")
            emitLine("lda  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.FMULTT")
            emitLine("ldx  #<${fpRegAddr(dst.value)}")
            emitLine("ldy  #>${fpRegAddr(dst.value)}")
            emitLine("jsr  floats.MOVMF")
        }

        else -> TODO("Unsupported float arithmetic opcode: ${insn.opcode}")
    }
}
