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
        else -> emitLine("; syscall #$num   (unimplemented)")
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
    emitLine("jsr  prog8_lib.strcmp")
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
    emitLine("$labelLoop:")
    emitLine("cmp.w  (a0)+, d0")
    emitLine("beq  $labelFound")
    emitLine("dbra  d1, $labelLoop")
    emitLine("move.b  #0, ${regAddrByte(resultReg, 0)}")
    emitLine("bra  $labelDone")
    emitLine("$labelFound:")
    emitLine("move.b  #1, ${regAddrByte(resultReg, 0)}")
    emitLine("$labelDone:")
}
