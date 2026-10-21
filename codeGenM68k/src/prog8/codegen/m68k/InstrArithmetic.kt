package prog8.codegen.m68k

import prog8.code.core.CpuType
import prog8.intermediate.*

internal fun AsmGen.translateArithmetic(insn: IRInstruction) {
    val type = insn.type ?: IRDataType.BYTE

    if (type == IRDataType.FLOAT) {
        translateFloatArithmetic(insn)
        return
    }

    when (insn.opcode) {
        Opcode.INC -> {
            val reg = insn.requireIntDest().intNumber
            emitLine("addq${dtSuffix(type)}  #1, ${operand(reg)}")
            invalidateD0CacheForSlot(reg)
        }

        Opcode.INCM -> {
            val target = resolveMemory(insn.requireMemory())
            emitLine("addq${dtSuffix(type)}  #1, $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.DEC -> {
            val reg = insn.requireIntDest().intNumber
            emitLine("subq${dtSuffix(type)}  #1, ${operand(reg)}")
            invalidateD0CacheForSlot(reg)
        }

        Opcode.DECM -> {
            val target = resolveMemory(insn.requireMemory())
            emitLine("subq${dtSuffix(type)}  #1, $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.NEG -> {
            val reg = insn.requireIntDest().intNumber
            emitLine("neg${dtSuffix(type)}  ${operand(reg)}")
            invalidateD0CacheForSlot(reg)
        }

        Opcode.NEGM -> {
            val target = resolveMemory(insn.requireMemory())
            emitLine("neg${dtSuffix(type)}  $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.ADDR -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            directDataPair(dstReg, srcReg, type)?.let { (d, s) ->
                emitLine("add${dtSuffix(type)}  $s, $d")
                invalidateD0CacheForSlot(dstReg)
                return@translateArithmetic
            }
            loadRegOrZeroExtendToD0(srcReg, type)
            emitLine("add${dtSuffix(type)}  $SCRATCH_D0, ${operand(dstReg)}")
            invalidateD0CacheForSlot(dstReg)
        }

        Opcode.ADD -> {
            val reg = insn.requireIntDest().intNumber
            val value = insn.requireImmediateInt()
            if(value in 1..8) {
                emitLine("addq${dtSuffix(type)}  #$value, ${operand(reg)}")
            } else {
                emitLine("add${dtSuffix(type)}  #${immVal(value, type)}, ${operand(reg)}")
            }
            invalidateD0CacheForSlot(reg)
        }

        Opcode.ADDM -> {
            val reg = insn.requireIntSourceA().intNumber
            val target = resolveMemory(insn.requireMemory())
            val sv = dtSuffix(type)
            directDataSrc(reg, type)?.let { s ->
                emitLine("add$sv  $s, $target")
            } ?: run {
                emitLoadD0(reg, type)
                emitLine("add$sv  $SCRATCH_D0, $target")
            }
            invalidateD0CacheForAddress(target)
        }

        Opcode.ADDIM -> {
            val value = insn.requireImmediateInt()
            val target = resolveMemory(insn.requireMemory())
            val sv = dtSuffix(type)
            if(value in 1..8) {
                emitLine("addq$sv  #$value,$target")
            } else {
                when (type) {
                    IRDataType.BYTE -> {
                        require(value in 2..255)
                        emitLine("add$sv  #$value,$target")
                    }
                    IRDataType.WORD -> {
                        require(value in 2..65535)
                        emitLine("add$sv  #$value,$target")
                    }
                    IRDataType.LONG, IRDataType.POINTER -> {
                        require(value in 2..0x7fffffff)
                        emitLine("add$sv  #$value,$target")
                    }
                }
            }
            invalidateD0CacheForAddress(target)
        }

        Opcode.SUBR -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            directDataPair(dstReg, srcReg, type)?.let { (d, s) ->
                emitLine("sub${dtSuffix(type)}  $s, $d")
                invalidateD0CacheForSlot(dstReg)
                return@translateArithmetic
            }
            loadRegOrZeroExtendToD0(srcReg, type)
            emitLine("sub${dtSuffix(type)}  $SCRATCH_D0, ${operand(dstReg)}")
            invalidateD0CacheForSlot(dstReg)
        }

        Opcode.SUB -> {
            val reg = insn.requireIntDest().intNumber
            val value = insn.requireImmediateInt()
            if(value in 1..8) {
                emitLine("subq${dtSuffix(type)}  #$value, ${operand(reg)}")
            } else {
                emitLine("sub${dtSuffix(type)}  #${immVal(value, type)}, ${operand(reg)}")
            }
            invalidateD0CacheForSlot(reg)
        }

        Opcode.SUBM -> {
            val reg = insn.requireIntSourceA().intNumber
            val target = resolveMemory(insn.requireMemory())
            val sv = dtSuffix(type)
            directDataSrc(reg, type)?.let { s ->
                emitLine("sub$sv  $s, $target")
            } ?: run {
                emitLoadD0(reg, type)
                emitLine("sub$sv  $SCRATCH_D0, $target")
            }
            invalidateD0CacheForAddress(target)
        }

        Opcode.SUBIM -> {
            val value = insn.requireImmediateInt()
            val target = resolveMemory(insn.requireMemory())
            val sv = dtSuffix(type)
            if(value in 1..8) {
                emitLine("subq$sv  #$value,$target")
            } else {
                when (type) {
                    IRDataType.BYTE -> {
                        require(value in 2..255)
                        emitLine("sub$sv  #$value,$target")
                    }
                    IRDataType.WORD -> {
                        require(value in 2..65535)
                        emitLine("sub$sv  #$value,$target")
                    }
                    IRDataType.LONG, IRDataType.POINTER -> {
                        require(value in 2..0x7fffffff)
                        emitLine("sub$sv  #$value,$target")
                    }
                }
            }
            invalidateD0CacheForAddress(target)
        }

        // --- Multiply (unsigned): M68k has no mulu.b, so .w with zero-extension for byte ---

        Opcode.MULR -> emitMulOp(insn.requireIntDest().intNumber, insn.requireIntSourceA().intNumber, type, unsigned=true, imm=null, target=null)
        Opcode.MUL -> emitMulOp(insn.requireIntDest().intNumber, null, type, unsigned=true, imm=insn.requireImmediateInt(), target=null)
        Opcode.MULM -> emitMulOp(insn.requireIntSourceA().intNumber, null, type, unsigned=true, imm=null, target=resolveMemory(insn.requireMemory()))

        // --- Multiply (signed) ---

        Opcode.MULSR -> emitMulOp(insn.requireIntDest().intNumber, insn.requireIntSourceA().intNumber, type, unsigned=false, imm=null, target=null)
        Opcode.MULS -> emitMulOp(insn.requireIntDest().intNumber, null, type, unsigned=false, imm=insn.requireImmediateInt(), target=null)
        Opcode.MULSM -> emitMulOp(insn.requireIntSourceA().intNumber, null, type, unsigned=false, imm=null, target=resolveMemory(insn.requireMemory()))

        // --- Divide (unsigned) ---

        Opcode.DIVR -> emitDivOp(insn.requireIntDest().intNumber, insn.requireIntSourceA().intNumber, type, unsigned=true, imm=null, target=null)
        Opcode.DIV -> emitDivOp(insn.requireIntDest().intNumber, null, type, unsigned=true, imm=insn.requireImmediateInt(), target=null)
        Opcode.DIVM -> emitDivOp(insn.requireIntSourceA().intNumber, null, type, unsigned=true, imm=null, target=resolveMemory(insn.requireMemory()))

        // --- Divide (signed) ---

        Opcode.DIVSR -> emitDivOp(insn.requireIntDest().intNumber, insn.requireIntSourceA().intNumber, type, unsigned=false, imm=null, target=null)
        Opcode.DIVS -> emitDivOp(insn.requireIntDest().intNumber, null, type, unsigned=false, imm=insn.requireImmediateInt(), target=null)
        Opcode.DIVSM -> emitDivOp(insn.requireIntSourceA().intNumber, null, type, unsigned=false, imm=null, target=resolveMemory(insn.requireMemory()))

        // --- Modulus: divu.w/divs.w remainder for byte/word, divul/divsl for long (68020+) ---

        Opcode.MODR -> emitModOp(insn.requireIntDest().intNumber, insn.requireIntSourceA().intNumber, type, unsigned=true, imm=null)
        Opcode.MOD -> emitModOp(insn.requireIntDest().intNumber, null, type, unsigned=true, imm=insn.requireImmediateInt())
        Opcode.MODSR -> emitModOp(insn.requireIntDest().intNumber, insn.requireIntSourceA().intNumber, type, unsigned=false, imm=null)
        Opcode.MODS -> emitModOp(insn.requireIntDest().intNumber, null, type, unsigned=false, imm=insn.requireImmediateInt())

        // --- DIVMOD (unsigned): 68020+ divul.l gives quotient + remainder in one instruction ---

        Opcode.DIVMODR -> emitDivModOp(insn.requireIntDest().intNumber, insn.requireDestB().intNumber, type, unsigned=true, imm=null)
        Opcode.DIVMOD -> emitDivModOp(insn.requireIntDest().intNumber, insn.requireDestB().intNumber, type, unsigned=true, imm=insn.requireImmediateInt())
        Opcode.SDIVMODR -> emitDivModOp(insn.requireIntDest().intNumber, insn.requireDestB().intNumber, type, unsigned=false, imm=null)
        Opcode.SDIVMOD -> emitDivModOp(insn.requireIntDest().intNumber, insn.requireDestB().intNumber, type, unsigned=false, imm=insn.requireImmediateInt())

        // --- Integer sqrt/square ---

        Opcode.SQRT -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            when (type) {
                IRDataType.BYTE -> {
                    emitLine("move.b  ${operand(srcReg)}, math._sqrt_ub.value", "sqrt byte")
                    invalidateD0Cache()
                    emitLine("bsr  math._sqrt_ub")
                    emitStoreD0(dstReg, IRDataType.BYTE)
                }
                IRDataType.WORD -> {
                    emitLine("move.w  ${operand(srcReg)}, math._sqrt_uw.value", "sqrt word")
                    invalidateD0Cache()
                    emitLine("bsr  math._sqrt_uw")
                    emitStoreD0(dstReg, IRDataType.BYTE)
                }
                IRDataType.LONG, IRDataType.POINTER -> {
                    emitLine("move.l  ${operand(srcReg)}, math._sqrt_l.value", "sqrt long")
                    invalidateD0Cache()
                    emitLine("bsr  math._sqrt_l")
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
            }
        }
        Opcode.SQUARE -> {
            val dstReg = insn.requireIntDest().intNumber
            val srcReg = insn.requireIntSourceA().intNumber
            when (type) {
                IRDataType.BYTE -> {
                    emitLoadD0(srcReg, IRDataType.BYTE)
                    emitLine("and.l  #\$ff, $SCRATCH_D0", "zero extend")
                    invalidateD0Cache()
                    emitLine("mulu.w  $SCRATCH_D0, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.BYTE)
                }
                IRDataType.WORD -> {
                    directDataPair(dstReg, srcReg, IRDataType.WORD)?.let { (d, s) ->
                        if (s != d) emitLine("move.w  $s, $d")
                        emitLine("mulu.w  $d, $d")
                        invalidateD0Cache()
                        return
                    }
                    emitLoadD0(srcReg, IRDataType.WORD)
                    emitLine("mulu.w  $SCRATCH_D0, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                IRDataType.LONG, IRDataType.POINTER -> {
                    directDataPair(dstReg, srcReg, IRDataType.LONG)?.let { (d, s) ->
                        if (s != d) emitLine("move.l  $s, $d")
                        emitLine("mulu.l  $d, $d")
                        invalidateD0Cache()
                        return
                    }
                    emitLoadD0(srcReg, IRDataType.LONG)
                    emitLine("mulu.l  $SCRATCH_D0, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.LONG)
                }
            }
        }

        Opcode.CMP -> {
            val leftReg = insn.requireIntSourceA().intNumber
            val rightReg = insn.requireSrcB().intNumber
            val s = dtSuffix(type)
            directDataDst(leftReg, type)?.let { d ->
                emitLine("cmp$s  ${operand(rightReg)}, $d")
                return@translateArithmetic
            }
            emitLoadD0(leftReg, type)
            emitLine("cmp$s  ${operand(rightReg)}, $SCRATCH_D0")
        }

        Opcode.CMPI -> {
            val reg = insn.requireIntSourceA().intNumber
            val masked = immVal(insn.requireImmediateInt(), type)
            // cmpi.x #0, operand can be replaced by the faster tst.x operand
            if(masked==0)
                emitLine("tst${dtSuffix(type)}  ${operand(reg)}")
            else
                emitLine("cmpi${dtSuffix(type)}  #$masked, ${operand(reg)}")
        }

        else -> error("Unknown arithmetic opcode: ${insn.opcode}")
    }
}

// === Multiply / Divide / Modulus / DIVMOD for all int sizes (68030-aware) ===

// emit a call to one of the 68000 long math helper routines (utility.library fast path + software fallback)
private fun AsmGen.emitLongMathCall(dstReg: Int, srcReg: Int?, imm: Int?, target: String?, routine: String, resultReg: String) {
    when {
        srcReg != null -> {
            emitLoadD0(dstReg, IRDataType.LONG)
            emitLine("move.l  ${operand(srcReg)}, $SCRATCH_D1")
        }
        imm != null -> {
            emitLoadD0(dstReg, IRDataType.LONG)
            emitLine("move.l  #$imm, $SCRATCH_D1")
        }
        target != null -> {
            emitLoadD0FromAddress(target, IRDataType.LONG)
            emitLine("move.l  ${operand(dstReg)}, $SCRATCH_D1")
        }
    }
    invalidateD0Cache()
    // Helpers clobber D2-D7 with no IR call edge, so save them; A2-A4 are untouched, A6 only consumed.
    emitLine("movem.l  d2-d7,-(sp)")
    emitLine("bsr  $routine")
    emitLine("movem.l  (sp)+,d2-d7")
    val storeTarget = target ?: operand(dstReg)
    if (resultReg == SCRATCH_D0) {
        emitStoreD0ToAddress(storeTarget, IRDataType.LONG)
    } else {
        invalidateD0CacheForAddress(storeTarget)
        emitLine("move.l  $resultReg, $storeTarget")
    }
}

private fun AsmGen.emitMulOp(dstReg: Int, srcReg: Int?, type: IRDataType, unsigned: Boolean, imm: Int?, target: String?) {
    val op = if (unsigned) "mulu" else "muls"
    when (type) {
        IRDataType.BYTE -> {
            // No .b multiply on M68k: zero-extend to word, mulu.w/muls.w, store low byte
            when {
                srcReg != null -> {
                    directDataPair(dstReg, srcReg, IRDataType.BYTE)?.let { (d, s) ->
                        emitLine("move.b  $s, $SCRATCH_D1")
                        if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D1") else emitSignExtendByteToLong(SCRATCH_D1)
                        if (unsigned) emitLine("and.l  #\$ff, $d") else emitSignExtendByteToLong(d)
                        emitLine("$op.w  $SCRATCH_D1, $d")
                        invalidateD0Cache()
                        return
                    }
                    emitLoadD0(dstReg, IRDataType.BYTE)
                    if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D0") else emitSignExtendByteToLong(SCRATCH_D0)
                    invalidateD0Cache()
                    emitLine("move.w  $SCRATCH_D0, $SCRATCH_D1")
                    emitLoadD0(srcReg, IRDataType.BYTE)
                    if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D0") else emitSignExtendByteToLong(SCRATCH_D0)
                    invalidateD0Cache()
                    emitLine("$op.w  $SCRATCH_D1, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.BYTE)
                }
                imm != null -> {
                    directDataDst(dstReg, IRDataType.BYTE)?.let { d ->
                        if (unsigned) emitLine("and.l  #\$ff, $d") else emitSignExtendByteToLong(d)
                        emitLine("$op.w  #${imm.and(0xffff)}, $d")
                        invalidateD0Cache()
                        return
                    }
                    emitLoadD0(dstReg, IRDataType.BYTE)
                    if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D0") else emitSignExtendByteToLong(SCRATCH_D0)
                    invalidateD0Cache()
                    emitLine("$op.w  #${imm.and(0xffff)}, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.BYTE)
                }
                target != null -> {
                    emitLine("move.b  $target, $SCRATCH_D1")
                    if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D1") else emitSignExtendByteToLong(SCRATCH_D1)
                    emitLoadD0(dstReg, IRDataType.BYTE)
                    if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D0") else emitSignExtendByteToLong(SCRATCH_D0)
                    invalidateD0Cache()
                    emitLine("$op.w  $SCRATCH_D1, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0ToAddress(target, IRDataType.BYTE)
                }
            }
        }

        IRDataType.WORD -> {
            // mulu.w/muls.w is native (16x16→32, lower 16 are result); destination must be a data register.
            when {
                srcReg != null -> {
                    directDataPair(dstReg, srcReg, IRDataType.WORD)?.let { (d, s) ->
                        emitLine("$op.w  $s, $d")
                        invalidateD0Cache()
                        return
                    }
                    emitLoadD0(dstReg, IRDataType.WORD)
                    emitLine("$op.w  ${operand(srcReg)}, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                imm != null -> {
                    directDataDst(dstReg, IRDataType.WORD)?.let { d ->
                        emitLine("$op.w  #${imm.and(0xffff)}, $d")
                        invalidateD0Cache()
                        return
                    }
                    emitLoadD0(dstReg, IRDataType.WORD)
                    emitLine("$op.w  #${imm.and(0xffff)}, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                target != null -> {
                    emitLoadD0(dstReg, IRDataType.WORD)
                    emitLine("$op.w  $target, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0ToAddress(target, IRDataType.WORD)
                }
            }
        }

        IRDataType.LONG -> {
            if(program.options.compTarget.cpu < CpuType.M68020) {
                // 68000: no mulu.l/muls.l; use a helper routine (utility.library or software fallback)
                val routine = if (unsigned) "p8_umult32" else "p8_smult32"
                emitLongMathCall(dstReg, srcReg, imm, target, routine, SCRATCH_D0)
            } else {
                // 68020+ mulu.l/muls.l (32x32→64, lower 32 are result); destination must be a data register.
                when {
                    srcReg != null -> {
                        directDataPair(dstReg, srcReg, IRDataType.LONG)?.let { (d, s) ->
                            emitLine("$op.l  $s, $d")
                            invalidateD0Cache()
                            return
                        }
                        emitLoadD0(dstReg, IRDataType.LONG)
                        emitLine("$op.l  ${operand(srcReg)}, $SCRATCH_D0")
                        invalidateD0Cache()
                        emitStoreD0(dstReg, IRDataType.LONG)
                    }
                    imm != null -> {
                        directDataDst(dstReg, IRDataType.LONG)?.let { d ->
                            emitLine("$op.l  #$imm, $d")
                            invalidateD0Cache()
                            return
                        }
                        emitLoadD0(dstReg, IRDataType.LONG)
                        emitLine("$op.l  #${imm}, $SCRATCH_D0")
                        invalidateD0Cache()
                        emitStoreD0(dstReg, IRDataType.LONG)
                    }
                    target != null -> {
                        emitLoadD0(dstReg, IRDataType.LONG)
                        emitLine("$op.l  $target, $SCRATCH_D0")
                        invalidateD0Cache()
                        emitStoreD0ToAddress(target, IRDataType.LONG)
                    }
                }
            }
        }

        else -> TODO("MUL for ${type.name}")
    }
}

private fun AsmGen.emitDivOp(dstReg: Int, srcReg: Int?, type: IRDataType, unsigned: Boolean, imm: Int?, target: String?) {
    val op = if (unsigned) "divu" else "divs"
    when (type) {
        IRDataType.BYTE -> {
            // No .b divide on M68k: extend to word, divu.w/divs.w, take quotient from low byte
            when {
                srcReg != null -> {
                    directDataPair(dstReg, srcReg, IRDataType.BYTE)?.let { (d, s) ->
                        emitLine("move.b  $s, $SCRATCH_D1")
                        if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D1") else emitSignExtendByteToLong(SCRATCH_D1)
                        if (unsigned) emitLine("and.l  #\$ff, $d") else emitSignExtendByteToLong(d)
                        emitLine("$op.w  $SCRATCH_D1, $d")
                        invalidateD0Cache()
                        return
                    }
                    emitLoadD0(dstReg, IRDataType.BYTE)
                    if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D0") else emitSignExtendByteToLong(SCRATCH_D0)
                    invalidateD0Cache()
                    emitLine("move.b  ${operand(srcReg)}, $SCRATCH_D1")
                    if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D1") else emitSignExtendByteToLong(SCRATCH_D1)
                    emitLine("$op.w  $SCRATCH_D1, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.BYTE)
                }
                imm != null -> {
                    directDataDst(dstReg, IRDataType.BYTE)?.let { d ->
                        if (unsigned) emitLine("and.l  #\$ff, $d") else emitSignExtendByteToLong(d)
                        emitLine("$op.w  #${imm.and(0xffff)}, $d")
                        invalidateD0Cache()
                        return
                    }
                    emitLoadD0(dstReg, IRDataType.BYTE)
                    if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D0") else emitSignExtendByteToLong(SCRATCH_D0)
                    invalidateD0Cache()
                    emitLine("$op.w  #${imm.and(0xffff)}, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.BYTE)
                }
                target != null -> {
                    emitLoadD0FromAddress(target, IRDataType.BYTE)
                    if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D0") else emitSignExtendByteToLong(SCRATCH_D0)
                    invalidateD0Cache()
                    emitLine("move.b  ${operand(dstReg)}, $SCRATCH_D1")
                    if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D1") else emitSignExtendByteToLong(SCRATCH_D1)
                    emitLine("$op.w  $SCRATCH_D1, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0ToAddress(target, IRDataType.BYTE)
                }
            }
        }

        IRDataType.WORD -> {
            // divu.w/divs.w take a 32-bit dividend: sign-extend for divs.w, zero-extend for divu.w.
            fun loadDividend() {
                emitLoadD0(dstReg, IRDataType.WORD)
                if (unsigned) {
                    emitLine("and.l  #\$ffff, $SCRATCH_D0", "zero-extend upper word for divu.w")
                    invalidateD0Cache()
                } else {
                    emitLine("ext.l  $SCRATCH_D0", "sign-extend upper word for divs.w (signed 32-bit dividend)")
                    invalidateD0Cache()
                }
            }
            when {
                srcReg != null -> {
                    directDataPair(dstReg, srcReg, IRDataType.WORD)?.let { (d, s) ->
                        check(s != d) { "divisor and dividend share $d" }  // divisor is read after the dividend is extended
                        if (unsigned) {
                            emitLine("and.l  #\$ffff, $d", "zero-extend upper word for divu.w")
                        } else {
                            emitLine("ext.l  $d", "sign-extend upper word for divs.w (signed 32-bit dividend)")
                        }
                        emitLine("$op.w  $s, $d")
                        invalidateD0Cache()
                        return
                    }
                    loadDividend()
                    emitLine("$op.w  ${operand(srcReg)}, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                imm != null -> {
                    directDataDst(dstReg, IRDataType.WORD)?.let { d ->
                        if (unsigned) {
                            emitLine("and.l  #\$ffff, $d", "zero-extend upper word for divu.w")
                        } else {
                            emitLine("ext.l  $d", "sign-extend upper word for divs.w (signed 32-bit dividend)")
                        }
                        emitLine("$op.w  #${imm.and(0xffff)}, $d")
                        invalidateD0Cache()
                        return
                    }
                    loadDividend()
                    emitLine("$op.w  #${imm.and(0xffff)}, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                target != null -> {
                    emitLoadD0FromAddress(target, IRDataType.WORD)
                    if (unsigned) {
                        emitLine("and.l  #\$ffff, $SCRATCH_D0", "zero-extend upper word for divu.w")
                        invalidateD0Cache()
                    } else {
                        emitLine("ext.l  $SCRATCH_D0", "sign-extend upper word for divs.w (signed 32-bit dividend)")
                        invalidateD0Cache()
                    }
                    emitLine("$op.w  ${operand(dstReg)}, $SCRATCH_D0")
                    invalidateD0Cache()
                    emitStoreD0ToAddress(target, IRDataType.WORD)
                }
            }
        }

        IRDataType.LONG -> {
            if(program.options.compTarget.cpu < CpuType.M68020) {
                // 68000: no divu.l/divs.l; use a helper routine (utility.library or software fallback)
                val routine = if (unsigned) "p8_udivmod32" else "p8_sdivmod32"
                emitLongMathCall(dstReg, srcReg, imm, target, routine, SCRATCH_D0)
            } else {
                // 68020+ divu.l/divs.l (32/32→32)
                when {
                    srcReg != null -> {
                        directDataPair(dstReg, srcReg, IRDataType.LONG)?.let { (d, s) ->
                            emitLine("$op.l  $s, $d")
                            invalidateD0Cache()
                            return
                        }
                        emitLoadD0(dstReg, IRDataType.LONG)
                        emitLine("$op.l  ${operand(srcReg)}, $SCRATCH_D0")
                        invalidateD0Cache()
                        emitStoreD0(dstReg, IRDataType.LONG)
                    }
                    imm != null -> {
                        directDataDst(dstReg, IRDataType.LONG)?.let { d ->
                            emitLine("$op.l  #$imm, $d")
                            invalidateD0Cache()
                            return
                        }
                        emitLoadD0(dstReg, IRDataType.LONG)
                        emitLine("$op.l  #${imm}, $SCRATCH_D0")
                        invalidateD0Cache()
                        emitStoreD0(dstReg, IRDataType.LONG)
                    }
                    target != null -> {
                        emitLoadD0FromAddress(target, IRDataType.LONG)
                        emitLine("$op.l  ${operand(dstReg)}, $SCRATCH_D0")
                        invalidateD0Cache()
                        emitStoreD0ToAddress(target, IRDataType.LONG)
                    }
                }
            }
        }

        else -> TODO("DIV for ${type.name}")
    }
}

private fun AsmGen.emitModOp(dstReg: Int, srcReg: Int?, type: IRDataType, unsigned: Boolean, imm: Int?) {
    val opDiv = if (unsigned) "divu" else "divs"
    when (type) {
        IRDataType.BYTE -> {
            // divu.w/divs.w gives quotient low word, remainder high word. Swap to get remainder.
            when {
                srcReg != null -> {
                    directDataPair(dstReg, srcReg, IRDataType.BYTE)?.let { (d, s) ->
                        emitLine("move.b  $s, $SCRATCH_D1")
                        if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D1") else emitSignExtendByteToLong(SCRATCH_D1)
                        if (unsigned) emitLine("and.l  #\$ff, $d") else emitSignExtendByteToLong(d)
                        emitLine("$opDiv.w  $SCRATCH_D1, $d")
                        emitLine("swap  $d", "remainder to low word")
                        invalidateD0Cache()
                        return
                    }
                    emitLoadD0(dstReg, IRDataType.BYTE)
                    if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D0") else emitSignExtendByteToLong(SCRATCH_D0)
                    invalidateD0Cache()
                    emitLine("move.b  ${operand(srcReg)}, $SCRATCH_D1")
                    if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D1") else emitSignExtendByteToLong(SCRATCH_D1)
                    emitLine("$opDiv.w  $SCRATCH_D1, $SCRATCH_D0")
                    invalidateD0Cache()
                }
                imm != null -> {
                    directDataDst(dstReg, IRDataType.BYTE)?.let { d ->
                        if (unsigned) emitLine("and.l  #\$ff, $d") else emitSignExtendByteToLong(d)
                        emitLine("$opDiv.w  #${imm.and(0xffff)}, $d")
                        emitLine("swap  $d", "remainder to low word")
                        invalidateD0Cache()
                        return
                    }
                    emitLoadD0(dstReg, IRDataType.BYTE)
                    if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D0") else emitSignExtendByteToLong(SCRATCH_D0)
                    invalidateD0Cache()
                    emitLine("$opDiv.w  #${imm.and(0xffff)}, $SCRATCH_D0")
                    invalidateD0Cache()
                }
            }
            // After divu.w/divs.w: quotient in lower word, remainder in upper word of d0.
            emitLine("swap  $SCRATCH_D0", "remainder to low word")
            invalidateD0Cache()
            emitStoreD0(dstReg, IRDataType.BYTE)
        }

        IRDataType.WORD -> {
            // divu.w/divs.w: remainder in upper 16 bits, swap to get it; extend the dividend as above.
            fun loadDividend() {
                emitLoadD0(dstReg, IRDataType.WORD)
                if (unsigned) {
                    emitLine("and.l  #\$ffff, $SCRATCH_D0", "zero-extend upper word for divu.w")
                    invalidateD0Cache()
                } else {
                    emitLine("ext.l  $SCRATCH_D0", "sign-extend upper word for divs.w (signed 32-bit dividend)")
                    invalidateD0Cache()
                }
            }
            when {
                srcReg != null -> {
                    directDataPair(dstReg, srcReg, IRDataType.WORD)?.let { (d, s) ->
                        check(s != d) { "divisor and dividend share $d" }  // divisor is read after the dividend is extended
                        if (unsigned) {
                            emitLine("and.l  #\$ffff, $d", "zero-extend upper word for divu.w")
                        } else {
                            emitLine("ext.l  $d", "sign-extend upper word for divs.w (signed 32-bit dividend)")
                        }
                        emitLine("$opDiv.w  $s, $d")
                        emitLine("swap  $d", "remainder to low word")
                        invalidateD0Cache()
                        return
                    }
                    loadDividend()
                    emitLine("$opDiv.w  ${operand(srcReg)}, $SCRATCH_D0")
                    invalidateD0Cache()
                }
                imm != null -> {
                    directDataDst(dstReg, IRDataType.WORD)?.let { d ->
                        if (unsigned) {
                            emitLine("and.l  #\$ffff, $d", "zero-extend upper word for divu.w")
                        } else {
                            emitLine("ext.l  $d", "sign-extend upper word for divs.w (signed 32-bit dividend)")
                        }
                        emitLine("$opDiv.w  #${imm.and(0xffff)}, $d")
                        emitLine("swap  $d", "remainder to low word")
                        invalidateD0Cache()
                        return
                    }
                    loadDividend()
                    emitLine("$opDiv.w  #${imm.and(0xffff)}, $SCRATCH_D0")
                    invalidateD0Cache()
                }
            }
            emitLine("swap  $SCRATCH_D0", "remainder to low word")
            invalidateD0Cache()
            emitStoreD0(dstReg, IRDataType.WORD)
        }

        IRDataType.LONG -> {
            if(program.options.compTarget.cpu < CpuType.M68020) {
                // 68000: no divul.l/divsl.l; use a helper routine (utility.library or software fallback)
                val routine = if (unsigned) "p8_udivmod32" else "p8_sdivmod32"
                emitLongMathCall(dstReg, srcReg, imm, null, routine, SCRATCH_D1)
            } else {
                // 68020+ divul.l <ea>, Dr, Dq: Dr=remainder, Dq=quotient
                val opLong = if (unsigned) "divul.l" else "divsl.l"
                when {
                    srcReg != null -> {
                        emitLoadD0(dstReg, IRDataType.LONG)
                        emitLine("$opLong  ${operand(srcReg)}, $SCRATCH_D1:$SCRATCH_D0")
                        invalidateD0Cache()
                        invalidateD0CacheForSlot(dstReg)
                        emitLine("move.l  $SCRATCH_D1, ${operand(dstReg)}", "remainder")
                    }
                    imm != null -> {
                        emitLoadD0(dstReg, IRDataType.LONG)
                        emitLine("move.l  #${imm}, $SCRATCH_D1", "divisor")
                        emitLine("$opLong  $SCRATCH_D1, $SCRATCH_D1:$SCRATCH_D0")
                        invalidateD0Cache()
                        invalidateD0CacheForSlot(dstReg)
                        emitLine("move.l  $SCRATCH_D1, ${operand(dstReg)}", "remainder")
                    }
                }
            }
        }

        else -> TODO("MOD for ${type.name}")
    }
}

private fun AsmGen.emitDivModOp(dstReg: Int, remainderReg: Int, type: IRDataType, unsigned: Boolean, imm: Int?) {
    val opLong = if (unsigned) "divul.l" else "divsl.l"
    when (type) {
        IRDataType.BYTE -> {
            val opDiv = if (unsigned) "divu.w" else "divs.w"
            emitLoadD0(dstReg, IRDataType.BYTE)
            if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D0") else emitSignExtendByteToLong(SCRATCH_D0)
            invalidateD0Cache()
            if(imm!=null) {
                if (unsigned)
                    emitLine("move.l  #${imm.and(0xff)}, $SCRATCH_D1")
                else
                    emitLine("moveq  #${imm.and(0xff)}, $SCRATCH_D1")
                if (!unsigned) emitSignExtendByteToLong(SCRATCH_D1)
            } else {
                emitLine("move.b  ${operand(remainderReg)}, $SCRATCH_D1")
                if (unsigned) emitLine("and.l  #\$ff, $SCRATCH_D1") else emitSignExtendByteToLong(SCRATCH_D1)
            }
            emitLine("$opDiv  $SCRATCH_D1, $SCRATCH_D0")
            invalidateD0Cache()
            emitStoreD0(dstReg, IRDataType.BYTE)
            emitLine("swap  $SCRATCH_D0", "remainder to low word")
            invalidateD0Cache()
            emitStoreD0(remainderReg, IRDataType.BYTE)
        }

        IRDataType.WORD -> {
            val opDiv = if (unsigned) "divu.w" else "divs.w"
            if (unsigned) {
                emitLine("moveq  #0, $SCRATCH_D0", "clear upper word for divu.w")
                invalidateD0Cache()
            }
            emitLoadD0(dstReg, IRDataType.WORD)
            if (!unsigned) {
                emitLine("ext.l  $SCRATCH_D0", "sign-extend for divs.w")
                invalidateD0Cache()
            }
            if(imm!=null) {
                emitLine("$opDiv  #${imm.and(0xffff)}, $SCRATCH_D0")
            } else {
                emitLine("$opDiv  ${operand(remainderReg)}, $SCRATCH_D0")
            }
            invalidateD0Cache()
            emitStoreD0(dstReg, IRDataType.WORD)
            emitLine("swap  $SCRATCH_D0", "remainder to low word")
            invalidateD0Cache()
            emitStoreD0(remainderReg, IRDataType.WORD)
        }

        IRDataType.LONG -> {
            if(program.options.compTarget.cpu < CpuType.M68020) {
                // 68000: no divul.l/divsl.l; use a helper routine (utility.library or software fallback)
                val routine = if (unsigned) "p8_udivmod32" else "p8_sdivmod32"
                emitLoadD0(dstReg, IRDataType.LONG)
                if(imm!=null) {
                    emitLine("move.l  #${imm}, $SCRATCH_D1", "divisor")
                } else {
                    emitLine("move.l  ${operand(remainderReg)}, $SCRATCH_D1", "divisor")
                }
                invalidateD0Cache()
                emitLine("bsr  $routine")
                emitStoreD0(dstReg, IRDataType.LONG)
                invalidateD0CacheForSlot(remainderReg)
                emitLine("move.l  $SCRATCH_D1, ${operand(remainderReg)}", "remainder")
            } else {
                emitLoadD0(dstReg, IRDataType.LONG)
                if(imm!=null) {
                    emitLine("move.l  #${imm}, $SCRATCH_D1", "divisor")
                } else {
                    emitLine("move.l  ${operand(remainderReg)}, $SCRATCH_D1", "divisor")
                }
                emitLine("$opLong  $SCRATCH_D1, $SCRATCH_D0")
                invalidateD0Cache()
                emitStoreD0(dstReg, IRDataType.LONG)
                invalidateD0CacheForSlot(remainderReg)
                emitLine("move.l  $SCRATCH_D1, ${operand(remainderReg)}", "remainder")
            }
        }

        else -> TODO("DIVMOD for ${type.name}")
    }
}

// === Float arithmetic via 68881 FPU ===
// Float virtual registers live in fp2-fp7, in a frame slot, or in the memory regfile.
// Physical fp0 (FP_ACC) and fp1 (FP_SRC) are used as scratch during FPU operations.

private fun AsmGen.emitFloatBinaryOp(op: String, dst: RegisterNum, src: RegisterNum) {
    directFloatReg(dst)?.let { d ->
        emitFloatOpSrc(op, floatOperand(src), d)
        return
    }
    emitFmoveFloat(floatOperand(dst), FP_ACC)
    emitFmoveFloat(floatOperand(src), FP_SRC)
    emitLine("$op  $FP_SRC, $FP_ACC")
    emitFmoveFloat(FP_ACC, floatOperand(dst))
}

private fun AsmGen.emitFloatOpWithConstant(fpReg: RegisterNum, op: String, value: Double) {
    directFloatReg(fpReg)?.let { d ->
        if ((op == "fadd" || op == "fsub") && value == 1.0) {
            emitLine("$op.s  #$value, $d")
            return
        }
        val native = nativeFloatConst(value)
        if (native != null) {
            emitLine("fmovecr  #$native, $FP_SRC")
        } else {
            val label = makeFloatConstLabel(value)
            emitLine("lea  $label, $SCRATCH_A0")
            emitLine("fmove.s  ($SCRATCH_A0), $FP_SRC")
        }
        emitLine("$op  $FP_SRC, $d")
        return
    }
    emitLine("fmove.s  ${floatOperand(fpReg)}, $FP_ACC")
    val native = nativeFloatConst(value)
    if (native != null) {
        emitLine("fmovecr  #$native, $FP_SRC")
    } else {
        val label = makeFloatConstLabel(value)
        emitLine("lea  $label, $SCRATCH_A0")
        emitLine("fmove.s  ($SCRATCH_A0), $FP_SRC")
    }
    emitLine("$op  $FP_SRC, $FP_ACC")
    emitLine("fmove.s  $FP_ACC, ${floatOperand(fpReg)}")
}

private fun AsmGen.translateFloatArithmetic(insn: IRInstruction) {
    when (insn.opcode) {
        Opcode.INCM -> emitFloatMemUnary("fadd.s", resolveMemory(insn.requireMemory(), forFloat = true), immediateStr = "#1.0")
        Opcode.DECM -> emitFloatMemUnary("fsub.s", resolveMemory(insn.requireMemory(), forFloat = true), immediateStr = "#1.0")
        Opcode.NEGM -> emitFloatMemUnary("fneg", resolveMemory(insn.requireMemory(), forFloat = true))

        Opcode.INC -> {
            val dst = insn.requireFloatDest().floatNumber
            directFloatReg(dst)?.let { d ->
                emitLine("fadd.s  #1.0, $d")
                return
            }
            emitLine("fmove.s  ${floatOperand(dst)}, $FP_ACC")
            emitLine("fadd.s  #1.0, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatOperand(dst)}")
        }
        Opcode.DEC -> {
            val dst = insn.requireFloatDest().floatNumber
            directFloatReg(dst)?.let { d ->
                emitLine("fsub.s  #1.0, $d")
                return
            }
            emitLine("fmove.s  ${floatOperand(dst)}, $FP_ACC")
            emitLine("fsub.s  #1.0, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatOperand(dst)}")
        }
        Opcode.NEG -> {
            val dst = insn.requireFloatDest().floatNumber
            directFloatReg(dst)?.let { d ->
                emitLine("fneg  $d, $d")
                return
            }
            emitLine("fmove.s  ${floatOperand(dst)}, $FP_ACC")
            emitLine("fneg  $FP_ACC, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatOperand(dst)}")
        }

        Opcode.ADDR -> emitFloatBinaryOp("fadd", insn.requireFloatDest().floatNumber, insn.requireFloatSourceA().floatNumber)
        Opcode.ADD -> emitFloatOpWithConstant(insn.requireFloatDest().floatNumber, "fadd", insn.requireImmediateFloat())

        Opcode.SUBR -> emitFloatBinaryOp("fsub", insn.requireFloatDest().floatNumber, insn.requireFloatSourceA().floatNumber)
        Opcode.SUB -> emitFloatOpWithConstant(insn.requireFloatDest().floatNumber, "fsub", insn.requireImmediateFloat())

        Opcode.MULR, Opcode.MULSR -> emitFloatBinaryOp("fmul", insn.requireFloatDest().floatNumber, insn.requireFloatSourceA().floatNumber)
        Opcode.MUL, Opcode.MULS -> emitFloatOpWithConstant(insn.requireFloatDest().floatNumber, "fmul", insn.requireImmediateFloat())

        Opcode.DIVR, Opcode.DIVSR -> emitFloatBinaryOp("fdiv", insn.requireFloatDest().floatNumber, insn.requireFloatSourceA().floatNumber)
        Opcode.DIV, Opcode.DIVS -> emitFloatOpWithConstant(insn.requireFloatDest().floatNumber, "fdiv", insn.requireImmediateFloat())

        Opcode.SQRT -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            directFloatReg(dst)?.let { d ->
                emitFloatOpSrc("fsqrt", floatOperand(src), d)
                return
            }
            emitFmoveFloat(floatOperand(src), FP_ACC)
            emitLine("fsqrt  $FP_ACC, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(dst))
        }

        Opcode.SQUARE -> emitFloatBinaryOp("fmul", insn.requireFloatDest().floatNumber, insn.requireFloatSourceA().floatNumber)

        Opcode.ADDM -> emitFloatMemBinary("fadd", insn.requireFloatSourceA().floatNumber, resolveMemory(insn.requireMemory(), forFloat = true))
        Opcode.SUBM -> emitFloatMemBinary("fsub", insn.requireFloatSourceA().floatNumber, resolveMemory(insn.requireMemory(), forFloat = true))
        Opcode.MULM, Opcode.MULSM -> emitFloatMemBinary("fmul", insn.requireFloatSourceA().floatNumber, resolveMemory(insn.requireMemory(), forFloat = true))
        Opcode.DIVM, Opcode.DIVSM -> emitFloatMemBinary("fdiv", insn.requireFloatSourceA().floatNumber, resolveMemory(insn.requireMemory(), forFloat = true))

        Opcode.ADDIM -> emitFloatMemBinaryImmediate("fadd", insn.requireImmediateFloat(), resolveMemory(insn.requireMemory(), forFloat = true))
        Opcode.SUBIM -> emitFloatMemBinaryImmediate("fsub", insn.requireImmediateFloat(), resolveMemory(insn.requireMemory(), forFloat = true))

        else -> TODO("FLOAT arithmetic: ${insn.opcode}")
    }
}

private fun AsmGen.emitFloatMemBinaryImmediate(op: String, value: Double, target: String) {
    emitLine("fmove.s $target,$FP_ACC")
    when (value) {
        0.0 -> { emitLine("fmove.s #0.0,$FP_SRC"); emitLine("$op $FP_SRC,$FP_ACC") }
        1.0 -> {
            if (op == "fadd" || op == "fsub") {
                emitLine("f${op.substring(1)}.s #1.0,$FP_ACC")
            } else {
                return  // mul/div by 1.0 is no-op, just keep loaded value
            }
        }
        else -> {
            val lbl = makeFloatConstLabel(value)
            emitLine("lea $lbl,$SCRATCH_A0")
            emitLine("fmove.s ($SCRATCH_A0),$FP_SRC")
            emitLine("$op $FP_SRC,$FP_ACC")
        }
    }
    emitLine("fmove.s $FP_ACC,$target")
}

private fun AsmGen.emitFloatMemUnary(op: String, target: String, immediateStr: String? = null) {
    emitLine("fmove.s  $target, $SCRATCH_FP0")
    if (immediateStr != null)
        emitLine("$op  $immediateStr, $SCRATCH_FP0")
    else
        emitLine("$op  $SCRATCH_FP0, $SCRATCH_FP0")
    emitLine("fmove.s  $SCRATCH_FP0, $target")
}

private fun AsmGen.emitFloatMemBinary(op: String, fpReg1: RegisterNum, target: String) {
    emitLine("fmove.s  $target, $FP_ACC")
    emitFmoveFloat(floatOperand(fpReg1), FP_SRC)
    emitLine("$op  $FP_SRC, $FP_ACC")
    emitLine("fmove.s  $FP_ACC, $target")
}

private fun immVal(value: Int, type: IRDataType): Int = when(type) {
    IRDataType.BYTE -> value and 0xff
    IRDataType.WORD -> value and 0xffff
    IRDataType.LONG, IRDataType.POINTER -> value
    else -> value and 0xffff
}
