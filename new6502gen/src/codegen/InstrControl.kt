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
            TODO("CALLI via r$reg")
        }

        Opcode.CALLFAR -> {
            val target = label ?: (addr?.value ?: 0u).toHex()
            val bank = imm ?: 0
            TODO("CALLFAR $target, bank=$bank")
        }

        Opcode.CALLFARVB -> {
            val target = label ?: (addr?.value ?: 0u).toHex()
            val bankReg = r1 ?: error("CALLFARVB needs reg1")
            TODO("CALLFARVB $target, bank in r$bankReg")
        }

        Opcode.SYSCALL -> {
            val args = insn.fcallArgs
            translateSyscall(insn, args)
        }

        Opcode.RETURN -> {
            emitLine("rts")
        }

        Opcode.RETURNR -> {
            val reg = r1 ?: error("RETURNR needs reg1")
            val type = insn.type ?: IRDataType.BYTE
            when (type) {
                IRDataType.BYTE -> {
                    emitLine("lda  ${regAddrLo(reg)}")
                }
                IRDataType.WORD -> {
                    emitLine("lda  ${regAddrLo(reg)}")
                    emitLine("ldx  ${regAddrHi(reg)}")
                }
                IRDataType.LONG -> {
                    emitLine("lda  ${regAddrLo(reg)}")
                    emitLine("sta  cx16.r14")
                    emitLine("lda  ${regAddrHi(reg)}")
                    emitLine("sta  cx16.r14+1")
                    emitLine("lda  ${regAddrLo(reg + 1)}")
                    emitLine("sta  cx16.r15")
                    emitLine("lda  ${regAddrHi(reg + 1)}")
                    emitLine("sta  cx16.r15+1")
                }
                else -> TODO("RETURNR r$reg ${type.name}")
            }
            emitLine("rts")
        }

        Opcode.RETURNI -> {
            val value = imm ?: error("RETURNI needs immediate")
            val type = insn.type ?: IRDataType.BYTE
            when (type) {
                IRDataType.BYTE -> {
                    emitLine("lda  #${value and 0xff}")
                }
                IRDataType.WORD -> {
                    emitLine("lda  #<${value and 0xffff}")
                    emitLine("ldx  #>${value and 0xffff}")
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
                IRDataType.FLOAT -> TODO("RETURNI FLOAT")
            }
            emitLine("rts")
        }

        Opcode.PUSH -> {
            val reg = r1 ?: error("PUSH needs reg1")
            val type = insn.type ?: IRDataType.BYTE
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
                    emitLine("lda  ${regAddrHi(reg + 1)}")
                    emitLine("pha")
                    emitLine("lda  ${regAddrLo(reg + 1)}")
                    emitLine("pha")
                    emitLine("lda  ${regAddrHi(reg)}")
                    emitLine("pha")
                    emitLine("lda  ${regAddrLo(reg)}")
                    emitLine("pha")
                }
                else -> TODO("PUSH r$reg ${type.name}")
            }
        }

        Opcode.POP -> {
            val reg = r1 ?: error("POP needs reg1")
            val type = insn.type ?: IRDataType.BYTE
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
                    emitLine("sta  ${regAddrLo(reg + 1)}")
                    emitLine("pla")
                    emitLine("sta  ${regAddrHi(reg + 1)}")
                }
                else -> TODO("POP r$reg ${type.name}")
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

        Opcode.SQRT -> TODO("SQRT")
        Opcode.SQUARE -> TODO("SQUARE")
        Opcode.SGN -> TODO("SGN")

        Opcode.FFROMUB, Opcode.FFROMSB, Opcode.FFROMUW, Opcode.FFROMSW, Opcode.FFROMSL -> TODO("${insn.opcode} (float conversion)")

        Opcode.FTOUB, Opcode.FTOSB, Opcode.FTOUW, Opcode.FTOSW, Opcode.FTOSL -> TODO("${insn.opcode} (float conversion)")

        Opcode.FABS -> TODO("FABS")
        Opcode.FSIN -> TODO("FSIN")
        Opcode.FCOS -> TODO("FCOS")
        Opcode.FTAN -> TODO("FTAN")
        Opcode.FATAN -> TODO("FATAN")
        Opcode.FPOW -> TODO("FPOW")
        Opcode.FLN -> TODO("FLN")
        Opcode.FLOG -> TODO("FLOG")
        Opcode.FROUND -> TODO("FROUND")
        Opcode.FFLOOR -> TODO("FFLOOR")
        Opcode.FCEIL -> TODO("FCEIL")
        Opcode.FCOMP -> TODO("FCOMP")

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
                        TODO("FLOAT arg to address $address")
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
                                TODO("FLOAT arg to $asmTarget")
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
                                TODO("FLOAT arg to $target")
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
                        TODO("FLOAT return to r$regNum (slot/flag not set)")
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
        IMSyscall.FLOATARRAY_CONTAINS.number -> TODO("SYSCALL FLOATARRAY_CONTAINS")
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
    emitLabel("+")
    emitLine("lda  (P8ZP_SCRATCH_W1),y")
    emitLine("beq  $labelNotFound")
    emitLine("cmp  P8ZP_SCRATCH_W2")
    emitLine("beq  $labelFound")
    emitLine("iny")
    emitLine("bne  +")
    emitLabel(labelFound)
    emitLine("lda  #1")
    emitLine("jmp  $labelDone")
    emitLabel(labelNotFound)
    emitLine("lda  #0")
    emitLabel(labelDone)
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
