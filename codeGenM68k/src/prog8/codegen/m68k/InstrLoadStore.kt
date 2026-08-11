package prog8.codegen.m68k

import prog8.intermediate.CallingConventionSlot
import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction
import prog8.intermediate.Opcode

private fun staticSymbolComment(label: String?): String = label ?: ""

private fun AsmGen.addIndirectOffset(offset: Int) {
    when (offset) {
        in 1..8 -> emitLine("addq.l  #$offset, a0")
        in -8..-1 -> emitLine("subq.l  #${-offset}, a0")
        else -> emitLine("adda.l  #$offset, a0")
    }
}

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
                value != null -> {
                    if(value == 0)
                        emitLine("clr$s  ${regAddr(dst)}")
                    else
                        emitLine("move$s  #$value, ${regAddr(dst)}")
                }
                sym != null -> {
                    val resolved = resolveSymbolRef(sym)
                    val symOff = if (offset != null) "$resolved+$offset" else resolved
                    emitLine("move.l  #$symOff, ${regAddr(dst)}")
                }
                else -> error("LOAD needs immediate or labelSymbol")
            }
        }

        Opcode.LOADM -> {
            val dst = r1 ?: error("LOADM needs reg1")
            emitLine("move${dtSuffix(type)}  $target, ${regAddr(dst)}", staticSymbolComment(label))
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
                addIndirectOffset(off)
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
            emitLine("move${dtSuffix(type)}  ${regAddr(src)}, $target", staticSymbolComment(label))
        }

        Opcode.STOREIM -> {
            val value = imm ?: error("STOREIM needs immediate value")
            if(value == 0)
                emitLine("clr$s  $target", staticSymbolComment(label))
            else
                emitLine("move$s  #$value, $target", staticSymbolComment(label))
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
            emitLine("clr${dtSuffix(type)}  $target", staticSymbolComment(label))
        }

        Opcode.STOREZI -> {
            val base = r1 ?: error("STOREZI needs reg1")
            val off = imm ?: 0
            loadPointerToA0(base)
            if(off<-32768 || off>32767) {
                addIndirectOffset(off)
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
                addIndirectOffset(off)
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
    val fpReg1 = insn.fpReg1
    val fpReg2 = insn.fpReg2
    val r1 = insn.reg1
    val imm = insn.immediate
    val immFp = insn.immediateFp
    val label = insn.labelSymbol
    val offset = insn.labelSymbolOffset

    when (insn.opcode) {
        Opcode.STOREZM -> {
            emitLine("fmovecr  #\$0f, fp0")
            emitLine("fmove.s  fp0, $target")
        }

        Opcode.STOREZI -> {
            val base = r1 ?: error("STOREZI.f needs reg1 (base)")
            val off = imm ?: 0
            loadPointerToA0(base)
            if (off != 0) addIndirectOffset(off)
            emitLine("fmovecr  #\$0f, fp0")
            emitLine("fmove.s  fp0, (a0)")
        }

        Opcode.STOREZX -> {
            val idx = r1 ?: error("STOREZX.f needs reg1 (index)")
            loadIndexToD0(idx)
            emitLine("lea  $target, a0")
            emitLine("fmovecr  #\$0f, fp0")
            emitLine("fmove.s  fp0, (0, a0, d0.l)", "index pre-scaled")
        }

        Opcode.STOREHFACZERO -> {
            emitLine("fmovecr  #\$0f, fp0")
            emitLine("fmove.s  fp0, $target")
        }

        Opcode.STOREIM -> {
            val value = immFp ?: error("STOREIM.f needs immediateFp value")
            val native = nativeFloatConst(value)
            if (native != null) {
                emitLine("fmovecr  #$native, fp0")
            } else {
                val lbl = makeFloatConstLabel(value)
                emitLine("lea  $lbl, a0")
                emitLine("fmove.s  (a0), fp0")
            }
            emitLine("fmove.s  fp0, $target")
        }

        else -> {
            val fp1 = fpReg1 ?: error("float op needs fpReg1 for ${insn.opcode}")
            when (insn.opcode) {
                Opcode.LOAD -> when {
                    immFp != null -> {
                        emitFloadConstantToAcc(immFp)
                        emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fp1)}")
                    }
                    label != null -> {
                        val resolved = resolveSymbolRef(label)
                        val symOff = if (offset != null) "$resolved+$offset" else resolved
                        emitLine("lea  $symOff, a0")
                        emitLine("fmove.s  (a0), $FP_ACC")
                        emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fp1)}")
                    }
                    else -> error("FLOAT LOAD needs immediateFp or labelSymbol")
                }

                Opcode.LOADM -> {
                    emitLine("fmove.s  $target, $FP_ACC")
                    emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fp1)}")
                }

                Opcode.LOADR -> {
                    val src = fpReg2 ?: error("LOADR.f needs fpReg2")
                    emitLine("fmove.s  ${floatRegFileAddr(src)}, $FP_ACC")
                    emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fp1)}")
                }

                Opcode.LOADX -> {
                    val idx = r1 ?: error("LOADX.f needs reg1 (index)")
                    loadIndexToD0(idx)
                    emitLine("lea  $target, a0")
                    emitLine("fmove.s  (0, a0, d0.l), $FP_ACC", "index pre-scaled")
                    emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fp1)}")
                }

                Opcode.LOADHR -> {
                    val slot = imm ?: error("LOADHR.f needs slot immediate")
                    val hwReg = m68kSlotRegister(CallingConventionSlot(slot))
                    emitLine("fmove  $hwReg, $FP_ACC")
                    emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fp1)}")
                }

                Opcode.LOADI -> {
                    val base = r1 ?: error("LOADI.f needs reg1 (base)")
                    val off = imm ?: 0
                    loadPointerToA0(base)
                    if (off != 0) addIndirectOffset(off)
                    emitLine("fmove.s  (a0), $FP_ACC")
                    emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fp1)}")
                }

                Opcode.STOREM -> {
                    emitLine("fmove.s  ${floatRegFileAddr(fp1)}, $FP_ACC")
                    emitLine("fmove.s  $FP_ACC, $target")
                }

                Opcode.STOREX -> {
                    val idx = r1 ?: error("STOREX.f needs reg1 (index)")
                    loadIndexToD0(idx)
                    emitLine("lea  $target, a0")
                    emitLine("fmove.s  ${floatRegFileAddr(fp1)}, $FP_ACC")
                    emitLine("fmove.s  $FP_ACC, (0, a0, d0.l)", "index pre-scaled")
                }

                Opcode.STOREHR -> {
                    val slot = imm ?: error("STOREHR.f needs slot immediate")
                    val hwReg = m68kSlotRegister(CallingConventionSlot(slot))
                    emitLine("fmove.s  ${floatRegFileAddr(fp1)}, $FP_ACC")
                    emitLine("fmove  $FP_ACC, $hwReg")
                }

                Opcode.STOREI -> {
                    val base = r1 ?: error("STOREI.f needs reg1 (base)")
                    val off = imm ?: 0
                    loadPointerToA0(base)
                    if (off != 0) addIndirectOffset(off)
                    emitLine("fmove.s  ${floatRegFileAddr(fp1)}, $FP_ACC")
                    emitLine("fmove.s  $FP_ACC, (a0)")
                }

                Opcode.LOADHFACZERO -> {
                    emitLine("fmovecr  #\$0f, $FP_ACC")
                    emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fp1)}")
                }
                Opcode.LOADHFACONE -> {
                    emitLine("fmovecr  #$32, $FP_ACC")
                    emitLine("fmove.s  $FP_ACC, ${floatRegFileAddr(fp1)}")
                }
                Opcode.STOREHFACONE -> {
                    emitLine("fmovecr  #$32, $FP_ACC")
                    emitLine("fmove.s  $FP_ACC, $target")
                }

                else -> error("Unknown float load/store opcode: ${insn.opcode}")
            }
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
