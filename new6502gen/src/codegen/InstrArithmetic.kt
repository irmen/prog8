/*
 * Arithmetic IR instruction translations for the new6502gen code generator.
 *
 * Handles: INC/DEC, NEG, ADD/SUB, MUL/DIV/MOD (signed and unsigned),
 * DIVMOD (combined division and modulo), and CMP/CMPI.
 *
 * All operations work on virtual registers (r0-r199 in the register file).
 * BYTE and WORD types are fully implemented; LONG and FLOAT are partial/TODO.
 *
 * Multiply/divide/modulo operations use helper subroutines (math_mul8, math_div16, etc.)
 * that must be provided externally (e.g. in the standard library or runtime).
 *
 * Important notes:
 *   - 6502 flag discipline: ADC needs CLC before, SBC needs SEC before
 *   - 16-bit addition/subtraction is done low byte first with carry propagation
 *   - Signed multiply/divide currently fall through to unsigned versions
 */

package codegen

import prog8.intermediate.*

fun CodeGenerator.translateArithmetic(insn: IRInstruction) {
    val type = insn.type ?: IRDataType.BYTE
    val r1 = insn.reg1          // nullable - INCM/DECM/NEGM have no reg1
    val r2 = insn.reg2
    val imm = insn.immediate
    val addr = insn.address
    val label = insn.labelSymbol

    when (insn.opcode) {
        Opcode.INC -> incrementRegister(r1 ?: error("INC needs reg1"), type)
        Opcode.INCM -> incrementMemory(resolveAddress(addr, label), type)
        Opcode.DEC -> decrementRegister(r1 ?: error("DEC needs reg1"), type)
        Opcode.DECM -> decrementMemory(resolveAddress(addr, label), type)
        Opcode.NEG -> negateRegister(r1 ?: error("NEG needs reg1"), type)
        Opcode.NEGM -> negateMemory(resolveAddress(addr, label), type)

        Opcode.ADDR -> {
            val r2val = r2 ?: error("ADDR needs reg2")
            addRegisters(r1 ?: error("ADDR needs reg1"), r2val, type)
        }
        Opcode.ADD -> {
            val value = imm ?: error("ADD needs immediate")
            addImmediate(r1 ?: error("ADD needs reg1"), value, type)
        }
        Opcode.ADDM -> {
            val target = resolveAddress(addr, label)
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
            val target = resolveAddress(addr, label)
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
            val target = resolveAddress(addr, label)
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
            val target = resolveAddress(addr, label)
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
            val target = resolveAddress(addr, label)
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
            val target = resolveAddress(addr, label)
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
            emitLine("inc  ${regAddrLo(reg) + 2}")
            emitLine("bne  +")
            emitLine("inc  ${regAddrLo(reg) + 3}")
            emitLabel("+")
        }
        IRDataType.FLOAT -> TODO("FLOAT INC r$reg")
    }
}

private fun CodeGenerator.decrementRegister(reg: Int, type: IRDataType) {
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
        }
        IRDataType.LONG -> {
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("bne  +")
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("bne  +")
            emitLine("lda  ${regAddrLo(reg) + 2}")
            emitLine("bne  +")
            emitLine("dec  ${regAddrLo(reg) + 3}")
            emitLabel("+")
            emitLine("dec  ${regAddrLo(reg) + 2}")
            emitLine("bne  +")
            emitLine("dec  ${regAddrHi(reg)}")
            emitLabel("+")
            emitLine("dec  ${regAddrLo(reg)}")
            // Note: this decrement logic is simplified and may not handle all cases correctly
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
        else -> TODO("INCM $target ${type.name}")
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
        }
        else -> TODO("DECM $target ${type.name}")
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
        else -> TODO("NEG r$reg ${type.name}")
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
        else -> TODO("NEGM $target ${type.name}")
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
            emitLine("lda  ${regAddrLo(dst) + 2}")
            emitLine("adc  ${regAddrLo(src) + 2}")
            emitLine("sta  ${regAddrLo(dst) + 2}")
            emitLine("lda  ${regAddrLo(dst) + 3}")
            emitLine("adc  ${regAddrLo(src) + 3}")
            emitLine("sta  ${regAddrLo(dst) + 3}")
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
        else -> TODO("ADD r$dst, #$value ${type.name}")
    }
}

private fun CodeGenerator.addMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("clc")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("adc  $source")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("clc")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("adc  $source")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("adc  $source+1")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        else -> TODO("ADDM r$dst, $source ${type.name}")
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
            emitLine("lda  ${regAddrLo(dst) + 2}")
            emitLine("sbc  ${regAddrLo(src) + 2}")
            emitLine("sta  ${regAddrLo(dst) + 2}")
            emitLine("lda  ${regAddrLo(dst) + 3}")
            emitLine("sbc  ${regAddrLo(src) + 3}")
            emitLine("sta  ${regAddrLo(dst) + 3}")
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
        else -> TODO("SUB r$dst, #$value ${type.name}")
    }
}

private fun CodeGenerator.subMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("sec")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sbc  $source")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("sec")
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sbc  $source")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sbc  $source+1")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        else -> TODO("SUBM r$dst, $source ${type.name}")
    }
}

// === Multiplication ===

private fun CodeGenerator.mulRegisters(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("jsr  math_mul8")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  math_tmp+1")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  math_tmp+2")
            emitLine("lda  ${regAddrHi(src)}")
            emitLine("sta  math_tmp+3")
            emitLine("jsr  math_mul16")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  math_tmp+1")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> TODO("MULR LONG r$dst, r$src")
        IRDataType.FLOAT -> TODO("MULR FLOAT r$dst, r$src")
    }
}

private fun CodeGenerator.mulImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  #${value and 0xff}")
            emitLine("jsr  math_mul8")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  math_tmp+1")
            emitLine("lda  #<${value and 0xffff}")
            emitLine("sta  math_tmp+2")
            emitLine("lda  #>${value and 0xffff}")
            emitLine("sta  math_tmp+3")
            emitLine("jsr  math_mul16")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  math_tmp+1")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        else -> TODO("MUL r$dst, #$value ${type.name}")
    }
}

private fun CodeGenerator.mulMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  $source")
            emitLine("jsr  math_mul8")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  math_tmp+1")
            emitLine("lda  $source")
            emitLine("sta  math_tmp+2")
            emitLine("lda  $source+1")
            emitLine("sta  math_tmp+3")
            emitLine("jsr  math_mul16")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  math_tmp+1")
            emitLine("sta  ${regAddrHi(dst)}")
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
            emitLine("sta  math_tmp")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  math_tmp+1")
            emitLine("jsr  math_div8")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  math_tmp+1")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  math_tmp+2")
            emitLine("lda  ${regAddrHi(src)}")
            emitLine("sta  math_tmp+3")
            emitLine("jsr  math_div16")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  math_tmp+1")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        else -> TODO("DIVR r$dst, r$src ${type.name}")
    }
}

private fun CodeGenerator.divImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  #${value and 0xff}")
            emitLine("sta  math_tmp+1")
            emitLine("jsr  math_div8")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  math_tmp+1")
            emitLine("lda  #<${value and 0xffff}")
            emitLine("sta  math_tmp+2")
            emitLine("lda  #>${value and 0xffff}")
            emitLine("sta  math_tmp+3")
            emitLine("jsr  math_div16")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  math_tmp+1")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        else -> TODO("DIV r$dst, $value ${type.name}")
    }
}

private fun CodeGenerator.divMemory(dst: Int, source: String, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  $source")
            emitLine("sta  math_tmp+1")
            emitLine("jsr  math_div8")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  math_tmp+1")
            emitLine("lda  $source")
            emitLine("sta  math_tmp+2")
            emitLine("lda  $source+1")
            emitLine("sta  math_tmp+3")
            emitLine("jsr  math_div16")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  math_tmp+1")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        else -> TODO("DIVM r$dst, $source ${type.name}")
    }
}

private fun CodeGenerator.divSignedRegisters(dst: Int, src: Int, type: IRDataType) {
    emitLine("; DIVSR r$dst, r$src (signed) - using unsigned for now")
    divRegisters(dst, src, type)
}

private fun CodeGenerator.divSignedImmediate(dst: Int, value: Int, type: IRDataType) {
    emitLine("; DIVS r$dst, $value (signed, using unsigned)")
    divImmediate(dst, value, type)
}

private fun CodeGenerator.divSignedMemory(dst: Int, source: String, type: IRDataType) {
    TODO("DIVSM r$dst, $source (signed)")
}

// === Modulo ===

private fun CodeGenerator.modRegisters(dst: Int, src: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  math_tmp+1")
            emitLine("jsr  math_mod8")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  math_tmp+1")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  math_tmp+2")
            emitLine("lda  ${regAddrHi(src)}")
            emitLine("sta  math_tmp+3")
            emitLine("jsr  math_mod16")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  math_tmp+1")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        else -> TODO("MODR r$dst, r$src ${type.name}")
    }
}

private fun CodeGenerator.modImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  #${value and 0xff}")
            emitLine("sta  math_tmp+1")
            emitLine("jsr  math_mod8")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  math_tmp+1")
            emitLine("lda  #<${value and 0xffff}")
            emitLine("sta  math_tmp+2")
            emitLine("lda  #>${value and 0xffff}")
            emitLine("sta  math_tmp+3")
            emitLine("jsr  math_mod16")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  math_tmp+1")
            emitLine("sta  ${regAddrHi(dst)}")
        }
        else -> TODO("MOD r$dst, $value ${type.name}")
    }
}

private fun CodeGenerator.modSignedRegisters(dst: Int, src: Int, type: IRDataType) {
    TODO("MODSR r$dst, r$src (signed)")
    modRegisters(dst, src, type)
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
            emitLine("sta  math_tmp")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  math_tmp+1")
            emitLine("jsr  math_divmod8")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  math_tmp+1")
            emitLine("sta  ${regAddrLo(dst) + 1}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  math_tmp+1")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  math_tmp+2")
            emitLine("lda  ${regAddrHi(src)}")
            emitLine("sta  math_tmp+3")
            emitLine("jsr  math_divmod16")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  math_tmp+1")
            emitLine("sta  ${regAddrHi(dst)}")
            emitLine("lda  math_tmp+2")
            emitLine("sta  ${regAddrLo(dst) + 2}")
            emitLine("lda  math_tmp+3")
            emitLine("sta  ${regAddrLo(dst) + 3}")
        }
        else -> TODO("DIVMODR r$dst, r$src ${type.name}")
    }
}

private fun CodeGenerator.sdivModRegisters(dst: Int, src: Int, type: IRDataType) {
    TODO("SDIVMODR r$dst, r$src (signed)")
    divModRegisters(dst, src, type)
}

private fun CodeGenerator.divModImmediate(dst: Int, value: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  #${value and 0xff}")
            emitLine("sta  math_tmp+1")
            emitLine("jsr  math_divmod8")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  math_tmp+1")
            emitLine("sta  ${regAddrLo(dst) + 1}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(dst)}")
            emitLine("sta  math_tmp")
            emitLine("lda  ${regAddrHi(dst)}")
            emitLine("sta  math_tmp+1")
            emitLine("lda  #<${value and 0xffff}")
            emitLine("sta  math_tmp+2")
            emitLine("lda  #>${value and 0xffff}")
            emitLine("sta  math_tmp+3")
            emitLine("jsr  math_divmod16")
            emitLine("lda  math_tmp")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("lda  math_tmp+1")
            emitLine("sta  ${regAddrHi(dst)}")
            emitLine("lda  math_tmp+2")
            emitLine("sta  ${regAddrLo(dst) + 2}")
            emitLine("lda  math_tmp+3")
            emitLine("sta  ${regAddrLo(dst) + 3}")
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
            emitLine("lda  ${regAddrLo(r1) + 2}")
            emitLine("sbc  ${regAddrLo(r2) + 2}")
            emitLine("lda  ${regAddrLo(r1) + 3}")
            emitLine("sbc  ${regAddrLo(r2) + 3}")
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
            emitLine("lda  ${regAddrLo(r1)}")
            emitLine("cmp  #<${value and 0xffff}")
            emitLine("lda  ${regAddrHi(r1)}")
            emitLine("sbc  #>${value and 0xffff}")
        }
        else -> TODO("CMPI r$r1, #$value ${type.name}")
    }
}
