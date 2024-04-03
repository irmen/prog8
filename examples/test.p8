%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        void, cx16.r0s, cx16.r1s = cx16.mouse_pos()
    }
}
