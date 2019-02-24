%import c64utils
%zeropage basicsafe

~ main {

    ; @todo test memset/memcopy  (there's a bug in memcopy?)

    ; @todo see looplabelproblem.p8

    sub start() {
        ubyte xx

        c64scr.print_ub(X)
        c64.CHROUT('\n')

        A=c64scr.getchr(20,1)
        c64scr.print_ub(A)
        c64.CHROUT('\n')
        xx=c64scr.getchr(20,1)
        c64scr.print_ub(xx)
        c64.CHROUT('\n')
        c64scr.print_ub(X)
        c64.CHROUT('\n')

        A=1+c64scr.getchr(20,1)
        c64scr.print_ub(A)
        c64.CHROUT('\n')
        xx=1+c64scr.getchr(20,1)
        c64scr.print_ub(xx)
        c64.CHROUT('\n')
        c64scr.print_ub(X)
        c64.CHROUT('\n')

        A=c64scr.getchr(20,1)+1
        c64scr.print_ub(A)
        c64.CHROUT('\n')
        xx=c64scr.getchr(20,1)+1
        c64scr.print_ub(xx)
        c64.CHROUT('\n')
        c64scr.print_ub(X)
        c64.CHROUT('\n')


    }

    asmsub asm_routine(ubyte arg1 @ A, ubyte arg2 @ Y) -> clobbers() -> (ubyte @ A) {
        return A+Y
    }

    sub drawNext(ubyte x) {
        A=x
    }
    sub drawNextW(uword w) {
        w++
    }

}
