%import textio
%zeropage basicsafe

main {

    sub start() {
        ubyte uu
        cx16.rambank(1)
        sys.memcopy(&banked.double, $a000, 100)
        cx16.rambank(0)
        txt.nl()

        uword ww
        uu = 99
        txt.print_ub(uu)
        txt.nl()
        callfar($01, $a000, &uu)
        txt.print_ub(uu)
    }
}


banked {
    asmsub double(ubyte number @A) -> ubyte @A {
        %asm {{
            asl  a
            rts
        }}
    }
}
