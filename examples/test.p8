%import textio
%import sprites
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {

        cx16.mouse_config2(1)
        sprites.set_mousepointer_hand()
    }
}
