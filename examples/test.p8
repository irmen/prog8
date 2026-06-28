%import textio
%option no_sysinit
%zeropage basicsafe
%encoding iso

main {
    sub start() {
        txt.iso()
        txt.print("hello world\n")
        ubyte bb
        uword ww
        bb, ww = func(42,12345)
        txt.print_ub(bb)
        txt.spc()
        txt.print_uw(ww)
        txt.nl()
        sys.poweroff_system()
    }

    sub func(ubyte a, uword b) -> ubyte, uword {
        a++
        b++
        return a,b
    }
}
