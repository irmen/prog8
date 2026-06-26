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

import prog8.code.core.Statusflag
import prog8.intermediate.*

fun CodeGenerator.translateControl(insn: IRInstruction) {
    val r1 = insn.reg1
    val r2 = insn.reg2
    val imm = insn.immediate
    val addr = insn.address
    val label = insn.labelSymbol

    when (insn.opcode) {
        Opcode.JUMP -> {
            val target = label ?: addr?.let { "${it.value}" } ?: error("JUMP needs target")
            emitLine("jmp $target")
        }

        Opcode.JUMPI -> {
            val reg = r1 ?: error("JUMPI needs reg1")
            emitLine("jmp (${regAddr(reg)})", "indirect jump via r$reg")
        }

        Opcode.CALL -> {
            val fnLabel = label ?: addr?.let { "${it.value}" } ?: error("CALL needs label or address")
            val args = insn.fcallArgs
            translateCall(fnLabel, args)
        }

        Opcode.CALLI -> {
            val reg = r1 ?: error("CALLI needs reg1")
            emitLine("; CALLI via r$reg (not implemented)")
        }

        Opcode.CALLFAR -> {
            val target = label ?: "${addr?.value ?: 0}"
            val bank = imm ?: 0
            emitLine("; CALLFAR $target, bank=$bank (not implemented)")
        }

        Opcode.CALLFARVB -> {
            val target = label ?: "${addr?.value ?: 0}"
            val bankReg = r1 ?: error("CALLFARVB needs reg1")
            emitLine("; CALLFARVB $target, bank in r$bankReg (not implemented)")
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
                    emitLine("lda ${regAddrLo(reg)}")
                }
                IRDataType.WORD -> {
                    emitLine("lda ${regAddrLo(reg)}")
                    emitLine("ldx ${regAddrHi(reg)}")
                }
                else -> emitLine("; RETURNR r$reg ${type.name} (not implemented)")
            }
            emitLine("rts")
        }

        Opcode.RETURNI -> {
            val value = imm ?: error("RETURNI needs immediate")
            val type = insn.type ?: IRDataType.BYTE
            when (type) {
                IRDataType.BYTE -> {
                    emitLine("lda #${value and 0xff}")
                }
                IRDataType.WORD -> {
                    emitLine("lda #<${value and 0xffff}")
                    emitLine("ldx #>${value and 0xffff}")
                }
                else -> emitLine("; RETURNI #$value ${type.name} (not implemented)")
            }
            emitLine("rts")
        }

        Opcode.PUSH -> {
            val reg = r1 ?: error("PUSH needs reg1")
            val type = insn.type ?: IRDataType.BYTE
            when (type) {
                IRDataType.BYTE -> {
                    emitLine("lda ${regAddrLo(reg)}")
                    emitLine("pha")
                }
                IRDataType.WORD -> {
                    emitLine("lda ${regAddrHi(reg)}")
                    emitLine("pha")
                    emitLine("lda ${regAddrLo(reg)}")
                    emitLine("pha")
                }
                else -> emitLine("; PUSH r$reg ${type.name} (not implemented)")
            }
        }

        Opcode.POP -> {
            val reg = r1 ?: error("POP needs reg1")
            val type = insn.type ?: IRDataType.BYTE
            when (type) {
                IRDataType.BYTE -> {
                    emitLine("pla")
                    emitLine("sta ${regAddrLo(reg)}")
                }
                IRDataType.WORD -> {
                    emitLine("pla")
                    emitLine("sta ${regAddrLo(reg)}")
                    emitLine("pla")
                    emitLine("sta ${regAddrHi(reg)}")
                }
                else -> emitLine("; POP r$reg ${type.name} (not implemented)")
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
            emitLine("lda ${regAddrLo(reg)}")
            emitLine("and #128")
            emitLine("beq +")
            emitLine("lda #255")
            emitLine("jmp ++")
            emitLabel("+")              // first + label
            emitLine("lda #0")
            emitLabel("+")              // second + label
            emitLine("sta ${regAddrHi(reg)}")
            emitStoreZero("${regAddrLo(reg) + 2}")
            emitStoreZero("${regAddrLo(reg) + 3}")
        }

        Opcode.LSIGW -> {
            val reg = r1 ?: error("LSIGW needs reg1")
            emitLine("lda ${regAddrHi(reg)}")
            emitLine("and #128")
            emitLine("beq +")
            emitLine("lda #255")
            emitLine("jmp ++")
            emitLabel("+")              // first + label
            emitLine("lda #0")
            emitLabel("+")              // second + label
            emitStoreZero("${regAddrLo(reg) + 2}")
            emitStoreZero("${regAddrLo(reg) + 3}")
        }

        Opcode.MSIGB, Opcode.MSIGW -> {
            emitLine("; ${insn.opcode} (not implemented)")
        }

        Opcode.BSIGB -> {
            emitLine("; BSIGB (not implemented)")
        }

        Opcode.MIDB -> {
            emitLine("; MIDB (not implemented)")
        }

        Opcode.CONCAT -> {
            emitLine("; CONCAT (not implemented)")
        }

        Opcode.EXT -> {
            val reg = r1 ?: error("EXT needs reg1")
            emitStoreZero("${regAddrHi(reg)}", "zero extend r$reg")
        }

        Opcode.EXTS -> {
            val reg = r1 ?: error("EXTS needs reg1")
            emitLine("lda ${regAddrLo(reg)}")
            emitLine("and #128")
            emitLine("beq +")
            emitLine("lda #255")
            emitLine("sta ${regAddrHi(reg)}")
            emitLine("jmp ++")
            emitLabel("+")
            emitStoreZero("${regAddrHi(reg)}")
            emitLabel("+")
        }

        Opcode.SQRT -> emitLine("; SQRT (not implemented)")
        Opcode.SQUARE -> emitLine("; SQUARE (not implemented)")
        Opcode.SGN -> emitLine("; SGN (not implemented)")

        Opcode.FFROMUB, Opcode.FFROMSB, Opcode.FFROMUW, Opcode.FFROMSW, Opcode.FFROMSL -> {
            emitLine("; ${insn.opcode} (float conversion, not implemented)")
        }

        Opcode.FTOUB, Opcode.FTOSB, Opcode.FTOUW, Opcode.FTOSW, Opcode.FTOSL -> {
            emitLine("; ${insn.opcode} (float conversion, not implemented)")
        }

        Opcode.FABS -> emitLine("; FABS (not implemented)")
        Opcode.FSIN -> emitLine("; FSIN (not implemented)")
        Opcode.FCOS -> emitLine("; FCOS (not implemented)")
        Opcode.FTAN -> emitLine("; FTAN (not implemented)")
        Opcode.FATAN -> emitLine("; FATAN (not implemented)")
        Opcode.FPOW -> emitLine("; FPOW (not implemented)")
        Opcode.FLN -> emitLine("; FLN (not implemented)")
        Opcode.FLOG -> emitLine("; FLOG (not implemented)")
        Opcode.FROUND -> emitLine("; FROUND (not implemented)")
        Opcode.FFLOOR -> emitLine("; FFLOOR (not implemented)")
        Opcode.FCEIL -> emitLine("; FCEIL (not implemented)")
        Opcode.FCOMP -> emitLine("; FCOMP (not implemented)")

        else -> error("Unknown control opcode: ${insn.opcode}")
    }
}

// === Call handling ===

private fun CodeGenerator.translateCall(fnLabel: String, args: FunctionCallArgs?) {
    if (args != null) {
        for (arg in args.arguments) {
            translateArgument(arg)
        }
    }

    emitLine("jsr $fnLabel")

    if (args != null) {
        for (ret in args.returns) {
            translateReturnValue(ret)
        }
    }
}

private fun CodeGenerator.translateArgument(arg: FunctionCallArgs.ArgumentSpec) {
    val regSpec = arg.reg
    val slot = regSpec.callingConventionSlot
    val regNum = regSpec.registerNum.value

    when (slot?.value) {
        0 -> {
            emitLine("lda ${regAddrLo(regNum)}", "arg to s0 (A)")
        }
        1 -> {
            emitLine("ldx ${regAddrLo(regNum)}", "arg to s1 (X)")
        }
        2 -> {
            emitLine("ldy ${regAddrLo(regNum)}", "arg to s2 (Y)")
        }
        3 -> {
            emitLine("lda ${regAddrLo(regNum)}", "arg to s3 (AX)")
            emitLine("ldx ${regAddrHi(regNum)}")
        }
        4 -> {
            emitLine("lda ${regAddrLo(regNum)}", "arg to s4 (AY)")
            emitLine("ldy ${regAddrHi(regNum)}")
        }
        5 -> {
            emitLine("ldx ${regAddrLo(regNum)}", "arg to s5 (XY)")
            emitLine("ldy ${regAddrHi(regNum)}")
        }
        null -> {
            val address = arg.address
            if (address != null) {
                emitLine("; arg passed via address $address")
            } else {
                emitLine("; arg passed via register $regNum (slot not set)")
            }
        }
    }
}

private fun CodeGenerator.translateReturnValue(ret: FunctionCallArgs.RegSpec) {
    val slot = ret.callingConventionSlot
    val regNum = ret.registerNum.value

    when (slot?.value) {
        0 -> {
            emitLine("sta ${regAddrLo(regNum)}", "return from s0 (A) -> r$regNum")
        }
        1 -> {
            emitLine("stx ${regAddrLo(regNum)}", "return from s1 (X) -> r$regNum")
        }
        2 -> {
            emitLine("sty ${regAddrLo(regNum)}", "return from s2 (Y) -> r$regNum")
        }
        3 -> {
            emitLine("sta ${regAddrLo(regNum)}", "return from s3 (AX) -> r$regNum")
            emitLine("stx ${regAddrHi(regNum)}")
        }
        4 -> {
            emitLine("sta ${regAddrLo(regNum)}", "return from s4 (AY) -> r$regNum")
            emitLine("sty ${regAddrHi(regNum)}")
        }
        5 -> {
            emitLine("stx ${regAddrLo(regNum)}", "return from s5 (XY) -> r$regNum")
            emitLine("sty ${regAddrHi(regNum)}")
        }
        null -> {
            val flag = ret.statusflag
            if (flag != null) {
                when (flag) {
                    Statusflag.Pc -> {
                        emitLine("lda #0")
                        emitLine("rol a", "carry flag -> bit 0")
                        emitLine("and #1")
                        emitLine("sta ${regAddrLo(regNum)}")
                        emitStoreZero("${regAddrHi(regNum)}")
                    }
                    Statusflag.Pz -> {
                        emitLine("lda #0")
                        emitLine("rol a", "zero flag -> bit 0")
                        emitLine("and #1")
                        emitLine("sta ${regAddrLo(regNum)}")
                        emitStoreZero("${regAddrHi(regNum)}")
                    }
                    Statusflag.Pv -> {
                        emitLine("lda #0")
                        emitLine("rol a", "overflow flag -> bit 0")
                        emitLine("and #1")
                        emitLine("sta ${regAddrLo(regNum)}")
                        emitStoreZero("${regAddrHi(regNum)}")
                    }
                    Statusflag.Pn -> {
                        emitLine("lda #0")
                        emitLine("rol a", "negative flag -> bit 0")
                        emitLine("and #1")
                        emitLine("sta ${regAddrLo(regNum)}")
                        emitStoreZero("${regAddrHi(regNum)}")
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
    emitLine("jsr p8_syscall_handler")
    if (args != null) {
        for (ret in args.returns) {
            translateReturnValue(ret)
        }
    }
}
