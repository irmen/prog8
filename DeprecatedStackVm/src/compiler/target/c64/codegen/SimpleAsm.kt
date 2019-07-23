package compiler.target.c64.codegen

import prog8.compiler.CompilerException
import prog8.compiler.intermediate.Instruction
import prog8.compiler.intermediate.IntermediateProgram
import prog8.compiler.intermediate.LabelInstr
import prog8.compiler.intermediate.Opcode
import prog8.compiler.target.c64.MachineDefinition.C64Zeropage
import prog8.compiler.target.c64.MachineDefinition.ESTACK_HI_HEX
import prog8.compiler.target.c64.MachineDefinition.ESTACK_HI_PLUS1_HEX
import prog8.compiler.target.c64.MachineDefinition.ESTACK_LO_HEX
import prog8.compiler.target.c64.MachineDefinition.ESTACK_LO_PLUS1_HEX
import prog8.compiler.target.c64.MachineDefinition.ESTACK_LO_PLUS2_HEX
import prog8.compiler.toHex
import prog8.vm.stackvm.Syscall
import prog8.vm.stackvm.syscallsForStackVm


// note: see https://wiki.nesdev.com/w/index.php/6502_assembly_optimisations


private var breakpointCounter = 0

internal fun simpleInstr2Asm(ins: Instruction, block: IntermediateProgram.ProgramBlock): String? {
    // a label 'instruction' is simply translated into a asm label
    if(ins is LabelInstr) {
        val labelresult =
                if(ins.name.startsWith("${block.name}."))
                    ins.name.substring(block.name.length+1)
                else
                    ins.name
        return if(ins.asmProc) labelresult+"\t\t.proc" else labelresult
    }

    // simple opcodes that are translated directly into one or a few asm instructions
    return when(ins.opcode) {
        Opcode.LINE -> " ;\tsrc line: ${ins.callLabel}"
        Opcode.NOP -> " nop"      // shouldn't be present anymore though
        Opcode.START_PROCDEF -> ""  // is done as part of a label
        Opcode.END_PROCDEF -> " .pend"
        Opcode.TERMINATE -> " brk"
        Opcode.SEC -> " sec"
        Opcode.CLC -> " clc"
        Opcode.SEI -> " sei"
        Opcode.CLI -> " cli"
        Opcode.CARRY_TO_A -> " lda  #0 |  adc  #0"
        Opcode.JUMP -> {
            if(ins.callLabel!=null)
                " jmp  ${ins.callLabel}"
            else
                " jmp  ${hexVal(ins)}"
        }
        Opcode.CALL -> {
            if(ins.callLabel!=null)
                " jsr  ${ins.callLabel}"
            else
                " jsr  ${hexVal(ins)}"
        }
        Opcode.RETURN -> " rts"
        Opcode.RSAVE -> {
            // save cpu status flag and all registers A, X, Y.
            // see http://6502.org/tutorials/register_preservation.html
            " php |  sta  ${C64Zeropage.SCRATCH_REG} | pha  | txa  | pha  | tya  | pha  | lda  ${C64Zeropage.SCRATCH_REG}"
        }
        Opcode.RRESTORE -> {
            // restore all registers and cpu status flag
            " pla |  tay |  pla |  tax |  pla |  plp"
        }
        Opcode.RSAVEX -> " sta  ${C64Zeropage.SCRATCH_REG} |  txa |  pha |  lda  ${C64Zeropage.SCRATCH_REG}"
        Opcode.RRESTOREX -> " sta  ${C64Zeropage.SCRATCH_REG} |  pla |  tax |  lda  ${C64Zeropage.SCRATCH_REG}"
        Opcode.DISCARD_BYTE -> " inx"
        Opcode.DISCARD_WORD -> " inx"
        Opcode.DISCARD_FLOAT -> " inx |  inx |  inx"
        Opcode.DUP_B -> {
            " lda $ESTACK_LO_PLUS1_HEX,x | sta $ESTACK_LO_HEX,x | dex | ;DUP_B "
        }
        Opcode.DUP_W -> {
            " lda $ESTACK_LO_PLUS1_HEX,x | sta $ESTACK_LO_HEX,x | lda $ESTACK_HI_PLUS1_HEX,x | sta $ESTACK_HI_HEX,x | dex "
        }

        Opcode.CMP_B, Opcode.CMP_UB -> {
            " inx | lda $ESTACK_LO_HEX,x | cmp #${ins.arg!!.integerValue().toHex()} | ;CMP_B "
        }

        Opcode.CMP_W, Opcode.CMP_UW -> {
            """
            inx
            lda   $ESTACK_HI_HEX,x
            cmp   #>${ins.arg!!.integerValue().toHex()}
            bne   +
            lda   $ESTACK_LO_HEX,x
            cmp   #<${ins.arg.integerValue().toHex()}
            ; bne   +    not necessary?
            ; lda   #0   not necessary?
+                           
            """
        }

        Opcode.INLINE_ASSEMBLY ->  "@inline@" + (ins.callLabel2 ?: "")      // All of the inline assembly is stored in the calllabel2 property. the '@inline@' is a special marker to accept it.
        Opcode.INCLUDE_FILE -> {
            val offset = if(ins.arg==null) "" else ", ${ins.arg.integerValue()}"
            val length = if(ins.arg2==null) "" else ", ${ins.arg2.integerValue()}"
            " .binary  \"${ins.callLabel}\" $offset $length"
        }
        Opcode.SYSCALL -> {
            if (ins.arg!!.numericValue() in syscallsForStackVm.map { it.callNr })
                throw CompilerException("cannot translate vm syscalls to real assembly calls - use *real* subroutine calls instead. Syscall ${ins.arg.numericValue()}")
            val call = Syscall.values().find { it.callNr==ins.arg.numericValue() }
            when(call) {
                Syscall.FUNC_SIN,
                Syscall.FUNC_COS,
                Syscall.FUNC_ABS,
                Syscall.FUNC_TAN,
                Syscall.FUNC_ATAN,
                Syscall.FUNC_LN,
                Syscall.FUNC_LOG2,
                Syscall.FUNC_SQRT,
                Syscall.FUNC_RAD,
                Syscall.FUNC_DEG,
                Syscall.FUNC_ROUND,
                Syscall.FUNC_FLOOR,
                Syscall.FUNC_CEIL,
                Syscall.FUNC_RNDF,
                Syscall.FUNC_ANY_F,
                Syscall.FUNC_ALL_F,
                Syscall.FUNC_MAX_F,
                Syscall.FUNC_MIN_F,
                Syscall.FUNC_SUM_F -> " jsr  c64flt.${call.name.toLowerCase()}"
                null -> ""
                else -> " jsr  prog8_lib.${call.name.toLowerCase()}"
            }
        }
        Opcode.BREAKPOINT -> {
            breakpointCounter++
            "_prog8_breakpoint_$breakpointCounter\tnop"
        }

        Opcode.PUSH_BYTE -> {
            " lda  #${hexVal(ins)} |  sta  $ESTACK_LO_HEX,x |  dex"
        }
        Opcode.PUSH_WORD -> {
            val value = hexVal(ins)
            " lda  #<$value |  sta  $ESTACK_LO_HEX,x |  lda  #>$value |  sta  $ESTACK_HI_HEX,x |  dex"
        }
        Opcode.PUSH_FLOAT -> {
            val floatConst = getFloatConst(ins.arg!!)
            " lda  #<$floatConst |  ldy  #>$floatConst |  jsr  c64flt.push_float"
        }
        Opcode.PUSH_VAR_BYTE -> {
            when(ins.callLabel) {
                "X" -> throw CompilerException("makes no sense to push X, it's used as a stack pointer itself. You should probably not use the X register (or only in trivial assignments)")
                "A" -> " sta  $ESTACK_LO_HEX,x |  dex"
                "Y" -> " tya |  sta  $ESTACK_LO_HEX,x |  dex"
                else -> " lda  ${ins.callLabel} |  sta  $ESTACK_LO_HEX,x |  dex"
            }
        }
        Opcode.PUSH_VAR_WORD -> {
            " lda  ${ins.callLabel} |  sta  $ESTACK_LO_HEX,x |  lda  ${ins.callLabel}+1 |    sta  $ESTACK_HI_HEX,x |  dex"
        }
        Opcode.PUSH_VAR_FLOAT -> " lda  #<${ins.callLabel} |  ldy  #>${ins.callLabel}|  jsr  c64flt.push_float"
        Opcode.PUSH_MEM_B, Opcode.PUSH_MEM_UB -> {
            """
                lda  ${hexVal(ins)}
                sta  $ESTACK_LO_HEX,x
                dex
                """
        }
        Opcode.PUSH_MEM_W, Opcode.PUSH_MEM_UW -> {
            """
                lda  ${hexVal(ins)}
                sta  $ESTACK_LO_HEX,x
                lda  ${hexValPlusOne(ins)}
                sta  $ESTACK_HI_HEX,x
                dex
                """
        }
        Opcode.PUSH_MEM_FLOAT -> {
            " lda  #<${hexVal(ins)} |  ldy  #>${hexVal(ins)}|  jsr  c64flt.push_float"
        }
        Opcode.PUSH_MEMREAD -> {
            """
                lda  $ESTACK_LO_PLUS1_HEX,x
                sta  (+) +1
                lda  $ESTACK_HI_PLUS1_HEX,x
                sta  (+) +2
+               lda  65535    ; modified
                sta  $ESTACK_LO_PLUS1_HEX,x
                """
        }

        Opcode.PUSH_REGAY_WORD -> {
            " sta  $ESTACK_LO_HEX,x |  tya |  sta  $ESTACK_HI_HEX,x |  dex "
        }
        Opcode.PUSH_ADDR_HEAPVAR -> {
            " lda  #<${ins.callLabel} |  sta  $ESTACK_LO_HEX,x |  lda  #>${ins.callLabel}  |  sta  $ESTACK_HI_HEX,x |  dex"
        }
        Opcode.POP_REGAX_WORD -> throw AssemblyError("cannot load X register from stack because it's used as the stack pointer itself")
        Opcode.POP_REGXY_WORD -> throw AssemblyError("cannot load X register from stack because it's used as the stack pointer itself")
        Opcode.POP_REGAY_WORD -> {
            " inx |  lda  $ESTACK_LO_HEX,x |  ldy  $ESTACK_HI_HEX,x "
        }

        Opcode.READ_INDEXED_VAR_BYTE -> {
            """
                ldy  $ESTACK_LO_PLUS1_HEX,x
                lda  ${ins.callLabel},y
                sta  $ESTACK_LO_PLUS1_HEX,x
                """
        }
        Opcode.READ_INDEXED_VAR_WORD -> {
            """
                lda  $ESTACK_LO_PLUS1_HEX,x
                asl  a
                tay
                lda  ${ins.callLabel},y
                sta  $ESTACK_LO_PLUS1_HEX,x
                lda  ${ins.callLabel}+1,y
                sta  $ESTACK_HI_PLUS1_HEX,x
                """
        }
        Opcode.READ_INDEXED_VAR_FLOAT -> {
            """
                lda  #<${ins.callLabel}
                ldy  #>${ins.callLabel}
                jsr  c64flt.push_float_from_indexed_var
                """
        }
        Opcode.WRITE_INDEXED_VAR_BYTE -> {
            """
                inx
                ldy  $ESTACK_LO_HEX,x
                inx
                lda  $ESTACK_LO_HEX,x
                sta  ${ins.callLabel},y
                """
        }
        Opcode.WRITE_INDEXED_VAR_WORD -> {
            """
                inx
                lda  $ESTACK_LO_HEX,x
                asl  a
                tay
                inx
                lda  $ESTACK_LO_HEX,x
                sta  ${ins.callLabel},y
                lda  $ESTACK_HI_HEX,x
                sta  ${ins.callLabel}+1,y
                """
        }
        Opcode.WRITE_INDEXED_VAR_FLOAT -> {
            """
                lda  #<${ins.callLabel}
                ldy  #>${ins.callLabel}
                jsr  c64flt.pop_float_to_indexed_var
                """
        }
        Opcode.POP_MEM_BYTE -> {
            """
                inx
                lda  $ESTACK_LO_HEX,x
                sta  ${hexVal(ins)}
                """
        }
        Opcode.POP_MEM_WORD -> {
            """
                inx
                lda  $ESTACK_LO_HEX,x
                sta  ${hexVal(ins)}
                lda  $ESTACK_HI_HEX,x
                sta  ${hexValPlusOne(ins)}
                """
        }
        Opcode.POP_MEM_FLOAT -> {
            " lda  ${hexVal(ins)} |  ldy  ${hexValPlusOne(ins)} |  jsr  c64flt.pop_float"
        }
        Opcode.POP_MEMWRITE -> {
            """
                inx
                lda  $ESTACK_LO_HEX,x
                sta  (+) +1
                lda  $ESTACK_HI_HEX,x
                sta  (+) +2
                inx
                lda  $ESTACK_LO_HEX,x
+               sta  65535       ; modified
                """
        }

        Opcode.POP_VAR_BYTE -> {
            when (ins.callLabel) {
                "X" -> throw CompilerException("makes no sense to pop X, it's used as a stack pointer itself")
                "A" -> " inx |  lda  $ESTACK_LO_HEX,x"
                "Y" -> " inx |  ldy  $ESTACK_LO_HEX,x"
                else -> " inx |  lda  $ESTACK_LO_HEX,x |  sta  ${ins.callLabel}"
            }
        }
        Opcode.POP_VAR_WORD -> {
            " inx |  lda  $ESTACK_LO_HEX,x |  ldy  $ESTACK_HI_HEX,x |  sta  ${ins.callLabel} |  sty  ${ins.callLabel}+1"
        }
        Opcode.POP_VAR_FLOAT -> {
            " lda  #<${ins.callLabel} |  ldy  #>${ins.callLabel} |  jsr  c64flt.pop_float"
        }

        Opcode.INC_VAR_UB, Opcode.INC_VAR_B -> {
            when (ins.callLabel) {
                "A" -> " clc |  adc  #1"
                "X" -> " inx"
                "Y" -> " iny"
                else -> " inc  ${ins.callLabel}"
            }
        }
        Opcode.INC_VAR_UW, Opcode.INC_VAR_W -> {
            " inc  ${ins.callLabel} |  bne  + |  inc  ${ins.callLabel}+1 |+"
        }
        Opcode.INC_VAR_F -> {
            """
                lda  #<${ins.callLabel}
                ldy  #>${ins.callLabel}
                jsr  c64flt.inc_var_f
                """
        }
        Opcode.POP_INC_MEMORY -> {
            """
                inx
                lda  $ESTACK_LO_HEX,x
                sta  (+) +1
                lda  $ESTACK_HI_HEX,x
                sta  (+) +2
+               inc  65535     ; modified
                """
        }
        Opcode.POP_DEC_MEMORY -> {
            """
                inx
                lda  $ESTACK_LO_HEX,x
                sta  (+) +1
                lda  $ESTACK_HI_HEX,x
                sta  (+) +2
+               dec  65535     ; modified
                """
        }
        Opcode.DEC_VAR_UB, Opcode.DEC_VAR_B -> {
            when (ins.callLabel) {
                "A" -> " sec |  sbc  #1"
                "X" -> " dex"
                "Y" -> " dey"
                else -> " dec  ${ins.callLabel}"
            }
        }
        Opcode.DEC_VAR_UW, Opcode.DEC_VAR_W -> {
            " lda  ${ins.callLabel} |  bne  + |  dec  ${ins.callLabel}+1 |+ |  dec  ${ins.callLabel}"
        }
        Opcode.DEC_VAR_F -> {
            """
                lda  #<${ins.callLabel}
                ldy  #>${ins.callLabel}
                jsr  c64flt.dec_var_f
                """
        }
        Opcode.INC_MEMORY -> " inc  ${hexVal(ins)}"
        Opcode.DEC_MEMORY -> " dec  ${hexVal(ins)}"
        Opcode.INC_INDEXED_VAR_B, Opcode.INC_INDEXED_VAR_UB -> " inx |  txa |  pha |  lda  $ESTACK_LO_HEX,x |  tax |  inc  ${ins.callLabel},x |  pla |  tax"
        Opcode.DEC_INDEXED_VAR_B, Opcode.DEC_INDEXED_VAR_UB -> " inx |  txa |  pha |  lda  $ESTACK_LO_HEX,x |  tax |  dec  ${ins.callLabel},x |  pla |  tax"

        Opcode.NEG_B -> " jsr  prog8_lib.neg_b"
        Opcode.NEG_W -> " jsr  prog8_lib.neg_w"
        Opcode.NEG_F -> " jsr  c64flt.neg_f"
        Opcode.ABS_B -> " jsr  prog8_lib.abs_b"
        Opcode.ABS_W -> " jsr  prog8_lib.abs_w"
        Opcode.ABS_F -> " jsr  c64flt.abs_f"
        Opcode.POW_F -> " jsr  c64flt.pow_f"
        Opcode.INV_BYTE -> {
            """
                lda  $ESTACK_LO_PLUS1_HEX,x
                eor  #255
                sta  $ESTACK_LO_PLUS1_HEX,x
                """
        }
        Opcode.INV_WORD -> " jsr  prog8_lib.inv_word"
        Opcode.NOT_BYTE -> " jsr  prog8_lib.not_byte"
        Opcode.NOT_WORD -> " jsr  prog8_lib.not_word"
        Opcode.BCS -> {
            val label = ins.callLabel ?: hexVal(ins)
            " bcs  $label"
        }
        Opcode.BCC -> {
            val label = ins.callLabel ?: hexVal(ins)
            " bcc  $label"
        }
        Opcode.BNEG -> {
            val label = ins.callLabel ?: hexVal(ins)
            " bmi  $label"
        }
        Opcode.BPOS -> {
            val label = ins.callLabel ?: hexVal(ins)
            " bpl  $label"
        }
        Opcode.BVC -> {
            val label = ins.callLabel ?: hexVal(ins)
            " bvc  $label"
        }
        Opcode.BVS -> {
            val label = ins.callLabel ?: hexVal(ins)
            " bvs  $label"
        }
        Opcode.BZ -> {
            val label = ins.callLabel ?: hexVal(ins)
            " beq  $label"
        }
        Opcode.BNZ -> {
            val label = ins.callLabel ?: hexVal(ins)
            " bne  $label"
        }
        Opcode.JZ -> {
            val label = ins.callLabel ?: hexVal(ins)
            """
                inx
                lda  $ESTACK_LO_HEX,x
                beq  $label
                """
        }
        Opcode.JZW -> {
            val label = ins.callLabel ?: hexVal(ins)
            """
                inx
                lda  $ESTACK_LO_HEX,x
                beq  $label
                lda  $ESTACK_HI_HEX,x
                beq  $label
                """
        }
        Opcode.JNZ -> {
            val label = ins.callLabel ?: hexVal(ins)
            """
                inx
                lda  $ESTACK_LO_HEX,x
                bne  $label
                """
        }
        Opcode.JNZW -> {
            val label = ins.callLabel ?: hexVal(ins)
            """
                inx
                lda  $ESTACK_LO_HEX,x
                bne  $label
                lda  $ESTACK_HI_HEX,x
                bne  $label
                """
        }
        Opcode.CAST_B_TO_UB -> ""  // is a no-op, just carry on with the byte as-is
        Opcode.CAST_UB_TO_B -> ""  // is a no-op, just carry on with the byte as-is
        Opcode.CAST_W_TO_UW -> ""  // is a no-op, just carry on with the word as-is
        Opcode.CAST_UW_TO_W -> ""  // is a no-op, just carry on with the word as-is
        Opcode.CAST_W_TO_UB -> ""  // is a no-op, just carry on with the lsb of the word as-is
        Opcode.CAST_W_TO_B -> ""  // is a no-op, just carry on with the lsb of the word as-is
        Opcode.CAST_UW_TO_UB -> ""  // is a no-op, just carry on with the lsb of the uword as-is
        Opcode.CAST_UW_TO_B -> ""  // is a no-op, just carry on with the lsb of the uword as-is
        Opcode.CAST_UB_TO_F -> " jsr  c64flt.stack_ub2float"
        Opcode.CAST_B_TO_F -> " jsr  c64flt.stack_b2float"
        Opcode.CAST_UW_TO_F -> " jsr  c64flt.stack_uw2float"
        Opcode.CAST_W_TO_F -> " jsr  c64flt.stack_w2float"
        Opcode.CAST_F_TO_UB -> " jsr  c64flt.stack_float2ub"
        Opcode.CAST_F_TO_B -> " jsr  c64flt.stack_float2b"
        Opcode.CAST_F_TO_UW -> " jsr  c64flt.stack_float2uw"
        Opcode.CAST_F_TO_W -> " jsr  c64flt.stack_float2w"
        Opcode.CAST_UB_TO_UW, Opcode.CAST_UB_TO_W -> " lda  #0 |  sta  $ESTACK_HI_PLUS1_HEX,x"     // clear the msb
        Opcode.CAST_B_TO_UW, Opcode.CAST_B_TO_W -> " lda  $ESTACK_LO_PLUS1_HEX,x |  ${signExtendA("$ESTACK_HI_PLUS1_HEX,x")}"     // sign extend the lsb
        Opcode.MSB -> " lda  $ESTACK_HI_PLUS1_HEX,x |  sta  $ESTACK_LO_PLUS1_HEX,x"
        Opcode.MKWORD -> " inx |  lda  $ESTACK_LO_HEX,x |  sta  $ESTACK_HI_PLUS1_HEX,x "

        Opcode.ADD_UB, Opcode.ADD_B -> {        // TODO inline better (pattern with more opcodes)
            """
                lda  $ESTACK_LO_PLUS2_HEX,x
                clc
                adc  $ESTACK_LO_PLUS1_HEX,x
                inx
                sta  $ESTACK_LO_PLUS1_HEX,x
                """
        }
        Opcode.SUB_UB, Opcode.SUB_B -> {        // TODO inline better (pattern with more opcodes)
            """
                lda  $ESTACK_LO_PLUS2_HEX,x
                sec
                sbc  $ESTACK_LO_PLUS1_HEX,x
                inx
                sta  $ESTACK_LO_PLUS1_HEX,x
                """
        }
        Opcode.ADD_W, Opcode.ADD_UW -> "  jsr  prog8_lib.add_w"
        Opcode.SUB_W, Opcode.SUB_UW -> "  jsr  prog8_lib.sub_w"
        Opcode.MUL_B, Opcode.MUL_UB -> "  jsr  prog8_lib.mul_byte"
        Opcode.MUL_W, Opcode.MUL_UW -> "  jsr  prog8_lib.mul_word"
        Opcode.MUL_F -> "  jsr  c64flt.mul_f"
        Opcode.ADD_F -> "  jsr  c64flt.add_f"
        Opcode.SUB_F -> "  jsr  c64flt.sub_f"
        Opcode.DIV_F -> "  jsr  c64flt.div_f"
        Opcode.IDIV_UB -> "  jsr  prog8_lib.idiv_ub"
        Opcode.IDIV_B -> "  jsr  prog8_lib.idiv_b"
        Opcode.IDIV_W -> "  jsr  prog8_lib.idiv_w"
        Opcode.IDIV_UW -> "  jsr  prog8_lib.idiv_uw"

        Opcode.AND_BYTE -> "  jsr  prog8_lib.and_b"
        Opcode.OR_BYTE -> "  jsr  prog8_lib.or_b"
        Opcode.XOR_BYTE -> "  jsr  prog8_lib.xor_b"
        Opcode.AND_WORD -> "  jsr  prog8_lib.and_w"
        Opcode.OR_WORD -> "  jsr  prog8_lib.or_w"
        Opcode.XOR_WORD -> "  jsr  prog8_lib.xor_w"

        Opcode.BITAND_BYTE -> "  jsr  prog8_lib.bitand_b"
        Opcode.BITOR_BYTE -> "  jsr  prog8_lib.bitor_b"
        Opcode.BITXOR_BYTE -> "  jsr  prog8_lib.bitxor_b"
        Opcode.BITAND_WORD -> "  jsr  prog8_lib.bitand_w"
        Opcode.BITOR_WORD -> "  jsr  prog8_lib.bitor_w"
        Opcode.BITXOR_WORD -> "  jsr  prog8_lib.bitxor_w"

        Opcode.REMAINDER_UB -> "  jsr prog8_lib.remainder_ub"
        Opcode.REMAINDER_UW -> "  jsr prog8_lib.remainder_uw"

        Opcode.GREATER_B -> "  jsr prog8_lib.greater_b"
        Opcode.GREATER_UB -> "  jsr prog8_lib.greater_ub"
        Opcode.GREATER_W -> "  jsr prog8_lib.greater_w"
        Opcode.GREATER_UW -> "  jsr prog8_lib.greater_uw"
        Opcode.GREATER_F -> "  jsr c64flt.greater_f"

        Opcode.GREATEREQ_B -> "  jsr prog8_lib.greatereq_b"
        Opcode.GREATEREQ_UB -> "  jsr prog8_lib.greatereq_ub"
        Opcode.GREATEREQ_W -> "  jsr prog8_lib.greatereq_w"
        Opcode.GREATEREQ_UW -> "  jsr prog8_lib.greatereq_uw"
        Opcode.GREATEREQ_F -> "  jsr c64flt.greatereq_f"

        Opcode.EQUAL_BYTE -> "  jsr prog8_lib.equal_b"
        Opcode.EQUAL_WORD -> "  jsr prog8_lib.equal_w"
        Opcode.EQUAL_F -> "  jsr c64flt.equal_f"
        Opcode.NOTEQUAL_BYTE -> "  jsr prog8_lib.notequal_b"
        Opcode.NOTEQUAL_WORD -> "  jsr prog8_lib.notequal_w"
        Opcode.NOTEQUAL_F -> "  jsr c64flt.notequal_f"

        Opcode.LESS_UB -> "  jsr  prog8_lib.less_ub"
        Opcode.LESS_B -> "  jsr  prog8_lib.less_b"
        Opcode.LESS_UW -> "  jsr  prog8_lib.less_uw"
        Opcode.LESS_W -> "  jsr  prog8_lib.less_w"
        Opcode.LESS_F -> "  jsr  c64flt.less_f"

        Opcode.LESSEQ_UB -> "  jsr  prog8_lib.lesseq_ub"
        Opcode.LESSEQ_B -> "  jsr  prog8_lib.lesseq_b"
        Opcode.LESSEQ_UW -> "  jsr  prog8_lib.lesseq_uw"
        Opcode.LESSEQ_W -> "  jsr  prog8_lib.lesseq_w"
        Opcode.LESSEQ_F -> "  jsr  c64flt.lesseq_f"

        Opcode.SHIFTEDL_BYTE -> "  asl  $ESTACK_LO_PLUS1_HEX,x"
        Opcode.SHIFTEDL_WORD -> "  asl  $ESTACK_LO_PLUS1_HEX,x |  rol  $ESTACK_HI_PLUS1_HEX,x"
        Opcode.SHIFTEDR_SBYTE -> "  lda  $ESTACK_LO_PLUS1_HEX,x |  asl  a |  ror  $ESTACK_LO_PLUS1_HEX,x"
        Opcode.SHIFTEDR_UBYTE -> "  lsr  $ESTACK_LO_PLUS1_HEX,x"
        Opcode.SHIFTEDR_SWORD -> "  lda  $ESTACK_HI_PLUS1_HEX,x |  asl a  |  ror  $ESTACK_HI_PLUS1_HEX,x |  ror  $ESTACK_LO_PLUS1_HEX,x"
        Opcode.SHIFTEDR_UWORD -> "  lsr  $ESTACK_HI_PLUS1_HEX,x |  ror  $ESTACK_LO_PLUS1_HEX,x"

        else -> null
    }
}
