package prog8.codegen.m68k

import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction
import prog8.intermediate.ImmediateOperand
import prog8.intermediate.Opcode
import prog8.intermediate.floatNumber
import prog8.intermediate.intNumber
import prog8.intermediate.requireFloatDest
import prog8.intermediate.requireFloatSourceA
import prog8.intermediate.requireHardwareSlot
import prog8.intermediate.requireImmediate
import prog8.intermediate.requireImmediateFloat
import prog8.intermediate.requireImmediateInt
import prog8.intermediate.requireIntDest
import prog8.intermediate.requireIntSourceA
import prog8.intermediate.requireMemory

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
                        emitLine("clr$s  ${regAddr(dst)}")
                    else
                        emitLine("move$s  #${value.value}, ${regAddr(dst)}")
                    invalidateD0CacheForSlot(dst)
                }
                is ImmediateOperand.SymbolAddress -> {
                    val resolved = resolveSymbolRef(value.symbol)
                    val symOff = if (value.offset != 0) "$resolved+${value.offset}" else resolved
                    emitLine("move.l  #$symOff, ${regAddr(dst)}")
                    invalidateD0CacheForSlot(dst)
                }
                is ImmediateOperand.FloatValue -> error("LOAD: float immediate for an integer register")
            }
        }

        Opcode.LOADM -> {
            val dst = insn.requireIntDest().intNumber
            val target = resolveMemory(insn.requireMemory())
            emitLine("move$s  $target, ${regAddr(dst)}")
            invalidateD0CacheForSlot(dst)
        }

        Opcode.LOADR -> {
            val dst = insn.requireIntDest().intNumber
            val src = insn.requireIntSourceA().intNumber
            emitLine("move$s  ${regAddr(src)}, ${regAddr(dst)}")
            invalidateD0CacheForSlot(dst)
        }

        Opcode.LOADX, Opcode.LOADI -> {
            val dst = insn.requireIntDest().intNumber
            val source = resolveMemory(insn.requireMemory())
            emitLoadD0FromAddress(source, type)
            emitStoreD0(dst, type)
        }

        Opcode.LOADHR -> {
            val dst = insn.requireIntDest().intNumber
            val hwReg = m68kSlotRegister(insn.requireHardwareSlot().slot)
            emitLine("move$s  $hwReg, ${regAddr(dst)}")
            invalidateD0CacheForSlot(dst)
        }

        Opcode.STOREM -> {
            val src = insn.requireIntSourceA().intNumber
            val target = resolveMemory(insn.requireMemory())
            emitLine("move$s  ${regAddr(src)}, $target")
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
            emitLine("move$s  ${regAddr(value)}, d1")
            emitLine("move$s  d1, $target")
        }

        Opcode.STOREZM, Opcode.STOREZI, Opcode.STOREZX -> {
            val target = resolveMemory(insn.requireMemory())
            emitLine("clr$s  $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.STOREHR -> {
            val src = insn.requireIntSourceA().intNumber
            val hwReg = m68kSlotRegister(insn.requireHardwareSlot().slot)
            emitLine("move$s  ${regAddr(src)}, $hwReg")
        }

        Opcode.STOREI -> {
            val value = insn.requireIntSourceA().intNumber
            val target = resolveMemory(insn.requireMemory())
            emitLine("move$s  ${regAddr(value)}, $target")
        }

        Opcode.LOADP_INC -> {
            val dst = insn.requireIntDest().intNumber
            // r1 = *a; a += sizeof(type)   where a is a pointer variable (LONG) in memory
            val target = resolveMemory(insn.requireMemory())
            emitLine("move.l  $target, a0")
            emitLoadD0FromAddress("(a0)+", type)
            emitLine("move.l  a0, $target")
            invalidateD0CacheForAddress(target)
            emitStoreD0(dst, type)
        }

        Opcode.STOREP_INC -> {
            val value = insn.requireIntSourceA().intNumber
            // *a = r1; a += sizeof(type)
            val target = resolveMemory(insn.requireMemory())
            emitLine("move.l  $target, a0")
            emitLine("move$s  ${regAddr(value)}, (a0)+")
            emitLine("move.l  a0, $target")
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
            emitLine($$"fmovecr  #$0f, fp0")
            emitLine("fmove.s  fp0, $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.STOREIM -> {
            val target = resolveMemory(insn.requireMemory(), forFloat = true)
            emitFloadConstantTo("fp0", insn.requireImmediateFloat())
            emitLine("fmove.s  fp0, $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.LOAD -> {
            val dst = insn.requireFloatDest().floatNumber
            when (val value = insn.requireImmediate()) {
                is ImmediateOperand.FloatValue -> {
                    if (suppressRegfileStore)
                        return
                    emitFloadConstantToAcc(value.value)
                    emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
                }
                is ImmediateOperand.SymbolAddress -> {
                    val resolved = resolveSymbolRef(value.symbol)
                    val symOff = if (value.offset != 0) "$resolved+${value.offset}" else resolved
                    emitLine("lea  $symOff, a0")
                    emitLine("fmove.s  (a0), $FP_ACC")
                    emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
                }
                is ImmediateOperand.Integer -> error("LOAD.f: integer immediate for a float register")
            }
        }

        Opcode.LOADM, Opcode.LOADX, Opcode.LOADI -> {
            val dst = insn.requireFloatDest().floatNumber
            val source = resolveMemory(insn.requireMemory(), forFloat = true)
            emitLine("fmove.s  $source, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }

        Opcode.LOADR -> {
            val dst = insn.requireFloatDest().floatNumber
            val src = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }

        Opcode.LOADHR -> {
            val dst = insn.requireFloatDest().floatNumber
            val hwReg = m68kSlotRegister(insn.requireHardwareSlot().slot)
            emitLine("fmove  $hwReg, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }

        Opcode.STOREM, Opcode.STOREX, Opcode.STOREI -> {
            val src = insn.requireFloatSourceA().floatNumber
            val target = resolveMemory(insn.requireMemory(), forFloat = true)
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.STOREHR -> {
            val src = insn.requireFloatSourceA().floatNumber
            val hwReg = m68kSlotRegister(insn.requireHardwareSlot().slot)
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
            emitLine("fmove  $FP_ACC, $hwReg")
        }

        // The 'FAC' hardware float registers only exist on the 6502 targets. On m68k they are
        // modelled with the two FPU scratch registers; loading them yields the 0.0 / 1.0 constants
        // that the 6502 targets keep in FAC1 / FAC2.
        Opcode.LOADHFACZERO -> {
            val dst = insn.requireFloatDest().floatNumber
            emitLine($$"fmovecr  #$0f, $$FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }
        Opcode.LOADHFACONE -> {
            val dst = insn.requireFloatDest().floatNumber
            emitLine("fmovecr  #$32, $FP_ACC")
            emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(dst)}")
        }
        Opcode.STOREHFACZERO -> {
            val src = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
        }
        Opcode.STOREHFACONE -> {
            val src = insn.requireFloatSourceA().floatNumber
            emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_SRC")
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
