package prog8.codegen.m68k

import prog8.code.core.CpuType
import prog8.intermediate.CallingConventionSlot
import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction
import prog8.intermediate.Opcode

/**
 * Load an array index into d0 for use with `(a0,d0.w)` addressing.
 * Word indices can be loaded directly; byte indices must be zero-extended
 * because `move.w` from a byte regfile slot would read the adjacent byte too.
 */
private fun AsmGen.loadIndexToD0(idx: Int) {
    if (regType(idx) == IRDataType.BYTE) {
        invalidateD0Cache()                 // moveq clobbers d0
        emitLine("moveq  #0, d0")
        emitLoadD0(idx, IRDataType.BYTE)    // cache as byte; consumer widens
    } else {
        emitLoadD0(idx, IRDataType.WORD)    // cache as word; consumer widens
    }
}

private fun AsmGen.addIndirectOffset(offset: Int) {
    when (offset) {
        in 1..8 -> emitLine("addq.l  #$offset, a0")
        in -8..-1 -> emitLine("subq.l  #${-offset}, a0")
        else -> emitLine("adda.l  #$offset, a0")
    }
}

internal fun AsmGen.translateLoadStore(insn: IRInstruction, suppressRegfileStore: Boolean = false) {
    val type = insn.type ?: IRDataType.BYTE
    val r1 = insn.reg1
    val r2 = insn.reg2
    val imm = insn.immediate
    val addr = insn.address
    val label = insn.labelSymbol
    val offset = insn.labelSymbolOffset
    val target = resolveAddress(addr, label, offset)

    if (type == IRDataType.FLOAT) {
        translateFloatLoadStore(insn, target, suppressRegfileStore)
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
                    if (suppressRegfileStore)
                        return
                    if(value == 0)
                        emitLine("clr$s  ${regAddr(dst)}")
                    else
                        emitLine("move$s  #$value, ${regAddr(dst)}")
                    invalidateD0CacheForSlot(dst)
                }
                sym != null -> {
                    val resolved = resolveSymbolRef(sym)
                    val symOff = if (offset != null) "$resolved+$offset" else resolved
                    emitLine("move.l  #$symOff, ${regAddr(dst)}")
                    invalidateD0CacheForSlot(dst)
                }
                else -> error("LOAD needs immediate or labelSymbol")
            }
        }

        Opcode.LOADM -> {
            val dst = r1 ?: error("LOADM needs reg1")
            emitLine("move${dtSuffix(type)}  $target, ${regAddr(dst)}")
            invalidateD0CacheForSlot(dst)
        }

        Opcode.LOADR -> {
            val dst = r1 ?: error("LOADR needs reg1")
            val src = r2 ?: error("LOADR needs reg2")
            emitLine("move$s  ${regAddr(src)}, ${regAddr(dst)}")
            invalidateD0CacheForSlot(dst)
        }

        Opcode.LOADX -> {
            val dst = r1 ?: error("LOADX needs reg1")
            val idx = r2 ?: error("LOADX needs reg2")
            val scale = insn.scale
            loadIndexToD0(idx)
            if(scale!=1) {
                invalidateD0Cache()             // scaling transforms d0
                if(program.options.compTarget.cpu >= CpuType.M68020) {
                    if(scale!=2 && scale!=4 && scale!=8) emitLine("muls.w  #$scale, d0")
                } else {
                    when(scale) {
                        2 -> emitLine("add.w  d0,d0")
                        4 -> emitLine("lsl.w  #2, d0")
                        else -> emitLine("muls.w  #$scale, d0")
                    }
                }
            }
            emitLine("lea  $target, a0")
            val sx = dtSuffix(type)
            val indexMode = when {
                program.options.compTarget.cpu >= CpuType.M68020 && scale==2 -> "(a0,d0.w*2)"
                program.options.compTarget.cpu >= CpuType.M68020 && scale==4 -> "(a0,d0.w*4)"
                program.options.compTarget.cpu >= CpuType.M68020 && scale==8 -> "(a0,d0.w*8)"
                else -> "(a0,d0.w)"
            }
            emitLoadD0FromAddress(indexMode, type)
            emitStoreD0(dst, type)
        }

        Opcode.LOADHR -> {
            val dst = r1 ?: error("LOADHR needs reg1")
            val slot = imm ?: error("LOADHR needs slot immediate")
            val hwReg = m68kSlotRegister(CallingConventionSlot(slot))
            emitLine("move$s  $hwReg, ${regAddr(dst)}")
            invalidateD0CacheForSlot(dst)
        }

        Opcode.LOADI -> {
            val dst = r1 ?: error("LOADI needs reg1")
            val base = r2 ?: error("LOADI needs reg2")
            val off = imm ?: 0
            loadPointerToA0(base)
            if(off<-32768 || off>32767) {
                addIndirectOffset(off)
                emitLoadD0FromAddress("(a0)", type)
            } else {
                if(off==0)
                    emitLoadD0FromAddress("(a0)", type)
                else
                    emitLoadD0FromAddress("($off,a0)", type)
            }
            emitStoreD0(dst, type)
        }

        Opcode.STOREM -> {
            val src = r1 ?: error("STOREM needs reg1")
            emitLine("move${dtSuffix(type)}  ${regAddr(src)}, $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.STOREIM -> {
            val value = imm ?: error("STOREIM needs immediate value")
            if(value == 0)
                emitLine("clr$s  $target")
            else
                emitLine("move$s  #$value, $target")
            invalidateD0CacheForAddress(target)
        }

        Opcode.STOREX -> {
            val value = r1 ?: error("STOREX needs reg1")
            val idx = r2 ?: error("STOREX needs reg2")
            val scale = insn.scale
            loadIndexToD0(idx)
            if(scale!=1) {
                invalidateD0Cache()             // scaling transforms d0
                if(program.options.compTarget.cpu >= CpuType.M68020) {
                    if(scale!=2 && scale!=4 && scale!=8) emitLine("muls.w  #$scale, d0")
                } else {
                    when(scale) {
                        2 -> emitLine("add.w  d0,d0")
                        4 -> emitLine("lsl.w  #2, d0")
                        else -> emitLine("muls.w  #$scale, d0")
                    }
                }
            }
            emitLine("lea  $target, a0")
            val sx = dtSuffix(type)
            emitLine("move$sx  ${regAddr(value)}, d1")
            val indexMode = when {
                program.options.compTarget.cpu >= CpuType.M68020 && scale==2 -> "(a0,d0.w*2)"
                program.options.compTarget.cpu >= CpuType.M68020 && scale==4 -> "(a0,d0.w*4)"
                program.options.compTarget.cpu >= CpuType.M68020 && scale==8 -> "(a0,d0.w*8)"
                else -> "(a0,d0.w)"
            }
            emitLine("move$sx  d1, $indexMode")
        }

        Opcode.STOREZM -> {
            emitLine("clr${dtSuffix(type)}  $target")
            invalidateD0CacheForAddress(target)
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
            val scale = insn.scale
            loadIndexToD0(idx)
            if(scale!=1) {
                invalidateD0Cache()             // scaling transforms d0
                if(program.options.compTarget.cpu >= CpuType.M68020) {
                    if(scale!=2 && scale!=4 && scale!=8) emitLine("muls.w  #$scale, d0")
                } else {
                    when(scale) {
                        2 -> emitLine("add.w  d0,d0")
                        4 -> emitLine("lsl.w  #2, d0")
                        else -> emitLine("muls.w  #$scale, d0")
                    }
                }
            }
            emitLine("lea  $target, a0")
            val indexMode = when {
                program.options.compTarget.cpu >= CpuType.M68020 && scale==2 -> "(a0,d0.w*2)"
                program.options.compTarget.cpu >= CpuType.M68020 && scale==4 -> "(a0,d0.w*4)"
                program.options.compTarget.cpu >= CpuType.M68020 && scale==8 -> "(a0,d0.w*8)"
                else -> "(a0,d0.w)"
            }
            emitLine("clr$s  $indexMode")
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

        Opcode.LOADP_INC -> {
            val dst = r1 ?: error("LOADP_INC needs reg1")
            // >r1,<>a  : r1 = *a; a += sizeof(type)
            // a is pointer variable (LONG) in memory
            emitLine("move.l  $target, a0")
            emitLoadD0FromAddress("(a0)+", type)
            emitLine("move.l  a0, $target")
            invalidateD0CacheForAddress(target)
            emitStoreD0(dst, type)
        }

        Opcode.STOREP_INC -> {
            val value = r1 ?: error("STOREP_INC needs reg1")
            // <r1,<>a  : *a = r1; a += sizeof(type)
            emitLine("move.l  $target, a0")
            emitLine("move$s  ${regAddr(value)}, (a0)+")
            emitLine("move.l  a0, $target")
            invalidateD0CacheForAddress(target)
        }

        else -> error("Unknown load/store opcode: ${insn.opcode}")
    }
}

// === Float load/store via FPU (68881/68882) ===

private fun AsmGen.translateFloatLoadStore(insn: IRInstruction, target: String, suppressRegfileStore: Boolean = false) {
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
            invalidateD0CacheForAddress(target)
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
            val scale = insn.scale
            // full-width d0.l indexing requires zero-extending the word index to 32 bits
            invalidateD0Cache()                 // moveq clobbers d0
            emitLine("moveq  #0, d0")
            emitLoadD0(idx, IRDataType.WORD)    // cache as word; d0.l widens it
            if(scale!=1) {
                invalidateD0Cache()             // scaling transforms d0
                when(scale) {
                    2 -> emitLine("add.l  d0,d0")
                    4 -> emitLine("lsl.l  #2, d0")
                    8 -> emitLine("lsl.l  #3, d0")
                    else -> emitLine("muls.w  #$scale, d0")
                }
            }
            emitLine("lea  $target, a0")
            emitLine("fmovecr  #\$0f, fp0")
            emitLine("fmove.s  fp0, (0, a0, d0.l)")
        }

        Opcode.STOREHFACZERO -> {
            emitLine("fmovecr  #\$0f, fp0")
            emitLine("fmove.s  fp0, $target")
            invalidateD0CacheForAddress(target)
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
            invalidateD0CacheForAddress(target)
        }

        else -> {
            val fp1 = fpReg1 ?: error("float op needs fpReg1 for ${insn.opcode}")
            when (insn.opcode) {
                Opcode.LOAD -> when {
                    immFp != null -> {
                        if (suppressRegfileStore)
                            return
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
                    val scale = insn.scale
                    // full-width d0.l indexing requires zero-extending the word index to 32 bits
                    invalidateD0Cache()                 // moveq clobbers d0
                    emitLine("moveq  #0, d0")
                    emitLoadD0(idx, IRDataType.WORD)    // cache as word; d0.l widens it
                    if(scale!=1) {
                        invalidateD0Cache()             // scaling transforms d0
                        when(scale) {
                            2 -> emitLine("add.l  d0,d0")
                            4 -> emitLine("lsl.l  #2, d0")
                            8 -> emitLine("lsl.l  #3, d0")
                            else -> emitLine("muls.w  #$scale, d0")
                        }
                    }
                    emitLine("lea  $target, a0")
                    emitLine("fmove.s  (0, a0, d0.l), $FP_ACC")
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
                    invalidateD0CacheForAddress(target)
                }

                Opcode.STOREX -> {
                    val idx = r1 ?: error("STOREX.f needs reg1 (index)")
                    val scale = insn.scale
                    // full-width d0.l indexing requires zero-extending the word index to 32 bits
                    invalidateD0Cache()                 // moveq clobbers d0
                    emitLine("moveq  #0, d0")
                    emitLoadD0(idx, IRDataType.WORD)    // cache as word; d0.l widens it
                    if(scale!=1) {
                        invalidateD0Cache()             // scaling transforms d0
                        when(scale) {
                            2 -> emitLine("add.l  d0,d0")
                            4 -> emitLine("lsl.l  #2, d0")
                            8 -> emitLine("lsl.l  #3, d0")
                            else -> emitLine("muls.w  #$scale, d0")
                        }
                    }
                    emitLine("lea  $target, a0")
                    emitLine("fmove.s  ${floatRegFileAddr(fp1)}, $FP_ACC")
                    emitLine("fmove.s  $FP_ACC, (0, a0, d0.l)")
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
                    invalidateD0CacheForAddress(target)
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
