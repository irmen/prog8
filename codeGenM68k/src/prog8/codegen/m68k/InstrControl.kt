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
                is CodeReference.Label -> emitLine("bra  ${fixNameSymbols(t.name)}")     // PC-relative branch; vasm picks the optimal size and falls back to jmp if out of range
                is CodeReference.Absolute -> emitLine("jmp  ${t.address.value.toHex()}")
                is CodeReference.Indirect -> error("JUMP needs target")
            }
        }

        Opcode.JUMPI -> {
            val ref = insn.requireTarget() as? CodeReference.Indirect ?: error("JUMPI needs an indirect target register")
            val reg = ref.pointer.intNumber
            invalidateD0Cache()
            if(program.options.compTarget.cpu >= CpuType.M68020) {
                // 68020+ supports memory-indirect addressing: fetch the target address from memory directly.
                emitLine("jmp  ([${regAddr(reg)}])")
            } else {
                emitLine("movea.l  ${regAddr(reg).removeSuffix("+0")}, a0")
                emitLine("jmp  (a0)")
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
            if(program.options.compTarget.cpu >= CpuType.M68020) {
                // 68020+ supports memory-indirect addressing: fetch the target address from memory directly.
                emitLine("jsr  ([${regAddr(reg)}])")
            } else {
                emitLine("movea.l  ${regAddr(reg).removeSuffix("+0")}, a0")
                emitLine("jsr  (a0)")
            }
        }

        Opcode.CALLFAR -> {
            // On the amiga target, CALLFAR is repurposed to encode automatic library LVO calls:
            // library = library number (1=exec, 2=dos, 3=graphics, 4=intuition, ...)
            // lvo = negative LVO offset (the offset into the library's jump table)
            // name = symbolic function name, emitted as the displacement in jsr Symbol(a6)
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
                // Skip status flag returns: handled by IR branch pattern.
                // In multi-assign context (returns.size > 1), also skip slot-based returns:
                // the IR generates LOADHR for them.
                // In single-return context, process slot returns normally.
                // LIMITATION: multiple status flag returns in one multi-assign are not supported
                // (codegen limitation: first flag extraction clobbers flags before second can be read).
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
            emitLine("rts")
        }

        Opcode.RETURNR -> {
            invalidateD0Cache()
            val type = insn.type ?: IRDataType.BYTE
            if (type == IRDataType.FLOAT) {
                val fpReg = insn.requireFloatSourceA().floatNumber
                emitLine("fmove.s  ${floatRegFileAddr(fpReg)}, $FP_ACC")
            } else {
                val reg = insn.requireIntSourceA().intNumber
                emitLoadD0(reg, type)
            }
            emitLine("rts")
        }

        Opcode.RETURNI -> {
            invalidateD0Cache()
            val value = insn.immediate?.integerValue ?: 0
            val type = insn.type ?: IRDataType.BYTE
            val s = dtSuffix(type)
            if (value in -128..127)
                emitLine("moveq  #$value, d0")
            else
                emitLine("move$s  #$value, d0")
            emitLine("rts")
        }

        // === Stack operations ===

        Opcode.PUSH -> {
            invalidateD0Cache()
            val reg = insn.requireIntSourceA().intNumber
            val type = insn.type ?: IRDataType.BYTE
            val s = dtSuffix(type)
            emitLine("move$s  ${regAddr(reg)}, -(sp)")
        }

        Opcode.POP -> {
            invalidateD0Cache()
            val reg = insn.requireIntDest().intNumber
            val type = insn.type ?: IRDataType.BYTE
            val s = dtSuffix(type)
            emitLine("move$s  (sp)+, ${regAddr(reg)}")
        }

        // === Status flag stack ops via Scc ===

        Opcode.PUSHST -> {
            // Move CCR to D0 (low byte), then push as byte.
            if(program.options.compTarget.cpu == CpuType.M68000)
                error("the 68000 cpu cannot save/restore the status bits using a nonprivileged instruction. This is required to implement the 'PUSHST/POPST' IR opcodes. Compile for 68010 or higher cpu or change the code such that PUSHST/POPST are no longer used (sometimes complicated rol/ror operations need it)")
            invalidateD0Cache()
            emitLine("move  ccr, d0")
            emitLine("move.b  d0, -(sp)")
        }

        Opcode.POPST -> {
            // Pop byte into D0, then restore CCR
            if(program.options.compTarget.cpu == CpuType.M68000)
                error("the 68000 cpu cannot save/restore the status bits using a nonprivileged instruction. This is required to implement the 'PUSHST/POPST' IR opcodes. Compile for 68010 or higher cpu or change the code such that PUSHST/POPST are no longer used (sometimes complicated rol/ror operations need it)")
            invalidateD0Cache()
            emitLine("move.b  (sp)+, d0")
            emitLine("move  d0, ccr")
        }

        // === Flag manipulation ===

        // On M68k, X (extend/CCR bit 4) serves as the rotate-carry,
        // while C (carry/CCR bit 0) is used for comparisons.
        // CLC/SEC must manage both to keep them in sync.
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
            emitLoadD0(srcReg, type)
            emitStoreD0(dstReg, IRDataType.BYTE)
        }

        Opcode.LSIGW -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            emitLoadD0(srcReg, IRDataType.LONG)
            emitStoreD0(dstReg, IRDataType.WORD)
        }

        Opcode.MSIGB -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            // Big-endian: MSB is at byte offset 0 for both word and long.
            emitLoadD0FromAddress(regAddrByte(srcReg, 0), IRDataType.BYTE)
            emitStoreD0(dstReg, IRDataType.BYTE)
        }

        Opcode.MSIGW -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            // Big-endian: word 0 (offset+0) is most significant. Extract directly.
            emitLoadD0FromAddress(regAddrByte(srcReg, 0), IRDataType.WORD)
            emitStoreD0(dstReg, IRDataType.WORD)
        }

        Opcode.BSIGB -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            // Big-endian: bits 16-23 are at byte offset 1 of a long.
            emitLoadD0FromAddress(regAddrByte(srcReg, 1), IRDataType.BYTE)
            emitStoreD0(dstReg, IRDataType.BYTE)
        }

        Opcode.MIDB -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            // Big-endian: bits 8-15 are at byte offset 2 of a long.
            emitLoadD0FromAddress(regAddrByte(srcReg, 2), IRDataType.BYTE)
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
                    invalidateD0Cache()
                    emitLine("moveq  #0, d0")
                    emitLoadD0(srcReg, IRDataType.BYTE)
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                IRDataType.WORD -> {
                    // zero-extend word to long
                    invalidateD0Cache()
                    emitLine("moveq  #0, d0")
                    emitLoadD0(srcReg, IRDataType.WORD)
                    emitStoreD0(dstReg, IRDataType.LONG)
                }
                IRDataType.LONG -> {
                    // no extension needed
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
                    emitLoadD0(srcReg, IRDataType.BYTE)
                    emitLine("ext.w  d0")
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                IRDataType.WORD -> {
                    // sign-extend word to long
                    emitLoadD0(srcReg, IRDataType.WORD)
                    emitLine("ext.l  d0")
                    emitStoreD0(dstReg, IRDataType.LONG)
                }
                IRDataType.LONG -> {
                    // no extension needed
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
            invalidateD0Cache()
            emitLine("moveq  #0, d0")
            emitLoadD0(srcReg, IRDataType.BYTE)
            emitStoreD0(dstReg, IRDataType.LONG)
        }

        Opcode.EXTLS -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            // sign-extend byte to long
            emitLoadD0(srcReg, IRDataType.BYTE)
            emitSignExtendByteToLong("d0")
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
                    emitLoadD0(srcReg2, IRDataType.BYTE)
                    emitLine("lsl.w  #8, d0")
                    invalidateD0Cache()
                    emitLine("or.b  ${regAddr(srcReg3)}, d0")
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                IRDataType.WORD -> {
                    // r1 = LONG(r2 as MSW, r3 as LSW)
                    emitLoadD0(srcReg2, IRDataType.WORD)
                    emitLine("swap  d0")
                    invalidateD0Cache()
                    emitLine("clr.w  d0")
                    emitLine("or.w  ${regAddr(srcReg3)}, d0")
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
                emitLine("ftst.s  ${floatRegFileAddr(srcFp)}")
                emitLine("fslt  d0")
                emitLine("fsgt  d1")
                emitLine("neg.b  d1")
                emitLine("or.b  d1, d0")
                emitStoreD0(dstReg, IRDataType.BYTE)
            } else {
                val srcReg = insn.requireIntSourceA().intNumber
                val type = insn.type ?: IRDataType.BYTE
                val s = dtSuffix(type)
                val zeroLabel = makeLabel("sgn_zero")
                val doneLabel = makeLabel("sgn_done")
                emitLoadD0(srcReg, type)
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
                invalidateD0CacheForSlot(dstReg)    // raw store bypasses emitStoreD0
            }
        }

        // === Floating point operations via 68881/68882 FPU ===

        Opcode.FFROMUB -> {
            val fpDst = insn.requireFloatDest().floatNumber
            val srcReg = insn.requireIntSourceA().intNumber
            emitLoadD0(srcReg, IRDataType.BYTE)
            emitLine($$"and.l  #$ff, d0")
            invalidateD0Cache()
            emitLine("fmove.l  d0, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fpDst)}")
        }

        Opcode.FFROMSB -> {
            val fpDst = insn.requireFloatDest().floatNumber
            val srcReg = insn.requireIntSourceA().intNumber
            emitLoadD0(srcReg, IRDataType.BYTE)
            emitSignExtendByteToLong("d0")
            emitLine("fmove.l  d0, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fpDst)}")
        }

        Opcode.FFROMUW -> {
            val fpDst = insn.requireFloatDest().floatNumber
            val srcReg = insn.requireIntSourceA().intNumber
            emitLoadD0(srcReg, IRDataType.WORD)
            emitLine($$"and.l  #$ffff, d0")
            invalidateD0Cache()
            emitLine("fmove.l  d0, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fpDst)}")
        }

        Opcode.FFROMSW -> {
            val fpDst = insn.requireFloatDest().floatNumber
            val srcReg = insn.requireIntSourceA().intNumber
            emitLoadD0(srcReg, IRDataType.WORD)
            emitLine("ext.l  d0")
            emitLine("fmove.l  d0, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fpDst)}")
        }

        Opcode.FFROMSL -> {
            val fpDst = insn.requireFloatDest().floatNumber
            val srcReg = insn.requireIntSourceA().intNumber
            emitLoadD0(srcReg, IRDataType.LONG)
            emitLine("fmove.l  d0, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fpDst)}")
        }

        Opcode.FTOUB -> {
            val dstReg = insn.requireIntDest().intNumber
            val fpSrc = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(fpSrc)}, $FP_ACC")
            invalidateD0Cache()
            emitLine("fmove.b  $FP_ACC, d0")
            emitLine($$"and.l  #$ff, d0")
            emitStoreD0(dstReg, IRDataType.BYTE)
        }

        Opcode.FTOSB -> {
            val dstReg = insn.requireIntDest().intNumber
            val fpSrc = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(fpSrc)}, $FP_ACC")
            invalidateD0Cache()
            emitLine("fmove.b  $FP_ACC, d0")
            emitStoreD0(dstReg, IRDataType.BYTE)
        }

        Opcode.FTOUW -> {
            val dstReg = insn.requireIntDest().intNumber
            val fpSrc = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(fpSrc)}, $FP_ACC")
            invalidateD0Cache()
            emitLine("fmove.w  $FP_ACC, d0")
            emitStoreD0(dstReg, IRDataType.WORD)
        }

        Opcode.FTOSW -> {
            val dstReg = insn.requireIntDest().intNumber
            val fpSrc = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(fpSrc)}, $FP_ACC")
            invalidateD0Cache()
            emitLine("fmove.w  $FP_ACC, d0")
            emitStoreD0(dstReg, IRDataType.WORD)
        }

        Opcode.FTOSL -> {
            val dstReg = insn.requireIntDest().intNumber
            val fpSrc = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(fpSrc)}, $FP_ACC")
            invalidateD0Cache()
            emitLine("fmove.l  $FP_ACC, d0")
            emitStoreD0(dstReg, IRDataType.LONG)
        }

        Opcode.FABS -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("fabs  $FP_ACC, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }

        Opcode.FSIN -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("fsin  $FP_ACC, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }

        Opcode.FCOS -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("fcos  $FP_ACC, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }

        Opcode.FTAN -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("ftan  $FP_ACC, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }

        Opcode.FATAN -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("fatan  $FP_ACC, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }

        Opcode.FPOW -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("fpow  $FP_ACC, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }

        Opcode.FLN -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("flogn  $FP_ACC, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }

        Opcode.FLOG -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("flog2  $FP_ACC, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }

        Opcode.FROUND -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("fround  $FP_ACC, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }

        Opcode.FFLOOR -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("ffloor  $FP_ACC, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }

        Opcode.FCEIL -> {
            // 68881 has no fceil; implement as: if x == int(x) then x else x > 0 ? int(x)+1 : int(x)
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            val isIntLabel = makeLabel("fceil_is_int")
            val doneLabel = makeLabel("fceil_done")
            val posLabel = makeLabel(".fceil_pos")
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, $FP_SRC")
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
            emitLine("fmove.s  $FP_SRC, ${floatRegFileAddr(dst)}")
        }

        Opcode.FCOMP -> {
            val dstReg = insn.requireIntDest().intNumber
            val fr1 = insn.requireFloatSourceA().floatNumber
            val fr2 = insn.requireSrcB().floatNumber
            val eqLabel = makeLabel("fcomp_eq")
            val doneLabel = makeLabel("fcomp_done")
            val gtLabel = makeLabel("fcomp_gt")
            invalidateD0Cache()
            emitLine("fmove.s  ${floatRegFileAddr(fr1)}, $FP_ACC")
            emitLine("fcmp.s  ${floatRegFileAddr(fr2)}, $FP_ACC")
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
            emitStoreD0(dstReg, IRDataType.BYTE)
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

    // Emit non-constant arguments first (from regfile), then constant immediates
    // just before the BSR/JSR to avoid clobbering the constant registers while
    // evaluating non-const arguments (e.g. address calculations that may use D0/A0 as scratch).
    val (forwarded, nonForwarded) = if (forwardedImmediateCall != null) {
        callSite.arguments.partition { it.source.register in forwardedImmediateCall.loads }
    } else {
        emptyList<CallArgument>() to callSite.arguments
    }
    for (arg in nonForwarded) {
        translateArgument(arg, fnLabel, forwardedImmediateCall)
    }
    for (arg in forwarded) {
        translateArgument(arg, fnLabel, forwardedImmediateCall)
    }

    // Check if this call targets an inline asmsub — emit its body directly instead of jsr
    val inlineTarget = program.allAsmSubs().find { it.label == fnLabel && it.isInline }
    if (inlineTarget != null) {
        emitRaw(inlineTarget.asmChunk.assembly)
    } else {
        // PC-relative call; vasm picks the optimal size and falls back to jsr if out of range
        emitLine("bsr  $fnLabel")
    }

    // Move return values back to virtual registers.
    // Skip status flag returns: always handled by IR's bsteq/bstneg/bstvs branch pattern.
    // In multi-assign context (results.size > 1), also skip slot-based returns:
    // the IR generates LOADHR for them, and emitting here would clobber CPU flags
    // before the branch pattern can read them.
    // In single-return expression context, process all slot returns normally
    // (the IR doesn't generate LOADHR for single-return calls).
    // LIMITATION: multiple status flag returns in one multi-assign (e.g. -> bool @Pz, bool @Pc)
    // are not supported - codegen limitation: the first flag's extraction clobbers the state for subsequent flags.
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

    // Only use .w/.l if we can 100% prove both pointers are aligned.
    // At codegen we only know immediates; computed addresses (e.g. &arr[i]) are not immediate
    // so we conservatively fall back to byte copies. A future IR alignment analysis could prove more.
    val srcImm = fwd.loads[srcArg.source.register]?.immediate?.integerValue
    val tgtImm = fwd.loads[tgtArg.source.register]?.immediate?.integerValue
    val bothEven = srcImm != null && tgtImm != null && srcImm % 2 == 0 && tgtImm % 2 == 0
    val bothLongAligned = srcImm != null && tgtImm != null && srcImm % 4 == 0 && tgtImm % 4 == 0
    val useLong = bothLongAligned && count % 4 == 0
    val useWord = bothEven && count % 2 == 0
    // Heuristics: inline small copies; use a dbra loop for the medium "small" range
    // to keep code size reasonable. The call to memcopy/CopyMem wins for large copies.
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
    // For larger small counts, a dbra loop is smaller than unrolled moves.
    // Threshold: a dbra loop needs ~10 bytes setup (move.w #count,dN + label + dbra),
    // so it wins over more than ~5 unrolled moves of the same size.
    val unrolledLimit = 5
    when {
        useLong -> {
            val longs = count / 4
            emitRaw("        ; inline memcopy $count bytes as $longs longwords")
            if (longs <= unrolledLimit) {
                repeat(longs) { emitLine("move.l  (a0)+,(a1)+") }
            } else {
                emitInlineCopyLoop(longs, 4)
            }
        }
        useWord -> {
            val words = count / 2
            emitRaw("        ; inline memcopy $count bytes as $words words")
            if (words <= unrolledLimit) {
                repeat(words) { emitLine("move.w  (a0)+,(a1)+") }
            } else {
                emitInlineCopyLoop(words, 2)
            }
        }
        else -> {
            emitRaw("        ; inline memcopy $count bytes")
            if (count <= unrolledLimit) {
                repeat(count) { emitLine("move.b  (a0)+,(a1)+") }
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
    emitLine("moveq  #$iterations, d0")
    emitLabel(loopLabel)
    emitLine("$moveInsn  (a0)+,(a1)+")
    emitLine("dbra  d0,$loopLabel")
}

private fun AsmGen.translateArgument(
    arg: CallArgument,
    fnLabel: String? = null,
    forwardedImmediateCall: ImmediateCallOptimization? = null
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
                emitLine("fmove.s  ${floatRegFileAddr(argReg.floatNumber)}, $hwReg")
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
                        // use moveq (2 bytes, 4 cycles) when the value fits in its signed 8-bit range;
                        // for byte args the value is unsigned 0-255, so map 128-255 to -128--1 (low byte is the same)
                        val moveqValue = if (argReg.type == IRDataType.BYTE && value in 128..255) value - 256 else value
                        if (moveqValue in -128..127)
                            emitLine("moveq  #$moveqValue, $hwReg")
                        else
                            emitLine("move$s  #$value, $hwReg")
                    }
                }
            } else {
                val source = regAddr(argReg.intNumber)
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
        // Store to the callee's parameter variable (if this is a named param)
        val paramName = (arg.location as? CallLocation.ParameterMemory)?.name.orEmpty()
        if (paramName.isNotEmpty() && fnLabel != null) {
            val target = fixNameSymbols("$fnLabel.$paramName")
            if (argReg.isFloat) {
                emitLine("fmove.s  ${floatRegFileAddr(argReg.floatNumber)}, $FP_ACC")
                emitLine("fmove.s  $FP_ACC, $target")
            } else {
                val s = dtSuffix(argReg.type)
                emitLine("move$s  ${regAddr(argReg.intNumber)}, $target")
            }
        }
    }
}

private fun AsmGen.translateReturnValue(ret: CallResult) {
    // Status flag returns are handled by the IR's branch-based pattern (bsteq/bstneg/bstvs).
    // Do NOT emit Scc here - it would clobber the flags before the branch can read them.
    if (ret.location is CallLocation.StatusFlag)
        return

    val destination = ret.destination ?: return

    // Otherwise, return value is in a hardware register
    val slot = (ret.location as? CallLocation.HardwareRegister)?.slot
    if (slot != null) {
        val hwReg = m68kSlotRegister(slot)
        if (destination.isFloat) {
            emitLine("fmove.s  $hwReg, ${floatRegFileAddr(destination.floatNumber)}")
        } else {
            val s = dtSuffix(destination.type)
            emitLine("move$s  $hwReg, ${regAddr(destination.intNumber)}")
        }
    } else {
        // Default: return value in d0 (standard m68k calling convention)
        // All non-float returns go through d0-d7 (not a0-a6) because the calling convention
        // expects values in data registers. Using A0 for pointers would be inconsistent:
        // callers read return values from data regs unless the slot annotation says otherwise.
        // Slightly inefficient: m68k pointers ideally live in address registers, but returning
        // them in d0 is simpler and avoids ambiguity. Explicit @A0 can be used for hot paths.
        if (destination.isFloat) {
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(destination.floatNumber)}")
        } else {
            emitStoreD0(destination.intNumber, destination.type)
        }
    }
}

// === Slot to M68k hardware register mapping ===

// Slots 0..7 are the 6502/cx16-style scalar registers (A, X, Y, AX, AY, XY,
// FAC1, FAC2) used by target-independent builtins (e.g. divmod returns its
// quotient in AY). Map them onto distinct M68k hardware registers so the
// backend can emit code for them.
fun m68kSlotRegister(slot: CallingConventionSlot): String = when (slot.value) {
    in 0..7 -> error("slots 0-7 should never be used on the M68K they are 6502 cpu registers")
    in 10..17 -> "d${slot.value - 10}"     // M68k slots: D0-D7
    in 18..24 -> "a${slot.value - 18}"     // M68k slots: A0-A6
    in 25..32 -> "fp${slot.value - 25}"    // M68k slots: FP0-FP7
    else -> error("unknown calling convention slot: $slot")
}
