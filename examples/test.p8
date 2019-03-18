%import c64utils
%zeropage basicsafe

~ main {

    ; @todo see problem in looplabelproblem.p8

    sub start() {
        str text = "hello"
        ubyte ub1
        ubyte ub2
        ubyte ub3
        ubyte ub4
        ubyte ub5

        ub1, ub2, ub3, ub4, ub5 = test()
    }

    sub test1() -> ubyte {
        return 99
    }

    asmsub test() -> clobbers() -> (ubyte @Pc, ubyte @Pz, ubyte @Pn, ubyte @Pv, ubyte @A) {
        %asm {{
            lda  #99
            sec
            rts
        }}
    }

}
