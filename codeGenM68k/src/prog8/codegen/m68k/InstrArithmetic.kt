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
            emitLine("addq${dtSuffix(type)}  #1, ${regAddr(reg)}")
            invalidateD0CacheForSlot(reg)
        }

        Opcode.INCM -> {
            val target = resolveMemory(insn.requireMemory())
            emitLine("addq${dtSuffix(type)}  #1, $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.DEC -> {
            val reg = insn.requireIntDest().intNumber
            emitLine("subq${dtSuffix(type)}  #1, ${regAddr(reg)}")
            invalidateD0CacheForSlot(reg)
        }

        Opcode.DECM -> {
            val target = resolveMemory(insn.requireMemory())
            emitLine("subq${dtSuffix(type)}  #1, $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.NEG -> {
            val reg = insn.requireIntDest().intNumber
            emitLine("neg${dtSuffix(type)}  ${regAddr(reg)}")
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
            loadRegOrZeroExtendToD0(srcReg, type)
            emitLine("add${dtSuffix(type)}  d0, ${regAddr(dstReg)}")
            invalidateD0CacheForSlot(dstReg)
        }

        Opcode.ADD -> {
            val reg = insn.requireIntDest().intNumber
            val value = insn.requireImmediateInt()
            if(value in 1..8) {
                emitLine("addq${dtSuffix(type)}  #$value, ${regAddr(reg)}")
            } else {
                emitLine("add${dtSuffix(type)}  #${immVal(value, type)}, ${regAddr(reg)}")
            }
            invalidateD0CacheForSlot(reg)
        }

        Opcode.ADDM -> {
            val reg = insn.requireIntSourceA().intNumber
            val target = resolveMemory(insn.requireMemory())
            val sv = dtSuffix(type)
            emitLoadD0(reg, type)
            emitLine("add$sv  d0, $target")
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
            loadRegOrZeroExtendToD0(srcReg, type)
            emitLine("sub${dtSuffix(type)}  d0, ${regAddr(dstReg)}")
            invalidateD0CacheForSlot(dstReg)
        }

        Opcode.SUB -> {
            val reg = insn.requireIntDest().intNumber
            val value = insn.requireImmediateInt()
            if(value in 1..8) {
                emitLine("subq${dtSuffix(type)}  #$value, ${regAddr(reg)}")
            } else {
                emitLine("sub${dtSuffix(type)}  #${immVal(value, type)}, ${regAddr(reg)}")
            }
            invalidateD0CacheForSlot(reg)
        }

        Opcode.SUBM -> {
            val reg = insn.requireIntSourceA().intNumber
            val target = resolveMemory(insn.requireMemory())
            val sv = dtSuffix(type)
            emitLoadD0(reg, type)
            emitLine("sub$sv  d0, $target")
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

        // --- Multiply (unsigned) ---
        // M68k has no mulu.b or muls.b; use .w with zero-extension for byte.

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

        // --- Modulus ---
        // Use divu.w/divs.w remainder (upper 16 bits) for byte/word; divul/divsl for long (68020+)

        Opcode.MODR -> emitModOp(insn.requireIntDest().intNumber, insn.requireIntSourceA().intNumber, type, unsigned=true, imm=null)
        Opcode.MOD -> emitModOp(insn.requireIntDest().intNumber, null, type, unsigned=true, imm=insn.requireImmediateInt())
        Opcode.MODSR -> emitModOp(insn.requireIntDest().intNumber, insn.requireIntSourceA().intNumber, type, unsigned=false, imm=null)
        Opcode.MODS -> emitModOp(insn.requireIntDest().intNumber, null, type, unsigned=false, imm=insn.requireImmediateInt())

        // --- DIVMOD (unsigned) ---
        // 68020+ divul.l gives quotient + remainder in one instruction

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
                    emitLine("move.b  ${regAddr(srcReg)}, math._sqrt_ub.value", "sqrt byte")
                    invalidateD0Cache()
                    emitLine("bsr  math._sqrt_ub")
                    emitStoreD0(dstReg, IRDataType.BYTE)
                }
                IRDataType.WORD -> {
                    emitLine("move.w  ${regAddr(srcReg)}, math._sqrt_uw.value", "sqrt word")
                    invalidateD0Cache()
                    emitLine("bsr  math._sqrt_uw")
                    emitStoreD0(dstReg, IRDataType.BYTE)
                }
                IRDataType.LONG, IRDataType.POINTER -> {
                    emitLine("move.l  ${regAddr(srcReg)}, math._sqrt_l.value", "sqrt long")
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
                    emitLine($$"and.l  #\$ff, d0", "zero extend")
                    invalidateD0Cache()
                    emitLine("mulu.w  d0, d0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.BYTE)
                }
                IRDataType.WORD -> {
                    emitLoadD0(srcReg, IRDataType.WORD)
                    emitLine("mulu.w  d0, d0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                IRDataType.LONG, IRDataType.POINTER -> {
                    emitLoadD0(srcReg, IRDataType.LONG)
                    emitLine("mulu.l  d0, d0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.LONG)
                }
            }
        }

        Opcode.CMP -> {
            val leftReg = insn.requireIntSourceA().intNumber
            val rightReg = insn.requireSrcB().intNumber
            val s = dtSuffix(type)
            emitLoadD0(leftReg, type)
            emitLine("cmp$s  ${regAddr(rightReg)}, d0")
        }

        Opcode.CMPI -> {
            val reg = insn.requireIntSourceA().intNumber
            val masked = immVal(insn.requireImmediateInt(), type)
            // cmpi.x #0, operand can be replaced by the faster tst.x operand
            if(masked==0)
                emitLine("tst${dtSuffix(type)}  ${regAddr(reg)}")
            else
                emitLine("cmpi${dtSuffix(type)}  #$masked, ${regAddr(reg)}")
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
            emitLine("move.l  ${regAddr(srcReg)}, d1")
        }
        imm != null -> {
            emitLoadD0(dstReg, IRDataType.LONG)
            emitLine("move.l  #$imm, d1")
        }
        target != null -> {
            emitLoadD0FromAddress(target, IRDataType.LONG)
            emitLine("move.l  ${regAddr(dstReg)}, d1")
        }
    }
    invalidateD0Cache()
    emitLine("bsr  $routine")
    val storeTarget = target ?: regAddr(dstReg)
    if (resultReg == "d0") {
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
                    emitLoadD0(dstReg, IRDataType.BYTE)
                    if (unsigned) emitLine($$"and.l  #$ff, d0") else emitSignExtendByteToLong("d0")
                    invalidateD0Cache()
                    emitLine("move.w  d0, d2")
                    emitLoadD0(srcReg, IRDataType.BYTE)
                    if (unsigned) emitLine($$"and.l  #$ff, d0") else emitSignExtendByteToLong("d0")
                    invalidateD0Cache()
                    emitLine("$op.w  d2, d0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.BYTE)
                }
                imm != null -> {
                    emitLoadD0(dstReg, IRDataType.BYTE)
                    if (unsigned) emitLine($$"and.l  #$ff, d0") else emitSignExtendByteToLong("d0")
                    invalidateD0Cache()
                    emitLine("$op.w  #${imm.and(0xffff)}, d0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.BYTE)
                }
                target != null -> {
                    emitLine("move.b  $target, d1")
                    if (unsigned) emitLine($$"and.l  #$ff, d1") else emitSignExtendByteToLong("d1")
                    emitLine("move.w  d1, d2")
                    emitLoadD0(dstReg, IRDataType.BYTE)
                    if (unsigned) emitLine($$"and.l  #$ff, d0") else emitSignExtendByteToLong("d0")
                    invalidateD0Cache()
                    emitLine("$op.w  d2, d0")
                    invalidateD0Cache()
                    emitStoreD0ToAddress(target, IRDataType.BYTE)
                }
            }
        }

        IRDataType.WORD -> {
            // mulu.w/muls.w is native (16x16→32, lower 16 are result)
            // destination must be a data register, not memory
            when {
                srcReg != null -> {
                    emitLoadD0(dstReg, IRDataType.WORD)
                    emitLine("$op.w  ${regAddr(srcReg)}, d0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                imm != null -> {
                    emitLoadD0(dstReg, IRDataType.WORD)
                    emitLine("$op.w  #${imm.and(0xffff)}, d0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                target != null -> {
                    emitLoadD0(dstReg, IRDataType.WORD)
                    emitLine("$op.w  $target, d0")
                    invalidateD0Cache()
                    emitStoreD0ToAddress(target, IRDataType.WORD)
                }
            }
        }

        IRDataType.LONG -> {
            if(program.options.compTarget.cpu < CpuType.M68020) {
                // 68000: no mulu.l/muls.l; use a helper routine (utility.library or software fallback)
                val routine = if (unsigned) "p8_umult32" else "p8_smult32"
                emitLongMathCall(dstReg, srcReg, imm, target, routine, "d0")
            } else {
                // 68020+ mulu.l/muls.l (32x32→64, lower 32 are result)
                // destination must be a data register, not memory
                when {
                    srcReg != null -> {
                        emitLoadD0(dstReg, IRDataType.LONG)
                        emitLine("$op.l  ${regAddr(srcReg)}, d0")
                        invalidateD0Cache()
                        emitStoreD0(dstReg, IRDataType.LONG)
                    }
                    imm != null -> {
                        emitLoadD0(dstReg, IRDataType.LONG)
                        emitLine("$op.l  #${imm}, d0")
                        invalidateD0Cache()
                        emitStoreD0(dstReg, IRDataType.LONG)
                    }
                    target != null -> {
                        emitLoadD0(dstReg, IRDataType.LONG)
                        emitLine("$op.l  $target, d0")
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
                    emitLoadD0(dstReg, IRDataType.BYTE)
                    if (unsigned) emitLine($$"and.l  #$ff, d0") else emitSignExtendByteToLong("d0")
                    invalidateD0Cache()
                    emitLine("move.b  ${regAddr(srcReg)}, d1")
                    if (unsigned) emitLine($$"and.l  #$ff, d1") else emitSignExtendByteToLong("d1")
                    emitLine("$op.w  d1, d0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.BYTE)
                }
                imm != null -> {
                    emitLoadD0(dstReg, IRDataType.BYTE)
                    if (unsigned) emitLine($$"and.l  #$ff, d0") else emitSignExtendByteToLong("d0")
                    invalidateD0Cache()
                    emitLine("$op.w  #${imm.and(0xffff)}, d0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.BYTE)
                }
                target != null -> {
                    emitLoadD0FromAddress(target, IRDataType.BYTE)
                    if (unsigned) emitLine($$"and.l  #$ff, d0") else emitSignExtendByteToLong("d0")
                    invalidateD0Cache()
                    emitLine("move.b  ${regAddr(dstReg)}, d1")
                    if (unsigned) emitLine($$"and.l  #$ff, d1") else emitSignExtendByteToLong("d1")
                    emitLine("$op.w  d1, d0")
                    invalidateD0Cache()
                    emitStoreD0ToAddress(target, IRDataType.BYTE)
                }
            }
        }

        IRDataType.WORD -> {
            // divu.w/divs.w divide a 32-bit dividend (Dd) by a 16-bit divisor.
            // divs.w requires the 32-bit dividend to be SIGN-extended; for unsigned it
            // must be ZERO-extended. A plain `move.w` does not extend the upper word, so
            // extend explicitly here (this is what was wrong: signed dividends ended up
            // zero-extended, e.g. -1000 became +64536).
            fun loadDividend() {
                emitLoadD0(dstReg, IRDataType.WORD)
                if (unsigned) {
                    emitLine($$"and.l  #$ffff, d0", "zero-extend upper word for divu.w")
                    invalidateD0Cache()
                } else {
                    emitLine("ext.l  d0", "sign-extend upper word for divs.w (signed 32-bit dividend)")
                    invalidateD0Cache()
                }
            }
            when {
                srcReg != null -> {
                    loadDividend()
                    emitLine("$op.w  ${regAddr(srcReg)}, d0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                imm != null -> {
                    loadDividend()
                    emitLine("$op.w  #${imm.and(0xffff)}, d0")
                    invalidateD0Cache()
                    emitStoreD0(dstReg, IRDataType.WORD)
                }
                target != null -> {
                    emitLoadD0FromAddress(target, IRDataType.WORD)
                    if (unsigned) {
                        emitLine($$"and.l  #$ffff, d0", "zero-extend upper word for divu.w")
                        invalidateD0Cache()
                    } else {
                        emitLine("ext.l  d0", "sign-extend upper word for divs.w (signed 32-bit dividend)")
                        invalidateD0Cache()
                    }
                    emitLine("$op.w  ${regAddr(dstReg)}, d0")
                    invalidateD0Cache()
                    emitStoreD0ToAddress(target, IRDataType.WORD)
                }
            }
        }

        IRDataType.LONG -> {
            if(program.options.compTarget.cpu < CpuType.M68020) {
                // 68000: no divu.l/divs.l; use a helper routine (utility.library or software fallback)
                val routine = if (unsigned) "p8_udivmod32" else "p8_sdivmod32"
                emitLongMathCall(dstReg, srcReg, imm, target, routine, "d0")
            } else {
                // 68020+ divu.l/divs.l (32/32→32)
                when {
                    srcReg != null -> {
                        emitLoadD0(dstReg, IRDataType.LONG)
                        emitLine("$op.l  ${regAddr(srcReg)}, d0")
                        invalidateD0Cache()
                        emitStoreD0(dstReg, IRDataType.LONG)
                    }
                    imm != null -> {
                        emitLoadD0(dstReg, IRDataType.LONG)
                        emitLine("$op.l  #${imm}, d0")
                        invalidateD0Cache()
                        emitStoreD0(dstReg, IRDataType.LONG)
                    }
                    target != null -> {
                        emitLoadD0FromAddress(target, IRDataType.LONG)
                        emitLine("$op.l  ${regAddr(dstReg)}, d0")
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
            // divu.w gives quotient low word, remainder high word. Swap to get remainder.
            when {
                srcReg != null -> {
                    emitLoadD0(dstReg, IRDataType.BYTE)
                    if (unsigned) emitLine($$"and.l  #$ff, d0") else emitSignExtendByteToLong("d0")
                    invalidateD0Cache()
                    emitLine("move.b  ${regAddr(srcReg)}, d1")
                    if (unsigned) emitLine($$"and.l  #$ff, d1") else emitSignExtendByteToLong("d1")
                    emitLine("$opDiv.w  d1, d0")
                    invalidateD0Cache()
                }
                imm != null -> {
                    emitLoadD0(dstReg, IRDataType.BYTE)
                    if (unsigned) emitLine($$"and.l  #$ff, d0") else emitSignExtendByteToLong("d0")
                    invalidateD0Cache()
                    emitLine("$opDiv.w  #${imm.and(0xffff)}, d0")
                    invalidateD0Cache()
                }
            }
            // After divu.w/divs.w: quotient in lower word, remainder in upper word of d0.
            emitLine("swap  d0", "remainder to low word")
            invalidateD0Cache()
            emitStoreD0(dstReg, IRDataType.BYTE)
        }

        IRDataType.WORD -> {
            // divu.w/divs.w: remainder in upper 16 bits, swap to get it.
            // divs.w requires the 32-bit dividend to be SIGN-extended; for unsigned it
            // must be ZERO-extended. A plain `move.w` does not extend the upper word, so
            // extend explicitly (signed dividends were wrongly zero-extended before).
            fun loadDividend() {
                emitLoadD0(dstReg, IRDataType.WORD)
                if (unsigned) {
                    emitLine($$"and.l  #$ffff, d0", "zero-extend upper word for divu.w")
                    invalidateD0Cache()
                } else {
                    emitLine("ext.l  d0", "sign-extend upper word for divs.w (signed 32-bit dividend)")
                    invalidateD0Cache()
                }
            }
            when {
                srcReg != null -> {
                    loadDividend()
                    emitLine("$opDiv.w  ${regAddr(srcReg)}, d0")
                    invalidateD0Cache()
                }
                imm != null -> {
                    loadDividend()
                    emitLine("$opDiv.w  #${imm.and(0xffff)}, d0")
                    invalidateD0Cache()
                }
            }
            emitLine("swap  d0", "remainder to low word")
            invalidateD0Cache()
            emitStoreD0(dstReg, IRDataType.WORD)
        }

        IRDataType.LONG -> {
            if(program.options.compTarget.cpu < CpuType.M68020) {
                // 68000: no divul.l/divsl.l; use a helper routine (utility.library or software fallback)
                val routine = if (unsigned) "p8_udivmod32" else "p8_sdivmod32"
                emitLongMathCall(dstReg, srcReg, imm, null, routine, "d1")
            } else {
                // 68020+ divul.l <ea>, Dr, Dq: Dr=remainder, Dq=quotient
                val opLong = if (unsigned) "divul.l" else "divsl.l"
                when {
                    srcReg != null -> {
                        emitLoadD0(dstReg, IRDataType.LONG)
                        emitLine("$opLong  ${regAddr(srcReg)}, d1:d0")
                        invalidateD0Cache()
                        invalidateD0CacheForSlot(dstReg)
                        emitLine("move.l  d1, ${regAddr(dstReg)}", "remainder")
                    }
                    imm != null -> {
                        emitLoadD0(dstReg, IRDataType.LONG)
                        emitLine("move.l  #${imm}, d2", "divisor")
                        emitLine("$opLong  d2, d1:d0")
                        invalidateD0Cache()
                        invalidateD0CacheForSlot(dstReg)
                        emitLine("move.l  d1, ${regAddr(dstReg)}", "remainder")
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
            if (unsigned) emitLine($$"and.l  #$ff, d0") else emitSignExtendByteToLong("d0")
            invalidateD0Cache()
            if(imm!=null) {
                if (unsigned)
                    emitLine("move.l  #${imm.and(0xff)}, d1")
                else
                    emitLine("moveq  #${imm.and(0xff)}, d1")
                if (!unsigned) emitSignExtendByteToLong("d1")
            } else {
                emitLine("move.b  ${regAddr(remainderReg)}, d1")
                if (unsigned) emitLine($$"and.l  #$ff, d1") else emitSignExtendByteToLong("d1")
            }
            emitLine("$opDiv  d1, d0")
            invalidateD0Cache()
            emitStoreD0(dstReg, IRDataType.BYTE)
            emitLine("swap  d0", "remainder to low word")
            invalidateD0Cache()
            emitStoreD0(remainderReg, IRDataType.BYTE)
        }

        IRDataType.WORD -> {
            val opDiv = if (unsigned) "divu.w" else "divs.w"
            if (unsigned) {
                emitLine("moveq  #0, d0", "clear upper word for divu.w")
                invalidateD0Cache()
            }
            emitLoadD0(dstReg, IRDataType.WORD)
            if (!unsigned) {
                emitLine("ext.l  d0", "sign-extend for divs.w")
                invalidateD0Cache()
            }
            if(imm!=null) {
                emitLine("$opDiv  #${imm.and(0xffff)}, d0")
            } else {
                emitLine("$opDiv  ${regAddr(remainderReg)}, d0")
            }
            invalidateD0Cache()
            emitStoreD0(dstReg, IRDataType.WORD)
            emitLine("swap  d0", "remainder to low word")
            invalidateD0Cache()
            emitStoreD0(remainderReg, IRDataType.WORD)
        }

        IRDataType.LONG -> {
            if(program.options.compTarget.cpu < CpuType.M68020) {
                // 68000: no divul.l/divsl.l; use a helper routine (utility.library or software fallback)
                val routine = if (unsigned) "p8_udivmod32" else "p8_sdivmod32"
                emitLoadD0(dstReg, IRDataType.LONG)
                if(imm!=null) {
                    emitLine("move.l  #${imm}, d1", "divisor")
                } else {
                    emitLine("move.l  ${regAddr(remainderReg)}, d1", "divisor")
                }
                invalidateD0Cache()
                emitLine("bsr  $routine")
                emitStoreD0(dstReg, IRDataType.LONG)
                invalidateD0CacheForSlot(remainderReg)
                emitLine("move.l  d1, ${regAddr(remainderReg)}", "remainder")
            } else {
                emitLoadD0(dstReg, IRDataType.LONG)
                if(imm!=null) {
                    emitLine("move.l  #${imm}, d1", "divisor")
                } else {
                    emitLine("move.l  ${regAddr(remainderReg)}, d1", "divisor")
                }
                emitLine("$opLong  d1, d0")
                invalidateD0Cache()
                emitStoreD0(dstReg, IRDataType.LONG)
                invalidateD0CacheForSlot(remainderReg)
                emitLine("move.l  d1, ${regAddr(remainderReg)}", "remainder")
            }
        }

        else -> TODO("DIVMOD for ${type.name}")
    }
}

// === Float arithmetic via 68881 FPU ===
// Float virtual registers live in the memory regfile. Physical fp0 (FP_ACC) and
// fp1 (FP_SRC) are used as scratch during FPU operations.

private fun AsmGen.emitFloatBinaryOp(op: String, dst: RegisterNum, src: RegisterNum) {
    emitLine("fmove.s  ${floatRegFileAddr(dst)}, $FP_ACC")
    emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_SRC")
    emitLine("$op  $FP_SRC, $FP_ACC")
    emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
}

private fun AsmGen.emitFloatOpWithConstant(fpReg: RegisterNum, op: String, value: Double) {
    emitLine("fmove.s  ${floatRegFileAddr(fpReg)}, $FP_ACC")
    val native = nativeFloatConst(value)
    if (native != null) {
        emitLine("fmovecr  #$native, $FP_SRC")
    } else {
        val label = makeFloatConstLabel(value)
        emitLine("lea  $label, a0")
        emitLine("fmove.s  (a0), $FP_SRC")
    }
    emitLine("$op  $FP_SRC, $FP_ACC")
    emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fpReg)}")
}

private fun AsmGen.translateFloatArithmetic(insn: IRInstruction) {
    when (insn.opcode) {
        Opcode.INCM -> emitFloatMemUnary("fadd.s", resolveMemory(insn.requireMemory(), forFloat = true), immediateStr = "#1.0")
        Opcode.DECM -> emitFloatMemUnary("fsub.s", resolveMemory(insn.requireMemory(), forFloat = true), immediateStr = "#1.0")
        Opcode.NEGM -> emitFloatMemUnary("fneg", resolveMemory(insn.requireMemory(), forFloat = true))

        Opcode.INC -> {
            val dst = insn.requireFloatDest().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(dst)}, $FP_ACC")
            emitLine("fadd.s  #1.0, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }
        Opcode.DEC -> {
            val dst = insn.requireFloatDest().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(dst)}, $FP_ACC")
            emitLine("fsub.s  #1.0, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }
        Opcode.NEG -> {
            val dst = insn.requireFloatDest().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(dst)}, $FP_ACC")
            emitLine("fneg  $FP_ACC, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
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
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("fsqrt  $FP_ACC, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
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
            emitLine("lea $lbl,a0")
            emitLine("fmove.s (a0),$FP_SRC")
            emitLine("$op $FP_SRC,$FP_ACC")
        }
    }
    emitLine("fmove.s $FP_ACC,$target")
}

private fun AsmGen.emitFloatMemUnary(op: String, target: String, immediateStr: String? = null) {
    emitLine("fmove.s  $target, fp0")
    if (immediateStr != null)
        emitLine("$op  $immediateStr, fp0")
    else
        emitLine("$op  fp0, fp0")
    emitLine("fmove.s  fp0, $target")
}

private fun AsmGen.emitFloatMemBinary(op: String, fpReg1: RegisterNum, target: String) {
    emitLine("fmove.s  $target, $FP_ACC")
    emitLine("fmove.s  ${floatRegFileAddr(fpReg1)}, $FP_SRC")
    emitLine("$op  $FP_SRC, $FP_ACC")
    emitLine("fmove.s  $FP_ACC, $target")
}

private fun immVal(value: Int, type: IRDataType): Int = when(type) {
    IRDataType.BYTE -> value and 0xff
    IRDataType.WORD -> value and 0xffff
    IRDataType.LONG, IRDataType.POINTER -> value
    else -> value and 0xffff
}
