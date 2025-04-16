%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        txt.home()
        for cx16.r11L in 0 to 20 {
            for cx16.r10L in 0 to 30 {
                txt.setchr(cx16.r10L, cx16.r11L, sc:'*')
                txt.setclr(cx16.r10L, cx16.r11L, txt.getclr(cx16.r10L, cx16.r11L)+1)
            }
        }
    }
}
