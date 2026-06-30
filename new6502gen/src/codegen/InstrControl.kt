/*
 * Control flow IR instruction translations for the new6502gen code generator.
 *
 * Handles: JUMP, JUMPI, CALL, CALLI, CALLFAR, CALLFARVB, SYSCALL,
 * RETURN, RETURNR, RETURNI, PUSH, POP, PUSHST, POPST,
 * CLC, SEC, CLI, SEI, ALIGN,
 * byte/word extraction (LSIGB, LSIGW, MSIGB, MSIGW, BSIGB, MIDB),
 * sign extension (EXT, EXTS),
 * and stubs for float math and conversions.
 *
 * CALL instruction translation:
 *   1. Load arguments into the appropriate calling convention slots (s0-s5)
 *   2. JSR to the subroutine
 *   3. Save return values from slots back to virtual registers
 *
 * Argument/return value passing:
 *   - s0 (A, byte), s1 (X, byte), s2 (Y, byte)
 *   - s3 (AX, word), s4 (AY, word), s5 (XY, word)
 *   - Status flags (Pc, Pz, Pv, Pn) for boolean returns
 *
 * Not yet implemented: CALLI, CALLFAR, CALLFARVB, all floating point ops.
 */

package codegen

import prog8.code.core.Statusflag
import prog8.code.core.toHex
import prog8.intermediate.*

fun CodeGenerator.translateControl(insn: IRInstruction) {
    val r1 = insn.reg1
    val r2 = insn.reg2
    val imm = insn.immediate
    val addr = insn.address
    val label = insn.labelSymbol

    when (insn.opcode) {
        Opcode.JUMP -> {
            val target = label ?: addr?.value?.toHex() ?: error("JUMP needs target")
            emitLine("jmp  $target")
        }

        Opcode.JUMPI -> {
            val reg = r1 ?: error("JUMPI needs reg1")
            emitLine("jmp  (${regAddr(reg)})")
        }

        Opcode.CALL -> {
            val fnLabel = label ?: addr?.value?.toHex() ?: error("CALL needs label or address")
            val args = insn.fcallArgs
            translateCall(fnLabel, args)
        }

        Opcode.CALLI -> {
            val reg = r1 ?: error("CALLI needs reg1")
            emitLine("lda  #>((+)-1)")
            emitLine("pha")
            emitLine("lda  #<((+)-1)")
            emitLine("pha")
            emitLine("jmp  (${regAddr(reg)})")
            emitLabel("+")
        }

        Opcode.CALLFAR -> {
            val target = label ?: (addr?.value ?: 0u).toHex()
            val bank = imm ?: 0
            val jsrfar = jsrfarRoutine()
            emitLine("jsr  $jsrfar")
            emitLine(".word  $target")
            emitLine(".byte  $$bank")
        }

        Opcode.CALLFARVB -> {
            val target = label ?: (addr?.value ?: 0u).toHex()
            val bankReg = r1 ?: error("CALLFARVB needs reg1")
            val jsrfar = jsrfarRoutine()
            val patchLabel = makeLabel("callfarvb_patch")
            emitLine("lda  ${regAddrLo(bankReg)}")
            emitLine("sta  ${patchLabel}+2")
            emitLine("jsr  $jsrfar")
            emitLabel(patchLabel)
            emitLine(".word  $target")
            emitLine(".byte  0")
        }

        Opcode.SYSCALL -> {
            val args = insn.fcallArgs
            translateSyscall(insn, args)
        }

        Opcode.RETURN -> {
            emitLine("rts")
        }

        Opcode.RETURNR -> {
            val type = insn.type ?: IRDataType.BYTE
            if (type == IRDataType.FLOAT) {
                val fpReg = insn.fpReg1 ?: error("RETURNR.f needs fpReg1")
                emitLine("lda  #<${fpRegAddr(fpReg.value)}")
                emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
                emitLine("jsr  floats.MOVFM")
            } else {
                val reg = r1 ?: error("RETURNR needs reg1")
                when (type) {
                    IRDataType.BYTE -> {
                        emitLine("lda  ${regAddrLo(reg)}")
                    }
                IRDataType.WORD -> {
                    emitLine("lda  ${regAddrLo(reg)}")
                    emitLine("ldy  ${regAddrHi(reg)}")
                }
                    IRDataType.LONG -> {
                        emitLine("lda  ${regAddrLo(reg)}")
                        emitLine("sta  cx16.r14")
                        emitLine("lda  ${regAddrHi(reg)}")
                        emitLine("sta  cx16.r14+1")
                        emitLine("lda  ${regAddrByte(reg, 2)}")
                        emitLine("sta  cx16.r15")
                        emitLine("lda  ${regAddrByte(reg, 3)}")
                        emitLine("sta  cx16.r15+1")
                    }
                }
            }
            emitLine("rts")
        }

        Opcode.RETURNI -> {
            val type = insn.type ?: IRDataType.BYTE
            if (type == IRDataType.FLOAT) {
                val value = insn.immediateFp ?: error("RETURNI.f needs immediateFp")
                val constLabel = getFloatConstLabel(value)
                emitLine("lda  #<$constLabel")
                emitLine("ldy  #>$constLabel")
                emitLine("jsr  floats.MOVFM")
            } else {
                val value = imm ?: error("RETURNI needs immediate")
                when (type) {
                    IRDataType.BYTE -> {
                        emitLine("lda  #${value and 0xff}")
                    }
                    IRDataType.WORD -> {
                        emitLine("lda  #<${value and 0xffff}")
                        emitLine("ldy  #>${value and 0xffff}")
                    }
                    IRDataType.LONG -> {
                        emitLine("lda  #${value and 0xff}")
                        emitLine("sta  cx16.r14")
                        emitLine("lda  #${(value ushr 8) and 0xff}")
                        emitLine("sta  cx16.r14+1")
                        emitLine("lda  #${(value ushr 16) and 0xff}")
                        emitLine("sta  cx16.r15")
                        emitLine("lda  #${(value ushr 24) and 0xff}")
                        emitLine("sta  cx16.r15+1")
                    }
                }
            }
            emitLine("rts")
        }

        Opcode.PUSH -> {
            val type = insn.type ?: IRDataType.BYTE
            if (type == IRDataType.FLOAT) {
                val fpReg = insn.fpReg1 ?: error("PUSH.f needs fpReg1")
                emitLine("lda  #<${fpRegAddr(fpReg.value)}")
                emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("jsr  floats.pushFAC1")
            } else {
                val reg = r1 ?: error("PUSH needs reg1")
                when (type) {
                    IRDataType.BYTE -> {
                        emitLine("lda  ${regAddrLo(reg)}")
                        emitLine("pha")
                    }
                    IRDataType.WORD -> {
                        emitLine("lda  ${regAddrHi(reg)}")
                        emitLine("pha")
                        emitLine("lda  ${regAddrLo(reg)}")
                        emitLine("pha")
                    }
                    IRDataType.LONG -> {
                        emitLine("lda  ${regAddrByte(reg, 3)}")
                        emitLine("pha")
                        emitLine("lda  ${regAddrByte(reg, 2)}")
                        emitLine("pha")
                        emitLine("lda  ${regAddrHi(reg)}")
                        emitLine("pha")
                        emitLine("lda  ${regAddrLo(reg)}")
                        emitLine("pha")
                    }
                }
            }
        }

        Opcode.POP -> {
            val type = insn.type ?: IRDataType.BYTE
            if (type == IRDataType.FLOAT) {
                val fpReg = insn.fpReg1 ?: error("POP.f needs fpReg1")
                emitLine("clc")
                emitLine("jsr  floats.popFAC")
                emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
                emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
                emitLine("jsr  floats.MOVMF")
            } else {
                val reg = r1 ?: error("POP needs reg1")
                when (type) {
                    IRDataType.BYTE -> {
                        emitLine("pla")
                        emitLine("sta  ${regAddrLo(reg)}")
                    }
                    IRDataType.WORD -> {
                        emitLine("pla")
                        emitLine("sta  ${regAddrLo(reg)}")
                        emitLine("pla")
                        emitLine("sta  ${regAddrHi(reg)}")
                    }
                    IRDataType.LONG -> {
                        emitLine("pla")
                        emitLine("sta  ${regAddrLo(reg)}")
                        emitLine("pla")
                        emitLine("sta  ${regAddrHi(reg)}")
                        emitLine("pla")
                        emitLine("sta  ${regAddrByte(reg, 2)}")
                        emitLine("pla")
                        emitLine("sta  ${regAddrByte(reg, 3)}")
                    }
                }
            }
        }

        Opcode.PUSHST -> {
            emitLine("php")
        }

        Opcode.POPST -> {
            emitLine("plp")
        }

        Opcode.CLC -> emitLine("clc")
        Opcode.SEC -> emitLine("sec")
        Opcode.CLI -> emitLine("cli")
        Opcode.SEI -> emitLine("sei")

        Opcode.ALIGN -> {
            val alignment = imm ?: 256
            emitLine("; ALIGN to $alignment bytes")
        }

        Opcode.LSIGB -> {
            val dest = r1 ?: error("LSIGB needs reg1")
            val src = r2 ?: error("LSIGB needs reg2")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dest)}")
        }

        Opcode.LSIGW -> {
            val dest = r1 ?: error("LSIGW needs reg1")
            val src = r2 ?: error("LSIGW needs reg2")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dest)}")
            emitLine("lda  ${regAddrHi(src)}")
            emitLine("sta  ${regAddrHi(dest)}")
        }

        Opcode.MSIGB -> {
            val dest = r1 ?: error("MSIGB needs reg1")
            val src = r2 ?: error("MSIGB needs reg2")
            val type = insn.type ?: IRDataType.WORD
            when (type) {
                IRDataType.WORD -> {
                    emitLine("lda  ${regAddrHi(src)}")
                    emitLine("sta  ${regAddrLo(dest)}")
                }
                IRDataType.LONG -> {
                    emitLine("lda  ${regAddrByte(src, 3)}")
                    emitLine("sta  ${regAddrLo(dest)}")
                }
                else -> TODO("MSIGB ${type.name}")
            }
        }

        Opcode.MSIGW -> {
            val dest = r1 ?: error("MSIGW needs reg1")
            val src = r2 ?: error("MSIGW needs reg2")
            emitLine("lda  ${regAddrByte(src, 2)}")
            emitLine("sta  ${regAddrLo(dest)}")
            emitLine("lda  ${regAddrByte(src, 3)}")
            emitLine("sta  ${regAddrHi(dest)}")
        }

        Opcode.BSIGB -> {
            val dest = r1 ?: error("BSIGB needs reg1")
            val src = r2 ?: error("BSIGB needs reg2")
            emitLine("lda  ${regAddrByte(src, 2)}")
            emitLine("sta  ${regAddrLo(dest)}")
        }

        Opcode.MIDB -> {
            val dest = r1 ?: error("MIDB needs reg1")
            val src = r2 ?: error("MIDB needs reg2")
            emitLine("lda  ${regAddrHi(src)}")
            emitLine("sta  ${regAddrLo(dest)}")
        }

        Opcode.CONCAT -> {
            val type = insn.type ?: IRDataType.BYTE
            val r1 = r1 ?: error("CONCAT needs reg1")
            val r2 = r2 ?: error("CONCAT needs reg2")
            val r3 = insn.reg3 ?: error("CONCAT needs reg3")
            when (type) {
                IRDataType.BYTE -> {
                    // r1 = WORD(r2 as MSB, r3 as LSB)
                    emitLine("lda  ${regAddrLo(r2)}")
                    emitLine("sta  ${regAddrHi(r1)}")
                    emitLine("lda  ${regAddrLo(r3)}")
                    emitLine("sta  ${regAddrLo(r1)}")
                }
                IRDataType.WORD -> {
                    // r1 = LONG(r2 as MSW, r3 as LSW)
                    // Save r2 (msw) first in case r1 overlaps with r2 or r2==r3
                    emitLine("lda  ${regAddrLo(r2)}")
                    emitLine("sta  $ZP_TEMP")
                    emitLine("lda  ${regAddrHi(r2)}")
                    emitLine("sta  ${ZP_TEMP}+1")
                    // Copy r3 (lsw) to r1+0, r1+1
                    emitLine("lda  ${regAddrLo(r3)}")
                    emitLine("sta  ${regAddrLo(r1)}")
                    emitLine("lda  ${regAddrHi(r3)}")
                    emitLine("sta  ${regAddrHi(r1)}")
                    // Copy saved msw to r1+2, r1+3
                    emitLine("lda  $ZP_TEMP")
                    emitLine("sta  ${regAddrByte(r1, 2)}")
                    emitLine("lda  ${ZP_TEMP}+1")
                    emitLine("sta  ${regAddrByte(r1, 3)}")
                }
                else -> TODO("CONCAT ${type.name}")
            }
        }

        Opcode.EXT -> {
            val reg = r1 ?: error("EXT needs reg1")
            val srcReg = r2 ?: error("EXT needs reg2")
            if (reg != srcReg) {
                emitLine("lda  ${regAddrLo(srcReg)}")
                emitLine("sta  ${regAddrLo(reg)}")
            }
            emitStoreZero(regAddrHi(reg))
        }

        Opcode.EXTS -> {
            val dest = r1 ?: error("EXTS needs reg1")
            val src = r2 ?: error("EXTS needs reg2")
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dest)}")
            emitLine("and  #128")
            emitLine("beq  +")
            emitLine("lda  #255")
            emitLine("sta  ${regAddrHi(dest)}")
            emitLine("jmp  ++")
            emitLabel("+")
            emitStoreZero(regAddrHi(dest))
            emitLabel("+")
        }

        Opcode.SQRT -> {
            if (insn.type == IRDataType.FLOAT)
                translateFloatUnary(insn, "floats.SQR")
            else
                TODO("SQRT (integer)")
        }
        Opcode.SQUARE -> {
            if (insn.type == IRDataType.FLOAT)
                translateFloatSquare(insn)
            else
                TODO("SQUARE (integer)")
        }
        Opcode.SGN -> {
            if (insn.type == IRDataType.FLOAT)
                translateFloatSign(insn)
            else
                TODO("SGN (integer)")
        }

        Opcode.FFROMUB, Opcode.FFROMSB, Opcode.FFROMUW, Opcode.FFROMSW -> translateFloatFromInt(insn)
        Opcode.FFROMSL -> translateFloatFromSignedLong(insn)

        Opcode.FTOUB, Opcode.FTOSB, Opcode.FTOUW, Opcode.FTOSW -> translateFloatToInt(insn)
        Opcode.FTOSL -> translateFloatToSignedLong(insn)

        Opcode.FABS -> translateFloatUnary(insn, "floats.ABS")
        Opcode.FSIN -> translateFloatUnary(insn, "floats.SIN")
        Opcode.FCOS -> translateFloatUnary(insn, "floats.COS")
        Opcode.FTAN -> translateFloatUnary(insn, "floats.TAN")
        Opcode.FATAN -> translateFloatUnary(insn, "floats.ATN")
        Opcode.FPOW -> translateFloatPower(insn)
        Opcode.FLN -> translateFloatUnary(insn, "floats.LOG")
        Opcode.FLOG -> translateFloatUnary(insn, "floats.LOG")
        Opcode.FROUND -> translateFloatUnary(insn, "floats.ROUND")
        Opcode.FFLOOR -> translateFloatUnary(insn, "floats.INT")
        Opcode.FCEIL -> translateFloatCeil(insn)
        Opcode.FCOMP -> translateFloatCompare(insn)

        else -> error("Unknown control opcode: ${insn.opcode}")
    }
}

// === Call handling ===

private fun CodeGenerator.translateCall(fnLabel: String, args: FunctionCallArgs?) {
    // Check if this is an inline ASMSUB - must be inlined at call site, not called with jsr
    val inlineAsmSub = findInlineAsmSub(fnLabel)
    if (inlineAsmSub != null) {
        // Inline the assembly body directly at the call site (no jsr, no rts)
        if (args != null) {
            // Process non-slot arguments first
            for ((index, arg) in args.arguments.withIndex()) {
                if (arg.reg.callingConventionSlot == null)
                    translateArgument(arg, index, fnLabel)
            }
            // Process slot arguments
            val slotArgs = args.arguments.withIndex().filter { it.value.reg.callingConventionSlot != null }
            val orderedSlotArgs = slotArgs.sortedWith(compareBy<IndexedValue<FunctionCallArgs.ArgumentSpec>> {
                val slot = it.value.reg.callingConventionSlot!!.value
                when (slot) {
                    3, 4, 5 -> 0
                    2 -> 1
                    1 -> 2
                    0 -> 3
                    6, 7 -> 4
                    else -> 5
                }
            }.thenByDescending { -it.index })
            for ((index, arg) in orderedSlotArgs) {
                translateArgument(arg, index, fnLabel)
            }
        }
        emitRaw("    ; inlined: $fnLabel")
        inlineAsmSub.asmChunk.assembly.lineSequence().forEach { line ->
            if (line.isNotBlank()) emitRaw("    $line")
        }
        emitRaw("    ; end inlined: $fnLabel")
        if (args != null) {
            for (ret in args.returns) {
                translateReturnValue(ret)
            }
        }
        return
    }

    if (args != null) {
        // Process non-slot arguments first (they use A as temp to store to memory/registers)
        for ((index, arg) in args.arguments.withIndex()) {
            if (arg.reg.callingConventionSlot == null)
                translateArgument(arg, index, fnLabel)
        }
        // Process slot arguments in optimal order to avoid register clobbering.
        // Order matches old 6502 codegen: paired regs first (AX/AY/XY), then single regs (Y, X, A),
        // then float regs, then status flags. This ensures that loading a paired register
        // (which clobbers two hardware regs) doesn't overwrite a value needed for a later argument.
        val slotArgs = args.arguments.withIndex().filter { it.value.reg.callingConventionSlot != null }
        val orderedSlotArgs = slotArgs.sortedWith(compareBy<IndexedValue<FunctionCallArgs.ArgumentSpec>> {
            val slot = it.value.reg.callingConventionSlot!!.value
            when (slot) {
                3, 4, 5 -> 0  // paired CPU regs (AX, AY, XY) - load first
                2 -> 1        // Y - load before X and A
                1 -> 2        // X - load before A
                0 -> 3        // A - load last (most commonly clobbered)
                6, 7 -> 4     // float regs (FAC1, FAC2)
                else -> 5     // status flags
            }
        }.thenByDescending {
            // Within each group, process in original index order (stable sort)
            -it.index
        })
        for ((index, arg) in orderedSlotArgs) {
            translateArgument(arg, index, fnLabel)
        }
    }

    emitLine("jsr  $fnLabel")

    if (args != null) {
        for (ret in args.returns) {
            translateReturnValue(ret)
        }
    }
}

/** Find an inline ASMSUB by its full scoped label. Returns null if not found or not inline. */
private fun CodeGenerator.findInlineAsmSub(label: String): IRAsmSubroutine? =
    program.findInlineAsmSub(label)

/** Find an inline ASMSUB by its full scoped label in the program. */
fun IRProgram.findInlineAsmSub(label: String): IRAsmSubroutine? {
    for (block in blocks) {
        for (element in block.children) {
            if (element is IRAsmSubroutine && element.label == label && element.isInline)
                return element
        }
    }
    return null
}

private fun CodeGenerator.translateArgument(arg: FunctionCallArgs.ArgumentSpec, argIndex: Int = -1, fnLabel: String? = null) {
    val regSpec = arg.reg
    val slot = regSpec.callingConventionSlot
    val regNum = regSpec.registerNum.value

    when (slot?.value) {
        0 -> {
            emitLine("lda  ${regAddrLo(regNum)}")
        }
        1 -> {
            emitLine("ldx  ${regAddrLo(regNum)}")
        }
        2 -> {
            emitLine("ldy  ${regAddrLo(regNum)}")
        }
        3 -> {
            emitLine("lda  ${regAddrLo(regNum)}")
            emitLine("ldx  ${regAddrHi(regNum)}")
        }
        4 -> {
            emitLine("lda  ${regAddrLo(regNum)}")
            emitLine("ldy  ${regAddrHi(regNum)}")
        }
        5 -> {
            emitLine("ldx  ${regAddrLo(regNum)}")
            emitLine("ldy  ${regAddrHi(regNum)}")
        }
        6 -> {
            // slot s6 = FAC1: load fp register into FAC1
            emitLine("lda  #<${fpRegAddr(regNum)}")
            emitLine("ldy  #>${fpRegAddr(regNum)}")
            emitLine("jsr  floats.MOVFM")
        }
        7 -> {
            // slot s7 = FAC2: load fp register into FAC2 via MOVFM + MOVAF
            emitLine("lda  #<${fpRegAddr(regNum)}")
            emitLine("ldy  #>${fpRegAddr(regNum)}")
            emitLine("jsr  floats.MOVFM")
            emitLine("jsr  floats.MOVAF")
        }
        null -> {
            val flag = regSpec.statusflag
            if (flag != null) {
                // Status flag argument - load value and set the appropriate flag
                when (flag) {
                    Statusflag.Pc -> {
                        emitLine("lda  ${regAddrLo(regNum)}")
                        emitLine("cmp  #0")
                        emitLine("beq  +")
                        emitLine("sec")
                        emitLabel("+")
                    }
                    Statusflag.Pv -> TODO("status flag Pv for argument")
                    else -> TODO("status flag $flag")
                }
                return
            }
                    val address = arg.address
                    if (address != null) {
                        when (regSpec.dt) {
                            IRDataType.BYTE -> {
                                emitLine("lda  ${regAddrLo(regNum)}")
                                emitLine("sta  ${address.toHex()}")
                            }
                            IRDataType.WORD -> {
                                emitLine("lda  ${regAddrLo(regNum)}")
                                emitLine("sta  ${address.toHex()}")
                                emitLine("lda  ${regAddrHi(regNum)}")
                                emitLine("sta  ${address.toHex()}+1")
                            }
                            IRDataType.LONG -> {
                                emitLine("lda  ${regAddrLo(regNum)}")
                                emitLine("sta  ${address.toHex()}")
                                emitLine("lda  ${regAddrHi(regNum)}")
                                emitLine("sta  ${address.toHex()}+1")
                                emitLine("lda  ${regAddrByte(regNum, 2)}")
                                emitLine("sta  ${address.toHex()}+2")
                                emitLine("lda  ${regAddrByte(regNum, 3)}")
                                emitLine("sta  ${address.toHex()}+3")
                            }
                            IRDataType.FLOAT -> {
                                emitLine("lda  #<${fpRegAddr(regNum)}")
                                emitLine("ldy  #>${fpRegAddr(regNum)}")
                                emitLine("jsr  floats.MOVFM")
                                emitLine("ldx  #<${address.toHex()}")
                                emitLine("ldy  #>${address.toHex()}")
                                emitLine("jsr  floats.MOVMF")
                            }
                        }
                    } else {
                val name = arg.name
                if (name.isNotEmpty()) {
                    // Check if this argument maps to an asmsub's cx16 virtual register parameter
                    val asmTarget = if (fnLabel != null && argIndex >= 0)
                        this.asmSubParamTarget(fnLabel, argIndex) else null
                    if (asmTarget != null) {
                        when (regSpec.dt) {
                            IRDataType.BYTE -> {
                                emitLine("lda  ${regAddrLo(regNum)}")
                                emitLine("sta  $asmTarget")
                            }
                            IRDataType.WORD -> {
                                emitLine("lda  ${regAddrLo(regNum)}")
                                emitLine("sta  $asmTarget")
                                emitLine("lda  ${regAddrHi(regNum)}")
                                emitLine("sta  ${asmTarget}+1")
                            }
                            IRDataType.LONG -> {
                                emitLine("lda  ${regAddrLo(regNum)}")
                                emitLine("sta  $asmTarget")
                                emitLine("lda  ${regAddrHi(regNum)}")
                                emitLine("sta  ${asmTarget}+1")
                                emitLine("lda  ${regAddrByte(regNum, 2)}")
                                emitLine("sta  ${asmTarget}+2")
                                emitLine("lda  ${regAddrByte(regNum, 3)}")
                                emitLine("sta  ${asmTarget}+3")
                            }
                            IRDataType.FLOAT -> {
                                emitLine("lda  #<${fpRegAddr(regNum)}")
                                emitLine("ldy  #>${fpRegAddr(regNum)}")
                                emitLine("jsr  floats.MOVFM")
                                emitLine("ldx  #<$asmTarget")
                                emitLine("ldy  #>$asmTarget")
                                emitLine("jsr  floats.MOVMF")
                            }
                        }
                    } else if (name == "x") {
                        // X register parameter for extsub calls
                        when (regSpec.dt) {
                            IRDataType.BYTE -> {
                                emitLine("ldx  ${regAddrLo(regNum)}")
                            }
                            IRDataType.WORD -> {
                                emitLine("ldx  ${regAddrLo(regNum)}")
                                // high byte ignored for single register
                            }
                            else -> TODO("LONG/FLOAT X register arg")
                        }
                    } else if (name == "y") {
                        // Y register parameter for extsub calls
                        when (regSpec.dt) {
                            IRDataType.BYTE -> {
                                emitLine("ldy  ${regAddrLo(regNum)}")
                            }
                            IRDataType.WORD -> {
                                emitLine("ldy  ${regAddrLo(regNum)}")
                            }
                            else -> TODO("LONG/FLOAT Y register arg")
                        }
                    } else if (name == "a") {
                        // A register parameter for extsub calls
                        when (regSpec.dt) {
                            IRDataType.BYTE -> {
                                emitLine("lda  ${regAddrLo(regNum)}")
                            }
                            IRDataType.WORD -> {
                                emitLine("lda  ${regAddrLo(regNum)}")
                            }
                            else -> TODO("LONG/FLOAT A register arg")
                        }
                    } else {
                        // Named parameter - store to the resolved symbol
                        // CX16 virtual register names (cx16.r0, etc.) are used directly
                        // as the target; other named params are resolved relative to the function label.
                        val target = if (name.startsWith("cx16."))
                            resolveSymbolRef(name)
                        else
                            resolveSymbolRef(if (fnLabel != null) "$fnLabel.$name" else name)
                        when (regSpec.dt) {
                            IRDataType.BYTE -> {
                                emitLine("lda  ${regAddrLo(regNum)}")
                                emitLine("sta  $target")
                            }
                            IRDataType.WORD -> {
                                emitLine("lda  ${regAddrLo(regNum)}")
                                emitLine("sta  $target")
                                emitLine("lda  ${regAddrHi(regNum)}")
                                emitLine("sta  ${target}+1")
                            }
                            IRDataType.LONG -> {
                                emitLine("lda  ${regAddrLo(regNum)}")
                                emitLine("sta  $target")
                                emitLine("lda  ${regAddrHi(regNum)}")
                                emitLine("sta  ${target}+1")
                                emitLine("lda  ${regAddrByte(regNum, 2)}")
                                emitLine("sta  ${target}+2")
                                emitLine("lda  ${regAddrByte(regNum, 3)}")
                                emitLine("sta  ${target}+3")
                            }
                            IRDataType.FLOAT -> {
                                emitLine("lda  #<${fpRegAddr(regNum)}")
                                emitLine("ldy  #>${fpRegAddr(regNum)}")
                                emitLine("jsr  floats.MOVFM")
                                emitLine("ldx  #<$target")
                                emitLine("ldy  #>$target")
                                emitLine("jsr  floats.MOVMF")
                            }
                        }
                    }
                } else {
                    // Syscall argument - value is already in the register file,
                    // the syscall handler reads it from there.
                }
            }
        }
    }
}

private fun CodeGenerator.translateReturnValue(ret: FunctionCallArgs.RegSpec) {
    val slot = ret.callingConventionSlot
    val regNum = ret.registerNum.value

    when (slot?.value) {
        0 -> {
            emitLine("sta  ${regAddrLo(regNum)}")
        }
        1 -> {
            emitLine("stx  ${regAddrLo(regNum)}")
        }
        2 -> {
            emitLine("sty  ${regAddrLo(regNum)}")
        }
        3 -> {
            emitLine("sta  ${regAddrLo(regNum)}")
            emitLine("stx  ${regAddrHi(regNum)}")
        }
        4 -> {
            emitLine("sta  ${regAddrLo(regNum)}")
            emitLine("sty  ${regAddrHi(regNum)}")
        }
        5 -> {
            emitLine("stx  ${regAddrLo(regNum)}")
            emitLine("sty  ${regAddrHi(regNum)}")
        }
        6 -> {
            // slot s6 = FAC1: store FAC1 to fp register
            emitLine("ldx  #<${fpRegAddr(regNum)}")
            emitLine("ldy  #>${fpRegAddr(regNum)}")
            emitLine("jsr  floats.MOVMF")
        }
        7 -> {
            // slot s7 = FAC2: copy FAC2 to FAC1 first, then store
            emitLine("jsr  floats.MOVFA")
            emitLine("ldx  #<${fpRegAddr(regNum)}")
            emitLine("ldy  #>${fpRegAddr(regNum)}")
            emitLine("jsr  floats.MOVMF")
        }
        null -> {
            val flag = ret.statusflag
            if (flag != null) {
                val mask = when (flag) {
                    Statusflag.Pc -> 1
                    Statusflag.Pz -> 2
                    Statusflag.Pv -> 64
                    Statusflag.Pn -> 128
                }
                emitLine("php")
                emitLine("pla")
                emitLine("and  #$mask")
                emitLine("beq  +")
                emitLine("lda  #1")
                emitLabel("+")
                emitLine("sta  ${regAddrLo(regNum)}")
                emitStoreZero(regAddrHi(regNum))
            } else if (regNum >= 0) {
                when (ret.dt) {
                    IRDataType.BYTE -> {
                        emitLine("sta  ${regAddrLo(regNum)}")
                    }
                    IRDataType.WORD -> {
                        emitLine("sta  ${regAddrLo(regNum)}")
                        emitLine("sty  ${regAddrHi(regNum)}")
                    }
                    IRDataType.LONG -> {
                        emitLine("lda  cx16.r14")
                        emitLine("sta  ${regAddrLo(regNum)}")
                        emitLine("lda  cx16.r14+1")
                        emitLine("sta  ${regAddrHi(regNum)}")
                        emitLine("lda  cx16.r15")
                        emitLine("sta  ${regAddrLo(regNum + 1)}")
                        emitLine("lda  cx16.r15+1")
                        emitLine("sta  ${regAddrHi(regNum + 1)}")
                    }
                    IRDataType.FLOAT -> {
                        emitLine("ldx  #<${fpRegAddr(regNum)}")
                        emitLine("ldy  #>${fpRegAddr(regNum)}")
                        emitLine("jsr  floats.MOVMF")
                    }
                }
            } else {
                emitLine("; return value to r$regNum (slot/flag not set)")
            }
        }
    }
}

// === Syscall handling ===

private fun CodeGenerator.translateSyscall(insn: IRInstruction, args: FunctionCallArgs?) {
    val syscallNum = insn.immediate ?: error("SYSCALL must have immediate(syscall number)")
    val argsNonNull = args ?: error("SYSCALL $syscallNum requires arguments")
    when (syscallNum) {
        IMSyscall.CLAMP_UBYTE.number -> translateSyscallClampUbyte(argsNonNull)
        IMSyscall.CLAMP_BYTE.number -> translateSyscallClampByte(argsNonNull)
        IMSyscall.CLAMP_UWORD.number -> translateSyscallClampUword(argsNonNull)
        IMSyscall.CLAMP_WORD.number -> translateSyscallClampWord(argsNonNull)
        IMSyscall.CLAMP_LONG.number -> translateSyscallClampLong(argsNonNull)
        IMSyscall.COMPARE_STRINGS.number -> translateSyscallStringCompare(argsNonNull)
        IMSyscall.STRING_CONTAINS.number -> translateSyscallStringContains(argsNonNull)
        IMSyscall.BYTEARRAY_CONTAINS.number -> translateSyscallBytearrayContains(argsNonNull)
        IMSyscall.WORDARRAY_CONTAINS.number -> translateSyscallWordarrayContains(argsNonNull)
        IMSyscall.SPLIT_WORDARRAY_CONTAINS.number -> translateSyscallSplitWordarrayContains(argsNonNull)
        IMSyscall.LONGARRAY_CONTAINS.number -> translateSyscallLongarrayContains(argsNonNull)
        IMSyscall.FLOATARRAY_CONTAINS.number -> translateSyscallFloatarrayContains(argsNonNull)
        IMSyscall.CALLFAR.number -> translateSyscallCallfar(argsNonNull)
        IMSyscall.CALLFAR2.number -> translateSyscallCallfar2(argsNonNull)
        IMSyscall.MEMCOPY.number -> translateSyscallMemcopy(argsNonNull)
        else -> TODO("unknown SYSCALL number $syscallNum")
    }
    for (ret in argsNonNull.returns) {
        translateReturnValue(ret)
    }
}

private fun CodeGenerator.translateSyscallClampUbyte(args: FunctionCallArgs) {
    val regValue = args.arguments.getOrNull(0)?.reg?.registerNum?.value ?: error("need value reg")
    val regMin = args.arguments.getOrNull(1)?.reg?.registerNum?.value ?: error("need min reg")
    val regMax = args.arguments.getOrNull(2)?.reg?.registerNum?.value ?: error("need max reg")
    emitLine("lda  ${regAddrLo(regMin)}")
    emitLine("sta  P8ZP_SCRATCH_W1")
    emitLine("lda  ${regAddrLo(regMax)}")
    emitLine("sta  P8ZP_SCRATCH_W1+1")
    emitLine("lda  ${regAddrLo(regValue)}")
    emitLine("jsr  prog8_lib.func_clamp_ubyte")
    emitLine("sta  ${regAddrLo(regValue)}")
}

private fun CodeGenerator.translateSyscallClampByte(args: FunctionCallArgs) {
    val regValue = args.arguments.getOrNull(0)?.reg?.registerNum?.value ?: error("need value reg")
    val regMin = args.arguments.getOrNull(1)?.reg?.registerNum?.value ?: error("need min reg")
    val regMax = args.arguments.getOrNull(2)?.reg?.registerNum?.value ?: error("need max reg")
    emitLine("lda  ${regAddrLo(regMin)}")
    emitLine("sta  P8ZP_SCRATCH_W1")
    emitLine("lda  ${regAddrLo(regMax)}")
    emitLine("sta  P8ZP_SCRATCH_W1+1")
    emitLine("lda  ${regAddrLo(regValue)}")
    emitLine("jsr  prog8_lib.func_clamp_byte")
    emitLine("sta  ${regAddrLo(regValue)}")
}

private fun CodeGenerator.translateSyscallClampUword(args: FunctionCallArgs) {
    val regValue = args.arguments.getOrNull(0)?.reg?.registerNum?.value ?: error("need value reg")
    val regMin = args.arguments.getOrNull(1)?.reg?.registerNum?.value ?: error("need min reg")
    val regMax = args.arguments.getOrNull(2)?.reg?.registerNum?.value ?: error("need max reg")
    // min in P8ZP_SCRATCH_W1, max in P8ZP_SCRATCH_W2, value in AY, result in AY
    emitLine("lda  ${regAddrLo(regMin)}")
    emitLine("sta  P8ZP_SCRATCH_W1")
    emitLine("lda  ${regAddrHi(regMin)}")
    emitLine("sta  P8ZP_SCRATCH_W1+1")
    emitLine("lda  ${regAddrLo(regMax)}")
    emitLine("sta  P8ZP_SCRATCH_W2")
    emitLine("lda  ${regAddrHi(regMax)}")
    emitLine("sta  P8ZP_SCRATCH_W2+1")
    emitLine("lda  ${regAddrLo(regValue)}")
    emitLine("ldy  ${regAddrHi(regValue)}")
    emitLine("jsr  prog8_lib.func_clamp_uword")
    emitLine("sta  ${regAddrLo(regValue)}")
    emitLine("sty  ${regAddrHi(regValue)}")
}

private fun CodeGenerator.translateSyscallClampWord(args: FunctionCallArgs) {
    val regValue = args.arguments.getOrNull(0)?.reg?.registerNum?.value ?: error("need value reg")
    val regMin = args.arguments.getOrNull(1)?.reg?.registerNum?.value ?: error("need min reg")
    val regMax = args.arguments.getOrNull(2)?.reg?.registerNum?.value ?: error("need max reg")
    emitLine("lda  ${regAddrLo(regMin)}")
    emitLine("sta  P8ZP_SCRATCH_W1")
    emitLine("lda  ${regAddrHi(regMin)}")
    emitLine("sta  P8ZP_SCRATCH_W1+1")
    emitLine("lda  ${regAddrLo(regMax)}")
    emitLine("sta  P8ZP_SCRATCH_W2")
    emitLine("lda  ${regAddrHi(regMax)}")
    emitLine("sta  P8ZP_SCRATCH_W2+1")
    emitLine("lda  ${regAddrLo(regValue)}")
    emitLine("ldy  ${regAddrHi(regValue)}")
    emitLine("jsr  prog8_lib.func_clamp_word")
    emitLine("sta  ${regAddrLo(regValue)}")
    emitLine("sty  ${regAddrHi(regValue)}")
}

private fun CodeGenerator.translateSyscallClampLong(args: FunctionCallArgs) {
    val regValue = args.arguments.getOrNull(0)?.reg?.registerNum?.value ?: error("need value reg")
    val regMin = args.arguments.getOrNull(1)?.reg?.registerNum?.value ?: error("need min reg")
    val regMax = args.arguments.getOrNull(2)?.reg?.registerNum?.value ?: error("need max reg")
    // value in R14:R15, min in R10:R11, max in R12:R13, result in R14:R15
    emitLine("lda  ${regAddrLo(regMin)}")
    emitLine("sta  cx16.r10L")
    emitLine("lda  ${regAddrHi(regMin)}")
    emitLine("sta  cx16.r10H")
    emitLine("lda  ${regAddrByte(regMin, 2)}")
    emitLine("sta  cx16.r11L")
    emitLine("lda  ${regAddrByte(regMin, 3)}")
    emitLine("sta  cx16.r11H")
    emitLine("lda  ${regAddrLo(regMax)}")
    emitLine("sta  cx16.r12L")
    emitLine("lda  ${regAddrHi(regMax)}")
    emitLine("sta  cx16.r12H")
    emitLine("lda  ${regAddrByte(regMax, 2)}")
    emitLine("sta  cx16.r13L")
    emitLine("lda  ${regAddrByte(regMax, 3)}")
    emitLine("sta  cx16.r13H")
    emitLine("lda  ${regAddrLo(regValue)}")
    emitLine("sta  cx16.r14L")
    emitLine("lda  ${regAddrHi(regValue)}")
    emitLine("sta  cx16.r14H")
    emitLine("lda  ${regAddrByte(regValue, 2)}")
    emitLine("sta  cx16.r15L")
    emitLine("lda  ${regAddrByte(regValue, 3)}")
    emitLine("sta  cx16.r15H")
    emitLine("jsr  prog8_lib.func_clamp_long")
}

private fun CodeGenerator.translateSyscallMemcopy(args: FunctionCallArgs) {
    val regSrc = args.arguments.getOrNull(0)?.reg?.registerNum?.value ?: error("need src reg")
    val regDst = args.arguments.getOrNull(1)?.reg?.registerNum?.value ?: error("need dst reg")
    val regCount = args.arguments.getOrNull(2)?.reg?.registerNum?.value ?: error("need count reg")
    // use existing library routine: memcopy_small
    // P8ZP_SCRATCH_W1 = source, P8ZP_SCRATCH_W2 = dest, Y = count (0 = 256)
    emitLine("lda  ${regAddrLo(regSrc)}")
    emitLine("sta  P8ZP_SCRATCH_W1")
    emitLine("lda  ${regAddrHi(regSrc)}")
    emitLine("sta  P8ZP_SCRATCH_W1+1")
    emitLine("lda  ${regAddrLo(regDst)}")
    emitLine("sta  P8ZP_SCRATCH_W2")
    emitLine("lda  ${regAddrHi(regDst)}")
    emitLine("sta  P8ZP_SCRATCH_W2+1")
    // check if count == 0
    emitLine("lda  ${regAddrLo(regCount)}")
    emitLine("ora  ${regAddrHi(regCount)}")
    emitLine("beq  +")
    emitLine("ldy  ${regAddrLo(regCount)}")
    emitLine("jsr  prog8_lib.memcopy_small")
    emitLabel("+")
}

private fun CodeGenerator.translateSyscallCallfar(args: FunctionCallArgs) {
    val regBank = args.arguments[0].reg.registerNum.value
    val regAddress = args.arguments[1].reg.registerNum.value
    val regArg = args.arguments[2].reg.registerNum.value
    val jsrfar = jsrfarRoutine()
    val label = makeLabel("callfar_patch")
    emitLine("lda  ${regAddrLo(regBank)}")
    emitLine("sta  ${label}+2")
    emitLine("lda  ${regAddrLo(regAddress)}")
    emitLine("sta  ${label}+0")
    emitLine("lda  ${regAddrHi(regAddress)}")
    emitLine("sta  ${label}+1")
    emitLine("lda  ${regAddrLo(regArg)}")
    emitLine("ldy  ${regAddrHi(regArg)}")
    emitLine("jsr  $jsrfar")
    emitLabel(label)
    emitLine(".word  0")
    emitLine(".byte  0")
}

private fun CodeGenerator.translateSyscallCallfar2(args: FunctionCallArgs) {
    val regBank = args.arguments[0].reg.registerNum.value
    val regAddress = args.arguments[1].reg.registerNum.value
    val regA = args.arguments[2].reg.registerNum.value
    val regX = args.arguments[3].reg.registerNum.value
    val regY = args.arguments[4].reg.registerNum.value
    val regCarry = args.arguments[5].reg.registerNum.value
    val jsrfar = jsrfarRoutine()
    val label = makeLabel("callfar2_patch")
    emitLine("lda  ${regAddrLo(regBank)}")
    emitLine("sta  ${label}+2")
    emitLine("lda  ${regAddrLo(regAddress)}")
    emitLine("sta  ${label}+0")
    emitLine("lda  ${regAddrHi(regAddress)}")
    emitLine("sta  ${label}+1")
    emitLine("ldx  ${regAddrLo(regX)}")
    emitLine("ldy  ${regAddrLo(regY)}")
    emitLine("lda  ${regAddrLo(regA)}")
    emitLine("pha")
    emitLine("lda  ${regAddrLo(regCarry)}")
    emitLine("beq  +")
    emitLine("pla")
    emitLine("sec")
    emitLine("jmp  ++")
    emitLabel("+")
    emitLine("pla")
    emitLine("clc")
    emitLabel("+")
    emitLine("jsr  $jsrfar")
    emitLabel(label)
    emitLine(".word  0")
    emitLine(".byte  0")
}

private fun CodeGenerator.translateSyscallStringCompare(args: FunctionCallArgs) {
    val regStr1 = args.arguments[0].reg.registerNum.value
    val regStr2 = args.arguments[1].reg.registerNum.value
    emitLine("lda  ${regAddrLo(regStr2)}")
    emitLine("sta  P8ZP_SCRATCH_W2")
    emitLine("lda  ${regAddrHi(regStr2)}")
    emitLine("sta  P8ZP_SCRATCH_W2+1")
    emitLine("lda  ${regAddrLo(regStr1)}")
    emitLine("ldy  ${regAddrHi(regStr1)}")
    emitLine("jsr  prog8_lib.strcmp_mem")
}

private fun CodeGenerator.translateSyscallBytearrayContains(args: FunctionCallArgs) {
    val regElem = args.arguments[0].reg.registerNum.value
    val regArr = args.arguments[1].reg.registerNum.value
    val regLen = args.arguments[2].reg.registerNum.value
    emitLine("lda  ${regAddrLo(regLen)}")
    emitLine("sta  P8ZP_SCRATCH_W2")
    emitLine("lda  ${regAddrLo(regArr)}")
    emitLine("ldy  ${regAddrHi(regArr)}")
    emitLine("sta  P8ZP_SCRATCH_W1")
    emitLine("sty  P8ZP_SCRATCH_W1+1")
    emitLine("lda  ${regAddrLo(regElem)}")
    emitLine("ldy  P8ZP_SCRATCH_W2")
    emitLine("jsr  prog8_lib.containment_bytearray")
}

private fun CodeGenerator.translateSyscallWordarrayContains(args: FunctionCallArgs) {
    val regElem = args.arguments[0].reg.registerNum.value
    val regArr = args.arguments[1].reg.registerNum.value
    val regLen = args.arguments[2].reg.registerNum.value
    emitLine("lda  ${regAddrLo(regArr)}")
    emitLine("ldy  ${regAddrHi(regArr)}")
    emitLine("sta  P8ZP_SCRATCH_W2")
    emitLine("sty  P8ZP_SCRATCH_W2+1")
    emitLine("lda  ${regAddrLo(regElem)}")
    emitLine("sta  P8ZP_SCRATCH_W1")
    emitLine("lda  ${regAddrHi(regElem)}")
    emitLine("sta  P8ZP_SCRATCH_W1+1")
    emitLine("ldy  ${regAddrLo(regLen)}")
    emitLine("jsr  prog8_lib.containment_linearwordarray")
}

private fun CodeGenerator.translateSyscallSplitWordarrayContains(args: FunctionCallArgs) {
    val regElem = args.arguments[0].reg.registerNum.value
    val regArr = args.arguments[1].reg.registerNum.value
    val regLen = args.arguments[2].reg.registerNum.value
    emitLine("lda  ${regAddrLo(regArr)}")
    emitLine("ldy  ${regAddrHi(regArr)}")
    emitLine("sta  P8ZP_SCRATCH_W2")
    emitLine("sty  P8ZP_SCRATCH_W2+1")
    emitLine("lda  ${regAddrLo(regElem)}")
    emitLine("sta  P8ZP_SCRATCH_W1")
    emitLine("lda  ${regAddrHi(regElem)}")
    emitLine("sta  P8ZP_SCRATCH_W1+1")
    emitLine("ldy  ${regAddrLo(regLen)}")
    emitLine("jsr  prog8_lib.containment_splitwordarray")
}

private fun CodeGenerator.translateSyscallLongarrayContains(args: FunctionCallArgs) {
    val regVal = args.arguments[0].reg.registerNum.value
    val regArr = args.arguments[1].reg.registerNum.value
    val regLen = args.arguments[2].reg.registerNum.value
    val labelFound = makeLabel("lac_found")
    val labelNotFound = makeLabel("lac_notfound")
    val labelLoop = makeLabel("lac_loop")
    val labelNextElement = makeLabel("lac_next")
    val labelSkipCarry = makeLabel("lac_nocarry")
    val labelDone = makeLabel("lac_done")
    emitLine("ldy  ${regAddrLo(regLen)}")
    emitLine("sty  P8ZP_SCRATCH_B1")
    emitLine("lda  ${regAddrLo(regArr)}")
    emitLine("ldy  ${regAddrHi(regArr)}")
    emitLine("sta  P8ZP_SCRATCH_PTR")
    emitLine("sty  P8ZP_SCRATCH_PTR+1")
    emitLine("lda  ${regAddrLo(regVal)}")
    emitLine("sta  P8ZP_SCRATCH_W1")
    emitLine("lda  ${regAddrHi(regVal)}")
    emitLine("sta  P8ZP_SCRATCH_W1+1")
    emitLine("lda  ${regAddrByte(regVal, 2)}")
    emitLine("sta  P8ZP_SCRATCH_W2")
    emitLine("lda  ${regAddrByte(regVal, 3)}")
    emitLine("sta  P8ZP_SCRATCH_W2+1")
    emitLabel(labelLoop)
    emitLine("ldy  #0")
    emitLine("lda  (P8ZP_SCRATCH_PTR),y")
    emitLine("cmp  P8ZP_SCRATCH_W1")
    emitLine("bne  $labelNextElement")
    emitLine("iny")
    emitLine("lda  (P8ZP_SCRATCH_PTR),y")
    emitLine("cmp  P8ZP_SCRATCH_W1+1")
    emitLine("bne  $labelNextElement")
    emitLine("iny")
    emitLine("lda  (P8ZP_SCRATCH_PTR),y")
    emitLine("cmp  P8ZP_SCRATCH_W2")
    emitLine("bne  $labelNextElement")
    emitLine("iny")
    emitLine("lda  (P8ZP_SCRATCH_PTR),y")
    emitLine("cmp  P8ZP_SCRATCH_W2+1")
    emitLine("beq  $labelFound")
    emitLabel(labelNextElement)
    emitLine("clc")
    emitLine("lda  P8ZP_SCRATCH_PTR")
    emitLine("adc  #4")
    emitLine("sta  P8ZP_SCRATCH_PTR")
    emitLine("bcc  $labelSkipCarry")
    emitLine("inc  P8ZP_SCRATCH_PTR+1")
    emitLabel(labelSkipCarry)
    emitLine("dec  P8ZP_SCRATCH_B1")
    emitLine("bne  $labelLoop")
    emitLabel(labelNotFound)
    emitLine("lda  #0")
    emitLine("jmp  $labelDone")
    emitLabel(labelFound)
    emitLine("lda  #1")
    emitLabel(labelDone)
}

private fun CodeGenerator.translateSyscallStringContains(args: FunctionCallArgs) {
    val regChar = args.arguments[0].reg.registerNum.value
    val regStr = args.arguments[1].reg.registerNum.value
    val labelFound = makeLabel("sc_found")
    val labelNotFound = makeLabel("sc_notfound")
    val labelDone = makeLabel("sc_done")
    emitLine("lda  ${regAddrLo(regChar)}")
    emitLine("sta  P8ZP_SCRATCH_W2")
    emitLine("lda  ${regAddrLo(regStr)}")
    emitLine("ldy  ${regAddrHi(regStr)}")
    emitLine("sta  P8ZP_SCRATCH_W1")
    emitLine("sty  P8ZP_SCRATCH_W1+1")
    emitLine("ldy  #0")
    emitLabel("-")
    emitLine("lda  (P8ZP_SCRATCH_W1),y")
    emitLine("beq  $labelNotFound")
    emitLine("cmp  P8ZP_SCRATCH_W2")
    emitLine("beq  $labelFound")
    emitLine("iny")
    emitLine("bne  -")
    emitLabel(labelFound)
    emitLine("lda  #1")
    emitLine("jmp  $labelDone")
    emitLabel(labelNotFound)
    emitLine("lda  #0")
    emitLabel(labelDone)
}

private fun CodeGenerator.translateSyscallFloatarrayContains(args: FunctionCallArgs) {
    val regNeedleFp = args.arguments.getOrNull(0)?.reg?.registerNum?.value ?: error("need needle fp reg")
    val regArr = args.arguments.getOrNull(1)?.reg?.registerNum?.value ?: error("need array reg")
    val regLen = args.arguments.getOrNull(2)?.reg?.registerNum?.value ?: error("need length reg")
    // Load needle value from fp register into FAC1
    emitLine("lda  #<${fpRegAddr(regNeedleFp)}")
    emitLine("ldy  #>${fpRegAddr(regNeedleFp)}")
    emitLine("jsr  floats.MOVFM")
    // Set up array pointer in P8ZP_SCRATCH_W1
    emitLine("lda  ${regAddrLo(regArr)}")
    emitLine("sta  P8ZP_SCRATCH_W1")
    emitLine("lda  ${regAddrHi(regArr)}")
    emitLine("sta  P8ZP_SCRATCH_W1+1")
    // Set length in Y
    emitLine("ldy  ${regAddrLo(regLen)}")
    emitLine("jsr  floats.containment_floatarray")
}

// === Float operations ===

private fun CodeGenerator.translateFloatFromInt(insn: IRInstruction) {
    val r1 = insn.reg1 ?: error("${insn.opcode} needs reg1 (int input)")
    val fpReg = insn.fpReg1 ?: error("${insn.opcode} needs fpReg1 (float output)")
    when (insn.opcode) {
        Opcode.FFROMUB -> {
            emitLine("ldy  ${regAddrLo(r1)}")
            emitLine("jsr  floats.FREADUY")
        }
        Opcode.FFROMSB -> {
            emitLine("lda  ${regAddrLo(r1)}")
            emitLine("jsr  floats.FREADSA")
        }
        Opcode.FFROMUW -> {
            emitLine("lda  ${regAddrLo(r1)}")
            emitLine("ldy  ${regAddrHi(r1)}")
            emitLine("jsr  floats.GIVUAYFAY")
        }
        Opcode.FFROMSW -> {
            emitLine("lda  ${regAddrLo(r1)}")
            emitLine("ldy  ${regAddrHi(r1)}")
            emitLine("jsr  floats.GIVAYFAY")
        }
        Opcode.FFROMSL -> TODO("FFROMSL (signed long to float)")
        else -> error("Unknown int-to-float conversion")
    }
    emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
    emitLine("jsr  floats.MOVMF")
}

private fun CodeGenerator.translateFloatToInt(insn: IRInstruction) {
    val r1 = insn.reg1 ?: error("${insn.opcode} needs reg1 (int output)")
    val fpReg = insn.fpReg1 ?: error("${insn.opcode} needs fpReg1 (float input)")
    emitLine("lda  #<${fpRegAddr(fpReg.value)}")
    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
    when (insn.opcode) {
        Opcode.FTOUB -> {
            // unsigned byte: use cast_as_uw_into_ya (GETADR), result in Y/A
            emitLine("jsr  floats.cast_as_uw_into_ya")
            emitLine("sty  ${regAddrLo(r1)}")   // Y has lo byte
        }
        Opcode.FTOSB -> {
            // signed byte: use cast_as_w_into_ay (AYINT2), result in A/Y
            emitLine("jsr  floats.cast_as_w_into_ay")
            emitLine("sta  ${regAddrLo(r1)}")   // A has lo byte
        }
        Opcode.FTOUW -> {
            // unsigned word: use cast_as_uw_into_ya (GETADR), result in Y/A
            emitLine("jsr  floats.cast_as_uw_into_ya")
            emitLine("sty  ${regAddrLo(r1)}")
            emitLine("sta  ${regAddrHi(r1)}")
        }
        Opcode.FTOSW -> {
            // signed word: use cast_as_w_into_ay (AYINT2), result in A/Y
            emitLine("jsr  floats.cast_as_w_into_ay")
            emitLine("sta  ${regAddrLo(r1)}")
            emitLine("sty  ${regAddrHi(r1)}")
        }
        Opcode.FTOSL -> TODO("FTOSL (float to signed long)")
        else -> error("Unknown float-to-int conversion")
    }
}

private fun CodeGenerator.translateFloatUnary(insn: IRInstruction, routine: String) {
    val src = insn.fpReg2 ?: error("${insn.opcode} needs fpReg2 (float input)")
    val dst = insn.fpReg1 ?: error("${insn.opcode} needs fpReg1 (float output)")
    emitLine("lda  #<${fpRegAddr(src.value)}")
    emitLine("ldy  #>${fpRegAddr(src.value)}")
    emitLine("jsr  floats.MOVFM")
    emitLine("jsr  $routine")
    emitLine("ldx  #<${fpRegAddr(dst.value)}")
    emitLine("ldy  #>${fpRegAddr(dst.value)}")
    emitLine("jsr  floats.MOVMF")
}

private fun CodeGenerator.translateFloatPower(insn: IRInstruction) {
    val src = insn.fpReg2 ?: error("FPOW needs fpReg2")
    val dst = insn.fpReg1 ?: error("FPOW needs fpReg1")
    // FPOW: fr1 = fr1 ^ fr2
    // KERNAL FPWRT: FAC1 = FAC2 ^ FAC1
    // Need FAC2=fr1, FAC1=fr2
    emitLine("lda  #<${fpRegAddr(dst.value)}")
    emitLine("ldy  #>${fpRegAddr(dst.value)}")
    emitLine("jsr  floats.MOVFM")
    emitLine("jsr  floats.MOVAF")
    emitLine("lda  #<${fpRegAddr(src.value)}")
    emitLine("ldy  #>${fpRegAddr(src.value)}")
    emitLine("jsr  floats.MOVFM")
    emitLine("jsr  floats.FPWRT")
    emitLine("ldx  #<${fpRegAddr(dst.value)}")
    emitLine("ldy  #>${fpRegAddr(dst.value)}")
    emitLine("jsr  floats.MOVMF")
}

private fun CodeGenerator.translateFloatCeil(insn: IRInstruction) {
    val src = insn.fpReg2 ?: error("FCEIL needs fpReg2")
    val dst = insn.fpReg1 ?: error("FCEIL needs fpReg1")
    // ceil(x) = -floor(-x)
    emitLine("lda  #<${fpRegAddr(src.value)}")
    emitLine("ldy  #>${fpRegAddr(src.value)}")
    emitLine("jsr  floats.MOVFM")
    emitLine("jsr  floats.NEGOP")
    emitLine("jsr  floats.INT")
    emitLine("jsr  floats.NEGOP")
    emitLine("ldx  #<${fpRegAddr(dst.value)}")
    emitLine("ldy  #>${fpRegAddr(dst.value)}")
    emitLine("jsr  floats.MOVMF")
}

private fun CodeGenerator.translateFloatFromSignedLong(insn: IRInstruction) {
    val r1 = insn.reg1 ?: error("FFROMSL needs reg1 (long input)")
    val fpReg = insn.fpReg1 ?: error("FFROMSL needs fpReg1 (float output)")
    val regAddr = regAddr(r1)
    // Convert 4-byte signed long to float by splitting into high and low words.
    // float = (float)(signed high_word) * 65536.0 + (float)(unsigned low_word)
    // Convert low word (bytes 0-1, unsigned) to float and save as temp
    val float65536 = getFloatConstLabel(65536.0)
    emitLine("lda  $regAddr")
    emitLine("ldy  ${regAddr}+1")
    emitLine("jsr  floats.GIVUAYFAY")
    emitLine("ldx  #<prog8_fp_temp")
    emitLine("ldy  #>prog8_fp_temp")
    emitLine("jsr  floats.MOVMF")
    // Convert high word (bytes 2-3, signed) to float and multiply by 65536.0
    emitLine("lda  ${regAddr}+2")
    emitLine("ldy  ${regAddr}+3")
    emitLine("jsr  floats.GIVAYFAY")
    emitLine("lda  #<$float65536")
    emitLine("ldy  #>$float65536")
    emitLine("jsr  floats.FMULT")
    // Add the low word float back
    emitLine("lda  #<prog8_fp_temp")
    emitLine("ldy  #>prog8_fp_temp")
    emitLine("jsr  floats.FADD")
    // Store to FP register
    emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
    emitLine("jsr  floats.MOVMF")
}

private fun CodeGenerator.translateFloatToSignedLong(insn: IRInstruction) {
    val r1 = insn.reg1 ?: error("FTOSL needs reg1 (long output)")
    val fpReg = insn.fpReg1 ?: error("FTOSL needs fpReg1 (float input)")
    val regAddr = regAddr(r1)
    val facho = "floats.FAC_ADDR+1"    // first mantissa byte after exponent
    // Load float from FP register into FAC1
    emitLine("lda  #<${fpRegAddr(fpReg.value)}")
    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
    emitLine("jsr  floats.MOVFM")
    val fpr = fpReg.value
    val doneLabel = "ftosl_done_${fpr}_${r1}"
    val posLabel = "ftosl_pos_${fpr}_${r1}"
    // Check if FAC1 is zero (exponent = 0)
    emitLine("lda  floats.FAC_ADDR")
    emitLine("bne  +")
    // Zero: store 4 zero bytes and return
    emitLine("stz  $regAddr")
    emitLine("stz  ${regAddr}+1")
    emitLine("stz  ${regAddr}+2")
    emitLine("stz  ${regAddr}+3")
    emitLine("bra  $doneLabel")
    // Non-zero: check sign (bit 7 of FAC_ADDR+1)
    emitLine("+   lda  floats.FAC_ADDR+1")
    emitLine("bpl  $posLabel")
    // Negative: negate, QINT, copy mantissa, then negate the 4-byte result
    emitLine("jsr  floats.NEGOP")
    emitLine("jsr  floats.QINT")
    emitLine("lda  $facho+3")
    emitLine("sta  $regAddr")
    emitLine("lda  $facho+2")
    emitLine("sta  ${regAddr}+1")
    emitLine("lda  $facho+1")
    emitLine("sta  ${regAddr}+2")
    emitLine("lda  $facho")
    emitLine("sta  ${regAddr}+3")
    // Negate the 32-bit result (two's complement)
    emitLine("sec")
    emitLine("lda  #0")
    emitLine("sbc  $regAddr")
    emitLine("sta  $regAddr")
    emitLine("lda  #0")
    emitLine("sbc  ${regAddr}+1")
    emitLine("sta  ${regAddr}+1")
    emitLine("lda  #0")
    emitLine("sbc  ${regAddr}+2")
    emitLine("sta  ${regAddr}+2")
    emitLine("lda  #0")
    emitLine("sbc  ${regAddr}+3")
    emitLine("sta  ${regAddr}+3")
    emitLine("bra  $doneLabel")
    // Positive: QINT and copy mantissa bytes to target
    emitLine("$posLabel:")
    emitLine("jsr  floats.QINT")
    emitLine("lda  $facho+3")
    emitLine("sta  $regAddr")
    emitLine("lda  $facho+2")
    emitLine("sta  ${regAddr}+1")
    emitLine("lda  $facho+1")
    emitLine("sta  ${regAddr}+2")
    emitLine("lda  $facho")
    emitLine("sta  ${regAddr}+3")
    emitLine("$doneLabel:")
}

private fun CodeGenerator.translateFloatCompare(insn: IRInstruction) {
    val r1 = insn.reg1 ?: error("FCOMP needs reg1 (int output)")
    val fr1 = insn.fpReg1 ?: error("FCOMP needs fpReg1")
    val fr2 = insn.fpReg2 ?: error("FCOMP needs fpReg2")
    // Compare fr1 with fr2.
    // KERNAL FCOMP: A = compare(FAC1, memory[AY])  - 0=equal, 1=greater, 255=less
    // Load fr1 into FAC1, then compare with fr2
    emitLine("lda  #<${fpRegAddr(fr1.value)}")
    emitLine("ldy  #>${fpRegAddr(fr1.value)}")
    emitLine("jsr  floats.MOVFM")
    emitLine("lda  #<${fpRegAddr(fr2.value)}")
    emitLine("ldy  #>${fpRegAddr(fr2.value)}")
    emitLine("jsr  floats.FCOMP")
    // FCOMP returns -1, 0, or 1 in A (as signed byte)
    emitLine("sta  ${regAddrLo(r1)}")
    // Sign-extend to word if needed (irrelevant for comparison since only byte used)
}

private fun CodeGenerator.translateFloatSquare(insn: IRInstruction) {
    val src = insn.fpReg2 ?: error("SQUARE.f needs fpReg2")
    val dst = insn.fpReg1 ?: error("SQUARE.f needs fpReg1")
    // fr1 = fr2^2 = fr2 * fr2
    emitLine("lda  #<${fpRegAddr(src.value)}")
    emitLine("ldy  #>${fpRegAddr(src.value)}")
    emitLine("jsr  floats.MOVFM")
    emitLine("jsr  floats.MOVAF")
    emitLine("lda  #<${fpRegAddr(src.value)}")
    emitLine("ldy  #>${fpRegAddr(src.value)}")
    emitLine("jsr  floats.MOVFM")
    emitLine("jsr  floats.FMULTT")
    emitLine("ldx  #<${fpRegAddr(dst.value)}")
    emitLine("ldy  #>${fpRegAddr(dst.value)}")
    emitLine("jsr  floats.MOVMF")
}

private fun CodeGenerator.translateFloatSign(insn: IRInstruction) {
    val r1 = insn.reg1 ?: error("SGN.f needs reg1 (int output)")
    val fpReg = insn.fpReg1 ?: error("SGN.f needs fpReg1 (float input)")
    // SGN: reg1 = sign(fr1) as integer (-1, 0, 1)
    emitLine("lda  #<${fpRegAddr(fpReg.value)}")
    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
    emitLine("jsr  floats.MOVFM")
    emitLine("jsr  floats.SIGN")
    emitLine("sta  ${regAddrLo(r1)}")
}

private fun CodeGenerator.jsrfarRoutine(): String {
    val targetName = program.options.compTarget.name
    return when (targetName) {
        "cx16" -> "cx16.JSRFAR"
        "c64" -> "c64.x16jsrfar"
        "c128" -> "c128.x16jsrfar"
        else -> "$targetName.x16jsrfar"
    }
}
