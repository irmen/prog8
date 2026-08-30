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
    loadStringArg(reg1, args.arguments[0].reg.dt, "d0")
    loadStringArg(reg2, args.arguments[1].reg.dt, "d1")
    emitLine("bsr  prog8_lib.strcmp")
    emitLine("move.b  d0, ${regAddrByte(resultReg, 0)}")
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

    emitLine("move.w  ${regAddr(regElem)}, d0")
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
            emitLine("move.b  ${regAddr(valueReg)}, d0")
            emitLine("cmp.b  ${regAddr(minReg)}, d0")
            emitLine("$bge  $labelCheckMax")
            emitLine("move.b  ${regAddr(minReg)}, d0")
            emitLine("bra  $labelDone")
            emitRaw("$labelCheckMax:")
            emitLine("cmp.b  ${regAddr(maxReg)}, d0")
            emitLine("$ble  $labelDone")
            emitLine("move.b  ${regAddr(maxReg)}, d0")
            emitRaw("$labelDone:")
            emitLine("move.b  d0, ${regAddr(resultReg)}")
        }
        IRDataType.WORD -> {
            emitLine("move.w  ${regAddr(valueReg)}, d0")
            emitLine("cmp.w  ${regAddr(minReg)}, d0")
            emitLine("$bge  $labelCheckMax")
            emitLine("move.w  ${regAddr(minReg)}, d0")
            emitLine("bra  $labelDone")
            emitRaw("$labelCheckMax:")
            emitLine("cmp.w  ${regAddr(maxReg)}, d0")
            emitLine("$ble  $labelDone")
            emitLine("move.w  ${regAddr(maxReg)}, d0")
            emitRaw("$labelDone:")
            emitLine("move.w  d0, ${regAddr(resultReg)}")
        }
        IRDataType.LONG -> {
            emitLine("move.l  ${regAddr(valueReg)}, d0")
            emitLine("cmp.l  ${regAddr(minReg)}, d0")
            emitLine("$bge  $labelCheckMax")
            emitLine("move.l  ${regAddr(minReg)}, d0")
            emitLine("bra  $labelDone")
            emitRaw("$labelCheckMax:")
            emitLine("cmp.l  ${regAddr(maxReg)}, d0")
            emitLine("$ble  $labelDone")
            emitLine("move.l  ${regAddr(maxReg)}, d0")
            emitRaw("$labelDone:")
            emitLine("move.l  d0, ${regAddr(resultReg)}")
        }
        else -> emitLine("; clamp: unsupported dt $dt")
    }
}
