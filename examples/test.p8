%import c64utils
%zeropage basicsafe

~ main {

    ; @todo see problem in looplabelproblem.p8
    ; @todo compiler error for using literal values other than 0 or 1 with boolean expressions

    sub start() {

        str  s1 = "hello\u0000abcd12345"
        str_s s2 = "hellothere\u0000bcde"

        c64scr.print(s1)
        c64.CHROUT('\n')
        c64scr.print_ub(len(s1))
        c64.CHROUT('\n')
        c64scr.print_ub(strlen(s1))
        c64.CHROUT('\n')
        s1[2]=0
        c64scr.print_ub(strlen(s1))
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        c64scr.print_ub(len(s2))
        c64.CHROUT('\n')
        c64scr.print_ub(strlen(s2))
        c64.CHROUT('\n')
        s2[7]=0
        c64scr.print_ub(strlen(s2))
        c64.CHROUT('\n')

    }
}
