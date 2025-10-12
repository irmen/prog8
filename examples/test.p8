%import textio
%zeropage basicsafe
;;%option no_sysinit

main {
    sub start() {
        txt.print("memtop=")
        cx16.r0 = cbm.MEMTOP(0, true)
        txt.print_uwhex(cx16.r0, true)
        txt.nl()

        txt.print("bfff: ")
        @($bfff) = 123
        cx16.r9++
        txt.print_ub(@($bfff))
        txt.spc()
        @($bfff) = 0
        cx16.r9++
        txt.print_ub(@($bfff))
        txt.nl()

        txt.print("c000: ")
        @($c000) = 123
        cx16.r9++
        txt.print_ub(@($c000))
        txt.spc()
        @($c000) = 0
        cx16.r9++
        txt.print_ub(@($c000))
        txt.nl()

        txt.print("d000: ")
        @($d000) = 123
        cx16.r9++
        txt.print_ub(@($d000))
        txt.spc()
        @($d000) = 0
        cx16.r9++
        txt.print_ub(@($d000))
        txt.nl()

        txt.print("e000: ")
        @($e000) = 123
        cx16.r9++
        txt.print_ub(@($e000))
        txt.spc()
        @($e000) = 0
        cx16.r9++
        txt.print_ub(@($e000))
        txt.nl()
    }
}
