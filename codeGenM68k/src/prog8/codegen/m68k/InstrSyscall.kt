/*
 * SYSCALL instruction translations for the M68k code generator.
 *
 * Handles inline expansion of intermediate representation syscall
 * instructions into M68k assembly code.
 */

package prog8.codegen.m68k

import prog8.intermediate.CallSite
import prog8.intermediate.IMSyscall
import prog8.intermediate.IRDataType
import prog8.intermediate.RegisterOperand
import prog8.intermediate.intNumber


// === SYSCALL dispatch ===

internal fun AsmGen.translateSyscall(num: Int, site: CallSite) {
    when (num) {
        IMSyscall.WORDARRAY_CONTAINS.number -> translateSyscallWordarrayContains(site)
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
    loadStringArg(arg2.intNumber, arg2.type, "d1")
    invalidateD0Cache()         // d0 is caller-saved across the subroutine call
    emitLine("bsr  prog8_lib.strcmp")
    emitStoreD0(resultReg, IRDataType.BYTE)
}

// Load a string pointer argument (16 or 32 bits) into the given data register,
// matching the calling convention of strings.compare (full 32 bits address).
private fun AsmGen.loadStringArg(reg: Int, dt: IRDataType, dreg: String) {
    if (dt == IRDataType.LONG) {
        emitLine("move.l  ${regAddr(reg)}, $dreg")
    } else {
        emitLine("move.w  ${regAddr(reg)}, $dreg")
    }
}

private fun AsmGen.translateSyscallWordarrayContains(site: CallSite) {
    val regElem = site.argument(0).intNumber
    val regArr = site.argument(1).intNumber
    val regLen = site.argument(2).intNumber
    val resultReg = site.resultRegister(0).intNumber

    val labelLoop = makeLabel(".wac_loop")
    val labelFound = makeLabel(".wac_found")
    val labelDone = makeLabel(".wac_done")

    emitLoadD0(regElem, IRDataType.WORD)
    emitLine("move.l  ${regAddr(regArr)}, a0")
    emitLine("moveq.l  #0, d1")
    emitLine("move.b  ${regAddrByte(regLen, 0)}, d1")
    emitLine("subq.w  #1, d1")
    emitLine("bmi  $labelDone       ; length was 0 -> not found")
    emitRaw("$labelLoop:")
    emitLine("cmp.w  (a0)+, d0")
    emitLine("beq  $labelFound")
    emitLine("dbra  d1, $labelLoop")
    emitLine("move.b  #0, ${regAddrByte(resultReg, 0)}")
    emitLine("bra  $labelDone")
    emitRaw("$labelFound:")
    emitLine("move.b  #1, ${regAddrByte(resultReg, 0)}")
    emitRaw("$labelDone:")
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
            emitLine("cmp.b  ${regAddr(minReg)}, d0")
            emitLine("$bge  $labelCheckMax")
            emitLoadD0(minReg, IRDataType.BYTE)
            emitLine("bra  $labelDone")
            emitRaw("$labelCheckMax:")
            invalidateD0Cache()             // branch target: d0 still holds valueReg, not minReg
            emitLine("cmp.b  ${regAddr(maxReg)}, d0")
            emitLine("$ble  $labelDone")
            emitLoadD0(maxReg, IRDataType.BYTE)
            emitRaw("$labelDone:")
            invalidateD0Cache()             // branch target: d0 differs per incoming path
            emitStoreD0(resultReg, IRDataType.BYTE)
        }
        IRDataType.WORD -> {
            emitLoadD0(valueReg, IRDataType.WORD)
            emitLine("cmp.w  ${regAddr(minReg)}, d0")
            emitLine("$bge  $labelCheckMax")
            emitLoadD0(minReg, IRDataType.WORD)
            emitLine("bra  $labelDone")
            emitRaw("$labelCheckMax:")
            invalidateD0Cache()             // branch target: d0 still holds valueReg, not minReg
            emitLine("cmp.w  ${regAddr(maxReg)}, d0")
            emitLine("$ble  $labelDone")
            emitLoadD0(maxReg, IRDataType.WORD)
            emitRaw("$labelDone:")
            invalidateD0Cache()             // branch target: d0 differs per incoming path
            emitStoreD0(resultReg, IRDataType.WORD)
        }
        IRDataType.LONG -> {
            emitLoadD0(valueReg, IRDataType.LONG)
            emitLine("cmp.l  ${regAddr(minReg)}, d0")
            emitLine("$bge  $labelCheckMax")
            emitLoadD0(minReg, IRDataType.LONG)
            emitLine("bra  $labelDone")
            emitRaw("$labelCheckMax:")
            invalidateD0Cache()             // branch target: d0 still holds valueReg, not minReg
            emitLine("cmp.l  ${regAddr(maxReg)}, d0")
            emitLine("$ble  $labelDone")
            emitLoadD0(maxReg, IRDataType.LONG)
            emitRaw("$labelDone:")
            invalidateD0Cache()             // branch target: d0 differs per incoming path
            emitStoreD0(resultReg, IRDataType.LONG)
        }
        else -> emitLine("; clamp: unsupported dt $dt")
    }
}
