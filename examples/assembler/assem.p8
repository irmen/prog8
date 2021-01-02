%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        ubyte instr_opcode=parse_mnemonic("lda", 3)
        if_cc
            txt.print("not found")
        else
            txt.print_ubhex(instr_opcode, 1)
        txt.chrout('\n')

        instr_opcode=parse_mnemonic("rts", 1)
        if_cc
            txt.print("not found")
        else
            txt.print_ubhex(instr_opcode, 1)
        txt.chrout('\n')

        instr_opcode=parse_mnemonic("rts", 0)
        if_cc
            txt.print("not found")
        else
            txt.print_ubhex(instr_opcode, 1)
        txt.chrout('\n')

;        if not instr_opcodes {
;            txt.print("invalid instruction\n")
;        } else {
;            ubyte opcode
;            if @(instr_opcodes) {
;                ; instruction has only a single addressing mode and opcode
;                opcode = @(instr_opcodes+1)
;            } else {
;                ; instruction has multiple addressing modes and possible opcodes
;                opcode = @(instr_opcodes+addr_mode)
;            }
;            txt.print_ubhex(opcode,1)
;        }

        test_stack.test()
    }

    inline asmsub  parse_mnemonic(uword mnemonic_ptr @AY, ubyte addr_mode @X) clobbers(X) -> ubyte @A, ubyte @Pc {
        %asm {{
            jsr  parse_mnemonic_asm
        }}
    }

    %asminclude "opcodes.asm", ""
}
