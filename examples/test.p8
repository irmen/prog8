%zeropage basicsafe
%import textio

main {
    sub start() {
        pointer pc1
        bool fast1
        pointer pc2
        bool fast2
        pc1, fast1 = blerp1()
        pc2, fast2 = blerp2()

        txt.print_ulhex(pc1, true)
        txt.spc()
        txt.print_bool(fast1)
        txt.nl()
        txt.print_ulhex(pc2, true)
        txt.spc()
        txt.print_bool(fast2)
        txt.nl()
    }

    asmsub blerp1() -> pointer @A0, bool @Pz {
        %asm {{
            move.l #$deadbeef,a0
            move.l #1,d0
            tst.l d0
            rts
        }}
    }

    asmsub blerp2() -> pointer @A0, bool @Pz {
        %asm {{
            move.l #$c0dedbad,a0
            move.l #0,d0
            tst.l d0
            rts
        }}
    }
}
