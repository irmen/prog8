%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit


main {
    sub start() {

        str[16] addr_modes = [ "Imp", "Acc", "Imm", "Zp", "ZpX", "ZpY", "Rel", "Abs", "AbsX", "AbsY", "Ind", "IzX", "IzY", "Zpr", "Izp", "IaX" ]

        txt.lowercase()
        txt.print("\nAssembler.\n\n")
        txt.print("Addressing mode ids:\n")
        ubyte mode_id = 0
        uword mode
        for mode in addr_modes {
            mode_id++
            txt.print_ub(mode_id)
            txt.chrout('.')
            txt.chrout(' ')
            txt.print(mode)
            txt.chrout('\n')
        }
        txt.chrout('\n')

        ;user_input()
        benchmark()

        ; test_stack.test()
    }

    sub benchmark() {
        str[20] instructions = ["lda", "ldx", "ldy", "jsr", "bcs", "rts", "lda", "ora", "and", "eor", "wai", "nop", "wai", "nop", "wai", "nop", "wai", "nop", "wai", "nop"]
        ubyte[20] modes =      [3,     4,     8,     8,     7,     1,     12,    13,     5,     4,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1]
        uword valid = 0

        const uword iterations = 40000 / len(instructions)
        const uword amount = iterations * len(instructions)

        txt.print("Benchmark.\nMatching ")
        txt.print_uw(amount)
        txt.print(" mnemonics")

        uword current_time = 0
        c64.SETTIM(0,0,0)

        uword total = 0
        repeat iterations {
            if lsb(total)==0
                txt.chrout('.')
            ubyte idx
            for idx in 0 to len(instructions)-1 {
                ubyte instr_opcode=parse_mnemonic(instructions[idx], modes[idx])
                if_cs
                    valid++
                total++
            }
        }

        ; read clock back
        %asm {{
            phx
            jsr  c64.RDTIM
            sta  current_time
            stx  current_time+1
            plx
        }}


        txt.print("\nDone.\nValid: ")
        txt.print_uw(valid)
        txt.print("\ninvalid: ")
        txt.print_uw(amount-valid)
        txt.print("\ntotal: ")
        txt.print_uw(total)
        txt.print("\nSeconds:")
        uword secs = current_time / 60
        current_time = (current_time - secs*60)*1000/60
        txt.print_uw(secs)
        txt.chrout('.')
        if current_time<10
            txt.chrout('0')
        if current_time<100
            txt.chrout('0')
        txt.print_uw(current_time)
        txt.chrout('\n')
    }

    sub user_input() {
        repeat {
            str input_mnem = "????????"
            str input_am = "????"
            ubyte input_length = 0
            while input_length<5 {
                txt.print("\nmnemonic,addrmode? ")
                input_length = txt.input_chars(input_mnem)
            }
            txt.chrout('\n')

            uword addrmode_str = strfind(input_mnem, ',')
            if addrmode_str {
                ;@(addrmode) = 0
                ;addrmode++
                ubyte addr_mode = conv.str2ubyte(addrmode_str+1)
                ubyte instr_opcode=parse_mnemonic(input_mnem, addr_mode)
                if_cc
                    txt.print("-> invalid\n")
                else {
                    txt.print("-> opcode: ")
                    txt.print_ubhex(instr_opcode, 1)
                    txt.chrout('\n')
                }
            } else {
                txt.print("??\n")
            }

        }
    }

    inline asmsub  parse_mnemonic(uword mnemonic_ptr @AY, ubyte addr_mode @X) clobbers(X) -> ubyte @A, ubyte @Pc {
        %asm {{
            jsr  parse_mnemonic_asm
        }}
    }

    %asminclude "opcodes.asm", ""
}
