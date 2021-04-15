%import textio
%import gfx2
%zeropage basicsafe

main {
    sub start()  {
        repeat {
            sys.waitvsync()
            ubyte joy = lsb(cx16.joystick_get2(0))
            txt.print_ubbin(joy,1)
            txt.nl()
        }

        repeat {
        }
    }
}
