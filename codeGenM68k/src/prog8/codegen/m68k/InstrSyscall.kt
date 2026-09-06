/*
 * SYSCALL instruction translations for the M68k code generator.
 *
 * Handles inline expansion of intermediate representation syscall
 * instructions into M68k assembly code.
 */

package prog8.codegen.m68k

import prog8.intermediate.FunctionCallArgs
import prog8.intermediate.IMSyscall
import prog8.intermediate.IRDataType


// === SYSCALL dispatch ===

internal fun AsmGen.translateSyscall(num: Int, args: FunctionCallArgs) {
    when (num) {
        IMSyscall.WORDARRAY_CONTAINS.number -> translateSyscallWordarrayContains(args)
        IMSyscall.COMPARE_STRINGS.number -> translateSyscallStringCompare(args)
        IMSyscall.CLAMP_BYTE.number -> translateSyscallClamp(args, IRDataType.BYTE, true)
        IMSyscall.CLAMP_UBYTE.number -> translateSyscallClamp(args, IRDataType.BYTE, false)
        IMSyscall.CLAMP_WORD.number -> translateSyscallClamp(args, IRDataType.WORD, true)
        IMSyscall.CLAMP_UWORD.number -> translateSyscallClamp(args, IRDataType.WORD, false)
        IMSyscall.CLAMP_LONG.number -> translateSyscallClamp(args, IRDataType.LONG, true)
        else -> TODO("syscall $num on m68k")
    }
}

// Compare two strings by delegating to the library routine prog8_lib.strcmp,
// which already implements an efficient case-sensitive comparison that
// correctly handles 32 bits pointers (the pointer type on M68k targets).
private fun AsmGen.translateSyscallStringCompare(args: FunctionCallArgs) {
    val reg1 = args.arguments[0].reg.registerNum.value
    val reg2 = args.arguments[1].reg.registerNum.value
    val resultReg = args.returns[0].registerNum.value
    emitLoadD0(reg1, args.arguments[0].reg.dt)
    loadStringArg(reg2, args.arguments[1].reg.dt, "d1")
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

private fun AsmGen.translateSyscallWordarrayContains(args: FunctionCallArgs) {
    val regElem = args.arguments[0].reg.registerNum.value
    val regArr = args.arguments[1].reg.registerNum.value
    val regLen = args.arguments[2].reg.registerNum.value
    val resultReg = args.returns[0].registerNum.value

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

private fun AsmGen.translateSyscallClamp(args: FunctionCallArgs, dt: IRDataType, signed: Boolean) {
    val valueReg = args.arguments[0].reg.registerNum.value
    val minReg = args.arguments[1].reg.registerNum.value
    val maxReg = args.arguments[2].reg.registerNum.value
    val resultReg = args.returns[0].registerNum.value

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
