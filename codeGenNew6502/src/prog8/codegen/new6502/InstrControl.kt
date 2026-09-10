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

package prog8.codegen.new6502

import prog8.code.core.Statusflag
import prog8.code.core.toHex
import prog8.intermediate.*

internal fun AsmGen.translateControl(insn: IRInstruction) {
    when (insn.opcode) {
        Opcode.JUMP -> {
            emitUnconditionalBranch(targetLabel(insn))
        }

        Opcode.JUMPI -> {
            val reg = (insn.requireTarget() as CodeReference.Indirect).pointer.intNumber
            // NOTE: `jmp (ptr)` has the 6502 page-wrap bug on plain 6502
            // (if the pointer's address ends in $FF the high byte is read
            // from the same page instead of the next). 65C02 is fine.
            // Latent: the pointer is a register in p8_regfile (BSS) and only
            // misbehaves if that slot happens to land at $xxFF. The old 6502
            // codegen has the same hazard.
            emitLine("jmp  (${regAddr(reg)})")
        }

        Opcode.CALL -> {
            val site = insn.requireCallSite()
            val fnLabel = when (val ref = site.codeReference) {
                is CodeReference.Label -> ref.name
                is CodeReference.Absolute -> ref.address.value.toHex()
                else -> error("CALL needs a direct label or address target")
            }
            translateCall(fnLabel, site)
        }

        Opcode.CALLI -> {
            val reg = ((insn.requireCallSite().target as CallTarget.Direct).reference as CodeReference.Indirect).pointer.intNumber
            // NOTE: same 6502 page-wrap pitfall as JUMPI above: `jmp (ptr)`
            // misbehaves on plain 6502 if the pointer (here a register in
            // p8_regfile) lands at $xxFF. 65C02 is fine. Same hazard exists
            // in the old 6502 codegen.
            emitLine("lda  #>((+)-1)")
            emitLine("pha")
            emitLine("lda  #<((+)-1)")
            emitLine("pha")
            emitLine("jmp  (${regAddr(reg)})")
            emitLabel("+")
        }

        Opcode.CALLFAR -> {
            val site = insn.requireCallSite()
            val bankedTarget = site.target as? CallTarget.Banked
                ?: error("CALLFAR amiga library calls are not supported on 6502 targets")
            val target = codeReferenceLabel(bankedTarget.reference)
            val bank = bankedTarget.bank
            val jsrfar = jsrfarRoutine()
            for ((index, arg) in site.arguments.withIndex()) {
                if (arg.hardwareSlot == null)
                    translateArgument(arg, index, null)
            }
            for ((index, arg) in site.arguments.inSlotLoadOrder()) {
                translateArgument(arg, index, null)
            }
            emitLine("jsr  $jsrfar")
            emitLine(".word  $target")
            emitLine(".byte  $bank")
            for (ret in site.results) {
                translateReturnValue(ret)
            }
        }

        Opcode.CALLFARVB -> {
            val site = insn.requireCallSite()
            val bankedTarget = site.target as CallTarget.BankedVariable
            val target = codeReferenceLabel(bankedTarget.reference)
            val bankReg = bankedTarget.bankRegister.intNumber
            val jsrfar = jsrfarRoutine()
            val patchLabel = makeLabel("callfarvb_patch")
            for ((index, arg) in site.arguments.withIndex()) {
                if (arg.hardwareSlot == null)
                    translateArgument(arg, index, null)
            }
            for ((index, arg) in site.arguments.inSlotLoadOrder()) {
                translateArgument(arg, index, null)
            }
            emitLine("lda  ${regAddrLo(bankReg)}")
            emitLine("sta  ${patchLabel}+2")
            emitLine("jsr  $jsrfar")
            emitLabel(patchLabel)
            emitLine(".word  $target")
            emitLine(".byte  0")
            for (ret in site.results) {
                translateReturnValue(ret)
            }
        }

        Opcode.SYSCALL -> {
            translateSyscall(insn)
        }

        Opcode.RETURN -> {
            emitLine("rts")
        }


        Opcode.RETURNR -> {
            val type = insn.type ?: IRDataType.BYTE
            if (type == IRDataType.FLOAT) {
                val fpReg = insn.requireFloatSourceA().floatNumber
                emitLine("lda  #<${fpRegAddr(fpReg.value)}")
                emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
                emitLine("jsr  floats.MOVFM")
            } else {
                val reg = insn.requireIntSourceA().intNumber
                when (type) {
                    IRDataType.BYTE -> {
                        emitLine("lda  ${regAddrLo(reg)}")
                    }
                    IRDataType.WORD, IRDataType.POINTER -> {
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
                val value = insn.requireImmediateFloat()
                val constLabel = getFloatConstLabel(value)
                emitLine("lda  #<$constLabel")
                emitLine("ldy  #>$constLabel")
                emitLine("jsr  floats.MOVFM")
            } else {
                val value = insn.requireImmediateInt()
                when (type) {
                    IRDataType.BYTE -> {
                        emitLine("lda  #${value and 0xff}")
                    }
                    IRDataType.WORD, IRDataType.POINTER -> {
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
                val fpReg = insn.requireFloatSourceA().floatNumber
                emitLine("lda  #<${fpRegAddr(fpReg.value)}")
                emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
                emitLine("jsr  floats.MOVFM")
                emitLine("jsr  floats.pushFAC1")
            } else {
                val reg = insn.requireIntSourceA().intNumber
                when (type) {
                    IRDataType.BYTE -> {
                        emitLine("lda  ${regAddrLo(reg)}")
                        emitLine("pha")
                    }
                    IRDataType.WORD, IRDataType.POINTER -> {
                        // byte order must match old codegen: push low byte first, then high byte -> stack top = high byte
                        emitLine("lda  ${regAddrLo(reg)}")
                        emitLine("pha")
                        emitLine("lda  ${regAddrHi(reg)}")
                        emitLine("pha")
                    }
                    IRDataType.LONG -> {
                        // byte order must match old codegen: push low byte first, then ascending -> stack top = highest byte
                        emitLine("lda  ${regAddrLo(reg)}")
                        emitLine("pha")
                        emitLine("lda  ${regAddrHi(reg)}")
                        emitLine("pha")
                        emitLine("lda  ${regAddrByte(reg, 2)}")
                        emitLine("pha")
                        emitLine("lda  ${regAddrByte(reg, 3)}")
                        emitLine("pha")
                    }
                }
            }
        }

        Opcode.POP -> {
            val type = insn.type ?: IRDataType.BYTE
            if (type == IRDataType.FLOAT) {
                val fpReg = insn.requireFloatDest().floatNumber
                emitLine("clc")
                emitLine("jsr  floats.popFAC")
                emitLine("ldx  #<${fpRegAddr(fpReg.value)}")
                emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
                emitLine("jsr  floats.MOVMF")
            } else {
                val reg = insn.requireIntDest().intNumber
                when (type) {
                    IRDataType.BYTE -> {
                        emitLine("pla")
                        emitLine("sta  ${regAddrLo(reg)}")
                    }
                    IRDataType.WORD, IRDataType.POINTER -> {
                        // byte order must match old codegen: pop high byte first (stack top), then low byte
                        emitLine("pla")
                        emitLine("sta  ${regAddrHi(reg)}")
                        emitLine("pla")
                        emitLine("sta  ${regAddrLo(reg)}")
                    }
                    IRDataType.LONG -> {
                        // byte order must match old codegen: pop highest byte first (stack top), then descending
                        emitLine("pla")
                        emitLine("sta  ${regAddrByte(reg, 3)}")
                        emitLine("pla")
                        emitLine("sta  ${regAddrByte(reg, 2)}")
                        emitLine("pla")
                        emitLine("sta  ${regAddrHi(reg)}")
                        emitLine("pla")
                        emitLine("sta  ${regAddrLo(reg)}")
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
            val alignment = insn.requireImmediateInt()
            if (alignment > 1) {
                emitLine(".align  ${alignment.toUInt().toHex()}")
            }
        }

        Opcode.LSIGB -> {
            val dest = insn.requireIntDest().intNumber
            val src = insn.requireIntSourceA().intNumber
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dest)}")
        }

        Opcode.LSIGW -> {
            val dest = insn.requireIntDest().intNumber
            val src = insn.requireIntSourceA().intNumber
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dest)}")
            emitLine("lda  ${regAddrHi(src)}")
            emitLine("sta  ${regAddrHi(dest)}")
        }

        Opcode.MSIGB -> {
            val dest = insn.requireIntDest().intNumber
            val src = insn.requireIntSourceA().intNumber
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
            val dest = insn.requireIntDest().intNumber
            val src = insn.requireIntSourceA().intNumber
            // dest is WORD (bytes 0-1 only), no need to zero bytes 2-3
            emitLine("lda  ${regAddrByte(src, 2)}")
            emitLine("sta  ${regAddrLo(dest)}")
            emitLine("lda  ${regAddrByte(src, 3)}")
            emitLine("sta  ${regAddrHi(dest)}")
        }

        Opcode.BSIGB -> {
            val dest = insn.requireIntDest().intNumber
            val src = insn.requireIntSourceA().intNumber
            emitLine("lda  ${regAddrByte(src, 2)}")
            emitLine("sta  ${regAddrLo(dest)}")
        }

        Opcode.MIDB -> {
            val dest = insn.requireIntDest().intNumber
            val src = insn.requireIntSourceA().intNumber
            emitLine("lda  ${regAddrHi(src)}")
            emitLine("sta  ${regAddrLo(dest)}")
        }

        Opcode.CONCAT -> {
            val type = insn.type ?: IRDataType.BYTE
            val dest = insn.requireIntDest().intNumber
            val msb = insn.requireIntSourceA().intNumber
            val lsb = insn.requireSrcB().intNumber
            when (type) {
                IRDataType.BYTE -> {
                    // dest = WORD(msb, lsb)
                    emitLine("lda  ${regAddrLo(msb)}")
                    emitLine("sta  ${regAddrHi(dest)}")
                    emitLine("lda  ${regAddrLo(lsb)}")
                    emitLine("sta  ${regAddrLo(dest)}")
                }
                IRDataType.WORD -> {
                    // dest = LONG(msb as MSW, lsb as LSW)
                    // Save msb first in case dest overlaps with msb or msb==lsb
                    emitLine("lda  ${regAddrLo(msb)}")
                    emitLine("sta  $ZP_TEMP")
                    emitLine("lda  ${regAddrHi(msb)}")
                    emitLine("sta  ${ZP_TEMP}+1")
                    // Copy lsb (lsw) to dest+0, dest+1
                    emitLine("lda  ${regAddrLo(lsb)}")
                    emitLine("sta  ${regAddrLo(dest)}")
                    emitLine("lda  ${regAddrHi(lsb)}")
                    emitLine("sta  ${regAddrHi(dest)}")
                    // Copy saved msw to dest+2, dest+3
                    emitLine("lda  $ZP_TEMP")
                    emitLine("sta  ${regAddrByte(dest, 2)}")
                    emitLine("lda  ${ZP_TEMP}+1")
                    emitLine("sta  ${regAddrByte(dest, 3)}")
                }
                else -> TODO("CONCAT ${type.name}")
            }
        }

        Opcode.EXT -> {
            val reg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            if (reg != srcReg) {
                emitLine("lda  ${regAddrLo(srcReg)}")
                emitLine("sta  ${regAddrLo(reg)}")
            }
            emitStoreZero(regAddrHi(reg))
        }

        Opcode.EXTS -> {
            val dest = insn.requireIntDest().intNumber
            val src = insn.requireIntSourceA().intNumber
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dest)}")
            emitLine("and  #128")
            emitLine("beq  +")
            emitLine("lda  #255")
            emitLine("sta  ${regAddrHi(dest)}")
            emitUnconditionalBranch("++")
            emitLabel("+")
            emitStoreZero(regAddrHi(dest))
            emitLabel("+")
        }

        Opcode.EXTL -> {
            val dest = insn.requireIntDest().intNumber
            val src = insn.requireIntSourceA().intNumber
            if (dest != src) {
                emitLine("lda  ${regAddrLo(src)}")
                emitLine("sta  ${regAddrLo(dest)}")
            }
            emitStoreZero(regAddrHi(dest))
            emitStoreZero(regAddrByte(dest, 2))
            emitStoreZero(regAddrByte(dest, 3))
        }

        Opcode.EXTLS -> {
            val dest = insn.requireIntDest().intNumber
            val src = insn.requireIntSourceA().intNumber
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("sta  ${regAddrLo(dest)}")
            emitLine("and  #128")
            emitLine("beq  +")
            emitLine("lda  #255")
            emitLine("sta  ${regAddrHi(dest)}")
            emitLine("sta  ${regAddrByte(dest, 2)}")
            emitLine("sta  ${regAddrByte(dest, 3)}")
            emitUnconditionalBranch("++")
            emitLabel("+")
            emitStoreZero(regAddrHi(dest))
            emitStoreZero(regAddrByte(dest, 2))
            emitStoreZero(regAddrByte(dest, 3))
            emitLabel("+")
        }

        Opcode.SQRT -> {
            if (insn.type == IRDataType.FLOAT)
                translateFloatUnary(insn, "floats.SQR")
            else
                translateIntSqrt(insn)
        }
        Opcode.SQUARE -> {
            if (insn.type == IRDataType.FLOAT)
                translateFloatSquare(insn)
            else
                translateIntSquare(insn)
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

/** the label (or fixed address) a static code reference denotes */
internal fun codeReferenceLabel(reference: CodeReference): String = when (reference) {
    is CodeReference.Label -> if (reference.offset != 0) "${reference.name}+${reference.offset}" else reference.name
    is CodeReference.Absolute -> reference.address.value.toHex()
    is CodeReference.Indirect -> error("code reference must be a static one, not $reference")
}

/** the calling convention slot this argument is passed in, if it is passed in a cpu hardware register */
internal val CallArgument.hardwareSlot: CallingConventionSlot?
    get() = (location as? CallLocation.HardwareRegister)?.slot

/** the calling convention slot this result is delivered in, if it is delivered in a cpu hardware register */
internal val CallResult.hardwareSlot: CallingConventionSlot?
    get() = (location as? CallLocation.HardwareRegister)?.slot

/**
 * The slot-based arguments, in the order they must be loaded to avoid register clobbering.
 * Order matches the old 6502 codegen: paired regs first (AX/AY/XY), then single regs (Y, X, A),
 * then float regs, then status flags. This ensures that loading a paired register
 * (which clobbers two hardware regs) doesn't overwrite a value needed for a later argument.
 * Within each group the original argument order is kept.
 */
private fun List<CallArgument>.inSlotLoadOrder(): List<IndexedValue<CallArgument>> =
    withIndex()
        .filter { it.value.hardwareSlot != null }
        .sortedWith(compareBy<IndexedValue<CallArgument>> {
            when (it.value.hardwareSlot!!.value) {
                3, 4, 5 -> 0  // paired CPU regs (AX, AY, XY) - load first
                2 -> 1        // Y - load before X and A
                1 -> 2        // X - load before A
                0 -> 3        // A - load last (most commonly clobbered)
                6, 7 -> 4     // float regs (FAC1, FAC2)
                else -> 5     // status flags
            }
        }.thenBy { it.index })

private fun AsmGen.translateCall(fnLabel: String, site: CallSite) {
    // Check if this is an inline ASMSUB - must be inlined at call site, not called with jsr
    val inlineAsmSub = findInlineAsmSub(fnLabel)
    if (inlineAsmSub != null) {
        // Inline the assembly body directly at the call site (no jsr, no rts)
        // Process non-slot arguments first
        for ((index, arg) in site.arguments.withIndex()) {
            if (arg.hardwareSlot == null)
                translateArgument(arg, index, fnLabel)
        }
        // Process slot arguments
        for ((index, arg) in site.arguments.inSlotLoadOrder()) {
            translateArgument(arg, index, fnLabel)
        }
        emitRaw("    ; inlined: $fnLabel")
        inlineAsmSub.asmChunk.assembly.lineSequence().forEach { line ->
            if (line.isNotBlank()) emitRaw("    $line")
        }
        emitRaw("    ; end inlined: $fnLabel")
        for (ret in site.results) {
            translateReturnValue(ret)
        }
        return
    }

    // Process non-slot arguments first (they use A as temp to store to memory/registers)
    for ((index, arg) in site.arguments.withIndex()) {
        if (arg.hardwareSlot == null)
            translateArgument(arg, index, fnLabel)
    }
    for ((index, arg) in site.arguments.inSlotLoadOrder()) {
        translateArgument(arg, index, fnLabel)
    }

    emitLine("jsr  $fnLabel")

    // Move return values back to virtual registers.
    // Skip status flag returns: always handled by IR's branch pattern (bsteq/bmi etc).
    // In multi-assign context (results.size > 1), also skip slot-based returns:
    // the IR generates LOADHR for them.
    // In single-return expression context, process slot returns normally
    // (the IR doesn't generate LOADHR for single-return calls).
    // LIMITATION: multiple status flag returns in one multi-assign (e.g. -> bool @Pz, bool @Pc)
    // are not supported - codegen limitation: the first flag's extraction clobbers the state for subsequent flags.
    val isMultiReturn = site.results.size > 1
    for (ret in site.results) {
        if (ret.location is CallLocation.StatusFlag)
            continue
        if (isMultiReturn)
            continue
        translateReturnValue(ret)
    }
}

/** Find an inline ASMSUB by its full scoped label. Returns null if not found or not inline. */
private fun AsmGen.findInlineAsmSub(label: String): IRAsmSubroutine? =
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

private fun AsmGen.translateArgument(arg: CallArgument, argIndex: Int = -1, fnLabel: String? = null) {
    val source = arg.source
    val regNum = source.register.num

    when (arg.hardwareSlot?.value) {
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
            when (val location = arg.location) {
                is CallLocation.StatusFlag -> {
                    // Status flag argument - load value and set the appropriate flag
                    when (location.flag) {
                        Statusflag.Pc -> {
                            emitLine("lda  ${regAddrLo(regNum)}")
                            emitLine("cmp  #0")
                            emitLine("beq  +")
                            emitLine("sec")
                            emitLabel("+")
                        }

                        Statusflag.Pv -> TODO("status flag Pv for argument")
                        else -> TODO("status flag ${location.flag}")
                    }
                }
                is CallLocation.ParameterMemory -> {
                    val address = location.address
                    if (address != null) {
                        storeArgumentTo(address.toHex(), regNum, source.type)
                    } else {
                        // Check if this argument maps to an asmsub's cx16 virtual register parameter
                        val asmTarget = if (fnLabel != null && argIndex >= 0)
                            this.asmSubParamTarget(fnLabel, argIndex) else null
                        if (asmTarget != null) {
                            storeArgumentTo(asmTarget, regNum, source.type)
                        } else {
                            // The argument is not passed in a cpu hardware register, so this is NOT
                            // an extsub parameter (extsub parameters have a HardwareRegister location
                            // for A/X/Y register passing). The parameter is accessed by name in the
                            // inline asm (e.g. `lda #<value` inside a regular sub's %asm body), and the
                            // parameter has been pre-allocated a fixed memory address by
                            // SubParamAllocator. We store the argument to that address here.
                            // For an asmsub cx16 virtual register parameter the name is like "cx16.r0"
                            // and is resolved to that register's address; other named params are
                            // resolved relative to the function label.
                            val name = location.name
                            val target = if (name.startsWith("cx16."))
                                resolveSymbolRef(name)
                            else
                                resolveSymbolRef(if (fnLabel != null) "$fnLabel.$name" else name)
                            storeArgumentTo(target, regNum, source.type)
                        }
                    }
                }
                CallLocation.Default -> {
                    // Syscall argument - value is already in the register file,
                    // the syscall handler reads it from there.
                }
                is CallLocation.HardwareRegister -> throw IllegalStateException("slot already handled")
            }
        }
        else -> TODO("calling convention slot ${arg.hardwareSlot} on 6502")
    }
}

/** store an argument that lives in virtual register [regNum] to a fixed memory location */
private fun AsmGen.storeArgumentTo(target: String, regNum: Int, type: IRDataType) {
    when (type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddrLo(regNum)}")
            emitLine("sta  $target")
        }
        IRDataType.WORD, IRDataType.POINTER -> {
            emitLine("lda  ${regAddrLo(regNum)}")
            emitLine("sta  $target")
            emitLine("lda  ${regAddrHi(regNum)}")
            emitLine("sta  ${target}+1")
        }
        IRDataType.LONG -> {
            val base = regAddrByte(regNum, 0)
            emitLine("ldy  #3")
            emitLine("-  lda  $base,y")
            emitLine("sta  $target,y")
            emitLine("dey")
            emitLine("bpl  -")
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

private fun AsmGen.translateReturnValue(ret: CallResult) {
    // Status flag returns are handled by the IR's branch pattern.
    // Do not extract them here - the IR emits bsteq/bmi etc after the call.
    if (ret.location is CallLocation.StatusFlag) {
        return
    }
    val destination = ret.destination
    if (destination == null) {
        emitLine("; uncaptured return value at ${ret.location}")
        return
    }
    val regNum = destination.register.num

    when (ret.hardwareSlot?.value) {
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
            when (destination.type) {
                IRDataType.BYTE -> {
                    emitLine("sta  ${regAddrLo(regNum)}")
                }
                IRDataType.WORD, IRDataType.POINTER -> {
                    emitLine("sta  ${regAddrLo(regNum)}")
                    emitLine("sty  ${regAddrHi(regNum)}")
                }
                IRDataType.LONG -> {
                    emitLine("lda  cx16.r14")
                    emitLine("sta  ${regAddrLo(regNum)}")
                    emitLine("lda  cx16.r14+1")
                    emitLine("sta  ${regAddrHi(regNum)}")
                    emitLine("lda  cx16.r15")
                    emitLine("sta  ${regAddrByte(regNum, 2)}")
                    emitLine("lda  cx16.r15+1")
                    emitLine("sta  ${regAddrByte(regNum, 3)}")
                }
                IRDataType.FLOAT -> {
                    emitLine("ldx  #<${fpRegAddr(regNum)}")
                    emitLine("ldy  #>${fpRegAddr(regNum)}")
                    emitLine("jsr  floats.MOVMF")
                }
            }
        }
        else -> TODO("calling convention slot ${ret.hardwareSlot} on 6502")
    }
}

// === Syscall handling ===

private fun CallSite.argumentOperand(index: Int, what: String): RegisterOperand =
    arguments.getOrNull(index)?.source ?: error("syscall needs $what as argument $index")

private fun CallSite.argumentRegister(index: Int, what: String): Int =
    argumentOperand(index, what).register.num

private fun AsmGen.translateSyscall(insn: IRInstruction) {
    val site = insn.requireCallSite()
    val syscallNum = (site.target as CallTarget.SystemCall).number
    when (syscallNum) {
        IMSyscall.CLAMP_UBYTE.number -> translateSyscallClampUbyte(site)
        IMSyscall.CLAMP_BYTE.number -> translateSyscallClampByte(site)
        IMSyscall.CLAMP_UWORD.number -> translateSyscallClampUword(site)
        IMSyscall.CLAMP_WORD.number -> translateSyscallClampWord(site)
        IMSyscall.CLAMP_LONG.number -> translateSyscallClampLong(site)
        IMSyscall.COMPARE_STRINGS.number -> translateSyscallStringCompare(site)
        IMSyscall.STRING_CONTAINS.number -> translateSyscallStringContains(site)
        IMSyscall.BYTEARRAY_CONTAINS.number -> translateSyscallBytearrayContains(site)
        IMSyscall.WORDARRAY_CONTAINS.number -> translateSyscallWordarrayContains(site)
        IMSyscall.SPLIT_WORDARRAY_CONTAINS.number -> translateSyscallSplitWordarrayContains(site)
        IMSyscall.LONGARRAY_CONTAINS.number -> translateSyscallLongarrayContains(site)
        IMSyscall.FLOATARRAY_CONTAINS.number -> translateSyscallFloatarrayContains(site)
        IMSyscall.CALLFAR.number -> translateSyscallCallfar(site)
        IMSyscall.CALLFAR2.number -> translateSyscallCallfar2(site)
        IMSyscall.MEMCOPY.number -> translateSyscallMemcopy(site)
        else -> TODO("unknown SYSCALL number $syscallNum")
    }
    for (ret in site.results) {
        translateReturnValue(ret)
    }
}

private fun AsmGen.translateSyscallClampUbyte(site: CallSite) {
    val regValue = site.argumentRegister(0, "value reg")
    val regMin = site.argumentRegister(1, "min reg")
    val regMax = site.argumentRegister(2, "max reg")
    emitLine("lda  ${regAddrLo(regMin)}")
    emitLine("sta  P8ZP_SCRATCH_W1")
    emitLine("lda  ${regAddrLo(regMax)}")
    emitLine("sta  P8ZP_SCRATCH_W1+1")
    emitLine("lda  ${regAddrLo(regValue)}")
    emitLine("jsr  prog8_lib.func_clamp_ubyte")
    emitLine("sta  ${regAddrLo(regValue)}")
}

private fun AsmGen.translateSyscallClampByte(site: CallSite) {
    val regValue = site.argumentRegister(0, "value reg")
    val regMin = site.argumentRegister(1, "min reg")
    val regMax = site.argumentRegister(2, "max reg")
    emitLine("lda  ${regAddrLo(regMin)}")
    emitLine("sta  P8ZP_SCRATCH_W1")
    emitLine("lda  ${regAddrLo(regMax)}")
    emitLine("sta  P8ZP_SCRATCH_W1+1")
    emitLine("lda  ${regAddrLo(regValue)}")
    emitLine("jsr  prog8_lib.func_clamp_byte")
    emitLine("sta  ${regAddrLo(regValue)}")
}

private fun AsmGen.translateSyscallClampUword(site: CallSite) {
    val regValue = site.argumentRegister(0, "value reg")
    val regMin = site.argumentRegister(1, "min reg")
    val regMax = site.argumentRegister(2, "max reg")
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

private fun AsmGen.translateSyscallClampWord(site: CallSite) {
    val regValue = site.argumentRegister(0, "value reg")
    val regMin = site.argumentRegister(1, "min reg")
    val regMax = site.argumentRegister(2, "max reg")
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

private fun AsmGen.translateSyscallClampLong(site: CallSite) {
    val regValue = site.argumentRegister(0, "value reg")
    val regMin = site.argumentRegister(1, "min reg")
    val regMax = site.argumentRegister(2, "max reg")
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

private fun AsmGen.translateSyscallMemcopy(site: CallSite) {
    val regSrc = site.argumentRegister(0, "src reg")
    val regDst = site.argumentRegister(1, "dst reg")
    val regCount = site.argumentRegister(2, "count reg")
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

private fun AsmGen.translateSyscallCallfar(site: CallSite) {
    val regBank = site.argumentRegister(0, "bank reg")
    val regAddress = site.argumentRegister(1, "address reg")
    val regArg = site.argumentRegister(2, "argument reg")
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

private fun AsmGen.translateSyscallCallfar2(site: CallSite) {
    val regBank = site.argumentRegister(0, "bank reg")
    val regAddress = site.argumentRegister(1, "address reg")
    val regA = site.argumentRegister(2, "A reg")
    val regX = site.argumentRegister(3, "X reg")
    val regY = site.argumentRegister(4, "Y reg")
    val regCarry = site.argumentRegister(5, "carry reg")
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
    emitUnconditionalBranch("++")
    emitLabel("+")
    emitLine("pla")
    emitLine("clc")
    emitLabel("+")
    emitLine("jsr  $jsrfar")
    emitLabel(label)
    emitLine(".word  0")
    emitLine(".byte  0")
}

private fun AsmGen.translateSyscallStringCompare(site: CallSite) {
    val regStr1 = site.argumentRegister(0, "string 1 reg")
    val regStr2 = site.argumentRegister(1, "string 2 reg")
    emitLine("lda  ${regAddrLo(regStr2)}")
    emitLine("sta  P8ZP_SCRATCH_W2")
    emitLine("lda  ${regAddrHi(regStr2)}")
    emitLine("sta  P8ZP_SCRATCH_W2+1")
    emitLine("lda  ${regAddrLo(regStr1)}")
    emitLine("ldy  ${regAddrHi(regStr1)}")
    emitLine("jsr  prog8_lib.strcmp_mem")
}

private fun AsmGen.translateSyscallBytearrayContains(site: CallSite) {
    val regElem = site.argumentRegister(0, "element reg")
    val regArr = site.argumentRegister(1, "array reg")
    val regLen = site.argumentRegister(2, "length reg")
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

private fun AsmGen.translateSyscallWordarrayContains(site: CallSite) {
    val regElem = site.argumentRegister(0, "element reg")
    val regArr = site.argumentRegister(1, "array reg")
    val regLen = site.argumentRegister(2, "length reg")
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

private fun AsmGen.translateSyscallSplitWordarrayContains(site: CallSite) {
    val regElem = site.argumentRegister(0, "element reg")
    val regArr = site.argumentRegister(1, "array reg")
    val regLen = site.argumentRegister(2, "length reg")
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

private fun AsmGen.translateSyscallLongarrayContains(site: CallSite) {
    val regVal = site.argumentRegister(0, "value reg")
    val regArr = site.argumentRegister(1, "array reg")
    val regLen = site.argumentRegister(2, "length reg")
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
    emitUnconditionalBranch(labelDone)
    emitLabel(labelFound)
    emitLine("lda  #1")
    emitLabel(labelDone)
}

private fun AsmGen.translateSyscallStringContains(site: CallSite) {
    val regChar = site.argumentRegister(0, "character reg")
    val regStr = site.argumentRegister(1, "string reg")
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
    emitUnconditionalBranch(labelDone)
    emitLabel(labelNotFound)
    emitLine("lda  #0")
    emitLabel(labelDone)
}

private fun AsmGen.translateSyscallFloatarrayContains(site: CallSite) {
    val regNeedleFp = site.argumentRegister(0, "needle fp reg")
    val regArr = site.argumentRegister(1, "array reg")
    val regLen = site.argumentRegister(2, "length reg")
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

private fun AsmGen.translateFloatFromInt(insn: IRInstruction) {
    val r1 = insn.requireIntSourceA().intNumber
    val fpReg = insn.requireFloatDest().floatNumber
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

private fun AsmGen.translateFloatToInt(insn: IRInstruction) {
    val r1 = insn.requireIntDest().intNumber
    val fpReg = insn.requireFloatSourceA().floatNumber
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

private fun AsmGen.translateFloatUnary(insn: IRInstruction, routine: String) {
    val src = insn.requireFloatSourceA().floatNumber
    val dst = insn.requireFloatDest().floatNumber
    emitLine("lda  #<${fpRegAddr(src.value)}")
    emitLine("ldy  #>${fpRegAddr(src.value)}")
    emitLine("jsr  floats.MOVFM")
    emitLine("jsr  $routine")
    emitLine("ldx  #<${fpRegAddr(dst.value)}")
    emitLine("ldy  #>${fpRegAddr(dst.value)}")
    emitLine("jsr  floats.MOVMF")
}

private fun AsmGen.translateFloatPower(insn: IRInstruction) {
    val src = insn.requireFloatSourceA().floatNumber
    val dst = insn.requireFloatDest().floatNumber
    // FPOW: dst = dst ^ src
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

private fun AsmGen.translateFloatCeil(insn: IRInstruction) {
    val src = insn.requireFloatSourceA().floatNumber
    val dst = insn.requireFloatDest().floatNumber
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

private fun AsmGen.translateFloatFromSignedLong(insn: IRInstruction) {
    val r1 = insn.requireIntSourceA().intNumber
    val fpReg = insn.requireFloatDest().floatNumber
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

private fun AsmGen.translateFloatToSignedLong(insn: IRInstruction) {
    val r1 = insn.requireIntDest().intNumber
    val fpReg = insn.requireFloatSourceA().floatNumber
    val regAddr = regAddr(r1)
    val facho = "floats.FAC_ADDR+1"    // first mantissa byte after exponent
    // Load float from FP register into FAC1
    emitLine("lda  #<${fpRegAddr(fpReg.value)}")
    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
    emitLine("jsr  floats.MOVFM")
    // QINT converts FAC1 to a signed 32-bit integer in the mantissa bytes.
    // Note: bit 7 of FAC_ADDR+1 (facho) is the hidden mantissa bit, NOT the
    // sign - the sign lives in facsgn (FAC_ADDR+5). QINT handles the sign
    // itself, so no extra sign handling is needed here. This matches the old
    // codegen's cast_as_long routine.
    emitLine("jsr  floats.QINT")
    // Copy the 4-byte (signed) integer result, least significant byte first.
    emitLine("lda  $facho+3")
    emitLine("sta  $regAddr")
    emitLine("lda  $facho+2")
    emitLine("sta  ${regAddr}+1")
    emitLine("lda  $facho+1")
    emitLine("sta  ${regAddr}+2")
    emitLine("lda  $facho")
    emitLine("sta  ${regAddr}+3")
}

private fun AsmGen.translateFloatCompare(insn: IRInstruction) {
    val r1 = insn.requireIntDest().intNumber
    val fr1 = insn.requireFloatSourceA().floatNumber
    val fr2 = insn.requireSrcB().floatNumber
    // Compare fr1 with fr2 using subtraction instead of KERNAL FCOMP.
    // KERNAL FCOMP on some CX16 ROM versions gives wrong results for certain value pairs.
    // Using FSUBT + SIGN is more reliable: compute fr1 - fr2, then check SIGN.
    // SIGN returns: $ff (-1) for negative, $00 for zero, $01 for positive.
    // This matches the FCOMP return convention (-1, 0, 1).
    emitLine("lda  #<${fpRegAddr(fr2.value)}")
    emitLine("ldy  #>${fpRegAddr(fr2.value)}")
    emitLine("jsr  floats.MOVFM")           // FAC1 = fr2
    emitLoadFAC2FromFpReg(fr1.value)         // FAC2 = fr1 (via CONUPK)
    emitLine("jsr  floats.FSUBT")           // FAC1 = FAC2 - FAC1 = fr1 - fr2
    emitLine("jsr  floats.SIGN")            // A = -1, 0, or 1
    emitLine("sta  ${regAddrLo(r1)}")
}

private fun AsmGen.translateIntSqrt(insn: IRInstruction) {
    val src = insn.requireIntSourceA().intNumber
    val dst = insn.requireIntDest().intNumber
    when (insn.type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddr(src)}")
            emitLine("ldy  #0")
            emitLine("jsr  prog8_lib.func_sqrt16_into_A")
            emitLine("sta  ${regAddr(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("ldy  ${regAddrHi(src)}")
            emitLine("jsr  prog8_lib.func_sqrt16_into_A")
            emitLine("ldy  #0")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("sty  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> {
            this.storeToMemory(src, "prog8_lib.sqrt_long.num", IRDataType.LONG)
            emitLine("jsr  prog8_lib.sqrt_long")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("sty  ${regAddrHi(dst)}")
        }
        else -> throw IllegalArgumentException("need only int type here")
    }
}

private fun AsmGen.translateIntSquare(insn: IRInstruction) {
    val src = insn.requireIntSourceA().intNumber
    val dst = insn.requireIntDest().intNumber
    when (insn.type) {
        IRDataType.BYTE -> {
            emitLine("lda  ${regAddr(src)}")
            emitLine("tay")
            emitLine("jsr  prog8_math.multiply_bytes")
            emitLine("sta  ${regAddr(dst)}")
        }
        IRDataType.WORD -> {
            emitLine("lda  ${regAddrLo(src)}")
            emitLine("ldy  ${regAddrHi(src)}")
            emitLine("jsr  prog8_math.square")
            emitLine("sta  ${regAddrLo(dst)}")
            emitLine("sty  ${regAddrHi(dst)}")
        }
        IRDataType.LONG -> {
            this.storeToMemory(src, "cx16.r14", IRDataType.LONG)
            emitLine("jsr  prog8_math.square_long")
            this.loadFromMemory(dst, "cx16.r14", IRDataType.LONG)
        }
        else -> throw IllegalArgumentException("need only int type here")
    }
}

private fun AsmGen.translateFloatSquare(insn: IRInstruction) {
    val src = insn.requireFloatSourceA().floatNumber
    val dst = insn.requireFloatDest().floatNumber
    // dst = src^2 = src * src
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

private fun AsmGen.translateFloatSign(insn: IRInstruction) {
    val r1 = insn.requireIntDest().intNumber
    val fpReg = insn.requireFloatSourceA().floatNumber
    // SGN: dest = sign(src) as integer (-1, 0, 1)
    emitLine("lda  #<${fpRegAddr(fpReg.value)}")
    emitLine("ldy  #>${fpRegAddr(fpReg.value)}")
    emitLine("jsr  floats.MOVFM")
    emitLine("jsr  floats.SIGN")
    emitLine("sta  ${regAddrLo(r1)}")
}

private fun AsmGen.jsrfarRoutine(): String {
    val targetName = program.options.compTarget.name
    return when (targetName) {
        "cx16" -> "cx16.JSRFAR"
        "c64" -> "c64.x16jsrfar"
        "c128" -> "c128.x16jsrfar"
        else -> "$targetName.x16jsrfar"
    }
}
