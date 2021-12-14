%import diskio
%import cx16diskio
%import textio
%zeropage basicsafe

main {
    sub start() {
        uword xx
        uword yy

        c64.SETMSG(%10000000)
        xx = diskio.load(8, "hello", 0)
        txt.nl()
        yy = diskio.load(8, "hello", $8800)
        txt.nl()
        c64.SETMSG(0)

        txt.print_uwhex(xx, true)
        txt.nl()
        txt.print_uwhex(yy, true)
        txt.nl()

        c64.SETMSG(%10000000)
        xx = diskio.load_raw(8, "hello", $8700)
        txt.nl()
        c64.SETMSG(0)
        txt.print_uwhex(xx, true)
        txt.nl()

        txt.print("\ncx16:\n")

        c64.SETMSG(%10000000)
        yy = cx16diskio.load(8, "x16edit", 1, $3000)
        txt.nl()
        c64.SETMSG(0)
        txt.print_uwhex(yy, true)
        txt.nl()

        c64.SETMSG(%10000000)
        xx = cx16diskio.load_raw(8, "x16edit", 1, $3000)
        txt.nl()
        c64.SETMSG(0)
        txt.print_uwhex(xx, true)
        txt.nl()
        txt.print_uw(cx16diskio.load_size(1, $3000, xx))
        txt.nl()

        c64.SETMSG(%10000000)
        xx = cx16diskio.load(8, "x16edit", 4, $a100)
        txt.nl()
        c64.SETMSG(0)
        txt.print_uwhex(xx, true)
        txt.nl()
        txt.print_uw(cx16diskio.load_size(4, $a100, xx))
        txt.nl()
        c64.SETMSG(%10000000)
        xx = cx16diskio.load_raw(8, "x16edit", 4, $a100)
        txt.nl()
        c64.SETMSG(0)
        txt.print_uwhex(xx, true)
        txt.nl()
        txt.print_uw(cx16diskio.load_size(4, $a100, xx))
        txt.nl()
    }
}
