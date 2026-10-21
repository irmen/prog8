/*
 * SYSCALL instruction translations for the M68k code generator.
 *
 * Handles inline expansion of intermediate representation syscall
 * instructions into M68k assembly code.
 */

package prog8.codegen.m68k

import prog8.intermediate.*


// === SYSCALL dispatch ===

internal fun AsmGen.translateSyscall(num: Int, site: CallSite) {
    when (num) {
        IMSyscall.STRING_CONTAINS.number -> translateSyscallStringContains(site)
        IMSyscall.BYTEARRAY_CONTAINS.number -> translateSyscallBytearrayContains(site)
        IMSyscall.WORDARRAY_CONTAINS.number -> translateSyscallWordarrayContains(site)
        IMSyscall.SPLIT_WORDARRAY_CONTAINS.number -> translateSyscallSplitWordarrayContains(site)
        IMSyscall.LONGARRAY_CONTAINS.number -> translateSyscallLongarrayContains(site)
        IMSyscall.FLOATARRAY_CONTAINS.number -> translateSyscallFloatarrayContains(site)
        IMSyscall.COMPARE_STRINGS.number -> translateSyscallStringCompare(site)
        IMSyscall.CLAMP_BYTE.number -> translateSyscallClamp(site, IRDataType.BYTE, true)
        IMSyscall.CLAMP_UBYTE.number -> translateSyscallClamp(site, IRDataType.BYTE, false)
        IMSyscall.CLAMP_WORD.number -> translateSyscallClamp(site, IRDataType.WORD, true)
        IMSyscall.CLAMP_UWORD.number -> translateSyscallClamp(site, IRDataType.WORD, false)
        IMSyscall.CLAMP_LONG.number -> translateSyscallClamp(site, IRDataType.LONG, true)
        else -> TODO("syscall $num on m68k")
    }
}

private fun CallSite.argument(index: Int): RegisterOperand =
    arguments.getOrNull(index)?.source ?: error("syscall is missing argument $index")

private fun CallSite.resultRegister(index: Int): RegisterOperand =
    results.getOrNull(index)?.destination ?: error("syscall is missing result $index")

// Compare two strings by delegating to the library routine prog8_lib.strcmp,
// which already implements an efficient case-sensitive comparison that
// correctly handles 32 bits pointers (the pointer type on M68k targets).
private fun AsmGen.translateSyscallStringCompare(site: CallSite) {
    val arg1 = site.argument(0)
    val arg2 = site.argument(1)
    val resultReg = site.resultRegister(0).intNumber
    emitLoadD0(arg1.intNumber, arg1.type)
    loadStringArg(arg2.intNumber, arg2.type, SCRATCH_D1)
    invalidateD0Cache()         // d0 is caller-saved across the subroutine call
    emitLine("bsr  prog8_lib.strcmp")
    emitStoreD0(resultReg, IRDataType.BYTE)
}

// Load a string pointer argument (16 or 32 bits) into the given data register,
// matching the calling convention of strings.compare (full 32 bits address).
private fun AsmGen.loadStringArg(reg: Int, dt: IRDataType, dreg: String) {
    if (dt == IRDataType.LONG) {
        emitLine("move.l  ${operand(reg)}, $dreg")
    } else {
        emitLine("move.w  ${operand(reg)}, $dreg")
    }
}

// Shared helper for array contains syscalls that scan a contiguous array.
// The length argument is a word, so arrays up to the target's ARRAY_SIZE_LIMIT
// are scanned correctly (m68k: 32768 elements, VM: 65535 elements).
private fun AsmGen.translateSyscallArrayContains(site: CallSite, dt: IRDataType) {
    val regElem = site.argument(0).intNumber
    val regArr = site.argument(1).intNumber
    val regLen = site.argument(2).intNumber
    val resultReg = site.resultRegister(0).intNumber

    val labelLoop = makeLabel(".ac_loop")
    val labelFound = makeLabel(".ac_found")
    val labelDone = makeLabel(".ac_done")

    emitLoadD0(regElem, dt)
    emitLine("move.l  ${operand(regArr)}, $SCRATCH_A0")
    emitLine("move.w  ${operand(regLen)}, $SCRATCH_D1")
    emitLine("subq.w  #1, $SCRATCH_D1")
    emitLine("bmi  $labelDone       ; length was 0 -> not found")
    emitRaw("$labelLoop:")
    val cmpInstr = when (dt) {
        IRDataType.BYTE -> "cmp.b"
        IRDataType.WORD -> "cmp.w"
        IRDataType.LONG -> "cmp.l"
        else -> error("array contains: unsupported element type $dt")
    }
    emitLine("$cmpInstr  ($SCRATCH_A0)+, $SCRATCH_D0")
    emitLine("beq  $labelFound")
    emitLine("dbra  $SCRATCH_D1, $labelLoop")
    emitByteConstantToRegister(0, resultReg)
    emitLine("bra  $labelDone")
    emitRaw("$labelFound:")
    emitByteConstantToRegister(1, resultReg)
    emitRaw("$labelDone:")
}

private fun AsmGen.translateSyscallBytearrayContains(site: CallSite) {
    translateSyscallArrayContains(site, IRDataType.BYTE)
}

private fun AsmGen.translateSyscallWordarrayContains(site: CallSite) {
    translateSyscallArrayContains(site, IRDataType.WORD)
}

private fun AsmGen.translateSyscallLongarrayContains(site: CallSite) {
    translateSyscallArrayContains(site, IRDataType.LONG)
}

private fun AsmGen.translateSyscallSplitWordarrayContains(site: CallSite) {
    val regElem = site.argument(0).intNumber
    val regArr = site.argument(1).intNumber
    val regLen = site.argument(2).intNumber
    val resultReg = site.resultRegister(0).intNumber

    val labelLoop = makeLabel(".swc_loop")
    val labelFound = makeLabel(".swc_found")
    val labelDone = makeLabel(".swc_done")

    emitLoadD0(regElem, IRDataType.WORD)
    emitLine("movem.l  d2-d4,-(sp)")
    emitLine("move.l  ${operand(regArr)}, $SCRATCH_A0")     // a0 = lsb array start
    emitLine("move.w  ${operand(regLen)}, $SCRATCH_D1")     // d1 = element count
    emitLine("move.w  $SCRATCH_D1, d2")
    emitLine("add.l  $SCRATCH_A0, d2")                      // d2 = msb array start (lsb + length)
    emitLine("subq.w  #1, $SCRATCH_D1")
    emitLine("bmi  $labelDone       ; length was 0 -> not found")
    emitRaw("$labelLoop:")
    emitLine("move.b  ($SCRATCH_A0,$SCRATCH_D1.w), d3")              // d3 = lsb of element at index d1
    emitLine("move.b  (d2,$SCRATCH_D1.w), d4")              // d4 = msb of element at index d1
    emitLine("lsl.w  #8, d4")
    emitLine("or.w  d3, d4")                       // d4 = reconstructed word
    emitLine("cmp.w  $SCRATCH_D0, d4")
    emitLine("beq  $labelFound")
    emitLine("dbra  $SCRATCH_D1, $labelLoop")
    emitByteConstantToRegister(0, resultReg)
    emitLine("bra  $labelDone")
    emitRaw("$labelFound:")
    emitByteConstantToRegister(1, resultReg)
    emitRaw("$labelDone:")
    emitLine("movem.l  (sp)+,d2-d4")
}

private fun AsmGen.translateSyscallFloatarrayContains(site: CallSite) {
    val regNeedle = site.argument(0).floatNumber
    val regArr = site.argument(1).intNumber
    val regLen = site.argument(2).intNumber
    val resultReg = site.resultRegister(0).intNumber

    val labelLoop = makeLabel(".fac_loop")
    val labelFound = makeLabel(".fac_found")
    val labelDone = makeLabel(".fac_done")

    emitLine("fmove.s  ${floatOperand(regNeedle)}, $SCRATCH_FP0")
    emitLine("move.l  ${operand(regArr)}, $SCRATCH_A0")
    emitLine("move.w  ${operand(regLen)}, $SCRATCH_D1")
    emitLine("subq.w  #1, $SCRATCH_D1")
    emitLine("bmi  $labelDone       ; length was 0 -> not found")
    emitRaw("$labelLoop:")
    emitLine("fcmp.s  ($SCRATCH_A0)+, $SCRATCH_FP0")
    emitLine("fbeq  $labelFound")
    emitLine("dbra  $SCRATCH_D1, $labelLoop")
    emitByteConstantToRegister(0, resultReg)
    emitLine("bra  $labelDone")
    emitRaw("$labelFound:")
    emitByteConstantToRegister(1, resultReg)
    emitRaw("$labelDone:")
}

private fun AsmGen.translateSyscallStringContains(site: CallSite) {
    val regChar = site.argument(0).intNumber
    val regStr = site.argument(1).intNumber
    val resultReg = site.resultRegister(0).intNumber

    val labelLoop = makeLabel(".sc_loop")
    val labelFound = makeLabel(".sc_found")
    val labelDone = makeLabel(".sc_done")

    emitLoadD0(regChar, IRDataType.BYTE)
    emitLine("move.l  ${operand(regStr)}, $SCRATCH_A0")
    emitRaw("$labelLoop:")
    emitLine("move.b  ($SCRATCH_A0)+, $SCRATCH_D1")
    emitLine("beq  $labelDone       ; reached null terminator -> not found")
    emitLine("cmp.b  $SCRATCH_D0, $SCRATCH_D1")
    emitLine("beq  $labelFound")
    emitLine("bra  $labelLoop")
    emitRaw("$labelFound:")
    emitByteConstantToRegister(1, resultReg)
    emitLine("bra  $labelDone")
    emitRaw("$labelDone:")
    emitByteConstantToRegister(0, resultReg)
}

private fun AsmGen.translateSyscallClamp(site: CallSite, dt: IRDataType, signed: Boolean) {
    val valueReg = site.argument(0).intNumber
    val minReg = site.argument(1).intNumber
    val maxReg = site.argument(2).intNumber
    val resultReg = site.resultRegister(0).intNumber

    val labelCheckMax = makeLabel(".clamp_max")
    val labelDone = makeLabel(".clamp_done")
    val bge = if (signed) "bge" else "bhs"
    val ble = if (signed) "ble" else "bls"

    when (dt) {
        IRDataType.BYTE -> {
            emitLoadD0(valueReg, IRDataType.BYTE)
            emitLine("cmp.b  ${operand(minReg)}, $SCRATCH_D0")
            emitLine("$bge  $labelCheckMax")
            emitLoadD0(minReg, IRDataType.BYTE)
            emitLine("bra  $labelDone")
            emitRaw("$labelCheckMax:")
            invalidateD0Cache()             // branch target: d0 still holds valueReg, not minReg
            emitLine("cmp.b  ${operand(maxReg)}, $SCRATCH_D0")
            emitLine("$ble  $labelDone")
            emitLoadD0(maxReg, IRDataType.BYTE)
            emitRaw("$labelDone:")
            invalidateD0Cache()             // branch target: d0 differs per incoming path
            emitStoreD0(resultReg, IRDataType.BYTE)
        }
        IRDataType.WORD -> {
            emitLoadD0(valueReg, IRDataType.WORD)
            emitLine("cmp.w  ${operand(minReg)}, $SCRATCH_D0")
            emitLine("$bge  $labelCheckMax")
            emitLoadD0(minReg, IRDataType.WORD)
            emitLine("bra  $labelDone")
            emitRaw("$labelCheckMax:")
            invalidateD0Cache()             // branch target: d0 still holds valueReg, not minReg
            emitLine("cmp.w  ${operand(maxReg)}, $SCRATCH_D0")
            emitLine("$ble  $labelDone")
            emitLoadD0(maxReg, IRDataType.WORD)
            emitRaw("$labelDone:")
            invalidateD0Cache()             // branch target: d0 differs per incoming path
            emitStoreD0(resultReg, IRDataType.WORD)
        }
        IRDataType.LONG -> {
            emitLoadD0(valueReg, IRDataType.LONG)
            emitLine("cmp.l  ${operand(minReg)}, $SCRATCH_D0")
            emitLine("$bge  $labelCheckMax")
            emitLoadD0(minReg, IRDataType.LONG)
            emitLine("bra  $labelDone")
            emitRaw("$labelCheckMax:")
            invalidateD0Cache()             // branch target: d0 still holds valueReg, not minReg
            emitLine("cmp.l  ${operand(maxReg)}, $SCRATCH_D0")
            emitLine("$ble  $labelDone")
            emitLoadD0(maxReg, IRDataType.LONG)
            emitRaw("$labelDone:")
            invalidateD0Cache()             // branch target: d0 differs per incoming path
            emitStoreD0(resultReg, IRDataType.LONG)
        }
        else -> emitLine("; clamp: unsupported dt $dt")
    }
}
