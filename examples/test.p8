%zeropage basicsafe
%option no_sysinit
%import textio

main {
    ubyte @shared screen_width, screen_height
    sub start() {
        void, screen_width, screen_height = get_screen_mode()
        screen_width, void, screen_height = get_screen_mode()
        screen_width, screen_height, void = get_screen_mode()
    }
    sub get_screen_mode() -> ubyte, ubyte, ubyte {
        return 0,80,60
    }
}
