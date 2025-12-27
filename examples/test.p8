%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        extcommand_print_ulhex($12345678, true)
        txt.nl()
        extcommand_print_ulhex($abcdef99, false)
        txt.nl()
    }

    asmsub extcommand_print_ulhex(long value @R0R1_32, bool prefix @A) clobbers(A,X,Y) {
        %asm {{
            sta  txt.print_ulhex.prefix
            lda  cx16.r0
            sta  txt.print_ulhex.value
            lda  cx16.r0+1
            sta  txt.print_ulhex.value+1
            lda  cx16.r0+2
            sta  txt.print_ulhex.value+2
            lda  cx16.r0+3
            sta  txt.print_ulhex.value+3
            jmp  txt.print_ulhex
        }}
    }

}
