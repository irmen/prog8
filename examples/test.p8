%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {
    sub start() {
        txt.fill_screen('.',2)

        repeat {
        }
    }

    sub delay () {
        ubyte tt
        repeat 55 {
            repeat 255 {
                tt++
            }
        }
    }
}
