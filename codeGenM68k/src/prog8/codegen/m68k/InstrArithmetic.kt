package prog8.codegen.m68k

import prog8.intermediate.*

internal fun AsmGen.translateArithmetic(insn: IRInstruction) {
    val type = insn.type ?: IRDataType.BYTE
    val r1 = insn.reg1
    val r2 = insn.reg2
    val imm = insn.immediate
    val addr = insn.address
    val label = insn.labelSymbol
    val offset = insn.labelSymbolOffset

    if (type == IRDataType.FLOAT) {
        translateFloatArithmetic(insn)
        return
    }

    when (insn.opcode) {
        Opcode.INC -> {
            val reg = r1 ?: error("INC needs reg1")
            emitLine("addq${dtSuffix(type)}  #1, ${regAddr(reg)}")
        }

        Opcode.INCM -> {
            val target = resolveAddress(addr, label, offset)
            emitLine("addq${dtSuffix(type)}  #1, $target")
        }

        Opcode.DEC -> {
            val reg = r1 ?: error("DEC needs reg1")
            emitLine("subq${dtSuffix(type)}  #1, ${regAddr(reg)}")
        }

        Opcode.DECM -> {
            val target = resolveAddress(addr, label, offset)
            emitLine("subq${dtSuffix(type)}  #1, $target")
        }

        Opcode.NEG -> {
            val reg = r1 ?: error("NEG needs reg1")
            emitLine("neg${dtSuffix(type)}  ${regAddr(reg)}")
        }

        Opcode.NEGM -> {
            val target = resolveAddress(addr, label, offset)
            emitLine("neg${dtSuffix(type)}  $target")
        }

        Opcode.ADDR -> {
            val dstReg = r1 ?: error("ADDR needs reg1")
            val srcReg = r2 ?: error("ADDR needs reg2")
            emitLine("move${dtSuffix(type)}  ${regAddr(srcReg)}, d0")
            emitLine("add${dtSuffix(type)}  d0, ${regAddr(dstReg)}")
        }

        Opcode.ADD -> {
            val reg = r1 ?: error("ADD needs reg1")
            val value = imm ?: error("ADD needs immediate")
            emitLine("add${dtSuffix(type)}  #${immVal(value, type)}, ${regAddr(reg)}")
        }

        Opcode.ADDM -> {
            val reg = r1 ?: error("ADDM needs reg1")
            val target = resolveAddress(addr, label, offset)
            val sv = dtSuffix(type)
            emitLine("move$sv  ${regAddr(reg)}, d0")
            emitLine("add$sv  d0, $target")
        }

        Opcode.SUBR -> {
            val dstReg = r1 ?: error("SUBR needs reg1")
            val srcReg = r2 ?: error("SUBR needs reg2")
            emitLine("move${dtSuffix(type)}  ${regAddr(srcReg)}, d0")
            emitLine("sub${dtSuffix(type)}  d0, ${regAddr(dstReg)}")
        }

        Opcode.SUB -> {
            val reg = r1 ?: error("SUB needs reg1")
            val value = imm ?: error("SUB needs immediate")
            emitLine("sub${dtSuffix(type)}  #${immVal(value, type)}, ${regAddr(reg)}")
        }

        Opcode.SUBM -> {
            val reg = r1 ?: error("SUBM needs reg1")
            val target = resolveAddress(addr, label, offset)
            val sv = dtSuffix(type)
            emitLine("move$sv  ${regAddr(reg)}, d0")
            emitLine("sub$sv  d0, $target")
        }

        // --- Multiply (unsigned) ---
        // M68k has no mulu.b or muls.b; use .w with zero-extension for byte.

        Opcode.MULR -> {
            val dstReg = r1 ?: error("MULR needs reg1")
            val srcReg = r2 ?: error("MULR needs reg2")
            emitMulOp(dstReg, srcReg, type, unsigned=true, imm=null, target=null)
        }

        Opcode.MUL -> {
            val reg = r1 ?: error("MUL needs reg1")
            val value = imm ?: error("MUL needs immediate")
            emitMulOp(reg, null, type, unsigned=true, imm=value, target=null)
        }

        Opcode.MULM -> {
            val reg = r1 ?: error("MULM needs reg1")
            val target = resolveAddress(addr, label, offset)
            emitMulOp(reg, null, type, unsigned=true, imm=null, target=target)
        }

        // --- Multiply (signed) ---

        Opcode.MULSR -> {
            val dstReg = r1 ?: error("MULSR needs reg1")
            val srcReg = r2 ?: error("MULSR needs reg2")
            emitMulOp(dstReg, srcReg, type, unsigned=false, imm=null, target=null)
        }

        Opcode.MULS -> {
            val reg = r1 ?: error("MULS needs reg1")
            val value = imm ?: error("MULS needs immediate")
            emitMulOp(reg, null, type, unsigned=false, imm=value, target=null)
        }

        Opcode.MULSM -> {
            val reg = r1 ?: error("MULSM needs reg1")
            val target = resolveAddress(addr, label, offset)
            emitMulOp(reg, null, type, unsigned=false, imm=null, target=target)
        }

        // --- Divide (unsigned) ---

        Opcode.DIVR -> {
            val dstReg = r1 ?: error("DIVR needs reg1")
            val srcReg = r2 ?: error("DIVR needs reg2")
            emitDivOp(dstReg, srcReg, type, unsigned=true, imm=null, target=null)
        }

        Opcode.DIV -> {
            val reg = r1 ?: error("DIV needs reg1")
            val value = imm ?: error("DIV needs immediate")
            emitDivOp(reg, null, type, unsigned=true, imm=value, target=null)
        }

        Opcode.DIVM -> {
            val reg = r1 ?: error("DIVM needs reg1")
            val target = resolveAddress(addr, label, offset)
            emitDivOp(reg, null, type, unsigned=true, imm=null, target=target)
        }

        // --- Divide (signed) ---

        Opcode.DIVSR -> {
            val dstReg = r1 ?: error("DIVSR needs reg1")
            val srcReg = r2 ?: error("DIVSR needs reg2")
            emitDivOp(dstReg, srcReg, type, unsigned=false, imm=null, target=null)
        }

        Opcode.DIVS -> {
            val reg = r1 ?: error("DIVS needs reg1")
            val value = imm ?: error("DIVS needs immediate")
            emitDivOp(reg, null, type, unsigned=false, imm=value, target=null)
        }

        Opcode.DIVSM -> {
            val reg = r1 ?: error("DIVSM needs reg1")
            val target = resolveAddress(addr, label, offset)
            emitDivOp(reg, null, type, unsigned=false, imm=null, target=target)
        }

        // --- Modulus ---
        // Use divu.w/divs.w remainder (upper 16 bits) for byte/word; divul/divsl for long (68020+)

        Opcode.MODR -> {
            val dstReg = r1 ?: error("MODR needs reg1")
            val srcReg = r2 ?: error("MODR needs reg2")
            emitModOp(dstReg, srcReg, type, unsigned=true, imm=null)
        }

        Opcode.MOD -> {
            val dstReg = r1 ?: error("MOD needs reg1")
            val divisor = imm ?: error("MOD needs immediate")
            emitModOp(dstReg, null, type, unsigned=true, imm=divisor)
        }

        Opcode.MODSR -> {
            val dstReg = r1 ?: error("MODSR needs reg1")
            val srcReg = r2 ?: error("MODSR needs reg2")
            emitModOp(dstReg, srcReg, type, unsigned=false, imm=null)
        }

        Opcode.MODS -> {
            val dstReg = r1 ?: error("MODS needs reg1")
            val divisor = imm ?: error("MODS needs immediate")
            emitModOp(dstReg, null, type, unsigned=false, imm=divisor)
        }

        // --- DIVMOD (unsigned) ---
        // 68020+ divul.l gives quotient + remainder in one instruction

        Opcode.DIVMODR -> {
            emitDivModOp(r1 ?: error("DIVMODR needs reg1"), r2 ?: error("DIVMODR needs reg2"), type, unsigned=true, imm=null)
        }

        Opcode.DIVMOD -> {
            emitDivModOp(r1 ?: error("DIVMOD needs reg1"), null, type, unsigned=true, imm=imm ?: error("DIVMOD needs immediate"))
        }

        Opcode.SDIVMODR -> {
            emitDivModOp(r1 ?: error("SDIVMODR needs reg1"), r2 ?: error("SDIVMODR needs reg2"), type, unsigned=false, imm=null)
        }

        Opcode.SDIVMOD -> {
            emitDivModOp(r1 ?: error("SDIVMOD needs reg1"), null, type, unsigned=false, imm=imm ?: error("SDIVMOD needs immediate"))
        }

        // --- Integer sqrt/square ---

        Opcode.SQRT -> {
            val dstReg = r1 ?: error("SQRT needs reg1 (output)")
            val srcReg = r2 ?: error("SQRT needs reg2 (input)")
            when (type) {
                IRDataType.BYTE -> {
                    emitLine("move.b  ${regAddr(srcReg)}, math._sqrt_ub.value", "sqrt byte")
                    emitLine("jsr  math._sqrt_ub")
                    emitLine("move.b  d0, ${regAddr(dstReg)}")
                }
                IRDataType.WORD -> {
                    emitLine("move.w  ${regAddr(srcReg)}, math._sqrt_uw.value", "sqrt word")
                    emitLine("jsr  math._sqrt_uw")
                    emitLine("move.b  d0, ${regAddr(dstReg)}")
                }
                IRDataType.LONG, IRDataType.POINTER -> {
                    emitLine("move.l  ${regAddr(srcReg)}, math._sqrt_l.value", "sqrt long")
                    emitLine("jsr  math._sqrt_l")
                    emitLine("move.w  d0, ${regAddr(dstReg)}")
                }
            }
        }
        Opcode.SQUARE -> {
            val dstReg = r1 ?: error("SQUARE needs reg1")
            val srcReg = r2 ?: error("SQUARE needs reg2")
            when (type) {
                IRDataType.BYTE -> {
                    emitLine("move.b  ${regAddr(srcReg)}, d0")
                    emitLine($$"and.l  #\$ff, d0", "zero extend")
                    emitLine("mulu.w  d0, d0")
                    emitLine("move.b  d0, ${regAddr(dstReg)}")
                }
                IRDataType.WORD -> {
                    emitLine("move.w  ${regAddr(srcReg)}, d0")
                    emitLine("mulu.w  d0, d0")
                    emitLine("move.w  d0, ${regAddr(dstReg)}")
                }
                IRDataType.LONG, IRDataType.POINTER -> {
                    emitLine("move.l  ${regAddr(srcReg)}, d0")
                    emitLine("mulu.l  d0, d0")
                    emitLine("move.l  d0, ${regAddr(dstReg)}")
                }
            }
        }

        Opcode.CMP -> {
            val leftReg = insn.reg1 ?: error("CMP needs reg1")
            val rightReg = insn.reg2 ?: error("CMP needs reg2")
            emitLine("move${dtSuffix(type)}  ${regAddr(leftReg)}, d0")
            emitLine("cmp${dtSuffix(type)}  ${regAddr(rightReg)}, d0")
        }

        Opcode.CMPI -> {
            val reg = insn.reg1 ?: error("CMPI needs reg1")
            val value = insn.immediate ?: error("CMPI needs immediate")
            val masked = immVal(value, type)
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

private fun AsmGen.emitMulOp(dstReg: Int, srcReg: Int?, type: IRDataType, unsigned: Boolean, imm: Int?, target: String?) {
    val op = if (unsigned) "mulu" else "muls"
    when (type) {
        IRDataType.BYTE -> {
            // No .b multiply on M68k: zero-extend to word, mulu.w/muls.w, store low byte
            val loadOp = if (unsigned) $$"and.l  #$ff, d0" else "extb.l  d0"
            when {
                srcReg != null -> {
                    emitLine("move.b  ${regAddr(srcReg)}, d0")
                    emitLine("move.b  ${regAddr(dstReg)}, d1")
                    emitLine(loadOp, "extend src to 32-bit")
                    emitLine("move.l  d0, d2")
                    emitLine("move.b  ${regAddr(dstReg)}, d0")
                    emitLine(loadOp, "extend dst to 32-bit")
                    emitLine("$op.w  d2, d0")
                    emitLine("move.b  d0, ${regAddr(dstReg)}")
                }
                imm != null -> {
                    emitLine("move.b  ${regAddr(dstReg)}, d0")
                    emitLine(loadOp, "extend to 32-bit")
                    emitLine("$op.w  #${imm.and(0xffff)}, d0")
                    emitLine("move.b  d0, ${regAddr(dstReg)}")
                }
                target != null -> {
                    emitLine("move.b  ${regAddr(dstReg)}, d0")
                    emitLine(loadOp, "extend to 32-bit")
                    emitLine("move.b  $target, d1")
                    emitLine("move.l  d1, d2")
                    emitLine("move.b  ${regAddr(dstReg)}, d0")
                    emitLine(loadOp, "extend to 32-bit")
                    emitLine("$op.w  d2, d0")
                    emitLine("move.b  d0, ${regAddr(dstReg)}")
                }
            }
        }

        IRDataType.WORD -> {
            // mulu.w/muls.w is native (16x16→32, lower 16 are result)
            // destination must be a data register, not memory
            when {
                srcReg != null -> {
                    emitLine("move.w  ${regAddr(srcReg)}, d0")
                    emitLine("move.w  ${regAddr(dstReg)}, d1")
                    emitLine("$op.w  d1, d0")
                    emitLine("move.w  d0, ${regAddr(dstReg)}")
                }
                imm != null -> {
                    emitLine("move.w  ${regAddr(dstReg)}, d0")
                    emitLine("$op.w  #${imm.and(0xffff)}, d0")
                    emitLine("move.w  d0, ${regAddr(dstReg)}")
                }
                target != null -> {
                    emitLine("move.w  $target, d0")
                    emitLine("move.w  ${regAddr(dstReg)}, d1")
                    emitLine("$op.w  d1, d0")
                    emitLine("move.w  d0, ${regAddr(dstReg)}")
                }
            }
        }

        IRDataType.LONG -> {
            // 68020+ mulu.l/muls.l (32x32→64, lower 32 are result)
            // destination must be a data register, not memory
            when {
                srcReg != null -> {
                    emitLine("move.l  ${regAddr(srcReg)}, d0")
                    emitLine("move.l  ${regAddr(dstReg)}, d1")
                    emitLine("$op.l  d1, d0")
                    emitLine("move.l  d0, ${regAddr(dstReg)}")
                }
                imm != null -> {
                    emitLine("move.l  ${regAddr(dstReg)}, d0")
                    emitLine("$op.l  #${imm}, d0")
                    emitLine("move.l  d0, ${regAddr(dstReg)}")
                }
                target != null -> {
                    emitLine("move.l  $target, d0")
                    emitLine("move.l  ${regAddr(dstReg)}, d1")
                    emitLine("$op.l  d1, d0")
                    emitLine("move.l  d0, ${regAddr(dstReg)}")
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
            val extend = if (unsigned) $$"and.l  #$ff, d0" else "extb.l  d0"
            when {
                srcReg != null -> {
                    emitLine("move.b  ${regAddr(dstReg)}, d0")
                    emitLine(extend, "extend dividend to 32-bit")
                    emitLine("move.b  ${regAddr(srcReg)}, d1")
                    if (unsigned) emitLine($$"and.l  #$ff, d1") else emitLine("extb.l  d1", "sign extend divisor")
                    emitLine("$op.w  d1, d0")
                    emitLine("move.b  d0, ${regAddr(dstReg)}", "quotient in low byte")
                }
                imm != null -> {
                    emitLine("move.b  ${regAddr(dstReg)}, d0")
                    emitLine(extend, "extend dividend")
                    emitLine("$op.w  #${imm.and(0xffff)}, d0")
                    emitLine("move.b  d0, ${regAddr(dstReg)}", "quotient")
                }
                target != null -> {
                    emitLine("move.b  ${regAddr(dstReg)}, d0")
                    emitLine(extend, "extend dividend")
                    emitLine("move.b  $target, d1")
                    if (unsigned) emitLine($$"and.l  #$ff, d1") else emitLine("extb.l  d1", "sign extend")
                    emitLine("$op.w  d1, d0")
                    emitLine("move.b  d0, ${regAddr(dstReg)}", "quotient")
                }
            }
        }

        IRDataType.WORD -> {
            when {
                srcReg != null -> {
                    emitLine("moveq  #0, d0", "clear upper word for divu.w")
                    emitLine("move.w  ${regAddr(dstReg)}, d0")
                    emitLine("$op.w  ${regAddr(srcReg)}, d0")
                    emitLine("move.w  d0, ${regAddr(dstReg)}", "quotient in low word")
                }
                imm != null -> {
                    emitLine("moveq  #0, d0", "clear upper word for divu.w")
                    emitLine("move.w  ${regAddr(dstReg)}, d0")
                    emitLine("$op.w  #${imm.and(0xffff)}, d0")
                    emitLine("move.w  d0, ${regAddr(dstReg)}", "quotient")
                }
                target != null -> {
                    emitLine("moveq  #0, d0", "clear upper word for divu.w")
                    emitLine("move.w  ${regAddr(dstReg)}, d0")
                    emitLine("$op.w  $target, d0")
                    emitLine("move.w  d0, ${regAddr(dstReg)}", "quotient")
                }
            }
        }

        IRDataType.LONG -> {
            // 68020+ divu.l/divs.l (32/32→32)
            when {
                srcReg != null -> {
                    emitLine("move.l  ${regAddr(dstReg)}, d0")
                    emitLine("$op.l  ${regAddr(srcReg)}, d0")
                    emitLine("move.l  d0, ${regAddr(dstReg)}", "quotient")
                }
                imm != null -> {
                    emitLine("move.l  ${regAddr(dstReg)}, d0")
                    emitLine("$op.l  #${imm}, d0")
                    emitLine("move.l  d0, ${regAddr(dstReg)}", "quotient")
                }
                target != null -> {
                    emitLine("move.l  ${regAddr(dstReg)}, d0")
                    emitLine("$op.l  $target, d0")
                    emitLine("move.l  d0, ${regAddr(dstReg)}", "quotient")
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
            val extend = if (unsigned) $$"and.l  #$ff, d0" else "extb.l  d0"
            when {
                srcReg != null -> {
                    emitLine("move.b  ${regAddr(dstReg)}, d0")
                    emitLine(extend, "extend dividend")
                    emitLine("move.b  ${regAddr(srcReg)}, d1")
                    if (unsigned) emitLine($$"and.l  #$ff, d1") else emitLine("extb.l  d1")
                    emitLine("$opDiv.w  d1, d0")
                    emitLine("lsr.l  #8, d0", "shift remainder to low byte")  // remainder in upper 16 bits after swap
                }
                imm != null -> {
                    emitLine("move.b  ${regAddr(dstReg)}, d0")
                    emitLine(extend, "extend dividend")
                    emitLine("$opDiv.w  #${imm.and(0xffff)}, d0")
                }
            }
            // After divu.w: remainder in upper 16 bits of d0 (bits 16-31 if 32-bit reg)
            // Actually: divu.w divides 32-bit D0 by 16-bit divisor.
            // After: quotient in lower 16 bits, remainder in upper 16 bits.
            // So we need to shift right by 16 to get remainder in low word, then take low byte.
            emitLine("swap  d0", "remainder to low word")
            emitLine("move.b  d0, ${regAddr(dstReg)}", "remainder")
        }

        IRDataType.WORD -> {
            // divu.w: remainder in upper 16 bits, swap to get it
            // note: clear d0 first because move.w preserves upper word on 68030
            when {
                srcReg != null -> {
                    emitLine("moveq  #0, d0", "clear upper word for divu.w")
                    emitLine("move.w  ${regAddr(dstReg)}, d0")
                    emitLine("$opDiv.w  ${regAddr(srcReg)}, d0")
                }
                imm != null -> {
                    emitLine("moveq  #0, d0", "clear upper word for divu.w")
                    emitLine("move.w  ${regAddr(dstReg)}, d0")
                    emitLine("$opDiv.w  #${imm.and(0xffff)}, d0")
                }
            }
            emitLine("swap  d0", "remainder to low word")
            emitLine("move.w  d0, ${regAddr(dstReg)}", "remainder")
        }

        IRDataType.LONG -> {
            // 68020+ divul.l <ea>, Dr, Dq: Dr=remainder, Dq=quotient
            val opLong = if (unsigned) "divul.l" else "divsl.l"
            when {
                srcReg != null -> {
                    emitLine("move.l  ${regAddr(dstReg)}, d0", "dividend")
                    emitLine("$opLong  ${regAddr(srcReg)}, d1, d0")
                    emitLine("move.l  d1, ${regAddr(dstReg)}", "remainder")
                }
                imm != null -> {
                    emitLine("move.l  ${regAddr(dstReg)}, d0", "dividend")
                    emitLine("move.l  #${imm}, d2", "divisor")
                    emitLine("$opLong  d2, d1, d0")
                    emitLine("move.l  d1, ${regAddr(dstReg)}", "remainder")
                }
            }
        }

        else -> TODO("MOD for ${type.name}")
    }
}

private fun AsmGen.emitDivModOp(dstReg: Int, srcReg: Int?, type: IRDataType, unsigned: Boolean, imm: Int?) {
    val op = if (unsigned) "divul.l" else "divsl.l"
    when (type) {
        IRDataType.BYTE -> {
            // divul: Dq=dividend→quotient, Dr=remainder. Pack remainder lo, quotient hi.
            val extend = if (unsigned) $$"and.l  #$ff, d0" else "extb.l  d0"
            when {
                srcReg != null -> {
                    emitLine("move.b  ${regAddr(dstReg)}, d0")
                    emitLine(extend, "extend dividend")
                    emitLine("move.b  ${regAddr(srcReg)}, d2")
                    if (unsigned) emitLine($$"and.l  #$ff, d2") else emitLine("extb.l  d2")
                }
                imm != null -> {
                    emitLine("move.b  ${regAddr(dstReg)}, d0")
                    emitLine(extend, "extend dividend")
                    emitLine("moveq  #${imm.and(0xff)}, d2")
                    if (!unsigned) emitLine("extb.l  d2", "sign extend")
                }
            }
            emitLine("$op  d2, d1, d0")
            emitLine("move.b  d1, ${regAddrByte(dstReg, 0)}", "remainder lo")
            emitLine("move.b  d0, ${regAddrByte(dstReg, 1)}", "quotient hi")
        }

        IRDataType.WORD -> {
            // divu.w packs quotient in low word, remainder in high word natively
            // note: clear d0 first because move.w preserves upper word on 68030
            val opDiv = if (unsigned) "divu.w" else "divs.w"
            when {
                srcReg != null -> {
                    emitLine("moveq  #0, d0", "clear upper word for divu.w")
                    emitLine("move.w  ${regAddr(dstReg)}, d0")
                    emitLine("$opDiv  ${regAddr(srcReg)}, d0")
                }
                imm != null -> {
                    emitLine("moveq  #0, d0", "clear upper word for divu.w")
                    emitLine("move.w  ${regAddr(dstReg)}, d0")
                    emitLine("$opDiv  #${imm.and(0xffff)}, d0")
                }
            }
            // After: low word = quotient, high word = remainder
            // Store: low word = quotient, next word = remainder? Or vice versa?
            // Convention: remainder in low part, quotient in high part
            emitLine("move.w  d0, ${regAddr(dstReg)}", "quotient in low word")
            emitLine("swap  d0", "remainder to low word")
            emitLine("move.w  d0, ${regAddrByte(dstReg, 2)}", "remainder in next word")
        }

        IRDataType.LONG -> {
            when {
                srcReg != null -> {
                    emitLine("move.l  ${regAddr(dstReg)}, d0", "dividend")
                    emitLine("$op  ${regAddr(srcReg)}, d1, d0")
                    emitLine("move.l  d1, ${regAddr(dstReg)}", "remainder")
                    emitLine("move.l  d0, ${regAddrByte(dstReg, 4)}", "quotient in next 4 bytes")
                }
                imm != null -> {
                    emitLine("move.l  ${regAddr(dstReg)}, d0", "dividend")
                    emitLine("move.l  #${imm}, d2", "divisor")
                    emitLine("$op  d2, d1, d0")
                    emitLine("move.l  d1, ${regAddr(dstReg)}", "remainder")
                    emitLine("move.l  d0, ${regAddrByte(dstReg, 4)}", "quotient in next 4 bytes")
                }
            }
        }

        else -> TODO("DIVMOD for ${type.name}")
    }
}

// === Float arithmetic via 68881 FPU ===

private fun AsmGen.translateFloatArithmetic(insn: IRInstruction) {
    val immFp = insn.immediateFp
    val fpReg1 = insn.fpReg1
    val fpReg2 = insn.fpReg2
    val addr = insn.address
    val label = insn.labelSymbol
    val offset = insn.labelSymbolOffset

    when (insn.opcode) {
        Opcode.INCM -> emitFloatMemUnary("fadd.s", addr, label, offset, immediateStr = "#1.0")
        Opcode.DECM -> emitFloatMemUnary("fsub.s", addr, label, offset, immediateStr = "#1.0")
        Opcode.NEGM -> emitFloatMemUnary("fneg", addr, label, offset)

        Opcode.INC -> emitLine("fadd.s  #1.0, ${fpuRegName(fpReg1!!)}")
        Opcode.DEC -> emitLine("fsub.s  #1.0, ${fpuRegName(fpReg1!!)}")

        Opcode.NEG -> emitLine("fneg  ${fpuRegName(fpReg1!!)}, ${fpuRegName(fpReg1)}")

        Opcode.ADDR -> {
            val src = fpReg2 ?: error("ADDR.f needs fpReg2")
            emitLine("fadd  ${fpuRegName(src)}, ${fpuRegName(fpReg1!!)}")
        }

        Opcode.ADD -> when {
            immFp != null -> emitFaddConstant(fpReg1!!, immFp)
            else -> TODO("FLOAT ADD without immediate")
        }

        Opcode.SUBR -> {
            val src = fpReg2 ?: error("SUBR.f needs fpReg2")
            emitLine("fsub  ${fpuRegName(src)}, ${fpuRegName(fpReg1!!)}")
        }

        Opcode.SUB -> when {
            immFp != null -> emitFsubConstant(fpReg1!!, immFp)
            else -> TODO("FLOAT SUB without immediate")
        }

        Opcode.MULR -> {
            val src = fpReg2 ?: error("MULR.f needs fpReg2")
            emitLine("fmul  ${fpuRegName(src)}, ${fpuRegName(fpReg1!!)}")
        }

        Opcode.MUL -> when {
            immFp != null -> emitFmulConstant(fpReg1!!, immFp)
            else -> TODO("FLOAT MUL without immediate")
        }

        Opcode.DIVR -> {
            val src = fpReg2 ?: error("DIVR.f needs fpReg2")
            emitLine("fdiv  ${fpuRegName(src)}, ${fpuRegName(fpReg1!!)}")
        }

        Opcode.DIV -> when {
            immFp != null -> emitFdivConstant(fpReg1!!, immFp)
            else -> TODO("FLOAT DIV without immediate")
        }

        // signed float multiply/divide are the same as unsigned for floats
        Opcode.MULSR -> {
            val src = fpReg2 ?: error("MULSR.f needs fpReg2")
            emitLine("fmul  ${fpuRegName(src)}, ${fpuRegName(fpReg1!!)}")
        }

        Opcode.MULS -> when {
            immFp != null -> emitFmulConstant(fpReg1!!, immFp)
            else -> TODO("FLOAT MULS without immediate")
        }

        Opcode.DIVSR -> {
            val src = fpReg2 ?: error("DIVSR.f needs fpReg2")
            emitLine("fdiv  ${fpuRegName(src)}, ${fpuRegName(fpReg1!!)}")
        }

        Opcode.DIVS -> when {
            immFp != null -> emitFdivConstant(fpReg1!!, immFp)
            else -> TODO("FLOAT DIVS without immediate")
        }

        Opcode.SQRT -> {
            val src = fpReg2 ?: error("SQRT.f needs fpReg2")
            emitLine("fsqrt  ${fpuRegName(src)}, ${fpuRegName(fpReg1!!)}")
        }

        Opcode.SQUARE -> {
            val src = fpReg2 ?: error("SQUARE.f needs fpReg2")
            emitLine("fmul  ${fpuRegName(src)}, ${fpuRegName(fpReg1!!)}")
        }

        Opcode.ADDM -> emitFloatMemBinary("fadd", fpReg1!!, addr, label, offset)
        Opcode.SUBM -> emitFloatMemBinary("fsub", fpReg1!!, addr, label, offset)
        Opcode.MULM, Opcode.MULSM -> emitFloatMemBinary("fmul", fpReg1!!, addr, label, offset)
        Opcode.DIVM, Opcode.DIVSM -> emitFloatMemBinary("fdiv", fpReg1!!, addr, label, offset)

        else -> TODO("FLOAT arithmetic: ${insn.opcode}")
    }
}

private fun AsmGen.emitFaddConstant(fpReg: RegisterNum, value: Double) {
    when {
        value == 1.0 -> emitLine("fadd.s  #1.0, ${fpuRegName(fpReg)}")
        else -> {
            val label = makeFloatConstLabel(value)
            emitLine("lea  $label, a0")
            emitLine("fadd.s  (a0), ${fpuRegName(fpReg)}")
        }
    }
}

private fun AsmGen.emitFsubConstant(fpReg: RegisterNum, value: Double) {
    val label = makeFloatConstLabel(value)
    emitLine("lea  $label, a0")
    emitLine("fsub.s  (a0), ${fpuRegName(fpReg)}")
}

private fun AsmGen.emitFmulConstant(fpReg: RegisterNum, value: Double) {
    val label = makeFloatConstLabel(value)
    emitLine("lea  $label, a0")
    emitLine("fmul.s  (a0), ${fpuRegName(fpReg)}")
}

private fun AsmGen.emitFdivConstant(fpReg: RegisterNum, value: Double) {
    val label = makeFloatConstLabel(value)
    emitLine("lea  $label, a0")
    emitLine("fdiv.s  (a0), ${fpuRegName(fpReg)}")
}

private fun AsmGen.emitFloatMemUnary(op: String, addr: MemoryAddress?, label: String?, offset: Int?, immediateStr: String? = null) {
    val target = resolveAddress(addr, label, offset)
    emitLine("fmove.s  $target, fp0")
    if (immediateStr != null)
        emitLine("$op  $immediateStr, fp0")
    else
        emitLine("$op  fp0, fp0")
    emitLine("fmove.s  fp0, $target")
}

private fun AsmGen.emitFloatMemBinary(op: String, fpReg1: RegisterNum, addr: MemoryAddress?, label: String?, offset: Int?) {
    val target = resolveAddress(addr, label, offset)
    val scratch = if (fpReg1.value == 0) "fp7" else "fp0"
    emitLine("fmove.s  $target, $scratch")
    emitLine("$op  ${fpuRegName(fpReg1)}, $scratch")
    emitLine("fmove.s  $scratch, $target")
}

private fun immVal(value: Int, type: IRDataType): Int = when(type) {
    IRDataType.BYTE -> value and 0xff
    IRDataType.WORD -> value and 0xffff
    IRDataType.LONG, IRDataType.POINTER -> value
    else -> value and 0xffff
}
