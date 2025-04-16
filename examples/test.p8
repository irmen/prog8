%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        repeat 16 {
            sys.waitvsync()
            sys.waitvsync()
            txt.scroll_right()
        }
        repeat 16 {
            sys.waitvsync()
            sys.waitvsync()
            txt.scroll_left()
        }
        repeat 16 {
            sys.waitvsync()
            sys.waitvsync()
            txt.scroll_down()
        }
        repeat 16 {
            sys.waitvsync()
            sys.waitvsync()
            txt.scroll_up()
        }
    }
}
