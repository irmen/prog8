%import floats
%import textio
%option no_sysinit
%zeropage basicsafe

main {
    struct List {
        uword s
        ubyte n
        float f
        bool b
        ^^List next
    }
    sub start() {
        ^^List @shared l0 = 30000
        ^^List @shared l1 = 20000
        l1.next = l0

        cx16.r0 = &l1.s
        cx16.r1 = &l1.n
        cx16.r2 = &l1.f
        cx16.r3 = &l1.b
        cx16.r4 = &l1.next
        cx16.r5 = &l1.next.s
        cx16.r6 = &l1.next.n
        cx16.r7 = &l1.next.f
        cx16.r8 = &l1.next.b
        cx16.r9 = &l1.next.next

        txt.print_uw(cx16.r0)
        txt.spc()
        txt.print_uw(cx16.r1)
        txt.spc()
        txt.print_uw(cx16.r2)
        txt.spc()
        txt.print_uw(cx16.r3)
        txt.spc()
        txt.print_uw(cx16.r4)
        txt.nl()

        txt.print_uw(cx16.r5)
        txt.spc()
        txt.print_uw(cx16.r6)
        txt.spc()
        txt.print_uw(cx16.r7)
        txt.spc()
        txt.print_uw(cx16.r8)
        txt.spc()
        txt.print_uw(cx16.r9)
        txt.nl()
    }
}
