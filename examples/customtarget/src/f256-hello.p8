;
; Simplistic example of some text output and
; reading keys.
;
%launcher none
%import textio

main {
    str hello = "Hello, World from Prog8!"

    sub start() {
        ubyte i
        ubyte key

        txt.cls()

        txt.nl()
        txt.print(hello)
        txt.nl()

        for i in 0 to 15 {
            txt.color($f0 + i)
            txt.print("This is color ")
            txt.print_ub(i)
            txt.nl()
        }
        txt.color($f2)

        txt.row(30)
        txt.print("Using setchr() to draw 'ABC'")
        txt.nl()

        txt.setchr(20, 20, 'A')
        txt.setchr(21, 21, 'B')
        txt.setchr(22, 22, 'C')

        txt.nl()
        txt.print("Using setchr(getchr()) to draw 'ABC'")
        txt.nl()

        txt.setchr(25, 25, txt.getchr(20, 20))
        txt.setchr(26, 26, txt.getchr(21, 21))
        txt.setchr(27, 27, txt.getchr(22, 22))

        txt.setclr(20,20, $f3)
        txt.setclr(21,21, $f4)
        txt.setclr(22,22, $f5)

        txt.setclr(25,25, txt.getclr(20,20))
        txt.setclr(26,26, txt.getclr(21,21))
        txt.setclr(27,27, txt.getclr(22,22))


        txt.nl()
        txt.print("Waiting for a key... ")
        key = txt.waitkey()
        txt.nl()
        txt.print("KEY: ")
        txt.chrout(key)
        txt.nl()
        txt.nl()

        txt.print("Press keys to show them, ctrl-c resets machine.")
        txt.nl()

        ; look for keys forever
        repeat {
            void, key = cbm.GETIN()

            if key != $00 {
                txt.plot(0,40)
                txt.print("key: ")
                txt.chrout(key)
                txt.spc()
                txt.print_ubhex(key, true)
            }

            ; ctrl-c exists / resets machine
            if key == $03
                break
        }
    }
}

