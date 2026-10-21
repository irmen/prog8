/*
 * Control flow IR instruction translations for the M68k code generator.
 *
 * Status flag returns (Pc/Pz/Pv/Pn) are handled by the IR's branch-based pattern
 * (bsteq/bstneg/bstvs). The backend skips status flag returns in translateCall/translateReturnValue
 * and trusts the IR to emit the appropriate branch instructions.
 *
 * PUSHST/POPST: save/restore CCR via the stack (Scc moves CCR to D0).
 *
 * Handles: JUMP, JUMPI, CALL, CALLI, CALLFAR, CALLFARVB, SYSCALL,
 * RETURN, RETURNR, RETURNI, PUSH, POP, PUSHST, POPST,
 * CLC, SEC, CLI, SEI, ALIGN,
 * byte/word extraction (LSIGB, LSIGW, MSIGB, MSIGW, BSIGB, MIDB),
 * sign extension (EXT, EXTS), and sign (SGN).
 */

package prog8.codegen.m68k

import prog8.code.core.CpuType
import prog8.code.core.toHex
import prog8.code.target.Amiga500Target
import prog8.intermediate.*

internal fun AsmGen.translateControl(insn: IRInstruction, forwardedImmediateCall: ImmediateCallOptimization? = null) {
    when (insn.opcode) {
        Opcode.JUMP -> {
            invalidateD0Cache()
            when (val t = insn.requireTarget()) {
                is CodeReference.Label -> {
                    // a jump to a label in another subroutine ends this activation: our frame must be gone first
                    if (labelIsOutsideCurrentSub(t.name))
                        emitFrameUnlk()
                    emitLine("bra  ${fixNameSymbols(t.name)}")     // PC-relative branch; vasm picks the optimal size and falls back to jmp if out of range
                }
                is CodeReference.Absolute -> {
                    // a fixed address can't be resolved to a chunk of this sub, so assume it leaves for good
                    emitFrameUnlk()
                    emitLine("jmp  ${t.address.value.toHex()}")
                }
                is CodeReference.Indirect -> error("JUMP needs target")
            }
        }

        Opcode.JUMPI -> {
            val ref = insn.requireTarget() as? CodeReference.Indirect ?: error("JUMPI needs an indirect target register")
            val reg = ref.pointer.intNumber
            invalidateD0Cache()
            // Keep the frame for a table whose targets all belong to this subroutine.
            if (!jumpTableStaysInCurrentSub(ref.pointer.register))
                emitFrameUnlk()
            if (program.options.compTarget.cpu >= CpuType.M68020 && VirtualRegister.int(reg) !in regAllocation) {
                // 68020+ memory-indirect addressing is only valid while the operand still lives in memory
                emitLine("jmp  ([${operand(reg)}])")
            } else {
                emitLine("movea.l  ${operand(reg).removeSuffix("+0")}, $SCRATCH_A0")
                emitLine("jmp  ($SCRATCH_A0)")
            }
        }

        Opcode.CALL -> {
            val callSite = insn.requireCallSite()
            val fnLabel = insn.labelTarget?.let { fixNameSymbols(it) }
                ?: (insn.codeTarget as? CodeReference.Absolute)?.address?.value?.toHex()
                ?: error("CALL needs label or address")
            invalidateD0Cache()
            translateCall(fnLabel, callSite, forwardedImmediateCall)
        }

        Opcode.CALLI -> {
            val ref = insn.codeTarget as? CodeReference.Indirect ?: error("CALLI needs an indirect target register")
            val reg = ref.pointer.intNumber
            invalidateD0Cache()
            if (program.options.compTarget.cpu >= CpuType.M68020 && VirtualRegister.int(reg) !in regAllocation) {
                // 68020+ memory-indirect addressing is only valid while the operand still lives in memory
                emitLine("jsr  ([${operand(reg)}])")
            } else {
                emitLine("movea.l  ${operand(reg).removeSuffix("+0")}, $SCRATCH_A0")
                emitLine("jsr  ($SCRATCH_A0)")
            }
        }

        Opcode.CALLFAR -> {
            // On amiga targets CALLFAR encodes a library LVO call: library number, lvo offset, name = jsr Symbol(a6)
            if(program.options.compTarget.name.contains("amiga")) {
                val callSite = insn.requireCallSite()
                val amigaTarget = callSite.target as? CallTarget.AmigaLibrary ?: error("CALLFAR needs an amiga library target")
                val offset = amigaTarget.lvo
                require(offset<0) { "amiga libcall offsets should all be <0" }
                val bank = amigaTarget.library
                if(bank==1) {
                    // exec.library is always at 4.w
                    emitLine("move.l  4.w,a6")
                } else {
                    val library = Amiga500Target.LibraryNumbers.getValue(bank)
                    val baseVar = "sys.${library}Base"
                    emitLine("move.l  $baseVar,a6")
                }

                invalidateD0Cache()
                for(arg in callSite.arguments)
                    translateArgument(arg)
                if(amigaTarget.name!=null) {
                    emitLine("jsr  ${amigaTarget.name}(a6)")    // do the actual call (symbolic ref)
                } else {
                    emitLine("jsr  $offset(a6)")    // do the actual call
                }
                // Skip status flag returns (handled by the IR branch pattern), and slot returns in multi-assign (LOADHR).
                val isMultiReturn = callSite.results.size > 1
                for(ret in callSite.results) {
                    if (ret.location is CallLocation.StatusFlag)
                        continue
                    if (isMultiReturn)
                        continue
                    translateReturnValue(ret)
                }
            }
            else  error("CALLFAR not applicable on this M68k")
        }

        Opcode.CALLFARVB -> {
            error("CALLFARVB not applicable on M68k")
        }

        Opcode.SYSCALL -> {
            val callSite = insn.requireCallSite()
            val num = (callSite.target as CallTarget.SystemCall).number
            invalidateD0Cache()
            if (callSite.arguments.isNotEmpty())
                translateSyscall(num, callSite)
            else
                emitLine("; syscall #$num   (no args)")
        }

        Opcode.RETURN -> {
            invalidateD0Cache()
            emitFrameUnlk()
            emitLine("rts")
        }

        Opcode.RETURNR -> {
            invalidateD0Cache()
            val type = insn.type ?: IRDataType.BYTE
            if (type == IRDataType.FLOAT) {
                val fpReg = insn.requireFloatSourceA().floatNumber
                emitFmoveFloat(floatOperand(fpReg), FP_ACC)
            } else {
                val reg = insn.requireIntSourceA().intNumber
                emitLoadD0(reg, type)
            }
            emitFrameUnlk()
            emitLine("rts")
        }

        Opcode.RETURNI -> {
            invalidateD0Cache()
            val value = insn.immediate?.integerValue ?: 0
            val type = insn.type ?: IRDataType.BYTE
            val s = dtSuffix(type)
            if (value in -128..127)
                emitLine("moveq  #$value, $SCRATCH_D0")
            else
                emitLine("move$s  #$value, $SCRATCH_D0")
            emitFrameUnlk()
            emitLine("rts")
        }

        // === Stack operations ===

        Opcode.PUSH -> {
            invalidateD0Cache()
            val reg = insn.requireIntSourceA().intNumber
            val type = insn.type ?: IRDataType.BYTE
            val s = dtSuffix(type)
            emitLine("move$s  ${operand(reg)}, -(sp)")
        }

        Opcode.POP -> {
            invalidateD0Cache()
            val reg = insn.requireIntDest().intNumber
            val type = insn.type ?: IRDataType.BYTE
            val s = dtSuffix(type)
            emitLine("move$s  (sp)+, ${operand(reg)}")
        }

        // === Status flag stack ops via Scc ===

        Opcode.PUSHST -> {
            // Save CCR to the stack; the 68010+ memory form is register-neutral and word-aligned.
            if(program.options.compTarget.cpu == CpuType.M68000)
                error("the 68000 cpu cannot save/restore the status bits using a nonprivileged instruction. This is required to implement the 'PUSHST/POPST' IR opcodes. Compile for 68010 or higher cpu or change the code such that PUSHST/POPST are no longer used (sometimes complicated rol/ror operations need it)")
            emitLine("move  ccr, -(sp)")
        }

        Opcode.POPST -> {
            // Restore CCR from the stack (register-neutral on 68010+).
            if(program.options.compTarget.cpu == CpuType.M68000)
                error("the 68000 cpu cannot save/restore the status bits using a nonprivileged instruction. This is required to implement the 'PUSHST/POPST' IR opcodes. Compile for 68010 or higher cpu or change the code such that PUSHST/POPST are no longer used (sometimes complicated rol/ror operations need it)")
            emitLine("move  (sp)+, ccr")
        }

        // === Flag manipulation ===

        // X (extend/CCR bit 4) is the rotate-carry and C (bit 0) is for comparisons; CLC/SEC manage both.
        Opcode.CLC -> emitLine($$"andi  #$ee, ccr")   // clear C and X
        Opcode.SEC -> emitLine($$"ori  #$11, ccr")    // set C and X
        Opcode.CLI -> emitLine($$"andi  #$fb, ccr")
        Opcode.SEI -> emitLine($$"ori  #$04, ccr")

        Opcode.ALIGN -> {
            val alignment = insn.immediate?.integerValue ?: 2
            emitLine("ALIGN  $alignment")
        }

        // === Byte/word extraction (no Scc needed) ===

        Opcode.LSIGB -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            val type = insn.type ?: IRDataType.WORD
            directDataMove(dstReg, srcReg)?.let { (d, s) ->
                emitLine("move.b  $s, $d")
                invalidateD0CacheForSlot(dstReg)
                return
            }
            emitLoadD0(srcReg, type)
            emitStoreD0(dstReg, IRDataType.BYTE)
        }

        Opcode.LSIGW -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            directDataMove(dstReg, srcReg)?.let { (d, s) ->
                emitLine("move.w  $s, $d")
                invalidateD0CacheForSlot(dstReg)
                return
            }
            emitLoadD0(srcReg, IRDataType.LONG)
            emitStoreD0(dstReg, IRDataType.WORD)
        }

        Opcode.MSIGB -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            // Big-endian: MSB is at byte offset 0 for both word and long.
            val type = insn.type ?: IRDataType.WORD
            if (VirtualRegister.int(srcReg) in regAllocation) {
                val dd = directDataDst(dstReg, IRDataType.BYTE)
                val mv = directDataMove(dstReg, srcReg)
                if (dd != null && mv != null) {
                    emitLine("move${dtSuffix(type)}  ${mv.second}, $dd")
                    when (type) {
                        IRDataType.WORD -> emitLine("lsr.l  #8, $dd")
                        IRDataType.LONG, IRDataType.POINTER -> {
                            emitLine("move.w  #24, $SCRATCH_D1")
                            emitLine("lsr.l  $SCRATCH_D1, $dd")
                        }
                        else -> {}
                    }
                    invalidateD0CacheForSlot(dstReg)
                    return
                }
                emitLoadD0(srcReg, type)
                when (type) {
                    IRDataType.WORD -> emitLine("lsr.l  #8, $SCRATCH_D0")
                    IRDataType.LONG, IRDataType.POINTER -> {
                        emitLine("move.w  #24, $SCRATCH_D1")
                        emitLine("lsr.l  $SCRATCH_D1, $SCRATCH_D0")
                    }
                    else -> {}
                }
            } else {
                emitLoadD0FromAddress(regAddrByte(srcReg, 0), IRDataType.BYTE)
            }
            emitStoreD0(dstReg, IRDataType.BYTE)
        }

        Opcode.MSIGW -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            // Big-endian: word 0 (offset+0) is most significant. Extract directly.
            val type = insn.type ?: IRDataType.WORD
            if (VirtualRegister.int(srcReg) in regAllocation) {
                val dd = directDataDst(dstReg, IRDataType.WORD)
                val mv = directDataMove(dstReg, srcReg)
                if (dd != null && mv != null) {
                    if (type == IRDataType.LONG || type == IRDataType.POINTER) {
                        emitLine("move.l  ${mv.second}, $dd")
                        emitLine("swap  $dd")
                    } else {
                        emitLine("move.w  ${mv.second}, $dd")
                    }
                    invalidateD0CacheForSlot(dstReg)
                    return
                }
                emitLoadD0(srcReg, type)
                if (type == IRDataType.LONG || type == IRDataType.POINTER)
                    emitLine("swap  $SCRATCH_D0")
            } else {
                emitLoadD0FromAddress(regAddrByte(srcReg, 0), IRDataType.WORD)
            }
            emitStoreD0(dstReg, IRDataType.WORD)
        }

        Opcode.BSIGB -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            // Big-endian: bits 16-23 are at byte offset 1 of a long.
            if (VirtualRegister.int(srcReg) in regAllocation) {
                val dd = directDataDst(dstReg, IRDataType.BYTE)
                val mv = directDataMove(dstReg, srcReg)
                if (dd != null && mv != null) {
                    emitLine("move.l  ${mv.second}, $dd")
                    emitLine("move.w  #16, $SCRATCH_D1")
                    emitLine("lsr.l  $SCRATCH_D1, $dd")
                    invalidateD0CacheForSlot(dstReg)
                    return
                }
                emitLoadD0(srcReg, IRDataType.LONG)
                emitLine("move.w  #16, $SCRATCH_D1")
                emitLine("lsr.l  $SCRATCH_D1, $SCRATCH_D0")
            } else {
                emitLoadD0FromAddress(regAddrByte(srcReg, 1), IRDataType.BYTE)
            }
            emitStoreD0(dstReg, IRDataType.BYTE)
        }

        Opcode.MIDB -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            // Big-endian: bits 8-15 are at byte offset 2 of a long.
            if (VirtualRegister.int(srcReg) in regAllocation) {
                val dd = directDataDst(dstReg, IRDataType.BYTE)
                val mv = directDataMove(dstReg, srcReg)
                if (dd != null && mv != null) {
                    emitLine("move.l  ${mv.second}, $dd")
                    emitLine("lsr.l  #8, $dd")
                    invalidateD0CacheForSlot(dstReg)
                    return
                }
                emitLoadD0(srcReg, IRDataType.LONG)
                emitLine("lsr.l  #8, $SCRATCH_D0")
            } else {
                emitLoadD0FromAddress(regAddrByte(srcReg, 2), IRDataType.BYTE)
            }
            emitStoreD0(dstReg, IRDataType.BYTE)
        }

        // === Sign/zero extension ===

        Opcode.EXT -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            val type = insn.type ?: IRDataType.WORD
            when (type) {
                IRDataType.BYTE -> {
                    // zero-extend byte to word
                    directExtend(dstReg, srcReg, IRDataType.WORD)?.let { d ->
                        emitLine("moveq  #0, $d")
                        emitLine("move.b  ${operand(srcReg)}, $d")
                        invalidateD0CacheForSlot(dstReg)
                        return
                    }
                    invalidateD0Cache()
                    emitLine("moveq  #0, $SCRATCH_D0")
                    emitLoadD0(srcReg, IRDataType.BYTE)
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                IRDataType.WORD -> {
                    // zero-extend word to long
                    directExtend(dstReg, srcReg, IRDataType.LONG)?.let { d ->
                        emitLine("moveq  #0, $d")
                        emitLine("move.w  ${operand(srcReg)}, $d")
                        invalidateD0CacheForSlot(dstReg)
                        return
                    }
                    invalidateD0Cache()
                    emitLine("moveq  #0, $SCRATCH_D0")
                    emitLoadD0(srcReg, IRDataType.WORD)
                    emitStoreD0(dstReg, IRDataType.LONG)
                }
                IRDataType.LONG -> {
                    // no extension needed
                    directDataMove(dstReg, srcReg)?.let { (d, s) ->
                        emitLine("move.l  $s, $d")
                        invalidateD0CacheForSlot(dstReg)
                        return
                    }
                    emitLoadD0(srcReg, IRDataType.LONG)
                    emitStoreD0(dstReg, IRDataType.LONG)
                }
                else -> TODO("EXT for ${type.name}")
            }
        }

        Opcode.EXTS -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            val type = insn.type ?: IRDataType.WORD
            when (type) {
                IRDataType.BYTE -> {
                    // sign-extend byte to word
                    directExtend(dstReg, srcReg, IRDataType.WORD)?.let { d ->
                        emitLine("move.b  ${operand(srcReg)}, $d")
                        emitLine("ext.w  $d")
                        invalidateD0CacheForSlot(dstReg)
                        return
                    }
                    emitLoadD0(srcReg, IRDataType.BYTE)
                    emitLine("ext.w  $SCRATCH_D0")
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                IRDataType.WORD -> {
                    // sign-extend word to long
                    directExtend(dstReg, srcReg, IRDataType.LONG)?.let { d ->
                        emitLine("move.w  ${operand(srcReg)}, $d")
                        emitLine("ext.l  $d")
                        invalidateD0CacheForSlot(dstReg)
                        return
                    }
                    emitLoadD0(srcReg, IRDataType.WORD)
                    emitLine("ext.l  $SCRATCH_D0")
                    emitStoreD0(dstReg, IRDataType.LONG)
                }
                IRDataType.LONG -> {
                    // no extension needed
                    directDataMove(dstReg, srcReg)?.let { (d, s) ->
                        emitLine("move.l  $s, $d")
                        invalidateD0CacheForSlot(dstReg)
                        return
                    }
                    emitLoadD0(srcReg, IRDataType.LONG)
                    emitStoreD0(dstReg, IRDataType.LONG)
                }
                else -> TODO("EXTS for ${type.name}")
            }
        }

        Opcode.EXTL -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            // zero-extend byte to long: move.b only writes the low byte, so clear d0 first
            directExtend(dstReg, srcReg, IRDataType.LONG)?.let { d ->
                emitLine("moveq  #0, $d")
                emitLine("move.b  ${operand(srcReg)}, $d")
                invalidateD0CacheForSlot(dstReg)
                return
            }
            invalidateD0Cache()
            emitLine("moveq  #0, $SCRATCH_D0")
            emitLoadD0(srcReg, IRDataType.BYTE)
            emitStoreD0(dstReg, IRDataType.LONG)
        }

        Opcode.EXTLS -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            // sign-extend byte to long
            directExtend(dstReg, srcReg, IRDataType.LONG)?.let { d ->
                emitLine("move.b  ${operand(srcReg)}, $d")
                emitSignExtendByteToLong(d)
                invalidateD0CacheForSlot(dstReg)
                return
            }
            emitLoadD0(srcReg, IRDataType.BYTE)
            emitSignExtendByteToLong(SCRATCH_D0)
            emitStoreD0(dstReg, IRDataType.LONG)
        }

        // === Concatenation ===

        Opcode.CONCAT -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg2 = insn.requireIntSourceA().intNumber
            val srcReg3 = insn.requireSrcB().intNumber
            val type = insn.type ?: IRDataType.BYTE
            when (type) {
                IRDataType.BYTE -> {
                    // r1 = WORD(r2 as MSB, r3 as LSB)
                    val d = directDataDst(dstReg, IRDataType.WORD)
                    val s2 = directMoveSrc(srcReg2)
                    val s3 = directMoveSrc(srcReg3)
                    if (d != null && s2 != null && s3 != null) {
                        check(s3 != d) { "concat source and destination share $d" }  // s3 is read after d is written
                        emitLine("move.b  $s2, $d")
                        emitLine("lsl.w  #8, $d")
                        emitLine("or.b  $s3, $d")
                        invalidateD0CacheForSlot(dstReg)
                        return
                    }
                    emitLoadD0(srcReg2, IRDataType.BYTE)
                    emitLine("lsl.w  #8, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitLine("or.b  ${operand(srcReg3)}, $SCRATCH_D0")
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                IRDataType.WORD -> {
                    // r1 = LONG(r2 as MSW, r3 as LSW)
                    val d = directDataDst(dstReg, IRDataType.LONG)
                    val s2 = directMoveSrc(srcReg2)
                    val s3 = directMoveSrc(srcReg3)
                    if (d != null && s2 != null && s3 != null) {
                        check(s3 != d) { "concat source and destination share $d" }  // s3 is read after d is written
                        emitLine("move.w  $s2, $d")
                        emitLine("swap  $d")
                        emitLine("clr.w  $d")
                        emitLine("or.w  $s3, $d")
                        invalidateD0CacheForSlot(dstReg)
                        return
                    }
                    emitLoadD0(srcReg2, IRDataType.WORD)
                    emitLine("swap  $SCRATCH_D0")
                    invalidateD0Cache()
                    emitLine("clr.w  $SCRATCH_D0")
                    emitLine("or.w  ${operand(srcReg3)}, $SCRATCH_D0")
                    emitStoreD0(dstReg, IRDataType.LONG)
                }
                else -> TODO("CONCAT for ${type.name}")
            }
        }

        // === Math ===

        Opcode.SGN -> {
            val dstReg = insn.requireIntDest().intNumber
            if (insn.type == IRDataType.FLOAT) {
                val srcFp = insn.requireFloatSourceA().floatNumber
                invalidateD0Cache()
                emitLine("ftst.s  ${floatOperand(srcFp)}")
                emitLine("fslt  $SCRATCH_D0")
                emitLine("fsgt  $SCRATCH_D1")
                emitLine("neg.b  $SCRATCH_D1")
                emitLine("or.b  $SCRATCH_D1, $SCRATCH_D0")
                emitStoreD0(dstReg, IRDataType.BYTE)
            } else {
                val srcReg = insn.requireIntSourceA().intNumber
                val type = insn.type ?: IRDataType.BYTE
                val s = dtSuffix(type)
                val zeroLabel = makeLabel("sgn_zero")
                val doneLabel = makeLabel("sgn_done")
                val dd = directDataDst(dstReg, IRDataType.BYTE)
                val ms = directMoveSrc(srcReg)
                if (dd != null && ms != null) {
                    emitLine("move$s  $ms, $dd")
                    emitLine("tst$s  $dd")
                    emitLine("beq  $zeroLabel")
                    emitLine("smi  $SCRATCH_D1")
                    emitLine("bmi  $doneLabel")
                    emitLine("moveq  #1, $SCRATCH_D1")
                    emitLine("bra  $doneLabel")
                    emitLabel(zeroLabel)
                    emitLine("moveq  #0, $SCRATCH_D1")
                    emitLabel(doneLabel)
                    emitLine("move.b  $SCRATCH_D1, $dd")
                    invalidateD0CacheForSlot(dstReg)
                } else {
                    emitLoadD0(srcReg, type)
                    emitLine("tst$s  $SCRATCH_D0")
                    emitLine("beq  $zeroLabel")
                    emitLine("smi  $SCRATCH_D1")
                    emitLine("bmi  $doneLabel")
                    emitLine("moveq  #1, $SCRATCH_D1")
                    emitLine("bra  $doneLabel")
                    emitLabel(zeroLabel)
                    emitLine("moveq  #0, $SCRATCH_D1")
                    emitLabel(doneLabel)
                    emitLine("move.b  $SCRATCH_D1, ${operand(dstReg)}")
                    invalidateD0CacheForSlot(dstReg)    // raw store bypasses emitStoreD0
                }
            }
        }

        // === Floating point operations via 68881/68882 FPU ===

        Opcode.FFROMUB -> {
            val fpDst = insn.requireFloatDest().floatNumber
            val srcReg = insn.requireIntSourceA().intNumber
            emitLoadD0(srcReg, IRDataType.BYTE)
            emitLine("and.l  #\$ff, $SCRATCH_D0")
            invalidateD0Cache()
            emitLine("fmove.l  $SCRATCH_D0, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(fpDst))
        }

        Opcode.FFROMSB -> {
            val fpDst = insn.requireFloatDest().floatNumber
            val srcReg = insn.requireIntSourceA().intNumber
            emitLoadD0(srcReg, IRDataType.BYTE)
            emitSignExtendByteToLong(SCRATCH_D0)
            emitLine("fmove.l  $SCRATCH_D0, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(fpDst))
        }

        Opcode.FFROMUW -> {
            val fpDst = insn.requireFloatDest().floatNumber
            val srcReg = insn.requireIntSourceA().intNumber
            emitLoadD0(srcReg, IRDataType.WORD)
            emitLine("and.l  #\$ffff, $SCRATCH_D0")
            invalidateD0Cache()
            emitLine("fmove.l  $SCRATCH_D0, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(fpDst))
        }

        Opcode.FFROMSW -> {
            val fpDst = insn.requireFloatDest().floatNumber
            val srcReg = insn.requireIntSourceA().intNumber
            emitLoadD0(srcReg, IRDataType.WORD)
            emitLine("ext.l  $SCRATCH_D0")
            emitLine("fmove.l  $SCRATCH_D0, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(fpDst))
        }

        Opcode.FFROMSL -> {
            val fpDst = insn.requireFloatDest().floatNumber
            val srcReg = insn.requireIntSourceA().intNumber
            emitLoadD0(srcReg, IRDataType.LONG)
            emitLine("fmove.l  $SCRATCH_D0, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(fpDst))
        }

        Opcode.FTOUB -> {
            val dstReg = insn.requireIntDest().intNumber
            val fpSrc = insn.requireFloatSourceA().floatNumber
            emitFmoveFloat(floatOperand(fpSrc), FP_ACC)
            invalidateD0Cache()
            emitLine("fmove.b  $FP_ACC, $SCRATCH_D0")
            emitLine("and.l  #\$ff, $SCRATCH_D0")
            emitStoreD0(dstReg, IRDataType.BYTE)
        }

        Opcode.FTOSB -> {
            val dstReg = insn.requireIntDest().intNumber
            val fpSrc = insn.requireFloatSourceA().floatNumber
            emitFmoveFloat(floatOperand(fpSrc), FP_ACC)
            invalidateD0Cache()
            emitLine("fmove.b  $FP_ACC, $SCRATCH_D0")
            emitStoreD0(dstReg, IRDataType.BYTE)
        }

        Opcode.FTOUW -> {
            val dstReg = insn.requireIntDest().intNumber
            val fpSrc = insn.requireFloatSourceA().floatNumber
            emitFmoveFloat(floatOperand(fpSrc), FP_ACC)
            invalidateD0Cache()
            emitLine("fmove.w  $FP_ACC, $SCRATCH_D0")
            emitStoreD0(dstReg, IRDataType.WORD)
        }

        Opcode.FTOSW -> {
            val dstReg = insn.requireIntDest().intNumber
            val fpSrc = insn.requireFloatSourceA().floatNumber
            emitFmoveFloat(floatOperand(fpSrc), FP_ACC)
            invalidateD0Cache()
            emitLine("fmove.w  $FP_ACC, $SCRATCH_D0")
            emitStoreD0(dstReg, IRDataType.WORD)
        }

        Opcode.FTOSL -> {
            val dstReg = insn.requireIntDest().intNumber
            val fpSrc = insn.requireFloatSourceA().floatNumber
            emitFmoveFloat(floatOperand(fpSrc), FP_ACC)
            invalidateD0Cache()
            emitLine("fmove.l  $FP_ACC, $SCRATCH_D0")
            emitStoreD0(dstReg, IRDataType.LONG)
        }

        Opcode.FABS -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            directFloatReg(dst)?.let { d ->
                emitFloatOpSrc("fabs", floatOperand(src), d)
                return
            }
            emitFmoveFloat(floatOperand(src), FP_ACC)
            emitLine("fabs  $FP_ACC, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(dst))
        }

        Opcode.FSIN -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            directFloatReg(dst)?.let { d ->
                emitFloatOpSrc("fsin", floatOperand(src), d)
                return
            }
            emitFmoveFloat(floatOperand(src), FP_ACC)
            emitLine("fsin  $FP_ACC, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(dst))
        }

        Opcode.FCOS -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            directFloatReg(dst)?.let { d ->
                emitFloatOpSrc("fcos", floatOperand(src), d)
                return
            }
            emitFmoveFloat(floatOperand(src), FP_ACC)
            emitLine("fcos  $FP_ACC, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(dst))
        }

        Opcode.FTAN -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            directFloatReg(dst)?.let { d ->
                emitFloatOpSrc("ftan", floatOperand(src), d)
                return
            }
            emitFmoveFloat(floatOperand(src), FP_ACC)
            emitLine("ftan  $FP_ACC, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(dst))
        }

        Opcode.FATAN -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            directFloatReg(dst)?.let { d ->
                emitFloatOpSrc("fatan", floatOperand(src), d)
                return
            }
            emitFmoveFloat(floatOperand(src), FP_ACC)
            emitLine("fatan  $FP_ACC, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(dst))
        }

        Opcode.FPOW -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            directFloatReg(dst)?.let { d ->
                emitFloatOpSrc("fpow", floatOperand(src), d)
                return
            }
            emitFmoveFloat(floatOperand(src), FP_ACC)
            emitLine("fpow  $FP_ACC, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(dst))
        }

        Opcode.FLN -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            directFloatReg(dst)?.let { d ->
                emitFloatOpSrc("flogn", floatOperand(src), d)
                return
            }
            emitFmoveFloat(floatOperand(src), FP_ACC)
            emitLine("flogn  $FP_ACC, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(dst))
        }

        Opcode.FLOG -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            directFloatReg(dst)?.let { d ->
                emitFloatOpSrc("flog2", floatOperand(src), d)
                return
            }
            emitFmoveFloat(floatOperand(src), FP_ACC)
            emitLine("flog2  $FP_ACC, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(dst))
        }

        Opcode.FROUND -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            directFloatReg(dst)?.let { d ->
                emitFloatOpSrc("fround", floatOperand(src), d)
                return
            }
            emitFmoveFloat(floatOperand(src), FP_ACC)
            emitLine("fround  $FP_ACC, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(dst))
        }

        Opcode.FFLOOR -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            directFloatReg(dst)?.let { d ->
                emitFloatOpSrc("ffloor", floatOperand(src), d)
                return
            }
            emitFmoveFloat(floatOperand(src), FP_ACC)
            emitLine("ffloor  $FP_ACC, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(dst))
        }

        Opcode.FCEIL -> {
            // 68881 has no fceil; implement as: if x == int(x) then x else x > 0 ? int(x)+1 : int(x)
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            val isIntLabel = makeLabel("fceil_is_int")
            val doneLabel = makeLabel("fceil_done")
            val posLabel = makeLabel(".fceil_pos")
            emitFmoveFloat(floatOperand(src), FP_ACC)
            emitFmoveFloat(FP_ACC, FP_SRC)
            emitLine("fintrz  $FP_SRC, $FP_SRC")  // truncate toward zero
            emitLine("fcmp  $FP_ACC, $FP_SRC")
            emitLine("fbeq  $isIntLabel")              // if equal, already integer
            emitLine("ftst  $FP_ACC")
            emitLine("fbgt  $posLabel")                // if >0, need to add 1
            emitLine("bra  $doneLabel")
            emitLabel(posLabel)
            emitLine("fadd.s  #1.0, $FP_SRC")
            emitLine("bra  $doneLabel")
            emitLabel(isIntLabel)
            // dst already holds the integer (from fintrz)
            emitLabel(doneLabel)
            emitFmoveFloat(FP_SRC, floatOperand(dst))
        }

        Opcode.FCOMP -> {
            val dstReg = insn.requireIntDest().intNumber
            val fr1 = insn.requireFloatSourceA().floatNumber
            val fr2 = insn.requireSrcB().floatNumber
            val eqLabel = makeLabel("fcomp_eq")
            val doneLabel = makeLabel("fcomp_done")
            val gtLabel = makeLabel("fcomp_gt")
            invalidateD0Cache()
            emitFmoveFloat(floatOperand(fr1), FP_ACC)
            emitFloatOpSrc("fcmp", floatOperand(fr2), FP_ACC)
            emitLine("fbeq  $eqLabel")
            emitLine("fbgt  $gtLabel")
            val acc = directDataDst(dstReg, IRDataType.BYTE)
            if (acc != null) {
                emitLine("moveq  #-1, $acc")
                emitLine("bra  $doneLabel")
                emitLabel(gtLabel)
                emitLine("moveq  #1, $acc")
                emitLine("bra  $doneLabel")
                emitLabel(eqLabel)
                emitLine("moveq  #0, $acc")
                emitLabel(doneLabel)
                invalidateD0CacheForSlot(dstReg)
            } else {
                emitLine("moveq  #-1, $SCRATCH_D0")
                emitLine("bra  $doneLabel")
                emitLabel(gtLabel)
                emitLine("moveq  #1, $SCRATCH_D0")
                emitLine("bra  $doneLabel")
                emitLabel(eqLabel)
                emitLine("moveq  #0, $SCRATCH_D0")
                emitLabel(doneLabel)
                emitStoreD0(dstReg, IRDataType.BYTE)
            }
        }

        Opcode.LOADHFACZERO, Opcode.LOADHFACONE, Opcode.STOREHFACZERO, Opcode.STOREHFACONE ->
            error("${insn.opcode} should have been handled by translateLoadStore")

        else -> error("Unknown control opcode: ${insn.opcode}")
    }
}

// === CALL translation with argument handling ===

private fun AsmGen.translateCall(fnLabel: String, callSite: CallSite, forwardedImmediateCall: ImmediateCallOptimization? = null) {
    if (fnLabel == "sys.memcopy" && emitInlineMemcopyCall(callSite, forwardedImmediateCall))
        return

    // Emit non-constant arguments first, then constant immediates just before the call to avoid clobbering.
    val paramNames = program.allSubs().find { it.label == fnLabel }?.parameters?.map { it.name } ?: emptyList()
    fun paramNameFor(index: Int): String? =
        paramNames.getOrNull(index)?.substringAfterLast('.')?.removePrefix("p8v_")
    val forwardedRegs = forwardedImmediateCall?.loads?.keys ?: emptySet()
    for ((index, arg) in callSite.arguments.withIndex()) {
        if (arg.source.register in forwardedRegs) continue
        translateArgument(arg, fnLabel, forwardedImmediateCall, paramNameFor(index))
    }
    for ((index, arg) in callSite.arguments.withIndex()) {
        if (arg.source.register !in forwardedRegs) continue
        translateArgument(arg, fnLabel, forwardedImmediateCall, paramNameFor(index))
    }

    // Check if this call targets an inline asmsub — emit its body directly instead of jsr
    val inlineTarget = program.allAsmSubs().find { it.label == fnLabel && it.isInline }
    if (inlineTarget != null) {
        emitRaw(inlineTarget.asmChunk.assembly)
    } else {
        // PC-relative call; vasm picks the optimal size and falls back to jsr if out of range
        emitLine("bsr  $fnLabel")
    }

    // caller cleanup of the all-stack argument area (each argument was pushed into its own word-packed slot)
    val pushedSlots = callSite.arguments.count { it.location is CallLocation.FrameSlot }
    if (pushedSlots > 0) {
        val totalSize = callSite.arguments
            .filter { it.location is CallLocation.FrameSlot }
            .sumOf { incomingSlotSize(it.source.type, target) }
        val needsCcr = callSite.results.any { it.location is CallLocation.StatusFlag }
        when {
            // addq is compact but updates CCR; lea preserves it so status-flag returns stay visible.
            totalSize in 1..8 && !needsCcr -> emitLine("addq.l  #$totalSize,sp")
            else -> emitLine("lea  $totalSize(sp),sp")
        }
    }

    // Skip status flag returns (IR branch pattern) and, in multi-assign, slot returns (LOADHR would clobber flags).
    val isMultiReturn = callSite.results.size > 1
    for (ret in callSite.results) {
        if (ret.location is CallLocation.StatusFlag)
            continue
        if (isMultiReturn)
            continue
        translateReturnValue(ret)
    }
}

private fun AsmGen.emitInlineMemcopyCall(callSite: CallSite, forwardedImmediateCall: ImmediateCallOptimization?): Boolean {
    if (callSite.arguments.size != 3) return false
    val fwd = forwardedImmediateCall ?: return false
    val srcArg = callSite.arguments[0]
    val tgtArg = callSite.arguments[1]
    val countArg = callSite.arguments[2]
    // size must be a constant immediate forwarded to the call; non-constant -> always call
    val countLoad = fwd.loads[countArg.source.register] ?: return false
    val count = countLoad.immediate?.integerValue ?: return false
    if (count <= 0) return true

    // Only .w/.l when both pointers are provably aligned; computed addresses fall back to byte copies.
    val srcImm = fwd.loads[srcArg.source.register]?.immediate?.integerValue
    val tgtImm = fwd.loads[tgtArg.source.register]?.immediate?.integerValue
    val bothEven = srcImm != null && tgtImm != null && srcImm % 2 == 0 && tgtImm % 2 == 0
    val bothLongAligned = srcImm != null && tgtImm != null && srcImm % 4 == 0 && tgtImm % 4 == 0
    val useLong = bothLongAligned && count % 4 == 0
    val useWord = bothEven && count % 2 == 0
    // Heuristics: inline small copies, dbra loop for the medium range; memcopy/CopyMem wins for large ones.
    val threshold = when {
        useLong -> 64
        useWord -> 32
        else -> 16
    }
    if (count > threshold) return false
    // Load pointers into a0/a1 (benefit from forwarded immediates)
    translateArgument(srcArg, "sys.memcopy", fwd)
    translateArgument(tgtArg, "sys.memcopy", fwd)
    // count is constant, no need to load d0
    // For larger small counts a dbra loop is smaller than unrolled moves (~10 bytes setup vs ~5 moves).
    val unrolledLimit = 5
    when {
        useLong -> {
            val longs = count / 4
            emitRaw("        ; inline memcopy $count bytes as $longs longwords")
            if (longs <= unrolledLimit) {
                repeat(longs) { emitLine("move.l  ($SCRATCH_A0)+,($SCRATCH_A1)+") }
            } else {
                emitInlineCopyLoop(longs, 4)
            }
        }
        useWord -> {
            val words = count / 2
            emitRaw("        ; inline memcopy $count bytes as $words words")
            if (words <= unrolledLimit) {
                repeat(words) { emitLine("move.w  ($SCRATCH_A0)+,($SCRATCH_A1)+") }
            } else {
                emitInlineCopyLoop(words, 2)
            }
        }
        else -> {
            emitRaw("        ; inline memcopy $count bytes")
            if (count <= unrolledLimit) {
                repeat(count) { emitLine("move.b  ($SCRATCH_A0)+,($SCRATCH_A1)+") }
            } else {
                emitInlineCopyLoop(count, 1)
            }
        }
    }
    return true
}

private fun AsmGen.emitInlineCopyLoop(iterations: Int, size: Int) {
    val loopLabel = makeLabel("memcpy_inline")
    val moveInsn = when (size) {
        4 -> "move.l"
        2 -> "move.w"
        else -> "move.b"
    }
    emitLine("moveq  #$iterations, $SCRATCH_D0")
    emitLabel(loopLabel)
    emitLine("$moveInsn  ($SCRATCH_A0)+,($SCRATCH_A1)+")
    emitLine("dbra  $SCRATCH_D0,$loopLabel")
}

private fun AsmGen.translateArgument(
    arg: CallArgument,
    fnLabel: String? = null,
    forwardedImmediateCall: ImmediateCallOptimization? = null,
    paramName: String? = null
) {
    val argReg = arg.source
    val forwarded = forwardedImmediateCall?.loads?.get(argReg.register)

    // If the argument has a calling convention slot, load it into that hardware register
    val slot = (arg.location as? CallLocation.HardwareRegister)?.slot
    if (slot != null) {
        val hwReg = m68kSlotRegister(slot)
        if (argReg.isFloat) {
            if (forwarded != null) {
                emitFloadConstantTo(hwReg, forwarded.requireImmediateFloat())
            } else {
                emitFmoveFloat(floatOperand(argReg.floatNumber), hwReg)
            }
        } else {
            if (forwarded != null) {
                val value = forwarded.requireImmediateInt()
                val s = dtSuffix(argReg.type)
                if (hwReg.startsWith("a")) {
                    // address registers only accept movea/suba, not clr or moveq
                    when (value) {
                        0 -> emitLine("suba.l  $hwReg, $hwReg")
                        else -> emitLine("movea.l  #$value, $hwReg")
                    }
                } else {
                    if (value == 0)
                        emitLine("clr$s  $hwReg")
                    else {
                        // use moveq (2 bytes, 4 cycles) when the value fits its signed 8-bit range; bytes 128-255 map to -128..-1
                        val moveqValue = if (argReg.type == IRDataType.BYTE && value in 128..255) value - 256 else value
                        if (moveqValue in -128..127)
                            emitLine("moveq  #$moveqValue, $hwReg")
                        else
                            emitLine("move$s  #$value, $hwReg")
                    }
                }
            } else {
                val source = operand(argReg.intNumber)
                if (hwReg.startsWith("a") && argReg.type in setOf(IRDataType.LONG, IRDataType.POINTER)) {
                    // loading a pointer into an address register; movea.l is the proper form and skips the +0 offset
                    emitLine("movea.l  ${source.removeSuffix("+0")}, $hwReg")
                } else {
                    val s = dtSuffix(argReg.type)
                    emitLine("move$s  $source, $hwReg")
                }
            }
        }
    } else {
        // Stack argument: push into the parameter's word-packed slot, landing at the slot's base byte.
        if (arg.location is CallLocation.FrameSlot) {
            if (argReg.isFloat) {
                // the FPU has no absolute-long addressing mode, so the value travels via fp0; the push reserves the slot
                emitFmoveFloat(floatOperand(argReg.floatNumber), FP_ACC)
                emitLine("fmove.s  $FP_ACC,-(sp)", paramName.orEmpty())
            } else {
                when (argReg.type) {
                    IRDataType.BYTE ->
                        // on the stack pointer a byte predecrement steps by 2, so this reserves the 2-byte slot in one go
                        emitLine("move.b  ${operand(argReg.intNumber)},-(sp)",
                            (if (paramName != null) "$paramName " else "") + "(note: pushed as a word)")
                    // a 2-byte predecrement push reserves and stores the word slot in one instruction
                    IRDataType.WORD ->
                        emitLine("move.w  ${operand(argReg.intNumber)},-(sp)", paramName.orEmpty())
                    // longs and pointers fill the whole slot, so reserve and store in one push
                    else -> emitLine("move.l  ${operand(argReg.intNumber)},-(sp)", paramName.orEmpty())
                }
            }
            return
        }
        // Store to the callee's parameter variable (if this is a named param)
        val paramName = (arg.location as? CallLocation.ParameterMemory)?.name.orEmpty()
        if (paramName.isNotEmpty() && fnLabel != null) {
            val target = fixNameSymbols("$fnLabel.$paramName")
            if (argReg.isFloat) {
                emitFmoveFloat(floatOperand(argReg.floatNumber), FP_ACC)
                emitLine("fmove.s  $FP_ACC, $target")
            } else {
                val s = dtSuffix(argReg.type)
                emitLine("move$s  ${operand(argReg.intNumber)}, $target")
            }
        }
    }
}

private fun AsmGen.translateReturnValue(ret: CallResult) {
    // Status flag returns are handled by the IR's branch pattern; emitting Scc here would clobber the flags.
    if (ret.location is CallLocation.StatusFlag)
        return

    val destination = ret.destination ?: return

    // Otherwise, return value is in a hardware register
    val slot = (ret.location as? CallLocation.HardwareRegister)?.slot
    if (slot != null) {
        val hwReg = m68kSlotRegister(slot)
        if (destination.isFloat) {
            // the vreg already lives in its slot register: no move needed.
            if (floatOperand(destination.floatNumber) == hwReg)
                return
            emitFmoveFloat(hwReg, floatOperand(destination.floatNumber))
        } else {
            // the vreg already lives in its slot register: no move needed.
            if (operand(destination.intNumber) == hwReg)
                return
            emitMoveToIntRegister(hwReg, destination.intNumber, destination.type)
        }
    } else {
        // Default: return value in d0; all non-float returns use data registers, explicit @A0 serves hot paths.
        if (destination.isFloat) {
            emitFmoveFloat(FP_ACC, floatOperand(destination.floatNumber))
        } else {
            emitStoreD0(destination.intNumber, destination.type)
        }
    }
}

// === Slot to M68k hardware register mapping ===

// Slots 0..7 are the 6502/cx16-style scalar registers (A, X, Y, AX, AY, XY, FAC1, FAC2) used by
// target-independent builtins (e.g. divmod returns its quotient in AY); map them onto distinct
// M68k hardware registers so the backend can emit code for them.
fun m68kSlotRegister(slot: CallingConventionSlot): String = M68kRegisterConvention.fixedRegister(slot)
