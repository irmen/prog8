%import c64utils
%zeropage basicsafe

~ main {

    ; @todo see problem in looplabelproblem.p8

    sub start() {
        ubyte ub1
        ubyte ub2
        ubyte ub3
        ubyte ub4
        ubyte ub5

        ub1, ub2 = test2()
        c64scr.print_ub(ub1)
        c64.CHROUT('\n')
        c64scr.print_ub(ub2)
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        ub1, ub2 = test3()
        c64scr.print_ub(ub1)
        c64.CHROUT('\n')
        c64scr.print_ub(ub2)
        c64.CHROUT('\n')
        c64.CHROUT('\n')
    }

    asmsub test2() -> clobbers() -> (ubyte @Pc, ubyte @A) {
        %asm {{
            lda  #100
            ldy  #100
            sec
            rts
        }}
    }

    asmsub test3() -> clobbers() -> (ubyte @Pc, ubyte @A) {
        %asm {{
            lda  #101
            ldy  #101
            clc
            rts
        }}
    }

}
