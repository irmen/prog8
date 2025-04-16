%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        txt.home()
        for cx16.r11L in 0 to 20 {
            for cx16.r10L in 0 to 30 {
                txt.setchr(cx16.r10L, cx16.r11L, sc:'.')
            }
        }
        cx16.r10L = txt.getchr(10,5)
        txt.setchr(10,5,sc:'*')
        cx16.r11L = txt.getchr(10,5)
        txt.print_ub(cx16.r10L)
        txt.spc()
        txt.print_ub(cx16.r11L)
    }
}
