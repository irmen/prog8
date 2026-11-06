package prog8.codegen.m68k

import prog8.intermediate.*

internal fun AsmGen.translateLoadStore(insn: IRInstruction, suppressRegfileStore: Boolean = false) {
    val type = insn.type ?: IRDataType.BYTE

    if (type == IRDataType.FLOAT) {
        translateFloatLoadStore(insn, suppressRegfileStore)
        return
    }

    val s = dtSuffix(type)

    when (insn.opcode) {
        Opcode.LOAD -> {
            val dst = insn.requireIntDest().intNumber
            when (val value = insn.requireImmediate()) {
                is ImmediateOperand.Integer -> {
                    if (suppressRegfileStore)
                        return
                    if(value.value == 0) {
                        // moveq is faster than clr.l on a data register (4 vs 6 cycles on
                        // the 68000) with identical flags; the full-long clear is a safe
                        // superset for byte/word vregs, whose upper bits are don't-care.
                        val phys = regAllocation[VirtualRegister.int(dst)]
                        if (phys?.startsWith("a") == true)
                            emitLine("suba.l  ${operand(dst)}, ${operand(dst)}")
                        else if (phys?.startsWith("d") == true)
                            emitLine("moveq  #0, $phys")
                        else
                            emitLine("clr$s  ${operand(dst)}")
                    }
                    else
                        emitMoveToIntRegister("#${value.value}", dst, type)
                    invalidateD0CacheForSlot(dst)
                }
                is ImmediateOperand.SymbolAddress -> {
                    val resolved = resolveSymbolRef(value.symbol)
                    val symOff = if (value.offset != 0) "$resolved+${value.offset}" else resolved
                    emitMoveToIntRegister("#$symOff", dst, type)
                    invalidateD0CacheForSlot(dst)
                }
                is ImmediateOperand.FloatValue -> error("LOAD: float immediate for an integer register")
            }
        }

        Opcode.LOADM -> {
            val dst = insn.requireIntDest().intNumber
            val target = resolveMemory(insn.requireMemory())
            emitLine("move$s  $target, ${operand(dst)}")
            invalidateD0CacheForSlot(dst)
        }

        Opcode.LOADR -> {
            val dst = insn.requireIntDest().intNumber
            val src = insn.requireIntSourceA().intNumber
            emitMoveToIntRegister(operand(src), dst, type)
            invalidateD0CacheForSlot(dst)
        }

        Opcode.LOADX, Opcode.LOADI -> {
            val dst = insn.requireIntDest().intNumber
            val source = resolveMemory(insn.requireMemory())
            emitMoveToIntRegister(source, dst, type)
            invalidateD0CacheForSlot(dst)
        }

        Opcode.LOADMZ -> {
            val dst = insn.requireIntDest().intNumber
            val target = resolveMemory(insn.requireMemory())
            when (type) {
                IRDataType.BYTE -> {
                    emitLoadD0ZeroExtendedFromAddress(target, IRDataType.BYTE)
                    emitStoreD0(dst, IRDataType.WORD)
                }
                IRDataType.WORD -> {
                    emitLoadD0ZeroExtendedFromAddress(target, IRDataType.WORD)
                    emitStoreD0(dst, IRDataType.LONG)
                }
                else -> error("LOADMZ of unsupported type $type")
            }
        }

        Opcode.LOADMS -> {
            val dst = insn.requireIntDest().intNumber
            val target = resolveMemory(insn.requireMemory())
            when (type) {
                IRDataType.BYTE -> {
                    emitLoadD0FromAddress(target, IRDataType.BYTE)
                    emitLine("ext.w   d0")
                    emitStoreD0(dst, IRDataType.WORD)
                }
                IRDataType.WORD -> {
                    emitLoadD0FromAddress(target, IRDataType.WORD)
                    emitLine("ext.l   d0")
                    emitStoreD0(dst, IRDataType.LONG)
                }
                else -> error("LOADMS of unsupported type $type")
            }
        }

        Opcode.LOADMZL -> {
            val dst = insn.requireIntDest().intNumber
            val target = resolveMemory(insn.requireMemory())
            emitLoadD0ZeroExtendedFromAddress(target, IRDataType.BYTE)
            emitStoreD0(dst, IRDataType.LONG)
        }

        Opcode.LOADMSL -> {
            val dst = insn.requireIntDest().intNumber
            val target = resolveMemory(insn.requireMemory())
            emitLoadD0FromAddress(target, IRDataType.BYTE)
            emitSignExtendByteToLong(SCRATCH_D0)
            emitStoreD0(dst, IRDataType.LONG)
        }

        Opcode.LOADIZ, Opcode.LOADIS, Opcode.LOADIZL, Opcode.LOADISL -> {
            val dst = insn.requireIntDest().intNumber
            val source = resolveMemory(insn.requireMemory())
            val isSign = insn.opcode in setOf(Opcode.LOADIS, Opcode.LOADISL)
            val isLong = insn.opcode in setOf(Opcode.LOADIZL, Opcode.LOADISL)
            val srcType = if (isLong) IRDataType.BYTE else type
            val destType = if (isLong) IRDataType.LONG else if (srcType == IRDataType.BYTE) IRDataType.WORD else IRDataType.LONG
            if (isSign) {
                emitLoadD0FromAddress(source, srcType)
                if (srcType == IRDataType.BYTE && destType == IRDataType.LONG)
                    emitSignExtendByteToLong(SCRATCH_D0)
                else {
                    if (srcType == IRDataType.BYTE) emitLine("ext.w   d0")
                    if (destType == IRDataType.LONG) emitLine("ext.l   d0")
                }
            } else {
                emitLoadD0ZeroExtendedFromAddress(source, srcType)
            }
            emitStoreD0(dst, destType)
        }

        Opcode.LOADHR -> {
            val dst = insn.requireIntDest().intNumber
            val hwReg = m68kSlotRegister(insn.requireHardwareSlot().slot)
            // the vreg already lives in its slot register: no move needed.
            if (operand(dst) != hwReg)
                emitMoveToIntRegister(hwReg, dst, type)
            invalidateD0CacheForSlot(dst)
        }

        Opcode.STOREM -> {
            val src = insn.requireIntSourceA().intNumber
            val target = resolveMemory(insn.requireMemory())
            emitLine("move$s  ${operand(src)}, $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.STOREIM -> {
            val value = insn.requireImmediateInt()
            val memory = insn.requireMemory()
            val target = resolveMemory(memory)
            // never clr an I/O address: the dummy read can hit hardware with read side effects.
            if(value == 0 && !memoryIsIOAddress(memory))
                emitLine("clr$s  $target")
            else
                emitLine("move$s  #$value, $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.STOREX -> {
            val value = insn.requireIntSourceA().intNumber
            val target = resolveMemory(insn.requireMemory())
            // the indexed effective address uses a0/d0 as scratch, so stage the value in d1
            emitLine("move$s  ${operand(value)}, $SCRATCH_D1")
            emitLine("move$s  $SCRATCH_D1, $target")
        }

        Opcode.STOREZM, Opcode.STOREZI, Opcode.STOREZX -> {
            val memory = insn.requireMemory()
            val target = resolveMemory(memory)
            // never clr an I/O address: the dummy read can hit hardware with read side effects.
            if (memoryIsIOAddress(memory))
                emitLine("move$s  #0, $target")
            else
                emitLine("clr$s  $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.STOREHR -> {
            val src = insn.requireIntSourceA().intNumber
            val hwReg = m68kSlotRegister(insn.requireHardwareSlot().slot)
            // the vreg already lives in its slot register: no move needed.
            if (operand(src) != hwReg)
                emitLine("move$s  ${operand(src)}, $hwReg")
        }

        Opcode.STOREI -> {
            val value = insn.requireIntSourceA().intNumber
            val target = resolveMemory(insn.requireMemory())
            emitLine("move$s  ${operand(value)}, $target")
        }

        Opcode.LOADP_INC -> {
            val dst = insn.requireIntDest().intNumber
            // r1 = *a; a += sizeof(type)   where a is a pointer variable (LONG) in memory
            val target = resolveMemory(insn.requireMemory())
            emitLine("move.l  $target, $SCRATCH_A0")
            emitMoveToIntRegister("($SCRATCH_A0)+", dst, type)
            emitLine("move.l  $SCRATCH_A0, $target")
            invalidateD0CacheForSlot(dst)
            invalidateD0CacheForAddress(target)
        }

        Opcode.STOREP_INC -> {
            val value = insn.requireIntSourceA().intNumber
            // *a = r1; a += sizeof(type)
            val target = resolveMemory(insn.requireMemory())
            emitLine("move.l  $target, $SCRATCH_A0")
            emitLine("move$s  ${operand(value)}, ($SCRATCH_A0)+")
            emitLine("move.l  $SCRATCH_A0, $target")
            invalidateD0CacheForAddress(target)
        }

        else -> error("Unknown load/store opcode: ${insn.opcode}")
    }
}

// === Float load/store via FPU (68881/68882) ===

private fun AsmGen.translateFloatLoadStore(insn: IRInstruction, suppressRegfileStore: Boolean = false) {
    when (insn.opcode) {
        Opcode.STOREZM, Opcode.STOREZI, Opcode.STOREZX -> {
            val target = resolveMemory(insn.requireMemory(), forFloat = true)
            emitLine("fmovecr  #\$0f, $SCRATCH_FP0")
            emitLine("fmove.s  $SCRATCH_FP0, $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.STOREIM -> {
            val target = resolveMemory(insn.requireMemory(), forFloat = true)
            emitFloadConstantTo(SCRATCH_FP0, insn.requireImmediateFloat())
            emitLine("fmove.s  $SCRATCH_FP0, $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.LOAD -> {
            val dst = insn.requireFloatDest().floatNumber
            when (val value = insn.requireImmediate()) {
                is ImmediateOperand.FloatValue -> {
                    if (suppressRegfileStore)
                        return
                    emitFloadConstantToAcc(value.value)
                    emitFmoveFloat(FP_ACC, floatOperand(dst))
                }
                is ImmediateOperand.SymbolAddress -> {
                    val resolved = resolveSymbolRef(value.symbol)
                    val symOff = if (value.offset != 0) "$resolved+${value.offset}" else resolved
                    emitLine("lea  $symOff, $SCRATCH_A0")
                    emitFmoveFloat("($SCRATCH_A0)", FP_ACC)
                    emitFmoveFloat(FP_ACC, floatOperand(dst))
                }
                is ImmediateOperand.Integer -> error("LOAD.f: integer immediate for a float register")
            }
        }

        Opcode.LOADM, Opcode.LOADX, Opcode.LOADI -> {
            val dst = insn.requireFloatDest().floatNumber
            val source = resolveMemory(insn.requireMemory(), forFloat = true)
            directFloatReg(dst)?.let { d ->
                emitLine("fmove.s  $source, $d")
                return
            }
            emitLine("fmove.s  $source, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatOperand(dst)}")
        }

        Opcode.LOADR -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            directFloatReg(dst)?.let { d ->
                emitFmoveFloat(floatOperand(src), d)
                return
            }
            emitFmoveFloat(floatOperand(src), FP_ACC)
            emitLine("fmove.s  $FP_ACC, ${floatOperand(dst)}")
        }

        Opcode.LOADHR -> {
            val dst = insn.requireFloatDest().floatNumber
            val hwReg = m68kSlotRegister(insn.requireHardwareSlot().slot)
            // the vreg already lives in its slot register: no move needed.
            if (floatOperand(dst) == hwReg)
                return
            directFloatReg(dst)?.let { d ->
                emitLine("fmove  $hwReg, $d")
                return
            }
            emitLine("fmove  $hwReg, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatOperand(dst)}")
        }

        Opcode.STOREM, Opcode.STOREX, Opcode.STOREI -> {
            val src = insn.requireFloatSourceA().floatNumber
            val target = resolveMemory(insn.requireMemory(), forFloat = true)
            directFloatReg(src)?.let { s ->
                // Source resident: fmove.s FPn,<ea> is legal, so no accumulator is needed.
                emitLine("fmove.s  $s, $target")
                invalidateD0CacheForAddress(target)
                return
            }
            emitLine("fmove.s  ${floatOperand(src)}, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.STOREHR -> {
            val src = insn.requireFloatSourceA().floatNumber
            val hwReg = m68kSlotRegister(insn.requireHardwareSlot().slot)
            // the vreg already lives in its slot register: no move needed.
            if (floatOperand(src) == hwReg)
                return
            emitLine("fmove.s  ${floatOperand(src)}, $FP_ACC")
            emitLine("fmove  $FP_ACC, $hwReg")
        }

        // 'FAC' registers only exist on the 6502 targets; here they are the 0.0 / 1.0 FPU constants.
        Opcode.LOADHFACZERO -> {
            val dst = insn.requireFloatDest().floatNumber
            emitLine($$"fmovecr  #$0f, $$FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(dst))
        }
        Opcode.LOADHFACONE -> {
            val dst = insn.requireFloatDest().floatNumber
            emitLine("fmovecr  #$32, $FP_ACC")
            emitFmoveFloat(FP_ACC, floatOperand(dst))
        }
        Opcode.STOREHFACZERO -> {
            val src = insn.requireFloatSourceA().floatNumber
            emitFmoveFloat(floatOperand(src), FP_ACC)
        }
        Opcode.STOREHFACONE -> {
            val src = insn.requireFloatSourceA().floatNumber
            emitFmoveFloat(floatOperand(src), FP_SRC)
        }

        else -> error("Unknown float load/store opcode: ${insn.opcode}")
    }
}


private var floatConstCounter = 0
internal fun AsmGen.makeFloatConstLabel(value: Double): String {
    floatConstCounter++
    val label = "p8c_fconst_$floatConstCounter"
    this.dataFloatConstants.add(Pair(label, value))
    return label
}
