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
                    if(value.value == 0)
                        if (regAllocation[VirtualRegister.int(dst)]?.startsWith("a") == true)
                            emitLine("suba.l  ${operand(dst)}, ${operand(dst)}")
                        else
                            emitLine("clr$s  ${operand(dst)}")
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
            val target = resolveMemory(insn.requireMemory())
            if(value == 0)
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
            val target = resolveMemory(insn.requireMemory())
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
