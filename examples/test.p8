%import c64utils
%zeropage basicsafe

~ main {

    ; @todo see problem in looplabelproblem.p8

    sub start() {

        uword z = c64utils.str2uword(" 1 2 3 4 5 ")
        c64scr.print_uw(c64utils.str2uword(" "))
        c64.CHROUT('\n')
        c64scr.print_uw(c64utils.str2uword("  222 "))
        c64.CHROUT('\n')
        c64scr.print_uw(c64utils.str2uword("1234 567"))
        c64.CHROUT('\n')
        c64scr.print_uw(c64utils.str2uword("1234x567"))
        c64.CHROUT('\n')
        c64scr.print_uw(c64utils.str2uword("+1234x567"))
        c64.CHROUT('\n')
        c64scr.print_uw(c64utils.str2uword("00065534"))
        c64.CHROUT('\n')
        c64scr.print_uw(c64utils.str2uword("0006553x4"))
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        word z2 = c64utils.str2word(" 1 2 3 4 5 ")
        c64scr.print_w(c64utils.str2word(" "))
        c64.CHROUT('\n')
        c64scr.print_w(c64utils.str2word("0000000"))
        c64.CHROUT('\n')
        c64scr.print_w(c64utils.str2word("12345"))
        c64.CHROUT('\n')
        c64scr.print_w(c64utils.str2word("64444"))
        c64.CHROUT('\n')
        c64scr.print_w(c64utils.str2word(" 12345"))
        c64.CHROUT('\n')
        c64scr.print_w(c64utils.str2word("-12345"))
        c64.CHROUT('\n')
        c64scr.print_w(c64utils.str2word(" - 123 45"))
        c64.CHROUT('\n')
        c64scr.print_w(c64utils.str2word("+1234 567 "))
        c64.CHROUT('\n')
        c64scr.print_w(c64utils.str2word("-1234 567 "))
        c64.CHROUT('\n')
        c64scr.print_w(c64utils.str2word("-31999"))
        c64.CHROUT('\n')
        c64scr.print_w(c64utils.str2word("31999"))
        c64.CHROUT('\n')
    }

}
