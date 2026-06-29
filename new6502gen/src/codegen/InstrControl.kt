/*
 * Control flow IR instruction translations for the new6502gen code generator.
 *
 * Handles: JUMP, JUMPI, CALL, CALLI, CALLFAR, CALLFARVB, SYSCALL,
 * RETURN, RETURNR, RETURNI, PUSH, POP, PUSHST, POPST,
 * CLC, SEC, CLI, SEI, ALIGN,
 * sign extension (LSIGB, LSIGW, EXT, EXTS),
 * and stubs for float math and conversions.
 *
 * CALL instruction translation:
 *   1. Load arguments into the appropriate calling convention slots (s0-s5)
 *   2. JSR to the subroutine
 *   3. Save return values from slots back to virtual registers
 *
 * Argument/return value passing:
 *   - s0 (A, byte), s1 (X, byte), s2 (Y, byte)
 *   - s3 (AX, word), s4 (AY, word), s5 (XY, word)
 *   - Status flags (Pc, Pz, Pv, Pn) for boolean returns
 *
 * Not yet implemented: CALLI, CALLFAR, CALLFARVB, all floating point ops.
 */

package codegen

import prog8.code.core.*
import prog8.intermediate.*

fun CodeGenerator.translateControl(insn: IRInstruction) {
    val r1 = insn.reg1
    val r2 = insn.reg2
    val imm = insn.immediate
    val addr = insn.address
    val label = insn.labelSymbol

    when (insn.opcode) {
        Opcode.JUMP -> {
            val target = label ?: addr?.let { it.value.toHex() } ?: error("JUMP needs target")
            emitLine("jmp  $target")
        }

        Opcode.JUMPI -> {
            val reg = r1 ?: error("JUMPI needs reg1")
            emitLine("jmp  (${regAddr(reg)})")
        }

        Opcode.CALL -> {
            val fnLabel = label ?: addr?.let { it.value.toHex() } ?: error("CALL needs label or address")
            val args = insn.fcallArgs
            translateCall(fnLabel, args)
        }

        Opcode.CALLI -> {
            val reg = r1 ?: error("CALLI needs reg1")
            TODO("CALLI via r$reg")
        }

        Opcode.CALLFAR -> {
            val target = label ?: (addr?.value ?: 0u).toHex()
            val bank = imm ?: 0
            TODO("CALLFAR $target, bank=$bank")
        }

        Opcode.CALLFARVB -> {
            val target = label ?: (addr?.value ?: 0u).toHex()
            val bankReg = r1 ?: error("CALLFARVB needs reg1")
            TODO("CALLFARVB $target, bank in r$bankReg")
        }

        Opcode.SYSCALL -> {
            val args = insn.fcallArgs
            translateSyscall(args)
        }

        Opcode.RETURN -> {
            emitLine("rts")
        }

        Opcode.RETURNR -> {
            val reg = r1 ?: error("RETURNR needs reg1")
            val type = insn.type ?: IRDataType.BYTE
            when (type) {
                IRDataType.BYTE -> {
                    emitLine("lda  ${regAddrLo(reg)}")
                }
                IRDataType.WORD -> {
                    emitLine("lda  ${regAddrLo(reg)}")
                    emitLine("ldx  ${regAddrHi(reg)}")
                }
                else -> TODO("RETURNR r$reg ${type.name}")
            }
            emitLine("rts")
        }

        Opcode.RETURNI -> {
            val value = imm ?: error("RETURNI needs immediate")
            val type = insn.type ?: IRDataType.BYTE
            when (type) {
                IRDataType.BYTE -> {
                    emitLine("lda  #${value and 0xff}")
                }
                IRDataType.WORD -> {
                    emitLine("lda  #<${value and 0xffff}")
                    emitLine("ldx  #>${value and 0xffff}")
                }
                else -> TODO("RETURNI #$value ${type.name}")
            }
            emitLine("rts")
        }

        Opcode.PUSH -> {
            val reg = r1 ?: error("PUSH needs reg1")
            val type = insn.type ?: IRDataType.BYTE
            when (type) {
                IRDataType.BYTE -> {
                    emitLine("lda  ${regAddrLo(reg)}")
                    emitLine("pha")
                }
                IRDataType.WORD -> {
                    emitLine("lda  ${regAddrHi(reg)}")
                    emitLine("pha")
                    emitLine("lda  ${regAddrLo(reg)}")
                    emitLine("pha")
                }
                else -> TODO("PUSH r$reg ${type.name}")
            }
        }

        Opcode.POP -> {
            val reg = r1 ?: error("POP needs reg1")
            val type = insn.type ?: IRDataType.BYTE
            when (type) {
                IRDataType.BYTE -> {
                    emitLine("pla")
                    emitLine("sta  ${regAddrLo(reg)}")
                }
                IRDataType.WORD -> {
                    emitLine("pla")
                    emitLine("sta  ${regAddrLo(reg)}")
                    emitLine("pla")
                    emitLine("sta  ${regAddrHi(reg)}")
                }
                else -> TODO("POP r$reg ${type.name}")
            }
        }

        Opcode.PUSHST -> {
            emitLine("php")
        }

        Opcode.POPST -> {
            emitLine("plp")
        }

        Opcode.CLC -> emitLine("clc")
        Opcode.SEC -> emitLine("sec")
        Opcode.CLI -> emitLine("cli")
        Opcode.SEI -> emitLine("sei")

        Opcode.ALIGN -> {
            val alignment = imm ?: 256
            emitLine("; ALIGN to $alignment bytes")
        }

        Opcode.LSIGB -> {
            val reg = r1 ?: error("LSIGB needs reg1")
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("and  #128")
            emitLine("beq  +")
            emitLine("lda  #255")
            emitLine("jmp  ++")
            emitLabel("+")              // first + label
            emitLine("lda  #0")
            emitLabel("+")              // second + label
            emitLine("sta  ${regAddrHi(reg)}")
            emitStoreZero("${regAddrLo(reg) + 2}")
            emitStoreZero("${regAddrLo(reg) + 3}")
        }

        Opcode.LSIGW -> {
            val reg = r1 ?: error("LSIGW needs reg1")
            emitLine("lda  ${regAddrHi(reg)}")
            emitLine("and  #128")
            emitLine("beq  +")
            emitLine("lda  #255")
            emitLine("jmp  ++")
            emitLabel("+")              // first + label
            emitLine("lda  #0")
            emitLabel("+")              // second + label
            emitStoreZero("${regAddrLo(reg) + 2}")
            emitStoreZero("${regAddrLo(reg) + 3}")
        }

        Opcode.MSIGB, Opcode.MSIGW -> TODO("${insn.opcode}")

        Opcode.BSIGB -> TODO("BSIGB")

        Opcode.MIDB -> TODO("MIDB")

        Opcode.CONCAT -> TODO("CONCAT")

        Opcode.EXT -> {
            val reg = r1 ?: error("EXT needs reg1")
            emitStoreZero("${regAddrHi(reg)}", "zero extend r$reg")
        }

        Opcode.EXTS -> {
            val reg = r1 ?: error("EXTS needs reg1")
            emitLine("lda  ${regAddrLo(reg)}")
            emitLine("and  #128")
            emitLine("beq  +")
            emitLine("lda  #255")
            emitLine("sta  ${regAddrHi(reg)}")
            emitLine("jmp  ++")
            emitLabel("+")
            emitStoreZero("${regAddrHi(reg)}")
            emitLabel("+")
        }

        Opcode.SQRT -> TODO("SQRT")
        Opcode.SQUARE -> TODO("SQUARE")
        Opcode.SGN -> TODO("SGN")

        Opcode.FFROMUB, Opcode.FFROMSB, Opcode.FFROMUW, Opcode.FFROMSW, Opcode.FFROMSL -> TODO("${insn.opcode} (float conversion)")

        Opcode.FTOUB, Opcode.FTOSB, Opcode.FTOUW, Opcode.FTOSW, Opcode.FTOSL -> TODO("${insn.opcode} (float conversion)")

        Opcode.FABS -> TODO("FABS")
        Opcode.FSIN -> TODO("FSIN")
        Opcode.FCOS -> TODO("FCOS")
        Opcode.FTAN -> TODO("FTAN")
        Opcode.FATAN -> TODO("FATAN")
        Opcode.FPOW -> TODO("FPOW")
        Opcode.FLN -> TODO("FLN")
        Opcode.FLOG -> TODO("FLOG")
        Opcode.FROUND -> TODO("FROUND")
        Opcode.FFLOOR -> TODO("FFLOOR")
        Opcode.FCEIL -> TODO("FCEIL")
        Opcode.FCOMP -> TODO("FCOMP")

        else -> error("Unknown control opcode: ${insn.opcode}")
    }
}

// === Call handling ===

private fun CodeGenerator.translateCall(fnLabel: String, args: FunctionCallArgs?) {
    if (args != null) {
        for (arg in args.arguments) {
            translateArgument(arg, fnLabel)
        }
    }

    emitLine("jsr  $fnLabel")

    if (args != null) {
        for (ret in args.returns) {
            translateReturnValue(ret)
        }
    }
}

private fun CodeGenerator.translateArgument(arg: FunctionCallArgs.ArgumentSpec, fnLabel: String? = null) {
    val regSpec = arg.reg
    val slot = regSpec.callingConventionSlot
    val regNum = regSpec.registerNum.value

    when (slot?.value) {
        0 -> {
            emitLine("lda  ${regAddrLo(regNum)}")
        }
        1 -> {
            emitLine("ldx  ${regAddrLo(regNum)}")
        }
        2 -> {
            emitLine("ldy  ${regAddrLo(regNum)}")
        }
        3 -> {
            emitLine("lda  ${regAddrLo(regNum)}")
            emitLine("ldx  ${regAddrHi(regNum)}")
        }
        4 -> {
            emitLine("lda  ${regAddrLo(regNum)}")
            emitLine("ldy  ${regAddrHi(regNum)}")
        }
        5 -> {
            emitLine("ldx  ${regAddrLo(regNum)}")
            emitLine("ldy  ${regAddrHi(regNum)}")
        }
        null -> {
            val address = arg.address
            if (address != null) {
                when (regSpec.dt) {
                    IRDataType.BYTE -> {
                        emitLine("lda  ${regAddrLo(regNum)}")
                        emitLine("sta  ${address.toHex()}")
                    }
                    IRDataType.WORD -> {
                        emitLine("lda  ${regAddrLo(regNum)}")
                        emitLine("sta  ${address.toHex()}")
                        emitLine("lda  ${regAddrHi(regNum)}")
                        emitLine("sta  ${address.toHex()}+1")
                    }
                    IRDataType.LONG -> {
                        emitLine("lda  ${regAddrLo(regNum)}")
                        emitLine("sta  ${address.toHex()}")
                        emitLine("lda  ${regAddrHi(regNum)}")
                        emitLine("sta  ${address.toHex()}+1")
                        emitLine("lda  ${regAddrLo(regNum) + 2}")
                        emitLine("sta  ${address.toHex()}+2")
                        emitLine("lda  ${regAddrLo(regNum) + 3}")
                        emitLine("sta  ${address.toHex()}+3")
                    }
                    IRDataType.FLOAT -> {
                        TODO("FLOAT arg to address $address")
                    }
                }
            } else {
                val name = arg.name
                if (name.isNotEmpty()) {
                    val fullName = if (fnLabel != null) "$fnLabel.$name" else name
                    val label = resolveSymbolRef(fullName)
                    when (regSpec.dt) {
                        IRDataType.BYTE -> {
                            emitLine("lda  ${regAddrLo(regNum)}")
                            emitLine("sta  $label")
                        }
                        IRDataType.WORD -> {
                            emitLine("lda  ${regAddrLo(regNum)}")
                            emitLine("sta  $label")
                            emitLine("lda  ${regAddrHi(regNum)}")
                            emitLine("sta  ${label}+1")
                        }
                        IRDataType.LONG -> {
                            emitLine("lda  ${regAddrLo(regNum)}")
                            emitLine("sta  $label")
                            emitLine("lda  ${regAddrHi(regNum)}")
                            emitLine("sta  ${label}+1")
                            emitLine("lda  ${regAddrLo(regNum) + 2}")
                            emitLine("sta  ${label}+2")
                            emitLine("lda  ${regAddrLo(regNum) + 3}")
                            emitLine("sta  ${label}+3")
                        }
                        IRDataType.FLOAT -> {
                            TODO("FLOAT arg to $label")
                        }
                    }
                } else {
                    TODO("arg r$regNum - no slot, address, or name")
                }
            }
        }
    }
}

private fun CodeGenerator.translateReturnValue(ret: FunctionCallArgs.RegSpec) {
    val slot = ret.callingConventionSlot
    val regNum = ret.registerNum.value

    when (slot?.value) {
        0 -> {
            emitLine("sta  ${regAddrLo(regNum)}")
        }
        1 -> {
            emitLine("stx  ${regAddrLo(regNum)}")
        }
        2 -> {
            emitLine("sty  ${regAddrLo(regNum)}")
        }
        3 -> {
            emitLine("sta  ${regAddrLo(regNum)}")
            emitLine("stx  ${regAddrHi(regNum)}")
        }
        4 -> {
            emitLine("sta  ${regAddrLo(regNum)}")
            emitLine("sty  ${regAddrHi(regNum)}")
        }
        5 -> {
            emitLine("stx  ${regAddrLo(regNum)}")
            emitLine("sty  ${regAddrHi(regNum)}")
        }
        null -> {
            val flag = ret.statusflag
            if (flag != null) {
                when (flag) {
                    Statusflag.Pc -> {
                        emitLine("lda  #0")
                        emitLine("rol  a")
                        emitLine("and  #1")
                        emitLine("sta  ${regAddrLo(regNum)}")
                        emitStoreZero("${regAddrHi(regNum)}")
                    }
                    Statusflag.Pz -> {
                        emitLine("lda  #0")
                        emitLine("rol  a")
                        emitLine("and  #1")
                        emitLine("sta  ${regAddrLo(regNum)}")
                        emitStoreZero("${regAddrHi(regNum)}")
                    }
                    Statusflag.Pv -> {
                        emitLine("lda  #0")
                        emitLine("rol  a")
                        emitLine("and  #1")
                        emitLine("sta  ${regAddrLo(regNum)}")
                        emitStoreZero("${regAddrHi(regNum)}")
                    }
                    Statusflag.Pn -> {
                        emitLine("lda  #0")
                        emitLine("rol  a")
                        emitLine("and  #1")
                        emitLine("sta  ${regAddrLo(regNum)}")
                        emitStoreZero("${regAddrHi(regNum)}")
                    }
                }
            } else if (regNum >= 0) {
                when (ret.dt) {
                    IRDataType.BYTE -> {
                        emitLine("sta  ${regAddrLo(regNum)}")
                    }
                    IRDataType.WORD -> {
                        emitLine("sta  ${regAddrLo(regNum)}")
                        emitLine("sty  ${regAddrHi(regNum)}")
                    }
                    IRDataType.LONG -> {
                        TODO("LONG return to r$regNum (slot/flag not set)")
                    }
                    IRDataType.FLOAT -> {
                        TODO("FLOAT return to r$regNum (slot/flag not set)")
                    }
                }
            } else {
                emitLine("; return value to r$regNum (slot/flag not set)")
            }
        }
    }
}

// === Syscall handling ===

private fun CodeGenerator.translateSyscall(args: FunctionCallArgs?) {
    emitLine("; SYSCALL")
    if (args != null) {
        for (arg in args.arguments) {
            translateArgument(arg)
        }
    }
    emitLine("jsr  p8_syscall_handler")
    if (args != null) {
        for (ret in args.returns) {
            translateReturnValue(ret)
        }
    }
}
