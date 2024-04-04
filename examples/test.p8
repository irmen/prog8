%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        cx16.mouse_config2(1)
        wait_mousebutton()

        sub wait_mousebutton() {
            do {
                cx16.r0L, void, void = cx16.mouse_pos()
            } until cx16.r0L!=0
            do {
                cx16.r0L, void, void = cx16.mouse_pos()
            } until cx16.r0L==0
        }
    }
}
