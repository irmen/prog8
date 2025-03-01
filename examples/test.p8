%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str name1 = "n1"
        str name2 = ""

        uword buf1 = memory("a", 2000, 0)
        uword buf2 = memory("", 2000, 0)
        uword buf3 = memory(name1, 2000, 0)
        uword buf4 = memory(name2, 2000, 0)

        txt.print_uwhex(buf1, true)
        txt.spc()
        txt.print_uwhex(buf2, true)
        txt.spc()
        txt.print_uwhex(buf3, true)
        txt.spc()
        txt.print_uwhex(buf4, true)
        txt.spc()
    }
}
