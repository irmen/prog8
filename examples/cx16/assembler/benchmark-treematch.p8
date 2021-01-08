%target cx16
%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit


main {

    sub start() {
        txt.print("\nassembler benchmark - tree match routine\n")

        benchmark.benchmark()

        test_stack.test()
    }

}

benchmark {
    sub benchmark() {
        str[20] mnemonics = ["lda", "ldx", "ldy", "jsr", "bcs", "rts", "lda", "ora", "and", "eor", "wai", "nop", "wai", "nop", "wai", "nop", "wai", "nop", "wai", "nop"]
        ubyte[20] modes =   [3,     4,     8,     8,     7,     1,     12,    13,     5,     4,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1]
        uword valid = 0

        const uword iterations = 40000 / len(mnemonics)
        const uword amount = iterations * len(mnemonics)

        txt.print("matching ")
        txt.print_uw(amount)
        txt.print(" mnemonics")

        c64.SETTIM(0,0,0)

        uword total = 0
        repeat iterations {
            if lsb(total)==0
                txt.chrout('.')
            ubyte idx
            for idx in 0 to len(mnemonics)-1 {
                uword instr_info = instructions.match(mnemonics[idx])
                ubyte opcode = instructions.opcode(instr_info, modes[idx])
                if_cs
                    valid++
                total++
            }
        }

        uword current_time = c64.RDTIM16()
        txt.print("\nvalid: ")
        txt.print_uw(valid)
        txt.print("\ninvalid: ")
        txt.print_uw(amount-valid)
        txt.print("\ntotal: ")
        txt.print_uw(total)
        txt.print("\n\nseconds:")
        uword secs = current_time / 60
        current_time = (current_time - secs*60)*1000/60
        txt.print_uw(secs)
        txt.chrout('.')
        if current_time<10
            txt.chrout('0')
        if current_time<100
            txt.chrout('0')
        txt.print_uw(current_time)
        txt.nl()
    }
}

instructions {
    asmsub  match(uword mnemonic_ptr @AY) -> uword @AY {
        ; -- input: mnemonic_ptr in AY,   output:  pointer to instruction info structure or $0000 in AY
        %asm {{
            phx
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldy  #0
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f   ; lowercase
            pha
            iny
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f   ; lowercase
            pha
            iny
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f   ; lowercase
            pha
            iny
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f   ; lowercase
            sta  cx16.r4                ; fourth letter in R4 (only exists for the few 4-letter mnemonics)
            iny
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f   ; lowercase
            sta  cx16.r5                ; fifth letter in R5 (should always be zero or whitespace for a valid mnemonic)
            pla
            tay
            pla
            tax
            pla
            jsr  get_opcode_info
            plx
            rts
        }}
    }

    asmsub  opcode(uword instr_info_ptr @AY, ubyte addr_mode @X) clobbers(X) -> ubyte @A, ubyte @Pc {
        ; -- input: instruction info struct ptr @AY,  desired addr_mode @X
        ;    output: opcode @A,   valid @carrybit
        %asm {{
            cpy  #0
            beq  _not_found
            sta  P8ZP_SCRATCH_W2
            sty  P8ZP_SCRATCH_W2+1
            stx  cx16.r15

            ; debug result address
            ;sec
            ;jsr  txt.print_uwhex
            ;lda  #13
            ;jsr  c64.CHROUT

            ldy  #0
            lda  (P8ZP_SCRATCH_W2),y
            beq  _multi_addrmodes
            iny
            lda  (P8ZP_SCRATCH_W2),y
            cmp  cx16.r15               ; check single possible addr.mode
            bne  _not_found
            iny
            lda  (P8ZP_SCRATCH_W2),y    ; get opcode
            sec
            rts

_not_found  lda  #0
            clc
            rts

_multi_addrmodes
            ldy  cx16.r15
            lda  (P8ZP_SCRATCH_W2),y    ; check opcode for addr.mode
            bne  _valid
            ; opcode $00 usually means 'invalid' but for "brk" it is actually valid so check for "brk"
            ldy  #0
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f       ; lowercase
            cmp  #'b'
            bne  _not_found
            iny
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f       ; lowercase
            cmp  #'r'
            bne  _not_found
            iny
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f       ; lowercase
            cmp  #'k'
            bne  _not_found
            lda  #0
_valid      sec
            rts
        }}
    }

    %asminclude "opcodes.asm", ""
}
