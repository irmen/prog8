package prog8.compiler.target.c64.codegen

import prog8.ast.base.printWarning
import prog8.compiler.intermediate.Instruction
import prog8.compiler.intermediate.Opcode
import prog8.compiler.target.c64.MachineDefinition
import prog8.compiler.target.c64.MachineDefinition.C64Zeropage
import prog8.compiler.target.c64.MachineDefinition.ESTACK_HI_HEX
import prog8.compiler.target.c64.MachineDefinition.ESTACK_HI_PLUS1_HEX
import prog8.compiler.target.c64.MachineDefinition.ESTACK_LO_HEX
import prog8.compiler.target.c64.MachineDefinition.ESTACK_LO_PLUS1_HEX
import prog8.compiler.toHex

// note: see https://wiki.nesdev.com/w/index.php/6502_assembly_optimisations


internal class AsmPattern(val sequence: List<Opcode>, val altSequence: List<Opcode>?=null, val asm: (List<Instruction>)->String?)

internal fun loadAFromIndexedByVar(idxVarInstr: Instruction, readArrayInstr: Instruction): String {
    // A =  readArrayInstr [ idxVarInstr ]
    return when (idxVarInstr.callLabel) {
        "A" -> " tay |  lda  ${readArrayInstr.callLabel},y"
        "X" -> " txa |  tay |  lda  ${readArrayInstr.callLabel},y"
        "Y" -> " lda  ${readArrayInstr.callLabel},y"
        else -> " ldy  ${idxVarInstr.callLabel} |  lda  ${readArrayInstr.callLabel},y"
    }
}

internal fun loadAYFromWordIndexedByVar(idxVarInstr: Instruction, readArrayInstr: Instruction): String {
    // AY = readWordArrayInstr [ idxVarInstr ]
    return when(idxVarInstr.callLabel) {
        "A" ->
            """
                stx  ${C64Zeropage.SCRATCH_B1}
                asl  a
                tax
                lda  ${readArrayInstr.callLabel},x
                ldy  ${readArrayInstr.callLabel}+1,x
                ldx  ${C64Zeropage.SCRATCH_B1}
                """
        "X" ->
            """
                stx  ${C64Zeropage.SCRATCH_B1}
                txa
                asl  a
                tax
                lda  ${readArrayInstr.callLabel},x
                ldy  ${readArrayInstr.callLabel}+1,x
                ldx  ${C64Zeropage.SCRATCH_B1}
                """
        "Y" ->
            """
                stx  ${C64Zeropage.SCRATCH_B1}
                tya
                asl  a
                tax
                lda  ${readArrayInstr.callLabel},x
                ldy  ${readArrayInstr.callLabel}+1,x
                ldx  ${C64Zeropage.SCRATCH_B1}
                """
        else ->
            """
                stx  ${C64Zeropage.SCRATCH_B1}
                lda  ${idxVarInstr.callLabel}
                asl  a
                tax
                lda  ${readArrayInstr.callLabel},x
                ldy  ${readArrayInstr.callLabel}+1,x
                ldx  ${C64Zeropage.SCRATCH_B1}
                """
    }
}

internal fun storeAToIndexedByVar(idxVarInstr: Instruction, writeArrayInstr: Instruction): String {
    // writeArrayInstr [ idxVarInstr ] =  A
    return when (idxVarInstr.callLabel) {
        "A" -> " tay |  sta  ${writeArrayInstr.callLabel},y"
        "X" -> " stx  ${C64Zeropage.SCRATCH_B1} |  ldy  ${C64Zeropage.SCRATCH_B1} |  sta  ${writeArrayInstr.callLabel},y"
        "Y" -> " sta  ${writeArrayInstr.callLabel},y"
        else -> " ldy  ${idxVarInstr.callLabel} |  sta  ${writeArrayInstr.callLabel},y"
    }
}

internal fun optimizedIntMultiplicationsOnStack(mulIns: Instruction, amount: Int): String? {

    if(mulIns.opcode == Opcode.MUL_B || mulIns.opcode==Opcode.MUL_UB) {
        if(amount in setOf(0,1,2,4,8,16,32,64,128,256))
            printWarning("multiplication by power of 2 should have been optimized into a left shift instruction: $mulIns $amount")
        if(amount in setOf(3,5,6,7,9,10,11,12,13,14,15,20,25,40))
            return " jsr  math.mul_byte_$amount"
        if(mulIns.opcode == Opcode.MUL_B && amount in setOf(-3,-5,-6,-7,-9,-10,-11,-12,-13,-14,-15,-20,-25,-40))
            return " jsr  prog8_lib.neg_b |  jsr  math.mul_byte_${-amount}"
    }
    else if(mulIns.opcode == Opcode.MUL_UW) {
        if(amount in setOf(0,1,2,4,8,16,32,64,128,256))
            printWarning("multiplication by power of 2 should have been optimized into a left shift instruction: $mulIns $amount")
        if(amount in setOf(3,5,6,7,9,10,12,15,20,25,40))
            return " jsr  math.mul_word_$amount"
    }
    else if(mulIns.opcode == Opcode.MUL_W) {
        if(amount in setOf(0,1,2,4,8,16,32,64,128,256))
            printWarning("multiplication by power of 2 should have been optimized into a left shift instruction: $mulIns $amount")
        if(amount in setOf(3,5,6,7,9,10,12,15,20,25,40))
            return " jsr  math.mul_word_$amount"
        if(amount in setOf(-3,-5,-6,-7,-9,-10,-12,-15,-20,-25,-40))
            return " jsr  prog8_lib.neg_w |  jsr  math.mul_word_${-amount}"
    }

    return null
}

object Patterns {
    internal val patterns = mutableListOf<AsmPattern>()

    init {

        // ----------- assignment to BYTE VARIABLE ----------------
        // var = (u)bytevalue
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
            when (segment[1].callLabel) {
                "A", "X", "Y" ->
                    " ld${segment[1].callLabel!!.toLowerCase()}  #${hexVal(segment[0])}"
                else ->
                    " lda  #${hexVal(segment[0])} |  sta  ${segment[1].callLabel}"
            }
        })
        // var = other var
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
            when (segment[1].callLabel) {
                "A" ->
                    when (segment[0].callLabel) {
                        "A" -> null
                        "X" -> "  txa"
                        "Y" -> "  tya"
                        else -> "  lda  ${segment[0].callLabel}"
                    }
                "X" ->
                    when (segment[0].callLabel) {
                        "A" -> "  tax"
                        "X" -> null
                        "Y" -> "  tya |  tax"
                        else -> "  ldx  ${segment[0].callLabel}"
                    }
                "Y" ->
                    when (segment[0].callLabel) {
                        "A" -> "  tay"
                        "X" -> "  txa |  tay"
                        "Y" -> null
                        else -> "  ldy  ${segment[0].callLabel}"
                    }
                else ->
                    when (segment[0].callLabel) {
                        "A" -> "  sta  ${segment[1].callLabel}"
                        "X" -> "  stx  ${segment[1].callLabel}"
                        "Y" -> "  sty  ${segment[1].callLabel}"
                        else -> "  lda  ${segment[0].callLabel} |  sta  ${segment[1].callLabel}"
                    }
            }
        })
        // var = mem (u)byte
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.POP_VAR_BYTE),
                listOf(Opcode.PUSH_MEM_UB, Opcode.POP_VAR_BYTE)) { segment ->
            when (segment[1].callLabel) {
                "A", "X", "Y" -> " ld${segment[1].callLabel!!.toLowerCase()}  ${hexVal(segment[0])}"
                else -> " lda  ${hexVal(segment[0])} |  sta  ${segment[1].callLabel}"
            }
        })
        // var = (u)bytearray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
            val index = intVal(segment[0])
            when (segment[2].callLabel) {
                "A", "X", "Y" ->
                    " ld${segment[2].callLabel!!.toLowerCase()}  ${segment[1].callLabel}+$index"
                else ->
                    " lda  ${segment[1].callLabel}+$index |  sta  ${segment[2].callLabel}"
            }
        })
        // var = (u)bytearray[indexvar]
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
            val loadByteA = loadAFromIndexedByVar(segment[0], segment[1])
            when (segment[2].callLabel) {
                "A" -> " $loadByteA"
                "X" -> " $loadByteA |  tax"
                "Y" -> " $loadByteA |  tay"
                else -> " $loadByteA |  sta  ${segment[2].callLabel}"
            }
        })
        // var = (u)bytearray[mem index var]
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_VAR_BYTE),
                listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
            val loadByteA = " ldy  ${hexVal(segment[0])} |  lda  ${segment[1].callLabel},y"
            when (segment[2].callLabel) {
                "A" -> " $loadByteA"
                "X" -> " $loadByteA |  tax"
                "Y" -> " $loadByteA |  tay"
                else -> " $loadByteA |  sta  ${segment[2].callLabel}"
            }
        })


        // ----------- assignment to BYTE MEMORY ----------------
        // mem = (u)byte value
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
            " lda  #${hexVal(segment[0])} |  sta  ${hexVal(segment[1])}"
        })
        // mem = (u)bytevar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
            when (segment[0].callLabel) {
                "A" -> " sta  ${hexVal(segment[1])}"
                "X" -> " stx  ${hexVal(segment[1])}"
                "Y" -> " sty  ${hexVal(segment[1])}"
                else -> " lda  ${segment[0].callLabel} |  sta  ${hexVal(segment[1])}"
            }
        })
        // mem = mem (u)byte
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.POP_MEM_BYTE),
                listOf(Opcode.PUSH_MEM_UB, Opcode.POP_MEM_BYTE)) { segment ->
            " lda  ${hexVal(segment[0])} |  sta  ${hexVal(segment[1])}"
        })
        // mem = (u)bytearray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
            val address = hexVal(segment[2])
            val index = intVal(segment[0])
            " lda  ${segment[1].callLabel}+$index |  sta  $address"
        })
        // mem = (u)bytearray[indexvar]
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
            val loadByteA = loadAFromIndexedByVar(segment[0], segment[1])
            " $loadByteA |  sta  ${hexVal(segment[2])}"
        })
        // mem = (u)bytearray[mem index var]
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_MEM_BYTE),
                listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
            """
                ldy  ${hexVal(segment[0])}
                lda  ${segment[1].callLabel},y
                sta  ${hexVal(segment[2])}
                """
        })


        // ----------- assignment to BYTE ARRAY ----------------
        // bytearray[index] = (u)byte value
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val index = intVal(segment[1])
            val value = hexVal(segment[0])
            " lda  #$value |  sta  ${segment[2].callLabel}+$index"
        })
        // bytearray[index] = (u)bytevar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val index = intVal(segment[1])
            when (segment[0].callLabel) {
                "A" -> " sta  ${segment[2].callLabel}+$index"
                "X" -> " stx  ${segment[2].callLabel}+$index"
                "Y" -> " sty  ${segment[2].callLabel}+$index"
                else -> " lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel}+$index"
            }
        })
        // bytearray[index] = mem(u)byte
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE),
                listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val index = intVal(segment[1])
            " lda  ${hexVal(segment[0])} |  sta  ${segment[2].callLabel}+$index"
        })

        // bytearray[index var] = (u)byte value
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val storeA = storeAToIndexedByVar(segment[1], segment[2])
            " lda  #${hexVal(segment[0])} |  $storeA"
        })
        // (u)bytearray[index var] = (u)bytevar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val storeA = storeAToIndexedByVar(segment[1], segment[2])
            when (segment[0].callLabel) {
                "A" -> " $storeA"
                "X" -> " txa |  $storeA"
                "Y" -> " tya |  $storeA"
                else -> " lda  ${segment[0].callLabel} |  $storeA"
            }
        })
        // (u)bytearray[index var] = mem (u)byte
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE),
                listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val storeA = storeAToIndexedByVar(segment[1], segment[2])
            " lda  ${hexVal(segment[0])} |  $storeA"
        })

        // bytearray[index mem] = (u)byte value
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            """
                lda  #${hexVal(segment[0])}
                ldy  ${hexVal(segment[1])}
                sta  ${segment[2].callLabel},y
                """
        })
        // bytearray[index mem] = (u)byte var
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val loadY = " ldy  ${hexVal(segment[1])}"
            when (segment[0].callLabel) {
                "A" -> " $loadY |  sta  ${segment[2].callLabel},y"
                "X" -> " txa |  $loadY |  sta  ${segment[2].callLabel},y"
                "Y" -> " tya |  $loadY |  sta  ${segment[2].callLabel},y"
                else -> " lda  ${segment[0].callLabel} |  $loadY |  sta  ${segment[2].callLabel},y"
            }
        })
        // bytearray[index mem] = mem(u)byte
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE),
                listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            """
                ldy  ${hexVal(segment[1])}
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel},y
                """
        })

        // (u)bytearray2[index2] = (u)bytearray1[index1]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val index1 = intVal(segment[0])
            val index2 = intVal(segment[2])
            " lda  ${segment[1].callLabel}+$index1 |  sta  ${segment[3].callLabel}+$index2"
        })
        // (u)bytearray2[index2] = (u)bytearray[indexvar]
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val loadByteA = loadAFromIndexedByVar(segment[0], segment[1])
            val index2 = intVal(segment[2])
            " $loadByteA |  sta  ${segment[3].callLabel}+$index2"
        })
        // (u)bytearray[index2] = (u)bytearray[mem ubyte]
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE),
                listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val index2 = intVal(segment[2])
            """
                ldy  ${hexVal(segment[0])}
                lda  ${segment[1].callLabel},y
                sta  ${segment[3].callLabel}+$index2
                """
        })

        // (u)bytearray2[idxvar2] = (u)bytearray1[index1]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val storeA = storeAToIndexedByVar(segment[2], segment[3])
            val index1 = intVal(segment[0])
            " lda  ${segment[1].callLabel}+$index1 |  $storeA"
        })
        // (u)bytearray2[idxvar2] = (u)bytearray1[idxvar]
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val loadA = loadAFromIndexedByVar(segment[0], segment[1])
            val storeA = storeAToIndexedByVar(segment[2], segment[3])
            " $loadA |  $storeA"
        })
        // (u)bytearray2[idxvar2] = (u)bytearray1[mem ubyte]
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE),
                listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val storeA = storeAToIndexedByVar(segment[2], segment[3])
            " ldy  ${hexVal(segment[0])} |  lda  ${segment[1].callLabel},y |  $storeA"
        })

        // (u)bytearray2[index mem] = (u)bytearray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val index1 = intVal(segment[0])
            """
                lda  ${segment[1].callLabel}+$index1
                ldy  ${hexVal(segment[2])}
                sta  ${segment[3].callLabel},y
                """
        })



        // ----------- assignment to WORD VARIABLE ----------------
        // var = wordvalue
patterns.add(AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_VAR_WORD)) { segment ->
            val number = hexVal(segment[0])
            """
                lda  #<$number
                ldy  #>$number
                sta  ${segment[1].callLabel}
                sty  ${segment[1].callLabel}+1
                """
        })
        // var = ubytevar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_VAR_WORD)) { segment ->
            when (segment[0].callLabel) {
                "A" -> " sta  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
                "X" -> " stx  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
                "Y" -> " sty  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
                else -> " lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
            }
        })
        // var = other var
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  ${segment[0].callLabel}
                ldy  ${segment[0].callLabel}+1
                sta  ${segment[1].callLabel}
                sty  ${segment[1].callLabel}+1
                """
        })
        // var = address-of other var
patterns.add(AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  #<${segment[0].callLabel}
                ldy  #>${segment[0].callLabel}
                sta  ${segment[1].callLabel}
                sty  ${segment[1].callLabel}+1
                """
        })
        // var = mem ubyte
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_UW, Opcode.POP_VAR_WORD)) { segment ->
            " lda  ${hexVal(segment[0])} |  sta  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
        })
        // var = mem (u)word
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_W, Opcode.POP_VAR_WORD),
                listOf(Opcode.PUSH_MEM_UW, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${segment[1].callLabel}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${segment[1].callLabel}+1
                """
        })
        // var = ubytearray[index_byte]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_VAR_WORD)) { segment ->
            val index = hexVal(segment[0])
            " lda  ${segment[1].callLabel}+$index |  sta  ${segment[3].callLabel} |  lda  #0 |  sta  ${segment[3].callLabel}+1"
        })
        // var = ubytearray[index var]
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_VAR_WORD)) { segment ->
            val loadA = loadAFromIndexedByVar(segment[0], segment[1])
            " $loadA |  sta  ${segment[3].callLabel} |  lda  #0 |  sta  ${segment[3].callLabel}+1"
        })
        // var = ubytearray[index mem]
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_VAR_WORD),
                listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel}
                lda  #0
                sta  ${segment[3].callLabel}+1
                """
        })
        // var = (u)wordarray[index_byte]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_VAR_WORD)) { segment ->
            val index = intVal(segment[0]) * 2
            " lda  ${segment[1].callLabel}+$index |  sta  ${segment[2].callLabel} |  lda  ${segment[1].callLabel}+${index + 1} |  sta  ${segment[2].callLabel}+1"
        })
        // var = (u)wordarray[index var]
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_VAR_WORD)) { segment ->
            val loadAY = loadAYFromWordIndexedByVar(segment[0], segment[1])
            """
                $loadAY
                sta  ${segment[2].callLabel}
                sty  ${segment[2].callLabel}+1
                """
        })
        // var = (u)wordarray[index mem]
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_VAR_WORD),
                listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                asl  a
                tay
                lda  ${segment[1].callLabel},y
                sta  ${segment[2].callLabel}
                lda  ${segment[1].callLabel}+1,y
                sta  ${segment[2].callLabel}+1
                """
        })
        // mem = (u)word value
patterns.add(AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_MEM_WORD)) { segment ->
            """
                lda  #<${hexVal(segment[0])}
                ldy  #>${hexVal(segment[0])}
                sta  ${hexVal(segment[1])}
                sty  ${hexValPlusOne(segment[1])}
                """
        })
        // mem uword = ubyte var
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_MEM_WORD)) { segment ->
            when (segment[0].callLabel) {
                "A" -> " sta  ${hexVal(segment[2])} |  lda  #0 |  sta  ${hexValPlusOne(segment[2])}"
                "X" -> " stx  ${hexVal(segment[2])} |  lda  #0 |  sta  ${hexValPlusOne(segment[2])}"
                "Y" -> " sty  ${hexVal(segment[2])} |  lda  #0 |  sta  ${hexValPlusOne(segment[2])}"
                else -> " lda  ${segment[0].callLabel} ||  sta  ${hexVal(segment[2])} |  lda  #0 |  sta  ${hexValPlusOne(segment[2])}"
            }
        })
        // mem uword = mem ubyte
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_UW, Opcode.POP_MEM_WORD)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${hexVal(segment[2])}
                lda  #0
                sta  ${hexValPlusOne(segment[2])}
                """
        })
        // mem uword = uword var
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_MEM_WORD)) { segment ->
            " lda  ${segment[0].callLabel} |  ldy  ${segment[0].callLabel}+1 |  sta  ${hexVal(segment[1])}  |  sty  ${hexValPlusOne(segment[1])}"
        })
        // mem uword = address-of var
patterns.add(AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_MEM_WORD)) { segment ->
            " lda  #<${segment[0].callLabel} |  ldy  #>${segment[0].callLabel} |  sta  ${hexVal(segment[1])} |  sty  ${hexValPlusOne(segment[1])}"
        })
        // mem (u)word = mem (u)word
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_UW, Opcode.POP_MEM_WORD),
                listOf(Opcode.PUSH_MEM_W, Opcode.POP_MEM_WORD)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                ldy  ${hexValPlusOne(segment[0])}
                sta  ${hexVal(segment[1])}
                sty  ${hexValPlusOne(segment[1])}
                """
        })
        // mem uword = ubytearray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_MEM_WORD)) { segment ->
            val index = intVal(segment[0])
            """
                lda  ${segment[1].callLabel}+$index
                ldy  #0
                sta  ${hexVal(segment[3])}
                sty  ${hexValPlusOne(segment[3])}
                """
        })
        // mem uword = bytearray[index]  (sign extended)
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_MEM_WORD)) { segment ->
            val index = intVal(segment[0])
            """
                lda  ${segment[1].callLabel}+$index
                sta  ${hexVal(segment[3])}
                ${signExtendA(hexValPlusOne(segment[3]))}
                """
        })
        // mem uword = bytearray[index var]  (sign extended)
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_MEM_WORD)) { segment ->
            val loadA = loadAFromIndexedByVar(segment[0], segment[1])
            """
                $loadA
                sta  ${hexVal(segment[3])}
                ${signExtendA(hexValPlusOne(segment[3]))}
                """
        })
        // mem uword = bytearray[mem (u)byte]  (sign extended)
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_MEM_WORD),
                listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_MEM_WORD)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${hexVal(segment[3])}
                ${signExtendA(hexValPlusOne(segment[3]))}
                """
        })
        // mem uword = ubytearray[index var]
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_MEM_WORD)) { segment ->
            val loadA = loadAFromIndexedByVar(segment[0], segment[1])
            " $loadA |  sta  ${hexVal(segment[3])} |  lda  #0 |  sta  ${hexValPlusOne(segment[3])}"
        })
        // mem uword = ubytearray[mem (u)bute]
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_MEM_WORD),
                listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_MEM_WORD)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${hexVal(segment[3])}
                lda  #0
                sta  ${hexValPlusOne(segment[3])}
                """
        })
        // mem uword = (u)wordarray[indexvalue]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_MEM_WORD)) { segment ->
            val index = intVal(segment[0]) * 2
            """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+1+$index
                sta  ${hexVal(segment[2])}
                sty  ${hexValPlusOne(segment[2])}
                """
        })
        // mem uword = (u)wordarray[index var]
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_MEM_WORD)) { segment ->
            val loadAY = loadAYFromWordIndexedByVar(segment[0], segment[1])
            """
                $loadAY
                sta  ${hexVal(segment[2])}
                sty  ${hexValPlusOne(segment[2])}
                """
        })
        // mem uword = (u)wordarray[mem index]
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_MEM_WORD),
                listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_MEM_WORD)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                asl  a
                tay
                lda  ${segment[1].callLabel},y
                sta  ${hexVal(segment[2])}
                lda  ${segment[1].callLabel}+1,y
                sta  ${hexValPlusOne(segment[2])}
                """
        })
        // word var = bytevar (sign extended)
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  ${segment[0].callLabel}
                sta  ${segment[2].callLabel}
                ${signExtendA(segment[2].callLabel + "+1")}
                """
        })
        // mem word = bytevar (sign extended)
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_MEM_WORD)) { segment ->
            """
                lda  ${segment[0].callLabel}
                sta  ${hexVal(segment[2])}
                ${signExtendA(hexValPlusOne(segment[2]))}
                """
        })
        // mem word = mem byte (sign extended)
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_W, Opcode.POP_MEM_WORD)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${hexVal(segment[2])}
                ${signExtendA(hexValPlusOne(segment[2]))}
                """
        })
        // var = membyte (sign extended)
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_W, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel}
                ${signExtendA(segment[2].callLabel + "+1")}
                """
        })
        // var = bytearray[index_byte]  (sign extended)
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_VAR_WORD)) { segment ->
            val index = hexVal(segment[0])
            """
                lda  ${segment[1].callLabel}+$index
                sta  ${segment[3].callLabel}
                ${signExtendA(segment[3].callLabel + "+1")}
                """
        })
        // var = bytearray[index var]  (sign extended)
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_VAR_WORD)) { segment ->
            val loadByteA = loadAFromIndexedByVar(segment[0], segment[1])
            """
                $loadByteA
                sta  ${segment[3].callLabel}
                ${signExtendA(segment[3].callLabel + "+1")}
                """
        })
        // var = bytearray[mem (u)byte]  (sign extended)
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_VAR_WORD),
                listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel}
                ${signExtendA(segment[3].callLabel + "+1")}
                """
        })

        // ----------- assignment to UWORD ARRAY ----------------
        // uwordarray[index] = (u)word value
patterns.add(AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index = intVal(segment[1]) * 2
            val value = hexVal(segment[0])
            """
                lda  #<$value
                ldy  #>$value
                sta  ${segment[2].callLabel}+$index
                sty  ${segment[2].callLabel}+${index + 1}
                """
        })
        // uwordarray[index mem] = (u)word value
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_WORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                listOf(Opcode.PUSH_WORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val value = hexVal(segment[0])
            """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  #<$value
                sta  ${segment[2].callLabel},y
                lda  #>$value
                sta  ${segment[2].callLabel}+1,y
                """
        })
        // uwordarray[index mem] = mem (u)word
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel},y
                lda  ${hexValPlusOne(segment[0])}
                sta  ${segment[2].callLabel}+1,y
                """
        })
        // uwordarray[index mem] = (u)word var
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  ${segment[0].callLabel}
                sta  ${segment[2].callLabel},y
                lda  ${segment[0].callLabel}+1
                sta  ${segment[2].callLabel}+1,y
                """
        })
        // uwordarray[index mem] = address-of var
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  #<${segment[0].callLabel}
                sta  ${segment[2].callLabel},y
                lda  #>${segment[0].callLabel}
                sta  ${segment[2].callLabel}+1,y
                """
        })
        // uwordarray[index] = ubytevar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index = intVal(segment[2]) * 2
            when (segment[0].callLabel) {
                "A" -> " sta  ${segment[3].callLabel}+$index |  lda  #0 |  sta  ${segment[3].callLabel}+${index + 1}"
                "X" -> " stx  ${segment[3].callLabel}+$index |  lda  #0 |  sta  ${segment[3].callLabel}+${index + 1}"
                "Y" -> " sty  ${segment[3].callLabel}+$index |  lda  #0 |  sta  ${segment[3].callLabel}+${index + 1}"
                else -> " lda  ${segment[0].callLabel} |  sta  ${segment[3].callLabel}+$index |  lda  #0 |  sta  ${segment[3].callLabel}+${index + 1}"
            }
        })
        // wordarray[index] = bytevar  (extend sign)
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index = intVal(segment[2]) * 2
            when (segment[0].callLabel) {
                "A" ->
                    """
                        sta  ${segment[3].callLabel}+$index
                        ${signExtendA(segment[3].callLabel + "+${index + 1}")}
                        """
                "X" ->
                    """
                        txa
                        sta  ${segment[3].callLabel}+$index
                        ${signExtendA(segment[3].callLabel + "+${index + 1}")}
                        """
                "Y" ->
                    """
                        tya
                        sta  ${segment[3].callLabel}+$index
                        ${signExtendA(segment[3].callLabel + "+${index + 1}")}
                        """
                else ->
                    """
                        lda  ${segment[0].callLabel}
                        sta  ${segment[3].callLabel}+$index
                        ${signExtendA(segment[3].callLabel + "+${index + 1}")}
                        """
            }
        })
        // wordarray[index mem] = bytevar  (extend sign)
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            when (segment[0].callLabel) {
                "A" ->
                    """
                        pha
                        sty  ${C64Zeropage.SCRATCH_B1}
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        pla
                        sta  ${segment[3].callLabel},y
                        ${signExtendA(segment[3].callLabel + "+1,y")}
                        """
                "X" ->
                    """
                        stx  ${C64Zeropage.SCRATCH_B1}
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        lda  ${C64Zeropage.SCRATCH_B1}
                        sta  ${segment[3].callLabel},y
                        ${signExtendA(segment[3].callLabel + "+1,y")}
                        """
                "Y" ->
                    """
                        sty  ${C64Zeropage.SCRATCH_B1}
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        lda  ${C64Zeropage.SCRATCH_B1}
                        sta  ${segment[3].callLabel},y
                        ${signExtendA(segment[3].callLabel + "+1,y")}
                        """
                else ->
                    """
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        lda  ${segment[0].callLabel}
                        sta  ${segment[3].callLabel},y
                        ${signExtendA(segment[3].callLabel + "+1,y")}
                        """
            }
        })
        // wordarray[memory (u)byte] = ubyte mem
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_W, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_W, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            """
                lda  ${hexVal(segment[2])}
                asl  a
                tay
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel},y
                ${signExtendA(segment[3].callLabel + "+1,y")}
                """
        })
        // wordarray[index] = mem byte  (extend sign)
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_W, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index = intVal(segment[2]) * 2
            """
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel}+$index
                ${signExtendA(segment[3].callLabel + "+${index + 1}")}
                """
        })
        // wordarray2[index2] = bytearray1[index1] (extend sign)
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index1 = intVal(segment[0])
            val index2 = segment[3].arg!!.integerValue() * 2
            """
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[4].callLabel}+$index2
                ${signExtendA(segment[4].callLabel + "+${index2 + 1}")}
                """
        })
        // wordarray2[mem (u)byte] = bytearray1[index1] (extend sign)
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index1 = intVal(segment[0])
            """
                lda  ${hexVal(segment[3])}
                asl  a
                tay
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[4].callLabel},y
                ${signExtendA(segment[4].callLabel + "+1,y")}
                """
        })

        // wordarray[indexvar]  = byte  var (sign extended)
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val loadValueOnStack = when (segment[0].callLabel) {
                "A" -> " pha"
                "X" -> " txa |  pha"
                "Y" -> " tya |  pha"
                else -> " lda  ${segment[0].callLabel} |  pha"
            }
            val loadIndexY = when (segment[2].callLabel) {
                "A" -> " asl  a |  tay"
                "X" -> " txa |  asl  a | tay"
                "Y" -> " tya |  asl  a | tay"
                else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
            }
            """
                $loadValueOnStack
                $loadIndexY
                pla
                sta  ${segment[3].callLabel},y
                ${signExtendA(segment[3].callLabel + "+1,y")}
                """
        })
        // wordarray[indexvar]  = byte  mem (sign extended)
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_W, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val loadIndexY = when (segment[2].callLabel) {
                "A" -> " asl  a |  tay"
                "X" -> " txa |  asl  a | tay"
                "Y" -> " tya |  asl  a | tay"
                else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
            }
            """
                $loadIndexY
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel},y
                ${signExtendA(segment[3].callLabel + "+1,y")}
                """
        })
        // wordarray[index] = mem word
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index = intVal(segment[1]) * 2
            """
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel}+$index
                lda  ${hexValPlusOne(segment[0])}
                sta  ${segment[2].callLabel}+$index+1
                """
        })
        // wordarray2[indexvar] = bytearay[index] (sign extended)
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index = intVal(segment[0])
            val loadIndex2Y = when (segment[3].callLabel) {
                "A" -> " asl  a |  tay"
                "X" -> " txa |  asl  a | tay"
                "Y" -> " tya |  asl  a | tay"
                else -> " lda  ${segment[3].callLabel} |  asl  a |  tay"
            }
            """
                $loadIndex2Y
                lda  ${segment[1].callLabel}+$index
                sta  ${segment[4].callLabel},y
                lda  ${segment[1].callLabel}+$index+1
                sta  ${segment[4].callLabel}+1,y
                """
        })
        // uwordarray[mem (u)byte] = mem word
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel},y
                lda  ${hexValPlusOne(segment[0])}
                sta  ${segment[2].callLabel}+1,y
                """
        })

        // uwordarray[index mem] = ubytevar
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            when (segment[0].callLabel) {
                "A" ->
                    """
                        pha
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        pla
                        sta  ${segment[3].callLabel},y
                        lda  #0
                        sta  ${segment[3].callLabel}+1,y
                        """
                "X" ->
                    """
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        txa
                        sta  ${segment[3].callLabel},y
                        lda  #0
                        sta  ${segment[3].callLabel}+1,y
                        """
                "Y" ->
                    """
                        lda  ${hexVal(segment[2])}
                        asl  a
                        stx  ${C64Zeropage.SCRATCH_B1}
                        tax
                        tya
                        sta  ${segment[3].callLabel},x
                        lda  #0
                        sta  ${segment[3].callLabel}+1,x
                        ldx  ${C64Zeropage.SCRATCH_B1}
                        """
                else ->
                    """
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        lda  ${segment[0].callLabel}
                        sta  ${segment[3].callLabel},y
                        lda  #0
                        sta  ${segment[3].callLabel}+1,y
                        """
            }
        })
        // uwordarray[index mem] = ubyte mem
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_UW, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_UW, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            """
                lda  ${hexVal(segment[2])}
                asl  a
                tay
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel},y
                lda  #0
                sta  ${segment[3].callLabel}+1,y
                """
        })
        // uwordarray[index] = mem ubyte
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_UW, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index = intVal(segment[2]) * 2
            """
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel}+$index
                lda  #0
                sta  ${segment[3].callLabel}+${index + 1}
                """
        })
        // uwordarray[index] = (u)wordvar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index = intVal(segment[1]) * 2
            " lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel}+$index |  lda  ${segment[0].callLabel}+1 |  sta  ${segment[2].callLabel}+${index + 1}"
        })
        // uwordarray[index] = address-of var
patterns.add(AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index = intVal(segment[1]) * 2
            " lda  #<${segment[0].callLabel} |  ldy  #>${segment[0].callLabel}  |  sta  ${segment[2].callLabel}+$index |  sty  ${segment[2].callLabel}+${index + 1}"
        })
        // uwordarray[index] = mem uword
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index = intVal(segment[1]) * 2
            """
                lda  ${hexVal(segment[0])}
                ldy  ${hexValPlusOne(segment[0])}
                sta  ${segment[2].callLabel}+$index
                sty  ${segment[2].callLabel}+${index + 1}
                """
        })
        // uwordarray2[index2] = ubytearray1[index1]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index1 = intVal(segment[0])
            val index2 = segment[3].arg!!.integerValue() * 2
            """
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[4].callLabel}+$index2
                lda  #0
                sta  ${segment[4].callLabel}+${index2 + 1}
                """
        })
        // uwordarray2[index2] = (u)wordarray1[index1]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index1 = intVal(segment[0])
            val index2 = intVal(segment[2]) * 2
            """
                lda  ${segment[1].callLabel}+$index1
                ldy  ${segment[1].callLabel}+${index1 + 1}
                sta  ${segment[3].callLabel}+$index2
                sta  ${segment[3].callLabel}+${index2 + 1}
                """
        })
        // uwordarray[indexvar] = (u)word value
patterns.add(AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val value = hexVal(segment[0])
            val loadIndexY = when (segment[1].callLabel) {
                "A" -> " asl  a |  tay"
                "X" -> " txa |  asl  a | tay"
                "Y" -> " tya |  asl  a | tay"
                else -> " lda  ${segment[1].callLabel} |  asl  a |  tay"
            }
            " $loadIndexY |  lda  #<$value |  sta  ${segment[2].callLabel},y |  lda  #>$value |  sta  ${segment[2].callLabel}+1,y"
        })
        // uwordarray[indexvar]  = ubyte  var
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val loadValueOnStack = when (segment[0].callLabel) {
                "A" -> " pha"
                "X" -> " txa |  pha"
                "Y" -> " tya |  pha"
                else -> " lda  ${segment[0].callLabel} |  pha"
            }
            val loadIndexY = when (segment[2].callLabel) {
                "A" -> " asl  a |  tay"
                "X" -> " txa |  asl  a | tay"
                "Y" -> " tya |  asl  a | tay"
                else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
            }
            " $loadValueOnStack | $loadIndexY |  pla |  sta  ${segment[3].callLabel},y |  lda  #0 |  sta  ${segment[3].callLabel}+1,y"
        })
        // uwordarray[indexvar]  = uword  var
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val loadIndexY = when (segment[1].callLabel) {
                "A" -> " asl  a |  tay"
                "X" -> " txa |  asl  a | tay"
                "Y" -> " tya |  asl  a | tay"
                else -> " lda  ${segment[1].callLabel} |  asl  a |  tay"
            }
            " $loadIndexY |  lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel},y |  lda  ${segment[0].callLabel}+1 |  sta  ${segment[2].callLabel}+1,y"
        })
        // uwordarray[indexvar]  = address-of var
patterns.add(AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val loadIndexY = when (segment[1].callLabel) {
                "A" -> " asl  a |  tay"
                "X" -> " txa |  asl  a | tay"
                "Y" -> " tya |  asl  a | tay"
                else -> " lda  ${segment[1].callLabel} |  asl  a |  tay"
            }
            " $loadIndexY |  lda  #<${segment[0].callLabel} |  sta  ${segment[2].callLabel},y |  lda  #>${segment[0].callLabel} |  sta  ${segment[2].callLabel}+1,y"
        })
        // uwordarray[indexvar]  = mem ubyte
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_UW, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val loadIndexY = when (segment[2].callLabel) {
                "A" -> " asl  a |  tay"
                "X" -> " txa |  asl  a | tay"
                "Y" -> " tya |  asl  a | tay"
                else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
            }
            " $loadIndexY |  lda  ${hexVal(segment[0])} |  sta  ${segment[3].callLabel},y |  lda  #0 |  sta  ${segment[3].callLabel}+1,y"
        })
        // uwordarray[indexvar]  = mem (u)word
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD),
                listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val loadIndexY = when (segment[1].callLabel) {
                "A" -> " asl  a |  tay"
                "X" -> " txa |  asl  a | tay"
                "Y" -> " tya |  asl  a | tay"
                else -> " lda  ${segment[1].callLabel} |  asl  a |  tay"
            }
            " $loadIndexY |  lda  ${hexVal(segment[0])} |  sta  ${segment[2].callLabel},y |  lda  ${hexValPlusOne(segment[0])} |  sta  ${segment[2].callLabel}+1,y"
        })
        // uwordarray2[indexvar] = ubytearay[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index = intVal(segment[0])
            val loadIndex2Y = when (segment[3].callLabel) {
                "A" -> " asl  a |  tay"
                "X" -> " txa |  asl  a | tay"
                "Y" -> " tya |  asl  a | tay"
                else -> " lda  ${segment[3].callLabel} |  asl  a |  tay"
            }
            " $loadIndex2Y |  lda  ${segment[1].callLabel}+$index |  sta  ${segment[4].callLabel},y |  lda  #0 |  sta  ${segment[4].callLabel}+1,y"
        })
        // uwordarray2[indexvar] = uwordarray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index = intVal(segment[0]) * 2
            val loadIndex2Y = when (segment[2].callLabel) {
                "A" -> " asl  a |  tay"
                "X" -> " txa |  asl  a | tay"
                "Y" -> " tya |  asl  a | tay"
                else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
            }
            " $loadIndex2Y |  lda  ${segment[1].callLabel}+$index |  sta  ${segment[3].callLabel},y |  lda  ${segment[1].callLabel}+${index + 1} |  sta  ${segment[3].callLabel}+1,y"
        })
        // uwordarray2[index mem] = ubytearray1[index1]
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index1 = intVal(segment[0])
            """
                lda  ${hexVal(segment[3])}
                asl  a
                tay
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[4].callLabel},y
                lda  #0
                sta  ${segment[4].callLabel}+1,y
                """
        })
        // uwordarray2[index mem] = uwordarray1[index1]
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val index1 = intVal(segment[0])
            """
                lda  ${hexVal(segment[2])}
                asl  a
                tay
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[3].callLabel},y
                lda  ${segment[1].callLabel}+${index1 + 1}
                sta  ${segment[3].callLabel}+1,y
                """
        })


        // ----------- assignment to FLOAT VARIABLE ----------------
        // floatvar  = ubytevar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
            val loadByteA = when (segment[0].callLabel) {
                "A" -> ""
                "X" -> "txa"
                "Y" -> "tya"
                else -> "lda  ${segment[0].callLabel}"
            }
            """
                $loadByteA
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.ub2float
                """
        })
        // floatvar = uwordvar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_UW_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
            """
                lda  ${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${segment[0].callLabel}+1
                sta  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.uw2float
                """
        })
        // floatvar = bytevar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
            val loadByteA = when (segment[0].callLabel) {
                "A" -> ""
                "X" -> "txa"
                "Y" -> "tya"
                else -> "lda  ${segment[0].callLabel}"
            }
            """
                $loadByteA
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.b2float
                """
        })
        // floatvar = wordvar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_W_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
            """
                lda  ${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${segment[0].callLabel}+1
                sta  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.w2float
                """
        })
        // floatvar = float value
patterns.add(AsmPattern(listOf(Opcode.PUSH_FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
            val floatConst = getFloatConst(segment[0].arg!!)
            """
                lda  #<$floatConst
                ldy  #>$floatConst
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${segment[1].callLabel}
                ldy  #>${segment[1].callLabel}
                jsr  c64flt.copy_float
                """
        })
        // floatvar = float var
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
            """
                lda  #<${segment[0].callLabel}
                ldy  #>${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${segment[1].callLabel}
                ldy  #>${segment[1].callLabel}
                jsr  c64flt.copy_float
                """
        })
        // floatvar = mem float
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
            """
                lda  #<${hexVal(segment[0])}
                ldy  #>${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${segment[1].callLabel}
                ldy  #>${segment[1].callLabel}
                jsr  c64flt.copy_float
                """
        })
        // floatvar = mem byte
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.b2float
                """
        })
        // floatvar = mem ubyte
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.ub2float
                """
        })
        // floatvar = mem word
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.CAST_W_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.w2float
                """
        })
        // floatvar = mem uword
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_UW, Opcode.CAST_UW_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.uw2float
                """
        })

        // floatvar = bytearray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
            val index = intVal(segment[0])
            """
                lda  ${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[3].callLabel}
                ldy  #>${segment[3].callLabel}
                jsr  c64flt.b2float
                """
        })
        // floatvar = ubytearray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
            val index = intVal(segment[0])
            """
                lda  ${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[3].callLabel}
                ldy  #>${segment[3].callLabel}
                jsr  c64flt.ub2float
                """
        })
        // floatvar = wordarray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.CAST_W_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
            val index = intVal(segment[0]) * 2
            """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+${index + 1}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${segment[3].callLabel}
                ldy  #>${segment[3].callLabel}
                jsr  c64flt.w2float
                """
        })
        // floatvar = uwordarray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.CAST_UW_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
            val index = intVal(segment[0]) * 2
            """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+${index + 1}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${segment[3].callLabel}
                ldy  #>${segment[3].callLabel}
                jsr  c64flt.uw2float
                """
        })
        // floatvar = floatarray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
            val index = intVal(segment[0]) * MachineDefinition.Mflpt5.MemorySize
            """
                lda  #<${segment[1].callLabel}+$index
                ldy  #>${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.copy_float
                """
        })

        // memfloat = float value
patterns.add(AsmPattern(listOf(Opcode.PUSH_FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
            val floatConst = getFloatConst(segment[0].arg!!)
            """
                lda  #<$floatConst
                ldy  #>$floatConst
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${hexVal(segment[1])}
                ldy  #>${hexVal(segment[1])}
                jsr  c64flt.copy_float
                """
        })
        // memfloat = float var
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
            """
                lda  #<${segment[0].callLabel}
                ldy  #>${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${hexVal(segment[1])}
                ldy  #>${hexVal(segment[1])}
                jsr  c64flt.copy_float
                """
        })
        // memfloat  = ubytevar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
            val loadByteA = when (segment[0].callLabel) {
                "A" -> ""
                "X" -> "txa"
                "Y" -> "tya"
                else -> "lda  ${segment[0].callLabel}"
            }
            """
                $loadByteA
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.ub2float
                """
        })
        // memfloat = uwordvar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_UW_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
            """
                lda  ${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${segment[0].callLabel}+1
                sta  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.uw2float
                """
        })
        // memfloat = bytevar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
            val loadByteA = when (segment[0].callLabel) {
                "A" -> ""
                "X" -> "txa"
                "Y" -> "tya"
                else -> "lda  ${segment[0].callLabel}"
            }
            """
                $loadByteA
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.b2float
                """
        })
        // memfloat = wordvar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_W_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
            """
                lda  ${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${segment[0].callLabel}+1
                sta  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.w2float
                """
        })
        // memfloat = mem byte
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.b2float
                """
        })
        // memfloat = mem ubyte
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.ub2float
                """
        })
        // memfloat = mem word
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.CAST_W_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.w2float
                """
        })
        // memfloat = mem uword
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_UW, Opcode.CAST_UW_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.uw2float
                """
        })
        // memfloat = mem float
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
            """
                lda  #<${hexVal(segment[0])}
                ldy  #>${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${hexVal(segment[1])}
                ldy  #>${hexVal(segment[1])}
                jsr  c64flt.copy_float
                """
        })
        // memfloat = bytearray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
            val index = intVal(segment[0])
            """
                lda  ${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[3])}
                ldy  #>${hexVal(segment[3])}
                jsr  c64flt.b2float
                """
        })
        // memfloat = ubytearray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
            val index = intVal(segment[0])
            """
                lda  ${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[3])}
                ldy  #>${hexVal(segment[3])}
                jsr  c64flt.ub2float
                """
        })
        // memfloat = wordarray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.CAST_W_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
            val index = intVal(segment[0]) * 2
            """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+${index + 1}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${hexVal(segment[3])}
                ldy  #>${hexVal(segment[3])}
                jsr  c64flt.w2float
                """
        })
        // memfloat = uwordarray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.CAST_UW_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
            val index = intVal(segment[0]) * 2
            """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+${index + 1}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${hexVal(segment[3])}
                ldy  #>${hexVal(segment[3])}
                jsr  c64flt.uw2float
                """
        })
        // memfloat = floatarray[index]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
            val index = intVal(segment[0]) * MachineDefinition.Mflpt5.MemorySize
            """
                lda  #<${segment[1].callLabel}+$index
                ldy  #>${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.copy_float
                """
        })
        // floatarray[idxbyte] = float
patterns.add(AsmPattern(listOf(Opcode.PUSH_FLOAT, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_FLOAT)) { segment ->
            val floatConst = getFloatConst(segment[0].arg!!)
            val index = intVal(segment[1]) * MachineDefinition.Mflpt5.MemorySize
            """
                lda  #<$floatConst
                ldy  #>$floatConst
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<(${segment[2].callLabel}+$index)
                ldy  #>(${segment[2].callLabel}+$index)
                jsr  c64flt.copy_float
                """
        })
        // floatarray[idxbyte] = floatvar
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_FLOAT, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_FLOAT)) { segment ->
            val index = intVal(segment[1]) * MachineDefinition.Mflpt5.MemorySize
            """
                lda  #<${segment[0].callLabel}
                ldy  #>${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<(${segment[2].callLabel}+$index)
                ldy  #>(${segment[2].callLabel}+$index)
                jsr  c64flt.copy_float
                """
        })
        //  floatarray[idxbyte] = memfloat
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_FLOAT, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_FLOAT)) { segment ->
            val index = intVal(segment[1]) * MachineDefinition.Mflpt5.MemorySize
            """
                lda  #<${hexVal(segment[0])}
                ldy  #>${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<(${segment[2].callLabel}+$index)
                ldy  #>(${segment[2].callLabel}+$index)
                jsr  c64flt.copy_float
                """
        })
        //  floatarray[idx2] = floatarray[idx1]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_FLOAT, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_FLOAT)) { segment ->
            val index1 = intVal(segment[0]) * MachineDefinition.Mflpt5.MemorySize
            val index2 = intVal(segment[2]) * MachineDefinition.Mflpt5.MemorySize
            """
                lda  #<(${segment[1].callLabel}+$index1)
                ldy  #>(${segment[1].callLabel}+$index1)
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1 + 1}
                lda  #<(${segment[3].callLabel}+$index2)
                ldy  #>(${segment[3].callLabel}+$index2)
                jsr  c64flt.copy_float
                """
        })

        // ---------- some special operations ------------------
        // var word = AX register pair
patterns.add(AsmPattern(listOf(Opcode.PUSH_REGAX_WORD, Opcode.POP_VAR_WORD)) { segment ->
            """
                sta  ${segment[1].callLabel}
                stx  ${segment[1].callLabel}+1
                """
        })
        // var word = AY register pair
patterns.add(AsmPattern(listOf(Opcode.PUSH_REGAY_WORD, Opcode.POP_VAR_WORD)) { segment ->
            """
                sta  ${segment[1].callLabel}
                sty  ${segment[1].callLabel}+1
                """
        })
        // var word = XY register pair
patterns.add(AsmPattern(listOf(Opcode.PUSH_REGXY_WORD, Opcode.POP_VAR_WORD)) { segment ->
            """
                stx  ${segment[1].callLabel}
                sty  ${segment[1].callLabel}+1
                """
        })
        // mem word = AX register pair
patterns.add(AsmPattern(listOf(Opcode.PUSH_REGAX_WORD, Opcode.POP_MEM_WORD)) { segment ->
            """
                sta  ${hexVal(segment[1])}
                stx  ${hexValPlusOne(segment[1])}
                """
        })
        // mem word = AY register pair
patterns.add(AsmPattern(listOf(Opcode.PUSH_REGAY_WORD, Opcode.POP_MEM_WORD)) { segment ->
            """
                sta  ${hexVal(segment[1])}
                sty  ${hexValPlusOne(segment[1])}
                """
        })
        // mem word = XY register pair
patterns.add(AsmPattern(listOf(Opcode.PUSH_REGXY_WORD, Opcode.POP_MEM_WORD)) { segment ->
            """
                stx  ${hexVal(segment[1])}
                sty  ${hexValPlusOne(segment[1])}
                """
        })

        // AX register pair = word value
patterns.add(AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_REGAX_WORD)) { segment ->
            val value = hexVal(segment[0])
            " lda  #<$value |  ldx  #>$value"
        })
        // AY register pair = word value
patterns.add(AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_REGAY_WORD)) { segment ->
            val value = hexVal(segment[0])
            " lda  #<$value |  ldy  #>$value"
        })
        // XY register pair = word value
patterns.add(AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_REGXY_WORD)) { segment ->
            val value = hexVal(segment[0])
            " ldx  #<$value |  ldy  #>$value"
        })
        // AX register pair = word var
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_REGAX_WORD)) { segment ->
            " lda  ${segment[0].callLabel} |  ldx  ${segment[0].callLabel}+1"
        })
        // AY register pair = word var
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_REGAY_WORD)) { segment ->
            " lda  ${segment[0].callLabel} |  ldy  ${segment[0].callLabel}+1"
        })
        // XY register pair = word var
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_REGXY_WORD)) { segment ->
            " ldx  ${segment[0].callLabel} |  ldy  ${segment[0].callLabel}+1"
        })
        // AX register pair = mem word
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_UW, Opcode.POP_REGAX_WORD),
                listOf(Opcode.PUSH_MEM_W, Opcode.POP_REGAX_WORD)) { segment ->
            " lda  ${hexVal(segment[0])} |  ldx  ${hexValPlusOne(segment[0])}"
        })
        // AY register pair = mem word
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_UW, Opcode.POP_REGAY_WORD),
                listOf(Opcode.PUSH_MEM_W, Opcode.POP_REGAY_WORD)) { segment ->
            " lda  ${hexVal(segment[0])} |  ldy  ${hexValPlusOne(segment[0])}"
        })
        // XY register pair = mem word
patterns.add(AsmPattern(
                listOf(Opcode.PUSH_MEM_UW, Opcode.POP_REGXY_WORD),
                listOf(Opcode.PUSH_MEM_W, Opcode.POP_REGXY_WORD)) { segment ->
            " ldx  ${hexVal(segment[0])} |  ldy  ${hexValPlusOne(segment[0])}"
        })


        // byte var = wordvar as (u)byte
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_W_TO_UB, Opcode.POP_VAR_BYTE),
                listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_W_TO_B, Opcode.POP_VAR_BYTE)) { segment ->
            when (segment[2].callLabel) {
                "A" -> " lda  ${segment[0].callLabel}"
                "X" -> " ldx  ${segment[0].callLabel}"
                "Y" -> " ldy  ${segment[0].callLabel}"
                else -> " lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel}"
            }
        })
        // byte var = uwordvar as (u)byte
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_UW_TO_UB, Opcode.POP_VAR_BYTE),
                listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_UW_TO_B, Opcode.POP_VAR_BYTE)) { segment ->
            when (segment[2].callLabel) {
                "A" -> " lda  ${segment[0].callLabel}"
                "X" -> " ldx  ${segment[0].callLabel}"
                "Y" -> " ldy  ${segment[0].callLabel}"
                else -> " lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel}"
            }
        })
        // byte var = msb(word var)
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.MSB, Opcode.POP_VAR_BYTE)) { segment ->
            when (segment[2].callLabel) {
                "A" -> " lda  ${segment[0].callLabel}+1"
                "X" -> " ldx  ${segment[0].callLabel}+1"
                "Y" -> " ldy  ${segment[0].callLabel}+1"
                else -> " lda  ${segment[0].callLabel}+1 |  sta  ${segment[2].callLabel}"
            }
        })
        // push word var as (u)byte
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_W_TO_UB),
                listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_W_TO_B)) { segment ->
            " lda  ${segment[0].callLabel} |  sta  $ESTACK_LO_HEX,x |  dex "
        })
        // push uword var as (u)byte
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_UW_TO_UB),
                listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_UW_TO_B)) { segment ->
            " lda  ${segment[0].callLabel} |  sta  $ESTACK_LO_HEX,x |  dex "
        })
        // push msb(word var)
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.MSB)) { segment ->
            " lda  ${segment[0].callLabel}+1 |  sta  $ESTACK_LO_HEX,x |  dex "
        })

        // set a register pair to a certain memory address (of a variable)
patterns.add(AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_REGAX_WORD)) { segment ->
            " lda  #<${segment[0].callLabel} |  ldx  #>${segment[0].callLabel} "
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_REGAY_WORD)) { segment ->
            " lda  #<${segment[0].callLabel} |  ldy  #>${segment[0].callLabel} "
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_REGXY_WORD)) { segment ->
            " ldx  #<${segment[0].callLabel} |  ldy  #>${segment[0].callLabel} "
        })

        // push  memory byte | bytevalue
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_BYTE, Opcode.BITOR_BYTE),
                listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_BYTE, Opcode.BITOR_BYTE)) { segment ->
            " lda  ${hexVal(segment[0])} |  ora  #${hexVal(segment[1])} |  sta  $ESTACK_LO_HEX,x |  dex "
        })
        // push  memory byte & bytevalue
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_BYTE, Opcode.BITAND_BYTE),
                listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_BYTE, Opcode.BITAND_BYTE)) { segment ->
            " lda  ${hexVal(segment[0])} |  and  #${hexVal(segment[1])} |  sta  $ESTACK_LO_HEX,x |  dex "
        })
        // push  memory byte ^ bytevalue
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_BYTE, Opcode.BITXOR_BYTE),
                listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_BYTE, Opcode.BITXOR_BYTE)) { segment ->
            " lda  ${hexVal(segment[0])} |  eor  #${hexVal(segment[1])} |  sta  $ESTACK_LO_HEX,x |  dex "
        })
        // push  var byte | bytevalue
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.BITOR_BYTE)) { segment ->
            " lda  ${segment[0].callLabel} |  ora  #${hexVal(segment[1])} |  sta  $ESTACK_LO_HEX,x |  dex "
        })
        // push  var byte & bytevalue
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.BITAND_BYTE)) { segment ->
            " lda  ${segment[0].callLabel} |  and  #${hexVal(segment[1])} |  sta  $ESTACK_LO_HEX,x |  dex "
        })
        // push  var byte ^ bytevalue
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.BITXOR_BYTE)) { segment ->
            " lda   ${segment[0].callLabel} |  eor  #${hexVal(segment[1])} |  sta  $ESTACK_LO_HEX,x |  dex "
        })

        // push  memory word | wordvalue
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_WORD, Opcode.BITOR_WORD),
                listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_WORD, Opcode.BITOR_WORD)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                ora  #<${hexVal(segment[1])}
                sta  $ESTACK_LO_HEX,x
                lda  ${hexValPlusOne(segment[0])}
                ora  #>${hexVal(segment[1])}
                sta  $ESTACK_HI_HEX,x
                dex
                """
        })
        // push  memory word & wordvalue
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_WORD, Opcode.BITAND_WORD),
                listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_WORD, Opcode.BITAND_WORD)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                and  #<${hexVal(segment[1])}
                sta  $ESTACK_LO_HEX,x
                lda  ${hexValPlusOne(segment[0])}
                and  #>${hexVal(segment[1])}
                sta  $ESTACK_HI_HEX,x
                dex
                """
        })
        // push  memory word ^ wordvalue
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_WORD, Opcode.BITXOR_WORD),
                listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_WORD, Opcode.BITXOR_WORD)) { segment ->
            """
                lda  ${hexVal(segment[0])}
                eor  #<${hexVal(segment[1])}
                sta  $ESTACK_LO_HEX,x
                lda  ${hexValPlusOne(segment[0])}
                eor  #>${hexVal(segment[1])}
                sta  $ESTACK_HI_HEX,x
                dex
                """
        })
        // push  var word | wordvalue
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_WORD, Opcode.BITOR_WORD)) { segment ->
            """
                lda  ${segment[0].callLabel}
                ora  #<${hexVal(segment[1])}
                sta  $ESTACK_LO_HEX,x
                lda  ${segment[0].callLabel}+1
                ora  #>${hexVal(segment[1])}
                sta  $ESTACK_HI_HEX,x
                dex
                """
        })
        // push  var word & wordvalue
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_WORD, Opcode.BITAND_WORD)) { segment ->
            """
                lda  ${segment[0].callLabel}
                and  #<${hexVal(segment[1])}
                sta  $ESTACK_LO_HEX,x
                lda  ${segment[0].callLabel}+1
                and  #>${hexVal(segment[1])}
                sta  $ESTACK_HI_HEX,x
                dex
                """
        })
        // push  var word ^ wordvalue
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_WORD, Opcode.BITXOR_WORD)) { segment ->
            """
                lda  ${segment[0].callLabel}
                eor  #<${hexVal(segment[1])}
                sta  $ESTACK_LO_HEX,x
                lda  ${segment[0].callLabel}+1
                eor  #>${hexVal(segment[1])}
                sta  $ESTACK_HI_HEX,x
                dex
                """
        })
        // push  var byte & var byte
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.BITAND_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
            """
                lda  ${segment[0].callLabel}
                and  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}
                """
        })
        // push  var byte | var byte
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.BITOR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
            """
                lda  ${segment[0].callLabel}
                ora  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}
                """
        })
        // push  var byte ^ var byte
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.BITXOR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
            """
                lda  ${segment[0].callLabel}
                eor  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}
                """
        })
        // push  var word & var word
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_VAR_WORD, Opcode.BITAND_WORD, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  ${segment[0].callLabel}
                and  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}
                lda  ${segment[0].callLabel}+1
                and  ${segment[1].callLabel}+1
                sta  ${segment[3].callLabel}+1
                """
        })
        // push  var word | var word
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_VAR_WORD, Opcode.BITOR_WORD, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  ${segment[0].callLabel}
                ora  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}
                lda  ${segment[0].callLabel}+1
                ora  ${segment[1].callLabel}+1
                sta  ${segment[3].callLabel}+1
                """
        })
        // push  var word ^ var word
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_VAR_WORD, Opcode.BITXOR_WORD, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  ${segment[0].callLabel}
                eor  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}
                lda  ${segment[0].callLabel}+1
                eor  ${segment[1].callLabel}+1
                sta  ${segment[3].callLabel}+1
                """
        })

        // bytearray[consti3] = bytearray[consti1] ^ bytearray[consti2]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE,
                Opcode.BITXOR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val i1 = segment[5].arg!!.integerValue()
            val i2 = segment[0].arg!!.integerValue()
            val i3 = segment[2].arg!!.integerValue()
            """
                lda  ${segment[1].callLabel}+$i2
                eor  ${segment[3].callLabel}+$i3
                sta  ${segment[6].callLabel}+$i1
                """
        })
        // warray[consti3] = warray[consti1] ^ warray[consti2]
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD,
                Opcode.BITXOR_WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val i1 = segment[5].arg!!.integerValue() * 2
            val i2 = segment[0].arg!!.integerValue() * 2
            val i3 = segment[2].arg!!.integerValue() * 2
            """
                lda  ${segment[1].callLabel}+$i2
                eor  ${segment[3].callLabel}+$i3
                sta  ${segment[6].callLabel}+$i1
                lda  ${segment[1].callLabel}+${i2 + 1}
                eor  ${segment[3].callLabel}+${i3 + 1}
                sta  ${segment[6].callLabel}+${i1 + 1}
                """
        })

patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.MKWORD)) { segment ->
            """
                lda  ${segment[0].callLabel}
                sta  $ESTACK_LO_HEX,x
                lda  ${segment[1].callLabel}
                sta  $ESTACK_HI_HEX,x
                dex
                """
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.MKWORD)) { segment ->
            """
                lda  #${hexVal(segment[0])}
                sta  $ESTACK_LO_HEX,x
                lda  ${segment[1].callLabel}
                sta  $ESTACK_HI_HEX,x
                dex
                """
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.MKWORD)) { segment ->
            """
                lda  ${segment[0].callLabel}
                sta  $ESTACK_LO_HEX,x
                lda  #${hexVal(segment[1])}
                sta  $ESTACK_HI_HEX,x
                dex
                """
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.MKWORD, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  ${segment[0].callLabel}
                sta  ${segment[3].callLabel}
                lda  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}+1
                """
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.MKWORD, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  ${segment[0].callLabel}
                sta  ${segment[3].callLabel}
                lda  #${hexVal(segment[1])}
                sta  ${segment[3].callLabel}+1
                """
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.MKWORD, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  #${hexVal(segment[0])}
                sta  ${segment[3].callLabel}
                lda  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}+1
                """
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.MKWORD, Opcode.CAST_UW_TO_W, Opcode.POP_VAR_WORD)) { segment ->
            """
                lda  ${segment[0].callLabel}
                sta  ${segment[4].callLabel}
                lda  ${segment[1].callLabel}
                sta  ${segment[4].callLabel}+1
                """
        })

        // more efficient versions of x+1 and x-1 to avoid pushing the 1 on the stack
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.ADD_B), listOf(Opcode.PUSH_BYTE, Opcode.ADD_UB)) { segment ->
            val amount = segment[0].arg!!.integerValue()
            if (amount in 1..2) {
                " inc  $ESTACK_LO_PLUS1_HEX,x | ".repeat(amount)
            } else
                null
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.ADD_UW), listOf(Opcode.PUSH_WORD, Opcode.ADD_W)) { segment ->
            val amount = segment[0].arg!!.integerValue()
            if (amount in 1..2) {
                " inc  $ESTACK_LO_PLUS1_HEX,x |  bne  + |  inc  $ESTACK_HI_PLUS1_HEX,x |+ | ".repeat(amount)
            } else
                null
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.SUB_B), listOf(Opcode.PUSH_BYTE, Opcode.SUB_UB)) { segment ->
            val amount = segment[0].arg!!.integerValue()
            if (amount in 1..2) {
                " dec  $ESTACK_LO_PLUS1_HEX,x | ".repeat(amount)
            } else
                null
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.SUB_UW), listOf(Opcode.PUSH_WORD, Opcode.SUB_W)) { segment ->
            val amount = segment[0].arg!!.integerValue()
            if (amount in 1..2) {
                " lda  $ESTACK_LO_PLUS1_HEX,x |  bne  + |  dec  $ESTACK_HI_PLUS1_HEX,x |+ |  dec  $ESTACK_LO_PLUS1_HEX,x | ".repeat(amount)
            } else
                null
        })

        // @todo optimize 8 and 16 bit adds and subs (avoid stack use altogether on most common operations)

patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CMP_B), listOf(Opcode.PUSH_VAR_BYTE, Opcode.CMP_UB)) { segment ->
            // this pattern is encountered as part of the loop bound condition in for loops (var + cmp + jz/jnz)
            val cmpval = segment[1].arg!!.integerValue()
            when (segment[0].callLabel) {
                "A" -> {
                    " cmp #$cmpval "
                }
                "X" -> {
                    " cpx #$cmpval "
                }
                "Y" -> {
                    " cpy #$cmpval "
                }
                else -> {
                    " lda  ${segment[0].callLabel} |  cmp  #$cmpval "
                }
            }
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CMP_W), listOf(Opcode.PUSH_VAR_WORD, Opcode.CMP_UW)) { segment ->
            // this pattern is encountered as part of the loop bound condition in for loops (var + cmp + jz/jnz)
            """
                lda  ${segment[0].callLabel}
                cmp  #<${hexVal(segment[1])}
                bne  +
                lda  ${segment[0].callLabel}+1
                cmp  #>${hexVal(segment[1])}
                bne  +
                lda  #0
+
                """
        })

patterns.add(AsmPattern(listOf(Opcode.RRESTOREX, Opcode.LINE, Opcode.RSAVEX), listOf(Opcode.RRESTOREX, Opcode.RSAVEX)) { segment ->
            """
                sta  ${C64Zeropage.SCRATCH_REG}
                pla
                tax
                pha
                lda  ${C64Zeropage.SCRATCH_REG}
                """
        })

        // various optimizable integer multiplications
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.MUL_B), listOf(Opcode.PUSH_BYTE, Opcode.MUL_UB)) { segment ->
            val amount = segment[0].arg!!.integerValue()
            val result = optimizedIntMultiplicationsOnStack(segment[1], amount)
            result
                    ?: " lda  #${hexVal(segment[0])} |  sta  $ESTACK_LO_HEX,x |  dex |  jsr  prog8_lib.mul_byte"
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.MUL_W), listOf(Opcode.PUSH_WORD, Opcode.MUL_UW)) { segment ->
            val amount = segment[0].arg!!.integerValue()
            val result = optimizedIntMultiplicationsOnStack(segment[1], amount)
            if (result != null) result else {
                val value = hexVal(segment[0])
                " lda  #<$value |  sta  $ESTACK_LO_HEX,x |  lda  #>$value |  sta  $ESTACK_HI_HEX,x |  dex |  jsr  prog8_lib.mul_word"
            }
        })

        // various variable or memory swaps
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.POP_VAR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
            val var1 = segment[0].callLabel
            val var2 = segment[1].callLabel
            val var3 = segment[2].callLabel
            val var4 = segment[3].callLabel
            if (var1 == var3 && var2 == var4) {
                """
                    lda  $var1
                    tay
                    lda  $var2
                    sta  $var1
                    sty  $var2
                    """
            } else null
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_VAR_WORD, Opcode.POP_VAR_WORD, Opcode.POP_VAR_WORD)) { segment ->
            val var1 = segment[0].callLabel
            val var2 = segment[1].callLabel
            val var3 = segment[2].callLabel
            val var4 = segment[3].callLabel
            if (var1 == var3 && var2 == var4) {
                """
                    lda  $var1
                    tay
                    lda  $var2
                    sta  $var1
                    sty  $var2
                    lda  $var1+1
                    tay
                    lda  $var2+1
                    sta  $var1+1
                    sty  $var2+1
                    """
            } else null
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_MEM_B, Opcode.POP_MEM_BYTE, Opcode.POP_MEM_BYTE),
                listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_MEM_UB, Opcode.POP_MEM_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
            val addr1 = segment[0].arg!!.integerValue()
            val addr2 = segment[1].arg!!.integerValue()
            val addr3 = segment[2].arg!!.integerValue()
            val addr4 = segment[3].arg!!.integerValue()
            if (addr1 == addr3 && addr2 == addr4) {
                """
                    lda  ${addr1.toHex()}
                    tay
                    lda  ${addr2.toHex()}
                    sta  ${addr1.toHex()}
                    sty  ${addr2.toHex()}
                    """
            } else null
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_MEM_W, Opcode.POP_MEM_WORD, Opcode.POP_MEM_WORD),
                listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_MEM_UW, Opcode.POP_MEM_WORD, Opcode.POP_MEM_WORD)) { segment ->
            val addr1 = segment[0].arg!!.integerValue()
            val addr2 = segment[1].arg!!.integerValue()
            val addr3 = segment[2].arg!!.integerValue()
            val addr4 = segment[3].arg!!.integerValue()
            if (addr1 == addr3 && addr2 == addr4) {
                """
                    lda  ${addr1.toHex()}
                    tay
                    lda  ${addr2.toHex()}
                    sta  ${addr1.toHex()}
                    sty  ${addr2.toHex()}
                    lda  ${(addr1 + 1).toHex()}
                    tay
                    lda  ${(addr2 + 1).toHex()}
                    sta  ${(addr1 + 1).toHex()}
                    sty  ${(addr2 + 1).toHex()}
                    """
            } else null
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE,
                Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val i1 = segment[0].arg!!.integerValue()
            val i2 = segment[2].arg!!.integerValue()
            val i3 = segment[4].arg!!.integerValue()
            val i4 = segment[6].arg!!.integerValue()
            val array1 = segment[1].callLabel
            val array2 = segment[3].callLabel
            val array3 = segment[5].callLabel
            val array4 = segment[7].callLabel
            if (i1 == i3 && i2 == i4 && array1 == array3 && array2 == array4) {
                """
                    lda  $array1+$i1
                    tay
                    lda  $array2+$i2
                    sta  $array1+$i1
                    sty  $array2+$i2
                    """
            } else null
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE,
                Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
            val vi1 = segment[0].callLabel
            val vi2 = segment[2].callLabel
            val vi3 = segment[4].callLabel
            val vi4 = segment[6].callLabel
            val array1 = segment[1].callLabel
            val array2 = segment[3].callLabel
            val array3 = segment[5].callLabel
            val array4 = segment[7].callLabel
            if (vi1 == vi3 && vi2 == vi4 && array1 == array3 && array2 == array4) {
                val load1 = loadAFromIndexedByVar(segment[0], segment[1])
                val load2 = loadAFromIndexedByVar(segment[2], segment[3])
                val storeIn1 = storeAToIndexedByVar(segment[0], segment[1])
                val storeIn2 = storeAToIndexedByVar(segment[2], segment[3])
                """
                    $load1
                    pha
                    $load2
                    $storeIn1
                    pla
                    $storeIn2
                    """
            } else null
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD,
                Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val i1 = segment[0].arg!!.integerValue() * 2
            val i2 = segment[2].arg!!.integerValue() * 2
            val i3 = segment[4].arg!!.integerValue() * 2
            val i4 = segment[6].arg!!.integerValue() * 2
            val array1 = segment[1].callLabel
            val array2 = segment[3].callLabel
            val array3 = segment[5].callLabel
            val array4 = segment[7].callLabel
            if (i1 == i3 && i2 == i4 && array1 == array3 && array2 == array4) {
                """
                    lda  $array1+$i1
                    tay
                    lda  $array2+$i2
                    sta  $array1+$i1
                    sty  $array2+$i2
                    lda  $array1+${i1 + 1}
                    tay
                    lda  $array2+${i2 + 1}
                    sta  $array1+${i1 + 1}
                    sty  $array2+${i2 + 1}
                    """
            } else null
        })
patterns.add(AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_WORD,
                Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
            val vi1 = segment[0].callLabel
            val vi2 = segment[2].callLabel
            val vi3 = segment[4].callLabel
            val vi4 = segment[6].callLabel
            val array1 = segment[1].callLabel
            val array2 = segment[3].callLabel
            val array3 = segment[5].callLabel
            val array4 = segment[7].callLabel
            if (vi1 == vi3 && vi2 == vi4 && array1 == array3 && array2 == array4) {
                //  SCRATCH_B1 = index1
                //  SCRATCH_REG = index2
                //  SCRATCH_W1 = temp storage of array[index2]
                """
                    lda  ${segment[0].callLabel}
                    asl  a
                    sta  ${C64Zeropage.SCRATCH_B1}
                    lda  ${segment[2].callLabel}
                    asl  a
                    sta  ${C64Zeropage.SCRATCH_REG}
                    stx  ${C64Zeropage.SCRATCH_REG_X}
                    tax
                    lda  ${segment[3].callLabel},x
                    ldy  ${segment[3].callLabel}+1,x
                    sta  ${C64Zeropage.SCRATCH_W1}
                    sty  ${C64Zeropage.SCRATCH_W1}+1
                    ldx  ${C64Zeropage.SCRATCH_B1}
                    lda  ${segment[1].callLabel},x
                    ldy  ${segment[1].callLabel}+1,x
                    ldx  ${C64Zeropage.SCRATCH_REG}
                    sta  ${segment[3].callLabel},x
                    tya
                    sta  ${segment[3].callLabel}+1,x
                    ldx  ${C64Zeropage.SCRATCH_B1}
                    lda  ${C64Zeropage.SCRATCH_W1}
                    sta  ${segment[1].callLabel},x
                    lda  ${C64Zeropage.SCRATCH_W1}+1
                    sta  ${segment[1].callLabel}+1,x
                    ldx  ${C64Zeropage.SCRATCH_REG_X}
                    """
            } else null
        })

patterns.add(AsmPattern(listOf(Opcode.DUP_W, Opcode.CMP_UW),
                listOf(Opcode.DUP_W, Opcode.CMP_W)) { segment ->
            """
            lda   $ESTACK_HI_PLUS1_HEX,x
            cmp   #>${segment[1].arg!!.integerValue().toHex()}
            bne   +
            lda   $ESTACK_LO_PLUS1_HEX,x
            cmp   #<${segment[1].arg!!.integerValue().toHex()}
            ; bne   +    not necessary?
            ; lda   #0   not necessary?
+
            """
        })
patterns.add(AsmPattern(listOf(Opcode.DUP_B, Opcode.CMP_UB),
                listOf(Opcode.DUP_B, Opcode.CMP_B)) { segment ->
            """ lda  $ESTACK_LO_PLUS1_HEX,x | cmp  #${segment[1].arg!!.integerValue().toHex()} """
        })
    }
}
