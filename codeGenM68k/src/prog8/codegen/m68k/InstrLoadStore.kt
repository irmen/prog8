package prog8.codegen.m68k

import prog8.intermediate.*

internal fun AsmGen.translateLoadStore(insn: IRInstruction) {
    val type = insn.type ?: IRDataType.BYTE
    val r1 = insn.reg1
    val r2 = insn.reg2
    val imm = insn.immediate
    val addr = insn.address
    val label = insn.labelSymbol
    val offset = insn.labelSymbolOffset
    val target = resolveAddress(addr, label, offset)

    if (type == IRDataType.FLOAT) {
        translateFloatLoadStore(insn, target)
        return
    }

    val s = dtSuffix(type)

    when (insn.opcode) {
        Opcode.LOAD -> {
            val dst = r1 ?: error("LOAD needs reg1")
            val value = insn.immediate
            val sym = insn.labelSymbol
            when {
                value != null -> emitLine("move$s  #${value}, ${regAddr(dst)}")
                sym != null -> {
                    val resolved = resolveSymbolRef(sym)
                    val symOff = if (offset != null) "$resolved+$offset" else resolved
                    emitLine("lea  $symOff, a0")
                    storeA0ToPointer(dst)
                }
                else -> error("LOAD needs immediate or labelSymbol")
            }
        }

        Opcode.LOADM -> {
            val dst = r1 ?: error("LOADM needs reg1")
            emitLine("move${dtSuffix(type)}  $target, ${regAddr(dst)}")
        }

        Opcode.LOADR -> {
            val dst = r1 ?: error("LOADR needs reg1")
            val src = r2 ?: error("LOADR needs reg2")
            emitLine("move$s  ${regAddr(src)}, ${regAddr(dst)}")
        }

        Opcode.LOADX -> {
            val dst = r1 ?: error("LOADX needs reg1")
            val idx = r2 ?: error("LOADX needs reg2")
            loadIndexToD0(idx)
            emitLine("lea  $target, a0")
            val sx = dtSuffix(type)
            emitLine("move$sx  (a0,d0.w), d1")
            emitLine("move$sx  d1, ${regAddr(dst)}")
        }

        Opcode.LOADHR -> {
            val dst = r1 ?: error("LOADHR needs reg1")
            val slot = imm ?: error("LOADHR needs slot immediate")
            val hwReg = m68kSlotRegister(CallingConventionSlot(slot))
            emitLine("move$s  $hwReg, ${regAddr(dst)}")
        }

        Opcode.LOADI -> {
            val dst = r1 ?: error("LOADI needs reg1")
            val base = r2 ?: error("LOADI needs reg2")
            val off = imm ?: 0
            loadPointerToA0(base)
            if(off<-32768 || off>32767) {
                if (off != 0) emitLine("adda.l  #$off, a0")
                emitLine("move$s  (a0), d0")
            } else {
                if(off==0)
                    emitLine("move$s  (a0), d0")
                else
                    emitLine("move$s  ($off,a0), d0")
            }
            emitLine("move$s  d0, ${regAddr(dst)}")
        }

        Opcode.STOREM -> {
            val src = r1 ?: error("STOREM needs reg1")
            emitLine("move${dtSuffix(type)}  ${regAddr(src)}, $target")
        }

        Opcode.STOREX -> {
            val value = r1 ?: error("STOREX needs reg1")
            val idx = r2 ?: error("STOREX needs reg2")
            loadIndexToD0(idx)
            emitLine("lea  $target, a0")
            val sx = dtSuffix(type)
            emitLine("move$sx  ${regAddr(value)}, d1")
            emitLine("move$sx  d1, (a0,d0.w)")
        }

        Opcode.STOREZM -> {
            emitLine("clr${dtSuffix(type)}  $target")
        }

        Opcode.STOREZI -> {
            val base = r1 ?: error("STOREZI needs reg1")
            val off = imm ?: 0
            loadPointerToA0(base)
            if(off<-32768 || off>32767) {
                if (off != 0) emitLine("adda.l  #$off, a0")
                emitLine("clr$s  (a0)")
            } else {
                if(off==0)
                    emitLine("clr$s  (a0)")
                else
                    emitLine("clr$s  ($off,a0)")
            }
        }

        Opcode.STOREZX -> {
            val idx = r1 ?: error("STOREZX needs reg1")
            loadIndexToD0(idx)
            emitLine("lea  $target, a0")
            emitLine("clr$s  (a0,d0.w)")
        }

        Opcode.STOREHR -> {
            val src = r1 ?: error("STOREHR needs reg1")
            val slot = imm ?: error("STOREHR needs slot immediate")
            val hwReg = m68kSlotRegister(CallingConventionSlot(slot))
            emitLine("move$s  ${regAddr(src)}, $hwReg")
        }

        Opcode.STOREI -> {
            val value = r1 ?: error("STOREI needs reg1")
            val base = r2 ?: error("STOREI needs reg2")
            val off = imm ?: 0
            loadPointerToA0(base)
            if(off<-32768 || off>32767) {
                if (off != 0) emitLine("adda.l  #$off, a0")
                emitLine("move$s  ${regAddr(value)}, (a0)")
            } else {
                if(off==0)
                    emitLine("move$s  ${regAddr(value)}, (a0)")
                else
                    emitLine("move$s  ${regAddr(value)}, ($off,a0)")
            }
        }

        else -> error("Unknown load/store opcode: ${insn.opcode}")
    }
}

// === Float load/store via FPU (68881/68882) ===

private fun AsmGen.translateFloatLoadStore(insn: IRInstruction, target: String) {
    val fpReg1 = insn.fpReg1 ?: error("float op needs fpReg1")
    val fpReg2 = insn.fpReg2
    val r1 = insn.reg1
    val imm = insn.immediate
    val immFp = insn.immediateFp
    val label = insn.labelSymbol
    val offset = insn.labelSymbolOffset

    when (insn.opcode) {
        Opcode.LOAD -> when {
            immFp != null -> {
                emitFloadConstant(fpReg1, immFp)
            }
            label != null -> {
                val resolved = resolveSymbolRef(label)
                val symOff = if (offset != null) "$resolved+$offset" else resolved
                emitLine("lea  $symOff, a0")
                emitLine("fmove.s  (a0), ${fpuRegName(fpReg1)}")
            }
            else -> error("FLOAT LOAD needs immediateFp or labelSymbol")
        }

        Opcode.LOADM -> {
            emitLine("fmove.s  $target, ${fpuRegName(fpReg1)}")
        }

        Opcode.LOADR -> {
            val src = fpReg2 ?: error("LOADR.f needs fpReg2")
            emitLine("fmove  ${fpuRegName(src)}, ${fpuRegName(fpReg1)}")
        }

        Opcode.LOADX -> {
            val idx = r1 ?: error("LOADX.f needs reg1 (index)")
            loadIndexToD0(idx)
            emitLine("lea  $target, a0")
            emitLine("fmove.s  (0, a0, d0.l), ${fpuRegName(fpReg1)}", "index pre-scaled")
        }

        Opcode.LOADHR -> {
            val slot = imm ?: error("LOADHR.f needs slot immediate")
            val hwReg = m68kSlotRegister(CallingConventionSlot(slot))
            emitLine("fmove  $hwReg, ${fpuRegName(fpReg1)}")
        }

        Opcode.LOADI -> {
            val base = r1 ?: error("LOADI.f needs reg1 (base)")
            val off = imm ?: 0
            loadPointerToA0(base)
            if (off != 0) emitLine("adda.l  #$off, a0")
            emitLine("fmove.s  (a0), ${fpuRegName(fpReg1)}")
        }

        Opcode.STOREM -> {
            emitLine("fmove.s  ${fpuRegName(fpReg1)}, $target")
        }

        Opcode.STOREX -> {
            val idx = r1 ?: error("STOREX.f needs reg1 (index)")
            loadIndexToD0(idx)
            emitLine("lea  $target, a0")
            emitLine("fmove.s  ${fpuRegName(fpReg1)}, (0, a0, d0.l)", "index pre-scaled")
        }

        Opcode.STOREZM -> {
            emitFloatZero(fpReg1)
            emitLine("fmove.s  ${fpuRegName(fpReg1)}, $target")
        }

        Opcode.STOREZI -> {
            val base = r1 ?: error("STOREZI.f needs reg1 (base)")
            val off = imm ?: 0
            loadPointerToA0(base)
            if (off != 0) emitLine("adda.l  #$off, a0")
            emitFloatZero(fpReg1)
            emitLine("fmove.s  ${fpuRegName(fpReg1)}, (a0)")
        }

        Opcode.STOREZX -> {
            val idx = r1 ?: error("STOREZX.f needs reg1 (index)")
            loadIndexToD0(idx)
            emitLine("lea  $target, a0")
            emitFloatZero(fpReg1)
            emitLine("fmove.s  ${fpuRegName(fpReg1)}, (0, a0, d0.l)", "index pre-scaled")
        }

        Opcode.STOREHR -> {
            val slot = imm ?: error("STOREHR.f needs slot immediate")
            val hwReg = m68kSlotRegister(CallingConventionSlot(slot))
            emitLine("fmove  ${fpuRegName(fpReg1)}, $hwReg")
        }

        Opcode.STOREI -> {
            val valueReg = fpReg1
            val base = r1 ?: error("STOREI.f needs reg1 (base)")
            val off = imm ?: 0
            loadPointerToA0(base)
            if (off != 0) emitLine("adda.l  #$off, a0")
            emitLine("fmove.s  ${fpuRegName(valueReg)}, (a0)")
        }

        Opcode.LOADHFACZERO -> emitLine("fmovecr  #\$0f, ${fpuRegName(fpReg1)}")   // 0.0

        Opcode.LOADHFACONE -> emitLine("fmovecr  #\$0e, ${fpuRegName(fpReg1)}")   // 1.0

        Opcode.STOREHFACZERO -> {
            emitFloatZero(fpReg1)
            emitLine("fmove.s  ${fpuRegName(fpReg1)}, $target")
        }

        Opcode.STOREHFACONE -> {
            emitLine("fmovecr  #\$0e, ${fpuRegName(fpReg1)}")
            emitLine("fmove.s  ${fpuRegName(fpReg1)}, $target")
        }

        else -> error("Unknown float load/store opcode: ${insn.opcode}")
    }
}

private fun AsmGen.emitFloatZero(fpReg: RegisterNum) {
    emitLine("fmovecr  #\$0f, ${fpuRegName(fpReg)}")
}

private fun AsmGen.emitFloadConstant(fpReg: RegisterNum, value: Double) {
    when {
        value == 0.0 -> emitLine("fmovecr  #\$0f, ${fpuRegName(fpReg)}")
        value == 1.0 -> emitLine("fmovecr  #\$0e, ${fpuRegName(fpReg)}")
        else -> {
            val label = makeFloatConstLabel(value)
            emitLine("lea  $label, a0")
            emitLine("fmove.s  (a0), ${fpuRegName(fpReg)}")
        }
    }
}

private var floatConstCounter = 0
internal fun AsmGen.makeFloatConstLabel(value: Double): String {
    floatConstCounter++
    val label = "p8c_fconst_$floatConstCounter"
    this.dataFloatConstants.add(Pair(label, value))
    return label
}
