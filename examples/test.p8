%import textio
%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        cx16.r0L = 0
        cx16.r9L = if cx16.r0L & $80 != 0  11 else 22
        cx16.r10L = if cx16.r0L & $40 == 0  11 else 22

        txt.print_ub(cx16.r9L)
        txt.spc()
        txt.print_ub(cx16.r10L)
        txt.nl()

        cx16.r0L = 255
        cx16.r9L = if cx16.r0L & $80 != 0  11 else 22
        cx16.r10L = if cx16.r0L & $40 == 0  11 else 22

        txt.print_ub(cx16.r9L)
        txt.spc()
        txt.print_ub(cx16.r10L)
        txt.nl()
    }
}
