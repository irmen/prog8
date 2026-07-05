/*
 * Control flow IR instruction translations for the M68k code generator.
 *
 * Scc (set on condition) instructions are used to convert status flags
 * into byte values for:
 *   - PUSHST/POPST: save/restore CCR via the stack  (Scc moves CCR to D0)
 *   - CALL return values via Statusflag (Pc/Pz/Pv/Pn -> 0/1 in virtual reg)
 *   - Status flag calling convention: seq/scs/smi/svs to extract flags
 *
 * Handles: JUMP, JUMPI, CALL, CALLI, CALLFAR, CALLFARVB, SYSCALL,
 * RETURN, RETURNR, RETURNI, PUSH, POP, PUSHST, POPST,
 * CLC, SEC, CLI, SEI, ALIGN,
 * byte/word extraction (LSIGB, LSIGW, MSIGB, MSIGW, BSIGB, MIDB),
 * sign extension (EXT, EXTS), and sign (SGN).
 */

package prog8.codegen.m68k

import prog8.code.core.Statusflag
import prog8.code.core.toHex
import prog8.intermediate.FunctionCallArgs
import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction
import prog8.intermediate.Opcode

internal fun AsmGen.translateControl(insn: IRInstruction) {
    val r1 = insn.reg1
    val r2 = insn.reg2
    val imm = insn.immediate
    val addr = insn.address
    val label = insn.labelSymbol

    when (insn.opcode) {
        Opcode.JUMP -> {
            val target = label?.let { fixNameSymbols(it) } ?: addr?.value?.toHex() ?: error("JUMP needs target")
            emitLine("jmp  $target")
        }

        Opcode.JUMPI -> {
            val reg = r1 ?: error("JUMPI needs reg1")
            emitLine("move.l  ${regAddr(reg)}, a0")
            emitLine("jmp  (a0)")
        }

        Opcode.CALL -> {
            val fnLabel = label?.let { fixNameSymbols(it) } ?: addr?.value?.toHex() ?: error("CALL needs label or address")
            val args = insn.fcallArgs
            translateCall(fnLabel, args)
        }

        Opcode.CALLI -> {
            val reg = r1 ?: error("CALLI needs reg1")
            emitLine("move.l  ${regAddr(reg)}, a0")
            emitLine("jsr  (a0)")
        }

        Opcode.CALLFAR -> {
            TODO("CALLFAR (not applicable to M68k)")
        }

        Opcode.CALLFARVB -> {
            TODO("CALLFARVB (not applicable to M68k)")
        }

        Opcode.SYSCALL -> {
            val num = imm ?: 0
            emitLine("; syscall #$num")
            emitLine("trap  #$num")
        }

        Opcode.RETURN -> emitLine("rts")

        Opcode.RETURNR -> {
            val reg = r1 ?: error("RETURNR needs reg1")
            val type = insn.type ?: IRDataType.BYTE
            val s = dtSuffix(type)
            emitLine("move$s  ${regAddr(reg)}, d0")
            emitLine("rts")
        }

        Opcode.RETURNI -> {
            val value = imm ?: 0
            val type = insn.type ?: IRDataType.BYTE
            val s = dtSuffix(type)
            emitLine("move$s  #$value, d0")
            emitLine("rts")
        }

        // === Stack operations ===

        Opcode.PUSH -> {
            val reg = r1 ?: error("PUSH needs reg1")
            val type = insn.type ?: IRDataType.BYTE
            val s = dtSuffix(type)
            emitLine("move$s  ${regAddr(reg)}, -(sp)")
        }

        Opcode.POP -> {
            val reg = r1 ?: error("POP needs reg1")
            val type = insn.type ?: IRDataType.BYTE
            val s = dtSuffix(type)
            emitLine("move$s  (sp)+, ${regAddr(reg)}")
        }

        // === Status flag stack ops via Scc ===

        Opcode.PUSHST -> {
            // Move CCR to D0 (low byte), then push as byte
            emitLine("move  ccr, d0")
            emitLine("move.b  d0, -(sp)")
        }

        Opcode.POPST -> {
            // Pop byte into D0, then restore CCR
            emitLine("move.b  (sp)+, d0")
            emitLine("move  d0, ccr")
        }

        // === Flag manipulation ===

        Opcode.CLC -> emitLine($$"andi  #$fe, ccr")
        Opcode.SEC -> emitLine($$"ori  #$01, ccr")
        Opcode.CLI -> emitLine($$"andi  #$fb, ccr")
        Opcode.SEI -> emitLine($$"ori  #$04, ccr")

        Opcode.ALIGN -> {
            val alignment = imm ?: 2
            emitLine("ALIGN  $alignment")
        }

        // === Byte/word extraction (no Scc needed) ===

        Opcode.LSIGB -> {
            val dstReg = r1 ?: error("LSIGB needs reg1")
            val srcReg = r2 ?: error("LSIGB needs reg2")
            val type = insn.type ?: IRDataType.WORD
            val s = dtSuffix(type)
            emitLine("move$s  ${regAddr(srcReg)}, d0")
            emitLine("move.b  d0, ${regAddr(dstReg)}")
        }

        Opcode.LSIGW -> {
            val dstReg = r1 ?: error("LSIGW needs reg1")
            val srcReg = r2 ?: error("LSIGW needs reg2")
            emitLine("move.l  ${regAddr(srcReg)}, d0")
            emitLine("move.w  d0, ${regAddr(dstReg)}")
        }

        Opcode.MSIGB -> {
            val dstReg = r1 ?: error("MSIGB needs reg1")
            val srcReg = r2 ?: error("MSIGB needs reg2")
            val type = insn.type ?: IRDataType.WORD
            when (type) {
                IRDataType.WORD -> {
                    emitLine("move.w  ${regAddr(srcReg)}, d0")
                    emitLine("lsr.w  #8, d0")
                }
                IRDataType.LONG -> {
                    emitLine("move.l  ${regAddr(srcReg)}, d0")
                    emitLine("lsr.w  #8, d0")
                }
                else -> TODO("MSIGB for ${type.name}")
            }
            emitLine("move.b  d0, ${regAddr(dstReg)}")
        }

        Opcode.MSIGW -> {
            val dstReg = r1 ?: error("MSIGW needs reg1")
            val srcReg = r2 ?: error("MSIGW needs reg2")
            emitLine("move.l  ${regAddr(srcReg)}, d0")
            emitLine("swap  d0")
            emitLine("move.w  d0, ${regAddr(dstReg)}")
        }

        Opcode.BSIGB -> {
            val dstReg = r1 ?: error("BSIGB needs reg1")
            val srcReg = r2 ?: error("BSIGB needs reg2")
            emitLine("move.l  ${regAddr(srcReg)}, d0")
            emitLine("lsr.l  #8, d0")
            emitLine("lsr.l  #8, d0")
            emitLine("move.b  d0, ${regAddr(dstReg)}")
        }

        Opcode.MIDB -> {
            val dstReg = r1 ?: error("MIDB needs reg1")
            val srcReg = r2 ?: error("MIDB needs reg2")
            emitLine("move.l  ${regAddr(srcReg)}, d0")
            emitLine("lsr.l  #8, d0")
            emitLine("move.b  d0, ${regAddr(dstReg)}")
        }

        // === Sign/zero extension ===

        Opcode.EXT -> {
            val dstReg = r1 ?: error("EXT needs reg1")
            val srcReg = r2 ?: error("EXT needs reg2")
            val type = insn.type ?: IRDataType.WORD
            when (type) {
                IRDataType.BYTE -> {
                    // EXT -> byte: just copy
                    emitLine("move.b  ${regAddr(srcReg)}, d0")
                    emitLine("move.b  d0, ${regAddr(dstReg)}")
                }
                IRDataType.WORD -> {
                    // EXT.b -> word: zero-extend byte to word
                    emitLine("move.b  ${regAddr(srcReg)}, d0")
                    emitLine($$"and.w  #$ff, d0")
                    emitLine("move.w  d0, ${regAddr(dstReg)}")
                }
                IRDataType.LONG -> {
                    // EXT.w -> long: zero-extend word to long
                    emitLine("move.w  ${regAddr(srcReg)}, d0")
                    emitLine($$"and.l  #$ffff, d0")
                    emitLine("move.l  d0, ${regAddr(dstReg)}")
                }
                else -> TODO("EXT for ${type.name}")
            }
        }

        Opcode.EXTS -> {
            val dstReg = r1 ?: error("EXTS needs reg1")
            val srcReg = r2 ?: error("EXTS needs reg2")
            val type = insn.type ?: IRDataType.WORD
            when (type) {
                IRDataType.BYTE -> {
                    // sign-extend byte to byte is just a copy
                    emitLine("move.b  ${regAddr(srcReg)}, d0")
                    emitLine("move.b  d0, ${regAddr(dstReg)}")
                }
                IRDataType.WORD -> {
                    emitLine("move.b  ${regAddr(srcReg)}, d0")
                    emitLine("ext.w  d0")
                    emitLine("move.w  d0, ${regAddr(dstReg)}")
                }
                IRDataType.LONG -> {
                    emitLine("move.w  ${regAddr(srcReg)}, d0")
                    emitLine("ext.l  d0")
                    emitLine("move.l  d0, ${regAddr(dstReg)}")
                }
                else -> TODO("EXTS for ${type.name}")
            }
        }

        // === Concatenation ===

        Opcode.CONCAT -> {
            val dstReg = r1 ?: error("CONCAT needs reg1")
            val srcReg2 = r2 ?: error("CONCAT needs reg2")
            val srcReg3 = insn.reg3 ?: error("CONCAT needs reg3")
            val type = insn.type ?: IRDataType.BYTE
            when (type) {
                IRDataType.BYTE -> {
                    // r1 = WORD(r2 as MSB, r3 as LSB)
                    emitLine("move.b  ${regAddr(srcReg2)}, d0")
                    emitLine("lsl.w  #8, d0")
                    emitLine("move.b  ${regAddr(srcReg3)}, d1")
                    emitLine("or.w  d1, d0")
                    emitLine("move.w  d0, ${regAddr(dstReg)}")
                }
                IRDataType.WORD -> {
                    // r1 = LONG(r2 as MSW, r3 as LSW)
                    emitLine("move.w  ${regAddr(srcReg2)}, d0")
                    emitLine("lsl.l  #16, d0")
                    emitLine("move.w  ${regAddr(srcReg3)}, d1")
                    emitLine("or.l  d1, d0")
                    emitLine("move.l  d0, ${regAddr(dstReg)}")
                }
                else -> TODO("CONCAT for ${type.name}")
            }
        }

        // === Math ===

        Opcode.SGN -> {
            val dstReg = r1 ?: error("SGN needs reg1")
            val srcReg = r2 ?: error("SGN needs reg2")
            val type = insn.type ?: IRDataType.BYTE
            val s = dtSuffix(type)
            val zeroLabel = makeLabel("sgn_zero")
            val doneLabel = makeLabel("sgn_done")
            emitLine("move$s  ${regAddr(srcReg)}, d0")
            emitLine("tst$s  d0")
            emitLine("beq  $zeroLabel")
            emitLine("smi  d1")
            emitLine("bmi  $doneLabel")
            emitLine("moveq  #1, d1")
            emitLine("bra  $doneLabel")
            emitLabel(zeroLabel)
            emitLine("moveq  #0, d1")
            emitLabel(doneLabel)
            emitLine("move.b  d1, ${regAddr(dstReg)}")
        }

        // === Floating point operations via 68881/68882 FPU ===

        Opcode.FFROMUB -> {
            val fpDst = insn.fpReg1 ?: error("FFROMUB needs fpReg1")
            val srcReg = r1 ?: error("FFROMUB needs reg1")
            emitLine("move.b  ${regAddr(srcReg)}, d0")
            emitLine("fmove.b  d0, ${fpuRegName(fpDst)}")
        }

        Opcode.FFROMSB -> {
            val fpDst = insn.fpReg1 ?: error("FFROMSB needs fpReg1")
            val srcReg = r1 ?: error("FFROMSB needs reg1")
            emitLine("move.b  ${regAddr(srcReg)}, d0")
            emitLine("extb.l  d0", "68020+ sign-extend byte to long")
            emitLine("fmove.l  d0, ${fpuRegName(fpDst)}")
        }

        Opcode.FFROMUW -> {
            val fpDst = insn.fpReg1 ?: error("FFROMUW needs fpReg1")
            val srcReg = r1 ?: error("FFROMUW needs reg1")
            emitLine("move.w  ${regAddr(srcReg)}, d0")
            emitLine("fmove.w  d0, ${fpuRegName(fpDst)}")
        }

        Opcode.FFROMSW -> {
            val fpDst = insn.fpReg1 ?: error("FFROMSW needs fpReg1")
            val srcReg = r1 ?: error("FFROMSW needs reg1")
            emitLine("move.w  ${regAddr(srcReg)}, d0")
            emitLine("ext.l  d0")
            emitLine("fmove.l  d0, ${fpuRegName(fpDst)}")
        }

        Opcode.FFROMSL -> {
            val fpDst = insn.fpReg1 ?: error("FFROMSL needs fpReg1")
            val srcReg = r1 ?: error("FFROMSL needs reg1")
            emitLine("move.l  ${regAddr(srcReg)}, d0")
            emitLine("fmove.l  d0, ${fpuRegName(fpDst)}")
        }

        Opcode.FTOUB -> {
            val dstReg = r1 ?: error("FTOUB needs reg1")
            val fpSrc = insn.fpReg1 ?: error("FTOUB needs fpReg1")
            emitLine("fmove.b  ${fpuRegName(fpSrc)}, d0")
            emitLine("and.l  #\$ff, d0")
            emitLine("move.b  d0, ${regAddr(dstReg)}")
        }

        Opcode.FTOSB -> {
            val dstReg = r1 ?: error("FTOSB needs reg1")
            val fpSrc = insn.fpReg1 ?: error("FTOSB needs fpReg1")
            emitLine("fmove.b  ${fpuRegName(fpSrc)}, d0")
            emitLine("move.b  d0, ${regAddr(dstReg)}")
        }

        Opcode.FTOUW -> {
            val dstReg = r1 ?: error("FTOUW needs reg1")
            val fpSrc = insn.fpReg1 ?: error("FTOUW needs fpReg1")
            emitLine("fmove.w  ${fpuRegName(fpSrc)}, d0")
            emitLine("move.w  d0, ${regAddr(dstReg)}")
        }

        Opcode.FTOSW -> {
            val dstReg = r1 ?: error("FTOSW needs reg1")
            val fpSrc = insn.fpReg1 ?: error("FTOSW needs fpReg1")
            emitLine("fmove.w  ${fpuRegName(fpSrc)}, d0")
            emitLine("move.w  d0, ${regAddr(dstReg)}")
        }

        Opcode.FTOSL -> {
            val dstReg = r1 ?: error("FTOSL needs reg1")
            val fpSrc = insn.fpReg1 ?: error("FTOSL needs fpReg1")
            emitLine("fmove.l  ${fpuRegName(fpSrc)}, d0")
            emitLine("move.l  d0, ${regAddr(dstReg)}")
        }

        Opcode.FABS -> {
            val dst = insn.fpReg1 ?: error("FABS needs fpReg1")
            val src = insn.fpReg2 ?: error("FABS needs fpReg2")
            emitLine("fabs.d  ${fpuRegName(src)}, ${fpuRegName(dst)}")
        }

        Opcode.FSIN -> {
            val dst = insn.fpReg1 ?: error("FSIN needs fpReg1")
            val src = insn.fpReg2 ?: error("FSIN needs fpReg2")
            emitLine("fsin.d  ${fpuRegName(src)}, ${fpuRegName(dst)}")
        }

        Opcode.FCOS -> {
            val dst = insn.fpReg1 ?: error("FCOS needs fpReg1")
            val src = insn.fpReg2 ?: error("FCOS needs fpReg2")
            emitLine("fcos.d  ${fpuRegName(src)}, ${fpuRegName(dst)}")
        }

        Opcode.FTAN -> {
            val dst = insn.fpReg1 ?: error("FTAN needs fpReg1")
            val src = insn.fpReg2 ?: error("FTAN needs fpReg2")
            emitLine("ftan.d  ${fpuRegName(src)}, ${fpuRegName(dst)}")
        }

        Opcode.FATAN -> {
            val dst = insn.fpReg1 ?: error("FATAN needs fpReg1")
            val src = insn.fpReg2 ?: error("FATAN needs fpReg2")
            emitLine("fatan.d  ${fpuRegName(src)}, ${fpuRegName(dst)}")
        }

        Opcode.FPOW -> {
            val dst = insn.fpReg1 ?: error("FPOW needs fpReg1")
            val src = insn.fpReg2 ?: error("FPOW needs fpReg2")
            emitLine("fpow.d  ${fpuRegName(src)}, ${fpuRegName(dst)}")
        }

        Opcode.FLN -> {
            val dst = insn.fpReg1 ?: error("FLN needs fpReg1")
            val src = insn.fpReg2 ?: error("FLN needs fpReg2")
            emitLine("flogn.d  ${fpuRegName(src)}, ${fpuRegName(dst)}")
        }

        Opcode.FLOG -> {
            val dst = insn.fpReg1 ?: error("FLOG needs fpReg1")
            val src = insn.fpReg2 ?: error("FLOG needs fpReg2")
            emitLine("flog2.d  ${fpuRegName(src)}, ${fpuRegName(dst)}")
        }

        Opcode.FROUND -> {
            val dst = insn.fpReg1 ?: error("FROUND needs fpReg1")
            val src = insn.fpReg2 ?: error("FROUND needs fpReg2")
            emitLine("fround.d  ${fpuRegName(src)}, ${fpuRegName(dst)}")
        }

        Opcode.FFLOOR -> {
            val dst = insn.fpReg1 ?: error("FFLOOR needs fpReg1")
            val src = insn.fpReg2 ?: error("FFLOOR needs fpReg2")
            emitLine("ffloor.d  ${fpuRegName(src)}, ${fpuRegName(dst)}")
        }

        Opcode.FCEIL -> {
            // 68881 has no fceil; implement as: if x == int(x) then x else x > 0 ? int(x)+1 : int(x)
            val dst = insn.fpReg1 ?: error("FCEIL needs fpReg1")
            val src = insn.fpReg2 ?: error("FCEIL needs fpReg2")
            val isIntLabel = makeLabel("fceil_is_int")
            val doneLabel = makeLabel("fceil_done")
            emitLine("fmove.d  ${fpuRegName(src)}, ${fpuRegName(dst)}")
            emitLine("fintrz.d  ${fpuRegName(dst)}, ${fpuRegName(dst)}")  // truncate toward zero
            emitLine("fcmp.d  ${fpuRegName(src)}, ${fpuRegName(dst)}")
            emitLine("fbeq  $isIntLabel")              // if equal, already integer
            emitLine("ftst.d  ${fpuRegName(src)}")
            emitLine("fbgt  +")                         // if >0, need to add 1
            emitLine("bra  $doneLabel")
            emitLine("+  fadd.d  #1.0, ${fpuRegName(dst)}")
            emitLine("bra  $doneLabel")
            emitLabel(isIntLabel)
            // dst already holds the integer (from fintrz)
            emitLabel(doneLabel)
        }

        Opcode.FCOMP -> {
            val dstReg = r1 ?: error("FCOMP needs reg1 (int output)")
            val fr1 = insn.fpReg1 ?: error("FCOMP needs fpReg1")
            val fr2 = insn.fpReg2 ?: error("FCOMP needs fpReg2")
            val eqLabel = makeLabel("fcomp_eq")
            val doneLabel = makeLabel("fcomp_done")
            val gtLabel = makeLabel("fcomp_gt")
            emitLine("fcmp.d  ${fpuRegName(fr2)}, ${fpuRegName(fr1)}")
            emitLine("fbeq  $eqLabel")
            emitLine("fbgt  $gtLabel")
            emitLine("moveq  #-1, d0")
            emitLine("bra  $doneLabel")
            emitLabel(gtLabel)
            emitLine("moveq  #1, d0")
            emitLine("bra  $doneLabel")
            emitLabel(eqLabel)
            emitLine("moveq  #0, d0")
            emitLabel(doneLabel)
            emitLine("move.b  d0, ${regAddr(dstReg)}")
        }

        Opcode.LOADHFACZERO, Opcode.LOADHFACONE, Opcode.STOREHFACZERO, Opcode.STOREHFACONE ->
            error("${insn.opcode} should have been handled by translateLoadStore")

        else -> error("Unknown control opcode: ${insn.opcode}")
    }
}

// === CALL translation with argument handling ===

private fun AsmGen.translateCall(fnLabel: String, args: FunctionCallArgs?) {
    if (args != null) {
        for (arg in args.arguments) {
            translateArgument(arg, fnLabel)
        }
    }

    emitLine("jsr  $fnLabel")

    // Move return values back to virtual registers
    if (args != null) {
        for (ret in args.returns) {
            translateReturnValue(ret)
        }
    }
}

private fun AsmGen.translateArgument(arg: FunctionCallArgs.ArgumentSpec, fnLabel: String? = null) {
    val argReg = arg.reg

    // If the argument has a calling convention slot, load it into that hardware register
    val slot = argReg.callingConventionSlot
    if (slot != null) {
        val hwReg = m68kSlotRegister(slot, argReg.dt)
        if (argReg.dt == IRDataType.FLOAT) {
            emitLine("fmove.d  ${regAddr(argReg.registerNum.value)}, $hwReg")
        } else {
            val s = dtSuffix(argReg.dt)
            emitLine("move$s  ${regAddr(argReg.registerNum.value)}, $hwReg")
        }
    } else {
        // Store to the callee's parameter variable (if this is a named param)
        if (arg.name.isNotEmpty() && fnLabel != null) {
            val paramVarName = "$fnLabel.${arg.name}"
            val target = fixNameSymbols(paramVarName)
            val regVal = regAddr(argReg.registerNum.value)
            when (argReg.dt) {
                IRDataType.BYTE -> emitLine("move.b  $regVal, $target")
                IRDataType.WORD -> {
                    val sv = suffixForVar(IRDataType.WORD, paramVarName)
                    emitLine("move$sv  $regVal, $target")
                }
                IRDataType.LONG -> emitLine("move.l  $regVal, $target")
                IRDataType.FLOAT -> emitLine("fmove.d  $regVal, $target")
            }
        }
    }
}

private fun AsmGen.translateReturnValue(ret: FunctionCallArgs.RegSpec) {
    val retReg = ret.registerNum

    // Handle status flag returns using Scc to convert flags to 0/1
    if (ret.statusflag != null) {
        val s = when (ret.statusflag!!) {
            Statusflag.Pc -> "scs"
            Statusflag.Pz -> "seq"
            Statusflag.Pv -> "svs"
            Statusflag.Pn -> "smi"
        }
        emitLine("$s  d0")
        emitLine("neg.b  d0")
        emitLine("move.b  d0, ${regAddr(retReg.value)}")
        return
    }

    // Otherwise, return value is in a hardware register
    val slot = ret.callingConventionSlot
    if (slot != null) {
        val hwReg = m68kSlotRegister(slot, ret.dt)
        if (ret.dt == IRDataType.FLOAT) {
            emitLine("fmove.d  $hwReg, ${regAddr(retReg.value)}")
        } else {
            val s = dtSuffix(ret.dt)
            emitLine("move$s  $hwReg, ${regAddr(retReg.value)}")
        }
    } else {
        // Return value via stack
        if (ret.dt == IRDataType.FLOAT) {
            emitLine("fmove.d  (sp)+, ${regAddr(retReg.value)}")
        } else {
            val s = dtSuffix(ret.dt)
            emitLine("move$s  (sp)+, ${regAddr(retReg.value)}")
        }
    }
}

// === Slot to M68k hardware register mapping ===

fun m68kSlotRegister(slot: prog8.intermediate.CallingConventionSlot, dt: IRDataType): String = when (slot.value) {
    0, 3, 4 -> "d0"       // slots 0,3,4 all map to D0 (byte/word/32-bit pair)
    1, 5 -> "d1"          // slots 1,5 map to D1
    2 -> "d2"
    6 -> if (dt == IRDataType.FLOAT) "fp0" else "d6"
    7 -> if (dt == IRDataType.FLOAT) "fp1" else "d7"
    else -> error("unknown calling convention slot: $slot")
}
